package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AccessRules
import com.domnex.cfi.bridge.auth.ClientDeletionConfirmation
import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.auth.clientDeletionSummaryFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras puras das operações administrativas sensíveis:
 * senha inicial na criação de acesso e confirmação por nome da exclusão
 * definitiva de cliente. Sem Android, sem rede — JVM puro.
 */
class AdminOperationsRulesTest {

    // ------------------------------------------------------- senha inicial

    @Test
    fun `senha vazia é válida - significa usar convite por e-mail`() {
        assertNull(AccessRules.passwordIssue(""))
        assertNull(AccessRules.initialPasswordPairIssue("", ""))
    }

    @Test
    fun `senha com exatamente oito caracteres é aceita`() {
        assertNull(AccessRules.passwordIssue("12345678"))
    }

    @Test
    fun `senha com sete caracteres é rejeitada`() {
        assertEquals(
            AccessRules.PASSWORD_TOO_SHORT,
            AccessRules.passwordIssue("1234567")
        )
    }

    @Test
    fun `senha acima de setenta e dois caracteres é rejeitada - limite bcrypt`() {
        val limite = "a".repeat(72)
        val estourado = "a".repeat(73)
        assertNull(AccessRules.passwordIssue(limite))
        assertEquals(AccessRules.PASSWORD_TOO_LONG, AccessRules.passwordIssue(estourado))
    }

    @Test
    fun `confirmação divergente é rejeitada mesmo com senha válida`() {
        assertEquals(
            AccessRules.PASSWORD_MISMATCH,
            AccessRules.initialPasswordPairIssue("senha-segura-1", "senha-segura-2")
        )
    }

    @Test
    fun `par de senhas válido passa sem erro`() {
        assertNull(AccessRules.initialPasswordPairIssue("senha-segura-1", "senha-segura-1"))
    }

    @Test
    fun `senha válida com confirmação vazia é rejeitada`() {
        assertEquals(
            AccessRules.PASSWORD_MISMATCH,
            AccessRules.initialPasswordPairIssue("senha-segura-1", "")
        )
    }

    @Test
    fun `senha curta reporta problema antes da comparação de confirmação`() {
        assertEquals(
            AccessRules.PASSWORD_TOO_SHORT,
            AccessRules.initialPasswordPairIssue("curta", "curta")
        )
    }

    // ------------------------------------------- confirmação por nome exato

    @Test
    fun `nome digitado idêntico ao real habilita a exclusão`() {
        assertTrue(ClientDeletionConfirmation.matches("Padaria Estrela", "Padaria Estrela"))
    }

    @Test
    fun `qualquer diferença bloqueia a exclusão - inclusive caixa e espaço`() {
        assertFalse(ClientDeletionConfirmation.matches("padaria estrela", "Padaria Estrela"))
        assertFalse(ClientDeletionConfirmation.matches("Padaria Estrela ", "Padaria Estrela"))
        assertFalse(ClientDeletionConfirmation.matches(" Padaria Estrela", "Padaria Estrela"))
        assertFalse(ClientDeletionConfirmation.matches("Padaria Estrela Ltda", "Padaria Estrela"))
    }

    @Test
    fun `texto vazio nunca confirma`() {
        assertFalse(ClientDeletionConfirmation.matches("", "Padaria Estrela"))
    }

    // ------------------------------------------- resumo real para exclusão

    private fun user(
        id: String,
        name: String,
        role: UserRole = UserRole.CLIENT,
        status: UserStatus = UserStatus.ACTIVE,
        clientId: String? = "c-1",
        clientName: String? = "Padaria Estrela"
    ) = UserAccount(
        id = id,
        name = name,
        email = "$id@exemplo.com",
        role = role,
        clientId = clientId,
        clientName = clientName,
        status = status
    )

    @Test
    fun `resumo conta apenas acessos CLIENT vinculados ao cliente alvo`() {
        val users = listOf(
            user("1", "A", status = UserStatus.ACTIVE),
            user("2", "B", status = UserStatus.SUSPENDED),
            user("3", "C", status = UserStatus.PENDING),
            user("4", "Outro cliente", clientId = "c-2", clientName = "Mercado Central"),
            user("5", "Admin", role = UserRole.DOMNEX_ADMIN, clientId = null, clientName = null)
        )
        val summary = clientDeletionSummaryFor(users, "c-1", "Padaria Estrela")
        assertEquals(3, summary?.totalAccesses)
        assertEquals(1, summary?.activeUsers)
        assertEquals(1, summary?.pendingUsers)
        assertEquals(1, summary?.suspendedUsers)
    }

    @Test
    fun `resumo casa por nome quando o backend DEV não popula clientId`() {
        val users = listOf(
            user("1", "DEV", clientId = null, clientName = "Farmácia Vida"),
            user("2", "DEV2", clientId = null, clientName = "Farmácia Vida"),
            user("3", "Outro", clientId = null, clientName = "Mercado Central")
        )
        val summary = clientDeletionSummaryFor(users, null, "Farmácia Vida")
        assertEquals(2, summary?.totalAccesses)
        assertEquals("Farmácia Vida", summary?.clientName)
    }

    @Test
    fun `resumo é nulo quando nenhum acesso corresponde - nada de números inventados`() {
        assertNull(clientDeletionSummaryFor(emptyList(), "c-404", "Cliente Fantasma"))
        assertNull(clientDeletionSummaryFor(listOf(user("1", "A")), null, null))
    }
}
