package ca.schippers.hfm.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** CAP-08: a spoken note's sound as a WAV file (16-bit mono PCM), which any computer plays without an extra library. */
object VoiceWav {

    const val RATE = 16_000

    fun wrap(pcm: ByteArray, rate: Int = RATE): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    /** The longest note the desktop keeps: the phone stops at a minute; a little room for the header. */
    const val MAX_SECONDS = 65

    /**
     * Whether [wav] is a note as [wrap] makes it: a RIFF/WAVE file, 16-bit mono PCM, no longer than
     * [MAX_SECONDS]. The desktop keeps and plays nothing else from a phone (Phase 5 security review).
     */
    fun isVoiceNote(wav: ByteArray): Boolean {
        if (wav.size < 44 || wav.size > 44 + MAX_SECONDS * RATE * 2) return false
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(at: Int) = String(wav, at, 4, Charsets.US_ASCII)
        return tag(0) == "RIFF" && tag(8) == "WAVE" && tag(12) == "fmt " && b.getShort(20).toInt() == 1 && b.getShort(22).toInt() == 1 && b.getShort(34).toInt() == 16
    }

    /** The length in whole seconds of a WAV file made by [wrap]. */
    fun seconds(wav: ByteArray, rate: Int = RATE): Int = ((wav.size - 44).coerceAtLeast(0)) / (rate * 2)
}
