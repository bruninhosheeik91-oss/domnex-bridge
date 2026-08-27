package com.domnex.cfi.bridge.data

import com.domnex.cfi.bridge.data.local.CapturedSaleEntity
import com.domnex.cfi.bridge.model.SaleData
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/** Filtro por forma de pagamento (dados reais, normalização só para comparar). */
enum class PaymentFilter { ALL, CREDITO, DEBITO, PIX }

/** Período do histórico em dias corridos (inclui hoje). */
enum class PeriodFilter(val days: Int?) {
    TODAY(1),
    DAYS_7(7),
    DAYS_30(30)
}

data class HistoryFilters(
    val payment: PaymentFilter = PaymentFilter.ALL,
    val period: PeriodFilter = PeriodFilter.TODAY,
    val query: String = ""
)

data class TodaySummary(
    val count: Int,
    /** Soma REAL dos valores parseáveis; null quando algum valor não pôde ser interpretado. */
    val totalCentavos: Long?,
    val allValuesParsed: Boolean
)

/**
 * Lógica pura do histórico (testável em JVM, sem Android).
 * NÃO altera SaleData nem a deduplicação existente do SaleSender.
 */
object SaleHistoryLogic {

    private val KEY_CLEAN_REGEX = Regex("[^A-Za-z0-9]")

    // ── Identidade / deduplicação do histórico ────────────────

    /**
     * Preferência: código da transação real.
     * Fallback: fingerprint SHA-256 sobre campos reais disponíveis
     * (somente para o histórico).
     */
    fun identityKey(sale: SaleData): String {
        val tx = sale.codigoTransacao.trim()
        if (tx.isNotEmpty()) {
            return "tx:" + KEY_CLEAN_REGEX.replace(tx, "").uppercase(Locale.ROOT)
        }
        return "fp:" + sha256(
            listOf(
                sale.valorVenda,
                sale.dataHora,
                sale.situacao,
                sale.totalReceber,
                sale.taxaVenda,
                sale.formaPagamento,
                sale.bandeira,
                sale.meioCaptura,
                sale.numeroSerie
            ).joinToString("\u0001") { it.trim() }
        )
    }

    fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ── Valor monetário (parser DEDICADO do histórico) ─────────

    /**
     * Interpreta formatos pt-BR reais ("R$ 1.234,56", "1.234,56", "89,90",
     * "1234"). Retorna null quando não consegue interpretar com segurança —
     * nunca inventa valor nem altera SaleData.
     */
    fun parseValorCentavos(raw: String?): Long? {
        if (raw == null) return null
        var s = raw
            .replace(Regex("(?i)r\\$"), "")
            .replace('\u00A0', ' ')
            .replace(" ", "")
            .trim()
        if (s.isEmpty()) return null
        s = s.replace(".", "")
        if (!Regex("^\\d+(,\\d{1,2})?$").matches(s)) return null
        val parts = s.split(",")
        val reais = parts[0].toLongOrNull() ?: return null
        val centavos = when {
            parts.size == 1 -> 0L
            else -> parts[1].padEnd(2, '0').toLongOrNull() ?: return null
        }
        return reais * 100L + centavos
    }

    /** Formata centavos como "R$ 1.234,56" (sem depender de locale). */
    fun formatCentavos(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val reais = abs / 100L
        val frac = abs % 100L
        var rest = reais
        val groups = mutableListOf<String>()
        do {
            groups.add((rest % 1000L).toString())
            rest /= 1000L
        } while (rest > 0L)
        val reaisText = groups
            .mapIndexed { index, part ->
                if (index == groups.lastIndex) part else part.padStart(3, '0')
            }
            .reversed()
            .joinToString(".")
        val sign = if (negative) "-" else ""
        return "R$ $sign$reaisText,${frac.toString().padStart(2, '0')}"
    }

    // ── Forma de pagamento ────────────────────────────────────

