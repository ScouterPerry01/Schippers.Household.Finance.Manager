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
        val rates = IncomeTax.federalMedical(LocalDate(2025, 1, 1))
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

    private fun TaxEstimate.carry(kind: CarryKind) = carryForwards.first { it.kind == kind }

    @Test
    fun `a student claims tuition to bring the tax to zero, carried amounts first, and transfers up to 5,000`() {
        val inputs = pay("20000") + mapOf(
            TaxInput.TUITION to d("8000"), TaxInput.TUITION_CARRIED to d("3000"), TaxInput.TUITION_TO_TRANSFER to d("5000"),
            TaxInput.TUITION_CARRIED_PROVINCIAL to d("1000"),
        )
        val e = IncomeTax.estimate(2025, Province.ON, inputs, age65 = false)
        // Federal: 20,000 × 14.5 % = 2,900 of tax is 20,000 of credit amounts; less 16,129 + 1,471, 2,400 of tuition is needed.
        assertEquals(d("2400.00"), e.line(TaxPart.FEDERAL, TaxLineKind.TUITION))
        assertEquals(d("0.00"), e.federalTax)
        val fed = e.carry(CarryKind.TUITION_FEDERAL)
        // All from the 3,000 carried; of this year's 8,000 none used, so 5,000 can be transferred; 11,000 − 2,400 − 5,000 left.
        assertEquals(listOf(d("11000.00"), d("2400.00"), d("5000.00"), d("3600.00")), listOf(fed.available, fed.used, fed.transferred, fed.left))
        // Ontario ended the credit for this year's fees, but the 1,000 carried still counts (7,253 would be needed).
        val on = e.carry(CarryKind.TUITION_PROVINCIAL)
        assertEquals(listOf(d("1000.00"), d("1000.00"), d("0.00"), d("0.00")), listOf(on.available, on.used, on.transferred, on.left))

        // The parent who receives it, in British Columbia: 5,000 federal and 5,000 provincial.
        val parent = IncomeTax.estimate(2025, Province.BC, pay("80000") + (TaxInput.TUITION_RECEIVED to d("6000")), age65 = false)
        assertEquals(d("5000.00"), parent.line(TaxPart.FEDERAL, TaxLineKind.TUITION_RECEIVED))
        assertEquals(d("5000.00"), parent.line(TaxPart.PROVINCIAL, TaxLineKind.TUITION_RECEIVED))
    }

    @Test
    fun `a Quebec student uses the 8 percent credit first and transfers the rest of this year's fees`() {
        val inputs = pay("22000") + mapOf(TaxInput.TUITION to d("6000"), TaxInput.TUITION_TO_TRANSFER to d("6000"))
        val e = IncomeTax.estimate(2025, Province.QC, inputs, age65 = false)
        // Federal: 22,000 − 17,600 = 4,400 used; 5,000 − 4,400 = 600 transferred; 1,000 left.
        val fed = e.carry(CarryKind.TUITION_FEDERAL)
        assertEquals(listOf(d("4400.00"), d("600.00"), d("1000.00")), listOf(fed.used, fed.transferred, fed.left))
        // Quebec: 20,680 × 14 % = 2,895.20, less 18,571 × 14 % = 2,599.94; 295.26 at 8 % is 3,690.75 of fees; the other 2,309.25 to a parent.
        val qc = e.carry(CarryKind.TUITION_PROVINCIAL)
        assertEquals(listOf(d("3690.75"), d("2309.25"), d("0.00")), listOf(qc.used, qc.transferred, qc.left))
        assertEquals(d("-295.26"), e.line(TaxPart.PROVINCIAL, TaxLineKind.TUITION))
        assertEquals(d("0.00"), e.provincialTax)
    }

    @Test
    fun `carried losses reduce taxable income, donations are limited to 75 percent, RRSP to its limit`() {
        val inputs = pay("100000") + mapOf(
            TaxInput.TAXABLE_CAPITAL_GAINS to d("4000"), TaxInput.CAPITAL_LOSSES_CARRIED to d("10000"),
            TaxInput.DONATIONS to d("1000"), TaxInput.DONATIONS_CARRIED to d("500"),
            TaxInput.RRSP to d("10000"), TaxInput.RRSP_UNUSED to d("5000"), TaxInput.RRSP_LIMIT to d("12000"),
        )
        val e = IncomeTax.estimate(2025, Province.ON, inputs, age65 = false)
        assertEquals(d("12000.00"), e.line(TaxPart.INCOME, TaxLineKind.RRSP_DEDUCTION))
        assertEquals(d("92000.00"), e.netIncome)
        assertEquals(d("-4000.00"), e.line(TaxPart.INCOME, TaxLineKind.CAPITAL_LOSSES))
        assertEquals(d("88000.00"), e.taxableIncome)
        assertEquals(d("3000.00"), e.carry(CarryKind.RRSP).left)
        assertEquals(d("6000.00"), e.carry(CarryKind.CAPITAL_LOSSES).left)
        // 1,500 of gifts: 200 at 14.5 % and 1,300 at 29 %.
        assertEquals(d("-406.00"), e.line(TaxPart.FEDERAL, TaxLineKind.DONATIONS))
        assertEquals(d("0.00"), e.carry(CarryKind.DONATIONS).left)

        val small = IncomeTax.estimate(2025, Province.ON, pay("2000") + (TaxInput.DONATIONS to d("3000")), age65 = false)
        assertEquals(listOf(d("1500.00"), d("1500.00")), small.carry(CarryKind.DONATIONS).let { listOf(it.used, it.left) })
    }

    @Test
    fun `a year before the first rates has no estimate`() {
        assertFailsWith<RuleException> { IncomeTax.estimate(2023, Province.ON, pay("50000"), age65 = false) }
    }
}
