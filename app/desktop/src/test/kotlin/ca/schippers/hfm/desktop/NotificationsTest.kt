package ca.schippers.hfm.desktop

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Computer notifications: with details off, no name of a medication, person, appointment or bill. */
class NotificationsTest {

    private val prefs = Preferences.userRoot().node("ca/schippers/hfm-test/notifications-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() = prefs.removeNode()

    private val lines = listOf(
        BooksModel.ReminderLine("event:1", "Dr Gagnon, Sam: appointment at 14:00", Section.CALENDAR),
        BooksModel.ReminderLine("bill:1", "Hydro due in 3 days", Section.BILLS),
        BooksModel.ReminderLine("refill:1", "Ventolin (Sam): refill due", Section.HEALTH),
        BooksModel.ReminderLine("bill:2", "Rent due today", Section.BILLS),
        BooksModel.ReminderLine("refill:2", "Insulin (Marie): refill due", Section.HEALTH),
    )
    private val names = mapOf(Section.CALENDAR to "Calendar", Section.BILLS to "Bills", Section.HEALTH to "Health")

    @Test
    fun `with details, up to four reminders as the banner shows them`() {
        val body = notificationBody(lines, details = true) { names.getValue(it) }
        assertEquals(lines.take(4).map { it.text }, body.lines())
    }

    @Test
    fun `without details, only the kind and the count`() {
        val body = notificationBody(lines, details = false) { names.getValue(it) }
        assertEquals(listOf("Calendar (1)", "Bills (2)", "Health (2)"), body.lines())
        for (secret in listOf("Gagnon", "Sam", "Ventolin", "Insulin", "Marie", "Hydro", "Rent", "14:00")) assertFalse(secret in body, secret)
    }

    @Test
    fun `the choice is kept for this computer, details shown until turned off`() {
        assertTrue(AppState(prefs = prefs).notificationDetails)
        AppState(prefs = prefs).chooseNotificationDetails(false)
        assertFalse(AppState(prefs = prefs).notificationDetails)
    }
}
