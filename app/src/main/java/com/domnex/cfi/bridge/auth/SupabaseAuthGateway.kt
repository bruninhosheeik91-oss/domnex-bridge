package com.domnex.cfi.bridge.auth

import android.util.Log
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.supabase.BridgeProfileRow
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.HttpResponse
import com.domnex.cfi.bridge.auth.supabase.StoredSession
import com.domnex.cfi.bridge.auth.supabase.StoredUserSnapshot
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.auth.supabase.SupabaseSessionStore
import com.domnex.cfi.bridge.auth.supabase.SupabaseTokenResponse
import com.domnex.cfi.bridge.auth.supabase.toUserAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Implementação REAL de [AuthGateway] sobre o Supabase Auth (GoTrue REST).
 *
 * Todas as operações são bloqueantes (rede) e DEVEM ser chamadas fora da main thread.
 * A UI não conhece detalhes do Supabase — fala apenas com AuthGateway/AuthProvider.
 *
 * Segurança: usa somente a anon/publishable key no cabeçalho `apikey`.
 * Nenhuma service_role key, senha administrativa ou segredo privado passa por aqui.
 */
class SupabaseAuthGateway(
    private val config: SupabaseAuthConfig,
    private val httpClient: SupabaseHttpClient,
    private val sessionStore: SupabaseSessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) : AuthGateway {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedSession: AuthSession? = null

    override fun login(email: String, password: String): AuthResult {
        if (!config.isConfigured()) return AuthResult.Denied(DenialReason.NOT_CONFIGURED)
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isEmpty() || password.isEmpty()) return AuthResult.Rejected

        val loginBody = buildJsonObject {
            put("email", normalizedEmail)
            put("password", password)
        }

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.authUrl("/token?grant_type=password"),
                    method = "POST",
                    headers = baseHeaders(),
                    body = json.encodeToString(
                        kotlinx.serialization.json.JsonObject.serializer(),
                        loginBody
                    ).toByteArray(Charsets.UTF_8)
                )
            )
        } catch (_: IOException) {
            return AuthResult.Denied(DenialReason.NETWORK_ERROR)
        } catch (_: Exception) {
            return AuthResult.Denied(DenialReason.SERVER_ERROR)
        }

        if (response.statusCode !in 200..299) {
            return if (response.statusCode == 400 || response.statusCode == 401) {
                AuthResult.Rejected
            } else {
                AuthResult.Denied(DenialReason.SERVER_ERROR)
            }
        }

        val token = runCatching {
            json.decodeFromString(SupabaseTokenResponse.serializer(), response.bodyText())
        }.getOrNull() ?: return AuthResult.Denied(DenialReason.SERVER_ERROR)
        val authUserId = token.user?.id ?: return AuthResult.Denied(DenialReason.SERVER_ERROR)

        val profile = try {
            fetchProfile(authUserId, accessToken = token.accessToken)
        } catch (_: IOException) {
            return AuthResult.Denied(DenialReason.NETWORK_ERROR)
        } catch (_: ProfileHttpException) {
            return AuthResult.Denied(DenialReason.SERVER_ERROR)
        } ?: return AuthResult.Denied(DenialReason.PROFILE_MISSING)

        val account = profile.toUserAccount()
        if (BuildConfig.DEBUG) {
            // Diagnóstico de roteamento: role/status vindos do backend real.
            Log.d(
                LOG_TAG,
                "login email=$normalizedEmail userId=${account.id} " +
                    "role=${account.role} status=${account.status}"
            )
        }
        return when (account.status) {
            UserStatus.SUSPENDED -> AuthResult.Denied(DenialReason.PROFILE_SUSPENDED)
            UserStatus.PENDING -> AuthResult.Denied(DenialReason.PROFILE_PENDING)
            UserStatus.ACTIVE -> {
                val session = AuthSession(user = account)
                persistSession(session, token, fallbackNow = nowEpochSeconds())
                AuthResult.Authorized(session)
            }
        }
    }

    override fun logout() {
        val stored = sessionStore.load()
        if (stored != null && config.isConfigured()) {
            try {
                httpClient.execute(
                    HttpRequest(
                        url = config.authUrl("/logout"),
                        method = "POST",
                        headers = baseHeaders() +
                            ("Authorization" to "Bearer ${stored.accessToken}")
                    )
                )
            } catch (_: IOException) {
                // Logout local prossegue mesmo se a revogação falhar.
            }
        }
        cachedSession = null
        sessionStore.clear()
    }

    /**
     * Restaura a sessão salva. Token válido -> sessão; expirado -> tenta refresh;
     * refresh revogado -> null (sessão inválida); erro de rede -> snapshot local (offline).
     */
    override fun currentSession(): AuthSession? {
        cachedSession?.let { cached ->
            val stored = sessionStore.load()
            if (stored != null && !isExpired(stored)) return cached
        }

        val stored = sessionStore.load() ?: run {
            cachedSession = null
            return null
        }

        if (!isExpired(stored)) {
            val session = stored.toSession()
            cachedSession = session
            return session
        }

        val refreshed = try {
            refreshSession(stored)
        } catch (_: IOException) {
            // Offline: mantém snapshot local para não deslogar injustamente.
            val session = stored.toSession()
            cachedSession = session
            return session
        }

        if (refreshed == null) {
            cachedSession = null
            sessionStore.clear()
            return null
        }

        cachedSession = refreshed.session
        return refreshed.session
    }

    override fun currentUser(): UserAccount? =
        cachedSession?.user ?: currentSession()?.user

    /**
     * Token de acesso vigente para chamadas autenticadas ao backend.
     *
     * Garante frescor: antes de devolver, tenta [currentSession], que renova o
     * JWT expirado (via refresh_token) e persiste o novo token. Se o refresh
     * falhar/for revogado, [currentSession] zera a sessão e este método
     * devolve null. Assim nenhuma chamada envia um JWT expirado para a proxy.
     */
    fun currentAccessToken(): String? {
        currentSession()
        return sessionStore.load()?.accessToken
    }

    private data class Refreshed(val session: AuthSession)

    private fun refreshSession(stored: StoredSession): Refreshed? {
        val body = buildJsonObject { put("refresh_token", stored.refreshToken) }
        val response = httpClient.execute(
            HttpRequest(
                url = config.authUrl("/token?grant_type=refresh_token"),
                method = "POST",
                headers = baseHeaders(),
                body = json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    body
                ).toByteArray(Charsets.UTF_8)
            )
        )
        if (response.statusCode !in 200..299) return null

        val token = runCatching {
            json.decodeFromString(SupabaseTokenResponse.serializer(), response.bodyText())
        }.getOrNull() ?: return null

        val userId = token.user?.id ?: stored.user.id
        val profile = try {
            fetchProfile(userId, accessToken = token.accessToken)
        } catch (_: Exception) {
            null
        }

        if (profile != null && profile.toUserAccount().status != UserStatus.ACTIVE) return null

        val account = profile?.toUserAccount()
            ?: stored.user.toAccount()
        val session = AuthSession(user = account)
        persistSession(session, token, fallbackNow = nowEpochSeconds())
        return Refreshed(session)
    }

    private class ProfileHttpException(val statusCode: Int) : Exception()

    private fun fetchProfile(userId: String, accessToken: String): BridgeProfileRow? {
        val url = config.restUrl("/bridge_profiles") +
            "?select=id,name,email,role,client_id,status,created_at,updated_at,client:bridge_clients(name)" +
            "&id=eq.$userId&limit=1"
        val response = httpClient.execute(
            HttpRequest(
                url = url,
                method = "GET",
                headers = baseHeaders(accessToken = accessToken)
            )
        )
        if (response.statusCode !in 200..299) throw ProfileHttpException(response.statusCode)
        val rows = runCatching {
            json.decodeFromString<List<BridgeProfileRow>>(response.bodyText())
        }.getOrNull() ?: return null
        return rows.firstOrNull()
    }

    private fun isExpired(stored: StoredSession): Boolean =
        nowEpochSeconds() >= stored.expiresAtEpochSeconds - EXPIRY_SKEW_SECONDS

    private fun persistSession(
        session: AuthSession,
        token: SupabaseTokenResponse,
        fallbackNow: Long
    ) {
        cachedSession = session
        sessionStore.save(
            StoredSession(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtEpochSeconds = token.expiresAtEpochSeconds(fallbackNow),
                user = session.user.toSnapshot()
            )
        )
    }

    private fun baseHeaders(accessToken: String? = null): Map<String, String> {
        val headers = linkedMapOf(
            "apikey" to config.anonKey,
            "Content-Type" to "application/json"
        )
        if (accessToken != null) headers["Authorization"] = "Bearer $accessToken"
        return headers
    }

    private companion object {
        const val LOG_TAG = "DomnexAuth"
        const val EXPIRY_SKEW_SECONDS = 30L
    }
}

private fun StoredUserSnapshot.toAccount(): UserAccount = UserAccount(
    id = id,
    name = name,
    email = email,
    role = UserRole.valueOf(role),
    clientName = clientName,
    status = runCatching { UserStatus.valueOf(status) }.getOrDefault(UserStatus.ACTIVE),
    createdAtMillis = createdAtMillis
)

private fun UserAccount.toSnapshot(): StoredUserSnapshot = StoredUserSnapshot(
    id = id,
    name = name,
    email = email,
    role = role.name,
    clientName = clientName,
    status = status.name,
    createdAtMillis = createdAtMillis
)

private fun StoredSession.toSession(): AuthSession =
    AuthSession(user = user.toAccount())
