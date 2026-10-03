package ca.schippers.hfm.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SEC-08: release signatures. Interoperability with the real minisign tool is checked in CI. */
class ReleaseSignatureTest {

    private val key = ReleaseSignature.SecretKey.generate()
    private val public = key.publicKey
    private val data = "ranns-roost_0.9.0_amd64.deb contents".toByteArray()

    @Test
    fun `a signed file verifies and returns its trusted comment`() {
        val signature = ReleaseSignature.sign(data, key, "file:update.json\tversion:0.9.0")
        assertEquals("file:update.json\tversion:0.9.0", ReleaseSignature.verify(data, signature, public))
    }

    @Test
    fun `keys survive being written out and read back`() {
        val again = ReleaseSignature.SecretKey.parse(key.toText())
        val publicAgain = ReleaseSignature.PublicKey.parse(public.toText())
        assertTrue(public.toText().startsWith("untrusted comment: minisign public key ${public.keyIdHex}\nRW"))
        val signature = ReleaseSignature.sign(data, again, "file:x")
        assertEquals("file:x", ReleaseSignature.verify(data, signature, publicAgain))
    }

    @Test
    fun `a changed file is refused`() {
        val signature = ReleaseSignature.sign(data, key, "file:x")
        val changed = data.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFailsWith<ReleaseSignature.BadSignature> { ReleaseSignature.verify(changed, signature, public) }
    }

    @Test
    fun `a changed trusted comment is refused`() {
        val signature = ReleaseSignature.sign(data, key, "file:old.deb")
        val moved = signature.replace("file:old.deb", "file:update.json")
        assertFailsWith<ReleaseSignature.BadSignature> { ReleaseSignature.verify(data, moved, public) }
    }

    @Test
    fun `another key is refused, even with the same key id`() {
        val signature = ReleaseSignature.sign(data, key, "file:x")
        val other = ReleaseSignature.SecretKey.generate()
        assertFailsWith<ReleaseSignature.BadSignature> { ReleaseSignature.verify(data, signature, other.publicKey) }
        val impostor = ReleaseSignature.SecretKey(key.keyId, Random.bytes(32))
        val forged = ReleaseSignature.sign(data, impostor, "file:x")
        assertFailsWith<ReleaseSignature.BadSignature> { ReleaseSignature.verify(data, forged, public) }
    }

    @Test
    fun `garbage is refused, not crashed on`() {
        for (text in listOf("", "hello", "untrusted comment: x\n!!!\ntrusted comment: y\n???\n")) {
            assertFailsWith<ReleaseSignature.BadSignature> { ReleaseSignature.verify(data, text, public) }
        }
    }
}
