package com.domnex.cfi.bridge.auth

import java.util.UUID

enum class UserRole {
    CLIENT,
    DOMNEX_ADMIN
}

enum class UserStatus {
    ACTIVE,
    PENDING,
    SUSPENDED
}

data class UserAccount(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val clientName: String? = null,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class AuthSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val user: UserAccount
)

enum class DenialReason {
    NOT_CONFIGURED,
    NETWORK_ERROR,
    SERVER_ERROR,
    PROFILE_MISSING,
    PROFILE_SUSPENDED,
    PROFILE_PENDING;

    fun userMessage(): String = when (this) {
        NOT_CONFIGURED -> "Autenticação online não configurada neste build."
        NETWORK_ERROR -> "Sem conexão com o servidor. Tente novamente."
        SERVER_ERROR -> "Servidor indisponível. Tente novamente em instantes."
        PROFILE_MISSING -> "Conta sem perfil Bridge configurado. Contate o suporte Domnex."
        PROFILE_SUSPENDED -> "Acesso suspenso. Contate o suporte Domnex."
        PROFILE_PENDING -> "Acesso aguardando ativação pelo time Domnex."
    }
}

sealed interface AuthResult {
    data class Authorized(val session: AuthSession) : AuthResult
    data object Rejected : AuthResult
    data class Denied(val reason: DenialReason) : AuthResult
}
