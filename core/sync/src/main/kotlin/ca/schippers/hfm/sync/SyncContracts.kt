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

/**
 * Kinds of item a phone can capture (CAP-01..08, MNT-03). [CONTACT] only marks a new contact in the
 * phone's own queue: contacts travel in [SyncRequest.contacts], never as a [CaptureItem], so a
 * desktop that does not know them still reads the request. [TASK_DONE] marks a task ticked in the
 * seasonal checklist the same way (SEA-04, [SyncRequest.tasksDone]), and [TRACKER] a meter
 * reading, tank level, hours, chore or volunteer hours ([SyncRequest.trackers]).
 */
enum class CaptureKind { RECEIPT, BILL, DOCUMENT, QUICK_EXPENSE, METER_READING, CONTACT, TASK_DONE, TRACKER }

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
    /** CAP-08: a spoken note, as a WAV file in base64 (mono, 16 kHz); its words are in the note when dictated. */
    val voice: String? = null,
    /**
     * CAP-05: text shared from another app (an email), kept as a plain text document. Its lines are
     * also sent as [ocrLines], so a desktop that predates this field keeps the words as the text it read.
     */
    val text: String? = null,
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

/**
 * CON-07: a contact made on the phone, for the desktop to review before it becomes a contact.
 * [id] is made on the phone, so the desktop receives it at most once. [kinds] are the names of the
 * desktop's contact kinds (BANK, PHARMACY...); for a person, [organizationId] is an organization
 * from [ReferenceData.contacts], and [organizationName] its name or one typed on the phone.
 */
@Serializable
data class PhoneContact(
    val id: String,
    val createdAtMillis: Long,
    val name: String,
    val person: Boolean = false,
    val organizationId: String? = null,
    val organizationName: String? = null,
    val kinds: List<String> = emptyList(),
    val purpose: String? = null,
    val phones: List<RefContactDetail> = emptyList(),
    val emails: List<RefContactDetail> = emptyList(),
    val address: String? = null,
    val notes: String? = null,
)

/**
 * SEA-04: a task ticked in the seasonal checklist on the phone, for the desktop to record in the
 * service log of the vehicle or asset ([vehicle] tells which). [id] is made on the phone, so the
 * desktop records it at most once. [date] is ISO (yyyy-MM-dd); [cost] a decimal string in the
 * vehicle's or asset's currency; [reading] the odometer or meter reading, all optional.
 */
@Serializable
data class PhoneTaskDone(
    val id: String,
    val createdAtMillis: Long,
    val taskId: String,
    val subjectId: String,
    val vehicle: Boolean,
    val date: String,
    val note: String? = null,
    val cost: String? = null,
    val reading: Int? = null,
)

/**
 * [contacts] (CON-07) and [tasksDone] (SEA-04) were added after the first phones: a desktop that
 * predates them ignores the field and does not acknowledge them, so they stay queued on the phone
 * until it is updated.
 */
@Serializable
data class SyncRequest(
    val sentAtMillis: Long,
    val items: List<CaptureItem>,
    val referenceVersion: String? = null,
    val contacts: List<PhoneContact> = emptyList(),
    /**
     * CSY-02: the phone's calendars brought in, each whole when it changed. A desktop that predates
     * them ignores the field and does not acknowledge them, so the phone keeps sending them.
     */
    val calendars: List<CalendarSnapshot> = emptyList(),
    val tasksDone: List<PhoneTaskDone> = emptyList(),
    /** UTL-01, UTL-02, HRS-01, CHO-01, VOL-01: what the phone's log forms recorded; ignored (and kept on the phone) by older desktops. */
    val trackers: List<PhoneTracker> = emptyList(),
)

/**
 * SYNC-09: an item the desktop could not store, with why: [reason] in English and [reasonFr] in
 * French (absent from desktops before 1.0), so the phone shows it in its own language.
 */
@Serializable
data class SyncFailure(val id: String, val reason: String, val reasonFr: String? = null) {
    fun reason(french: Boolean): String = if (french) reasonFr ?: reason else reason
}

/** SYNC-04: the items (and phone contacts) the desktop has stored; the phone may then delete its copies. */
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
    /** MNT-05 on the phone: maintenance due this month or overdue, on vehicles and other assets. */
    val maintenance: List<RefDue> = emptyList(),
    val generatedAtMillis: Long = 0,
    /** CON-07: the contacts the phone's user can see, without account or client numbers. */
    val contacts: List<RefContact> = emptyList(),
    /** CAL-03 on the phone: coming events, from the groups the phone's user can see, with their reminders. */
    val events: List<RefEvent> = emptyList(),
    /** CAL-03, HLT-03 on the phone: medication refills coming up or overdue. */
    val refills: List<RefRefill> = emptyList(),
    /** SEA-04 on the phone: the current season's checklist. */
    val seasonal: RefSeasonal? = null,
    /** UTL-01, UTL-02, HRS-01, CHO-01, VOL-01: the meters, tanks, clients, chores and organizations the log forms pick from. */
    val trackers: RefTrackers = RefTrackers(),
    /** CAL-10 on the phone: each person's work and school hours today and tomorrow, from the groups the phone's user can see. */
    val schedules: List<RefSchedule> = emptyList(),
) {
    companion object {
        /**
         * What a phone app understands of the reference data: 1 up to maintenance and budgets, 2
         * with contacts, 3 with events and refills, 4 with the seasonal checklist, what the log forms pick from and schedules. A phone that kept its copy with an older app asks for all of it again
         * ([knownVersion]), since that app dropped what it did not know.
         */
        const val FORMAT = 4

        /** The version a phone sends: none when its copy was kept by an app reading an older [FORMAT]. */
        fun knownVersion(version: String?, storedFormat: Int): String? = version?.takeIf { storedFormat >= FORMAT }
    }
}

