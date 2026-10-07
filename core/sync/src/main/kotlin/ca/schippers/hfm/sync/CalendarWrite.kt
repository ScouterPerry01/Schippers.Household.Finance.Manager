package ca.schippers.hfm.sync

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * CSY-06: how the phone's calendars work with RANN's Roost. [OFF]: nothing is read or written;
 * [BRING_IN] (the default): the calendars the user ticks are brought in, nothing is written;
 * [BOTH_WAYS]: also writes the household's appointments, hours and bills into a calendar the user chooses.
 */
enum class CalendarSyncMode { OFF, BRING_IN, BOTH_WAYS }

/**
 * CSY-06: where RANN's Roost writes. [phoneOnly]: its own calendar, kept only on this phone (a local
 * calendar that no account syncs); otherwise [calendarId], a writable calendar of one of the phone's
 * accounts, which then syncs with that account's provider.
 */
@Serializable
data class WriteTarget(
    val phoneOnly: Boolean,
    val calendarId: Long? = null,
    val name: String = "",
    val accountName: String? = null,
    val colour: Int? = null,
)

/** One item RANN's Roost wrote: the phone's event id, a fingerprint of what was written, and its first day. */
@Serializable
data class WrittenEvent(val eventId: Long, val print: String, val date: String)

/**
 * One item to write. [key] is stable from one transfer to the next, so a changed item is updated in
 * place and a removed one deleted. An item without [startTime] is all day, from [startDate] to
 * [endDate] (its last day); a timed item ends at [endTime] on [endDate].
 */
