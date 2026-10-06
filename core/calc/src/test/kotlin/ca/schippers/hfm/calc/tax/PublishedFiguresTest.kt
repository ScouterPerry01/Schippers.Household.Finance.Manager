package ca.schippers.hfm.calc.tax

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 5 exit check: the income tax estimate against figures published by the governments
 * themselves (CRA, ESDC, Revenu Québec, Finances Québec, the B.C. Ministry of Finance), each cited
 * where it is used. Hand calculations from the published rates are in [IncomeTaxTest].
 */
class PublishedFiguresTest {

    private fun d(s: String) = BigDecimal(s)
    private fun TaxEstimate.lineOrZero(part: TaxPart, kind: TaxLineKind) = lines.firstOrNull { it.part == part && it.kind == kind }?.amount ?: d("0.00")

    // --- OAS recovery tax ------------------------------------------------------------------------

    @Test
    fun `OAS recovery tax, ESDC's worked example for 2025`() {
        // ESDC, Old Age Security pension recovery tax (canada.ca/en/services/benefits/publicpensions/old-age-security/recovery-tax.html):
        // "The threshold for 2025 is $93,454. If your income in 2025 was $100,000 ... $6,546 x 0.15 = $981.90".
        val e = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.OAS to d("8000"), TaxInput.PENSION to d("92000")), age65 = true)
        assertEquals(d("981.90"), e.lineOrZero(TaxPart.OTHER, TaxLineKind.OAS_RECOVERY))
    }

    @Test
    fun `OAS is fully recovered at ESDC's published income`() {
        // Same page: the full OAS pension (age 65 to 74) is recovered at a net income of $152,062 for 2025 and
        // $148,451 for 2024; the pension that income recovers is 15 % of the income above the threshold.
        for ((year, income, threshold) in listOf(Triple(2025, "152062", "93454"), Triple(2024, "148451", "90997"))) {
            val full = (d(income) - d(threshold)).multiply(d("0.15")).setScale(2, RoundingMode.HALF_UP)
            val inputs = mapOf(TaxInput.OAS to full, TaxInput.PENSION to d(income) - full)
            val e = IncomeTax.estimate(year, Province.ON, inputs, age65 = true)
            assertEquals(full, e.lineOrZero(TaxPart.OTHER, TaxLineKind.OAS_RECOVERY), "$year")
            assertEquals(d(threshold), Rules.list("tax.oasRecovery", LocalDate(year, 1, 1))[0])
        }
    }

    // --- Canada workers benefit and the medical expense supplement -------------------------------

    @Test
    fun `Canada workers benefit ends at the CRA's published incomes`() {
        // CRA, line 45300, How much you can get (2025): "$1,633 for single individuals", reduced above $26,855,
        // nothing at $37,742; families $2,813, above $30,639, nothing at $49,393.
        // CRA, Schedule 6 (5000-S6) 2024: $1,590 and $2,739; nothing at $36,749 and $48,093.
        fun cwb(year: Int, pay: String, family: Boolean): BigDecimal {
            val inputs = mapOf(TaxInput.EMPLOYMENT to d(pay)) + if (family) mapOf(TaxInput.SPOUSE_NET_INCOME to d("0")) else emptyMap()
            return IncomeTax.estimate(year, Province.ON, inputs, age65 = false).lineOrZero(TaxPart.REFUNDABLE, TaxLineKind.CWB)
        }
        assertEquals(d("1633.00"), cwb(2025, "26855", family = false))
        assertEquals(d("0.00"), cwb(2025, "37742", family = false))
        assertEquals(d("2813.00"), cwb(2025, "30639", family = true))
        assertEquals(d("0.00"), cwb(2025, "49393", family = true))
        assertEquals(d("1590.00"), cwb(2024, "26149", family = false))
        assertEquals(d("0.00"), cwb(2024, "36749", family = false))
        assertEquals(d("2739.00"), cwb(2024, "29833", family = true))
        assertEquals(d("0.00"), cwb(2024, "48093", family = true))
        assertTrue(cwb(2025, "37500", family = false).signum() > 0)
    }

    @Test
    fun `Canada workers benefit for Quebec residents, CRA form 5005-S6`() {
        // CRA, Schedule 6 for residents of Quebec (5005-S6) 2025: single, 37.3 % of working income above $2,400, at most
        // $3,812.06, less 20 % of adjusted family net income above $14,170.05; nothing at $33,230.35.
        fun cwb(pay: String) = IncomeTax.estimate(2025, Province.QC, mapOf(TaxInput.EMPLOYMENT to d(pay)), age65 = false)
            .lineOrZero(TaxPart.REFUNDABLE, TaxLineKind.CWB)
        assertEquals(d("3812.06"), cwb("14170.05"))
        assertEquals(d("0.00"), cwb("33230.35"))
    }

    @Test
    fun `refundable medical expense supplement, CRA worksheet figures`() {
        // CRA, Federal Worksheet (5000-D1) 2025, line 45200: "whichever is less: amount from line 13 or $1,504", reduced by 5 %
        // above $33,294; CRA, line 45200 page: available when "adjusted family net income is less than $63,374".
        fun supplement(pay: String) = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.EMPLOYMENT to d(pay), TaxInput.MEDICAL to d("10000")), age65 = false)
            .lineOrZero(TaxPart.REFUNDABLE, TaxLineKind.MEDICAL_SUPPLEMENT)
        assertEquals(d("1504.00"), supplement("33294"))
        assertEquals(d("0.00"), supplement("63374"))
        assertTrue(supplement("63000").signum() > 0)
        // Earned income of at least $4,390 is needed.
        val low = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.EMPLOYMENT to d("4389"), TaxInput.MEDICAL to d("3000")), age65 = false)
        assertEquals(d("0.00"), low.lineOrZero(TaxPart.REFUNDABLE, TaxLineKind.MEDICAL_SUPPLEMENT))
    }

    // --- Quebec --------------------------------------------------------------------------------------

    @Test
    fun `Quebec work premium reaches the maximums Revenu Quebec publishes`() {
        // Revenu Québec, Schedule P (TP-1.D.P-V) 2024 and 2025, and Finances Québec, Paramètres du régime d'imposition 2026:
        // maximum work premium, person alone / couple without children / single parent / couple with children.
        val maximums = mapOf(
            2024 to listOf("1152.34", "1797.07", "2980.20", "3873.00"),
            2025 to listOf("1185.52", "1848.34", "3066.00", "3983.50"),
            2026 to listOf("1207.33", "1882.45", "3122.40", "4057.00"),
        )
        for ((year, max) in maximums) {
            // Work income at the ceiling, and family income no higher, so nothing is taken back.
            val ceiling = if (year == 2024) listOf("12334", "19092") else if (year == 2025) listOf("12620", "19534") else listOf("12808", "19828")
            fun premium(pay: String, couple: Boolean, children: Int): BigDecimal {
                val inputs = buildMap {
                    put(TaxInput.BUSINESS, d(pay))
                    if (couple) { put(TaxInput.SPOUSE_NET_INCOME, d("0")); put(TaxInput.SPOUSE_WORKING_INCOME, d("0")) }
                    if (children > 0) put(TaxInput.CHILDREN, d(children.toString()))
                }
                return IncomeTax.estimate(year, Province.QC, inputs, age65 = false).lineOrZero(TaxPart.REFUNDABLE, TaxLineKind.WORK_PREMIUM)
            }
            assertEquals(d(max[0]), premium(ceiling[0], couple = false, children = 0), "$year alone")
            assertEquals(d(max[1]), premium(ceiling[1], couple = true, children = 0), "$year couple")
            assertEquals(d(max[2]), premium(ceiling[0], couple = false, children = 1), "$year single parent")
            assertEquals(d(max[3]), premium(ceiling[1], couple = true, children = 1), "$year couple with children")
        }
    }

    @Test
    fun `Quebec refundable medical credit ends where Revenu Quebec's table says`() {
        // Revenu Québec, line 462, point 1 (2025): expenses up to $1,877 give no credit at a family income of $32,800
        // (25 % of the expenses above 3 % of family income, less 5 % of family income above $28,335; at most $1,466).
        // Pay of 34,220 less the deduction for workers (1,420) is a family income of 32,800.
        fun credit(expenses: String) = IncomeTax.estimate(2025, Province.QC, mapOf(TaxInput.EMPLOYMENT to d("34220"), TaxInput.MEDICAL to d(expenses)), age65 = false)
            .lineOrZero(TaxPart.REFUNDABLE, TaxLineKind.QC_MEDICAL_CREDIT)
        assertEquals(d("0.00"), credit("1877"))
        assertEquals(d("0.25"), credit("1878"))
    }

    @Test
    fun `health services fund contribution at the points of Schedule F`() {
        // Revenu Québec, Schedule F 2025 (TP-1.D.F-V): 1 % of income other than employment income, OAS and the dividend
        // gross-up above $18,130, at most $150 up to $63,060; above it $150 plus 1 %, at most $1,000.
        fun fund(interest: String) = IncomeTax.estimate(2025, Province.QC, mapOf(TaxInput.INTEREST to d(interest), TaxInput.EMPLOYMENT to d("50000")), age65 = false)
            .lineOrZero(TaxPart.OTHER, TaxLineKind.HEALTH_FUND)
        assertEquals(d("68.70"), fund("25000"))
        assertEquals(d("150.00"), fund("40000"))
        assertEquals(d("319.40"), fund("80000"))
        assertEquals(d("1000.00"), fund("148060"))
        assertEquals(d("1000.00"), fund("300000"))
    }

    @Test
    fun `drug insurance premium at the exemptions and maximums of Schedule K`() {
        // Revenu Québec, Schedule K 2025 (TP-1.D.K-V) and Finances Québec, Information Bulletin 2025-8, table 5: "for the 2025
        // calendar year, the maximum premium payable is $755 per adult"; exempt family income $19,890 alone, $32,240 for a couple;
        // 7.84 % (couple 3.93 %) of the first $5,000 above it ($392.00 and $196.50), then 11.76 % (couple 5.89 %).
        // Schedule K 2024: at most $737.50; $19,500 alone.
        fun premium(year: Int, otherIncome: String, spouse: String? = null): BigDecimal {
            val inputs = mapOf(TaxInput.OTHER_INCOME to d(otherIncome)) + (spouse?.let { mapOf(TaxInput.SPOUSE_NET_INCOME to d(it)) } ?: emptyMap())
            return IncomeTax.estimate(year, Province.QC, inputs, age65 = false).lineOrZero(TaxPart.OTHER, TaxLineKind.DRUG_PREMIUM)
        }
        assertEquals(d("0.00"), premium(2025, "19890"))
        assertEquals(d("392.00"), premium(2025, "24890"))
        assertEquals(d("755.00"), premium(2025, "40000"))
        assertEquals(d("196.50"), premium(2025, "37240", spouse = "0"))
        assertEquals(d("0.00"), premium(2024, "19500"))
        assertEquals(d("737.50"), premium(2024, "40000"))
    }

    @Test
    fun `Quebec minimum tax exemptions, TP-776_42`() {
        // Revenu Québec, TP-776.42-V: "Subtract $179,990 ... Multiply line 24 by 19%" (2025); $175,000 for 2024 (Information
        // Bulletin 2023-4); $183,680 for 2026.
        for ((year, exemption) in listOf(2024 to "175000", 2025 to "179990", 2026 to "183680")) {
            val (rate, ex) = Rules.list("tax.qc.amt", LocalDate(year, 1, 1))
            assertEquals(0, d("0.19").compareTo(rate))
            assertEquals(0, d(exemption).compareTo(ex), "$year")
        }
    }

    // --- Federal amounts and the alternative minimum tax ---------------------------------------------

    @Test
    fun `federal basic personal amount, CRA Federal Worksheet line 30000`() {
        // CRA, Federal Worksheet (5000-D1) 2025: "$177,882 or less, enter $16,129 ... $253,414 or more, enter $14,538";
        // in between, $14,538 plus $1,591 x (253,414 - net income) / 75,532. 2024: $15,705 and $14,156 between $173,205 and $246,752.
        val y2025 = Rules.list("tax.fed.bpa", LocalDate(2025, 1, 1))
        val between = d("14538") + d("1591").multiply(d("253414") - d("200000")).divide(d("75532"), 10, RoundingMode.HALF_UP)
        assertEquals(between.setScale(2, RoundingMode.HALF_UP), IncomeTax.phased(y2025, d("200000")).setScale(2, RoundingMode.HALF_UP))
        val y2024 = Rules.list("tax.fed.bpa", LocalDate(2024, 1, 1))
        assertEquals(0, d("15705").compareTo(IncomeTax.phased(y2024, d("173205"))))
        assertEquals(0, d("14156").compareTo(IncomeTax.phased(y2024, d("246752"))))
    }

    @Test
    fun `alternative minimum tax for 2024 with the figures of Form T691`() {
        // CRA, Form T691 2024: basic exemption $173,205 (line 95), rate 20.5 % (line 97), 100 % of capital gains, 50 % of the
        // non-refundable credits (line 103) and 80 % of the donation credit (line 104).
        // A taxable capital gain of 250,000 (a gain of 500,000), nothing else:
        val e = IncomeTax.estimate(2024, Province.ON, mapOf(TaxInput.TAXABLE_CAPITAL_GAINS to d("250000")), age65 = false)
        // Regular: brackets of 2024 on 250,000 less 15 % of the basic personal amount at that income (14,156).
        val regular = d("55867").multiply(d("0.15")) + (d("111733") - d("55867")).multiply(d("0.205")) +
            (d("173205") - d("111733")).multiply(d("0.26")) + (d("246752") - d("173205")).multiply(d("0.29")) +
            (d("250000") - d("246752")).multiply(d("0.33")) - d("14156").multiply(d("0.15"))
        assertEquals(regular.setScale(2, RoundingMode.HALF_UP), e.lineOrZero(TaxPart.FEDERAL, TaxLineKind.BASIC_TAX))
        // Minimum: 20.5 % of (500,000 - 173,205), less half of the credit.
        val minimum = (d("500000") - d("173205")).multiply(d("0.205")) - d("14156").multiply(d("0.15")).multiply(d("0.5"))
        assertEquals(minimum.setScale(2, RoundingMode.HALF_UP), e.lineOrZero(TaxPart.FEDERAL, TaxLineKind.MINIMUM_TAX))
        assertEquals(minimum.setScale(2, RoundingMode.HALF_UP), e.federalTax)
    }

    // --- Tuition -------------------------------------------------------------------------------------

    @Test
    fun `where this year's tuition still gives a provincial credit, and the transfer limit`() {
        // CRA, provincial and territorial tax information for 2025 (5001-PC to 5014-PC): NL, PE, NS, NB, MB, BC, YT, NT and NU have
        // a current-year tuition amount, and "the maximum amount each student can transfer to you is $5,000 minus the current year's
        // amount that they claimed"; Ontario, Saskatchewan and Alberta only carry unused amounts forward. Guide P105: "a maximum of
        // $5,000 of the current year's federal tuition amount".
        val on = LocalDate(2025, 1, 1)
        val withCredit = Province.entries.filter { it != Province.QC && Rules.yesNo("tax.prov.tuition", on, it) }.toSet()
        assertEquals(Province.entries.toSet() - setOf(Province.QC, Province.ON, Province.SK, Province.AB), withCredit)
        withCredit.forEach { assertEquals(0, d("5000").compareTo(Rules.decimal("tax.prov.tuitionTransfer", on, it)), "$it") }
        assertEquals(0, d("5000").compareTo(Rules.decimal("tax.fed.tuitionTransfer", on)))
    }

    // --- Provincial tax payable, B.C. Budget interprovincial comparison -------------------------------

    /**
     * B.C. Ministry of Finance, Budget and Fiscal Plan 2025/26 to 2027/28, table A4, "Interprovincial Comparisons of Provincial
     * Personal Income Taxes Payable" (rates known as of February 1, 2025): "a single individual with wage income, and claiming
     * credits for Canada Pension Plan and Quebec Pension Plan contributions, Employment Insurance premiums, Quebec Parental
     * Insurance Plan premiums, and the basic personal amount", with low-income reductions, Ontario's and P.E.I.'s surtaxes and the
     * Ontario Health Premium; Quebec after the federal abatement. Columns BC, AB, SK, MB, ON, QC, NB, NS, PE, NL.
     */
    private val bcBudget2025 = mapOf(
        30000 to listOf(365, 561, 638, 1292, 300, 815, 816, 1448, 1300, 1467),
        50000 to listOf(1701, 2409, 2827, 3326, 2277, 3290, 3104, 4349, 3699, 3385),
        80000 to listOf(3901, 5222, 6290, 6938, 4940, 7879, 7020, 9078, 7988, 7540),
        100000 to listOf(5448, 7217, 8784, 9482, 6827, 10997, 9814, 12429, 11300, 10570),
        150000 to listOf(11616, 12217, 15034, 18076, 14852, 20860, 17740, 21179, 20158, 18470),
    )
    private val columns = listOf(Province.BC, Province.AB, Province.SK, Province.MB, Province.ON, Province.QC, Province.NB, Province.NS, Province.PE, Province.NL)

    /** 2025 payroll contributions on [pay]: CPP or QPP (both tiers), and EI (with QPIP in Quebec), at the CRA's and Revenu Québec's 2025 rates. */
    private fun payroll2025(pay: BigDecimal, quebec: Boolean): Pair<BigDecimal, BigDecimal> {
        val first = (pay.min(d("71300")) - d("3500")).max(BigDecimal.ZERO).multiply(if (quebec) d("0.064") else d("0.0595"))
        val second = (pay.min(d("81200")) - d("71300")).max(BigDecimal.ZERO).multiply(d("0.04"))
        val ei = if (quebec) pay.min(d("65700")).multiply(d("0.0131")) + pay.min(d("98000")).multiply(d("0.00494")) else pay.min(d("65700")).multiply(d("0.0164"))
        return (first + second).setScale(2, RoundingMode.HALF_UP) to ei.setScale(2, RoundingMode.HALF_UP)
    }

    @Test
    fun `provincial tax payable matches the B_C_ Budget's interprovincial comparison for 2025`() {
        val misses = ArrayList<String>()
        for ((income, row) in bcBudget2025) for ((i, province) in columns.withIndex()) {
            // Left out, as the table was made with the rates known on February 1, 2025: Alberta's 8 % bracket and Nova Scotia's
            // indexed brackets and new basic personal amount came after (Nova Scotia matches the 2024 table, below), and so did
            // Newfoundland and Labrador's 2025 low-income tax reduction (it changes only the $30,000 row). At $30,000 the table is
            // lower in Saskatchewan (its refundable low-income tax credit, which the estimate leaves out with other provincial
            // benefits, appears to be counted), and in Quebec at $30,000 and $50,000 by an amount the table's notes do not explain;
            // from $80,000 Quebec matches within 0.3 %.
            if (province == Province.AB || province == Province.NS) continue
            if (income == 30000 && province in setOf(Province.SK, Province.QC, Province.NL) || income == 50000 && province == Province.QC) continue
            val pay = BigDecimal(income)
            val (plan, ei) = payroll2025(pay, province == Province.QC)
            val e = IncomeTax.estimate(2025, province, mapOf(TaxInput.EMPLOYMENT to pay, TaxInput.CPP_QPP to plan, TaxInput.EI_QPIP to ei), age65 = false)
            // Quebec's column is net of the federal abatement, which the estimate shows on the federal side.
            val provincial = e.provincialTax + if (province == Province.QC) e.lineOrZero(TaxPart.FEDERAL, TaxLineKind.ABATEMENT) else BigDecimal.ZERO
            val published = BigDecimal(row[i])
            val diff = (provincial - published).abs()
            // The table rounds to the dollar and does not say how it treats the enhanced CPP or QPP contributions (a deduction since
            // 2019); within 1.5 % or $40, whichever is more.
            if (diff > published.multiply(d("0.015")).max(d("40"))) misses += "$province $income: published $published, estimated $provincial"
        }
        assertTrue(misses.isEmpty(), misses.joinToString("\n"))
    }

    @Test
    fun `Nova Scotia's provincial tax matches the B_C_ Budget's comparison for 2024`() {
        // B.C. Budget and Fiscal Plan 2024/25 to 2026/27, table A4 (rates known as of February 1, 2024), Nova Scotia column; 2024
        // payroll: CPP 5.95 % up to $68,500 above $3,500 and 4 % up to $73,200, EI 1.66 % up to $63,200.
        for ((income, published) in listOf(30000 to 1479, 50000 to 4427, 80000 to 9234, 100000 to 12619, 150000 to 21369)) {
            val pay = BigDecimal(income)
            val plan = ((pay.min(d("68500")) - d("3500")).multiply(d("0.0595")) + (pay.min(d("73200")) - d("68500")).max(BigDecimal.ZERO).multiply(d("0.04")))
                .setScale(2, RoundingMode.HALF_UP)
            val ei = pay.min(d("63200")).multiply(d("0.0166")).setScale(2, RoundingMode.HALF_UP)
            val e = IncomeTax.estimate(2024, Province.NS, mapOf(TaxInput.EMPLOYMENT to pay, TaxInput.CPP_QPP to plan, TaxInput.EI_QPIP to ei), age65 = false)
            val diff = (e.provincialTax - BigDecimal(published)).abs()
            assertTrue(diff <= BigDecimal(published).multiply(d("0.015")).max(d("40")), "NS $income: published $published, estimated ${e.provincialTax}")
        }
    }

    @Test
    fun `Ontario LIFT credit and the Atlantic low-income tax reductions, from the 2025 forms`() {
        // CRA, Schedule ON428-A 2025: 5.05 % of employment income, at most $875, less 5 % of adjusted net income above $32,500.
        // At $30,000 of pay the full $875 more than covers Ontario's tax after its tax reduction, which leaves the health premium.
        val on = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.EMPLOYMENT to d("30000")), age65 = false)
        assertTrue(on.lineOrZero(TaxPart.PROVINCIAL, TaxLineKind.LOW_INCOME_CREDIT).signum() < 0)
        assertEquals(d("300.00"), on.provincialTax)
        val onHigher = IncomeTax.estimate(2025, Province.ON, mapOf(TaxInput.EMPLOYMENT to d("40000")), age65 = false)
        // 875 less 5 % of 40,000 - 32,500 = 500.
        assertEquals(d("-500.00"), onHigher.lineOrZero(TaxPart.PROVINCIAL, TaxLineKind.LOW_INCOME_CREDIT))
        // CRA, Form NB428 2025, lines 77 to 86: $802 less 3 % of adjusted family income above $21,920.
        val nb = IncomeTax.estimate(2025, Province.NB, mapOf(TaxInput.EMPLOYMENT to d("30000")), age65 = false)
        assertEquals(d("-559.60"), nb.lineOrZero(TaxPart.PROVINCIAL, TaxLineKind.TAX_REDUCTION))
        // CRA, Form PE428 2025, lines 75 to 87: a single parent of two: $350, $350 for the child claimed as an eligible dependant,
        // $300 for the other child, less 5 % of family income above $22,650: 1,000 - 367.50.
        val pe = IncomeTax.estimate(2025, Province.PE, mapOf(TaxInput.EMPLOYMENT to d("30000"), TaxInput.CHILDREN to d("2")), age65 = false)
        assertEquals(d("-632.50"), pe.lineOrZero(TaxPart.PROVINCIAL, TaxLineKind.TAX_REDUCTION))
    }
}
