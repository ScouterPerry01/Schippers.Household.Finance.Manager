package ca.schippers.hfm.desktop

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.CalendarEvent
import ca.schippers.hfm.books.CalendarItem
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.EventDraft
import ca.schippers.hfm.books.GroupInfo
import ca.schippers.hfm.books.HealthDue
import ca.schippers.hfm.books.OccurrenceMark
import ca.schippers.hfm.books.OccurrenceStatus
import ca.schippers.hfm.books.Renewal
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private enum class CalendarTab { AGENDA, MONTH }

/** CAL-01 to CAL-06: appointments and events of any kind, shown with bills and health due dates. */
@Composable
fun CalendarScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(CalendarTab.AGENDA) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var creating by remember { mutableStateOf<EventDraft?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.calendar"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { creating = model.newEventDraft(today()) }) { Text(model.t("calendar.add")) }
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = 8.dp)) {
            for (t in CalendarTab.entries) {
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(model.t("calendar.tab.${t.name}")) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                CalendarTab.AGENDA -> AgendaList(model, today(), today().plus(DatePeriod(days = 60)), onEdit = { editing = it })
                CalendarTab.MONTH -> MonthGrid(model, onEdit = { editing = it }, onAdd = { creating = model.newEventDraft(it) })
            }
        }
    }

    creating?.let { draft -> EventDialog(model, null, draft) { creating = null } }
    editing?.let { event -> EventDialog(model, event, null) { editing = null } }
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

    LazyColumn {
        if (items.isEmpty()) item { Text(model.t(if (memberId == null) "calendar.nothing" else "health.noAppointments"), Modifier.padding(8.dp)) }
        val byDay = items.groupBy { it.date }
        for ((day, list) in byDay) {
            item(key = "d-$day") {
                Text(dayTitle(model, day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            items(list, key = { itemKey(it) }) { item -> AgendaRow(model, item, names, onEdit) }
        }
    }
}

private fun itemKey(item: CalendarItem): String = when (item) {
    is CalendarItem.Event -> "e-${item.occurrence.event.id}-${item.date}"
    is CalendarItem.Bill -> "b-${item.occurrence.bill.id}-${item.date}"
    is CalendarItem.Health -> "h-${item.due.javaClass.simpleName}-${item.due.memberId}-${item.date}-${healthTitle(item.due)}"
    is CalendarItem.Renewal -> "r-${item.renewal.kind}-${item.renewal.subjectId}-${item.date}-${item.renewal.detail}"
    is CalendarItem.Maintenance -> "m-${item.due.taskId}-${item.date}"
}

/** Names of people, providers and accounts, for the agenda lines. */
private class Lookups(val people: Map<String, String>, val providers: Map<String, String>, val accounts: Map<String, String>)

private fun lookups(model: BooksModel): Lookups {
    val books = model.books
    return Lookups(
        model.peopleAndPets(includeArchived = true).associate { it.id to it.name },
        runCatching { books.health.providers() }.getOrDefault(emptyList()).associate { it.id to it.name },
        books.accounts.list(includeClosed = true).associate { it.account.id to it.account.name },
    )
}

private fun dayTitle(model: BooksModel, day: LocalDate): String {
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
private fun renewalTitle(model: BooksModel, r: Renewal): String = model.t("renewalKind.${r.kind}") + (r.detail?.let { " ($it)" }.orEmpty())

private fun time(t: LocalTime?): String? = t?.let { "%02d:%02d".format(it.hour, it.minute) }

private fun healthTitle(due: HealthDue): String = when (due) {
    is HealthDue.Refill -> due.medication.name
    is HealthDue.TestFollowUp -> due.test.name
    is HealthDue.ImmunizationDue -> due.immunization.vaccine
}

@Composable
private fun AgendaRow(model: BooksModel, item: CalendarItem, names: Lookups, onEdit: (CalendarEvent) -> Unit) {
    val books = model.books
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).border(2.dp, kindColor(item)))
            when (item) {
                is CalendarItem.Event -> {
                    val o = item.occurrence
                    val e = o.event
                    val done = o.mark != null
                    Text(time(e.startTime) ?: model.t("calendar.allDay"), Modifier.width(110.dp).padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.title + (o.mark?.let { " (${model.t("calendar.mark.${it.name}")})" }.orEmpty()),
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (o.mark == OccurrenceMark.CANCELLED) TextDecoration.LineThrough else null,
                        )
                        Text(
                            listOfNotNull(
                                model.t("eventCategory.${e.category}"),
                                e.durationMinutes?.takeIf { e.startTime != null }?.let { durationText(model, it) },
                                e.location,
                                e.memberId?.let { names.people[it] },
                                e.providerId?.let { names.providers[it] },
                                e.accountId?.let { names.accounts[it] },
                                e.recurrence?.let { describeRecurrence(model, it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (done) {
                            TextButton(onClick = { model.act { books.calendar.mark(e.id, o.date, null) } }) { Text(model.t("calendar.undo")) }
                        } else {
                            OutlinedButton(onClick = { model.act { books.calendar.mark(e.id, o.date, OccurrenceMark.DONE) } }) { Text(model.t("calendar.markDone")) }
                            TextButton(onClick = { model.act { books.calendar.mark(e.id, o.date, OccurrenceMark.CANCELLED) } }) { Text(model.t("calendar.markCancelled")) }
                        }
                        TextButton(onClick = { onEdit(e) }) { Text(model.t("common.edit")) }
                    }
                }
                is CalendarItem.Bill -> {
                    val o = item.occurrence
                    Text(model.t("calendar.bill"), Modifier.width(110.dp).padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.bill.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (o.amountKnown) "" else "≈ ") + model.money(o.amount) + " · " + model.t("occurrenceStatus.${o.status}"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { model.section = Section.BILLS }) { Text(model.t("calendar.openBills")) }
                }
                is CalendarItem.Health -> {
                    val due = item.due
                    Text(model.t("calendar.health"), Modifier.width(110.dp).padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            model.t("healthDue.${due.javaClass.simpleName}", healthTitle(due)),
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(names.people[due.memberId].orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { model.section = Section.HEALTH }) { Text(model.t("calendar.openHealth")) }
                }
                is CalendarItem.Renewal -> {
                    val r = item.renewal
                    Text(model.t("calendar.renewal"), Modifier.width(110.dp).padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(renewalTitle(model, r), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.subjectName, style = MaterialTheme.typography.bodySmall)
                    }
                    val section = model.renewalSection(r.kind)
                    TextButton(onClick = { model.section = section }) { Text(model.t("calendar.open.${section.name}")) }
                }
                is CalendarItem.Maintenance -> {
                    val due = item.due
                    Text(model.t("calendar.maintenance"), Modifier.width(110.dp).padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(due.taskName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(due.subjectName, style = MaterialTheme.typography.bodySmall)
                    }
                    val section = if (due.vehicle) Section.VEHICLES else Section.ASSETS
                    TextButton(onClick = { model.section = section }) { Text(model.t("calendar.open.${section.name}")) }
                }
            }
        }
    }
}

@Composable
private fun kindColor(item: CalendarItem): Color = when (item) {
    is CalendarItem.Event -> MaterialTheme.colorScheme.primary
    is CalendarItem.Bill -> MaterialTheme.colorScheme.tertiary
    is CalendarItem.Health -> MaterialTheme.colorScheme.secondary
    is CalendarItem.Renewal -> MaterialTheme.colorScheme.secondary
    is CalendarItem.Maintenance -> MaterialTheme.colorScheme.secondary
}

private fun durationText(model: BooksModel, minutes: Int): String =
    if (minutes % 60 == 0) model.t("calendar.hours", minutes / 60) else model.t("calendar.minutes", minutes)

// --- Month ----------------------------------------------------------------------------------------

@Composable
private fun MonthGrid(model: BooksModel, onEdit: (CalendarEvent) -> Unit, onAdd: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(today().let { LocalDate(it.year, it.month, 1) }) }
    val end = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    val items = remember(model.revision, month) { model.books.calendar.items(month, end).groupBy { it.date } }
    val locale = model.language.locale

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) { Text("◀") }
            Text(
                java.time.YearMonth.of(month.year, month.month.ordinal + 1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(220.dp),
            )
            TextButton(onClick = { month = month.plus(DatePeriod(months = 1)) }) { Text("▶") }
            TextButton(onClick = { month = today().let { LocalDate(it.year, it.month, 1) } }) { Text(model.t("calendar.today")) }
            Text(model.t("calendar.clickDay"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
        }
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
                        Modifier.weight(1f).height(110.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .then(if (date != null) Modifier.clickable { onAdd(date) } else Modifier).padding(4.dp),
                    ) {
                        if (date != null) {
                            val list = items[date].orEmpty()
                            Column {
                                Text(
                                    date.day.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (date == today()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (date == today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                for (item in list.take(4)) MonthCellLine(model, item, onEdit)
                                if (list.size > 4) Text("+${list.size - 4}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCellLine(model: BooksModel, item: CalendarItem, onEdit: (CalendarEvent) -> Unit) {
    val (text, modifier) = when (item) {
        is CalendarItem.Event -> {
            val e = item.occurrence.event
            listOfNotNull(time(e.startTime), e.title).joinToString(" ") to Modifier.clickable { onEdit(e) }
        }
        is CalendarItem.Bill -> item.occurrence.bill.name to Modifier.clickable { model.section = Section.BILLS }
        is CalendarItem.Health -> model.t("healthDue.${item.due.javaClass.simpleName}", healthTitle(item.due)) to Modifier.clickable { model.section = Section.HEALTH }
        is CalendarItem.Renewal -> "${item.renewal.subjectName}: ${renewalTitle(model, item.renewal)}" to Modifier.clickable { model.section = model.renewalSection(item.renewal.kind) }
        is CalendarItem.Maintenance -> "${item.due.subjectName}: ${item.due.taskName}" to Modifier.clickable { model.section = if (item.due.vehicle) Section.VEHICLES else Section.ASSETS }
    }
    val faded = (item is CalendarItem.Event && item.occurrence.mark != null) || (item is CalendarItem.Bill && item.occurrence.status != OccurrenceStatus.DUE)
    Text(
        text, modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (faded) MaterialTheme.colorScheme.outline else kindColor(item),
        textDecoration = if (item is CalendarItem.Event && item.occurrence.mark == OccurrenceMark.CANCELLED) TextDecoration.LineThrough else null,
    )
}

// --- Editor ---------------------------------------------------------------------------------------

/** Reminder choices offered in the event editor (CAL-03), in minutes before the start. */
private val REMINDER_CHOICES = listOf(0, 15, 60, 120, 1440, 2 * 1440, 7 * 1440)

private val EVENT_REPEATS = Repeat.entries.filter { it != Repeat.SEMI_MONTHLY }

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

/** Add or edit an appointment or event (CAL-01 to CAL-03, CAL-06). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventDialog(model: BooksModel, existing: CalendarEvent?, draft: EventDraft?, onClose: () -> Unit) {
    val books = model.books
    val start = existing?.let { EventDraft(it.groupId, it.title, it.category, it.startDate, it.startTime, it.durationMinutes, it.location, it.notes, it.memberId, it.providerId, it.accountId, it.recurrence, it.endDate, it.reminderMinutes) }
        ?: draft!!
    val people = remember { model.peopleAndPets() }
    val providers = remember(model.revision) { books.health.providers().filter { !it.archived } }
    val accounts = remember { books.accounts.list().map { it.account } }
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
    var end by remember { mutableStateOf(start.endDate?.toString().orEmpty()) }
    var reminders by remember { mutableStateOf(start.reminderMinutes.toSet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun recurrence(): Recurrence? {
        val n = interval.trim().toIntOrNull()?.takeIf { it >= 1 } ?: throw ValidationException("error.invalidNumber")
        return when (repeat) {
            Repeat.ONCE -> null
            Repeat.EVERY_N_DAYS -> Recurrence(Frequency.DAILY, n)
            Repeat.EVERY_N_WEEKS -> Recurrence(Frequency.WEEKLY, n)
            Repeat.EVERY_N_MONTHS -> Recurrence(Frequency.MONTHLY, n)
            else -> repeat.recurrence
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
                val d = EventDraft(
                    groupId, title, category, parseDate(date) ?: throw ValidationException("error.invalidDate"), startTime, minutes,
                    location, notes, memberId, providerId, accountId, recurrence(), parseDate(end), reminders.sortedDescending(),
                )
                if (existing == null) {
                    books.calendar.create(d)
                } else {
                    books.calendar.update(
                        existing.copy(
                            title = d.title, category = d.category, startDate = d.startDate, startTime = d.startTime, durationMinutes = d.durationMinutes,
                            location = d.location, notes = d.notes, memberId = d.memberId, providerId = d.providerId, accountId = d.accountId,
                            recurrence = d.recurrence, endDate = d.endDate, reminderMinutes = d.reminderMinutes,
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
                    TextInput(model.t("calendar.time"), timeText, Modifier.weight(0.7f), error = if (parseTime(timeText) == null) "HH:MM" else null) { timeText = it }
                    TextInput(model.t("calendar.duration"), duration, Modifier.weight(0.8f)) { duration = it }
                }
            }
            LabeledCheckbox(model.t("calendar.allDay"), allDay) { allDay = it }
            TextInput(model.t("calendar.location"), location) { location = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("calendar.person"), listOf(null) + people, people.firstOrNull { it.id == memberId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { memberId = it?.id }
                Picker(model.t("calendar.provider"), listOf(null) + providers, providers.firstOrNull { it.id == providerId }, { it?.name ?: model.t("common.none") }, Modifier.weight(1f)) { providerId = it?.id }
            }
            Picker(model.t("calendar.account"), listOf(null) + accounts, accounts.firstOrNull { it.id == accountId }, { it?.name ?: model.t("common.none") }) { accountId = it?.id }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("bills.repeat"), EVENT_REPEATS, repeat, { model.t("repeat.$it") }, Modifier.weight(1f)) { repeat = it }
                if (repeat in setOf(Repeat.EVERY_N_DAYS, Repeat.EVERY_N_WEEKS, Repeat.EVERY_N_MONTHS)) {
                    TextInput(model.t("bills.interval"), interval, Modifier.weight(0.6f)) { interval = it }
                }
                if (repeat != Repeat.ONCE) DateInput(model.t("bills.end"), end, Modifier.weight(1f)) { end = it }
            }
            Text(model.t("calendar.reminders"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (m in REMINDER_CHOICES) {
                    LabeledCheckbox(reminderLabel(model, m), m in reminders) { on -> reminders = if (on) reminders + m else reminders - m }
                }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = existing == null) { groupId = it.id }
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

private fun parseTime(text: String): LocalTime? {
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
