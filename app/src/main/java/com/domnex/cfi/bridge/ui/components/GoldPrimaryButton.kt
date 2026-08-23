package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.theme.Gold

@Composable
fun GoldPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = MaterialTheme.shapes.medium
    if (enabled) {
        Surface(
            modifier = modifier.shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Gold.copy(alpha = 0.35f),
                spotColor = Gold.copy(alpha = 0.45f)
            ),
            onClick = onClick,
            shape = shape,
            color = Gold,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Surface(
            modifier = modifier,
            onClick = onClick,
            enabled = false,
            shape = shape,
            color = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White.copy(alpha = 0.30f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
