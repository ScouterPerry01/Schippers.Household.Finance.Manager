package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.BundleFile
import ca.schippers.hfm.sync.CaptureFields
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PairedDesktop
import ca.schippers.hfm.sync.PhoneContact
import ca.schippers.hfm.sync.RefContactDetail
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CON-07: contacts sent to the phone, and contacts made on the phone reviewed on the computer. */
class PhoneContactsTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("P.hfm")
    private val today = LocalDate(2026, 10, 5)
    private val now = 1_790_000_000_000L
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    private fun household(): Books = Books(store.create(dir, "P", "perry", "Perry", "password1".toCharArray()).session)
    private fun marie() = Books(store.unlock(dir, "marie", "password2-long".toCharArray()))
    private fun perry() = Books(store.unlock(dir, "perry", "password1".toCharArray()))

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    private fun pairPhone(books: Books, deviceId: String): ByteArray {
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Bureau", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest(deviceId, "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        return PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
    }

    private fun send(books: Books, deviceId: String, key: ByteArray, request: SyncRequest): SyncResponse {
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, books.sync.desktopId, deviceId, Direction.TO_DESKTOP)
        return SyncCrypto.open(SyncResponse.serializer(), books.sync.handle(deviceId, sealed, noConversion, now, today), key, books.sync.desktopId, deviceId, Direction.TO_PHONE)
    }

    private val plumber = PhoneContact(
        "pc-1", now, "Plomberie Roy", kinds = listOf("CONTRACTOR", "NOT_A_KIND"), purpose = "Water heater",
        phones = listOf(RefContactDetail("418 555-0199", "Cell")), emails = listOf(RefContactDetail("info@roy.example")),
        address = "12 rue Saint-Jean, Québec", notes = "Met at the home show",
    )

    @Test
    fun `the phone receives the contacts its user can see, without numbers or archived ones`() {
        household().use { books ->
            val shared = books.groups().single().id
            val lea = books.members.create("Léa", MemberKind.CHILD).id
            val rbc = books.contacts.save(
                Contact(
                    "", shared, "RBC Royal Bank", kinds = setOf(ContactKind.BANK), purpose = "Chequing",
                    details = listOf(
                        ContactDetail(type = DetailType.PHONE, label = "Branch", value = "613 555-0101"),
                        ContactDetail(type = DetailType.EMAIL, value = "branch@rbc.example"),
                        ContactDetail(type = DetailType.NUMBER, label = "Client card", value = "4519 0000 1111 QXZW"),
                    ),
                    address = "90 Sparks St, Ottawa", website = "rbc.com", hours = "Mon-Fri 9-5", notes = "Ask for Jane",
                ),
            )
            books.contacts.save(Contact("", shared, "Jane Roy", person = true, organizationId = rbc.id, jobTitle = "Advisor", memberIds = setOf(lea)))
            books.contacts.save(Contact("", shared, "Old dentist", kinds = setOf(ContactKind.DENTIST), archived = true))
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(shared, marie, PermissionLevel.VIEW)
        }
        marie().use { m ->
            val own = m.session.createGroup("Marie - privé", private = true)
            m.contacts.save(Contact("", own, "Dr. Private", kinds = setOf(ContactKind.SPECIALIST), details = listOf(ContactDetail(type = DetailType.NUMBER, value = "FILE-1234"))))
            assertEquals(listOf("Dr. Private", "Jane Roy", "RBC Royal Bank"), m.sync.reference(today, now).contacts.map { it.name }.sorted(), "her phone gets hers and the shared ones")
        }
        perry().use { p ->
            val sent = p.sync.reference(today, now).contacts
            assertEquals(listOf("Jane Roy", "RBC Royal Bank"), sent.map { it.name }.sorted(), "not Marie's private contact, nor the archived one")
            val rbc = sent.first { it.name == "RBC Royal Bank" }
            assertEquals(listOf("BANK"), rbc.kinds)
            assertEquals("Chequing", rbc.purpose)
            assertEquals(listOf(RefContactDetail("613 555-0101", "Branch")), rbc.phones)
            assertEquals(listOf(RefContactDetail("branch@rbc.example")), rbc.emails)
            assertEquals(listOf("90 Sparks St, Ottawa", "rbc.com", "Mon-Fri 9-5", "Ask for Jane"), listOf(rbc.address, rbc.website, rbc.hours, rbc.notes))
            val jane = sent.first { it.name == "Jane Roy" }
            assertEquals(rbc.id, jane.organizationId)
            assertEquals("RBC Royal Bank", jane.organizationName)
            assertEquals(listOf("Léa"), jane.forWhom)
            assertEquals(listOf(), rbc.forWhom, "for the whole household")
            // Account and client numbers never leave the computer, not even masked.
            val text = kotlinx.serialization.json.Json.encodeToString(ca.schippers.hfm.sync.ReferenceData.serializer(), p.sync.reference(today, now))
            assertFalse("QXZW" in text || "Client card" in text || "••••" in text, text)
        }
    }

    @Test
    fun `a contact from the phone waits for review once, and is added as a new contact`() = household().use { books ->
        val shared = books.groups().single().id
        val key = pairPhone(books, "pixel")
        val receipt = CaptureItem("item-1", CaptureKind.QUICK_EXPENSE, now, fields = CaptureFields(merchant = "Café", amount = "4.50"))
        val first = send(books, "pixel", key, SyncRequest(now, listOf(receipt), contacts = listOf(plumber)))
        assertEquals(setOf("item-1", "pc-1"), first.imported.toSet())
        assertEquals("Plomberie Roy", books.phoneContacts.waiting().single().contact.name)
        // Sent again (no answer reached the phone), it is acknowledged but not added twice.
        assertEquals(listOf("pc-1"), send(books, "pixel", key, SyncRequest(now, emptyList(), contacts = listOf(plumber))).imported)
        assertEquals(1, books.phoneContacts.count())
        assertTrue(books.contacts.list().isEmpty(), "nothing becomes a contact without review")
        assertEquals(2, books.sync.devices().single().itemsReceived)

        val waiting = books.phoneContacts.waiting().single()
        assertEquals("Pixel", waiting.deviceName)
        assertEquals(shared, waiting.groupId)
        val draft = books.phoneContacts.draft(waiting, shared)
        assertEquals(setOf(ContactKind.CONTRACTOR), draft.kinds, "kinds the computer does not know are left out")
        assertEquals(listOf("Cell" to "418 555-0199", null to "info@roy.example"), draft.details.map { it.label to it.value })
        val added = books.phoneContacts.add(waiting.id, draft.copy(purpose = "Water heater and pipes"))
        assertEquals("Water heater and pipes", added.purpose)
        assertEquals("12 rue Saint-Jean, Québec", added.address)
        assertEquals("Met at the home show", added.notes)
        assertEquals(0, books.phoneContacts.count())
        assertEquals(listOf("Plomberie Roy"), books.contacts.list().map { it.name })
        assertFailsWith<AccessDeniedException> { books.phoneContacts.discard(waiting.id) }
    }

    @Test
    fun `a contact from the phone can be added to an existing contact, or discarded`() = household().use { books ->
        val shared = books.groups().single().id
        val existing = books.contacts.save(
            Contact("", shared, "Plumbing Roy", kinds = setOf(ContactKind.OTHER), details = listOf(ContactDetail(type = DetailType.PHONE, value = "+1 (418) 555-0199")), notes = "Fixed the sink"),
        )
        val rbc = books.contacts.save(Contact("", shared, "RBC Royal Bank", kinds = setOf(ContactKind.BANK)))
        val key = pairPhone(books, "pixel")
        val jane = PhoneContact("pc-2", now, "Jane Roy", person = true, organizationName = "rbc royal bank", phones = listOf(RefContactDetail("613 555-0123")))
        val unknownOrg = PhoneContact("pc-3", now, "Sam Lee", person = true, organizationName = "Desjardins")
        send(books, "pixel", key, SyncRequest(now, emptyList(), contacts = listOf(plumber, jane, unknownOrg)))
        assertEquals(3, books.phoneContacts.count())

        val roy = books.phoneContacts.waiting().first { it.id == "pc-1" }
        assertEquals(listOf(existing.id), books.phoneContacts.duplicates(roy).map { it.id }, "the same phone, written otherwise")
        val merged = books.phoneContacts.merge(roy.id, existing.id)
        assertEquals("Plumbing Roy", merged.name, "the existing contact keeps its name")
        assertEquals(setOf(ContactKind.OTHER, ContactKind.CONTRACTOR), merged.kinds)
        assertEquals(listOf("+1 (418) 555-0199", "info@roy.example"), merged.details.map { it.value }, "the phone already there is not added twice")
        assertEquals("Water heater", merged.purpose)
        assertEquals("Fixed the sink\nMet at the home show", merged.notes)
        assertEquals(2, books.contacts.list().size)

        // A person's organization is found by its name; one the computer does not know goes into the notes.
        val janeWaiting = books.phoneContacts.waiting().first { it.id == "pc-2" }
        assertEquals(rbc.id, books.phoneContacts.draft(janeWaiting, shared).organizationId)
        assertTrue(books.phoneContacts.duplicates(janeWaiting).isEmpty())
        val sam = books.phoneContacts.draft(books.phoneContacts.waiting().first { it.id == "pc-3" }, shared)
        assertNull(sam.organizationId)
        assertEquals("Organization: Desjardins", sam.notes)

        books.phoneContacts.discard("pc-3")
        assertEquals(listOf("pc-2"), books.phoneContacts.waiting().map { it.id })
        assertEquals(2, books.contacts.list().size, "discarding adds nothing")
    }

    @Test
    fun `a member's phone contacts wait in her private group, out of others' sight`() {
        household().use { it.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()) }
        marie().use { m ->
            m.session.createGroup("Marie - privé", private = true)
            val key = pairPhone(m, "marie-phone")
            send(m, "marie-phone", key, SyncRequest(now, emptyList(), contacts = listOf(plumber)))
            val waiting = m.phoneContacts.waiting().single()
            assertEquals(m.groups().first { it.isPrivate }.id, waiting.groupId)
        }
        perry().use { p -> assertEquals(0, p.phoneContacts.count(), "Perry does not see Marie's") }
    }

    @Test
    fun `requests from older phones, without contacts, and as files are still read`() = household().use { books ->
        val key = pairPhone(books, "pixel")
        // What a phone from before contacts sends: no "contacts" field at all.
        val old: JsonObject = buildJsonObject {
            put("sentAtMillis", now)
            put("items", buildJsonArray { })
        }
        val sealed = SyncCrypto.seal(JsonObject.serializer(), old, key, books.sync.desktopId, "pixel", Direction.TO_DESKTOP)
        val answer = SyncCrypto.open(SyncResponse.serializer(), books.sync.handle("pixel", sealed, noConversion, now, today), key, books.sync.desktopId, "pixel", Direction.TO_PHONE)
        assertTrue(answer.imported.isEmpty())
        assertTrue(answer.reference != null)

        // A contact in a transfer file (folder, email or USB) arrives the same way.
        val desktop = PairedDesktop(books.sync.desktopId, "P", "127.0.0.1", 47311, "pixel", SyncCrypto.b64(key))
        val (_, file) = BundleFile.request(desktop, SyncRequest(now, emptyList(), contacts = listOf(plumber)), now)
        val reply = books.sync.handleFile(file, noConversion, now, today)
        assertEquals(1, reply.received)
        assertEquals(listOf("pc-1"), BundleFile.reply(desktop, reply.bytes).imported)
        assertEquals("Plomberie Roy", books.phoneContacts.waiting().single().contact.name)
        // A contact without a name is refused, and stays on the phone with the reason.
        val nameless = send(books, "pixel", key, SyncRequest(now, emptyList(), contacts = listOf(PhoneContact("pc-9", now, " "))))
        assertEquals(listOf("pc-9"), nameless.failed.map { it.id })
    }
}
