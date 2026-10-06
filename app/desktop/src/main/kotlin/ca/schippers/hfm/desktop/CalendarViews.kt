package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.CalendarEvent
import ca.schippers.hfm.books.CalendarItem
import ca.schippers.hfm.books.Driver
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.EventOccurrence
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.books.OccurrenceMark
import ca.schippers.hfm.books.ValidationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// --- Week and Day (CAL-07, CAL-10) ------------------------------------------------------------------

private val GUTTER = 52.dp

/** CAL-07: the week from Monday, timed items on an hour grid and the rest at the top; a click on a day's name opens it. */
@Composable
internal fun WeekView(
    model: BooksModel, monday: LocalDate, items: List<CalendarItem>, names: Lookups, people: CalendarPeople,
    onEdit: (CalendarEvent) -> Unit, onOpenDay: (LocalDate) -> Unit, onAdd: (LocalDate, LocalTime?) -> Unit,
) {
    val days = (0 until 7).map { monday.plus(DatePeriod(days = it)) }
    val locale = model.language.locale
    Column(Modifier.fillMaxSize()) {
        Row {
            Box(Modifier.width(GUTTER))
            for (d in days) {
                val name = java.time.DayOfWeek.of(d.dayOfWeek.ordinal + 1).getDisplayName(java.time.format.TextStyle.SHORT, locale)
                Text(
                    "$name ${d.day}", Modifier.weight(1f).clickable { onOpenDay(d) }.padding(4.dp),
                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                    fontWeight = if (d == today()) FontWeight.Bold else FontWeight.Normal,
                    color = if (d == today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TopRows(model, days, items, people, onEdit, maxLines = 3)
        HourGrid(model, days, items, names, people, 36.dp, detailed = false, onEdit, onAdd, Modifier.weight(1f))
    }
}

/**
 * CAL-07: one day on an hour grid, and beside it the day's items with their actions: schedules with
 * Day off, activities with their drivers and cost (CAL-11).
 */
@Composable
internal fun DayView(
    model: BooksModel, day: LocalDate, items: List<CalendarItem>, names: Lookups, people: CalendarPeople,
    onEdit: (CalendarEvent) -> Unit, onAdd: (LocalDate, LocalTime?) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            TopRows(model, listOf(day), items, people, onEdit, maxLines = 6)
            HourGrid(model, listOf(day), items, names, people, 44.dp, detailed = true, onEdit, onAdd, Modifier.weight(1f))
        }
        val list = items.filter { it.date == day }
        Box(Modifier.width(360.dp).padding(start = 12.dp)) {
            LazyColumn {
                item { Text(model.t("calendar.dayList"), style = MaterialTheme.typography.titleSmall) }
                if (list.isEmpty()) item { Text(model.t("calendar.nothingThisDay"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                items(list.sortedBy { (it as? CalendarItem.Event)?.occurrence?.event?.startTime ?: (it as? CalendarItem.Schedule)?.day?.start ?: LocalTime(0, 0) }, key = { itemKey(it) }) {
                    AgendaRow(model, it, names, people, onEdit, compact = true)
                }
            }
        }
    }
}

/** Schedule bars, then the items with no time (all-day events, bills, renewals...), above the hour grid. */
@Composable
private fun TopRows(model: BooksModel, days: List<LocalDate>, items: List<CalendarItem>, people: CalendarPeople, onEdit: (CalendarEvent) -> Unit, maxLines: Int) {
    val byDay = items.groupBy { it.date }
    val anyBars = days.any { d -> byDay[d].orEmpty().any { it is CalendarItem.Schedule } }
    if (anyBars) {
        Row {
            Box(Modifier.width(GUTTER))
            for (d in days) {
                Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                    for (b in byDay[d].orEmpty().filterIsInstance<CalendarItem.Schedule>().sortedBy { people.order(it.day.memberId) }) {
                        ScheduleBar(model, b.day, people, small = days.size > 1)
                    }
                }
            }
        }
    }
    val untimed = days.associateWith { d -> byDay[d].orEmpty().filter { untimed(it) } }
    if (untimed.values.any { it.isNotEmpty() }) {
        Row(Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text(model.t("calendar.allDay"), Modifier.width(GUTTER).padding(2.dp), style = MaterialTheme.typography.labelSmall)
            for (d in days) {
                Column(Modifier.weight(1f).padding(2.dp)) {
                    val list = untimed[d].orEmpty()
                    for (item in list.take(maxLines)) {
                        Text(
                            lineText(model, item), Modifier.fillMaxWidth().clickable { openItem(model, item, onEdit) },
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = kindColor(item, people),
                        )
                    }
                    if (list.size > maxLines) Text("+${list.size - maxLines}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Items shown at the top: everything but schedules and timed appointments. */
private fun untimed(item: CalendarItem): Boolean = when (item) {
    is CalendarItem.Schedule -> false
    is CalendarItem.Event -> item.occurrence.event.startTime == null
    else -> true
}

/** A part of a day on the grid, from [from] to [to] minutes after midnight. */
private class Span(val from: Int, val to: Int)

/**
 * The hours of [days], scrolled to 7:00 at first. Scheduled hours are shaded in each person's colour
 * (a night shift continues into the next morning); timed appointments sit at their hours, side by
 * side when they overlap. A click on an empty hour adds an appointment then.
 */
@Composable
private fun HourGrid(
    model: BooksModel, days: List<LocalDate>, items: List<CalendarItem>, names: Lookups, people: CalendarPeople, hour: Dp, detailed: Boolean,
    onEdit: (CalendarEvent) -> Unit, onAdd: (LocalDate, LocalTime?) -> Unit, modifier: Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { (hour * 7).roundToPx() })
    val line = MaterialTheme.colorScheme.outlineVariant
    val byDay = items.groupBy { it.date }
    Box(modifier.verticalScroll(scroll)) {
        Row {
            Column(Modifier.width(GUTTER)) {
                for (h in 0 until 24) Text("$h:00", Modifier.height(hour).padding(end = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
            for (d in days) {
                val previous = d.minus(DatePeriod(days = 1))
                val schedules = byDay[d].orEmpty().filterIsInstance<CalendarItem.Schedule>().map { it.day } +
                    byDay[previous].orEmpty().filterIsInstance<CalendarItem.Schedule>().map { it.day }.filter { it.overnight }
                val timed = byDay[d].orEmpty().filterIsInstance<CalendarItem.Event>().filter { it.occurrence.event.startTime != null }
                BoxWithConstraints(
                    Modifier.weight(1f).height(hour * 24).border(0.5.dp, line)
                        .drawBehind {
                            for (h in 1 until 24) {
                                val y = hour.toPx() * h
                                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            }
                        }
                        .pointerInput(d, hour) {
                            detectTapGestures { o -> onAdd(d, LocalTime((o.y / hour.toPx()).toInt().coerceIn(0, 23), 0)) }
                        },
                ) {
                    // CAL-10: the scheduled hours, shaded, with a stripe per person at the left.
                    for (s in schedules) {
                        val span = if (s.date == d) Span(minutes(s.start), if (s.overnight) 24 * 60 else minutes(s.end)) else Span(0, minutes(s.end))
                        val color = people.color(s.memberId)
                        val stripe = (people.order(s.memberId).coerceAtMost(8) * 5).dp
                        Box(Modifier.offset(y = hour * span.from / 60f).height(hour * (span.to - span.from) / 60f).fillMaxWidth().background(color.copy(alpha = 0.10f)))
                        Box(Modifier.offset(x = stripe, y = hour * span.from / 60f).height(hour * (span.to - span.from) / 60f).width(4.dp).background(color.copy(alpha = 0.7f)))
                    }
                    // Timed appointments, in lanes when they overlap.
                    val spans = timed.map { e -> e to eventSpan(e.occurrence) }
                    val lanes = lanes(spans.map { it.second })
                    val count = (lanes.maxOrNull() ?: 0) + 1
                    val left = 24.dp
                    val laneWidth = (maxWidth - left) / count
                    for ((i, pair) in spans.withIndex()) {
                        val (item, span) = pair
                        val o = item.occurrence
                        val e = o.event
                        val height = maxOf(hour * (span.to - span.from) / 60f, 20.dp)
                        Box(
                            Modifier.offset(x = left + laneWidth * lanes[i], y = hour * span.from / 60f).width(laneWidth).height(height).padding(1.dp)
                                .background(kindColor(item, people).copy(alpha = 0.16f), MaterialTheme.shapes.extraSmall)
                                .border(1.dp, kindColor(item, people), MaterialTheme.shapes.extraSmall)
                                .clickable { onEdit(e) }.padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Column {
                                Text(
                                    "${time(e.startTime)} ${e.title}", style = MaterialTheme.typography.labelMedium, maxLines = if (detailed) 1 else 2, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                    color = if (o.mark != null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (o.mark == OccurrenceMark.CANCELLED) TextDecoration.LineThrough else null,
                                )
                                if (detailed) {
                                    val detail = listOfNotNull(
                                        e.location, e.memberId?.let { names.people[it] },
                                        if (e.category == EventCategory.ACTIVITY) driversText(model, names, o.driverThere, o.driverBack) else null,
                                    ).joinToString(" · ")
                                    if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun minutes(t: LocalTime) = t.hour * 60 + t.minute

private fun eventSpan(o: EventOccurrence): Span {
    val start = minutes(o.event.startTime!!)
    return Span(start, (start + (o.event.durationMinutes ?: 60)).coerceAtMost(24 * 60))
}

/** The lane of each span: the first one free when it starts. */
private fun lanes(spans: List<Span>): List<Int> {
    val ends = ArrayList<Int>()
    return spans.map { s ->
        val lane = ends.indexOfFirst { it <= s.from }.takeIf { it >= 0 } ?: ends.size.also { ends += 0 }
        ends[lane] = s.to
        lane
    }
}

// --- Year (CAL-07) -----------------------------------------------------------------------------------

/** CAL-07: the twelve months; a day with something on it is marked, and a click opens it in the Day view. */
@Composable
internal fun YearView(model: BooksModel, year: Int, items: List<CalendarItem>, onOpenDay: (LocalDate) -> Unit) {
    // Schedules are on most days, so only other items mark a day.
    val busy = items.filter { it !is CalendarItem.Schedule }.groupingBy { it.date }.eachCount()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (col in 0 until 4) {
                    val month = LocalDate(year, row * 4 + col + 1, 1)
                    MiniMonth(model, month, Modifier.weight(1f), marked = { (busy[it] ?: 0) > 0 }, selected = null, onPick = onOpenDay)
                }
            }
        }
    }
}

/** A small month, weeks from Monday: for the Year view and the date picker. */
@Composable
internal fun MiniMonth(model: BooksModel, month: LocalDate, modifier: Modifier, marked: (LocalDate) -> Boolean, selected: LocalDate?, onPick: (LocalDate) -> Unit) {
    val locale = model.language.locale
    val end = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    val days = month.daysUntil(end) + 1
    val offset = month.dayOfWeek.ordinal
    Column(modifier) {
        Text(monthTitle(model, month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 2.dp))
        Row {
            for (dow in 0 until 7) {
                Text(
                    java.time.DayOfWeek.of(dow + 1).getDisplayName(java.time.format.TextStyle.NARROW, locale), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                )
            }
        }
        for (week in 0 until (offset + days + 6) / 7) {
            Row {
                for (dow in 0 until 7) {
                    val index = week * 7 + dow - offset
                    val date = if (index in 0 until days) month.plus(DatePeriod(days = index)) else null
                    Box(Modifier.weight(1f).height(22.dp).then(if (date != null) Modifier.clickable { onPick(date) } else Modifier), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val isToday = date == today()
                            val busy = marked(date)
                            Box(
                                Modifier.size(20.dp).then(
                                    when {
                                        date == selected -> Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                        busy -> Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                        else -> Modifier
                                    },
                                ).then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date.day.toString(), style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (busy || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (date == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** CAL-07: the date picker: a month to click in, or a date typed in. */
@Composable
internal fun GoToDateDialog(model: BooksModel, start: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(LocalDate(start.year, start.month, 1)) }
    var text by remember { mutableStateOf(start.toString()) }
    FormDialog(model.t("calendar.goTo"), model.t("calendar.go"), model.t("common.cancel"), canSave = runCatching { LocalDate.parse(text.trim()) }.isSuccess, onDismiss = onDismiss, onSave = {
        runCatching { LocalDate.parse(text.trim()) }.getOrNull()?.let(onPick)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolButton(model.t("common.previousMonth"), "◀") { month = month.minus(DatePeriod(months = 1)) }
            Box(Modifier.weight(1f))
            SymbolButton(model.t("common.nextMonth"), "▶") { month = month.plus(DatePeriod(months = 1)) }
        }
        MiniMonth(model, month, Modifier.fillMaxWidth(), marked = { false }, selected = runCatching { LocalDate.parse(text.trim()) }.getOrNull(), onPick = onPick)
        DateInput(model.t("calendar.date"), text, Modifier.fillMaxWidth()) { text = it }
    }
}

// --- Activities (CAL-11) -----------------------------------------------------------------------------

private const val OTHER_DRIVER = "\u0000other"

/** Who drives: nobody, a household member, or someone else whose name is typed in. */
@Composable
internal fun DriverPicker(model: BooksModel, label: String, members: List<Member>, driver: Driver?, modifier: Modifier, onChange: (Driver?) -> Unit) {
    var choice by remember { mutableStateOf(driver?.memberId ?: if (driver?.name != null) OTHER_DRIVER else null) }
    var name by remember { mutableStateOf(driver?.name.orEmpty()) }
    Column(modifier) {
        Picker(
            label, listOf<String?>(null) + members.map { it.id } + OTHER_DRIVER, choice,
            { id ->
                when (id) {
                    null -> model.t("calendar.driver.none")
                    OTHER_DRIVER -> model.t("calendar.driver.other")
                    else -> members.firstOrNull { it.id == id }?.displayName.orEmpty()
                }
            },
            Modifier.fillMaxWidth(),
        ) { id ->
            choice = id
            onChange(
                when (id) {
                    null -> null
                    OTHER_DRIVER -> name.trim().ifEmpty { null }?.let { Driver(name = it) }
                    else -> Driver(memberId = id)
                },
            )
        }
        if (choice == OTHER_DRIVER) {
            TextInput(model.t("calendar.driver.name"), name, Modifier.fillMaxWidth()) {
                name = it
                onChange(it.trim().ifEmpty { null }?.let { n -> Driver(name = n) })
            }
        }
    }
}

/** CAL-11: who drives on this date only (a carpool turn), or the usual drivers again. */
@Composable
internal fun DriversDialog(model: BooksModel, o: EventOccurrence, onClose: () -> Unit) {
    val members = remember { model.books.members.list() }
    var there by remember { mutableStateOf(o.driverThere) }
    var back by remember { mutableStateOf(o.driverBack) }
    FormDialog(model.t("calendar.driversFor", o.event.title, model.date(o.date)), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        if (model.act { model.books.calendar.setDrivers(o.event.id, o.date, there, back) } != null) onClose()
    }) {
        Text(model.t("calendar.driversFor.hint"), style = MaterialTheme.typography.bodySmall)
        DriverPicker(model, model.t("calendar.driverThere"), members, there, Modifier.fillMaxWidth()) { there = it }
        DriverPicker(model, model.t("calendar.driverBack"), members, back, Modifier.fillMaxWidth()) { back = it }
        if (o.driversChanged) {
            TextButton(onClick = { if (model.act { model.books.calendar.clearDrivers(o.event.id, o.date) } != null) onClose() }) { Text(model.t("calendar.driversUsual")) }
        }
    }
}

/** CAL-11: the activity's cost on this date, entered in the books as spending from a chosen account. */
@Composable
internal fun RecordCostDialog(model: BooksModel, o: EventOccurrence, onClose: () -> Unit) {
    val books = model.books
    val cost = o.event.cost ?: return
    val accounts = remember { books.accounts.list().map { it.account }.filter { it.currency == cost.currency } }
    val tree = remember { books.categories.tree() }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(activityCategory(tree)?.id) }
    FormDialog(model.t("calendar.recordCost"), model.t("common.save"), model.t("common.cancel"), canSave = accountId != null, onDismiss = onClose, onSave = {
        val ok = model.act { books.calendar.recordCost(o.event.id, o.date, accountId ?: throw ValidationException("error.invalidNumber"), categoryId) }
        if (ok != null) onClose()
    }) {
        Text(model.t("calendar.recordCost.body", model.money(cost), o.event.title, model.date(o.date)))
        Picker(model.t("bills.payingAccount"), accounts, accounts.firstOrNull { it.id == accountId }, { it.name }) { accountId = it.id }
        Picker(
            model.t("register.category"), listOf<Pair<ca.schippers.hfm.books.Category, Int>?>(null) + tree, tree.firstOrNull { it.first.id == categoryId },
            { it?.first?.name(model.language) ?: model.t("common.none") }, indent = { it?.second ?: 0 },
        ) { categoryId = it?.first?.id }
    }
}

