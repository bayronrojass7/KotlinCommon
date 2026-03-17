package com.verazial.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NfcDeviceData(
    val uuid: String? = null,
    @SerialName("wristband-state") val wristbandState: WristbandState? = null,
    @SerialName("storage-status") val storageStatus: StorageStatus? = null
)

@Serializable
data class WristbandState(
    val registered: Boolean,
    val linked: Boolean,
    val worn: Boolean,
    val active: Boolean
)

@Serializable
data class StorageStatus(
    val total: Double,
    val used: Double,
    val available: Double
)