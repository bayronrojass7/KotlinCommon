package com.verazial.biometry.dev.iritech

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import com.iritech.iddk.android.HIRICAMM
import com.iritech.iddk.android.Iddk2000Apis
import com.iritech.iddk.android.IddkCaptureMode
import com.iritech.iddk.android.IddkCaptureOperationMode
import com.iritech.iddk.android.IddkCaptureStatus
import com.iritech.iddk.android.IddkCaptureStatus.IDDK_COMPLETE
import com.iritech.iddk.android.IddkDataBuffer
import com.iritech.iddk.android.IddkDeviceConfig
import com.iritech.iddk.android.IddkDeviceInfo
import com.iritech.iddk.android.IddkEyeSubType
import com.iritech.iddk.android.IddkImage
import com.iritech.iddk.android.IddkImageFormat
import com.iritech.iddk.android.IddkImageKind
import com.iritech.iddk.android.IddkInteger
import com.iritech.iddk.android.IddkIsoRevision
import com.iritech.iddk.android.IddkQualityMode
import com.iritech.iddk.android.IddkResult
import com.iritech.iddk.android.IddkResult.IDDK_DEVICE_ALREADY_OPEN
import com.iritech.iddk.android.IddkResult.IDDK_OK
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceUSB
import com.verazial.biometry.lib.util.ImageUtil.toJpeg
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.IRIS
import com.verazial.core.model.BiometricSample.Type.Subtype
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

