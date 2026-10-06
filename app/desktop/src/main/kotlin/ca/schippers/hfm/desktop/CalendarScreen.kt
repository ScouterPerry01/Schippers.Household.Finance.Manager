package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.CalendarEvent
import ca.schippers.hfm.books.CalendarItem
import ca.schippers.hfm.books.CalendarKind
import ca.schippers.hfm.books.Category
import ca.schippers.hfm.books.Driver
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.EventDraft
import ca.schippers.hfm.books.GroupInfo
import ca.schippers.hfm.books.HealthDue
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.OccurrenceMark
import ca.schippers.hfm.books.OccurrenceStatus
import ca.schippers.hfm.books.Renewal
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.schedule.BusinessDayAdjust
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.MonthDay
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** CAL-07: the calendar's views, in the order of the tabs. */
internal enum class CalendarView { AGENDA, DAY, WEEK, MONTH, YEAR }

/** CAL-01 to CAL-11: appointments, activities and schedules, shown with bills and every other due date. */
@Composable
fun CalendarScreen(model: BooksModel) {
    // The view and its date live in the model, so they stay while moving between screens.
    val view = model.calendarView?.let { v -> CalendarView.entries.firstOrNull { it.name == v } } ?: CalendarView.AGENDA
    val anchor = model.calendarDate
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var creating by remember { mutableStateOf<EventDraft?>(null) }
    var schedules by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(true) }

    val (from, to) = period(view, anchor)
    // The day before too, so a night shift that started then shades the first morning (CAL-10).
    val all = remember(model.revision, from, to) { model.books.calendar.items(from.minus(DatePeriod(days = 1)), to) }
    val hidden = model.calendarHidden
    val shown = all.filter { visible(it, hidden) }
    val people = remember(model.revision) { CalendarPeople.of(model) }
    val names = remember(model.revision) { lookups(model) }
    val openDay = { d: LocalDate -> model.calendarDate = d; model.calendarView = CalendarView.DAY.name }
    val add = { d: LocalDate, time: LocalTime? -> creating = model.newEventDraft(d)?.let { if (time != null) it.copy(startTime = time) else it } }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.t("nav.calendar"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { schedules = true }) { Text(model.t("schedule.title")) }
            BroughtInButtons(model)
            Button(onClick = { add(if (view == CalendarView.AGENDA) today() else anchor, null) }) { Text(model.t("calendar.add")) }
        }
        PrimaryTabRow(selectedTabIndex = view.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in CalendarView.entries) {
                Tab(selected = view == t, onClick = { model.calendarView = t.name }, text = { Text(model.t("calendar.tab.${t.name}")) })
            }
        }
        // CAL-07: today, previous, next and a date picker, for every view.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { model.calendarDate = today() }) { Text(model.t("calendar.today")) }
            SymbolButton(model.t("calendar.previous"), "◀") { model.calendarDate = step(view, anchor, -1) }
            SymbolButton(model.t("calendar.next"), "▶") { model.calendarDate = step(view, anchor, 1) }
            Text(periodTitle(model, view, anchor), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedButton(onClick = { picking = true }) { Text(model.t("calendar.goTo")) }
            Box(Modifier.weight(1f))
            TextButton(onClick = { panel = !panel }) { Text(model.t(if (panel) "calendar.hidePanel" else "calendar.showPanel")) }
        }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) {
                val inRange = shown.filter { it.date in from..to }
                when (view) {
                    CalendarView.AGENDA -> AgendaContent(model, inRange, names, people, model.t("calendar.nothing"), onEdit = { editing = it })
                    CalendarView.DAY -> DayView(model, anchor, shown, names, people, onEdit = { editing = it }, onAdd = add)
                    CalendarView.WEEK -> WeekView(model, from, shown, names, people, onEdit = { editing = it }, onOpenDay = openDay, onAdd = add)
                    CalendarView.MONTH -> MonthGrid(model, anchor, inRange, people, onEdit = { editing = it }, onOpenDay = openDay)
                    CalendarView.YEAR -> YearView(model, anchor.year, inRange, onOpenDay = openDay)
                }
            }
            if (panel) ShowPanel(model, all, people)
        }
    }

    creating?.let { draft -> EventDialog(model, null, draft) { creating = null } }
    editing?.let { event -> EventDialog(model, event, null) { editing = null } }
    if (schedules) SchedulesDialog(model, null) { schedules = false }
    if (picking) GoToDateDialog(model, anchor, onDismiss = { picking = false }) { model.calendarDate = it; picking = false }
}

/** The dates a view shows around [anchor]. */
internal fun period(view: CalendarView, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (view) {
    CalendarView.AGENDA -> anchor to anchor.plus(DatePeriod(days = AGENDA_DAYS - 1))
    CalendarView.DAY -> anchor to anchor
    CalendarView.WEEK -> mondayOf(anchor).let { it to it.plus(DatePeriod(days = 6)) }
    CalendarView.MONTH -> LocalDate(anchor.year, anchor.month, 1).let { it to it.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)) }
    CalendarView.YEAR -> LocalDate(anchor.year, 1, 1) to LocalDate(anchor.year, 12, 31)
}

