package com.domnex.cfi.bridge.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.AuthSession
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.provisioning.BridgeProvisioning
import com.domnex.cfi.bridge.provisioning.ProvisioningState
import com.domnex.cfi.bridge.provisioning.RemoteProvisioningOutcome
import com.domnex.cfi.bridge.ui.screens.admin.AdminRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppDestination {
    Splash,
    Login,
    Setup,
    Home,
    AdminHome
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(AppDestination.Splash) }
    var setupMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var loggedSession by remember { mutableStateOf<AuthSession?>(null) }

    val setupNotConfiguredMessage =
        "Este acesso ainda não possui um DOMNEX BRIDGE configurado."

    fun isBridgeConfigured(): Boolean =
        BridgeProvisioning.get(context).state() == ProvisioningState.CONFIGURED

    /**
     * Destino pós-login com reprovisionamento automático:
     *  - DOMNEX_ADMIN -> Administração (nunca depende de config técnica local);
     *  - CLIENT com configuração local completa -> Home (mantém, não sobrescreve);
     *  - CLIENT sem configuração -> tenta `bridge-provisioning`; sucesso -> Home,
     *    configured=false -> Setup, erro -> Setup com mensagem amigável + retry.
     */
    suspend fun resolveDestination(session: AuthSession): AppDestination {
        if (BuildConfig.DEBUG) {
            Log.d(
                "DomnexAuth",
                "role=${session.user.role} status=${session.user.status} " +
                    "localBackend=${AuthProvider.usingLocalDevBackend}"
            )
        }

        if (session.user.role == UserRole.DOMNEX_ADMIN) {
            return AppDestination.AdminHome
        }

        if (isBridgeConfigured()) {
            // Configuração local válida: manter o fluxo atual, sem tocar na rede.
            return AppDestination.Home
        }

        val outcome = withContext(Dispatchers.IO) {
            val remote = AuthProvider.remoteBridgeProvisioningRepository
            if (remote == null) {
                RemoteProvisioningOutcome.Failed(
                    "Backend de provisionamento indisponível neste build."
                )
            } else {
                remote.ensureConfigured()
            }
        }

        return when (outcome) {
            is RemoteProvisioningOutcome.Configured -> AppDestination.Home
            RemoteProvisioningOutcome.NotConfigured -> {
                setupMessage = setupNotConfiguredMessage
                AppDestination.Setup
            }
            is RemoteProvisioningOutcome.Failed -> {
                setupMessage = "${outcome.message} Tente novamente."
                AppDestination.Setup
            }
        }
    }

    fun performLogout() {
        scope.launch(Dispatchers.IO) {
            AuthProvider.authGateway.logout()
        }
        destination = AppDestination.Login
    }

    fun retryProvisioning() {
        val session = loggedSession
        if (session != null) {
            scope.launch { destination = resolveDestination(session) }
        } else {
            scope.launch {
                // Restaura/renova a sessão e re-tenta o reprovisionamento.
                val restored = withContext(Dispatchers.IO) {
                    AuthProvider.authGateway.currentSession()
                }
                loggedSession = restored
                destination = if (restored == null) {
                    AppDestination.Login
                } else {
                    resolveDestination(restored)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // currentSession pode renovar token via rede -> roda em IO.
        val session = withContext(Dispatchers.IO) {
            AuthProvider.authGateway.currentSession()
        }
        loggedSession = session
        destination = if (session == null) {
            AppDestination.Login
        } else {
            resolveDestination(session)
        }
    }

    when (destination) {
        AppDestination.Splash -> SplashScreen(
            onReady = { /* navegação é controlada pela restauração de sessão */ }
        )

        AppDestination.Login -> LoginScreen(
            onAuthenticated = { session ->
                loggedSession = session
                scope.launch {
                    destination = resolveDestination(session)
                }
            }
        )

        // Bloqueio para CLIENT sem provisionamento (após tentar o back-end).
        AppDestination.Setup -> ClientSetupRequiredScreen(
            message = setupMessage,
            onRetry = { retryProvisioning() },
            onLogout = { performLogout() }
        )

        AppDestination.Home -> HomeRoot(
            onLogout = { performLogout() }
        )

        AppDestination.AdminHome -> AdminRoot(
            onLogout = { performLogout() }
        )
    }
}