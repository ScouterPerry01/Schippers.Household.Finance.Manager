package ca.schippers.hfm.security

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.Base64

/**
 * Signed releases (SEC-08, ADR 0008): Ed25519 signatures in the minisign format, so anyone can
 * also check a download with the `minisign` tool and the published public key.
 *
 * - Public key: "Ed", an 8-byte key id, the 32-byte key, in base64 under an untrusted comment.
 * - Signature: "ED", the key id and an Ed25519 signature of the file's BLAKE2b-512 hash, then a
 *   trusted comment and a second signature over the first signature and that comment, so the
 *   comment (which names the file) cannot be changed or moved to another file.
 */
object ReleaseSignature {

    class PublicKey(val keyId: ByteArray, val key: ByteArray) {
        /** The id as minisign prints it (the little-endian number in hex). */
        val keyIdHex: String get() = keyId.reversedArray().joinToString("") { "%02X".format(it) }

        fun toText(): String =
            "untrusted comment: minisign public key $keyIdHex\n" +
                Base64.getEncoder().encodeToString(ALG_KEY + keyId + key) + "\n"

        companion object {
            fun parse(text: String): PublicKey {
                val raw = Base64.getDecoder().decode(payloadLines(text).first())
                require(raw.size == 42 && raw.copyOfRange(0, 2).contentEquals(ALG_KEY)) { "Not an Ed25519 public key" }
                return PublicKey(raw.copyOfRange(2, 10), raw.copyOfRange(10, 42))
            }
        }
    }

    /** The secret part: the 32-byte Ed25519 seed and the key id. Never stored in the repository. */
    class SecretKey(val keyId: ByteArray, val seed: ByteArray) {
        val publicKey: PublicKey get() = PublicKey(keyId, Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded)

        fun toText(): String = Base64.getEncoder().encodeToString(keyId + seed)

        companion object {
            fun generate(): SecretKey = SecretKey(Random.bytes(8), Random.bytes(32))
            fun parse(text: String): SecretKey {
                val raw = Base64.getDecoder().decode(text.trim())
                require(raw.size == 40) { "Not a release signing key" }
                return SecretKey(raw.copyOfRange(0, 8), raw.copyOfRange(8, 40))
            }
        }
    }

    /** Thrown when a signature does not match: a damaged or forged file, or the wrong key. */
    class BadSignature(message: String) : Exception(message)

    fun sign(data: ByteArray, key: SecretKey, trustedComment: String, untrustedComment: String = "signature from RANN release key"): String {
        require('\n' !in trustedComment && '\n' !in untrustedComment)
        val private = Ed25519PrivateKeyParameters(key.seed, 0)
        val signature = ed25519Sign(private, blake2b(data))
        val global = ed25519Sign(private, signature + trustedComment.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return "untrusted comment: $untrustedComment\n" +
            b64.encodeToString(ALG_PREHASHED + key.keyId + signature) + "\n" +
            "trusted comment: $trustedComment\n" +
            b64.encodeToString(global) + "\n"
    }

    /** Checks [signatureText] over [data]; returns the trusted comment, or throws [BadSignature]. */
    fun verify(data: ByteArray, signatureText: String, key: PublicKey): String {
        val lines = signatureText.lines().map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
        if (lines.size != 4 || !lines[0].startsWith("untrusted comment:") || !lines[2].startsWith(TRUSTED)) {
            throw BadSignature("Not a minisign signature")
        }
        val raw = runCatching { Base64.getDecoder().decode(lines[1]) }.getOrNull()
        val global = runCatching { Base64.getDecoder().decode(lines[3]) }.getOrNull()
        if (raw == null || raw.size != 74 || global == null || global.size != 64) throw BadSignature("Malformed signature")
        if (!raw.copyOfRange(0, 2).contentEquals(ALG_PREHASHED)) throw BadSignature("Unsupported signature algorithm")
        if (!raw.copyOfRange(2, 10).contentEquals(key.keyId)) throw BadSignature("Signed with another key")
        val signature = raw.copyOfRange(10, 74)
        val public = Ed25519PublicKeyParameters(key.key, 0)
        if (!ed25519Verify(public, blake2b(data), signature)) throw BadSignature("The file does not match its signature")
        val comment = lines[2].removePrefix(TRUSTED)
        if (!ed25519Verify(public, signature + comment.toByteArray(Charsets.UTF_8), global)) {
            throw BadSignature("The trusted comment does not match its signature")
        }
        return comment
    }

    private fun blake2b(data: ByteArray): ByteArray =
        Blake2bDigest(512).run { update(data, 0, data.size); ByteArray(64).also { doFinal(it, 0) } }

    private fun ed25519Sign(key: Ed25519PrivateKeyParameters, message: ByteArray): ByteArray =
        Ed25519Signer().run { init(true, key); update(message, 0, message.size); generateSignature() }

    private fun ed25519Verify(key: Ed25519PublicKeyParameters, message: ByteArray, signature: ByteArray): Boolean =
        Ed25519Signer().run { init(false, key); update(message, 0, message.size); verifySignature(signature) }

    private fun payloadLines(text: String) =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("untrusted comment:") }

    private const val TRUSTED = "trusted comment: "
    private val ALG_KEY = byteArrayOf('E'.code.toByte(), 'd'.code.toByte())
    private val ALG_PREHASHED = byteArrayOf('E'.code.toByte(), 'D'.code.toByte())
}
