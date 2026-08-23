package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.theme.Gold

@Composable
fun BridgeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Gold,
    showAccentDot: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAccentDot) {
            BridgeStatusDot(color = accentColor, size = 6.dp, glowRadius = 4.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}
