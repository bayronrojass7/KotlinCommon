package com.verazial.server.model.biometric

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Sample(
    @Json(name = "contents") val contents: String,
    @Json(name = "type") val type: String,
    @Json(name = "subtype") val subtype: String,
    @Json(name = "format") val format: String,
    @Json(name = "quality") val quality: Int
)