package ca.schippers.hfm.sync

import java.net.URLDecoder
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
