package com.domnex.cfi.bridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.service.SaleSender

@Composable
fun CfiConfigSection() {
    val context = LocalContext.current
    val defaultUrl = "https://xfvdqbuwqzenxdvtiqqd.supabase.co/functions/v1/cfi-bridge-webhook"
    val storedUrl = SaleSender.getBaseUrl(context)
    val baseUrl = remember { mutableStateOf(storedUrl.ifBlank { defaultUrl }) }
    val bridgeToken = remember { mutableStateOf(SaleSender.getBridgeToken(context)) }
    var showPassword by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var configured by remember {
        mutableStateOf(
            SaleSender.getBaseUrl(context).isNotBlank() &&
                SaleSender.getBridgeToken(context).isNotBlank()
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Configuração CFI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (configured) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        contentColor = Color(0xFF4CAF50),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "CFI configurado",
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = baseUrl.value,
                onValueChange = { baseUrl.value = it },
                label = { Text("Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = bridgeToken.value,
                onValueChange = { bridgeToken.value = it },
                label = { Text("Token do Bridge") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Ocultar" else "Mostrar")
                    }
                }
            )

            Button(
                onClick = {
                    SaleSender.setBaseUrl(context, baseUrl.value.trim())
                    SaleSender.setBridgeToken(context, bridgeToken.value.trim())
                    configured =
                        baseUrl.value.trim().isNotBlank() && bridgeToken.value.trim()
                            .isNotBlank()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar configuração")
            }

            if (saved) {
                Text(
                    text = "Configuração salva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
