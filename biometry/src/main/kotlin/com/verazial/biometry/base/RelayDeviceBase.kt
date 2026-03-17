package com.verazial.biometry.base

import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.DeviceManager
import com.verazial.core.interfaces.RelayDevice
import com.verazial.core.model.BiometricSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

abstract class RelayDeviceBase : RelayDevice() {
    protected abstract val coroutineContext: CoroutineContext
    protected open var isOn: Boolean = false


    final override suspend fun initialize() =
        closeOnFail {
            withTimeoutOrCommError(settings.initTimeout) {
                runCatching {
                    initializeSafe()
                }.onFailure { closeSafe() }.getOrThrow()
            }
        }

    final override suspend fun close() =
        closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { closeSafe() } }

    final override suspend fun stillAlive(): Boolean =
        closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { stillAliveSafe() } }

    final override suspend fun turnOn(
        duration: Duration
    ) = closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { turnOnSafe(duration) } }

    final override suspend fun turnOff() =
        closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { turnOffSafe() } }

    final override suspend fun isOn() =
        closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { isOnSafe() } }

    final override suspend fun isOff() =
        closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { isOffSafe() } }

    final override suspend fun toggle(
        duration: Duration
    ) = closeOnFail { withTimeoutOrCommError(settings.generalTimeout) { toggleSafe(duration) } }

    protected abstract suspend fun initializeSafe()

    protected abstract suspend fun closeSafe()
    protected abstract suspend fun stillAliveSafe(): Boolean
    protected abstract suspend fun turnOnSafe(
        duration: Duration
    )

    protected abstract suspend fun turnOffSafe()
    protected open suspend fun isOnSafe(): Boolean = isOn
    private suspend fun isOffSafe(): Boolean = !isOnSafe()
    private suspend fun toggleSafe(
        duration: Duration
    ): Unit = if (isOnSafe()) turnOffSafe() else turnOnSafe(duration)


    protected fun <T> T?.notNullOrCommError(): T =
        this ?: throw DeviceCommunicationError(
            message = "expected not null value"
        )

    protected fun <T> T?.notNullOrReadingTimeout(): T =
        this ?: throw ReadingTimeout

    protected fun <T> List<BiometricSample.Type.Format>.bestFormat(
        formatMappings: Map<BiometricSample.Type.Format, T>,
        defaultFormat: BiometricSample.Type.Format
    ): Pair<BiometricSample.Type.Format, T> {
        require(formatMappings.containsKey(defaultFormat))
        forEach { format ->
            formatMappings[format]?.let { return format to it }
        }
        return defaultFormat to formatMappings[defaultFormat]!!
    }

    private suspend fun <T> withTimeoutOrCommError(
        timeout: Duration,
        block: suspend CoroutineScope.() -> T
    ): T =
        try {
            withContext(coroutineContext) {
                withTimeout(timeout) { block() }
            }
        } catch (e: TimeoutCancellationException) {
            throw DeviceCommunicationError(
                message = "operation timeout exceeded",
                cause = e
            )
        }

    private suspend fun <T> withTimeoutOrReadingTimeout(
        timeout: Duration,
        block: suspend CoroutineScope.() -> T
    ): T =
        try {
            withContext(coroutineContext) {
                withTimeout(timeout) { block() }
            }
        } catch (e: TimeoutCancellationException) {
            throw ReadingTimeout
        }

    private suspend inline fun <T> closeOnFail(
        block: () -> T
    ): T = runCatching(block)
        .onFailure { closeSafe() }
        .getOrThrow()

    companion object {
        lateinit var settings: DeviceManager.Settings
    }
}