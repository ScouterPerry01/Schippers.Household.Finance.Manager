package ca.schippers.hfm.ocr

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldExtractorTest {

    private fun lines(text: String, confidence: Float = 0.97f) = text.trimIndent().lines().map { OcrLine(it, confidence) }
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val today = LocalDate(2026, 10, 2)

    @Test
    fun `a Quebec grocery receipt in French`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                IGA Extra Famille Jodoin
                1250, boul. Charest Ouest
                Québec QC  418-555-0123
                BIENVENUE
                LAIT 2% 4L              6,49
                PATES FRAICHES          4,99
                BOEUF HACHE MI-MAIGRE  12,87
                PAPIER TOILETTE        15,49 TX
                SOUS-TOTAL             39,84
                TPS 5 %                 0,77
                TVQ 9,975 %             1,55
                TOTAL                  42,16
                INTERAC                42,16
                CARTE ************4821
                Facture no 0042-1187
                2026/09/28 14:32
                TOTAL DES ÉCONOMIES     3,50
                MERCI DE VOTRE VISITE
                """,
            ),
            today,
        )
        assertEquals(DocumentKind.RECEIPT, draft.kind)
        assertEquals("IGA Extra Famille Jodoin", draft.merchant?.value)
        assertEquals(cad("42.16"), draft.total?.value)
        assertTrue(draft.total!!.confidence >= 0.95f, "39,84 + 0,77 + 1,55 = 42,16: the arithmetic confirms it")
        assertEquals(cad("39.84"), draft.subtotal?.value)
        assertEquals(listOf(TaxName.GST to cad("0.77"), TaxName.QST to cad("1.55")), draft.taxes.map { it.first to it.second.value })
        assertEquals(LocalDate(2026, 9, 28), draft.date?.value)
        assertEquals("Interac", draft.paymentMethod?.value)
        assertEquals("4821", draft.cardLast4?.value)
        assertEquals("0042-1187", draft.invoiceNumber?.value)
        assertEquals(Currency.CAD, draft.currency)
    }

    @Test
    fun `an English receipt with HST and a Visa card`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Canadian Tire #412
                Store 412 Ottawa ON
                Receipt # 412-88213
                MOTOMASTER OIL 5W30    39.99
                WIPER BLADES           24.99
                SUBTOTAL               64.98
                HST 13%                 8.45
                TOTAL                  73.43
                VISA            XXXXXXXXXXXX7731
                09/14/2026 16:05
                THANK YOU
                """,
            ),
            today,
        )
        assertEquals("Canadian Tire #412", draft.merchant?.value)
        assertEquals(cad("73.43"), draft.total?.value)
        assertEquals(listOf(TaxName.HST), draft.taxes.map { it.first })
        assertEquals(LocalDate(2026, 9, 14), draft.date?.value, "14 cannot be a month")
        assertEquals("Visa", draft.paymentMethod?.value)
        assertEquals("7731", draft.cardLast4?.value)
    }

    @Test
    fun `a Hydro-Quebec bill with due date and account number`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Hydro-Québec
                Votre facture d'électricité
                Date de facturation : 15 septembre 2026
                Numéro de compte : 2992 0471 6553
                Période de facturation du 13 juil. 2026 au 11 sept. 2026
                Consommation totale 1 842 kWh
                Montant à payer 142,37 $
                Date d'échéance : 2026-10-06
                """,
            ),
            today,
        )
        assertEquals(DocumentKind.BILL, draft.kind)
        assertEquals("Hydro-Québec", draft.merchant?.value)
        assertEquals(cad("142.37"), draft.total?.value)
        assertEquals(LocalDate(2026, 9, 15), draft.date?.value)
        assertEquals(LocalDate(2026, 10, 6), draft.dueDate?.value)
        assertEquals("2992 0471 6553", draft.accountNumber?.value)
    }

    @Test
    fun `an English phone bill`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Bell
                Your bill
                Account number 512 345 6789
                Bill date Sep 20, 2026
                Total amount due $118.64
                Payment due date Oct 11, 2026
                """,
            ),
            today,
        )
        assertEquals(DocumentKind.BILL, draft.kind)
        assertEquals(cad("118.64"), draft.total?.value)
        assertEquals(LocalDate(2026, 9, 20), draft.date?.value)
        assertEquals(LocalDate(2026, 10, 11), draft.dueDate?.value)
        assertEquals("512 345 6789", draft.accountNumber?.value)
    }

    @Test
    fun `amounts, rates and separators`() {
        assertEquals(listOf(BigDecimal("1234.56")), FieldExtractor.amounts("TOTAL 1 234,56 \$"))
        assertEquals(listOf(BigDecimal("1234.56")), FieldExtractor.amounts("TOTAL \$1,234.56"))
        assertEquals(listOf(BigDecimal("1.55")), FieldExtractor.amounts("TVQ 9,975 % 1,55"), "the rate is not an amount")
        assertEquals(listOf(BigDecimal("-3.50")), FieldExtractor.amounts("RABAIS -3.50"))
        assertTrue(FieldExtractor.amounts("Tel 418-555-0123").isEmpty())
    }

    @Test
    fun `day and month order`() {
        assertEquals(LocalDate(2026, 3, 4), FieldExtractor.dates("04/03/2026", french = true).single().first, "French: day first")
        assertEquals(LocalDate(2026, 4, 3), FieldExtractor.dates("04/03/2026", french = false).single().first, "English: month first")
        assertTrue(FieldExtractor.dates("04/03/2026", french = true).single().second < Extracted.REVIEW_THRESHOLD, "an ambiguous order is flagged")
        assertEquals(LocalDate(2026, 3, 12), FieldExtractor.dates("le 12 mars 2026", french = true).single().first)
        assertEquals(LocalDate(2026, 3, 12), FieldExtractor.dates("March 12, 2026", french = false).single().first)
    }

    @Test
    fun `without a total line the largest amount is offered with low confidence`() {
        val draft = FieldExtractor.extract(lines("Dépanneur Chez Ti-Guy\nCAFE 2,25\nMUFFIN 3,10"), today)
        assertEquals(cad("3.10"), draft.total?.value)
        assertTrue(draft.total!!.needsReview)
    }

    @Test
    fun `low recognition confidence and a wrong sum need review`() {
        val draft = FieldExtractor.extract(lines("Metro\nSOUS-TOTAL 10,00\nTPS 0,50\nTVQ 1,00\nTOTAL 12,00", 0.9f), today)
        assertTrue(draft.total!!.needsReview, "10 + 0,50 + 1,00 is not 12,00")
        assertFalse(FieldExtractor.extract(lines("Metro\nSOUS-TOTAL 10,00\nTPS 0,50\nTVQ 1,00\nTOTAL 11,50", 0.9f), today).total!!.needsReview)
        assertNull(FieldExtractor.extract(emptyList()).total)
    }

    @Test
    fun `US dollars`() {
        assertEquals(Currency.USD, FieldExtractor.extract(lines("Walmart Plattsburgh NY\nTOTAL USD 54.20"), today).currency)
    }
}
