package com.verazial.core.model

import com.squareup.moshi.JsonClass

/**
 * Actions that can be performed as a clock event.
 */
@JsonClass(generateAdapter = true)
data class ClockAction(
    /**
     * The action code.
     */
    val code: String,
    /**
     * The action name.
     */
    val name: String,
)