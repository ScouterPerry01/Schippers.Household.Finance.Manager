package ca.schippers.hfm.calc.rules

import ca.schippers.hfm.calc.Province
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rates and rules: built-in values by date and province, and the household's own taking their place. */
class RulesTest {

    @AfterTest
    fun reset() {
        Rules.userValues = emptyList()
    }

    @Test
    fun `every built-in value fits its rule, and every rule has a value`() {
        assertTrue(Rules.catalogue.isNotEmpty())
        val keys = Rules.builtIn.map { it.key }.toSet()
        assertEquals(emptyList(), Rules.catalogue.map { it.key }.filter { it !in keys }, "rules without a built-in value")
        for (v in Rules.builtIn) Rules.check(Rules.rule(v.key), v.value)
        for (v in Rules.builtIn) assertTrue(v.province == null || Rules.rule(v.key).perProvince, "${v.key} has a province but is kept for all")
    }

    @Test
    fun `the latest value in effect applies, and none before the first`() {
        assertEquals(5000, Rules.int("tfsa.limit", LocalDate(2009, 6, 1)))
        assertEquals(10000, Rules.int("tfsa.limit", LocalDate(2015, 12, 31)))
        assertEquals(7000, Rules.int("tfsa.limit", LocalDate(2030, 1, 1)), "a year not yet published keeps the last value")
        assertNull(Rules.valueOn("tfsa.limit", LocalDate(2008, 12, 31)))
    }

    @Test
    fun `the household's value takes the place of the built-in one from its date`() {
        Rules.userValues = listOf(RuleValue("tfsa.limit", null, LocalDate(2027, 1, 1), "7500", builtIn = false, id = "u1"))
        assertEquals(7000, Rules.int("tfsa.limit", LocalDate(2026, 12, 31)))
        assertEquals(7500, Rules.int("tfsa.limit", LocalDate(2027, 1, 1)))
        Rules.userValues = listOf(RuleValue("tfsa.limit", null, LocalDate(2026, 1, 1), "7100", builtIn = false, id = "u2"))
        assertEquals(7100, Rules.int("tfsa.limit", LocalDate(2026, 3, 1)), "on the same date the household's value wins")
    }

    @Test
    fun `a province's own value comes before the value for everywhere`() {
        val rule = Rules.catalogue.firstOrNull { it.perProvince } ?: return
        val on = LocalDate(2099, 1, 1)
        Rules.userValues = listOf(
            RuleValue(rule.key, null, LocalDate(2098, 1, 1), sample(rule.type), builtIn = false, id = "all"),
            RuleValue(rule.key, Province.NU, LocalDate(2098, 6, 1), sample(rule.type, 2), builtIn = false, id = "nu"),
        )
        assertEquals("nu", Rules.valueOn(rule.key, on, Province.NU)?.id)
        assertEquals("all", Rules.valueOn(rule.key, on, null)?.id)
    }

    @Test
    fun `values are checked against their type`() {
        val rate = Rule("x", RuleType.RATE, "test", false)
        Rules.check(rate, "0.05")
        assertFailsWith<RuleException> { Rules.check(rate, "5") }
        assertFailsWith<RuleException> { Rules.check(Rule("b", RuleType.BRACKETS, "test", false), "50000:0.2; 0:0.15") }
        Rules.check(Rule("b", RuleType.BRACKETS, "test", false), "0:0.15; 58523:0.205")
        Rules.check(Rule("d", RuleType.MONTH_DAY, "test", false), "11-15")
        assertFailsWith<RuleException> { Rules.check(Rule("d", RuleType.MONTH_DAY, "test", false), "02-30") }
    }

    private fun sample(type: RuleType, n: Int = 1): String = when (type) {
        RuleType.RATE -> "0.0$n"
        RuleType.AMOUNT, RuleType.NUMBER, RuleType.DAYS -> "$n"
        RuleType.BRACKETS -> "0:0.0$n"
        RuleType.MONTH_DAY -> "01-0$n"
        RuleType.YES_NO -> (n == 1).toString()
        RuleType.LIST -> "$n"
    }
}
