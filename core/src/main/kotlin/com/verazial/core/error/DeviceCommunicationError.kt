package com.verazial.core.error

/**
 * Represents that an error occurred while communicating with a biometric device
 */
class DeviceCommunicationError(
    override val message: String? = "",
    override val cause: Throwable? = null
) : Exception()