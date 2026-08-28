package com.domnex.cfi.bridge.monitoring

import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.WarningAmber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Modelos da resposta REAL de monitoramento de bridges, repassada pela Edge
 * Function `bridge-monitoring-proxy` (que valida DOMNEX_ADMIN+ACTIVE no servidor
 * e faz a chamada server-to-server ao CFI com o M2M_MONITORING_SECRET).
 *
 * Nenhum dado é inventado aqui: tudo vem do JSON sanitizado do CFI. Campos
 * desconhecidos/extras são ignorados ([ignoreUnknownKeys]); campos ausentes
 * assumem valores neutros — jamais fake sucesso.
 */

@Serializable
data class BridgeMonitoringResponse(
    @SerialName("totalBridges") val totalBridges: Int = 0,
    @SerialName("activeBridges") val activeBridges: Int = 0,
    @SerialName("suspendedBridges") val suspendedBridges: Int = 0,
    @SerialName("revokedBridges") val revokedBridges: Int = 0,
    @SerialName("recentActivityBridges") val recentActivityBridges: Int = 0,
    @SerialName("noRecentActivityBridges") val noRecentActivityBridges: Int = 0,
    @SerialName("neverUsedBridges") val neverUsedBridges: Int = 0,
    @SerialName("pendingMappingTotal") val pendingMappingTotal: Int = 0,
    @SerialName("ingestsToday") val ingestsToday: Int = 0,
    /**
     * O backend CFI entrega o resumo de KPIs aninhado aqui. Quando presente,
     * [summary] é a fonte dos KPIs; os contadores achatados acima atuam apenas
     * como fallback para respostas que os tragam na raiz.
     */
    @SerialName("summary") val summary: BridgeMonitoringSummary? = null,
    @SerialName("bridges") val bridges: List<BridgeMonitoringRow> = emptyList()
)

/**
 * Resumo (KPIs) do monitoramento repassado pelo backend CFI, aninhado no campo
 * `summary`. Valores reais do servidor — nunca calculados nem inventados aqui.
 */
@Serializable
data class BridgeMonitoringSummary(
    @SerialName("totalBridges") val totalBridges: Int = 0,
    @SerialName("activeBridges") val activeBridges: Int = 0,
    @SerialName("suspendedBridges") val suspendedBridges: Int = 0,
    @SerialName("revokedBridges") val revokedBridges: Int = 0,
    @SerialName("ingestsToday") val ingestsToday: Int = 0
)

@Serializable
data class BridgeMonitoringRow(
    @SerialName("bridgeId") val bridgeId: String = "",
    @SerialName("bridgeName") val bridgeName: String = "",
    @SerialName("organizationId") val organizationId: String = "",
    @SerialName("organizationName") val organizationName: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("sistemaDestino") val sistemaDestino: String = "",
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("revokedAt") val revokedAt: String? = null,
    @SerialName("lastActivityAt") val lastActivityAt: String? = null,
    @SerialName("lastSerialNumber") val lastSerialNumber: String = "",
    @SerialName("lastTransactionCode") val lastTransactionCode: String = "",
    @SerialName("totalIngests") val totalIngests: Int = 0,
    @SerialName("ingestsToday") val ingestsToday: Int = 0,
    @SerialName("successfulIngests") val successfulIngests: Int = 0,
    @SerialName("pendingMappingCount") val pendingMappingCount: Int = 0,
    @SerialName("failedIngests") val failedIngests: Int = 0,
    @SerialName("activityStatus") val activityStatus: String = ""
)

/**
 * Status da credencial do bridge. Tolerante a entradas do backend
 * (ACTIVE/SUSPENDED/REVOKED e variações de caixa); valor desconhecido cai em
 * UNKNOWN (nunca é disfarçado de ativo).
 */
enum class BridgeCredentialStatus(val label: String) {
    ACTIVE("Ativo"),
    SUSPENDED("Suspenso"),
    REVOKED("Revogado"),
    UNKNOWN("Desconhecido");

    companion object {
        fun from(raw: String?): BridgeCredentialStatus = when (raw?.trim()?.uppercase()) {
            "ACTIVE" -> ACTIVE
            "SUSPENDED" -> SUSPENDED
            "REVOKED" -> REVOKED
            else -> UNKNOWN
        }
    }
}