class IrisReader(
    private val context: Context,
    usbDevice: UsbDevice
) : BiometricDeviceUSB(context, usbDevice) {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.IRIS)

    private var api: Iddk2000Apis? = null
    private var handle: HIRICAMM? = null

    override suspend fun initializeUSBDeviceSafe() {
        api = Iddk2000Apis.getInstance(context)
        handle = HIRICAMM()

        val deviceDescriptions: ArrayList<String> = arrayListOf()
        checkingResult { scanDevices(deviceDescriptions) }

        val okCodes = listOf(IDDK_OK, IDDK_DEVICE_ALREADY_OPEN)
        deviceDescriptions.firstOrNull {
            val openResult = api?.openDevice(it, handle)?.value
            openResult in okCodes
        }.notNullOrCommError()

        val currentConfig = IddkDeviceConfig()
        checkingResult {
            api?.getDeviceConfig(handle.notNullOrCommError(), currentConfig).notNullOrCommError()
        }
        currentConfig.isEnableStream = true
        checkingResult {
            api?.setDeviceConfig(handle.notNullOrCommError(), currentConfig).notNullOrCommError()
        }
    }

    override suspend fun getMaxSamplesSafe(): Int {
        val isBinocular = IddkInteger(-1)
        checkingResult {
            checkNotNull(api).Iddk_IsBinocular(handle, isBinocular)
        }
        check(isBinocular.value in 0..1)
        return if (isBinocular.value == 1) 2 else 1
    }

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {
        val handle = this@IrisReader.handle.notNullOrCommError()

        checkingResult {
            initCamera(handle, null, null)
        }

        val isMonoEye = getMaxSamplesSafe() == 1

        val eyeSubType = when {
            targetSamples.size >= 2 -> IddkEyeSubType.IDDK_BOTH_EYE.also { require(isMonoEye.not()) }
            targetSamples.contains(IRIS.UNKNOWN) || isMonoEye -> IddkEyeSubType.IDDK_UNKNOWN_EYE
            targetSamples.contains(IRIS.RIGHT_IRIS) -> IddkEyeSubType.IDDK_RIGHT_EYE
            targetSamples.contains(IRIS.LEFT_IRIS) -> IddkEyeSubType.IDDK_LEFT_EYE
            else -> error("Unknown eye type: $targetSamples")
        }

        val (sampleFormat, isTemplateFormat) = preferredFormats.bestFormat(
            formatMappings = mapOf(
                IRIS.IMAGE to false,
                IRIS.ISO_IEC_19794_6_2005 to true,
            ),
            defaultFormat = IRIS.IMAGE
        )

        checkingResult {
            startCapture(
                handle,
                IddkCaptureMode(IddkCaptureMode.IDDK_FRAMEBASED),
                10,
                IddkQualityMode(IddkQualityMode.IDDK_QUALITY_NORMAL),
                IddkCaptureOperationMode(IddkCaptureOperationMode.IDDK_AUTO_CAPTURE),
                IddkEyeSubType(eyeSubType),
                true,
                null
            )
        }

        val captureStatus = IddkCaptureStatus()
        coroutineScope {
            while (isActive) {
                if (enablePreviews) {
                    waitForImage(
                        isPreview = true,
                        isTemplateFormat = false,
                        isMonoEye = isMonoEye,
                        targetSamples = targetSamples,
                        eyeSubType = eyeSubType,
                        sampleFormat = IRIS.IMAGE
                    ).let { emit(BiometricCapture(samples = it)) }
                }
                checkingResult { getCaptureStatus(handle, captureStatus) }
                if (captureStatus.value == IDDK_COMPLETE) break
                delay(100)
            }
        }
        if (captureStatus.value != IDDK_COMPLETE) throw ReadingTimeout
        waitForImage(
            false,
            isTemplateFormat,
            isMonoEye,
            targetSamples,
            eyeSubType,
            sampleFormat
        ).let { emit(BiometricCapture(samples = it)) }
    }

    private fun waitForImage(
        isPreview: Boolean,
        isTemplateFormat: Boolean,
        isMonoEye: Boolean,
        targetSamples: List<Subtype>,
        eyeSubType: Int,
        sampleFormat: BiometricSample.Type.Format
    ): List<BiometricSample> {
        var rightEyeByteBuffer: ByteArray? = null
        var leftEyeByteBuffer: ByteArray? = null

        if (isTemplateFormat) {
            listOf(
                IddkEyeSubType.IDDK_RIGHT_EYE,
                IddkEyeSubType.IDDK_LEFT_EYE
            ).forEach { capturedEyeSubType ->
                val dataBuffer = IddkDataBuffer()
                checkingResult(
                    IDDK_OK,
                    IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED,
                    IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED
                ) {
                    getResultIsoImage(
                        handle,
                        IddkIsoRevision(IddkIsoRevision.IDDK_IISO_2005),
                        IddkImageFormat(IddkImageFormat.IDDK_IFORMAT_MONO_RAW),
                        IddkImageKind(IddkImageKind.IDDK_IKIND_K1),
                        0,
                        IddkEyeSubType(capturedEyeSubType),
                        dataBuffer
                    )
                }
                val eyeByteBuffer = dataBuffer.data
                when (capturedEyeSubType) {
                    IddkEyeSubType.IDDK_RIGHT_EYE ->
                        rightEyeByteBuffer = eyeByteBuffer.takeIf(ByteArray::isNotEmpty)

                    IddkEyeSubType.IDDK_LEFT_EYE ->
                        leftEyeByteBuffer = eyeByteBuffer.takeIf(ByteArray::isNotEmpty)

                    else -> error("Unknown eye type: $capturedEyeSubType")
                }
            }
        } else {
            val monoBestImages: ArrayList<IddkImage> = arrayListOf()
            checkingResult(
                IDDK_OK,
                IddkResult.IDDK_SE_LEFT_FRAME_UNQUALIFIED,
                IddkResult.IDDK_SE_RIGHT_FRAME_UNQUALIFIED
            ) {
                if (isPreview) getStreamImage(
                    handle,
                    monoBestImages,
                    IddkInteger(2),
                    IddkCaptureStatus()
                )
                else getResultImage(
                    handle,
                    IddkImageKind(IddkImageKind.IDDK_IKIND_K1),
                    IddkImageFormat(IddkImageFormat.IDDK_IFORMAT_MONO_RAW),
                    0,
                    monoBestImages,
                    IddkInteger()
                )
            }
            rightEyeByteBuffer =
                monoBestImages.getOrNull(0)?.takeIf { it.imageData != null }?.toJpeg()
            leftEyeByteBuffer =
                monoBestImages.getOrNull(1)?.takeIf { it.imageData != null }?.toJpeg()
        }

        if (isMonoEye && targetSamples.single() == IRIS.LEFT_IRIS) {
            leftEyeByteBuffer = rightEyeByteBuffer
            rightEyeByteBuffer = null
        }

        if (eyeSubType == IddkEyeSubType.IDDK_UNKNOWN_EYE
            && leftEyeByteBuffer != null
            && rightEyeByteBuffer != null
        ) leftEyeByteBuffer = null

        if (isPreview && targetSamples.singleOrNull() == IRIS.LEFT_IRIS) {
            leftEyeByteBuffer = rightEyeByteBuffer
            rightEyeByteBuffer = null
        }

        return buildList {
            leftEyeByteBuffer?.run {
                BiometricSample(
                    contents = base64String,
                    type = IRIS,
                    subtype = IRIS.LEFT_IRIS,
                    format = sampleFormat,
                    quality = -1
                ).let(::add)
            }
            rightEyeByteBuffer?.run {
                BiometricSample(
                    contents = base64String,
                    type = IRIS,
                    subtype = IRIS.RIGHT_IRIS,
                    format = sampleFormat,
                    quality = -1
                ).let(::add)
            }
        }.also { check(it.isNotEmpty()) }
    }

    override suspend fun stopReadSafe() {
        api?.deinitCamera(handle.notNullOrCommError())
    }

    override suspend fun closeSafe() {
        api?.closeDevice(handle)
        api = null
        handle = null
    }

    override suspend fun stillAliveSafe(): Boolean =
        runCatching {
            checkingResult {
                getDeviceInfo(handle, IddkDeviceInfo())
            }
        }.isSuccess

    private inline fun checkingResult(
        vararg validCodes: Int = intArrayOf(IDDK_OK),
        block: Iddk2000Apis.() -> IddkResult
    ): IddkResult = runCatching {
        val result = api?.block().notNullOrCommError()
        if (result.value !in validCodes) return@runCatching null
        result
    }.getOrNull().notNullOrCommError()

    internal companion object : DeviceProvider(Dispatchers.IO) {
        private const val VID = 8035
        private val PIDS = listOf(61441, 61445, 61442, 61460, 61461)

        override suspend fun DeviceProviderScope.getManageableDevice(): BiometricDevice? {
            if (deviceCandidate !is IDeviceManager.DeviceCandidate.Usb) return null
            if (deviceCandidate.device.vendorId != VID || deviceCandidate.device.productId !in PIDS) return null

            return IrisReader(context, deviceCandidate.device)
        }
    }

    private fun IddkImage.toJpeg(): ByteArray {
        val bits = ByteArray(imageData.size * 4) // That's where the RGBA array goes.
        var j = 0
        while (j < imageData.size) {
            bits[j * 4] = imageData[j]
            bits[j * 4 + 1] = imageData[j]
            bits[j * 4 + 2] = imageData[j]
            bits[j * 4 + 3] = -1 // That's the alpha
            j++
        }

        // Now put these nice RGBA pixels into a Bitmap object
        val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bits))
        return bitmap.toJpeg()
    }
}