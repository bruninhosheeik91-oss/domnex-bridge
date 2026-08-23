package com.domnex.cfi.bridge.auth

import com.domnex.cfi.bridge.auth.supabase.BridgeClientRow
import com.domnex.cfi.bridge.auth.supabase.BridgeProfileRow
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.auth.supabase.toUserAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exceção de transporte/negócio do diretório remoto. A UI captura para exibir erro.
 */
class DirectoryRequestException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Implementação REAL de [UserDirectory] sobre o backend Supabase.
 *
 * - Leitura de perfis/clientes: PostgREST direto — a segurança vem das policies RLS
 *   no banco (um CLIENT nunca recebe linhas de outros clientes; só DOMNEX_ADMIN
 *   ativo enxerga tudo). Nada é decidido apenas na interface.
 * - Suspender/reabilitar: RPC seguro [bridge_admin_set_user_status], SECURITY DEFINER,
 *   que revalida o papel do chamador no servidor.
 * - Criar novo acesso: exige função de backend privilegiada (Edge Function
 *   `admin-create-access`), pois criar usuários do Auth requer service_role — que
 *   NUNCA vai para dentro do APK. Se a Edge Function ainda não estiver implantada,
 *   a operação falha explicitamente (sem simular sucesso).
 *
 * Métodos bloqueantes: chamar fora da main thread.
 */
class RemoteUserDirectory(
    private val config: SupabaseAuthConfig,
    private val httpClient: SupabaseHttpClient,
    private val accessTokenProvider: () -> String?
) : UserDirectory {

    private val json = Json { ignoreUnknownKeys = true }

    override fun listUsers(query: String, filter: AccessFilter): List<UserAccount> {
        val rows = fetchProfiles()
        val q = query.trim().lowercase()
        return rows.asSequence()
            .map { it.toUserAccount() }
            .filter { user ->
                when (filter) {
                    AccessFilter.ALL -> true
                    AccessFilter.CLIENTS -> user.role == UserRole.CLIENT
                    AccessFilter.ADMINS -> user.role == UserRole.DOMNEX_ADMIN
                    AccessFilter.SUSPENDED -> user.status == UserStatus.SUSPENDED
                }
            }
            .filter { user ->
                q.isEmpty() ||
                    user.name.lowercase().contains(q) ||
                    user.email.lowercase().contains(q) ||
                    (user.clientName?.lowercase()?.contains(q) ?: false)
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    override fun getUser(userId: String): UserAccount? =
        fetchProfiles(id = userId).firstOrNull()?.toUserAccount()

    override fun findClientNames(): List<String> {
        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.restUrl("/bridge_clients?select=name&order=name.asc"),
                    method = "GET",
                    headers = baseHeaders()
                )
            )
        } catch (e: java.io.IOException) {
            throw DirectoryRequestException("Falha de rede ao carregar clientes.", e)
        }
        ensureSuccess(response, "clientes")
        val rows = runCatching {
            json.decodeFromString<List<BridgeClientRow>>(response.bodyText())
        }.getOrThrow()
        return rows.map { it.name }
    }

    override fun createAccess(
        name: String,
        email: String,
        role: UserRole,
        clientName: String?,
        status: UserStatus
    ): CreateUserOutcome {
        if (!config.isConfigured()) {
            return CreateUserOutcome.Failed("Backend Supabase não configurado neste build.")
        }
        val token = accessTokenProvider()
            ?: return CreateUserOutcome.Failed("Sessão administrativa expirada. Entre novamente.")

        val body = json.encodeToString(
            CreateAccessRequest.serializer(),
            CreateAccessRequest(name.trim(), email.trim().lowercase(), role.name, clientName?.trim(), status.name)
        ).toByteArray(Charsets.UTF_8)

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.functionsUrl(ADMIN_CREATE_ACCESS_FUNCTION),
                    method = "POST",
                    headers = baseHeaders(token),
                    body = body
                )
            )
        } catch (e: java.io.IOException) {
            return CreateUserOutcome.Failed("Falha de rede ao contatar o backend.")
        }

        val parsed = runCatching {
            json.decodeFromString(CreateAccessResponse.serializer(), response.bodyText())
        }.getOrNull()

        return when {
            response.statusCode == 201 || response.statusCode == 200 ->
                CreateUserOutcome.Created(
                    UserAccount(
                        id = parsed?.userId.orEmpty(),
                        name = name.trim(),
                        email = email.trim().lowercase(),
                        role = role,
                        clientName = if (role == UserRole.CLIENT) clientName?.trim() else null,
                        status = status
                    )
                )
            response.statusCode == 409 -> CreateUserOutcome.EmailInUse
            else -> CreateUserOutcome.Failed(
                parsed?.detail ?: parsed?.error ?: "Backend recusou a criação (HTTP ${response.statusCode})."
            )
        }
    }

    override fun setStatus(userId: String, status: UserStatus): Boolean {
        if (!config.isConfigured()) return false
        val token = accessTokenProvider() ?: return false

        val body = buildJsonObject {
            put("p_user_id", userId)
            put("p_new_status", status.name)
        }

        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = config.restUrl("/rpc/bridge_admin_set_user_status"),
                    method = "POST",
                    headers = baseHeaders(token),
                    body = json.encodeToString(
                        kotlinx.serialization.json.JsonObject.serializer(),
                        body
                    ).toByteArray(Charsets.UTF_8)
                )
            )
            response.statusCode in 200..299
        } catch (_: java.io.IOException) {
            false
        }
    }

    private fun fetchProfiles(id: String? = null): List<BridgeProfileRow> {
        if (!config.isConfigured()) {
            throw DirectoryRequestException("Backend Supabase não configurado neste build.")
        }
        var url = config.restUrl("/bridge_profiles") +
            "?select=id,name,email,role,client_id,status,created_at,updated_at,client:bridge_clients(name)" +
            "&order=name.asc"
        if (id != null) url += "&id=eq.$id"
        val response = try {
            httpClient.execute(HttpRequest(url, "GET", baseHeaders()))
        } catch (e: java.io.IOException) {
            throw DirectoryRequestException("Falha de rede ao carregar acessos.", e)
        }
        ensureSuccess(response, "acessos")
        return runCatching { json.decodeFromString<List<BridgeProfileRow>>(response.bodyText()) }.getOrThrow()
    }

    private fun baseHeaders(accessToken: String? = null): Map<String, String> {
        val token = accessToken ?: accessTokenProvider()
        val headers = linkedMapOf(
            "apikey" to config.anonKey,
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )
        if (token != null) headers["Authorization"] = "Bearer $token"
        return headers
    }

    private fun ensureSuccess(response: com.domnex.cfi.bridge.auth.supabase.HttpResponse, what: String) {
        if (response.statusCode in 200..299) return
        val message = when (response.statusCode) {
            401, 403 -> "Sessão sem permissão administrativa para $what."
            else -> "Backend indisponível ($what, HTTP ${response.statusCode})."
        }
        throw DirectoryRequestException(message)
    }

    companion object {
        const val ADMIN_CREATE_ACCESS_FUNCTION = "admin-create-access"
    }
}

@Serializable
private data class CreateAccessRequest(
    val name: String,
    val email: String,
    val role: String,
    @kotlinx.serialization.SerialName("client_name") val clientName: String?,
    val status: String
)

@Serializable
private data class CreateAccessResponse(
    @kotlinx.serialization.SerialName("user_id") val userId: String? = null,
    val email: String? = null,
    val role: String? = null,
    @kotlinx.serialization.SerialName("client_name") val clientName: String? = null,
    val status: String? = null,
    val error: String? = null,
    val detail: String? = null
)
