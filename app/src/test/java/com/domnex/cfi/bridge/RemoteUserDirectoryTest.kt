package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.CreateUserOutcome
import com.domnex.cfi.bridge.auth.DirectoryRequestException
import com.domnex.cfi.bridge.auth.RemoteUserDirectory
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.HttpResponse
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUserDirectoryTest {

    private class FakeHttpClient(
        var handler: ((HttpRequest) -> HttpResponse)? = null
    ) : SupabaseHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler?.invoke(request) ?: HttpResponse.of(500, """{"error":"no_handler"}""")
        }
    }

    private val config = SupabaseAuthConfig(
        projectUrl = "https://projeto.supabase.co",
        anonKey = "anon-test-key"
    )

    private fun profilesPayload(vararg rows: String): String =
        "[" + rows.joinToString(",") + "]"

    private fun row(
        id: String,
        name: String,
        email: String,
        role: String = "CLIENT",
        status: String = "ACTIVE",
        clientId: String? = "c-1",
        clientName: String? = null
    ): String {
        val embed = clientName?.let { ""","client":{"name":"$it"}""" } ?: ""
        return """{"id":"$id","name":"$name","email":"$email","role":"$role",""" +
            """"client_id":${clientId?.let { "\"$it\"" } ?: "null"},"status":"$status",""" +
            """"created_at":"2026-08-01T12:00:00+00:00"$embed}"""
    }

    // -------------------------------------------------------------- listUsers

    @Test
    fun `listUsers aplica filtro e busca no CLIENT autenticado`() {
        val http = FakeHttpClient { request ->
            assertTrue(request.headers["Authorization"] == "Bearer tok-1")
            HttpResponse.of(
                200,
                profilesPayload(
                    row("1", "Maria Souza", "maria@padaria.com", clientName = "Padaria Estrela"),
                    row("2", "João Lima", "joao@mercado.com", clientName = "Mercado Central"),
                    row("3", "Admin Domnex", "admin@domnex.com", role = "DOMNEX_ADMIN")
                )
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val clients = directory.listUsers(query = "", filter = AccessFilter.CLIENTS)
        assertEquals(listOf("João Lima", "Maria Souza"), clients.map { it.name })

        val mariaOnly = directory.listUsers(query = "maria", filter = AccessFilter.ALL)
        assertEquals(listOf("Maria Souza"), mariaOnly.map { it.name })

        val suspended = directory.listUsers(filter = AccessFilter.SUSPENDED)
        assertTrue(suspended.isEmpty())
    }

    @Test
    fun `listUsers mapeia status e cliente vinculado`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(
                200,
                profilesPayload(row("4", "Pedro Lima", "pedro@farmacia.com", status = "SUSPENDED"))
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val users = directory.listUsers()
        assertEquals(1, users.size)
        assertEquals(UserStatus.SUSPENDED, users[0].status)
        assertEquals("c-1", users[0].clientName) // sem embed, usa client_id
    }

    @Test
    fun `listUsers com backend não configurado lança erro claro`() {
        val http = FakeHttpClient()
        val directory = RemoteUserDirectory(
            SupabaseAuthConfig(projectUrl = "", anonKey = ""), http
        ) { "tok-1" }

        try {
            directory.listUsers()
            throw AssertionError("Deveria lançar DirectoryRequestException")
        } catch (expected: DirectoryRequestException) {
            assertTrue(expected.message!!.contains("não configurado"))
        }
    }

    // --------------------------------------------------------------- getUser

    @Test
    fun `getUser retorna usuário por id`() {
        val http = FakeHttpClient { request ->
            assertTrue("id=eq.uid-9" in request.url)
            HttpResponse.of(200, profilesPayload(row("uid-9", "Ana Clara", "ana@loja.com")))
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val user = directory.getUser("uid-9")
        assertEquals("Ana Clara", user?.name)
    }

    @Test
    fun `getUser inexistente retorna nulo`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(200, "[]") }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        assertEquals(null, directory.getUser("missing"))
    }

    // -------------------------------------------------------- findClientNames

    @Test
    fun `findClientNames consulta bridge_clients ordenado`() {
        val http = FakeHttpClient { request ->
            assertTrue("/bridge_clients?" in request.url)
            assertTrue("order=name.asc" in request.url)
            HttpResponse.of(
                200,
                """[{"id":"c-2","name":"Mercado Central"},{"id":"c-1","name":"Padaria Estrela"}]"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        assertEquals(listOf("Mercado Central", "Padaria Estrela"), directory.findClientNames())
    }

    // ---------------------------------------------------------------- setStatus

    @Test
    fun `setStatus chama RPC segura com payload correto`() {
        val http = FakeHttpClient { request ->
            assertTrue("/rpc/bridge_admin_set_user_status" in request.url)
            val bodyText = String(request.body!!, Charsets.UTF_8)
            assertTrue(bodyText.contains("\"p_user_id\":\"uid-9\""))
            assertTrue(bodyText.contains("\"p_new_status\":\"SUSPENDED\""))
            HttpResponse.of(204, "")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        assertTrue(directory.setStatus("uid-9", UserStatus.SUSPENDED))
    }

    @Test
    fun `setStatus recusado pelo backend retorna falso`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(403, """{"message":"permission denied"}""") }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        assertFalse(directory.setStatus("uid-9", UserStatus.ACTIVE))
    }

    // ------------------------------------------------------------ createAccess

    private fun createAccessHandler(statusCode: Int, body: String): (HttpRequest) -> HttpResponse =
        { request ->
            assertTrue(request.url.endsWith("/functions/v1/admin-create-access"))
            val bodyText = String(request.body ?: ByteArray(0), Charsets.UTF_8)
            if (statusCode == 201 || statusCode == 200) {
                assertTrue(bodyText.contains("\"role\":\"CLIENT\""))
                assertTrue(bodyText.contains("\"client_name\":\"Padaria Estrela\""))
            }
            HttpResponse.of(statusCode, body)
        }

    @Test
    fun `createAccess sucesso via Edge Function`() {
        val http = FakeHttpClient(
            createAccessHandler(
                201,
                """{"user_id":"uid-new","email":"novo@cliente.com","role":"CLIENT",""" +
                    """"client_name":"Padaria Estrela","status":"ACTIVE","invite_sent":true}"""
            )
        )
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.createAccess(
            name = "Novo Usuário",
            email = "Novo@Cliente.com",
            role = UserRole.CLIENT,
            clientName = "Padaria Estrela",
            status = UserStatus.ACTIVE
        )

        assertTrue(outcome is CreateUserOutcome.Created)
        val created = (outcome as CreateUserOutcome.Created).user
        assertEquals("uid-new", created.id)
        assertEquals("novo@cliente.com", created.email)
        assertEquals(UserRole.CLIENT, created.role)
    }

    @Test
    fun `createAccess e-mail duplicado vira EmailInUse`() {
        val http = FakeHttpClient(createAccessHandler(409, """{"error":"EMAIL_IN_USE"}"""))
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.createAccess("X", "x@y.com", UserRole.CLIENT, "Cli", UserStatus.ACTIVE)

        assertEquals(CreateUserOutcome.EmailInUse, outcome)
    }

    @Test
    fun `createAccess falha de backend vira Failed com mensagem`() {
        val http = FakeHttpClient(createAccessHandler(500, """{"detail":"smtp offline"}"""))
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.createAccess("X", "x@y.com", UserRole.CLIENT, "Cli", UserStatus.ACTIVE)

        assertTrue(outcome is CreateUserOutcome.Failed)
        assertEquals("smtp offline", (outcome as CreateUserOutcome.Failed).message)
    }

    @Test
    fun `createAccess sem sessão administrativa falha explicitamente`() {
        val http = FakeHttpClient()
        val directory = RemoteUserDirectory(config, http) { null }

        val outcome = directory.createAccess("X", "x@y.com", UserRole.CLIENT, "Cli", UserStatus.ACTIVE)

        assertTrue(outcome is CreateUserOutcome.Failed)
        assertFalse((outcome as CreateUserOutcome.Failed).message.isBlank())
        assertEquals(0, http.requests.size)
    }
}
