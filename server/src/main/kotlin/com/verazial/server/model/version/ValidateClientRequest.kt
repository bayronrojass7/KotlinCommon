package com.verazial.server.model.version

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ValidateClientRequest(
    @Json(name = "idproduct") val idProduct: String,
    @Json(name = "version") val version: String,
)
