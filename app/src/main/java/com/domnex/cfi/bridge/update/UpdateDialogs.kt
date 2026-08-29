package com.domnex.cfi.bridge.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.TextSecondary

/**
 * Diálogo de atualização disponível (opcional ou obrigatória).
 *
 * Oculto quando não há atualização ([UpdateCheckUiState.UpToDate]/Idle/etc.);
 * nada é mostrado desnecessariamente.
 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    required: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    installing: Boolean
) {
    val title = if (required) "Atualização necessária" else "Nova versão disponível"

    AlertDialog(
        onDismissRequest = { if (!installing && !required) onDismiss() },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = if (required) FailureRose else Gold,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "DOMNEX BRIDGE ${info.latestVersionName.ifBlank { info.latestVersionCode.toString() }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                if (required) {
                    Text(
                        text = "Uma nova versão do DOMNEX BRIDGE precisa ser instalada " +
                            "para continuar utilizando o serviço.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "Já existe uma versão mais recente disponível para o seu DOMNEX BRIDGE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Novidades:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (installing) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Preparando o download...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !installing) {
                Text(
                    text = if (installing) "AGUARDE..." else "ATUALIZAR AGORA",
                    color = Gold,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            if (!required) {
                TextButton(onClick = onDismiss, enabled = !installing) {
                    Text(
                        text = "MAIS TARDE",
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !installing) {
                    Text(
                        text = "SAIR",
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

/**
 * Mensagem de estado (não-configurado / erro / atualizado) exibida de forma
 * discreta após consultar manualmente as atualizações na conta.
 */
@Composable
fun UpdateStatusMessage(state: UpdateCheckUiState) {
    val message = when (state) {
        is UpdateCheckUiState.UpToDate -> "Você está com a versão ${state.installedVersionName} — atualizado."
        is UpdateCheckUiState.Error -> "Não foi possível verificar atualizações agora. Tente novamente em instantes."
        UpdateCheckUiState.NotConfigured -> "Serviço de atualizações ainda não configurado."
        else -> null
    }
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}
