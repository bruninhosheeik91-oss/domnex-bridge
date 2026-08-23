package com.domnex.cfi.bridge.auth

/**
 * Destino pós-login resolvido de forma pura/testável.
 * CLIENT -> fluxo operacional (configuração do Bridge se necessário)
 * DOMNEX_ADMIN -> Administração Domnex Bridge
 */
enum class RouteTarget {
    SETUP,
    HOME,
    ADMIN_HOME
}

object AuthRouting {
    fun resolveTarget(role: UserRole, bridgeConfigured: Boolean): RouteTarget = when (role) {
        UserRole.DOMNEX_ADMIN -> RouteTarget.ADMIN_HOME
        UserRole.CLIENT -> if (bridgeConfigured) RouteTarget.HOME else RouteTarget.SETUP
    }
}
