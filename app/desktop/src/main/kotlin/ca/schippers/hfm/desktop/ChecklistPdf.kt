package ca.schippers.hfm.desktop

import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.FontFactory
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.Rectangle
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream

/**
 * SEA-02: a checklist to print and tick by hand: for each group (a vehicle, a pool), its tasks with
 * a box (marked when already done), the due or done date, and room to write.
 */
object ChecklistPdf {

    data class Line(val done: Boolean, val text: String, val detail: String)

    data class Group(val title: String, val lines: List<Line>)

    fun write(title: String, subtitle: String, notesHeading: String, groups: List<Group>, file: File) {
        val document = Document(PageSize.LETTER, 42f, 42f, 42f, 42f)
        FileOutputStream(file).use { out ->
            PdfWriter.getInstance(document, out)
            document.open()
            document.add(Paragraph(text(title), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)))
            document.add(Paragraph(text(subtitle), FontFactory.getFont(FontFactory.HELVETICA, 9f)))
            val heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)
            val body = FontFactory.getFont(FontFactory.HELVETICA, 10f)
            val small = FontFactory.getFont(FontFactory.HELVETICA, 8f)
            val mark = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)
            for (g in groups) {
                document.add(Paragraph(" "))
                document.add(Paragraph(text(g.title), heading))
                val table = PdfPTable(floatArrayOf(0.22f, 3f, 1.4f, 2.2f)).apply { widthPercentage = 100f; setSpacingBefore(4f) }
                table.addCell(PdfPCell(Phrase("")).apply { border = Rectangle.NO_BORDER })
                table.addCell(PdfPCell(Phrase("")).apply { border = Rectangle.NO_BORDER })
                table.addCell(PdfPCell(Phrase("")).apply { border = Rectangle.NO_BORDER })
                table.addCell(PdfPCell(Phrase(text(notesHeading), small)).apply { border = Rectangle.NO_BORDER })
                for (l in g.lines) {
                    // The box: a small bordered cell, marked X when the task is already done.
                    table.addCell(
                        PdfPCell(Phrase(if (l.done) "X" else " ", mark)).apply {
                            border = Rectangle.BOX
                            horizontalAlignment = Element.ALIGN_CENTER
                            verticalAlignment = Element.ALIGN_MIDDLE
                            fixedHeight = 14f
                        },
                    )
                    table.addCell(PdfPCell(Phrase(text(l.text), body)).apply { border = Rectangle.NO_BORDER; paddingLeft = 6f; paddingBottom = 6f })
                    table.addCell(PdfPCell(Phrase(text(l.detail), small)).apply { border = Rectangle.NO_BORDER; paddingBottom = 6f })
                    // A line to write the date, the cost or a note on.
                    table.addCell(PdfPCell(Phrase(" ", body)).apply { border = Rectangle.BOTTOM; borderWidthBottom = 0.5f })
                }
                document.add(table)
            }
            document.close()
        }
    }

    /** The standard PDF fonts cover French accents but not the narrow spaces used in French numbers. */
    private fun text(s: String) = s.replace(' ', ' ').replace(' ', ' ')
}