internal fun step(view: CalendarView, anchor: LocalDate, by: Int): LocalDate = when (view) {
    CalendarView.AGENDA -> anchor.plus(DatePeriod(days = AGENDA_DAYS * by))
    CalendarView.DAY -> anchor.plus(DatePeriod(days = by))
    CalendarView.WEEK -> anchor.plus(DatePeriod(days = 7 * by))
    CalendarView.MONTH -> anchor.plus(DatePeriod(months = by))
    CalendarView.YEAR -> anchor.plus(DatePeriod(years = by))
}

internal fun mondayOf(d: LocalDate): LocalDate = d.minus(DatePeriod(days = d.dayOfWeek.ordinal))

private const val AGENDA_DAYS = 60

private fun periodTitle(model: BooksModel, view: CalendarView, anchor: LocalDate): String = when (view) {
    CalendarView.AGENDA -> model.t("calendar.fromDay", dayTitle(model, anchor))
    CalendarView.DAY -> dayTitle(model, anchor)
    CalendarView.WEEK -> mondayOf(anchor).let { model.t("calendar.weekOf", model.date(it), model.date(it.plus(DatePeriod(days = 6)))) }
    CalendarView.MONTH -> monthTitle(model, anchor)
    CalendarView.YEAR -> anchor.year.toString()
}

internal fun monthTitle(model: BooksModel, d: LocalDate): String {
    val locale = model.language.locale
    return java.time.YearMonth.of(d.year, d.month.ordinal + 1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
}

/** A new event in the user's usual group; medical ones default to a private group. */
internal fun BooksModel.newEventDraft(date: LocalDate, category: EventCategory = EventCategory.OTHER, memberId: String? = null): EventDraft? {
    val group = (if (category == EventCategory.MEDICAL) defaultGroupForPersonalRecords() else editableGroups().firstOrNull { !it.isPrivate })
        ?: editableGroups().firstOrNull()
    if (group == null) {
        error = t("error.noEditableGroup")
        return null
    }
    return EventDraft(group.id, "", category, date, LocalTime(9, 0), 60, memberId = memberId)
}

// --- Show and hide (CAL-08) -------------------------------------------------------------------------

/** The person an item is for, if any: the calendar's people filter applies to it. */
internal fun personOf(item: CalendarItem): String? = when (item) {
    is CalendarItem.Event -> item.occurrence.event.memberId
    is CalendarItem.Health -> item.due.memberId
    is CalendarItem.Schedule -> item.day.memberId
    else -> null
}

/** Whether the user's choices show [item]: its kind is not hidden, nor the person it is for. */
internal fun visible(item: CalendarItem, hidden: Set<String>): Boolean =
    "K:${item.kind.name}" !in hidden && personOf(item)?.let { "P:$it" !in hidden } != false

/**
 * The household's people and pets, each with a colour of its own for schedule bars (CAL-10). The
 * colours are dark enough for white text (WCAG AA).
 */
internal class CalendarPeople(val list: List<BooksModel.Who>) {
    private val index = list.withIndex().associate { (i, w) -> w.id to i }

    fun color(memberId: String?): Color = PERSON_COLORS[(index[memberId] ?: 0) % PERSON_COLORS.size]

    fun name(memberId: String?): String = list.firstOrNull { it.id == memberId }?.name.orEmpty()

    fun order(memberId: String?): Int = index[memberId] ?: Int.MAX_VALUE

    companion object {
        fun of(model: BooksModel) = CalendarPeople(model.peopleAndPets())

        private val PERSON_COLORS = listOf(
            Color(0xFF1565C0), Color(0xFFAD1457), Color(0xFF2E7D32), Color(0xFFBF360C),
            Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFF4E342E), Color(0xFF283593),
        )
    }
}

/** CAL-08: what the calendar shows, by kind and by person; remembered for this user on this computer. */
@Composable
private fun ShowPanel(model: BooksModel, all: List<CalendarItem>, people: CalendarPeople) {
    val hidden = model.calendarHidden
    fun toggle(key: String, on: Boolean) = model.changeCalendarHidden(if (on) hidden - key else hidden + key)
    Column(Modifier.width(210.dp).padding(start = 12.dp).verticalScroll(rememberScrollState())) {
        Text(model.t("calendar.show"), style = MaterialTheme.typography.titleSmall)
        for (k in CalendarKind.entries) {
            // Brought-in calendars only once there are some (or when hidden, so they can be shown again).
            if (k == CalendarKind.IMPORTED && all.none { it.kind == k } && "K:${k.name}" !in hidden) continue
            LabeledCheckbox(model.t("calendarKind.${k.name}"), "K:${k.name}" !in hidden) { toggle("K:${k.name}", it) }
        }
        if (people.list.isNotEmpty()) {
            Text(model.t("calendar.people"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            for (p in people.list) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(people.color(p.id)))
                    LabeledCheckbox(p.name, "P:${p.id}" !in hidden) { toggle("P:${p.id}", it) }
                }
            }
        }
        if (hidden.isNotEmpty()) TextButton(onClick = { model.changeCalendarHidden(emptySet()) }) { Text(model.t("calendar.showAll")) }
    }
}

