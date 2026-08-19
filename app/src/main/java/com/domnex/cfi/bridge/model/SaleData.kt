package com.domnex.cfi.bridge.model

data class SaleData(
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
    val capturadoEm: Long = System.currentTimeMillis()
) {
    val hasData: Boolean
        get() = codigoTransacao.isNotEmpty() || valorVenda.isNotEmpty()

    fun prettyPrint(): String = buildString {
        if (valorVenda.isNotEmpty()) appendLine("Valor da venda: $valorVenda")
        if (dataHora.isNotEmpty()) appendLine("Data e hora: $dataHora")
        if (situacao.isNotEmpty()) appendLine("Situação: $situacao")
        if (totalReceber.isNotEmpty()) appendLine("Total a receber: $totalReceber")
        if (taxaVenda.isNotEmpty()) appendLine("Taxa da venda: $taxaVenda")
        if (formaPagamento.isNotEmpty()) appendLine("Forma de pagamento: $formaPagamento")
        if (bandeira.isNotEmpty()) appendLine("Bandeira: $bandeira")
        if (meioCaptura.isNotEmpty()) appendLine("Meio de captura: $meioCaptura")
        if (numeroSerie.isNotEmpty()) appendLine("Número de série: $numeroSerie")
        if (codigoTransacao.isNotEmpty()) appendLine("Código da transação: $codigoTransacao")
        if (codigoAutorizacao.isNotEmpty()) appendLine("Código de autorização: $codigoAutorizacao")
    }
}
