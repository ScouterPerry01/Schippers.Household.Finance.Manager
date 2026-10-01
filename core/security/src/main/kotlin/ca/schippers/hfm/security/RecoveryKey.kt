package ca.schippers.hfm.security

/**
 * The printed recovery key generated at setup (SEC-07). There is no server that could reset a
 * forgotten master password, so this key is the only way back in.
 *
 * It holds 256 random bits written in Crockford base32 (no I, L, O or U, so it is easy to read
 * and type), followed by a 2-character checksum, in groups of four:
 * `7KQ2-M9XD-...-W3`. Typing mistakes are caught by the checksum before any decryption is tried.
 */
class RecoveryKey private constructor(val bytes: ByteArray) {

    fun display(): String {
        val body = encode(bytes)
        val text = body + checksum(body)
        return text.chunked(4).joinToString("-")
    }

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        fun generate(): RecoveryKey = RecoveryKey(Random.key())

        /** Parses a typed recovery key, forgiving case, spaces, dashes, and I/L/O look-alikes. */
        fun parse(text: String): RecoveryKey {
            val cleaned = text.uppercase()
                .filterNot { it == '-' || it.isWhitespace() }
                .map { c -> when (c) { 'I', 'L' -> '1'; 'O' -> '0'; else -> c } }
                .joinToString("")
            require(cleaned.all { it in ALPHABET }) { "The recovery key contains characters that are not allowed" }
            val bodyLength = (KEY_BYTES * 8 + 4) / 5
            require(cleaned.length == bodyLength + 2) { "The recovery key has the wrong length" }
            val body = cleaned.substring(0, bodyLength)
            require(checksum(body) == cleaned.substring(bodyLength)) { "The recovery key has a typing mistake" }
            return RecoveryKey(decode(body, KEY_BYTES))
        }

        private fun encode(data: ByteArray): String {
            val sb = StringBuilder()
            var buffer = 0
            var bits = 0
            for (b in data) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bits += 8
                while (bits >= 5) {
                    sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                    bits -= 5
                }
            }
            if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
            return sb.toString()
        }

        private fun decode(text: String, length: Int): ByteArray {
            val out = ByteArray(length)
            var buffer = 0
            var bits = 0
            var i = 0
            for (c in text) {
                buffer = (buffer shl 5) or ALPHABET.indexOf(c)
                bits += 5
                if (bits >= 8 && i < length) {
                    out[i++] = (buffer shr (bits - 8)).toByte()
                    bits -= 8
                }
            }
            return out
        }

        /** Two base32 characters (10 bits) of SHA-256 over the body. */
        private fun checksum(body: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            val v = ((digest[0].toInt() and 0xFF) shl 2) or ((digest[1].toInt() and 0xFF) shr 6)
            return "${ALPHABET[v shr 5]}${ALPHABET[v and 31]}"
        }
    }
}
