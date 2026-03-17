package com.verazial.biometry_test.tests

import com.verazial.biometry.IDeviceManager
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.biometry_test.tests.model.TestDefinition
import com.verazial.biometry_test.tests.model.TestStatus
import com.verazial.biometry_test.tests.model.TestStatus.Skipped
import com.verazial.biometry_test.tests.model.TestStatus.Success
import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


object CanBeDetected : TestDefinition() {
    override val name: String = "Can be detected"
    override val description: String = "The device should be detected when scanning for devices"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the app context and the expected device class
        val appContext = testContext.context
        val expectedDevice = testContext.bioDevSpecs.deviceKClass
        // WHEN the devices are scanned
        val device = IDeviceManager(appContext).getDevices()
            .map { it.filterIsInstance<BiometricDevice>() }
            .mapNotNull { detectedDevices ->
                detectedDevices.singleOrNull { it::class == expectedDevice }
            }.firstOrNull()
        // THEN the detected devices should contain the expected device class
        withClue("Detected devices does not contain test subject class") {
            device.shouldNotBeNull()
        }
        // UPDATE TEST CONTEXT
        testContext.device = device
        return Success
    }
}

//region Pre-initialization tests
object PropertiesAreCorrect : TestDefinition() {
    override val name: String = "Properties are correct"
    override val description: String = "The device properties should match the expected values"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the detected device
        val device = testContext.device ?: return Skipped
        // WHEN the device properties are read
        val expectedDeviceId = testContext.bioDevSpecs.deviceId
        val expectedDeviceName = testContext.bioDevSpecs.deviceName
        val expectedDeviceBiometricTechnology =
            testContext.bioDevSpecs.deviceBiometricTechnologies
        // THEN the device properties should match the expected values
        assertSoftly {
            device.run {
                withClue("Device ID does not match") { id shouldBe expectedDeviceId }
                if (expectedDeviceName != null)
                    withClue("Device name is specified and does not match") { name shouldBe expectedDeviceName }
                withClue("Device biometric technologies does not match") {
                    biometricTechnologies shouldBe expectedDeviceBiometricTechnology
                }
            }
        }
        // UPDATE TEST CONTEXT
        testContext.deviceNameBeforeInitialization = device.name

        return Success
    }
}

object IsNotAliveBeforeInit : TestDefinition() {
    override val name: String = "Is not alive before init"
    override val description: String =
        "The device should not be marked as alive before initialization"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the device that has not been initialized yet
        val device = testContext.device ?: return Skipped
        // WHEN the device is checked if it is alive
        val isAlive = withClue("An exception was thrown when checking if the device is alive") {
            shouldNotThrowAny {
                device.stillAlive()
            }
        }
        // THEN the device should not be alive
        withClue("Device is alive before initialization") { isAlive shouldBe false }

        return Success
    }
}

object CanBeClosedBeforeInit : TestDefinition() {
    override val name: String = "Can be closed before init"
    override val description: String =
        "The device should be able to be closed before initialization"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the device that has not been initialized yet
        val device = testContext.device ?: return Skipped
        // THEN the device should be able to be closed
        withClue("An exception was thrown when closing the device") {
            shouldNotThrowAny {
                device.close()
            }
        }

        return Success
    }
}

object CanStopReadBeforeInit : TestDefinition() {
    override val name: String = "Can stop read before init"
    override val description: String =
        "The device should be able to stop reading before initialization, even if it is not reading"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the device that has not been initialized yet
        val device = testContext.device ?: return Skipped
        // THEN the device should be able to stop reading
        withClue("An exception was thrown when stopping read") {
            shouldNotThrowAny {
                device.stopRead()
            }
        }

        return Success
    }
}

object ThrowsOnReadBeforeInit : TestDefinition() {
    override val name: String = "Throws on read before init"
    override val description: String =
        "The device should throw an exception when trying to read before initialization"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the device that has not been initialized yet and the reading parameters
        val device = testContext.device ?: return Skipped
        val binder = testContext.bioDevSpecs.deviceBinder?.invoke(testContext)
        val formats = testContext.bioDevSpecs.templateFormats
        val timeout = 1.seconds
        val samples = testContext.bioDevSpecs.stubSampleSubtype
        // THEN the device should throw an exception when trying to read
        withClue("An exception was not thrown when reading or the exception was not DeviceCommunicationError") {
            shouldThrowExactly<DeviceCommunicationError> {
                device.performRead(
                    timeout = timeout,
                    preferredFormats = formats,
                    targetSamples = samples,
                    binder = binder,
                    enablePreviews = false
                ).last()
            }
        }

        return Success
    }
}
//endregion

