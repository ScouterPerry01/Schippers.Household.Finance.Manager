package ca.schippers.hfm.books

import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.sync.CalendarShare
import ca.schippers.hfm.sync.CalendarSnapshot
import ca.schippers.hfm.sync.CalendarVisibility
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.security.MessageDigest
import ca.schippers.hfm.data.ledger.Brought_in_item as ItemRow

/**
 * CSY-04: one occurrence of an item brought in from a person's phone, read-only. [title] and
 * [location] are null for a busy-only entry another person sees ("Alex: busy"). The times are null
 * for an all-day item, whose [endDate] is its last day.
 */
data class BroughtInItem(
    val id: String,
    val calendarId: String,
    val groupId: String,
    val ownerUserId: String,
    val ownerName: String,
    /** The calendar's name and account on the phone, when the viewer may see them. */
    val sourceName: String?,
    val accountName: String?,
    val colour: Int?,
    val visibility: CalendarVisibility,
    val title: String?,
    val location: String?,
    val startDate: LocalDate,
    val startTime: LocalTime?,
    val endDate: LocalDate,
    val endTime: LocalTime?,
) {
    val busyOnly: Boolean get() = title == null
    val allDay: Boolean get() = startTime == null
}

/** A calendar the signed-in user brought in from a phone, as the computer keeps it (CSY-01, CSY-03). */
data class BroughtInCalendar(
    val id: String,
    val deviceId: String,
    val sourceName: String,
    val accountName: String?,
    val colour: Int?,
    val visibility: CalendarVisibility,
    /** The owner's private group, which keeps the calendar and its private and busy-only items. */
    val privateGroupId: String,
    /** The group others see the shared and busy-only items in; the private group when there is none. */
    val othersGroupId: String,
    val items: Int,
    val updatedAt: Long,
)

/**
 * CSY-01 to CSY-04 on the computer: the calendars each person brings in from their phone. The
 * owner's private group keeps every private and busy-only item in full; the group chosen for others
 * (the shared group by default) keeps the shared items in full and the busy-only ones as a time
 * range alone, so their title and place never reach a ledger others can open (HH-11). Items are
 * read-only: they change only when the phone sends the calendar again.
 */
class BroughtInCalendarService internal constructor(private val books: Books) {

    /**
     * CSY-02: stores a calendar the phone sent whole, replacing its items from the snapshot's first
     * day on, or deletes it when the phone stopped bringing it in. An older snapshot than the one
     * kept is ignored. The signed-in user is the phone's owner.
     */
    fun receive(deviceId: String, snapshot: CalendarSnapshot, now: Long) {
        val id = calendarId(deviceId, snapshot.calendarKey)
        val groups = books.groups()
        val kept = groups.firstNotNullOfOrNull { g -> books.ledger(g).broughtInCalendarQueries.broughtInCalendarById(id).executeAsOneOrNull()?.let { g to it } }
        if (kept != null && kept.second.snapshot_at > snapshot.takenAtMillis) return
        if (snapshot.removed) {
            delete(id)
            return
        }
        val private = ownPrivateGroup()
        val master = books.ledger(private).broughtInCalendarQueries.broughtInCalendarById(id).executeAsOneOrNull()
        val others = master?.target_group_id?.let { t -> writable().firstOrNull { it.id == t } } ?: defaultOthersGroup() ?: private
        val from = runCatching { LocalDate.parse(snapshot.fromDate) }.getOrElse { books.today() }
        val items = snapshot.items.take(MAX_ITEMS).mapNotNull { i ->
            if (i.eventId.length > MAX_ID) return@mapNotNull null
            val start = runCatching { LocalDate.parse(i.startDate) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(i.endDate) }.getOrNull()?.takeIf { it >= start } ?: start
            Stored(
                itemId(id, i.eventId, i.startDate, i.startTime), i.eventId, CalendarShare.effective(snapshot.visibility, i),
                i.title.take(MAX_TEXT), i.location?.take(MAX_TEXT), start, i.startTime?.let(::parseTime), end, i.endTime?.let(::parseTime),
            )
        }
        val meta = Meta(id, deviceId, snapshot.calendarName.take(MAX_TEXT), snapshot.accountName?.take(MAX_TEXT), snapshot.colour, snapshot.visibility, snapshot.takenAtMillis)
        val before = master?.let { CalendarVisibility.valueOf(it.visibility) }
        if (before != null && snapshot.visibility < before) {
            // CSY-03: a calendar made less visible takes its past items along (the phone no longer
            // sends them): none of them stays more visible than the calendar now is.
            val past = fullItems(id).values.filter { it.endDate < from }.map { it.narrowed(snapshot.visibility) }
            place(meta, past + items, null, private, others, now)
        } else {
            place(meta, items, from, private, others, now)
        }
    }

