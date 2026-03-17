package com.verazial.core.model

/**
 * Represents a single capture of biometric data.
 */
data class BiometricCapture(
    /**
     * Indicates the distance between the user and the sensor,
     * when not available it will be [DistanceStatus.UNKNOWN].
     */
    val distanceStatus: DistanceStatus = DistanceStatus.UNKNOWN,
    /**
     * The samples captured during this biometric capture.
     */
    val samples: List<BiometricSample>,
) {
    /**
     * Represents the status of the distance between the user and the sensor.
     */
    enum class DistanceStatus {
        /**
         * The distance status is unknown.
         */
        UNKNOWN,
        /**
         * The user is too close to the sensor.
         */
        TOO_CLOSE,
        /**
         * The user is at an optimal distance from the sensor.
         */
        OK,
        /**
         * The user is too far from the sensor.
         */
        TOO_FAR
    }
}