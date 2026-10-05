package ca.schippers.hfm.calc.rules

import ca.schippers.hfm.calc.PensionJurisdiction
import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.invest.CostBase
import ca.schippers.hfm.calc.invest.CostEvent
import ca.schippers.hfm.calc.invest.CostEventKind
import ca.schippers.hfm.calc.invest.ForeignExchange
import ca.schippers.hfm.calc.invest.InvestmentIncome
import ca.schippers.hfm.calc.invest.SlipKind
import ca.schippers.hfm.calc.invest.TaxSlips
import ca.schippers.hfm.calc.medical.Medical
import ca.schippers.hfm.calc.plans.RegisteredPlans
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rates and rules of registered plans, investing and medical: the built-in figures as the code
 * applied them, and the household's own values taking their place from their date.
 */
class PlansInvestingMedicalRulesTest {

    @AfterTest
    fun reset() {
        Rules.userValues = emptyList()
    }

    private fun own(key: String, from: String, value: String, province: Province? = null) =
        RuleValue(key, province, LocalDate.parse(from), value, builtIn = false, id = "$key@$from")

    private fun n(s: String) = BigDecimal(s)

    @Test
    fun `a changed FHSA limit applies from its year`() {
        assertEquals(listOf(8000_00L, 16000_00L), RegisteredPlans.fhsaRoom(2026, 2027, emptyMap()).map { it.available })
        Rules.userValues = listOf(own("fhsa.annual", "2027-01-01", "9000"))
        assertEquals(listOf(8000_00L, 17000_00L), RegisteredPlans.fhsaRoom(2026, 2027, emptyMap()).map { it.available }, "8,000 carried, 9,000 for 2027")
        Rules.userValues = listOf(own("fhsa.lifetime", "2023-04-01", "10000"))
        assertEquals(10000_00L, RegisteredPlans.fhsaRoom(2023, 2024, mapOf(2023 to 8000_00L)).let { it[0].available + it[1].available })
    }

    @Test
    fun `a changed CRA medical amount replaces the built-in one for its year only`() {
        assertEquals(n("2834"), Medical.federalMaxReduction(2025))
        assertNull(Medical.federalMaxReduction(2027), "an indexed amount is not carried into a later year")
        Rules.userValues = listOf(own("medical.threshold.max", "2027-01-01", "2950"), own("medical.threshold.max", "2025-01-01", "2800"))
        assertEquals(n("2950"), Medical.federalMaxReduction(2027))
        assertEquals(n("2800"), Medical.federalMaxReduction(2025), "on the same date the household's value wins")
        assertEquals(n("2890"), Medical.federalMaxReduction(2026))
        Rules.userValues = listOf(own("medical.threshold.rate", "2027-01-01", "0.04"))
        assertEquals(n("600.00"), Medical.claimable(n("1000"), n("10000"), null, 2027), "4 % of 10,000")
        assertEquals(n("700.00"), Medical.claimable(n("1000"), n("10000"), null, 2026))
    }

    @Test
    fun `medical travel rates by province and year, and the 40 km`() {
        assertEquals("0.62", Medical.travelRate(LocalDate(2025, 7, 1), Province.ON)?.value)
        assertEquals("0.605", Medical.travelRate(LocalDate(2025, 7, 1), Province.QC)?.value)
        assertEquals("0.715", Medical.travelRate(LocalDate(2024, 7, 1), Province.YT)?.value)
        assertEquals("0.62", Medical.travelRate(LocalDate(2026, 7, 1), Province.ON)?.value, "2026 is not published yet: the last rate")
        assertNull(Medical.travelRate(LocalDate(2022, 7, 1), Province.ON))
        assertEquals(n("40"), Medical.travelMinimumKm(LocalDate(2026, 1, 1)))
        Rules.userValues = listOf(own("medical.travel.rate", "2026-01-01", "0.64", Province.ON))
        assertEquals("0.64", Medical.travelRate(LocalDate(2026, 7, 1), Province.ON)?.value)
        assertEquals("0.605", Medical.travelRate(LocalDate(2026, 7, 1), Province.QC)?.value)
    }

