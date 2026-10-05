package ca.schippers.hfm.calc.rules

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.PasswordRules.Problem
import ca.schippers.hfm.calc.schedule.BusinessDays
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rates and rules for reminders, thresholds, password rules and bank holidays (the owner's decision 2026-10-05). */
class AppRulesTest {

    @AfterTest
    fun reset() {
        Rules.userValues = emptyList()
    }

    private fun d(s: String) = LocalDate.parse(s)
    private fun own(key: String, from: String, value: String, province: Province? = null) =
        RuleValue(key, province, d(from), value, builtIn = false, id = "$key|$from|$province")

    @Test
    fun `built-in lead times and thresholds are today's figures`() {
        val on = d("2026-10-05")
        assertEquals(30, LeadTimes.renewals(on))
        assertEquals(60, LeadTimes.warranty(on))
        assertEquals(listOf(7, 1), LeadTimes.newBill(on))
        assertEquals(1440, LeadTimes.newEvent(on))
        assertEquals(10 * 60_000L, LeadTimes.syncInvitationMillis(on))
        assertEquals(6, LeadTimes.documentRetention(on))
        assertEquals(45, Thresholds.reconcileBehind(on))
        assertEquals(0, BigDecimal("1.25").compareTo(Thresholds.unusualBill(on)))
        assertEquals(0, BigDecimal("3.5").compareTo(Thresholds.statementFxPercent(on)))
        assertEquals(0, BigDecimal(50).compareTo(Thresholds.t5Interest(on)))
        assertEquals(listOf(d("2026-03-15"), d("2026-06-15"), d("2026-09-15"), d("2026-12-15")), Thresholds.instalmentDueDates(2026))
    }

    @Test
    fun `a changed lead time applies from its date`() {
        Rules.userValues = listOf(own("reminder.renewals", "2027-01-01", "45"))
        assertEquals(30, LeadTimes.renewals(d("2026-12-31")))
        assertEquals(45, LeadTimes.renewals(d("2027-01-01")))
    }

    @Test
    fun `a value outside its range is brought back into it when read`() {
        Rules.userValues = listOf(own("security.password.minLength", "2026-01-01", "4"), own("security.argon2.memory", "2026-01-01", "8"))
        assertEquals(PasswordRules.MIN, PasswordRules.on(d("2026-06-01")).minLength)
        assertFalse(RuleLimits.allows("security.password.minLength", "4"))
        assertFalse(RuleLimits.allows("security.password.minLength", "12.5"))
        assertTrue(RuleLimits.allows("security.password.minLength", "16"))
        assertFalse(RuleLimits.allows("security.argon2.memory", "8"), "below the OWASP minimum of 19 MiB")
        assertFalse(RuleLimits.allows("security.argon2.iterations", "1"))
        assertEquals(BigDecimal(19), RuleLimits.clamp("security.argon2.memory", BigDecimal(8)))
    }

    @Test
    fun `Quebec winter tires go on by its legal date, elsewhere on the suggested one`() {
        assertEquals(12 to 1, Thresholds.winterTiresOn(d("2026-10-01"), Province.QC))
        assertEquals(12 to 15, Thresholds.winterTiresOn(d("2015-10-01"), Province.QC), "December 15 before 2019")
        assertEquals(11 to 15, Thresholds.winterTiresOn(d("2026-10-01"), Province.ON))
        assertEquals(4 to 15, Thresholds.winterTiresOff(d("2026-10-01"), Province.QC))
    }

    @Test
    fun `the default password rules ask for 12 characters and no login name`() {
        val rules = PasswordRules.on(d("2026-10-05"))
        assertEquals(PasswordRules(12, capitals = false, smallLetters = false, digits = false, symbols = false, notLoginName = true), rules)
        assertEquals(listOf(Problem.TOO_SHORT), rules.problems("short".toCharArray(), "perry"))
        assertEquals(emptyList(), rules.problems("correct horse battery".toCharArray(), "perry"))
        assertEquals(listOf(Problem.HAS_LOGIN_NAME), rules.problems("my name is PERRY ok".toCharArray(), "perry"), "the login name in any case")
        assertEquals(emptyList(), rules.problems("al pacino sings well".toCharArray(), "al"), "a login name of two letters is not looked for")
    }

    @Test
    fun `required kinds of characters are each checked`() {
        val strict = PasswordRules(8, capitals = true, smallLetters = true, digits = true, symbols = true, notLoginName = false)
        assertEquals(listOf(Problem.NO_CAPITAL, Problem.NO_DIGIT, Problem.NO_SYMBOL), strict.problems("lowercase only".toCharArray(), null))
        assertEquals(listOf(Problem.NO_SMALL_LETTER), strict.problems("UPPER 12 !!".toCharArray(), null))
        assertEquals(emptyList(), strict.problems("Été 2026 !".toCharArray(), null), "accented letters count")
        assertFalse(PasswordRules.isSymbol(' '))
        assertTrue(PasswordRules.isSymbol('#'))
    }

    @Test
    fun `the household's password rules apply from their date, and a new household keeps the built-in ones`() {
        Rules.userValues = listOf(own("security.password.minLength", "2026-11-01", "16"), own("security.password.digits", "2026-11-01", "true"))
        assertEquals(12, PasswordRules.on(d("2026-10-31")).minLength)
        val later = PasswordRules.on(d("2026-11-01"))
        assertEquals(16, later.minLength)
        assertTrue(later.digits)
        assertEquals(listOf(Problem.TOO_SHORT, Problem.NO_DIGIT), later.problems("fifteen letters".toCharArray(), null))
        assertEquals(12, PasswordRules.builtIn(d("2026-11-01")).minLength, "creating a household ignores the open household's values")
    }

    @Test
    fun `bank holidays follow each province's rules by year`() {
        val before = BusinessDays.province
        try {
            // Family Day: Ontario since 2008, British Columbia since 2013, never in Quebec.
            assertTrue(LocalDate(2026, 2, 16) in BusinessDays.holidays(2026, Province.ON))
            assertFalse(LocalDate(2026, 2, 16) in BusinessDays.holidays(2026, Province.QC))
            assertFalse(LocalDate(2012, 2, 20) in BusinessDays.holidays(2012, Province.BC), "not yet a holiday in B.C.")
            assertTrue(LocalDate(2026, 6, 24) in BusinessDays.holidays(2026, Province.QC))
            assertTrue(LocalDate(2026, 6, 22) in BusinessDays.holidays(2026, Province.YT), "June 21, 2026 is a Sunday")
            assertFalse(LocalDate(2016, 6, 21) in BusinessDays.holidays(2016, Province.YT), "Yukon added it in 2017")
            assertFalse(LocalDate(2020, 9, 30) in BusinessDays.holidays(2020, Province.ON))
            assertTrue(LocalDate(2021, 9, 30) in BusinessDays.holidays(2021, Province.ON))

            // A province adding a holiday is entered as a value from that year.
            Rules.userValues = listOf(own("holiday.familyDay", "2027-01-01", "true", Province.QC))
            assertFalse(LocalDate(2026, 2, 16) in BusinessDays.holidays(2026, Province.QC))
            assertTrue(LocalDate(2027, 2, 15) in BusinessDays.holidays(2027, Province.QC))
            BusinessDays.province = Province.QC
            assertFalse(BusinessDays.isBusinessDay(LocalDate(2027, 2, 15)))
            Rules.userValues = emptyList()
            assertTrue(BusinessDays.isBusinessDay(LocalDate(2027, 2, 15)), "the cache follows the household's values")
        } finally {
            BusinessDays.province = before
        }
    }
}