    /** The calendars the signed-in user brought in, from every phone. */
    fun calendars(): List<BroughtInCalendar> {
        val groups = books.groups()
        return groups.filter { it.isPrivate && it.ownerUserId == books.userId }.flatMap { g ->
            books.ledger(g).broughtInCalendarQueries.broughtInCalendars().executeAsList().filter { it.owner_user_id == books.userId }.map {
                // Each item once, though a busy-only one is kept in both groups.
                val count = groups.filter { o -> o.id == g.id || o.id == it.target_group_id }
                    .flatMap { o -> books.ledger(o).broughtInCalendarQueries.broughtInItemsOf(it.id).executeAsList().map { i -> i.id } }.distinct().size
                BroughtInCalendar(
                    it.id, it.device_id, it.source_name.orEmpty(), it.account_name, it.colour?.toInt(), CalendarVisibility.valueOf(it.visibility),
                    g.id, it.target_group_id ?: g.id, count, it.updated_at,
                )
            }
        }.sortedBy { it.sourceName.lowercase() }
    }

    /**
     * CSY-03: moves what others see of the calendar [calendarId] to [groupId] (the owner's private
     * group keeps it all to themselves).
     */
    fun setOthersGroup(calendarId: String, groupId: String) {
        val private = ownPrivateGroup()
        val master = books.ledger(private).broughtInCalendarQueries.broughtInCalendarById(calendarId).executeAsOneOrNull()
            ?: throw ca.schippers.hfm.data.AccessDeniedException("Calendar not found")
        val target = writable().firstOrNull { it.id == groupId } ?: throw ca.schippers.hfm.data.AccessDeniedException("You cannot add to this account group")
        val meta = Meta(calendarId, master.device_id, master.source_name.orEmpty(), master.account_name, master.colour?.toInt(), CalendarVisibility.valueOf(master.visibility), master.snapshot_at)
        place(meta, fullItems(calendarId).values.toList(), null, private, target, books.now())
    }

    /** Every item of the calendar in full: the private group's items and the shared ones kept for others. */
    private fun fullItems(calendarId: String): Map<String, Stored> {
        val full = LinkedHashMap<String, Stored>()
        for (g in writable()) {
            books.ledger(g).broughtInCalendarQueries.broughtInItemsOf(calendarId).executeAsList().filter { it.title != null }.forEach { full[it.id] = it.toStored() }
        }
        return full
    }

    /**
     * Deletes the calendar's copy on this computer, in every group. The phone sends it again when it
     * changes, unless it is no longer brought in on the phone.
     */
    fun remove(calendarId: String) {
        require(calendars().any { it.id == calendarId }) { "Not one of your calendars" }
        delete(calendarId)
    }

