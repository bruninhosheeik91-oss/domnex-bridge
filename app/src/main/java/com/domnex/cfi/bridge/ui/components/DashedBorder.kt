package com.domnex.cfi.bridge.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp
): Modifier = drawBehind {
    val stroke = strokeWidth.toPx()
    val radius = cornerRadius.toPx()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(
                    left = stroke / 2f,
                    top = stroke / 2f,
                    right = size.width - stroke / 2f,
                    bottom = size.height - stroke / 2f
                ),
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx())
            )
        )
    )
}
