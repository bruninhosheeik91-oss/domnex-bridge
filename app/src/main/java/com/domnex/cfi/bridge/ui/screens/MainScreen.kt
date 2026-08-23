package com.domnex.cfi.bridge.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.domnex.cfi.bridge.auth.AuthSession
import com.domnex.cfi.bridge.auth.LocalAuthGateway
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.service.SaleSender
import com.domnex.cfi.bridge.ui.screens.admin.AdminRoot

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
    var destination by rememberSaveable { mutableStateOf(AppDestination.Splash) }

    fun isBridgeConfigured(): Boolean =
        SaleSender.getBaseUrl(context).isNotBlank() &&
            SaleSender.getBridgeToken(context).isNotBlank()

    fun destinationFor(session: AuthSession): AppDestination = when {
        session.user.role == UserRole.DOMNEX_ADMIN -> AppDestination.AdminHome
        !isBridgeConfigured() -> AppDestination.Setup
        else -> AppDestination.Home
    }

    fun performLogout() {
        LocalAuthGateway.logout()
        destination = AppDestination.Login
    }

    when (destination) {
        AppDestination.Splash -> SplashScreen(
            onReady = {
                val session = LocalAuthGateway.currentSession()
                destination = if (session == null) {
                    AppDestination.Login
                } else {
                    destinationFor(session)
                }
            }
        )

        AppDestination.Login -> LoginScreen(
            onAuthenticated = { session ->
                destination = destinationFor(session)
            }
        )

        AppDestination.Setup -> InitialSetupScreen(
            onEnterBridge = { destination = AppDestination.Home }
        )

        AppDestination.Home -> HomeRoot(
            onLogout = { performLogout() }
        )

        AppDestination.AdminHome -> AdminRoot(
            onLogout = { performLogout() }
        )
    }
}
