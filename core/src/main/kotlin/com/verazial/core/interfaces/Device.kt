package com.verazial.core.interfaces


/**
 * Represents any device, used to manage the communication
 * with an instance of a device.
 *
 * When referring to a device, it is meant to be a device
 * model, not a specific instance of it. On the other hand, when
 * referring to a device instance, it is meant to be a
 * specific physical device, not a model.
 */
abstract class Device {

    /**
     * Device friendly name, there is not need for it to be unique.
     *
     * A good example of a friendly name is the device model name.
     */
    abstract val name: String

    /**
     * Device unique identifier, must be unique not only from other devices,
     * but also for other instances of the same device model.
     *
     * A good example of a unique identifier is the device MAC address,
     * or the device serial number.
     */
    abstract val id: String


    /**
     * Initializes the device, should be called before any other method.
     */
    abstract suspend fun initialize()


    /**
     * Closes connection with the device.
     * Must be called when the device is no longer needed.
     * After this method is called, the device cannot be used anymore,
     * and must be re-initialized.
     */
    abstract suspend fun close()

    /**
     * Checks if the device is still alive,
     * if not it can be considered as closed and gone,
     * should clear any references to it.
     *
     * @return True if the device is still alive, false otherwise.
     */
    abstract suspend fun stillAlive(): Boolean

    /**
     * Checks if two devices are the same, based on their id.
     *
     * @param other The other device to compare to.
     * @return True if the devices are the same, false otherwise.
     */
    final override fun equals(other: Any?): Boolean = id == (other as? Device)?.id

    /**
     * Returns the hash code of the device, based on its id.
     *
     * @return The hash code of the device.
     */
    final override fun hashCode(): Int = id.hashCode()
}