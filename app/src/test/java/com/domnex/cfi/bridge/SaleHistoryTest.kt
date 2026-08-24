package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.data.HistoryFilters
import com.domnex.cfi.bridge.data.PaymentFilter
import com.domnex.cfi.bridge.data.PeriodFilter
import com.domnex.cfi.bridge.data.SaleHistoryLogic
import com.domnex.cfi.bridge.data.SaleHistoryRepository
import com.domnex.cfi.bridge.data.local.CapturedSaleDao
import com.domnex.cfi.bridge.data.local.CapturedSaleEntity
import com.domnex.cfi.bridge.model.SaleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes FASE 8 — histórico real de vendas.
 * O motor de captura (SaleRowDetector/SaleData/SaleSender) permanece intacto;
 * aqui testamos a camada aditiva de persistência e suas regras.
 */
class SaleHistoryTest {

    /** DAO em memória com a mesma semântica do Room (índice único em identityKey). */
    private class InMemoryCapturedSaleDao : CapturedSaleDao {
        private val rows = mutableListOf<CapturedSaleEntity>()
        private val flow = MutableStateFlow<List<CapturedSaleEntity>>(emptyList())

        private fun publish() {
            flow.value = rows.sortedWith(
                compareByDescending<CapturedSaleEntity> { it.capturadoEm }.thenByDescending { it.id }
            )
        }

        override fun observeAll(): Flow<List<CapturedSaleEntity>> = flow

        override suspend fun findById(id: Long): CapturedSaleEntity? =
            rows.firstOrNull { it.id == id }

        override suspend fun existsByIdentityKey(identityKey: String): Boolean =
            rows.any { it.identityKey == identityKey }

        override suspend fun insert(entity: CapturedSaleEntity): Long {
            if (rows.any { it.identityKey == entity.identityKey }) return -1L
            val id = (rows.maxOfOrNull { it.id } ?: 0L) + 1L
            rows.add(entity.copy(id = id))
            publish()
            return id
        }

        /** Simula o armazenamento persistente sobrevivendo a um "reinício". */
        fun snapshot(): List<CapturedSaleEntity> = rows.toList()
    }

    // ── Fábricas de dados reais-like ──────────────────────────

    private fun sale(
        valorVenda: String = "R$ 150,00",
        dataHora: String = "24/08/2026 10:30",
        situacao: String = "Aprovada",
        totalReceber: String = "R$ 148,50",
        taxaVenda: String = "R$ 1,50",
        formaPagamento: String = "Crédito",
        bandeira: String = "Master",
        meioCaptura: String = "Chip sem contato",
        numeroSerie: String = "1401234567",
        codigoTransacao: String = "TX-000111",
        codigoAutorizacao: String = "AUTH-777",
        capturadoEm: Long = 1_000L
    ) = SaleData(
        valorVenda = valorVenda,
        dataHora = dataHora,
        situacao = situacao,
        totalReceber = totalReceber,
        taxaVenda = taxaVenda,
        formaPagamento = formaPagamento,
        bandeira = bandeira,
        meioCaptura = meioCaptura,
        numeroSerie = numeroSerie,
        codigoTransacao = codigoTransacao,
        codigoAutorizacao = codigoAutorizacao,
        capturadoEm = capturadoEm
    )

    private fun repository(
        dao: CapturedSaleDao,
        now: Long = 10_000_000_000L
    ) = SaleHistoryRepository(dao, nowMillis = { now })

    // 1) Inserir venda preserva os campos reais.
    @Test
    fun `inserir venda persiste os onze campos reais`() = runTest {
        val dao = InMemoryCapturedSaleDao()
        val repo = repository(dao)
        val original = sale()

        assertTrue(repo.record(original))

        val stored = dao.findById(1L)
        assertNotNull(stored)
        assertEquals(original.valorVenda, stored!!.valorVenda)
        assertEquals(original.dataHora, stored.dataHora)
        assertEquals(original.situacao, stored.situacao)
        assertEquals(original.totalReceber, stored.totalReceber)
        assertEquals(original.taxaVenda, stored.taxaVenda)
        assertEquals(original.formaPagamento, stored.formaPagamento)
        assertEquals(original.bandeira, stored.bandeira)
        assertEquals(original.meioCaptura, stored.meioCaptura)
        assertEquals(original.numeroSerie, stored.numeroSerie)
        assertEquals(original.codigoTransacao, stored.codigoTransacao)
        assertEquals(original.codigoAutorizacao, stored.codigoAutorizacao)
        assertEquals(original.capturadoEm, stored.capturadoEm)
    }

