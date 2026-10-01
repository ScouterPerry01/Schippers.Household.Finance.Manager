package ca.schippers.hfm.sync

/**
 * Contracts for phone-to-desktop transfer (section 3), implemented in Phase 2.
 *
 * Pairing: the desktop shows a QR code containing a [PairingInvitation]. The phone scans it,
 * generates its own key pair and answers over the local network; both sides then derive the
 * shared bundle key (SYNC-03). Bundles are encrypted end to end whatever the transport
 * (Wi-Fi, cloud folder, email, USB), so the transport never needs to be trusted.
 */
data class PairingInvitation(
    val desktopId: String,
    val desktopName: String,
    /** The desktop's X25519 public key. */
    val desktopPublicKey: ByteArray,
    val host: String,
    val port: Int,
    /** Short one-time code that proves the phone saw the QR code; expires after a few minutes. */
    val oneTimeCode: String,
) {
    override fun equals(other: Any?): Boolean =
        other is PairingInvitation && other.desktopId == desktopId && other.oneTimeCode == oneTimeCode &&
            other.desktopPublicKey.contentEquals(desktopPublicKey)

    override fun hashCode(): Int = 31 * desktopId.hashCode() + oneTimeCode.hashCode()
}

/** SYNC-09 transfer states, shown on both devices. */
enum class TransferStatus { PENDING, SENT, IMPORTED, FAILED }

/** Kinds of item a phone can capture (CAP-01..08, MNT-03). */
enum class CaptureKind { RECEIPT, BILL, DOCUMENT, QUICK_EXPENSE, METER_READING }

/**
 * One captured item. [id] is a UUIDv7 created on the phone, so the desktop imports it at most once
 * however many times it is sent (SYNC-02); [capturedBy] records the household member (SYNC-07).
 */
data class CapturedItemRef(
    val id: String,
    val kind: CaptureKind,
    val capturedBy: String,
    val deviceId: String,
    val capturedAtMillis: Long,
)
