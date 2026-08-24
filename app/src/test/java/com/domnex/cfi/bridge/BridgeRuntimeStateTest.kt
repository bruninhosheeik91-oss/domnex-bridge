package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.service.BridgeRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Estados operacionais reais do Bridge:
 *  - PAUSED = decisão do usuário de desligar temporariamente;
 *  - NEEDS_PERMISSION = acessibilidade não ativa.
 * PAUSED tem precedência e nunca deve aparecer como NEEDS_PERMISSION.
 */
class BridgeRuntimeStateTest {

    @Test
    fun `monitoramento habilitado com acessibilidade ativa é ACTIVE`() {
        assertEquals(
            BridgeRuntimeState.ACTIVE,
            BridgeRuntimeState.resolve(monitorEnabled = true, accessibilityRunning = true)
        )
    }

    @Test
    fun `pausado pelo usuário é PAUSED mesmo sem acessibilidade`() {
        assertEquals(
            BridgeRuntimeState.PAUSED,
            BridgeRuntimeState.resolve(monitorEnabled = false, accessibilityRunning = false)
        )
        assertEquals(
            BridgeRuntimeState.PAUSED,
            BridgeRuntimeState.resolve(monitorEnabled = false, accessibilityRunning = true)
        )
    }

    @Test
    fun `habilitado sem acessibilidade é NEEDS_PERMISSION`() {
        assertEquals(
            BridgeRuntimeState.NEEDS_PERMISSION,
            BridgeRuntimeState.resolve(monitorEnabled = true, accessibilityRunning = false)
        )
    }
}
