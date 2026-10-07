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
    fun `an Ontario electricity bill with statement number, issue date and meter readings (BILL-19)`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Hydro Ottawa
                Your electricity bill
                Account number: 6 1234 5678 9
                Statement number: 2026-0914-1187
                Issue date: Sep 14, 2026
                Previous reading (Aug 12, 2026): 45 678
                Current reading (Sep 11, 2026): 46 321
                Electricity used 643 kWh
                Amount due $138.91
                Due date: Oct 6, 2026
                """,
            ),
            today,
        )
        assertEquals(DocumentKind.BILL, draft.kind)
        assertEquals("6 1234 5678 9", draft.accountNumber?.value)
        assertEquals("2026-0914-1187", draft.invoiceNumber?.value)
        assertEquals(LocalDate(2026, 9, 14), draft.date?.value, "the issue date, not a reading's date")
        assertEquals(LocalDate(2026, 10, 6), draft.dueDate?.value)
        assertEquals(cad("138.91"), draft.total?.value)
        val m = draft.meter!!.value
        assertEquals(BigDecimal(45678), m.previous)
        assertEquals(LocalDate(2026, 8, 12), m.previousDate)
        assertEquals(BigDecimal(46321), m.current)
        assertEquals(LocalDate(2026, 9, 11), m.currentDate)
        assertEquals(BigDecimal(643), m.used)
        assertEquals("KWH", m.unit)
    }

    @Test
    fun `a Hydro-Quebec bill in French with readings, issue date and bill number (BILL-19)`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Hydro-Québec
                Votre facture d'électricité
                Numéro de compte : 2992 0471 6553
                Numéro de la facture : 630122448
                Date d'émission : 15 septembre 2026
                Relevé précédent : 2026-07-13  45 678
                Relevé actuel : 2026-09-11  47 520
                Consommation 1 842 kWh
                Montant à payer 142,37 $
                Date d'échéance : 2026-10-06
                """,
            ),
            today,
        )
        assertEquals("2992 0471 6553", draft.accountNumber?.value)
        assertEquals("630122448", draft.invoiceNumber?.value)
        assertEquals(LocalDate(2026, 9, 15), draft.date?.value)
        assertEquals(LocalDate(2026, 10, 6), draft.dueDate?.value)
        val m = draft.meter!!.value
        assertEquals(BigDecimal(45678), m.previous)
        assertEquals(LocalDate(2026, 7, 13), m.previousDate)
        assertEquals(BigDecimal(47520), m.current)
        assertEquals(LocalDate(2026, 9, 11), m.currentDate)
        assertEquals(BigDecimal(1842), m.used)
        assertEquals(m.used, m.usedOrComputed)
    }

    @Test
    fun `a gas bill with readings dated without their year, in cubic metres (BILL-19)`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Enbridge Gas
                Account Number 9100 2233 4455
                Bill Date Sep 18, 2026
                Meter Reading
                Previous Reading Jul 17 ACTUAL 12,345
                Current Reading Aug 18 ACTUAL 12,456
                Gas Used 111 m³
                Amount Due $86.40
                Due Date Oct 9, 2026
                """,
            ),
            today,
        )
        assertEquals("9100 2233 4455", draft.accountNumber?.value)
        assertEquals(LocalDate(2026, 9, 18), draft.date?.value)
        val m = draft.meter!!.value
        assertEquals(BigDecimal(12345), m.previous)
        assertEquals(LocalDate(2026, 7, 17), m.previousDate)
        assertEquals(BigDecimal(12456), m.current)
        assertEquals(LocalDate(2026, 8, 18), m.currentDate)
        assertEquals(BigDecimal(111), m.used)
        assertEquals("M3", m.unit)
    }

    @Test
    fun `a French telecom bill has a statement number and no meter (BILL-19)`() {
        val draft = FieldExtractor.extract(
            lines(
                """
                Vidéotron
                Votre facture
                Numéro de compte : 000 123 456
                Numéro de relevé : R-88213
                Date de facturation : 20 septembre 2026
                Montant à payer 95,00 $
                À payer au plus tard le 11 octobre 2026
                """,
            ),
            today,
        )
        assertEquals(DocumentKind.BILL, draft.kind)
        assertEquals("000 123 456", draft.accountNumber?.value)
        assertEquals("R-88213", draft.invoiceNumber?.value)
        assertEquals(LocalDate(2026, 9, 20), draft.date?.value)
        assertEquals(LocalDate(2026, 10, 11), draft.dueDate?.value)
        assertEquals(cad("95.00"), draft.total?.value)
        assertNull(draft.meter)
    }

    @Test
    fun `readings without a printed amount used give it as the difference`() {
        val m = MeterReadings(BigDecimal(100), null, BigDecimal(160), null)
        assertEquals(BigDecimal(60), m.usedOrComputed)
        assertNull(MeterReadings(BigDecimal(160), null, BigDecimal(100), null).usedOrComputed, "a reading lower than the previous one is not a use")
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

    @Test
    fun `statements, pay stubs and explanations of benefits are told apart, in English and French`() {
        fun kind(vararg rows: String) = FieldExtractor.extract(rows.map { OcrLine(it, 0.97f) }, today).kind
        assertEquals(DocumentKind.CARD_STATEMENT, kind("TD Visa", "Statement date 2026-09-20", "Previous balance 1,204.55", "New balance 980.10", "Minimum payment 10.00", "Credit limit 8,000.00"))
        assertEquals(DocumentKind.CARD_STATEMENT, kind("Visa Desjardins", "Solde précédent 1 204,55", "Nouveau solde 980,10", "Paiement minimum 10,00", "Limite de crédit 8 000,00"))
        assertEquals(DocumentKind.BANK_STATEMENT, kind("TD Canada Trust", "Account statement", "Opening balance 2,450.00", "Withdrawals  Deposits", "Closing balance 3,101.20"))
        assertEquals(DocumentKind.BANK_STATEMENT, kind("Desjardins", "Relevé de compte", "Solde d’ouverture 2 450,00", "Retraits  Dépôts", "Solde de fermeture 3 101,20"))
        assertEquals(DocumentKind.PAY_STUB, kind("Ville de Québec", "Talon de paie", "Salaire brut 3 150,00", "Retenues : impôt, RRQ, RQAP", "Salaire net 2 210,40"))
        assertEquals(DocumentKind.PAY_STUB, kind("Employer Inc.", "Pay statement", "Gross pay 3,150.00", "Deductions: tax, CPP, EI premium", "Net pay 2,210.40", "Year to date 41,000.00"))
        assertEquals(DocumentKind.EOB, kind("Manulife", "Explanation of benefits", "Claim number 88213", "Eligible amount 95.00", "Amount reimbursed 76.00"))
        assertEquals(DocumentKind.EOB, kind("Canada Vie", "Relevé de prestations", "Numéro de demande 88213", "Montant admissible 95,00", "Montant remboursé 76,00"))
        assertEquals(DocumentKind.INVESTMENT_STATEMENT, kind("TD Direct Investing", "Portfolio", "Holdings: XIC 300 units", "Book value 10,219.90", "Market value 11,955.00"))
        // A receipt that mentions a balance stays a receipt.
        assertEquals(DocumentKind.RECEIPT, kind("Starbucks", "CARD BALANCE 12.50", "New balance 7.90", "SUBTOTAL 4.60", "GST 0.23", "TOTAL 4.83"))
    }
}
