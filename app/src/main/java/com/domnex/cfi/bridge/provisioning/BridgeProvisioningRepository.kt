package com.domnex.cfi.bridge.provisioning

import android.content.Context

/**
 * Estado seguro do provisionamento para qualquer tela da UI:
 *  - [UNCONFIGURED]: sem endpoint/token válidos;
 *  - [CONFIGURED]: integração técnica completa;
 *  - [ERROR]: falha ao ler o armazenamento local (não vaza detalhes técnicos).
 */
enum class ProvisioningState {
    UNCONFIGURED,
    CONFIGURED,
    ERROR
}

/**
 * Configuração TÉCNICA da integração. Visível/editável SOMENTE por
 * DOMNEX_ADMIN através da Configuração Técnica — nunca exibida para CLIENT.
 *
 * Chaves preservadas exatamente como sempre existiram (não renomear/migrar):
 *  - `api_base_url`
 *  - `bridge_token`
 * Nova chave apenas apresentacional:
 *  - `target_system_name` (não participa da captura nem do envio)
 */
data class TechnicalConfig(
    val baseUrl: String = "",
    val bridgeToken: String = "",
    val targetSystemName: String = ""
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && bridgeToken.isNotBlank()

    /** Nome amigável para exibição (nunca dados técnicos). */
    fun displayNameOrDefault(): String =
        targetSystemName.trim().ifBlank { DEFAULT_SYSTEM_NAME }

    companion object {
        const val DEFAULT_SYSTEM_NAME = "Domnex Bridge"
    }
}

/**
 * Validação LOCAL de configuração (sem chamadas de rede — o endpoint atual é um
 * webhook que registra vendas; não existe healthcheck seguro, então NENHUM
 * teste envia payload). Mensagens nunca ecoam o token.
 */
object TechnicalConfigValidator {

    fun validate(baseUrl: String, bridgeToken: String): List<String> {
        val reasons = mutableListOf<String>()
        val url = baseUrl.trim()
        val token = bridgeToken.trim()

        if (url.isEmpty()) {
            reasons.add("Informe o endpoint.")
        } else if (!url.startsWith("https://", ignoreCase = true)) {
            reasons.add("O endpoint deve usar HTTPS.")
        } else {
            val hostOk = runCatching {
                java.net.URI(url).host?.isNotBlank() == true
            }.getOrDefault(false)
            if (!hostOk) reasons.add("Endpoint inválido.")
        }

        if (token.isEmpty()) reasons.add("Informe o token do Bridge.")

        return reasons
    }

    /**
     * Mascaramento para exibição segura: revela no máximo os 4 últimos
     * caracteres, somente para tokens longos.
     */
    fun maskToken(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return "—"
        return if (trimmed.length >= 8) {
            "••••••••" + trimmed.takeLast(4)
        } else {
            "•".repeat(trimmed.length)
        }
    }
}

/** Armazenamento mínimo, abstraído para permitir testes JVM. */
interface ProvisioningStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

interface BridgeProvisioningRepository {
    fun load(): TechnicalConfig
    fun save(config: TechnicalConfig)
    fun state(): ProvisioningState
}

/**
 * Implementação sobre SharedPreferences usando o MESMO arquivo e as MESMAS
 * chaves históricas do motor (`api_base_url`, `bridge_token`). Valores já
 * existentes nunca são apagados ou resetados por esta camada.
 */
class PrefsBridgeProvisioningRepository(
    private val storage: ProvisioningStorage
) : BridgeProvisioningRepository {

    override fun load(): TechnicalConfig = TechnicalConfig(
        baseUrl = storage.getString(KEY_BASE_URL).orEmpty(),
        bridgeToken = storage.getString(KEY_BRIDGE_TOKEN).orEmpty(),
        targetSystemName = storage.getString(KEY_SYSTEM_NAME).orEmpty()
    )

    override fun save(config: TechnicalConfig) {
        // Salva campo a campo: nenhum valor pré-existente é limpo implicitamente.
        storage.putString(KEY_BASE_URL, config.baseUrl.trim())
        storage.putString(KEY_BRIDGE_TOKEN, config.bridgeToken.trim())
        storage.putString(KEY_SYSTEM_NAME, config.targetSystemName.trim())
    }

    override fun state(): ProvisioningState = try {
        if (load().isComplete) ProvisioningState.CONFIGURED else ProvisioningState.UNCONFIGURED
    } catch (_: Exception) {
        ProvisioningState.ERROR
    }

    companion object {
        /** Mesmo arquivo do motor — mantém compatibilidade total. */
        const val PREFS_FILE = "cfi_bridge_prefs"
        const val KEY_BASE_URL = "api_base_url"
        const val KEY_BRIDGE_TOKEN = "bridge_token"
        const val KEY_SYSTEM_NAME = "target_system_name"

        /** Arquivos que NUNCA devem conter configuração técnica (contrato). */
        const val SESSION_PREFS_SUPABASE = "domnex_bridge_supabase_session"
        const val SESSION_PREFS_LOCAL_UI = "domnex_bridge_ui_session"
        const val MONITOR_PREFS_FILE = "bridge_monitor_prefs"
    }
}

/** SharedPreferences real do dispositivo. */
class PrefsProvisioningStorage(context: Context) : ProvisioningStorage {
    private val prefs = context.applicationContext
        .getSharedPreferences(PrefsBridgeProvisioningRepository.PREFS_FILE, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/** Ponto único de acesso da UI ao provisionamento. */
object BridgeProvisioning {

    @Volatile
    private var repository: BridgeProvisioningRepository? = null

    fun get(context: Context): BridgeProvisioningRepository =
        repository ?: synchronized(this) {
            repository ?: PrefsBridgeProvisioningRepository(PrefsProvisioningStorage(context))
                .also { repository = it }
        }
}
