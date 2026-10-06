package ca.schippers.hfm.importers

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * CSY-05: the events of an iCalendar (.ics) file (RFC 5545), for a one-time copy. Only VEVENT, with
 * repeats by day, week, month or year (INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY, BYMONTH) and the
 * dates left out (EXDATE); what is not understood is skipped and reported. Times are given in the
 * computer's time zone. The file comes from outside, so its size, its lines and the number of
 * events are limited.
 */
object ICalendar {

    /** The largest file read; a year of a busy calendar is far smaller. */
    const val MAX_BYTES = 5 * 1024 * 1024

    /** Events after this many are skipped. */
    const val MAX_EVENTS = 5000

    /** Longest kept text (title, place, description). */
    const val MAX_TEXT = 2000

    private const val MAX_LINE = 20_000

    /** Dates left out (EXDATE) kept per event: each becomes a cancelled date in the calendar. */
    const val MAX_EXDATES = 1000

    /** Days, weeks, months or years gone through for one file's repeats, at most: a file of many long repeats stays quick. */
    const val MAX_PERIODS = 200_000L

    /**
     * What [occurrences] may still go through, shared by the calls of one import. [exhausted] once
     * a repeat was cut short by it.
     */
    class Budget(var periods: Long = MAX_PERIODS) {
        var exhausted = false
    }

    enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** A day of the week in a BYDAY list; [nth] is its rank in the month or year (2 = second, -1 = last). */
    data class WeekDay(val day: DayOfWeek, val nth: Int? = null)

    data class Rule(
        val frequency: Frequency,
        val interval: Int = 1,
        val count: Int? = null,
        val until: LocalDate? = null,
        val byDay: List<WeekDay> = emptyList(),
        val byMonthDay: List<Int> = emptyList(),
        val byMonth: List<Int> = emptyList(),
    )

    /**
     * One event. An all-day event has no times and [endDate] is its last day. [recurrenceId] is set
     * on a changed occurrence of a repeating event (same [uid]): the date it replaces.
     */
    data class Event(
        val uid: String?,
        val title: String,
        val location: String?,
        val description: String?,
        val startDate: LocalDate,
        val startTime: LocalTime?,
        val endDate: LocalDate,
        val endTime: LocalTime?,
        val rule: Rule? = null,
        val exDates: Set<LocalDate> = emptySet(),
        val recurrenceId: LocalDate? = null,
    ) {
        val allDay: Boolean get() = startTime == null

        /** Minutes from start to end, for a timed event. */
        val durationMinutes: Long?
            get() = startTime?.let { ChronoUnit.MINUTES.between(LocalDateTime.of(startDate, it), LocalDateTime.of(endDate, endTime ?: it)) }
    }

    data class Calendar(val events: List<Event>, val notes: List<ImportNote>)

    /**
     * Reads [bytes] as an .ics file. Events that cannot be read, or repeat in a way not understood,
     * are left out with a note naming them.
     * @throws IllegalArgumentException when the file is larger than [MAX_BYTES] or is not a calendar.
     */
    fun parse(bytes: ByteArray, zone: ZoneId): Calendar {
        require(bytes.size <= MAX_BYTES) { "The calendar file is larger than ${MAX_BYTES / 1024 / 1024} MB" }
        val lines = unfold(bytes.decodeToString().removePrefix("﻿"))
        require(lines.any { it.equals("BEGIN:VCALENDAR", ignoreCase = true) }) { "Not an iCalendar file" }
        val events = ArrayList<Event>()
        val notes = ArrayList<ImportNote>()
        var current: MutableList<Property>? = null
        var depth = 0
        var tooMany = 0
        for (line in lines) {
            val p = property(line) ?: continue
            when {
                p.name == "BEGIN" && p.value.equals("VEVENT", true) && current == null -> { current = ArrayList(); depth = 0 }
                p.name == "BEGIN" && current != null -> depth++
                p.name == "END" && current != null && depth > 0 -> depth--
                p.name == "END" && p.value.equals("VEVENT", true) && current != null -> {
                    if (events.size >= MAX_EVENTS) tooMany++ else event(current, zone, notes)?.let { events += it }
                    current = null
                }
                current != null && depth == 0 -> current += p
            }
        }
        if (tooMany > 0) notes += ImportNote.of("icsTooMany", "$tooMany events past the first $MAX_EVENTS were skipped.", tooMany, MAX_EVENTS)
        return Calendar(events, notes)
    }

