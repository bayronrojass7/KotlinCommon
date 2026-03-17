package com.verazial.biometry_test.tests.model

import androidx.compose.runtime.Stable
import com.verazial.biometry_test.tests.TestContext
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.interfaces.Device
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import kotlin.reflect.KClass
import kotlin.time.Duration

@Stable
data class BioDevSpecs(
    val friendlyName: String,
    val readingTimeout: Duration,
    val deviceKClass: KClass<out Device>,
    val deviceBiometricTechnologies: Set<BiometricTechnology>,
    val deviceId: String,
    val deviceName: String?,
    val deviceCanTakeUnknownSamples: Boolean,
    val deviceMaxSamples: Int,
    val deviceCapableFormats: List<BiometricSample.Type.Format>,
    val providesDistanceStatus: Boolean,
    val providesPreviews: Boolean,
    val deviceBinder: ((TestContext) -> BiometricDevice.BiometricDeviceViewBinder)?
)