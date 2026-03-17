package com.verazial.biometry_test.feat_main

import com.verazial.biometry_test.tests.allBioDevSpecs
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.biometry_test.tests.model.TestDefinitionWithStatus

data class MainState(
    val bioDevSpecifications: List<BioDevSpecs> = allBioDevSpecs,
    val selectedBioDevSpecs: BioDevSpecs? = null,
    val tests: List<TestDefinitionWithStatus> = emptyList(),
    val isRunningTests: Boolean = false,
)
