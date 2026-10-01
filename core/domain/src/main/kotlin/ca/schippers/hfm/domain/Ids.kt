package ca.schippers.hfm.domain

import java.security.SecureRandom
import java.util.UUID

/**
 * Record identifiers are UUID version 7: unique across every device that creates records
 * (desktop, each paired phone), so items are never imported twice (SYNC-02), and time-ordered,
 * which keeps database indexes compact.
 */
object Ids {
    private val random = SecureRandom()
    private var lastMillis = 0L
    private var sequence = 0

    fun newId(): String = newUuid().toString()

    @Synchronized
    fun newUuid(nowMillis: Long = System.currentTimeMillis()): UUID {
        // Keep ids strictly increasing within one process even if the clock stalls or steps back.
        val millis: Long
        if (nowMillis > lastMillis) {
            millis = nowMillis
            sequence = random.nextInt(0x800)
        } else {
            millis = lastMillis
            sequence++
            check(sequence < 0x1000) { "Too many ids in one millisecond" }
        }
        lastMillis = millis

        val msb = (millis shl 16) or (0x7L shl 12) or sequence.toLong()
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE // variant 10
        return UUID(msb, lsb)
    }

    fun isValid(id: String): Boolean = try {
        UUID.fromString(id).toString() == id.lowercase()
    } catch (_: IllegalArgumentException) {
        false
    }
}
