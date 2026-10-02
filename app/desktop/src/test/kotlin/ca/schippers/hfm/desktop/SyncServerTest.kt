package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Books
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.OcrText
import ca.schippers.hfm.sync.SyncClient
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncException
import ca.schippers.hfm.sync.SyncRequest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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
}
