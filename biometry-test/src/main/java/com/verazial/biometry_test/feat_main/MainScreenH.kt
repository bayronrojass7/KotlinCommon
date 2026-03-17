package com.verazial.biometry_test.feat_main

import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
fun MainScreenH(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    activity: Activity
) = Row(modifier) {

    val state: MainState by viewModel.state.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    TestControl(
        modifier = Modifier.weight(1f),
        alwaysShowBioDevInfo = true,
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
            .fillMaxHeight()
            .weight(0.02f)
    )
    TestsList(
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f),
        tests = state.tests,
        scrollState = scrollState
    )
}