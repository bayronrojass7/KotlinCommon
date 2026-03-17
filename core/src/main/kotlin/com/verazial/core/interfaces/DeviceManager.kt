package com.verazial.core.interfaces

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * Manages device detection.
 */
abstract class DeviceManager {

    val settings = Settings(
        queryTimeout = 8.seconds,
        initTimeout = 5.seconds,
        generalTimeout = 5.seconds
    )

    /**
     * Gets currently available devices.
     *
     * @return A flow with the list of available devices.
     */
    abstract fun getDevices(): Flow<List<Device>>

    /**
     * Class to hold the settings for the manager
     * and the devices that are found.
     */
    data class Settings(
        /**
         * The timeout when querying a device.
         * Used when determining if a found device is compatible and can be used.
         */
        var queryTimeout: Duration,
        /**
         * The timeout when initializing the device.
         */
        var initTimeout: Duration,
        /**
         * The timeout used for general communication with the device.
         * For example, when stopping the device.
         */
        var generalTimeout: Duration
    )
}