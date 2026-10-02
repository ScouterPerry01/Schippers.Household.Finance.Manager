package ca.schippers.hfm.security

import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val KEY_BYTES = 32

/** Thrown when decryption fails: wrong password or key, or data that was tampered with. */
class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Random {
    private val secureRandom = SecureRandom()
    fun bytes(count: Int): ByteArray = ByteArray(count).also(secureRandom::nextBytes)
    fun key(): ByteArray = bytes(KEY_BYTES)
}

/** Argon2id cost settings, stored with each derived key so they can be raised in later versions. */
@Serializable
data class KdfParams(
    val memoryKiB: Int = 64 * 1024,
    val iterations: Int = 3,
    val parallelism: Int = 1,
) {
    companion object {
        /** Cheap settings for unit tests only. */
        val TESTING = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
    }
}

/** Derives a 256-bit key from a password with Argon2id (SEC-01). */
object PasswordKdf {
    const val SALT_BYTES = 16

    fun derive(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        require(salt.size >= SALT_BYTES) { "Salt too short" }
        val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .build()
        val out = ByteArray(KEY_BYTES)
        Argon2BytesGenerator().apply { init(argon) }.generateBytes(password, out)
        return out
    }
}

/**
 * AES-256-GCM authenticated encryption (SEC-01). Output is nonce (12 bytes) followed by the
 * ciphertext and tag. [aad] binds the ciphertext to its context so it cannot be moved elsewhere.
 */
object Aead {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == KEY_BYTES) { "Key must be 256 bits" }
        val nonce = Random.bytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == KEY_BYTES) { "Key must be 256 bits" }
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw DecryptionException("Ciphertext too short")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw DecryptionException("Decryption failed: wrong key or tampered data", e)
        }
    }
}

object Hkdf {
    fun derive(ikm: ByteArray, salt: ByteArray, info: String, length: Int = KEY_BYTES): ByteArray {
        val out = ByteArray(length)
        HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(ikm, salt, info.toByteArray())) }
            .generateBytes(out, 0, length)
        return out
    }
}

/** An X25519 key pair identifying one user or one paired device. */
class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
    companion object {
        fun generate(): KeyPair {
            val private = X25519PrivateKeyParameters(SecureRandom())
            return KeyPair(private.encoded, private.generatePublicKey().encoded)
        }

        fun fromPrivate(privateKey: ByteArray): KeyPair =
            KeyPair(privateKey, X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded)
    }
}

/**
 * Encrypts a secret for one recipient's public key, so a key can be granted to a user without
 * knowing their password (anonymous ECIES: X25519 + HKDF-SHA256 + AES-256-GCM).
 * Output: ephemeral public key (32 bytes) followed by an [Aead] ciphertext.
 */
object SealedBox {
    private const val INFO = "hfm/sealed-box/v1"

    fun seal(recipientPublicKey: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val ephemeral = KeyPair.generate()
        val key = sharedKey(ephemeral.privateKey, recipientPublicKey, ephemeral.publicKey, recipientPublicKey)
        return try {
            ephemeral.publicKey + Aead.seal(key, plaintext, aad)
        } finally {
            key.fill(0)
            ephemeral.privateKey.fill(0)
        }
    }

    fun open(recipient: KeyPair, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        if (sealed.size <= KEY_BYTES) throw DecryptionException("Sealed box too short")
        val ephemeralPublic = sealed.copyOfRange(0, KEY_BYTES)
        val key = sharedKey(recipient.privateKey, ephemeralPublic, ephemeralPublic, recipient.publicKey)
        return try {
            Aead.open(key, sealed.copyOfRange(KEY_BYTES, sealed.size), aad)
        } finally {
            key.fill(0)
        }
    }

    private fun sharedKey(private: ByteArray, peerPublic: ByteArray, ephemeralPublic: ByteArray, recipientPublic: ByteArray): ByteArray {
        val secret = ByteArray(KEY_BYTES)
        X25519Agreement().apply { init(X25519PrivateKeyParameters(private, 0)) }
            .calculateAgreement(X25519PublicKeyParameters(peerPublic, 0), secret, 0)
        if (secret.all { it == 0.toByte() }) throw DecryptionException("Invalid public key")
        return try {
            Hkdf.derive(secret, ephemeralPublic + recipientPublic, INFO)
        } finally {
            secret.fill(0)
        }
    }
}

/**
 * SYNC-03: the key a paired phone and desktop share. Each side combines its own private key with
 * the other's public key (X25519); both public keys are bound in, so the key belongs to this pair.
 */
object PairKey {
    private const val INFO = "hfm/sync-pair/v1"

    fun derive(own: KeyPair, peerPublicKey: ByteArray, desktopPublicKey: ByteArray, phonePublicKey: ByteArray): ByteArray {
        val secret = ByteArray(KEY_BYTES)
        X25519Agreement().apply { init(X25519PrivateKeyParameters(own.privateKey, 0)) }
            .calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), secret, 0)
        if (secret.all { it == 0.toByte() }) throw DecryptionException("Invalid public key")
        return try {
            Hkdf.derive(secret, desktopPublicKey + phonePublicKey, INFO)
        } finally {
            secret.fill(0)
        }
    }
}
