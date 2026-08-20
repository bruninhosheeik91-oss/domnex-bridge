package com.domnex.cfi.bridge.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.R
import com.domnex.cfi.bridge.model.SaleData
import com.domnex.cfi.bridge.service.SaleSender
import com.domnex.cfi.bridge.service.TonAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val isRunning by TonAccessibilityService.isRunning.collectAsState()
    val lastSale by TonAccessibilityService.lastSale.collectAsState()
    val lastLog by TonAccessibilityService.lastLog.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            StatusChip(isRunning)

            CfiConfigSection()

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_accessibility_settings))
            }

            SaleCard(lastSale)

            LogSection(lastLog)

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusChip(active: Boolean) {
    val color = if (active) Color(0xFF4CAF50) else Color(0xFFF44336)
    val label = if (active) {
        stringResource(R.string.bridge_active)
    } else {
        stringResource(R.string.bridge_inactive)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SaleCard(sale: SaleData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.last_sale_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!sale.hasData) {
                Text(
                    text = stringResource(R.string.no_sale_captured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SaleField("Valor da venda", sale.valorVenda)
                SaleField("Data e hora", sale.dataHora)
                SaleField("Situação", sale.situacao)
                SaleField("Total a receber", sale.totalReceber)
                SaleField("Taxa da venda", sale.taxaVenda)
                SaleField("Forma de pagamento", sale.formaPagamento)
                SaleField("Bandeira", sale.bandeira)
                SaleField("Meio de captura", sale.meioCaptura)
                SaleField("Número de série", sale.numeroSerie)
                SaleField("Código da transação", sale.codigoTransacao)
                SaleField("Código de autorização", sale.codigoAutorizacao)
            }
        }
    }
}

@Composable
private fun SaleField(label: String, value: String) {
    if (value.isNotEmpty()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LogSection(log: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.log_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.ifEmpty { "\u2014" },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CfiConfigSection() {
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
