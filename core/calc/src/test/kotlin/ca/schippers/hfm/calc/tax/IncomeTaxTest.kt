package ca.schippers.hfm.calc.tax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.RuleException
import ca.schippers.hfm.calc.rules.RuleValue
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The income tax estimate against hand calculations from the published rates. */
class IncomeTaxTest {

    @AfterTest
    fun reset() {
        Rules.userValues = emptyList()
    }

    private fun d(s: String) = BigDecimal(s)
    private fun pay(amount: String) = mapOf(TaxInput.EMPLOYMENT to d(amount))
    private fun TaxEstimate.line(part: TaxPart, kind: TaxLineKind) = lines.first { it.part == part && it.kind == kind }.amount

    @Test
    fun `every province and territory has rates for 2024 to 2026`() {
        for (year in 2024..2026) for (p in Province.entries) {
            val e = IncomeTax.estimate(year, p, pay("75000"), age65 = true)
            assertTrue(e.federalTax.signum() > 0 && e.provincialTax.signum() > 0, "$p $year")
            assertEquals(year, e.ratesFrom)
        }
    }

    @Test
    fun `Ontario 2025 at 60,000 of pay`() {
        val e = IncomeTax.estimate(2025, Province.ON, pay("60000"), age65 = false)
        // Federal: 57,375 at 14.5 % and 2,625 at 20.5 %, less (16,129 + 1,471) at 14.5 %.
        assertEquals(d("8857.51"), e.line(TaxPart.FEDERAL, TaxLineKind.TAX_ON_INCOME))
        assertEquals(d("-2552.00"), e.line(TaxPart.FEDERAL, TaxLineKind.CREDITS))
        assertEquals(d("6305.51"), e.federalTax)
        // Ontario: 52,886 at 5.05 % and 7,114 at 9.15 %, less 12,747 at 5.05 %, plus the 600 health premium; no surtax.
        assertEquals(d("3321.67"), e.line(TaxPart.PROVINCIAL, TaxLineKind.TAX_ON_INCOME))
        assertEquals(d("600.00"), e.line(TaxPart.PROVINCIAL, TaxLineKind.HEALTH_PREMIUM))
        assertTrue(e.lines.none { it.kind == TaxLineKind.SURTAX })
        assertEquals(d("3277.95"), e.provincialTax)
        assertEquals(d("9583.46"), e.totalTax)
        // An extra $100 is taxed at 20.5 % + 9.15 %.
        assertEquals(d("0.2965"), e.marginalRate)
    }

    @Test
    fun `Ontario surtax and health premium at a high income`() {
        val e = IncomeTax.estimate(2025, Province.ON, pay("250000"), age65 = false)
        val basic = e.line(TaxPart.PROVINCIAL, TaxLineKind.BASIC_TAX)
        val surtax = (basic - d("5710")).multiply(d("0.20")) + (basic - d("7307")).multiply(d("0.36"))
        assertEquals(surtax.setScale(2, java.math.RoundingMode.HALF_UP), e.line(TaxPart.PROVINCIAL, TaxLineKind.SURTAX))
        assertEquals(d("900.00"), e.line(TaxPart.PROVINCIAL, TaxLineKind.HEALTH_PREMIUM))
    }

    @Test
    fun `Quebec 2025, federal abatement and Quebec's own tax`() {
        val e = IncomeTax.estimate(2025, Province.QC, pay("60000"), age65 = false)
        assertEquals(d("-1040.41"), e.line(TaxPart.FEDERAL, TaxLineKind.ABATEMENT))
        assertEquals(d("5265.10"), e.federalTax)
        // Deduction for workers: 6 % of pay, at most 1,420; then 53,255 at 14 % and 5,325 at 19 %, less 18,571 at 14 %.
        assertEquals(d("1420.00"), e.line(TaxPart.PROVINCIAL, TaxLineKind.WORKER_DEDUCTION))
        assertEquals(d("8467.45"), e.line(TaxPart.PROVINCIAL, TaxLineKind.TAX_ON_INCOME))
        assertEquals(d("5867.51"), e.provincialTax)
    }

    @Test
    fun `Quebec medical credit at 20 percent above 3 percent of family income`() {
        val inputs = pay("60000") + mapOf(TaxInput.MEDICAL to d("3000"), TaxInput.SPOUSE_NET_INCOME to d("20000"))
        val e = IncomeTax.estimate(2025, Province.QC, inputs, age65 = false)
        // Family income 58,580 + 20,000 = 78,580; 3 % is 2,357.40; 642.60 at 20 %.
        assertEquals(d("-128.52"), e.lines.first { it.part == TaxPart.PROVINCIAL && it.kind == TaxLineKind.MEDICAL }.amount)
    }

    @Test
    fun `Alberta 2025 starts at 8 percent`() {
        val e = IncomeTax.estimate(2025, Province.AB, pay("60000"), age65 = false)
        assertEquals(d("4800.00"), e.line(TaxPart.PROVINCIAL, TaxLineKind.TAX_ON_INCOME))
        assertEquals(d("3014.16"), e.provincialTax)
    }

