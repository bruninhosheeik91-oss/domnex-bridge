package com.domnex.cfi.bridge.provisioning

import android.util.Log
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Resultado do reprovisionamento remoto. Nunca mascara erro como sucesso:
 * - [Configured]: configuração local já completa OU devolvida pelo backend;
 * - [NotConfigured]: usuário autenticado sem Bridge associado (configured=false);
 * - [Failed]: rede, JWT inválido/sem permissão, resposta inválida ou backend indisponível.
 */
sealed interface RemoteProvisioningOutcome {
    data class Configured(val config: TechnicalConfig) : RemoteProvisioningOutcome
    data object NotConfigured : RemoteProvisioningOutcome
    data class Failed(val message: String) : RemoteProvisioningOutcome
}

/**
 * Reprovisionamento automático do DOMNEX BRIDGE após reinstalação.
 *
 * Fluxo (chamado após login Supabase bem-sucedido, fora da main thread):
 *  1. Configuração local já existente (endpoint + token) -> mantém, NUNCA
 *     sobrescreve e NUNCA contata a rede.
 *  2. Sem configuração local -> chama a Edge Function `bridge-provisioning`
 *     enviando o JWT real da sessão (apenas anon key + access token; nenhum
 *     service_role/M2M/segredo de servidor passa pelo APK).
 *  3. configured=true  -> salva SOMENTE se a resposta for completa (nunca
 *     grava configuração parcial) usando o MESMO `BridgeProvisioningRepository`
 *     / SharedPreferences `cfi_bridge_prefs` já existentes.
 *  4. configured=false -> [NotConfigured] (nada salvo).
 *  5. Erro (rede/401/403/5xx/JSON inválido) -> [Failed] (config local intacta).
 *
 * Segurança de logs: o bridgeToken NUNCA é impresso. Logs sanitizados usam
 * as tags PROVISIONING_AUTH / PROVISIONING_SUCCESS / PROVISIONING_NOT_CONFIGURED /
 * PROVISIONING_ERROR e imprimem apenas presença/ausência e códigos de status.
 *
 * Método bloqueante: chamar fora da main thread.
 */
