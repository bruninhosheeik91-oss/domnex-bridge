package com.domnex.cfi.bridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.auth.AccountPresentation
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.data.SaleHistory
import com.domnex.cfi.bridge.provisioning.BridgeProvisioning
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.IntegrationStatusCard
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import com.domnex.cfi.bridge.ui.theme.WarningAmber

/**
 * Conta: dados reais da sessão (Supabase) e status da integração.
 * Nunca exibe senha, tokens, endpoint ou qualquer parâmetro técnico.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { BridgeProvisioning.get(context) }
    val provisioningState = remember { repository.state() }
    val systemName = remember { repository.load().displayNameOrDefault() }
    val account = AuthProvider.authGateway.currentUser()
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← Voltar",
                    color = Gold,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Text(text = "CONTA", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Seu acesso",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))

        IntegrationStatusCard(
            provisioningState = provisioningState,
            systemName = systemName
        )

        Spacer(Modifier.height(14.dp))

        BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            Column {
                Text(
                    text = "PERFIL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = account?.name?.trim()?.ifEmpty { null } ?: "Usuário",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val email = account?.email?.trim().orEmpty()
                if (email.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = AccountPresentation.roleLabel(account?.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gold,
                    fontWeight = FontWeight.Bold
                )

                // Cliente/organização vinculada — somente quando existe de fato.
                val linkedClient = AccountPresentation.linkedClientLabel(account)
                if (linkedClient != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Cliente vinculado: $linkedClient",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // Status real da conta quando disponível na sessão.
                val statusLabel = AccountPresentation.statusLabel(account?.status)
                if (statusLabel != null && account != null) {
                    Spacer(Modifier.height(10.dp))
                    StatusLine(statusLabel, account.status)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            Column {
                Text(
                    text = "HISTÓRICO",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Vendas capturadas ficam guardadas somente neste dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Limpar histórico deste dispositivo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FailureRose,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = onLogout,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Sair da conta",
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(18.dp))
        AboutCard()
        Spacer(Modifier.height(30.dp))
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(text = "Excluir histórico local?", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "Esta ação remove somente o histórico armazenado neste dispositivo. " +
                        "Vendas já enviadas ao sistema de destino não serão excluídas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SaleHistory.clearAsync(context)
                    showClearHistoryDialog = false
                }) {
                    Text(
                        text = "LIMPAR HISTÓRICO",
                        color = FailureRose,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(text = "CANCELAR", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun StatusLine(label: String, status: UserStatus?) {
    val accent = when (status) {
        UserStatus.ACTIVE -> SuccessGreen
        UserStatus.PENDING -> WarningAmber
        else -> FailureRose
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BridgeStatusDot(color = accent, size = 7.dp, glowRadius = 4.dp)
        Spacer(Modifier.padding(end = 6.dp))
        Text(
            text = "Status da conta: $label",
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Sobre o Domnex Bridge — dados reais do BuildConfig (sem hardcode duplicado).
 */
@Composable
private fun AboutCard() {
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "SOBRE O DOMNEX BRIDGE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        AboutRow("Versão", BuildConfig.VERSION_NAME)
        AboutRow("Build", BuildConfig.VERSION_CODE.toString())
        AboutRow("Desenvolvido por", "Domnex Tech")
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
