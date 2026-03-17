package com.verazial.core.error

/**
 * Represents an error that occurred while contacting the external biographic server
 */
class ExtServerError(
    override val message: String? = "",
    override val cause: Throwable? = null
) : Exception()