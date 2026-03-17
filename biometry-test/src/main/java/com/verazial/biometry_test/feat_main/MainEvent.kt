package com.verazial.biometry_test.feat_main

import android.app.Activity
import com.verazial.biometry_test.tests.model.BioDevSpecs

sealed class MainEvent {
    class SelectBioDevSpecs(val selected: BioDevSpecs?): MainEvent()
    class RunTests(val activity: Activity) : MainEvent()
    object Clear: MainEvent()
}