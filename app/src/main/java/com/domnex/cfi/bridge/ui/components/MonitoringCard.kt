package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.service.BridgeRuntimeState
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun MonitoringCard(
    state: BridgeRuntimeState,
    onPauseBridge: () -> Unit,
    onActivateBridge: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (state) {
        BridgeRuntimeState.ACTIVE -> SuccessGreen
        BridgeRuntimeState.PAUSED -> Gold
        BridgeRuntimeState.NEEDS_PERMISSION -> FailureRose
    }
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
                    text = when (state) {
                        BridgeRuntimeState.ACTIVE -> "Bridge ativo"
                        BridgeRuntimeState.PAUSED -> "Bridge pausado"
                        BridgeRuntimeState.NEEDS_PERMISSION -> "Requer atenção"
                    },
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
                    text = when (state) {
                        BridgeRuntimeState.ACTIVE -> "Monitoramento TON: Ativo"
                        BridgeRuntimeState.PAUSED -> "Monitoramento TON: Pausado"
                        BridgeRuntimeState.NEEDS_PERMISSION -> "Monitoramento TON: Permissão necessária"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
        }

        Spacer(Modifier.height(14.dp))

        when (state) {
            BridgeRuntimeState.ACTIVE -> {
                PauseActivateAction(
                    text = "Pausar Bridge",
                    containerColor = Color.White.copy(alpha = 0.04f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    borderColor = Color.White.copy(alpha = 0.12f),
                    onClick = onPauseBridge
                )
            }
            BridgeRuntimeState.PAUSED -> {
                GoldPrimaryButton(
                    text = "Ativar Bridge",
                    onClick = onActivateBridge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BridgeRuntimeState.NEEDS_PERMISSION -> {
                GoldPrimaryButton(
                    text = "Configurações de Acessibilidade",
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PauseActivateAction(
    text: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}
