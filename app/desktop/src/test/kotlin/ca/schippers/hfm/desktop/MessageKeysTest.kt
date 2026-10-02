package ca.schippers.hfm.desktop

import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every message key used by the desktop app and the bookkeeping rules exists in both languages
 * (NFR-06). Keys built from enum names (e.g. "accountType.$type") are checked for every value.
 */
class MessageKeysTest {

    private val sources = listOf(File("src/main/kotlin"), File("../../core/books/src/main/kotlin"))
        .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }

    @Test
    fun `every key used in code exists in English and French`() {
        val keys = sources.flatMapTo(sortedSetOf()) { file ->
            val text = file.readText()
            Regex("""\bt\("([A-Za-z0-9_.]+)"""").findAll(text).map { it.groupValues[1] } +
                Regex(""""(error\.[A-Za-z0-9_.]+)"""").findAll(text).map { it.groupValues[1] }
        }
        val enumKeys = listOf(
            "accountType" to ca.schippers.hfm.domain.AccountType.entries,
            "accountKind" to ca.schippers.hfm.domain.AccountKind.entries,
            "status" to ca.schippers.hfm.domain.AccountStatus.entries,
            "memberKind" to ca.schippers.hfm.domain.MemberKind.entries,
            "tax" to ca.schippers.hfm.domain.TaxFlag.entries,
            "category.new" to ca.schippers.hfm.domain.CategoryKind.entries,
            "nav" to Section.entries.map { it.name.lowercase() },
            "lineStatus" to ca.schippers.hfm.books.LineStatus.entries,
            "statementStatus" to ca.schippers.hfm.books.StatementStatus.entries,
            "import.amountMode" to listOf("single", "split"),
            "billKind" to ca.schippers.hfm.books.BillKind.entries,
            "amountKind" to ca.schippers.hfm.books.AmountKind.entries,
            "paymentMethod" to ca.schippers.hfm.books.PaymentMethod.entries,
            "monthDay" to ca.schippers.hfm.calc.schedule.MonthDay.entries,
            "adjust" to ca.schippers.hfm.calc.schedule.BusinessDayAdjust.entries,
            "bills.tab" to listOf("AGENDA", "ALL", "CALENDAR", "SUBSCRIPTIONS", "FORECAST"),
            "repeat" to listOf("ONCE", "WEEKLY", "BI_WEEKLY", "SEMI_MONTHLY", "MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "EVERY_N_DAYS", "EVERY_N_WEEKS", "EVERY_N_MONTHS"),
        ).flatMap { (prefix, values) -> values.map { "$prefix.$it" } }

        val chosenInCode = listOf(
            "bills.overdue", "bills.dueToday", "bills.upcoming", "bills.markPaid", "bills.markReceived", "bills.edit",
            "bills.payingAccount", "bills.depositAccount", "repeat.EVERY_N_DAYS.n", "repeat.EVERY_N_WEEKS.n", "repeat.EVERY_N_MONTHS.n",
        )
        for (language in Language.entries) {
            val missing = (keys + enumKeys + chosenInCode).filterNot { it in Messages.keys(language) }
            assertEquals(emptyList(), missing, "missing in $language")
        }
    }
}
