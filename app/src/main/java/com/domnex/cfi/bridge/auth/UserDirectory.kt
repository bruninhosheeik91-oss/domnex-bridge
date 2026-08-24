package com.domnex.cfi.bridge.auth

enum class AccessFilter {
    ALL,
    CLIENTS,
    ADMINS,
    SUSPENDED
}

/** Cliente (empresa) existente no backend, com id real para vínculos. */
data class ClientRef(
    val id: String,
    val name: String
)

sealed interface CreateUserOutcome {
    data class Created(val user: UserAccount) : CreateUserOutcome
    data object EmailInUse : CreateUserOutcome
    data class Failed(val message: String) : CreateUserOutcome
}

sealed interface StatusChangeOutcome {
    data object Updated : StatusChangeOutcome
    data class Failed(val message: String) : StatusChangeOutcome
}

sealed interface UpdateAccessOutcome {
    data object Updated : UpdateAccessOutcome
    data class Failed(val message: String) : UpdateAccessOutcome
}

sealed interface EmailChangeOutcome {
    /** Backend aceitou e aplicou a troca no Supabase Auth. */
    data object Changed : EmailChangeOutcome
    data class Failed(val message: String) : EmailChangeOutcome
}

sealed interface PasswordResetOutcome {
    /**
     * O backend ACEITOU a solicitação de redefinição. A entrega do e-mail só
     * acontece se o projeto Supabase tiver SMTP configurado — nunca é sucesso
     * simulado.
     */
    data object Requested : PasswordResetOutcome
    data class Failed(val message: String) : PasswordResetOutcome
}

/**
 * Alterações administrativas sobre um acesso. Campos nulos = não alterar.
 * A validação final (papel/status/cliente) é sempre refeita no servidor.
 */
data class AccessUpdate(
    val name: String? = null,
    val role: UserRole? = null,
    val status: UserStatus? = null,
    val clientId: String? = null,
    val clearClient: Boolean = false
) {
    fun hasChanges(): Boolean =
        name != null || role != null || status != null || clientId != null || clearClient
}

interface UserDirectory {
    fun listUsers(query: String = "", filter: AccessFilter = AccessFilter.ALL): List<UserAccount>
    fun getUser(userId: String): UserAccount?
    fun findClients(): List<ClientRef>
    fun createAccess(
        name: String,
        email: String,
        role: UserRole,
        clientName: String?,
        status: UserStatus
    ): CreateUserOutcome

    fun setStatus(userId: String, status: UserStatus): StatusChangeOutcome

    /** Edição atômica de nome/perfil/status/cliente via RPC segura no servidor. */
    fun updateAccess(userId: String, update: AccessUpdate): UpdateAccessOutcome

    /** E-mail pertence ao Supabase Auth: exige função privilegiada no backend. */
    fun changeEmail(userId: String, newEmail: String): EmailChangeOutcome

    /** Fluxo REAL de redefinição de senha do Supabase (nunca simulado). */
    fun sendPasswordReset(userId: String): PasswordResetOutcome
}
