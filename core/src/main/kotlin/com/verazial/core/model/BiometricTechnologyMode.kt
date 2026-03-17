package com.verazial.core.model

import com.verazial.core.model.BiometricTechnology.FACIAL
import com.verazial.core.model.BiometricTechnology.FINGERPRINT
import com.verazial.core.model.BiometricTechnology.IRIS

/**
 * Biometric technology mode, which indicates the type of biometric technologies that are enabled.
 */
sealed class BiometricTechnologyMode(
    protected val id: String,
    vararg enabledTechnologies: BiometricTechnology
) {
    /**
     * Biometric technologies that are enabled in this mode.
     */
    val enabledTechnologies: List<BiometricTechnology> = enabledTechnologies.asList()

    /**
     * Returns true if the given [technology] is enabled in this mode.
     */
    fun isTechnologyEnabled(technology: BiometricTechnology) =
        enabledTechnologies.contains(technology)

    /**
     * Returns the string representation of this mode, which is the same as the [id].
     */
    override fun toString(): String = id


    object FingerprintOnly : BiometricTechnologyMode("FingerprintOnly", FINGERPRINT)

    object FacialOnly : BiometricTechnologyMode("FacialOnly", FACIAL)

    object IrisOnly : BiometricTechnologyMode("IrisOnly", IRIS)

    object FingerprintAndFacial :
        BiometricTechnologyMode("FingerprintAndFacial", FINGERPRINT, FACIAL)

    object IrisAndFacial :
        BiometricTechnologyMode("IrisAndFacial", IRIS, FACIAL)

    object FingerprintAndIris :
        BiometricTechnologyMode("FingerprintAndIris", FINGERPRINT, IRIS)

    object NFC:
        BiometricTechnologyMode("NFC", BiometricTechnology.NFC)

    companion object {
        /**
         * Returns the [BiometricTechnologyMode] with the given [id].
         */
        fun getFromString(id: String): BiometricTechnologyMode =
            when (id) {
                FingerprintOnly.id -> FingerprintOnly
                FacialOnly.id -> FacialOnly
                IrisOnly.id -> IrisOnly
                FingerprintAndFacial.id -> FingerprintAndFacial
                IrisAndFacial.id -> IrisAndFacial
                NFC.id -> NFC
                else -> error("BiometricTechnologyMode id string is not valid")
            }
    }
}