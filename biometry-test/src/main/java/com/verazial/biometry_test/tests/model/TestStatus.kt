package com.verazial.biometry_test.tests.model

import androidx.compose.runtime.Stable

@Stable
sealed class TestStatus {
    object NotRun : TestStatus()
    object Skipped : TestStatus()
    object Running : TestStatus()
    object WaitingUser : TestStatus()
    object Success : TestStatus()
    data class Failure(val error: Throwable) : TestStatus()

    val isFinished: Boolean
        get() = this is Success
                || this is Failure
                || this is Skipped

    val isOngoing: Boolean
        get() = this is Running
                || this is WaitingUser

    val isStarted: Boolean
        get() = this is NotRun
}
