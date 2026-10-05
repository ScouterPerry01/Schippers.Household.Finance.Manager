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

    /** The length in whole seconds of a WAV file made by [wrap]. */
    fun seconds(wav: ByteArray, rate: Int = RATE): Int = ((wav.size - 44).coerceAtLeast(0)) / (rate * 2)
}
