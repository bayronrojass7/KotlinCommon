package com.verazial.server.model.version

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ValidateClientResponse(
    val operation: Int
)
