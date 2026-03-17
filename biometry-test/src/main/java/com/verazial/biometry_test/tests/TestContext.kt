package com.verazial.biometry_test.tests

import android.app.Activity
import android.content.Context
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.core.interfaces.BiometricDevice

data class TestContext(
    val bioDevSpecs: BioDevSpecs,
    val activity: Activity,
    var device: BiometricDevice? = null,
    var deviceNameBeforeInitialization: String? = null,
    var isInitialized: Boolean = false,
) {
    val context: Context = activity.applicationContext

    private val cleanupTasks = mutableListOf<suspend TestContext.() -> Unit>()

    fun addCleanupTask(task: suspend TestContext.() -> Unit) {
        cleanupTasks.add(task)
    }

    suspend fun cleanup() {
        cleanupTasks.forEach { it() }
    }
}