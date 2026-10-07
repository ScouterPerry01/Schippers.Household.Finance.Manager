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
}
