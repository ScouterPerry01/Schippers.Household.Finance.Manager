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