    /**
     * The dates [event] falls on between [from] and [to] (both included), at most [limit], without
     * its EXDATEs. COUNT counts from the first date, as the standard says. Without a COUNT, the
     * periods before [from] are skipped; the periods gone through are taken from [budget].
     */
    fun occurrences(event: Event, from: LocalDate, to: LocalDate, limit: Int = 1000, budget: Budget = Budget()): List<LocalDate> {
        val rule = event.rule ?: return if (event.startDate in from..to) listOf(event.startDate) else emptyList()
        val out = ArrayList<LocalDate>()
        var counted = 0
        val last = listOfNotNull(to, rule.until).min()
        var period = if (rule.count == null) firstPeriod(event.startDate, rule, from) else 0L
        // A period without any date (the 31st in a short month) is skipped; give up after many in a row.
        var empty = 0
        while (empty < 2000) {
            if (budget.periods <= 0) {
                budget.exhausted = true
                return out
            }
            budget.periods--
            val dates = datesOfPeriod(event.startDate, rule, period).filter { it >= event.startDate }.sorted()
            period += rule.interval
            if (dates.isEmpty()) {
                empty++
                if (periodStart(event.startDate, rule.frequency, period) > last) break
                continue
            }
            empty = 0
            for (d in dates) {
                if (d > last) return out
                counted++
                if (rule.count != null && counted > rule.count) return out
                if (d >= from && d !in event.exDates) {
                    out += d
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }

    /** The last period, a whole number of intervals from the start, that ends before [from]: the ones before it have no date to give. */
    private fun firstPeriod(start: LocalDate, rule: Rule, from: LocalDate): Long {
        if (from <= start) return 0
        val unit = when (rule.frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS
            Frequency.WEEKLY -> ChronoUnit.WEEKS
            Frequency.MONTHLY -> ChronoUnit.MONTHS
            Frequency.YEARLY -> ChronoUnit.YEARS
        }
        val units = unit.between(periodStart(start, rule.frequency, 0), periodStart(from, rule.frequency, 0))
        return maxOf(0L, (units / rule.interval - 1) * rule.interval)
    }

    // --- Reading ------------------------------------------------------------------------------------

    private class Property(val name: String, val params: Map<String, String>, val value: String)

    /** Joins folded lines (a line starting with a space or tab continues the one before). */
    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split("\r\n", "\n", "\r")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                if (out.last().length < MAX_LINE) out[out.size - 1] = (out.last() + raw.substring(1)).take(MAX_LINE)
            } else if (raw.isNotEmpty()) {
                out += raw.take(MAX_LINE)
            }
        }
        return out
    }

    /** NAME;PARAM=VALUE;PARAM="A:B":value */
    private fun property(line: String): Property? {
        var quoted = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) {
                colon = i
                break
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Property(head.first().uppercase(), params, line.substring(colon + 1))
    }

    private fun event(props: List<Property>, zone: ZoneId, notes: MutableList<ImportNote>): Event? {
        fun first(name: String) = props.firstOrNull { it.name == name }
        val title = first("SUMMARY")?.value?.let(::unescape)?.trim()?.take(MAX_TEXT)?.ifBlank { null }
        val label = title ?: "?"
        if (first("STATUS")?.value.equals("CANCELLED", ignoreCase = true)) return null
        val start = first("DTSTART")?.let { moment(it, zone) }
        if (start == null) {
            notes += ImportNote.of("icsNoDate", "\"$label\" has no start date that could be read and was skipped.", label)
            return null
        }
        val end = first("DTEND")?.let { moment(it, zone) } ?: first("DURATION")?.value?.let { duration(it) }?.let { d ->
            if (start.second == null) Pair(start.first.plusDays(maxOf(1, d.toDays())), null) else LocalDateTime.of(start.first, start.second).plus(d).let { it.toLocalDate() to it.toLocalTime() }
        }
        val (endDate, endTime) = when {
            start.second == null -> {
                // All day: DTEND is the day after the last day.
                val last = end?.first?.minusDays(1)
                (if (last == null || last < start.first) start.first else last) to null
            }
            end?.second != null && LocalDateTime.of(end.first, end.second) >= LocalDateTime.of(start.first, start.second) -> end.first to end.second
            else -> start.first to start.second
        }
        val rules = props.filter { it.name == "RRULE" }
        val rule = if (rules.isEmpty()) null else rules.singleOrNull()?.let { rule(it.value, zone) }
        if (rules.isNotEmpty() && rule == null) {
            notes += ImportNote.of("icsRule", "\"$label\" repeats in a way that could not be read and was skipped.", label)
            return null
        }
        if (props.any { it.name == "RDATE" }) notes += ImportNote.of("icsRdate", "\"$label\": extra dates (RDATE) were left out.", label)
        val exDates = props.asSequence().filter { it.name == "EXDATE" }.flatMap { p ->
            p.value.split(',').asSequence().mapNotNull { v -> moment(Property(p.name, p.params, v), zone)?.first }
        }.distinct().take(MAX_EXDATES).toSet()
        return Event(
            first("UID")?.value?.trim()?.take(MAX_TEXT), title ?: "", first("LOCATION")?.value?.let(::unescape)?.trim()?.take(MAX_TEXT)?.ifBlank { null },
            first("DESCRIPTION")?.value?.let(::unescape)?.trim()?.take(MAX_TEXT)?.ifBlank { null },
            start.first, start.second, endDate, endTime, rule, exDates, first("RECURRENCE-ID")?.let { moment(it, zone) }?.first,
        )
    }

