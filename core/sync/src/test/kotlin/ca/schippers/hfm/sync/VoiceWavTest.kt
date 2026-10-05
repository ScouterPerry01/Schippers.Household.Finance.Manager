package ca.schippers.hfm.sync

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/** CAP-08: the phone's voice notes are WAV files the desktop's own sound system reads. */
class VoiceWavTest {

    @Test
    fun `a recorded note opens with the computer's sound system, at its length`() {
        val pcm = ByteArray(VoiceWav.RATE * 2 * 3) { (it % 50).toByte() }
        val wav = VoiceWav.wrap(pcm)
        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))
        assertEquals(16_000f, stream.format.sampleRate)
        assertEquals(1, stream.format.channels)
        assertEquals(16, stream.format.sampleSizeInBits)
        assertEquals(3L * VoiceWav.RATE, stream.frameLength)
        assertEquals(3, VoiceWav.seconds(wav))
    }
}
