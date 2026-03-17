/*package com.verazial.biometry.dev.pixsur_gm50

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.Camera
import android.util.Log
import cn.com.pixsur.gmfaceiris.EnrollConfig
import cn.com.pixsur.gmfaceiris.EnrollHandler
import cn.com.pixsur.gmfaceiris.EnrollListener
import cn.com.pixsur.gmfaceiris.type.DistanceType
import cn.com.pixsur.gmfaceiris.utils.GmSdkMemory
import com.sensetime.senseid.sdk.face.Quality
import com.sensetime.senseid.sdk.face.Target
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricCapture.DistanceStatus
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.IRIS
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap
import com.verazial.core.error.DeviceCommunicationError
import java.util.concurrent.atomic.AtomicBoolean

class Gm50IrisReader(context: Context) : Gm50BaseReader(context) {

    override val biometricTechnologies = setOf(BiometricTechnology.IRIS)
    override val name = "gm50_iris"
    override val id = "gm50_iris"

    override suspend fun getMaxSamplesSafe(): Int = 2

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = callbackFlow {

        val handler: EnrollHandler = requireHandler()

        val config = EnrollConfig().apply {
            haveMotor = true
            irisShowEnroll = true
            enableMotorEnroll = true
            antiSpoofing = true
            antiSpoofingStrict = true
            disableGrey = false
            enableGlass = true
            enableBeauty = false
            motion = 20f
            autoControlEnrollLed = true
            generateEnrollFaceTemplate = false
        }

        handler.setConfig(config)
        handler.updateMotorStatus(haveMotor = true, enableMotorEnroll = true)


        val listener = buildListener(
            enablePreviews = enablePreviews,
            flow = this@callbackFlow,
            onIrisReady = {
                handler.captureIrisImage(
                    cmdType = 1,
                    eye = 3,
                    quality = 50,
                    eyeExpo = 50,
                    timeOut = 100_000,
                )
            }
        )

        val activity = (binder as? PixsurGm50ViewBinder)?.activity ?: context

        handler.setEnrollListener(listener)
        handler.initFaceAndIris(activity)

        awaitClose {
            handler.stop()
            handler.setEnrollListener(null)
        }
    }

    private fun buildListener(
        enablePreviews: Boolean,
        flow: ProducerScope<BiometricCapture>,
        onIrisReady: () -> Unit
    ) = object : EnrollListener {
        private var lastDistance: DistanceStatus = DistanceStatus.UNKNOWN
        private val finalEmitted = AtomicBoolean(false)

        private fun mapDistance(value: Int): DistanceStatus =
            when {
                value <= DistanceType.NONE -> DistanceStatus.UNKNOWN
                value < DistanceType.CLOSE -> DistanceStatus.TOO_CLOSE
                value < DistanceType.OK -> DistanceStatus.OK
                else -> DistanceStatus.TOO_FAR
            }

        private fun emitDistance(value: Int) {
            val mapped = mapDistance(value)
            if (mapped != lastDistance) {
                lastDistance = mapped
                flow.trySend(BiometricCapture(mapped, emptyList()))
            }
        }

        override fun onDistanceNotify(isFaceDistance: Boolean, value: Int) {
            emitDistance(value)
        }

        override fun onRadarDistanceListener(distanceInCM: Int, distanceAverage: Int) {
            Log.v(
                "GM50-IRIS READER",
                "Radar distance: current=$distanceInCM cm, average=$distanceAverage cm"
            )
        }

        override fun onProximityDistanceListener(value: Long) {
            Log.v("GM50-IRIS READER", "Proximity distance: $value")
        }

        override fun onIrisDeviceReady(isReady: Boolean, irisVersion: String, boxId: String) {
            Log.d(TAG, "onIrisDeviceReady: isReady=$isReady, version=$irisVersion, boxId=$boxId")
            if (isReady) {
                onIrisReady()
            } else {
                if (!finalEmitted.getAndSet(true)) {
                    flow.close(DeviceCommunicationError("Iris device failed to initialize"))
                }
            }
        }

        override fun onIrisLiveImage(
            eye: Int,
            w: Int,
            h: Int,
            rawFrame: ByteBuffer?
        ) {
            if (finalEmitted.get() || !enablePreviews || rawFrame == null) return
            rawFrame.rewind()

            val bytes = ByteArray(rawFrame.remaining())

            rawFrame.get(bytes)

            flow.trySend(
                BiometricCapture(
                    lastDistance,
                    listOf(
                        BiometricSample(
                            bytes.base64String,
                            IRIS,
                            IRIS.UNKNOWN,
                            IRIS.IMAGE,
                            -1
                        )
                    )
                )
            )
        }

        override fun onEnrollIrisSuccess(
            result: Int,
            lImageQuality: ByteArray?,
            rImageQuality: ByteArray?,
            lTemplate: ByteArray?,
            rTemplate: ByteArray?,
            lImage: ByteArray?,
            rImage: ByteArray?
        ) {
        }

        override fun onEnrollIrisTimeOut(resString: String) {
            if (!finalEmitted.getAndSet(true)) flow.close()
        }

        override fun onEnrollIrisError() {
            if (!finalEmitted.getAndSet(true)) flow.close()
        }

        override fun onCheckDevice(faceCameraId: Int, irisCameraId: Int?) {}
        override fun onCaptureNotify(
            result: Int,
            lQuality: Int,
            rQuality: Int,
            lImage: ByteArray?,
            rImage: ByteArray?
        ) {
            if (finalEmitted.getAndSet(true)) return

            val samples = mutableListOf<BiometricSample>()

            lImage?.let {
                samples += BiometricSample(
                    convertGrayImageToJpeg(
                        it
                    )?.base64String
                        ?: "",
                    IRIS,
                    IRIS.LEFT_IRIS,
                    IRIS.IMAGE,
                    lQuality
                )
            }

            rImage?.let {
                samples += BiometricSample(
                    convertGrayImageToJpeg(
                        it
                    )?.base64String
                        ?: "",
                    IRIS,
                    IRIS.RIGHT_IRIS,
                    IRIS.IMAGE,
                    rQuality
                )
            }

            flow.trySend(BiometricCapture(lastDistance, samples))
            flow.close()
        }

        override fun onEnrollIrisDup(leftIndex: Int, rightIndex: Int) {}
        override fun onEnrollIrisGlassBeautyNotify(eye: Int, glass: Int, beauty: Int) {}
        override fun onEnrollIrisOpenEyesNotify() {}
        override fun onEnrollIrisUpdateCount(leftCount: Int, rightCount: Int) {}
        override fun onFaceDetect(
            hasFace: Boolean,
            rect: Rect?,
            leftCenterX: Int,
            leftCenterY: Int,
            rightCenterX: Int,
            rightCenterY: Int,
            imageWidth: Int,
            imageHeight: Int
        ) {
        }

        override fun onFaceEnrollSuccess(
            target: Target,
            image: ByteArray?,
            imageWidth: Int,
            imageHeight: Int,
            quality: Float,
            featureCode: ByteArray?,
            code: Int,
            index: Int,
            result: Int
        ) {
        }

        override fun onFacePoint(
            leftCenterX: Int,
            leftCenterY: Int,
            rightCenterX: Int,
            rightCenterY: Int,
            imageWidth: Int,
            imageHeight: Int,
            isFrontFace: Boolean,
            quality: Quality?
        ) {
        }

        override fun onFacePreviewFrame(
            data: ByteArray?,
            width: Int,
            height: Int,
            camera: Camera?
        ) {
        }
    }

    private fun convertGrayImageToJpeg(grayData: ByteArray): ByteArray? {
        return try {
            val bitmap = convertGrayToBitmap(
                grayData
            )
            if (bitmap == null) {
                Log.e(TAG, "Failed to convert gray data to bitmap")
                return null
            }

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            bitmap.recycle()

            val jpegData = outputStream.toByteArray()
            Log.d(TAG, "Converted gray image to JPEG: ${grayData.size} -> ${jpegData.size}")
            jpegData
        } catch (e: Exception) {
            Log.e(TAG, "Error converting gray image to JPEG: ${e.message}", e)
            null
        }
    }

    private fun convertGrayToBitmap(grayData: ByteArray): Bitmap? {
        return try {
            val width = GmSdkMemory.irisImageWidth
            val height = GmSdkMemory.irisImageHeight
            val pixels = width * height

            if (grayData.size != pixels) {
                Log.e(TAG, "Gray data size mismatch: expected $pixels, got ${grayData.size}")
                return null
            }

            val bitmap = createBitmap(width, height)
            val pixelsArray = IntArray(pixels)

            for (i in 0 until pixels) {
                val gray = grayData[i].toInt() and 0xFF
                pixelsArray[i] = (255 shl 24) or (gray shl 16) or (gray shl 8) or gray
            }

            bitmap.setPixels(pixelsArray, 0, width, 0, 0, width, height)
            Log.d(TAG, "Converted Gray to Bitmap: ${grayData.size} -> ${width}x${height}")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Gray to Bitmap: ${e.message}", e)
            null
        }
    }

    companion object {
        const val TAG = "GM50IrisReader"
    }
}*/