package com.verazial.biometry_test.feat_main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.verazial.biometry_test.tests.allTestDefinitions
import com.verazial.biometry_test.tests.model.TestDefinitionWithStatus
import com.verazial.biometry_test.tests.model.TestStatus
import com.verazial.biometry_test.ui.theme.Colors

@Composable
fun TestsList(
    modifier: Modifier = Modifier,
    tests: List<TestDefinitionWithStatus>,
    scrollState: LazyListState = rememberLazyListState(),
) = Section(modifier) {
    AnimatedContent(targetState = tests.isEmpty().not(), label = "Tests list") { hasTests ->
        if (hasTests) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = scrollState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = tests
                ) { index, test ->
                    TestItem(
                        modifier = Modifier.fillMaxWidth(),
                        test = test,
                        requestFocus = { scrollState.animateScrollToItem(index) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tests available",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun TestItem(
    modifier: Modifier = Modifier,
    test: TestDefinitionWithStatus,
    requestFocus: suspend () -> Unit = {}
) = Card(
    modifier = modifier
        .fillMaxWidth()
        .then(
            if (test.status == TestStatus.Skipped) Modifier.alpha(0.5f) else Modifier
        ),
    elevation = CardDefaults.elevatedCardElevation(),
    colors = CardDefaults.cardColors(
        containerColor = Colors.SurfaceWhite,
        contentColor = Colors.Text,
    )
) {
    LaunchedEffect(test.status) {
        if (test.status.isOngoing) requestFocus()
    }
    var isExpandedByUser by rememberSaveable {
        mutableStateOf(false)
    }
    val isExpanded by remember(test.status) {
        derivedStateOf {
            isExpandedByUser || test.status.isOngoing
        }
    }
    Column(
        Modifier.clickable { isExpandedByUser = !isExpandedByUser }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            when (test.status) {
                is TestStatus.Running -> CircularProgressIndicator(
                    Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    strokeCap = StrokeCap.Round
                )

                else -> Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(test.status.color, CircleShape)
                ) {
                    Icon(
                        modifier = Modifier.align(Alignment.Center),
                        imageVector = test.status.icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.fillMaxWidth(0.1f))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = test.definition.name,
                    style = MaterialTheme.typography.titleLarge
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Text(
                        text = test.definition.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = test.status is TestStatus.Failure,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            if (test.status is TestStatus.Failure) TestFailureReport(
                failure = test.status,
                isExpanded = isExpanded
            )
        }
    }
}

@Composable
private fun TestFailureReport(
    modifier: Modifier = Modifier,
    failure: TestStatus.Failure,
    isExpanded: Boolean
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .background(Colors.TestFailure)
        .padding(4.dp),
) {
    val clueText = remember(failure) {
        failure.error.localizedMessage
            ?.lineSequence()
            ?.firstOrNull()
            ?: "Unknown error"
    }
    val errorText = remember(failure) {
        failure.error.localizedMessage
            ?.lineSequence()
            ?.drop(1)
            ?.joinToString("\n")
            ?.takeUnless(String::isBlank)
            ?: "-"
    }
    val stackTraceText = remember(failure) {
        failure.error.stackTraceToString()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Error icon",
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = clueText,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontStyle = FontStyle.Italic,
            )
        )
    }
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = errorText,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = stackTraceText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private val TestStatus.color: Color
    get() = when (this) {
        is TestStatus.NotRun -> com.verazial.biometry_test.ui.theme.Colors.TestNotRun
        is TestStatus.Skipped -> com.verazial.biometry_test.ui.theme.Colors.TestSkipped
        is TestStatus.Running -> com.verazial.biometry_test.ui.theme.Colors.TestNotRun
        is TestStatus.WaitingUser -> com.verazial.biometry_test.ui.theme.Colors.TestWaitingUser
        is TestStatus.Success -> com.verazial.biometry_test.ui.theme.Colors.TestSuccess
        is TestStatus.Failure -> com.verazial.biometry_test.ui.theme.Colors.TestFailure
    }
private val TestStatus.icon: ImageVector
    get() = when (this) {
        is TestStatus.NotRun -> Icons.Rounded.PlayArrow
        is TestStatus.Skipped -> Icons.Rounded.Warning
        is TestStatus.Running -> Icons.Rounded.Check
        is TestStatus.WaitingUser -> Icons.Rounded.ThumbUp
        is TestStatus.Success -> Icons.Rounded.Check
        is TestStatus.Failure -> Icons.Rounded.Clear
    }


@Preview
@Composable
private fun TestsListPreview() = TestsList(
    modifier = Modifier.fillMaxSize(),
    tests = allTestDefinitions.map { TestDefinitionWithStatus(it, TestStatus.NotRun) }
)

@Preview
@Composable
private fun TestItemNotRunPreview() = TestItem(
    test = TestDefinitionWithStatus(allTestDefinitions.first(), TestStatus.NotRun)
)

@Preview
@Composable
private fun TestItemSkippedPreview() = TestItem(
    test = TestDefinitionWithStatus(allTestDefinitions.first(), TestStatus.Skipped)
)

@Preview
@Composable
private fun TestItemRunningPreview() = TestItem(
    test = TestDefinitionWithStatus(allTestDefinitions.first(), TestStatus.Running)
)

@Preview
@Composable
private fun TestItemWaitingUserPreview() = TestItem(
    test = TestDefinitionWithStatus(allTestDefinitions.first(), TestStatus.WaitingUser)
)

@Preview
@Composable
private fun TestItemSuccessPreview() = TestItem(
    test = TestDefinitionWithStatus(allTestDefinitions.first(), TestStatus.Success)
)

@Preview
@Composable
private fun TestItemFailurePreview() = TestItem(
    test = TestDefinitionWithStatus(
        allTestDefinitions.first(),
        TestStatus.Failure(Error("Test Failure"))
    )
)