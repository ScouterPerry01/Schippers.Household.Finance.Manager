package ca.schippers.hfm.ocr.desktop

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.FieldExtractor
import ca.schippers.hfm.ocr.TaxName
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OCR-01, OCR-02, OCR-04 on the desktop: the real models, end to end. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopOcrTest {

    private val engine = PaddleOcrEngine()
    private val reader = DocumentReader(engine)

    @AfterAll
    fun close() = engine.close()

    /** A slightly rotated thermal-style receipt, saved as a JPEG like a phone photo. */
    private fun receiptJpeg(rows: List<String>): ByteArray {
        val img = BufferedImage(760, 70 + rows.size * 46, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = Color(246, 244, 238)
            fillRect(0, 0, img.width, img.height)
            rotate(Math.toRadians(1.5))
            color = Color(30, 30, 30)
            font = Font(Font.MONOSPACED, Font.BOLD, 28)
            rows.forEachIndexed { i, row -> drawString(row, 40, 60 + i * 46) }
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    @Test
    fun `a French receipt photo is read and its fields extracted`() {
        val bytes = receiptJpeg(
            listOf(
                "MARCHE TRADITION",
                "2026-09-28 10:14",
                "POMMES CORTLAND     4,29",
                "FROMAGE OKA         9,99",
                "SAVON A VAISSELLE   3,49",
                "SOUS-TOTAL         17,77",
                "TPS                 0,17",
                "TVQ                 0,35",
                "TOTAL              18,29",
                "VISA ************4821",
            ),
        )
        assertEquals(FileKind.JPEG, FileKind.of(bytes))
        val read = reader.read(bytes)
        val draft = FieldExtractor.extract(read.result, LocalDate(2026, 10, 2))
        assertEquals(Money.parse("18.29", Currency.CAD), draft.total?.value, read.result.text)
        assertEquals(listOf(TaxName.GST, TaxName.QST), draft.taxes.map { it.first })
        assertEquals(LocalDate(2026, 9, 28), draft.date?.value)
        assertEquals("4821", draft.cardLast4?.value)
        assertTrue(read.result.elapsedMillis < 20_000)
    }

    @Test
    fun `a PDF e-bill is read from its text layer`() {
        val pdf = ByteArrayOutputStream().also { out ->
            PDDocument().use { doc ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    cs.newLineAtOffset(72f, 700f)
                    for (line in listOf("Videotron", "Account number 123 456 789", "Bill date 2026-09-18", "Amount due 95.00", "Due date 2026-10-09")) {
                        cs.showText(line)
                        cs.newLineAtOffset(0f, -18f)
                    }
                    cs.endText()
                }
                doc.save(out)
            }
        }.toByteArray()
        val read = reader.read(pdf)
        assertTrue(read.fromTextLayer)
        val draft = FieldExtractor.extract(read.result)
        assertEquals(Money.parse("95.00", Currency.CAD), draft.total?.value)
        assertEquals(LocalDate(2026, 10, 9), draft.dueDate?.value)
        assertEquals(1f, draft.total!!.confidence, "text from the PDF itself is exact")
        assertTrue(reader.preview(pdf) != null)
    }
}
