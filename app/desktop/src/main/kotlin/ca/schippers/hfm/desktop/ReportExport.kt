package ca.schippers.hfm.desktop

import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import org.dhatim.fastexcel.Workbook
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.swing.JFileChooser

/**
 * A report as rows and columns: the table view under every chart, and what is exported to CSV,
 * Excel and PDF or printed (RPT-04). Cells are text, [Money] or [LocalDate].
 */
/** [notes] are printed under the table in every export, such as the TAX-04 notice. */
data class ReportTable(
    val title: String,
    val subtitle: String,
    val columns: List<String>,
    val rows: List<List<Any?>>,
    val notes: List<String> = emptyList(),
) {
    fun isNumeric(column: Int): Boolean = rows.any { it.getOrNull(column) is Money }
}

enum class ExportFormat(val extension: String) { CSV("csv"), XLSX("xlsx"), PDF("pdf") }

object ReportExport {

    fun text(cell: Any?, locale: Locale): String = when (cell) {
        null -> ""
        is Money -> MoneyFormat.format(cell, locale)
        is LocalDate -> cell.toString()
        else -> cell.toString()
    }

    /** Asks where to save, then writes the file. Returns the file, or null if cancelled. */
    fun save(table: ReportTable, format: ExportFormat, locale: Locale, title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            selectedFile = File(table.title.replace(Regex("""[\\/:*?"<>|]"""), "-") + "." + format.extension)
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val file = chooser.selectedFile.let { if (it.extension.equals(format.extension, true)) it else File(it.path + "." + format.extension) }
        write(table, format, file, locale)
        return file
    }

    fun write(table: ReportTable, format: ExportFormat, file: File, locale: Locale) = when (format) {
        ExportFormat.CSV -> csv(table, file, locale)
        ExportFormat.XLSX -> xlsx(table, file, locale)
        ExportFormat.PDF -> pdf(table, file, locale)
    }

    /** Prints through the system's PDF viewer. */
    fun print(table: ReportTable, locale: Locale) {
        val file = File.createTempFile("hfm-report-", ".pdf").apply { deleteOnExit() }
        pdf(table, file, locale)
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.PRINT)) desktop.print(file) else desktop.open(file)
    }

    /**
     * CSV in UTF-8 with a byte-order mark so Excel reads accents. French spreadsheets use the comma
     * as decimal separator, so French exports use semicolons between columns.
     */
    fun csv(table: ReportTable, file: File, locale: Locale) {
        val french = locale.language == "fr"
        val separator = if (french) ';' else ','
        fun quote(s: String) = if (s.any { it == separator || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        fun cell(c: Any?): String = when (c) {
            is Money -> c.toBigDecimal().toPlainString().let { if (french) it.replace('.', ',') else it }
            else -> text(c, locale)
        }
        val text = buildString {
            append('﻿')
            append(table.columns.joinToString(separator.toString(), transform = ::quote)).append("\r\n")
            for (row in table.rows) append(row.joinToString(separator.toString()) { quote(cell(it)) }).append("\r\n")
            if (table.notes.isNotEmpty()) append("\r\n")
            for (note in table.notes) append(quote(note)).append("\r\n")
        }
        file.writeText(text, Charsets.UTF_8)
    }

    /** Excel workbook with real numbers and dates, so the user can keep calculating. */
    fun xlsx(table: ReportTable, file: File, locale: Locale) {
        FileOutputStream(file).use { out ->
            val workbook = Workbook(out, "RANN's Roost", "1.0")
            val sheet = workbook.newWorksheet(table.title.take(31).replace(Regex("""[\\/:*?\[\]]"""), "-"))
            sheet.value(0, 0, table.title)
            sheet.style(0, 0).bold().fontSize(14).set()
            sheet.value(1, 0, table.subtitle)
            table.columns.forEachIndexed { c, name ->
                sheet.value(3, c, name)
                sheet.style(3, c).bold().fillColor("E1E0D9").set()
            }
            table.rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, cell ->
                    val rowIndex = 4 + r
                    when (cell) {
                        is Money -> {
                            sheet.value(rowIndex, c, cell.toBigDecimal())
                            sheet.style(rowIndex, c).format(if (cell.currency.minorUnits == 0) "#,##0" else "#,##0." + "0".repeat(cell.currency.minorUnits)).set()
                        }
                        is LocalDate -> {
                            sheet.value(rowIndex, c, java.time.LocalDate.of(cell.year, cell.month.ordinal + 1, cell.day))
                            sheet.style(rowIndex, c).format("yyyy-mm-dd").set()
                        }
                        null -> Unit
                        else -> sheet.value(rowIndex, c, text(cell, locale))
                    }
                }
            }
            table.notes.forEachIndexed { i, note -> sheet.value(5 + table.rows.size + i, 0, note) }
            table.columns.indices.forEach { sheet.width(it, if (table.isNumeric(it)) 16.0 else 28.0) }
            workbook.finish()
        }
    }

    /** A printable PDF: title, filters, and the table with right-aligned amounts. */
    fun pdf(table: ReportTable, file: File, locale: Locale) {
        val landscape = table.columns.size > 5
        val document = Document(if (landscape) PageSize.LETTER.rotate() else PageSize.LETTER, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()
        document.add(Paragraph(pdfText(table.title), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)))
        document.add(Paragraph(pdfText(table.subtitle), FontFactory.getFont(FontFactory.HELVETICA, 9f)))
        document.add(Paragraph(" "))
        val pdfTable = PdfPTable(table.columns.size).apply {
            widthPercentage = 100f
            headerRows = 1
        }
        val header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f)
        val body = FontFactory.getFont(FontFactory.HELVETICA, 9f)
        table.columns.forEachIndexed { c, name -> pdfTable.addCell(cell(name, header, table.isNumeric(c), shaded = true)) }
        for (row in table.rows) {
            table.columns.indices.forEach { c -> pdfTable.addCell(cell(text(row.getOrNull(c), locale), body, table.isNumeric(c), shaded = false)) }
        }
        document.add(pdfTable)
        for (note in table.notes) {
            document.add(Paragraph(" "))
            document.add(Paragraph(pdfText(note), FontFactory.getFont(FontFactory.HELVETICA, 8f)))
        }
        document.close()
    }

    private fun cell(text: String, font: Font, right: Boolean, shaded: Boolean) = PdfPCell(Phrase(pdfText(text), font)).apply {
        horizontalAlignment = if (right) Element.ALIGN_RIGHT else Element.ALIGN_LEFT
        paddingTop = 3f
        paddingBottom = 4f
        borderWidth = 0.25f
        if (shaded) backgroundColor = java.awt.Color(0xE1, 0xE0, 0xD9)
    }

    /** The standard PDF fonts cover French accents but not the narrow spaces used in French numbers. */
    private fun pdfText(s: String) = s.replace(' ', ' ').replace(' ', ' ')
}
