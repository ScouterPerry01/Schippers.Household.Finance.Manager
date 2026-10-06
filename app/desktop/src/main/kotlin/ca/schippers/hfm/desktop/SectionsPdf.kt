package ca.schippers.hfm.desktop

import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.FontFactory
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom

/** A titled part of a printed summary: lines of label and value; a blank label continues the line above. */
data class DocSection(val title: String, val lines: List<Pair<String, String>>, val note: String? = null)

/**
 * EST-03, HLT-09: a summary of several sections as a PDF to print or hand over. With a [password],
 * the file is encrypted (AES) and opens only with it, so it can be sent to a spouse or an executor.
 */
object SectionsPdf {

    /** [emptyText] is printed under a section with nothing in it, as the screen shows it. */
    fun write(title: String, subtitle: String, sections: List<DocSection>, file: File, password: CharArray? = null, emptyText: String? = null) {
        val document = Document(PageSize.LETTER, 42f, 42f, 42f, 42f)
        FileOutputStream(file).use { out ->
            val writer = PdfWriter.getInstance(document, out)
            if (password != null) {
                // The owner password is random and thrown away: no one can lift the restrictions.
                val owner = ByteArray(24).also(SecureRandom()::nextBytes)
                writer.setEncryption(String(password).toByteArray(Charsets.UTF_8), owner, PdfWriter.ALLOW_PRINTING or PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128)
            }
            document.open()
            document.add(Paragraph(text(title), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)))
            document.add(Paragraph(text(subtitle), FontFactory.getFont(FontFactory.HELVETICA, 9f)))
            val heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)
            val label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f)
            val body = FontFactory.getFont(FontFactory.HELVETICA, 9f)
            for (s in sections) {
                document.add(Paragraph(" "))
                document.add(Paragraph(text(s.title), heading))
                if (s.lines.isNotEmpty()) {
                    val table = PdfPTable(floatArrayOf(1f, 2.4f)).apply { widthPercentage = 100f; setSpacingBefore(4f) }
                    for ((l, v) in s.lines) {
                        table.addCell(PdfPCell(Phrase(text(l), label)).apply { border = 0; paddingBottom = 3f; verticalAlignment = Element.ALIGN_TOP })
                        table.addCell(PdfPCell(Phrase(text(v), body)).apply { border = 0; paddingBottom = 3f })
                    }
                    document.add(table)
                } else if (emptyText != null) {
                    document.add(Paragraph(text(emptyText), body))
                }
                s.note?.let { document.add(Paragraph(text(it), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f))) }
            }
            document.close()
        }
    }

    /** The standard PDF fonts cover French accents but not the narrow spaces used in French numbers. */
    private fun text(s: String) = s.replace('\u202F', ' ').replace('\u00A0', ' ')
}
