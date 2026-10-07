package ca.schippers.hfm.books

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DOC-02: a receipt itemized by hand, its taxes shared as on a receipt split by AI (OCR-03). */
class ItemizeTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val ON = LocalDate(2026, 10, 3)
    private fun item(description: String, amount: String, category: String? = null, vararg taxes: String) = TypedItem(description, BigDecimal(amount), category, taxes.toSet())
    private fun shares(i: Itemized?) = i!!.shares.map { it.share.toBigDecimal().toPlainString() }

    @Test
    fun `the same shares as the AI split for the same receipt`() {
        val typed = listOf(
            item("MILK 2% 4L", "6.49", "food"), item("WHOLE CHICKEN", "17.98", "food"), item("PRODUCE", "42.37", "food"),
            item("PAPER TOWELS", "15.99", "household", "HST"), item("SHAMPOO", "44.70", "care", "HST"),
        )
        val taxes = mapOf("HST" to BigDecimal("7.89"))
        val byHand = Itemizer.itemize(typed, taxes, cad("135.42"), ON)!!
        val byAi = ItemSplitter.split(typed.map { ReceiptLine(it.description, it.amount, it.taxes) }, taxes, cad("135.42"), ON)!!
        assertEquals(byAi.shares, byHand.shares)
        assertEquals(listOf("6.49", "17.98", "42.37", "18.07", "50.51"), shares(byHand))
        assertEquals(SplitNote.NONE, byHand.note)
        assertTrue(byHand.matches)
        assertEquals(cad("127.53"), byHand.itemsTotal)
        assertEquals(cad("7.89"), byHand.taxesTotal)
    }

    @Test
    fun `items of the same category become one split line, noting what it covers`() {
        val typed = listOf(
            item("Lait", "6.49", "food"), item("Papier toilette", "15.49", "household", "GST", "QST"), item("Pain", "3.50", "food"),
        )
        val result = Itemizer.itemize(typed, mapOf("GST" to BigDecimal("0.77"), "QST" to BigDecimal("1.55")), cad("27.80"), ON)!!
        assertEquals(listOf("6.49", "17.81", "3.50"), shares(result))
        val splits = result.splits(fallbackCategory = null, negative = true)
        assertEquals(listOf("food", "household"), splits.map { it.categoryId })
        assertEquals(listOf(cad("-9.99"), cad("-17.81")), splits.map { it.amount })
        assertEquals("Lait, Pain", splits[0].memo)
        assertEquals(cad("-27.80"), splits.map { it.amount }.reduce(Money::plus))
    }

    @Test
    fun `an item without a category takes the transaction's`() {
        val typed = listOf(item("Repas", "40.00", null, "GST", "QST"), item("Vin", "20.00", "wine", "GST", "QST"))
        val result = Itemizer.itemize(typed, mapOf("GST" to BigDecimal("3.00"), "QST" to BigDecimal("5.99")), cad("78.99"), ON)!!
        // The 10.00 tip is the difference, shared over every item as the AI split does.
        assertFalse(result.matches)
        assertEquals(cad("10.00"), result.difference)
        assertEquals(listOf("52.66", "26.33"), shares(result))
        assertEquals(listOf("restaurants", "wine"), result.splits("restaurants", negative = true).map { it.categoryId })
        // Money in (a refund) keeps the sign.
        assertTrue(result.splits(null, negative = false).all { it.amount.isPositive })
    }

    @Test
    fun `with no tax ticked, the taxes are shared over every item in proportion`() {
        val typed = listOf(item("A", "10.00"), item("B", "30.00"))
        val result = Itemizer.itemize(typed, mapOf("HST" to BigDecimal("5.20")), cad("45.20"), ON)!!
        assertEquals(SplitNote.NO_CODES, result.note)
        assertEquals(listOf("11.30", "33.90"), shares(result))
        assertTrue(result.matches)
    }

    @Test
    fun `ticks that cannot account for a tax are not trusted`() {
        // 7.89 is no Canadian rate of 6.49: the ticks are wrong, so the tax goes over everything.
        val typed = listOf(item("Milk", "6.49", null, "HST"), item("Grocery", "112.59"))
        val result = Itemizer.itemize(typed, mapOf("HST" to BigDecimal("7.89")), cad("126.97"), ON)!!
        assertEquals(SplitNote.CODES_DISAGREE, result.note)
        assertEquals(cad("126.97"), result.shares.map { it.share }.reduce(Money::plus))
    }

    @Test
    fun `without taxes there is nothing to note, and the sum still comes to the total to the cent`() {
        val typed = listOf(item("A", "1.00"), item("B", "1.00"), item("C", "1.00"))
        val result = Itemizer.itemize(typed, emptyMap(), cad("10.00"), ON)!!
        assertEquals(SplitNote.NONE, result.note)
        assertEquals(cad("7.00"), result.difference)
        // 3.333... each: the cent left by rounding goes to one line (largest remainders).
        assertEquals(listOf("3.34", "3.33", "3.33"), shares(result))
    }

    @Test
    fun `a single item takes the whole total, and no items or items adding to nothing give no result`() {
        val one = Itemizer.itemize(listOf(item("Bike", "500.00", "sport", "HST")), mapOf("HST" to BigDecimal("65.00")), cad("-565.00"), ON)!!
        assertEquals(listOf("565.00"), shares(one))
        assertTrue(one.matches)
        assertNull(Itemizer.itemize(emptyList(), emptyMap(), cad("10.00"), ON))
        assertNull(Itemizer.itemize(listOf(item("A", "5.00"), item("Coupon", "-5.00")), emptyMap(), cad("0.00"), ON))
    }
}
