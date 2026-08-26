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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.data.SaleHistory
import com.domnex.cfi.bridge.data.local.CapturedSaleEntity
import com.domnex.cfi.bridge.diagnostics.AccessibilityPermissionState
import com.domnex.cfi.bridge.diagnostics.BridgeDiagnosticsLogic
import com.domnex.cfi.bridge.diagnostics.BridgeSwitchState
import com.domnex.cfi.bridge.diagnostics.DiagnosticsOverall
import com.domnex.cfi.bridge.diagnostics.DiagnosticsSignals
import com.domnex.cfi.bridge.diagnostics.MonitorServiceState
import com.domnex.cfi.bridge.provisioning.BridgeProvisioning
import com.domnex.cfi.bridge.provisioning.ProvisioningState
import com.domnex.cfi.bridge.service.BridgeForegroundService
import com.domnex.cfi.bridge.service.BridgeMonitor
import com.domnex.cfi.bridge.service.TonAccessibilityService
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

/**
 * Diagnóstico do Bridge — somente estados REAIS do aplicativo.
 * Sem endpoint/token/URL/payload; sem healthcheck inventado; sem polling novo
 * (todos os sinais vêm de StateFlows/Flow já existentes).
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Sinais reais existentes (mesmos flows da Home aprovada).
    val accessibilityRunning by TonAccessibilityService.isRunning.collectAsState()
    val monitorEnabled by BridgeMonitor.enabled.collectAsState()
    val lastLog by TonAccessibilityService.lastLog.collectAsState()

    val historyRepository = remember { SaleHistory.get(context) }
    val storedSales by historyRepository.observeAll().collectAsState(initial = emptyList())

    val provisioningRepository = remember { BridgeProvisioning.get(context) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val provisioningState = remember(refreshKey) { provisioningRepository.state() }
    val connectedSystem = remember(refreshKey) {
        BridgeDiagnosticsLogic.connectedSystemName(
            provisioningRepository.load().targetSystemName
        )
    }

    val signals = DiagnosticsSignals(
        monitorEnabled = monitorEnabled,
        accessibilityRunning = accessibilityRunning,
        provisioning = provisioningState
    )
    val overall = BridgeDiagnosticsLogic.overall(signals)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(text = "← Voltar", color = Gold, fontWeight = FontWeight.ExtraBold)
            }
        }

        Text(text = "DIAGNÓSTICO", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Diagnóstico do Bridge",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))
        OverallCard(overall)
        Spacer(Modifier.height(14.dp))

        MonitoringSection(signals)
        Spacer(Modifier.height(14.dp))

        IntegrationSection(provisioningState, connectedSystem)
        Spacer(Modifier.height(14.dp))

        LastActivitySection(lastLog)
        Spacer(Modifier.height(14.dp))

        LocalHistorySection(storedSales.size, storedSales.firstOrNull())
        Spacer(Modifier.height(14.dp))

        ActionsSection(
            signals = signals,
            onActivateBridge = {
                BridgeMonitor.setEnabled(context, true)
                BridgeForegroundService.start(context)
            },
            onPauseBridge = {
                BridgeMonitor.setEnabled(context, false)
                BridgeForegroundService.stop(context)
            },
            onOpenAccessibilitySettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
            onRetry = {
                // Relê o estado persistido real e reavalia a integração.
                BridgeMonitor.refresh(context)
                refreshKey++
            }
        )

        Spacer(Modifier.height(30.dp))
    }
}

// ── Estado geral ─────────────────────────────────────────────

@Composable
private fun OverallCard(overall: DiagnosticsOverall) {
    val accent = when (overall) {
        DiagnosticsOverall.ALL_OK -> SuccessGreen
        DiagnosticsOverall.PAUSED -> Gold
        DiagnosticsOverall.ACTION_NEEDED -> FailureRose
    }
    val label = when (overall) {
        DiagnosticsOverall.ALL_OK -> "Tudo funcionando"
        DiagnosticsOverall.PAUSED -> "Bridge pausado"
        DiagnosticsOverall.ACTION_NEEDED -> "Ação necessária"
    }
    val subtitle = when (overall) {
        DiagnosticsOverall.ALL_OK -> "Monitoramento e integração operando"
        DiagnosticsOverall.PAUSED -> "Ative o Bridge para retomar o monitoramento."
        DiagnosticsOverall.ACTION_NEEDED -> "Verifique os itens destacados abaixo."
    }

    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BridgeStatusDot(color = accent, size = 10.dp, glowRadius = 6.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

// ── Monitoramento ────────────────────────────────────────────

@Composable
private fun MonitoringSection(signals: DiagnosticsSignals) {
    SectionCard(title = "MONITORAMENTO") {
        val bridgeSwitch = BridgeDiagnosticsLogic.bridgeSwitch(signals.monitorEnabled)
        StatusRow(
            label = "Bridge",
            value = when (bridgeSwitch) {
                BridgeSwitchState.ACTIVE -> "Ativo"
                BridgeSwitchState.PAUSED -> "Pausado"
            },
            accent = when (bridgeSwitch) {
                BridgeSwitchState.ACTIVE -> SuccessGreen
                BridgeSwitchState.PAUSED -> Gold
            }
        )
        Spacer(Modifier.height(10.dp))
        val accessibility = BridgeDiagnosticsLogic.accessibility(signals.accessibilityRunning)
        StatusRow(
            label = "Acessibilidade TON",
            value = when (accessibility) {
                AccessibilityPermissionState.GRANTED -> "Ativa"
                AccessibilityPermissionState.NEEDED -> "Permissão necessária"
            },
            accent = when (accessibility) {
                AccessibilityPermissionState.GRANTED -> SuccessGreen
                AccessibilityPermissionState.NEEDED -> FailureRose
            }
        )
        Spacer(Modifier.height(10.dp))
        val service = BridgeDiagnosticsLogic.monitorService(
            signals.monitorEnabled,
            signals.accessibilityRunning
        )
        StatusRow(
            label = "Serviço de monitoramento",
            value = when (service) {
                MonitorServiceState.RUNNING -> "Em execução"
                MonitorServiceState.STOPPED -> "Parado"
            },
            accent = when (service) {
                MonitorServiceState.RUNNING -> SuccessGreen
                MonitorServiceState.STOPPED -> Gold
            }
        )
    }
}

// ── Integração ───────────────────────────────────────────────

@Composable
private fun IntegrationSection(
    provisioningState: ProvisioningState,
    connectedSystem: String?
) {
    SectionCard(title = "INTEGRAÇÃO") {
        StatusRow(
            label = "Integração",
            value = BridgeDiagnosticsLogic.integrationLabel(provisioningState),
            accent = when (provisioningState) {
                ProvisioningState.CONFIGURED -> SuccessGreen
                ProvisioningState.UNCONFIGURED -> Gold
                ProvisioningState.ERROR -> FailureRose
            }
        )
        if (connectedSystem != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Sistema conectado: $connectedSystem",
                style = MaterialTheme.typography.bodyMedium,
                color = Gold.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Última atividade (fonte auxiliar sanitizada) ─────────────

@Composable
private fun LastActivitySection(lastLog: String) {
    SectionCard(title = "ÚLTIMA ATIVIDADE") {
        val safe = BridgeDiagnosticsLogic.sanitizeLastLog(lastLog)
        if (safe == null) {
            Text(
                text = "Nenhuma atividade registrada nesta sessão.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            Text(
                text = safe,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

// ── Histórico local (derivado do Room, sem polling novo) ─────

@Composable
private fun LocalHistorySection(count: Int, latest: CapturedSaleEntity?) {
    SectionCard(title = "HISTÓRICO LOCAL") {
        if (count <= 0 || latest == null) {
            Text(
                text = "Nenhuma venda no histórico local.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            Text(
                text = if (count == 1) "1 venda armazenada" else "$count vendas armazenadas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            val parts = listOfNotNull(
                latest.valorVenda.trim().ifEmpty { null },
                latest.dataHora.trim().ifEmpty { null }
            )
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Última captura: ${parts.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

// ── Ações (reutilizam exatamente as implementações da Home) ──

@Composable
private fun ActionsSection(
    signals: DiagnosticsSignals,
    onActivateBridge: () -> Unit,
    onPauseBridge: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRetry: () -> Unit
) {
    SectionCard(title = "AÇÕES") {
        when {
            !signals.monitorEnabled -> {
                GoldPrimaryButton(
                    text = "Ativar Bridge",
                    onClick = onActivateBridge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            !signals.accessibilityRunning -> {
                GoldPrimaryButton(
                    text = "Abrir configurações de acessibilidade",
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                ActionButton(text = "Pausar Bridge", onClick = onPauseBridge)
            }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton(text = "Tentar novamente", onClick = onRetry)
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.04f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
        )
    }
}

// ── Blocos visuais compartilhados ────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BridgeStatusDot(color = accent, size = 8.dp, glowRadius = 5.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
