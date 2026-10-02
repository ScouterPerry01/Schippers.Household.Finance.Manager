package ca.schippers.hfm.sync

import ca.schippers.hfm.security.Aead
import kotlinx.serialization.KSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Which way a bundle travels; bound into its encryption so a reply cannot be replayed as a request. */
enum class Direction { TO_DESKTOP, TO_PHONE }

/** SYNC-03: proofs for pairing and the end-to-end encryption of every bundle. */
object SyncCrypto {

    /** The phone shows it saw the QR code: a MAC under the one-time code over both public keys. */
    fun phoneProof(oneTimeCode: String, phonePublicKey: ByteArray, desktopPublicKey: ByteArray): String =
        b64(hmac(oneTimeCode.encodeToByteArray(), "pair".encodeToByteArray() + phonePublicKey + desktopPublicKey))

    /** The desktop shows it derived the same pair key. */
    fun desktopProof(pairKey: ByteArray, desktopId: String, deviceId: String): String =
        b64(hmac(pairKey, "paired|$desktopId|$deviceId".encodeToByteArray()))

    fun sameProof(a: String, b: String): Boolean = MessageDigest.isEqual(a.encodeToByteArray(), b.encodeToByteArray())

    fun <T> seal(serializer: KSerializer<T>, value: T, pairKey: ByteArray, desktopId: String, deviceId: String, direction: Direction): ByteArray =
        Aead.seal(pairKey, gzip(SyncJson.encodeToString(serializer, value).encodeToByteArray()), aad(desktopId, deviceId, direction))

    /** @throws ca.schippers.hfm.security.DecryptionException when the bundle is not from the paired device. */
    fun <T> open(serializer: KSerializer<T>, sealed: ByteArray, pairKey: ByteArray, desktopId: String, deviceId: String, direction: Direction): T =
        SyncJson.decodeFromString(serializer, gunzip(Aead.open(pairKey, sealed, aad(desktopId, deviceId, direction))).decodeToString())

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun aad(desktopId: String, deviceId: String, direction: Direction) = "hfm-sync|v1|$desktopId|$deviceId|$direction".encodeToByteArray()

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}
