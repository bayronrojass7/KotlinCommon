package com.verazial.core.model

/**
 * A users biometric sample.
 */
data class BiometricSample(
    /**
     * Content of the sample, this is a base64 encoded string representing the sample.
     */
    val contents: String,
    /**
     * The type of the sample.
     */
    val type: Type,
    /**
     * The subtype of the sample.
     */
    val subtype: Type.Subtype,
    /**
     * The format of the sample.
     */
    val format: Type.Format,
    /**
     * The quality of the sample.
     */
    val quality: Int
) {
    @Suppress("ClassName", "unused")
    sealed interface Type {
        sealed interface Subtype
        sealed interface Format

        object FINGER : Type {
            object UNKNOWN : Subtype
            object LEFT_THUMB : Subtype
            object LEFT_INDEX_FINGER : Subtype
            object LEFT_MIDDLE_FINGER : Subtype
            object LEFT_RING_FINGER : Subtype
            object LEFT_LITTLE_FINGER : Subtype
            object RIGHT_THUMB : Subtype
            object RIGHT_INDEX_FINGER : Subtype
            object RIGHT_MIDDLE_FINGER : Subtype
            object RIGHT_RING_FINGER : Subtype
            object RIGHT_LITTLE_FINGER : Subtype

            object ISO_IEC_19794_2_2005 : Format
            object ISO_IEC_19794_4_2005 : Format
            object ANSI_INCITS_378_2004 : Format
            object IMAGE : Format
        }

        object ROLLED_FINGER : Type {
            object UNKNOWN : Subtype
            object LEFT_THUMB_ROLLED : Subtype
            object LEFT_INDEX_FINGER_ROLLED : Subtype
            object LEFT_MIDDLE_FINGER_ROLLED : Subtype
            object LEFT_RING_FINGER_ROLLED : Subtype
            object LEFT_LITTLE_FINGER_ROLLED : Subtype
            object RIGHT_THUMB_ROLLED : Subtype
            object RIGHT_INDEX_FINGER_ROLLED : Subtype
            object RIGHT_MIDDLE_FINGER_ROLLED : Subtype
            object RIGHT_RING_FINGER_ROLLED : Subtype
            object RIGHT_LITTLE_FINGER_ROLLED : Subtype

            object IMAGE : Format
        }

        object FACE : Type {
            object UNKNOWN : Subtype
            object UP_LEFT_FACE : Subtype
            object UP_FACE : Subtype
            object UP_RIGHT_FACE : Subtype
            object RIGHT_FACE : Subtype
            object DOWN_RIGHT_FACE : Subtype
            object DOWN_FACE : Subtype
            object DOWN_LEFT_FACE : Subtype
            object LEFT_FACE : Subtype
            object FRONTAL_FACE : Subtype

            object IMAGE : Format
        }

        object IRIS : Type {
            object UNKNOWN : Subtype
            object LEFT_IRIS : Subtype
            object RIGHT_IRIS : Subtype

            object IMAGE : Format
            object ISO_IEC_19794_6_2005 : Format
        }

        object PALM : Type {
            object UNKNOWN : Subtype
            object LEFT_UPPER_PALM : Subtype
            object LEFT_LOWER_PALM : Subtype
            object LEFT_LATERAL_PALM : Subtype
            object RIGHT_UPPER_PALM : Subtype
            object RIGHT_LOWER_PALM : Subtype
            object RIGHT_LATERAL_PALM : Subtype

            object IMAGE : Format
        }

        object CARD : Type {
            object UNKNOWN : Subtype
            object CARD_1 : Subtype
            object CARD_2 : Subtype

            object TEXT : Format
        }

        object NFC : Type {
            object UNKNOWN : Subtype
            object BAND_1 : Subtype
            object BAND_2 : Subtype

            object TEXT : Format
        }
    }
}