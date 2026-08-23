package com.domnex.cfi.bridge.auth

import android.content.Context
import android.util.Patterns
import java.util.UUID

object LocalAuthGateway : AuthGateway {

    private const val DEV_FIXTURE_ADMIN_EMAIL = "admin@domnex.dev"
    private const val DEV_FIXTURE_CLIENT_EMAIL = "cliente@domnex.dev"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeSession: AuthSession? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            activeSession = appContext?.let { LocalSessionStore.readSession(it) }
        }
    }

    override fun login(email: String, password: String): AuthResult {
        val context = appContext ?: return AuthResult.Rejected
        val normalized = email.trim().lowercase()
        val emailValid = normalized.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(normalized).matches()
        val passwordValid = password.length >= 4
        if (!emailValid || !passwordValid) return AuthResult.Rejected

        val user = when (normalized) {
            DEV_FIXTURE_ADMIN_EMAIL -> devAdminFixture(normalized)
            DEV_FIXTURE_CLIENT_EMAIL -> devClientFixture(normalized)
            else -> genericClientDevUser(normalized)
        }

        val session = AuthSession(sessionId = UUID.randomUUID().toString(), user = user)
        activeSession = session
        LocalSessionStore.saveSession(context, session)
        return AuthResult.Authorized(session)
    }

    override fun logout() {
        activeSession = null
        appContext?.let { LocalSessionStore.clear(it) }
    }

    override fun currentSession(): AuthSession? = activeSession

    override fun currentUser(): UserAccount? = activeSession?.user

    private fun devAdminFixture(email: String): UserAccount = UserAccount(
        id = "dev-admin-0001",
        name = "Equipe Domnex (DEV)",
        email = email,
        role = UserRole.DOMNEX_ADMIN,
        clientName = null,
        status = UserStatus.ACTIVE,
        createdAtMillis = 1767225600000L
    )

    private fun devClientFixture(email: String): UserAccount = UserAccount(
        id = "dev-client-0001",
        name = "Conta de Teste (DEV)",
        email = email,
        role = UserRole.CLIENT,
        clientName = "Cliente de Teste (DEV)",
        status = UserStatus.ACTIVE,
        createdAtMillis = 1767225600000L
    )

    private fun genericClientDevUser(email: String): UserAccount {
        val label = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        return UserAccount(
            id = "dev-client-${UUID.randomUUID().toString().take(8)}",
            name = label,
            email = email,
            role = UserRole.CLIENT,
            clientName = label,
            status = UserStatus.ACTIVE,
            createdAtMillis = System.currentTimeMillis()
        )
    }
}
