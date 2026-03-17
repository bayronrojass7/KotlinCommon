package com.verazial.biometry.base

import com.verazial.core.error.AlreadyActived
import com.verazial.core.error.AlreadyRegistered
import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.error.NoStatusObtained
import com.verazial.core.error.NoValidOption
import com.verazial.core.error.NoWorn
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.error.UUIDMismatch
import com.verazial.core.interfaces.BiometricDeviceReadWrite
import com.verazial.core.interfaces.DeviceManager
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

abstract class BiometricDeviceReadWriteBase : BiometricDeviceReadWrite(),
    KoinComponent {
    protected abstract val coroutineContext: CoroutineContext


    final override suspend fun initialize() =
        withTimeoutOrCommError(settings.initTimeout) {
            runCatching {
                initializeSafe()
            }.onFailure { closeSafe() }.getOrThrow()
        }

    final override suspend fun getMaxSamples(): Int =
        withTimeoutOrCommError(settings.initTimeout) {
            runCatching {
                getMaxSamplesSafe()
            }.onFailure { closeSafe() }.getOrThrow()
        }

    final override suspend fun performRead(
        timeout: Duration,
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {
        runCatching {
            val job = performReadSafe(preferredFormats, targetSamples, binder, enablePreviews)
                .catch {
                    it.printStackTrace() // STOPSHIP: Remove logging
                    throw it
                }
                .flowOn(coroutineContext)
            withTimeout(timeout) {
                job.collect { emit(it) }
            }
        }.also {
            withContext(NonCancellable) {
                stopRead()
            }
        }.getOrElse {
            if (it is TimeoutCancellationException) throw ReadingTimeout
            if (it is CancellationException) throw ReadingTimeout
            it.printStackTrace() // STOPSHIP: Remove logging
            close()
            throw DeviceCommunicationError(
                message = "performRead() failed",
                cause = it
            )
        }
    }

    final override suspend fun stopRead() =
        runCatching {
            withContext(coroutineContext) {
                stopReadSafe()
            }
        }.getOrElse {
            runCatching { closeSafe() }
            it.printStackTrace() // STOPSHIP: Remove logging
            throw DeviceCommunicationError(
                message = "stopRead() failed",
                cause = it
            )
        }

    final override suspend fun performWrite(
        timeout: Duration,
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean,
        content: JsonObject
    ): Flow<BiometricCapture> = flow {
        runCatching {
            val job = performWriteSafe(preferredFormats, targetSamples, binder, enablePreviews, content)
                .catch {
                    it.printStackTrace() // STOPSHIP: Remove logging
                    throw it
                }
                .flowOn(coroutineContext)
            withTimeout(timeout) {
                job.collect { emit(it) }
            }
        }.also {
            withContext(NonCancellable) {
                stopWrite()
            }
        }.getOrElse {
            if (it is TimeoutCancellationException) throw ReadingTimeout
            if (it is CancellationException) throw ReadingTimeout
            if (it is NoWorn || it is AlreadyActived || it is AlreadyRegistered
                || it is NoStatusObtained || it is NoValidOption || it is UUIDMismatch
            ) throw it
            it.printStackTrace() // STOPSHIP: Remove logging
            close()
            throw DeviceCommunicationError(
                message = "performRead() failed",
                cause = it
            )
        }
    }

    final override suspend fun stopWrite() =
        runCatching {
            withContext(coroutineContext) {
                stopWriteSafe()
            }
        }.getOrElse {
            runCatching { closeSafe() }
            it.printStackTrace() // STOPSHIP: Remove logging
            throw DeviceCommunicationError(
                message = "stopWrite() failed",
                cause = it
            )
        }

    final override suspend fun close() =
        withTimeoutOrCommError(settings.generalTimeout) { closeSafe() }

    final override suspend fun stillAlive(): Boolean =
        withTimeoutOrCommError(settings.generalTimeout) { stillAliveSafe() }

    abstract suspend fun initializeSafe()
    abstract suspend fun getMaxSamplesSafe(): Int
    abstract suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture>

    abstract suspend fun stopReadSafe()

    abstract suspend fun performWriteSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean,
        content: JsonObject,
    ): Flow<BiometricCapture>

    abstract suspend fun stopWriteSafe()
    abstract suspend fun closeSafe()
    abstract suspend fun stillAliveSafe(): Boolean


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

    companion object {
        lateinit var settings: DeviceManager.Settings
    }
}