package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfxImporterTest {

    private fun bytes(name: String) = javaClass.getResourceAsStream("/samples/$name")!!.readBytes()
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun read(name: String) = OfxImporter().read(bytes(name).inputStream())

    @Test
    fun `SGML bank statement in windows-1252 with French accents`() {
        val s = read("chequing-fr.ofx").single()
        assertEquals("0045678", s.accountNumberHint)
        assertEquals(Currency.CAD, s.currency)
        assertEquals(LocalDate(2026, 3, 1), s.periodStart)
        assertEquals(LocalDate(2026, 3, 31), s.periodEnd)
        assertEquals(cad("3922.52"), s.closingBalance)
        assertEquals(cad("2450.00"), s.openingBalance)
        assertEquals(4, s.lines.size)
        val hydro = s.lines[1]
        assertEquals("Hydro-Québec", hydro.payee)
        assertEquals("Facture électricité", hydro.memo)
        assertEquals(cad("-132.48"), hydro.amount)
        assertEquals("2026031200002", hydro.externalId)
        assertEquals(LocalDate(2026, 3, 1), s.lines[0].date)
        assertEquals("107", s.lines[2].checkNumber)
        assertEquals("Vidéotron & cie", s.lines[3].payee)
    }

    @Test
    fun `XML credit card statement`() {
        val s = read("visa.qfx").single()
        assertEquals("4540123412341234", s.accountNumberHint)
        assertEquals(listOf(cad("-187.32"), cad("-243.90"), cad("566.57")), s.lines.map { it.amount })
        assertEquals(cad("-431.22"), s.closingBalance)
        assertEquals(cad("-566.57"), s.openingBalance)
    }

    @Test
    fun `several accounts in one file`() {
        val statements = read("two-accounts.ofx")
        assertEquals(listOf("111", "222"), statements.map { it.accountNumberHint })
        assertEquals(Currency.USD, statements[1].currency)
        assertEquals("Café", statements[0].lines.single().payee)
    }

    @Test
    fun `importers are chosen by content and extension`() {
        assertIs<OfxImporter>(Importers.forFile("download.qbo", "OFXHEADER:100".toByteArray()))
        assertIs<OfxImporter>(Importers.forFile("export.txt", "<?xml?><OFX>".toByteArray()))
        assertIs<CsvImporter>(Importers.forFile("export.csv", "Date,Amount".toByteArray()))
    }

    @Test
    fun `garbage is rejected clearly`() {
        assertFailsWith<ImportException> { OfxImporter().read("hello".byteInputStream()) }
    }
}

class CsvImporterTest {

    private fun bytes(name: String) = javaClass.getResourceAsStream("/samples/$name")!!.readBytes()
    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    @Test
    fun `guesses the layout of an English bank export`() {
        val preview = CsvImporter().preview(bytes("bank-en.csv"))
        val g = preview.guess
        assertEquals(',', g.delimiter)
        assertTrue(g.hasHeader)
        assertEquals(2, g.dateColumn)
        assertEquals("M/d/yyyy", g.dateFormat)
        assertEquals(6, g.amountColumn)
        assertEquals(4, g.payeeColumn)
        assertEquals(5, preview.rows.size)

        val s = CsvImporter().read(bytes("bank-en.csv").inputStream(), ImportOptions(mapping = g.toMap())).single()
        assertEquals(listOf(cad("3150.00"), cad("-132.48"), cad("-1450.00"), cad("-95.00")), s.lines.map { it.amount })
        assertEquals("PAYROLL, EMPLOYER INC", s.lines[0].payee)
        assertEquals(LocalDate(2026, 3, 12), s.lines[1].date)
        assertNull(s.closingBalance)
    }

    @Test
    fun `guesses a French export with debit and credit columns`() {
        val g = CsvImporter().preview(bytes("banque-fr.csv")).guess
        assertEquals(';', g.delimiter)
        assertEquals("windows-1252", g.charset)
        assertEquals(2, g.debitColumn)
        assertEquals(3, g.creditColumn)
        assertNull(g.amountColumn)
        assertEquals(4, g.balanceColumn)
        assertTrue(g.decimalComma)

        val s = CsvImporter().read(bytes("banque-fr.csv").inputStream(), ImportOptions(mapping = g.toMap())).single()
        assertEquals(listOf(cad("-95.00"), cad("-1450.00"), cad("-132.48"), cad("3150.00")), s.lines.map { it.amount })
        assertEquals("Hydro-Québec", s.lines[2].payee)
        assertEquals(cad("3922.52"), s.closingBalance, "balance on the newest row")
        assertEquals(LocalDate(2026, 3, 1), s.periodStart)
    }

    @Test
    fun `mapping survives saving and loading`() {
        val m = CsvMapping(delimiter = '\t', hasHeader = false, dateColumn = 1, debitColumn = 3, creditColumn = 4, negate = true, decimalComma = true)
        assertEquals(m, CsvMapping.fromMap(m.toMap()))
    }

    @Test
    fun `card exports with positive purchases can be flipped`() {
        val csv = "Date,Description,Amount\n2026-03-06,IGA,187.32\n2026-03-10,PAYMENT,-566.57\n"
        val mapping = CsvMapping(dateColumn = 0, payeeColumn = 1, amountColumn = 2, negate = true)
        val s = CsvImporter().read(csv.byteInputStream(), ImportOptions(mapping = mapping.toMap())).single()
        assertEquals(listOf(cad("-187.32"), cad("566.57")), s.lines.map { it.amount })
    }

    @Test
    fun `bad dates name the row`() {
        val csv = "Date,Amount\n31/31/2026,5.00\n"
        val e = assertFailsWith<ImportException> {
            CsvImporter().read(csv.byteInputStream(), ImportOptions(mapping = CsvMapping(amountColumn = 1, dateFormat = "dd/MM/yyyy").toMap()))
        }
        assertTrue(e.message!!.contains("Row 1"))
    }

    @Test
    fun `quoted fields and doubled quotes`() {
        val rows = CsvImporter.rows("a,\"b, c\",\"say \"\"hi\"\"\"\r\n1,2,3\n", ',')
        assertEquals(listOf(listOf("a", "b, c", "say \"hi\""), listOf("1", "2", "3")), rows)
    }
}
