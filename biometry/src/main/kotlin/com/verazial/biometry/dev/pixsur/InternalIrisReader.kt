/*
package com.verazial.biometry.dev.pixsur

import android.content.Context
import android.os.Build
import cn.com.pixsur.devicesdk.IrLedManager
import cn.com.pixsur.irissdk.IrisConfiguration
import cn.com.pixsur.irissdk.IrisDevice
import cn.com.pixsur.irissdk.IrisHandler
import cn.com.pixsur.irissdk.IrisHandlerListener
import cn.com.pixsur.irissdk.IrisManager
import com.verazial.biometry.IBiometricManager.DeviceProvider
import com.verazial.biometry.IBiometricManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.IRIS
import com.verazial.core.model.BiometricSample.Type.Subtype
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class InternalIrisReader private constructor(
    private val context: Context,
    private val device: IrisDevice
) : BiometricDeviceBase() {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val biometricTechnology: BiometricTechnology = BiometricTechnology.IRIS

    private var manager: IrisManager? = null
    private var handler: IrisHandler? = null
    private var lastStatusValue: Int? = null

    override val id: String
        get() = with(device) {
            "${IrisConfiguration.deviceProtocolItems[protocol].name}$id"
        }
    override val name: String
        get() = "Pixsur Internal"

    override suspend fun initializeSafe() {
        IrisManager.getInstance(context)?.release()
        manager = IrisManager.getInstance(context).notNullOrCommError()

        val modelPath = cloneModelFile()
        device.extra = modelPath
    }

    override suspend fun getMaxSamplesSafe(): Int = 2

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<List<BiometricSample>> = flow {
        handler = manager?.createHandler(device)
        val handler = handler.notNullOrCommError()
        val distanceViewBinder = binder as? DistanceViewBinder

        val eyeSubType = when {
            targetSamples.size > 1 -> IrisConfiguration.BioSPI_EYE_BOTH
            targetSamples.contains(IRIS.RIGHT_IRIS) -> IrisConfiguration.BioSPI_EYE_RIGHT
            targetSamples.contains(IRIS.LEFT_IRIS) -> IrisConfiguration.BioSPI_EYE_LEFT
            targetSamples.contains(IRIS.UNKNOWN) -> IrisConfiguration.BioSPI_EYE_EITHER
            else -> throw IllegalArgumentException("Unknown target samples: $targetSamples")
        }

        IrLedManager.openIR()
        handler.parameter = IrisConfiguration.defaultEnrollParameters
        val captureResult: CaptureResult = suspendCancellableCoroutine { cont ->
            handler.setListener(object : IrisHandlerListener {
                //<editor-fold desc="not needed overrides">
                override fun onLiveImage(p0: Int, p1: Int, p2: Int, p3: ByteBuffer?) = Unit

                override fun onEnrollNotify(
                    p0: Int,
                    p1: ByteArray?,
                    p2: ByteArray?,
                    p3: ByteArray?,
                    p4: ByteArray?,
                    p5: ByteArray?,
                    p6: ByteArray?,
                ) = Unit

                override fun onIdentifyNotify(
                    p0: Int,
                    p1: Int,
                    p2: Int,
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

                override fun onStatusNotify(key: Int, value: Int) {
                    if (distanceViewBinder == null) return
                    when (key) {
                        IrisConfiguration.BioSPI_DEVICE_EVENT_DISTANCE -> {
                            when (value) {
                                IrisConfiguration.BioSPI_DISTANCE_RANGE_NEAR ->
                                    DistanceViewBinder.DistanceState.TOO_CLOSE
                                IrisConfiguration.BioSPI_DISTANCE_RANGE_FAR ->
                                    DistanceViewBinder.DistanceState.TOO_FAR
                                IrisConfiguration.BioSPI_DISTANCE_RANGE_OK ->
                                    DistanceViewBinder.DistanceState.GOOD
                                else -> null
                            }?.let(distanceViewBinder.onDistanceChanged)
                        }
                        else -> Unit
                    }
                }

                override fun onCompressedCaptureNotify(
                    p0: Int,  // status
                    p1: Int,  // left eye quality
                    p2: Int,  // right eye quality
                    p3: ByteArray?,  // left eye image
                    p4: ByteArray?  // right eye image
                ) = cont.resume(CaptureResult(targetSamples.size, p0, p1, p2, p3, p4))
            })
            handler.compressedCapture(
                eyeSubType,
                IrisConfiguration.BioSPI_PARAMS_QUALITY_COMMON,
                IrisConfiguration.BioSPI_PARAMS_EYE_EXPO_IDENTIFY,
                30_000,  // Big enough to not timeout and let the coroutine be cancelled if needed
                IrisConfiguration.BioSPI_JPEG_QUALITY_80
            )
        }

        emit(captureResult.samples)
    }

    override suspend fun stopReadSafe() {
        handler?.cancel(0)  // Int param is not used
        manager?.run { // must close everything here, otherwise other apps that use the camera will make this one crash
            handler?.let(::closeHandler)
            release()
        }
        IrLedManager.closeIR()
    }

    override suspend fun closeSafe() {
        manager?.run {
            handler?.let(::closeHandler)
            release()
        }
        manager = null
        handler = null
    }

    override suspend fun stillAliveSafe(): Boolean =
        manager != null

    private fun cloneModelFile(): String = with(context) {
        val newDir = getExternalFilesDir("")?.absolutePath + "/model"
        val newName = "iris.mnn"
        val newFile = File(newDir, newName).apply {
            parentFile?.mkdirs()
            createNewFile()
        }
        assets.open("iris.mnn").use { assetsFileStream ->
            newFile.outputStream().use(assetsFileStream::copyTo)
        }
        newFile.absolutePath
    }

    internal companion object : DeviceProvider(Dispatchers.Main) {
        override suspend fun DeviceProviderScope.getManageableDevices(): List<BiometricDevice> {
            // PixSur SDK is only available for android sdk 28 and above
            if (Build.VERSION.SDK_INT < 28) return emptyList()
            if (Build.DEVICE != "tb8766p1_64_bsp") return emptyList()


            val manager = IrisManager.getInstance(context)
            val device: IrisDevice = suspendCancellableCoroutine { cont ->
                val listener = object : IrisManager.IDeviceListener {
                    override fun onExisted(p0: ArrayList<IrisDevice>) {
                        if (p0.isEmpty()) return
                        runCatching { cont.resume(p0.first()) }
                    }

                    override fun onPermissionGot(p0: IrisDevice) {
                        runCatching { cont.resume(p0) }
                    }

                    override fun onPermissionDenied(p0: IrisDevice) = Unit

                    override fun onDetached(p0: IrisDevice) = Unit
                }
                manager.addDeviceListener(listener)
                cont.invokeOnCancellation {
                    manager.delDeviceListener(listener)
                }
            }

            return InternalIrisReader(context, device).let(::listOf)
        }
    }

    private inner class CaptureResult(
        numOfEyes: Int,
        status: Int,
        leftImageQuality: Int,
        rightImageQuality: Int,
        leftImage: ByteArray?,
        rightImage: ByteArray?,
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
                    (leftImageQuality to BiometricSample(
                        contents = base64String,
                        type = IRIS,
                        subtype = IRIS.LEFT_IRIS,
                        format = IRIS.IMAGE,
                        quality = -1
                    )).let(::add)
                }
                rightImage?.run {
                    (rightImageQuality to BiometricSample(
                        contents = base64String,
                        type = IRIS,
                        subtype = IRIS.RIGHT_IRIS,
                        format = IRIS.IMAGE,
                        quality = -1
                    )).let(::add)
                }
            }.also { check(it.isNotEmpty()) }
                .sortedByDescending { it.first }
                .take(numOfEyes)
                .map { it.second }
        }
    }
}

class DistanceViewBinder(
    val onDistanceChanged: (DistanceState) -> Unit
): BiometricDevice.BiometricDeviceViewBinder {

    enum class DistanceState {
        TOO_FAR, TOO_CLOSE, GOOD
    }
}*/
