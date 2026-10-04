package ca.schippers.hfm.books

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** OCR-03: a receipt's amount paid shared over its items, by the receipt's tax codes when it shows them. */
class ItemSplitTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun line(description: String, amount: String, vararg taxes: String, codes: Boolean = true) =
        ReceiptLine(description, BigDecimal(amount), if (codes) taxes.toSet() else null)
    private fun shares(split: ItemSplit?) = split!!.shares.map { it.share.toBigDecimal().toPlainString() }

    @Test
    fun `an Ontario grocery receipt, zero-rated food carries no HST, the taxable items carry it all`() {
        val lines = listOf(
            line("MILK 2% 4L", "6.49"), line("WHOLE CHICKEN", "17.98"), line("PRODUCE", "42.37"),
            line("PAPER TOWELS", "15.99", "HST"), line("SHAMPOO", "44.70", "HST"),
        )
        // 13 % of 60.69 is 7.8897, printed as 7.89.
        val split = ItemSplitter.split(lines, mapOf("HST" to BigDecimal("7.89")), cad("135.42"))
        assertEquals(SplitMethod.TAX_CODES, split!!.method)
        assertEquals(SplitNote.NONE, split.note)
        assertEquals(listOf("6.49", "17.98", "42.37", "18.07", "50.51"), shares(split))
        assertEquals(setOf("HST"), split.shares[3].taxes)
    }

    @Test
    fun `a Quebec receipt, GST and QST on the taxable line only`() {
        val lines = listOf(line("LAIT 2% 4L", "6.49"), line("PAPIER TOILETTE", "15.49", "GST", "QST"))
        val split = ItemSplitter.split(lines, mapOf("GST" to BigDecimal("0.77"), "QST" to BigDecimal("1.55")), cad("24.30"))
        assertEquals(listOf("6.49", "17.81"), shares(split))
    }

    @Test
    fun `a restaurant bill, taxes by their codes, the tip over everything`() {
        val lines = listOf(line("Repas", "40.00", "GST", "QST"), line("Vin", "20.00", "GST", "QST"))
        val split = ItemSplitter.split(lines, mapOf("GST" to BigDecimal("3.00"), "QST" to BigDecimal("5.99")), cad("78.99"))
        assertEquals(SplitMethod.TAX_CODES, split!!.method)
        // Food: 40 + 2.00 GST + 3.99 QST + two thirds of the 10.00 tip.
        assertEquals(listOf("52.66", "26.33"), shares(split))
    }

    @Test
    fun `codes that cannot account for a printed tax are not trusted`() {
        // 7.89 is not 13, 14 or 15 % of 112.59: the codes were misread.
        val lines = listOf(line("MILK", "6.49"), line("GROCERY", "112.59", "HST"))
        val split = ItemSplitter.split(lines, mapOf("HST" to BigDecimal("7.89")), cad("126.97"))
        assertEquals(SplitMethod.PROPORTIONAL, split!!.method)
        assertEquals(SplitNote.CODES_DISAGREE, split.note)
        assertEquals(cad("126.97"), split.shares.map { it.share }.reduce(Money::plus))
    }

    @Test
    fun `without codes every tax is shared over every line`() {
        val lines = listOf(line("A", "10.00", codes = false), line("B", "20.00", codes = false))
        val split = ItemSplitter.split(lines, mapOf("HST" to BigDecimal("3.90")), cad("33.90"))
        assertEquals(SplitNote.NO_CODES, split!!.note)
        assertEquals(listOf("11.30", "22.60"), shares(split))
    }

    @Test
    fun `shares always add up to the amount paid, discounts included`() {
        val amounts = listOf("0.99", "1.01", "33.33", "-2.00", "7.77", "12.34", "0.01")
        for (n in 2..amounts.size) {
            val lines = amounts.take(n).mapIndexed { i, a -> line("item $i", a, *(if (i % 2 == 0) arrayOf("HST") else emptyArray())) }
            for (paid in listOf("50.00", "99.99", "0.37")) {
                val split = ItemSplitter.split(lines, mapOf("HST" to BigDecimal("1.23")), cad(paid)) ?: continue
                assertEquals(cad(paid), split.shares.map { it.share }.reduce(Money::plus), "$n items, $paid paid")
            }
        }
    }

    @Test
    fun `one item, or items adding up to nothing, cannot be split`() {
        assertEquals(null, ItemSplitter.split(listOf(line("A", "5.00")), emptyMap(), cad("5.00")))
        assertEquals(null, ItemSplitter.split(listOf(line("A", "5.00"), line("Return", "-5.00")), emptyMap(), cad("0.00")))
    }
}
