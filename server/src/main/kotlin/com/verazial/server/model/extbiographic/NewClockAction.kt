package com.verazial.server.model.extbiographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NewClockAction(
    @Json(name = "code") val code: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "attributes") val attributes: Attributes?
) {

    @JsonClass(generateAdapter = true)
    data class Attributes(
        @Json(name = "applicationId") val applicationId: String,
        @Json(name = "locationId") val locationId: String,
        @Json(name = "deviceId") val deviceId: String,
        @Json(name = "segmentId") val segmentId: String,
        @Json(name = "identityId") val identityId: String,
        @Json(name = "eventCode") val eventCode: String,
        @Json(name = "eventId") val eventId: String,
        @Json(name = "eventLatitude") val eventLatitude: String,
        @Json(name = "eventLongitude") val eventLongitude: String,
        @Json(name = "eventTimestamp") val eventTimestamp: String,
        @Json(name = "actualTime") val actualTime: String
    )
}