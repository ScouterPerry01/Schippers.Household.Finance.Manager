package ca.schippers.hfm.sync

import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** BUD-04: budget alerts at 80 % and 100 %, once a month each, in the phone's language. */
class BudgetAlertsTest {

    private val zone = ZoneId.of("America/Toronto")
    private val october = YearMonth.of(2026, 10)
    private val millis = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun reference(vararg budgets: RefBudget, at: Long = millis) = ReferenceData(
        householdName = "H", language = "en", baseCurrency = "CAD", accounts = emptyList(),
        categories = listOf(RefCategory("food", null, "Groceries", "Épicerie", false), RefCategory("fun", null, "Entertainment", "Loisirs", false)),
        payees = emptyList(), people = emptyList(), vehicles = emptyList(), bills = emptyList(), budgets = budgets.toList(), maintenance = emptyList(),
        generatedAtMillis = at,
    )

    @Test
    fun `alerts at 80 and 100 percent, once each, named in the phone's language`() {
        val ref = reference(
            RefBudget("Groceries", "600.00", "-492.10", "CAD", "food"),
            RefBudget("Entertainment", "100.00", "-130.00", "CAD", "fun"),
            RefBudget("Transport", "200.00", "-20.00", "CAD", "car"),
        )
        val first = BudgetAlerts.due(ref, october, zone, french = true, shown = emptySet())
        assertEquals(listOf("Épicerie" to 80, "Loisirs" to 100), first.map { it.categoryName to it.level })

        val shown = first.flatMap { it.keys }.toSet()
        assertEquals(emptyList(), BudgetAlerts.due(ref, october, zone, false, shown), "each is shown once")
        val later = reference(RefBudget("Groceries", "600.00", "-610.00", "CAD", "food"), RefBudget("Entertainment", "100.00", "-150.00", "CAD", "fun"))
        assertEquals(listOf("Groceries" to 100), BudgetAlerts.due(later, october, zone, false, shown).map { it.categoryName to it.level })
        assertEquals(emptyList(), BudgetAlerts.due(later, october.plusMonths(1), zone, false, emptySet()), "figures from last month raise nothing")
    }
}
