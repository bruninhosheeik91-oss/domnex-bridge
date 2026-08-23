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

sealed interface AuthResult {
    data class Authorized(val session: AuthSession) : AuthResult
    data object Rejected : AuthResult
}
