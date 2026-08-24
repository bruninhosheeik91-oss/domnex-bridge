package com.domnex.cfi.bridge.ui.screens.admin

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.auth.AccessRules
import com.domnex.cfi.bridge.auth.AccessUpdate
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.ClientRef
import com.domnex.cfi.bridge.auth.EmailChangeOutcome
import com.domnex.cfi.bridge.auth.UpdateAccessOutcome
import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.FilterPill
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edição REAL de um acesso existente:
 *  - nome/perfil/status/cliente -> RPC segura `bridge_admin_update_access`
 *    (o servidor revalida DOMNEX_ADMIN+ACTIVE e as regras de vínculo);
 *  - e-mail -> Edge Function privilegiada `admin-update-email` (auth.admin),
 *    pois o e-mail pertence ao Supabase Auth, não a bridge_profiles.
 *
 * Nenhuma operação finge sucesso: falhas do backend aparecem como erro.
 */
@Composable
fun EditAccessScreen(
    userId: String,
    onBack: () -> Unit,
    onDataChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableStateOf(0) }
    var user by remember(userId) { mutableStateOf<UserAccount?>(null) }
    var clients by remember { mutableStateOf<List<ClientRef>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(true) }

    var name by rememberSaveable { mutableStateOf("") }
    var roleIsAdmin by rememberSaveable { mutableStateOf(false) }
    var selectedStatus by rememberSaveable { mutableStateOf(UserStatus.ACTIVE.name) }
    var selectedClientId by rememberSaveable { mutableStateOf<String?>(null) }
    var noClientSelected by rememberSaveable { mutableStateOf(false) }

    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var pendingAdminSwitch by rememberSaveable { mutableStateOf(false) }

    var newEmail by rememberSaveable { mutableStateOf("") }
    var emailBusy by rememberSaveable { mutableStateOf(false) }
    var emailMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var showEmailConfirm by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, tick) {
        loading = true
        val loadedUser = withContext(Dispatchers.IO) {
            runCatching { AuthProvider.userDirectory.getUser(userId) }.getOrNull()
        }
        val loadedClients = withContext(Dispatchers.IO) {
            runCatching { AuthProvider.userDirectory.findClients() }.getOrDefault(emptyList())
        }
        clients = loadedClients
        user = loadedUser
        loadedUser?.let { account ->
            name = account.name
            roleIsAdmin = account.role == UserRole.DOMNEX_ADMIN
            selectedStatus = account.status.name
            val linkedClient = loadedClients.firstOrNull { it.name == account.clientName }
            selectedClientId = linkedClient?.id
            noClientSelected = !roleIsAdmin && account.status == UserStatus.PENDING && linkedClient == null
        }
        loading = false
    }

    val selfEditing = user?.let { it.id == AuthProvider.authGateway.currentUser()?.id } == true

    fun effectiveRole(): UserRole =
        if (roleIsAdmin) UserRole.DOMNEX_ADMIN else UserRole.CLIENT

    fun effectiveStatus(): UserStatus =
        runCatching { UserStatus.valueOf(selectedStatus) }.getOrDefault(UserStatus.PENDING)

    fun effectiveClientId(): String? =
        when {
            roleIsAdmin -> null
            noClientSelected -> null
            else -> selectedClientId
        }

    fun submit(confirmedAdminSwitch: Boolean) {
        if (saving) return
        errorMessage = null
        if (name.trim().length < 2) {
            errorMessage = "Informe o nome do usuário."
            return
        }
        val violation = AccessRules.validate(effectiveRole(), effectiveStatus(), effectiveClientId())
        if (violation != null) {
            errorMessage = violation
            return
        }
        if (selfEditing) {
            // Espelha a proteção da RPC contra auto-bloqueio do último admin.
            if (user?.role != null && effectiveRole() != user?.role) {
                errorMessage = "Não é permitido alterar o próprio perfil."
                return
            }
            if (user?.status != null && effectiveStatus() != user?.status) {
                errorMessage = "Não é permitido alterar o próprio status."
                return
            }
        }
        if (roleIsAdmin && user?.role != UserRole.DOMNEX_ADMIN && !confirmedAdminSwitch) {
            pendingAdminSwitch = true
            return
        }

        val target = user ?: return

        // Diff de vínculo: compara o id efetivo escolhido com o vínculo atual.
        val currentClientId = clients.firstOrNull { it.name == target.clientName }?.id
        val newClientId = effectiveClientId()
        val clientChanged = when {
            roleIsAdmin -> target.role == UserRole.CLIENT // virar admin limpa o vínculo
            else -> newClientId != currentClientId
        }

        val update = AccessUpdate(
            name = name.trim().takeIf { it != target.name },
            role = effectiveRole().takeIf { it != target.role },
            status = effectiveStatus().takeIf { it != target.status },
            clientId = newClientId?.takeIf { clientChanged && !roleIsAdmin },
            clearClient = clientChanged && newClientId == null
        )

        if (!update.hasChanges()) {
            onBack()
            return
        }

        saving = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                AuthProvider.userDirectory.updateAccess(target.id, update)
            }
            saving = false
            when (outcome) {
                UpdateAccessOutcome.Updated -> {
                    onDataChanged()
                    onBack()
                }
                is UpdateAccessOutcome.Failed -> errorMessage = outcome.message
            }
        }
    }

    fun applyEmailChange() {
        if (emailBusy) return
        emailError = null
        emailMessage = null
        val normalized = newEmail.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            emailError = "Informe um e-mail válido."
            return
        }
        if (normalized == user?.email) {
            emailError = "O e-mail informado já é o atual."
            return
        }
        emailBusy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                AuthProvider.userDirectory.changeEmail(userId, normalized)
            }
            emailBusy = false
            when (outcome) {
                EmailChangeOutcome.Changed -> {
                    emailMessage = "E-mail atualizado no Supabase Auth para $normalized."
                    newEmail = ""
                    tick++
                }
                is EmailChangeOutcome.Failed -> emailError = outcome.message
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(30.dp))
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
                text = "Editar acesso",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (loading) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Carregando acesso...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            return@Column
        }

        val currentUser = user
        if (currentUser == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Usuário não encontrado.",
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose
            )
            return@Column
        }
        val user = currentUser

        if (selfEditing) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Este é o seu próprio acesso: papel e status são alteráveis apenas por outro administrador.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(18.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionCaption("NOME")
                Spacer(Modifier.height(10.dp))
                BridgeTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    placeholder = "Nome completo",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionCaption("PERFIL DE ACESSO")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(
                        text = "CLIENT",
                        selected = !roleIsAdmin,
                        onClick = { roleIsAdmin = false; errorMessage = null }
                    )
                    FilterPill(
                        text = "DOMNEX_ADMIN",
                        selected = roleIsAdmin,
                        onClick = { roleIsAdmin = true; errorMessage = null }
                    )
                }
                if (roleIsAdmin) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Administradores têm controle total sobre clientes e acessos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        if (!roleIsAdmin) {
            Spacer(Modifier.height(16.dp))
            BridgeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionCaption("CLIENTE VINCULADO")
                    Spacer(Modifier.height(10.dp))
                    clients.forEach { client ->
                        FilterPill(
                            text = client.name,
                            selected = selectedClientId == client.id && !noClientSelected,
                            onClick = {
                                selectedClientId = client.id
                                noClientSelected = false
                                errorMessage = null
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    FilterPill(
                        text = "Sem cliente (somente PENDENTE)",
                        selected = noClientSelected,
                        onClick = {
                            noClientSelected = true
                            errorMessage = null
                        },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (effectiveStatus() != UserStatus.PENDING && effectiveClientId() == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = AccessRules.CLIENT_REQUIRES_CLIENT,
                            style = MaterialTheme.typography.bodySmall,
                            color = FailureRose
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionCaption("STATUS")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UserStatus.ACTIVE, UserStatus.PENDING, UserStatus.SUSPENDED).forEach { status ->
                        FilterPill(
                            text = when (status) {
                                UserStatus.ACTIVE -> "Ativo"
                                UserStatus.PENDING -> "Pendente"
                                UserStatus.SUSPENDED -> "Suspenso"
                            },
                            selected = selectedStatus == status.name,
                            onClick = {
                                selectedStatus = status.name
                                errorMessage = null
                            }
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))
        GoldPrimaryButton(
            text = if (saving) "SALVANDO..." else "SALVAR ALTERAÇÕES",
            onClick = { submit(confirmedAdminSwitch = false) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )

        // ------------------------------------------------------- e-mail (Auth)
        Spacer(Modifier.height(24.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionCaption("E-MAIL · SUPABASE AUTH")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                BridgeTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it; emailError = null; emailMessage = null },
                    placeholder = "Novo e-mail",
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                GoldPrimaryButton(
                    text = if (emailBusy) "APLICANDO..." else "APLICAR NOVO E-MAIL",
                    onClick = { showEmailConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !emailBusy
                )
                if (emailMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = emailMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                }
                if (emailError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = emailError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = FailureRose
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "O e-mail pertence ao Supabase Auth e é alterado pela função segura " +
                        "admin-update-email no backend. Requer deploy da função no projeto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(30.dp))
    }

    if (pendingAdminSwitch) {
        AlertDialog(
            onDismissRequest = { pendingAdminSwitch = false },
            title = {
                Text(text = "Tornar DOMNEX_ADMIN?", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "Administradores têm controle total sobre clientes, acessos e configurações, " +
                        "e ficam sem cliente vinculado. Confirmar?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAdminSwitch = false
                    submit(confirmedAdminSwitch = true)
                }) {
                    Text(text = "Confirmar", color = Gold, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdminSwitch = false }) {
                    Text(text = "Cancelar", color = TextSecondary)
                }
            }
        )
    }

    if (showEmailConfirm) {
        AlertDialog(
            onDismissRequest = { showEmailConfirm = false },
            title = {
                Text(text = "Alterar e-mail de acesso", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "O login passará a ser feito com o novo e-mail (${newEmail.trim().lowercase()}). " +
                        "A alteração é aplicada diretamente no Supabase Auth. Confirmar?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEmailConfirm = false
                    applyEmailChange()
                }) {
                    Text(text = "Confirmar", color = Gold, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailConfirm = false }) {
                    Text(text = "Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
