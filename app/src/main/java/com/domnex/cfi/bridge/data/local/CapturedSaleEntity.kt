package com.domnex.cfi.bridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidade LOCAL do histórico — camada aditiva. NÃO substitui nem altera
 * [com.domnex.cfi.bridge.model.SaleData].
 *
 * Preserva exatamente os 11 campos reais capturados da TON (strings originais,
 * sem normalização que invente dados). Campos vazios permanecem vazios "".
 *
 * statusEnvio NÃO é persistido nesta fase: sent_tx_codes/pending_sales existem,
 * mas o envio é assíncrono e o status muda APÓS a publicação, sem notificação
 * ao histórico — não há vínculo confiável por venda (limitação documentada).
 *
 * valorCentavos: derivado por parser dedicado do histórico para permitir
 * somatório REAL na tela. Null quando o formato não pôde ser interpretado —
 * nunca inventa valor.
 */
@Entity(
    tableName = "captured_sales",
    indices = [Index(value = ["identityKey"], unique = true)]
)
data class CapturedSaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val valorVenda: String = "",
    val dataHora: String = "",
    val situacao: String = "",
    val totalReceber: String = "",
    val taxaVenda: String = "",
    val formaPagamento: String = "",
    val bandeira: String = "",
    val meioCaptura: String = "",
    val numeroSerie: String = "",
    val codigoTransacao: String = "",
    val codigoAutorizacao: String = "",
    val capturadoEm: Long = 0L,
    val criadoEm: Long = 0L,
    /** Chave de deduplicação DO HISTÓRICO (não substitui a dedup do SaleSender). */
    val identityKey: String = "",
    val valorCentavos: Long? = null
)
