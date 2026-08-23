package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    BridgeCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
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

        val infoTiles = listOf(
            SaleTile("Data e hora", sale.dataHora.trim(), MaterialTheme.colorScheme.onSurface),
            SaleTile("Total a receber", sale.totalReceber.trim(), SuccessGreen, monospace = true),
            SaleTile(
                "Taxa da venda",
                sale.taxaVenda.trim(),
                Color.White.copy(alpha = 0.90f),
                monospace = true
            ),
            SaleTile("Bandeira", sale.bandeira.trim(), MaterialTheme.colorScheme.onSurface),
            SaleTile("Meio de captura", sale.meioCaptura.trim(), MaterialTheme.colorScheme.onSurface)
        ).filter { it.value.isNotEmpty() }

        if (infoTiles.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionCaption("INFORMAÇÕES DA VENDA")
            Spacer(Modifier.height(8.dp))
            infoTiles.chunked(2).forEachIndexed { index, pair ->
                if (index > 0) Spacer(Modifier.height(7.dp))
                TileRow(pair)
            }
        }

        val idTiles = listOfNotNull(
            sale.numeroSerie.trim().takeIf { it.isNotEmpty() }?.let {
                SaleTile("Número de série", it, Color.White.copy(alpha = 0.95f), monospace = true)
            },
            sale.codigoTransacao.trim().takeIf { it.isNotEmpty() }?.let {
                SaleTile("Código da transação", it, Color.White.copy(alpha = 0.95f), monospace = true)
            },
            sale.codigoAutorizacao.trim().takeIf { it.isNotEmpty() }?.let {
                SaleTile("Código de autorização", it, Color.White.copy(alpha = 0.95f), monospace = true)
            }
        )

        if (idTiles.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionCaption("IDENTIFICAÇÃO")
            Spacer(Modifier.height(8.dp))
            val serial = idTiles.firstOrNull { it.label == "Número de série" }
            val rest = idTiles.filter { it.label != "Número de série" }

            if (serial != null) {
                SaleFieldTile(
                    label = serial.label,
                    value = serial.value,
                    valueColor = serial.valueColor,
                    monospace = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            rest.chunked(2).forEach { pair ->
                Spacer(Modifier.height(7.dp))
                TileRow(pair)
            }
        }
    }
}

@Composable
private fun TileRow(pair: List<SaleTile>) {
    val second = pair.getOrNull(1)
    FieldGridTilePair(
        first = {
            SaleFieldTile(pair[0].label, pair[0].value, valueColor = pair[0].valueColor, monospace = pair[0].monospace)
        },
        second = if (second != null) {
            {
                SaleFieldTile(second.label, second.value, valueColor = second.valueColor, monospace = second.monospace)
            }
        } else {
            null
        }
    )
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
        color = TextSecondary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SaleHeroRow(sale: SaleData) {
    val amount = sale.valorVenda.trim()
    val situation = sale.situacao.trim()

    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "VALOR DA VENDA",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            if (amount.isNotEmpty()) {
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
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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