    /** A date, or a date and time in [zone]: UTC times (Z) and times with a known TZID are converted. */
    private fun moment(p: Property, zone: ZoneId): Pair<LocalDate, LocalTime?>? = runCatching {
        val v = p.value.trim()
        if (p.params["VALUE"].equals("DATE", true) || v.length == 8) return@runCatching LocalDate.parse(v, BASIC_DATE) to null
        val utc = v.endsWith("Z")
        val local = LocalDateTime.parse(v.removeSuffix("Z").take(15), BASIC_DATE_TIME)
        val source: ZoneId? = if (utc) ZoneOffset.UTC else p.params["TZID"]?.let { id -> runCatching { ZoneId.of(id.removePrefix("/")) }.getOrNull() }
        val here = if (source == null) local else local.atZone(source).withZoneSameInstant(zone).toLocalDateTime()
        here.toLocalDate() to here.toLocalTime().withSecond(0).withNano(0)
    }.getOrNull()

    /** P1D, PT1H30M, P2W. */
    private fun duration(text: String): Duration? = runCatching {
        val t = text.trim().removePrefix("+")
        if (t.endsWith("W")) Duration.ofDays(7 * t.removePrefix("P").removeSuffix("W").toLong()) else Duration.parse(t)
    }.getOrNull()?.takeIf { !it.isNegative }

    private fun rule(text: String, zone: ZoneId): Rule? {
        val parts = text.split(';').filter { it.contains('=') }.associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
        val known = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST")
        if (parts.keys.any { it !in known }) return null
        val frequency = runCatching { Frequency.valueOf(parts["FREQ"].orEmpty().uppercase()) }.getOrNull() ?: return null
        if (parts["WKST"]?.uppercase()?.let { it != "MO" } == true && frequency == Frequency.WEEKLY) return null
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        if (interval !in 1..1000) return null
        val count = parts["COUNT"]?.let { it.toIntOrNull()?.takeIf { n -> n in 1..100_000 } ?: return null }
        val until = parts["UNTIL"]?.let { moment(Property("UNTIL", emptyMap(), it), zone)?.first ?: return null }
        val byDay = parts["BYDAY"]?.split(',')?.map { d -> weekDay(d.trim()) ?: return null }.orEmpty()
        val byMonthDay = parts["BYMONTHDAY"]?.split(',')?.map { d -> d.trim().toIntOrNull()?.takeIf { it != 0 && it in -31..31 } ?: return null }.orEmpty()
        val byMonth = parts["BYMONTH"]?.split(',')?.map { d -> d.trim().toIntOrNull()?.takeIf { it in 1..12 } ?: return null }.orEmpty()
        // A rank (2nd Tuesday) only makes sense by month or year; by year it needs the month.
        if (byDay.any { it.nth != null } && (frequency == Frequency.DAILY || frequency == Frequency.WEEKLY || (frequency == Frequency.YEARLY && byMonth.isEmpty()))) return null
        if (byDay.any { it.nth != null && it.nth !in -5..5 }) return null
        return Rule(frequency, interval, count, until, byDay, byMonthDay, byMonth)
    }

