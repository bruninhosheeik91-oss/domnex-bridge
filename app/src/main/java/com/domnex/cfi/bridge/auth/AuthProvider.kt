package com.domnex.cfi.bridge.auth

import android.content.Context
import android.util.Log
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.supabase.HttpUrlConnectionSupabaseClient
import com.domnex.cfi.bridge.auth.supabase.PrefsSupabaseSessionStore
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig

/**
 * Ponto único de acesso da UI aos gateways de autenticação.
 *
 * Seleção de implementação:
 * - Build RELEASE: SEMPRE Supabase (LocalAuthGateway nunca roda em produção).
 * - Build DEBUG: usa Supabase se as chaves estiverem configuradas
 *   (SUPABASE_URL + SUPABASE_ANON_KEY); caso contrário cai no backend local DEV.
 *
 * A UI só conhece AuthGateway/UserDirectory — nunca detalhes do Supabase.
 */
object AuthProvider {

    @Volatile
    private var initialized = false

    lateinit var authGateway: AuthGateway
        private set

    lateinit var userDirectory: UserDirectory
        private set

    var usingLocalDevBackend: Boolean = true
        private set

    fun init(appContext: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val config = SupabaseAuthConfig.fromBuildConfig(
                projectUrl = BuildConfig.SUPABASE_URL,
                anonKey = BuildConfig.SUPABASE_ANON_KEY
            )
            // RELEASE nunca usa backend local; DEBUG usa Supabase quando configurado.
            if (!BuildConfig.DEBUG || config.isConfigured()) {
                val httpClient = HttpUrlConnectionSupabaseClient(config.timeoutMillis)
                val gateway = SupabaseAuthGateway(
                    config = config,
                    httpClient = httpClient,
                    sessionStore = PrefsSupabaseSessionStore(appContext)
                )
                authGateway = gateway
                userDirectory = RemoteUserDirectory(config, httpClient) { gateway.currentAccessToken() }
                usingLocalDevBackend = false
                logInit("AuthGateway=SupabaseAuthGateway url=${config.projectUrl}")
            } else {
                LocalAuthGateway.init(appContext)
                authGateway = LocalAuthGateway
                userDirectory = LocalUserDirectory
                usingLocalDevBackend = true
                logInit(
                    "AuthGateway=LocalAuthGateway (BuildConfig.SUPABASE_URL/SUPABASE_ANON_KEY " +
                        "ausentes — preencha local.properties para autenticação real)"
                )
            }
            initialized = true
        }
    }

    fun supabaseConfigured(): Boolean =
        !usingLocalDevBackend

    // Log de desenvolvimento: qual backend de autenticação está ativo neste build.
    private fun logInit(message: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message)
    }

    private const val LOG_TAG = "DomnexAuth"
}
