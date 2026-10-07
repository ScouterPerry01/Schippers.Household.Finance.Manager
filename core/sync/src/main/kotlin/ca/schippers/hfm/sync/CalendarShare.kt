package ca.schippers.hfm.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * CSY-03: who sees a brought-in calendar or item on the computer. [PRIVATE]: only its owner;
 * [BUSY]: others see the owner busy at those times, without the title or place; [SHARED]: the
 * details, for everyone who can see the account group chosen on the computer.
 */
enum class CalendarVisibility { PRIVATE, BUSY, SHARED }

/**
 * CSY-02: one calendar of the phone, whole, as it stands: the desktop replaces that calendar's items
 * from [fromDate] on with [items]. [removed] when the user stopped bringing it in, so the desktop
 * deletes it. [id] is new for every snapshot and acknowledged like a capture; [calendarKey] is the
 * phone's own id of the calendar, the same in every snapshot of it.
 */
@Serializable
data class CalendarSnapshot(
    val id: String,
    val calendarKey: String,
    val takenAtMillis: Long,
    val calendarName: String = "",
    val accountName: String? = null,
    val colour: Int? = null,
    val visibility: CalendarVisibility = CalendarVisibility.PRIVATE,
    val fromDate: String = "",
    val toDate: String = "",
    val items: List<CalendarInstance> = emptyList(),
    val removed: Boolean = false,
)

/**
 * One occurrence of an item (a repeating item has one per date). Dates are ISO dates and times
 * "HH:mm" in the phone's time zone; an all-day item has no times and [endDate] is its last day.
 * [visibility] is set when the user chose one for this item apart from its calendar.
 */
@Serializable
data class CalendarInstance(
    val eventId: String,
    val startDate: String,
    val endDate: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val title: String = "",
    val location: String? = null,
    val visibility: CalendarVisibility? = null,
)

/** A calendar Android syncs (Google, Outlook or Exchange, Samsung...), as CalendarContract lists it. */
@Serializable
data class DeviceCalendar(val id: Long, val name: String, val accountName: String? = null, val colour: Int? = null)

/**
 * One row of CalendarContract.Instances: repeating items come already expanded. An all-day item's
 * times are midnights in UTC, as Android stores them; other items' are instants.
 */
