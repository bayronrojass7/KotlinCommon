package com.verazial.biometry_test.tests.model

import android.util.Log
import androidx.compose.runtime.Stable
import com.verazial.biometry_test.tests.TestContext
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology

@Stable
abstract class TestDefinition {
    abstract val name: String
    abstract val description: String

    protected val BioDevSpecs.imageFormat
        get() = when (deviceBiometricTechnologies.first()) {
            BiometricTechnology.FINGERPRINT -> BiometricSample.Type.FINGER.IMAGE
            BiometricTechnology.IRIS -> BiometricSample.Type.IRIS.IMAGE
            BiometricTechnology.FACIAL -> BiometricSample.Type.FACE.IMAGE
            BiometricTechnology.PALM -> BiometricSample.Type.PALM.IMAGE
            BiometricTechnology.FINGERPRINT_ROLLED -> BiometricSample.Type.ROLLED_FINGER.IMAGE
            BiometricTechnology.CARD -> BiometricSample.Type.NFC.TEXT
            BiometricTechnology.NFC -> BiometricSample.Type.NFC.TEXT
        }
    protected val BioDevSpecs.templateFormats get() = deviceCapableFormats - imageFormat
    protected val BioDevSpecs.canTakeImage get() = deviceCapableFormats.contains(imageFormat)
    protected val BioDevSpecs.canTakeTemplate get() =
        deviceCapableFormats.size.let { if (canTakeImage) it - 1 else it } > 0
    protected val BioDevSpecs.stubSampleSubtypes
        get() = if (deviceCanTakeUnknownSamples) stubUnknownSampleSubtypes
        else stubSpecificSampleSubtypes
    protected val BioDevSpecs.stubSampleSubtype
        get() = stubSampleSubtypes.take(1)
    protected val BioDevSpecs.stubUnknownSampleSubtypes
        get() = when (deviceBiometricTechnologies.first()) {
            BiometricTechnology.FINGERPRINT -> BiometricSample.Type.FINGER.UNKNOWN
            BiometricTechnology.IRIS -> BiometricSample.Type.IRIS.UNKNOWN
            BiometricTechnology.FACIAL -> BiometricSample.Type.FACE.UNKNOWN
            BiometricTechnology.PALM -> BiometricSample.Type.PALM.UNKNOWN
            BiometricTechnology.FINGERPRINT_ROLLED -> BiometricSample.Type.ROLLED_FINGER.UNKNOWN
            BiometricTechnology.CARD -> BiometricSample.Type.CARD.UNKNOWN
            BiometricTechnology.NFC -> BiometricSample.Type.NFC.UNKNOWN
        }.let { subtype ->
            List(10) { subtype }
        }
    protected val BioDevSpecs.stubSpecificSampleSubtypes
        get() = when (deviceBiometricTechnologies.first()) {
            BiometricTechnology.FINGERPRINT -> listOf(
                BiometricSample.Type.FINGER.RIGHT_INDEX_FINGER,
                BiometricSample.Type.FINGER.RIGHT_MIDDLE_FINGER,
                BiometricSample.Type.FINGER.RIGHT_RING_FINGER,
                BiometricSample.Type.FINGER.RIGHT_LITTLE_FINGER,
                BiometricSample.Type.FINGER.LEFT_INDEX_FINGER,
                BiometricSample.Type.FINGER.LEFT_MIDDLE_FINGER,
                BiometricSample.Type.FINGER.LEFT_RING_FINGER,
                BiometricSample.Type.FINGER.LEFT_LITTLE_FINGER,
                BiometricSample.Type.FINGER.RIGHT_THUMB,
                BiometricSample.Type.FINGER.LEFT_THUMB
            )

            BiometricTechnology.IRIS -> listOf(
                BiometricSample.Type.IRIS.LEFT_IRIS,
                BiometricSample.Type.IRIS.RIGHT_IRIS
            )

            BiometricTechnology.FACIAL -> listOf(
                BiometricSample.Type.FACE.FRONTAL_FACE
            )

            BiometricTechnology.PALM -> listOf(
                BiometricSample.Type.PALM.LEFT_LOWER_PALM,
                BiometricSample.Type.PALM.LEFT_UPPER_PALM,
                BiometricSample.Type.PALM.LEFT_LATERAL_PALM,
                BiometricSample.Type.PALM.RIGHT_LOWER_PALM,
                BiometricSample.Type.PALM.RIGHT_UPPER_PALM,
                BiometricSample.Type.PALM.RIGHT_LATERAL_PALM
            )

            BiometricTechnology.FINGERPRINT_ROLLED -> listOf(
                BiometricSample.Type.ROLLED_FINGER.RIGHT_INDEX_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.RIGHT_MIDDLE_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.RIGHT_RING_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.RIGHT_LITTLE_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.LEFT_INDEX_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.LEFT_MIDDLE_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.LEFT_RING_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.LEFT_LITTLE_FINGER_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.RIGHT_THUMB_ROLLED,
                BiometricSample.Type.ROLLED_FINGER.LEFT_THUMB_ROLLED
            )

            BiometricTechnology.CARD -> listOf(
                BiometricSample.Type.CARD.UNKNOWN
            )

            BiometricTechnology.NFC -> listOf(
                BiometricSample.Type.NFC.UNKNOWN
            )
        }

    open fun shouldBeRunOnBioDev(bioDevSpecs: BioDevSpecs): Boolean = true

    suspend fun runTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ) = runCatching {
        Log.i("TestDefinition", "Running test ($name)")
        performTest(testContext, onWaitUserAction).also {
            if (it is TestStatus.Failure) {
                Log.e("TestDefinition", "Test failed ($name)", it.error)
            }
        }
    }.getOrElse(TestStatus::Failure)

    protected abstract suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus
}