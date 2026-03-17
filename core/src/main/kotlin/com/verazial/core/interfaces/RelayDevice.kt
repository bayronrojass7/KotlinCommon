package com.verazial.core.interfaces

import kotlin.time.Duration


/**
 * Represents a relay device, used to manage the communication
 * with an instance of a relay device.
 *
 * When referring to a relay device, it is meant to be a device
 * model, not a specific instance of it. On the other hand, when
 * referring to a relay device instance, it is meant to be a
 * specific physical device, not a model.
 *
 * A relay device is a device that can control the flow of electricity
 * to a device, like a door opener, to turn it on or off.
 */
abstract class RelayDevice : Device() {

    /**
     * Action: Turn on the relay.
     *
     * @param duration The duration to keep the relay on.
     */
    abstract suspend fun turnOn(
        duration: Duration
    )

    /**
     * Action: Turn off the relay.
     */
    abstract suspend fun turnOff()

    /**
     * Action: Toggle the relay.
     *
     * If the relay is on, it will turn it off. If the relay is off,
     * it will turn it on.
     *
     * @param duration The duration to keep the relay on.
     */
    abstract suspend fun toggle(
        duration: Duration
    )

    /**
     * Action: Check if the relay is on.
     *
     * @return True if the relay is on, false otherwise.
     */
    abstract suspend fun isOn(): Boolean

    /**
     * Action: Check if the relay is off.
     *
     * @return True if the relay is off, false otherwise.
     */
    abstract suspend fun isOff(): Boolean
}