package com.verazial.biometry_test.feat_main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verazial.biometry_test.tests.TestRunner
import com.verazial.biometry_test.tests.allTestDefinitions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
): AndroidViewModel(application) {

    private val _state: MutableStateFlow<MainState> = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val testRunner: MutableStateFlow<TestRunner?> = MutableStateFlow(null)
    private var runTestsJob: Job? = null


    init {
        // Create TestRunner if a device is selected
        viewModelScope.launch {
            state.mapNotNull { it.selectedBioDevSpecs }.distinctUntilChanged().collect {
                testRunner.value = TestRunner(
                    testDefinitions = allTestDefinitions,
                    bioDevSpecs = it
                )
            }
        }

        // Update state with the tests of testRunner
        viewModelScope.launch {
            testRunner.collectLatest {
                if (it == null) updateState { copy(tests = emptyList()) }
                else it.testsFlow.collect {
                    updateState { copy(tests = it) }
                }
            }
        }
    }

    fun sendEvent(event: MainEvent) {
        when (event) {
            is MainEvent.SelectBioDevSpecs -> {
                updateState {
                    copy(
                        selectedBioDevSpecs = event.selected
                    )
                }
            }
            is MainEvent.RunTests -> {
                if (runTestsJob?.isActive == true) {
                    runTestsJob?.cancel()
                    return
                }
                runTestsJob = viewModelScope.launch {
                    updateState { copy(isRunningTests = true) }
                    testRunner.value?.performTests(
                        activity = event.activity
                    )
                }.apply {
                    invokeOnCompletion {
                        updateState { copy(isRunningTests = false) }
                    }
                }
            }
            is MainEvent.Clear -> {
                testRunner.value?.restartTests()
            }
        }
    }


    private fun updateState(block: MainState.() -> MainState) = _state.update(block)
}