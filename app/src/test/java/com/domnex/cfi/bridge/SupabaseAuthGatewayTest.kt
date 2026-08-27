package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AuthResult
import com.domnex.cfi.bridge.auth.DenialReason
import com.domnex.cfi.bridge.auth.RouteTarget
import com.domnex.cfi.bridge.auth.AuthRouting
import com.domnex.cfi.bridge.auth.SupabaseAuthGateway
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.HttpResponse
import com.domnex.cfi.bridge.auth.supabase.StoredSession
import com.domnex.cfi.bridge.auth.supabase.StoredUserSnapshot
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.auth.supabase.SupabaseSessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SupabaseAuthGatewayTest {

    private class FakeHttpClient(
        var handler: ((HttpRequest) -> HttpResponse)? = null
    ) : SupabaseHttpClient {
        val requests = mutableListOf<HttpRequest>()

        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler?.invoke(request)
                ?: HttpResponse.of(500, """{"error":"no_handler"}""")
        }
    }

    private class InMemorySessionStore : SupabaseSessionStore {
        var stored: StoredSession? = null
        override fun load(): StoredSession? = stored
        override fun save(session: StoredSession) { stored = session }
        override fun clear() { stored = null }
    }

    private val nowEpochSeconds = 1_800_000_000L
    private var now = nowEpochSeconds

    private fun config(
        url: String = "https://projeto.supabase.co",
        key: String = "anon-test-key"
    ) = SupabaseAuthConfig(projectUrl = url, anonKey = key)

    private fun tokenJson(
        userId: String,
        accessToken: String = "access-1",
        refreshToken: String = "refresh-1",
        expiresIn: Long = 3600
    ): String {
        // expires_at omitido de propósito: cobre o cálculo now + expires_in.
        return """{"access_token":"$accessToken","refresh_token":"$refreshToken",""" +
            """"expires_in":$expiresIn,"token_type":"bearer","user":{"id":"$userId","email":"u@test.com"}}"""
    }

    private fun profilesJson(vararg rows: String): String =
        "[" + rows.joinToString(",") + "]"

    private fun profileRow(
        id: String,
        role: String,
        status: String,
        clientName: String? = null
    ): String {
        val embed = clientName?.let { ""","client":{"name":"$it"}""" } ?: ""
        return """{"id":"$id","name":"Usuário Teste","email":"u@test.com","role":"$role",""" +
            """"status":"$status","created_at":"2026-08-01T12:00:00+00:00"$embed}"""
    }

    private fun newGateway(
        http: FakeHttpClient,
        cfg: SupabaseAuthConfig = config(),
        store: InMemorySessionStore = InMemorySessionStore()
    ): Pair<SupabaseAuthGateway, InMemorySessionStore> {
        val gateway = SupabaseAuthGateway(cfg, http, store) { now }
        return gateway to store
    }

    private fun loginRoutingHandler(
        role: String,
        status: String,
        clientName: String? = "Cliente X"
    ): (HttpRequest) -> HttpResponse = { request ->
        when {
            request.url.endsWith("/token?grant_type=password") -> {
                assertTrue("apikey header ausente", request.headers["apikey"] == "anon-test-key")
                HttpResponse.of(200, tokenJson(userId = "uid-1"))
            }
            "/bridge_profiles?" in request.url -> {
                assertTrue(
                    "Authorization Bearer ausente no PostgREST",
                    request.headers["Authorization"] == "Bearer access-1"
                )
                HttpResponse.of(
                    200,
                    profilesJson(profileRow(id = "uid-1", role = role, status = status, clientName = clientName))
                )
            }
            else -> HttpResponse.of(404, "{}")
        }
    }

    // ------------------------------------------------------------------ login

    @Test
    fun `login CLIENT ativo autoriza e persiste sessão`() {
        val http = FakeHttpClient(loginRoutingHandler(role = "CLIENT", status = "ACTIVE"))
        val (gateway, store) = newGateway(http)

        val result = gateway.login("U@Test.com", "senha-segura")

        assertTrue(result is AuthResult.Authorized)
        val session = (result as AuthResult.Authorized).session
        assertEquals(UserRole.CLIENT, session.user.role)
        assertEquals("Cliente X", session.user.clientName)
        assertNotNull(store.stored)
        assertEquals("access-1", store.stored?.accessToken)
    }

    @Test
    fun `login DOMNEX_ADMIN ativo roteia para administracao`() {
        val http = FakeHttpClient(
            loginRoutingHandler(role = "DOMNEX_ADMIN", status = "ACTIVE", clientName = null)
        )
        val (gateway, _) = newGateway(http)

        val result = gateway.login("admin@domnex.com.br", "senha")

        assertTrue(result is AuthResult.Authorized)
        assertEquals(UserRole.DOMNEX_ADMIN, (result as AuthResult.Authorized).session.user.role)
        val target = AuthRouting.resolveTarget(result.session.user.role, bridgeConfigured = false)
        assertEquals(RouteTarget.ADMIN_HOME, target)
    }

    @Test
    fun `login CLIENT ativo roteia para fluxo operacional`() {
        val http = FakeHttpClient(loginRoutingHandler(role = "CLIENT", status = "ACTIVE"))
        val (gateway, _) = newGateway(http)

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Authorized)
        val target = AuthRouting.resolveTarget(
            (result as AuthResult.Authorized).session.user.role,
            bridgeConfigured = true
        )
        assertEquals(RouteTarget.HOME, target)
    }

    @Test
    fun `senha errada rejeita sem criar sessão`() {
        val http = FakeHttpClient(
            handlerForTokenOnly(statusCode = 400, body = """{"error":"invalid_grant"}""")
        )
        val (gateway, store) = newGateway(http)

        val result = gateway.login("u@test.com", "errada")

        assertTrue(result is AuthResult.Rejected)
        assertNull(store.stored)
    }

    @Test
    fun `perfil suspenso nega login mesmo com credenciais válidas`() {
        val http = FakeHttpClient(loginRoutingHandler(role = "CLIENT", status = "SUSPENDED"))
        val (gateway, store) = newGateway(http)

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Denied)
        assertEquals(DenialReason.PROFILE_SUSPENDED, (result as AuthResult.Denied).reason)
        assertNull(store.stored)
    }

    @Test
    fun `perfil pendente nega login`() {
        val http = FakeHttpClient(loginRoutingHandler(role = "CLIENT", status = "PENDING"))
        val (gateway, _) = newGateway(http)

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Denied)
        assertEquals(DenialReason.PROFILE_PENDING, (result as AuthResult.Denied).reason)
    }

    @Test
    fun `usuário sem profile nega login`() {
        val http = FakeHttpClient { request ->
            when {
                request.url.endsWith("/token?grant_type=password") ->
                    HttpResponse.of(200, tokenJson(userId = "uid-1"))
                "/bridge_profiles?" in request.url -> HttpResponse.of(200, "[]")
                else -> HttpResponse.of(404, "{}")
            }
        }
        val (gateway, _) = newGateway(http)

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Denied)
        assertEquals(DenialReason.PROFILE_MISSING, (result as AuthResult.Denied).reason)
    }

    @Test
    fun `backend não configurado nega sem chamar rede`() {
        val http = FakeHttpClient()
        val (gateway, _) = newGateway(http, cfg = config(url = "", key = ""))

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Denied)
        assertEquals(DenialReason.NOT_CONFIGURED, (result as AuthResult.Denied).reason)
        assertEquals(0, http.requests.size)
    }

    @Test
    fun `erro de rede no login vira NETWORK_ERROR`() {
        val http = FakeHttpClient(handler = { throw IOException("offline") })
        val (gateway, _) = newGateway(http)

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Denied)
        assertEquals(DenialReason.NETWORK_ERROR, (result as AuthResult.Denied).reason)
    }

    @Test
    fun `servidor 500 no login vira SERVER_ERROR`() {
        val http = FakeHttpClient(handlerForTokenOnly(500, """{"error":"oops"}"""))
        val (gateway, _) = newGateway(http)

        val result = gateway.login("u@test.com", "senha")

        assertTrue(result is AuthResult.Denied)
        assertEquals(DenialReason.SERVER_ERROR, (result as AuthResult.Denied).reason)
    }

    // --------------------------------------------------------- currentSession

    @Test
    fun `sessão válida não expirada restaura sem rede`() {
        val http = FakeHttpClient()
        val store = InMemorySessionStore()
        store.save(storedSession(expiresIn = 3600))
        val (gateway, _) = newGateway(http, store = store)

        val session = gateway.currentSession()

        assertNotNull(session)
        assertEquals("uid-1", session?.user?.id)
        assertEquals(0, http.requests.size)
    }

    @Test
    fun `sessão expirada com refresh válido renova e mantém acesso`() {
        var refreshedOnce = false
        val http = FakeHttpClient { request ->
            when {
                request.url.endsWith("/token?grant_type=refresh_token") -> {
                    refreshedOnce = true
                    HttpResponse.of(
                        200,
                        tokenJson(userId = "uid-1", accessToken = "access-2", refreshToken = "refresh-2")
                    )
                }
                "/bridge_profiles?" in request.url -> HttpResponse.of(
                    200,
                    profilesJson(profileRow(id = "uid-1", role = "CLIENT", status = "ACTIVE"))
                )
                else -> HttpResponse.of(404, "{}")
            }
        }
        val store = InMemorySessionStore()
        store.save(storedSession(expiresIn = -60)) // já expirada
        val (gateway, _) = newGateway(http, store = store)

        val session = gateway.currentSession()

        assertTrue(refreshedOnce)
        assertNotNull(session)
        assertEquals("access-2", store.stored?.accessToken)
    }

    @Test
    fun `refresh token revogado invalida sessão`() {
        val http = FakeHttpClient { request ->
            if (request.url.endsWith("/token?grant_type=refresh_token")) {
                HttpResponse.of(400, """{"error":"invalid_grant","error_description":"Invalid Refresh Token"}""")
            } else {
                HttpResponse.of(404, "{}")
            }
        }
        val store = InMemorySessionStore()
        store.save(storedSession(expiresIn = -3600))
        val (gateway, _) = newGateway(http, store = store)

        val session = gateway.currentSession()

        assertNull(session)
        assertNull(store.stored)
    }

    @Test
    fun `sem sessão armazenada currentSession é nulo`() {
        val http = FakeHttpClient()
        val (gateway, _) = newGateway(http)

        assertNull(gateway.currentSession())
        assertNull(gateway.currentUser())
        assertEquals(0, http.requests.size)
    }

    @Test
    fun `currentAccessToken renova JWT expirado antes de devolver`() {
        var refreshedOnce = false
        val http = FakeHttpClient { request ->
            when {
                request.url.endsWith("/token?grant_type=refresh_token") -> {
                    refreshedOnce = true
                    HttpResponse.of(
                        200,
                        tokenJson(userId = "uid-1", accessToken = "access-2", refreshToken = "refresh-2")
                    )
                }
                "/bridge_profiles?" in request.url -> HttpResponse.of(
                    200,
                    profilesJson(profileRow(id = "uid-1", role = "DOMNEX_ADMIN", status = "ACTIVE"))
                )
                else -> HttpResponse.of(404, "{}")
            }
        }
        val store = InMemorySessionStore()
        store.save(storedSession(expiresIn = -60)) // JWT já expirada
        val (gateway, _) = newGateway(http, store = store)

        val token = gateway.currentAccessToken()

        assertTrue(refreshedOnce)
        // Nunca devolve o JWT vencido: retorna o token renovado e persistido.
        assertEquals("access-2", token)
        assertEquals("access-2", store.stored?.accessToken)
    }

    // ----------------------------------------------------------------- logout

    @Test
    fun `logout revoga token e limpa sessão local`() {
        val http = FakeHttpClient { request ->
            if (request.url.endsWith("/auth/v1/logout")) {
                assertEquals("Bearer access-1", request.headers["Authorization"])
                HttpResponse.of(204, "")
            } else {
                HttpResponse.of(404, "{}")
            }
        }
        val store = InMemorySessionStore()
        store.save(storedSession(expiresIn = 3600))
        val (gateway, _) = newGateway(http, store = store)

        assertNotNull(gateway.currentSession())
        gateway.logout()

        assertNull(store.stored)
        assertNull(gateway.currentUser())
        assertTrue(http.requests.any { it.url.endsWith("/auth/v1/logout") })
    }

    // ---------------------------------------------------------------- helpers

    private fun handlerForTokenOnly(
        statusCode: Int,
        body: String
    ): (HttpRequest) -> HttpResponse = { _ -> HttpResponse.of(statusCode, body) }

    private fun storedSession(expiresIn: Long): StoredSession = StoredSession(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresAtEpochSeconds = 0L, // recalculado abaixo para ficar explícito
        user = StoredUserSnapshot(
            id = "uid-1",
            name = "Usuário Teste",
            email = "u@test.com",
            role = UserRole.CLIENT.name,
            clientName = "Cliente X",
            status = "ACTIVE",
            createdAtMillis = 1_700_000_000_000
        )
    ).let {
        // expiresIn positivo => válida; negativo => expirada em relação a `now`.
        it.copy(expiresAtEpochSeconds = nowEpochSeconds + expiresIn)
    }
}
