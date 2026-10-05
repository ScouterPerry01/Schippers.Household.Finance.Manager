package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Invoice
import ca.schippers.hfm.books.InvoiceLine
import ca.schippers.hfm.books.InvoiceTax
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.parser.PdfTextExtractor
import java.math.BigDecimal
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/** SAL-04: the invoice PDF shows its lines, each sales tax and the total. */
class InvoicePdfTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `the invoice PDF lists the lines, the taxes and the total`() {
        val invoice = Invoice(
            "i", "g", "2026-001", "Mme Roy", LocalDate(2026, 9, 1), Currency.CAD,
            listOf(InvoiceLine("Tutoring", "6", "45"), InvoiceLine("Workbook", "1", "19.99")),
            dueDate = LocalDate(2026, 9, 30), customerDetails = "12 Elm St\nQuébec", taxes = listOf(InvoiceTax.ofPercent("GST", BigDecimal("5")), InvoiceTax.ofPercent("QST", BigDecimal("9.975"))),
        )
        val file = temp.resolve("invoice.pdf").toFile()
        InvoicePdf.write(invoice, "Sam", file, { key, args -> listOf(key, *args).joinToString(" ") }, { MoneyFormat.format(it, Locale.CANADA) }, { it.toString() })
        val text = PdfReader(file.readBytes()).use { PdfTextExtractor(it).getTextFromPage(1) }
        for (expected in listOf("invoice.title 2026-001", "Mme Roy", "Québec", "Workbook", "\$45.00", "invoice.taxLine taxName.QST 9.975 \$28.93", "invoice.totalLine \$333.42")) {
            assertTrue(expected in text, "$expected in $text")
        }
    }
}
