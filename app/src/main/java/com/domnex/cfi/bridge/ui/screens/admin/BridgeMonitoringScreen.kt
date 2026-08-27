package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.monitoring.BridgeActivityStatus
import com.domnex.cfi.bridge.monitoring.BridgeCredentialStatus
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringResult
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringRow
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringUiModel
import com.domnex.cfi.bridge.monitoring.activityColor
import com.domnex.cfi.bridge.monitoring.activityStatus
import com.domnex.cfi.bridge.monitoring.credentialColor
import com.domnex.cfi.bridge.monitoring.credentialStatus
import com.domnex.cfi.bridge.monitoring.formatBridgeTimestamp
import com.domnex.cfi.bridge.monitoring.maskTransactionCode
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.components.StatusBadge
import com.domnex.cfi.bridge.ui.components.BadgeTone
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Monitoramento de Bridges — dados REAIS do backend (via Edge Function
 * `bridge-monitoring-proxy`), restrito a DOMNEX_ADMIN + ACTIVE.
 *
 * - O autorizador REAL é o servidor: a proxy valida JWT + bridge_profiles
 *   (role DOMNEX_ADMIN, status ACTIVE) e só então consulta o CFI com o
 *   M2M secret. O APK nunca conhece o secret nem a service_role do CFI.
 * - Sem polling agressivo: carrega ao abrir e ao tocar "Atualizar".
 * - Falha nunca é mascarada como sucesso (estados reais de loading/erro/vazio).
 * - Atividade recente NÃO significa dispositivo online — apenas venda/ingestão
 *   registrada nas últimas 24 horas.
 */
@Composable
fun BridgeMonitoringScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var data by remember { mutableStateOf<BridgeMonitoringUiModel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Defesa em profundidade: a tela fica na área Admin (já restrita), mas
    // reforça que só um DOMNEX_ADMIN vê monitoramento.
    val isAdmin = AuthProvider.authGateway.currentUser()?.role == UserRole.DOMNEX_ADMIN
    val repository = AuthProvider.bridgeMonitoringRepository

    LaunchedEffect(refreshKey) {
        if (!isAdmin) {
            loading = false
            loadError = "Somente um administrador Domnex ativo pode ver o monitoramento."
            return@LaunchedEffect
        }
        if (repository == null) {
            loading = false
            loadError = "Monitoramento indisponível neste build (backend remoto não configurado)."
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.load() }.getOrElse {
                BridgeMonitoringResult.Failed(it.message ?: "Falha ao carregar o monitoramento.")
            }
        }
        when (result) {
            is BridgeMonitoringResult.Success ->
                data = BridgeMonitoringUiModel.from(result.data)
            is BridgeMonitoringResult.Failed -> {
                data = null
                loadError = result.message
            }
        }
        loading = false
    }

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

        Text(text = "MONITORAMENTO DE BRIDGES", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Monitoramento de Bridges",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Dados reais das bridges conectadas ao CFI (somente DOMNEX_ADMIN).",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))

        when {
            loading -> LoadingState()

            loadError != null -> ErrorState(
                message = loadError.orEmpty(),
                onRetry = { refreshKey++ }
            )

            data == null || data!!.empty -> EmptyState(
                onRefresh = { refreshKey++ }
            )

            else -> LoadedContent(
                model = data!!,
                onRefresh = { refreshKey++ }
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Gold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Carregando monitoramento...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Text(
            text = "Não foi possível carregar o monitoramento.",
            style = MaterialTheme.typography.titleSmall,
            color = FailureRose,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        GoldPrimaryButton(text = "TENTAR NOVAMENTE", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Text(
            text = "Nenhuma bridge encontrada.",
            style = MaterialTheme.typography.titleSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Nenhum bridge registrado no monitoramento atual.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        GoldPrimaryButton(text = "ATUALIZAR", onClick = onRefresh, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoadedContent(model: BridgeMonitoringUiModel, onRefresh: () -> Unit) {
    // Resumo com KPIs reais
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiTile(label = "TOTAL", value = model.total, modifier = Modifier.weight(1f))
        KpiTile(label = "ATIVOS", value = model.active, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiTile(
            label = "REQUER ATENÇÃO",
            value = model.attention,
            accent = if (model.attention > 0) FailureRose else SuccessGreen,
            modifier = Modifier.weight(1f)
        )
        KpiTile(label = "INGESTÕES HOJE", value = model.ingestsToday, modifier = Modifier.weight(1f))
    }

    Spacer(Modifier.height(14.dp))
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(
            text = "Atividade recente = venda/ingestão registrada nas últimas 24 horas. " +
                "NÃO indica que o dispositivo está online.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }

    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionCaption("BRIDGES", modifier = Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Text(text = "Atualizar", color = Gold, fontWeight = FontWeight.ExtraBold)
        }
    }
    Spacer(Modifier.height(8.dp))

    model.bridges.forEach { bridge ->
        BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            BridgeRow(bridge)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun KpiTile(label: String, value: Int, modifier: Modifier = Modifier, accent: androidx.compose.ui.graphics.Color = Gold) {
    BridgeCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
            color = accent
        )
    }
}

@Composable
private fun BridgeRow(bridge: BridgeMonitoringRow) {
    val status = bridge.credentialStatus()
    val activity = bridge.activityStatus()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BridgeStatusDot(color = bridge.credentialColor(), size = 8.dp, glowRadius = 5.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = bridge.bridgeName.ifBlank { "Bridge sem nome" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(text = status.label, tone = when (status) {
                BridgeCredentialStatus.ACTIVE -> BadgeTone.Success
                BridgeCredentialStatus.SUSPENDED -> BadgeTone.Warning
                BridgeCredentialStatus.REVOKED -> BadgeTone.Failure
                BridgeCredentialStatus.UNKNOWN -> BadgeTone.Neutral
            })
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = bridge.organizationName.ifBlank { "Organização não informada" },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BridgeStatusDot(color = bridge.activityColor(), size = 7.dp, glowRadius = 4.dp)
            Text(
                text = when (activity) {
                    BridgeActivityStatus.RECENT -> "ATIVIDADE RECENTE"
                    BridgeActivityStatus.NO_RECENT -> "SEM ATIVIDADE RECENTE"
                    BridgeActivityStatus.NEVER -> "NUNCA RECEBEU VENDA"
                },
                style = MaterialTheme.typography.labelMedium,
                color = bridge.activityColor(),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))
        MetricLine("Última atividade", formatBridgeTimestamp(bridge.lastActivityAt))
        MetricLine("Último serial", bridge.lastSerialNumber.ifBlank { "—" })
        MetricLine("Última transação", maskTransactionCode(bridge.lastTransactionCode))
        MetricLine("Ingestões hoje", bridge.ingestsToday.toString())
        MetricLine("Pendências de mapeamento", bridge.pendingMappingCount.toString())
        MetricLine("Falhas", bridge.failedIngests.toString())
        if (bridge.sistemaDestino.isNotBlank()) {
            MetricLine("Sistema de destino", bridge.sistemaDestino)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
