package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.domnex.cfi.bridge.ui.screens.AccountScreen
import com.domnex.cfi.bridge.ui.screens.DiagnosticsScreen
import com.domnex.cfi.bridge.ui.screens.TechnicalConfigScreen

private enum class AdminRoute {
    HOME,
    BRIDGE_MONITORING,
    CLIENTS,
    CLIENT_DETAIL,
    ACCESSES,
    CREATE_ACCESS,
    USER_DETAIL,
    EDIT_USER,
    TECHNICAL_CONFIG,
    DIAGNOSTICS,
    ACCOUNT
}

@Composable
fun AdminRoot(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var route by rememberSaveable { mutableStateOf(AdminRoute.HOME) }
    var selectedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClientId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClientName by rememberSaveable { mutableStateOf<String?>(null) }
    var dataVersion by rememberSaveable { mutableIntStateOf(0) }

    when (route) {
        AdminRoute.HOME -> AdminHomeScreen(
            onOpenAccesses = { route = AdminRoute.ACCESSES },
            onOpenClients = { route = AdminRoute.CLIENTS },
            onOpenBridgeMonitoring = { route = AdminRoute.BRIDGE_MONITORING },
            onOpenTechnicalConfig = { route = AdminRoute.TECHNICAL_CONFIG },
            onOpenDiagnostics = { route = AdminRoute.DIAGNOSTICS },
            onOpenAccount = { route = AdminRoute.ACCOUNT },
            onLogout = onLogout,
            dataVersion = dataVersion,
            modifier = modifier
        )

        AdminRoute.BRIDGE_MONITORING -> BridgeMonitoringScreen(
            onBack = { route = AdminRoute.HOME },
            modifier = modifier
        )

        AdminRoute.CLIENTS -> ClientListScreen(
            onBack = { route = AdminRoute.HOME },
            onOpenClientDetail = { clientId, clientName ->
                selectedClientId = clientId
                selectedClientName = clientName
                route = AdminRoute.CLIENT_DETAIL
            },
            dataVersion = dataVersion,
            modifier = modifier
        )

        AdminRoute.CLIENT_DETAIL -> {
            val currentClientId = selectedClientId
            if (currentClientId == null) {
                route = AdminRoute.CLIENTS
            } else {
                ClientDetailScreen(
                    clientId = currentClientId,
                    onBack = {
                        dataVersion++
                        route = AdminRoute.CLIENTS
                    },
                    onOpenUserDetail = { userId ->
                        selectedUserId = userId
                        route = AdminRoute.USER_DETAIL
                    },
                    onCreateAccess = { clientId, clientName ->
                        selectedClientId = clientId
                        selectedClientName = clientName
                        route = AdminRoute.CREATE_ACCESS
                    },
                    onDataChanged = { dataVersion++ },
                    dataVersion = dataVersion,
                    modifier = modifier
                )
            }
        }

        AdminRoute.ACCOUNT -> AccountScreen(
            onBack = { route = AdminRoute.HOME },
            onLogout = onLogout,
            modifier = modifier
        )

        AdminRoute.DIAGNOSTICS -> DiagnosticsScreen(
            onBack = { route = AdminRoute.HOME },
            modifier = modifier
        )

        AdminRoute.TECHNICAL_CONFIG -> TechnicalConfigScreen(
            onBack = { route = AdminRoute.HOME },
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
            onBack = {
                dataVersion++
                route = if (selectedClientId != null) AdminRoute.CLIENT_DETAIL else AdminRoute.ACCESSES
            },
            onCreated = {
                dataVersion++
                route = if (selectedClientId != null) AdminRoute.CLIENT_DETAIL else AdminRoute.ACCESSES
            },
            preSelectedClientName = selectedClientName,
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
                        route = if (selectedClientId != null) AdminRoute.CLIENT_DETAIL else AdminRoute.ACCESSES
                    },
                    onDataChanged = { dataVersion++ },
                    onEditUser = { route = AdminRoute.EDIT_USER },
                    modifier = modifier
                )
            }
        }

        AdminRoute.EDIT_USER -> {
            val currentUserId = selectedUserId
            if (currentUserId == null) {
                route = AdminRoute.ACCESSES
            } else {
                EditAccessScreen(
                    userId = currentUserId,
                    onBack = { route = AdminRoute.USER_DETAIL },
                    onDataChanged = { dataVersion++ },
                    modifier = modifier
                )
            }
        }
    }
}
