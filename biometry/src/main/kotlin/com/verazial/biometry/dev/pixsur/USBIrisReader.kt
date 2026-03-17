package com.verazial.biometry.dev.pixsur

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import cn.com.pixsur.pirisbsp.IrisConfiguration
import cn.com.pixsur.pirisbsp.IrisHandler
import cn.com.pixsur.pirisbsp.IrisHandlerListener
import cn.com.pixsur.pirisbsp.IrisManager
import cn.com.pixsur.pirisbsp.IrisParameter
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceUSB
import com.verazial.biometry.lib.util.ImageUtil.toJpeg
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.IRIS
import com.verazial.core.model.BiometricSample.Type.Subtype
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

class USBIrisReader private constructor(
    private val context: Context,
    usbDevice: UsbDevice
) : BiometricDeviceUSB(context, usbDevice) {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.IRIS)

    private var manager: IrisManager? = null
    private var handler: IrisHandler? = null

    override suspend fun initializeUSBDeviceSafe() {
        manager = IrisManager.getInstance(context).notNullOrCommError()

        handler = manager?.run {
            createHandler(usableDevices.firstOrNull().notNullOrCommError(),0)
        }.notNullOrCommError()
            .apply {
                setIRLedMode(2)
            }
    }

    override suspend fun getMaxSamplesSafe(): Int = 2

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = callbackFlow {
        val handler = handler.notNullOrCommError()

        val eyeSubType = when {
            targetSamples.size > 1 -> IrisConfiguration.BioSPI_EYE_BOTH
            targetSamples.contains(IRIS.RIGHT_IRIS) -> IrisConfiguration.BioSPI_EYE_RIGHT
            targetSamples.contains(IRIS.LEFT_IRIS) -> IrisConfiguration.BioSPI_EYE_LEFT
            targetSamples.contains(IRIS.UNKNOWN) -> IrisConfiguration.BioSPI_EYE_EITHER
            else -> throw IllegalArgumentException("Unknown target samples: $targetSamples")
        }
        val needsRight =
            eyeSubType == IrisConfiguration.BioSPI_EYE_BOTH || eyeSubType == IrisConfiguration.BioSPI_EYE_RIGHT
        val needsLeft =
            eyeSubType == IrisConfiguration.BioSPI_EYE_BOTH || eyeSubType == IrisConfiguration.BioSPI_EYE_LEFT

        var lastDistanceStatus = BiometricCapture.DistanceStatus.UNKNOWN
        var lastLeftEye: ByteArray? = null
        var lastRightEye: ByteArray? = null

        val callback: IrisHandlerListener = object : IrisHandlerListener {
            //<editor-fold desc="not needed overrides">
            override fun onEnrollNotify(
                p0: Int,
                p1: Int,
                p2: Int,
                p3: Int,
                p4: Int,
                p5: ByteArray?,
                p6: ByteArray?,
                p7: ByteArray?,
                p8: ByteArray?
            ) = Unit

            override fun onIdentifyNotify(
                p0: Int,
                p1: Int,
                p2: Int,
                p3: Int,
                p4: Int,
                p5: ByteArray?,
                p6: ByteArray?
            ) = Unit

            override fun onCaptureNotify(
                p0: Int,
                p1: Int,
                p2: Int,
                p3: ByteArray?,
                p4: ByteArray?
            ) = Unit

            override fun onIdentifyCaptureNotify(
                p0: Int,
                p1: Int,
                p2: Int,
                p3: ByteArray?,
                p4: ByteArray?,
                p5: ByteArray?,
                p6: ByteArray?
            ) = Unit
            //</editor-fold>

            override fun onLiveImage(
                p0: Int, // eye
                p1: Int, // width
                p2: Int, // height
                p3: ByteBuffer? // image
            ) {
                if (!enablePreviews) return
                if (p3 == null) return
                val capturedSubtype = when (p0) {
                    IrisConfiguration.BioSPI_EYE_LEFT -> IRIS.LEFT_IRIS
                    IrisConfiguration.BioSPI_EYE_RIGHT -> IRIS.RIGHT_IRIS
                    else -> return
                }

                val bitmap = Bitmap.createBitmap(p1, p2, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(p3)
                val jpeg = bitmap.toJpeg()

                val leftEyeImage = jpeg
                    .takeIf { capturedSubtype == IRIS.LEFT_IRIS }
                    .let { if (needsLeft) it ?: lastLeftEye else it }
                    .also { lastLeftEye = it }
                if (needsLeft && leftEyeImage == null) return

                val rightEyeImage = jpeg
                    .takeIf { capturedSubtype == IRIS.RIGHT_IRIS }
                    .let { if (needsRight) it ?: lastRightEye else it }
                    .also { lastRightEye = it }
                if (needsRight && rightEyeImage == null) return

                val samples = CaptureResult(
                    status = p0,
                    leftImage = leftEyeImage,
                    rightImage = rightEyeImage
                ).samples
                trySend(
                    BiometricCapture(
                        distanceStatus = lastDistanceStatus,
                        samples = samples
                    )
                )
            }

            override fun onCompressedCaptureNotify(
                p0: Int,  // status
                p1: Int,  // left eye quality
                p2: Int,  // right eye quality
                p3: ByteArray?,  // left eye image
                p4: ByteArray?  // right eye image
            ) {
                val samples = CaptureResult(
                    status = p0,
                    leftImage = p3,
                    rightImage = p4
                ).samples
                trySend(
                    BiometricCapture(
                        distanceStatus = lastDistanceStatus,
                        samples = samples
                    )
                )
                close()
            }

            override fun onStatusNotify(type: Int, value: Int) {
                if (type != IrisConfiguration.BioSPI_DEVICE_EVENT_RANGE)
                    return
                lastDistanceStatus = value.toDistanceStatus()
            }
        }
        handler.setListener(callback)
        val params = IrisParameter()
        params.eyes = eyeSubType
        params.timeout = 30_000
        params.quality = IrisConfiguration.BioSPI_PARAMS_QUALITY_IDENTIFY
        params.intent = IrisConfiguration.BioSPI_PARAMS_EYE_EXPO_IDENTIFY
        handler.compressedCapture(
            params,
            /*eyeSubType,
            IrisConfiguration.BioSPI_PARAMS_QUALITY_IDENTIFY,
            IrisConfiguration.BioSPI_PARAMS_EYE_EXPO_IDENTIFY,
            30_000, */ // Big enough to not timeout and let the coroutine be cancelled if needed
            95
        )
        awaitClose { }
    }

    override suspend fun stopReadSafe() {
        handler?.cancel(0)  // Int param is not used
    }

    override suspend fun closeSafe() {
        manager?.release()
        manager = null
        handler = null
    }

    override suspend fun stillAliveSafe(): Boolean =
        handler?.isConnected == true

    internal companion object : DeviceProvider(Dispatchers.Main) {
        private const val VID = 4660
        private const val PID = 257

        override suspend fun DeviceProviderScope.getManageableDevice(): BiometricDevice? {
            if (deviceCandidate !is IDeviceManager.DeviceCandidate.Usb) return null
            if (deviceCandidate.device.vendorId != VID || deviceCandidate.device.productId != PID) return null

            return USBIrisReader(context, deviceCandidate.device)
        }
    }

    private inner class CaptureResult(
        status: Int,
        leftImage: ByteArray?,
        rightImage: ByteArray?
    ) {
        val samples: List<BiometricSample>

        init {
            check(
                status in listOf(
                    IrisConfiguration.BioSPI_EYE_LEFT,
                    IrisConfiguration.BioSPI_EYE_RIGHT,
                    IrisConfiguration.BioSPI_EYE_BOTH
                )
            )

            samples = buildList {
                leftImage?.run {
                    BiometricSample(
                        contents = base64String,
                        type = IRIS,
                        subtype = IRIS.LEFT_IRIS,
                        format = IRIS.IMAGE,
                        quality = -1
                    ).let(::add)
                }
                rightImage?.run {
                    BiometricSample(
                        contents = base64String,
                        type = IRIS,
                        subtype = IRIS.RIGHT_IRIS,
                        format = IRIS.IMAGE,
                        quality = -1
                    ).let(::add)
                }
            }.also { check(it.isNotEmpty()) }
        }
    }

    private fun Int.toDistanceStatus() = when (this) {
        IrisConfiguration.BioSPI_USER_RANGE_NEAR -> BiometricCapture.DistanceStatus.TOO_CLOSE

        IrisConfiguration.BioSPI_USER_RANGE_OK -> BiometricCapture.DistanceStatus.OK

        IrisConfiguration.BioSPI_USER_RANGE_FAR,
        IrisConfiguration.BioSPI_USER_RANGE_TOOFAR -> BiometricCapture.DistanceStatus.TOO_FAR

        else -> BiometricCapture.DistanceStatus.UNKNOWN
    }
}