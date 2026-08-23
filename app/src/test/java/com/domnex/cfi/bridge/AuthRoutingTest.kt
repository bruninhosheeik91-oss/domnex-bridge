package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AuthRouting
import com.domnex.cfi.bridge.auth.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRoutingTest {

    @Test
    fun `cliente com bridge configurado vai para fluxo operacional`() {
        val target = AuthRouting.resolveTarget(UserRole.CLIENT, bridgeConfigured = true)
        assertEquals(com.domnex.cfi.bridge.auth.RouteTarget.HOME, target)
    }

    @Test
    fun `cliente sem bridge configurado vai para setup`() {
        val target = AuthRouting.resolveTarget(UserRole.CLIENT, bridgeConfigured = false)
        assertEquals(com.domnex.cfi.bridge.auth.RouteTarget.SETUP, target)
    }

    @Test
    fun `admin vai para administracao mesmo sem bridge configurado`() {
        val configured = AuthRouting.resolveTarget(UserRole.DOMNEX_ADMIN, bridgeConfigured = true)
        val unconfigured = AuthRouting.resolveTarget(UserRole.DOMNEX_ADMIN, bridgeConfigured = false)
        assertEquals(com.domnex.cfi.bridge.auth.RouteTarget.ADMIN_HOME, configured)
        assertEquals(com.domnex.cfi.bridge.auth.RouteTarget.ADMIN_HOME, unconfigured)
    }

    @Test
    fun `admin nunca cai no fluxo operacional de cliente`() {
        val target = AuthRouting.resolveTarget(UserRole.DOMNEX_ADMIN, bridgeConfigured = true)
        assertEquals(false, target == com.domnex.cfi.bridge.auth.RouteTarget.HOME)
        assertEquals(false, target == com.domnex.cfi.bridge.auth.RouteTarget.SETUP)
    }
}
