package com.verazial.core.interfaces

import com.verazial.core.model.BiometricTechnology

interface BiometricDeviceBase {
    val name: String
    val id: String
    val biometricTechnologies: Set<BiometricTechnology>
}