package ca.schippers.hfm.calc.salestax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.RuleValue
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sales taxes by date and province, from Rates and rules, and the taxes in a total. */
class SalesTaxesTest {

    @AfterTest
    fun reset() {
        Rules.userValues = emptyList()
    }

    private fun rates(y: Int, m: Int, d: Int, p: Province): List<String> =
        SalesTaxes.ratesOn(LocalDate(y, m, d), p).map { "${it.label} ${it.rate.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()}" }

    @Test
    fun `each province's taxes on a given day`() {
        assertEquals(listOf("HST 13"), rates(2026, 3, 1, Province.ON))
        assertEquals(listOf("HST 15"), rates(2025, 3, 31, Province.NS))
        assertEquals(listOf("HST 14"), rates(2025, 4, 1, Province.NS))
        assertEquals(listOf("GST 5", "QST 9.975"), rates(2026, 3, 1, Province.QC))
        assertEquals(listOf("GST 5", "PST 7"), rates(2026, 3, 1, Province.BC))
        assertEquals(listOf("GST 5"), rates(2026, 3, 1, Province.AB))
        assertEquals(listOf("GST 5"), rates(2026, 3, 1, Province.NU))
        assertEquals(listOf("GST 5", "RST 7"), rates(2026, 3, 1, Province.MB))
        assertEquals(listOf("GST 5", "RST 8"), rates(2015, 3, 1, Province.MB))
        assertEquals(listOf("GST 5", "PST 6"), rates(2026, 3, 1, Province.SK))
        assertEquals(listOf("GST 5", "PST 5"), rates(2017, 3, 22, Province.SK))
        assertEquals(listOf("HST 15"), rates(2026, 3, 1, Province.PE))
        assertEquals(listOf("HST 14"), rates(2014, 3, 1, Province.PE))
        assertEquals(listOf("HST 13"), rates(2016, 6, 30, Province.NB))
        assertEquals(listOf("HST 15"), rates(2016, 7, 1, Province.NL))
    }

    @Test
    fun `British Columbia and Ontario before, during and after the HST`() {
        assertEquals(listOf("GST 5", "PST 7"), rates(2010, 6, 30, Province.BC))
        assertEquals(listOf("HST 12"), rates(2010, 7, 1, Province.BC))
        assertEquals(listOf("HST 12"), rates(2013, 3, 31, Province.BC))
        assertEquals(listOf("GST 5", "PST 7"), rates(2013, 4, 1, Province.BC))
        assertEquals(listOf("GST 5", "RST 8"), rates(2010, 6, 30, Province.ON))
        assertEquals(listOf("HST 13"), rates(2010, 7, 1, Province.ON))
    }

    @Test
    fun `Quebec's QST was charged on the GST before 2013`() {
        val y2012 = SalesTaxes.ratesOn(LocalDate(2012, 6, 1), Province.QC)
        assertEquals(BigDecimal("0.095"), y2012[1].rate)
        assertTrue(y2012[1].onGst)
        val taxes = SalesTaxes.taxesOn(BigDecimal("100.00"), y2012)
        assertEquals(listOf(BigDecimal("5.00"), BigDecimal("9.98")), taxes.map { it.amount }, "9.5 % of 105.00")
        val y2013 = SalesTaxes.ratesOn(LocalDate(2013, 1, 1), Province.QC)
        assertEquals(false, y2013[1].onGst)
        assertEquals(listOf(BigDecimal("5.00"), BigDecimal("9.98")), SalesTaxes.taxesOn(BigDecimal("100.00"), y2013).map { it.amount })
    }

    @Test
    fun `the taxes in a total`() {
        val qc = SalesTaxes.ratesOn(LocalDate(2026, 1, 15), Province.QC)
        assertEquals(listOf(BigDecimal("5.00"), BigDecimal("9.98")), SalesTaxes.fromTotal(BigDecimal("114.98"), qc).map { it.amount })
        val on = SalesTaxes.ratesOn(LocalDate(2026, 1, 15), Province.ON)
        assertEquals(listOf(BigDecimal("13.00")), SalesTaxes.fromTotal(BigDecimal("-113.00"), on).map { it.amount }, "the sign is ignored")
        val bc = SalesTaxes.ratesOn(LocalDate(2026, 1, 15), Province.BC)
        assertEquals(listOf(BigDecimal("5.00"), BigDecimal("7.00")), SalesTaxes.fromTotal(BigDecimal("112.00"), bc).map { it.amount })
        assertEquals(emptyList(), SalesTaxes.fromTotal(BigDecimal("10"), emptyList()))
    }

    @Test
    fun `the household's own rate takes the place of the built-in one`() {
        Rules.userValues = listOf(RuleValue("sales.hst", Province.NS, LocalDate(2027, 4, 1), "0.13", builtIn = false, id = "u"))
        assertEquals(listOf("HST 14"), rates(2027, 3, 31, Province.NS))
        assertEquals(listOf("HST 13"), rates(2027, 4, 1, Province.NS))
    }

    @Test
    fun `rates anywhere in Canada on a day`() {
        val all = SalesTaxes.ratesAnywhere(LocalDate(2026, 1, 1))
        assertEquals(setOf(BigDecimal("0.05")), all[SalesTaxKind.GST])
        assertEquals(setOf(BigDecimal("0.13"), BigDecimal("0.14"), BigDecimal("0.15")), all[SalesTaxKind.HST])
        assertEquals(setOf(BigDecimal("0.09975")), all[SalesTaxKind.QST])
        assertEquals(setOf(BigDecimal("0.07"), BigDecimal("0.06")), all[SalesTaxKind.PST])
    }
}
