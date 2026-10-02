package ca.schippers.hfm.books

import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.security.Random
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PairResponse
import ca.schippers.hfm.sync.PairingInvitation
import ca.schippers.hfm.sync.RefAccount
import ca.schippers.hfm.sync.RefBill
import ca.schippers.hfm.sync.RefBudget
import ca.schippers.hfm.sync.RefCategory
import ca.schippers.hfm.sync.RefPayee
import ca.schippers.hfm.sync.RefPerson
import ca.schippers.hfm.sync.RefVehicle
import ca.schippers.hfm.sync.ReferenceData
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncFailure
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.security.MessageDigest

/** A paired phone (SYNC-07), as the desktop's Phones screen shows it. */
data class PairedDevice(
    val id: String,
    val name: String,
    val userId: String,
    val groupId: String?,
    val pairedAt: Long,
    val lastSeen: Long?,
    val itemsReceived: Int,
    val revoked: Boolean,
)

/** SYNC-08: the request came from a phone that is not, or no longer, paired. */
class DeviceNotPairedException : Exception("This phone is not paired")

/**
 * HH-11, HH-12: the phone belongs to another household user, who is not the one signed in. Its pair
 * key is sealed for that user, so its captures wait on the phone until they open the household.
 */
class OwnerAwayException(val ownerName: String) : Exception("The phone's owner is not signed in")

/** SYNC-03: the phone's answer did not match a current QR code. */
class PairingRejectedException : Exception("Pairing was refused")

/**
 * Converts what the desktop needs: a multi-page capture into one PDF (CAP-02), and text
 * recognition when the phone sent none (OCR-01). Provided by the desktop app.
 */
interface CaptureConverter {
    fun pagesToPdf(pages: List<ByteArray>): ByteArray
    fun recognize(content: ByteArray): OcrResult?
}

/**
 * The desktop's side of phone capture (section 3): pairing by QR code (SYNC-03), receiving items
 * exactly once with an acknowledgement (SYNC-02, SYNC-04), removing a lost phone (SYNC-08), the
 * reference data sent back (SYNC-06), and several phones per desktop (SYNC-07). Received items
 * land in the review inbox (SYNC-05), never straight in the books.
 */
class SyncService internal constructor(private val books: Books) {

    private class Pending(val invitation: PairingInvitation, val expiresAt: Long)

    private val pending = ArrayList<Pending>()

    /** This household's desktop identity: its id and long-term key pair, made on first use. */
    private fun identity(): Pair<String, KeyPair> {
        val id = books.setting(DESKTOP_ID) ?: Ids.newId().also { books.putSetting(DESKTOP_ID, it) }
        val key = books.setting(PRIVATE_KEY)?.let { KeyPair.fromPrivate(SyncCrypto.unb64(it)) }
            ?: KeyPair.generate().also { books.putSetting(PRIVATE_KEY, SyncCrypto.b64(it.privateKey)) }
        return id to key
    }

    val desktopId: String get() = identity().first

    /** SYNC-03: a new QR code, valid for [validMillis]. Earlier codes stay valid until they expire. */
    @Synchronized
    fun invitation(desktopName: String, host: String, port: Int, now: Long, validMillis: Long = INVITATION_MILLIS): PairingInvitation {
        val (id, key) = identity()
        val code = Random.bytes(6).joinToString("") { "%02x".format(it) }
        val invitation = PairingInvitation(id, desktopName, SyncCrypto.b64(key.publicKey), host, port, code)
        pending.removeAll { it.expiresAt < now }
        pending += Pending(invitation, now + validMillis)
        return invitation
    }

    /** Answers a phone's pairing request; the phone becomes a device of the signed-in user. */
    @Synchronized
    fun pair(request: PairRequest, now: Long): PairResponse {
        val (id, key) = identity()
        pending.removeAll { it.expiresAt < now }
        val phoneKey = runCatching { SyncCrypto.unb64(request.phonePublicKey) }.getOrNull()?.takeIf { it.size == 32 } ?: throw PairingRejectedException()
        val match = pending.firstOrNull { SyncCrypto.sameProof(request.proof, SyncCrypto.phoneProof(it.invitation.oneTimeCode, phoneKey, key.publicKey)) }
            ?: throw PairingRejectedException()
        pending.remove(match)
        require(request.deviceId.isNotBlank() && request.deviceId.length <= 64) { "Invalid device id" }
        val pairKey = PairKey.derive(key, phoneKey, key.publicKey, phoneKey)
        // Only the user who paired the phone can open its key (HH-11).
        val sealedKey = SyncCrypto.b64(books.session.sealFor(books.userId, pairKey, "device:${request.deviceId}"))
        books.core.insertDevice(request.deviceId, request.deviceName.take(80).ifBlank { "Phone" }, request.phonePublicKey, sealedKey, books.userId, defaultGroup(), now)
        books.session.audit("PAIR", "device", request.deviceId, request.deviceName.take(80))
        return PairResponse(id, books.core.household().executeAsOne().name, SyncCrypto.desktopProof(pairKey, id, request.deviceId))
    }

