package com.domnex.cfi.bridge.diagnostics

import com.domnex.cfi.bridge.provisioning.ProvisioningState

/**
 * Lógica PURA do Diagnóstico (testável em JVM, sem Android).
 *
 * Deriva estados EXCLUSIVAMENTE de sinais reais já existentes no app:
 *  - `bridge_enabled` (BridgeMonitor.enabled);
 *  - estado real do TonAccessibilityService (isRunning);
 *  - ProvisioningState (BridgeProvisioningRepository);
 *  - lastLog do motor (sanitizado — fonte auxiliar).
 *
 * NENHUM healthcheck de rede é inventado. Nenhum dado técnico (endpoint,
 * token, headers, payload) chega à UI.
 */

/** Estado principal do diagnóstico. */
enum class DiagnosticsOverall { ALL_OK, PAUSED, ACTION_NEEDED }

/** Bridge: controle real Ativar/Pausar (bridge_enabled). */
enum class BridgeSwitchState { ACTIVE, PAUSED }

/** Acessibilidade TON: permissão real do serviço. */
enum class AccessibilityPermissionState { GRANTED, NEEDED }

/**
 * Serviço de monitoramento: deriva da MESMA condição real usada pelo app
 * para manter o foreground service rodando (bridge_enabled + acessibilidade
 * ativa), espelhando o indicador operacional existente da Home.
 * Nada de ActivityManager polling nem healthcheck novo.
 */
enum class MonitorServiceState { RUNNING, STOPPED }

data class DiagnosticsSignals(
    val monitorEnabled: Boolean,
    val accessibilityRunning: Boolean,
    val provisioning: ProvisioningState
)

object BridgeDiagnosticsLogic {

    /**
     * Precedência idêntica à [com.domnex.cfi.bridge.service.BridgeRuntimeState]:
     * PAUSED (decisão do usuário) vence; depois permissão/integração.
     */
    fun overall(signals: DiagnosticsSignals): DiagnosticsOverall = when {
        !signals.monitorEnabled -> DiagnosticsOverall.PAUSED
        signals.accessibilityRunning &&
            signals.provisioning == ProvisioningState.CONFIGURED ->
            DiagnosticsOverall.ALL_OK
        else -> DiagnosticsOverall.ACTION_NEEDED
    }

    fun bridgeSwitch(monitorEnabled: Boolean): BridgeSwitchState =
        if (monitorEnabled) BridgeSwitchState.ACTIVE else BridgeSwitchState.PAUSED

    fun accessibility(accessibilityRunning: Boolean): AccessibilityPermissionState =
        if (accessibilityRunning) {
            AccessibilityPermissionState.GRANTED
        } else {
            AccessibilityPermissionState.NEEDED
        }

    fun monitorService(
        monitorEnabled: Boolean,
        accessibilityRunning: Boolean
    ): MonitorServiceState =
        if (monitorEnabled && accessibilityRunning) {
            MonitorServiceState.RUNNING
        } else {
            MonitorServiceState.STOPPED
        }

    /** Rótulo da integração — nunca dados técnicos. */
    fun integrationLabel(provisioning: ProvisioningState): String = when (provisioning) {
        ProvisioningState.CONFIGURED -> "Configurada"
        ProvisioningState.UNCONFIGURED -> "Configuração necessária"
        ProvisioningState.ERROR -> "Erro de configuração"
    }

    /**
     * Nome amigável do sistema conectado SOMENTE quando realmente existe
     * (`target_system_name` preenchido). Sem fallback inventado: retorna null
     * quando ausente e a UI omite a linha.
     */
    fun connectedSystemName(targetSystemName: String): String? =
        targetSystemName.trim().ifEmpty { null }

    // ── Sanitização da última atividade ──────────────────────

    private val URL_REGEX = Regex("(?i)https?://\\S+")
    private val SECRET_ASSIGNMENT_REGEX =
        Regex("(?i)(token|authorization|bearer|api[_-]?key|secret)\\s*[:=]\\s*\\S+")
    private val JWT_REGEX = Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}")
    private val TX_NA_REGEX = Regex("\\s*[—–\\-]?\\s*Tx:\\s*N/?A", RegexOption.IGNORE_CASE)
    private val SERIAL_NA_REGEX = Regex("\\s*[—–\\-]?\\s*Serial:\\s*N/?A", RegexOption.IGNORE_CASE)
    private const val MAX_LENGTH = 200

    /**
     * Sanitiza o lastLog do motor para exibição:
     *  - remove URLs (endpoint nunca aparece);
     *  - remove valores atribuídos a token/authorization/bearer/api key/secret;
     *  - remove JWTs por segurança adicional;
     *  - remove placeholders "N/A" (mesma regra visual da Home);
     *  - limita o tamanho (nunca payload completo).
     *
     * Retorna null quando não há nada seguro a exibir → estado vazio honesto.
     */
    fun sanitizeLastLog(raw: String): String? {
        var out = raw.trim()
        if (out.isEmpty()) return null
        out = JWT_REGEX.replace(out, "[oculto]")
        out = URL_REGEX.replace(out, "")
        out = SECRET_ASSIGNMENT_REGEX.replace(out, "$1: [oculto]")
        out = TX_NA_REGEX.replace(out, "")
        out = SERIAL_NA_REGEX.replace(out, "")
        out = out.replace(Regex("[—–\\-]\\s*$"), "").trim()
        if (out.length > MAX_LENGTH) out = out.take(MAX_LENGTH).trimEnd() + "…"
        return out.ifEmpty { null }
    }
}
