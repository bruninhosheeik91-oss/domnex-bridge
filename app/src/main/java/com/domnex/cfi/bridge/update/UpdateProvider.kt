package com.domnex.cfi.bridge.update

import android.util.Log
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.supabase.HttpUrlConnectionSupabaseClient
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig

/**
 * Ponto único de acesso da UI ao [UpdateRepository].
 *
 * Enquanto não houver uma fonte oficial de atualização configurada neste build,
 * [repository] fica null e o fluxo de UI exibe o estado normal "serviço de
 * atualização ainda não configurado" — NUNCA finge que existe atualização.
 *
 * Quando o backend Supabase estiver configurado (SUPABASE_URL + ANON_KEY), cria
 * um [VersionTableUpdateSource] consultando a tabela oficial de versões via REST.
 */
object UpdateProvider {

    @Volatile
    private var initialized = false

    /**
     * Repository de atualização. null => serviço de update ainda não configurado.
     */
    var repository: UpdateRepository? = null
        private set

    val isConfigured: Boolean
        get() = repository != null

    fun init(appContext: android.content.Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val config = SupabaseAuthConfig.fromBuildConfig(
                projectUrl = BuildConfig.SUPABASE_URL,
                anonKey = BuildConfig.SUPABASE_ANON_KEY
            )
            if (config.isConfigured()) {
                val httpClient = HttpUrlConnectionSupabaseClient(config.timeoutMillis)
                val source = VersionTableUpdateSource(config = config)
                repository = UpdateRepository(
                    installedVersionCodeProvider = { InstalledVersion.versionCode(appContext) },
                    httpClient = httpClient,
                    source = source
                )
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "UpdateRepository=VersionTableUpdateSource url=${config.projectUrl}")
                }
            }
            initialized = true
        }
    }

    /**
     * Fonte de atualização via tabela Supabase (`domnex_bridge_versions`),
     * consultada pelo anon/publishable key. A URL do APK vem apenas da linha
     * oficial da tabela — nunca de input livre do usuário.
     *
     * Somente releases `published = true` são retornadas (filtro REST + RLS),
     * ordenadas pelo maior `version_code` e limitadas a 1. A ordenação é por
     * version_code, nunca por version_name.
     */
    class VersionTableUpdateSource(
        private val config: SupabaseAuthConfig,
        private val table: String = VERSIONS_TABLE
    ) : UpdateSource {
        override fun endpoint(): String =
            "${config.restUrl("/$table")}?select=*&published=eq.true&order=version_code.desc&limit=1"

        override fun headers(): Map<String, String> = linkedMapOf(
            "apikey" to config.anonKey,
            "Authorization" to "Bearer ${config.anonKey}",
            "Accept" to "application/json"
        )

        companion object {
            const val VERSIONS_TABLE = "domnex_bridge_versions"
        }
    }

    private const val LOG_TAG = "DomnexUpdate"
}
