package com.domnex.cfi.bridge.auth

import java.util.UUID

/**
 * Backend de DESENVOLVIMENTO (usado somente em builds DEBUG sem chaves Supabase).
 *
 * Regras fixas:
 *  - Nunca é usado quando o Supabase está configurado (AuthProvider garante isso).
 *  - NÃO simula operações que exigem backend privilegiado real: redefinição de
 *    senha e troca de e-mail falham explicitamente, sem fingir sucesso.
 */
object LocalUserDirectory : UserDirectory {

    private val users: MutableList<UserAccount> = defaultUsers().toMutableList()

    private fun defaultUsers(): List<UserAccount> = listOf(
        UserAccount(
            id = "u1",
            name = "Equipe Domnex",
            email = "admin@domnex.dev",
            role = UserRole.DOMNEX_ADMIN,
            clientName = null,
            status = UserStatus.ACTIVE,
            createdAtMillis = 1767225600000L
        ),
        UserAccount(
            id = "u2",
            name = "João da Silva",
            email = "joao@padariaestrela.com.br",
            role = UserRole.CLIENT,
            clientName = "Padaria Estrela",
            status = UserStatus.ACTIVE,
            createdAtMillis = 1767312000000L
        ),
        UserAccount(
            id = "u3",
            name = "Maria Souza",
            email = "maria@mercadocentral.com.br",
            role = UserRole.CLIENT,
            clientName = "Mercado Central",
            status = UserStatus.PENDING,
            createdAtMillis = 1767484800000L
        ),
        UserAccount(
            id = "u4",
            name = "Pedro Lima",
            email = "pedro@farmaciavida.com.br",
            role = UserRole.CLIENT,
            clientName = "Farmácia Vida",
            status = UserStatus.SUSPENDED,
            createdAtMillis = 1767571200000L
        ),
        UserAccount(
            id = "u5",
            name = "Conta de Teste",
            email = "cliente@domnex.dev",
            role = UserRole.CLIENT,
            clientName = "Cliente de Teste (DEV)",
            status = UserStatus.ACTIVE,
            createdAtMillis = 1767744000000L
        )
    )

    private val demoClients = listOf(
        ClientRef(id = "c-padaria-estrela", name = "Padaria Estrela"),
        ClientRef(id = "c-mercado-central", name = "Mercado Central"),
        ClientRef(id = "c-farmacia-vida", name = "Farmácia Vida")
    )

    override fun listUsers(query: String, filter: AccessFilter): List<UserAccount> {
        val q = query.trim().lowercase()
        return users.asSequence()
            .filter { user ->
                when (filter) {
                    AccessFilter.ALL -> true
                    AccessFilter.CLIENTS -> user.role == UserRole.CLIENT
                    AccessFilter.ADMINS -> user.role == UserRole.DOMNEX_ADMIN
                    AccessFilter.SUSPENDED -> user.status == UserStatus.SUSPENDED
                }
            }
            .filter { user ->
                q.isEmpty() ||
                    user.name.lowercase().contains(q) ||
                    user.email.lowercase().contains(q) ||
                    (user.clientName?.lowercase()?.contains(q) ?: false)
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    override fun getUser(userId: String): UserAccount? = users.firstOrNull { it.id == userId }

    override fun findClients(): List<ClientRef> = demoClients.toList()

    override fun createAccess(
        name: String,
        email: String,
        role: UserRole,
        clientName: String?,
        status: UserStatus
    ): CreateUserOutcome {
        val normalizedEmail = email.trim().lowercase()
        if (users.any { it.email.lowercase() == normalizedEmail }) {
            return CreateUserOutcome.EmailInUse
        }
        val user = UserAccount(
            id = "u-${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            email = normalizedEmail,
            role = role,
            clientName = if (role == UserRole.CLIENT) clientName?.trim()?.takeIf { it.isNotEmpty() } else null,
            status = status,
            createdAtMillis = System.currentTimeMillis()
        )
        users.add(user)
        return CreateUserOutcome.Created(user)
    }

    override fun setStatus(userId: String, status: UserStatus): StatusChangeOutcome {
        val index = users.indexOfFirst { it.id == userId }
        if (index < 0) return StatusChangeOutcome.Failed("Usuário não encontrado.")
        users[index] = users[index].copy(status = status)
        return StatusChangeOutcome.Updated
    }

    override fun updateAccess(userId: String, update: AccessUpdate): UpdateAccessOutcome {
        if (!update.hasChanges()) return UpdateAccessOutcome.Updated
        val index = users.indexOfFirst { it.id == userId }
        if (index < 0) return UpdateAccessOutcome.Failed("Usuário não encontrado.")

        val current = users[index]
        val newRole = update.role ?: current.role
        val newStatus = update.status ?: current.status

        // Espelha a RPC bridge_admin_update_access: cliente explícito precisa existir.
        var newClientId: String? = current.clientName
        when {
            update.clearClient -> newClientId = null
            update.clientId != null -> {
                val known = findClients().firstOrNull { it.id == update.clientId }
                    ?: return UpdateAccessOutcome.Failed("Cliente não encontrado.")
                newClientId = known.name
            }
        }

        // Espelha a validação feita no servidor pela RPC bridge_admin_update_access.
        val violation = AccessRules.validate(newRole, newStatus, newClientId)
        if (violation != null) return UpdateAccessOutcome.Failed(violation)

        if (update.name != null && update.name.trim().length < 2) {
            return UpdateAccessOutcome.Failed("Nome inválido.")
        }

        users[index] = current.copy(
            name = update.name?.trim() ?: current.name,
            role = newRole,
            status = newStatus,
            clientName = if (newRole == UserRole.CLIENT) newClientId else null
        )
        return UpdateAccessOutcome.Updated
    }

    override fun changeEmail(userId: String, newEmail: String): EmailChangeOutcome =
        // Sem Supabase Auth não existe e-mail real para alterar: falha honesta.
        EmailChangeOutcome.Failed(
            "Alteração de e-mail requer o backend Supabase (indisponível neste build DEV)."
        )

    override fun sendPasswordReset(userId: String): PasswordResetOutcome =
        // Sem Supabase Auth não há fluxo real de redefinição: nunca simulamos envio.
        PasswordResetOutcome.Failed(
            "Redefinição de senha requer o backend Supabase (indisponível neste build DEV)."
        )

    /** Restaura o estado inicial do diretório DEV (uso exclusivo em testes JVM). */
    fun resetForTests() {
        users.clear()
        users.addAll(defaultUsers())
    }
}
