package com.verazial.biometry.dev.generic

import android.content.Context
import android.content.Intent
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
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
import com.verazial.core.interfaces.NfcSessionRequester
import com.verazial.core.model.NfcDeviceData
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import com.verazial.core.model.WristbandState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NfcDevice(
    private val context: Context,
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

    private var tagChannel = Channel<Tag>(capacity = 1)

    /**
     * Called by the invisible Activity when a Tag is discovered.
     */
    fun onTagDiscovered(tag: Tag) {
        tagChannel.trySend(tag)
    }

    override suspend fun initializeSafe() {
        initialized = true
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
        try {
            tagChannel = Channel(capacity = 1)

            // Start the transparent Activity
            NfcSessionRequester.instance.requestNfcSession()

            while (reading) {

                // 1. stopReadSafe() check
                if (tagChannel.isClosedForReceive) {
                    throw CancellationException("NFC read cancelled")
                }

                // 2. Wait for a tag (short wait, loop continues)
                val tag = withTimeoutOrNull(300) { tagChannel.receive() }
                    ?: continue

                // 3. Must be IsoDep
                val isoDep = IsoDep.get(tag) ?: continue

                try {
                    isoDep.connect()
                    val infoJson = sendJsonApdu(isoDep, "info", null)
                    if (infoJson == null) {
                        isoDep.close()
                        continue
                    }

                    val credentialsJson = sendJsonApdu(isoDep, "credentials", null)
                    if (credentialsJson == null) {
                        isoDep.close()
                        continue
                    }

                    val biodataJson = sendJsonApdu(isoDep, "biodata", null)
                    if (biodataJson == null) {
                        isoDep.close()
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
                    isoDep.close()
                    break
                } catch (e: Exception) {
                    Log.w("NfcDevice", "Error during NFC session: ${e.message}", e)
                    try {
                        isoDep.close()
                    } catch (_: Exception) {
                    }
                    continue
                }
            }
        } finally {
            reading = false
        }
    }

    override suspend fun stopReadSafe() {
        reading = false
        tagChannel.close()
        val intent = Intent("com.verazial.ACTION_CLOSE_NFC")
        context.sendBroadcast(intent)
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
        try {
            tagChannel = Channel(capacity = 1)

            // Start the transparent Activity
            NfcSessionRequester.instance.requestNfcSession()

            while (writing) {

                // 1. stopReadSafe() check
                if (tagChannel.isClosedForReceive) {
                    throw CancellationException("NFC read cancelled")
                }

                // 2. Wait for a tag (short wait, loop continues)
                val tag = withTimeoutOrNull(300) { tagChannel.receive() }
                    ?: continue

                // 3. Must be IsoDep
                val isoDep = IsoDep.get(tag) ?: continue

                try {
                    isoDep.connect()

                    val action = content["action"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("action missing")

                    val infoJson = sendJsonApdu(isoDep, "info", null)
                    if (infoJson == null) {
                        isoDep.close()
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
                        isoDep.close()
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
                    isoDep.close()
                    break
                } catch (e: Exception) {
                    Log.w("NfcDevice", "Error during NFC session: ${e.message}", e)
                    try {
                        isoDep.close()
                    } catch (_: Exception) {
                    }
                    if(e is NoWorn || e is AlreadyActived || e is AlreadyRegistered
                        || e is NoStatusObtained || e is NoValidOption || e is UUIDMismatch) {
                        writing = false
                        throw e
                    }
                    continue
                }
            }
        } finally {
            writing = false
        }
    }

    override suspend fun stopWriteSafe() {
        writing = false
        tagChannel.close()
        val intent = Intent("com.verazial.ACTION_CLOSE_NFC")
        context.sendBroadcast(intent)
    }

    override suspend fun closeSafe() {
        reading = false
        initialized = false
        tagChannel.close()
    }

    override suspend fun stillAliveSafe(): Boolean = initialized

    internal companion object : DeviceProvider(Dispatchers.IO) {
        @Volatile
        private var weakInstance: WeakReference<NfcDevice>? = null

        internal fun getInstanceInternal(): NfcDevice? =
            weakInstance?.get()

        override suspend fun IDeviceManager.StaticDeviceProviderScope.getStaticManagedDevices():
                List<Device> {

            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
                ?: return emptyList()

            return if (adapter.isEnabled) {
                listOf(
                    NfcDevice(
                        context,
                        "INTEGRATED_NFC",
                        "iNFC",
                    ).also {
                        weakInstance = WeakReference(it)
                    }
                )
            } else emptyList()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("-", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun sendJsonApdu(isoDep: IsoDep, action: String, data: String? = null): String? {
        // --- SELECT AID ---
        val aid = hexToBytes("F0010203040506")
        val selectApdu = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
            aid.size.toByte()
        ) + aid

        val selectResp = try {
            isoDep.transceive(selectApdu)
        } catch (e: Exception) {
            Log.w("NfcDevice", "SendJson - SELECT AID transceive failed: ${e.message}", e)
            return null
        }

        if (selectResp.size < 2) {
            Log.w("NfcDevice", "SendJson - SELECT AID response too short")
            return null
        }

        val selSw1 = selectResp[selectResp.size - 2]
        val selSw2 = selectResp[selectResp.size - 1]

        Log.i(
            "NfcDevice",
            "SendJson - SELECT AID SW1SW2: %02X%02X".format(selSw1, selSw2)
        )

        if (selSw1 != 0x90.toByte() || selSw2 != 0x00.toByte()) {
            Log.w(
                "NfcDevice",
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

        Log.i("NfcDevice", "Sending JSON: $json")

        val CHUNK_SIZE = 250

        return if (jsonBytes.size <= CHUNK_SIZE) {
            sendSingleApdu(isoDep, jsonBytes)
        } else {
            sendChunkedApdu(isoDep, jsonBytes, CHUNK_SIZE)
        }
    }

    private fun sendSingleApdu(isoDep: IsoDep, data: ByteArray): String? {
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
            Log.w("NfcDevice", "SendJson - JSON APDU transceive failed: ${e.message}", e)
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
        isoDep: IsoDep,
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
                Log.w("NfcDevice", "Chunk $i transceive failed: ${e.message}", e)
                return null
            }

            if (resp.size < 2) return null

            lastSw1 = resp[resp.size - 2]
            lastSw2 = resp[resp.size - 1]

            fullResponse.addAll(resp.copyOfRange(0, resp.size - 2).toList())

            if (lastSw1 != 0x90.toByte() && lastSw1 != 0x61.toByte()) {
                Log.w("NfcDevice", "Chunk $i failed SW1SW2: %02X%02X".format(lastSw1, lastSw2))
                return null
            }
        }

        // Handle more data after last chunk
        val extra = getMoreData(isoDep, lastSw1, lastSw2)
        fullResponse.addAll(extra)

        return fullResponse.toByteArray().toString(Charsets.UTF_8)
    }

    private fun getMoreData(isoDep: IsoDep, sw1: Byte, sw2: Byte): List<Byte> {
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

object Iso7816NfcDeviceLocator {
    fun get(): NfcDevice? =
        NfcDevice.getInstanceInternal()
}
