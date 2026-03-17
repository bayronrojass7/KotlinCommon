package com.verazial.biometry.base

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.verazial.biometry.BuildConfig
import com.verazial.core.error.DeviceCommunicationError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

abstract class BiometricDeviceUSB(
    private val context: Context,
    protected val usbDevice: UsbDevice
) : BiometricDeviceBase() {

    final override val name: String = usbDevice.productName ?: "¿?"
    final override val id: String = usbDevice.run { "$vendorId:$productId" }

    internal val usbManager = context.getSystemService(UsbManager::class.java)

    final override suspend fun initializeSafe() {
        runCatching {
            askUSBPermission()
        }.getOrElse {
            throw DeviceCommunicationError(
                message = "USB permission denied",
                cause = it
            )
        }
        initializeUSBDeviceSafe()
    }

    protected abstract suspend fun initializeUSBDeviceSafe()

    private suspend fun askUSBPermission(): Boolean = suspendCancellableCoroutine { cont ->
        if (usbManager.hasPermission(usbDevice)) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    kotlin.runCatching {
                        cont.resume(
                            intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                            )
                        )
                    }
                }
            }
        }

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName) // Make intent explicit for Android U+
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            usbDevice.deviceId,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
            else
                0
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, null, null, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        usbManager.requestPermission(usbDevice, permissionIntent)

        cont.invokeOnCancellation {
            context.unregisterReceiver(usbReceiver)
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION =
            "${BuildConfig.LIBRARY_PACKAGE_NAME}.USB_PERMISSION"
    }
}