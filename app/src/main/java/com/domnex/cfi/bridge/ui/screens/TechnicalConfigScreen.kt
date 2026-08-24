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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.provisioning.BridgeProvisioning
import com.domnex.cfi.bridge.provisioning.TechnicalConfigValidator
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

/**
 * Configuração TÉCNICA — acesso exclusivo de DOMNEX_ADMIN ativo.
 * Guarda de perfil em nível de UI; o CLIENT nunca chega aqui pelo roteamento.
 */
@Composable
fun TechnicalConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = AuthProvider.authGateway.currentUser()
    val isAdmin = account?.role == UserRole.DOMNEX_ADMIN

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

        if (!isAdmin) {
            Spacer(Modifier.height(24.dp))
            BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Text(
                    text = "Acesso restrito",
                    style = MaterialTheme.typography.titleMedium,
                    color = FailureRose,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A configuração técnica é exclusiva do administrador Domnex.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            return@Column
        }

        TechnicalConfigForm()
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun TechnicalConfigForm() {
    val context = LocalContext.current
    val repository = remember { BridgeProvisioning.get(context) }
    val saved = remember { repository.load() }

    var systemName by remember { mutableStateOf(saved.targetSystemName) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var bridgeToken by remember { mutableStateOf(saved.bridgeToken) }
    var revealToken by remember { mutableStateOf(false) }
    var validationReasons by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationOk by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }

    fun clearFeedback() {
        validationOk = false
        saveSuccess = false
        saveError = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "CONFIGURAÇÃO TÉCNICA", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Visível apenas para o administrador Domnex.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(Modifier.height(18.dp))

        BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "SISTEMA DE DESTINO",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            BridgeTextField(
                value = systemName,
                onValueChange = {
                    systemName = it
                    clearFeedback()
                },
                placeholder = "Nome exibido ao cliente (ex.: CFI)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Somente apresentação — não afeta a captura nem o envio.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(14.dp))

        BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "CREDENCIAIS",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "ENDPOINT",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            BridgeTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    clearFeedback()
                },
                placeholder = "URL de recebimento das vendas",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "TOKEN",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            BridgeTextField(
                value = bridgeToken,
                onValueChange = {
                    bridgeToken = it
                    clearFeedback()
                },
                placeholder = "Token do Bridge",
                keyboardType = KeyboardType.Password,
                visualTransformation = if (revealToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    // Revelação somente mediante ação explícita do admin.
                    TextButton(onClick = { revealToken = !revealToken }) {
                        Text(
                            text = if (revealToken) "Ocultar" else "Mostrar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (!revealToken && bridgeToken.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Token atual: ${TechnicalConfigValidator.maskToken(bridgeToken)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = {
                clearFeedback()
                validationReasons =
                    TechnicalConfigValidator.validate(baseUrl, bridgeToken)
                validationOk = validationReasons.isEmpty()
            },
            shape = MaterialTheme.shapes.medium,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "VALIDAR CONFIGURAÇÃO (LOCAL)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        if (validationReasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            validationReasons.forEach { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = FailureRose
                )
            }
        }
        if (validationOk) {
            Spacer(Modifier.height(8.dp))
            FeedbackRow(color = SuccessGreen, text = "Configuração local válida.")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Teste de conectividade não disponível nesta versão: o endpoint atual registra vendas e não possui healthcheck seguro.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(12.dp))
        GoldPrimaryButton(
            text = "SALVAR CONFIGURAÇÃO",
            onClick = {
                clearFeedback()
                val reasons = TechnicalConfigValidator.validate(baseUrl, bridgeToken)
                if (reasons.isNotEmpty()) {
                    validationReasons = reasons
                } else {
                    val ok = runCatching {
                        repository.save(
                            com.domnex.cfi.bridge.provisioning.TechnicalConfig(
                                baseUrl = baseUrl,
                                bridgeToken = bridgeToken,
                                targetSystemName = systemName
                            )
                        )
                        repository.state() ==
                            com.domnex.cfi.bridge.provisioning.ProvisioningState.CONFIGURED
                    }.getOrDefault(false)
                    if (ok) {
                        saveSuccess = true
                    } else {
                        saveError = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (saveSuccess) {
            Spacer(Modifier.height(8.dp))
            FeedbackRow(color = SuccessGreen, text = "Configuração salva.")
        }
        if (saveError) {
            Spacer(Modifier.height(8.dp))
            FeedbackRow(color = FailureRose, text = "Não foi possível salvar. Tente novamente.")
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "O token nunca aparece em logs nem em mensagens de erro.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun FeedbackRow(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BridgeStatusDot(color = color, size = 6.dp, glowRadius = 4.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
