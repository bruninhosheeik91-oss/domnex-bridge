package com.domnex.cfi.bridge.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.NavyCard
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        delay(500)
        onReady()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-70).dp)
                .size(320.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(SuccessGreen.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(360.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Gold.copy(alpha = 0.07f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Gold.copy(alpha = 0.16f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false,
                            ambientColor = Gold.copy(alpha = 0.35f),
                            spotColor = Gold.copy(alpha = 0.45f)
                        )
                        .background(color = NavyCard, shape = RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            color = Gold.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BridgeStatusDot(color = Gold, size = 26.dp, glowRadius = 14.dp)
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "DOMNEX ",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "BRIDGE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Gold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "BY DOMNEX TECH",
                style = MicroCaps,
                color = Gold.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulsingDot(startOffsetMs = 0, color = Gold)
                PulsingDot(startOffsetMs = 180, color = SuccessGreen)
                PulsingDot(startOffsetMs = 360, color = Gold)
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Preparando seu Bridge...",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun PulsingDot(
    startOffsetMs: Int,
    color: Color
) {
    val transition = rememberInfiniteTransition(label = "splashDot")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(startOffsetMs)
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .drawBehind {
                drawCircle(color = color.copy(alpha = alpha))
                drawCircle(color = color, radius = size.minDimension * 0.32f)
            }
    )
}
