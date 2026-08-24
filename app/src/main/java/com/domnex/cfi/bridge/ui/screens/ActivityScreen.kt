package com.domnex.cfi.bridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.data.HistoryFilters
import com.domnex.cfi.bridge.data.PaymentFilter
import com.domnex.cfi.bridge.data.PeriodFilter
import com.domnex.cfi.bridge.data.SaleHistory
import com.domnex.cfi.bridge.data.SaleHistoryLogic
import com.domnex.cfi.bridge.data.TodaySummary
import com.domnex.cfi.bridge.data.local.CapturedSaleEntity
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

/**
 * Tela Atividade / Vendas Capturadas — usa EXCLUSIVAMENTE o histórico real
 * local. Nada fictício; sem vendas salvas mostra estado vazio.
 */
@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SaleHistory.get(context) }
    val allSales by repository.observeAll().collectAsState(initial = emptyList())

    var filters by remember { mutableStateOf(HistoryFilters()) }
    var selectedSaleId by remember { mutableStateOf<Long?>(null) }

    val nowMillis = remember { System.currentTimeMillis() }
    val filtered = remember(allSales, filters) {
        repository.applyFilters(allSales, filters, nowMillis)
    }
    val summary = remember(allSales) { repository.todaySummary(allSales, nowMillis) }

    val selectedId = selectedSaleId
    if (selectedId != null) {
        val selectedSale by produceState<CapturedSaleEntity?>(null, selectedId) {
            value = repository.findById(selectedId)
        }
        val sale = selectedSale
        if (sale != null) {
            SaleDetailScreen(sale = sale, onBack = { selectedSaleId = null })
            return
        }
    }

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

        Text(text = "ATIVIDADE", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Vendas capturadas",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))
        SummaryCard(summary)

        Spacer(Modifier.height(14.dp))
        FiltersCard(
            filters = filters,
            onChange = { filters = it }
        )

        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            EmptyHistoryState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filtered.forEach { sale ->
                    SaleRowItem(sale = sale, onClick = { selectedSaleId = sale.id })
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SummaryCard(summary: TodaySummary) {
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text(
            text = "HOJE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = summary.count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = Gold,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(0.dp))
            Text(
                text = if (summary.count == 1) " venda capturada" else " vendas capturadas",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Total exibido SOMENTE quando todos os valores de hoje puderam ser
        // interpretados com segurança pelo parser dedicado do histórico.
        if (summary.totalCentavos != null && summary.count > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BridgeStatusDot(color = Gold, size = 6.dp, glowRadius = 3.dp)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = "Valor total capturado: ${SaleHistoryLogic.formatCentavos(summary.totalCentavos)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FiltersCard(
    filters: HistoryFilters,
    onChange: (HistoryFilters) -> Unit
) {
    BridgeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "FILTROS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentChip("Todas", filters.payment == PaymentFilter.ALL) {
                onChange(filters.copy(payment = PaymentFilter.ALL))
            }
            PaymentChip("Crédito", filters.payment == PaymentFilter.CREDITO) {
                onChange(filters.copy(payment = PaymentFilter.CREDITO))
            }
            PaymentChip("Débito", filters.payment == PaymentFilter.DEBITO) {
                onChange(filters.copy(payment = PaymentFilter.DEBITO))
            }
            PaymentChip("PIX", filters.payment == PaymentFilter.PIX) {
                onChange(filters.copy(payment = PaymentFilter.PIX))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip("Hoje", filters.period == PeriodFilter.TODAY) {
                onChange(filters.copy(period = PeriodFilter.TODAY))
            }
            PeriodChip("7 dias", filters.period == PeriodFilter.DAYS_7) {
                onChange(filters.copy(period = PeriodFilter.DAYS_7))
            }
            PeriodChip("30 dias", filters.period == PeriodFilter.DAYS_30) {
                onChange(filters.copy(period = PeriodFilter.DAYS_30))
            }
        }
        Spacer(Modifier.height(12.dp))
        BridgeTextField(
            value = filters.query,
            onValueChange = { onChange(filters.copy(query = it)) },
            placeholder = "Buscar por transação, serial ou autorização",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PaymentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            labelColor = TextSecondary,
            selectedContainerColor = Gold.copy(alpha = 0.16f),
            selectedLabelColor = Gold
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)
        )
    )
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PaymentChip(label, selected, onClick)
}

@Composable
private fun SaleRowItem(sale: CapturedSaleEntity, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.04f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sale.valorVenda.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Gold,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                if (sale.formaPagamento.isNotBlank()) {
                    Text(
                        text = sale.formaPagamento,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val subtitleParts = listOf(sale.bandeira, sale.numeroSerie).filter { it.isNotBlank() }
            if (subtitleParts.isNotEmpty() || sale.dataHora.isNotBlank()) {
                Text(
                    text = listOf(sale.dataHora.ifBlank { null }, subtitleParts.joinToString(" · ").ifBlank { null })
                        .filterNotNull()
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nenhuma venda capturada ainda",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "As próximas vendas identificadas pela TON aparecerão aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
