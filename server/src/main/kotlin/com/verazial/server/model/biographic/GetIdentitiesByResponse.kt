package com.verazial.server.model.biographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.verazial.server.model.ApiResponse


@JsonClass(generateAdapter = true)
class GetIdentitiesByResponse(
    @Json(name = "status") status: String,
    @Json(name = "identities") val identities: List<Identity>?
): ApiResponse(status) {

    @JsonClass(generateAdapter = true)
    data class Identity(
        @Json(name = "attributes") val attributes: List<Attribute>
    )

    @JsonClass(generateAdapter = true)
    data class Attribute(
        @Json(name = "name") val name: String,
        @Json(name = "value") val value: String?
    )
}