    // 2) Não duplicar pelo mesmo txCode (mesmo com horário diferente).
    @Test
    fun `nao duplicar venda com o mesmo codigo de transacao`() = runTest {
        val dao = InMemoryCapturedSaleDao()
        val repo = repository(dao)

        assertTrue(repo.record(sale(capturadoEm = 100L)))
        assertFalse(repo.record(sale(capturadoEm = 999_999L)))

        assertEquals(1, dao.observeAll().first().size)
    }

    // 3) Fallback de fingerprint quando txCode está vazio.
    @Test
    fun `fingerprint local evita duplicata quando txCode vazio`() = runTest {
        val dao = InMemoryCapturedSaleDao()
        val repo = repository(dao)

        assertTrue(repo.record(sale(codigoTransacao = "", capturadoEm = 100L)))
        // Mesma venda recapturada (campos idênticos) → bloqueada pelo fingerprint.
        assertFalse(repo.record(sale(codigoTransacao = "", capturadoEm = 200L)))
        // Venda diferente (valor distinto) → aceita.
        assertTrue(
            repo.record(sale(codigoTransacao = "", valorVenda = "R$ 99,90", capturadoEm = 300L))
        )

        assertEquals(2, dao.observeAll().first().size)
    }

    // 4) Listagem em ordem decrescente de captura.
    @Test
    fun `listar em ordem decrescente por capturadoEm`() = runTest {
        val dao = InMemoryCapturedSaleDao()
        val repo = repository(dao)

        repo.record(sale(codigoTransacao = "TX-A", capturadoEm = 300L))
        repo.record(sale(codigoTransacao = "TX-B", capturadoEm = 100L))
        repo.record(sale(codigoTransacao = "TX-C", capturadoEm = 200L))

        val list = dao.observeAll().first()
        assertEquals(listOf("TX-A", "TX-C", "TX-B"), list.map { it.codigoTransacao })
    }

    // 5) Filtro Crédito.
    @Test
    fun `filtro credito retorna apenas vendas de credito`() {
        val sales = listOf(
            entity(formaPagamento = "Crédito"),
            entity(formaPagamento = "Débito"),
            entity(formaPagamento = "PIX")
        )
        val result = SaleHistoryLogic.filter(
            sales,
            HistoryFilters(payment = PaymentFilter.CREDITO),
            nowMillis = NOW
        )
        assertEquals(listOf("Crédito"), result.map { it.formaPagamento })
    }

    // 6) Filtro Débito.
    @Test
    fun `filtro debito retorna apenas vendas de debito`() {
        val sales = listOf(
            entity(formaPagamento = "Cartão Débito"),
            entity(formaPagamento = "Credito"),
            entity(formaPagamento = "PIX")
        )
        val result = SaleHistoryLogic.filter(
            sales,
            HistoryFilters(payment = PaymentFilter.DEBITO),
            nowMillis = NOW
        )
        assertEquals(listOf("Cartão Débito"), result.map { it.formaPagamento })
    }

    // 7) Filtro PIX.
    @Test
    fun `filtro pix retorna apenas vendas pix`() {
        val sales = listOf(
            entity(formaPagamento = "PIX"),
            entity(formaPagamento = "Pix"),
            entity(formaPagamento = "Crédito")
        )
        val result = SaleHistoryLogic.filter(
            sales,
            HistoryFilters(payment = PaymentFilter.PIX),
            nowMillis = NOW
        )
        assertEquals(2, result.size)
        assertTrue(result.all { SaleHistoryLogic.paymentCategory(it.formaPagamento) == PaymentFilter.PIX })
    }

