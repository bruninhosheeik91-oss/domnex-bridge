package com.domnex.cfi.bridge.service

/**
 * Estado operacional real do Bridge exibido na Home:
 *
 *  - [ACTIVE]           → monitorando vendas da TON;
 *  - [PAUSED]           → usuário decidiu desligar temporariamente;
 *  - [NEEDS_PERMISSION] → serviço de acessibilidade não está ativo.
 *
 * PAUSED tem precedência sobre NEEDS_PERMISSION: se o usuário pausou, a Home
 * mostra "Bridge pausado" independentemente do status da acessibilidade.
 */
enum class BridgeRuntimeState {
    ACTIVE,
    PAUSED,
    NEEDS_PERMISSION;

    companion object {
        fun resolve(monitorEnabled: Boolean, accessibilityRunning: Boolean): BridgeRuntimeState =
            when {
                !monitorEnabled -> PAUSED
                accessibilityRunning -> ACTIVE
                else -> NEEDS_PERMISSION
            }
    }
}
