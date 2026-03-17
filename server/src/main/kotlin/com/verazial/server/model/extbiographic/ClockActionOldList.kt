package com.verazial.server.model.extbiographic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ClockActionOldList (
    //@Json(name = "id") val id: String, se supone que esto hace falta, pero en SAV no lo tienen
    @Json(name = "codigo") val code: String,
    @Json(name = "nombre") val name: String
)
