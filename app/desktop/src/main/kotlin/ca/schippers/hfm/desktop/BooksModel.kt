package ca.schippers.hfm.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.ImportResult
import ca.schippers.hfm.books.EventReminder
import ca.schippers.hfm.books.GroupInfo
import ca.schippers.hfm.books.MeterUnit
import ca.schippers.hfm.books.UpkeepDue
import ca.schippers.hfm.books.RefillReminder
import ca.schippers.hfm.books.Renewal
import ca.schippers.hfm.books.RenewalKind
import ca.schippers.hfm.books.Reminder
import ca.schippers.hfm.books.SearchResults
import ca.schippers.hfm.books.Species
import ca.schippers.hfm.books.ReconciledChangeException
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

enum class Section { DASHBOARD, CONTACTS, ACCOUNTS, DOCUMENTS, BILLS, BUDGETS, GOALS, FAMILY, SIDE, INVESTMENTS, PLANS, LOANS, REPORTS, TAXES, CALENDAR, HEALTH, MEDICAL, ESTATE, PETS, VEHICLES, TRIPS, ASSETS, UTILITIES, VOLUNTEER, CATEGORIES, PAYEES, RULES, INSTITUTIONS, MEMBERS, RATES, RATE_RULES, PHONES, AI, USERS, BACKUPS, SECURITY, DISPLAY, WALKME, ABOUT }

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

    /** REC-08: an investment statement to open when the Investments screen shows next. */
    var openInvestmentStatementId by mutableStateOf<String?>(null)

    /** Counts from the last import, shown at the top of the reconciliation screen. */
    var lastImport by mutableStateOf<ImportResult?>(null)

    /** Section 3: listens for paired phones while this household is unlocked. */
    val syncServer = SyncServer(books, { today() }) { changed() }

    /** Why the phone listener could not start, if it could not. */
    var syncError by mutableStateOf<String?>(null)

    /** The name the phone shows for this computer. */
    fun desktopName(): String = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull() ?: "Desktop"

    /** CAP-03, CAP-04: what the last document import did. */
    var lastImportMessage by mutableStateOf<String?>(null)

    /** RPT-05: the scheduled reports made since the household was opened. */
    var reportMessage by mutableStateOf<String?>(null)

    /** Section 3.2: what the last look at the transfer folder found. */
    var transferStatus by mutableStateOf<String?>(null)

    /** OTH-03: the last search and its results, shown in a dialog. */
    var search by mutableStateOf<Pair<String, SearchResults>?>(null)

    /** A transaction to open in the register's entry form (from search). */
    var focusTransactionId by mutableStateOf<String?>(null)

    /** A contact to show in the Contacts screen (from search or a record's screen). */
    var focusContactId by mutableStateOf<String?>(null)

    /** A contractor to show in Home and assets (from a contact's page): its jobs when [focusContractorJobs], else its form. */
    var focusContractorId by mutableStateOf<String?>(null)
    var focusContractorJobs by mutableStateOf(false)

    /** A document to open in the documents screen (from search). */
    var focusDocumentId by mutableStateOf<String?>(null)

    /** A change that needs confirmation because it touches reconciled transactions. */
    var pendingReconciledChange by mutableStateOf<(() -> Unit)?>(null)

    val language: Language get() = app.language

    /** CAL-08: what the calendar hides for this user on this computer (kinds as "K:<kind>", people as "P:<id>"). */
    var calendarHidden by mutableStateOf(app.calendarHidden(session.userId))
        private set

    fun changeCalendarHidden(hidden: Set<String>) {
        calendarHidden = hidden
        app.setCalendarHidden(session.userId, hidden)
    }

    private var calendarViewState by mutableStateOf(app.calendarView(session.userId))

    /** CAL-07: the calendar view last shown to this user on this computer. */
    var calendarView: String?
        get() = calendarViewState
        set(value) {
            calendarViewState = value
            if (value != null) app.setCalendarView(session.userId, value)
        }

    /** CAL-07: the date the calendar's views are on, kept while moving between screens. */
    var calendarDate by mutableStateOf(today())

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
        return t("common.labelValue", o.bill.name, "$whenText (${if (o.amountKnown) "" else "≈ "}${money(o.shownAmount)})")
    }

    /** One line per reminder of any kind (bills, appointments, refills) and where it leads. */
    data class ReminderLine(val key: String, val text: String, val section: Section)

    fun reminderLines(): List<ReminderLine> {
        val now = java.time.LocalDateTime.now().let { LocalDateTime(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) }
        val bills = reminders().map { ReminderLine("bill:${it.occurrence.bill.id}:${it.occurrence.dueDate}:${it.daysBefore}", describe(it), Section.BILLS) }
        val events = runCatching { books.calendar.reminders(now) }.getOrDefault(emptyList()).map { r ->
            val lead = r.occurrence.event.reminderMinutes.filter { r.minutesBefore <= it }.minOrNull()
            ReminderLine("event:${r.occurrence.event.id}:${r.occurrence.date}:$lead", describe(r), Section.CALENDAR)
        }
        val refills = runCatching { books.health.refillReminders(today()) }.getOrDefault(emptyList())
            .map { ReminderLine("refill:${it.medication.id}:${it.due}", describe(it), Section.HEALTH) }
        val renewals = runCatching { books.renewals(today()) }.getOrDefault(emptyList())
            .map { ReminderLine("renewal:${it.kind}:${it.subjectId}:${it.date}:${it.detail}", describe(it), renewalSection(it.kind)) }
        val maintenance = runCatching { books.upkeepDue(today()) }.getOrDefault(emptyList())
            .map { ReminderLine("maintenance:${it.taskId}:${it.status.dueDate}:${it.status.dueUsage}:${it.status.state}", describe(it), if (it.vehicle) Section.VEHICLES else Section.ASSETS) }
        // INV-09, INV-10: over-contributions, RRIF and LIF minimums, RRSPs to convert.
        val plans = runCatching { books.plans.warnings(today()) }.getOrDefault(emptyList())
            .map { w -> ReminderLine("plan:${w.key}:${w.subjectId}", t(w.key, *w.args.map { a -> if (a is Money) money(a) else a }.toTypedArray()), Section.PLANS) }
        // ACC-06: low balances, card limits and unusual activity, on the accounts that ask for them.
        val alerts = runCatching { books.accountAlerts.alerts(today()) }.getOrDefault(emptyList()).map { ReminderLine(it.key, describe(it), Section.ACCOUNTS) }
        // UTL-01: a meter that used more than usual last month (or the month before).
        val utilities = runCatching { books.utilities.unusual(today()) }.getOrDefault(emptyList())
            .map { ReminderLine("utility:${it.meter.id}:${it.month.use.year}-${it.month.use.month}", describe(it), Section.UTILITIES) }
        return events + bills + refills + renewals + maintenance + plans + alerts + utilities
    }

    /** UTL-01: "Cottage hydro: unusual use in September 2026 (+35 % on the same month last year)". */
    fun describe(u: ca.schippers.hfm.books.UnusualUse): String {
        val month = monthName(u.month.use, midSentence = true)
        val change = u.month.changePercent
        return if (change != null) {
            t("utilities.unusualReminder", u.meter.name, month, (if (change.signum() > 0) "+" else "") + MoneyFormat.formatDecimal(change, language.locale))
        } else {
            t("utilities.unusualReminder.recent", u.meter.name, month)
        }
    }

    /** ACC-06: "Chequing: balance $412.00, below $500.00". */
    fun describe(a: ca.schippers.hfm.books.AccountAlert): String = when (a.kind) {
        ca.schippers.hfm.books.AccountAlertKind.LOW_BALANCE -> t("alert.LOW_BALANCE", a.account.name, money(a.amount), a.threshold?.let(::money) ?: "")
        ca.schippers.hfm.books.AccountAlertKind.OVER_LIMIT -> t("alert.OVER_LIMIT", a.account.name, money(a.amount), a.threshold?.let(::money) ?: "")
        ca.schippers.hfm.books.AccountAlertKind.NEAR_LIMIT -> t("alert.NEAR_LIMIT", a.account.name, a.percent ?: 0, a.threshold?.let(::money) ?: "")
        ca.schippers.hfm.books.AccountAlertKind.LARGE_TRANSACTION ->
            t("alert.LARGE_TRANSACTION", a.account.name, money(a.amount.abs()), a.payee ?: "—", a.date?.let(::date) ?: "", a.usual?.let(::money) ?: "")
        ca.schippers.hfm.books.AccountAlertKind.NEW_PAYEE -> t("alert.NEW_PAYEE", a.account.name, money(a.amount.abs()), a.payee ?: "—", a.date?.let(::date) ?: "")
    }

    /** "Civic: oil change due 2026-11-03 or at 55,700 km". */
    fun describe(m: UpkeepDue): String {
        val s = m.status
        val due = listOfNotNull(s.dueDate?.let(::date), s.dueUsage?.let { usage(it, m.unit) }).joinToString(" ${t("vehicles.or")} ")
        return t("common.labelValue", m.subjectName, "${m.taskName} ${t("maintenance.${s.state}", due)}")
    }

    /** "55,700 km" or "120 h". */
    fun usage(value: Int, unit: MeterUnit?): String =
        t(if (unit == MeterUnit.HOURS) "maintenance.hours" else "vehicles.km", String.format(language.locale, "%,d", value))

    /** "Rex: municipal licence expires in 12 days". */
    fun describe(r: Renewal): String {
        val whenText = if (r.daysLeft < 0) t("renewal.overdue", -r.daysLeft) else t("renewal.inDays", r.daysLeft)
        val what = "${t("renewalKind.${r.kind}")}${renewalDetail(r)?.let { " ($it)" }.orEmpty()} $whenText"
        return r.subjectName.ifBlank { null }?.let { t("common.labelValue", it, what) } ?: what
    }

    /**
     * A renewal's detail as shown; for a tax instalment, the authority named in the user's language;
     * for a vehicle warranty with no provider, its kind (sent as the kind's name) in the user's language.
     */
    fun renewalDetail(r: Renewal): String? = when (r.kind) {
        RenewalKind.TAX_INSTALMENT -> r.detail?.let { t("taxAuthority.$it") }
        RenewalKind.WARRANTY -> r.detail?.split(" · ")?.joinToString(" · ") { part ->
            if (ca.schippers.hfm.books.WarrantyKind.entries.any { it.name == part }) t("warrantyKind.$part") else part
        }
        else -> r.detail
    }

    fun renewalSection(kind: RenewalKind): Section = when (kind) {
        RenewalKind.PET_LICENCE, RenewalKind.PET_INSURANCE -> Section.PETS
        RenewalKind.LOAN_RENEWAL -> Section.LOANS
        RenewalKind.CARD_ANNUAL_FEE, RenewalKind.CARD_PAYMENT_DUE -> Section.ACCOUNTS
        RenewalKind.MEDICAL_CLAIM -> Section.MEDICAL
        RenewalKind.ASSET_WARRANTY, RenewalKind.INSURANCE_RENEWAL -> Section.ASSETS
        RenewalKind.TAX_INSTALMENT -> Section.TAXES
        RenewalKind.SECURITY_MATURITY -> Section.INVESTMENTS
        RenewalKind.FUEL_ORDER -> Section.UTILITIES
        else -> Section.VEHICLES
    }

    /** "Garage: winter tires: tomorrow at 09:30". */
    fun describe(r: EventReminder): String {
        val o = r.occurrence
        val time = o.event.startTime?.let { "%02d:%02d".format(it.hour, it.minute) }
        val whenText = when {
            r.minutesBefore < 60 && time != null -> t("reminder.event.inMinutes", r.minutesBefore)
            o.date == today() -> if (time != null) t("reminder.event.todayAt", time) else t("reminder.event.today")
            o.date == today().plus(DatePeriod(days = 1)) -> if (time != null) t("reminder.event.tomorrowAt", time) else t("reminder.event.tomorrow")
            else -> if (time != null) t("reminder.event.onAt", date(o.date), time) else t("reminder.event.on", date(o.date))
        }
        return t("common.labelValue", o.event.title, whenText)
    }

    /** "Atorvastatin (Marie): refill due in 4 days". */
    fun describe(r: RefillReminder): String {
        val person = peopleAndPets(includeArchived = true).firstOrNull { it.id == r.medication.memberId }?.name
        val base = when {
            r.daysLeft < 0 -> t("reminder.refillOverdue", -r.daysLeft)
            r.daysLeft == 0 -> t("reminder.refillToday")
            else -> t("reminder.refillIn", r.daysLeft)
        }
        val renew = if (r.medication.needsRenewal) " · " + t("reminder.renew") else ""
        return t("common.labelValue", "${r.medication.name}${person?.let { " ($it)" }.orEmpty()}", "$base$renew")
    }

    /** Someone a record can be about: a household member or a pet (PET-03). */
    data class Who(val id: String, val name: String, val species: Species?) {
        val isPet: Boolean get() = species != null
    }

    /** People first, then pets, e.g. "Léa", "Rex (dog)". */
    fun peopleAndPets(includeArchived: Boolean = false): List<Who> =
        books.members.list(includeArchived).map { Who(it.id, it.displayName, null) } +
            runCatching { books.pets.list(includeArchived) }.getOrDefault(emptyList()).map { Who(it.id, "${it.name} (${t("species.${it.species}").lowercase(language.locale)})", it.species) }

    /** Vehicles a transaction can be linked to (VEH-09), as id and name. */
    fun vehicleChoices(): List<Pair<String, String>> = runCatching { books.vehicles.list() }.getOrDefault(emptyList()).map { it.id to it.name }

    /** The person or pet the Health screen shows first, e.g. when coming from the Pets screen. */
    var healthSubjectId by mutableStateOf<String?>(null)

    /** Account groups the user may edit, for "store in" choices (CAL-06). */
    fun editableGroups(): List<GroupInfo> = books.groups().filter { it.level == PermissionLevel.EDIT }

    /** The user's own private group if there is one, otherwise the first shared group. */
    fun defaultGroupForPersonalRecords(): GroupInfo? =
        editableGroups().let { groups -> groups.firstOrNull { it.ownerUserId == session.userId } ?: groups.firstOrNull() }

    /** CON-03: contacts belong to the household, so they go in its first shared group unless the user chooses otherwise. */
    fun defaultGroupForContacts(): GroupInfo? = editableGroups().let { groups -> groups.firstOrNull { !it.isPrivate } ?: groups.firstOrNull() }

    /** Creates "<name> - private" for personal records. Returns the new group's id. */
    fun createPrivateGroup(): String? = act {
        val name = session.core.coreQueries.userById(session.userId).executeAsOne().display_name
        session.createGroup(t("group.privateName", name), private = true)
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
