package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Books
import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.RuleType
import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rates and rules: the value editor turns what is typed into the stored value, and only an administrator adds one. */
class RateRuleInputTest {

    @TempDir
    lateinit var temp: Path

    private val en = Language.ENGLISH.locale
    private val fr = Language.FRENCH.locale

    @AfterTest
    fun forgetHouseholdValues() {
        Rules.userValues = emptyList()
    }

    @Test
    fun `a rate is typed as a percentage and kept as a fraction, with a French decimal comma`() {
        assertEquals("0.09975", RateRuleInput.stored(RuleType.RATE, "9.975", en))
        assertEquals("0.09975", RateRuleInput.stored(RuleType.RATE, "9,975", fr))
        assertEquals("0.09975", RateRuleInput.stored(RuleType.RATE, "9.975", fr), "a dot alone is a decimal point in French too")
        assertEquals("0.13", RateRuleInput.stored(RuleType.RATE, "13 %", fr))
        assertEquals("0.05", RateRuleInput.stored(RuleType.RATE, "5", en))
        assertEquals("9,975", RateRuleInput.typed(RuleType.RATE, "0.09975", fr))
        assertEquals("9.975", RateRuleInput.typed(RuleType.RATE, "0.09975", en))
        assertNull(RateRuleInput.stored(RuleType.RATE, "abc", en))
    }

    @Test
    fun `amounts, days, month-days and lists are read in the user's language`() {
        assertEquals("7500", RateRuleInput.stored(RuleType.AMOUNT, "7 500", fr))
        assertEquals("7500", RateRuleInput.stored(RuleType.AMOUNT, "$7,500.00", en))
        assertEquals("2834.5", RateRuleInput.stored(RuleType.AMOUNT, "2 834,50", fr))
        assertEquals("30", RateRuleInput.stored(RuleType.DAYS, "30", en))
        assertNull(RateRuleInput.stored(RuleType.DAYS, "-3", en))
        assertEquals("11-05", RateRuleInput.stored(RuleType.MONTH_DAY, "11-5", en))
        assertEquals("0.0528; 0.054", RateRuleInput.stored(RuleType.LIST, "0,0528; 0,0540", fr))
        assertEquals("true", RateRuleInput.stored(RuleType.YES_NO, "true", en))
    }

    @Test
    fun `brackets typed as a table of thresholds and rates become the stored text`() {
        val rows = listOf("58 523" to "20,5", "0" to "15", "" to "")
        assertEquals("0:0.15; 58523:0.205", RateRuleInput.storedBrackets(rows, fr), "empty rows are skipped, the rows are put in order")
        assertEquals("0:0.15; 58523:0.205", RateRuleInput.storedBrackets(listOf("0" to "15", "58,523" to "20.5"), en))
        assertEquals(listOf("0" to "15", "58523" to "20,5"), RateRuleInput.typedBrackets("0:0.15; 58523:0.205", fr))
        assertNull(RateRuleInput.storedBrackets(listOf("0" to "x"), en))
        assertNull(RateRuleInput.storedBrackets(listOf("" to ""), en))
    }

    @Test
    fun `the filter finds a rule by name or key, accents ignored`() {
        assertTrue(RateRuleInput.matches("Plafond annuel du CELI", "tfsa.limit", "celi"))
        assertTrue(RateRuleInput.matches("Taxe de vente du Québec", "sales.qst", "quebec"))
        assertTrue(RateRuleInput.matches("TFSA dollar limit", "tfsa.limit", "TFSA.LIM"))
        assertTrue(!RateRuleInput.matches("TFSA dollar limit", "tfsa.limit", "rrsp"))
    }

    @Test
    fun `the screen's help topic and manual chapter is rates-rules, other screens keep their names`() {
        assertEquals("rates-rules", Section.RATE_RULES.helpId)
        assertEquals("rates", Section.RATES.helpId)
        assertEquals("rules", Section.RULES.helpId)
    }

    @Test
    fun `an administrator adds a value, a member cannot`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val dir = temp.resolve("R.hfm")
        val admin = Books(store.create(dir, "R", "perry", "Perry", "password1".toCharArray()).session)
        admin.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray())
        val stored = RateRuleInput.stored(RuleType.AMOUNT, "7 500", fr)!!
        val added = admin.rateRules.add("tfsa.limit", null, LocalDate(2027, 1, 1), stored, "Announced in November")
        assertEquals(BigDecimal("7500"), Rules.decimal("tfsa.limit", LocalDate(2027, 3, 1)))
        assertEquals("perry", admin.users.list().first { it.id == admin.rateRules.origins().getValue(added.id!!).createdBy }.loginName)
        admin.session.close()

        val member = Books(store.unlock(dir, "marie", "password2-long".toCharArray()))
        try {
            assertFailsWith<AccessDeniedException> { member.rateRules.add("tfsa.limit", null, LocalDate(2028, 1, 1), "8000") }
            assertFailsWith<AccessDeniedException> { member.rateRules.delete(added.id!!) }
            assertFailsWith<AccessDeniedException> { member.rateRules.add("tfsa.limit", Province.ON, LocalDate(2028, 1, 1), "8000") }
            assertEquals(BigDecimal("7500"), Rules.decimal("tfsa.limit", LocalDate(2028, 3, 1)), "the member's attempt changed nothing")
        } finally {
            member.session.close()
        }
    }
}
