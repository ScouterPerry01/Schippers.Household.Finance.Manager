package ca.schippers.hfm.desktop

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.parser.PdfTextExtractor
import kotlin.test.Test
import kotlin.test.assertTrue

/** CAP-06: an email kept as a PDF keeps its letters, Western or not. */
class EmailPdfTest {

    private fun text(pdf: ByteArray): String = PdfReader(pdf).use { PdfTextExtractor(it).getTextFromPage(1) }

    @Test
    fun `a Western email uses the standard font and keeps its accents`() {
        val pdf = emailPdf(listOf("From: Épicerie Côté"), "Total : 12,50 $\nMerci de votre visite")
        val text = text(pdf)
        assertTrue("Épicerie Côté" in text && "Merci de votre visite" in text, text)
    }

    @Test
    fun `Greek and Cyrillic letters are kept with a font of the computer that has them`() {
        val body = "Αριθμός παραγγελίας 1042\nСпасибо за покупку"
        assumeTrue(unicodeFont(body, SYSTEM_FONTS) != null, "no font with these letters on this computer")
        val text = text(emailPdf(listOf("From: Ταβέρνα"), body))
        for (expected in listOf("Ταβέρνα", "Αριθμός", "Спасибо")) assertTrue(expected in text, "$expected in $text")
    }

    @Test
    fun `without any such font the email is still kept`() {
        val pdf = emailPdf(listOf("From: Ταβέρνα"), "Спасибо", fonts = listOf("/no/such/font.ttf"))
        assertTrue(pdf.isNotEmpty())
    }
}
