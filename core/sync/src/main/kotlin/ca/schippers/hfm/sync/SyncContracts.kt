package ca.schippers.hfm.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Phone-to-desktop transfer (section 3).
 *
 * Pairing (SYNC-03): the desktop shows a QR code with a [PairingInvitation]. The phone makes its own
 * key pair and answers over the local network with a [PairRequest]; its proof shows it saw the
 * one-time code in the QR code. Both sides then derive the same pair key ([PairKey]) and every
 * [SyncRequest] and [SyncResponse] is sealed with it, so the network never needs to be trusted.
 */
@Serializable
data class PairingInvitation(
    val desktopId: String,
    val desktopName: String,
    /** The desktop's X25519 public key, base64. */
    val desktopPublicKey: String,
    val host: String,
    val port: Int,
    /** A short code that proves the phone saw the QR code; it expires after a few minutes. */
    val oneTimeCode: String,
) {
    /**
     * The text inside the QR code: a link the companion app opens, so the phone's own camera app can
     * start the pairing too.
     */
    fun toQrText(): String = PREFIX + B64URL.encodeToString(SyncJson.encodeToString(serializer(), this).encodeToByteArray())

    companion object {
        const val SCHEME = "hfmpair"
        private const val PREFIX = "$SCHEME:v1:"

        fun fromQrText(text: String): PairingInvitation? = runCatching {
            val trimmed = text.trim()
            require(trimmed.startsWith(PREFIX))
            SyncJson.decodeFromString(serializer(), B64URL_DECODER.decode(trimmed.removePrefix(PREFIX)).decodeToString())
        }.getOrNull()
    }
}

@Serializable
data class PairRequest(val deviceId: String, val deviceName: String, val phonePublicKey: String, val proof: String)

/** [proof] shows the desktop derived the same pair key, so the phone knows it reached the right desktop. */
@Serializable
data class PairResponse(val desktopId: String, val householdName: String, val proof: String)

/** SYNC-09 transfer states, shown on both devices. */
enum class TransferStatus { PENDING, SENT, IMPORTED, FAILED }

/** Kinds of item a phone can capture (CAP-01..08, MNT-03). */
enum class CaptureKind { RECEIPT, BILL, DOCUMENT, QUICK_EXPENSE, METER_READING }

/**
 * One captured item. [id] is created on the phone, so the desktop imports it at most once however
 * many times it is sent (SYNC-02). Pages are JPEG images, base64; [ocrText] is what the phone read
 * (OCR-01), one line per row, with its confidence.
 */
@Serializable
data class CaptureItem(
    val id: String,
    val kind: CaptureKind,
    val capturedAtMillis: Long,
    val pages: List<String> = emptyList(),
    val pdf: String? = null,
    val fileName: String? = null,
    val ocrLines: List<OcrText> = emptyList(),
    val fields: CaptureFields = CaptureFields(),
)

@Serializable
data class OcrText(val text: String, val confidence: Float)

/** What the person typed on the phone (CAP-07): all optional. Amounts are decimal strings. */
@Serializable
data class CaptureFields(
    val merchant: String? = null,
    val date: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val memberId: String? = null,
    val note: String? = null,
    val vehicleId: String? = null,
    val odometer: Int? = null,
)

@Serializable
data class SyncRequest(val sentAtMillis: Long, val items: List<CaptureItem>, val referenceVersion: String? = null)

@Serializable
data class SyncFailure(val id: String, val reason: String)

/** SYNC-04: the items the desktop has stored; the phone may then delete its copies. */
@Serializable
data class SyncResponse(
    val imported: List<String>,
    val failed: List<SyncFailure> = emptyList(),
    val referenceVersion: String,
    /** SYNC-06: only sent when it changed since [SyncRequest.referenceVersion]. */
    val reference: ReferenceData? = null,
)

/** SYNC-06, RPT-06: what the phone needs to pick from and to show, read-only. */
@Serializable
data class ReferenceData(
    val householdName: String,
    val language: String,
    val baseCurrency: String,
    val accounts: List<RefAccount> = emptyList(),
    val categories: List<RefCategory> = emptyList(),
    val payees: List<RefPayee> = emptyList(),
    val people: List<RefPerson> = emptyList(),
    val vehicles: List<RefVehicle> = emptyList(),
    /** BILL-04 on the phone: bills due soon, for reminders. */
    val bills: List<RefBill> = emptyList(),
    val budgets: List<RefBudget> = emptyList(),
    val generatedAtMillis: Long = 0,
)

@Serializable
data class RefAccount(val id: String, val name: String, val type: String, val currency: String, val balance: String)

@Serializable
data class RefCategory(val id: String, val parentId: String?, val nameEn: String, val nameFr: String, val income: Boolean)

@Serializable
data class RefPayee(val name: String, val categoryId: String?)

@Serializable
data class RefPerson(val id: String, val name: String, val pet: Boolean)

@Serializable
data class RefVehicle(val id: String, val name: String, val odometer: Int?)

@Serializable
data class RefBill(val name: String, val dueDate: String, val amount: String, val currency: String, val estimated: Boolean, val reminderDays: List<Int>)

@Serializable
data class RefBudget(val categoryName: String, val budget: String, val spent: String, val currency: String)

internal val SyncJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
internal val B64URL: Base64.Encoder get() = Base64.getUrlEncoder().withoutPadding()
internal val B64URL_DECODER: Base64.Decoder get() = Base64.getUrlDecoder()
