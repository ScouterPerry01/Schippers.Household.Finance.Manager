package ca.schippers.hfm.update

import ca.schippers.hfm.security.ReleaseSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SEC-08 and DIST-05: only a correctly signed, newer release is offered. */
class UpdateCheckTest {

    private val key = ReleaseSignature.SecretKey.generate()
    private val check = UpdateCheck(key.publicKey)
    private val sha = "ab".repeat(32)

    private fun manifest(version: String = "0.9.1", url: String = "https://github.com/x/releases/download/v0.9.1/ranns-roost.deb") =
        UpdateManifest(
            version = version,
            date = "2026-10-20",
            notes = mapOf("en" to "Fixes", "fr" to "Corrections"),
            files = listOf(UpdateFile("deb", "ranns-roost.deb", url, 1234, sha)),
        ).toJson().toByteArray()

    private fun signed(bytes: ByteArray, comment: String = "file:update.json\tversion:0.9.1") =
        ReleaseSignature.sign(bytes, key, comment)

    @Test
    fun `a newer signed release is offered with its notes`() {
        val bytes = manifest()
        val offer = assertNotNull(check.evaluate(bytes, signed(bytes), "0.9.0", Channel.DEB))
        assertEquals("0.9.1", offer.version.toString())
        assertEquals("Corrections", offer.notes("fr"))
        assertEquals(1234, offer.file.size)
    }

    @Test
    fun `the same or an older release is not offered`() {
        for (current in listOf("0.9.1", "0.10.0", "1.0.0")) {
            val bytes = manifest()
            assertNull(check.evaluate(bytes, signed(bytes), current, Channel.DEB), current)
        }
    }

    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(Version.parse("0.10.0") > Version.parse("0.9.9"))
        assertTrue(Version.parse("v1.0") == Version.parse("1.0.0"))
        assertFailsWith<IllegalArgumentException> { Version.parse("1.x") }
    }

    @Test
    fun `a channel without a file in the release gets nothing`() {
        val bytes = manifest()
        assertNull(check.evaluate(bytes, signed(bytes), "0.9.0", Channel.APK))
    }

    @Test
    fun `a manifest changed after signing is rejected`() {
        val bytes = manifest()
        val signature = signed(bytes)
        val changed = bytes.toString(Charsets.UTF_8).replace("ranns-roost.deb\"", "evil.deb\"").toByteArray()
        assertFailsWith<UpdateRejected> { check.evaluate(changed, signature, "0.9.0", Channel.DEB) }
    }

    @Test
    fun `a signature made for another release file is rejected`() {
        val bytes = manifest()
        assertFailsWith<UpdateRejected> { check.evaluate(bytes, signed(bytes, "file:ranns-roost.deb"), "0.9.0", Channel.DEB) }
    }

    @Test
    fun `a manifest signed with another key is rejected`() {
        val bytes = manifest()
        val other = ReleaseSignature.sign(bytes, ReleaseSignature.SecretKey.generate(), "file:update.json")
        assertFailsWith<UpdateRejected> { check.evaluate(bytes, other, "0.9.0", Channel.DEB) }
    }

    @Test
    fun `a download address that is not https is rejected`() {
        val bytes = manifest(url = "http://example.com/ranns-roost.deb")
        assertFailsWith<UpdateRejected> { check.evaluate(bytes, signed(bytes), "0.9.0", Channel.DEB) }
    }

    @Test
    fun `a download is checked against the signed size and hash`() {
        val file = UpdateFile("deb", "a.deb", "https://x", 1234, sha)
        UpdateCheck.verifyDownload(file, 1234, sha.uppercase())
        assertFailsWith<UpdateRejected> { UpdateCheck.verifyDownload(file, 1233, sha) }
        assertFailsWith<UpdateRejected> { UpdateCheck.verifyDownload(file, 1234, "cd".repeat(32)) }
    }
}
