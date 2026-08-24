package com.domnex.cfi.bridge.data

import android.content.Context
import com.domnex.cfi.bridge.data.local.CapturedSaleDao
import com.domnex.cfi.bridge.data.local.CapturedSaleEntity
import com.domnex.cfi.bridge.data.local.SaleHistoryDatabase
import com.domnex.cfi.bridge.model.SaleData
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Camada ADITIVA de histórico. Não altera o fluxo do motor:
 * TON → captura → SaleData → envio. Aqui apenas: SaleData → armazenamento.
 *
 * A deduplicação desta camada NÃO substitui a deduplicação existente
 * do SaleSender (sent_tx_codes) nem a knownTxCodes do serviço.
 */
class SaleHistoryRepository(
    private val dao: CapturedSaleDao,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    /** Fluxo observável — a UI reage a novos registros sem polling. */
    fun observeAll(): Flow<List<CapturedSaleEntity>> = dao.observeAll()

    suspend fun findById(id: Long): CapturedSaleEntity? = dao.findById(id)

    /**
     * Persiste uma venda publicada/concluída pelo fluxo atual.
     * Retorna false quando é duplicata (mesmo txCode ou mesmo fingerprint).
     * Falhas nunca propagam para o motor (chamador usa runCatching).
     */
    suspend fun record(sale: SaleData): Boolean {
        val capturedAt = if (sale.capturadoEm > 0L) sale.capturadoEm else nowMillis()
        val createdAt = nowMillis()
        val entity = CapturedSaleEntity(
            valorVenda = sale.valorVenda,
            dataHora = sale.dataHora,
            situacao = sale.situacao,
            totalReceber = sale.totalReceber,
            taxaVenda = sale.taxaVenda,
            formaPagamento = sale.formaPagamento,
            bandeira = sale.bandeira,
            meioCaptura = sale.meioCaptura,
            numeroSerie = sale.numeroSerie,
            codigoTransacao = sale.codigoTransacao,
            codigoAutorizacao = sale.codigoAutorizacao,
            capturadoEm = capturedAt,
            criadoEm = createdAt,
            identityKey = SaleHistoryLogic.identityKey(sale),
            valorCentavos = SaleHistoryLogic.parseValorCentavos(sale.valorVenda)
        )
        if (dao.existsByIdentityKey(entity.identityKey)) return false
        return dao.insert(entity) != -1L
    }

    fun applyFilters(
        sales: List<CapturedSaleEntity>,
        filters: HistoryFilters,
        nowMillis: Long = this.nowMillis()
    ): List<CapturedSaleEntity> = SaleHistoryLogic.filter(sales, filters, nowMillis)

    fun todaySummary(
        sales: List<CapturedSaleEntity>,
        nowMillis: Long = this.nowMillis()
    ): TodaySummary = SaleHistoryLogic.todaySummary(sales, nowMillis)
}

/**
 * Ponto único de acesso da UI/Service ao histórico. O Service usa apenas
 * [recordAsync] — falha de persistência JAMAIS afeta a captura/envio.
 */
object SaleHistory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: SaleHistoryRepository? = null

    fun get(context: Context): SaleHistoryRepository =
        repository ?: synchronized(this) {
            repository ?: create(context).also { repository = it }
        }

    private fun create(context: Context): SaleHistoryRepository {
        val database = Room.databaseBuilder(
            context.applicationContext,
            SaleHistoryDatabase::class.java,
            "domnex_sale_history.db"
        ).build()
        return SaleHistoryRepository(database.capturedSaleDao())
    }

    /** Chamada fire-and-forget a partir do ponto de publicação do motor. */
    fun recordAsync(context: Context, sale: SaleData) {
        scope.launch {
            runCatching { get(context).record(sale) }
        }
    }
}