data class WriteItem(
    val key: String,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String? = null,
    val description: String? = null,
) {
    val allDay: Boolean get() = startTime == null

    /** Changes when anything written changes. */
    val print: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest(listOf(title, startDate, endDate, startTime, endTime, location, description).joinToString("\u0001").encodeToByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
}

/** The times as CalendarContract stores them: an all-day item from midnight UTC to the midnight after its last day. */
data class EventTimes(val startMillis: Long, val endMillis: Long, val allDay: Boolean, val timeZone: String)

/** The words written, in the app's language. */
interface WriteTexts {
    /** The title of a medical appointment: "Health appointment". */
    val health: String

    /** "Bill due: Hydro". */
    fun bill(name: String): String

    /** "Alex: work"; [kind] is WORK, SCHOOL or OTHER. */
    fun schedule(person: String, kind: String): String

    fun forWhom(names: String): String
    fun driverThere(name: String): String
    fun driverBack(name: String): String

    /** The last line of every description: written by RANN's Roost, changes made here are replaced. */
    val footer: String
}

/** What to do in the calendar: insert, update and delete; [forget] are past items no longer followed. */
data class WritePlan(
    val inserts: List<WriteItem> = emptyList(),
    val updates: List<Pair<WrittenEvent, WriteItem>> = emptyList(),
    val deletes: Map<String, WrittenEvent> = emptyMap(),
    val forget: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty() && forget.isEmpty()
}

/** Writes into the phone's calendars: Android's CalendarContract on the phone, a list in tests. */
interface CalendarWriter {
    /** The new event's id, or null when it could not be written. */
    fun insert(calendarId: Long, item: WriteItem): Long?

    /** False when the event is no longer there (the user deleted it). */
    fun update(eventId: Long, item: WriteItem): Boolean

    fun delete(eventId: Long)
}

/**
 * CSY-06: what RANN's Roost writes into the chosen calendar, and how it keeps it current. Plain
 * Kotlin, so it is tested here; the Android app gives it CalendarContract as [CalendarWriter].
 *
 * What is written: the coming 60 days of the household's appointments and events, each person's work
 * and school hours, and bills due, as the computer sent them to this phone (only what this phone's
 * user may see). The privacy rule is the same in every calendar, as other apps on the phone can read
 * the phone-only calendar too: a medical appointment is written as "Health appointment", with no place
 * and no details; a bill is "Bill due: name", never its amount.
 */
object CalendarWritePlanner {

    /** Past items stay as written; they are no longer followed after this many days. */
    const val KEEP_PAST_DAYS = 30L

    private const val MAX_TEXT = 300

    /** Whether anything should be written now: both ways, a target chosen, and paired with a computer. */
    fun wanted(settings: CalendarShareSettings, paired: Boolean): Boolean = settings.writes && paired && settings.writeTarget != null

    /**
     * Whether what was written must first be removed: writing stopped, or it goes into another calendar
     * now. [calendarId] is the target's calendar; null for a phone-only calendar not made yet.
     */
    fun mustRemove(settings: CalendarShareSettings, paired: Boolean, calendarId: Long?): Boolean {
        val anything = settings.written.isNotEmpty() || settings.writtenCalendarId != null || settings.localCalendarId != null
        if (!anything) return false
        if (!wanted(settings, paired)) return true
        val target = settings.writeTarget!!
        // The phone-only calendar is removed once the user writes into an account calendar instead.
        if (!target.phoneOnly && settings.localCalendarId != null) return true
        return settings.writtenCalendarId != null && settings.writtenCalendarId != calendarId
    }

    /** The settings once everything written was removed. */
    fun cleared(settings: CalendarShareSettings): CalendarShareSettings =
        settings.copy(written = emptyMap(), writtenCalendarId = null, localCalendarId = null)

    /** The items to write, from today through the agenda's last day. */
    fun desired(reference: ReferenceData?, texts: WriteTexts, today: LocalDate): List<WriteItem> {
        reference ?: return emptyList()
        val last = Agenda.lastDay(today)
        fun inWindow(d: LocalDate) = !d.isBefore(today) && !d.isAfter(last)
        val out = ArrayList<WriteItem>()
        for (e in reference.events) {
            val d = date(e.date)?.takeIf(::inWindow) ?: continue
            val start = time(e.time)
            val medical = e.category == "MEDICAL"
            val lines = listOfNotNull(
                e.forWhom?.takeIf { it.isNotBlank() }?.let(texts::forWhom),
                e.driverThere?.takeIf { it.isNotBlank() && !medical }?.let(texts::driverThere),
                e.driverBack?.takeIf { it.isNotBlank() && !medical }?.let(texts::driverBack),
                texts.footer,
            )
            val end = start?.plusHours(1)
            out += WriteItem(
                key = "e:${e.id}",
                title = clip(if (medical) texts.health else e.title),
                startDate = d,
                endDate = if (start != null && end!!.isBefore(start)) d.plusDays(1) else d,
                startTime = start,
                endTime = end,
                location = if (medical) null else e.location?.trim()?.ifBlank { null }?.let(::clip),
                description = lines.joinToString("\n"),
            )
        }
        for (s in reference.schedules.sortedWith(compareBy({ it.date }, { it.person }, { it.kind }, { it.start }))) {
            val d = date(s.date)?.takeIf(::inWindow) ?: continue
            val start = time(s.start) ?: continue
            val end = time(s.end) ?: continue
            out += WriteItem(
                key = "s:${s.person}|${s.kind}|${s.date}",
                title = clip(texts.schedule(s.person, s.kind)),
                startDate = d,
                // Hours that end at or before they start end the next day (a night shift).
                endDate = if (!end.isAfter(start)) d.plusDays(1) else d,
                startTime = start,
                endTime = end,
                location = s.label?.trim()?.ifBlank { null }?.let(::clip),
                description = texts.footer,
            )
        }
        for (b in reference.bills) {
            val d = date(b.dueDate)?.takeIf(::inWindow) ?: continue
            out += WriteItem(key = "b:${b.name}|${b.dueDate}", title = clip(texts.bill(b.name)), startDate = d, endDate = d, description = texts.footer)
        }
        // Two items with the same key (two shifts in a day, two bills of the same name) are told apart by their order.
        val seen = HashMap<String, Int>()
        return out.map { item ->
            val n = seen.merge(item.key, 1, Int::plus)!!
            if (n == 1) item else item.copy(key = "${item.key}#$n")
        }
    }

    /** What changes in the calendar to make it match [desired]. Past items are left as they are. */
    fun plan(desired: List<WriteItem>, written: Map<String, WrittenEvent>, today: LocalDate): WritePlan {
        val wantedKeys = desired.associateBy { it.key }
        val inserts = desired.filter { it.key !in written }
        val updates = desired.mapNotNull { item -> written[item.key]?.takeIf { it.print != item.print }?.let { it to item } }
        val deletes = LinkedHashMap<String, WrittenEvent>()
        val forget = HashSet<String>()
        for ((key, w) in written) {
            if (key in wantedKeys) continue
            val d = date(w.date)
            when {
                d != null && d.isBefore(today.minusDays(KEEP_PAST_DAYS)) -> forget += key
                d != null && d.isBefore(today) -> Unit
                else -> deletes[key] = w
            }
        }
        return WritePlan(inserts, updates, deletes, forget)
    }

    /** Carries out [plan] into [calendarId]; returns what is written afterwards. */
    fun apply(plan: WritePlan, calendarId: Long, writer: CalendarWriter, written: Map<String, WrittenEvent>): Map<String, WrittenEvent> {
        val out = LinkedHashMap(written)
        for ((key, w) in plan.deletes) {
            writer.delete(w.eventId)
            out.remove(key)
        }
        plan.forget.forEach { out.remove(it) }
        for ((w, item) in plan.updates) {
            if (writer.update(w.eventId, item)) {
                out[item.key] = WrittenEvent(w.eventId, item.print, item.startDate.toString())
            } else {
                // Deleted in the calendar by the user: written again, as it changed.
                out.remove(item.key)
                writer.insert(calendarId, item)?.let { out[item.key] = WrittenEvent(it, item.print, item.startDate.toString()) }
            }
        }
        for (item in plan.inserts) {
            writer.insert(calendarId, item)?.let { out[item.key] = WrittenEvent(it, item.print, item.startDate.toString()) }
        }
        return out
    }

    /** Deletes everything written. */
    fun removeAll(written: Map<String, WrittenEvent>, writer: CalendarWriter) {
        written.values.forEach { writer.delete(it.eventId) }
    }

    /** The times of [item] as Android stores them: all-day items in UTC, others in the phone's time zone. */
    fun times(item: WriteItem, zone: ZoneId): EventTimes =
        if (item.allDay) {
            EventTimes(
                item.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                maxOf(item.endDate, item.startDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                true, "UTC",
            )
        } else {
            val start = item.startDate.atTime(item.startTime).atZone(zone).toInstant().toEpochMilli()
            val end = item.endDate.atTime(item.endTime ?: item.startTime!!.plusHours(1)).atZone(zone).toInstant().toEpochMilli()
            EventTimes(start, maxOf(end, start), false, zone.id)
        }

    private fun clip(text: String) = text.trim().take(MAX_TEXT)

    private fun date(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun time(text: String?): LocalTime? = text?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}