object CanBeInitialized : TestDefinition() {
    override val name: String = "Can be initialized"
    override val description: String =
        "The device should be able to be initialized, starting the communication with the device"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the device that has not been initialized yet
        val device = testContext.device ?: return Skipped
        // THEN the device should be able to be initialized
        withClue("An exception was thrown when initializing the device") {
            shouldNotThrowAny {
                device.initialize()
            }
        }
        // UPDATE TEST CONTEXT
        testContext.isInitialized = true
        testContext.addCleanupTask { device.close() }

        return Success
    }
}

//region Post-initialization tests
object IsAliveAfterInit : TestDefinition() {
    override val name: String = "Is alive after init"
    override val description: String =
        "The device should be marked as alive after initialization"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        // WHEN the device is checked if it is alive
        val isAlive = withClue("An exception was thrown when checking if the device is alive") {
            shouldNotThrowAny {
                device.stillAlive()
            }
        }
        // THEN the device should be alive
        withClue("Device is not alive after initialization") { isAlive shouldBe true }

        return Success
    }
}

object CanProvideMaxSamples : TestDefinition() {
    override val name: String = "Can provide max samples"
    override val description: String =
        "The device should be able to provide the maximum number of samples it supports, and it should match the expected value"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        // WHEN the device is asked for the maximum number of samples it supports
        val maxSamples =
            withClue("An exception was thrown when asking for the maximum number of samples") {
                shouldNotThrowAny {
                    device.getMaxSamples()
                }
            }
        // THEN the device should provide the maximum number of samples it supports
        withClue("Provided maximum number of samples does not match the expected value") {
            maxSamples shouldBe testContext.bioDevSpecs.deviceMaxSamples
        }

        return Success
    }
}

object NameDidNotChange : TestDefinition() {
    override val name: String = "Name did not change"
    override val description: String =
        "The device should not change its name after initialization"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device and the device name before initialization
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        val nameBeforeInit = testContext.deviceNameBeforeInitialization
            ?: return Skipped
        // THEN the device should provide the same name as the one provided in the specs
        withClue("Device name changed after initialization") {
            device.name shouldBe nameBeforeInit
        }

        return Success
    }
}
//endregion

object HonorsTimeout : TestDefinition() {
    override val name: String = "Honors timeout"
    override val description: String =
        "The device should honor the timeout parameter when performing a biometric capture"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device and the reading parameters
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        val binder = testContext.bioDevSpecs.deviceBinder?.invoke(testContext)
        val formats = testContext.bioDevSpecs.templateFormats
        val samples = testContext.bioDevSpecs.stubSampleSubtype
        val timeout = 1.seconds
        // WHEN the device is asked to perform a biometric capture with a timeout and the timeout is exceeded
        val samplesTakenResult = runCatching {
            withTimeout(2.seconds) {
                device.performRead(
                    timeout = timeout,
                    preferredFormats = formats,
                    targetSamples = samples,
                    binder = binder,
                    enablePreviews = true
                ).count()
            }
        }
        // THEN the device should throw a ReadingTimeout exception after the specified timeout
        withClue("Timeout specified was exceeded") {
            shouldThrowExactly<ReadingTimeout>(samplesTakenResult::getOrThrow)
        }

        return Success
    }
}

object CanCancelOngoingRead : TestDefinition() {
    override val name: String = "Can cancel ongoing read"
    override val description: String =
        "The device should be able to cancel an ongoing biometric capture"

    override fun shouldBeRunOnBioDev(bioDevSpecs: BioDevSpecs): Boolean = true

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device and the reading parameters
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        val binder = testContext.bioDevSpecs.deviceBinder?.invoke(testContext)
        val formats = testContext.bioDevSpecs.templateFormats
        val timeout = 2.seconds
        val samples = testContext.bioDevSpecs.stubSampleSubtype
        // WHEN the device is asked to perform a biometric capture and then cancel it
        val samplesTakenResult = runCatching {
            coroutineScope {
                val firstReadJob = launch {
                    try {
                        device.performRead(
                            timeout = timeout,
                            preferredFormats = formats,
                            targetSamples = samples,
                            binder = binder,
                            enablePreviews = false
                        )
                    } catch (e: ReadingTimeout) {
                        // Ignore
                    }
                }
                device.stopRead()
                val secondReadJob = async {
                    runCatching {
                        device.performRead(
                            timeout = timeout,
                            preferredFormats = formats,
                            targetSamples = samples,
                            binder = binder,
                            enablePreviews = false
                        )
                    }
                }
                firstReadJob.join()
                secondReadJob.await().recoverCatching {
                    if (it !is ReadingTimeout) throw it
                    else Unit
                }.getOrThrow()
            }
        }
        // THEN the device should not take too long to cancel the ongoing biometric capture
        withClue("Cancellation threw an exception") {
            shouldNotThrowAny(samplesTakenResult::getOrThrow)
        }

