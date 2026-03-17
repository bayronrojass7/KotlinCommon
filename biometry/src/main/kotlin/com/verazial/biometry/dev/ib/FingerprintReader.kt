package com.verazial.biometry.dev.ib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.util.Log
import com.integratedbiometrics.ibscanultimate.IBScan
import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.integratedbiometrics.ibscanultimate.IBScanDeviceListener
import com.integratedbiometrics.ibscanultimate.IBScanException
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceUSB
import com.verazial.biometry.lib.util.ImageUtil.toJpeg
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.Device
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.Subtype
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

class FingerprintReader(
    private val context: Context,
    usbDevice: UsbDevice
) : BiometricDeviceUSB(context, usbDevice) {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val biometricTechnologies: Set<BiometricTechnology> =
        setOf(BiometricTechnology.FINGERPRINT)

    private var screenOffReceiver: BroadcastReceiver? = null
    private var deviceInfo: IBScan.DeviceDesc? = null
    private var deviceApi: IBScanDevice? = null
    private var isConnected = false

    private inner class ScreenOffReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                isConnected = false
            }
        }
    }

    override suspend fun initializeUSBDeviceSafe() {
        val manager = IBScan.getInstance(context)
        val deviceIndex = (0 until manager.deviceCount).singleOrNull {
            manager.getDeviceDescription(it).deviceId == usbDevice.deviceId
        }.notNullOrCommError()
        deviceInfo = manager.getDeviceDescription(deviceIndex).notNullOrCommError()
        deviceApi = manager.openDevice(deviceIndex).notNullOrCommError()
        screenOffReceiver = ScreenOffReceiver()
        context.registerReceiver(
            screenOffReceiver,
            IntentFilter("android.intent.action.SCREEN_OFF")
        )
        isConnected = true
    }

    override suspend fun getMaxSamplesSafe(): Int =
        when (deviceInfo.notNullOrCommError().productName) {
            "COLUMBO" -> 1
            else -> 1
        }

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = callbackFlow {
        val api = deviceApi.notNullOrCommError()
        var detectedFinger = false
        val callback = object : IBScanDeviceListener {
            override fun deviceCommunicationBroken(device: IBScanDevice?) {
                isConnected = false
                trySend(runCatching { throw ReadingTimeout })
            }
            val subtype = targetSamples.first()

            override fun deviceImagePreviewAvailable(
                device: IBScanDevice,
                image: IBScanDevice.ImageData
            ) {
                if (!detectedFinger) return
                if (!enablePreviews) return
                trySend(
                    runCatching {
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    contents = image.toBitmap().toJpeg().base64String,
                                    type = BiometricSample.Type.FINGER,
                                    subtype = subtype,
                                    format = BiometricSample.Type.FINGER.IMAGE,
                                    quality = 0
                                )
                            )
                        )
                    }
                )
            }

            override fun deviceFingerCountChanged(
                device: IBScanDevice?,
                fingerState: IBScanDevice.FingerCountState?
            ) {
                if (fingerState == IBScanDevice.FingerCountState.NON_FINGER) return
                detectedFinger = true
            }

            override fun deviceFingerQualityChanged(
                device: IBScanDevice?,
                fingerQualities: Array<out IBScanDevice.FingerQualityState>?
            ) = Unit

            override fun deviceAcquisitionBegun(
                device: IBScanDevice?,
                imageType: IBScanDevice.ImageType?
            ) = Unit

            override fun deviceAcquisitionCompleted(
                device: IBScanDevice?,
                imageType: IBScanDevice.ImageType?
            ) = Unit

            override fun deviceImageResultAvailable(
                device: IBScanDevice,
                image: IBScanDevice.ImageData,
                imageType: IBScanDevice.ImageType,
                splitImageArray: Array<out IBScanDevice.ImageData>
            ) {
                trySend(
                    runCatching {
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    contents = image.toBitmap().toJpeg().base64String,
                                    type = BiometricSample.Type.FINGER,
                                    subtype = subtype,
                                    format = BiometricSample.Type.FINGER.IMAGE,
                                    quality = 0
                                )
                            )
                        )
                    }
                )
                close()
            }

            override fun deviceImageResultExtendedAvailable(
                device: IBScanDevice?,
                imageStatus: IBScanException?,
                image: IBScanDevice.ImageData?,
                imageType: IBScanDevice.ImageType?,
                detectedFingerCount: Int,
                segmentImageArray: Array<out IBScanDevice.ImageData>?,
                segmentPositionArray: Array<out IBScanDevice.SegmentPosition>?
            ) = Unit

            override fun devicePlatenStateChanged(
                device: IBScanDevice?,
                platenState: IBScanDevice.PlatenState?
            ) = Unit

            override fun deviceWarningReceived(
                device: IBScanDevice?,
                warning: IBScanException?
            ) = Unit

            override fun devicePressedKeyButtons(
                device: IBScanDevice?,
                pressedKeyButtons: Int
            ) = Unit
        }
        api.setScanDeviceListener(callback)
        api.beginCaptureImage(
            IBScanDevice.ImageType.FLAT_SINGLE_FINGER,
            IBScanDevice.ImageResolution.RESOLUTION_500,
            IBScanDevice.OPTION_AUTO_CAPTURE
                    or IBScanDevice.OPTION_AUTO_CONTRAST
        )
        awaitClose {
            api.setScanDeviceListener(null)
        }
    }.map(Result<BiometricCapture>::getOrThrow)
        .catch {
            Log.w("FingerprintReader", "performReadSafe() failed", it)
            if (it is IBScanException)
                Log.w("FingerprintReader", "IBScanException: ${it.type}")
            throw it
        }

    override suspend fun stopReadSafe() {
        runCatching { deviceApi?.cancelCaptureImage() }
            .onFailure {
                if (it !is IBScanException) throw it
                if (it.type == IBScanException.Type.CAPTURE_NOT_RUNNING) return@onFailure
                if (it.type == IBScanException.Type.DEVICE_INVALID_STATE) return@onFailure
                throw it
            }
    }

    override suspend fun closeSafe() {
        deviceApi?.close()
        deviceApi = null
        deviceInfo = null
        isConnected = false
        screenOffReceiver?.let {
            context.unregisterReceiver(it)
            screenOffReceiver = null
        }
    }

    override suspend fun stillAliveSafe(): Boolean =
        isConnected

    internal companion object : DeviceProvider(Dispatchers.IO) {
        private const val VID = 4415
        private val PIDS = listOf(4352, 5632)

        override suspend fun DeviceProviderScope.getManageableDevice(): Device? {
            if (deviceCandidate !is IDeviceManager.DeviceCandidate.Usb) return null
            if (deviceCandidate.device.vendorId != VID || deviceCandidate.device.productId !in PIDS) return null

            return FingerprintReader(context, deviceCandidate.device)
        }
    }
}