class RemoteBridgeProvisioningRepository(
    private val config: SupabaseAuthConfig,
    private val httpClient: SupabaseHttpClient,
    private val accessTokenProvider: () -> String?,
    private val storage: BridgeProvisioningRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun ensureConfigured(): RemoteProvisioningOutcome {
        // ------------------------------------------------------------------ 1
        // Configuração local já completa: manter. Nunca sobrescrever nem chamar rede.
        val local = storage.load()
        if (local.isComplete) {
            log("PROVISIONING_SUCCESS", "local_config_present keep_existing=true")
            return RemoteProvisioningOutcome.Configured(local)
        }

        return provisionRemote()
    }

    private fun provisionRemote(): RemoteProvisioningOutcome {
        if (!config.isConfigured()) {
            log("PROVISIONING_ERROR", "supabase_not_configured")
            return RemoteProvisioningOutcome.Failed("Backend Supabase não configurado neste build.")
        }

        val token = accessTokenProvider()
            ?: return RemoteProvisioningOutcome.Failed("Sessão expirada. Entre novamente.")
        // Presença apenas — nunca o JWT.
        log("PROVISIONING_AUTH", "session_token=present")

        val response = try {
            httpClient.execute(
                HttpRequest(
                    url = config.functionsUrl(BRIDGE_PROVISIONING_FUNCTION),
                    method = "GET",
                    headers = baseHeaders(token)
                )
            )
        } catch (_: IOException) {
            log("PROVISIONING_ERROR", "network_error no_config_touched=true")
            return RemoteProvisioningOutcome.Failed("Sem conexão com o servidor. Tente novamente.")
        }

        // Diagnóstico SANITIZADO: HTTP status + código de erro do backend (quando
        // JSON). NUNCA imprime JWT, header, body cru nem o bridgeToken.
        if (BuildConfig.DEBUG) {
            val code = runCatching {
                json.decodeFromString(ProvisioningError.serializer(), response.bodyText()).error
            }.getOrNull()
            log(
                "PROVISIONING_ERROR",
                "http_status=${response.statusCode} error_code=${code ?: "(sem corpo JSON)"}"
            )
        }

        return when {
            response.statusCode == 200 -> parseConfigured(response.bodyText())
            response.statusCode == 401 || response.statusCode == 403 -> {
                log("PROVISIONING_ERROR", "forbidden_or_unauthenticated no_config_touched=true")
                RemoteProvisioningOutcome.Failed(
                    "Sessão sem permissão para provisionar o Bridge. Entre novamente."
                )
            }
            else -> {
                log("PROVISIONING_ERROR", "backend_unavailable http_status=${response.statusCode}")
                RemoteProvisioningOutcome.Failed(
                    "Servidor indisponível (HTTP ${response.statusCode}). Tente novamente."
                )
            }
        }
    }

    private fun parseConfigured(bodyText: String): RemoteProvisioningOutcome {
        val payload = runCatching {
            json.decodeFromString(ProvisioningResponse.serializer(), bodyText)
        }.getOrNull()
            ?: return RemoteProvisioningOutcome.Failed("Resposta inválida do servidor.")

        if (payload.configured != true) {
            // Usuário autenticado sem Bridge associado. Nada é gravado apagado.
            log("PROVISIONING_NOT_CONFIGURED", "configured=false nothing_saved=true")
            return RemoteProvisioningOutcome.NotConfigured
        }

        val tech = payload.toTechnicalConfig()
        if (!tech.isComplete) {
            // configured=true mas sem campos obrigatórios: resposta inválida.
            // NUNCA grava configuração parcial.
            log("PROVISIONING_ERROR", "invalid_response_partial_config save_aborted=true")
            return RemoteProvisioningOutcome.Failed("Resposta inválida do servidor.")
        }

        // Gravação somente com resposta completa e válida, no MESMO storage.
        storage.save(tech)
        log("PROVISIONING_SUCCESS", "provisioned=true saved=true")
        return RemoteProvisioningOutcome.Configured(tech)
    }

    private fun baseHeaders(token: String): Map<String, String> = linkedMapOf(
        "apikey" to config.anonKey,
        "Authorization" to "Bearer $token",
        "Accept" to "application/json"
    )

    private fun log(tag: String, message: String) {
        // android.util.Log vira no-op nos testes JVM (isReturnDefaultValues).
        Log.d(LOG_TAG, "$tag $message")
    }

    companion object {
        const val BRIDGE_PROVISIONING_FUNCTION = "bridge-provisioning"

        @Suppress("unused")
        const val TAG_AUTH = "PROVISIONING_AUTH"
        @Suppress("unused")
        const val TAG_SUCCESS = "PROVISIONING_SUCCESS"
        @Suppress("unused")
        const val TAG_NOT_CONFIGURED = "PROVISIONING_NOT_CONFIGURED"
        @Suppress("unused")
        const val TAG_ERROR = "PROVISIONING_ERROR"

        private const val LOG_TAG = "DomnexProvisioning"
    }
}

@Serializable
private data class ProvisioningResponse(
    val configured: Boolean = false,
    @SerialName("targetSystemName") val targetSystemName: String? = null,
    @SerialName("apiBaseUrl") val apiBaseUrl: String? = null,
    @SerialName("bridgeToken") val bridgeToken: String? = null
) {
    fun toTechnicalConfig(): TechnicalConfig = TechnicalConfig(
        baseUrl = apiBaseUrl.orEmpty().trim(),
        bridgeToken = bridgeToken.orEmpty().trim(),
        targetSystemName = targetSystemName.orEmpty().trim()
    )
}

@Serializable
private data class ProvisioningError(
    val error: String? = null
)