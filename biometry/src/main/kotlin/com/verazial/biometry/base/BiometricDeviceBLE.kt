package com.verazial.biometry.base

import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds


abstract class BiometricDeviceBLE(
    private val advertisement: Advertisement,
    private val mtuSize: Int
) : BiometricDeviceBase() {

    final override val coroutineContext: CoroutineContext = Dispatchers.IO
    final override val name: String = advertisement.name ?: "¿?"
    final override val id: String = advertisement.address
    private val scope = CoroutineScope(coroutineContext)

    private var _peripheral: Peripheral? = null
    protected val peripheral get() = _peripheral.notNullOrCommError()

    final override suspend fun initializeSafe() {
        _peripheral = scope.peripheral(advertisement) {
            onServicesDiscovered {
                requestMtu(mtuSize)
            }
        }
        peripheral.connect()
        initializeBLEDeviceSafe()
    }

    final override suspend fun stillAliveSafe() =
        _peripheral?.state?.value == State.Connected

    protected abstract suspend fun initializeBLEDeviceSafe()


    final override suspend fun closeSafe() {
        withTimeoutOrNull(1.seconds) {
            _peripheral?.disconnect()
        }
    }
}