package com.verazial.server.model.extbiographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.verazial.server.model.ApiResponse


@JsonClass(generateAdapter = true)
class GetClockActionsResponse(
    @Json(name = "status") status: String,
    @Json(name = "actions") val actions: List<ClockAction>?
): ApiResponse(status)