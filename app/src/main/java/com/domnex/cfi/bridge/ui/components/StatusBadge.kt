package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.theme.InfoBlue

enum class BadgeTone {
    Success,
    Warning,
    Failure,
    Info,
    Neutral
}

@Composable
fun badgeToneColor(tone: BadgeTone): Color = when (tone) {
    BadgeTone.Success -> MaterialTheme.colorScheme.secondary
    BadgeTone.Warning -> MaterialTheme.colorScheme.tertiary
    BadgeTone.Failure -> MaterialTheme.colorScheme.error
    BadgeTone.Info -> InfoBlue
    BadgeTone.Neutral -> Color.White.copy(alpha = 0.6f)
}

@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone = BadgeTone.Success,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    val accent = badgeToneColor(tone)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = accent.copy(alpha = 0.15f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                BridgeStatusDot(color = accent, size = 6.dp, glowRadius = 4.dp)
                Spacer(Modifier.padding(end = 3.dp))
            }
            if (leading != null) leading()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