@Serializable
data class RefAccount(val id: String, val name: String, val type: String, val currency: String, val balance: String)

@Serializable
data class RefCategory(val id: String, val parentId: String?, val nameEn: String, val nameFr: String, val income: Boolean)

@Serializable
data class RefPayee(val name: String, val categoryId: String?)

@Serializable
data class RefPerson(val id: String, val name: String, val pet: Boolean)

/** A vehicle, or another asset with a meter (MNT-03); [unit] is what the reading counts: KM or HOURS. */
@Serializable
data class RefVehicle(val id: String, val name: String, val odometer: Int?, val unit: String = "KM")

@Serializable
data class RefBill(val name: String, val dueDate: String, val amount: String, val currency: String, val estimated: Boolean, val reminderDays: List<Int>)

/** MNT-05: a task due soon ([state] SOON) or overdue (DUE), or next due this month (OK); [unit] counts [dueUsage]. */
@Serializable
data class RefDue(val taskId: String, val subject: String, val task: String, val state: String, val dueDate: String? = null, val dueUsage: Int? = null, val unit: String? = null)

@Serializable
data class RefBudget(
    val categoryName: String,
    val budget: String,
    val spent: String,
    val currency: String,
    /** BUD-04: so the phone can name the category in its own language and remember which alerts it showed. */
    val categoryId: String? = null,
)

/**
 * CON-07: a contact as the phone shows it, read-only. [kinds] are the desktop's kind names; [forWhom]
 * the names of the people it serves (none: the whole household). Account and client numbers are
 * never sent. A person's [organizationId] is set when that organization is sent too;
 * [organizationName] names it either way.
 */
@Serializable
data class RefContact(
    val id: String,
    val name: String,
    val person: Boolean = false,
    val organizationId: String? = null,
    val organizationName: String? = null,
    val jobTitle: String? = null,
    val kinds: List<String> = emptyList(),
    val purpose: String? = null,
    val forWhom: List<String> = emptyList(),
    val phones: List<RefContactDetail> = emptyList(),
    val emails: List<RefContactDetail> = emptyList(),
    val address: String? = null,
    val website: String? = null,
    val hours: String? = null,
    val notes: String? = null,
)

/**
 * CAL-03: one occurrence of an event. [id] names the occurrence (the event and its date), so each
 * reminder is shown once. [time] is "HH:mm", absent for an all-day event (reminded from 08:00);
 * [reminderMinutes] are the lead times chosen on the computer. [category] is the computer's
 * (MEDICAL, FINANCIAL...): a medical event is health data.
 */
@Serializable
data class RefEvent(
    val id: String,
    val title: String,
    val date: String,
    val time: String? = null,
    val category: String = "OTHER",
    val location: String? = null,
    val forWhom: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
)

/**
 * HLT-03: a medication whose supply runs out on [dueDate], reminded [reminderDays] ahead; [renewal]
 * when no refills are left, so the prescription must be renewed. The medication's name is health data.
 */
@Serializable
data class RefRefill(
    val id: String,
    val medication: String,
    val dueDate: String,
    val reminderDays: Int = 7,
    val forWhom: String? = null,
    val renewal: Boolean = false,
)

/**
 * CAL-10: one person's hours on [date]: [kind] is WORK, SCHOOL or OTHER, [start] and [end] are "HH:mm"
 * (an [end] before [start] ends the next day); [label] is where, when given.
 */
@Serializable
data class RefSchedule(
    val person: String,
    val kind: String,
    val date: String,
    val start: String,
    val end: String,
    val label: String? = null,
)

/**
 * SEA-04: the current season's checklist. [season] is SPRING, SUMMER, FALL or WINTER; it runs from
 * [start] up to the day before [end] (ISO dates).
 */
@Serializable
data class RefSeasonal(val season: String, val start: String, val end: String, val tasks: List<RefSeasonalTask> = emptyList())

/**
 * SEA-04: one task of the checklist. [state] is DONE, DUE, SOON or TO_DO; [unit] (KM or HOURS) is
 * set when a reading may be given with the tick; [currency] is that of a cost.
 */
@Serializable
data class RefSeasonalTask(
    val taskId: String,
    val subjectId: String,
    val subject: String,
    val task: String,
    val vehicle: Boolean,
    val state: String,
    val dueDate: String? = null,
    val doneOn: String? = null,
    val unit: String? = null,
    val currency: String = "CAD",
)

/** A phone number or email with its label ("Office", "Cell"). */
@Serializable
data class RefContactDetail(val value: String, val label: String? = null)

internal val SyncJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
internal val B64URL: Base64.Encoder get() = Base64.getUrlEncoder().withoutPadding()
internal val B64URL_DECODER: Base64.Decoder get() = Base64.getUrlDecoder()