    fun devices(): List<PairedDevice> = books.core.devices().executeAsList().map {
        PairedDevice(it.id, it.name, it.user_id, it.group_id, it.paired_at, it.last_seen, it.items_received.toInt(), it.revoked_at != null)
    }

    /** SYNC-08: the phone can no longer send or receive anything; it must be paired again. */
    fun revoke(deviceId: String, now: Long) {
        books.core.revokeDevice(now, deviceId)
        books.session.audit("REVOKE", "device", deviceId)
    }

    fun forget(deviceId: String) {
        books.core.deleteDevice(deviceId)
        books.session.audit("DELETE", "device", deviceId)
    }

    fun update(deviceId: String, name: String, groupId: String?) {
        validate(name.isNotBlank(), "error.nameRequired")
        books.core.renameDevice(name.trim(), groupId, deviceId)
    }

    /**
     * Opens a phone's sealed request, stores each new item, and returns the sealed answer: the
     * items stored, those that failed, and the reference data when it changed.
     */
    fun handle(deviceId: String, sealed: ByteArray, converter: CaptureConverter, now: Long, today: LocalDate): ByteArray {
        val device = books.core.deviceById(deviceId).executeAsOneOrNull()?.takeIf { it.revoked_at == null } ?: throw DeviceNotPairedException()
        if (device.user_id != books.userId) throw OwnerAwayException(books.core.userById(device.user_id).executeAsOneOrNull()?.display_name.orEmpty())
        val key = books.session.openSealed(SyncCrypto.unb64(device.pair_key), "device:$deviceId")
        val request = SyncCrypto.open(SyncRequest.serializer(), sealed, key, desktopId, deviceId, Direction.TO_DESKTOP)
        val imported = ArrayList<String>()
        val failed = ArrayList<SyncFailure>()
        var added = 0
        for (item in request.items.take(MAX_ITEMS)) {
            if (books.core.syncItemById(item.id).executeAsOneOrNull() != null) {
                imported += item.id
                continue
            }
            runCatching { receive(device.group_id ?: defaultGroup(), deviceId, item, converter, today) }
                .onSuccess { documentId ->
                    books.core.insertSyncItem(item.id, deviceId, item.kind.name, now, documentId, "IMPORTED")
                    imported += item.id
                    added++
                }
                .onFailure { failed += SyncFailure(item.id, it.message ?: it.javaClass.simpleName) }
        }
        books.core.deviceSeen(now, added.toLong(), deviceId)
        val reference = reference(today, now)
        val version = version(reference)
        val response = SyncResponse(imported, failed, version, reference.takeIf { version != request.referenceVersion })
        return SyncCrypto.seal(SyncResponse.serializer(), response, key, desktopId, deviceId, Direction.TO_PHONE)
    }

