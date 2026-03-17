/*package com.verazial.biometry.dev.pixsur_gm50

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.Camera
import cn.com.pixsur.gmfaceiris.EnrollConfig
import cn.com.pixsur.gmfaceiris.EnrollListener
import com.sensetime.senseid.sdk.face.Quality
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricCapture.DistanceStatus
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.FACE
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import androidx.core.graphics.createBitmap
import cn.com.pixsur.gmfaceiris.EnrollHandler
import kotlinx.coroutines.isActive

class Gm50FaceReader(context: Context) : Gm50BaseReader(context) {

    override val biometricTechnologies = setOf(BiometricTechnology.FACIAL)
    override val name = "gm50_face"
    override val id = "gm50_face"

    override suspend fun getMaxSamplesSafe(): Int = 1

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
            enableGlass = false
            enableBeauty = false
            motion = 40f
            autoControlEnrollLed = true
            generateEnrollFaceTemplate = false
        }

        handler.setConfig(config)
        handler.updateMotorStatus(haveMotor = true, enableMotorEnroll = true)

        val listener = buildListener(enablePreviews, this@callbackFlow)
        val activity = (binder as? PixsurGm50ViewBinder)?.activity ?: context

        handler.setEnrollListener(listener)
        handler.initFaceAndIris(activity)

        handler.startFaceEnroll()

        awaitClose {
        }
    }

    private fun buildListener(
        enablePreviews: Boolean,
        flow: ProducerScope<BiometricCapture>
    ) = object : EnrollListener {

        private var lastPreview: ByteArray? = null
        private var lastDistance: DistanceStatus = DistanceStatus.UNKNOWN
        private var finalEmitted = false

        private fun mapDistance(cm: Int): DistanceStatus =
            when {
                cm <= 0 -> DistanceStatus.UNKNOWN
                cm < 40 -> DistanceStatus.TOO_CLOSE
                cm < 100 -> DistanceStatus.OK
                else -> DistanceStatus.TOO_FAR
            }

        private fun emitDistance(cm: Int) {
            val mapped = mapDistance(cm)
            if (mapped != lastDistance) {
                lastDistance = mapped
                flow.trySend(BiometricCapture(mapped, emptyList()))
            }
        }

        override fun onDistanceNotify(isFaceDistance: Boolean, value: Int) {
            emitDistance(value)
        }

        private var frameCounter = 0
        override fun onFacePreviewFrame(
            data: ByteArray?,
            width: Int,
            height: Int,
            camera: Camera?
        ) {

            if (!flow.isActive || finalEmitted || !enablePreviews || data == null) return


            frameCounter++
            if (frameCounter % 3 != 0) return
            val jpegData = convertBGRToJpegManual(data, width, height)
            if (jpegData != null) {
                flow.trySend(
                    BiometricCapture(
                        lastDistance,
                        listOf(
                            BiometricSample(
                                jpegData.base64String,
                                FACE,
                                FACE.UNKNOWN,
                                FACE.IMAGE,
                                -1
                            )
                        )
                    )
                )
            }
        }

        override fun onIrisDeviceReady(isReady: Boolean, irisVersion: String, boxId: String) {}

        override fun onFaceEnrollSuccess(
            target: com.sensetime.senseid.sdk.face.Target,
            image: ByteArray?,
            imageWidth: Int,
            imageHeight: Int,
            quality: Float,
            featureCode: ByteArray?,
            code: Int,
            index: Int,
            result: Int
        ) {
            if (image != null) {
                flow.trySend(
                    BiometricCapture(
                        lastDistance,
                        listOf(
                            BiometricSample(
                                image.base64String,
                                FACE,
                                FACE.UNKNOWN,
                                FACE.IMAGE,
                                quality.toInt()
                            )
                        )
                    )
                )
            }
            finalEmitted = true
            flow.close()
        }

        // Unused callbacks
        override fun onCheckDevice(faceCameraId: Int, irisCameraId: Int?) {}
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

        override fun onEnrollIrisTimeOut(resString: String) {}
        override fun onEnrollIrisError() {}
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

        override fun onIrisLiveImage(eye: Int, w: Int, h: Int, rawFrame: java.nio.ByteBuffer?) {}
        override fun onCaptureNotify(
            result: Int,
            lQuality: Int,
            rQuality: Int,
            lImage: ByteArray?,
            rImage: ByteArray?
        ) {
        }

        override fun onRadarDistanceListener(distanceInCM: Int, distanceAverage: Int) {
            emitDistance(distanceAverage)
        }

        override fun onProximityDistanceListener(value: Long) {
            emitDistance(value.toInt())
        }
    }

    private fun convertBGRToJpegManual(bgrData: ByteArray, width: Int, height: Int): ByteArray? {
        return try {
            val pixels = width * height
            if (bgrData.size != pixels * 3) return null

            val bitmap = createBitmap(width, height)
            val pixelsArray = IntArray(pixels)

            var j = 0
            for (i in 0 until pixels) {
                val r = bgrData[j++].toInt() and 0xFF
                val g = bgrData[j++].toInt() and 0xFF
                val b = bgrData[j++].toInt() and 0xFF

                pixelsArray[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }

            bitmap.setPixels(pixelsArray, 0, width, 0, 0, width, height)

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            bitmap.recycle()

            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}*/