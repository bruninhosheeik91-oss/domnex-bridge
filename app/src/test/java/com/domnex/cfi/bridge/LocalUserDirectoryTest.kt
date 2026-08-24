package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.AccessUpdate
import com.domnex.cfi.bridge.auth.EmailChangeOutcome
import com.domnex.cfi.bridge.auth.LocalUserDirectory
import com.domnex.cfi.bridge.auth.PasswordResetOutcome
import com.domnex.cfi.bridge.auth.StatusChangeOutcome
import com.domnex.cfi.bridge.auth.UpdateAccessOutcome
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * O backend DEV local é apenas fallback (nunca roda com Supabase configurado).
 * Regras essenciais testadas:
 *  - mutações de status/edição funcionam de verdade no modo dev;
 *  - a matriz de constraints é respeitada também aqui;
 *  - redefinição de senha e troca de e-mail NUNCA são simuladas como sucesso.
 */
class LocalUserDirectoryTest {

    @Before
    fun reset() {
        // Recria o estado base do diretório DEV para isolamento entre testes.
        LocalUserDirectory.resetForTests()
    }

    private fun pendingClientWithoutClientId(): String {
        val outcome = LocalUserDirectory.createAccess(
            name = "Cliente Pendente",
            email = "pendente@dev.local",
            role = UserRole.CLIENT,
            clientName = null,
            status = UserStatus.PENDING
        )
        return (outcome as com.domnex.cfi.bridge.auth.CreateUserOutcome.Created).user.id
    }

    // -------------------------------------------------------------- setStatus

    @Test
    fun `setStatus suspende e reativa usuário real do diretório`() {
        assertTrue(LocalUserDirectory.setStatus("u2", UserStatus.SUSPENDED) is StatusChangeOutcome.Updated)
        assertEquals(
            listOf("João da Silva", "Pedro Lima"),
            LocalUserDirectory.listUsers(filter = AccessFilter.SUSPENDED).map { it.name }
        )

        assertTrue(LocalUserDirectory.setStatus("u2", UserStatus.ACTIVE) is StatusChangeOutcome.Updated)
        assertTrue(LocalUserDirectory.listUsers(filter = AccessFilter.SUSPENDED).none { it.id == "u2" })
    }

    @Test
    fun `setStatus usuário inexistente vira falha`() {
        val outcome = LocalUserDirectory.setStatus("ghost", UserStatus.ACTIVE)
        assertTrue(outcome is StatusChangeOutcome.Failed)
    }

    // ------------------------------------------------------------ updateAccess

    @Test
    fun `updateAccess vincula cliente e ativa CLIENT PENDING`() {
        val userId = pendingClientWithoutClientId()

        val outcome = LocalUserDirectory.updateAccess(
            userId,
            AccessUpdate(status = UserStatus.ACTIVE, clientId = "c-padaria-estrela")
        )
        assertEquals(UpdateAccessOutcome.Updated, outcome)

        val updated = LocalUserDirectory.getUser(userId)!!
        assertEquals(UserStatus.ACTIVE, updated.status)
        assertEquals("Padaria Estrela", updated.clientName)
    }

    @Test
    fun `CLIENT ACTIVE sem client_id é rejeitado`() {
        val userId = pendingClientWithoutClientId()

        val outcome = LocalUserDirectory.updateAccess(
            userId,
            AccessUpdate(status = UserStatus.ACTIVE) // sem vínculo
        )
        assertTrue(outcome is UpdateAccessOutcome.Failed)
        assertEquals("CLIENT ativo ou suspenso exige cliente vinculado.", (outcome as UpdateAccessOutcome.Failed).message)

        // Estado permanece PENDING: falha não foi aplicada como sucesso.
        assertEquals(UserStatus.PENDING, LocalUserDirectory.getUser(userId)?.status)
    }

    @Test
    fun `CLIENT ativo virando DOMNEX_ADMIN fica sem client_id (como na RPC)`() {
        // Mesma semântica do servidor: a UI envia clearClient ao promover a admin.
        val outcome = LocalUserDirectory.updateAccess(
            "u2",
            AccessUpdate(role = UserRole.DOMNEX_ADMIN, clearClient = true)
        )
        assertEquals(UpdateAccessOutcome.Updated, outcome)

        val updated = LocalUserDirectory.getUser("u2")!!
        assertEquals(UserRole.DOMNEX_ADMIN, updated.role)
        assertEquals(null, updated.clientName)
    }

    @Test
    fun `promover a DOMNEX_ADMIN mantendo cliente é rejeitado (como na RPC)`() {
        val outcome = LocalUserDirectory.updateAccess("u2", AccessUpdate(role = UserRole.DOMNEX_ADMIN))
        assertTrue(outcome is UpdateAccessOutcome.Failed)
        assertEquals("DOMNEX_ADMIN não deve ter cliente vinculado.", (outcome as UpdateAccessOutcome.Failed).message)
    }

    @Test
    fun `updateAccess altera nome de verdade`() {
        val outcome = LocalUserDirectory.updateAccess("u3", AccessUpdate(name = " Maria Souza Lima "))
        assertEquals(UpdateAccessOutcome.Updated, outcome)
        assertEquals("Maria Souza Lima", LocalUserDirectory.getUser("u3")?.name)
    }

    // ------------------------------------------------- operações privilegiadas

    @Test
    fun `sendPasswordReset nunca simula sucesso no backend DEV`() {
        val outcome = LocalUserDirectory.sendPasswordReset("u2")
        assertTrue(outcome is PasswordResetOutcome.Failed)
        assertNotNull((outcome as PasswordResetOutcome.Failed).message)
    }

    @Test
    fun `changeEmail nunca simula sucesso no backend DEV`() {
        val outcome = LocalUserDirectory.changeEmail("u2", "novo@dev.local")
        assertTrue(outcome is EmailChangeOutcome.Failed)
        assertNotNull((outcome as EmailChangeOutcome.Failed).message)
    }
}
