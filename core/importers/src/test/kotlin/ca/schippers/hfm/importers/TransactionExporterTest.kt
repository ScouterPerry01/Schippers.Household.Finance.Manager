package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** EXP-02: selected transactions as CSV, QIF and OFX, read back by the app's own importers. */
class TransactionExporterTest {

    private val groceries = ExportTransaction(
        "t1", LocalDate(2026, 3, 14), "IGA Extra", "Semaine; lait & œufs", BigDecimal("-84.37"), ExportCleared.CLEARED,
        listOf("Food", "Groceries"), null, tags = listOf("Cottage"),
    )
    private val split = ExportTransaction(
        "t2", LocalDate(2026, 3, 20), "Costco", null, BigDecimal("-150.00"), ExportCleared.RECONCILED, null, null,
        listOf(ExportSplit(listOf("Food", "Groceries"), null, BigDecimal("-100.00"), "Food"), ExportSplit(listOf("Household"), null, BigDecimal("-50.00"), null)),
    )
    private val pay = ExportTransaction("t3", LocalDate(2026, 3, 31), "Employer <Inc>", null, BigDecimal("2500.00"), ExportCleared.UNCLEARED, null, "Savings")
    private val all = listOf(groceries, split, pay)

    @Test
    fun `CSV quotes what needs it and follows the language's separators`() {
        val headers = listOf("Date", "Payee", "Category", "Memo", "Amount", "Currency", "Cleared", "Tags")
        val en = TransactionExporter.csv(all, "CAD", headers).lines()
        assertEquals("Date,Payee,Category,Memo,Amount,Currency,Cleared,Tags", en[0])
        assertEquals("2026-03-14,IGA Extra,Food:Groceries,Semaine; lait & œufs,-84.37,CAD,c,Cottage", en[1])
        assertEquals("2026-03-20,Costco,Food:Groceries | Household,,-150.00,CAD,R,", en[2])
        assertEquals("2026-03-31,Employer <Inc>,[Savings],,2500.00,CAD,,", en[3])
        val fr = TransactionExporter.csv(all, "CAD", headers, separator = ';', decimalComma = true).lines()
        assertEquals("2026-03-14;IGA Extra;Food:Groceries;\"Semaine; lait & œufs\";-84,37;CAD;c;Cottage", fr[1])
    }

    @Test
    fun `QIF is read back with categories, splits, transfers and cleared marks`() {
        val file = QifParser.parse(TransactionExporter.qif(all, "Joint chequing", ExportAccountKind.BANK))
        assertEquals(listOf("Joint chequing"), file.accounts.map { it.name })
        assertEquals(QifAccountKind.BANK, file.accounts.single().kind)
        val (a, b, c) = file.transactions
        assertEquals(LocalDate(2026, 3, 14), a.date.toLocalDate(file.dateOrder ?: DateOrder.MONTH_DAY))
        assertEquals(BigDecimal("-84.37"), a.amount)
        assertEquals("IGA Extra", a.payee)
        assertEquals(listOf("Food", "Groceries"), a.category)
        assertEquals('*', a.cleared)
        assertEquals('X', b.cleared)
        assertEquals(listOf(BigDecimal("-100.00"), BigDecimal("-50.00")), b.splits.map { it.amount })
        assertEquals(listOf(listOf("Food", "Groceries"), listOf("Household")), b.splits.map { it.category })
        assertEquals("Savings", c.transferAccount)
    }

    @Test
    fun `OFX is read back as a statement, with the transaction ids as FITID`() {
        val text = TransactionExporter.ofx(all, "acct-1", "CAD", ExportAccountKind.BANK, BigDecimal("1234.56"), LocalDate(2026, 4, 1))
        val statement = OfxImporter().read(text.byteInputStream()).single()
        assertEquals(Currency.CAD, statement.currency)
        assertEquals(listOf("t1", "t2", "t3"), statement.lines.map { it.externalId })
        assertEquals(Money.parse("-84.37", Currency.CAD), statement.lines.first().amount)
        assertEquals("Employer <Inc>", statement.lines.last().payee)
        assertEquals(Money.parse("1234.56", Currency.CAD), statement.closingBalance)
        assertTrue("<CCSTMTRS>" in TransactionExporter.ofx(all, "card", "CAD", ExportAccountKind.CREDIT_CARD, BigDecimal.ZERO, LocalDate(2026, 4, 1)))
    }
}
