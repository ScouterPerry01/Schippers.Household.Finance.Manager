package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.EventDraft
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.sync.BundleFile
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.OcrText
import ca.schippers.hfm.sync.SyncClient
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncException
import ca.schippers.hfm.sync.SyncRequest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Section 3 end to end: a phone client pairs with the desktop listener over HTTP and sends captures. */
class SyncServerTest {

    @TempDir
    lateinit var temp: Path

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(60, 80, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    @Test
    fun `pair, send, acknowledge, and refuse a removed phone`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val books = Books(store.create(temp.resolve("N.hfm"), "Famille N", "perry", "Perry", "pw".toCharArray()).session)
        var changes = 0
        SyncServer(books, { LocalDate(2026, 10, 2) }) { changes++ }.use { server ->
            server.start()
            val invitation = books.sync.invitation("Bureau", "127.0.0.1", server.port, System.currentTimeMillis())
            val client = SyncClient()
            val desktop = client.pair(invitation, "phone-1", "Pixel")
            assertEquals("Famille N", desktop.householdName)

            val bill = CaptureItem(
                "bill-1", CaptureKind.BILL, System.currentTimeMillis(), pages = listOf(jpeg(), jpeg()).map(SyncCrypto::b64),
                ocrLines = listOf(OcrText("Vidéotron", 0.99f), OcrText("Montant à payer 95,00 \$", 0.98f), OcrText("Date d'échéance : 2026-10-18", 0.98f)),
            )
            val response = client.sync(desktop, SyncRequest(System.currentTimeMillis(), listOf(bill)))
            assertEquals(listOf("bill-1"), response.imported)
            assertNotNull(response.reference)
            val doc = books.documents.inbox().single()
            assertEquals("application/pdf", doc.mimeType, "two pages became one PDF")
            assertEquals("%PDF-", books.documents.content(doc.id).copyOfRange(0, 5).decodeToString())
            assertEquals(LocalDate(2026, 10, 18), doc.draft?.dueDate?.value)
            assertEquals(2, changes, "the screens are told after pairing and after the transfer")

            books.sync.revoke("phone-1", System.currentTimeMillis())
            val refused = assertFailsWith<SyncException> { client.sync(desktop, SyncRequest(System.currentTimeMillis(), emptyList())) }
            assertEquals(SyncException.Reason.REVOKED, refused.reason)
        }
        val gone = assertFailsWith<SyncException> {
            SyncClient(connectTimeoutMillis = 1_000).pair(books.sync.invitation("Bureau", "127.0.0.1", 1, System.currentTimeMillis()), "phone-2", "Other")
        }
        assertEquals(SyncException.Reason.UNREACHABLE, gone.reason)
        books.session.close()
    }

    @Test
    fun `transfer files are received whether or not the listener runs, and say what became of them`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val books = Books(store.create(temp.resolve("F.hfm"), "Famille F", "perry", "Perry", "pw".toCharArray()).session)
        val server = SyncServer(books, { LocalDate(2026, 10, 2) }) {}
        server.start()
        val desktop = SyncClient().pair(books.sync.invitation("Bureau", "127.0.0.1", server.port, System.currentTimeMillis()), "phone-1", "Pixel")
        // Away from home: the listener is off, the file arrives through the folder.
        server.close()
        val item = CaptureItem("r-1", CaptureKind.RECEIPT, System.currentTimeMillis(), pages = listOf(SyncCrypto.b64(jpeg())))
        val (_, file) = BundleFile.request(desktop, SyncRequest(System.currentTimeMillis(), listOf(item)), System.currentTimeMillis())

        val first = server.receiveFile(file)
        assertEquals(TransferOutcome.RECEIVED to 1, first.outcome to first.received)
        assertEquals(listOf("r-1"), BundleFile.reply(desktop, first.reply!!.bytes).imported)
        assertEquals(0, server.receiveFile(file).received, "the same file twice imports once")
        assertEquals(TransferOutcome.NOT_A_BUNDLE, server.receiveFile(jpeg()).outcome)
        assertEquals(TransferOutcome.OTHER_HOUSEHOLD, server.receiveFile(BundleFile.request(desktop.copy(desktopId = "x"), SyncRequest(0, emptyList()), 0).second).outcome)
        books.sync.revoke("phone-1", System.currentTimeMillis())
        assertEquals(TransferOutcome.NOT_PAIRED, server.receiveFile(file).outcome)
        books.session.close()
    }

    @Test
    fun `the phone receives the events it may see, with their reminders`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val file = temp.resolve("C.hfm")
        val today = LocalDate(2026, 10, 5)
        store.create(file, "Famille C", "perry", "Perry", "password1".toCharArray()).session.let { Books(it) }.let { admin ->
            val shared = admin.groups().single().id
            admin.calendar.create(EventDraft(shared, "Dentist", EventCategory.MEDICAL, LocalDate(2026, 10, 6), LocalTime(14, 30), reminderMinutes = listOf(1440, 60)))
            val marie = admin.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            admin.session.setPermission(shared, marie, PermissionLevel.VIEW)
            admin.session.close()
        }
        Books(store.unlock(file, "marie", "password2-long".toCharArray())).let { marie ->
            marie.calendar.create(EventDraft(marie.session.createGroup("Marie - privé", private = true), "Therapist", EventCategory.MEDICAL, LocalDate(2026, 10, 7), LocalTime(9, 0)))
            marie.session.close()
        }
        val books = Books(store.unlock(file, "perry", "password1".toCharArray()))
        SyncServer(books, { today }) {}.use { server ->
            server.start()
            val client = SyncClient()
            val desktop = client.pair(books.sync.invitation("Bureau", "127.0.0.1", server.port, System.currentTimeMillis()), "phone-1", "Pixel")
            val reference = assertNotNull(client.sync(desktop, SyncRequest(System.currentTimeMillis(), emptyList())).reference)
            val dentist = reference.events.single()
            assertEquals(listOf("Dentist", "2026-10-06", "14:30", "MEDICAL"), listOf(dentist.title, dentist.date, dentist.time, dentist.category))
            assertEquals(listOf(1440, 60), dentist.reminderMinutes)
            assertTrue(reference.events.none { it.title == "Therapist" }, "Marie's private event stays off Perry's phone")
        }
        books.session.close()
    }
}