    /**
     * CSY-04: the brought-in items the signed-in user may see that overlap [from] to [to]. The owner
     * sees their own items in full; a busy-only entry is shown only where its details are not visible.
     */
    fun items(from: LocalDate, to: LocalDate): List<BroughtInItem> {
        val names = books.core.users().executeAsList().associate { it.id to it.display_name }
        val out = LinkedHashMap<String, BroughtInItem>()
        for (g in books.groups()) {
            val q = books.ledger(g).broughtInCalendarQueries
            val calendars = q.broughtInCalendars().executeAsList().associateBy { it.id }
            for (row in q.broughtInItemsBetween(to.toString(), from.toString()).executeAsList()) {
                val c = calendars[row.calendar_id] ?: continue
                val item = BroughtInItem(
                    row.id, row.calendar_id, g.id, c.owner_user_id, names[c.owner_user_id].orEmpty(), c.source_name, c.account_name, c.colour?.toInt(),
                    CalendarVisibility.valueOf(row.visibility), row.title, row.location, LocalDate.parse(row.start_date), row.start_time?.let(::parseTime),
                    LocalDate.parse(row.end_date), row.end_time?.let(::parseTime),
                )
                val seen = out[row.id]
                if (seen == null || (seen.busyOnly && !item.busyOnly)) out[row.id] = item
            }
        }
        return out.values.sortedWith(compareBy({ it.startDate }, { it.startTime ?: LocalTime(0, 0) }))
    }

    /** The days [item] covers between [from] and [to], for the calendar's day lists (at most two months). */
    fun days(item: BroughtInItem, from: LocalDate, to: LocalDate): List<LocalDate> {
        val first = maxOf(item.startDate, from)
        // A timed item ending at midnight does not cover the next day.
        val end = if (item.endTime == LocalTime(0, 0) && item.endDate > item.startDate) item.endDate.minus(DatePeriod(days = 1)) else item.endDate
        val last = minOf(end, to)
        if (last < first) return emptyList()
        return (0..minOf(first.daysUntil(last), MAX_DAYS)).map { first.plus(DatePeriod(days = it)) }
    }

    // --- Storing ------------------------------------------------------------------------------------

    private class Meta(val id: String, val deviceId: String, val name: String, val account: String?, val colour: Int?, val visibility: CalendarVisibility, val snapshotAt: Long)

    private class Stored(
        val id: String, val eventId: String, val visibility: CalendarVisibility, val title: String, val location: String?,
        val startDate: LocalDate, val startTime: LocalTime?, val endDate: LocalDate, val endTime: LocalTime?,
    ) {
        /** The item seen by no more people than [limit] allows. */
        fun narrowed(limit: CalendarVisibility): Stored =
            if (visibility <= limit) this else Stored(id, eventId, limit, title, location, startDate, startTime, endDate, endTime)
    }

    /**
     * Writes [items] (from [from] on, or all of them when null) into the private group and the group
     * for others, and removes the calendar from any other group it was in.
     */
    private fun place(meta: Meta, items: List<Stored>, from: LocalDate?, private: GroupInfo, others: GroupInfo, now: Long) {
        val apart = others.id != private.id
        val forOthers = if (apart) items.filter { it.visibility != CalendarVisibility.PRIVATE } else emptyList()
        val forOwner = if (apart) items.filter { it.visibility != CalendarVisibility.SHARED } else items
        val pruneBefore = books.today().minus(DatePeriod(years = 1)).toString()
        // Out of every other group that held it, also one the user may no longer add to: a copy left there could still be read.
        for (g in books.groups()) {
            if (g.id == private.id || (apart && g.id == others.id)) continue
            val q = books.ledger(g).broughtInCalendarQueries
            if (q.broughtInCalendarById(meta.id).executeAsOneOrNull()?.owner_user_id == books.userId) q.deleteBroughtInCalendar(meta.id)
        }
        books.ledger(private).broughtInCalendarQueries.transaction {
            val q = books.ledger(private).broughtInCalendarQueries
            q.upsertBroughtInCalendar(meta.id, books.userId, meta.deviceId, meta.name, meta.account, meta.colour?.toLong(), meta.visibility.name, others.id, meta.snapshotAt, now)
            if (from == null) q.deleteBroughtInItems(meta.id) else q.deleteBroughtInItemsFrom(meta.id, from.toString())
            q.pruneBroughtInItems(meta.id, pruneBefore)
            forOwner.forEach { insert(q, meta.id, it, full = true, now) }
        }
        if (!apart) return
        val q = books.ledger(others).broughtInCalendarQueries
        if (forOthers.isEmpty()) {
            // Nothing for others: no trace of the calendar is left where they can read, once its past items are gone.
            if (q.broughtInCalendarById(meta.id).executeAsOneOrNull() == null) return
            q.transaction {
                if (from == null) q.deleteBroughtInItems(meta.id) else q.deleteBroughtInItemsFrom(meta.id, from.toString())
                q.pruneBroughtInItems(meta.id, pruneBefore)
                if (q.broughtInItemsOf(meta.id).executeAsList().isEmpty()) q.deleteBroughtInCalendar(meta.id)
            }
            return
        }
        q.transaction {
            // The calendar's own name is shown to others only when the whole calendar is shared.
            val shared = meta.visibility == CalendarVisibility.SHARED
            q.upsertBroughtInCalendar(
                meta.id, books.userId, meta.deviceId, meta.name.takeIf { shared }, meta.account.takeIf { shared }, meta.colour?.toLong(), meta.visibility.name, null, meta.snapshotAt, now,
            )
            if (from == null) q.deleteBroughtInItems(meta.id) else q.deleteBroughtInItemsFrom(meta.id, from.toString())
            q.pruneBroughtInItems(meta.id, pruneBefore)
            forOthers.forEach { insert(q, meta.id, it, full = it.visibility == CalendarVisibility.SHARED, now) }
        }
    }

