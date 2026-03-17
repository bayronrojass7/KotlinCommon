package com.verazial.server.model.extbiographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.verazial.server.model.ApiResponse

@JsonClass(generateAdapter = true)
class GetIdentityOldResponse(
    @Json(name = "status") status: String,
    @Json(name = "identities") val identities: List<IdentityOld>?
) : ApiResponse(status) {

    @JsonClass(generateAdapter = true)
    data class IdentityOld(
        @Json(name = "numId") val nif: String,
        @Json(name = "firstname") val firstName: String?,
        @Json(name = "secondname") val secondName: String?,
        @Json(name = "firstlastname") val lastName: String?,
        @Json(name = "secondlastname") val secondLastName: String?,
        @Json(name = "birthdate") val birthdate: String?,
        @Json(name = "sex") val sex: GetIdentityResponse.Gender?,
    )
}