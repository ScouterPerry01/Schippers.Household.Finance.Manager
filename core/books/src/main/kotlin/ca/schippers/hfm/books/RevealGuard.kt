package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException

/**
 * Thrown when a full number is asked for too soon after several wrong passwords; the password is
 * not even checked. [waitSeconds] is how long until the next try.
 */
class TooManyAttemptsException(val waitSeconds: Long) : Exception("Too many wrong passwords; try again in $waitSeconds s")

/**
 * SEC-04: the password asked again before showing a full account or client number. After
 * [FREE_TRIES] wrong passwords in a row, each further try must wait, 30 seconds at first and twice
 * as long after each new mistake, up to 5 minutes; a right password starts over. Every wrong
 * password is written to the activity log as REVEAL_FAILED with the record's id, never the number
 * or the password typed. The count lives with the signed-in session: signing in again, which asks
 * for the password through the same slow Argon2id check, starts it over.
 */
internal class RevealGuard(private val books: Books) {

    private var failures = 0
    private var blockedUntil = 0L

    /** Checks [password] for revealing [entity] [entityId]; throws when wrong or while waiting. */
    @Synchronized
    fun check(password: CharArray, entity: String, entityId: String) {
        val now = books.now()
        if (now < blockedUntil) throw TooManyAttemptsException((blockedUntil - now + 999) / 1000)
        if (books.session.verifyPassword(password)) {
            failures = 0
            blockedUntil = 0
            return
        }
        failures++
        books.session.audit("REVEAL_FAILED", entity, entityId)
        if (failures >= FREE_TRIES) {
            val wait = minOf(FIRST_WAIT_MS shl minOf(failures - FREE_TRIES, 10), MAX_WAIT_MS)
            blockedUntil = now + wait
        }
        throw AccessDeniedException("Wrong password")
    }

    companion object {
        const val FREE_TRIES = 3
        const val FIRST_WAIT_MS = 30_000L
        const val MAX_WAIT_MS = 300_000L
    }
}
