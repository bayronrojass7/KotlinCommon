package com.verazial.core.model

import java.io.Serializable

/**
 * Biometric data of a user.
 */
@kotlinx.serialization.Serializable
data class User(
    /**
     * User's id.
     */
    val id: String
) : Serializable