// --- Agenda ---------------------------------------------------------------------------------------

/**
 * Everything from [from] to [to], day by day. With [memberId] only that person's medical
 * appointments are shown (the Health screen).
 */
@Composable
internal fun AgendaList(model: BooksModel, from: LocalDate, to: LocalDate, memberId: String? = null, onEdit: (CalendarEvent) -> Unit) {
    val books = model.books
    val items = remember(model.revision, from, to, memberId) {
        books.calendar.items(from, to).filter { item ->
            memberId == null || (item is CalendarItem.Event && item.occurrence.event.memberId == memberId && item.occurrence.event.category in setOf(EventCategory.MEDICAL, EventCategory.PET))
        }
    }
    val names = remember(model.revision) { lookups(model) }
    val people = remember(model.revision) { CalendarPeople.of(model) }
    AgendaContent(model, items, names, people, model.t(if (memberId == null) "calendar.nothing" else "health.noAppointments"), onEdit)
}

/** The agenda's lines, day by day; each day's schedules show as bars under its heading (CAL-10). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgendaContent(model: BooksModel, items: List<CalendarItem>, names: Lookups, people: CalendarPeople, empty: String, onEdit: (CalendarEvent) -> Unit) {
    LazyColumn {
        if (items.isEmpty()) item { Text(empty, Modifier.padding(8.dp)) }
        val byDay = items.groupBy { it.date }
        for ((day, list) in byDay) {
            item(key = "d-$day") {
                Text(dayTitle(model, day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            val bars = list.filterIsInstance<CalendarItem.Schedule>()
            if (bars.isNotEmpty()) {
                item(key = "s-$day") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (b in bars.sortedBy { people.order(it.day.memberId) }) ScheduleBar(model, b.day, people, Modifier.width(260.dp))
                    }
                }
            }
            items(list.filter { it !is CalendarItem.Schedule }, key = { itemKey(it) }) { item -> AgendaRow(model, item, names, people, onEdit) }
        }
    }
}

internal fun itemKey(item: CalendarItem): String = when (item) {
    is CalendarItem.Event -> "e-${item.occurrence.event.id}-${item.date}"
    is CalendarItem.Bill -> "b-${item.occurrence.bill.id}-${item.date}"
    is CalendarItem.Health -> "h-${item.due.javaClass.simpleName}-${item.due.memberId}-${item.date}-${healthTitle(item.due)}"
    is CalendarItem.Renewal -> "r-${item.renewal.kind}-${item.renewal.subjectId}-${item.date}-${item.renewal.detail}"
    is CalendarItem.Maintenance -> "m-${item.due.taskId}-${item.date}"
    is CalendarItem.Schedule -> "s-${item.day.schedule.id}-${item.date}"
    is CalendarItem.Imported -> importedKey(item)
}

/** Names of people, providers and accounts, for the agenda lines. */
internal class Lookups(val people: Map<String, String>, val providers: Map<String, String>, val accounts: Map<String, String>)

internal fun lookups(model: BooksModel): Lookups {
    val books = model.books
    return Lookups(
        model.peopleAndPets(includeArchived = true).associate { it.id to it.name },
        runCatching { books.health.providers() }.getOrDefault(emptyList()).associate { it.id to it.name },
        books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name },
    )
}

internal fun dayTitle(model: BooksModel, day: LocalDate): String {
    val locale = model.language.locale
    val name = java.time.LocalDate.of(day.year, day.month.ordinal + 1, day.day)
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    val relative = when (day) {
        today() -> " · " + model.t("calendar.today")
        today().plus(DatePeriod(days = 1)) -> " · " + model.t("calendar.tomorrow")
        else -> ""
    }
    return "$name ${model.date(day)}$relative"
}

/** "Municipal licence (Québec)". */
private fun renewalTitle(model: BooksModel, r: Renewal): String = model.t("renewalKind.${r.kind}") + (model.renewalDetail(r)?.let { " ($it)" }.orEmpty())

internal fun time(t: LocalTime?): String? = t?.let { "%02d:%02d".format(it.hour, it.minute) }

/** "8:00", as on schedule bars. */
internal fun clock(t: LocalTime): String = "%d:%02d".format(t.hour, t.minute)

private fun healthTitle(due: HealthDue): String = when (due) {
    is HealthDue.Refill -> due.medication.name
    is HealthDue.TestFollowUp -> due.test.name
    is HealthDue.ImmunizationDue -> due.immunization.vaccine
}

/** CAL-11: "Sam" or the name typed in. */
internal fun driverName(names: Lookups, d: Driver?): String? = d?.let { it.name ?: names.people[it.memberId] }

/** CAL-11: "There: Sam · Back: Jen", or null when no one is set. */
internal fun driversText(model: BooksModel, names: Lookups, there: Driver?, back: Driver?): String? {
    val a = driverName(names, there)?.let { model.t("calendar.driverThere.short", it) }
    val b = driverName(names, back)?.let { model.t("calendar.driverBack.short", it) }
    return listOfNotNull(a, b).joinToString(" · ").ifEmpty { null }
}

