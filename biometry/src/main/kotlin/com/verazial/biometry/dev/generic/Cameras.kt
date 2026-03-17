package com.verazial.biometry.dev.generic

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.biometry.lib.util.ImageUtil.rotate
import com.verazial.biometry.lib.util.ImageUtil.toJpeg
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
sealed class Camera(
    private val context: Context,
    final override val name: String,
    final override val id: String
) : BiometricDeviceBase() {
    final override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.FACIAL)
    final override val coroutineContext = Dispatchers.Main

    protected abstract val cameraSelector: CameraSelector
    protected abstract val mirroredH: Boolean

    private var lifecycleOwner: LifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = lifecycleRegistry
    }
    private var lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(lifecycleOwner).apply { currentState = CREATED }
    private var cameraProvider: ProcessCameraProvider? = null


    final override suspend fun initializeSafe() {
        cameraProvider = suspendCancellableCoroutine { continuation ->
            ProcessCameraProvider.getInstance(context).let { providerFuture ->
                providerFuture.addListener({
                    @Suppress("BlockingMethodInNonBlockingContext")
                    continuation.resume(providerFuture.get())
                }, ContextCompat.getMainExecutor(context))
                continuation.invokeOnCancellation { providerFuture.cancel(false) }
            }
        }

        changeLifecycleState(STARTED)
    }

    final override suspend fun getMaxSamplesSafe(): Int = 1

    final override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {
        check(lifecycleRegistry.currentState.isAtLeast(CREATED)) { "Camera is not initialized" }
        val cameraProvider = checkNotNull(cameraProvider) { "CameraProvider is null" }

        cameraProvider.unbindAll()

        changeLifecycleState(RESUMED)

        cameraProvider.bindPreviewUseCase { imageProxy ->
            val contents = imageProxy
                .toBitmap()
                .rotate(imageProxy.imageInfo.rotationDegrees)
                .toJpeg()
                .base64String
            emit(
                BiometricCapture(
                    samples = listOf(
                        BiometricSample(
                            type = BiometricSample.Type.FACE,
                            subtype = BiometricSample.Type.FACE.FRONTAL_FACE,
                            format = BiometricSample.Type.FACE.IMAGE,
                            quality = -1,
                            contents = contents
                        )
                    )
                )
            )
        }
    }.run { if (enablePreviews) this else take(1) }

    override suspend fun stopReadSafe() {
        cameraProvider?.unbindAll()
    }

    private suspend fun ProcessCameraProvider.bindPreviewUseCase(
        onNewImage: suspend (ImageProxy) -> Unit
    ) {
        val analysisUseCase = ImageAnalysis.Builder()
            .setTargetRotation((ContextCompat.getDisplayOrDefault(context).rotation + extraDegreesForDevice).asSurfaceRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    imageProxy.image
                }
            }
        val imageProxyFlow = callbackFlow {
            analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                trySend(imageProxy)
            }
            awaitClose { analysisUseCase.clearAnalyzer() }
        }
        bindToLifecycle(lifecycleOwner, cameraSelector, analysisUseCase)
        imageProxyFlow.collect {
            onNewImage(it)
            it.close()
        }
    }

    private fun changeLifecycleState(state: Lifecycle.State) {
        lifecycleRegistry.currentState = state
    }

    final override suspend fun closeSafe() {
        runCatching { changeLifecycleState(CREATED) } // may be already garbage collected, for some reason (e.g. activity destroyed)
    }

    final override suspend fun stillAliveSafe(): Boolean =
        lifecycleRegistry.currentState.isAtLeast(STARTED)

    // Obsceno, pero no hay otra.
    private val extraDegreesForDevice: Int
        get() = when (Build.DEVICE) {
            "tb8766p1_64_bsp" -> 90
            "rk3288" -> 90
            else -> 0
        }

    private val Int.asSurfaceRotation: Int
        get() = when (this % 360) {
            0 -> Surface.ROTATION_0
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> throw IllegalArgumentException("Unsupported rotation: $this")
        }

    private val Int.asDegrees: Int
        get() = when (this) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> throw IllegalArgumentException("Unsupported rotation: $this")
        }
}

class BackCamera(
    context: Context
) : Camera(context, "BACK_INTEGRATED_CAMERA", "bic") {
    override val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    override val mirroredH: Boolean = false

    internal companion object : DeviceProvider(Dispatchers.Main) {

        override suspend fun IDeviceManager.StaticDeviceProviderScope.getStaticManagedDevices(): List<BiometricDevice> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager?
                ?: return emptyList()
            val hasBackCamera = manager.cameraIdList.any { id ->
                try {
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
                } catch (e: IllegalArgumentException) {
                    Log.w("Cameras", "Error detecting back camera: ${e.message}", e)
                    false
                }
            }
            return if (hasBackCamera) listOf(BackCamera(context)) else emptyList()
        }
    }
}

class FrontCamera(
    context: Context
) : Camera(context, "FRONT_INTEGRATED_CAMERA", "fic") {
    override val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    override val mirroredH: Boolean = true

    internal companion object : DeviceProvider(Dispatchers.Main) {
        override suspend fun IDeviceManager.StaticDeviceProviderScope.getStaticManagedDevices(): List<BiometricDevice> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager?
                ?: return emptyList()
            val hasFrontCamera = manager.cameraIdList.any {
                manager.getCameraCharacteristics(it)[CameraCharacteristics.LENS_FACING] ==
                        CameraMetadata.LENS_FACING_FRONT
            }
            return if (hasFrontCamera) listOf(FrontCamera(context)) else emptyList()
        }
    }
}