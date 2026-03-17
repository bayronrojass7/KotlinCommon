package com.verazial.biometry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds


fun Context.usbDevices(): Flow<List<UsbDevice>> = callbackFlow {
    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            trySend(Unit)
        }
    }
    registerReceiver(
        usbReceiver,
        IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
    )
    awaitClose { runCatching { unregisterReceiver(usbReceiver) } }
}.onStart {
    emit(Unit)
}.map {
    ContextCompat.getSystemService(this, UsbManager::class.java)?.run {
        deviceList?.values?.toList()
    }.orEmpty()
}.distinctUntilChanged { old, new -> old == new }


fun bleDevices(): Flow<List<Advertisement>> = flow {
    val foundInFrame = mutableMapOf<String, Advertisement>()
    var frameStart = System.currentTimeMillis()

    val scanned = withTimeoutOrNull(10_000) {
        Scanner()
            .advertisements
            .onEach { advertisement ->
                println("Found device: ${advertisement.name} @ ${advertisement.address}")
            }
            .catch { error ->
                println("BLE scan error: $error")
            }
            .collect { advertisement ->
                foundInFrame[advertisement.address] = advertisement

                val now = System.currentTimeMillis()
                if (now - frameStart > 5_000) {
                    println("Emitting ${foundInFrame.size} devices")
                    emit(foundInFrame.values.toList())
                    foundInFrame.clear()
                    frameStart = now
                }
            }
    }

    if (scanned == null) {
        println("BLE scan timed out after 10 seconds")
    }

}.onStart {
    println("BLE scan started")
    emit(emptyList())
}
