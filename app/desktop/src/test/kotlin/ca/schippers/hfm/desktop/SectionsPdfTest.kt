package ca.schippers.hfm.desktop

import org.junit.jupiter.api.io.TempDir
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.PdfWriter
import org.openpdf.text.pdf.parser.PdfTextExtractor
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** EST-03, HLT-09: the summary PDF, and the protected copy that opens only with its password. */
class SectionsPdfTest {

    @TempDir
    lateinit var temp: Path

    private val sections = listOf(DocSection("Papers of Perry", listOf("Where the will is" to "Notary Tremblay, Québec"), note = "Balances as of 2026-10-05."))

    @Test
    fun `a protected summary opens only with its password`() {
        val plain = temp.resolve("plain.pdf").toFile()
        SectionsPdf.write("In case of emergency", "Prepared 2026-10-05", sections, plain)
        PdfReader(plain.readBytes()).use { assertTrue(PdfTextExtractor(it).getTextFromPage(1).contains("Notary Tremblay")) }

        val locked = temp.resolve("locked.pdf").toFile()
        SectionsPdf.write("In case of emergency", "Prepared 2026-10-05", sections, locked, "correct horse".toCharArray())
        assertFailsWith<Exception> { PdfReader(locked.readBytes()).use { PdfTextExtractor(it).getTextFromPage(1) } }
        assertFailsWith<Exception> { PdfReader(locked.readBytes(), "wrong pass".toByteArray()).use { } }
        PdfReader(locked.readBytes(), "correct horse".toByteArray()).use { assertTrue(PdfTextExtractor(it).getTextFromPage(1).contains("Notary Tremblay")) }
    }

    @Test
    fun `the protected summary uses AES-256, not the MD5-keyed AES-128`() {
        val locked = temp.resolve("locked.pdf").toFile()
        SectionsPdf.write("In case of emergency", "Prepared 2026-10-05", sections, locked, "correct horse".toCharArray())
        PdfReader(locked.readBytes(), "correct horse".toByteArray()).use { reader ->
            assertTrue(reader.isEncrypted)
            assertEquals(PdfWriter.ENCRYPTION_AES_256_V3, reader.cryptoMode and 7)
        }
        // The encryption dictionary itself is never encrypted: version 5, revision 6 (SHA-2 password hashing).
        val raw = String(locked.readBytes(), Charsets.ISO_8859_1)
        assertTrue(Regex("""/R\s*6\b""").containsMatchIn(raw) && Regex("""/V\s*5\b""").containsMatchIn(raw), "security handler V5 R6")
    }

    fun `an empty section says so, as on screen`() {
        val file = temp.resolve("empty.pdf").toFile()
        SectionsPdf.write("In case of emergency", "Prepared 2026-10-05", sections + DocSection("Pensions", emptyList()), file, emptyText = "Nothing recorded.")
        PdfReader(file.readBytes()).use { assertTrue(PdfTextExtractor(it).getTextFromPage(1).contains("Nothing recorded.")) }
    }
}
