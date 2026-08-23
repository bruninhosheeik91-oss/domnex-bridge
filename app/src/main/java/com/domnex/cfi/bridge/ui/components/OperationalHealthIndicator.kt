package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.WarningAmber

enum class HealthState { Ok, Attention, ActionNeeded }

@Composable
fun OperationalHealthIndicator(
    isRunning: Boolean,
    lastLog: String,
    modifier: Modifier = Modifier
) {
    val logLower = lastLog.lowercase()
    val logHasError = listOf("erro", "falha", "exception", "crash").any { logLower.contains(it) }
    val state = when {
        !isRunning -> HealthState.ActionNeeded
        logHasError -> HealthState.Attention
        else -> HealthState.Ok
    }

    val (accent, label) = when (state) {
        HealthState.Ok -> SuccessGreen to "Tudo funcionando"
        HealthState.Attention -> WarningAmber to "Verificar último evento"
        HealthState.ActionNeeded -> FailureRose to "Ação necessária"
    }
    val subLabel = if (isRunning) {
        "Serviço de captura em execução"
    } else {
        "Serviço de captura parado"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BridgeStatusDot(color = accent, size = 9.dp, glowRadius = 6.dp)
        Spacer(Modifier.padding(end = 8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
