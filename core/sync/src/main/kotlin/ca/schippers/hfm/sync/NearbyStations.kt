package ca.schippers.hfm.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.util.Locale

/**
 * TRP-18: a fuel station or EV charger near the phone, from OpenStreetMap. [kind] is FUEL or
 * CHARGING; [distanceM] is from the phone's own position (worked out on the phone, never sent).
 */
data class NearbyStation(
    val id: String,
    val kind: String,
    val name: String?,
    val brand: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceM: Int,
) {
    /** What to call it: its name, else its brand or operator; null when OpenStreetMap has neither. */
    val label: String? get() = name ?: brand
}

/**
 * TRP-18: nearby fuel stations and EV chargers from OpenStreetMap's Overpass API, asked only when the
 * user taps Stations nearby with the option turned on. Only a rough position is sent (rounded to two
 * decimals, about a kilometre); the distances are worked out on the phone from its own fix. The
 * search covers [RADII_M] in turn, widening when fewer than [ENOUGH] are found. The HTTP request is
 * the caller's (HTTPS only, with time and size limits); this object builds it and reads the answer.
 */
object NearbyStations {

    /** Overpass's main public instance; HTTPS only. */
    const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /** Shown under the list, as OpenStreetMap's licence asks. */
    const val ATTRIBUTION = "© OpenStreetMap contributors"

    /** The search radii, in metres: 5 km, then 15 km when the first finds too few. */
    val RADII_M = listOf(5_000, 15_000)

    /** Fewer stations than this within the first radius widens the search. */
    const val ENOUGH = 3

    /** The most stations shown. */
    const val MAX_RESULTS = 40

    /** The largest answer read, in bytes; a longer one is cut off and refused. */
    const val MAX_BYTES = 1_000_000

    /** Seconds Overpass may spend on the search; the phone's own timeouts are a little longer. */
    const val SERVER_TIMEOUT_S = 20

    /** The longest an answer may take to arrive, however slowly it trickles in, in milliseconds. */
    const val MAX_ANSWER_MS = 45_000L

    /**
     * Reads an answer from [input]: at most [MAX_BYTES], within [MAX_ANSWER_MS] in all ([nanos] is
     * the clock), as UTF-8; throws [IOException] beyond either. A read timeout alone would let a server
     * keep the phone waiting by sending a byte now and then.
     */
    fun readAnswer(input: InputStream, nanos: () -> Long = System::nanoTime): String {
        val deadline = nanos() + MAX_ANSWER_MS * 1_000_000
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) throw IOException("Answer too large")
            if (nanos() > deadline) throw IOException("Answer too slow")
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** A position rounded to two decimals (about 1.1 km north to south), as sent. */
    fun rough(value: Double): Double = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * The Overpass query for fuel stations and chargers within [radiusM] of the rough position:
     * nodes and areas (a station drawn as a building gives its centre), with their tags.
     */
    fun query(latitude: Double, longitude: Double, radiusM: Int): String {
        // The rounding moves the centre by up to about 800 m, so the radius grows to cover it.
        val around = String.format(Locale.ROOT, "around:%d,%.2f,%.2f", radiusM + 800, rough(latitude), rough(longitude))
        return "[out:json][timeout:$SERVER_TIMEOUT_S];" +
            "(nwr[\"amenity\"=\"fuel\"]($around);nwr[\"amenity\"=\"charging_station\"]($around););" +
            "out center tags ${MAX_RESULTS * 3};"
    }

    /** The POST body for [query] (application/x-www-form-urlencoded). */
    fun body(query: String): String = "data=" + URLEncoder.encode(query, "UTF-8")

    /**
     * The stations in an Overpass JSON [answer], closest first to the phone's own position
     * ([latitude], [longitude]), those beyond [radiusM] left out. An answer that is not Overpass JSON
     * gives an empty list.
     */
    fun parse(answer: String, latitude: Double, longitude: Double, radiusM: Int = Int.MAX_VALUE): List<NearbyStation> {
        val root = runCatching { SyncJson.parseToJsonElement(answer).jsonObject }.getOrNull() ?: return emptyList()
        val elements = runCatching { root["elements"]?.jsonArray }.getOrNull() ?: return emptyList()
        return elements.mapNotNull { e -> runCatching { station(e.jsonObject, latitude, longitude) }.getOrNull() }
            .filter { it.distanceM <= radiusM }
            .distinctBy { it.id }
            .sortedWith(compareBy<NearbyStation> { it.distanceM }.thenBy { it.label.orEmpty() })
            .take(MAX_RESULTS)
    }

    /**
     * Asks for stations around the phone's position, at 5 km then 15 km when too few, with [post]
     * sending a body to [ENDPOINT] and giving the answer (or throwing when it fails).
     */
    fun search(latitude: Double, longitude: Double, post: (body: String) -> String): List<NearbyStation> {
        var found = emptyList<NearbyStation>()
        for (radius in RADII_M) {
            found = parse(post(body(query(latitude, longitude, radius))), latitude, longitude, radius)
            if (found.size >= ENOUGH) break
        }
        return found
    }

    private fun station(e: JsonObject, fromLat: Double, fromLon: Double): NearbyStation? {
        val type = e["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val id = e["id"]?.jsonPrimitive?.longOrNull ?: return null
        val center = e["center"]?.jsonObject
        val lat = (e["lat"] ?: center?.get("lat"))?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = (e["lon"] ?: center?.get("lon"))?.jsonPrimitive?.doubleOrNull ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val tags = e["tags"]?.jsonObject ?: return null
        fun tag(key: String): String? = tags[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
        val kind = when (tag("amenity")) {
            "fuel" -> "FUEL"
            "charging_station" -> "CHARGING"
            else -> return null
        }
        val street = listOfNotNull(tag("addr:housenumber"), tag("addr:street")).joinToString(" ").ifEmpty { null }
        val address = listOfNotNull(street, tag("addr:city")).joinToString(", ").ifEmpty { null }
        return NearbyStation(
            "$type/$id", kind, tag("name"), tag("brand") ?: tag("operator"), address, lat, lon,
            TripRules.distanceM(fromLat, fromLon, lat, lon).toInt(),
        )
    }

    /** "850 m" below a kilometre, else "2.4 km" ("2,4 km" in French). */
    fun distanceText(metres: Int, locale: Locale): String =
        if (metres < 1_000) "${(metres / 10) * 10} m" else String.format(locale, "%.1f km", metres / 1_000.0)

    private const val MAX_TEXT = 120
}
