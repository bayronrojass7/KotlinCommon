package com.verazial.biometry_test.tests

import android.app.Activity
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.biometry_test.tests.model.TestDefinition
import com.verazial.biometry_test.tests.model.TestDefinitionWithStatus
import com.verazial.biometry_test.tests.model.TestStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class TestRunner(
    private val testDefinitions: List<TestDefinition>,
    private val bioDevSpecs: BioDevSpecs
) {
    private val _testsFlow: MutableStateFlow<List<TestDefinitionWithStatus>> =
        MutableStateFlow(emptyList())
    val testsFlow = _testsFlow.asStateFlow()

    init {
        restartTests()
    }

    fun restartTests() {
        val testDefinitionsWithStatus = testDefinitions
            .filter { it.shouldBeRunOnBioDev(bioDevSpecs) }
            .map(::TestDefinitionWithStatus)
        _testsFlow.value = testDefinitionsWithStatus
    }

    @OptIn(ExperimentalTime::class)
    suspend fun performTests(
        activity: Activity
    ) {
        restartTests()

        val testContext = TestContext(
            bioDevSpecs = bioDevSpecs,
            activity = activity
        )
        val testsToRun = _testsFlow.value
            .filter { it.status != TestStatus.Skipped }
            .map(TestDefinitionWithStatus::definition)
        testsToRun.forEachIndexed { index, test ->
            coroutineScope {
                updateStatus(index, TestStatus.Running)
                val testResult = async {
                    measureTimedValue {
                        test.runTest(
                            testContext = testContext,
                            onWaitUserAction = {
                                updateStatus(index, TestStatus.WaitingUser)
                            }
                        )
                    }.run {
                        val minDuration = when (value) {
                            is TestStatus.Failure -> 1.seconds
                            TestStatus.Skipped -> 0.2.seconds
                            else -> 0.8.seconds
                        }
                        delay(minDuration - duration)
                        value
                    }
                }
                testResult.invokeOnCompletion {
                    if (it is CancellationException)
                        updateStatus(index, TestStatus.Skipped)
                }
                updateStatus(index, testResult.await())
            }
        }

        runCatching { testContext.cleanup() }
    }

    private fun updateStatus(testIndex: Int, status: TestStatus) {
        val test = _testsFlow.value[testIndex]
        val newValue = TestDefinitionWithStatus(
            definition = test.definition,
            status = status
        )
        _testsFlow.update {
            it.toMutableList().apply { set(testIndex, newValue) }.toList()
        }
    }
}