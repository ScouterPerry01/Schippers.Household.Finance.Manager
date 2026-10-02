package ca.schippers.hfm.sync

import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** A desktop this phone is paired with; stored encrypted on the phone (SYNC-01). */
@Serializable
data class PairedDesktop(
    val desktopId: String,
    val householdName: String,
    val host: String,
    val port: Int,
    val deviceId: String,
    /** The pair key, base64. */
    val pairKey: String,
)

class SyncException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason {
        /** The desktop is off, asleep, on another network, or its app is locked. */
        UNREACHABLE,
        /** SYNC-08: this phone was removed on the desktop and must be paired again. */
        REVOKED,
        /** The QR code expired, or the answer did not prove the right desktop. */
        REJECTED,
    }
}

/**
 * The phone's side of the transfer, over plain HTTP on the local network: every body is sealed
 * with the pair key, so the content is private even on a shared Wi-Fi. Uses only java.net, so it
 * runs on Android and in desktop tests.
 */
class SyncClient(private val connectTimeoutMillis: Int = 5_000, private val readTimeoutMillis: Int = 180_000) {

    /** SYNC-03: answers the desktop's QR code. */
    fun pair(invitation: PairingInvitation, deviceId: String, deviceName: String, keys: KeyPair = KeyPair.generate()): PairedDesktop {
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        val request = PairRequest(deviceId, deviceName, SyncCrypto.b64(keys.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, keys.publicKey, desktopKey))
        val body = post(invitation.host, invitation.port, PAIR_PATH, SyncJson.encodeToString(PairRequest.serializer(), request).encodeToByteArray(), deviceId = null)
        val response = SyncJson.decodeFromString(PairResponse.serializer(), body.decodeToString())
        val pairKey = PairKey.derive(keys, desktopKey, desktopKey, keys.publicKey)
        if (response.desktopId != invitation.desktopId || !SyncCrypto.sameProof(response.proof, SyncCrypto.desktopProof(pairKey, invitation.desktopId, deviceId))) {
            throw SyncException(SyncException.Reason.REJECTED, "The desktop's answer could not be verified")
        }
        return PairedDesktop(invitation.desktopId, response.householdName, invitation.host, invitation.port, deviceId, SyncCrypto.b64(pairKey))
    }

    /** Sends captured items and receives the acknowledgements and reference data (SYNC-04, SYNC-06). */
    fun sync(desktop: PairedDesktop, request: SyncRequest): SyncResponse {
        val key = SyncCrypto.unb64(desktop.pairKey)
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, desktop.desktopId, desktop.deviceId, Direction.TO_DESKTOP)
        val answer = post(desktop.host, desktop.port, SYNC_PATH, sealed, desktop.deviceId)
        return SyncCrypto.open(SyncResponse.serializer(), answer, key, desktop.desktopId, desktop.deviceId, Direction.TO_PHONE)
    }

    private fun post(host: String, port: Int, path: String, body: ByteArray, deviceId: String?): ByteArray {
        val connection = try {
            (URI("http", null, host, port, path, null, null).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("Content-Type", "application/octet-stream")
                deviceId?.let { setRequestProperty(DEVICE_HEADER, it) }
                setFixedLengthStreamingMode(body.size)
            }
        } catch (e: Exception) {
            throw SyncException(SyncException.Reason.UNREACHABLE, "Cannot reach the desktop", e)
        }
        try {
            connection.outputStream.use { it.write(body) }
            return when (val code = connection.responseCode) {
                200 -> connection.inputStream.use { it.readBytes() }
                403 -> throw SyncException(SyncException.Reason.REVOKED, "This phone is no longer paired")
                400, 401 -> throw SyncException(SyncException.Reason.REJECTED, "The desktop refused the request ($code)")
                else -> throw SyncException(SyncException.Reason.UNREACHABLE, "The desktop answered $code")
            }
        } catch (e: IOException) {
            throw SyncException(SyncException.Reason.UNREACHABLE, "Cannot reach the desktop", e)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val PAIR_PATH = "/hfm/v1/pair"
        const val SYNC_PATH = "/hfm/v1/sync"
        const val DEVICE_HEADER = "X-HFM-Device"
        const val DEFAULT_PORT = 47311
    }
}
