package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.update.UpdateProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUpdateSourceTest {

    private val config = SupabaseAuthConfig(
        projectUrl = "https://projeto.supabase.co",
        anonKey = "anon-test-key"
    )

    @Test
    fun `consulta filtra published true e ordena por version_code desc limit 1`() {
        val source = UpdateProvider.VersionTableUpdateSource(config)

        val endpoint = source.endpoint()
        assertTrue(endpoint, endpoint.startsWith("https://projeto.supabase.co/rest/v1/domnex_bridge_versions"))
        assertTrue(endpoint, endpoint.contains("published=eq.true"))
        assertTrue(endpoint, endpoint.contains("order=version_code.desc"))
        assertTrue(endpoint, endpoint.contains("limit=1"))
        // Nunca ordena por version_name.
        assertTrue(endpoint, !endpoint.contains("order=version_name"))
        // Nunca usa o nome camelCase da coluna.
        assertTrue(endpoint, !endpoint.contains("latestVersionCode"))
    }

    @Test
    fun `headers usam apenas a chave anon publica`() {
        val source = UpdateProvider.VersionTableUpdateSource(config)
        val headers = source.headers()
        assertEquals("anon-test-key", headers["apikey"])
        assertEquals("Bearer anon-test-key", headers["Authorization"])
        // Nenhuma credencial sensível.
        assertTrue(!headers.values.any { it.contains("service_role") })
        assertTrue(!headers.values.any { it.contains("M2M") })
        assertTrue(!headers.values.any { it.contains("secret") })
    }
}
