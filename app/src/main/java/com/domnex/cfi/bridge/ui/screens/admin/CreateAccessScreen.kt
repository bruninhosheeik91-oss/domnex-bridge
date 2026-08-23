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
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.CreateUserOutcome
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.FilterPill
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CreateAccessScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var roleIsAdmin by rememberSaveable { mutableStateOf(false) }
    var selectedClient by rememberSaveable { mutableStateOf<String?>(null) }
    var customClient by rememberSaveable { mutableStateOf("") }
    var statusPending by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdminConfirm by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val remoteBackend = AuthProvider.usingLocalDevBackend.not()
    var clientNames by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        clientNames = withContext(Dispatchers.IO) {
            runCatching { AuthProvider.userDirectory.findClientNames() }.getOrDefault(emptyList())
        }
    }

    fun effectiveClientName(): String? {
        if (roleIsAdmin) return null
        val typed = customClient.trim()
        return if (typed.isNotEmpty()) typed else selectedClient
    }

    fun submit(confirmedAdmin: Boolean) {
        if (creating) return
        errorMessage = null
        if (name.trim().length < 2) {
            errorMessage = "Informe o nome do usuário."
            return
        }
        val normalizedEmail = email.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            errorMessage = "Informe um e-mail válido."
            return
        }
        if (!roleIsAdmin && effectiveClientName().isNullOrBlank()) {
            errorMessage = "Vincule um cliente ao perfil CLIENT."
            return
        }
        if (roleIsAdmin && !confirmedAdmin) {
            showAdminConfirm = true
            return
        }
        val initialStatus = if (statusPending) UserStatus.PENDING else UserStatus.ACTIVE
        creating = true
        scope.launch {
            // Criação real envolve backend privilegiado -> roda em IO.
            val outcome = withContext(Dispatchers.IO) {
                AuthProvider.userDirectory.createAccess(
                    name = name,
                    email = normalizedEmail,
                    role = if (roleIsAdmin) UserRole.DOMNEX_ADMIN else UserRole.CLIENT,
                    clientName = effectiveClientName(),
                    status = initialStatus
                )
            }
            creating = false
            when (outcome) {
                is CreateUserOutcome.Created -> onCreated()
                CreateUserOutcome.EmailInUse -> {
                    errorMessage = "Já existe um acesso com este e-mail."
                }
                is CreateUserOutcome.Failed -> {
                    errorMessage = outcome.message
                }
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
                text = "Criar acesso",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(18.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionCaption("DADOS DO USUÁRIO")
                Spacer(Modifier.height(10.dp))
                BridgeTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    placeholder = "Nome completo",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                BridgeTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    placeholder = "E-mail",
                    keyboardType = KeyboardType.Email,
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
                        text = "Administradores enxergam todos os clientes e gerenciam acessos. Confirmar antes de criar.",
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
                    clientNames.forEach { client ->
                        FilterPill(
                            text = client,
                            selected = selectedClient == client && customClient.isBlank(),
                            onClick = {
                                selectedClient = client
                                customClient = ""
                                errorMessage = null
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    BridgeTextField(
                        value = customClient,
                        onValueChange = { customClient = it; errorMessage = null },
                        placeholder = "Ou informe outro cliente",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (remoteBackend) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Se o cliente ainda não existir, ele será criado no Supabase com este nome.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionCaption("STATUS INICIAL")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(
                        text = "Ativo",
                        selected = !statusPending,
                        onClick = { statusPending = false }
                    )
                    FilterPill(
                        text = "Pendente",
                        selected = statusPending,
                        onClick = { statusPending = true }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (remoteBackend) {
                        "O acesso é criado no Supabase Auth sem senha. O usuário recebe um convite por e-mail para definir a própria senha."
                    } else {
                        "O acesso é criado sem senha. O usuário recebe um convite para definir a própria senha — fluxo simulado nesta versão DEV."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))
        GoldPrimaryButton(
            text = if (creating) "CRIANDO..." else "CRIAR ACESSO",
            onClick = { submit(confirmedAdmin = false) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !creating
        )
        Spacer(Modifier.height(30.dp))
    }

    if (showAdminConfirm) {
        AlertDialog(
            onDismissRequest = { showAdminConfirm = false },
            title = {
                Text(text = "Criar outro DOMNEX_ADMIN?", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "Administradores têm controle total sobre clientes, acessos e configurações. Deseja confirmar a criação deste acesso administrativo?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdminConfirm = false
                    submit(confirmedAdmin = true)
                }) {
                    Text(text = "Confirmar", color = Gold, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminConfirm = false }) {
                    Text(text = "Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