/**
 * One item of the agenda: its time (or what it is), title and details, and its actions. [compact] puts
 * the actions under the text, for the Day view's narrow list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgendaRow(model: BooksModel, item: CalendarItem, names: Lookups, people: CalendarPeople, onEdit: (CalendarEvent) -> Unit, compact: Boolean = false) {
    val books = model.books
    var drivers by remember { mutableStateOf(false) }
    var cost by remember { mutableStateOf(false) }
    val lead: String
    val title: String
    var struck = false
    val detail: String?
    val actions: @Composable () -> Unit
    when (item) {
        is CalendarItem.Event -> {
            val o = item.occurrence
            val e = o.event
            lead = time(e.startTime) ?: model.t("calendar.allDay")
            title = e.title + (o.mark?.let { " (${model.t("calendar.mark.${it.name}")})" }.orEmpty())
            struck = o.mark == OccurrenceMark.CANCELLED
            detail = listOfNotNull(
                model.t("eventCategory.${e.category}"),
                e.durationMinutes?.takeIf { e.startTime != null }?.let { durationText(model, it) },
                e.location,
                e.memberId?.let { names.people[it] },
                e.providerId?.let { names.providers[it] },
                e.accountId?.let { names.accounts[it] },
                e.recurrence?.let { describeRecurrence(model, it) },
                driversText(model, names, o.driverThere, o.driverBack),
                e.cost?.let { model.money(it) + if (o.costTransactionId != null) " · " + model.t("calendar.costRecorded") else "" },
            ).joinToString(" · ")
            actions = {
                if (e.category == EventCategory.ACTIVITY) {
                    TextButton(onClick = { drivers = true }) { Text(model.t("calendar.drivers")) }
                    if (e.cost != null && o.costTransactionId == null) TextButton(onClick = { cost = true }) { Text(model.t("calendar.recordCost")) }
                }
                if (o.mark != null) {
                    TextButton(onClick = { model.act { books.calendar.mark(e.id, o.date, null) } }) { Text(model.t("calendar.undo")) }
                } else {
                    OutlinedButton(onClick = { model.act { books.calendar.mark(e.id, o.date, OccurrenceMark.DONE) } }) { Text(model.t("calendar.markDone")) }
                    TextButton(onClick = { model.act { books.calendar.mark(e.id, o.date, OccurrenceMark.CANCELLED) } }) { Text(model.t("calendar.markCancelled")) }
                }
                TextButton(onClick = { onEdit(e) }) { Text(model.t("common.edit")) }
                if (drivers) DriversDialog(model, o) { drivers = false }
                if (cost) RecordCostDialog(model, o) { cost = false }
            }
        }
        is CalendarItem.Schedule -> {
            val d = item.day
            lead = "${clock(d.start)}–${clock(d.end)}"
            title = listOfNotNull(people.name(d.memberId), model.t("scheduleKind.${d.schedule.kind}"), d.schedule.label).joinToString(" · ")
            detail = if (d.changed) model.t("schedule.changedHours") else null
            actions = {
                if (d.changed) {
                    TextButton(onClick = { model.act { books.schedules.removeException(d.schedule.id, d.date) } }) { Text(model.t("schedule.usualHours")) }
                } else {
                    TextButton(onClick = {
                        model.act { books.schedules.setException(d.schedule.id, ca.schippers.hfm.books.ScheduleException(d.date, off = true)) }
                    }) { Text(model.t("schedule.dayOff")) }
                }
            }
        }
        is CalendarItem.Imported -> {
            // CSY-04: an item brought in from a phone, read-only.
            val i = item.item
            lead = when {
                i.allDay -> model.t("calendar.allDay")
                item.date == i.startDate -> time(i.startTime)!!
                else -> model.t("calendar.broughtIn.continues")
            }
            title = importedTitle(model, i)
            detail = listOfNotNull(
                i.startTime?.let { s -> i.endTime?.let { e -> model.t("calendar.broughtIn.range", time(s)!!, time(e)!!) } },
                i.location,
                model.t("calendar.broughtIn.from", importedSource(model, i), i.ownerName),
                model.t("calendar.broughtIn.visibility.${i.visibility}"),
            ).joinToString(" · ")
            actions = { Text(model.t("calendar.broughtIn.readOnly"), style = MaterialTheme.typography.bodySmall) }
        }
        else -> {
            lead = itemLabel(model, item)
            title = itemTitle(model, item)
            detail = itemDetail(model, item, names)
            actions = {
                itemSection(model, item)?.let { section ->
                    TextButton(onClick = { model.section = section }) {
                        Text(model.t(if (section == Section.BILLS) "calendar.openBills" else if (section == Section.HEALTH) "calendar.openHealth" else "calendar.open.${section.name}"))
                    }
                }
            }
        }
    }
    val titleText = @Composable {
        Text(
            if (compact) "$lead · $title" else title, fontWeight = FontWeight.Medium, maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        )
        detail?.ifEmpty { null }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 4 else 2, overflow = TextOverflow.Ellipsis) }
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        if (compact) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Box(Modifier.width(4.dp).height(36.dp).border(2.dp, kindColor(item, people)))
                Column(Modifier.padding(start = 8.dp)) {
                    titleText()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.Center) { actions() }
                }
            }
        } else {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(36.dp).border(2.dp, kindColor(item, people)))
                Text(lead, Modifier.width(110.dp).padding(start = 12.dp))
                Column(Modifier.weight(1f)) { titleText() }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
    }
}

/** The word in an agenda line's time column for items that have no time. */
private fun itemLabel(model: BooksModel, item: CalendarItem): String = when (item) {
    is CalendarItem.Bill -> model.t("calendar.bill")
    is CalendarItem.Health -> model.t("calendar.health")
    is CalendarItem.Renewal -> model.t("calendar.renewal")
    is CalendarItem.Maintenance -> model.t("calendar.maintenance")
    else -> ""
}

