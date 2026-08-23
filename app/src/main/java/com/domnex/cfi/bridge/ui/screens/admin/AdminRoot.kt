package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class AdminRoute {
    HOME,
    ACCESSES,
    CREATE_ACCESS,
    USER_DETAIL
}

@Composable
fun AdminRoot(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var route by rememberSaveable { mutableStateOf(AdminRoute.HOME) }
    var selectedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var dataVersion by rememberSaveable { mutableIntStateOf(0) }

    when (route) {
        AdminRoute.HOME -> AdminHomeScreen(
            onOpenAccesses = { route = AdminRoute.ACCESSES },
            onLogout = onLogout,
            dataVersion = dataVersion,
            modifier = modifier
        )

        AdminRoute.ACCESSES -> AccessManagementScreen(
            onBack = { route = AdminRoute.HOME },
            onCreateAccess = { route = AdminRoute.CREATE_ACCESS },
            onOpenUser = { userId ->
                selectedUserId = userId
                route = AdminRoute.USER_DETAIL
            },
            dataVersion = dataVersion,
            modifier = modifier
        )

        AdminRoute.CREATE_ACCESS -> CreateAccessScreen(
            onBack = { route = AdminRoute.ACCESSES },
            onCreated = {
                dataVersion++
                route = AdminRoute.ACCESSES
            },
            modifier = modifier
        )

        AdminRoute.USER_DETAIL -> {
            val currentUserId = selectedUserId
            if (currentUserId == null) {
                route = AdminRoute.ACCESSES
            } else {
                UserDetailScreen(
                    userId = currentUserId,
                    onBack = {
                        dataVersion++
                        route = AdminRoute.ACCESSES
                    },
                    onDataChanged = { dataVersion++ },
                    modifier = modifier
                )
            }
        }
    }
}