        return Success
    }
}

//region Read tests

open class CaptureTestDefinition(
    private val type: CaptureType = CaptureType.ANY,
    private val samplesAmount: Int,
    private val timeout: Duration = 5.seconds,
    private val useUnknownSubtypes: Boolean = true,
    private val useSequential: Boolean = false
) : TestDefinition() {
    override val name: String = buildString {
        append("Can take ")
        append(samplesAmount)
        if (!useUnknownSubtypes) append(" specific")
        append(" ")
        append(
            when (type) {
                CaptureType.TEMPLATE, CaptureType.ALL_TEMPLATES -> "template"
                CaptureType.IMAGE -> "image"
                CaptureType.ANY -> "sample"
            }
        )
        if (samplesAmount > 1) append("s")
        if (useSequential) append(" sequentially")
    }
    override val description: String = buildString {
        append("The device should be able to perform a biometric capture of ")
        append(samplesAmount)
        if (useUnknownSubtypes) append(" unknown") else append(" specific")
        append(" sample")
        if (samplesAmount > 1) append("s")
        if (type != CaptureType.ANY) {
            append(" in ")
            append(
                when (type) {
                    CaptureType.TEMPLATE -> "template"
                    CaptureType.IMAGE -> "image"
                    else -> ""
                }
            )
            append(" format")
        }
        if (useSequential) append(" sequentially")
    }

    override fun shouldBeRunOnBioDev(bioDevSpecs: BioDevSpecs): Boolean = bioDevSpecs.run {
        val formatOk = when (type) {
            CaptureType.TEMPLATE, CaptureType.ALL_TEMPLATES -> canTakeTemplate
            CaptureType.IMAGE -> canTakeImage
            CaptureType.ANY -> canTakeTemplate || canTakeImage
        }
        val sequentialNeeded = samplesAmount > bioDevSpecs.deviceMaxSamples
        val sequentialOk = sequentialNeeded == useSequential
        val irisMaxReadOk =
            samplesAmount <= 2 || BiometricTechnology.IRIS !in deviceBiometricTechnologies

        formatOk && sequentialOk && irisMaxReadOk
    }

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device and the reading parameters
        if (!testContext.isInitialized) return Skipped
        if (!testContext.bioDevSpecs.deviceCanTakeUnknownSamples && useUnknownSubtypes)
            return TestStatus.Failure(Error("Device cannot take unknown samples"))
        val device = testContext.device ?: return Skipped
        val binder = testContext.bioDevSpecs.deviceBinder?.invoke(testContext)
        val formats = when (type) {
            CaptureType.TEMPLATE, CaptureType.ALL_TEMPLATES -> testContext.bioDevSpecs.templateFormats
            CaptureType.IMAGE -> listOf(testContext.bioDevSpecs.imageFormat)
            CaptureType.ANY -> testContext.bioDevSpecs.deviceCapableFormats
        }
        val timeout = testContext.bioDevSpecs.readingTimeout
        val samples = when {
            useUnknownSubtypes -> testContext.bioDevSpecs.stubUnknownSampleSubtypes
            else -> testContext.bioDevSpecs.stubSpecificSampleSubtypes
        }.take(samplesAmount)
        // WHEN the device is asked to perform a biometric capture
        onWaitUserAction()
        val takes: List<Result<List<BiometricSample>>> =
            samples.chunked(testContext.bioDevSpecs.deviceMaxSamples).flatMap { takeSamples ->
                val takesFormat = if (type == CaptureType.ALL_TEMPLATES) formats.map(::listOf)
                else listOf(formats)
                takesFormat.map { takeFormat ->
                    runCatching {
                        println(
                            """
                            Performing biometric capture with the following parameters:
                                timeout: $timeout
                                preferredFormats: $takeFormat
                                targetSamples: $takeSamples
                                binder: $binder
                        """.trimIndent()
                        )
                        device.performRead(
                            timeout = timeout,
                            preferredFormats = takeFormat,
                            targetSamples = takeSamples,
                            binder = binder,
                            enablePreviews = false
                        ).last().samples
                    }
                }
            }
        // THEN the properties of samples captured should match the expected values
        takes.forEachIndexed { idx, samplesTakenResult ->
            withClue("Timeout when performing biometric capture") {
                samplesTakenResult.onFailure { it shouldNotBe ReadingTimeout }
            }
            val samplesTaken =
                withClue("An exception was thrown when performing biometric capture") {
                    shouldNotThrowAny {
                        samplesTakenResult.exceptionOrNull()?.cause?.let { throw it }
                        samplesTakenResult.getOrThrow()
                    }
                }
            samplesTaken.forEach {
                withClue("The format of the provided sample does not match the expected value") {
                    if (type == CaptureType.ALL_TEMPLATES)
                        it.format shouldBe testContext.bioDevSpecs.templateFormats[idx]
                    else it.format shouldBeIn formats
                }
                if (useUnknownSubtypes.not())
                    withClue("The subtype of the provided sample does not match the expected value") {
                        it.subtype shouldBeIn samples
                    }
            }
        }

        withClue("The number of samples provided does not match the expected value") {
            takes.sumOf { it.getOrThrow().size } shouldBeExactly samplesAmount
        }

        return Success
    }

    enum class CaptureType {
        IMAGE, TEMPLATE, ALL_TEMPLATES, ANY
    }
}