    // 8) Estado vazio quando não há histórico.
    @Test
    fun `estado vazio quando nao ha vendas salvas`() = runTest {
        val repo = repository(InMemoryCapturedSaleDao())
        assertTrue(repo.observeAll().first().isEmpty())
        assertTrue(
            SaleHistoryLogic.filter(emptyList(), HistoryFilters(), nowMillis = NOW).isEmpty()
        )
    }

    // 9) Campos vazios são preservados como vazios (nada inventado) e ocultos nos detalhes.
    @Test
    fun `campos vazios preservados e ocultos nos detalhes`() = runTest {
        val dao = InMemoryCapturedSaleDao()
        val repo = repository(dao)

        repo.record(
            sale(
                totalReceber = "",
                taxaVenda = "",
                bandeira = "",
                codigoAutorizacao = ""
            )
        )

        val stored = dao.findById(1L)
        assertNotNull(stored)
        assertEquals("", stored!!.totalReceber)
        assertEquals("", stored.taxaVenda)
        assertEquals("", stored.bandeira)
        assertEquals("", stored.codigoAutorizacao)

        val labels = SaleHistoryLogic.detailRows(stored).map { it.first }
        assertTrue(labels.contains("Valor da venda"))
        assertFalse(labels.contains("Total a receber"))
        assertFalse(labels.contains("Taxa da venda"))
        assertFalse(labels.contains("Bandeira"))
        assertFalse(labels.contains("Código de autorização"))
        // Nenhum valor exibido contém placeholders proibidos.
        assertTrue(SaleHistoryLogic.detailRows(stored).none { (label, value) ->
            value == "null" || value == "N/A" || value == "undefined"
        })
    }

    // 10) Persistência sobre reinício: nova instância sobre o MESMO armazenamento
    //     enxerga os registros gravados antes do "reinício".
    @Test
    fun `persistencia sobrevive ao reinicio do app`() = runTest {
        val storage = InMemoryCapturedSaleDao()
        val firstSession = repository(storage)
        assertTrue(firstSession.record(sale(codigoTransacao = "TX-RESTART")))

        // "Reinício": novo DAO/repository lendo o mesmo armazenamento físico.
        val restartedStorage = InMemoryCapturedSaleDao().apply {
            storage.snapshot().forEach { insert(it.copy(id = 0)) }
        }
        val secondSession = repository(restartedStorage)

        val list = secondSession.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("TX-RESTART", list.first().codigoTransacao)
        // E a deduplicação continua válida na nova sessão.
        assertFalse(secondSession.record(sale(codigoTransacao = "TX-RESTART")))
    }

    // ── Parser dedicado do histórico ──────────────────────────

    @Test
    fun `parser de valor interpreta formatos pt-br reais`() {
        assertEquals(123456L, SaleHistoryLogic.parseValorCentavos("R$ 1.234,56"))
        assertEquals(8990L, SaleHistoryLogic.parseValorCentavos("89,90"))
        assertEquals(123400L, SaleHistoryLogic.parseValorCentavos("1.234,00"))
        assertEquals(150015L, SaleHistoryLogic.parseValorCentavos("1500,15"))
        assertEquals(700L, SaleHistoryLogic.parseValorCentavos("7"))
        assertNull(SaleHistoryLogic.parseValorCentavos(""))
        assertNull(SaleHistoryLogic.parseValorCentavos("N/A"))
        assertNull(SaleHistoryLogic.parseValorCentavos(null))
        assertNull(SaleHistoryLogic.parseValorCentavos("12,345"))
    }

    @Test
    fun `formatador exibe centavos em pt-br`() {
        assertEquals("R$ 1.234,56", SaleHistoryLogic.formatCentavos(123456L))
        assertEquals("R$ 89,90", SaleHistoryLogic.formatCentavos(8990L))
        assertEquals("R$ 0,07", SaleHistoryLogic.formatCentavos(7L))
        assertEquals("R$ 1.000.000,00", SaleHistoryLogic.formatCentavos(100000000L))
    }

    // ── Resumo de hoje (apenas métricas reais) ────────────────

