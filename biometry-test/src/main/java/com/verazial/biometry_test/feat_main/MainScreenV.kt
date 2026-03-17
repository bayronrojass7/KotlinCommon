package com.verazial.biometry_test.feat_main

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.verazial.biometry_test.feat_main.components.TestControl
import com.verazial.biometry_test.feat_main.components.TestsList
import kotlinx.coroutines.launch

@Composable
fun MainScreenV(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    activity: Activity
) = Column(modifier) {

    val state: MainState by viewModel.state.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    TestControl(
        modifier = Modifier.fillMaxWidth(),
        availableBioDevSpecs = state.bioDevSpecifications,
        bioDevSelected = state.selectedBioDevSpecs,
        tests = state.tests,
        onBioDevSelected = { viewModel.sendEvent(MainEvent.SelectBioDevSpecs(it)) },
        onClearClicked = { viewModel.sendEvent(MainEvent.Clear) },
        onRunClicked = {
            coroutineScope.launch {
                scrollState.animateScrollToItem(0)
            }
            viewModel.sendEvent(MainEvent.RunTests(activity))
        },
    )
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.02f)
    )
    TestsList(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        tests = state.tests,
        scrollState = scrollState
    )
}