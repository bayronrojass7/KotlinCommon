package com.verazial.biometry.dev.suprema

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.util.Log
import com.biominiseries.BioMiniFactory
import com.biominiseries.IBioMiniDevice
import com.biominiseries.IBioMiniDevice.CaptureOption
import com.biominiseries.IUsbEventHandler.DeviceChangeEvent
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceUSB
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class BioMini(
    private val context: Context,
    usbDevice: UsbDevice
) : BiometricDeviceUSB(context, usbDevice) {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val biometricTechnologies: Set<BiometricTechnology> =
        setOf(BiometricTechnology.FINGERPRINT)

    private var bioMiniFactory: BioMiniFactory? = null
    private var currentDevice: IBioMiniDevice? = null


    override suspend fun initializeUSBDeviceSafe() {

        bioMiniFactory = object : BioMiniFactory(context, usbManager) {
            override fun onDeviceChange(event: DeviceChangeEvent?, dev: Any?) {
                // Optional: handle hot-plug events if needed
                Log.d("BioMini", "Device change event: $event")
            }
        }

        //bioMiniFactory?.setTransferMode(IBioMiniDevice.TransferMode.MODE1)

        val success = bioMiniFactory?.addDevice(usbDevice) ?: false
        if (!success) {
            Log.e("Suprema BioMini", "Failed to initialize Suprema BioMini iris algorithm, error code: $success")
        }

        currentDevice = bioMiniFactory?.getDevice(0)
        if (currentDevice == null) {
            Log.e("Suprema BioMini", "getDevice is null")
        }

        // Optionally get device info
        val deviceInfo = currentDevice?.deviceInfo
        Log.d("BioMini", "Device attached: ${deviceInfo?.deviceName}, SN: ${deviceInfo?.deviceSN}")
    }



    override suspend fun getMaxSamplesSafe(): Int = 1

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = callbackFlow {
        val option = CaptureOption().apply {
            captureTimeout = -1
            captureFuntion = IBioMiniDevice.CaptureFuntion.CAPTURE_SINGLE
            extractParam.captureTemplate = false

        }

        val callback = object : com.biominiseries.z {
            override fun onCapture(
                context: Any?,
                fingerState: IBioMiniDevice.FingerState?
            ) {
                Log.d("BioMini", "Finger scanning: $fingerState")
            }

            override fun onCaptureEx(
                context: Any?,
                option: CaptureOption?,
                capturedImage: Bitmap?,
                capturedTemplate: IBioMiniDevice.TemplateData?,
                fingerState: IBioMiniDevice.FingerState?
            ): Boolean {
                trySend(
                    runCatching {
                        if (capturedImage == null) throw IllegalStateException("Captured image is null")

                        val base64String = capturedImage.toJpeg().base64String
                        val subtype = targetSamples.first()
                        BiometricCapture(
                            samples = listOf(
                                BiometricSample(
                                    contents = base64String,
                                    type = BiometricSample.Type.FINGER,
                                    subtype = subtype,
                                    format = BiometricSample.Type.FINGER.IMAGE,
                                    quality = 0
                                )
                            )
                        )
                    }
                )
                launch { close() }
                return true
            }

            override fun onCaptureError(
                context: Any?,
                errorCode: Int,
                message: String?
            ) {
                trySend(
                    runCatching {
                        throw Exception("Capture error $errorCode: $message")
                    }
                )
                launch { close() }
            }
        }

        currentDevice?.captureSingle(option, callback, true)
            ?: run {
                trySend(runCatching { throw IllegalStateException("Device is null") })
                launch { close() }
            }

        awaitClose {
            // Optionally cancel capture or clean up resources here
        }
    }.map(Result<BiometricCapture>::getOrThrow)
        .catch {
            Log.w("FingerprintReader", "performReadSafe() failed", it)
            throw it
        }


    override suspend fun stopReadSafe() {

        if(currentDevice?.isCapturing == true)
        {
            currentDevice?.abortCapturing()
        }
    }

    override suspend fun closeSafe() {
        bioMiniFactory?.removeDevice(usbDevice)
        bioMiniFactory?.close()

        currentDevice = null
        bioMiniFactory = null
    }

    override suspend fun stillAliveSafe(): Boolean =
        currentDevice != null

    internal companion object : DeviceProvider(Dispatchers.IO) {
        override suspend fun DeviceProviderScope.getManageableDevice(): BiometricDevice? {
            if (deviceCandidate !is IDeviceManager.DeviceCandidate.Usb) return null
            val usbDevice = deviceCandidate.device

            if (usbDevice.vendorId == 0x16d1) {
                return BioMini(context, usbDevice)
            }
            return null
        }
    }
}
