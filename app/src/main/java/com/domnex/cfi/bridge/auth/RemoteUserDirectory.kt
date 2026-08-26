package com.domnex.cfi.bridge.auth

import com.domnex.cfi.bridge.auth.supabase.BridgeClientRow
import com.domnex.cfi.bridge.auth.supabase.BridgeProfileRow
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.auth.supabase.toUserAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * - Suspender/reabilitar: RPC segura [bridge_admin_set_user_status], SECURITY DEFINER,
 *   que revalida o papel do chamador no servidor.
 * - Editar nome/perfil/status/cliente: RPC segura [bridge_admin_update_access]
 *   (SECURITY DEFINER), que revalida DOMNEX_ADMIN+ACTIVE e as regras de vínculo
 *   NO SERVIDOR antes de aplicar.
 * - Criar novo acesso / trocar e-mail: exigem funções de backend privilegiadas
 *   (Edge Functions `admin-create-access` e `admin-update-email`), pois operações
 *   em auth.users requerem service_role — que NUNCA vai para dentro do APK.
 *   Se as Edge Functions ainda não estiverem implantadas, a operação falha
 *   explicitamente (sem simular sucesso).
 * - Redefinição de senha: endpoint público real `/auth/v1/recover` do Supabase.
 *   A entrega do e-mail depende de SMTP configurado no projeto; isso é informado
 *   claramente na interface — nunca é reportado como "redefinido".
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

    override fun findClients(): List<ClientRef> {
        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.restUrl("/bridge_clients?select=id,name&order=name.asc"),
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
        return rows.map { ClientRef(id = it.id, name = it.name) }
    }

    override fun createAccess(
        name: String,
        email: String,
        role: UserRole,
        clientName: String?,
        status: UserStatus,
        initialPassword: String?
    ): CreateUserOutcome {
        if (!config.isConfigured()) {
            return CreateUserOutcome.Failed("Backend Supabase não configurado neste build.")
        }
        val token = accessTokenProvider()
            ?: return CreateUserOutcome.Failed("Sessão administrativa expirada. Entre novamente.")

        val body = json.encodeToString(
            CreateAccessRequest.serializer(),
            CreateAccessRequest(
                name.trim(),
                email.trim().lowercase(),
                role.name,
                clientName?.trim()?.takeIf { it.isNotEmpty() },
                status.name,
                initialPassword?.takeIf { it.isNotEmpty() }
            )
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

    override fun deleteClient(clientId: String): DeleteClientOutcome {
        if (!config.isConfigured()) {
            return DeleteClientOutcome.Failed("Backend Supabase não configurado neste build.")
        }
        val token = accessTokenProvider()
            ?: return DeleteClientOutcome.Failed("Sessão administrativa expirada. Entre novamente.")

        val body = buildJsonObject { put("client_id", clientId) }

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.functionsUrl(ADMIN_DELETE_CLIENT_FUNCTION),
                    method = "POST",
                    headers = baseHeaders(token),
                    body = json.encodeToString(JsonObject.serializer(), body)
                        .toByteArray(Charsets.UTF_8)
                )
            )
        } catch (_: java.io.IOException) {
            return DeleteClientOutcome.Failed("Falha de rede ao contatar o backend.")
        }

        val parsed = runCatching {
            json.decodeFromString(ClientDeleteResponse.serializer(), response.bodyText())
        }.getOrNull()

        return when {
            // 207 Multi-Status = exclusão PARCIAL/abortada: NUNCA é sucesso.
            response.statusCode == 207 ->
                DeleteClientOutcome.Failed(
                    parsed?.detail
                        ?: "Exclusão incompleta: o cliente NÃO foi removido. Tente novamente."
                )
            response.statusCode in 200..299 ->
                DeleteClientOutcome.Deleted(parsed?.clientName.orEmpty().ifEmpty { "Cliente" })
            response.statusCode == 404 -> DeleteClientOutcome.Failed("Cliente não encontrado.")
            else -> when (parsed?.error) {
                "SELF_DELETE_FORBIDDEN", "FORBIDDEN", "UNAUTHENTICATED" -> DeleteClientOutcome.Failed(
                    parsed?.detail ?: "Somente um administrador Domnex ativo pode excluir clientes."
                )
                "ADMIN_LINKED_PROTECTED" -> DeleteClientOutcome.Failed(
                    parsed?.detail ?: "Existem acessos administrativos vinculados a este cliente."
                )
                else -> DeleteClientOutcome.Failed(
                    parsed?.detail ?: parsed?.error
                        ?: "Backend recusou a exclusão do cliente (HTTP ${response.statusCode})."
                )
            }
        }
    }

    override fun setStatus(userId: String, status: UserStatus): StatusChangeOutcome {
        if (!config.isConfigured()) {
            return StatusChangeOutcome.Failed("Backend Supabase não configurado neste build.")
        }
        val token = accessTokenProvider()
            ?: return StatusChangeOutcome.Failed("Sessão administrativa expirada. Entre novamente.")

        val body = buildJsonObject {
            put("p_user_id", userId)
            put("p_new_status", status.name)
        }

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.restUrl("/rpc/bridge_admin_set_user_status"),
                    method = "POST",
                    headers = baseHeaders(token),
                    body = json.encodeToString(JsonObject.serializer(), body)
                        .toByteArray(Charsets.UTF_8)
                )
            )
        } catch (_: java.io.IOException) {
            return StatusChangeOutcome.Failed("Falha de rede ao contatar o backend.")
        }

        return if (response.statusCode in 200..299) {
            StatusChangeOutcome.Updated
        } else {
            StatusChangeOutcome.Failed(
                errorMessage(response.bodyText(), "Backend recusou a alteração de status (HTTP ${response.statusCode}).")
            )
        }
    }

    override fun updateAccess(userId: String, update: AccessUpdate): UpdateAccessOutcome {
        if (!config.isConfigured()) {
            return UpdateAccessOutcome.Failed("Backend Supabase não configurado neste build.")
        }
        if (!update.hasChanges()) return UpdateAccessOutcome.Updated
        val token = accessTokenProvider()
            ?: return UpdateAccessOutcome.Failed("Sessão administrativa expirada. Entre novamente.")

        // Somente os campos alterados seguem para a RPC (NULL = manter atual).
        val body = buildJsonObject {
            put("p_target_user_id", userId)
            update.name?.let { put("p_name", it.trim()) }
            update.role?.let { put("p_role", it.name) }
            update.status?.let { put("p_status", it.name) }
            update.clientId?.let { put("p_client_id", it) }
            if (update.clearClient) put("p_clear_client", true)
        }

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.restUrl("/rpc/bridge_admin_update_access"),
                    method = "POST",
                    headers = baseHeaders(token),
                    body = json.encodeToString(JsonObject.serializer(), body)
                        .toByteArray(Charsets.UTF_8)
                )
            )
        } catch (_: java.io.IOException) {
            return UpdateAccessOutcome.Failed("Falha de rede ao contatar o backend.")
        }

        return if (response.statusCode in 200..299) {
            UpdateAccessOutcome.Updated
        } else {
            UpdateAccessOutcome.Failed(
                errorMessage(response.bodyText(), "Backend recusou a edição (HTTP ${response.statusCode}).")
            )
        }
    }

    override fun changeEmail(userId: String, newEmail: String): EmailChangeOutcome {
        if (!config.isConfigured()) {
            return EmailChangeOutcome.Failed("Backend Supabase não configurado neste build.")
        }
        val token = accessTokenProvider()
            ?: return EmailChangeOutcome.Failed("Sessão administrativa expirada. Entre novamente.")

        val normalizedEmail = newEmail.trim().lowercase()
        val body = buildJsonObject {
            put("user_id", userId)
            put("email", normalizedEmail)
        }

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.functionsUrl(ADMIN_UPDATE_EMAIL_FUNCTION),
                    method = "POST",
                    headers = baseHeaders(token),
                    body = json.encodeToString(JsonObject.serializer(), body)
                        .toByteArray(Charsets.UTF_8)
                )
            )
        } catch (_: java.io.IOException) {
            return EmailChangeOutcome.Failed("Falha de rede ao contatar o backend.")
        }

        val parsed = runCatching {
            json.decodeFromString(EdgeErrorResponse.serializer(), response.bodyText())
        }.getOrNull()

        return when {
            response.statusCode in 200..299 -> EmailChangeOutcome.Changed
            response.statusCode == 409 -> EmailChangeOutcome.Failed("Já existe um acesso com este e-mail.")
            else -> EmailChangeOutcome.Failed(
                parsed?.detail ?: parsed?.error
                    ?: "Backend recusou a alteração de e-mail (HTTP ${response.statusCode})."
            )
        }
    }

    override fun sendPasswordReset(userId: String): PasswordResetOutcome {
        if (!config.isConfigured()) {
            return PasswordResetOutcome.Failed("Backend Supabase não configurado neste build.")
        }

        val target = getUser(userId)
            ?: return PasswordResetOutcome.Failed("Usuário não encontrado para redefinição.")

        val body = buildJsonObject { put("email", target.email) }
        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.authUrl("/recover"),
                    method = "POST",
                    // Endpoint público de redefinição do GoTrue: apenas apikey,
                    // sem o Bearer administrativo da sessão.
                    headers = linkedMapOf(
                        "apikey" to config.anonKey,
                        "Content-Type" to "application/json",
                        "Accept" to "application/json"
                    ),
                    body = json.encodeToString(JsonObject.serializer(), body)
                        .toByteArray(Charsets.UTF_8)
                )
            )
        } catch (_: java.io.IOException) {
            return PasswordResetOutcome.Failed("Falha de rede ao solicitar redefinição.")
        }

        return if (response.statusCode in 200..299) {
            PasswordResetOutcome.Requested
        } else {
            PasswordResetOutcome.Failed(
                errorMessage(response.bodyText(), "Supabase recusou a solicitação (HTTP ${response.statusCode}).")
            )
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

    /**
     * Extrai a mensagem real devolvida pelo servidor (RPC/PostgREST/Edge Function)
     * para que erros de negócio apareçam como são — sem virar falso sucesso.
     */
    private fun errorMessage(bodyText: String, fallback: String): String {
        val parsed = runCatching {
            json.decodeFromString(ErrorBody.serializer(), bodyText)
        }.getOrNull()
        return parsed?.message ?: parsed?.detail ?: parsed?.error ?: fallback
    }

    companion object {
        const val ADMIN_CREATE_ACCESS_FUNCTION = "admin-create-access"
        const val ADMIN_UPDATE_EMAIL_FUNCTION = "admin-update-email"
        const val ADMIN_DELETE_CLIENT_FUNCTION = "admin-delete-client"
    }
}

@Serializable
private data class CreateAccessRequest(
    val name: String,
    val email: String,
    val role: String,
    @kotlinx.serialization.SerialName("client_name") val clientName: String?,
    val status: String,
    val password: String? = null
)

@Serializable
private data class ClientDeleteResponse(
    @kotlinx.serialization.SerialName("client_id") val clientId: String? = null,
    @kotlinx.serialization.SerialName("client_name") val clientName: String? = null,
    @kotlinx.serialization.SerialName("deleted_users") val deletedUsers: Int? = null,
    @kotlinx.serialization.SerialName("remaining_users") val remainingUsers: Int? = null,
    val error: String? = null,
    val detail: String? = null
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

@Serializable
private data class EdgeErrorResponse(
    val error: String? = null,
    val detail: String? = null
)

@Serializable
private data class ErrorBody(
    val message: String? = null,
    val detail: String? = null,
    val error: String? = null
)
