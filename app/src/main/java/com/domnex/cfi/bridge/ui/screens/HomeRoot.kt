package com.domnex.cfi.bridge.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class HomeRoute { HOME, ACTIVITY, DIAGNOSTICS, ACCOUNT }

@Composable
fun HomeRoot(
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var route by rememberSaveable { mutableStateOf(HomeRoute.HOME) }
    when (route) {
        HomeRoute.HOME -> HomeScreen(
            onOpenActivity = { route = HomeRoute.ACTIVITY },
            onOpenDiagnostics = { route = HomeRoute.DIAGNOSTICS },
            onOpenAccount = { route = HomeRoute.ACCOUNT }
        )

        HomeRoute.ACTIVITY -> ActivityScreen(
            onBack = { route = HomeRoute.HOME },
            modifier = modifier
        )

        HomeRoute.DIAGNOSTICS -> DiagnosticsScreen(
            onBack = { route = HomeRoute.HOME },
            modifier = modifier
        )

        HomeRoute.ACCOUNT -> AccountScreen(
            onBack = { route = HomeRoute.HOME },
            onLogout = onLogout,
            modifier = modifier
        )
    }
}
