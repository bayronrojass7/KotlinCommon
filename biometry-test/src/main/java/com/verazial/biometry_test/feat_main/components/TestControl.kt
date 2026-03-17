package com.verazial.biometry_test.feat_main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.biometry_test.tests.model.TestDefinitionWithStatus
import com.verazial.biometry_test.tests.model.TestStatus
import com.verazial.biometry_test.ui.theme.Colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestControl(
    modifier: Modifier = Modifier,
    alwaysShowBioDevInfo: Boolean = false,
    availableBioDevSpecs: List<BioDevSpecs>,
    bioDevSelected: BioDevSpecs?,
    tests: List<TestDefinitionWithStatus>,
    onBioDevSelected: (BioDevSpecs?) -> Unit,
    onClearClicked: () -> Unit,
    onRunClicked: () -> Unit
) = Section(modifier.animateContentSize()) {
    var isBioDevInfoExpanded by rememberSaveable { mutableStateOf(alwaysShowBioDevInfo) }
    val isRunningTests by remember(tests) {
        derivedStateOf {
            tests.any { it.status.isOngoing }
        }
    }

    Box {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = !expanded
            }
        ) {
            OutlinedTextField(
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text(text = "Select a biometric device") },
                value = bioDevSelected?.friendlyName ?: "",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableBioDevSpecs.forEach { bioDevSpecs ->
                    DropdownMenuItem(
                        text = { Text(text = bioDevSpecs.friendlyName) },
                        onClick = {
                            expanded = false
                            onBioDevSelected(bioDevSpecs)
                        }
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (bioDevSelected != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!alwaysShowBioDevInfo) {
                IconToggleButton(
                    checked = isBioDevInfoExpanded,
                    onCheckedChange = { isBioDevInfoExpanded = it },
                ) {
                    Icon(
                        imageVector =
                        if (isBioDevInfoExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = "BioDevInfo show/hide",
                    )
                }
                Spacer(modifier = Modifier.weight(0.1f))
            }
            CurrentRunControl(
                modifier = Modifier.weight(1.5f),
                tests = tests,
                onClearClicked = onClearClicked,
            )
            Spacer(modifier = Modifier.weight(0.1f))
            FilledIconButton(
                modifier = Modifier.width(64.dp),
                onClick = onRunClicked,
            ) {
                if (isRunningTests) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Stop tests",
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Run tests",
                    )
                }
            }
        }
        AnimatedVisibility(visible = isBioDevInfoExpanded) {
            BioDevInfo(
                modifier = Modifier.padding(8.dp),
                bioDevSpecs = bioDevSelected
            )
        }
    }
}

@Composable
private fun CurrentRunControl(
    modifier: Modifier = Modifier,
    tests: List<TestDefinitionWithStatus>,
    onClearClicked: () -> Unit,
) = Row(
    modifier = modifier.background(
        color = Colors.AccentDark.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.large
    ),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
) {
    val isWaitingForUser by remember(tests) {
        derivedStateOf {
            tests.any { it.status == TestStatus.WaitingUser }
        }
    }
    val isRunningTests by remember(tests) {
        derivedStateOf {
            tests.any { it.status.isOngoing }
        }
    }
    val testsSuccess by remember(tests) {
        derivedStateOf {
            tests.count { it.status == TestStatus.Success }
        }
    }
    val areAllTestsSuccess by remember(testsSuccess, tests) {
        derivedStateOf {
            testsSuccess == tests.size
        }
    }
    val testsFailure by remember(tests) {
        derivedStateOf {
            tests.count { it.status is TestStatus.Failure }
        }
    }
    val areThereFailedTests by remember(tests) {
        derivedStateOf {
            testsFailure > 0
        }
    }
    val areAllTestsRun by remember(tests) {
        derivedStateOf {
            tests.all { it.status.isFinished }
        }
    }
    IconButton(
        onClick = onClearClicked,
        enabled = areAllTestsRun,
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Clear",
        )
    }
    Text(
        text = tests.size.toString(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge,
    )
    if (areAllTestsRun || isRunningTests) {
        Text(
            text = testsSuccess.toString(),
            color = Colors.TestSuccess,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = testsFailure.toString(),
            color = Colors.TestFailure,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
    when {
        isRunningTests && !isWaitingForUser -> CircularProgressIndicator(
            Modifier
                .size(40.dp)
                .padding(8.dp),
            color = if (areThereFailedTests) Colors.TestFailure else MaterialTheme.colorScheme.primary,
            strokeCap = StrokeCap.Round
        )

        isWaitingForUser -> {
            val warningAnimation = rememberInfiniteTransition(label = "warningAnimation")
            val warningAlpha by warningAnimation.animateFloat(
                label = "warningAlpha",
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                )
            )
            Icon(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
                    .alpha(warningAlpha)
                    .background(color = Colors.TestWaitingUser, shape = CircleShape)
                    .padding(4.dp),
                imageVector = Icons.Rounded.ThumbUp,
                tint = Color.White,
                contentDescription = "Waiting for user"
            )
        }

        areAllTestsSuccess -> {
            Icon(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
                    .background(color = Colors.TestSuccess, shape = CircleShape)
                    .padding(4.dp),
                imageVector = Icons.Rounded.Check,
                tint = Color.White,
                contentDescription = "All tests success"
            )
        }

        areThereFailedTests -> {
            Icon(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
                    .background(color = Colors.TestFailure, shape = CircleShape)
                    .padding(4.dp),
                imageVector = Icons.Rounded.Clear,
                tint = Color.White,
                contentDescription = "There are failed tests"
            )
        }

        else -> Spacer(modifier = Modifier.size(40.dp))
    }
}