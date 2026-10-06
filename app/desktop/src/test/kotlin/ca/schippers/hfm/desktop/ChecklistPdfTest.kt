package ca.schippers.hfm.desktop

import org.junit.jupiter.api.io.TempDir
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.parser.PdfTextExtractor
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** SEA-02: the printed seasonal checklist. */
class ChecklistPdfTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `the checklist prints each task under its vehicle or asset, done ones marked`() {
        val file = temp.resolve("fall.pdf").toFile()
        ChecklistPdf.write(
            "Checklist: Fall 2026", "2026-09-22 to 2026-12-20 · 1 of 2 done", "Date, cost, notes",
            listOf(
                ChecklistPdf.Group("Above-ground pool", listOf(ChecklistPdf.Line(true, "Close and winterize the pool", "done 2026-09-20"))),
                ChecklistPdf.Group("Civic", listOf(ChecklistPdf.Line(false, "Install winter tires", "due 2026-11-15"))),
            ),
            file,
        )
        PdfReader(file.readBytes()).use { reader ->
            val text = PdfTextExtractor(reader).getTextFromPage(1)
            for (s in listOf("Checklist: Fall 2026", "Above-ground pool", "Close and winterize the pool", "Install winter tires", "due 2026-11-15", "X")) {
                assertTrue(s in text, "$s in $text")
            }
        }
    }
}
