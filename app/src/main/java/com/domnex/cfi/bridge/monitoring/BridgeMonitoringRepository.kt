package com.domnex.cfi.bridge.monitoring

import android.util.Log
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Exceção de transporte do monitoramento. A UI captura para exibir erro real.
 */
class BridgeMonitoringException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Resultado real do monitoramento: [data] apenas quando o backend devolveu uma
 * resposta 2xx parseável. Qualquer erro (rede, JWT ausente, 401/403/5xx, JSON
 * inválido) vira [Failed] — nunca é mascarado como sucesso.
 */
sealed interface BridgeMonitoringResult {
    data class Success(val data: BridgeMonitoringResponse) : BridgeMonitoringResult
    data class Failed(val message: String) : BridgeMonitoringResult
}

/**
 * Busca o monitoramento de bridges no backend real do DOMNEX BRIDGE.
 *
 * Fluxo: envia o JWT real da sessão Supabase autenticada até a Edge Function
 * `bridge-monitoring-proxy` (que revalida DOMNEX_ADMIN+ACTIVE no servidor e,
 * de forma server-to-server, usa o M2M_MONITORING_SECRET para consultar o CFI).
 *
 * SEGURANÇA: o APK NUNCA conhece o M2M_MONITORING_SECRET nem a service_role do
 * CFI. O app apenas envia o próprio JWT; o proxy é quem detém os segredos.
 *
 * Método bloqueante: chamar fora da main thread.
 */
class BridgeMonitoringRepository(
    private val config: SupabaseAuthConfig,
    private val httpClient: SupabaseHttpClient,
    private val accessTokenProvider: () -> String?
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): BridgeMonitoringResult {
        if (!config.isConfigured()) {
            return BridgeMonitoringResult.Failed("Backend Supabase não configurado neste build.")
        }
        val token = accessTokenProvider()
            ?: return BridgeMonitoringResult.Failed("Sessão administrativa expirada. Entre novamente.")

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.functionsUrl(BRIDGE_MONITORING_PROXY_FUNCTION),
                    method = "GET",
                    headers = baseHeaders(token)
                )
            )
        } catch (_: java.io.IOException) {
            return BridgeMonitoringResult.Failed("Falha de rede ao contatar o backend.")
        }

        // Diagnóstico SOMENTE em debug: HTTP status + código de erro sanitizado da
        // proxy. NUNCA loga JWT, headers nem qualquer segredo.
        if (BuildConfig.DEBUG) {
            val code = runCatching {
                json.decodeFromString(ProxyError.serializer(), response.bodyText()).error
            }.getOrNull()
            Log.d(LOG_TAG, "bridge-monitoring-proxy status=${response.statusCode} ${code ?: "(sem corpo de erro)"}")
        }

        return when {
            response.statusCode in 200..299 -> {
                val parsed = runCatching {
                    json.decodeFromString(BridgeMonitoringResponse.serializer(), response.bodyText())
                }.getOrNull()
                if (parsed == null) {
                    BridgeMonitoringResult.Failed("Resposta inválida do monitoramento.")
                } else {
                    BridgeMonitoringResult.Success(parsed)
                }
            }
            response.statusCode == 401 || response.statusCode == 403 ->
                BridgeMonitoringResult.Failed("Sessão sem permissão administrativa para monitoramento.")
            else -> BridgeMonitoringResult.Failed(
                "Backend indisponível para monitoramento (HTTP ${response.statusCode})."
            )
        }
    }

    private fun baseHeaders(token: String): Map<String, String> = linkedMapOf(
        "apikey" to config.anonKey,
        "Authorization" to "Bearer $token",
        "Content-Type" to "application/json",
        "Accept" to "application/json"
    )

    companion object {
        const val BRIDGE_MONITORING_PROXY_FUNCTION = "bridge-monitoring-proxy"
        private const val LOG_TAG = "DomnexMonitor"
    }
}

@Serializable
private data class ProxyError(
    val error: String? = null,
    @SerialName("status") val statusCode: Int? = null
)