object CanTake1Image : CaptureTestDefinition(
    type = CaptureType.IMAGE,
    samplesAmount = 1
)

object CanTake1Template : CaptureTestDefinition(
    type = CaptureType.ALL_TEMPLATES,
    samplesAmount = 1
)

object CanTake2Samples : CaptureTestDefinition(
    type = CaptureType.ANY,
    samplesAmount = 2
)

object CanTake4Samples : CaptureTestDefinition(
    type = CaptureType.ANY,
    samplesAmount = 4
)

object CanTake1ImageWithKnownSubtype : CaptureTestDefinition(
    type = CaptureType.IMAGE,
    samplesAmount = 1,
    useUnknownSubtypes = false
)

object CanTake1TemplateWithKnownSubtype : CaptureTestDefinition(
    type = CaptureType.TEMPLATE,
    samplesAmount = 1,
    useUnknownSubtypes = false
)

object CanTake2SamplesWithKnownSubtype : CaptureTestDefinition(
    type = CaptureType.ANY,
    samplesAmount = 2,
    useUnknownSubtypes = false
)

object CanTake4SamplesWithKnownSubtype : CaptureTestDefinition(
    type = CaptureType.ANY,
    samplesAmount = 4,
    useUnknownSubtypes = false
)

object CanTake2SamplesSequentially : CaptureTestDefinition(
    samplesAmount = 2,
    useSequential = true
)

object CanTake4SamplesSequentially : CaptureTestDefinition(
    samplesAmount = 4,
    useSequential = true
)

//endregion

object ProvidesPreviews : TestDefinition() {
    override val name: String = "Does provide previews"
    override val description: String =
        "The device should provide previews when performing a biometric capture"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        // THEN the device should report the distance
        delay(2.seconds) // Ensure user is not taking a sample
        var count = 0
        val result = runCatching {
            device.performRead(
                timeout = 5.seconds,
                preferredFormats = testContext.bioDevSpecs.deviceCapableFormats,
                targetSamples = testContext.bioDevSpecs.stubSampleSubtype,
                binder = null,
                enablePreviews = true
            ).collect { count++ }
        }
        withClue("An exception was thrown when performing biometric capture") {
            shouldThrowExactly<ReadingTimeout>(result::getOrThrow)
        }
        withClue("No previews were provided") {
            count shouldBeGreaterThan 1
        }

        return Success
    }

    override fun shouldBeRunOnBioDev(bioDevSpecs: BioDevSpecs) =
        bioDevSpecs.providesPreviews
}

object ProvidesDistance : TestDefinition() {
    override val name: String = "Does provide distance"
    override val description: String =
        "The device should not always report the same distance when performing a biometric capture"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        // THEN the device should report the distance
        delay(2.seconds) // Ensure user is not taking a sample
        onWaitUserAction()
        var count = 0
        val result = runCatching {
            device.performRead(
                timeout = 5.seconds,
                preferredFormats = testContext.bioDevSpecs.deviceCapableFormats,
                targetSamples = testContext.bioDevSpecs.stubSampleSubtype,
                binder = null,
                enablePreviews = true
            ).map { it.distanceStatus }
                .distinctUntilChanged()
                .take(2)
                .collect { count++ }
        }
        withClue("An exception was thrown when performing biometric capture") {
            shouldThrowExactly<ReadingTimeout>(result::getOrThrow)
        }
        withClue("The reported distance did not change") {
            count shouldBe 2
        }

        return Success
    }

    override fun shouldBeRunOnBioDev(bioDevSpecs: BioDevSpecs) =
        bioDevSpecs.providesDistanceStatus
}

