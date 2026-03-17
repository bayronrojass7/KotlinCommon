package com.verazial.biometry.dev.elyctis

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.elyctis.idboxsdk.elyserialdevice.ElySerialDevice
import com.elyctis.idboxsdk.scard.SCardContext
import com.elyctis.idboxsdk.scard.SCardHandle
import com.elyctis.idboxsdk.scard.SCardReader
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.base.BiometricDeviceReadWriteBase
import com.verazial.core.error.AlreadyActived
import com.verazial.core.error.AlreadyRegistered
import com.verazial.core.error.NoStatusObtained
import com.verazial.core.error.NoValidOption
import com.verazial.core.error.NoWorn
import com.verazial.core.error.UUIDMismatch
import com.verazial.core.interfaces.Device
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import com.verazial.core.model.NfcDeviceData
import com.verazial.core.model.WristbandState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ElyctisIdReader(
    private val context: Context,
    private val usbDevice: UsbDevice,
    override val name: String,
    override val id: String,
) : BiometricDeviceReadWriteBase() {

    private var initialized = false

    @Volatile
    var reading: Boolean = false
        private set

    @Volatile
    var writing: Boolean = false
        private set

    override val biometricTechnologies: Set<BiometricTechnology> =
        setOf(BiometricTechnology.NFC)

    override val coroutineContext = Dispatchers.IO

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override suspend fun initializeSafe() {
        // Permission is handled internally by the idboxsdk when SCardContext.listReaders() is called.
        ElySerialDevice.powerOn()
        initialized = true
        Log.i(TAG, "ElyctisIdReader initialized")
    }

    override suspend fun getMaxSamplesSafe(): Int = 1

    override suspend fun closeSafe() {
        reading = false
        writing = false
        if (initialized) {
            ElySerialDevice.powerOff()
        }
        initialized = false
        Log.i(TAG, "ElyctisIdReader closed")
    }

    override suspend fun stillAliveSafe(): Boolean = initialized

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {

        check(initialized)
        reading = true

        try {
            while (reading) {
                val transport = ElyctisScardTransport(context)

                // connect() powers on the card; throws if no card present yet → retry
                try {
                    transport.connect()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "No card yet: ${e.message}")
                    delay(200)
                    continue
                }

                try {
                    val infoJson = sendJsonApdu(transport, "info", null)
                    if (infoJson == null) { delay(100); continue }

                    val credentialsJson = sendJsonApdu(transport, "credentials", null)
                    if (credentialsJson == null) { delay(100); continue }

                    val biodataJson = sendJsonApdu(transport, "biodata", null)
                    if (biodataJson == null) { delay(100); continue }

                    val infoObj    = if (infoJson.isNotBlank())        JSONObject(infoJson)        else JSONObject()
                    val credsObj   = if (credentialsJson.isNotBlank()) JSONObject(credentialsJson) else JSONObject()
                    val biodataObj = if (biodataJson.isNotBlank())     JSONObject(biodataJson)     else JSONObject()

                    val credentialsRaw   = credsObj.optString("credentials", null)
                    val credentialsArray = if (!credentialsRaw.isNullOrBlank())
                        runCatching { JSONArray(credentialsRaw) }.getOrNull()
                    else null

                    val biodataRaw    = biodataObj.optString("biodata", null)
                    val biodataObject = runCatching {
                        if (!biodataRaw.isNullOrBlank()) JSONObject(biodataRaw) else null
                    }.getOrNull()

                    val jsonContent = JSONObject()
                    for (key in infoObj.keys()) jsonContent.put(key, infoObj.get(key))
                    jsonContent.put("credentials", credentialsArray)
                    jsonContent.put("biodata", biodataObject)
                    jsonContent.put("template", if (biodataObject != null) "ON_TEMPLATE" else "ON_SERVER")

                    val subtype = targetSamples.first()
                    emit(
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    type     = BiometricSample.Type.NFC,
                                    subtype  = subtype,
                                    format   = BiometricSample.Type.NFC.TEXT,
                                    quality  = -1,
                                    contents = jsonContent.toString()
                                )
                            )
                        )
                    )

                    reading = false
                    break

                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error during read session: ${e.message}", e)
                    delay(100)
                } finally {
                    transport.safeClose()
                }
            }
        } finally {
            reading = false
        }
    }

    override suspend fun stopReadSafe() {
        reading = false
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    override suspend fun performWriteSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean,
        content: JsonObject
    ): Flow<BiometricCapture> = flow {

        check(initialized)
        writing = true

        try {
            while (writing) {
                val transport = ElyctisScardTransport(context)

                try {
                    transport.connect()

                    val action = content["action"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("action missing")

                    val infoJson = sendJsonApdu(transport, "info", null)
                    if (infoJson == null) { delay(100); continue }

                    val nfcId = content["nfcId"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("nfcId missing")

                    val dataPayload = content["data"]?.toString()

                    if (JSONObject(infoJson).getString("uuid") != nfcId) {
                        writing = false
                        throw UUIDMismatch
                    }

                    val jsonParser    = Json { ignoreUnknownKeys = true }
                    val nfcDeviceData = jsonParser.decodeFromString<NfcDeviceData>(infoJson)
                    val state         = getNfcDeviceState(nfcDeviceData.wristbandState)

                    if (state == "ERROR") throw NoStatusObtained

                    when (action) {
                        "register" -> if (state != "unregistered") throw AlreadyRegistered
                        "activate" -> when {
                            state == "active" -> throw AlreadyActived
                            state != "worn"   -> throw NoWorn
                        }
                        "unknown"  -> throw NoValidOption
                    }

                    val json = sendJsonApdu(transport, action, dataPayload)
                    if (json == null) { delay(100); continue }

                    val subtype = targetSamples.first()
                    emit(
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    type     = BiometricSample.Type.NFC,
                                    subtype  = subtype,
                                    format   = BiometricSample.Type.NFC.TEXT,
                                    quality  = -1,
                                    contents = json
                                )
                            )
                        )
                    )

                    writing = false
                    break

                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error during write session: ${e.message}", e)
                    if (e is NoWorn || e is AlreadyActived || e is AlreadyRegistered
                        || e is NoStatusObtained || e is NoValidOption || e is UUIDMismatch
                    ) {
                        writing = false
                        throw e
                    }
                    delay(100)
                } finally {
                    transport.safeClose()
                }
            }
        } finally {
            writing = false
        }
    }

    override suspend fun stopWriteSafe() {
        writing = false
    }

    // -------------------------------------------------------------------------
    // Device discovery
    // -------------------------------------------------------------------------

    internal companion object : DeviceProvider(Dispatchers.IO) {

        private const val TAG = "ElyctisIdReader"

        // ELY Chipset User Manual §1.1 — VID / contactless-capable PIDs
        private const val ELYCTIS_VID             = 11128
        private val      ELYCTIS_CONTACTLESS_PIDS = setOf(66, 67)
        private const val CHUNK_SIZE               = 250

        override suspend fun IDeviceManager.DeviceProviderScope.getManageableDevice(): Device? {
            if (deviceCandidate is IDeviceManager.DeviceCandidate.Usb) {
                val usb = deviceCandidate.device
                if (usb.vendorId == ELYCTIS_VID && usb.productId in ELYCTIS_CONTACTLESS_PIDS) {
                    return ElyctisIdReader(
                        context   = context,
                        usbDevice = usb,
                        name      = "ElyctisIdReader",
                        id        = "Elyctis"
                    )
                }
            }
            return null
        }
    }

    // -------------------------------------------------------------------------
    // JSON APDU helpers
    // -------------------------------------------------------------------------

    private fun sendJsonApdu(transport: ElyctisScardTransport, action: String, data: String?): String? {
        val aid        = hexToBytes("F0010203040506")
        val selectApdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid

        val selectResp = runCatching { transport.transceive(selectApdu) }.getOrElse {
            Log.w(TAG, "SELECT AID failed: ${it.message}"); return null
        }
        if (selectResp.size < 2) return null

        val selSw1 = selectResp[selectResp.size - 2]
        val selSw2 = selectResp[selectResp.size - 1]
        Log.i(TAG, "SELECT AID SW: %02X%02X".format(selSw1, selSw2))
        if (selSw1 != 0x90.toByte() || selSw2 != 0x00.toByte()) return null

        val payload = buildJsonObject {
            put("origin", JsonPrimitive("verazial"))
            put("action", JsonPrimitive(action))
            if (!data.isNullOrBlank()) put("data", Json.parseToJsonElement(data))
        }
        val json      = Json.encodeToString(JsonObject.serializer(), payload)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        Log.i(TAG, "Sending JSON: $json")

        return if (jsonBytes.size <= CHUNK_SIZE) sendSingleApdu(transport, jsonBytes)
        else sendChunkedApdu(transport, jsonBytes)
    }

    private fun sendSingleApdu(transport: ElyctisScardTransport, data: ByteArray): String? {
        val apdu = byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, data.size.toByte()) + data + 0x00
        val resp = runCatching { transport.transceive(apdu) }.getOrElse {
            Log.w(TAG, "Single APDU failed: ${it.message}"); return null
        }
        if (resp.size < 2) return null

        val sw1  = resp[resp.size - 2]
        val sw2  = resp[resp.size - 1]
        val full = ArrayList<Byte>()
        full.addAll(resp.copyOfRange(0, resp.size - 2).toList())
        full.addAll(getMoreData(transport, sw1, sw2))
        return full.toByteArray().toString(Charsets.UTF_8)
    }

    private fun sendChunkedApdu(transport: ElyctisScardTransport, data: ByteArray): String? {
        val totalChunks  = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        val fullResponse = ArrayList<Byte>()
        var lastSw1: Byte = 0
        var lastSw2: Byte = 0

        for (i in 0 until totalChunks) {
            val offset = i * CHUNK_SIZE
            val chunk  = data.copyOfRange(offset, minOf(offset + CHUNK_SIZE, data.size))
            val p1     = when (i) { 0 -> 0x01; totalChunks - 1 -> 0x03; else -> 0x02 }.toByte()
            val apdu   = byteArrayOf(0x80.toByte(), 0x10, p1, i.toByte(), chunk.size.toByte()) +
                    chunk + 0x00

            val resp = runCatching { transport.transceive(apdu) }.getOrElse {
                Log.w(TAG, "Chunk $i failed: ${it.message}"); return null
            }
            if (resp.size < 2) return null

            lastSw1 = resp[resp.size - 2]
            lastSw2 = resp[resp.size - 1]
            fullResponse.addAll(resp.copyOfRange(0, resp.size - 2).toList())

            if (lastSw1 != 0x90.toByte() && lastSw1 != 0x61.toByte()) {
                Log.w(TAG, "Chunk $i bad SW: %02X%02X".format(lastSw1, lastSw2))
                return null
            }
        }
        fullResponse.addAll(getMoreData(transport, lastSw1, lastSw2))
        return fullResponse.toByteArray().toString(Charsets.UTF_8)
    }

    private fun getMoreData(transport: ElyctisScardTransport, sw1: Byte, sw2: Byte): List<Byte> {
        val result = ArrayList<Byte>()
        var cSw1   = sw1
        var cSw2   = sw2
        while (cSw1 == 0x61.toByte()) {
            val le      = if (cSw2.toInt() == 0) 256 else cSw2.toInt()
            val getResp = byteArrayOf(0x80.toByte(), 0x20, 0x00, 0x00, le.toByte())
            val more    = try { transport.transceive(getResp) } catch (e: Exception) { break }
            if (more.size < 2) break
            result.addAll(more.copyOfRange(0, more.size - 2).toList())
            cSw1 = more[more.size - 2]
            cSw2 = more[more.size - 1]
        }
        return result
    }

    private fun getNfcDeviceState(state: WristbandState?): String {
        if (state == null) return "ERROR"
        return when {
            state.active     -> "active"
            state.worn       -> "worn"
            state.linked     -> "linked"
            state.registered -> "registered"
            else             -> "unregistered"
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("-", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // -------------------------------------------------------------------------
    // ElyctisScardTransport
    //
    // Wraps SCardContext + SCardReader + SCardHandle from the idboxsdk AAR.
    //
    // Confirmed API from ElySCardReaderFragment + ElySCardScriptFragment:
    //
    //   SCardContext(context).listReaders()      → SCardReader[]
    //   SCardReader.connect(PROTOCOL_ANY)        → SCardHandle  (null = no card)
    //   SCardReader.disconnect()                 → void
    //   SCardReader.escape(apdu)                 → ByteArray    (reader escape commands)
    //   SCardHandle.transmit(apdu)               → ByteArray    (card APDUs)
    //   SCardHandle.getAtr()                     → ByteArray
    //
    // connect() prefers the "CL" (contactless) slot when multiple slots are present,
    // matching the reader name convention seen in ElySCardReaderAdapter ("CL" check).
    // -------------------------------------------------------------------------

    class ElyctisScardTransport(private val context: Context) {

        private var scardReader: SCardReader? = null
        private var cardHandle:  SCardHandle? = null
        private var connected = false

        /**
         * Finds the contactless reader slot via SCardContext, then powers on the card.
         * Throws if no reader is found or no card is present in the slot.
         */
        fun connect() {
            val scardContext = SCardContext(context)

            // listReaders() returns all available reader slots (SCardReader[])
            val readers = scardContext.listReaders()
            if (readers.isNullOrEmpty()) {
                throw IllegalStateException("No Elyctis reader slot found")
            }

            // Prefer the contactless slot ("CL" in name), as seen in ElySCardReaderAdapter.
            // Fall back to the first available slot if no CL slot is found.
            val reader = readers.firstOrNull { it.readerName.contains("CL") }
                ?: readers.first()

            Log.i(TAG, "Using reader slot: ${reader.readerName}")

            // connect(PROTOCOL_ANY) powers on the card and returns a SCardHandle.
            // Returns null if no card is present in the slot.
            val handle = reader.connect(SCardReader.PROTOCOL_ANY)
                ?: throw IllegalStateException("No card present in slot: ${reader.readerName}")

            Log.i(TAG, "Card ATR: ${handle.atr.joinToString("") { "%02X".format(it) }}")

            scardReader = reader
            cardHandle  = handle
            connected   = true
        }

        /**
         * Sends an APDU to the card via SCardHandle.transmit() and returns the response.
         * Confirmed in ElySCardScriptFragment: card.transmit(apduBytes)
         */
        fun transceive(apdu: ByteArray): ByteArray {
            if (!connected) throw IllegalStateException("Transport not connected")
            return cardHandle?.transmit(apdu)
                ?: throw IllegalStateException("SCardHandle.transmit() returned null")
        }

        /**
         * Sends a reader escape command via SCardReader.escape().
         * Used for reader-level commands (e.g. LPCD calibration, Get LPCD reference)
         * that do not require a card present.
         * Confirmed in ElySCardScriptFragment: mScardReader.escape(apduBytes)
         */
        fun escape(apdu: ByteArray): ByteArray {
            return scardReader?.escape(apdu)
                ?: throw IllegalStateException("SCardReader not connected")
        }

        /**
         * Disconnects from the card slot via SCardReader.disconnect().
         * Confirmed in ElySCardReaderFragment.disconnectCard()
         */
        fun close() {
            connected  = false
            cardHandle = null
            runCatching { scardReader?.disconnect() }
            scardReader = null
        }

        fun safeClose() = runCatching { close() }

        private val SCardReader.readerName: String
            get() = runCatching { getReaderName() }.getOrDefault("unknown")

        private val SCardHandle.atr: ByteArray
            get() = runCatching { getAtr() }.getOrDefault(ByteArray(0))

        companion object {
            private const val TAG = "ElyctisScardTransport"
        }
    }
}