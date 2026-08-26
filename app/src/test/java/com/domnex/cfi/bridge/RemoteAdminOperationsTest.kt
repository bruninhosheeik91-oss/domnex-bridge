package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.CreateUserOutcome
import com.domnex.cfi.bridge.auth.DeleteClientOutcome
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
import java.io.IOException

/**
 * Operações administrativas privilegiadas contra o backend real (via HTTP fake):
 * criação de acesso COM senha inicial e exclusão definitiva de cliente.
 * Garante que o que sai do APK é exatamente o contrato das Edge Functions —
 * e que nenhuma chave privilegiada (service_role) participa do fluxo.
 */
class RemoteAdminOperationsTest {

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

    private val config = SupabaseAuthConfig(
        projectUrl = "https://projeto.supabase.co",
        anonKey = "anon-test-key"
    )

    // --------------------------------------------------- criação com senha

    @Test
    fun `createAccess envia senha inicial no corpo e trata 201 como Created`() {
        lateinit var request: HttpRequest
        val http = FakeHttpClient { req ->
            request = req
            HttpResponse.of(
                201,
                """{"user_id":"u-novo","email":"novo@cliente.com","role":"CLIENT",""" +
                    """"client_name":"Padaria Estrela","status":"ACTIVE"}"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.createAccess(
            name = "Novo Usuário",
            email = "novo@cliente.com",
            role = UserRole.CLIENT,
            clientName = "Padaria Estrela",
            status = UserStatus.ACTIVE,
            initialPassword = "senha-segura-123"
        )

        assertTrue(outcome is CreateUserOutcome.Created)
        assertEquals("POST", request.method)
        assertTrue(request.url.endsWith("/functions/v1/admin-create-access"))
        assertEquals("Bearer tok-admin", request.headers["Authorization"])
        assertEquals("anon-test-key", request.headers["apikey"])

        val body = String(request.body!!, Charsets.UTF_8)
        assertTrue(body.contains("\"password\":\"senha-segura-123\""))
        assertTrue(body.contains("\"name\":\"Novo Usuário\""))
        assertTrue(body.contains("\"client_name\":\"Padaria Estrela\""))
    }

    @Test
    fun `createAccess sem senha inicial omite o campo password - fluxo legado de convite`() {
        lateinit var request: HttpRequest
        val http = FakeHttpClient { req ->
            request = req
            HttpResponse.of(201, """{"user_id":"u-novo"}""")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.createAccess(
            "Alguém", "alguem@cliente.com", UserRole.CLIENT, "Cliente X", UserStatus.PENDING
        )

        assertTrue(outcome is CreateUserOutcome.Created)
        val body = String(request.body!!, Charsets.UTF_8)
        assertFalse(body.contains("password"))
    }

    @Test
    fun `createAccess recusado pelo servidor nunca vira sucesso`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(403, """{"error":"FORBIDDEN"}""")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-cliente" }

        val outcome = directory.createAccess(
            "X", "x@y.com", UserRole.CLIENT, "Cli", UserStatus.ACTIVE, initialPassword = "senha-segura-123"
        )

        assertTrue(outcome is CreateUserOutcome.Failed)
    }

    @Test
    fun `createAccess não carrega segredo privilegiado no corpo ou headers`() {
        lateinit var request: HttpRequest
        val http = FakeHttpClient { req ->
            request = req
            HttpResponse.of(201, """{"user_id":"u-1"}""")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }
        directory.createAccess(
            "X", "x@y.com", UserRole.DOMNEX_ADMIN, null, UserStatus.ACTIVE, initialPassword = "senha-forte-9"
        )

        val body = String(request.body!!, Charsets.UTF_8)
        assertFalse(body.contains("service_role"))
        assertFalse(request.headers.values.any { it.contains("service") })
        // A única autenticação é o JWT da sessão + a apikey pública (anon).
        assertEquals("Bearer tok-admin", request.headers["Authorization"])
        assertEquals("anon-test-key", request.headers["apikey"])
    }

    // ------------------------------------------ exclusão definitiva cliente

    @Test
    fun `deleteClient sucesso remove usuário Auth e cliente via Edge Function`() {
        lateinit var request: HttpRequest
        val http = FakeHttpClient { req ->
            request = req
            HttpResponse.of(
                200,
                """{"client_id":"c-1","client_name":"Padaria Estrela","deleted_users":3}"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.deleteClient("c-1")

        assertEquals(DeleteClientOutcome.Deleted("Padaria Estrela"), outcome)
        assertEquals("POST", request.method)
        assertTrue(request.url.endsWith("/functions/v1/admin-delete-client"))
        assertEquals("Bearer tok-admin", request.headers["Authorization"])
        val body = String(request.body!!, Charsets.UTF_8)
        assertTrue(body.contains("\"client_id\":\"c-1\""))
        assertFalse(body.contains("service_role"))
    }

    @Test
    fun `deleteClient com cliente inexistente falha sem fingir exclusão`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(404, """{"error":"CLIENT_NOT_FOUND"}""")
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.deleteClient("c-404")

        assertTrue(outcome is DeleteClientOutcome.Failed)
        assertEquals("Cliente não encontrado.", (outcome as DeleteClientOutcome.Failed).message)
    }

    @Test
    fun `deleteClient bloqueia auto-exclusão e não administrador no servidor`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(
                403,
                """{"error":"SELF_DELETE_FORBIDDEN","detail":"O administrador está vinculado ao próprio cliente."}"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin-vinculado" }

        val outcome = directory.deleteClient("c-1")

        assertTrue(outcome is DeleteClientOutcome.Failed)
        assertTrue((outcome as DeleteClientOutcome.Failed).message.contains("administrador"))
    }

    @Test
    fun `deleteClient com falha parcial NÃO exclui o cliente nem reporta sucesso`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(
                207,
                """{"error":"PARTIAL_DELETE_FAILED","detail":"Alguns usuários não puderam ser removidos. """ +
                    """O cliente NÃO foi excluído; tente novamente.","deleted_users":2,"remaining_users":1,""" +
                    """"failed_users":[{"user_id":"u-3","email":"c@x.com","error":"boom"}]}"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.deleteClient("c-1")

        assertTrue(outcome is DeleteClientOutcome.Failed)
        assertFalse(outcome is DeleteClientOutcome.Deleted)
        assertTrue((outcome as DeleteClientOutcome.Failed).message.contains("NÃO foi excluído"))
    }

    @Test
    fun `deleteClient com perfis residuais falha coerentemente`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(
                207,
                """{"error":"RESIDUAL_PROFILES","detail":"Ainda existem perfis vinculados ao cliente. Ele NÃO foi excluído.","remaining_users":2}"""
            )
        }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.deleteClient("c-1")
        assertTrue(outcome is DeleteClientOutcome.Failed)
    }

    @Test
    fun `deleteClient com falha de rede retorna Failed - nunca sucesso`() {
        val http = FakeHttpClient { _ -> throw IOException("offline") }
        val directory = RemoteUserDirectory(config, http) { "tok-admin" }

        val outcome = directory.deleteClient("c-1")
        assertTrue(outcome is DeleteClientOutcome.Failed)
    }

    @Test
    fun `deleteClient sem backend configurado falha explicitamente`() {
        val unconfigured = SupabaseAuthConfig(projectUrl = "", anonKey = "")
        val directory = RemoteUserDirectory(unconfigured, FakeHttpClient()) { null }

        val outcome = directory.deleteClient("c-1")
        assertTrue(outcome is DeleteClientOutcome.Failed)
    }

    @Test
    fun `createAccess sem backend configurado falha explicitamente`() {
        val unconfigured = SupabaseAuthConfig(projectUrl = "", anonKey = "")
        val directory = RemoteUserDirectory(unconfigured, FakeHttpClient()) { null }

        val outcome = directory.createAccess(
            "X", "x@y.com", UserRole.CLIENT, "Cli", UserStatus.ACTIVE, initialPassword = "senha-forte-9"
        )
        assertTrue(outcome is CreateUserOutcome.Failed)
    }
}
