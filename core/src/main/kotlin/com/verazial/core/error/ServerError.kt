package com.verazial.core.error

/**
 * Represents an error that occurred while contacting the VerázialID server
 */
class ServerError(
    override val message: String? = "",
    override val cause: Throwable? = null
) : Exception()