package com.verazial.server.model.biographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class GetIdentitiesByBody(
    @Json(name = "attributes") val attributes: List<Attribute>
) {

    @JsonClass(generateAdapter = true)
    data class Attribute(
        @Json(name = "name") val name: String,
        @Json(name = "value") val value: String
    )
}