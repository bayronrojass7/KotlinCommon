package com.verazial.server.model.storage

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.verazial.server.model.ApiResponse

@JsonClass(generateAdapter = true)
class GetResourceResponse(
    @Json(name = "status") status: String,
    @Json(name = "data") val data: String?
): ApiResponse(status)