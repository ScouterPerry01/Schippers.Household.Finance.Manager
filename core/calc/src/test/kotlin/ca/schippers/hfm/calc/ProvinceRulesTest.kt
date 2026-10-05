package ca.schippers.hfm.calc

import ca.schippers.hfm.calc.plans.RegisteredPlans
import ca.schippers.hfm.calc.schedule.BusinessDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PROV-02, PROV-04, PROV-05: rules that change with the province or territory. */
class ProvinceRulesTest {

    @AfterTest
    fun reset() {
        BusinessDays.province = Province.QC
    }

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `bank holidays per province in 2026`() {
        val familyDay = d("2026-02-16")
        val stJean = d("2026-06-24")
        val civic = d("2026-08-03")
        val qc = BusinessDays.holidays(2026, Province.QC)
        val on = BusinessDays.holidays(2026, Province.ON)
        assertTrue(stJean in qc && stJean !in on)
        assertTrue(familyDay in on && familyDay !in qc)
        assertTrue(civic in on && civic !in qc)
        assertTrue(civic !in BusinessDays.holidays(2026, Province.NL), "Newfoundland banks keep the federal days")
        assertTrue(d("2026-07-09") in BusinessDays.holidays(2026, Province.NU), "Nunavut Day")
        assertTrue(d("2026-08-17") in BusinessDays.holidays(2026, Province.YT), "Discovery Day")
        assertTrue(d("2026-06-22") in BusinessDays.holidays(2026, Province.NT), "June 21 is a Sunday: observed Monday")
        for (p in Province.entries) assertTrue(d("2026-07-01") in BusinessDays.holidays(2026, p) && d("2026-12-25") in BusinessDays.holidays(2026, p))
    }

    @Test
    fun `the February holiday's Monday and when territorial holidays began`() {
        // B.C. Family Day: second Monday 2013 to 2018, third from 2019.
        assertTrue(d("2018-02-12") in BusinessDays.holidays(2018, Province.BC) && d("2018-02-19") !in BusinessDays.holidays(2018, Province.BC))
        assertTrue(d("2019-02-18") in BusinessDays.holidays(2019, Province.BC))
        assertTrue(d("2012-02-13") !in BusinessDays.holidays(2012, Province.BC))
        // P.E.I. Islander Day: second Monday in 2009, third from 2010.
        assertTrue(d("2009-02-09") in BusinessDays.holidays(2009, Province.PE))
        assertTrue(d("2010-02-15") in BusinessDays.holidays(2010, Province.PE))
        assertTrue(d("2018-02-19") in BusinessDays.holidays(2018, Province.ON))
        // Nunavut Day a general holiday from 2020.
        assertTrue(d("2019-07-09") !in BusinessDays.holidays(2019, Province.NU))
        assertTrue(d("2020-07-09") in BusinessDays.holidays(2020, Province.NU))
    }

    @Test
    fun `the last business day follows the household's province`() {
        BusinessDays.province = Province.ON
        assertEquals(d("2026-07-31"), BusinessDays.lastBusinessDayOfMonth(2026, Month.JULY))
        assertFalse(BusinessDays.isBusinessDay(d("2026-08-03")), "Civic Holiday in Ontario")
        BusinessDays.province = Province.QC
        assertTrue(BusinessDays.isBusinessDay(d("2026-08-03")), "an ordinary Monday in Quebec")
    }

    @Test
    fun `LIF maximum by jurisdiction`() {
        assertTrue(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Federal))
        assertTrue(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Provincial(Province.ON)))
        assertFalse(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Provincial(Province.SK)))
        assertFalse(RegisteredPlans.lifHasMaximum(PensionJurisdiction.Provincial(Province.PE)))
        assertEquals(PensionJurisdiction.Federal, PensionJurisdiction.of("FEDERAL"))
        assertEquals(PensionJurisdiction.Provincial(Province.AB), PensionJurisdiction.of("AB"))
        assertEquals(14, PensionJurisdiction.all.size)
    }

    @Test
    fun `provincial RESP grants`() {
        assertEquals(RegisteredPlans.ProvincialGrant.QESI, RegisteredPlans.provincialGrant(Province.QC))
        assertEquals(RegisteredPlans.ProvincialGrant.BCTESG, RegisteredPlans.provincialGrant(Province.BC))
        assertNull(RegisteredPlans.provincialGrant(Province.ON))
        val born = d("2019-05-01")
        assertFalse(RegisteredPlans.bctesgEligible(born, d("2025-04-30")), "not before the 6th birthday")
        assertTrue(RegisteredPlans.bctesgEligible(born, d("2025-05-01")))
        assertFalse(RegisteredPlans.bctesgWindowClosed(born, d("2028-04-30")))
        assertTrue(RegisteredPlans.bctesgWindowClosed(born, d("2028-05-01")), "closed at 9")
        assertFalse(RegisteredPlans.bctesgEligible(d("2005-12-31"), d("2012-01-01")), "born before 2006")
    }
}