    @Test
    fun `dividend gross-up and credit by year, as the CRA's T5 guide gives them`() {
        assertEquals(n("1.38"), TaxSlips.eligibleGrossUp(2026))
        assertEquals(n("1.41"), TaxSlips.eligibleGrossUp(2011))
        assertEquals(n("1.25"), TaxSlips.ordinaryGrossUp(2013))
        assertEquals(n("1.18"), TaxSlips.ordinaryGrossUp(2015))
        assertEquals(n("0.110169"), TaxSlips.ordinaryCredit(2015), "11.0169 %, not 11.0198 %")
        assertEquals(n("1.15"), TaxSlips.ordinaryGrossUp(2026))
        Rules.userValues = listOf(own("dividends.other.grossup", "2027-01-01", "1.14"))
        assertEquals(n("114.00"), TaxSlips.boxes(SlipKind.T5, InvestmentIncome(ordinaryDividends = n("100")), 2027)["11"])
        assertEquals(n("115.00"), TaxSlips.boxes(SlipKind.T5, InvestmentIncome(ordinaryDividends = n("100")), 2026)["11"])
    }

    @Test
    fun `capital gains inclusion, the foreign exchange exemption and superficial losses`() {
        assertEquals(n("0.5"), TaxSlips.inclusionRate(LocalDate(2026, 6, 1)))
        assertEquals(n("0.75"), TaxSlips.inclusionRate(LocalDate(1995, 6, 1)))
        Rules.userValues = listOf(own("capitalgains.inclusion", "2027-01-01", "0.6"), own("fx.exemption", "2027-01-01", "250"), own("superficial.loss.days", "2027-01-01", "10"))
        assertEquals(n("0.6"), TaxSlips.inclusionRate(LocalDate(2027, 2, 1)))
        assertEquals(n("0.00"), ForeignExchange.reportable(n("240.00"), 2027))
        assertEquals(n("40.00"), ForeignExchange.reportable(n("240.00"), 2026))
        val zero = Money.zero(Currency.CAD)
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun sale(buyAgain: String) = CostBase.run(
            listOf(
                CostEvent(LocalDate(2027, 1, 5), CostEventKind.ACQUIRE, n("10"), cad("1000")),
                CostEvent(LocalDate(2027, 3, 1), CostEventKind.DISPOSE, n("10"), cad("800")),
                CostEvent(LocalDate.parse(buyAgain), CostEventKind.ACQUIRE, n("10"), cad("800")),
            ),
            zero,
        ).dispositions.single().possibleSuperficialLoss
        assertFalse(sale("2027-03-20"), "19 days later, beyond the household's 10")
        assertTrue(sale("2027-03-08"))
    }

    @Test
    fun `RRIF factors, LIF rules and RESP grants read by year and province`() {
        assertEquals(n("0.0540"), RegisteredPlans.rrifFactor(72, 2026))
        assertEquals(n("0.2000"), RegisteredPlans.rrifFactor(101, 2026))
        assertEquals(n("0.0540"), RegisteredPlans.rrifFactor(72, 2010), "before the first value, the earliest")
        assertFalse(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Provincial(Province.SK), 2026))
        assertTrue(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Federal, 2026))
        assertEquals(RegisteredPlans.ProvincialGrant.QESI, RegisteredPlans.provincialGrant(Province.QC, 2026))
        assertNull(RegisteredPlans.provincialGrant(Province.ON, 2026))
        Rules.userValues = listOf(
            own("lif.maximum", "2027-01-01", "false", Province.ON),
            own("resp.cesg.room", "2027-01-01", "600"),
            own("rrsp.excess.buffer", "2027-01-01", "2500"),
        )
        assertFalse(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Provincial(Province.ON), 2027))
        assertTrue(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Provincial(Province.ON), 2026))
        val years = RegisteredPlans.grants(RegisteredPlans.CESG, 2026, mapOf(2027 to 5000_00L), 2027)
        assertEquals(listOf(0L, 1000_00L), years.map { it.grant }, "500 of room for 2026, 600 for 2027, at most 1,000 a year")
        assertEquals(100_00L, years.last().roomLeft)
        assertEquals(n("2500"), RegisteredPlans.rrspExcessBuffer(2027))
        assertEquals(n("2000"), RegisteredPlans.rrspExcessBuffer(2026))
    }
}
