package com.domnex.cfi.bridge.service

/**
 * Abstração mínima de nÃ³ de acessibilidade, permitindo que toda a lógica de
 * detecção de rows da lista TON rode em JVM pura (testes unitários sem Android).
 */
interface RowNode {
    val text: String?
    val packageName: String?
    val isClickable: Boolean
    val isScrollable: Boolean
    val childCount: Int
    val parent: RowNode?

    fun childAt(index: Int): RowNode?
    fun performClick(): Boolean
}

/**
 * Campos extraídos exclusivamente dos textos descendentes de UM container de row.
 */
data class SaleRowFields(
    val sectionDate: String,
    val time: String,
    val captureMethod: String,
    val amount: String,
    val fee: String,
    val paymentMethod: String,
    val status: String,
    val otherTexts: List<String> = emptyList()
) {
    /** sectionDate|time|amount|fee|paymentMethod|status|captureMethod */
    fun fingerprint(): String =
        listOf(sectionDate, time, amount, fee, paymentMethod, status, captureMethod)
            .joinToString("|") { it.trim() }
}

class DetectedSaleRow(
    val fields: SaleRowFields,
    val node: RowNode
)

object SaleRowDetector {

    private val VALUE_REGEX = Regex("""^R\$\s*\d+[.,]\d{2}$""")
    private val FEE_REGEX = Regex("""^-\s*R\$\s*\d+[.,]\d{2}$""")
    private val TIME_CAPTURE_REGEX = Regex("""^\s*(\d{1,2}:\d{2})\s*•\s*(.*?)\s*$""")
    private val DATE_HEADER_REGEX = Regex("""^\d{1,2}\s+[A-Za-zÀ-ÿ]{2,15}\.?\s+\d{4}$""")

    private val STATUS_WORDS = setOf(
        "aprovada", "autorizada", "pendente", "processando", "cancelada",
        "recusada", "negada", "estornada", "devolvida", "expirada",
        "aguardando", "capturada", "concluída", "concluida", "finalizada"
    )

    /**
     * Percorre a árvore em document order a partir do container scrollável da lista.
     * Mantém currentSectionDate conforme encontra headers ("21 ago. 2026").
     * Cada row detectada carrega o PRÓPRIO nÃ³ clicável que a gerou.
     */
    fun detectSaleRows(root: RowNode): List<DetectedSaleRow> {
        val container = findListContainer(root) ?: return emptyList()
        val rows = mutableListOf<DetectedSaleRow>()
        val state = WalkState()
        walk(container, container, state, rows)
        return rows
    }

    /**
     * Localiza o container scrollável principal da lista: entre os nós scrolláveis,
     * escolhe o único cujo subtree contém horários com "•" (a barra de filtros é
     * scrollável mas não contém rows de venda).
     */
    fun findListContainer(root: RowNode): RowNode? {
        var best: RowNode? = null
        var bestScore = 0

        fun visit(n: RowNode) {
            if (n.isScrollable) {
                val texts = descendantTexts(n)
                var score = 0
                if (texts.any { TIME_CAPTURE_REGEX.containsMatchIn(it) }) score += 100
                score += texts.count { VALUE_REGEX.matches(it.trim()) || FEE_REGEX.matches(it.trim()) }
                if (score > bestScore) {
                    bestScore = score
                    best = n
                }
            }
            for (i in 0 until n.childCount) {
                n.childAt(i)?.let(::visit)
            }
        }

        visit(root)
        return if (bestScore >= 100) best else null
    }

    /**
     * Clique determinístico: valida pacote e clica EXATAMENTE o container da row
     * detectada. Só sobe a ancestrais se o próprio container não for clicável.
     */
    fun clickRow(row: DetectedSaleRow, expectedPackage: String): Boolean {
        var target: RowNode? = row.node
        if (target == null || target.packageName != expectedPackage) return false
        if (!target.isClickable) {
            var p = target.parent
            while (p != null && !p.isClickable) p = p.parent
            target = p ?: return false
            if (target.packageName != expectedPackage) return false
        }
        return target.performClick()
    }

    fun isSaleRowTexts(texts: List<String>): Boolean {
        var hasTime = false
        var hasAmount = false
        for (t in texts) {
            val tt = t.trim()
            if (!hasTime && TIME_CAPTURE_REGEX.containsMatchIn(tt)) hasTime = true
            if (!hasAmount && VALUE_REGEX.matches(tt)) hasAmount = true
            if (hasTime && hasAmount) return true
        }
        return false
    }

    fun parseRow(descendantTexts: List<String>, sectionDate: String): SaleRowFields? {
        var time = ""
        var captureMethod = ""
        var amount = ""
        var fee = ""
        var paymentMethod = ""
        var status = ""
        val others = mutableListOf<String>()

        for (raw in descendantTexts) {
            val t = raw.trim()
            if (t.isEmpty()) continue

            val timeMatch = TIME_CAPTURE_REGEX.matchEntire(t)
            when {
                timeMatch != null && time.isEmpty() -> {
                    time = timeMatch.groupValues[1]
                    captureMethod = timeMatch.groupValues[2]
                }
                fee.isEmpty() && FEE_REGEX.matches(t) -> fee = t
                amount.isEmpty() && VALUE_REGEX.matches(t) -> amount = t
                status.isEmpty() && t.lowercase() in STATUS_WORDS -> status = t
                paymentMethod.isEmpty() && !DATE_HEADER_REGEX.matches(t) -> paymentMethod = t
                else -> others += t
            }
        }

        if (time.isEmpty() || amount.isEmpty()) return null

        return SaleRowFields(
            sectionDate = sectionDate,
            time = time,
            captureMethod = captureMethod,
            amount = amount,
            fee = fee,
            paymentMethod = paymentMethod,
            status = status,
            otherTexts = others
        )
    }

    fun descendantTexts(node: RowNode): List<String> {
        val out = mutableListOf<String>()
        collect(node, out)
        return out
    }

    private fun collect(node: RowNode, out: MutableList<String>) {
        node.text?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in 0 until node.childCount) {
            node.childAt(i)?.let { collect(it, out) }
        }
    }

    private fun walk(
        node: RowNode,
        containerRoot: RowNode,
        state: WalkState,
        out: MutableList<DetectedSaleRow>
    ) {
        node.text?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            if (DATE_HEADER_REGEX.matches(t)) state.sectionDate = t
        }

        if (node !== containerRoot && node.isClickable) {
            val texts = descendantTexts(node)
            if (isSaleRowTexts(texts)) {
                parseRow(texts, state.sectionDate)?.let {
                    out += DetectedSaleRow(it, node)
                }
                return
            }
        }

        for (i in 0 until node.childCount) {
            node.childAt(i)?.let { walk(it, containerRoot, state, out) }
        }
    }

    private class WalkState {
        var sectionDate: String = ""
    }
}
