package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun SaleFieldTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.05f),
        contentColor = valueColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = TextSecondary.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = value,
                style = if (monospace) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = valueColor,
                maxLines = 2
            )
        }
    }
}

@Composable
internal fun FieldGridTilePair(
    first: (@Composable () -> Unit)?,
    second: (@Composable () -> Unit)?
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) { first?.invoke() ?: Spacer(Modifier) }
        Spacer(Modifier.size(8.dp))
        Box(modifier = Modifier.weight(1f)) { second?.invoke() ?: Spacer(Modifier) }
    }
}
