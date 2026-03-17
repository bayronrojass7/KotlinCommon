package com.verazial.biometry_test.tests.model

import androidx.compose.runtime.Stable

@Stable
data class TestDefinitionWithStatus(
    val definition: TestDefinition,
    val status: TestStatus = TestStatus.NotRun,
)