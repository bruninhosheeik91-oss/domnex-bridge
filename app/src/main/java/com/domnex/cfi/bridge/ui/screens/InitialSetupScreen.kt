package com.domnex.cfi.bridge.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.service.SaleSender
import com.domnex.cfi.bridge.service.TonAccessibilityService
import com.domnex.cfi.bridge.ui.components.BadgeTone
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.GlowIconTile
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.IntegrationConfigForm
import com.domnex.cfi.bridge.ui.components.StatusBadge
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted

@Composable
fun InitialSetupScreen(
    onEnterBridge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRunning by TonAccessibilityService.isRunning.collectAsState()
    var configSaved by remember {
        mutableStateOf(
            SaleSender.getBaseUrl(context).isNotBlank() &&
                SaleSender.getBridgeToken(context).isNotBlank()
        )
    }
    val canEnter = configSaved && isRunning

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        Text(
            text = "Configurar Domnex Bridge",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Três passos rápidos para colocar o Bridge em operação.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(Modifier.height(22.dp))

        BridgeCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowIconTile(tint = Gold)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sistema de destino",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Defina para onde as vendas capturadas na TON serão enviadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        BridgeCard(contentPadding = PaddingValues(16.dp)) {
            SectionCaption("CONFIGURAÇÃO DA INTEGRAÇÃO")
            Spacer(Modifier.height(12.dp))
            IntegrationConfigForm(onConfiguredChange = { configSaved = it })
        }

        Spacer(Modifier.height(14.dp))

        BridgeCard(contentPadding = PaddingValues(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    SectionCaption("MONITORAMENTO TON")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BridgeStatusDot(
                            color = if (isRunning) SuccessGreen else FailureRose,
                            size = 8.dp,
                            glowRadius = 5.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "Monitoramento ativo" else "Monitoramento desativado",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                StatusBadgeProxy(isRunning)
            }

            if (!isRunning) {
                Spacer(Modifier.height(14.dp))
                GoldPrimaryButton(
                    text = "ATIVAR MONITORAMENTO",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        GoldPrimaryButton(
            text = "ENTRAR NO DOMNEX BRIDGE",
            onClick = onEnterBridge,
            enabled = canEnter,
            modifier = Modifier.fillMaxWidth()
        )

        if (!canEnter) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Salve a configuração e ative o monitoramento para continuar.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun StatusBadgeProxy(active: Boolean) {
    if (active) {
        StatusBadge(text = "Ativo", tone = BadgeTone.Success, showDot = true)
    } else {
        StatusBadge(text = "Inativo", tone = BadgeTone.Failure, showDot = true)
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.Bold
    )
}
