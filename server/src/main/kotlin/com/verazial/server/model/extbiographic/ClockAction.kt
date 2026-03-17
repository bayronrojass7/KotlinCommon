package com.verazial.server.model.extbiographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClockAction(
    @Json(name = "code") val code: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "attributes") val attributesMap: Map<String, String>?
)