/** An item's name in one line, as the month grid and the agenda show it. */
internal fun itemTitle(model: BooksModel, item: CalendarItem): String = when (item) {
    is CalendarItem.Event -> item.occurrence.event.title
    is CalendarItem.Bill -> item.occurrence.bill.name
    is CalendarItem.Health -> model.t("healthDue.${item.due.javaClass.simpleName}", healthTitle(item.due))
    is CalendarItem.Renewal -> renewalTitle(model, item.renewal)
    is CalendarItem.Maintenance -> item.due.taskName
    is CalendarItem.Schedule -> model.t("scheduleKind.${item.day.schedule.kind}")
    is CalendarItem.Imported -> importedTitle(model, item.item)
}

private fun itemDetail(model: BooksModel, item: CalendarItem, names: Lookups): String? = when (item) {
    is CalendarItem.Bill -> (if (item.occurrence.amountKnown) "" else "≈ ") + model.money(item.occurrence.amount) + " · " + model.t("occurrenceStatus.${item.occurrence.status}")
    is CalendarItem.Health -> names.people[item.due.memberId].orEmpty()
    is CalendarItem.Renewal -> item.renewal.subjectName
    is CalendarItem.Maintenance -> item.due.subjectName
    else -> null
}

/** The screen an item that comes from elsewhere is managed on. */
internal fun itemSection(model: BooksModel, item: CalendarItem): Section? = when (item) {
    is CalendarItem.Bill -> Section.BILLS
    is CalendarItem.Health -> Section.HEALTH
    is CalendarItem.Renewal -> model.renewalSection(item.renewal.kind)
    is CalendarItem.Maintenance -> if (item.due.vehicle) Section.VEHICLES else Section.ASSETS
    else -> null
}

/** "Alex · Work 8:00–16:30 (Hospital)". */
internal fun scheduleText(model: BooksModel, d: ca.schippers.hfm.books.ScheduleDay, people: CalendarPeople): String =
    "${people.name(d.memberId)} · ${model.t("scheduleKind.${d.schedule.kind}")} ${clock(d.start)}–${clock(d.end)}" + (d.schedule.label?.let { " ($it)" }.orEmpty())

/** CAL-10: a thin bar in the person's colour: "Alex · Work 8:00–16:30". */
@Composable
internal fun ScheduleBar(model: BooksModel, d: ca.schippers.hfm.books.ScheduleDay, people: CalendarPeople, modifier: Modifier = Modifier.fillMaxWidth(), small: Boolean = false) {
    val text = scheduleText(model, d, people)
    // In a narrow cell the times come first, so they stay when the end is cut off.
    val shown = if (small) "${people.name(d.memberId)} ${clock(d.start)}–${clock(d.end)} · ${model.t("scheduleKind.${d.schedule.kind}")}" else text
    Box(modifier.padding(vertical = 1.dp).background(people.color(d.memberId), MaterialTheme.shapes.extraSmall).semantics { contentDescription = text }) {
        Text(
            shown, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = if (small) 0.dp else 1.dp),
        )
    }
}

