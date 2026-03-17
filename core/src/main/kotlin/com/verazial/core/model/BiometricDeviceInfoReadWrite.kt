package com.verazial.core.model

import com.verazial.core.interfaces.BiometricDeviceBase
import com.verazial.core.interfaces.BiometricDeviceReadWrite
import kotlin.reflect.KClass

/**
 * Biometric device info.
 */
@JvmInline
value class BiometricDeviceInfoReadWrite(
    private val biometricDevice: BiometricDeviceReadWrite
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
    val controller: KClass<out BiometricDeviceReadWrite> get() = biometricDevice::class
}