    /** Stores one captured item; returns the document it became, if any. */
    private fun receive(groupId: String?, deviceId: String, item: CaptureItem, converter: CaptureConverter, today: LocalDate): String? {
        val f = item.fields
        if (item.kind == CaptureKind.METER_READING) {
            // MNT-03: a reading is a fact, not a document to review.
            val vehicle = f.vehicleId ?: throw ValidationException("error.vehicleRequired")
            books.vehicles.addReading(vehicle, f.date?.let(LocalDate::parse) ?: today, f.odometer ?: throw ValidationException("error.invalidNumber"), f.note)
            return null
        }
        val group = groupId ?: throw ValidationException("error.noEditableGroup")
        val pages = item.pages.map(SyncCrypto::unb64)
        val pdf = item.pdf
        val (content, mime) = when {
            pdf != null -> SyncCrypto.unb64(pdf) to "application/pdf"
            pages.size > 1 -> converter.pagesToPdf(pages) to "application/pdf"
            pages.size == 1 -> pages.single() to "image/jpeg"
            // CAP-07: a quick expense with no photo is kept as a short note to review.
            else -> listOfNotNull(f.merchant, f.date, f.amount, f.note).joinToString("\n").ifBlank { item.kind.name }.encodeToByteArray() to "text/plain"
        }
        val doc = books.documents.import(group, content, item.fileName ?: "${item.kind.name.lowercase()}-${item.id.take(8)}", mime, deviceId).document
        val ocr = when {
            item.ocrLines.isNotEmpty() -> OcrResult(item.ocrLines.map { OcrLine(it.text, it.confidence) }, 0) to "mlkit"
            mime != "text/plain" -> converter.recognize(content)?.let { it to "desktop" }
            else -> null
        }
        ocr?.let { (result, engine) -> books.documents.recordText(doc.id, maxOf(1, pages.size), result, engine, today) }
        // What the person typed on the phone wins over what was read.
        val read = books.documents.get(doc.id)
        val amount = f.amount?.let { a -> runCatching { Money.exact(BigDecimal(a), f.currency?.let(Currency::of) ?: read.amount?.currency ?: books.rates.baseCurrency) }.getOrNull() }
        val kind = when (item.kind) {
            CaptureKind.BILL -> DocumentKind.BILL
            CaptureKind.RECEIPT, CaptureKind.QUICK_EXPENSE -> DocumentKind.RECEIPT
            else -> read.kind
        }
        books.documents.update(
            doc.id,
            DocumentDetails(
                read.title, kind, f.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: read.date, f.merchant ?: read.merchant,
                amount ?: read.amount, read.keepForever, f.note ?: read.notes,
            ),
        )
        return doc.id
    }

    /** SYNC-06, RPT-06, BILL-04: what the phone shows and picks from. */
    fun reference(today: LocalDate, now: Long): ReferenceData {
        val base = books.rates.baseCurrency
        val budgets = runCatching { books.budgets.month(LocalDate(today.year, today.month, 1)) }.getOrNull()
        return ReferenceData(
            householdName = books.core.household().executeAsOne().name,
            language = books.setting("ui.language") ?: "en",
            baseCurrency = base.code,
            accounts = books.accounts.list().map { RefAccount(it.account.id, it.account.name, it.account.type.name, it.account.currency.code, it.balance.toBigDecimal().toPlainString()) },
            categories = books.categories.list().map { RefCategory(it.id, it.parentId, it.nameEn, it.nameFr, it.kind == CategoryKind.INCOME) },
            payees = books.payees.list().take(MAX_PAYEES).map { RefPayee(it.name, it.defaultCategoryId) },
            people = books.members.list().map { RefPerson(it.id, it.displayName, false) } + books.pets.list().map { RefPerson(it.id, it.name, true) },
            vehicles = books.vehicles.list().map { RefVehicle(it.id, it.name, books.vehicles.latestOdometer(it.id)?.odometer) },
            bills = books.bills.occurrences(today, today.plus(DatePeriod(days = 60))).filter { it.status == OccurrenceStatus.DUE && it.bill.kind == BillKind.BILL }.map {
                RefBill(it.bill.name, it.dueDate.toString(), it.amount.toBigDecimal().toPlainString(), it.amount.currency.code, !it.amountKnown, it.bill.reminderDays)
            },
            budgets = budgets?.lines.orEmpty().filter { it.category.kind == CategoryKind.EXPENSE }.map {
                RefBudget(it.category.name(ca.schippers.hfm.i18n.Language.ENGLISH), it.budgeted.toBigDecimal().toPlainString(), it.actual.toBigDecimal().toPlainString(), it.budgeted.currency.code)
            },
            generatedAtMillis = now,
        )
    }

    /** Changes when anything the phone shows changes, but not merely with the time. */
    private fun version(r: ReferenceData): String {
        val text = kotlinx.serialization.json.Json.encodeToString(ReferenceData.serializer(), r.copy(generatedAtMillis = 0))
        return MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    /** HH-12: a member's captures go to their own private group; an administrator's to the shared one. */
    private fun defaultGroup(): String? {
        val groups = books.groups().filter { it.level.allows(PermissionLevel.CAPTURE_ONLY) }
        val own = groups.firstOrNull { it.ownerUserId == books.userId }
        val shared = groups.firstOrNull { !it.isPrivate }
        return (if (books.role == ca.schippers.hfm.domain.Role.ADMINISTRATOR) shared ?: own else own ?: shared)?.id ?: groups.firstOrNull()?.id
    }

    companion object {
        private const val DESKTOP_ID = "sync.desktopId"
        private const val PRIVATE_KEY = "sync.privateKey"
        const val INVITATION_MILLIS = 10 * 60_000L
        private const val MAX_ITEMS = 50
        private const val MAX_PAYEES = 400
    }
}
