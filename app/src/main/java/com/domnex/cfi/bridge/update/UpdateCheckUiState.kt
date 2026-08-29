package com.domnex.cfi.bridge.update

/**
 * Estado da UI de verificação de atualização. Independente do Compose para
 * permitir testes JVM puros.
 */
sealed interface UpdateCheckUiState {
    object Idle : UpdateCheckUiState
    object Checking : UpdateCheckUiState
    object NotConfigured : UpdateCheckUiState
    data class UpToDate(val installedVersionName: String) : UpdateCheckUiState
    data class Available(
        val info: UpdateInfo,
        val required: Boolean
    ) : UpdateCheckUiState
    data class Error(val message: String) : UpdateCheckUiState
}