@Composable
internal fun kindColor(item: CalendarItem, people: CalendarPeople): Color = when (item) {
    is CalendarItem.Event -> if (item.occurrence.event.category == EventCategory.ACTIVITY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    is CalendarItem.Bill -> MaterialTheme.colorScheme.tertiary
    is CalendarItem.Schedule -> people.color(item.day.memberId)
    else -> MaterialTheme.colorScheme.secondary
}

internal fun durationText(model: BooksModel, minutes: Int): String =
    if (minutes % 60 == 0) model.t("calendar.hours", minutes / 60) else model.t("calendar.minutes", minutes)

/** What clicking an item does: an appointment opens its form, anything else the screen it comes from. */
internal fun openItem(model: BooksModel, item: CalendarItem, onEdit: (CalendarEvent) -> Unit) {
    if (item is CalendarItem.Event) onEdit(item.occurrence.event) else itemSection(model, item)?.let { model.section = it }
}

// --- Month ----------------------------------------------------------------------------------------

/** CAL-07, CAL-10: the month's grid, each day with its schedule bars on top; a click on a day opens it in the Day view. */
@Composable
private fun MonthGrid(model: BooksModel, anchor: LocalDate, items: List<CalendarItem>, people: CalendarPeople, onEdit: (CalendarEvent) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val month = LocalDate(anchor.year, anchor.month, 1)
    val end = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    val byDay = items.groupBy { it.date }
    val locale = model.language.locale

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(model.t("calendar.clickDay"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        Row {
            for (dow in 0 until 7) {
                val name = java.time.DayOfWeek.of(dow + 1).getDisplayName(java.time.format.TextStyle.SHORT, locale)
                Text(name, Modifier.weight(1f).padding(4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        val firstOffset = month.dayOfWeek.ordinal // Monday = 0
        val days = month.daysUntil(end) + 1
        for (week in 0 until (firstOffset + days + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val index = week * 7 + dow - firstOffset
                    val date = if (index in 0 until days) month.plus(DatePeriod(days = index)) else null
                    Box(
                        Modifier.weight(1f).height(126.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .then(if (date != null) Modifier.clickable { onOpenDay(date) } else Modifier).padding(3.dp),
                    ) {
                        if (date != null) {
                            val list = byDay[date].orEmpty()
                            val bars = list.filterIsInstance<CalendarItem.Schedule>().sortedBy { people.order(it.day.memberId) }
                            val others = list.filter { it !is CalendarItem.Schedule }
                            Column {
                                Text(
                                    date.day.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (date == today()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (date == today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                for (b in bars) ScheduleBar(model, b.day, people, small = true)
                                val room = (4 - bars.size / 2).coerceAtLeast(2)
                                for (item in others.take(room)) MonthCellLine(model, item, people, onEdit)
                                if (others.size > room) Text("+${others.size - room}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An item in one short line, for a day cell: "09:30 Winter tires on", "Civic: oil change". */
internal fun lineText(model: BooksModel, item: CalendarItem): String = when (item) {
    is CalendarItem.Event -> listOfNotNull(time(item.occurrence.event.startTime), item.occurrence.event.title).joinToString(" ")
    is CalendarItem.Renewal -> "${item.renewal.subjectName}: ${renewalTitle(model, item.renewal)}"
    is CalendarItem.Maintenance -> "${item.due.subjectName}: ${item.due.taskName}"
    is CalendarItem.Imported -> importedMonthText(model, item)
    else -> itemTitle(model, item)
}

@Composable
private fun MonthCellLine(model: BooksModel, item: CalendarItem, people: CalendarPeople, onEdit: (CalendarEvent) -> Unit) {
    val text = lineText(model, item)
    val faded = (item is CalendarItem.Event && item.occurrence.mark != null) || (item is CalendarItem.Bill && item.occurrence.status != OccurrenceStatus.DUE)
    Text(
        text, Modifier.fillMaxWidth().clickable { openItem(model, item, onEdit) }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (faded) MaterialTheme.colorScheme.outline else kindColor(item, people),
        textDecoration = if (item is CalendarItem.Event && item.occurrence.mark == OccurrenceMark.CANCELLED) TextDecoration.LineThrough else null,
    )
}

// --- Editor ---------------------------------------------------------------------------------------

/** Reminder choices offered in the event editor (CAL-03), in minutes before the start. */
private val REMINDER_CHOICES = listOf(0, 15, 60, 120, 1440, 2 * 1440, 7 * 1440)

/** Repeats counted in months, where the month's last day or last business day can be chosen. */
private val MONTHLY_REPEATS = setOf(Repeat.MONTHLY, Repeat.QUARTERLY, Repeat.SEMI_ANNUAL, Repeat.ANNUAL, Repeat.EVERY_N_MONTHS)

/** "Store in": the account groups the user may edit; private groups are marked (CAL-06). */
@Composable
internal fun GroupPicker(model: BooksModel, selectedId: String?, enabled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (GroupInfo) -> Unit) {
    val groups = remember(model.revision) { model.editableGroups() }
    Picker(
        model.t("calendar.storeIn"), groups, groups.firstOrNull { it.id == selectedId },
        { if (it.isPrivate) model.t("group.privateLabel", it.name) else it.name }, modifier, enabled = enabled,
    ) { onSelect(it) }
}

/** Offers to create a private group when the user has none, so personal records stay private. */
@Composable
internal fun PrivateGroupHint(model: BooksModel, onCreated: (String) -> Unit) {
    val hasPrivate = remember(model.revision) { model.editableGroups().any { it.isPrivate && it.ownerUserId == model.session.userId } }
    if (!hasPrivate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("group.privateHint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { model.createPrivateGroup()?.let(onCreated) }) { Text(model.t("group.createPrivate")) }
        }
    }
}

/** Add or edit an appointment or event (CAL-01 to CAL-03, CAL-06), or a child's activity (CAL-11). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventDialog(model: BooksModel, existing: CalendarEvent?, draft: EventDraft?, onClose: () -> Unit) {
    val books = model.books
    val start = existing?.let {
        EventDraft(
            it.groupId, it.title, it.category, it.startDate, it.startTime, it.durationMinutes, it.location, it.notes, it.memberId, it.providerId, it.accountId,
            it.recurrence, it.endDate, it.reminderMinutes, it.driverThere, it.driverBack, it.cost,
        )
    } ?: draft!!
    val people = remember { model.peopleAndPets() }
    val members = remember { books.members.list() }
    val providers = remember(model.revision) { books.health.providers().filter { !it.archived } }
    val accounts = remember { books.accounts.list().map { it.account } }
    val currency = start.cost?.currency ?: books.rates.baseCurrency
    val locale = model.language.locale
    var groupId by remember { mutableStateOf(start.groupId) }
    var title by remember { mutableStateOf(start.title) }
    var category by remember { mutableStateOf(start.category) }
    var date by remember { mutableStateOf(start.startDate.toString()) }
    var allDay by remember { mutableStateOf(start.startTime == null) }
    var timeText by remember { mutableStateOf(time(start.startTime) ?: "09:00") }
    var duration by remember { mutableStateOf(start.durationMinutes?.toString().orEmpty()) }
    var location by remember { mutableStateOf(start.location.orEmpty()) }
    var notes by remember { mutableStateOf(start.notes.orEmpty()) }
    var memberId by remember { mutableStateOf(start.memberId) }
    var providerId by remember { mutableStateOf(start.providerId) }
    var accountId by remember { mutableStateOf(start.accountId) }
    var repeat by remember { mutableStateOf(start.recurrence?.let { Repeat.of(it) } ?: Repeat.ONCE) }
    var interval by remember { mutableStateOf(start.recurrence?.interval?.toString() ?: "1") }
    // CAL-02: the same patterns as bills (BILL-02): a second day for twice a month, the month's last
    // day or last business day, and moving off weekends and holidays.
    var monthDay by remember { mutableStateOf(start.recurrence?.monthDay ?: MonthDay.SAME_DAY) }
    var secondDay by remember { mutableStateOf(start.recurrence?.secondDay?.toString() ?: "0") }
    var adjust by remember { mutableStateOf(start.recurrence?.adjust ?: BusinessDayAdjust.NONE) }
    var end by remember { mutableStateOf(start.endDate?.toString().orEmpty()) }
    var reminders by remember { mutableStateOf(start.reminderMinutes.toSet()) }
    var driverThere by remember { mutableStateOf(start.driverThere) }
    var driverBack by remember { mutableStateOf(start.driverBack) }
    var cost by remember { mutableStateOf(start.cost?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun recurrence(): Recurrence? {
        val n = interval.trim().toIntOrNull()?.takeIf { it >= 1 } ?: throw ValidationException("error.invalidNumber")
        val monthly = repeat in MONTHLY_REPEATS
        return when (repeat) {
            Repeat.ONCE -> null
            Repeat.SEMI_MONTHLY -> Recurrence(Frequency.SEMI_MONTHLY, secondDay = secondDay.trim().toIntOrNull()?.takeIf { it in 0..31 } ?: throw ValidationException("error.dayOfMonth"), adjust = adjust)
            Repeat.EVERY_N_DAYS -> Recurrence(Frequency.DAILY, n, adjust = adjust)
            Repeat.EVERY_N_WEEKS -> Recurrence(Frequency.WEEKLY, n, adjust = adjust)
            Repeat.EVERY_N_MONTHS -> Recurrence(Frequency.MONTHLY, n, monthDay, adjust = adjust)
            else -> repeat.recurrence!!.copy(monthDay = if (monthly) monthDay else MonthDay.SAME_DAY, adjust = adjust)
        }
    }

    FormDialog(
        model.t(if (existing == null) "calendar.add" else "calendar.edit"), model.t("common.save"), model.t("common.cancel"),
        canSave = title.isNotBlank(),
        onDismiss = onClose,
        onSave = {
            val ok = model.act {
                fun parseDate(text: String) = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }
                val startTime = if (allDay) null else parseTime(timeText) ?: throw ValidationException("error.invalidTime")
                val minutes = if (allDay) null else duration.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") }
                val activity = category == EventCategory.ACTIVITY
                val costMoney = if (activity) runCatching { parseAmount(cost, currency, locale) }.getOrElse { throw ValidationException("error.invalidNumber") } else null
                val d = EventDraft(
                    groupId, title, category, parseDate(date) ?: throw ValidationException("error.invalidDate"), startTime, minutes,
                    location, notes, memberId, providerId, accountId, recurrence(), parseDate(end), reminders.sortedDescending(),
                    driverThere.takeIf { activity }, driverBack.takeIf { activity }, costMoney,
                )
                if (existing == null) {
                    books.calendar.create(d)
                } else {
                    books.calendar.update(
                        existing.copy(
                            title = d.title, category = d.category, startDate = d.startDate, startTime = d.startTime, durationMinutes = d.durationMinutes,
                            location = d.location, notes = d.notes, memberId = d.memberId, providerId = d.providerId, accountId = d.accountId,
                            recurrence = d.recurrence, endDate = d.endDate, reminderMinutes = d.reminderMinutes,
                            driverThere = d.driverThere, driverBack = d.driverBack, cost = d.cost,
                        ),
                    )
                }
            }
            if (ok != null) onClose()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("calendar.title"), title, Modifier.weight(2f)) { title = it }
                Picker(model.t("calendar.category"), EventCategory.entries, category, { model.t("eventCategory.$it") }, Modifier.weight(1f)) { category = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateInput(model.t("calendar.date"), date, Modifier.weight(1f)) { date = it }
                if (!allDay) {
                    TextInput(model.t("calendar.time"), timeText, Modifier.weight(0.7f), error = if (parseTime(timeText) == null) model.t("calendar.timeHint") else null) { timeText = it }
                    TextInput(model.t("calendar.duration"), duration, Modifier.weight(0.8f)) { duration = it }
                }
            }
            LabeledCheckbox(model.t("calendar.allDay"), allDay) { allDay = it }
            TextInput(model.t("calendar.location"), location) { location = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("calendar.person"), listOf(null) + people, people.firstOrNull { it.id == memberId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { memberId = it?.id }
                Picker(model.t("calendar.provider"), listOf(null) + providers, providers.firstOrNull { it.id == providerId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { providerId = it?.id }
            }
            // CAL-11: who drives each way, and what it costs.
            if (category == EventCategory.ACTIVITY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DriverPicker(model, model.t("calendar.driverThere"), members, driverThere, Modifier.weight(1f)) { driverThere = it }
                    DriverPicker(model, model.t("calendar.driverBack"), members, driverBack, Modifier.weight(1f)) { driverBack = it }
                }
                AmountInput(model.t("calendar.cost"), cost, currency, locale, Modifier.fillMaxWidth(), model::money) { cost = it }
                Text(model.t("calendar.cost.hint"), style = MaterialTheme.typography.bodySmall)
            }
            Picker(model.t("calendar.account"), listOf(null) + accounts, accounts.firstOrNull { it.id == accountId }, { it?.name ?: model.t("common.none") }) { accountId = it?.id }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("bills.repeat"), Repeat.entries, repeat, { model.t("repeat.$it") }, Modifier.weight(1f)) { repeat = it }
                when (repeat) {
                    Repeat.EVERY_N_DAYS, Repeat.EVERY_N_WEEKS, Repeat.EVERY_N_MONTHS -> TextInput(model.t("bills.interval"), interval, Modifier.weight(0.6f)) { interval = it }
                    Repeat.SEMI_MONTHLY -> TextInput(model.t("bills.secondDay"), secondDay, Modifier.weight(0.6f), supporting = model.t("bills.secondDay.hint")) { secondDay = it }
                    else -> Unit
                }
                if (repeat != Repeat.ONCE) DateInput(model.t("bills.end"), end, Modifier.weight(1f)) { end = it }
            }
            if (repeat != Repeat.ONCE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repeat in MONTHLY_REPEATS) {
                        Picker(model.t("bills.monthDay"), MonthDay.entries, monthDay, { model.t("monthDay.$it") }, Modifier.weight(1f)) { monthDay = it }
                    }
                    Picker(model.t("bills.adjust"), BusinessDayAdjust.entries, adjust, { model.t("adjust.$it") }, Modifier.weight(1f)) { adjust = it }
                }
            }
            Text(model.t("calendar.reminders"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // The household's default lead time (Rates and rules) is offered even when it is not a usual choice.
                for (m in (REMINDER_CHOICES + start.reminderMinutes).distinct().sorted()) {
                    LabeledCheckbox(reminderLabel(model, m), m in reminders) { on -> reminders = if (on) reminders + m else reminders - m }
                }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = existing == null) { groupId = it.id }
            // CON-04: who the appointment is with, as a contact.
            if (existing != null) LinkedContacts(model, LinkTarget.EVENT, existing.id, memberIds = setOfNotNull(existing.memberId), groupId = existing.groupId)
            if (existing == null) PrivateGroupHint(model) { groupId = it }
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete && existing != null) {
        FormDialog(model.t("calendar.delete.title"), model.t("common.delete"), model.t("common.cancel"), onDismiss = { confirmDelete = false }, onSave = {
            if (model.act { books.calendar.delete(existing.id) } != null) {
                confirmDelete = false
                onClose()
            }
        }) { Text(model.t("calendar.delete.body", existing.title)) }
    }
}

/** The category an activity's cost goes to by default: Children, Activities and camps. */
internal fun activityCategory(tree: List<Pair<Category, Int>>): Category? = tree.firstOrNull { it.first.systemKey == "children.activities" }?.first

internal fun parseTime(text: String): LocalTime? {
    val parts = text.trim().replace('h', ':').replace('.', ':').split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.ifEmpty { "0" }?.toIntOrNull() ?: 0
    return if (parts.size <= 2 && hour in 0..23 && minute in 0..59) LocalTime(hour, minute) else null
}

private fun reminderLabel(model: BooksModel, minutes: Int): String = when {
    minutes == 0 -> model.t("calendar.remind.atStart")
    minutes % 1440 == 0 -> model.t("calendar.remind.days", minutes / 1440)
    minutes % 60 == 0 -> model.t("calendar.remind.hours", minutes / 60)
    else -> model.t("calendar.remind.minutes", minutes)
}
