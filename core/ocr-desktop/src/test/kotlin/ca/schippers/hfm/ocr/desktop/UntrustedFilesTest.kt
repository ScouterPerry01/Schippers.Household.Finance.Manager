package ca.schippers.hfm.ocr.desktop

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Security review 2026-10-07: files from outside (a phone, an import, an email) cannot take all the memory when shown or read. */
class UntrustedFilesTest {

    private fun pdf(vararg sizes: PDRectangle): ByteArray = PDDocument().use { doc ->
        sizes.forEach { doc.addPage(PDPage(it)) }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test
    fun `a page hundreds of inches wide is drawn within the viewer's size`() {
        // 200 inches square: at 150 DPI, 30,000 pixels a side, 3.6 GB.
        val bytes = pdf(PDRectangle.LETTER, PDRectangle(14_400f, 14_400f))
        assertEquals(2, DocumentReader.pageCount(bytes))
        val huge = assertNotNull(DocumentReader.page(bytes, 1))
        assertTrue(maxOf(huge.width, huge.height) <= 2400, "${huge.width} x ${huge.height}")
        // A letter page is still drawn at the viewer's full resolution: 8.5 x 11 inches at 150 DPI.
        val letter = assertNotNull(DocumentReader.page(bytes, 0))
        assertTrue(letter.width in 1274..1275 && letter.height in 1649..1650, "${letter.width} x ${letter.height}")
    }

    @Test
    fun `the resolution is lowered only for pages too large`() {
        assertEquals(150f, DocumentReader.renderDpi(792f, 150f, 2400))
        assertEquals(12f, DocumentReader.renderDpi(14_400f, 150f, 2400))
    }

    @Test
    fun `a photo's orientation tag pointing outside the file is ignored`() {
        // A JPEG whose EXIF block gives an offset past the end (and one that wraps round as a negative number).
        for (offset in listOf(byteArrayOf(0x7F, -1, -1, -1), byteArrayOf(-1, -1, -1, -16))) {
            val exif = byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00, 0x10, 'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
                'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, *offset, 0, 0, 0, 0,
            )
            assertEquals(1, ImageLoader.exifOrientation(exif))
        }
    }
}
