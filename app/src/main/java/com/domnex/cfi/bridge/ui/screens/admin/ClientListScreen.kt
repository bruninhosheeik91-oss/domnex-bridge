package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.ClientRef
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.ui.components.DevDataBadge
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.components.StatusBadge
import com.domnex.cfi.bridge.ui.components.BadgeTone
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import com.domnex.cfi.bridge.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ClientWithStats(
    val client: ClientRef,
    val totalAccesses: Int,
    val activeCount: Int,
    val pendingCount: Int,
    val suspendedCount: Int
)

@Composable
fun ClientListScreen(
    onBack: () -> Unit,
    onOpenClientDetail: (clientId: String, clientName: String) -> Unit,
    dataVersion: Int = 0,
    modifier: Modifier = Modifier
) {
    var clients by remember { mutableStateOf<List<ClientWithStats>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dataVersion) {
        loading = true
        loadError = null
        clients = withContext(Dispatchers.IO) {
            runCatching {
                val directory = AuthProvider.userDirectory
                val clientRefs = directory.findClients()
                val allUsers = directory.listUsers(filter = AccessFilter.CLIENTS)
                clientRefs.map { ref ->
                    val linked = allUsers.filter { it.clientName == ref.name }
                    ClientWithStats(
                        client = ref,
                        totalAccesses = linked.size,
                        activeCount = linked.count { it.status == UserStatus.ACTIVE },
                        pendingCount = linked.count { it.status == UserStatus.PENDING },
                        suspendedCount = linked.count { it.status == UserStatus.SUSPENDED }
                    )
                }
            }.onFailure { error ->
                clients = emptyList()
                loadError = error.message ?: "Falha ao carregar clientes."
            }.getOrDefault(emptyList())
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.05f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = "←",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = "Clientes",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(14.dp))
        if (AuthProvider.usingLocalDevBackend) {
            DevDataBadge()
        } else {
            StatusBadge(text = "BACKEND SUPABASE", tone = BadgeTone.Info)
        }

        Spacer(Modifier.height(18.dp))
        SectionCaption(if (loading) "CLIENTES" else "CLIENTES (${clients.size})")
        Spacer(Modifier.height(10.dp))

        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Carregando clientes...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = loadError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FailureRose
                )
            }
        } else if (clients.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Nenhum cliente encontrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)
            ) {
                items(clients, key = { it.client.id }) { clientWithStats ->
                    ClientListItem(
                        clientWithStats = clientWithStats,
                        onClick = { onOpenClientDetail(clientWithStats.client.id, clientWithStats.client.name) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Perfis: CLIENT · DOMNEX_ADMIN",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Gold.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ClientListItem(
    clientWithStats: ClientWithStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.04f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = clientWithStats.client.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${clientWithStats.totalAccesses} acessos",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (clientWithStats.activeCount > 0) {
                        Text(
                            text = "${clientWithStats.activeCount} ativos",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                    }
                    if (clientWithStats.pendingCount > 0) {
                        Text(
                            text = "${clientWithStats.pendingCount} pendentes",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber
                        )
                    }
                    if (clientWithStats.suspendedCount > 0) {
                        Text(
                            text = "${clientWithStats.suspendedCount} suspensos",
                            style = MaterialTheme.typography.bodySmall,
                            color = FailureRose
                        )
                    }
                }
            }
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
                color = Gold
            )
        }
    }
}
