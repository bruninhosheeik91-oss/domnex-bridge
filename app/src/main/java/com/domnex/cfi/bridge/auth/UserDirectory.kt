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
 * Resumo REAL dos acessos vinculados a um cliente, derivado de [UserDirectory.listUsers].
 * Alimenta o aviso pré-exclusão ("Zona de risco") — nunca usa números fictícios.
 */
data class ClientDeletionSummary(
    val clientId: String?,
    val clientName: String,
    val totalAccesses: Int,
    val activeUsers: Int,
    val pendingUsers: Int,
    val suspendedUsers: Int
)

sealed interface DeleteClientOutcome {
    /** Cliente e todos os seus usuários foram removidos do backend real. */
    data class Deleted(val clientName: String) : DeleteClientOutcome
    data class Failed(val message: String) : DeleteClientOutcome
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

    /**
     * Cria um novo acesso no backend privilegiado. Quando [initialPassword] é
     * fornecida (mínimo exigido por [AccessRules]), o usuário já nasce com
     * login utilizável (e-mail + senha) — sem depender de convite por e-mail.
     * A senha trafega apenas nesta chamada HTTPS; nunca é armazenada pelo app.
     */
    fun createAccess(
        name: String,
        email: String,
        role: UserRole,
        clientName: String?,
        status: UserStatus,
        initialPassword: String? = null
    ): CreateUserOutcome

    fun setStatus(userId: String, status: UserStatus): StatusChangeOutcome

    /** Edição atômica de nome/perfil/status/cliente via RPC segura no servidor. */
    fun updateAccess(userId: String, update: AccessUpdate): UpdateAccessOutcome

    /** E-mail pertence ao Supabase Auth: exige função privilegiada no backend. */
    fun changeEmail(userId: String, newEmail: String): EmailChangeOutcome

    /** Fluxo REAL de redefinição de senha do Supabase (nunca simulado). */
    fun sendPasswordReset(userId: String): PasswordResetOutcome

    /**
     * Exclusão DEFINITIVA de um cliente no backend real: remove os usuários de
     * Auth vinculados (o login deles passa a falhar imediatamente) e depois o
     * registro do cliente. Operação irreversível — exclusiva de DOMNEX_ADMIN.
     */
    fun deleteClient(clientId: String): DeleteClientOutcome
}

/**
 * Deriva [ClientDeletionSummary] a partir da lista REAL de acessos. Regra pura:
 * considera apenas perfis CLIENT; casa por [UserAccount.clientId] quando
 * disponível e, caso contrário, pelo nome do cliente (backend DEV não popula id).
 * Retorna null quando nenhum acesso corresponde — nada de números inventados.
 */
fun clientDeletionSummaryFor(
    users: List<UserAccount>,
    clientId: String?,
    clientName: String?
): ClientDeletionSummary? {
    val targetName = clientName?.trim().orEmpty()
    if (clientId.isNullOrBlank() && targetName.isEmpty()) return null
    val linked = users.filter { user ->
        user.role == UserRole.CLIENT && when {
            !user.clientId.isNullOrBlank() && !clientId.isNullOrBlank() -> user.clientId == clientId
            else -> user.clientName?.trim() == targetName && targetName.isNotEmpty()
        }
    }
    if (linked.isEmpty()) return null
    return ClientDeletionSummary(
        clientId = clientId,
        clientName = targetName.ifEmpty { linked.firstNotNullOfOrNull { it.clientName }.orEmpty() },
        totalAccesses = linked.size,
        activeUsers = linked.count { it.status == UserStatus.ACTIVE },
        pendingUsers = linked.count { it.status == UserStatus.PENDING },
        suspendedUsers = linked.count { it.status == UserStatus.SUSPENDED }
    )
}
