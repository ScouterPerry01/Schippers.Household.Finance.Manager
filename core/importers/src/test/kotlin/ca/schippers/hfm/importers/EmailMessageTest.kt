package ca.schippers.hfm.importers

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CAP-06: e-receipts saved as .eml files. */
class EmailMessageTest {

    private val pdf = "%PDF-1.4 receipt".toByteArray()

    @Test
    fun `a receipt with a PDF attached gives the attachment and the text`() {
        val eml = """
            From: =?UTF-8?Q?Soci=C3=A9t=C3=A9_des_alcools?= <recu@saq.com>
            Subject: =?UTF-8?B?${Base64.getEncoder().encodeToString("Votre reçu".toByteArray())}?=
            Date: Sat, 3 Oct 2026 14:12:00 -0400
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary="XYZ"

            --XYZ
            Content-Type: text/plain; charset=UTF-8
            Content-Transfer-Encoding: quoted-printable

            Merci! Total : 45,67 =24
            Re=C3=A7u no 1234
            --XYZ
            Content-Type: application/pdf; name="recu.pdf"
            Content-Disposition: attachment; filename*=UTF-8''re%C3%A7u%20SAQ.pdf
            Content-Transfer-Encoding: base64

            ${Base64.getEncoder().encodeToString(pdf)}
            --XYZ--
        """.trimIndent().replace("\n", "\r\n")
        val m = EmailMessage.parse(eml.toByteArray(Charsets.ISO_8859_1))
        assertEquals("Société des alcools <recu@saq.com>", m.from)
        assertEquals("Votre reçu", m.subject)
        assertEquals("Merci! Total : 45,67 $\r\nReçu no 1234", m.text)
        assertEquals(listOf("reçu SAQ.pdf" to "application/pdf"), m.attachments.map { it.fileName to it.mimeType })
        assertTrue(m.attachments.single().content.contentEquals(pdf))
    }

    @Test
    fun `an HTML-only receipt becomes its visible text`() {
        val eml = """
            From: orders@example.ca
            Subject: Your order
            Content-Type: multipart/alternative; boundary=b1

            --b1
            Content-Type: text/html; charset=UTF-8

            <html><head><style>p{color:red}</style></head><body><table><tr><td>Coffee</td><td>4.50</td></tr>
            <tr><td>TOTAL</td><td>4.50</td></tr></table><p>Paid&nbsp;by Visa &amp; thanks</p></body></html>
            --b1--
        """.trimIndent()
        val m = EmailMessage.parse(eml.toByteArray())
        assertEquals("Coffee 4.50\nTOTAL 4.50\nPaid by Visa & thanks", m.text)
        assertTrue(m.attachments.isEmpty())
    }

    // --- Phase 5 security review: a saved email is outside input ---------------------------------

    @Test
    fun `attachment names keep only their last part, without control characters`() {
        fun named(name: String) = EmailMessage.parse(
            """
            Content-Type: multipart/mixed; boundary=b

            --b
            Content-Type: application/pdf
            Content-Disposition: attachment; filename="$name"

            %PDF-1.4
            --b--
            """.trimIndent().toByteArray(),
        ).attachments.single().fileName
        assertEquals("evil.pdf", named("../../AppData/Roaming/evil.pdf"))
        assertEquals("evil.pdf", named("""C:\Users\Public\evil.pdf"""))
        assertEquals("recu.pdf", named("re\u0007cu.pdf"))
        assertEquals("attachment", named(".."+"/"))
    }

    @Test
    fun `parts nested too deep or too many are skipped, and a huge file is refused`() {
        var eml = "Content-Type: application/pdf; name=\"deep.pdf\"\r\n\r\n%PDF-1.4"
        repeat(EmailMessage.MAX_DEPTH + 5) { eml = "Content-Type: message/rfc822\r\n\r\n$eml" }
        assertTrue(EmailMessage.parse(eml.toByteArray()).attachments.isEmpty(), "the attachment under too many levels is not read")

        val many = buildString {
            append("Content-Type: multipart/mixed; boundary=b\r\n\r\n")
            repeat(EmailMessage.MAX_PARTS + 50) { append("--b\r\nContent-Type: application/pdf; name=\"$it.pdf\"\r\n\r\n%PDF\r\n") }
            append("--b--")
        }
        assertTrue(EmailMessage.parse(many.toByteArray()).attachments.size < EmailMessage.MAX_PARTS)

        assertFailsWith<IllegalArgumentException> { EmailMessage.parse(ByteArray(EmailMessage.MAX_BYTES + 1)) }
    }

    @Test
    fun `malformed HTML is read in linear time and a bad character reference does not fail`() {
        val started = System.nanoTime()
        EmailMessage.htmlToText("<script".repeat(200_000))
        EmailMessage.htmlToText("<".repeat(1_000_000))
        EmailMessage.htmlToText("<p".repeat(300_000) + ">")
        assertTrue(System.nanoTime() - started < 5_000_000_000L, "a crafted body cannot hold the import")
        assertEquals("a b", EmailMessage.htmlToText("a &#99999999; b"))
        assertEquals("before", EmailMessage.htmlToText("before<script>never closed"))
    }

    @Test
    fun `logos shown inside an HTML receipt are not attachments`() {
        val png = Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3))
        val eml = """
            From: orders@example.ca
            Subject: Your order
            Content-Type: multipart/related; boundary=r1

            --r1
            Content-Type: text/html; charset=UTF-8

            <html><body><img src="cid:logo"><p>TOTAL 12.00</p><img src="cid:pixel"></body></html>
            --r1
            Content-Type: image/png; name="logo.png"
            Content-ID: <logo>
            Content-Disposition: inline; filename="logo.png"
            Content-Transfer-Encoding: base64

            $png
            --r1
            Content-Type: image/gif
            Content-ID: <pixel>
            Content-Transfer-Encoding: base64

            $png
            --r1
            Content-Type: image/jpeg; name="receipt.jpg"
            Content-Disposition: attachment; filename="receipt.jpg"
            Content-ID: <scan>
            Content-Transfer-Encoding: base64

            $png
            --r1--
        """.trimIndent()
        val m = EmailMessage.parse(eml.toByteArray())
        assertEquals("TOTAL 12.00", m.text)
        assertEquals(listOf("receipt.jpg"), m.attachments.map { it.fileName })
    }

    @Test
    fun `a large picture placed inline, like a photo mailed from a phone, is kept`() {
        val photo = Base64.getEncoder().encodeToString(ByteArray(100_000) { (it % 251).toByte() })
        val eml = """
            From: me@example.ca
            Content-Type: multipart/related; boundary=p

            --p
            Content-Type: text/html

            <img src="cid:photo">
            --p
            Content-Type: image/jpeg; name="IMG_1234.jpg"
            Content-ID: <photo>
            Content-Disposition: inline; filename="IMG_1234.jpg"
            Content-Transfer-Encoding: base64

            $photo
            --p--
        """.trimIndent()
        val m = EmailMessage.parse(eml.toByteArray())
        assertEquals(listOf("IMG_1234.jpg"), m.attachments.map { it.fileName })
        assertEquals(100_000, m.attachments.single().content.size)
    }
}
