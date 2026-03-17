package com.verazial.biometry_test.feat_main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.core.model.BiometricTechnology

@Composable
fun BioDevInfo(
    modifier: Modifier = Modifier,
    bioDevSpecs: BioDevSpecs
) = LazyColumn(
    modifier = modifier
        .fillMaxWidth()
        .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    item {
        FieldText(
            label = "Biometric Technology",
            value = bioDevSpecs.deviceBiometricTechnologies.map(BiometricTechnology::name).joinToString(separator = "\n"),
        )
    }
    item {
        FieldText(
            label = "ID",
            value = bioDevSpecs.deviceId,
        )
    }
    item {
        FieldText(
            label = "Name",
            value = bioDevSpecs.deviceName ?: "-",
        )
    }
    item {
        FieldText(
            label = "Can Take Unknown Samples",
            value = bioDevSpecs.deviceCanTakeUnknownSamples.toString(),
        )
    }
    item {
        FieldText(
            label = "Reading Timeout",
            value = bioDevSpecs.readingTimeout.toString(),
        )
    }
    item {
        FieldText(
            label = "Max Samples",
            value = bioDevSpecs.deviceMaxSamples.toString(),
        )
    }
    item {
        FieldText(
            label = "Capable Formats",
            value = bioDevSpecs.deviceCapableFormats.joinToString(separator = "\n") {
                it::class.simpleName ?: "Unknown"
            },
        )
    }
    item {
        FieldText(
            label = "Provides Distance Status",
            value = bioDevSpecs.providesDistanceStatus.toString(),
        )
    }
    item {
        FieldText(
            label = "Provides Previews",
            value = bioDevSpecs.providesPreviews.toString(),
        )
    }
}

@Composable
private fun FieldText(
    label: String,
    value: String,
) {
    OutlinedTextField(
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        value = value,
        onValueChange = { }
    )
}