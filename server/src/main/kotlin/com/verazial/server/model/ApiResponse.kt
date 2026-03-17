package com.verazial.server.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
open class ApiResponse(
    @Json(name = "status") val status: String
) {
    @Transient
    val isSuccessful: Boolean = status == "SUCCESS"
}