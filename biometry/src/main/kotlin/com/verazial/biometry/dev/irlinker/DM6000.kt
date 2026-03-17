package com.verazial.biometry.dev.irlinker

import android.content.Context
import android.os.Build
import android.util.Log
import com.superred.irisalgo.AlgManager
import com.superred.irisalgo.DistanceCheckModel
import com.superred.irisalgo.IIrisCaptureCallback
import com.superred.irisalgo.IrisCameraManager
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.Subtype
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class DM6000(
    private val context: Context
) : BiometricDeviceBase() {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.IRIS)
    override val id: String get() = Build.DEVICE
    override val name: String get() = Build.MODEL

    private var manager: IrisCameraManager? = null
    private val licenseDirPath =
        context.getExternalFilesDir(null)!!.absolutePath + "/eyeTracking/"
    private val hasLicense: Boolean
        get() = File(licenseDirPath, "license.lic").exists()
    private val emptySamples = listOf(
        BiometricSample(
            type = BiometricSample.Type.IRIS,
            subtype = BiometricSample.Type.IRIS.LEFT_IRIS,
            format = BiometricSample.Type.IRIS.IMAGE,
            quality = 0,
            contents = ""
        ),
        BiometricSample(
            type = BiometricSample.Type.IRIS,
            subtype = BiometricSample.Type.IRIS.RIGHT_IRIS,
            format = BiometricSample.Type.IRIS.IMAGE,
            quality = 0,
            contents = ""
        )
    )

    override suspend fun initializeSafe() {
        if (!hasLicense) error("No license found")
        val resultCode = AlgManager.getInstance().initAlg(licenseDirPath)
        check(resultCode == 0) {
            Log.e("DM6000", "Failed to initialize DM6000 iris algorithm, error code: $resultCode")
            "Failed to initialize DM6000"
        }

        manager = IrisCameraManager.getInstance().apply {
            init(context)
        }
    }

    override suspend fun getMaxSamplesSafe(): Int = 2

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {
        val manager = manager.notNullOrCommError()

        val capturesFlow = callbackFlow {
            val callback = object : IIrisCaptureCallback {
                override fun onSuccess(p0: Int, p1: Int, p2: ByteArray?, p3: ByteArray?) {
                    trySend(CaptureResult.Sample(p0, p1, p2!!))
                }

                override fun onTimeout() =
                    cancel("Read Timeout", ReadingTimeout)

                override fun onDistance(p0: Int, p1: Int) {
                    trySend(CaptureResult.Distance(p1))
                }

                override fun onError(p0: Int, p1: String?) =
                    cancel("Read Error", DeviceCommunicationError("DM6000 error ($p0): $p1"))

                override fun onAlive() {
                    Log.d("DM6000", "Alive")
                }
            }

            manager.run {
                setCaptureThreshold(30)
                setCaptureTimeout(1_000) // 1000 seconds timeout, so it will never timeout by itself, and will be managed by the coroutine
//                setDetectAlive(false)
                setOpenWhiteLight(false)
                setCaptureCallback(callback)
                startCapture()
            }
            awaitClose { manager.stop() }
        }

        var bestLeftCapture: CaptureResult.Sample? = null
        var bestRightCapture: CaptureResult.Sample? = null

        emit(
            BiometricCapture(
                distanceStatus = BiometricCapture.DistanceStatus.UNKNOWN,
                samples = emptySamples
            )
        )

        runCatching {
            capturesFlow
                .flowOn(coroutineContext)
                .collect { captureResult ->
                    if (captureResult is CaptureResult.Distance) {
                        Log.d("DM6000", "Distance: ${captureResult.distance}")
                        captureResult.toBiometricCapture()?.let { emit(it) }
                        return@collect
                    }
                    val capture = captureResult as CaptureResult.Sample
                    Log.d("DM6000", "Capture: ${capture.eye} - ${capture.quality}")
                    when (capture.eye) {
                        BiometricSample.Type.IRIS.LEFT_IRIS -> {
                            if ((bestLeftCapture?.quality ?: 0) < capture.quality) bestLeftCapture =
                                capture
                        }

                        BiometricSample.Type.IRIS.RIGHT_IRIS -> {
                            if ((bestRightCapture?.quality
                                    ?: 0) < capture.quality
                            ) bestRightCapture =
                                capture
                        }

                        else -> error("Invalid eye value: ${capture.eye}")
                    }

                    val missingRightIris =
                        BiometricSample.Type.IRIS.RIGHT_IRIS in targetSamples && bestRightCapture == null
                    val missingLeftIris =
                        BiometricSample.Type.IRIS.LEFT_IRIS in targetSamples && bestLeftCapture == null
                    val enoughSamples = listOfNotNull(
                        bestLeftCapture,
                        bestRightCapture
                    ).size >= targetSamples.size

                    if (enoughSamples && !missingLeftIris && !missingRightIris)
                        throw CancellationException()
                }
        }.onFailure {
            if (it !is CancellationException) throw it
        }

        if (targetSamples == listOf(BiometricSample.Type.IRIS.RIGHT_IRIS))
            bestLeftCapture = null
        if (targetSamples == listOf(BiometricSample.Type.IRIS.LEFT_IRIS))
            bestRightCapture = null
        if (targetSamples.size == 1 && bestLeftCapture != null && bestRightCapture != null)
            bestLeftCapture = null

        val samples = listOfNotNull(
            bestLeftCapture?.let { capture ->
                BiometricSample(
                    type = BiometricSample.Type.IRIS,
                    subtype = BiometricSample.Type.IRIS.LEFT_IRIS,
                    format = BiometricSample.Type.IRIS.IMAGE,
                    quality = capture.quality,
                    contents = capture.data.base64String
                )
            },
            bestRightCapture?.let { capture ->
                BiometricSample(
                    type = BiometricSample.Type.IRIS,
                    subtype = BiometricSample.Type.IRIS.RIGHT_IRIS,
                    format = BiometricSample.Type.IRIS.IMAGE,
                    quality = capture.quality,
                    contents = capture.data.base64String
                )
            }
        )
        emit(BiometricCapture(samples = samples))
    }

    override suspend fun stopReadSafe() {
        manager?.runCatching(IrisCameraManager::stop)
    }

    override suspend fun closeSafe() {
        manager = null
    }

    override suspend fun stillAliveSafe(): Boolean =
        manager != null

    private sealed interface CaptureResult {
        class Distance(val distance: Int) : CaptureResult

        class Sample(
            eye: Int,
            val quality: Int,
            val data: ByteArray
        ) : CaptureResult {
            val eye = when (eye) {
                0 -> BiometricSample.Type.IRIS.LEFT_IRIS
                1 -> BiometricSample.Type.IRIS.RIGHT_IRIS
                else -> error("Invalid eye value: $eye")
            }
        }
    }

    private fun CaptureResult.Distance.toBiometricCapture(): BiometricCapture? {
        return BiometricCapture(
            distanceStatus = when (distance) {
                DistanceCheckModel.DISTANCE_TOO_CLOSE -> BiometricCapture.DistanceStatus.TOO_CLOSE
                DistanceCheckModel.DISTANCE_TOO_FAR -> BiometricCapture.DistanceStatus.TOO_FAR
                DistanceCheckModel.DISTANCE_PROPER -> BiometricCapture.DistanceStatus.OK
                else -> return null
            },
            samples = emptySamples
        )
    }

    internal companion object : DeviceProvider(Dispatchers.IO) {
        private const val DEVICE_ID = "rk3288"

        override suspend fun IDeviceManager.StaticDeviceProviderScope.getStaticManagedDevices(): List<BiometricDevice> {
            val deviceID = Build.DEVICE

            if (deviceID != DEVICE_ID) return emptyList()

            return listOf(DM6000(context))
        }
    }
}