    fun normalizeText(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

    fun paymentCategory(formaPagamento: String): PaymentFilter {
        val normalized = normalizeText(formaPagamento)
        return when {
            normalized.contains("pix") -> PaymentFilter.PIX
            normalized.contains("cred") -> PaymentFilter.CREDITO
            normalized.contains("deb") -> PaymentFilter.DEBITO
            else -> PaymentFilter.ALL
        }
    }

    // ── Período ───────────────────────────────────────────────

    /**
     * Início do dia CALENDÁRIO local do dispositivo (00:00:00.000). É a
     * fronteira exata usada pelo filtro "Hoje" — vendas de dias anteriores
     * ficam abaixo dela e NUNCA entram no período "Hoje".
     */
    fun startOfDayMillis(nowMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun periodCutoff(period: PeriodFilter, nowMillis: Long): Long {
        val startToday = startOfDayMillis(nowMillis)
        val days = period.days ?: return 0L
        return startToday - (days - 1L) * 24L * 60L * 60L * 1000L
    }

    // ── Filtros e busca ───────────────────────────────────────

    fun filter(
        sales: List<CapturedSaleEntity>,
        filters: HistoryFilters,
        nowMillis: Long
    ): List<CapturedSaleEntity> {
        val cutoff = periodCutoff(filters.period, nowMillis)
        val query = normalizeText(filters.query.trim())
        return sales.filter { sale ->
            val periodOk = sale.capturadoEm >= cutoff
            val paymentOk = filters.payment == PaymentFilter.ALL ||
                paymentCategory(sale.formaPagamento) == filters.payment
            val queryOk = query.isEmpty() || run {
                listOf(
                    sale.codigoTransacao,
                    sale.numeroSerie,
                    sale.codigoAutorizacao
                ).any { field -> normalizeText(field).contains(query) }
            }
            periodOk && paymentOk && queryOk
        }
    }

    // ── Resumo (apenas métricas reais) ────────────────────────

    /**
     * Resumo financeiro para um período. Usa EXATAMENTE a mesma fronteira do
     * filtro ([periodCutoff]), garantindo que contagem e valor total correspondam
     * ao período selecionado (Hoje / 7 dias / 30 dias).
     */
    fun periodSummary(
        sales: List<CapturedSaleEntity>,
        period: PeriodFilter,
        nowMillis: Long
    ): TodaySummary {
        val cutoff = periodCutoff(period, nowMillis)
        val inPeriod = sales.filter { it.capturadoEm >= cutoff }
        val parsedValues = inPeriod.mapNotNull { it.valorCentavos }
        val allParsed = parsedValues.size == inPeriod.size
        return TodaySummary(
            count = inPeriod.size,
            totalCentavos = if (allParsed) parsedValues.sum() else null,
            allValuesParsed = allParsed
        )
    }

    /** Resumo de hoje (apenas métricas reais). */
    fun todaySummary(sales: List<CapturedSaleEntity>, nowMillis: Long): TodaySummary =
        periodSummary(sales, PeriodFilter.TODAY, nowMillis)

    // ── Detalhes: 11 campos reais, ocultando vazios ───────────

    fun detailRows(sale: CapturedSaleEntity): List<Pair<String, String>> = buildList {
        addIfPresent("Valor da venda", sale.valorVenda)
        addIfPresent("Data e hora", sale.dataHora)
        addIfPresent("Situação", sale.situacao)
        addIfPresent("Total a receber", sale.totalReceber)
        addIfPresent("Taxa da venda", sale.taxaVenda)
        addIfPresent("Forma de pagamento", sale.formaPagamento)
        addIfPresent("Bandeira", sale.bandeira)
        addIfPresent("Meio de captura", sale.meioCaptura)
        addIfPresent("Número de série", sale.numeroSerie)
        addIfPresent("Código da transação", sale.codigoTransacao)
        addIfPresent("Código de autorização", sale.codigoAutorizacao)
    }

    private fun MutableList<Pair<String, String>>.addIfPresent(label: String, value: String) {
        if (value.isNotBlank()) add(label to value)
    }
}
