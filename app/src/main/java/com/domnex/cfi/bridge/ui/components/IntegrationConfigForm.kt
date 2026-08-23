package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.domnex.cfi.bridge.service.SaleSender
import com.domnex.cfi.bridge.ui.theme.SuccessGreen

@Composable
fun IntegrationConfigForm(
    modifier: Modifier = Modifier,
    onConfiguredChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val defaultUrl = "https://xfvdqbuwqzenxdvtiqqd.supabase.co/functions/v1/cfi-bridge-webhook"
    val storedUrl = SaleSender.getBaseUrl(context)
    val baseUrl = remember { mutableStateOf(storedUrl.ifBlank { defaultUrl }) }
    val bridgeToken = remember { mutableStateOf(SaleSender.getBridgeToken(context)) }
    var showPassword by remember { mutableStateOf(false) }
    var configured by remember {
        mutableStateOf(
            SaleSender.getBaseUrl(context).isNotBlank() &&
                SaleSender.getBridgeToken(context).isNotBlank()
        )
    }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(configured) {
        onConfiguredChange(configured)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CREDENCIAIS",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (configured) {
                StatusBadge(text = "CONFIGURADA", tone = BadgeTone.Success)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "ENDPOINT",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        BridgeTextField(
            value = baseUrl.value,
            onValueChange = { baseUrl.value = it },
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
            value = bridgeToken.value,
            onValueChange = { bridgeToken.value = it },
            placeholder = "Token do Bridge",
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(
                        text = if (showPassword) "Ocultar" else "Mostrar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        GoldPrimaryButton(
            text = "SALVAR CONFIGURAÇÃO",
            onClick = {
                SaleSender.setBaseUrl(context, baseUrl.value.trim())
                SaleSender.setBridgeToken(context, bridgeToken.value.trim())
                configured = baseUrl.value.trim().isNotBlank() &&
                    bridgeToken.value.trim().isNotBlank()
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (saved) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BridgeStatusDot(color = SuccessGreen, size = 6.dp, glowRadius = 4.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Configuração salva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
