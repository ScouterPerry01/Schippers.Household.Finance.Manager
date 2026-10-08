package ca.schippers.hfm.sync

import java.net.URLDecoder
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRP-18: the Overpass question sent for nearby stations, and its answer read closest first. */
class NearbyStationsTest {

    private val sample = javaClass.getResource("/overpass-sample.json")!!.readText()

    // Ottawa City Hall.
    private val lat = 45.42153
    private val lon = -75.69719

    @Test
    fun `stations are read closest first, with their kind, name, brand and address`() {
        val found = NearbyStations.parse(sample, lat, lon)
        assertEquals(listOf("node/3003", "node/1001", "way/2002", "node/4004"), found.map { it.id }, "parking and a node with no position left out, a repeat once")
        val charger = found[0]
        assertEquals("CHARGING", charger.kind)
        assertEquals("City Hall chargers", charger.label)
        assertEquals("FLO", charger.brand, "the operator when there is no brand")
        assertTrue(charger.distanceM < 50)
        val petro = found[1]
        assertEquals("FUEL", petro.kind)
        assertEquals("Petro-Canada", petro.brand)
        assertEquals("350 Rideau Street, Ottawa", petro.address)
        val shell = found[2]
        assertNull(shell.name)
        assertEquals("Shell Canada", shell.label, "named by its operator")
        assertNull(shell.address)
        assertEquals(45.43, shell.latitude, 0.0001)
    }

    @Test
    fun `stations beyond the radius are left out`() {
        val near = NearbyStations.parse(sample, lat, lon, radiusM = 5_000)
        assertFalse(near.any { it.id == "node/4004" }, "about 10 km away")
        assertTrue(NearbyStations.parse(sample, lat, lon, radiusM = 15_000).any { it.id == "node/4004" })
    }

    @Test
    fun `an answer that is not Overpass JSON gives nothing`() {
        assertTrue(NearbyStations.parse("", lat, lon).isEmpty())
        assertTrue(NearbyStations.parse("<html>Too many requests</html>", lat, lon).isEmpty())
        assertTrue(NearbyStations.parse("""{"remark":"runtime error"}""", lat, lon).isEmpty())
        assertTrue(NearbyStations.parse("""{"elements":[{"type":"node","id":"x"}]}""", lat, lon).isEmpty())
    }

    @Test
    fun `only a rough position is sent`() {
        val query = NearbyStations.query(lat, lon, 5_000)
        assertTrue("around:5800,45.42,-75.70" in query, query)
        assertFalse("45.4215" in query, "the precise position stays on the phone")
        assertTrue("\"amenity\"=\"fuel\"" in query && "\"amenity\"=\"charging_station\"" in query)
        assertTrue(query.startsWith("[out:json]"))
        val body = NearbyStations.body(query)
        assertTrue(body.startsWith("data="))
        assertEquals(query, URLDecoder.decode(body.removePrefix("data="), "UTF-8"))
        assertTrue(NearbyStations.ENDPOINT.startsWith("https://"))
    }

    @Test
    fun `the search widens when too few are near`() {
        val asked = ArrayList<String>()
        val few = """{"elements":[{"type":"node","id":1,"lat":45.4216,"lon":-75.6975,"tags":{"amenity":"fuel","name":"Only one"}}]}"""
        val found = NearbyStations.search(lat, lon) { body ->
            asked += URLDecoder.decode(body.removePrefix("data="), "UTF-8")
            if (asked.size == 1) few else sample
        }
        assertEquals(2, asked.size)
        assertTrue("around:15800" in asked[1])
        assertEquals(4, found.size)
        // Enough at 5 km: asked once.
        var times = 0
        NearbyStations.search(lat, lon) { times++; sample }
        assertEquals(1, times)
    }

    @Test
    fun `distances are shown in metres or kilometres`() {
        assertEquals("850 m", NearbyStations.distanceText(853, Locale.CANADA))
        assertEquals("2.4 km", NearbyStations.distanceText(2_412, Locale.CANADA))
        assertEquals("2,4 km", NearbyStations.distanceText(2_412, Locale.CANADA_FRENCH))
    }

    @Test
    fun `a hostile answer gives nothing harmful`() {
        // Nested a hundred thousand deep.
        assertTrue(NearbyStations.parse("[".repeat(100_000) + "]".repeat(100_000), lat, lon).isEmpty())
        assertTrue(NearbyStations.parse("""{"elements":""" + "[".repeat(50_000) + "]".repeat(50_000) + "}", lat, lon).isEmpty())
        // Numbers out of range or absurd, a name a megabyte long, an id that is not a number.
        val long = "x".repeat(1_000_000)
        val answer = """{"elements":[
            {"type":"node","id":1,"lat":1e999,"lon":-75.6975,"tags":{"amenity":"fuel"}},
            {"type":"node","id":2,"lat":45.4216,"lon":-1e400,"tags":{"amenity":"fuel"}},
            {"type":"node","id":99999999999999999999999,"lat":45.4216,"lon":-75.6975,"tags":{"amenity":"fuel"}},
            {"type":"node","id":3,"lat":"45.4216","lon":-75.6975,"tags":{"amenity":"fuel"}},
            {"type":"node","id":4,"lat":45.4216,"lon":-75.6975,"tags":{"amenity":"fuel","name":"$long","addr:street":"$long"}},
            {"type":"node","id":5,"lat":45.4216,"lon":-75.6975,"tags":"fuel"},
            {"type":"node","id":6,"lat":45.4216,"lon":-75.6975,"tags":{"amenity":["fuel"]}}
        ]}"""
        val found = NearbyStations.parse(answer, lat, lon)
        assertEquals(listOf("node/3", "node/4"), found.map { it.id }.sorted(), "positions as text are read; the rest is left out")
        assertTrue(found.all { (it.name?.length ?: 0) <= 120 && (it.address?.length ?: 0) <= 250 }, "texts are cut")
        // Thousands of stations: the closest forty.
        val many = (1..5_000).joinToString(",") { """{"type":"node","id":$it,"lat":${45.4 + it / 1e6},"lon":-75.7,"tags":{"amenity":"fuel"}}""" }
        assertEquals(NearbyStations.MAX_RESULTS, NearbyStations.parse("""{"elements":[$many]}""", lat, lon).size)
    }

    @Test
    fun `an answer is read only up to its size and time limits`() {
        val big = java.io.ByteArrayInputStream(ByteArray(NearbyStations.MAX_BYTES + 20_000) { 'a'.code.toByte() })
        assertFailsWith<java.io.IOException> { NearbyStations.readAnswer(big) }
        // A server sending a byte at a time for ever: stopped once the time is up.
        var clock = 0L
        val trickle = object : java.io.InputStream() {
            override fun read(): Int = 'a'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                clock += 1_000_000_000L
                b[off] = '{'.code.toByte()
                return 1
            }
        }
        assertFailsWith<java.io.IOException> { NearbyStations.readAnswer(trickle) { clock } }
        assertTrue(clock <= (NearbyStations.MAX_ANSWER_MS + 2_000) * 1_000_000)
        assertEquals("{}", NearbyStations.readAnswer("{}".byteInputStream()))
    }
}
