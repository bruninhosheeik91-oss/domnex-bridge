package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlowIconTile(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
    content: @Composable () -> Unit = {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(color = tint, radius = size.minDimension * 0.28f)
        }
    }
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = tint.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = tint.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
