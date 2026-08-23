package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BridgeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
