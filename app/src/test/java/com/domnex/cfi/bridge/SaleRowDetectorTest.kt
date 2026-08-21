package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.service.DetectedSaleRow
import com.domnex.cfi.bridge.service.RowNode
import com.domnex.cfi.bridge.service.SaleRowDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Réplica fiel da estrutura confirmada em ton-lista-formatted.xml:
 * container scrollável -> headers de data + containers clicáveis de venda.
 */
class SaleRowDetectorTest {

    private class FakeNode(
        override val text: String? = null,
        override val isClickable: Boolean = false,
        override val isScrollable: Boolean = false,
        override val packageName: String? = "br.com.stone.ton",
        val id: String = "",
        val children: List<FakeNode> = emptyList()
    ) : RowNode {

        override val childCount: Int get() = children.size
        override fun childAt(index: Int): RowNode? = children.getOrNull(index)
        override fun performClick(): Boolean {
            clicked.set(true)
            return true
        }

        val clicked = AtomicBoolean(false)
        var parentRef: FakeNode? = null
        override val parent: RowNode? get() = parentRef

        init {
            children.forEach { it.parentRef = this }
        }
    }

    private fun saleRow(
        id: String,
        timeCapture: String,
        amount: String = "R$ 1,00",
        fee: String = "- R$ 0,01",
        method: String = "Débito",
        status: String = "Aprovada"
    ): FakeNode = FakeNode(
        isClickable = true,
        id = id,
        children = listOf(
            FakeNode(id = "$id.icon"),
            FakeNode(text = method, id = "$id.method"),
            FakeNode(text = timeCapture, id = "$id.time"),
            FakeNode(text = amount, id = "$id.amount"),
            FakeNode(text = fee, id = "$id.fee"),
            FakeNode(text = status, id = "$id.status"),
            FakeNode(id = "$id.chevron"),
            FakeNode(isClickable = true, id = "$id.button")
        )
    )

    /** Lista com as 3 vendas simultâneas de R$1,00 do incidente + contexto anterior. */
    private fun incidentList(): FakeNode {
        val listScrollable = FakeNode(
            isScrollable = true,
            id = "list",
            children = listOf(
                FakeNode(text = "21 ago. 2026", id = "h21"),
                saleRow("r1140", "11:40 • Tap"),
                saleRow("r1122", "11:22 • Tap"),
                saleRow("r1051", "10:51 • Tap"),
                FakeNode(text = "20 ago. 2026", id = "h20"),
                saleRow("old2350", "23:50 • Tap"),
                FakeNode(
                    isClickable = true,
                    id = "partial",
                    children = listOf(
                        FakeNode(text = "Débito"),
                        FakeNode(text = "R$ 1,00")
                    )
                )
            )
        )
        return FakeNode(
            id = "root",
            children = listOf(
                FakeNode(
                    id = "filterbar",
                    isScrollable = true,
                    children = listOf(FakeNode(text = "7 dias"), FakeNode(text = "Forma de pagamento"))
                ),
                listScrollable
            )
        )
    }

    @Test
    fun `localiza container scrollavel da lista e nao a barra de filtros`() {
        val root = incidentList()
        val container = SaleRowDetector.findListContainer(root)
        assertEquals("list", (container as FakeNode).id)
    }

    @Test
    fun `tres vendas identicas geram fingerprints distintos ligados ao proprio container`() {
        val detected = SaleRowDetector.detectSaleRows(incidentList())

        val day21 = detected.filter { it.fields.sectionDate == "21 ago. 2026" }
        assertEquals(3, day21.size)

        val byTime = day21.associate { it.fields.time to it }
        assertEquals(setOf("11:40", "11:22", "10:51"), byTime.keys)

        val fingerprints = day21.map { it.fields.fingerprint() }
        assertEquals(3, fingerprints.toSet().size)
        assertTrue(fingerprints.all { it.contains("R\$ 1,00") })
        assertTrue(fingerprints.all { it.contains("Débito") })
        assertTrue(fingerprints.all { it.startsWith("21 ago. 2026|") })

        // Cada fingerprint permanece associado ao horário da sua própria row
        assertEquals("11:40", byTime.getValue("11:40").fields.time)
        assertEquals("Tap", byTime.getValue("11:40").fields.captureMethod)
        assertEquals("11:22", byTime.getValue("11:22").fields.time)
        assertEquals("10:51", byTime.getValue("10:51").fields.time)

        // Clique vai exatamente no container que gerou cada fingerprint
        for ((time, row) in byTime) {
            assertTrue(SaleRowDetector.clickRow(row, "br.com.stone.ton"))
            val node = row.node as FakeNode
            assertTrue(node.clicked.get())
            val timeText = node.childAt(2) as FakeNode
            assertTrue("row clicada $node não contém $time", timeText.text!!.startsWith(time))
        }
    }

    @Test
    fun `row parcialmente visivel sem horario e ignorada`() {
        val detected = SaleRowDetector.detectSaleRows(incidentList())
        assertNull(detected.singleOrNull { (it.node as FakeNode).id == "partial" })
        assertFalse(detected.any { it.fields.fee.isEmpty() && it.fields.time.isEmpty() })
    }

    @Test
    fun `segundo ciclo nao produz novas deteccoes para as mesmas rows`() {
        val detected = SaleRowDetector.detectSaleRows(incidentList())
        val seen = detected.map { it.fields.fingerprint() }.toSet()

        val secondPass = SaleRowDetector.detectSaleRows(incidentList())
        val novos = secondPass.filter { it.fields.fingerprint() !in seen }
        assertTrue(novos.isEmpty())
    }

    @Test
    fun `nova venda inserida no topo gera um unico fingerprint novo vinculado a ela`() {
        val before = SaleRowDetector.detectSaleRows(incidentList())
        val seenBefore = before.map { it.fields.fingerprint() }.toSet()

        val afterRoot = incidentList()
        val list = afterRoot.childAt(1) as FakeNode
        val nova = saleRow("r1215", "12:15 • Tap")
        val rebuilt = FakeNode(
            isScrollable = true,
            id = "list",
            children = listOf(FakeNode(text = "21 ago. 2026"), nova) +
                    list.children.drop(1)
        )
        val detectedAfter = SaleRowDetector.detectSaleRows(rebuilt)
        val novos = detectedAfter.filter { it.fields.fingerprint() !in seenBefore }

        assertEquals(1, novos.size)
        assertEquals("12:15", novos[0].fields.time)
        org.junit.Assert.assertSame("fingerprint novo deve estar ligado ao container da row 12:15", nova, novos[0].node)
        assertTrue(SaleRowDetector.clickRow(novos[0], "br.com.stone.ton"))
        assertTrue((novos[0].node as FakeNode).clicked.get())
    }

    @Test
    fun `clique rejeita pacote diferente do TON`() {
        val detected = SaleRowDetector.detectSaleRows(incidentList())
        val row = DetectedSaleRow(detected[0].fields, object : RowNode {
            override val text: String? = null
            override val packageName: String? = "outro.app"
            override val isClickable = true
            override val isScrollable = false
            override val childCount = 0
            override val parent: RowNode? = null
            override fun childAt(index: Int): RowNode? = null
            override fun performClick(): Boolean = true
        })
        assertFalse(SaleRowDetector.clickRow(row, "br.com.stone.ton"))
    }
}