    @Test
    fun `Nunavut 2026 at 100,000`() {
        val e = IncomeTax.estimate(2026, Province.NU, pay("100000"), age65 = false)
        assertEquals(d("16696.01"), e.line(TaxPart.FEDERAL, TaxLineKind.TAX_ON_INCOME))
        assertEquals(d("14182.59"), e.federalTax)
        assertEquals(d("4539.61"), e.provincialTax)
    }

    @Test
    fun `CPP, the base part is a credit, the rest a deduction`() {
        val e = IncomeTax.estimate(2025, Province.ON, pay("80000") + (TaxInput.CPP_QPP to d("4430.10")), age65 = false)
        // 4,034.10 × 4.95 / 5.95 = 3,356.10 credited; 678 + 396 deducted.
        assertEquals(d("3356.10"), e.line(TaxPart.FEDERAL, TaxLineKind.CPP))
        assertEquals(d("1074.00"), e.line(TaxPart.INCOME, TaxLineKind.CPP_ENHANCED))
        assertEquals(d("78926.00"), e.netIncome)
    }

    @Test
    fun `donation credit tiers`() {
        val rates = Rules.list("tax.fed.donation", LocalDate(2025, 1, 1))
        assertEquals(d("29.00"), IncomeTax.donationCredit(rates, d("200"), d("50000"), d("253414")))
        assertEquals(d("319.00"), IncomeTax.donationCredit(rates, d("1200"), d("50000"), d("253414")))
        assertEquals(d("339.00"), IncomeTax.donationCredit(rates, d("1200"), d("253914"), d("253414")))
        assertEquals(d("359.00"), IncomeTax.donationCredit(rates, d("1200"), d("263414"), d("253414")))
    }

    @Test
    fun `medical expenses count above the lesser of 3 percent and the fixed amount`() {
        val rates = Rules.list("tax.fed.medical", LocalDate(2025, 1, 1))
        assertEquals(0, d("1200").compareTo(IncomeTax.medical(rates, d("3000"), d("60000"))))
        assertEquals(0, d("166").compareTo(IncomeTax.medical(rates, d("3000"), d("200000"))))
        assertEquals(0, BigDecimal.ZERO.compareTo(IncomeTax.medical(rates, d("1000"), d("60000"))))
    }

    @Test
    fun `the federal basic personal amount falls between the two incomes`() {
        val bpa = Rules.list("tax.fed.bpa", LocalDate(2025, 1, 1))
        assertEquals(0, d("16129").compareTo(IncomeTax.phased(bpa, d("100000"))))
        assertEquals(0, d("15333.5").compareTo(IncomeTax.phased(bpa, d("215648"))))
        assertEquals(0, d("14538").compareTo(IncomeTax.phased(bpa, d("300000"))))
    }

    @Test
    fun `Ontario Health Premium tiers`() {
        val tiers = Rules.list("tax.prov.healthPremium", LocalDate(2025, 1, 1), Province.ON)
        for ((income, premium) in listOf("20000" to "0.00", "22000" to "120.00", "30000" to "300.00", "37000" to "360.00", "48200" to "500.00", "100000" to "750.00", "300000" to "900.00")) {
            assertEquals(d(premium), IncomeTax.healthPremium(tiers, d(income)), income)
        }
    }

    @Test
    fun `age amount, spouse amount, tax paid and the household's own rates`() {
        val base = IncomeTax.estimate(2025, Province.ON, pay("40000") + (TaxInput.TAX_DEDUCTED to d("6000")), age65 = false)
        val senior = IncomeTax.estimate(2025, Province.ON, pay("40000") + (TaxInput.TAX_DEDUCTED to d("6000")), age65 = true)
        assertEquals(d("9028.00"), senior.line(TaxPart.FEDERAL, TaxLineKind.AGE))
        assertTrue(senior.totalTax < base.totalTax)
        assertEquals(base.totalTax - d("6000.00"), base.balance)
        val spouse = IncomeTax.estimate(2025, Province.ON, pay("40000") + (TaxInput.SPOUSE_NET_INCOME to d("5000")), age65 = false)
        assertEquals(d("11129.00"), spouse.line(TaxPart.FEDERAL, TaxLineKind.SPOUSE))
        assertEquals(d("6905.00"), spouse.line(TaxPart.PROVINCIAL, TaxLineKind.SPOUSE))

        Rules.userValues = listOf(RuleValue("tax.prov.creditRate", Province.ON, LocalDate(2025, 1, 1), "0.06", builtIn = false, id = "u1"))
        val changed = IncomeTax.estimate(2025, Province.ON, pay("40000"), age65 = false)
        assertEquals(d("0.06"), changed.lines.first { it.part == TaxPart.PROVINCIAL && it.kind == TaxLineKind.CREDITS }.rate)
    }

    @Test
    fun `a year before the first rates has no estimate`() {
        assertFailsWith<RuleException> { IncomeTax.estimate(2023, Province.ON, pay("50000"), age65 = false) }
    }
}
