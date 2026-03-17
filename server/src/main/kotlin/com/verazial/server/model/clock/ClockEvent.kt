package com.verazial.server.model.clock

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClockEvent(
    @Json(name = "id") val id: Int,
    @Json(name = "applicationId") val applicationId: String,
    @Json(name = "locationId") val locationId: String,
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "segmentId") val segmentId: String,
    @Json(name = "identityId") val identityId: String,
    @Json(name = "eventCode") val eventCode: String,
    @Json(name = "eventId") val eventId: String,
    @Json(name = "eventLatitude") val eventLatitude: Float?,
    @Json(name = "eventLongitude") val eventLongitude: Float?,
    @Json(name = "eventTimestamp") val eventTimestamp: String
)