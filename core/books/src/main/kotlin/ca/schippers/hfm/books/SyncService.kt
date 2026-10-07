package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.schedule.Seasons
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.i18n.Language
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
import ca.schippers.hfm.sync.BundleFile
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PairResponse
import ca.schippers.hfm.sync.PairingInvitation
import ca.schippers.hfm.sync.PhoneTaskDone
import ca.schippers.hfm.sync.PhoneFuel
import ca.schippers.hfm.sync.RefTrailer
import ca.schippers.hfm.sync.RefAccount
import ca.schippers.hfm.sync.RefBill
import ca.schippers.hfm.sync.RefBudget
import ca.schippers.hfm.sync.RefCategory
import ca.schippers.hfm.sync.RefDue
import ca.schippers.hfm.sync.RefEvent
import ca.schippers.hfm.sync.RefRefill
import ca.schippers.hfm.sync.RefRenewal
import ca.schippers.hfm.sync.RefSchedule
import ca.schippers.hfm.sync.RefSeasonal
import ca.schippers.hfm.sync.RefSeasonalTask
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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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

/** Section 3.2: the reply to a request that came as a file, and how many of its items were new. */
class FileReply(val name: String, val bytes: ByteArray, val deviceId: String, val received: Int)

/** Section 3.2: a transfer file for another household, such as one sharing the same cloud folder. */
class NotThisHouseholdException : Exception("This transfer file is for another household")

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
 * land in the review inbox (SYNC-05), and contacts made on the phone in their own review list
 * (CON-07), never straight in the books.
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

    /**
     * SYNC-03: a new QR code, valid for [validMillis] (the invitation lead time of Rates and rules,
     * 10 minutes built in). Earlier codes stay valid until they expire.
     */
    @Synchronized
    fun invitation(desktopName: String, host: String, port: Int, now: Long, validMillis: Long = LeadTimes.syncInvitationMillis(books.today())): PairingInvitation {
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

    /** SYNC-08: the phone can no longer send or receive anything; it must be paired again. Its owner or an administrator. */
    fun revoke(deviceId: String, now: Long) {
        manageable(deviceId, ownerOnly = false)
        books.core.revokeDevice(now, deviceId)
        books.session.audit("REVOKE", "device", deviceId)
    }

    /** Removes a phone from the list; its owner or an administrator. */
    fun forget(deviceId: String) {
        manageable(deviceId, ownerOnly = false)
        books.core.deleteDevice(deviceId)
        books.session.audit("DELETE", "device", deviceId)
    }

    /**
     * Renames the phone and chooses the group its captures, trips and places go to: only its owner
     * (HH-11: another user could otherwise send them to a group they can read), and only a group the
     * owner may add to.
     */
    fun update(deviceId: String, name: String, groupId: String?) {
        manageable(deviceId, ownerOnly = true)
        validate(name.isNotBlank(), "error.nameRequired")
        groupId?.let { books.require(books.group(it), PermissionLevel.CAPTURE_ONLY) }
        books.core.renameDevice(name.trim(), groupId, deviceId)
    }

    /** Refuses unless the signed-in user owns the phone [deviceId], or is an administrator when not [ownerOnly]. */
    private fun manageable(deviceId: String, ownerOnly: Boolean) {
        val device = books.core.deviceById(deviceId).executeAsOneOrNull() ?: throw ca.schippers.hfm.data.AccessDeniedException("Phone not found")
        if (device.user_id != books.userId && (ownerOnly || books.role != ca.schippers.hfm.domain.Role.ADMINISTRATOR)) {
            throw ca.schippers.hfm.data.AccessDeniedException("Only the phone's owner can do this")
        }
    }

    /**
     * Opens a phone's sealed request, stores each new item, and returns the sealed answer: the
     * items stored, those that failed, and the reference data when it changed.
     */
    fun handle(deviceId: String, sealed: ByteArray, converter: CaptureConverter, now: Long, today: LocalDate): ByteArray =
        process(deviceId, sealed, converter, now, today).first

    /**
     * [handle], also giving how many items were new. With [confirmRecent], the answer also confirms
     * every item received from this phone in the last [RECENT_CONFIRM_MS] (a file brought in by
     * hand had no reply the phone could collect, and the phone does not put those items in the
     * folder again).
     */
    private fun process(deviceId: String, sealed: ByteArray, converter: CaptureConverter, now: Long, today: LocalDate, confirmRecent: Boolean = false): Pair<ByteArray, Int> {
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
                    // CAP-08: the spoken note is kept beside the document it belongs to.
                    val voice = item.voice
                    if (voice != null && documentId != null) runCatching { books.documents.attachVoice(documentId, SyncCrypto.unb64(voice), deviceId) }
                    books.core.insertSyncItem(item.id, deviceId, item.kind.name, now, documentId, "IMPORTED")
                    imported += item.id
                    added++
                }
                .onFailure { failed += failure(item.id, it) }
        }
        // CON-07: contacts made on the phone wait for review; a contact already received is acknowledged again.
        for (contact in request.contacts.take(MAX_ITEMS)) {
            if (books.core.syncItemById(contact.id).executeAsOneOrNull() != null) {
                imported += contact.id
                continue
            }
            runCatching { books.phoneContacts.receive(device.group_id ?: defaultGroup() ?: throw ValidationException("error.noEditableGroup"), deviceId, contact, now) }
                .onSuccess {
                    books.core.insertSyncItem(contact.id, deviceId, CaptureKind.CONTACT.name, now, null, "IMPORTED")
                    imported += contact.id
                    added++
                }
                .onFailure { failed += failure(contact.id, it) }
        }
        // SEA-04: tasks ticked in the seasonal checklist on the phone go into the service log.
        for (done in request.tasksDone.take(MAX_ITEMS)) {
            if (books.core.syncItemById(done.id).executeAsOneOrNull() != null) {
                imported += done.id
                continue
            }
            runCatching { recordTaskDone(done, today) }
                .onSuccess {
                    books.core.insertSyncItem(done.id, deviceId, CaptureKind.TASK_DONE.name, now, null, "IMPORTED")
                    imported += done.id
                    added++
                }
                .onFailure { failed += failure(done.id, it) }
        }
        // CSY-02: each calendar brought in replaces the copy kept; a snapshot already stored is acknowledged again.
        for (snapshot in request.calendars.take(MAX_CALENDARS)) {
            runCatching { books.broughtIn.receive(deviceId, snapshot, now) }
                .onSuccess { imported += snapshot.id }
                .onFailure { failed += failure(snapshot.id, it) }
        }
        // UTL-01, UTL-02, HRS-01, CHO-01, VOL-01: readings, hours, chores and volunteer hours are stored as they come.
        for (tracker in request.trackers.take(MAX_ITEMS)) {
            if (books.core.syncItemById(tracker.id).executeAsOneOrNull() != null) {
                imported += tracker.id
                continue
            }
            runCatching { books.trackerSync.receive(tracker, device.group_id ?: defaultGroup(), deviceId) }
                .onSuccess {
                    books.core.insertSyncItem(tracker.id, deviceId, CaptureKind.TRACKER.name, now, null, "IMPORTED")
                    imported += tracker.id
                    added++
                }
                .onFailure { failed += failure(tracker.id, it) }
        }
        // TRP-02, TRP-01, TRP-05: places first, so the trips and fill-ups that name them find them.
        val group = device.group_id ?: defaultGroup()
        val travel = request.places.take(MAX_ITEMS).map { Triple(it.changeId, CaptureKind.PLACE) { g: String -> books.places.receive(g, deviceId, it) } } +
            request.trips.take(MAX_ITEMS).map { Triple(it.id, CaptureKind.TRIP) { g: String -> books.trips.receive(g, deviceId, it) } } +
            request.fuel.take(MAX_ITEMS).map { Triple(it.id, CaptureKind.FUEL) { _: String -> receiveFuel(deviceId, it) } }
        for ((id, kind, store) in travel) {
            if (books.core.syncItemById(id).executeAsOneOrNull() != null) {
                imported += id
                continue
            }
            runCatching { store(group ?: throw ValidationException("error.noEditableGroup")) }
                .onSuccess {
                    books.core.insertSyncItem(id, deviceId, kind.name, now, null, "IMPORTED")
                    imported += id
                    added++
                }
                .onFailure { failed += failure(id, it) }
        }
        books.core.deviceSeen(now, added.toLong(), deviceId)
        if (confirmRecent) {
            imported += books.core.syncItemsForDevice(deviceId, MAX_RECENT_CONFIRM).executeAsList()
                .filter { it.received_at >= now - RECENT_CONFIRM_MS && it.status == "IMPORTED" }.map { it.item_id }
        }
        val reference = reference(today, now)
        val version = version(reference)
        val response = SyncResponse(imported.distinct(), failed, version, reference.takeIf { version != request.referenceVersion })
        return SyncCrypto.seal(SyncResponse.serializer(), response, key, desktopId, deviceId, Direction.TO_PHONE) to added
    }

    /**
     * Section 3.2: a phone's request that came as a file (a cloud folder, email or USB). Stores its
     * items as [handle] does and returns the reply as a file, with its name, for the phone to read
     * when it next looks in the folder. The reply also confirms the items of files imported by hand
     * in the last 60 days (section 3.1), so the phone hears about them through the folder too.
     * @throws BundleFile.NotABundleException when the file is not a request; [NotThisHouseholdException]
     * when it is for another household (several may share a folder).
     */
    fun handleFile(bytes: ByteArray, converter: CaptureConverter, now: Long, today: LocalDate): FileReply {
        val (header, sealed) = BundleFile.read(bytes)
        if (header.direction != Direction.TO_DESKTOP) throw BundleFile.NotABundleException()
        if (header.desktopId != desktopId) throw NotThisHouseholdException()
        val (answer, added) = process(header.deviceId, sealed, converter, now, today, confirmRecent = true)
        val reply = BundleFile.Header(desktopId, header.deviceId, Direction.TO_PHONE, now)
        return FileReply(BundleFile.name(reply), BundleFile.write(reply, answer), header.deviceId, added)
    }

    /** SEA-04: a task ticked on the phone, recorded as done in its vehicle's or asset's service log. */
    private fun recordTaskDone(done: PhoneTaskDone, today: LocalDate) {
        val date = runCatching { LocalDate.parse(done.date) }.getOrElse { throw ValidationException("error.invalidDate") }
        validate(date <= today.plus(DatePeriod(days = 1)), "error.invalidDate")
        books.seasonal.record(done.vehicle, done.subjectId, done.taskId, date, phoneText(done.note), phoneDecimal(done.cost), done.reading)
    }

    /** Stores one captured item; returns the document it became, if any. */
    private fun receive(groupId: String?, deviceId: String, item: CaptureItem, converter: CaptureConverter, today: LocalDate): String? {
        val f = item.fields
        // Contacts travel apart from captures (SyncRequest.contacts); this kind only marks them in the phone's queue.
        require(item.kind != CaptureKind.CONTACT) { "A contact is not a capture" }
        if (item.kind == CaptureKind.METER_READING) {
            // MNT-03: a reading is a fact, not a document to review.
            val vehicle = f.vehicleId ?: throw ValidationException("error.vehicleRequired")
            val date = f.date?.let(LocalDate::parse) ?: today
            val value = f.odometer ?: throw ValidationException("error.invalidNumber")
            // The id is a vehicle's, or that of another asset with a meter.
            if (books.vehicles.list(includeInactive = true).any { it.id == vehicle }) {
                books.vehicles.addReading(vehicle, date, value, f.note)
            } else {
                books.assetMaintenance.addReading(vehicle, date, value, f.note)
            }
            return null
        }
        val group = groupId ?: throw ValidationException("error.noEditableGroup")
        val pages = item.pages.map(SyncCrypto::unb64)
        val pdf = item.pdf
        val (content, mime) = when {
            pdf != null -> SyncCrypto.unb64(pdf) to "application/pdf"
            pages.size > 1 -> converter.pagesToPdf(pages) to "application/pdf"
            pages.size == 1 -> pages.single() to "image/jpeg"
            // CAP-05: an email or other text shared to the phone, kept as a text document.
            !item.text.isNullOrBlank() -> item.text!!.take(MAX_SHARED_TEXT).encodeToByteArray() to "text/plain"
            // CAP-07: a quick expense with no photo is kept as a short note to review.
            else -> listOfNotNull(f.merchant, f.date, f.amount, f.note).joinToString("\n").ifBlank { item.kind.name }.encodeToByteArray() to "text/plain"
        }
        val doc = books.documents.import(group, content, item.fileName ?: "${item.kind.name.lowercase()}-${item.id.take(8)}", mime, deviceId).document
        val ocr = when {
            item.ocrLines.isNotEmpty() -> OcrResult(item.ocrLines.map { OcrLine(it.text, it.confidence) }, 0) to (if (item.text != null) "text" else "mlkit")
            mime != "text/plain" -> converter.recognize(content)?.let { it to "desktop" }
            else -> null
        }
        ocr?.let { (result, engine) -> books.documents.recordText(doc.id, maxOf(1, pages.size), result, engine, today) }
        // What the person typed on the phone wins over what was read.
        val read = books.documents.get(doc.id)
        val amount = f.amount?.let { a -> runCatching { Money.exact(phoneDecimal(a)!!, f.currency?.let(Currency::of) ?: read.amount?.currency ?: books.rates.baseCurrency) }.getOrNull() }
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
        // CAP-07: Paid with, Category and For, offered first when the document is filed.
        books.documents.recordChoices(doc.id, CaptureChoices(f.accountId, f.categoryId, f.memberId))
        return doc.id
    }

    /** TRP-05, TRP-10: a fill-up or charge entered on the phone, in the vehicle's currency. */
    private fun receiveFuel(deviceId: String, f: PhoneFuel) {
        val vehicle = books.vehicles.get(f.vehicleId)
        // Kept under the phone's own id: received again (the answer was lost, or the computer stopped
        // before noting it), it is the same fill-up.
        validate(f.id.isNotBlank() && f.id.length <= MAX_ID, "error.invalidNumber")
        if (books.vehicles.fuel(vehicle.id).any { it.id == f.id }) return
        val quantity = phoneDecimal(f.quantity) ?: throw ValidationException("error.fuelQuantity")
        val cost = phoneDecimal(f.cost)?.let { Money.of(it, vehicle.currency) }
        val place = f.placeId?.let { books.places.find(it) }
        books.vehicles.saveFuel(
            FuelEntry(
                "", vehicle.id, runCatching { LocalDate.parse(f.date) }.getOrElse { throw ValidationException("error.invalidDate") }, f.odometer, quantity, cost, f.fullTank,
                place?.name ?: f.station?.trim()?.take(120)?.ifEmpty { null },
                energy = Energy.entries.firstOrNull { it.name == f.energy }, charging = f.charging?.let { c -> Charging.entries.firstOrNull { it.name == c } },
                placeId = place?.id, deviceId = deviceId,
            ),
            newId = f.id,
        )
    }

    /** SYNC-06, RPT-06, BILL-04: what the phone shows and picks from. */
    fun reference(today: LocalDate, now: Long): ReferenceData {
        val base = books.rates.baseCurrency
        val budgets = runCatching { books.budgets.month(LocalDate(today.year, today.month, 1)) }.getOrNull()
        return ReferenceData(
            householdName = books.core.household().executeAsOne().name,
            // M-79: the language the desktop is shown in, which the app sets on the books.
            language = books.language.tag,
            baseCurrency = base.code,
            accounts = books.accounts.list().map { RefAccount(it.account.id, it.account.name, it.account.type.name, it.account.currency.code, it.balance.toBigDecimal().toPlainString()) },
            categories = books.categories.list().map { RefCategory(it.id, it.parentId, it.nameEn, it.nameFr, it.kind == CategoryKind.INCOME) },
            payees = books.payees.list().take(MAX_PAYEES).map { RefPayee(it.name, it.defaultCategoryId) },
            people = books.members.list().map { RefPerson(it.id, it.displayName, false) } + books.pets.list().map { RefPerson(it.id, it.name, true) },
            vehicles = books.vehicles.list().map { RefVehicle(it.id, it.name, books.vehicles.latestOdometer(it.id)?.odometer, fuelType = it.fuelType.name, use = it.usage.name) } +
                books.assets.list().mapNotNull { a -> a.meter?.let { RefVehicle(a.id, a.name, books.assetMaintenance.latestUsage(a.id), it.name) } },
            bills = books.bills.occurrences(today, today.plus(DatePeriod(days = 60))).filter { it.status == OccurrenceStatus.DUE && it.bill.kind == BillKind.BILL }.map {
                RefBill(it.bill.name, it.dueDate.toString(), it.amount.toBigDecimal().toPlainString(), it.amount.currency.code, !it.amountKnown, it.bill.reminderDays)
            },
            budgets = budgets?.lines.orEmpty().filter { it.category.kind == CategoryKind.EXPENSE }.map {
                RefBudget(
                    it.category.name(ca.schippers.hfm.i18n.Language.ENGLISH), it.budgeted.toBigDecimal().toPlainString(), it.actual.toBigDecimal().toPlainString(),
                    it.budgeted.currency.code, it.category.id,
                )
            },
            maintenance = maintenance(today),
            generatedAtMillis = now,
            contacts = runCatching { books.phoneContacts.forPhone() }.getOrDefault(emptyList()),
            events = runCatching { events(today) }.getOrDefault(emptyList()),
            refills = runCatching { refills(today) }.getOrDefault(emptyList()),
            places = runCatching { books.places.forPhone() }.getOrDefault(emptyList()),
            trailers = books.assets.list().filter { it.kind == AssetKind.TRAILER }.map { RefTrailer(it.id, it.name) },
            userMemberId = runCatching { books.users.list().firstOrNull { it.isMe }?.memberId }.getOrNull(),
            schedules = runCatching { schedules(today) }.getOrDefault(emptyList()),
            trackers = books.trackerSync.reference(today),
            seasonal = runCatching { seasonal(today) }.getOrNull(),
            renewals = runCatching { renewals(today) }.getOrDefault(emptyList()),
            maintenanceAhead = runCatching { maintenanceAhead(today) }.getOrDefault(emptyList()),
        )
    }

    /**
     * The agenda: what is to be renewed within [AGENDA_DAYS] days, as the computer's calendar shows it (card payments
     * on every due date of the period), from the groups the signed-in user can see (HH-11). Policy numbers, plates and
     * amounts stay on the computer.
     */
    private fun renewals(today: LocalDate): List<RefRenewal> {
        val until = today.plus(DatePeriod(days = AGENDA_DAYS - 1))
        return (books.renewals(today, AGENDA_DAYS - 1, cardPayments = false) + books.creditCards.paymentsDue(today, until, today))
            .filter { it.date in today..until }.sortedBy { it.date }.take(MAX_EVENTS)
            .map { RefRenewal(it.kind.name, it.subjectId, it.subjectName, it.date.toString()) }
    }

    /** The agenda: maintenance next due after this month and within [AGENDA_DAYS] days; [maintenance] sends the rest. */
    private fun maintenanceAhead(today: LocalDate): List<RefDue> {
        val from = endOfMonth(today).plus(DatePeriod(days = 1))
        val until = today.plus(DatePeriod(days = AGENDA_DAYS - 1))
        if (from > until) return emptyList()
        return books.upkeepBetween(from, until, today).take(MAX_DUE).map { u ->
            RefDue(u.taskId, u.subjectName, u.taskName, u.status.state.name, u.status.nextDate?.toString(), u.status.dueUsage, u.unit?.name)
        }
    }

    private fun endOfMonth(today: LocalDate) = LocalDate(today.year, today.month, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

    /** SEA-04: the current season's checklist, without the tasks of vehicles and assets the user may only view. */
    private fun seasonal(today: LocalDate): RefSeasonal {
        val c = books.seasonal.checklist(Seasons.windowOf(today), today)
        val may = HashMap<Pair<Boolean, String>, Boolean>()
        return RefSeasonal(
            c.window.season.name, c.window.start.toString(), c.window.end.toString(),
            c.items.filter { may.getOrPut(it.vehicle to it.subjectId) { books.seasonal.mayTick(it.vehicle, it.subjectId) } }.take(MAX_SEASONAL).map {
                RefSeasonalTask(it.taskId, it.subjectId, it.subjectName, it.taskName, it.vehicle, it.state.name, it.dueDate?.toString(), it.doneOn?.toString(), it.unit?.name, it.currency.code, it.again?.toString())
            },
        )
    }

    private fun names(): Map<String, String> =
        runCatching { books.members.list(includeArchived = true).associate { it.id to it.displayName } }.getOrDefault(emptyMap()) +
            runCatching { books.pets.list(includeArchived = true).associate { it.id to it.name } }.getOrDefault(emptyMap())

    /**
     * CAL-03: the coming events (not done or cancelled) with their reminder lead times, from the
     * groups the signed-in user can see (HH-11): another user's private events never reach this phone.
     */
    private fun events(today: LocalDate): List<RefEvent> {
        val who = names()
        return books.calendar.occurrences(today, today.plus(DatePeriod(days = EVENT_DAYS))).filter { it.mark == null }.take(MAX_EVENTS).map { o ->
            val e = o.event
            RefEvent(
                "${e.id}|${o.date}", e.title, o.date.toString(), e.startTime?.let { "%02d:%02d".format(it.hour, it.minute) }, e.category.name,
                e.location, e.memberId?.let(who::get), e.reminderMinutes,
                o.driverThere?.let { it.name ?: who[it.memberId] }, o.driverBack?.let { it.name ?: who[it.memberId] },
            )
        }
    }

    /**
     * CAL-10: each person's work and school hours for the agenda's [AGENDA_DAYS] days (older phones show today's and
     * tomorrow's), from the groups the signed-in user can see.
     */
    private fun schedules(today: LocalDate): List<RefSchedule> {
        val who = names()
        return books.schedules.days(today, today.plus(DatePeriod(days = AGENDA_DAYS - 1))).take(MAX_SCHEDULES).mapNotNull { d ->
            val person = who[d.memberId] ?: return@mapNotNull null
            RefSchedule(person, d.schedule.kind.name, d.date.toString(), hhmm(d.start), hhmm(d.end), d.schedule.label)
        }
    }

    private fun hhmm(t: kotlinx.datetime.LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    /** HLT-03: active medications running out within [EVENT_DAYS] days, or already out. */
    private fun refills(today: LocalDate): List<RefRefill> {
        val who = names()
        val until = today.plus(DatePeriod(days = EVENT_DAYS))
        return books.health.medications().filter { it.active }.mapNotNull { m ->
            val due = m.nextRefill?.takeIf { it <= until } ?: return@mapNotNull null
            RefRefill(m.id, m.name, due.toString(), m.refillReminderDays, who[m.memberId], m.needsRenewal)
        }.sortedBy { it.dueDate }.take(MAX_EVENTS)
    }

    /** MNT-05: overdue and soon due, and anything else next due by the end of the month. */
    private fun maintenance(today: LocalDate): List<RefDue> {
        return (books.upkeepDue(today) + books.upkeepBetween(today, endOfMonth(today), today)).distinctBy { it.taskId }.take(MAX_DUE).map { u ->
            RefDue(u.taskId, u.subjectName, u.taskName, u.status.state.name, u.status.nextDate?.toString(), u.status.dueUsage, u.unit?.name)
        }
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
        private const val MAX_ITEMS = 50
        private const val MAX_CALENDARS = 40

        /** The longest id of an entry made on the phone (they are UUIDs). */
        private const val MAX_ID = 64

        /** Section 3.1: how far back a reply file confirms items received from the phone, as long as replies are kept in the folder. */
        const val RECENT_CONFIRM_MS = 60L * 24 * 3600 * 1000
        private const val MAX_RECENT_CONFIRM = 1000L
        private const val MAX_PAYEES = 400
        private const val MAX_DUE = 50
        private const val MAX_SEASONAL = 200

        /** Events and refills up to two months ahead: the longest reminder lead time the computer allows. */
        private const val EVENT_DAYS = 61
        private const val MAX_EVENTS = 150

        /** The phone's agenda: today and the 59 days after it (events and refills are sent a day further, for reminders). */
        private const val AGENDA_DAYS = 60
        private const val MAX_SCHEDULES = 600

        /** CAP-05: a shared text longer than this (a very long email thread) is cut. */
        private const val MAX_SHARED_TEXT = 200_000
    }
}

/** SYNC-09: why an item was refused, in English and French: the rule it broke, else a general reason. */
internal fun failure(id: String, error: Throwable): SyncFailure = when (error) {
    is ValidationException -> SyncFailure(id, error.message(Language.ENGLISH), error.message(Language.FRENCH))
    is ca.schippers.hfm.data.AccessDeniedException -> SyncFailure(id, Messages.get(Language.ENGLISH, "sync.failed.access"), Messages.get(Language.FRENCH, "sync.failed.access"))
    else -> SyncFailure(id, Messages.get(Language.ENGLISH, "sync.failed.other"), Messages.get(Language.FRENCH, "sync.failed.other"))
}