    private fun weekDay(text: String): WeekDay? {
        if (text.length < 2) return null
        val day = DAYS[text.takeLast(2).uppercase()] ?: return null
        val rank = text.dropLast(2)
        return if (rank.isEmpty()) WeekDay(day) else rank.removePrefix("+").toIntOrNull()?.takeIf { it != 0 }?.let { WeekDay(day, it) }
    }

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val n = text[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    // --- Repeats ------------------------------------------------------------------------------------

    private fun periodStart(start: LocalDate, frequency: Frequency, period: Long): LocalDate = when (frequency) {
        Frequency.DAILY -> start.plusDays(period)
        Frequency.WEEKLY -> start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(period)
        Frequency.MONTHLY -> start.withDayOfMonth(1).plusMonths(period)
        Frequency.YEARLY -> start.withDayOfYear(1).plusYears(period)
    }

    /** The dates of the [period]-th day, week, month or year from the start (before COUNT and UNTIL). */
    private fun datesOfPeriod(start: LocalDate, rule: Rule, period: Long): List<LocalDate> {
        val days = rule.byDay.map { it.day }.toSet()
        return when (rule.frequency) {
            Frequency.DAILY -> listOf(start.plusDays(period)).filter { d ->
                (days.isEmpty() || d.dayOfWeek in days) && (rule.byMonth.isEmpty() || d.monthValue in rule.byMonth) &&
                    (rule.byMonthDay.isEmpty() || rule.byMonthDay.any { monthDay(d.withDayOfMonth(1), it) == d })
            }
            Frequency.WEEKLY -> {
                val monday = periodStart(start, Frequency.WEEKLY, period)
                val wanted = days.ifEmpty { setOf(start.dayOfWeek) }
                (0L until 7L).map { monday.plusDays(it) }.filter { it.dayOfWeek in wanted && (rule.byMonth.isEmpty() || it.monthValue in rule.byMonth) }
            }
            Frequency.MONTHLY -> {
                val first = periodStart(start, Frequency.MONTHLY, period)
                if (rule.byMonth.isNotEmpty() && first.monthValue !in rule.byMonth) emptyList() else inMonth(first, start, rule)
            }
            Frequency.YEARLY -> {
                val year = periodStart(start, Frequency.YEARLY, period)
                val months = rule.byMonth.ifEmpty { listOf(start.monthValue) }
                months.flatMap { m -> inMonth(year.withMonth(m), start, rule) }
            }
        }
    }

    /** The dates in the month starting [first]: by rank or weekday, by day of the month, or the start's day. */
    private fun inMonth(first: LocalDate, start: LocalDate, rule: Rule): List<LocalDate> {
        val last = first.with(TemporalAdjusters.lastDayOfMonth())
        val byDay = if (rule.byDay.isEmpty()) {
            null
        } else {
            rule.byDay.flatMap { wd ->
                val all = generateSequence(first.with(TemporalAdjusters.nextOrSame(wd.day))) { it.plusWeeks(1) }.takeWhile { it <= last }.toList()
                when {
                    wd.nth == null -> all
                    wd.nth > 0 -> listOfNotNull(all.getOrNull(wd.nth - 1))
                    else -> listOfNotNull(all.getOrNull(all.size + wd.nth))
                }
            }.toSet()
        }
        val byMonthDay = if (rule.byMonthDay.isEmpty()) null else rule.byMonthDay.mapNotNull { monthDay(first, it) }.toSet()
        return when {
            byDay != null && byMonthDay != null -> byDay.intersect(byMonthDay).toList()
            byDay != null -> byDay.toList()
            byMonthDay != null -> byMonthDay.toList()
            // The start's day; a month without it (the 31st, February 29) is skipped, as the standard says.
            else -> listOfNotNull(if (start.dayOfMonth <= last.dayOfMonth) first.withDayOfMonth(start.dayOfMonth) else null)
        }
    }

    /** Day [n] of the month starting [first] (negative from the end), or null when it has no such day. */
    private fun monthDay(first: LocalDate, n: Int): LocalDate? {
        val length = first.lengthOfMonth()
        val day = if (n > 0) n else length + n + 1
        return if (day in 1..length) first.withDayOfMonth(day) else null
    }

    private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )
}
