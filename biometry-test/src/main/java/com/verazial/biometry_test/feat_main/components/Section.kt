package com.verazial.biometry_test.feat_main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verazial.biometry_test.ui.theme.Colors

@Composable
fun Section(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = Card(
    modifier = Modifier
        .padding(4.dp)
        .then(modifier),
    elevation = CardDefaults.elevatedCardElevation(),
    colors = CardDefaults.elevatedCardColors(
        containerColor = Colors.SurfaceBlue,
        contentColor = Colors.Text,
        disabledContainerColor = Colors.SurfaceBlue.copy(alpha = 0.5f),
        disabledContentColor = Colors.Text.copy(alpha = 0.5f)
    )
) {
    Column(
        modifier = Modifier.padding(16.dp),
        content = content
    )
}