/**
 * Estado de atividade do bridge. IMPORTANTE: atividade recente significa
 * "venda/ingestão registrada nas últimas 24h" — NÃO indica dispositivo online.
 */
enum class BridgeActivityStatus(val label: String) {
    RECENT("ATIVIDADE RECENTE"),
    NO_RECENT("SEM ATIVIDADE RECENTE"),
    NEVER("NUNCA RECEBEU VENDA");

    companion object {
        fun from(raw: String?): BridgeActivityStatus = when (raw?.trim()?.uppercase()) {
            "RECENT", "RECENT_ACTIVITY", "ACTIVITY_RECENT", "ATIVO" -> RECENT
            "NEVER", "NEVER_USED", "NUNCA", "NUNCA_USADO" -> NEVER
            else -> NO_RECENT
        }
    }
}

/**
 * Deriva KPIs e formatação pura (testável) a partir da resposta do CFI.
 * Os contadores reais fornecidos pelo servidor são mantidos como vêm.
 */
data class BridgeMonitoringUiModel(
    val total: Int,
    val active: Int,
    val attention: Int,
    val ingestsToday: Int,
    val bridges: List<BridgeMonitoringRow>
) {
    val empty: Boolean get() = bridges.isEmpty()

    companion object {
        fun from(response: BridgeMonitoringResponse): BridgeMonitoringUiModel =
            BridgeMonitoringUiModel(
                total = response.summary?.totalBridges ?: response.totalBridges,
                active = response.summary?.activeBridges ?: response.activeBridges,
                attention = (response.summary?.suspendedBridges ?: response.suspendedBridges) +
                    (response.summary?.revokedBridges ?: response.revokedBridges),
                ingestsToday = response.summary?.ingestsToday ?: response.ingestsToday,
                bridges = response.bridges
            )
    }
}

fun BridgeMonitoringRow.credentialStatus(): BridgeCredentialStatus =
    BridgeCredentialStatus.from(status)

fun BridgeMonitoringRow.activityStatus(): BridgeActivityStatus =
    BridgeActivityStatus.from(activityStatus)
        .let { parsed ->
            // Fallback determinístico quando o backend não informa activityStatus:
            // haver lastActivityAt => presume atividade (contra rastreável na tela),
            // ausência => ainda sem venda registrada. Nunca inventa valor se o
            // backend tiver mandado um estado explícito.
            if (activityStatus.isBlank()) {
                if (lastActivityAt.isNullOrBlank()) BridgeActivityStatus.NEVER
                else BridgeActivityStatus.RECENT
            } else parsed
        }

/** Cor discreta do estado visual do status da credencial. */
fun BridgeMonitoringRow.credentialColor(): androidx.compose.ui.graphics.Color = when (credentialStatus()) {
    BridgeCredentialStatus.ACTIVE -> SuccessGreen
    BridgeCredentialStatus.SUSPENDED -> WarningAmber
    BridgeCredentialStatus.REVOKED -> FailureRose
    BridgeCredentialStatus.UNKNOWN -> TextMuted
}

/** Cor discreta do estado visual de atividade. */
fun BridgeMonitoringRow.activityColor(): androidx.compose.ui.graphics.Color = when (activityStatus()) {
    BridgeActivityStatus.RECENT -> SuccessGreen.copy(alpha = 0.9f)
    BridgeActivityStatus.NO_RECENT -> WarningAmber.copy(alpha = 0.95f)
    BridgeActivityStatus.NEVER -> TextMuted
}

private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Formata um ISO-8601 para "dd/MM/yyyy HH:mm" no fuso local, ou "—". */
fun formatBridgeTimestamp(iso: String?): String {
    val instant = parseInstant(iso) ?: return "—"
    val zdt = instant.atZone(ZoneId.systemDefault())
    return String.format(
        "%02d/%02d/%04d %02d:%02d",
        zdt.dayOfMonth, zdt.monthValue, zdt.year,
        zdt.hour, zdt.minute
    )
}

/**
 * Mascara o código da última transação: exibe apenas os 4 últimos caracteres
 * (ex.: "•••• 4821"). Vazio/nulo vira "—" (nunca expõe o código completo).
 */
fun maskTransactionCode(code: String?): String {
    val trimmed = code?.trim().orEmpty()
    if (trimmed.isEmpty()) return "—"
    return "•••• " + trimmed.takeLast(4)
}
