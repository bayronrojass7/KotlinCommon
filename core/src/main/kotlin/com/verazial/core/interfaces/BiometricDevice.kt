package com.verazial.core.interfaces

import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration


/**
 * Represents a biometric device, used to manage the communication
 * with an instance of a biometric device.
 *
 * When referring to a biometric device, it is meant to be a device
 * model, not a specific instance of it. On the other hand, when
 * referring to a biometric device instance, it is meant to be a
 * specific physical device, not a model.
 */
abstract class BiometricDevice : Device() {
    /**
     * Biometric technologies used by this device.
     */
    abstract val biometricTechnologies: Set<BiometricTechnology>

    /**
     * Action: Gets the maximum number of samples that can be read from the device in one read.
     * Will always return a value greater than 0.
     *
     * @return The maximum number of samples that can be read from the device.
     */
    abstract suspend fun getMaxSamples(): Int

    /**
     * Action: Perform a biometric reading with the given options.
     *
     * @param timeout The maximum time to wait for the reading to complete.
     * @param preferredFormats The ordered list of preferred formats to use for the samples.
     * @param targetSamples The list of samples to read from the device.
     * @param binder The binder to use to communicate with the device.
     * @param enablePreviews If previews should be enabled for the reading.
     * @return A flow of takes read from the device, each take is a list of samples that were read in the same take.
     */
    abstract suspend fun performRead(
        timeout: Duration,
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture>

    /**
     * Action: Stop a biometric reading in progress.
     */
    abstract suspend fun stopRead()

    /**
     * Used to communicate with the device in order to display information to the user.
     */
    interface BiometricDeviceViewBinder
}