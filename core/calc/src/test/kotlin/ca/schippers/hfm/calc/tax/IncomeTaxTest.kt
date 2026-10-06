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
    fun `Canada workers benefit for a low-income single person, reduced above the threshold`() {
        val low = IncomeTax.estimate(2025, Province.ON, pay("15000") + (TaxInput.TAX_DEDUCTED to d("500")), age65 = false)
        // 27 % of 12,000 above 3,000 is 3,240: the maximum of 1,633; net income below 26,855.
        assertEquals(d("1633.00"), low.line(TaxPart.REFUNDABLE, TaxLineKind.CWB))
        assertEquals(d("1633.00"), low.refundable)
        assertEquals(low.totalTax - d("1633.00") - d("500.00"), low.balance)
        val higher = IncomeTax.estimate(2025, Province.ON, pay("30000"), age65 = false)
        // Less 15 % of 30,000 − 26,855 = 471.75.
        assertEquals(d("-471.75"), higher.line(TaxPart.REFUNDABLE, TaxLineKind.INCOME_REDUCTION))
        assertEquals(d("1161.25"), higher.line(TaxPart.REFUNDABLE, TaxLineKind.CWB))
    }

    @Test
    fun `refundable medical expense supplement`() {
        val e = IncomeTax.estimate(2025, Province.ON, pay("20000") + (TaxInput.MEDICAL to d("3000")), age65 = false)
        // 3,000 above 3 % of 20,000 = 2,400 claimed; 25 % is 600.
        assertEquals(d("600.00"), e.line(TaxPart.REFUNDABLE, TaxLineKind.MEDICAL_SUPPLEMENT))
        assertEquals(d("2233.00"), e.refundable)
    }

    @Test
    fun `Quebec workers benefit and work premium for a single person`() {
        val e = IncomeTax.estimate(2025, Province.QC, pay("12000"), age65 = false)
        // Quebec's CWB: 37.3 % of 12,000 − 2,400. Work premium: 11.6 % of 12,000 − 2,400; Quebec net income 11,280 is below 12,620.
        assertEquals(d("3580.80"), e.line(TaxPart.REFUNDABLE, TaxLineKind.CWB))
        assertEquals(d("1113.60"), e.line(TaxPart.REFUNDABLE, TaxLineKind.WORK_PREMIUM))
        assertEquals(d("4694.40"), e.refundable)
    }

    @Test
    fun `GST credit and child benefit are shown apart from the balance`() {
        val inputs = pay("40000") + mapOf(TaxInput.CHILDREN to d("2"), TaxInput.CHILDREN_UNDER_6 to d("1"))
        val e = IncomeTax.estimate(2025, Province.ON, inputs, age65 = false)
        // 445 + 2 × 234 + (445 − 234) for the first child of a single parent + the 234 supplement; below 46,432.
        assertEquals(d("1358.00"), e.line(TaxPart.BENEFITS, TaxLineKind.GST_CREDIT))
        // 8,157 + 6,883, less 13.5 % of 40,000 − 38,237.
        assertEquals(d("14801.99"), e.line(TaxPart.BENEFITS, TaxLineKind.CHILD_BENEFIT))
        // A single parent gets the family workers benefit: 2,813 less 15 % of 40,000 − 30,639.
        assertEquals(d("1408.85"), e.line(TaxPart.REFUNDABLE, TaxLineKind.CWB))
        assertEquals(e.totalTax - e.refundable - e.paid, e.balance)
    }

    @Test
    fun `OAS recovery tax at a high income, capped at the OAS received`() {
        val e = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.OAS to d("8800"), TaxInput.PENSION to d("120000")), age65 = true)
        // 15 % of 128,800 − 93,454; deducted from net income and added to the balance.
        assertEquals(d("5301.90"), e.line(TaxPart.OTHER, TaxLineKind.OAS_RECOVERY))
        assertEquals(d("-5301.90"), e.line(TaxPart.INCOME, TaxLineKind.OAS_DEDUCTION))
        assertEquals(d("123498.10"), e.netIncome)
        assertEquals(e.totalTax + d("5301.90"), e.balance)
        val richer = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.OAS to d("8800"), TaxInput.PENSION to d("200000")), age65 = true)
        assertEquals(d("8800.00"), richer.other)
    }

    @Test
    fun `alternative minimum tax on a large capital gain, and its recovery in a later year`() {
        val gain = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.TAXABLE_CAPITAL_GAINS to d("300000")), age65 = false)
        // Regular: 73,773.24 on 300,000, less 14,538 × 14.5 % = 71,665.23. Minimum: 20.5 % of 600,000 − 177,882,
        // less half of 2,108.01 = 85,480.19.
        assertEquals(d("71665.23"), gain.line(TaxPart.FEDERAL, TaxLineKind.BASIC_TAX))
        assertEquals(d("85480.19"), gain.line(TaxPart.FEDERAL, TaxLineKind.MINIMUM_TAX))
        assertEquals(d("13814.96"), gain.line(TaxPart.FEDERAL, TaxLineKind.AMT_ADDITIONAL))
        assertEquals(d("85480.19"), gain.federalTax)
        // Ontario adds 24.63 % of the federal additional tax.
        assertEquals(d("3402.62"), gain.line(TaxPart.PROVINCIAL, TaxLineKind.AMT_ADDITIONAL))
        assertEquals(d("13814.96"), gain.carry(CarryKind.MINIMUM_TAX).left)
        // Without a large gain there is no minimum tax line.
        assertTrue(IncomeTax.estimate(2025, Province.ON, pay("100000"), age65 = false).lines.none { it.kind == TaxLineKind.MINIMUM_TAX })

        // A later year with regular tax: the carryover brings the federal tax down to the minimum (zero here).
        val later = IncomeTax.estimate(2025, Province.ON, pay("100000") + (TaxInput.AMT_CARRIED to d("13814.96")), age65 = false)
        assertEquals(d("-13814.96"), later.line(TaxPart.FEDERAL, TaxLineKind.AMT_CARRYOVER))
        assertEquals(d("690.55"), later.federalTax)
        assertEquals(d("-3402.62"), later.line(TaxPart.PROVINCIAL, TaxLineKind.AMT_CARRYOVER))
        assertEquals(d("0.00"), later.carry(CarryKind.MINIMUM_TAX).left)
    }

    @Test
    fun `Quebec minimum tax at 19 percent`() {
        val e = IncomeTax.estimate(2025, Province.QC, mapOf(TaxInput.TAXABLE_CAPITAL_GAINS to d("300000")), age65 = false)
        // 19 % of 600,000 − 179,990, less half of 18,571 × 14 %; the regular Quebec tax is 64,394.74.
        assertEquals(d("64394.74"), e.line(TaxPart.PROVINCIAL, TaxLineKind.BASIC_TAX))
        assertEquals(d("78501.93"), e.provincialTax)
    }

    @Test
    fun `a Quebec retiree pays the health services fund contribution and the drug insurance premium`() {
        val inputs = mapOf(TaxInput.PENSION to d("70000"), TaxInput.OAS to d("8000"), TaxInput.INTEREST to d("10000"))
        val e = IncomeTax.estimate(2025, Province.QC, inputs, age65 = true)
        // Health services fund: 88,000 less the OAS is 80,000, above 63,060: 150 + 1 % of 16,940.
        assertEquals(d("319.40"), e.line(TaxPart.OTHER, TaxLineKind.HEALTH_FUND))
        // Drug insurance: 88,000 is far above the 19,890 exemption: the year's maximum, 755, for 12 months.
        assertEquals(d("755.00"), e.line(TaxPart.OTHER, TaxLineKind.DRUG_PREMIUM))
        assertEquals(d("1074.40"), e.other)
        // Covered by a group plan half the year: half the premium.
        val half = IncomeTax.estimate(2025, Province.QC, inputs + (TaxInput.DRUG_PLAN_MONTHS to d("6")), age65 = true)
        assertEquals(d("377.50"), half.line(TaxPart.OTHER, TaxLineKind.DRUG_PREMIUM))
        assertTrue(IncomeTax.estimate(2025, Province.QC, inputs + (TaxInput.DRUG_PLAN_MONTHS to d("0")), age65 = true).lines.none { it.kind == TaxLineKind.DRUG_PREMIUM })
    }

    @Test
    fun `the drug insurance premium at a low income, and no Quebec contributions outside Quebec`() {
        // Pay of 26,000: the deduction for workers (1,420) leaves 24,580; 4,690 above 19,890 at 7.84 % = 367.70.
        // Employment income gives no health services fund contribution.
        val e = IncomeTax.estimate(2025, Province.QC, pay("26000"), age65 = false)
        assertEquals(d("367.70"), e.line(TaxPart.OTHER, TaxLineKind.DRUG_PREMIUM))
        assertTrue(e.lines.none { it.kind == TaxLineKind.HEALTH_FUND })
        // Self-employment of 20,000: 1 % of 20,000 − 18,130.
        val self = IncomeTax.estimate(2025, Province.QC, mapOf(TaxInput.BUSINESS to d("20000")), age65 = false)
        assertEquals(d("18.70"), self.line(TaxPart.OTHER, TaxLineKind.HEALTH_FUND))
        assertTrue(IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.BUSINESS to d("90000")), age65 = false).lines.none { it.part == TaxPart.OTHER })
    }

    private val disabled = TaxInput.DISABILITY to BigDecimal.ONE

    @Test
    fun `the disability amount, federal, provincial and Quebec`() {
        val base = IncomeTax.estimate(2025, Province.ON, pay("60000"), age65 = false)
        val e = IncomeTax.estimate(2025, Province.ON, pay("60000") + disabled, age65 = false)
        // TD1 2025: 10,138 federal, 10,298 Ontario. Federal credits (16,129 + 1,471 + 10,138) × 14.5 % = 4,022.01, 1,470.01 more;
        // Ontario (12,747 + 10,298) × 5.05 % = 1,163.77, 520.05 more.
        assertEquals(d("10138.00"), e.line(TaxPart.FEDERAL, TaxLineKind.DISABILITY))
        assertEquals(d("10298.00"), e.line(TaxPart.PROVINCIAL, TaxLineKind.DISABILITY))
        assertEquals(d("1470.01"), base.federalTax - e.federalTax)
        assertEquals(d("520.05"), base.provincialTax - e.provincialTax)
        // Quebec: the amount for a severe and prolonged impairment, 4,123 in 2025 (line 376).
        val qc = IncomeTax.estimate(2025, Province.QC, pay("60000") + disabled, age65 = false)
        assertEquals(d("4123.00"), qc.line(TaxPart.PROVINCIAL, TaxLineKind.DISABILITY))
        assertTrue(base.lines.none { it.kind == TaxLineKind.DISABILITY })
    }

    @Test
    fun `the workers benefit disability supplement, phased in on own working income and reduced on family income`() {
        // Schedule 6 2025: 27 % of 15,000 − 1,150 is more than the 843 maximum; net income below 37,740.
        val low = IncomeTax.estimate(2025, Province.ON, pay("15000") + disabled, age65 = false)
        assertEquals(d("843.00"), low.line(TaxPart.REFUNDABLE, TaxLineKind.CWB_DISABILITY))
        assertEquals(d("2476.00"), low.refundable)
        assertTrue(IncomeTax.estimate(2025, Province.ON, pay("15000"), age65 = false).lines.none { it.kind == TaxLineKind.CWB_DISABILITY })
        // At 40,000 the basic benefit is gone; the supplement loses 15 % of 40,000 − 37,740 = 339.
        val single = IncomeTax.estimate(2025, Province.ON, pay("40000") + disabled, age65 = false)
        assertEquals(d("504.00"), single.line(TaxPart.REFUNDABLE, TaxLineKind.CWB_DISABILITY))
        // A couple: family net income 70,000 less the secondary earner's 16,386 is 53,614, 4,225 above 49,389.
        val couple = pay("40000") + disabled + mapOf(TaxInput.SPOUSE_NET_INCOME to d("30000"), TaxInput.SPOUSE_WORKING_INCOME to d("30000"))
        assertEquals(d("209.25"), IncomeTax.estimate(2025, Province.ON, couple, age65 = false).line(TaxPart.REFUNDABLE, TaxLineKind.CWB_DISABILITY))
        // Both spouses eligible: reduced at 7.5 %, 316.88.
        val both = IncomeTax.estimate(2025, Province.ON, couple + (TaxInput.SPOUSE_DISABILITY to BigDecimal.ONE), age65 = false)
        assertEquals(d("526.12"), both.line(TaxPart.REFUNDABLE, TaxLineKind.CWB_DISABILITY))
    }

    @Test
    fun `Quebec's own disability supplement`() {
        // 5005-S6 2025: 40 % without a spouse, maximum 851.31; a single person's reduction starts at 33,230.35.
        val single = IncomeTax.estimate(2025, Province.QC, pay("20000") + disabled, age65 = false)
        assertEquals(d("851.31"), single.line(TaxPart.REFUNDABLE, TaxLineKind.CWB_DISABILITY))
        // With a dependant and no spouse it starts at 24,561.56: 20 % of 1,438.44 = 287.69 off.
        val parent = IncomeTax.estimate(2025, Province.QC, pay("26000") + disabled + (TaxInput.CHILDREN to d("1")), age65 = false)
        assertEquals(d("563.62"), parent.line(TaxPart.REFUNDABLE, TaxLineKind.CWB_DISABILITY))
    }

    @Test
    fun `minimum tax counts 30 percent of the gains on donated listed securities`() {
        val e = IncomeTax.estimate(2025, Province.ON, pay("100000") + (TaxInput.DONATED_SECURITIES_GAINS to d("1000000")), age65 = false)
        // Not in income; for the minimum tax 300,000 is added (T691 line 26): 20.5 % of 400,000 − 177,882, less half of 2,552.
        assertEquals(d("100000.00"), e.totalIncome)
        assertEquals(d("400000.00"), e.line(TaxPart.FEDERAL, TaxLineKind.ADJUSTED_TAXABLE_INCOME))
        assertEquals(d("44258.19"), e.line(TaxPart.FEDERAL, TaxLineKind.MINIMUM_TAX))
        assertEquals(d("44258.19"), e.federalTax)
    }

    @Test
    fun `the capital gains deduction lowers taxable income and 30 percent of the gains count for the minimum tax`() {
        val inputs = mapOf(TaxInput.TAXABLE_CAPITAL_GAINS to d("500000"), TaxInput.CAPITAL_GAINS_DEDUCTION to d("500000"))
        val e = IncomeTax.estimate(2025, Province.ON, inputs, age65 = false)
        assertEquals(d("-500000.00"), e.line(TaxPart.INCOME, TaxLineKind.CAPITAL_GAINS_DEDUCTION))
        assertEquals(d("0.00"), e.taxableIncome)
        // 500,000 (the other half of the gain) less 40 % of the deduction (T691 line 87): 300,000. 20.5 % of 300,000 − 177,882,
        // less half of 14,538 × 14.5 % = 23,980.19; Ontario adds 24.63 % of it.
        assertEquals(d("300000.00"), e.line(TaxPart.FEDERAL, TaxLineKind.ADJUSTED_TAXABLE_INCOME))
        assertEquals(d("23980.19"), e.federalTax)
        assertEquals(d("5906.32"), e.line(TaxPart.PROVINCIAL, TaxLineKind.AMT_ADDITIONAL))
        // The deduction is at most the taxable capital gains.
        val more = IncomeTax.estimate(2025, Province.ON, pay("50000") + inputs + (TaxInput.CAPITAL_GAINS_DEDUCTION to d("600000")), age65 = false)
        assertEquals(d("50000.00"), more.taxableIncome)
    }

    @Test
    fun `security options deduction, added back for the minimum tax except 70 percent of donated option shares`() {
        val inputs = mapOf(
            TaxInput.EMPLOYMENT to d("1000000"),
            TaxInput.SECURITY_OPTIONS_DEDUCTION to d("1000000"),
            TaxInput.SECURITY_OPTIONS_GIFTS to d("500000"),
        )
        val e = IncomeTax.estimate(2025, Province.ON, inputs, age65 = false)
        assertEquals(d("-1000000.00"), e.line(TaxPart.INCOME, TaxLineKind.SECURITY_OPTIONS))
        assertEquals(d("0.00"), e.taxableIncome)
        // T691 lines 28 to 32: 500,000 less 40 % of the 500,000 for donated shares = 300,000, 30 % of the benefit.
        assertEquals(d("300000.00"), e.line(TaxPart.FEDERAL, TaxLineKind.ADJUSTED_TAXABLE_INCOME))
        // 25,034.19 less half of (14,538 + 1,471) × 14.5 %.
        assertEquals(d("23873.54"), e.federalTax)
    }

    @Test
    fun `Quebec's own security option deduction when entered`() {
        val inputs = pay("200000") + (TaxInput.SECURITY_OPTIONS_DEDUCTION to d("50000"))
        val same = IncomeTax.estimate(2025, Province.QC, inputs, age65 = false)
        assertEquals(d("150000.00"), same.taxableIncome)
        // Quebec: 200,000 less the deduction for workers (1,420) and the same 50,000.
        assertEquals(d("148580.00"), same.line(TaxPart.PROVINCIAL, TaxLineKind.TAXABLE_INCOME))
        val own = IncomeTax.estimate(2025, Province.QC, inputs + (TaxInput.SECURITY_OPTIONS_DEDUCTION_QC to d("25000")), age65 = false)
        assertEquals(d("173580.00"), own.line(TaxPart.PROVINCIAL, TaxLineKind.TAXABLE_INCOME))
    }

    @Test
    fun `Quebec minimum tax carried forward, recovered against the Quebec tax`() {
        val gain = IncomeTax.estimate(2025, Province.QC, mapOf(TaxInput.TAXABLE_CAPITAL_GAINS to d("300000")), age65 = false)
        assertEquals(d("14107.19"), gain.carry(CarryKind.MINIMUM_TAX_QC).left)

        val base = IncomeTax.estimate(2025, Province.QC, pay("100000"), age65 = false)
        val later = IncomeTax.estimate(2025, Province.QC, pay("100000") + (TaxInput.AMT_CARRIED_QC to d("5000")), age65 = false)
        assertEquals(d("-5000.00"), later.line(TaxPart.PROVINCIAL, TaxLineKind.AMT_CARRYOVER))
        assertEquals(base.provincialTax - d("5000.00"), later.provincialTax)
        assertEquals(d("0.00"), later.carry(CarryKind.MINIMUM_TAX_QC).left)
        // At most the Quebec tax (the minimum tax is zero here); the rest stays for later years.
        val big = IncomeTax.estimate(2025, Province.QC, pay("100000") + (TaxInput.AMT_CARRIED_QC to d("100000")), age65 = false)
        assertEquals(d("0.00"), big.provincialTax)
        assertEquals(d("100000.00") - base.provincialTax, big.carry(CarryKind.MINIMUM_TAX_QC).left)
        // Not on a return outside Quebec.
        assertTrue(IncomeTax.estimate(2025, Province.ON, pay("100000") + (TaxInput.AMT_CARRIED_QC to d("5000")), age65 = false).carryForwards.isEmpty())
    }

    @Test
    fun `the health services fund leaves out income not subject to it and takes off its deductions`() {
        val inputs = mapOf(TaxInput.PENSION to d("70000"), TaxInput.OAS to d("8000"), TaxInput.INTEREST to d("10000"))
        // Schedule F: 80,000, less 10,000 of income not subject (line 34) and 5,000 of deductions (line 68) = 65,000:
        // 150 + 1 % of 65,000 − 63,060.
        val e = IncomeTax.estimate(2025, Province.QC, inputs + mapOf(TaxInput.FSS_EXEMPT_INCOME to d("10000"), TaxInput.FSS_DEDUCTIONS to d("5000")), age65 = true)
        assertEquals(d("169.40"), e.line(TaxPart.OTHER, TaxLineKind.HEALTH_FUND))
        // They are parts of other figures: total income does not change.
        assertEquals(d("88000.00"), e.totalIncome)
    }

    @Test
    fun `2026 figures read in the provincial and territorial acts and from the RAMQ`() {
        val day = LocalDate(2026, 6, 30)
        fun list(key: String, p: Province? = null) = Rules.list(key, day, p).map { it.stripTrailingZeros().toPlainString() }
        assertEquals(listOf("0.03", "2942"), list("tax.prov.medical", Province.AB))
        assertEquals(listOf("0.03", "2854"), list("tax.prov.medical", Province.NB))
        for (p in listOf(Province.YT, Province.NT)) assertEquals(listOf("0.03", "2890"), list("tax.prov.medical", p))
        assertEquals(listOf("0.4", "0.4"), list("tax.prov.amt", Province.BC))
        assertEquals(listOf("0.621", "0.621"), list("tax.prov.amt", Province.NL))
        assertEquals(listOf("0.4571", "0.4571"), list("tax.prov.amt", Province.YT))
        assertEquals(listOf("0.2463", "0.2463"), list("tax.prov.amt", Province.ON))
        assertEquals("777.5", list("tax.qc.drugPremium").first())
        // The year before keeps its own figures.
        assertEquals(d("755"), Rules.list("tax.qc.drugPremium", LocalDate(2025, 12, 31)).first().stripTrailingZeros())
    }

    @Test
    fun `a year before the first rates has no estimate`() {
        assertFailsWith<RuleException> { IncomeTax.estimate(2023, Province.ON, pay("50000"), age65 = false) }
    }
}
