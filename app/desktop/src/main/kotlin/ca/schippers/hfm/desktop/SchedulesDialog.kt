package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.PersonSchedule
import ca.schippers.hfm.books.ScheduleException
import ca.schippers.hfm.books.ScheduleKind
import ca.schippers.hfm.books.ScheduleService
import ca.schippers.hfm.books.ScheduleShift
import ca.schippers.hfm.books.ValidationException
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * CAL-09: the household's work and school schedules, or one person's ([memberId]), from the Calendar
 * and from Household members.
 */
@Composable
internal fun SchedulesDialog(model: BooksModel, memberId: String?, onClose: () -> Unit) {
    val books = model.books
    val schedules = remember(model.revision) { books.schedules.list().filter { memberId == null || it.memberId == memberId } }
    val people = remember(model.revision) { CalendarPeople.of(model) }
    var editing by remember { mutableStateOf<PersonSchedule?>(null) }
    var deleting by remember { mutableStateOf<PersonSchedule?>(null) }

    WideDialog(model.t("schedule.title"), model.t("common.close"), onClose) {
        Text(model.t("schedule.intro"), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            if (schedules.isEmpty()) Text(model.t("schedule.none"), Modifier.padding(8.dp))
            for (s in schedules.sortedWith(compareBy({ people.order(it.memberId) }, { it.startDate }))) {
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(people.color(s.memberId)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                listOfNotNull(people.name(s.memberId), model.t("scheduleKind.${s.kind}"), s.label).joinToString(" · "),
                                fontWeight = FontWeight.Medium,
                            )
                            Text(hoursSummary(model, s), style = MaterialTheme.typography.bodySmall)
                            Text(
                                listOfNotNull(
                                    s.endDate?.let { model.t("schedule.fromTo", model.date(s.startDate), model.date(it)) } ?: model.t("schedule.from", model.date(s.startDate)),
                                    model.t("schedule.holidaysOff.short").takeIf { s.holidaysOff },
                                    s.exceptions.size.takeIf { it > 0 }?.let { model.t("schedule.exceptionCount", it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { editing = s }) { Text(model.t("common.edit")) }
                        TextButton(onClick = { deleting = s }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Button(onClick = { editing = newSchedule(model, memberId) }) { Text(model.t("schedule.add")) }
    }
    editing?.let { s -> ScheduleEditor(model, s) { editing = null } }
    deleting?.let { s ->
        AskBeforeDeleting(model, model.t("schedule.delete", people.name(s.memberId), model.t("scheduleKind.${s.kind}")), onDismiss = { deleting = null }) {
            model.act { books.schedules.delete(s.id) } != null
        }
    }
}

/** A new schedule, Monday to Friday 9:00 to 17:00, in the household's shared group (CAL-09). */
private fun newSchedule(model: BooksModel, memberId: String?): PersonSchedule? {
    val group = model.editableGroups().firstOrNull { !it.isPrivate } ?: model.editableGroups().firstOrNull()
    if (group == null) {
        model.error = model.t("error.noEditableGroup")
        return null
    }
    val member = memberId ?: model.books.members.list().firstOrNull()?.id ?: run {
        model.error = model.t("error.scheduleMember")
        return null
    }
    val weekdays = (1..5).map { ScheduleShift(0, DayOfWeek(it), kotlinx.datetime.LocalTime(9, 0), kotlinx.datetime.LocalTime(17, 0)) }
    return PersonSchedule("", group.id, member, ScheduleKind.WORK, null, today(), null, 1, true, null, weekdays)
}

/** "Mon, Tue, Wed 8:00–16:30; Thu 12:00–20:00", week by week for a rotation. */
internal fun hoursSummary(model: BooksModel, s: PersonSchedule): String {
    val locale = model.language.locale
    fun day(d: DayOfWeek) = java.time.DayOfWeek.of(d.ordinal + 1).getDisplayName(java.time.format.TextStyle.SHORT, locale)
    val weeks = (0 until s.rotationWeeks).map { w ->
        s.shifts.filter { it.week == w }.sortedBy { it.dayOfWeek.ordinal }.groupBy { it.start to it.end }.entries
            .joinToString("; ") { (hours, list) -> list.joinToString(", ") { day(it.dayOfWeek) } + " ${clock(hours.first)}–${clock(hours.second)}" }
            .ifEmpty { model.t("schedule.weekOff") }
    }
    return if (s.rotationWeeks == 1) weeks.single() else weeks.withIndex().joinToString(" | ") { (i, text) -> model.t("schedule.weekHours", model.t("schedule.week", i + 1), text) }
}

/** One day's hours as typed in the editor. */
private data class DayHours(val on: Boolean, val start: String, val end: String)

/** CAL-09: add or change a schedule: person, kind, days and hours week by week, dates, holidays and exceptions. */
@Composable
private fun ScheduleEditor(model: BooksModel, start: PersonSchedule, onClose: () -> Unit) {
    val books = model.books
    val members = remember { books.members.list() }
    val locale = model.language.locale
    var memberId by remember { mutableStateOf(start.memberId) }
    var kind by remember { mutableStateOf(start.kind) }
    var label by remember { mutableStateOf(start.label.orEmpty()) }
    var from by remember { mutableStateOf(start.startDate.toString()) }
    var until by remember { mutableStateOf(start.endDate?.toString().orEmpty()) }
    var rotation by remember { mutableStateOf(start.rotationWeeks) }
    var hours by remember {
        mutableStateOf(start.shifts.associate { (it.week to it.dayOfWeek.ordinal) to DayHours(true, clock(it.start), clock(it.end)) })
    }
    var holidaysOff by remember { mutableStateOf(start.holidaysOff) }
    var exceptions by remember { mutableStateOf(start.exceptions.sortedBy { it.date }) }
    var notes by remember { mutableStateOf(start.notes.orEmpty()) }
    var groupId by remember { mutableStateOf(start.groupId) }
    // The exception being added.
    var exDate by remember { mutableStateOf("") }
    var exOff by remember { mutableStateOf(true) }
    var exStart by remember { mutableStateOf("") }
    var exEnd by remember { mutableStateOf("") }
    var exReason by remember { mutableStateOf("") }

    fun parse(text: String) = parseTime(text) ?: throw ValidationException("error.invalidTime")

    FormDialog(model.t(if (start.id.isEmpty()) "schedule.add" else "schedule.edit"), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            fun date(text: String) = text.trim().ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw ValidationException("error.invalidDate") } }
            val shifts = hours.filter { (k, v) -> v.on && k.first < rotation }.map { (k, v) -> ScheduleShift(k.first, DayOfWeek(k.second + 1), parse(v.start), parse(v.end)) }
            books.schedules.save(
                start.copy(
                    groupId = groupId, memberId = memberId, kind = kind, label = label, startDate = date(from) ?: throw ValidationException("error.invalidDate"),
                    endDate = date(until), rotationWeeks = rotation, holidaysOff = holidaysOff, notes = notes, shifts = shifts, exceptions = exceptions,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("schedule.person"), members, members.firstOrNull { it.id == memberId }, { it.displayName }, Modifier.weight(1f)) { memberId = it.id }
                Picker(model.t("schedule.kind"), ScheduleKind.entries, kind, { model.t("scheduleKind.$it") }, Modifier.weight(1f)) { kind = it }
            }
            TextInput(model.t("schedule.label"), label, supporting = model.t("schedule.label.hint")) { label = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput(model.t("schedule.starts"), from, Modifier.weight(1f)) { from = it }
                DateInput(model.t("schedule.ends"), until, Modifier.weight(1f)) { until = it }
            }
            Picker(
                model.t("schedule.repeats"), (1..ScheduleService.MAX_ROTATION_WEEKS).toList(), rotation,
                { if (it == 1) model.t("schedule.everyWeek") else model.t("schedule.rotation", it) },
            ) { rotation = it }
            if (rotation > 1) Text(model.t("schedule.rotation.hint"), style = MaterialTheme.typography.bodySmall)
            for (w in 0 until rotation) {
                if (rotation > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.t("schedule.week", w + 1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (w == 0) {
                            TextButton(onClick = {
                                val first = hours.filterKeys { it.first == 0 }
                                hours = hours.filterKeys { it.first == 0 } + (1 until rotation).flatMap { n -> first.map { (k, v) -> (n to k.second) to v } }
                            }) { Text(model.t("schedule.copyWeek")) }
                        }
                    }
                }
                for (d in 0 until 7) {
                    val key = w to d
                    val h = hours[key] ?: DayHours(false, "", "")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(170.dp)) {
                            LabeledCheckbox(java.time.DayOfWeek.of(d + 1).getDisplayName(java.time.format.TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }, h.on) { on ->
                                // A day ticked takes the hours of the first day already on.
                                val usual = hours.values.firstOrNull { it.on } ?: DayHours(true, "9:00", "17:00")
                                hours = hours + (key to if (on) DayHours(true, h.start.ifEmpty { usual.start }, h.end.ifEmpty { usual.end }) else h.copy(on = false))
                            }
                        }
                        if (h.on) {
                            TextInput(model.t("schedule.startTime"), h.start, Modifier.width(110.dp), error = if (parseTime(h.start) == null) model.t("error.invalidTime") else null) { hours = hours + (key to h.copy(start = it)) }
                            TextInput(model.t("schedule.endTime"), h.end, Modifier.width(110.dp), error = if (parseTime(h.end) == null) model.t("error.invalidTime") else null) { hours = hours + (key to h.copy(end = it)) }
                        }
                    }
                }
            }
            Text(model.t("schedule.night.hint"), style = MaterialTheme.typography.bodySmall)
            val province = remember(memberId) { model.t("province.${books.provinceOf(memberId)}") }
            LabeledCheckbox(model.t("schedule.holidaysOff", province), holidaysOff) { holidaysOff = it }

            // Exceptions: days off and changed hours.
            Text(model.t("schedule.exceptions"), style = MaterialTheme.typography.labelLarge)
            if (exceptions.isEmpty()) Text(model.t("schedule.exceptions.none"), style = MaterialTheme.typography.bodySmall)
            for (e in exceptions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            model.date(e.date),
                            if (e.off) model.t("schedule.dayOff") else "${e.start?.let(::clock)}–${e.end?.let(::clock)}",
                            e.reason,
                        ).joinToString(" · "),
                        Modifier.weight(1f),
                    )
                    RemoveButton(model.t("schedule.removeException")) { exceptions = exceptions - e }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateInput(model.t("calendar.date"), exDate, Modifier.weight(1f)) { exDate = it }
                LabeledCheckbox(model.t("schedule.dayOff"), exOff) { exOff = it }
            }
            if (!exOff) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(model.t("schedule.startTime"), exStart, Modifier.weight(1f)) { exStart = it }
                    TextInput(model.t("schedule.endTime"), exEnd, Modifier.weight(1f)) { exEnd = it }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextInput(model.t("schedule.reason"), exReason, Modifier.weight(1f), supporting = model.t("schedule.reason.hint")) { exReason = it }
                OutlinedButton(onClick = {
                    model.act {
                        val date = runCatching { LocalDate.parse(exDate.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }
                        val e = if (exOff) ScheduleException(date, true, reason = exReason.trim().ifEmpty { null })
                        else ScheduleException(date, false, parse(exStart), parse(exEnd), exReason.trim().ifEmpty { null })
                        exceptions = (exceptions.filter { it.date != date } + e).sortedBy { it.date }
                        exDate = ""
                        exReason = ""
                    }
                }) { Text(model.t("schedule.addException")) }
            }
            TextInput(model.t("calendar.notes"), notes, singleLine = false) { notes = it }
            GroupPicker(model, groupId, enabled = start.id.isEmpty()) { groupId = it.id }
            if (start.id.isEmpty()) PrivateGroupHint(model) { groupId = it }
        }
    }
}
