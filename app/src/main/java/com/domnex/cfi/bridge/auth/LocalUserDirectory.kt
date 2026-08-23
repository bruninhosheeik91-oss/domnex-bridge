package com.domnex.cfi.bridge.auth

import java.util.UUID

object LocalUserDirectory : UserDirectory {

    private val users = mutableListOf(
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

    private val demoClientNames = listOf(
        "Padaria Estrela",
        "Mercado Central",
        "Farmácia Vida"
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

    override fun findClientNames(): List<String> = demoClientNames.toList()

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

    override fun setStatus(userId: String, status: UserStatus): Boolean {
        val index = users.indexOfFirst { it.id == userId }
        if (index < 0) return false
        users[index] = users[index].copy(status = status)
        return true
    }
}
