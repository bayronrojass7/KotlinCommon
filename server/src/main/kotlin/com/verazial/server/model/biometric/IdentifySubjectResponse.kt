package com.verazial.server.model.biometric

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.verazial.server.model.ApiResponse

@JsonClass(generateAdapter = true)
class IdentifySubjectResponse(
    @Json(name = "status") status: String,
    @Json(name = "id") val id: String?
): ApiResponse(status)