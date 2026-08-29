package com.domnex.cfi.bridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

/**
 * Tela bloqueante para CLIENT quando o Bridge ainda não foi provisionado.
 * Não expõe endpoint/token nem qualquer campo técnico.
 *
 * A mensagem vem do orquestrador (MainScreen):
 *  - configured=false  -> "Este acesso ainda não possui um DOMNEX BRIDGE configurado."
 *  - falha de backend  -> erro amigável + "Tente novamente." (botão re-tenta o
 *                         reprovisionamento remoto; a configuração local, se
 *                         existir, nunca é apagada).
 */
@Composable
fun ClientSetupRequiredScreen(
    message: String?,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "DOMNEX BRIDGE", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
            Spacer(Modifier.height(16.dp))
            BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Configuração necessária",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = message
                            ?: "Este acesso ainda não possui um DOMNEX BRIDGE configurado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    GoldPrimaryButton(
                        text = "TENTAR NOVAMENTE",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sair da conta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FailureRose,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Dúvidas? Fale com a Domnex Tech.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}