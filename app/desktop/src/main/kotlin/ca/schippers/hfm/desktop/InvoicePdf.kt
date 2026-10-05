package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Invoice
import ca.schippers.hfm.money.Money
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
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode

/** SAL-04: the invoice as a PDF to send: who it is from and for, its lines, taxes and total. */
object InvoicePdf {

    /** [t] gives a text in the user's language, [money] and [date] format amounts and dates. */
    fun write(i: Invoice, from: String, file: File, t: (String, Array<out Any>) -> String, money: (Money) -> String, date: (kotlinx.datetime.LocalDate) -> String) {
        fun tr(key: String, vararg args: Any) = t(key, args)
        // The standard PDF fonts have no narrow or non-breaking spaces.
        fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')
        val document = Document(PageSize.LETTER, 54f, 54f, 54f, 54f)
        FileOutputStream(file).use { out ->
            PdfWriter.getInstance(document, out)
            document.open()
            val f = FontFactory.getFont(FontFactory.HELVETICA, 10f)
            val b = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)
            fun p(text: String, font: Font = f) = document.add(Paragraph(plain(text), font))
            p(tr("invoice.title", i.number), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f))
            p(from, b)
            p(" ")
            p(tr("invoice.billTo"), b)
            p(i.customer)
            i.customerDetails?.lines()?.forEach { p(it) }
            p(" ")
            p(listOfNotNull(tr("invoice.issuedOn", date(i.issueDate)), i.dueDate?.let { tr("invoice.dueOn", date(it)) }).joinToString("    "))
            p(" ")
            val table = PdfPTable(floatArrayOf(4f, 1f, 1.4f, 1.6f)).apply { widthPercentage = 100f }
            fun cell(text: String, font: Font, right: Boolean = false) = table.addCell(
                PdfPCell(Phrase(plain(text), font)).apply {
                    horizontalAlignment = if (right) Element.ALIGN_RIGHT else Element.ALIGN_LEFT
                    paddingBottom = 4f
                    borderWidth = 0.25f
                },
            )
            cell(tr("share.description"), b)
            cell(tr("invoice.quantity"), b, true)
            cell(tr("invoice.price"), b, true)
            cell(tr("share.amount"), b, true)
            for (l in i.lines) {
                cell(l.description, f)
                cell(l.quantity, f, true)
                cell(money(Money.of(BigDecimal(l.unitPrice).setScale(i.currency.minorUnits, RoundingMode.HALF_UP), i.currency)), f, true)
                cell(money(l.amount(i.currency)), f, true)
            }
            document.add(table)
            p(" ")
            p(tr("invoice.subtotal", money(i.subtotal)))
            for (tax in i.taxes) {
                val rate = tax.percent
                p(tr("invoice.taxLine", tr("taxName.${tax.name}"), rate, money(i.tax(tax))))
            }
            p(tr("invoice.totalLine", money(i.total)), b)
            i.notes?.let { p(" "); p(it) }
            document.close()
        }
    }
}
