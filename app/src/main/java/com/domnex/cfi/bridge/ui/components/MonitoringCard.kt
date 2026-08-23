package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun MonitoringCard(
    active: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (active) SuccessGreen else MaterialTheme.colorScheme.error
    BridgeCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "MONITORAMENTO",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (active) "Bridge ativo" else "Monitoramento desativado",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            GlowIconTile(tint = accent)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BridgeStatusDot(color = accent, size = 8.dp, glowRadius = 5.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (active) "Monitorando vendas da TON" else "Ação necessária",
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }

        if (!active) {
            Spacer(Modifier.height(14.dp))
            GoldPrimaryButton(
                text = "Configurações de Acessibilidade",
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
