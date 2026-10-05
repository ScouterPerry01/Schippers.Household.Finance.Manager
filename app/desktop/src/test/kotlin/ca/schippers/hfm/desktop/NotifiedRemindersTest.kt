package ca.schippers.hfm.desktop

import kotlinx.datetime.LocalDate
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BILL-04: a reminder is notified once per household and day, also when the app is opened again. */
class NotifiedRemindersTest {

    private val prefs = Preferences.userRoot().node("ca/schippers/hfm-test-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() = prefs.removeNode()

    private val today = LocalDate(2026, 10, 5)

    @Test
    fun `once per household and day, remembered on this computer`() {
        val keys = listOf("bill:hydro:2026-10-12:7", "refill:ventolin:2026-10-06")
        assertEquals(keys.toSet(), NotifiedReminders(prefs).fresh("house-a", today, keys))
        assertTrue(NotifiedReminders(prefs).fresh("house-a", today, keys).isEmpty(), "the app was closed and opened again the same day")
        assertEquals(setOf("bill:rent:2026-10-05:0"), NotifiedReminders(prefs).fresh("house-a", today, keys + "bill:rent:2026-10-05:0"))
        assertEquals(keys.toSet(), NotifiedReminders(prefs).fresh("house-b", today, keys), "another household has its own")
        assertEquals(setOf("refill:ventolin:2026-10-06"), NotifiedReminders(prefs).fresh("house-a", LocalDate(2026, 10, 6), keys.drop(1)), "the next day, again")
    }
}
