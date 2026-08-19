package com.domnex.cfi.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.domnex.cfi.bridge.model.SaleData
import kotlinx.coroutines.flow.MutableStateFlow

class TonAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CFIBridge"
        private const val TON_PACKAGE = "br.com.stone.ton"

        private val FIELD_LABELS = listOf(
            "Valor da venda" to "valorVenda",
            "Data da venda" to "dataHora",
            "Data e hora" to "dataHora",
            "Situação" to "situacao",
            "Status" to "situacao",
            "Total a receber" to "totalReceber",
            "Total receber" to "totalReceber",
            "Taxa da venda" to "taxaVenda",
            "Taxa" to "taxaVenda",
            "Forma de pagamento" to "formaPagamento",
            "Forma pgto" to "formaPagamento",
            "Forma de pgto" to "formaPagamento",
            "Bandeira" to "bandeira",
            "Meio de captura" to "meioCaptura",
            "Tipo de captura" to "meioCaptura",
            "Número de série" to "numeroSerie",
            "N\u00famero de s\u00e9rie" to "numeroSerie",
            "N\u00ba s\u00e9rie" to "numeroSerie",
            "N\u00ba da s\u00e9rie" to "numeroSerie",
            "Código da transação" to "codigoTransacao",
            "C\u00f3digo da transa\u00e7\u00e3o" to "codigoTransacao",
            "Código transação" to "codigoTransacao",
            "C\u00f3d. transa\u00e7\u00e3o" to "codigoTransacao",
            "Código de autorização" to "codigoAutorizacao",
            "C\u00f3digo de autoriza\u00e7\u00e3o" to "codigoAutorizacao",
            "Código autorização" to "codigoAutorizacao",
            "Autorização" to "codigoAutorizacao"
        )

        private val LABEL_LOWER: Set<String> = FIELD_LABELS.map { it.first.lowercase() }.toHashSet()

        private val SKIP_PREFIXES = listOf("jade ")

        private val SECTION_TITLES = setOf(
            "dados da transa\u00e7\u00e3o",
            "detalhes da venda",
            "dados da venda",
            "resumo da venda",
            "informa\u00e7\u00f5es da venda"
        )

        var instance: TonAccessibilityService? = null
            private set

        val isRunning = MutableStateFlow(false)
        val lastSale = MutableStateFlow(SaleData())
        val lastLog = MutableStateFlow("")
    }

    private enum class DetailState {
        IDLE, TOP_CAPTURE_PENDING, TOP_CAPTURED, SCROLLED, COMPLETE
    }

    private var detailState = DetailState.IDLE
    private var detailStateStartTime = 0L
    private var partialSale = SaleData()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning.value = true
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != TON_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                scrapeCurrentScreen()
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        isRunning.value = false
        super.onDestroy()
        Log.i(TAG, "AccessibilityService destroyed")
    }

    // ── Scraping ──────────────────────────────────────────────

    private fun scrapeCurrentScreen() {
        val root = rootInActiveWindow ?: return
        try {
            val allTexts = mutableListOf<String>()
            collectTextsOnly(root, allTexts)

            val isDetailScreen = allTexts.any { t ->
                t.contains("Detalhes da venda", ignoreCase = true)
            }

            if (detailState == DetailState.IDLE) {
                if (!isDetailScreen) return
                detailState = DetailState.TOP_CAPTURE_PENDING
                detailStateStartTime = System.currentTimeMillis()
            }

            val extracted = extractSaleData(allTexts)
            partialSale = mergeData(partialSale, extracted)

            when (detailState) {
                DetailState.TOP_CAPTURE_PENDING -> {
                    val topReady = partialSale.valorVenda.isNotEmpty() &&
                            partialSale.dataHora.isNotEmpty() &&
                            partialSale.situacao.isNotEmpty()
                    val elapsed = System.currentTimeMillis() - detailStateStartTime
                    when {
                        topReady && elapsed >= 800L -> {
                            detailState = DetailState.TOP_CAPTURED
                        }
                        elapsed > 5000L -> {
                            publishAndReset()
                            return
                        }
                    }
                }
                DetailState.TOP_CAPTURED -> {
                    if (performScroll(root)) {
                        detailState = DetailState.SCROLLED
                        detailStateStartTime = System.currentTimeMillis()
                    } else {
                        publishAndReset()
                        return
                    }
                }
                DetailState.SCROLLED -> {
                    val elapsed = System.currentTimeMillis() - detailStateStartTime
                    val bottomReady = partialSale.codigoTransacao.isNotEmpty() &&
                            partialSale.numeroSerie.isNotEmpty()
                    if (bottomReady || elapsed > 3000L) {
                        detailState = DetailState.COMPLETE
                        publishAndReset()
                        return
                    }
                }
                DetailState.COMPLETE -> {
                    publishAndReset()
                    return
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scrape error", e)
            lastLog.value = "Erro na captura: ${e.message}"
        } finally {
            root.recycle()
        }
    }

    private fun publishAndReset() {
        if (partialSale.hasData) {
            lastSale.value = partialSale
            val ts = java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            val tx = partialSale.codigoTransacao.ifEmpty { "N/A" }
            lastLog.value = "Captura OK em $ts \u2014 Tx: $tx"
            Log.i(TAG, "Sale captured: $tx")
        }
        detailState = DetailState.IDLE
        partialSale = SaleData()
    }

    // ── Extraction: flat sequential ───────────────────────────

    private fun extractSaleData(allTexts: List<String>): SaleData {
        val values = mutableMapOf<String, String>()

        for (i in allTexts.indices) {
            val text = allTexts[i]
            val match = FIELD_LABELS.firstOrNull { (label, _) ->
                text.equals(label, ignoreCase = true)
            } ?: continue
            val (_, field) = match
            if (values.containsKey(field)) continue

            for (j in i + 1 until allTexts.size) {
                val candidate = allTexts[j]
                if (!isValidValue(candidate)) continue
                values[field] = candidate
                break
            }
        }

        return SaleData(
            valorVenda = values["valorVenda"] ?: "",
            dataHora = values["dataHora"] ?: "",
            situacao = values["situacao"] ?: "",
            totalReceber = values["totalReceber"] ?: "",
            taxaVenda = values["taxaVenda"] ?: "",
            formaPagamento = values["formaPagamento"] ?: "",
            bandeira = values["bandeira"] ?: "",
            meioCaptura = values["meioCaptura"] ?: "",
            numeroSerie = values["numeroSerie"] ?: "",
            codigoTransacao = values["codigoTransacao"] ?: "",
            codigoAutorizacao = values["codigoAutorizacao"] ?: ""
        )
    }

    private fun isValidValue(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return false
        if (LABEL_LOWER.contains(lower)) return false
        for (prefix in SKIP_PREFIXES) {
            if (lower.startsWith(prefix)) return false
        }
        if (lower in SECTION_TITLES) return false
        return true
    }

    // ── Text collector: text only, strict tree order ──────────

    private fun collectTextsOnly(node: AccessibilityNodeInfo, out: MutableList<String>) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val txt = child.text?.toString()
            if (!txt.isNullOrBlank()) out.add(txt.trim())
            collectTextsOnly(child, out)
            child.recycle()
        }
    }

    // ── Scroll support ────────────────────────────────────────

    private fun performScroll(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val scrolled = performScroll(child)
            child.recycle()
            if (scrolled) return true
        }
        return false
    }

    private fun mergeData(primary: SaleData, secondary: SaleData): SaleData {
        return SaleData(
            valorVenda = primary.valorVenda.ifEmpty { secondary.valorVenda },
            dataHora = primary.dataHora.ifEmpty { secondary.dataHora },
            situacao = primary.situacao.ifEmpty { secondary.situacao },
            totalReceber = primary.totalReceber.ifEmpty { secondary.totalReceber },
            taxaVenda = primary.taxaVenda.ifEmpty { secondary.taxaVenda },
            formaPagamento = primary.formaPagamento.ifEmpty { secondary.formaPagamento },
            bandeira = primary.bandeira.ifEmpty { secondary.bandeira },
            meioCaptura = primary.meioCaptura.ifEmpty { secondary.meioCaptura },
            numeroSerie = primary.numeroSerie.ifEmpty { secondary.numeroSerie },
            codigoTransacao = primary.codigoTransacao.ifEmpty { secondary.codigoTransacao },
            codigoAutorizacao = primary.codigoAutorizacao.ifEmpty { secondary.codigoAutorizacao }
        )
    }
}
