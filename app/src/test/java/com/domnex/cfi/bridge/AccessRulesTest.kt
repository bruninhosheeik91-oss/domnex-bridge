package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AccessRules
import com.domnex.cfi.bridge.auth.AccessUpdate
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Matriz de constraints papel/status/cliente — espelho puro da constraint do
 * banco e da RPC bridge_admin_update_access:
 *
 *   * DOMNEX_ADMIN sem client_id;
 *   * CLIENT PENDING pode estar sem client_id;
 *   * CLIENT ACTIVE/SUSPENDED exige client_id.
 */
class AccessRulesTest {

    // ------------------------------------------------------- DOMNEX_ADMIN

    @Test
    fun `DOMNEX_ADMIN sem cliente é válido em qualquer status`() {
        UserStatus.values().forEach { status ->
            assertNull(AccessRules.validate(UserRole.DOMNEX_ADMIN, status, null))
        }
    }

    @Test
    fun `DOMNEX_ADMIN com cliente é rejeitado`() {
        val violation = AccessRules.validate(UserRole.DOMNEX_ADMIN, UserStatus.ACTIVE, "c-1")
        assertNotNull(violation)
        assertEquals(AccessRules.ADMIN_MUST_NOT_HAVE_CLIENT, violation)
    }

    @Test
    fun `DOMNEX_ADMIN com cliente vazio é aceito como ausência de vínculo`() {
        assertNull(AccessRules.validate(UserRole.DOMNEX_ADMIN, UserStatus.ACTIVE, ""))
    }

    // -------------------------------------------------------------- CLIENT

    @Test
    fun `CLIENT ACTIVE exige client_id`() {
        val violation = AccessRules.validate(UserRole.CLIENT, UserStatus.ACTIVE, null)
        assertNotNull(violation)
        assertEquals(AccessRules.CLIENT_REQUIRES_CLIENT, violation)
    }

    @Test
    fun `CLIENT ACTIVE com client_id vazio também é rejeitado`() {
        val violation = AccessRules.validate(UserRole.CLIENT, UserStatus.ACTIVE, "   ")
        assertNotNull(violation)
        assertEquals(AccessRules.CLIENT_REQUIRES_CLIENT, violation)
    }

    @Test
    fun `CLIENT SUSPENDED exige client_id`() {
        val violation = AccessRules.validate(UserRole.CLIENT, UserStatus.SUSPENDED, null)
        assertNotNull(violation)
        assertEquals(AccessRules.CLIENT_REQUIRES_CLIENT, violation)
    }

    @Test
    fun `CLIENT PENDING pode estar sem client_id`() {
        assertNull(AccessRules.validate(UserRole.CLIENT, UserStatus.PENDING, null))
    }

    @Test
    fun `CLIENT com client_id real é válido em todos os status`() {
        UserStatus.values().forEach { status ->
            assertNull(AccessRules.validate(UserRole.CLIENT, status, "c-1"))
        }
    }

    // ------------------------------------------------------------ AccessUpdate

    @Test
    fun `AccessUpdate vazio não tem mudanças`() {
        assertNull(AccessUpdate().name)
        org.junit.Assert.assertFalse(AccessUpdate().hasChanges())
    }

    @Test
    fun `AccessUpdate detecta qualquer campo alterado`() {
        org.junit.Assert.assertTrue(AccessUpdate(name = "Novo").hasChanges())
        org.junit.Assert.assertTrue(AccessUpdate(role = UserRole.CLIENT).hasChanges())
        org.junit.Assert.assertTrue(AccessUpdate(status = UserStatus.SUSPENDED).hasChanges())
        org.junit.Assert.assertTrue(AccessUpdate(clientId = "c-1").hasChanges())
        org.junit.Assert.assertTrue(AccessUpdate(clearClient = true).hasChanges())
    }
}
