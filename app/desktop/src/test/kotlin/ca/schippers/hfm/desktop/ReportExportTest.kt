package ca.schippers.hfm.desktop

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportExportTest {

    @TempDir
    lateinit var temp: File

    private val table = ReportTable(
        "Dépenses par catégorie", "2026-01-01 – 2026-03-31",
        listOf("Catégorie", "Montant"),
        listOf(
            listOf("Épicerie; marché", Money.parse("1234.56", Currency.CAD)),
            listOf("Restaurants \"chic\"", Money.parse("-60.00", Currency.CAD)),
            listOf(LocalDate(2026, 3, 31), null),
        ),
    )

    @Test
    fun `French CSV uses semicolons, decimal commas and a byte-order mark`() {
        val file = File(temp, "r.csv")
        ReportExport.csv(table, file, Locale.forLanguageTag("fr-CA"))
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿"))
        val lines = text.removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals("Catégorie;Montant", lines[0])
        assertEquals("\"Épicerie; marché\";1234,56", lines[1])
        assertEquals("\"Restaurants \"\"chic\"\"\";-60,00", lines[2])
        assertEquals("2026-03-31;", lines[3])
    }

    @Test
    fun `English CSV uses commas and decimal points`() {
        val file = File(temp, "r.csv")
        ReportExport.csv(table, file, Locale.forLanguageTag("en-CA"))
        assertEquals("Épicerie; marché,1234.56", file.readText().removePrefix("﻿").split("\r\n")[1])
    }

    @Test
    fun `Excel and PDF files are written`() {
        val xlsx = File(temp, "r.xlsx")
        ReportExport.xlsx(table, xlsx, Locale.CANADA_FRENCH)
        ZipFile(xlsx).use { zip -> assertTrue(zip.getEntry("xl/worksheets/sheet1.xml") != null) }

        val pdf = File(temp, "r.pdf")
        ReportExport.pdf(table, pdf, Locale.CANADA_FRENCH)
        assertEquals("%PDF", pdf.readBytes().copyOfRange(0, 4).decodeToString())
    }
}
