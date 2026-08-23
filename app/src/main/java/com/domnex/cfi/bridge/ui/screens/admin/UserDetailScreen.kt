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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.auth.LocalUserDirectory
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
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun UserDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onDataChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    var showEditInfo by rememberSaveable { mutableStateOf(false) }
    var resetSentTick by rememberSaveable { mutableIntStateOf(0) }

    val user = remember(userId, tick) { LocalUserDirectory.getUser(userId) }

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

        if (user == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Usuário não encontrado.",
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose
            )
            return@Column
        }

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

        Spacer(Modifier.height(20.dp))
        SectionCaption("AÇÕES")
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showEditInfo = true },
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.04f),
            contentColor = Gold,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f))
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
                    onClick = {
                        LocalUserDirectory.setStatus(user.id, UserStatus.SUSPENDED)
                        tick++
                        onDataChanged()
                    }
                )
            }
            UserStatus.SUSPENDED -> {
                Spacer(Modifier.height(10.dp))
                GoldPrimaryButton(
                    text = "REATIVAR ACESSO",
                    onClick = {
                        LocalUserDirectory.setStatus(user.id, UserStatus.ACTIVE)
                        tick++
                        onDataChanged()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { resetSentTick++ },
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.04f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "Enviar redefinição de senha",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (resetSentTick > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Convite de redefinição seria enviado para ${user.email} (simulado).",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
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

    if (showEditInfo) {
        AlertDialog(
            onDismissRequest = { showEditInfo = false },
            title = {
                Text(text = "Editar acesso", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "A edição completa (nome, e-mail e cliente vinculado) será habilitada quando o backend de autenticação estiver integrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showEditInfo = false }) {
                    Text(text = "Entendi", color = Gold, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }
}

@Composable
private fun DangerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
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
