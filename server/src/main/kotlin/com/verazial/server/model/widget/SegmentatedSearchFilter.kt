package com.verazial.server.model.widget

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SegmentatedSearchFilter(
    @Json(name = "fieldName") val fieldName: String,
    @Json(name = "values") val values: List<String>,
)