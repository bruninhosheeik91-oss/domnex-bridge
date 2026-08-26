package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.diagnostics.AccessibilityPermissionState
import com.domnex.cfi.bridge.diagnostics.BridgeDiagnosticsLogic
import com.domnex.cfi.bridge.diagnostics.BridgeSwitchState
import com.domnex.cfi.bridge.diagnostics.DiagnosticsOverall
import com.domnex.cfi.bridge.diagnostics.DiagnosticsSignals
import com.domnex.cfi.bridge.diagnostics.MonitorServiceState
import com.domnex.cfi.bridge.provisioning.ProvisioningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 9 — lógica pura do Diagnóstico.
 * Derivações usam SOMENTE sinais reais (bridge_enabled, acessibilidade,
 * provisionamento e lastLog sanitizado). Sem healthcheck, sem mocks.
 */
class BridgeDiagnosticsTest {

    private fun signals(
        monitorEnabled: Boolean = true,
        accessibilityRunning: Boolean = true,
        provisioning: ProvisioningState = ProvisioningState.CONFIGURED
    ) = DiagnosticsSignals(monitorEnabled, accessibilityRunning, provisioning)

    // ── Estado geral ──────────────────────────────────────────

    @Test
    fun `diagnostico ACTIVE com tudo ativo e integrado é Tudo funcionando`() {
        assertEquals(
            DiagnosticsOverall.ALL_OK,
            BridgeDiagnosticsLogic.overall(
                signals(monitorEnabled = true, accessibilityRunning = true,
                    provisioning = ProvisioningState.CONFIGURED)
            )
        )
    }

    @Test
    fun `bridge pausado pelo usuário tem precedência e vira PAUSED`() {
        // Mesmo sem permissão e sem integração, a decisão do usuário domina
        // (mesma precedência do BridgeRuntimeState).
        assertEquals(
            DiagnosticsOverall.PAUSED,
            BridgeDiagnosticsLogic.overall(
                signals(monitorEnabled = false, accessibilityRunning = false,
                    provisioning = ProvisioningState.UNCONFIGURED)
            )
        )
    }

    @Test
    fun `acessibilidade sem permissão vira Ação necessária`() {
        assertEquals(
            DiagnosticsOverall.ACTION_NEEDED,
            BridgeDiagnosticsLogic.overall(
                signals(accessibilityRunning = false)
            )
        )
    }

    @Test
    fun `integração UNCONFIGURED ou ERROR vira Ação necessária`() {
        assertEquals(
            DiagnosticsOverall.ACTION_NEEDED,
            BridgeDiagnosticsLogic.overall(signals(provisioning = ProvisioningState.UNCONFIGURED))
        )
        assertEquals(
            DiagnosticsOverall.ACTION_NEEDED,
            BridgeDiagnosticsLogic.overall(signals(provisioning = ProvisioningState.ERROR))
        )
    }

    // ── Linhas de monitoramento ───────────────────────────────

    @Test
    fun `linha Bridge reflete bridge_enabled real`() {
        assertEquals(
            BridgeSwitchState.ACTIVE,
            BridgeDiagnosticsLogic.bridgeSwitch(monitorEnabled = true)
        )
        assertEquals(
            BridgeSwitchState.PAUSED,
            BridgeDiagnosticsLogic.bridgeSwitch(monitorEnabled = false)
        )
    }

    @Test
    fun `linha acessibilidade reflete permissão real do serviço`() {
        assertEquals(
            AccessibilityPermissionState.GRANTED,
            BridgeDiagnosticsLogic.accessibility(accessibilityRunning = true)
        )
        assertEquals(
            AccessibilityPermissionState.NEEDED,
            BridgeDiagnosticsLogic.accessibility(accessibilityRunning = false)
        )
    }

    @Test
    fun `serviço de monitoramento em execução somente com bridge ativo e acessibilidade`() {
        assertEquals(
            MonitorServiceState.RUNNING,
            BridgeDiagnosticsLogic.monitorService(true, true)
        )
        assertEquals(
            MonitorServiceState.STOPPED,
            BridgeDiagnosticsLogic.monitorService(false, true)
        )
        assertEquals(
            MonitorServiceState.STOPPED,
            BridgeDiagnosticsLogic.monitorService(true, false)
        )
    }

    // ── Integração (sem dados técnicos) ───────────────────────

    @Test
    fun `integração CONFIGURED UNCONFIGURED e ERROR têm rótulos corretos`() {
        assertEquals(
            "Configurada",
            BridgeDiagnosticsLogic.integrationLabel(ProvisioningState.CONFIGURED)
        )
        assertEquals(
            "Configuração necessária",
            BridgeDiagnosticsLogic.integrationLabel(ProvisioningState.UNCONFIGURED)
        )
        assertEquals(
            "Erro de configuração",
            BridgeDiagnosticsLogic.integrationLabel(ProvisioningState.ERROR)
        )
    }

    @Test
    fun `sistema conectado só aparece quando target_system_name existe`() {
        assertEquals(
            "Maquininha do Cliente",
            BridgeDiagnosticsLogic.connectedSystemName(" Maquininha do Cliente ")
        )
        assertNull(BridgeDiagnosticsLogic.connectedSystemName(""))
        assertNull(BridgeDiagnosticsLogic.connectedSystemName("   "))
    }

    // ── Sanitização da última atividade ───────────────────────

    @Test
    fun `evento normal do motor é preservado na última atividade`() {
        val raw = "Venda capturada \u2014 Tx: TX-1 \u2014 Serial: 1401234567"
        assertEquals(raw, BridgeDiagnosticsLogic.sanitizeLastLog(raw))
    }

    @Test
    fun `placeholders N-A são removidos como na Home`() {
        assertEquals(
            "Venda capturada",
            BridgeDiagnosticsLogic.sanitizeLastLog("Venda capturada \u2014 Tx: N/A \u2014 Serial: N/A")
        )
    }

    @Test
    fun `endpoint nunca aparece na última atividade`() {
        val sanitized = BridgeDiagnosticsLogic.sanitizeLastLog(
            "Envio para https://webhook.exemplo.com/supersenha falhou"
        )
        assertFalse(sanitized!!.contains("https://"))
        assertFalse(sanitized.contains("supersenha"))
    }

    @Test
    fun `token atribuído nunca aparece na última atividade`() {
        val sanitized = BridgeDiagnosticsLogic.sanitizeLastLog(
            "config carregada token=abc123secret ok"
        )
        assertFalse(sanitized!!.contains("abc123secret"))
        assertTrue(sanitized.contains("[oculto]"))
    }

    @Test
    fun `jwt nunca aparece na última atividade`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV"
        val sanitized = BridgeDiagnosticsLogic.sanitizeLastLog("sessão $jwt restaurada")
        assertFalse(sanitized!!.contains("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV"))
        assertTrue(sanitized.contains("[oculto]"))
    }

    @Test
    fun `última atividade vazia indica estado vazio honesto`() {
        assertNull(BridgeDiagnosticsLogic.sanitizeLastLog(""))
        assertNull(BridgeDiagnosticsLogic.sanitizeLastLog("   "))
    }

    @Test
    fun `última atividade é truncada para nunca expor payload completo`() {
        val longLog = "x".repeat(500)
        val sanitized = BridgeDiagnosticsLogic.sanitizeLastLog(longLog)!!
        assertTrue(sanitized.length <= 201)
        assertTrue(sanitized.endsWith("…"))
    }
}
