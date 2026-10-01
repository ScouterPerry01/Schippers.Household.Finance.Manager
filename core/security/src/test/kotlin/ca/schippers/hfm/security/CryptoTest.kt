package ca.schippers.hfm.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CryptoTest {

    @Test
    fun `Argon2id is deterministic and sensitive to every character`() {
        val salt = ByteArray(16) { 2 }
        val a = PasswordKdf.derive("correct horse".toCharArray(), salt, KdfParams.TESTING)
        val b = PasswordKdf.derive("correct horse".toCharArray(), salt, KdfParams.TESTING)
        val c = PasswordKdf.derive("correct horsf".toCharArray(), salt, KdfParams.TESTING)
        assertContentEquals(a, b)
        assertFalse(a.contentEquals(c))
        assertEquals(32, a.size)
    }

    @Test
    fun `AES-GCM round trip, and tampering or wrong context is detected`() {
        val key = Random.key()
        val sealed = Aead.seal(key, "secret".toByteArray(), "ctx".toByteArray())
        assertEquals("secret", Aead.open(key, sealed, "ctx".toByteArray()).decodeToString())
        assertFailsWith<DecryptionException> { Aead.open(key, sealed, "other".toByteArray()) }
        assertFailsWith<DecryptionException> { Aead.open(Random.key(), sealed, "ctx".toByteArray()) }
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertFailsWith<DecryptionException> { Aead.open(key, tampered, "ctx".toByteArray()) }
    }

    @Test
    fun `sealed box opens only for the recipient`() {
        val alice = KeyPair.generate()
        val bob = KeyPair.generate()
        val box = SealedBox.seal(alice.publicKey, "partition key".toByteArray(), "aad".toByteArray())
        assertEquals("partition key", SealedBox.open(alice, box, "aad".toByteArray()).decodeToString())
        assertFailsWith<DecryptionException> { SealedBox.open(bob, box, "aad".toByteArray()) }
        assertFailsWith<DecryptionException> { SealedBox.open(alice, box, "other".toByteArray()) }
    }

    @Test
    fun `key pair can be rebuilt from its private key`() {
        val pair = KeyPair.generate()
        assertContentEquals(pair.publicKey, KeyPair.fromPrivate(pair.privateKey).publicKey)
    }
}

class RecoveryKeyTest {

    @Test
    fun `display and parse round trip`() {
        val key = RecoveryKey.generate()
        val text = key.display()
        assertEquals(14, text.split("-").size)
        assertContentEquals(key.bytes, RecoveryKey.parse(text).bytes)
        assertContentEquals(key.bytes, RecoveryKey.parse(text.lowercase().replace("-", " ")).bytes)
    }

    @Test
    fun `look-alike letters are accepted`() {
        repeat(50) {
            val key = RecoveryKey.generate()
            val text = key.display()
            val typed = text.replace('1', 'l').replace('0', 'O')
            assertContentEquals(key.bytes, RecoveryKey.parse(typed).bytes)
        }
    }

    @Test
    fun `typing mistakes are caught`() {
        val text = RecoveryKey.generate().display()
        val wrong = (if (text[0] == 'A') "B" else "A") + text.substring(1)
        assertFailsWith<IllegalArgumentException> { RecoveryKey.parse(wrong) }
        assertFailsWith<IllegalArgumentException> { RecoveryKey.parse(text.dropLast(1)) }
    }
}
