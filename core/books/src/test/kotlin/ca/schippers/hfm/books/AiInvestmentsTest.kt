package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** INV-05: trade confirmations and investment statements read by AI, into the books; AI-03: a user's type kept as text. */
class AiInvestmentsTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(s: String) = LocalDate.parse(s)

    private val confirmation = """
        {"institution":"TD Direct Investing","account_number_last_digits":"5512","confirmation_number":"C-1","currency":"CAD",
         "trades":[
           {"action":"buy","trade_date":"2026-10-01","settlement_date":"2026-10-02","symbol":"XEQT","description":"iShares Core Equity ETF Portfolio",
            "quantity":100,"price":34.55,"gross_amount":3455.00,"commission":9.95,"net_amount":3464.95},
           {"action":"buy","trade_date":"2026-10-01","settlement_date":"2026-10-02","symbol":"ZAG","description":"BMO Aggregate Bond Index ETF",
            "quantity":50,"price":13.90,"gross_amount":695.00,"net_amount":695.00}]}
    """

    private val statement = """
        {"institution":"TD","account_number_last_digits":"5512","period_start":"2026-10-01","period_end":"2026-10-31","currency":"CAD",
         "opening_cash_balance":10000.00,"cash_balance":6552.05,"total_value":10012.05,
         "holdings":[{"symbol":"XEQT","description":"iShares Core Equity ETF Portfolio","quantity":100,"price":34.60,"market_value":3460.00}],
         "activity":[{"date":"2026-10-01","settlement_date":"2026-10-02","type":"buy","symbol":"XEQT","quantity":100,"price":34.55,"commission":9.95,"amount":-3464.95},
                     {"date":"2026-10-15","type":"dividend","symbol":"XEQT","description":"Dividend","amount":17.00,"tax_withheld":3.00},
                     {"date":"2026-10-20","type":"other","description":"Name change"}]}
    """

    private fun setUp(block: (Books, String, String) -> Unit) {
        val books = Books(store.create(temp.resolve("A.hfm"), "A", "perry", "Perry", "password1".toCharArray()).session)
        try {
            val group = books.groups().single().id
            val tfsa = books.accounts.create(AccountDraft(group, "TFSA Alex", AccountType.TFSA, Currency.CAD, cad("10000"), d("2026-01-01"), number = "TFSA-5512"))
            books.accounts.create(AccountDraft(group, "RRSP Sam", AccountType.RRSP, Currency.CAD, cad("0"), d("2026-01-01"), number = "RRSP-8820"))
            block(books, group, tfsa.id)
        } finally {
            books.session.close()
        }
    }

    private fun read(books: Books, group: String, typeId: String, answer: String): String {
        val doc = books.documents.import(group, (typeId + answer.length).encodeToByteArray(), "$typeId.pdf", "application/pdf").document
        books.ai.saveReading(doc.id, typeId, "hfm/$typeId/v1", answer, true, "claude-opus-5-5", DocumentDraft(DocumentKind.OTHER))
        return doc.id
    }

    @Test
    fun `a trade confirmation becomes its trades, once`() = setUp { books, group, tfsa ->
        val existing = books.investments.saveSecurity(Security("", "XEQT", "TSX", "iShares Core Equity ETF Portfolio", SecurityKind.ETF, Currency.CAD))
        val doc = read(books, group, "trade_confirmation", confirmation)
        assertEquals(tfsa, books.ai.suggestInvestmentAccount(doc), "the account whose number ends in 5512")

        val result = books.ai.importInvestments(doc, tfsa)
        assertEquals(2, result.added)
        assertEquals(1, result.securitiesCreated, "ZAG is new; XEQT is the security already there")
        assertEquals(false, result.statementSaved)
        val txns = books.investments.transactions(tfsa)
        val xeqt = txns.single { it.securityId == existing.id }
        assertEquals(InvestmentKind.BUY, xeqt.kind)
        assertEquals(d("2026-10-01"), xeqt.date, "on the trade date")
        assertEquals(0, BigDecimal("100").compareTo(xeqt.quantity))
        assertEquals(0, BigDecimal("34.55").compareTo(xeqt.price))
        assertEquals(cad("3455.00"), xeqt.amount)
        assertEquals(cad("9.95"), xeqt.fees)
        assertTrue(xeqt.memo!!.contains("Settles 2026-10-02") && xeqt.memo.contains("C-1"), xeqt.memo)
        assertEquals(cad("-4159.95"), txns.map { it.cashEffect }.reduce(Money::plus))

        val again = books.ai.importInvestments(doc, tfsa)
        assertEquals(0, again.added)
        assertEquals(2, again.alreadyThere)
    }

    @Test
    fun `a trade entered by hand is matched, and a statement brings its activity and waits for the check`() = setUp { books, group, tfsa ->
        val xeqt = books.investments.saveSecurity(Security("", "XEQT", "TSX", "iShares Core Equity ETF Portfolio", SecurityKind.ETF, Currency.CAD))
        // Entered by hand on the settlement date, before the confirmation arrived.
        books.investments.save(InvestmentTxn("", tfsa, d("2026-10-02"), InvestmentKind.BUY, xeqt.id, BigDecimal("100"), BigDecimal("34.55"), cad("3455.00"), cad("9.95")))
        val confirmed = books.ai.importInvestments(read(books, group, "trade_confirmation", confirmation), tfsa)
        assertEquals(1, confirmed.added, "only ZAG")
        assertEquals(1, confirmed.alreadyThere)
        // Sell ZAG again by hand so the statement's holdings are XEQT alone.
        val zag = books.investments.findSecurity("ZAG", null)!!
        books.investments.save(InvestmentTxn("", tfsa, d("2026-10-03"), InvestmentKind.SELL, zag.id, BigDecimal("50"), BigDecimal("13.90"), cad("695.00")))

        val doc = read(books, group, "investment_statement", statement)
        val result = books.ai.importInvestments(doc, tfsa)
        assertEquals(1, result.added, "the dividend; the purchase is the one entered by hand")
        assertEquals(1, result.alreadyThere)
        assertTrue(result.warnings.any { "Name change" in it }, result.warnings.toString())
        val dividend = books.investments.transactions(tfsa).single { it.kind == InvestmentKind.INCOME }
        assertEquals(cad("20.00"), dividend.amount, "the gross: 17.00 paid and 3.00 withheld")
        assertEquals(cad("3.00"), dividend.withheld)

        assertTrue(result.statementSaved)
        val id = assertNotNull(result.statementId)
        val check = books.investments.check(tfsa, id)
        assertEquals(cad("6552.05"), check.statement.cash)
        assertTrue(check.matches, "cash and holdings agree with the books: $check")
        assertEquals("AI", check.statement.source)
    }

    @Test
    fun `amounts in another currency than the account's are refused`() = setUp { books, group, tfsa ->
        val doc = read(books, group, "trade_confirmation", confirmation.replace("\"currency\":\"CAD\"", "\"currency\":\"USD\""))
        val e = assertFailsWith<ValidationException> { books.ai.importInvestments(doc, tfsa) }
        assertEquals("error.aiInvestmentCurrency", e.key)
        assertEquals(null, books.ai.suggestInvestmentAccount(doc), "no account in US dollars")
        val receipt = read(books, group, "receipt", """{"merchant":"X","date":"2026-10-01","total":1.00,"currency":"CAD"}""")
        assertEquals("error.aiNoStatement", assertFailsWith<ValidationException> { books.ai.importInvestments(receipt, tfsa) }.key)
    }

    @Test
    fun `a reading of a user's type is kept with the document's text and can be searched`() = setUp { books, group, _ ->
        val doc = books.documents.import(group, "tax".encodeToByteArray(), "tax.jpg", "image/jpeg").document
        books.documents.recordText(doc.id, 1, ca.schippers.hfm.ocr.OcrResult(listOf(ca.schippers.hfm.ocr.OcrLine("AVIS D'IMPOSITION", 0.9f)), 10), "paddle", d("2026-10-01"))
        val answer = """{"municipality":"Ville de Gatineau","roll_number":"1234-56"}"""
        books.ai.saveReading(doc.id, "property_tax", "user/property_tax/v1", answer, false, "claude-opus-5-5", DocumentDraft(DocumentKind.OTHER), "Municipality: Ville de Gatineau\nRoll number: 1234-56")
        assertEquals(listOf(doc.id), books.documents.search(DocumentQuery(text = "1234-56")).map { it.id })
        assertEquals("AVIS D'IMPOSITION", DocumentService.recognisedOnly(books.documents.get(doc.id).text), "the recognised text stays")

        // Read again as a shipped type: the earlier fields leave the text.
        books.ai.saveReading(doc.id, "bill", "hfm/bill/v1", """{"biller":"Gatineau","amount_due":1.00,"currency":"CAD"}""", true, "claude-opus-5-5", DocumentDraft(DocumentKind.BILL))
        assertEquals("AVIS D'IMPOSITION", books.documents.get(doc.id).text)
        assertEquals(emptyList(), books.documents.search(DocumentQuery(text = "1234-56")))
    }
}
