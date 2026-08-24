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
import com.domnex.cfi.bridge.provisioning.ProvisioningState
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted

/**
 * Card operacional do CLIENT: mostra apenas nome amigável do sistema e status
 * da integração. NUNCA exibe endpoint, token ou parâmetros técnicos.
 */
@Composable
fun IntegrationStatusCard(
    provisioningState: ProvisioningState,
    systemName: String,
    modifier: Modifier = Modifier
) {
    val configured = provisioningState == ProvisioningState.CONFIGURED
    BridgeCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text(
            text = "INTEGRAÇÃO",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (configured) "Sistema conectado" else "Configuração necessária",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = systemName,
            style = MaterialTheme.typography.bodyMedium,
            color = Gold.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BridgeStatusDot(
                color = if (configured) SuccessGreen else Gold,
                size = 8.dp,
                glowRadius = 5.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (configured) {
                    "Integração configurada"
                } else {
                    "Este Bridge ainda não foi configurado pela Domnex Tech."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (configured) SuccessGreen else Gold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
