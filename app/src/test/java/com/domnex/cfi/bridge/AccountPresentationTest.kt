package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AccountPresentation
import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FASE 9 — apresentação da Conta (modelos de UI puros).
 * Regras: rótulos corretos por papel/status, cliente vinculado somente quando
 * real e NENHUM secret (id/UUID, tokens, credenciais) nos modelos de UI.
 */
class AccountPresentationTest {

    private fun account(
        role: UserRole = UserRole.CLIENT,
        status: UserStatus = UserStatus.ACTIVE,
        clientName: String? = "Cliente Exemplo LTDA"
    ) = UserAccount(
        id = "550e8400-e29b-41d4-a716-446655440000",
        name = "Maria da Silva",
        email = "maria@cliente.com.br",
        role = role,
        clientName = clientName,
        status = status
    )

    // ── Tipo de acesso ────────────────────────────────────────

    @Test
    fun `conta CLIENT mostra Cliente`() {
        assertEquals("Cliente", AccountPresentation.roleLabel(UserRole.CLIENT))
    }

    @Test
    fun `conta DOMNEX_ADMIN mostra Administrador Domnex`() {
        assertEquals("Administrador Domnex", AccountPresentation.roleLabel(UserRole.DOMNEX_ADMIN))
    }

    @Test
    fun `sem sessão o tipo de acesso é neutro e honesto`() {
        assertEquals("Cliente", AccountPresentation.roleLabel(null))
    }

    // ── Status da conta ───────────────────────────────────────

    @Test
    fun `status real Ativo Pendente Suspenso`() {
        assertEquals("Ativo", AccountPresentation.statusLabel(UserStatus.ACTIVE))
        assertEquals("Pendente", AccountPresentation.statusLabel(UserStatus.PENDING))
        assertEquals("Suspenso", AccountPresentation.statusLabel(UserStatus.SUSPENDED))
    }

    @Test
    fun `status ausente é omitido (null) em vez de inventado`() {
        assertNull(AccountPresentation.statusLabel(null))
    }

    // ── Cliente vinculado ─────────────────────────────────────

    @Test
    fun `CLIENT com vínculo real mostra o nome do cliente`() {
        assertEquals(
            "Cliente Exemplo LTDA",
            AccountPresentation.linkedClientLabel(account(clientName = "Cliente Exemplo LTDA"))
        )
    }

    @Test
    fun `CLIENT sem vínculo não mostra linha inventada`() {
        assertNull(AccountPresentation.linkedClientLabel(account(clientName = null)))
        assertNull(AccountPresentation.linkedClientLabel(account(clientName = "   ")))
    }

    @Test
    fun `DOMNEX_ADMIN nunca tem cliente vinculado inventado`() {
        assertNull(
            AccountPresentation.linkedClientLabel(
                account(role = UserRole.DOMNEX_ADMIN, clientName = "Qualquer")
            )
        )
    }

    @Test
    fun `sem sessão não há cliente vinculado`() {
        assertNull(AccountPresentation.linkedClientLabel(null))
    }

    // ── Nenhum secret nos modelos de UI ───────────────────────

    @Test
    fun `nenhum rótulo expõe id e-mail ou dado sensível da sessão`() {
        val sensitive = account()
        val outputs = listOf(
            AccountPresentation.roleLabel(sensitive.role),
            AccountPresentation.statusLabel(sensitive.status),
            AccountPresentation.linkedClientLabel(sensitive)
        ).filterNotNull()

        assertFalse(outputs.any { it.contains(sensitive.id) })
        assertFalse(outputs.any { it.contains(sensitive.email) })
        assertFalse(outputs.any { it.contains("@") })
    }
}
