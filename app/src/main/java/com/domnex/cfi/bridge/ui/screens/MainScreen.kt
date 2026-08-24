package com.domnex.cfi.bridge.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.AuthRouting
import com.domnex.cfi.bridge.auth.AuthSession
import com.domnex.cfi.bridge.auth.RouteTarget
import com.domnex.cfi.bridge.provisioning.BridgeProvisioning
import com.domnex.cfi.bridge.provisioning.ProvisioningState
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

    fun isBridgeConfigured(): Boolean =
        BridgeProvisioning.get(context).state() == ProvisioningState.CONFIGURED

    fun destinationFor(session: AuthSession): AppDestination {
        val target = AuthRouting.resolveTarget(session.user.role, isBridgeConfigured())
        val resolved = when (target) {
            RouteTarget.ADMIN_HOME -> AppDestination.AdminHome
            RouteTarget.SETUP -> AppDestination.Setup
            RouteTarget.HOME -> AppDestination.Home
        }
        if (BuildConfig.DEBUG) {
            // Diagnóstico de roteamento pós-login.
            Log.d(
                "DomnexAuth",
                "RouteTarget=$target destino=$resolved role=${session.user.role} " +
                    "status=${session.user.status} localBackend=${AuthProvider.usingLocalDevBackend}"
            )
        }
        return resolved
    }

    fun performLogout() {
        scope.launch(Dispatchers.IO) {
            AuthProvider.authGateway.logout()
        }
        destination = AppDestination.Login
    }

    LaunchedEffect(Unit) {
        // currentSession pode renovar token via rede -> roda em IO.
        val session = withContext(Dispatchers.IO) {
            AuthProvider.authGateway.currentSession()
        }
        destination = if (session == null) {
            AppDestination.Login
        } else {
            destinationFor(session)
        }
    }

    when (destination) {
        AppDestination.Splash -> SplashScreen(
            onReady = { /* navegação é controlada pela restauração de sessão */ }
        )

        AppDestination.Login -> LoginScreen(
            onAuthenticated = { session ->
                destination = destinationFor(session)
            }
        )

        // Bloqueio para CLIENT sem provisionamento: nunca expõe campos técnicos.
        AppDestination.Setup -> ClientSetupRequiredScreen(
            onRetry = { isBridgeConfigured() },
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
