package ca.schippers.hfm.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SEC-09: the phone app's lock time. */
class PhoneLockTest {

    @Test
    fun `the app locks after the time chosen`() {
        assertFalse(PhoneLock.mustLock(60_000, 1, ownScreen = false), "a minute exactly is not more than a minute")
        assertTrue(PhoneLock.mustLock(60_001, 1, ownScreen = false))
        assertFalse(PhoneLock.mustLock(4 * 60_000, 5, ownScreen = false))
        assertTrue(PhoneLock.mustLock(5 * 60_000 + 1, 5, ownScreen = false))
        assertFalse(PhoneLock.mustLock(14 * 60_000, 15, ownScreen = false))
        assertTrue(PhoneLock.mustLock(16 * 60_000, 15, ownScreen = false))
    }

    @Test
    fun `at once locks on every return, except from a screen the app opened`() {
        assertTrue(PhoneLock.mustLock(0, 0, ownScreen = false))
        assertTrue(PhoneLock.mustLock(500, 0, ownScreen = false))
        assertFalse(PhoneLock.mustLock(30_000, 0, ownScreen = true), "back from the document scanner")
        assertTrue(PhoneLock.mustLock(61_000, 0, ownScreen = true))
        assertFalse(PhoneLock.mustLock(4 * 60_000, 5, ownScreen = true), "a longer time chosen still applies")
    }

    @Test
    fun `an unknown value falls back to one minute`() {
        assertEquals(1, PhoneLock.minutes(7))
        assertEquals(0, PhoneLock.minutes(0))
        assertEquals(listOf(0, 1, 5, 15), PhoneLock.CHOICES)
    }

    @Test
    fun `back from the app's own screen, at once still means at once next time`() {
        val w = LockWatch()
        // The scanner opens, the app is left for it at once, and comes back 30 seconds later: no PIN.
        w.opened(1_000)
        w.stopped(1_400)
        assertFalse(w.mustLock(31_400, 0))
        w.resumed()
        // The user then leaves the app themselves: the PIN, at once.
        w.stopped(40_000)
        assertTrue(w.mustLock(41_000, 0))
    }

    @Test
    fun `a permission request or a screen that never opened leaves no mark`() {
        val w = LockWatch()
        // A permission request is a dialog: the app pauses and resumes without being left.
        w.opened(1_000)
        w.resumed()
        w.stopped(2_000)
        assertTrue(w.mustLock(3_000, 0), "leaving afterwards locks at once")
        // A screen that failed to open: the user leaves the app a minute later.
        w.opened(10_000)
        w.stopped(70_000)
        assertTrue(w.mustLock(71_000, 0))
        // Even back from the app's own screen, never more than a minute (or the time chosen) without the PIN.
        w.opened(100_000)
        w.stopped(100_500)
        assertTrue(w.mustLock(100_500 + 60_001, 0))
        assertFalse(LockWatch().mustLock(5_000, 0), "never left: nothing to ask")
    }
}