data class DeviceInstance(
    val calendarId: Long,
    val eventId: Long,
    val title: String?,
    val location: String?,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

/** What reads the phone's calendars: Android's CalendarContract on the phone, a list in tests. */
interface CalendarSource {
    fun calendars(): List<DeviceCalendar>

    /** The occurrences of the given calendars that overlap [fromMillis] to [toMillis]. */
    fun instances(calendarIds: Set<Long>, fromMillis: Long, toMillis: Long): List<DeviceInstance>
}

/** A calendar the user brings in, and who may see it (private by default, CSY-03). */
@Serializable
data class ChosenCalendar(
    val id: Long,
    val name: String,
    val accountName: String? = null,
    val colour: Int? = null,
    val visibility: CalendarVisibility = CalendarVisibility.PRIVATE,
)

/**
 * The phone's choices for bringing in its calendars, and what waits to be confirmed. [itemVisibility]
 * holds the visibility chosen for single items, by the phone's event id (every date of a repeating
 * item). [sent] is a fingerprint of the last snapshot made of each calendar, so an unchanged
 * calendar is not sent again; [pending] waits for the desktop's acknowledgement; [folder] are the
 * pending snapshots already left in the transfer folder; [failures] the desktop's reasons.
 */
@Serializable
data class CalendarShareSettings(
    val enabled: Boolean = false,
    val daysAhead: Int = DEFAULT_DAYS,
    val chosen: List<ChosenCalendar> = emptyList(),
    val itemVisibility: Map<String, CalendarVisibility> = emptyMap(),
    val sent: Map<String, String> = emptyMap(),
    val pending: List<CalendarSnapshot> = emptyList(),
    val folder: Set<String> = emptySet(),
    val failures: Map<String, String> = emptyMap(),
    val lastReadMillis: Long? = null,
    /** CSY-06: off, bring in only or both ways; null in settings kept before the choice existed (see [syncMode]). */
    val mode: CalendarSyncMode? = null,
    /** CSY-06: where RANN's Roost writes, when both ways. */
    val writeTarget: WriteTarget? = null,
    /** CSY-06: what RANN's Roost wrote, by its stable key, and in which calendar. */
    val written: Map<String, WrittenEvent> = emptyMap(),
    val writtenCalendarId: Long? = null,
    /** CSY-06: the phone-only RANN's Roost calendar, once made. */
    val localCalendarId: Long? = null,
) {
    /**
     * CSY-06: the mode chosen. Before the choice existed, bringing in was on or off with a switch: a phone
     * that had turned it off with calendars ticked stays off; otherwise bring in only, the default.
     */
    val syncMode: CalendarSyncMode
        get() = mode ?: if (enabled || chosen.isEmpty()) CalendarSyncMode.BRING_IN else CalendarSyncMode.OFF

    val bringsIn: Boolean get() = syncMode != CalendarSyncMode.OFF

    val writes: Boolean get() = syncMode == CalendarSyncMode.BOTH_WAYS

    /** The phone's event ids RANN's Roost wrote: never brought in as the user's own items. */
    val writtenEventIds: Set<Long> get() = written.values.map { it.eventId }.toSet()

    /** The phone-only RANN's Roost calendar is never brought in; in an account calendar, only the items it wrote are skipped. */
    fun isOwn(calendarId: Long): Boolean = calendarId == localCalendarId

    fun withMode(m: CalendarSyncMode): CalendarShareSettings = copy(mode = m, enabled = m != CalendarSyncMode.OFF)

    fun isPending(id: String): Boolean = pending.any { it.id == id }

    /** SYNC-04: snapshots the desktop stored are no longer kept. */
    fun acknowledged(ids: Collection<String>): CalendarShareSettings {
        val done = pending.filter { it.id in ids }
        if (done.isEmpty()) return this
        return copy(pending = pending - done.toSet(), folder = folder - done.map { it.id }.toSet(), failures = failures - done.map { it.calendarKey }.toSet())
    }

    /** SYNC-09: the desktop refused a snapshot; it is made again at the next transfer. */
    fun failed(id: String, reason: String): CalendarShareSettings {
        val snapshot = pending.firstOrNull { it.id == id } ?: return this
        return copy(pending = pending - snapshot, folder = folder - id, sent = sent - snapshot.calendarKey, failures = failures + (snapshot.calendarKey to reason))
    }

    companion object {
        const val DEFAULT_DAYS = 60
        val DAY_CHOICES = listOf(14, 30, 60, 90, 180)
    }
}

/**
 * CSY-01 to CSY-03 on the phone: reads the chosen calendars for the coming days and makes a snapshot
 * of each one that changed since it was last sent, and a removal for each calendar no longer brought
 * in. Plain Kotlin, so it is tested here; the Android app gives it CalendarContract as [CalendarSource].
 */
object CalendarShare {

    /** Items per calendar sent at most, the nearest first: a very busy shared calendar stays small. */
    const val MAX_ITEMS = 1500

    private const val MAX_TEXT = 300

    /** The settings with new snapshots queued in [CalendarShareSettings.pending]. */
    fun refresh(settings: CalendarShareSettings, source: CalendarSource, now: Long, zone: ZoneId, newId: () -> String): CalendarShareSettings {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val until = today.plusDays(settings.daysAhead.coerceIn(1, 366).toLong())
        val chosen = if (settings.bringsIn) settings.chosen.filterNot { settings.isOwn(it.id) } else emptyList()
        val own = settings.writtenEventIds
        var pending = settings.pending
        val sent = settings.sent.toMutableMap()
        if (chosen.isNotEmpty()) {
            val fromMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMillis = until.atStartOfDay(zone).toInstant().toEpochMilli()
            val byCalendar = source.instances(chosen.map { it.id }.toSet(), fromMillis, toMillis).groupBy { it.calendarId }
            for (c in chosen) {
                val key = c.id.toString()
                val items = byCalendar[c.id].orEmpty().filterNot { it.eventId in own }.mapNotNull { instance(it, zone, settings.itemVisibility) }
                    .filter { it.endDate >= today.toString() && it.startDate < until.toString() }
                    .sortedWith(compareBy({ it.startDate }, { it.startTime ?: "" }, { it.eventId })).take(MAX_ITEMS)
                val snapshot = CalendarSnapshot(
                    "", key, now, c.name.take(MAX_TEXT), c.accountName?.take(MAX_TEXT), c.colour, c.visibility, today.toString(), until.toString(), items,
                )
                val print = fingerprint(snapshot)
                if (sent[key] == print) continue
                pending = pending.filterNot { it.calendarKey == key } + snapshot.copy(id = newId())
                sent[key] = print
            }
        }
        val keep = chosen.map { it.id.toString() }.toSet()
        for (key in sent.keys - keep) {
            pending = pending.filterNot { it.calendarKey == key } + CalendarSnapshot(newId(), key, now, removed = true)
            sent.remove(key)
        }
        return settings.copy(pending = pending, sent = sent, folder = settings.folder.filter { id -> pending.any { it.id == id } }.toSet(), lastReadMillis = now)
    }

    /** One occurrence as the desktop receives it, in the phone's time zone; null when it has no length or date. */
    fun instance(row: DeviceInstance, zone: ZoneId, itemVisibility: Map<String, CalendarVisibility> = emptyMap()): CalendarInstance? {
        val eventId = row.eventId.toString()
        val visibility = itemVisibility[eventId]
        val title = row.title?.trim().orEmpty().take(MAX_TEXT)
        val location = row.location?.trim()?.ifBlank { null }?.take(MAX_TEXT)
        return if (row.allDay) {
            val start = Instant.ofEpochMilli(row.beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
            // The end is the midnight after the last day.
            val end = Instant.ofEpochMilli(maxOf(row.endMillis, row.beginMillis + DAY_MILLIS)).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            CalendarInstance(eventId, start.toString(), maxOf(start, end).toString(), title = title, location = location, visibility = visibility)
        } else {
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.beginMillis), zone)
            val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxOf(row.endMillis, row.beginMillis)), zone)
            CalendarInstance(eventId, start.toLocalDate().toString(), end.toLocalDate().toString(), hhmm(start), hhmm(end), title, location, visibility)
        }
    }

    /** The coming occurrences of the chosen calendars, for the list where single items are marked. */
    fun upcoming(settings: CalendarShareSettings, source: CalendarSource, now: Long, zone: ZoneId): List<Pair<ChosenCalendar, CalendarInstance>> {
        if (settings.chosen.isEmpty()) return emptyList()
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val until = today.plusDays(settings.daysAhead.coerceIn(1, 366).toLong())
        val byId = settings.chosen.filterNot { settings.isOwn(it.id) }.associateBy { it.id }
        if (byId.isEmpty()) return emptyList()
        val own = settings.writtenEventIds
        return source.instances(byId.keys, today.atStartOfDay(zone).toInstant().toEpochMilli(), until.atStartOfDay(zone).toInstant().toEpochMilli())
            .filterNot { it.eventId in own }
            .mapNotNull { row -> byId[row.calendarId]?.let { c -> instance(row, zone, settings.itemVisibility)?.let { c to it } } }
            .sortedWith(compareBy({ it.second.startDate }, { it.second.startTime ?: "" }))
    }

    /** Who sees an item: its own choice, else its calendar's. */
    fun effective(calendar: CalendarVisibility, item: CalendarInstance): CalendarVisibility = item.visibility ?: calendar

    /** Changes when anything the desktop would store changes, not with the time it was read. */
    fun fingerprint(s: CalendarSnapshot): String {
        val text = SyncJson.encodeToString(ListSerializer(CalendarInstance.serializer()), s.items) +
            "|${s.calendarName}|${s.accountName}|${s.colour}|${s.visibility}|${s.fromDate}"
        return MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    private fun hhmm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

    private const val DAY_MILLIS = 24L * 3600 * 1000
}