    @Test
    fun `resumo de hoje soma somente valores interpretaveis`() {
        val startToday = SaleHistoryLogic.startOfDayMillis(NOW)
        val todaySales = listOf(
            entity(valorVenda = "R$ 100,00", capturadoEm = startToday + 1),
            entity(valorVenda = "R$ 50,25", capturadoEm = startToday + 2)
        )
        val yesterday = listOf(entity(valorVenda = "R$ 999,00", capturadoEm = startToday - 86_400_000L))

        val summary = SaleHistoryLogic.todaySummary(todaySales + yesterday, nowMillis = NOW)
        assertEquals(2, summary.count)
        assertEquals(15025L, summary.totalCentavos)
        assertTrue(summary.allValuesParsed)

        // Um valor ilegível → total suprimido (nunca inventado), contagem real mantida.
        val partial = todaySales + entity(valorVenda = "???", capturadoEm = startToday + 3)
        val broken = SaleHistoryLogic.todaySummary(partial, nowMillis = NOW)
        assertEquals(3, broken.count)
        assertNull(broken.totalCentavos)
        assertFalse(broken.allValuesParsed)
    }

    // ── Busca real por transação / serial / autorização ───────

    @Test
    fun `busca localiza por transacao serial ou autorizacao`() {
        val sales = listOf(
            entity(codigoTransacao = "TX-ABC123", numeroSerie = "SER-1", codigoAutorizacao = ""),
            entity(codigoTransacao = "TX-XYZ", numeroSerie = "SER-777", codigoAutorizacao = ""),
            entity(codigoTransacao = "TX-QQQ", numeroSerie = "SER-9", codigoAutorizacao = "AUTH-555")
        )

        fun search(query: String) = SaleHistoryLogic.filter(
            sales, HistoryFilters(query = query), nowMillis = NOW
        ).size

        assertEquals(1, search("abc123"))
        assertEquals(1, search("777"))
        assertEquals(1, search("auth-555"))
        assertEquals(3, search(""))
    }

    // ── Períodos ──────────────────────────────────────────────

    @Test
    fun `periodos hoje sete e trinta dias filtram por capturadoEm`() {
        val startToday = SaleHistoryLogic.startOfDayMillis(NOW)
        val day = 24L * 60L * 60L * 1000L
        val sales = listOf(
            entity(codigoTransacao = "T-HOJE", capturadoEm = startToday + day / 2),
            entity(codigoTransacao = "T-5D", capturadoEm = startToday - 5 * day),
            entity(codigoTransacao = "T-20D", capturadoEm = startToday - 20 * day),
            entity(codigoTransacao = "T-40D", capturadoEm = startToday - 40 * day)
        )

        fun period(period: PeriodFilter) = SaleHistoryLogic.filter(
            sales, HistoryFilters(period = period), nowMillis = NOW
        ).map { it.codigoTransacao }

        assertEquals(listOf("T-HOJE"), period(PeriodFilter.TODAY))
        assertEquals(listOf("T-HOJE", "T-5D"), period(PeriodFilter.DAYS_7))
        assertEquals(listOf("T-HOJE", "T-5D", "T-20D"), period(PeriodFilter.DAYS_30))
    }

    private companion object {
        // Fixo para testes determinísticos (ex.: 2026-08-24T12:00Z).
        const val NOW = 1_788_000_000_000L

        fun entity(
            valorVenda: String = "R$ 10,00",
            formaPagamento: String = "Crédito",
            codigoTransacao: String = "TX",
            numeroSerie: String = "SER",
            codigoAutorizacao: String = "AUTH",
            capturadoEm: Long = NOW - 1_000L
        ) = CapturedSaleEntity(
            valorVenda = valorVenda,
            formaPagamento = formaPagamento,
            codigoTransacao = codigoTransacao,
            numeroSerie = numeroSerie,
            codigoAutorizacao = codigoAutorizacao,
            capturadoEm = capturadoEm,
            criadoEm = capturadoEm,
            identityKey = "tx:$codigoTransacao",
            valorCentavos = SaleHistoryLogic.parseValorCentavos(valorVenda)
        )
    }
}
