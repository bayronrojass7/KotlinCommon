package com.verazial.biometry.dev.hid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hidglobal.cardreaderlibrary.ccid.DeviceCommunicator
import com.hidglobal.cardreaderlibrary.usb.UsbCommunicator
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.base.BiometricDeviceReadWriteBase
import com.verazial.biometry.base.BiometricDeviceUSB.Companion.ACTION_USB_PERMISSION
import com.verazial.core.error.AlreadyActived
import com.verazial.core.error.AlreadyRegistered
import com.verazial.core.error.DeviceCommunicationError
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Omnikey5022(
    private val context: Context,
    private val usbDevice: UsbDevice,
    override val name: String,
    override val id: String,
) : BiometricDeviceReadWriteBase() {

    private var initialized = false

    internal val usbManager = context.getSystemService(UsbManager::class.java)

    @Volatile
    var reading: Boolean = false
        private set

    @Volatile
    var writing: Boolean = false
        private set

    override val biometricTechnologies: Set<BiometricTechnology> =
        setOf(BiometricTechnology.NFC)

    override val coroutineContext = Dispatchers.IO

    override suspend fun initializeSafe() {
        runCatching {
            askUSBPermission()
        }.getOrElse {
            throw DeviceCommunicationError(
                message = "USB permission denied",
                cause = it
            )
        }
        initialized = true
    }

    private suspend fun askUSBPermission(): Boolean = suspendCancellableCoroutine { cont ->
        if (usbManager.hasPermission(usbDevice)) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    kotlin.runCatching {
                        cont.resume(
                            intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                            )
                        )
                    }
                }
            }
        }

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
            else
                0
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, null, null, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        usbManager.requestPermission(usbDevice, permissionIntent)

        cont.invokeOnCancellation {
            context.unregisterReceiver(usbReceiver)
        }
    }

    override suspend fun getMaxSamplesSafe(): Int = 1

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {

        check(initialized)

        reading = true
        val isoDep = HidIsoDepTransport(context)

        try {
            while (reading) {
                try {
                    isoDep.connect()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("Omnikey", "Connect failed: ${e.message}", e)
                    delay(50)
                    continue
                }

                try {
                    val infoJson = sendJsonApdu(isoDep, "info", null)
                    if (infoJson == null) {
                        delay(50)
                        continue
                    }

                    val credentialsJson = sendJsonApdu(isoDep, "credentials", null)
                    if (credentialsJson == null) {
                        delay(50)
                        continue
                    }

                    val biodataJson = sendJsonApdu(isoDep, "biodata", null)
                    if (biodataJson == null) {
                        delay(50)
                        continue
                    }

                    val infoObj = if (infoJson.isNotBlank()) JSONObject(infoJson) else JSONObject()
                    val credsObj =
                        if (credentialsJson.isNotBlank()) JSONObject(credentialsJson) else JSONObject()
                    val biodataObj =
                        if (biodataJson.isNotBlank()) JSONObject(biodataJson) else JSONObject()

                    val credentialsRaw = credsObj.optString("credentials", null)

                    val credentialsArray: JSONArray? = if (!credentialsRaw.isNullOrBlank()) {
                        try {
                            JSONArray(credentialsRaw)
                        } catch (e: Exception) {
                            null
                        }
                    } else null

                    val biodataRaw = biodataObj.optString("biodata", null)
                    val biodataObject = try {
                        if (!biodataRaw.isNullOrBlank()) JSONObject(biodataRaw) else null
                    } catch (_: Exception) {
                        null
                    }

                    val jsonContent = JSONObject()
                    for (key in infoObj.keys()) {
                        jsonContent.put(key, infoObj.get(key))
                    }
                    jsonContent.put("credentials", credentialsArray)
                    jsonContent.put("biodata", biodataObject)
                    jsonContent.put(
                        "template",
                        if (biodataObject != null) "ON_TEMPLATE" else "ON_SERVER"
                    )

                    val subtype = targetSamples.first()
                    emit(
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    type = BiometricSample.Type.NFC,
                                    subtype = subtype,
                                    format = BiometricSample.Type.NFC.TEXT,
                                    quality = -1,
                                    contents = jsonContent.toString()
                                )
                            )
                        )
                    )

                    reading = false
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("Omnikey", "Error during NFC session: ${e.message}", e)
                    delay(50)
                    continue
                }
            }
            try {
                isoDep.close()
            } catch (e: Exception) {
                Log.w("Omnikey", "Error trying close NFC session: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.w("Omnikey", "Error in omnikey ${e.message}", e)
        } finally {
            reading = false
        }
    }

    override suspend fun stopReadSafe() {
        reading = false
    }

    override suspend fun performWriteSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean,
        content: JsonObject
    ): Flow<BiometricCapture> = flow {

        check(initialized)

        writing = true
        val isoDep = HidIsoDepTransport(context)
        try {
            while (writing) {
                try {
                    isoDep.connect()

                    val action = content["action"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("action missing")

                    val infoJson = sendJsonApdu(isoDep, "info", null)
                    if (infoJson == null) {
                        delay(50)
                        continue
                    }

                    val nfcId = content["nfcId"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("nfcId missing")

                    val dataPayload = content["data"]?.toString()

                    if (JSONObject(infoJson).getString("uuid") != nfcId) {
                        writing = false
                        throw UUIDMismatch
                    }

                    val jsonParser = Json { ignoreUnknownKeys = true }
                    val nfcDeviceData = jsonParser.decodeFromString<NfcDeviceData>(infoJson)
                    val state = getNfcDeviceState(nfcDeviceData.wristbandState)

                    if (state == "ERROR") {
                        throw NoStatusObtained
                    }
                    when (action) {
                        "register" -> if (state != "unregistered") {
                            throw AlreadyRegistered
                        }

                        "activate" ->
                            if (state == "active") {
                                throw AlreadyActived
                            } else if (state != "worn") {
                                throw NoWorn
                            }

                        "unknown" -> {
                            throw NoValidOption
                        }
                    }

                    val json = sendJsonApdu(isoDep, action, dataPayload)
                    if (json == null) {
                        delay(50)
                        continue
                    }
                    val subtype = targetSamples.first()
                    emit(
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    type = BiometricSample.Type.NFC,
                                    subtype = subtype,
                                    format = BiometricSample.Type.NFC.TEXT,
                                    quality = -1,
                                    contents = json
                                )
                            )
                        )
                    )
                    writing = false
                    break
                } catch (e: Exception) {
                    Log.w("Omnikey", "Error during NFC session: ${e.message}", e)
                    if (e is CancellationException) throw e
                    if (e is NoWorn || e is AlreadyActived || e is AlreadyRegistered
                        || e is NoStatusObtained || e is NoValidOption || e is UUIDMismatch
                    ) {
                        writing = false
                        throw e
                    }
                    delay(50)
                    continue
                }
            }
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                Log.w("Omnikey", "Error trying close NFC session on write: ${e.message}", e)
            }
            writing = false
        }
    }

    override suspend fun stopWriteSafe() {
        writing = false
    }

    override suspend fun closeSafe() {
        reading = false
        initialized = false
    }

    override suspend fun stillAliveSafe(): Boolean = initialized

    internal companion object : DeviceProvider(Dispatchers.IO) {
        override suspend fun IDeviceManager.DeviceProviderScope.getManageableDevice(): Device? {
            if (deviceCandidate is IDeviceManager.DeviceCandidate.Usb) {
                val usb = deviceCandidate.device

                if (usb.vendorId == 1899 && usb.productId == 20514) {
                    return Omnikey5022(
                        context = context,
                        usbDevice = usb,
                        name = "HID_OMNIKEY_5022",
                        id = "hid5022"
                    )
                }
            }
            return null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("-", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    class HidIsoDepTransport(private val context: Context) {

        private lateinit var usb: UsbCommunicator
        private lateinit var ccid: DeviceCommunicator

        private var connected = false

        fun connect() {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val device = findOmnikey(usbManager)
                ?: throw IllegalStateException("No HID Omnikey 5022 found")

            val intf = getCcidInterface(device)
                ?: throw IllegalStateException("No CCID interface found on Omnikey")

            if (!usbManager.hasPermission(device)) {
                throw IllegalStateException("Missing USB permission for Omnikey")
            }

            usb = UsbCommunicator(usbManager, device, intf).also {
                if (!it.initialize()) {
                    it.shutdown()
                    throw IllegalStateException("Failed to initialize USB communicator")
                }
            }

            ccid = DeviceCommunicator(usb).also {
                if (!it.initialize()) {
                    it.shutdown()
                    usb.shutdown()
                    throw IllegalStateException("Failed to initialize CCID communicator")
                }
            }

            val atr = try {
                ccid.slotIccOn()
            } catch (e: Exception) {
                Log.w("Omnikey", "slotIccOn failed: ${e.message}", e)
                close()
                throw IllegalStateException("Failed to power on card")
            }

            if (atr == null || atr.isEmpty()) {
                Log.w("Omnikey", "Invalid ATR, aborting")
                close()
                throw IllegalStateException("No card present")
            }

            connected = true
        }

        fun transceive(apdu: ByteArray): ByteArray {
            if (!connected) throw IllegalStateException("Not connected")
            return ccid.transmitData(apdu)
                ?: throw IllegalStateException("APDU transmit failed")
        }

        fun close() {
            connected = false
            if (::ccid.isInitialized) {
                try { ccid.slotIccOff() } catch (_: Exception) {}
                try { ccid.shutdown() } catch (_: Exception) {}
            }
            if (::usb.isInitialized) {
                try { usb.shutdown() } catch (_: Exception) {}
            }
        }

        private fun findOmnikey(usbManager: UsbManager): UsbDevice? {
            return usbManager.deviceList.values.firstOrNull { it.vendorId == 0x076B }
        }

        private fun getCcidInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == 0x0B) return intf
            }
            return null
        }
    }

    fun sendJsonApdu(isoDep: HidIsoDepTransport, action: String, data: String? = null): String? {
        // --- SELECT AID ---
        val aid = hexToBytes("F0010203040506")
        val selectApdu = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
            aid.size.toByte()
        ) + aid

        val selectResp = try {
            isoDep.transceive(selectApdu)
        } catch (e: Exception) {
            Log.w("Omnikey", "SendJson - SELECT AID transceive failed: ${e.message}", e)
            return null
        }

        if (selectResp.size < 2) {
            Log.w("Omnikey", "SendJson - SELECT AID response too short")
            return null
        }

        val selSw1 = selectResp[selectResp.size - 2]
        val selSw2 = selectResp[selectResp.size - 1]

        Log.i(
            "Omnikey",
            "SendJson - SELECT AID SW1SW2: %02X%02X".format(selSw1, selSw2)
        )

        if (selSw1 != 0x90.toByte() || selSw2 != 0x00.toByte()) {
            Log.w(
                "Omnikey",
                "SendJson - SELECT AID failed: %02X%02X".format(selSw1, selSw2)
            )
            return null
        }

        // --- Build JSON payload ---
        val payload = buildJsonObject {
            put("origin", JsonPrimitive("verazial"))
            put("action", JsonPrimitive(action))

            if (!data.isNullOrBlank()) {
                put("data", Json.parseToJsonElement(data))
            }
        }

        val json = Json.encodeToString(JsonObject.serializer(), payload)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        Log.i("Omnikey", "Sending JSON: $json")

        val CHUNK_SIZE = 250

        return if (jsonBytes.size <= CHUNK_SIZE) {
            sendSingleApdu(isoDep, jsonBytes)
        } else {
            sendChunkedApdu(isoDep, jsonBytes, CHUNK_SIZE)
        }
    }

    private fun sendSingleApdu(isoDep: HidIsoDepTransport, data: ByteArray): String? {
        val apdu = byteArrayOf(
            0x80.toByte(), // CLA
            0x10.toByte(), // INS
            0x00.toByte(), // P1
            0x00.toByte(), // P2
            data.size.toByte()
        ) + data + 0x00.toByte()

        val resp: ByteArray = try {
            isoDep.transceive(apdu)
        } catch (e: Exception) {
            Log.w("Omnikey", "SendJson - JSON APDU transceive failed: ${e.message}", e)
            return null
        }

        if (resp.size < 2) return null

        val sw1 = resp[resp.size - 2]
        val sw2 = resp[resp.size - 1]

        val full = ArrayList<Byte>()
        full.addAll(resp.copyOfRange(0, resp.size - 2).toList())

        // Handle more data (SW1=0x61 or DO case)
        val extra = getMoreData(isoDep, sw1, sw2)
        full.addAll(extra)

        return full.toByteArray().toString(Charsets.UTF_8)
    }

    private fun sendChunkedApdu(
        isoDep: HidIsoDepTransport,
        data: ByteArray,
        chunkSize: Int
    ): String? {

        val totalChunks = (data.size + chunkSize - 1) / chunkSize
        val fullResponse = ArrayList<Byte>()

        var lastSw1: Byte = 0
        var lastSw2: Byte = 0

        for (i in 0 until totalChunks) {
            val offset = i * chunkSize
            val size = minOf(chunkSize, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + size)

            val p1 = when (i) {
                0 -> 0x01
                totalChunks - 1 -> 0x03
                else -> 0x02
            }.toByte()

            val p2 = i.toByte()

            val apdu = byteArrayOf(
                0x80.toByte(), 0x10, p1, p2,
                chunk.size.toByte()
            ) + chunk + 0x00.toByte()

            val resp = try {
                isoDep.transceive(apdu)
            } catch (e: Exception) {
                Log.w("Omnikey", "Chunk $i transceive failed: ${e.message}", e)
                return null
            }

            if (resp.size < 2) return null

            lastSw1 = resp[resp.size - 2]
            lastSw2 = resp[resp.size - 1]

            fullResponse.addAll(resp.copyOfRange(0, resp.size - 2).toList())

            if (lastSw1 != 0x90.toByte() && lastSw1 != 0x61.toByte()) {
                Log.w("Omnikey", "Chunk $i failed SW1SW2: %02X%02X".format(lastSw1, lastSw2))
                return null
            }
        }

        // Handle more data after last chunk
        val extra = getMoreData(isoDep, lastSw1, lastSw2)
        fullResponse.addAll(extra)

        return fullResponse.toByteArray().toString(Charsets.UTF_8)
    }

    private fun getMoreData(isoDep: HidIsoDepTransport, sw1: Byte, sw2: Byte): List<Byte> {
        val result = ArrayList<Byte>()
        var currentSw1 = sw1
        var currentSw2 = sw2

        while (currentSw1 == 0x61.toByte()) {
            val le = if (currentSw2.toInt() == 0) 256 else currentSw2.toInt()

            val getResp = byteArrayOf(
                0x80.toByte(),
                0x20.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                le.toByte()
            )

            val more = isoDep.transceive(getResp)
            if (more.size < 2) break

            val data = more.copyOfRange(0, more.size - 2)
            result.addAll(data.toList())

            currentSw1 = more[more.size - 2]
            currentSw2 = more[more.size - 1]
        }

        return result
    }

    fun getNfcDeviceState(state: WristbandState?): String {
        if (state == null) return "ERROR"

        return when {
            state.active -> "active"
            state.worn -> "worn"
            state.linked -> "linked"
            state.registered -> "registered"
            else -> "unregistered"
        }
    }
}