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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.ClientDeletionConfirmation
import com.domnex.cfi.bridge.auth.ClientDeletionSummary
import com.domnex.cfi.bridge.auth.DeleteClientOutcome
import com.domnex.cfi.bridge.auth.StatusChangeOutcome
import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.auth.clientDeletionSummaryFor
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.DevDataBadge
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.components.StatusBadge
import com.domnex.cfi.bridge.ui.components.BadgeTone
import com.domnex.cfi.bridge.ui.components.UserListItem
import com.domnex.cfi.bridge.ui.components.formatDate
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import com.domnex.cfi.bridge.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ClientDetailScreen(
    clientId: String,
    onBack: () -> Unit,
    onOpenUserDetail: (String) -> Unit,
    onCreateAccess: (clientId: String, clientName: String) -> Unit,
    onDataChanged: () -> Unit,
    dataVersion: Int = 0,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    var clientName by remember { mutableStateOf<String?>(null) }
    var linkedUsers by remember { mutableStateOf<List<UserAccount>>(emptyList()) }
    var summary by remember { mutableStateOf<ClientDeletionSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var actionBusy by remember { mutableStateOf(false) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var typedClientName by remember { mutableStateOf("") }
    var deleteBusy by remember { mutableStateOf(false) }
    var deletedClientName by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(clientId, dataVersion, tick) {
        loading = true
        loadError = null
        actionError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val directory = AuthProvider.userDirectory
                val allUsers = directory.listUsers(filter = AccessFilter.CLIENTS)
                val users = allUsers.filter { user ->
                    user.clientId == clientId ||
                        user.clientName?.trim() == directory.findClients()
                            .firstOrNull { it.id == clientId }?.name
                }
                val resolvedName = users.firstNotNullOfOrNull { it.clientName }
                    ?: directory.findClients().firstOrNull { it.id == clientId }?.name
                val delSummary = clientDeletionSummaryFor(allUsers, clientId, resolvedName)
                Triple(resolvedName, users, delSummary)
            }.onFailure { error ->
                clientName = null
                linkedUsers = emptyList()
                summary = null
                loadError = error.message ?: "Falha ao carregar dados do cliente."
            }.getOrDefault(Triple(null, emptyList<ClientDeletionSummary>(), null))
        }
        @Suppress("UNCHECKED_CAST")
        val triple = result as Triple<String?, List<UserAccount>, ClientDeletionSummary?>
        clientName = triple.first
        linkedUsers = triple.second
        summary = triple.third
        loading = false
    }

    fun changeUserStatus(userId: String, target: UserStatus) {
        if (actionBusy) return
        actionBusy = true
        actionError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                AuthProvider.userDirectory.setStatus(userId, target)
            }
            actionBusy = false
            when (outcome) {
                StatusChangeOutcome.Updated -> {
                    tick++
                    onDataChanged()
                }
                is StatusChangeOutcome.Failed -> actionError = outcome.message
            }
        }
    }

    fun performClientDeletion() {
        val s = summary ?: return
        if (deleteBusy) return
        deleteBusy = true
        actionError = null
        scope.launch {
            val resolvedClientId = withContext(Dispatchers.IO) {
                AuthProvider.userDirectory.findClients()
                    .firstOrNull { it.id == clientId || it.name == s.clientName }?.id
            }
            val outcome: DeleteClientOutcome = if (resolvedClientId == null) {
                DeleteClientOutcome.Failed("Cliente não encontrado.")
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { AuthProvider.userDirectory.deleteClient(resolvedClientId) }
                        .getOrElse {
                            DeleteClientOutcome.Failed(it.message ?: "Falha ao excluir o cliente.")
                        }
                }
            }
            deleteBusy = false
            showDeleteDialog = false
            when (outcome) {
                is DeleteClientOutcome.Deleted -> {
                    onDataChanged()
                    deletedClientName = outcome.clientName.ifEmpty { s.clientName }
                }
                is DeleteClientOutcome.Failed -> actionError = outcome.message
            }
        }
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
                text = "Detalhe do cliente",
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

        if (loading) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Carregando dados do cliente...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            return@Column
        }

        if (loadError != null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = loadError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose
            )
            return@Column
        }

        val resolvedName = clientName
        if (resolvedName == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Cliente não encontrado.",
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose
            )
            return@Column
        }

        Spacer(Modifier.height(18.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = resolvedName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        SectionCaption("CRIADO EM")
                        Spacer(Modifier.height(2.dp))
                        val createdAt = linkedUsers.firstOrNull()?.createdAtMillis
                        Text(
                            text = if (createdAt != null) formatDate(createdAt) else "—",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        SectionCaption("ACESSOS VINCULADOS")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${summary?.totalAccesses ?: linkedUsers.size}",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiMini(
                label = "ATIVOS",
                value = "${summary?.activeUsers ?: linkedUsers.count { it.status == UserStatus.ACTIVE }}",
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            KpiMini(
                label = "PENDENTES",
                value = "${summary?.pendingUsers ?: linkedUsers.count { it.status == UserStatus.PENDING }}",
                color = WarningAmber,
                modifier = Modifier.weight(1f)
            )
            KpiMini(
                label = "SUSPENSOS",
                value = "${summary?.suspendedUsers ?: linkedUsers.count { it.status == UserStatus.SUSPENDED }}",
                color = FailureRose,
                modifier = Modifier.weight(1f)
            )
        }

        if (actionError != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = actionError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionCaption("USUÁRIOS VINCULADOS (${linkedUsers.size})")
        Spacer(Modifier.height(10.dp))

        if (linkedUsers.isEmpty()) {
            Text(
                text = "Nenhum usuário vinculado a este cliente.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            linkedUsers.forEach { user ->
                UserListItem(
                    user = user,
                    onClick = { onOpenUserDetail(user.id) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionCaption("AÇÕES DO CLIENTE")
        Spacer(Modifier.height(10.dp))

        GoldPrimaryButton(
            text = "NOVO ACESSO PARA ESTE CLIENTE",
            onClick = { onCreateAccess(clientId, resolvedName) },
            modifier = Modifier.fillMaxWidth()
        )

        val s = summary
        if (s != null && s.totalAccesses > 0) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = FailureRose.copy(alpha = 0.06f),
                contentColor = FailureRose,
                border = BorderStroke(1.dp, FailureRose.copy(alpha = 0.40f))
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Excluir \"$resolvedName\" definitivamente",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Remove PERMANENTEMENTE o cliente e TODOS os acessos vinculados do servidor. " +
                            "Os usuários perdem o login imediatamente e não há como desfazer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${s.totalAccesses} acesso(s) • ${s.activeUsers} ativo(s) • " +
                            "${s.pendingUsers} pendente(s) • ${s.suspendedUsers} suspenso(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    DangerAction(
                        text = if (deleteBusy) "EXCLUINDO..." else "Excluir cliente definitivamente",
                        enabled = !actionBusy && !deleteBusy,
                        onClick = {
                            typedClientName = ""
                            actionError = null
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Prefira suspender acessos a excluí-los — o histórico fica preservado.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showDeleteDialog && summary != null) {
        val s = summary!!
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) showDeleteDialog = false },
            title = {
                Text(text = "Excluir cliente definitivamente?", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column {
                    Text(
                        text = "Esta ação é irreversível e removerá do servidor:\n\n" +
                            "• O cliente \"${s.clientName}\"\n" +
                            "• ${s.totalAccesses} acesso(s): ${s.activeUsers} ativo(s), " +
                            "${s.pendingUsers} pendente(s), ${s.suspendedUsers} suspenso(s)\n\n" +
                            "Todos esses usuários perderão o acesso ao aplicativo IMEDIATAMENTE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Digite o nome do cliente para confirmar:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    BridgeTextField(
                        value = typedClientName,
                        onValueChange = { typedClientName = it },
                        placeholder = "Nome do cliente",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleteBusy &&
                        ClientDeletionConfirmation.matches(typedClientName, s.clientName),
                    onClick = { performClientDeletion() }
                ) {
                    Text(
                        text = "EXCLUIR DEFINITIVAMENTE",
                        color = FailureRose,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { showDeleteDialog = false }) {
                    Text(text = "Cancelar", color = TextSecondary)
                }
            }
        )
    }

    deletedClientName?.let { name ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = "Cliente excluído", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "\"$name\" foi excluído definitivamente, junto com todos os seus acessos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletedClientName = null
                    onBack()
                }) {
                    Text(text = "OK", color = Gold, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }
}

@Composable
private fun KpiMini(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DangerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = FailureRose.copy(alpha = 0.08f),
        contentColor = FailureRose,
        border = BorderStroke(1.dp, FailureRose.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}
