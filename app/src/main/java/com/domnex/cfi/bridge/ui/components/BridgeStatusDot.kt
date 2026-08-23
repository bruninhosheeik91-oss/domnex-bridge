package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.theme.SuccessGreen

@Composable
fun BridgeStatusDot(
    color: Color = SuccessGreen,
    size: Dp = 8.dp,
    glowRadius: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = glowRadius,
                shape = CircleShape,
                clip = false,
                ambientColor = color,
                spotColor = color
            )
            .drawBehind { drawCircle(color) }
    )
}
