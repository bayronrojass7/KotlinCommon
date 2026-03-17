package com.verazial.core.model

import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.interfaces.BiometricDeviceBase
import kotlin.reflect.KClass

/**
 * Biometric device info.
 */
@JvmInline
value class BiometricDeviceInfo(
    private val biometricDevice: BiometricDevice
) : BiometricDeviceBase {
    /**
     * Device friendly name.
     */
    override val name: String get() = biometricDevice.name

    /**
     * Device unique identifier.
     */
    override val id: String get() = biometricDevice.id

    /**
     * Biometric technologies used by the device.
     */
    override val biometricTechnologies: Set<BiometricTechnology> get() = biometricDevice.biometricTechnologies

    /**
     * The class that is configured to handle the biometric device.
     */
    val controller: KClass<out BiometricDevice> get() = biometricDevice::class
}