package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.DecryptionException
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.BundleFile
import ca.schippers.hfm.sync.CaptureFields
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.OcrText
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PairedDesktop
import ca.schippers.hfm.sync.PairingInvitation
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 3 on the desktop side, driven the way a phone drives it. */
class SyncServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private val today = LocalDate(2026, 10, 2)
    private val now = 1_790_000_000_000L
    private val phone = KeyPair.generate()

    private val converter = object : CaptureConverter {
        var pdfCalls = 0
        override fun pagesToPdf(pages: List<ByteArray>): ByteArray { pdfCalls++; return "%PDF-1.7 ${pages.size} pages".encodeToByteArray() }
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("S.hfm"), "Famille S", "perry", "Perry", "pw".toCharArray()).session)
        books.accounts.create(AccountDraft(books.groups().single().id, "Visa", AccountType.CREDIT_CARD, Currency.CAD, Money.parse("0", Currency.CAD), LocalDate(2026, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    /** What the phone does after scanning the QR code. */
    private fun pairAsPhone(invitation: PairingInvitation, deviceId: String = "pixel-8"): ByteArray {
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        val response = books.sync.pair(
            PairRequest(deviceId, "Pixel de Perry", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)),
            now,
        )
        val pairKey = PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
        assertTrue(SyncCrypto.sameProof(response.proof, SyncCrypto.desktopProof(pairKey, invitation.desktopId, deviceId)), "the phone can verify the desktop")
        assertEquals("Famille S", response.householdName)
        return pairKey
    }

    private fun send(pairKey: ByteArray, request: SyncRequest, deviceId: String = "pixel-8"): SyncResponse {
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, pairKey, books.sync.desktopId, deviceId, Direction.TO_DESKTOP)
        val answer = books.sync.handle(deviceId, sealed, converter, now, today)
        return SyncCrypto.open(SyncResponse.serializer(), answer, pairKey, books.sync.desktopId, deviceId, Direction.TO_PHONE)
    }

    private val receipt = CaptureItem(
        "item-1", CaptureKind.RECEIPT, now, pages = listOf(SyncCrypto.b64("jpeg-bytes".encodeToByteArray())),
        ocrLines = listOf(OcrText("Metro Plus", 0.98f), OcrText("TOTAL 23,45", 0.97f), OcrText("2026-09-30", 0.97f)),
    )

    @Test
    fun `a spoken note comes with its capture and is kept beside it`() {
        val key = pairAsPhone(books.sync.invitation("Bureau", "127.0.0.1", 47311, now))
        val wav = "RIFF....WAVEfmt fake".encodeToByteArray()
        val withVoice = receipt.copy(voice = SyncCrypto.b64(wav), fields = CaptureFields(note = "Lunch with a client, split with Paul"))
        assertEquals(listOf("item-1"), send(key, SyncRequest(now, listOf(withVoice))).imported)
        val doc = books.documents.inbox().single()
        val voice = books.documents.voiceNotes(doc.id).single()
        assertEquals("audio/wav", voice.mimeType)
        assertTrue(books.documents.content(voice.id).contentEquals(wav))
        assertEquals("Lunch with a client, split with Paul", doc.notes, "the dictated words are the note")
    }

    @Test
    fun `a request can come as a file, and the reply goes back as one`() {
        val key = pairAsPhone(books.sync.invitation("Bureau", "127.0.0.1", 47311, now))
        val desktop = PairedDesktop(books.sync.desktopId, "Famille S", "127.0.0.1", 47311, "pixel-8", SyncCrypto.b64(key))
        val (name, file) = BundleFile.request(desktop, SyncRequest(now, listOf(receipt)), now)
        assertTrue(name.startsWith("to-desktop-pixel8-") && name.endsWith(".roostsync"), name)

        val first = books.sync.handleFile(file, converter, now, today)
        val reply = first.bytes
        assertTrue(first.name.startsWith("to-phone-pixel8-"), first.name)
        assertEquals(1, first.received)
        val answer = BundleFile.reply(desktop, reply)
        assertEquals(listOf("item-1"), answer.imported)
        assertEquals(1, books.documents.inbox().size)
        // The same file again (a folder synced twice, an email opened twice) imports nothing new.
        val again = books.sync.handleFile(file, converter, now, today)
        assertEquals(listOf("item-1"), BundleFile.reply(desktop, again.bytes).imported)
        assertEquals(0, again.received)
        assertEquals(1, books.documents.inbox().size)

        assertFailsWith<BundleFile.NotABundleException> { books.sync.handleFile("hello".encodeToByteArray(), converter, now, today) }
        assertFailsWith<BundleFile.NotABundleException>("a reply is not a request") { books.sync.handleFile(reply, converter, now, today) }
        val other = BundleFile.request(desktop.copy(desktopId = "another-household"), SyncRequest(now, listOf(receipt)), now).second
        assertFailsWith<NotThisHouseholdException> { books.sync.handleFile(other, converter, now, today) }
        // Changing the header to pass as another phone makes the body fail to open.
        val forged = String(file, Charsets.ISO_8859_1).replace("\"pixel-8\"", "\"tablet\"").toByteArray(Charsets.ISO_8859_1)
        assertFailsWith<DeviceNotPairedException> { books.sync.handleFile(forged, converter, now, today) }
        assertFailsWith<BundleFile.NotABundleException>("a reply for another phone is refused") { BundleFile.reply(desktop.copy(deviceId = "tablet"), reply) }
    }

    @Test
    fun `pairing needs the code from the QR code`() {
        val invitation = books.sync.invitation("Bureau", "192.168.1.20", 47311, now)
        assertEquals(invitation, PairingInvitation.fromQrText(invitation.toQrText()), "the QR text round-trips")
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        assertFailsWith<PairingRejectedException> {
            books.sync.pair(PairRequest("x", "Intrus", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof("deadbeef0000", phone.publicKey, desktopKey)), now)
        }
        pairAsPhone(invitation)
        assertFailsWith<PairingRejectedException>("a code works once") { pairAsPhone(invitation, "second") }
        val late = books.sync.invitation("Bureau", "192.168.1.20", 47311, now)
        assertFailsWith<PairingRejectedException>("and only for ten minutes") {
            books.sync.pair(PairRequest("y", "Late", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(late.oneTimeCode, phone.publicKey, desktopKey)), now + 11 * 60_000)
        }
        assertEquals(listOf("pixel-8"), books.sync.devices().map { it.id })
    }

    @Test
    fun `items arrive once in the review inbox, with the reference data`() {
        val key = pairAsPhone(books.sync.invitation("Bureau", "127.0.0.1", 47311, now))
        val first = send(key, SyncRequest(now, listOf(receipt)))
        assertEquals(listOf("item-1"), first.imported)
        val inbox = books.documents.inbox().single()
        assertEquals("Metro Plus", inbox.merchant)
        assertEquals(Money.parse("23.45", Currency.CAD), inbox.amount)
        assertEquals("pixel-8", inbox.sourceDevice)
        val reference = assertNotNull(first.reference)
        assertEquals(listOf("Visa"), reference.accounts.map { it.name })
        assertTrue(reference.categories.isNotEmpty())

        // SYNC-02, SYNC-04: sent again (the acknowledgement was lost), acknowledged but not imported twice.
        val again = send(key, SyncRequest(now, listOf(receipt), first.referenceVersion))
        assertEquals(listOf("item-1"), again.imported)
        assertEquals(1, books.documents.inbox().size)
        assertNull(again.reference, "unchanged reference data is not sent again")
        assertEquals(1, books.sync.devices().single().itemsReceived)
    }

    @Test
    fun `typed fields, several pages, quick expenses and meter readings`() {
        val key = pairAsPhone(books.sync.invitation("Bureau", "127.0.0.1", 47311, now))
        val group = books.groups().single().id
        val civic = books.vehicles.save(Vehicle("", group, "Civic"))
        // MNT-03, MNT-05: a boat with an hour meter, and a task that is overdue.
        val boat = books.assets.save(Asset("", group, AssetKind.BOAT, "Ponton", meter = MeterUnit.HOURS))
        books.assetMaintenance.saveTask(AssetTask("", boat.id, "Vidange", intervalMonths = 12, startDate = LocalDate(2024, 5, 1)))
        val response = send(
            key,
            SyncRequest(
                now,
                listOf(
                    receipt.copy(id = "typed", fields = CaptureFields(merchant = "Metro", amount = "24.00", date = "2026-10-01", note = "Souper")),
                    CaptureItem("bill", CaptureKind.BILL, now, pages = listOf("p1", "p2").map { SyncCrypto.b64(it.encodeToByteArray()) }),
                    CaptureItem("quick", CaptureKind.QUICK_EXPENSE, now, fields = CaptureFields(merchant = "Stationnement", amount = "6.50")),
                    CaptureItem("odo", CaptureKind.METER_READING, now, fields = CaptureFields(vehicleId = civic.id, odometer = 61_250)),
                    CaptureItem("hours", CaptureKind.METER_READING, now, fields = CaptureFields(vehicleId = boat.id, odometer = 120)),
                    CaptureItem("bad", CaptureKind.METER_READING, now, fields = CaptureFields(odometer = 1)),
                ),
            ),
        )
        assertEquals(listOf("typed", "bill", "quick", "odo", "hours"), response.imported)
        assertEquals(listOf("bad"), response.failed.map { it.id })
        val typed = books.documents.inbox().first { it.notes == "Souper" }
        assertEquals("Metro", typed.merchant)
        assertEquals(Money.parse("24.00", Currency.CAD), typed.amount, "what the person typed wins over what was read")
        assertEquals(LocalDate(2026, 10, 1), typed.date)
        assertEquals(1, converter.pdfCalls, "two pages became one PDF (CAP-02)")
        assertEquals("application/pdf", books.documents.inbox().first { it.kind == DocumentKind.BILL }.mimeType)
        assertEquals(Money.parse("6.50", Currency.CAD), books.documents.inbox().first { it.merchant == "Stationnement" }.amount)
        assertEquals(61_250, books.vehicles.latestOdometer(civic.id)?.odometer)
        assertEquals(120, books.assetMaintenance.latestUsage(boat.id))
        val reference = assertNotNull(response.reference)
        assertEquals("HOURS", reference.vehicles.single { it.id == boat.id }.unit)
        assertEquals("DUE", reference.maintenance.single { it.taskId.isNotEmpty() && it.subject == "Ponton" }.state)
    }

    @Test
    fun `Paid with, Category and For chosen on the phone are kept for filing`() {
        val key = pairAsPhone(books.sync.invitation("Bureau", "127.0.0.1", 47311, now))
        val visa = books.accounts.list().single().account.id
        val groceries = books.categories.list().first { it.kind == ca.schippers.hfm.domain.CategoryKind.EXPENSE && it.parentId != null }.id
        val alex = books.members.create("Alex", ca.schippers.hfm.domain.MemberKind.ADULT).id
        val chosen = CaptureFields(accountId = visa, categoryId = groceries, memberId = alex)
        send(
            key,
            SyncRequest(
                now,
                listOf(
                    receipt.copy(fields = chosen),
                    CaptureItem("quick", CaptureKind.QUICK_EXPENSE, now, fields = chosen.copy(merchant = "Stationnement", amount = "6.50", memberId = null)),
                    receipt.copy(id = "plain", pages = listOf(SyncCrypto.b64("other-jpeg".encodeToByteArray()))),
                ),
            ),
        )
        val inbox = books.documents.inbox()
        val read = inbox.first { it.fileName == "receipt-item-1" }
        assertEquals(CaptureChoices(visa, groceries, alex), read.choices)
        assertNotNull(read.draft, "what the phone read is still there")
        // A capture with no photo has nothing read, and still keeps what was chosen.
        val quick = inbox.first { it.merchant == "Stationnement" }
        assertNull(quick.draft)
        assertEquals(CaptureChoices(visa, groceries, null), quick.choices)
        assertNull(inbox.first { it.fileName == "receipt-plain" }.choices, "nothing chosen, nothing kept")
        // Reading the document again does not lose the choices.
        books.documents.recordText(read.id, 1, OcrResult(listOf(ca.schippers.hfm.ocr.OcrLine("Metro Plus", 0.9f)), 0), "desktop", today)
        assertEquals(CaptureChoices(visa, groceries, alex), books.documents.get(read.id).choices)
    }

    @Test
    fun `a removed phone is refused, and others cannot read or forge bundles`() {
        val key = pairAsPhone(books.sync.invitation("Bureau", "127.0.0.1", 47311, now))
        val stranger = ByteArray(32) { 7 }
        assertFailsWith<DecryptionException> { send(stranger, SyncRequest(now, listOf(receipt))) }
        assertFailsWith<DeviceNotPairedException> { send(key, SyncRequest(now, emptyList()), deviceId = "unknown") }
        books.sync.revoke("pixel-8", now)
        assertFailsWith<DeviceNotPairedException> { send(key, SyncRequest(now, listOf(receipt))) }
        assertTrue(books.sync.devices().single().revoked)
    }
}
