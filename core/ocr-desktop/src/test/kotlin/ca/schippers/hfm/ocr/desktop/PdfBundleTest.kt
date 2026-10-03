package ca.schippers.hfm.ocr.desktop

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfBundleTest {

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    @Test
    fun `receipts bundle with a cover page`() {
        val pdf = PdfPages.fromJpegs(listOf(jpeg(), jpeg()))
        val bundle = PdfPages.bundle(
            "Frais médicaux 2026",
            listOf("2026-03-01  Léa  Examen de la vue  90,00 $", "Unsupported 漢字 text"),
            listOf("application/pdf" to pdf, "image/jpeg" to jpeg()),
        )
        Loader.loadPDF(bundle).use { doc ->
            assertEquals(4, doc.numberOfPages, "cover, the two pages of the PDF, and the photo")
            val text = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc)
            assertTrue("Frais médicaux 2026" in text)
            assertTrue("Examen de la vue" in text)
            assertTrue("Unsupported ?? text" in text, "characters the font lacks are replaced")
        }
    }
}
