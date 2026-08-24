package com.domnex.cfi.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.domnex.cfi.bridge.data.SaleHistory
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

        private const val PREFS_NAME = "cfi_bridge_prefs"
        private const val KEY_KNOWN_TX = "known_tx_codes"
        private const val KEY_SEEN_FP = "seen_fingerprints"
        private const val MAX_SEEN_FP = 500
        private const val MAX_KNOWN_TX = 500

        var instance: TonAccessibilityService? = null
            private set

        val isRunning = MutableStateFlow(false)
        val lastSale = MutableStateFlow(SaleData())
        val lastLog = MutableStateFlow("")
        val seenSaleKeys = mutableSetOf<String>()
    }

    private enum class DetailState {
        IDLE, TOP_CAPTURE_PENDING, TOP_CAPTURED, SCROLLED, COMPLETE
    }

    private var detailState = DetailState.IDLE
    private var detailStateStartTime = 0L
    private var partialSale = SaleData()
    private var isProcessingSale = false
    private var processingStartTime = 0L
    private var baselineComplete = false
    private val knownTxCodes = LinkedHashSet<String>()
    private val seenFingerprints = LinkedHashSet<String>()
    private var pendingBack = false
    private var pendingBackTime = 0L

    private val SALE_TIMEOUT_MS = 15000L
    private val REFRESH_AFTER_CYCLES = 4
    private val REFRESH_MIN_INTERVAL_MS = 20000L
    private var pollNoChangeCycles = 0
    private var lastRefreshTime = 0L
    private var refreshLoggedNoAccessible = false

    fun runPollCycle() {
        // Pausa operacional: usuário desligou o monitoramento (nada é processado).
        if (!BridgeMonitor.isActive()) return
        // ── DIAG TEMP ──
        val diagRoot = rootInActiveWindow
        Log.d(
            TAG,
            "[POLL] cycle started state=$detailState proc=$isProcessingSale " +
                    "back=$pendingBack root=${diagRoot != null} pkg=${diagRoot?.packageName ?: "null"}"
        )
        diagRoot?.recycle()
        // ── FIM DIAG ──
        if (isProcessingSale &&
            System.currentTimeMillis() - processingStartTime >= SALE_TIMEOUT_MS
        ) {
            Log.w(TAG, "Timeout processando venda - resetando maquina de estados")
            lastLog.value = "Timeout na captura - retomando monitoramento"
            isProcessingSale = false
            detailState = DetailState.IDLE
            partialSale = SaleData()
            processingStartTime = 0L
            return
        }
        if (detailState != DetailState.IDLE) return
        if (isProcessingSale) return
        if (pendingBack) return
        if (!isForeground()) return

        val root = rootInActiveWindow ?: return
        var shouldRefresh = false
        try {
            val allTexts = mutableListOf<String>()
            collectTextsOnly(root, allTexts)
            val isDetailScreen = allTexts.any { t ->
                t.contains("Detalhes da venda", ignoreCase = true)
            }
            if (isDetailScreen) return

            val fpBefore = seenFingerprints.size
            observeSaleList(root)

            if (seenFingerprints.size > fpBefore) {
                pollNoChangeCycles = 0
            } else {
                pollNoChangeCycles++
                val now = System.currentTimeMillis()
                if (pollNoChangeCycles >= REFRESH_AFTER_CYCLES &&
                    now - lastRefreshTime >= REFRESH_MIN_INTERVAL_MS
                ) {
                    shouldRefresh = true
                    lastRefreshTime = now
                    pollNoChangeCycles = 0
                }
            }
        } catch (_: Exception) {
        } finally {
            recycleTree(root)
            root.recycle()
        }

        if (shouldRefresh) {
            // ── DIAG TEMP ──
            Log.d(TAG, "[POLL] refresh ready")
            // ── FIM DIAG ──
            tryRefreshList()
        }
    }

    private fun tryRefreshList() {
        val root = rootInActiveWindow ?: return
        try {
            var button: AccessibilityNodeInfo? = null
            // ── DIAG TEMP ──
            var via = "nenhum"
            // ── FIM DIAG ──

            val candidates = root.findAccessibilityNodeInfosByViewId(
                "$TON_PACKAGE:id/JadeNavigationBar_SecondaryAction"
            )
            if (candidates != null && candidates.isNotEmpty()) {
                val c = candidates[0]
                if (c.packageName?.toString() == TON_PACKAGE) {
                    button = c
                    via = "viewId"
                }
            }

            if (button == null) {
                button = findRefreshByDfs(root)
                if (button != null) {
                    via = "dfs"
                }
            }

            // ── DIAG TEMP ──
            Log.d(TAG, "[POLL] refresh botao=${button != null} metodo=$via")
            // ── FIM DIAG ──

            if (button == null) {
                if (!refreshLoggedNoAccessible) {
                    Log.i(TAG, "Bot\u00e3o de atualiza\u00e7\u00e3o TON n\u00e3o encontrado")
                    lastLog.value = "Bot\u00e3o de atualiza\u00e7\u00e3o TON n\u00e3o encontrado"
                    refreshLoggedNoAccessible = true
                }
                return
            }

            val clickableTarget = findClickableAncestor(button)

            if (clickableTarget == null) {
                if (!refreshLoggedNoAccessible) {
                    Log.i(TAG, "Bot\u00e3o de atualiza\u00e7\u00e3o TON sem ancestral clic\u00e1vel")
                    lastLog.value = "Bot\u00e3o de atualiza\u00e7\u00e3o TON sem ancestral clic\u00e1vel"
                    refreshLoggedNoAccessible = true
                }
                return
            }

            refreshLoggedNoAccessible = false
            Log.i(TAG, "Atualizando lista TON...")
            lastLog.value = "Atualizando lista TON..."
            // ── DIAG TEMP ──
            val actionResult = clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "[POLL] refresh ACTION_CLICK=$actionResult")
            // ── FIM DIAG ──
        } catch (_: Exception) {
        } finally {
            recycleTree(root)
            root.recycle()
        }
    }

    private fun findRefreshByDfs(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName
        if (viewId != null && viewId.contains("JadeNavigationBar_SecondaryAction", ignoreCase = true)
            && node.packageName?.toString() == TON_PACKAGE
        ) {
            return node
        }
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("refresh") && node.packageName?.toString() == TON_PACKAGE) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findRefreshByDfs(child)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun isForeground(): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val pkg = root.packageName?.toString()
            return pkg == TON_PACKAGE
        } finally {
            root.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        knownTxCodes.addAll(loadStringSet(prefs, KEY_KNOWN_TX))
        seenFingerprints.addAll(loadStringSet(prefs, KEY_SEEN_FP))
        baselineComplete = false
        SaleSender.retryPending(this)
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
        // Pausa operacional: eventos da TON continuam chegando, mas o Bridge
        // não processa nada enquanto estiver pausado pelo usuário.
        if (!BridgeMonitor.isActive()) return

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
                if (pendingBack) {
                    if (!isDetailScreen) {
                        pendingBack = false
                    } else if (System.currentTimeMillis() - pendingBackTime > 5000L) {
                        pendingBack = false
                        Log.w(TAG, "Falha ao retornar para lista")
                        lastLog.value = "Falha ao retornar para lista"
                    }
                    if (pendingBack) return
                }
                if (!isDetailScreen) {
                    observeSaleList(root)
                    return
                }
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
            recycleTree(root)
            root.recycle()
        }
    }

    private fun publishAndReset() {
        val isComplete = partialSale.codigoTransacao.isNotEmpty() &&
                partialSale.numeroSerie.isNotEmpty()
        if (partialSale.hasData) {
            lastSale.value = partialSale
            val tx = partialSale.codigoTransacao.ifEmpty { "N/A" }
            val serial = partialSale.numeroSerie.ifEmpty { "N/A" }
            lastLog.value = "Venda capturada \u2014 Tx: $tx \u2014 Serial: $serial"
            Log.i(TAG, "Venda capturada \u2014 Tx: $tx \u2014 Serial: $serial")
        }
        if (partialSale.codigoTransacao.isNotEmpty()) {
            knownTxCodes.add(partialSale.codigoTransacao)
            saveStringSet(KEY_KNOWN_TX, knownTxCodes, MAX_KNOWN_TX)
        }
        val saleToSend = partialSale
        detailState = DetailState.IDLE
        partialSale = SaleData()
        isProcessingSale = false
        if (isComplete) {
            // Histórico local (camada aditiva) — falha aqui nunca afeta o envio.
            SaleHistory.recordAsync(applicationContext, saleToSend)
            SaleSender.sendSale(this, saleToSend)
            pendingBack = true
            pendingBackTime = System.currentTimeMillis()
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    // ── Sale list observer ────────────────────────────────────

    private fun observeSaleList(root: AccessibilityNodeInfo) {
        val detected = SaleRowDetector.detectSaleRows(AccRowNode(root))
        try {
            if (!baselineComplete) {
                for (row in detected) {
                    seenFingerprints.add(row.fields.fingerprint())
                }
                saveStringSet(KEY_SEEN_FP, seenFingerprints, MAX_SEEN_FP)
                baselineComplete = true
                Log.i(TAG, "Baseline conclu\u00eddo \u2014 ${seenFingerprints.size} vendas conhecidas")
                lastLog.value = "Baseline conclu\u00eddo \u2014 ${seenFingerprints.size} vendas conhecidas"
                return
            }

            for (row in detected) {
                val rowKey = row.fields.fingerprint()
                if (rowKey in seenSaleKeys) continue
                if (rowKey in seenFingerprints) {
                    seenSaleKeys.add(rowKey)
                    continue
                }

                Log.i(TAG, "Nova venda detectada")
                lastLog.value = "Nova venda detectada"

                if (!isProcessingSale && SaleRowDetector.clickRow(row, TON_PACKAGE)) {
                    Log.i(TAG, "Abrindo venda...")
                    lastLog.value = "Abrindo venda..."
                    seenSaleKeys.add(rowKey)
                    seenFingerprints.add(rowKey)
                    saveStringSet(KEY_SEEN_FP, seenFingerprints, MAX_SEEN_FP)
                    isProcessingSale = true
                    processingStartTime = System.currentTimeMillis()
                }
                return
            }
        } finally {
            for (row in detected) {
                (row.node as AccRowNode).recycle()
            }
        }
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
        }
    }

    private fun recycleTree(node: AccessibilityNodeInfo) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            recycleTree(child)
            child.recycle()
        }
    }

    // ── Persistence helpers ──────────────────────────────────

    private fun loadStringSet(prefs: SharedPreferences, key: String): LinkedHashSet<String> {
        val raw = prefs.getString(key, null)
        if (raw.isNullOrEmpty()) return LinkedHashSet()
        return LinkedHashSet(raw.split("\n").filter { it.isNotEmpty() })
    }

    private fun saveStringSet(key: String, set: LinkedHashSet<String>, maxSize: Int) {
        while (set.size > maxSize) {
            set.remove(set.first())
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(key, set.joinToString("\n"))
            .apply()
    }

    // ── Scroll support ────────────────────────────────────────

    private fun performScroll(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val scrolled = performScroll(child)
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

private class AccRowNode(private val node: AccessibilityNodeInfo) : RowNode {
    override val text: String?
        get() = node.text?.toString()
    override val packageName: String?
        get() = node.packageName?.toString()
    override val isClickable: Boolean
        get() = node.isClickable
    override val isScrollable: Boolean
        get() = node.isScrollable
    override val childCount: Int
        get() = node.childCount
    override val parent: RowNode?
        get() = node.parent?.let(::AccRowNode)

    override fun childAt(index: Int): RowNode? =
        node.getChild(index)?.let(::AccRowNode)

    override fun performClick(): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    fun recycle() {
        try {
            node.recycle()
        } catch (_: Exception) {
        }
    }
}
