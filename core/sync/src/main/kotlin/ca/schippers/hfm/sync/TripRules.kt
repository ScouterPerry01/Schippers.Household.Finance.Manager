package ca.schippers.hfm.sync

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TRP-02, TRP-03: what the phone and the desktop both decide about a trip, so they agree: which
 * saved place a location fix is, and the purpose suggested from the places and the vehicle's use.
 * Everything is worked out on the device; no map service is asked.
 */
object TripRules {

    /** The place categories, in the order they are offered. */
    val CATEGORIES = listOf("HOME", "WORK", "CLIENT", "STORE", "FUEL", "CHARGING", "GARAGE", "MEDICAL", "OTHER")

    /** TRP-12, TRP-15: what a point on the way is: a stop (a place visited) or a rest break. */
    const val STOP = "STOP"
    const val BREAK = "BREAK"

    /** The purposes a trip can have, as the desktop's trip log names them. */
    val PURPOSES = listOf("BUSINESS", "EMPLOYMENT", "MEDICAL", "PERSONAL")

    /** NONE, TOWING (a trailer) or HEAVY (a heavy load). */
    val LOADS = listOf("NONE", "TOWING", "HEAVY")

    /** A new place's matching radius, in metres. */
    const val DEFAULT_RADIUS_M = 150

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** The distance in metres between two points, along the earth's surface (haversine). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * The saved place a fix at [latitude], [longitude] is: the nearest place whose radius reaches the
     * fix, or null when none does (the user then names a new place or keeps the coordinates).
     */
    fun nearest(latitude: Double, longitude: Double, places: List<RefPlace>): RefPlace? = places
        .mapNotNull { p ->
            val lat = p.latitude ?: return@mapNotNull null
            val lon = p.longitude ?: return@mapNotNull null
            val d = distanceM(latitude, longitude, lat, lon)
            if (d <= p.radiusM.coerceAtLeast(1)) p to d else null
        }
        .minByOrNull { it.second }?.first

    /** "45.42153, -75.69719": a fix with no saved place, as shown and kept on the trip. */
    fun coordinates(latitude: Double, longitude: Double): String = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

    /**
     * TRP-03: the purpose suggested for a trip from [startCategory] to [endCategory] in a vehicle of
     * [vehicleUse]: to or from a client (home included) is business; to a medical place, or home from one, is
     * medical; any other trip in a commercial vehicle is business. Everything else, the commute
     * between home and the usual workplace included (the CRA counts it as personal), is personal.
     * The user confirms or changes it.
     */
    fun suggestPurpose(startCategory: String?, endCategory: String?, vehicleUse: String?): String = when {
        endCategory == "CLIENT" || startCategory == "CLIENT" -> "BUSINESS"
        endCategory == "MEDICAL" -> "MEDICAL"
        startCategory == "MEDICAL" && endCategory == "HOME" -> "MEDICAL"
        vehicleUse == "COMMERCIAL" -> "BUSINESS"
        else -> "PERSONAL"
    }

    /** TRP-15: the break under way: the last point, a break not yet ended; null when driving. */
    fun openBreak(stops: List<PhoneTripStop>): PhoneTripStop? = stops.lastOrNull()?.takeIf { it.kind == BREAK && it.endAt == null }

    /**
     * TRP-15: the minutes spent on breaks, each from its start to its end (one not ended counts
     * until [until]); times are "yyyy-MM-ddTHH:mm". Left out of the driving time.
     */
    fun breakMinutes(stops: List<PhoneTripStop>, until: String): Long = stops.filter { it.kind == BREAK }.sumOf { b ->
        val from = minuteOf(b.at) ?: return@sumOf 0L
        val to = minuteOf(b.endAt ?: until) ?: return@sumOf 0L
        (to - from).coerceAtLeast(0)
    }

    /** TRP-15: the time driven, in minutes, from [start] to [end] less the breaks; null when a time cannot be read. */
    fun drivingMinutes(start: String, end: String, stops: List<PhoneTripStop>): Long? {
        val from = minuteOf(start) ?: return null
        val to = minuteOf(end) ?: return null
        return (to - from - breakMinutes(stops, end)).coerceAtLeast(0)
    }

    /** TRP-12: the reading the next leg starts from: the last stop's, else the trip's start. */
    fun lastReading(startOdometer: Int, stops: List<PhoneTripStop>): Int =
        stops.lastOrNull { it.kind == STOP && it.odometer != null }?.odometer ?: startOdometer

    /** TRP-12: a reading at a stop or at arrival follows [last]: higher, and less than 10,000 km on. */
    fun follows(last: Int, reading: Int?): Boolean = reading != null && reading > last && reading - last < 10_000

    /** TRP-14: where "back to the previous stop" goes: the last stop made, else null (the start is offered as itself). */
    fun previousStop(stops: List<PhoneTripStop>): PhoneTripStop? = stops.lastOrNull { it.kind == STOP }

    /** Minutes since the epoch day of a "yyyy-MM-ddTHH:mm" time, for differences; null when it cannot be read. */
    private fun minuteOf(text: String): Long? = runCatching {
        java.time.LocalDateTime.parse(text.take(16)).let { it.toLocalDate().toEpochDay() * 1_440 + it.hour * 60 + it.minute }
    }.getOrNull()
}
