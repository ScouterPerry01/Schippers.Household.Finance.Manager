package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.CaptureFields
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.OcrText
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.RefEvent
import ca.schippers.hfm.sync.RefRefill
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** CAL-03 and CAP-05 on the phone: events and refills sent with the reference data, and shared text received. */
class PhoneEventsTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("E.hfm")
    private val today = LocalDate(2026, 10, 5)
    private val now = 1_790_000_000_000L
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    private fun household(): Books = Books(store.create(dir, "E", "perry", "Perry", "password1".toCharArray()).session)
    private fun marie() = Books(store.unlock(dir, "marie", "password2-long".toCharArray()))
    private fun perry() = Books(store.unlock(dir, "perry", "password1".toCharArray()))

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    private fun d(month: Int, day: Int) = LocalDate(2026, month, day)

    @Test
    fun `the phone gets the coming events and refills its user can see, not another user's private ones`() {
        household().use { books ->
            val shared = books.groups().single().id
            val lea = books.members.create("Léa", MemberKind.CHILD)
            books.calendar.create(
                EventDraft(shared, "Dentist", EventCategory.MEDICAL, d(10, 6), LocalTime(14, 30), 60, location = "12 Main St", memberId = lea.id, reminderMinutes = listOf(1440, 60)),
            )
            val weekly = books.calendar.create(EventDraft(shared, "Swimming", EventCategory.PERSONAL, d(10, 7), recurrence = Recurrence(Frequency.WEEKLY), reminderMinutes = listOf(0)))
            books.calendar.mark(weekly.id, d(10, 14), OccurrenceMark.CANCELLED)
            books.calendar.create(EventDraft(shared, "Last year", EventCategory.OTHER, d(9, 1)))
            books.calendar.create(EventDraft(shared, "Far away", EventCategory.OTHER, d(12, 20)))
            books.health.saveMedication(Medication("", shared, lea.id, "Ventolin", "100 mcg", null, null, null, null, null, null, 30, 0, d(9, 12), 5, true, null))
            books.health.saveMedication(Medication("", shared, lea.id, "Old cream", null, null, null, null, null, null, null, 30, 2, d(9, 12), 5, false, null))
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(shared, marie, PermissionLevel.VIEW)
        }
        marie().use { m ->
            val own = m.session.createGroup("Marie - privé", private = true)
            val marieId = m.members.list().first().id
            m.calendar.create(EventDraft(own, "Therapist", EventCategory.MEDICAL, d(10, 8), LocalTime(10, 0), reminderMinutes = listOf(120)))
            m.health.saveMedication(Medication("", own, marieId, "Sertraline", "50 mg", null, null, null, null, null, null, 30, 3, d(9, 20), 7, true, null))
            val ref = m.sync.reference(today, now)
            assertTrue("Therapist" in ref.events.map { it.title } && "Sertraline" in ref.refills.map { it.medication }, "her phone gets her private ones")
        }
        perry().use { p ->
            val ref = p.sync.reference(today, now)
            val dentist = ref.events.first { it.title == "Dentist" }
            assertEquals(
                RefEvent(dentist.id, "Dentist", "2026-10-06", "14:30", "MEDICAL", "12 Main St", "Léa", listOf(1440, 60)),
                dentist,
            )
            assertTrue(dentist.id.endsWith("|2026-10-06"), "one id per occurrence")
            assertEquals(listOf("2026-10-07", "2026-10-21", "2026-10-28"), ref.events.filter { it.title == "Swimming" }.map { it.date }.take(3), "the cancelled week is left out")
            assertEquals(null, ref.events.first { it.title == "Swimming" }.time, "all day")
            val titles = ref.events.map { it.title }.toSet()
            assertTrue("Therapist" !in titles && "Last year" !in titles && "Far away" !in titles, "not Marie's private event, nor past or distant ones: $titles")
            assertEquals(listOf(RefRefill(ref.refills.single().id, "Ventolin", "2026-10-12", 5, "Léa", renewal = true)), ref.refills, "not Marie's, nor an inactive medication")
            assertTrue(ref.events.size <= 150)
        }
    }

    @Test
    fun `an email shared to the phone arrives as a text document with what was read`() {
        household().use { books ->
            val phone = KeyPair.generate()
            val invitation = books.sync.invitation("Bureau", "127.0.0.1", 47311, now)
            val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
            books.sync.pair(PairRequest("phone-1", "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
            val key = PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
            val email = "Your Hydro Ottawa bill\nAmount due: 84.20\nDue date: 2026-10-20"
            val item = CaptureItem(
                "txt-1", CaptureKind.DOCUMENT, now, fileName = "Your Hydro Ottawa bill.txt", ocrLines = email.lines().map { OcrText(it, 1f) },
                fields = CaptureFields(note = "Pay from joint"), text = email,
            )
            val sealed = SyncCrypto.seal(SyncRequest.serializer(), SyncRequest(now, listOf(item)), key, books.sync.desktopId, "phone-1", Direction.TO_DESKTOP)
            val response = SyncCrypto.open(SyncResponse.serializer(), books.sync.handle("phone-1", sealed, noConversion, now, today), key, books.sync.desktopId, "phone-1", Direction.TO_PHONE)
            assertEquals(listOf("txt-1"), response.imported)
            val doc = books.documents.inbox().single()
            assertEquals("text/plain", doc.mimeType)
            assertEquals(email, books.documents.content(doc.id).decodeToString(), "the email itself, not the note")
            assertEquals("Pay from joint", books.documents.get(doc.id).notes)
            assertNotNull(doc.draft)
            assertNotNull(response.reference)
        }
    }
}
