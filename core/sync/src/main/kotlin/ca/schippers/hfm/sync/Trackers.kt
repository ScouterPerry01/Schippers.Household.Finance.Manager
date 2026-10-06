package ca.schippers.hfm.sync

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * UTL-01, UTL-02, HRS-01, CHO-01, VOL-01: one thing logged on the phone for the computer: a meter
 * reading, a tank level, hours worked, a chore done or volunteer hours (exactly one is set). [id] is
 * made on the phone, so the computer stores it at most once. They travel in [SyncRequest.trackers],
 * apart from captures, so a computer that does not know them still reads the request (and leaves
 * them on the phone until it is updated).
 */
@Serializable
data class PhoneTracker(
    val id: String,
    val createdAtMillis: Long,
    val meter: PhoneMeterReading? = null,
    val tank: PhoneTankReading? = null,
    val hours: PhoneHours? = null,
    val chore: PhoneChoreTick? = null,
    val volunteer: PhoneVolunteer? = null,
)

/** A utility meter's reading: its total and, with time of use, each register. Decimal strings. */
@Serializable
data class PhoneMeterReading(
    val meterId: String,
    val date: String,
    val value: String,
    val onPeak: String? = null,
    val midPeak: String? = null,
    val offPeak: String? = null,
    val note: String? = null,
)

/** A tank's gauge, in percent or in litres (one of them). */
@Serializable
data class PhoneTankReading(val tankId: String, val date: String, val percent: String? = null, val litres: String? = null, val note: String? = null)

/** Hours worked for a client: [minutes] on [date], from [startTime] (HH:mm) when timed. */
@Serializable
data class PhoneHours(
    val clientId: String,
    val date: String,
    val minutes: Int,
    val taskId: String? = null,
    val startTime: String? = null,
    val description: String? = null,
)

/** A chore done on [date]. */
@Serializable
data class PhoneChoreTick(val choreId: String, val date: String)

/** Volunteer time of a person for an organization; [kind] as on the computer (FIREFIGHTER, SEARCH_RESCUE, SCHOOL, OTHER). */
@Serializable
data class PhoneVolunteer(
    val memberId: String,
    val organization: String,
    val date: String,
    val minutes: Int,
    val kind: String = "OTHER",
    val contactId: String? = null,
    val activity: String? = null,
)

/** What the phone's log forms pick from (part of [ReferenceData] from [ReferenceData.FORMAT] 4). */
@Serializable
data class RefTrackers(
    val meters: List<RefMeter> = emptyList(),
    val tanks: List<RefTank> = emptyList(),
    val clients: List<RefWorkClient> = emptyList(),
    val chores: List<RefChore> = emptyList(),
    val organizations: List<RefVolunteerOrg> = emptyList(),
)

/** A utility meter: [kind] ELECTRICITY, GAS or WATER; [place] the home it is at; its last reading. */
@Serializable
data class RefMeter(
    val id: String,
    val name: String,
    val kind: String,
    val place: String? = null,
    val timeOfUse: Boolean = false,
    val lastValue: String? = null,
    val lastDate: String? = null,
)

/** A fuel tank: [fuel] PROPANE or HEATING_OIL, its capacity in litres and its last level in percent. */
@Serializable
data class RefTank(val id: String, val name: String, val fuel: String, val capacityLitres: String, val place: String? = null, val lastPercent: String? = null)

@Serializable
data class RefWorkClient(val id: String, val name: String, val tasks: List<RefWorkTask> = emptyList())

@Serializable
data class RefWorkTask(val id: String, val name: String)

/** A child's chore, worth [amount] (decimal, in [currency]) or [points]; [doneDates] its recent ticks. */
@Serializable
data class RefChore(
    val id: String,
    val name: String,
    val memberId: String,
    val memberName: String,
    val amount: String? = null,
    val currency: String? = null,
    val points: Int? = null,
    val doneDates: List<String> = emptyList(),
)

/** An organization volunteer hours were given to before, to pick again. */
@Serializable
data class RefVolunteerOrg(val name: String, val kind: String = "OTHER", val contactId: String? = null, val memberId: String? = null)

/**
 * HRS-01: a running timer for hours worked. Kept on the phone (encrypted, like the queue), so it
 * survives the app being closed or the phone restarting; stopping it gives the hours to send.
 */
@Serializable
data class HoursTimer(val clientId: String, val startedAtMillis: Long, val taskId: String? = null, val description: String? = null) {

    /** The minutes from the start to [nowMillis], rounded to the nearest minute, at least one. */
    fun minutes(nowMillis: Long): Int = ((nowMillis - startedAtMillis + 30_000) / 60_000).toInt().coerceAtLeast(1)

    /** The hours worked, dated and timed where the timer started. */
    fun stop(nowMillis: Long, zone: ZoneId): PhoneHours {
        val start = Instant.ofEpochMilli(startedAtMillis).atZone(zone)
        return PhoneHours(clientId, start.toLocalDate().toString(), minutes(nowMillis), taskId, "%02d:%02d".format(start.hour, start.minute), description)
    }

    companion object {
        /** "1:05" for 65 minutes. */
        fun format(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

        /** Minutes from hours typed as "1:30", "1.5", "1,5" or "1.5 h"; null when unreadable or not positive. */
        fun parse(text: String): Int? {
            val t = text.trim().lowercase().removeSuffix("h").trim()
            val minutes = when {
                ':' in t -> t.split(':').let { p -> if (p.size != 2) null else p[0].toIntOrNull()?.let { h -> p[1].toIntOrNull()?.takeIf { it in 0..59 }?.let { m -> h * 60 + m } } }
                else -> t.replace(',', '.').toBigDecimalOrNull()?.multiply(java.math.BigDecimal(60))?.setScale(0, java.math.RoundingMode.HALF_UP)?.toInt()
            }
            return minutes?.takeIf { it > 0 }
        }
    }
}
