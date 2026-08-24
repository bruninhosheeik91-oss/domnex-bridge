package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.PasswordResetOutcome
import com.domnex.cfi.bridge.auth.StatusChangeOutcome
import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.FieldRow
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.components.formatDate
import com.domnex.cfi.bridge.ui.components.statusColor
import com.domnex.cfi.bridge.ui.components.statusLabel
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UserDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onDataChanged: () -> Unit,
    onEditUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    var resetState by rememberSaveable { mutableStateOf("idle") } // idle | busy | requested | failed
    var resetError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var user by remember(userId) { mutableStateOf<UserAccount?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }

    LaunchedEffect(userId, tick) {
        loading = true
        user = withContext(Dispatchers.IO) {
            runCatching { AuthProvider.userDirectory.getUser(userId) }.getOrNull()
        }
        loading = false
    }

    fun changeStatus(target: UserStatus) {
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

    fun requestPasswordReset() {
        if (resetState == "busy") return
        resetState = "busy"
        resetError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                AuthProvider.userDirectory.sendPasswordReset(userId)
            }
            when (outcome) {
                PasswordResetOutcome.Requested -> resetState = "requested"
                is PasswordResetOutcome.Failed -> {
                    resetError = outcome.message
                    resetState = "failed"
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
                text = "Detalhes do acesso",
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

        Spacer(Modifier.height(18.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BridgeStatusDot(color = statusColor(user.status), size = 10.dp, glowRadius = 5.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                FieldRow(label = "PERFIL", value = user.role.name)
                if (user.role == com.domnex.cfi.bridge.auth.UserRole.CLIENT) {
                    Spacer(Modifier.height(10.dp))
                    FieldRow(label = "CLIENTE VINCULADO", value = user.clientName ?: "—")
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        SectionCaption("STATUS")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = statusLabel(user.status),
                            style = MaterialTheme.typography.titleSmall,
                            color = statusColor(user.status)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        SectionCaption("CRIADO EM")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatDate(user.createdAtMillis),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
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

        Spacer(Modifier.height(20.dp))
        SectionCaption("AÇÕES")
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onEditUser,
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.04f),
            contentColor = com.domnex.cfi.bridge.ui.theme.Gold,
            border = BorderStroke(1.dp, com.domnex.cfi.bridge.ui.theme.Gold.copy(alpha = 0.35f))
        ) {
            Text(
                text = "Editar acesso",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        when (user.status) {
            UserStatus.ACTIVE, UserStatus.PENDING -> {
                Spacer(Modifier.height(10.dp))
                DangerAction(
                    text = "Suspender acesso",
                    enabled = !actionBusy,
                    onClick = { changeStatus(UserStatus.SUSPENDED) }
                )
            }
            UserStatus.SUSPENDED -> {
                Spacer(Modifier.height(10.dp))
                GoldPrimaryButton(
                    text = if (actionBusy) "AGUARDE..." else "REATIVAR ACESSO",
                    onClick = { changeStatus(UserStatus.ACTIVE) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actionBusy
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { requestPasswordReset() },
            enabled = resetState != "busy",
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.04f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = if (resetState == "busy") "Solicitando redefinição..." else "Enviar redefinição de senha",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (resetState == "requested") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Solicitação aceita pelo Supabase para ${user.email}. " +
                            "O e-mail chega somente se o projeto tiver SMTP configurado " +
                            "(Authentication → Emails).",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                }
                if (resetError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = resetError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = FailureRose
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nenhuma senha é armazenada ou exibida neste painel. Prefira suspender o acesso a excluí-lo.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(30.dp))
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
