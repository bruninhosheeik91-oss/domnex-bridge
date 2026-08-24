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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.data.SaleHistoryLogic
import com.domnex.cfi.bridge.data.local.CapturedSaleEntity
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

/**
 * Detalhes da venda capturada — os 11 campos REAIS quando disponíveis.
 * Campos realmente vazios são ocultos (nunca "null"/"N/A"/"undefined").
 */
@Composable
fun SaleDetailScreen(
    sale: CapturedSaleEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(text = "← Voltar", color = Gold, fontWeight = FontWeight.ExtraBold)
            }
        }

        Text(text = "DETALHES", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Detalhes da venda capturada",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))

        BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            val rows = SaleHistoryLogic.detailRows(sale)
            if (rows.isEmpty()) {
                Text(
                    text = "Sem campos disponíveis nesta captura.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == 0 && sale.valorVenda.isNotBlank()) {
                            Gold
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (index == 0) FontWeight.ExtraBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Dados registrados localmente no momento da captura.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(30.dp))
    }
}