object CanStopReadWithoutStartingIt : TestDefinition() {
    override val name: String = "Can stop a read without starting it"
    override val description: String =
        "The device should be able to stop a read without starting it"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        // THEN the device should stop the read
        withClue("The device threw an exception when stopping the read") {
            shouldNotThrowAny { device.stopRead() }
        }

        return Success
    }
}

object CanCloseDevice : TestDefinition() {
    override val name: String = "Can close the device"
    override val description: String =
        "The device should be able to close itself"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the initialized device
        if (!testContext.isInitialized) return Skipped
        val device = testContext.device ?: return Skipped
        // THEN the device should close itself
        withClue("The device threw an exception when closing") {
            shouldNotThrowAny { device.close() }
        }

        return Success
    }
}

//region Equality tests

object EqualsToItself : TestDefinition() {
    override val name: String = "Equals to itself"
    override val description: String =
        "The device should be equal to itself"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN the device
        val device = testContext.device
        // THEN the device should be equal to itself
        device shouldBe device

        return Success
    }
}

object EqualsToOtherWithSameId : TestDefinition() {
    override val name: String = "Equals to other device with same id"
    override val description: String =
        "The device should be equal to another device with the same id"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN two devices with the same id
        val device = testContext.device ?: return Skipped
        val otherDevice = object : BiometricDevice() {
            override val biometricTechnologies: Set<BiometricTechnology>
                get() = error("Not implemented")
            override val id: String
                get() = device.id
            override val name: String
                get() = error("Not implemented")

            override suspend fun close() = error("Not implemented")

            override suspend fun getMaxSamples(): Int = error("Not implemented")

            override suspend fun initialize() = error("Not implemented")

            override suspend fun performRead(
                timeout: Duration,
                preferredFormats: List<BiometricSample.Type.Format>,
                targetSamples: List<BiometricSample.Type.Subtype>,
                binder: BiometricDeviceViewBinder?,
                enablePreviews: Boolean
            ): Flow<BiometricCapture> = error("Not implemented")

            override suspend fun stillAlive(): Boolean = error("Not implemented")

            override suspend fun stopRead() = error("Not implemented")
        }
        // THEN the devices should be equal
        device shouldBe otherDevice

        return Success
    }
}

object NotEqualsToOtherWithDifferentId : TestDefinition() {
    override val name: String = "Not equals to other device with different id"
    override val description: String =
        "The device should not be equal to another device with a different id"

    override suspend fun performTest(
        testContext: TestContext,
        onWaitUserAction: () -> Unit
    ): TestStatus {
        // GIVEN two devices with different ids
        val device = testContext.device ?: return Skipped
        val otherDevice = object : BiometricDevice() {
            override val biometricTechnologies: Set<BiometricTechnology>
                get() = error("Not implemented")
            override val id: String
                get() = device.id + "different"
            override val name: String
                get() = error("Not implemented")

            override suspend fun close() = error("Not implemented")

            override suspend fun getMaxSamples(): Int = error("Not implemented")

            override suspend fun initialize() = error("Not implemented")

            override suspend fun performRead(
                timeout: Duration,
                preferredFormats: List<BiometricSample.Type.Format>,
                targetSamples: List<BiometricSample.Type.Subtype>,
                binder: BiometricDeviceViewBinder?,
                enablePreviews: Boolean
            ): Flow<BiometricCapture> = error("Not implemented")

            override suspend fun stillAlive(): Boolean = error("Not implemented")

            override suspend fun stopRead() = error("Not implemented")
        }
        // THEN the devices should not be equal
        device shouldNotBe otherDevice

        return Success
    }
}

//endregion

val allTestDefinitions: List<TestDefinition> = listOf(
    CanBeDetected,
    PropertiesAreCorrect,
    IsNotAliveBeforeInit,
    CanBeClosedBeforeInit,
    CanStopReadBeforeInit,
    ThrowsOnReadBeforeInit,
    CanBeInitialized,
    IsAliveAfterInit,
    CanProvideMaxSamples,
    NameDidNotChange,
    CanTake1Image,
    /*CanTake1Template,
    CanTake2Samples,
    CanTake4Samples,
    CanTake1ImageWithKnownSubtype,
    CanTake1TemplateWithKnownSubtype,
    CanTake2SamplesWithKnownSubtype,
    CanTake4SamplesWithKnownSubtype,
    CanTake2SamplesSequentially,
    CanTake4SamplesSequentially,*/
    ProvidesDistance,
    ProvidesPreviews,
    HonorsTimeout,
    CanCancelOngoingRead,
    CanCloseDevice,
    EqualsToItself,
    EqualsToOtherWithSameId,
    NotEqualsToOtherWithDifferentId
)