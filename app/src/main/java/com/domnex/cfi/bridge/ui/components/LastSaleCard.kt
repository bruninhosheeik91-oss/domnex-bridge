package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.model.SaleData
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextSecondary

private data class SaleTile(
    val label: String,
    val value: String,
    val valueColor: Color,
    val monospace: Boolean = false
)

@Composable
fun LastSaleCard(
    sale: SaleData,
    modifier: Modifier = Modifier
) {
    BridgeCard(modifier = modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ÚLTIMA VENDA CAPTURADA",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = Gold,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            val paymentLabel = sale.formaPagamento.trim()
            if (paymentLabel.isNotEmpty()) {
                PaymentPill(paymentLabel.uppercase())
            }
        }

        Spacer(Modifier.height(12.dp))
        SaleHeroRow(sale)

        Spacer(Modifier.height(14.dp))

        val tiles = listOf(
            SaleTile("Data e hora", sale.dataHora.trim(), MaterialTheme.colorScheme.onSurface),
            SaleTile("Total a receber", sale.totalReceber.trim(), SuccessGreen, monospace = true),
            SaleTile(
                "Taxa da venda",
                sale.taxaVenda.trim(),
                Color.White.copy(alpha = 0.90f),
                monospace = true
            ),
            SaleTile("Forma de pagamento", sale.formaPagamento.trim(), MaterialTheme.colorScheme.onSurface),
            SaleTile("Bandeira", sale.bandeira.trim(), MaterialTheme.colorScheme.onSurface),
            SaleTile("Meio de captura", sale.meioCaptura.trim(), MaterialTheme.colorScheme.onSurface)
        ).filter { it.value.isNotEmpty() }

        tiles.chunked(2).forEach { pair ->
            val second = pair.getOrNull(1)
            FieldGridTilePair(
                first = {
                    SaleFieldTile(
                        pair[0].label,
                        pair[0].value,
                        valueColor = pair[0].valueColor,
                        monospace = pair[0].monospace
                    )
                },
                second = if (second != null) {
                    {
                        SaleFieldTile(second.label, second.value, valueColor = second.valueColor, monospace = second.monospace)
                    }
                } else {
                    null
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        val serial = sale.numeroSerie.trim()
        if (serial.isNotEmpty()) {
            SaleFieldTile(
                label = "Número de série",
                value = serial,
                valueColor = Color.White.copy(alpha = 0.95f),
                monospace = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        listOfNotNull(
            sale.codigoTransacao.trim().takeIf { it.isNotEmpty() }?.let {
                SaleTile("Código da transação", it, Color.White.copy(alpha = 0.95f), monospace = true)
            },
            sale.codigoAutorizacao.trim().takeIf { it.isNotEmpty() }?.let {
                SaleTile("Código de autorização", it, Color.White.copy(alpha = 0.95f), monospace = true)
            }
        ).chunked(2).forEach { pair ->
            val second = pair.getOrNull(1)
            FieldGridTilePair(
                first = {
                    SaleFieldTile(pair[0].label, pair[0].value, valueColor = pair[0].valueColor, monospace = true)
                },
                second = if (second != null) {
                    {
                        SaleFieldTile(second.label, second.value, valueColor = second.valueColor, monospace = true)
                    }
                } else {
                    null
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SaleHeroRow(sale: SaleData) {
    val amount = sale.valorVenda.trim()
    val situation = sale.situacao.trim()
    if (amount.isEmpty() && situation.isEmpty()) return

    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (amount.isNotEmpty()) {
                Text(
                    text = "VALOR DA VENDA",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = amount,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (situation.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SITUAÇÃO",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(text = situation, tone = situationTone(situation), showDot = true)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

@Composable
private fun PaymentPill(text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private fun situationTone(value: String): BadgeTone {
    val v = value.lowercase()
    return when {
        listOf("negad", "recusad", "falh", "cancelad", "rejeitad").any { v.contains(it) } -> BadgeTone.Failure
        listOf("pendent", "processand", "aguardand").any { v.contains(it) } -> BadgeTone.Warning
        else -> BadgeTone.Success
    }
}
