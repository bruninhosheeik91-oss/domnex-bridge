package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.AccessUpdate
import com.domnex.cfi.bridge.auth.ClientRef
import com.domnex.cfi.bridge.auth.CreateUserOutcome
import com.domnex.cfi.bridge.auth.DirectoryRequestException
import com.domnex.cfi.bridge.auth.EmailChangeOutcome
import com.domnex.cfi.bridge.auth.PasswordResetOutcome
import com.domnex.cfi.bridge.auth.RemoteUserDirectory
import com.domnex.cfi.bridge.auth.StatusChangeOutcome
import com.domnex.cfi.bridge.auth.UpdateAccessOutcome
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

    // -------------------------------------------------------- findClients

    @Test
    fun `findClients consulta bridge_clients ordenado com id e nome`() {
        val http = FakeHttpClient { request ->
            assertTrue("/bridge_clients?" in request.url)
            assertTrue("select=id,name" in request.url)
            assertTrue("order=name.asc" in request.url)
            HttpResponse.of(
                200,
                """[{"id":"c-2","name":"Mercado Central"},{"id":"c-1","name":"Padaria Estrela"}]"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val clients = directory.findClients()
        assertEquals(
            listOf(ClientRef("c-2", "Mercado Central"), ClientRef("c-1", "Padaria Estrela")),
            clients
        )
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

        assertEquals(StatusChangeOutcome.Updated, directory.setStatus("uid-9", UserStatus.SUSPENDED))
    }

    @Test
    fun `setStatus recusado pelo backend retorna falha com mensagem do servidor`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(403, """{"message":"Somente DOMNEX_ADMIN ativo pode alterar status."}""")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val outcome = directory.setStatus("uid-9", UserStatus.ACTIVE)
        assertTrue(outcome is StatusChangeOutcome.Failed)
        assertEquals("Somente DOMNEX_ADMIN ativo pode alterar status.", (outcome as StatusChangeOutcome.Failed).message)
    }

    @Test
    fun `setStatus sem sessão administrativa falha explicitamente`() {
        val http = FakeHttpClient()
        val directory = RemoteUserDirectory(config, http) { null }

        val outcome = directory.setStatus("uid-9", UserStatus.SUSPENDED)
        assertTrue(outcome is StatusChangeOutcome.Failed)
        assertEquals(0, http.requests.size)
    }

    // ------------------------------------------------------------ updateAccess

    @Test
    fun `updateAccess envia somente campos alterados para RPC segura`() {
        val http = FakeHttpClient { request ->
            assertTrue("/rpc/bridge_admin_update_access" in request.url)
            val bodyText = String(request.body!!, Charsets.UTF_8)
            assertTrue(bodyText.contains("\"p_target_user_id\":\"uid-9\""))
            assertTrue(bodyText.contains("\"p_name\":\"Novo Nome\""))
            assertTrue(bodyText.contains("\"p_client_id\":\"c-7\""))
            assertFalse(bodyText.contains("p_role"))
            assertFalse(bodyText.contains("p_status"))
            assertFalse(bodyText.contains("p_clear_client"))
            HttpResponse.of(204, "")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.updateAccess(
            "uid-9",
            AccessUpdate(name = " Novo Nome ", clientId = "c-7")
        )
        assertEquals(UpdateAccessOutcome.Updated, outcome)
    }

    @Test
    fun `updateAccess limpeza de cliente usa p_clear_client`() {
        val http = FakeHttpClient { request ->
            val bodyText = String(request.body!!, Charsets.UTF_8)
            assertTrue(bodyText.contains("\"p_clear_client\":true"))
            assertFalse(bodyText.contains("p_client_id"))
            HttpResponse.of(204, "")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        assertEquals(UpdateAccessOutcome.Updated, directory.updateAccess("uid-9", AccessUpdate(clearClient = true)))
    }

    @Test
    fun `updateAccess rejeitado por constraint devolve mensagem real do backend`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(
                400,
                """{"code":"23514","message":"CLIENT ACTIVE/SUSPENDED exige cliente vinculado.","details":null,"hint":null}"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.updateAccess("uid-9", AccessUpdate(status = UserStatus.ACTIVE))
        assertTrue(outcome is UpdateAccessOutcome.Failed)
        assertEquals("CLIENT ACTIVE/SUSPENDED exige cliente vinculado.", (outcome as UpdateAccessOutcome.Failed).message)
    }

    @Test
    fun `updateAccess sem mudanças não chama o backend`() {
        val http = FakeHttpClient()
        val directory = RemoteUserDirectory(config, http) { null }

        assertEquals(UpdateAccessOutcome.Updated, directory.updateAccess("uid-9", AccessUpdate()))
        assertEquals(0, http.requests.size)
    }

    // -------------------------------------------------------------- changeEmail

    @Test
    fun `changeEmail chama Edge Function privilegiada com payload correto`() {
        val http = FakeHttpClient { request ->
            assertTrue(request.url.endsWith("/functions/v1/admin-update-email"))
            val bodyText = String(request.body!!, Charsets.UTF_8)
            assertTrue(bodyText.contains("\"user_id\":\"uid-9\""))
            assertTrue(bodyText.contains("\"email\":\"novo@cliente.com\""))
            HttpResponse.of(200, """{"user_id":"uid-9","email":"novo@cliente.com","updated":true}""")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        assertEquals(EmailChangeOutcome.Changed, directory.changeEmail("uid-9", "Novo@Cliente.com"))
    }

    @Test
    fun `changeEmail duplicado vira falha clara`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(409, """{"error":"EMAIL_IN_USE"}""") }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.changeEmail("uid-9", "usado@cliente.com")
        assertTrue(outcome is EmailChangeOutcome.Failed)
        assertEquals("Já existe um acesso com este e-mail.", (outcome as EmailChangeOutcome.Failed).message)
    }

    @Test
    fun `changeEmail falha do backend nunca vira sucesso`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(500, """{"detail":"auth offline"}""") }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.changeEmail("uid-9", "x@y.com")
        assertTrue(outcome is EmailChangeOutcome.Failed)
        assertEquals("auth offline", (outcome as EmailChangeOutcome.Failed).message)
    }

    // -------------------------------------------------------- sendPasswordReset

    @Test
    fun `sendPasswordReset resolve usuario e chama recover real do Supabase`() {
        val http = FakeHttpClient { request ->
            if ("/rest/v1/bridge_profiles" in request.url) {
                HttpResponse.of(200, profilesPayload(row("uid-9", "Ana Clara", "ana@loja.com")))
            } else {
                assertTrue(request.url.endsWith("/auth/v1/recover"))
                assertTrue(!request.headers.containsKey("Authorization")) // endpoint público real
                val bodyText = String(request.body!!, Charsets.UTF_8)
                assertTrue(bodyText.contains("\"email\":\"ana@loja.com\""))
                HttpResponse.of(200, "{}")
            }
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        assertEquals(PasswordResetOutcome.Requested, directory.sendPasswordReset("uid-9"))
    }

    @Test
    fun `sendPasswordReset usuario inexistente falha sem chamar recover`() {
        val http = FakeHttpClient { request ->
            if ("/rest/v1/bridge_profiles" in request.url) {
                HttpResponse.of(200, "[]")
            } else {
                throw AssertionError("recover não deveria ser chamado")
            }
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val outcome = directory.sendPasswordReset("missing")
        assertTrue(outcome is PasswordResetOutcome.Failed)
    }

    @Test
    fun `sendPasswordReset erro do backend vira falha`() {
        val http = FakeHttpClient { request ->
            if ("/rest/v1/bridge_profiles" in request.url) {
                HttpResponse.of(200, profilesPayload(row("uid-9", "Ana Clara", "ana@loja.com")))
            } else {
                HttpResponse.of(429, """{"message":"rate limited"}""")
            }
        }
        val directory = RemoteUserDirectory(config, http) { "tok-1" }

        val outcome = directory.sendPasswordReset("uid-9")
        assertTrue(outcome is PasswordResetOutcome.Failed)
        // A mensagem real do servidor é preservada — nunca disfarçada de sucesso.
        assertEquals("rate limited", (outcome as PasswordResetOutcome.Failed).message)
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
