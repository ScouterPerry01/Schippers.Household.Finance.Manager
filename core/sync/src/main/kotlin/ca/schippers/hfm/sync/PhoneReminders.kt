package ca.schippers.hfm.sync

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * CAL-03 on the phone: when each event's reminders fall, which to schedule and which are due now.
 * Plain Kotlin, so it is tested here and used as is by the Android app, which sets an alarm for
 * each [upcoming] reminder and shows what [due] returns when the alarm (or any later check) runs.
 */
object EventReminders {

    /** All-day events are reminded counting from 08:00, as on the computer. */
    val ALL_DAY_TIME: LocalTime = LocalTime.of(8, 0)

    /** A reminder still shown this long after the event's start, for an alarm the phone delayed. */
    private const val GRACE_MINUTES = 15L

    data class Planned(val event: RefEvent, val start: LocalDateTime, val minutesBefore: Int) {
        val at: LocalDateTime get() = start.minusMinutes(minutesBefore.toLong())
        val key: String get() = "e|${event.id}|$minutesBefore"
    }

    /** A reminder to show now, and the keys to remember: earlier ones of the same event, missed, are not shown apart. */
    data class Due(val reminder: Planned, val keys: List<String>)

    fun start(event: RefEvent): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(event.date), event.time?.let(LocalTime::parse) ?: ALL_DAY_TIME)
    }.getOrNull()

    /** Every reminder of every event, in time order. */
    fun all(events: List<RefEvent>): List<Planned> = events.flatMap { e ->
        val start = start(e) ?: return@flatMap emptyList()
        e.reminderMinutes.filter { it >= 0 }.distinct().map { Planned(e, start, it) }
    }.sortedBy { it.at }

    /** The reminders still to come, for the phone's alarms. */
    fun upcoming(events: List<RefEvent>, now: LocalDateTime): List<Planned> = all(events).filter { it.at.isAfter(now) }

    /**
     * The reminders whose time has come for events not yet started (or just started), not shown
     * before: one per event, the latest; a phone that was off or not yet told shows it late.
     */
    fun due(events: List<RefEvent>, now: LocalDateTime, shown: Set<String>): List<Due> =
        all(events).filter { !it.at.isAfter(now) && !now.isAfter(it.start.plusMinutes(GRACE_MINUTES)) }
            .groupBy { it.event.id }
            .mapNotNull { (_, passed) ->
                val latest = passed.minBy { it.minutesBefore }
                if (latest.key in shown) null else Due(latest, passed.map { it.key })
            }
            .sortedBy { it.reminder.start }

    /** Minutes from [now] to the event's start, rounded up (an alarm rings a moment after its minute), never below zero. */
    fun minutesUntil(reminder: Planned, now: LocalDateTime): Long = (ChronoUnit.SECONDS.between(now, reminder.start).coerceAtLeast(0) + 59) / 60
}

/** HLT-03 on the phone: a refill reminder once, from its reminder days before it runs out (or when overdue). */
object RefillReminders {

    data class Alert(val refill: RefRefill, val daysLeft: Int, val key: String)

    fun due(refills: List<RefRefill>, today: LocalDate, shown: Set<String>): List<Alert> = refills.mapNotNull { r ->
        val date = runCatching { LocalDate.parse(r.dueDate) }.getOrNull() ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        val key = "r|${r.id}|${r.dueDate}"
        if (days > r.reminderDays || key in shown) null else Alert(r, days, key)
    }.sortedBy { it.refill.dueDate }
}
