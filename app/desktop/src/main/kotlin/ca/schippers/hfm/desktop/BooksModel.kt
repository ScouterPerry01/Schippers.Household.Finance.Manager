package ca.schippers.hfm.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.ImportResult
import ca.schippers.hfm.books.Reminder
import ca.schippers.hfm.books.ReconciledChangeException
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

enum class Section { DASHBOARD, ACCOUNTS, BILLS, BUDGETS, REPORTS, CATEGORIES, PAYEES, RULES, INSTITUTIONS, MEMBERS, RATES, BACKUPS }

/**
 * UI state for an unlocked household. [revision] increases after every successful change, and
 * screens re-read their data when it does. Errors are shown in the user's language.
 */
class BooksModel(val session: HouseholdSession, private val app: AppState) {
    val books = Books(session)

    var revision by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
    var section by mutableStateOf(Section.DASHBOARD)

    /** Report choices, kept while moving between screens. */
    val reportState = ReportState()
    var selectedAccountId by mutableStateOf<String?>(null)

    /** The statement being reconciled; the register is shown again when null. */
    var reconcilingStatementId by mutableStateOf<String?>(null)

    /** Counts from the last import, shown at the top of the reconciliation screen. */
    var lastImport by mutableStateOf<ImportResult?>(null)

    /** A change that needs confirmation because it touches reconciled transactions. */
    var pendingReconciledChange by mutableStateOf<(() -> Unit)?>(null)

    val language: Language get() = app.language

    fun t(key: String, vararg args: Any): String = app.t(key, *args)

    fun money(m: Money): String = MoneyFormat.format(m, language.locale)

    fun date(d: LocalDate): String = d.toString()

    /**
     * Runs a change. On success the screens refresh; on failure the error is shown and null returned.
     * If the change touches a reconciled transaction, [retryConfirmed] is offered to the user first.
     */
    fun <T> act(retryConfirmed: (() -> T)? = null, block: () -> T): T? = try {
        block().also { changed() }
    } catch (_: ReconciledChangeException) {
        if (retryConfirmed != null) pendingReconciledChange = { act { retryConfirmed() } }
        null
    } catch (e: Exception) {
        error = describe(e)
        null
    }

    /** BILL-04: what to remind about today, refreshed after every change. */
    fun reminders(): List<Reminder> = runCatching { books.bills.reminders(today()) }.getOrDefault(emptyList())

    /** One line per reminder, e.g. "Hydro-Québec: due in 7 days (≈ 142,00 $)". */
    fun describe(reminder: Reminder): String {
        val o = reminder.occurrence
        val whenText = when {
            o.bill.isSubscription && o.dueDate == o.bill.cancelBy -> t("reminder.cancelBy", reminder.daysBefore)
            reminder.daysBefore < 0 -> t("reminder.overdue", -reminder.daysBefore)
            reminder.daysBefore == 0 -> t("reminder.today")
            else -> t("reminder.inDays", reminder.daysBefore)
        }
        return "${o.bill.name}: $whenText (${if (o.amountKnown) "" else "≈ "}${money(o.amount)})"
    }

    fun changed() {
        revision++
    }

    fun describe(e: Throwable): String = when (e) {
        is ValidationException -> e.message(language)
        is AccessDeniedException -> t("error.accessDenied")
        else -> t("error.generic", e.message ?: e.javaClass.simpleName)
    }
}
