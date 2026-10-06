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
    val CATEGORIES = listOf("HOME", "WORK", "CLIENT", "STORE", "FUEL", "GARAGE", "MEDICAL", "OTHER")

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
}
