package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.HttpResponse
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.provisioning.BridgeProvisioningRepository
import com.domnex.cfi.bridge.provisioning.PrefsBridgeProvisioningRepository
import com.domnex.cfi.bridge.provisioning.ProvisioningStorage
import com.domnex.cfi.bridge.provisioning.RemoteBridgeProvisioningRepository
import com.domnex.cfi.bridge.provisioning.RemoteProvisioningOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do reprovisionamento automático pós-login (RemoteBridgeProvisioningRepository).
 *
 * Regras verificadas:
 *  - config local existente -> mantém, NUNCA sobrescreve nem chama a rede;
 *  - config local vazia     -> provisiona via bridge-provisioning;
 *  - configured=false       -> nada é gravado;
 *  - 401/403 (sessão/permissão) -> falha sem tocar na config;
 *  - erro de rede           -> falha amigável, config intacta;
 *  - resposta inválida      -> falha, sem gravação parcial;
 *  - erro NUNCA apaga config existente.
 */
class RemoteBridgeProvisioningRepositoryTest {

    private class FakeHttpClient(
        var handler: ((HttpRequest) -> HttpResponse)? = null
    ) : SupabaseHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler?.invoke(request) ?: HttpResponse.of(500, """{"error":"no_handler"}""")
        }
    }

    private class FakeStorage(initial: Map<String, String> = emptyMap()) : ProvisioningStorage {
        val map = initial.toMutableMap()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private val config = SupabaseAuthConfig(
        projectUrl = "https://projeto.supabase.co",
        anonKey = "anon-test-key"
    )

    private fun repo(
        http: SupabaseHttpClient,
        storage: ProvisioningStorage,
        tokenProvider: () -> String? = { "jwt-sessao" }
    ): RemoteBridgeProvisioningRepository = RemoteBridgeProvisioningRepository(
        config = config,
        httpClient = http,
        accessTokenProvider = tokenProvider,
        storage = PrefsBridgeProvisioningRepository(storage)
    )

    private fun configuredStorage(
        baseUrl: String = "https://existente.com/hook",
        token: String = "tok-existente"
    ): FakeStorage = FakeStorage(
        mapOf(
            PrefsBridgeProvisioningRepository.KEY_BASE_URL to baseUrl,
            PrefsBridgeProvisioningRepository.KEY_BRIDGE_TOKEN to token
        )
    )

    private fun emptyStorage(): FakeStorage = FakeStorage()

    // ------------------------------------------------------------- config local existe

    @Test
    fun `configuracao local existente mantem tudo e nao toca na rede`() {
        val http = FakeHttpClient()
        val storage = configuredStorage(baseUrl = "https://fixo.com/hook", token = "tok-fixo")
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Configured)
        assertEquals(0, http.requests.size)
        // Nada sobrescrito/apagado.
        assertEquals("https://fixo.com/hook", storage.map["api_base_url"])
        assertEquals("tok-fixo", storage.map["bridge_token"])
    }

    // ------------------------------------------------------------- provision via backend

    @Test
    fun `configuracao local vazia provisiona e salva a resposta completa`() {
        val http = FakeHttpClient { request ->
            assertTrue(request.url.endsWith("/functions/v1/bridge-provisioning"))
            assertEquals("GET", request.method)
            assertEquals("Bearer jwt-sessao", request.headers["Authorization"])
            assertEquals("anon-test-key", request.headers["apikey"])
            HttpResponse.of(
                200,
                """{"configured":true,"targetSystemName":"CFI",
                     "apiBaseUrl":"https://novo.com/ingest","bridgeToken":"tok-novo"}"""
            )
        }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Configured)
        assertEquals(1, http.requests.size)
        assertEquals("https://novo.com/ingest", storage.map["api_base_url"])
        assertEquals("tok-novo", storage.map["bridge_token"])
        assertEquals("CFI", storage.map["target_system_name"])
        val config = (result as RemoteProvisioningOutcome.Configured).config
        assertEquals("https://novo.com/ingest", config.baseUrl)
        assertEquals("tok-novo", config.bridgeToken)
    }

    @Test
    fun `sem JWT falha sem tocar na rede nem no storage`() {
        val http = FakeHttpClient()
        val storage = emptyStorage()
        val repository = repo(http, storage, tokenProvider = { null })

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertEquals(0, http.requests.size)
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `backend nao configurado falha sem tocar na rede`() {
        val http = FakeHttpClient()
        val storage = emptyStorage()
        val repository = RemoteBridgeProvisioningRepository(
            config = SupabaseAuthConfig(projectUrl = "", anonKey = ""),
            httpClient = http,
            accessTokenProvider = { "jwt" },
            storage = PrefsBridgeProvisioningRepository(storage)
        )

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertEquals(0, http.requests.size)
    }

    // ------------------------------------------------------------- configured=false

    @Test
    fun `resposta configured=false retorna NotConfigured e nao grava nada`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(200, """{"configured":false}""")
        }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertEquals(RemoteProvisioningOutcome.NotConfigured, result)
        assertTrue(storage.map.isEmpty())
    }

    // ------------------------------------------------------------- 401 / 403

    @Test
    fun `token invalido 401 falha e nao grava nada`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(401, """{"error":"UNAUTHENTICATED"}""")
        }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `usuario inativo 403 falha e nao grava nada`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(403, """{"error":"FORBIDDEN"}""")
        }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertTrue(storage.map.isEmpty())
    }

    // ------------------------------------------------------------- rede / 5xx

    @Test
    fun `erro de rede falha de forma amigavel e nao grava nada`() {
        val http = FakeHttpClient { _ -> throw java.io.IOException("boom") }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertTrue((result as RemoteProvisioningOutcome.Failed).message.isNotBlank())
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `erro 5xx falha sem mascarar sucesso e nao grava nada`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(502, """{"error":"UPSTREAM_ERROR"}""")
        }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertTrue(storage.map.isEmpty())
    }

    // ------------------------------------------------------------- resposta inválida

    @Test
    fun `resposta nao-JSON falha e nao grava nada`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(200, "not json at all") }
        val storage = emptyStorage()
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `resposta configured=true sem campos obrigatorios nao grava configuracao parcial`() {
        // apiBaseUrl/bridgeToken ausentes ou em branco -> resposta incompleta.
        val partialBodies = listOf(
            """{"configured":true,"targetSystemName":"CFI"}""",
            """{"configured":true,"apiBaseUrl":"https://parcial.com/ingest"}""",
            """{"configured":true,"apiBaseUrl":"   ","bridgeToken":"tok"}""",
        )
        for (body in partialBodies) {
            val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
            val storage = emptyStorage()
            val repository = repo(http, storage)

            val result = repository.ensureConfigured()

            assertTrue("configured=true inválido deveria falhar: $body", result is RemoteProvisioningOutcome.Failed)
            assertTrue("não deveria gravar parcial: $body", storage.map.isEmpty())
        }
    }

    // ------------------------------------------------------------- nunca apagar existente

    @Test
    fun `resposta invalida nao apaga configuracao parcialmente existente`() {
        // Cenário: config local pela metade (endpoint apenas) -> vira provisionamento,
        // e uma resposta inválida não deve apagar o que já existia.
        val http = FakeHttpClient { _ -> HttpResponse.of(200, "not json") }
        val storage = FakeStorage(
            mapOf(PrefsBridgeProvisioningRepository.KEY_BASE_URL to "https://parcial.com/hook")
        )
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertEquals("https://parcial.com/hook", storage.map["api_base_url"])
    }

    @Test
    fun `configured=false nao apaga configuracao parcialmente existente`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(200, """{"configured":false}""") }
        val storage = FakeStorage(
            mapOf(PrefsBridgeProvisioningRepository.KEY_BASE_URL to "https://parcial.com/hook")
        )
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertEquals(RemoteProvisioningOutcome.NotConfigured, result)
        assertEquals("https://parcial.com/hook", storage.map["api_base_url"])
    }

    @Test
    fun `403 nao apaga configuracao parcialmente existente`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(403, """{"error":"FORBIDDEN"}""") }
        val storage = FakeStorage(
            mapOf(PrefsBridgeProvisioningRepository.KEY_BRIDGE_TOKEN to "tok-parcial")
        )
        val repository = repo(http, storage)

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertEquals("tok-parcial", storage.map["bridge_token"])
    }

    // ------------------------------------------------------------- contrato

    @Test
    fun `repository usa o mesmo arquivo e chaves do motor`() {
        // Contrato central da feature: NÃO duplica storage — usa cfi_bridge_prefs.
        assertEquals("cfi_bridge_prefs", PrefsBridgeProvisioningRepository.PREFS_FILE)
        assertEquals("api_base_url", PrefsBridgeProvisioningRepository.KEY_BASE_URL)
        assertEquals("bridge_token", PrefsBridgeProvisioningRepository.KEY_BRIDGE_TOKEN)
        assertEquals("bridge-provisioning", RemoteBridgeProvisioningRepository.BRIDGE_PROVISIONING_FUNCTION)
    }

    @Test
    fun `outcome das falhas nunca expoe o JWT`() {
        val http = FakeHttpClient { _ -> throw java.io.IOException("boom") }
        val repository = repo(http, emptyStorage())

        val result = repository.ensureConfigured()

        assertTrue(result is RemoteProvisioningOutcome.Failed)
        assertFalse(result.toString().contains("jwt-sessao"))
        assertFalse((result as RemoteProvisioningOutcome.Failed).message.contains("jwt"))
    }
}