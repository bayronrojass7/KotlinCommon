package com.verazial.server.model.extbiographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.verazial.server.model.ApiResponse


@JsonClass(generateAdapter = true)
class GetIdentityResponse(
    @Json(name = "status") status: String,
    @Json(name = "identities") val identities: List<Identity>?
) : ApiResponse(status) {

    @JsonClass(generateAdapter = true)
    data class Identity(
        @Json(name = "nId") val nId: String,
        @Json(name = "attributes") val attributes: Attributes
    ) {

        @JsonClass(generateAdapter = true)
        data class Attributes(
            @Json(name = "firstName") val firstName: String?,
            @Json(name = "secondName") val secondName: String?,
            @Json(name = "lastName") val lastName: String?,
            @Json(name = "secondLastName") val secondLastName: String?,
            @Json(name = "gender") val gender: Gender?,
        )
    }

    enum class Gender {
        M, F;

        companion object {
            fun fromChar(char: Char) = values().first { it.name == char.toString() }
        }
    }
}