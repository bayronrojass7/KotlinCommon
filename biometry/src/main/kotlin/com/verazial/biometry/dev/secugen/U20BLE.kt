package com.verazial.biometry.dev.secugen

import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceBLE
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive


class U20BLE(advertisement: Advertisement) : BiometricDeviceBLE(advertisement, 301) {

    override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.FINGERPRINT)

    private val serviceUUID = "0000fda0-0000-1000-8000-00805f9b34fb"
    private val characteristicWriteUUID = "00002bb2-0000-1000-8000-00805f9b34fb"
    private val characteristicReadUUID = "00002bb1-0000-1000-8000-00805f9b34fb"

    private var isReading = false
    private lateinit var characteristicWrite: Characteristic
    private lateinit var characteristicRead: Characteristic


    override suspend fun initializeBLEDeviceSafe() {
        characteristicWrite = characteristicOf(
            serviceUUID,
            characteristicWriteUUID
        )
        characteristicRead = characteristicOf(
            serviceUUID,
            characteristicReadUUID
        )

        // SI_TEMPLATE_TYPE -> Set template type to ANSI378, because with ISO19794-2 (sensor's default) the generated template will not be identified by our server (why?)
        write(0, 32, 24, 0, 0, 1, 0, 0, 0, 0, 0, 0)
        read().throwIfError()
        // SI_SENSOR_TIME_OUT -> Set timeout to max supported value (20 seconds)
        write(0, 32, 23, 0, 20, 0, 0, 0, 0, 0, 0, 0)
        read().throwIfError()
    }

    override suspend fun getMaxSamplesSafe(): Int = 1

    override suspend fun performReadSafe(
        preferredFormats: List<Type.Format>,
        targetSamples: List<Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {
        runCatching {
            check(isReading.not()) { "Already reading" }
            isReading = true

            write(0, 64, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)  // CMD_GET_TEMPLATE

            peripheral.observe(characteristicRead).first()

            val metadata = read()
                .throwIfError()
                .map { it.toInt() }
            val dataSize = metadata.let {
                (it[6] or (it[7] shl 8)) or ((it[8] or (it[9] shl 8)) shl 16)
            }

            val rawTemplate: MutableList<Byte> = mutableListOf()

            coroutineScope {
                var dataRead = 0
                while (isActive && dataRead < dataSize) {
                    val data = read()
                    data.forEach(rawTemplate::add)
                    dataRead += (data.takeIf { it.isNotEmpty() }?.size) ?: break
                }
            }

            val template = rawTemplate.toByteArray()
                .base64String
                .ifEmpty { null }
                .notNullOrReadingTimeout()

            val subtype = targetSamples.first()
            BiometricSample(
                contents = template,
                type = Type.FINGER,
                subtype = subtype,
                format = Type.FINGER.ANSI_INCITS_378_2004,
                quality = 0
            ).let(::listOf)
        }.also {
            isReading = false
        }.getOrThrow()
            .let { emit(BiometricCapture(samples = it)) }
    }

    override suspend fun stopReadSafe() = Unit  // Can not stop reading from this device


    @Suppress("SameParameterValue")
    private suspend fun write(vararg bytes: Byte) =
        peripheral.write(characteristicWrite, bytes.withCheckSum(), WriteType.WithResponse)

    private suspend fun read(): ByteArray =
        peripheral.read(characteristicRead)

    private fun ByteArray.throwIfError(): ByteArray {
        val errorByte = get(10)
        if (errorByte == 8.toByte()) throw ReadingTimeout
        if (errorByte != 0.toByte()) throw DeviceCommunicationError(
            message = "Error reading from device: $errorByte"
        )
        return this
    }

    private fun ByteArray.withCheckSum(): ByteArray =
        apply { set(lastIndex, sum().toByte()) }


    internal companion object : DeviceProvider(Dispatchers.IO) {
        private const val SECUGEN_MAC_PREFIX = "CC:35:5A"

        override suspend fun DeviceProviderScope.getManageableDevice(): BiometricDevice? {
            if (deviceCandidate !is IDeviceManager.DeviceCandidate.Ble) return null
            if (!deviceCandidate.advertisement.address.startsWith(SECUGEN_MAC_PREFIX)) return null

            return U20BLE(deviceCandidate.advertisement)
        }
    }
}