    private fun insert(q: ca.schippers.hfm.data.ledger.BroughtInCalendarQueries, calendarId: String, i: Stored, full: Boolean, now: Long) {
        q.insertBroughtInItem(
            i.id, calendarId, i.eventId, i.visibility.name, if (full) i.title else null, if (full) i.location else null,
            i.startDate.toString(), i.startTime?.let(::hhmm), i.endDate.toString(), i.endTime?.let(::hhmm), now,
        )
    }

    /** Deletes the user's own calendar wherever it is kept, also in a group the user may no longer add to. */
    private fun delete(calendarId: String) {
        for (g in books.groups()) {
            val q = books.ledger(g).broughtInCalendarQueries
            val row = q.broughtInCalendarById(calendarId).executeAsOneOrNull() ?: continue
            if (row.owner_user_id == books.userId) q.deleteBroughtInCalendar(calendarId)
        }
    }

    private fun ItemRow.toStored() = Stored(
        id, event_id, CalendarVisibility.valueOf(visibility), title.orEmpty(), location, LocalDate.parse(start_date), start_time?.let(::parseTime),
        LocalDate.parse(end_date), end_time?.let(::parseTime),
    )

    /** Groups the user may add to. */
    private fun writable(): List<GroupInfo> = books.groups().filter { it.level.allows(PermissionLevel.CAPTURE_ONLY) }

    /** The user's own private group, made when they have none (as the Health screen offers). */
    private fun ownPrivateGroup(): GroupInfo {
        books.groups().firstOrNull { it.isPrivate && it.ownerUserId == books.userId && it.level.allows(PermissionLevel.EDIT) }?.let { return it }
        val name = books.core.userById(books.userId).executeAsOne().display_name
        val id = books.session.createGroup(books.text("group.privateName", name), private = true)
        return books.group(id)
    }

    /** The shared group by default. */
    private fun defaultOthersGroup(): GroupInfo? = writable().firstOrNull { !it.isPrivate }

    private fun parseTime(text: String): LocalTime? = runCatching { LocalTime.parse(text) }.getOrNull()

    private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    companion object {
        private const val MAX_TEXT = 300
        private const val MAX_DAYS = 62

        /** The longest event id kept (the phone's are numbers). */
        private const val MAX_ID = 64

        /** Items per calendar kept at most, as the phone sends (CalendarShare.MAX_ITEMS). */
        private const val MAX_ITEMS = CalendarShare.MAX_ITEMS

        /** The same in every group that keeps the calendar: the phone and its own id of the calendar. */
        fun calendarId(deviceId: String, calendarKey: String): String = hash("cal|$deviceId|$calendarKey")

        fun itemId(calendarId: String, eventId: String, startDate: String, startTime: String?): String = hash("item|$calendarId|$eventId|$startDate|${startTime.orEmpty()}")

        private fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }
}
