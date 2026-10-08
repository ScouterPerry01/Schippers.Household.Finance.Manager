package ca.schippers.hfm.sync

/**
 * SEC-03, SEC-09: when the phone app asks for its PIN again. The user chooses in Settings how long the
 * app may be away first: at once, 1 minute (the default), 5 or 15 minutes. "At once" means leaving the
 * app; a screen the app opened itself (the document scanner, a file or folder picker) is part of using
 * it, so coming back from one asks for the PIN only after a minute, or the longer time chosen.
 */
object PhoneLock {
    const val DEFAULT_MINUTES = 1
    val CHOICES = listOf(0, 1, 5, 15)

    private const val MINUTE = 60_000L

    /** The minutes chosen, or the default for a value that is not one of the choices. */
    fun minutes(chosen: Int): Int = chosen.takeIf { it in CHOICES } ?: DEFAULT_MINUTES

    /** Whether to lock after [awayMillis] away; [ownScreen] when the app was away in a screen it opened itself. */
    fun mustLock(awayMillis: Long, chosenMinutes: Int, ownScreen: Boolean): Boolean {
        val m = minutes(chosenMinutes)
        val limit = (if (ownScreen) maxOf(m, DEFAULT_MINUTES) else m) * MINUTE
        return if (limit == 0L) true else awayMillis > limit
    }
}

/**
 * SEC-09: the app's comings and goings, for [PhoneLock], in the phone's elapsed milliseconds. A screen
 * the app opens itself counts as part of using it only when the app is left for it right away: a
 * permission request (a dialog that does not take the app away) or a screen that failed to open leaves
 * no mark for the next time the user leaves the app.
 */
class LockWatch {
    private var stoppedAt = 0L
    private var openedAt = 0L
    private var inOwnScreen = false

    /** The app opens a screen of its own (the document scanner, a picker, the camera, a permission request). */
    fun opened(now: Long) {
        openedAt = now
    }

    /** The app is in front again without having been left (the screen was a dialog, or never opened). */
    fun resumed() {
        openedAt = 0L
    }

    /** The app is no longer seen. */
    fun stopped(now: Long) {
        stoppedAt = now
        inOwnScreen = openedAt != 0L && now - openedAt in 0..OWN_SCREEN_GRACE_MS
        openedAt = 0L
    }

    /** Whether to ask for the PIN on coming back at [now], with [chosenMinutes] chosen in Settings. */
    fun mustLock(now: Long, chosenMinutes: Int): Boolean {
        val lock = stoppedAt != 0L && PhoneLock.mustLock(now - stoppedAt, chosenMinutes, inOwnScreen)
        inOwnScreen = false
        return lock
    }

    companion object {
        /** How soon after opening its own screen the app must be left for the time away to count as spent there. */
        const val OWN_SCREEN_GRACE_MS = 5_000L
    }
}
