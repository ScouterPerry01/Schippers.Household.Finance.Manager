package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SAL-02 and OTH-01: pay stubs split into gross pay and deductions, donations with their official receipts. */
class PayStubDonationTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        chequing = books.accounts.create(AccountDraft(books.groups().single().id, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private val stub
        get() = PayStub(
            "Maple Logistics", LocalDate(2026, 9, 25),
            listOf(PayEarning("Regular", cad("2884.62")), PayEarning("Overtime bonus", cad("150.00"))),
            listOf(
                PayDeduction(DeductionKind.INCOME_TAX, "Federal tax", cad("380.12")),
                PayDeduction(DeductionKind.INCOME_TAX, "Ontario tax", cad("160.40")),
                PayDeduction(DeductionKind.CPP_QPP, "CPP", cad("172.02")),
                PayDeduction(DeductionKind.EI_QPIP, "EI", cad("49.60")),
                PayDeduction(DeductionKind.UNION_DUES, "Union dues", cad("41.00")),
                PayDeduction(DeductionKind.CHARITY, "United Way", cad("10.00")),
            ),
            printedNet = cad("2221.48"),
        )

    @Test
    fun `a pay stub becomes one deposit of the net pay, split into gross pay and each deduction`() {
        assertFalse(stub.netDisagrees)
        val txn = books.payStubs.record(chequing.id, stub)
        assertEquals(cad("2221.48"), txn.amount)
        assertEquals("Maple Logistics", txn.payeeText)
        val byCategory = txn.splits.groupBy { it.categoryId }.mapValues { (_, s) -> s.map { it.amount }.reduce(Money::plus) }
        assertEquals(cad("2884.62"), byCategory[cat("income.employment.salary")])
        assertEquals(cad("150.00"), byCategory[cat("income.employment.bonus")], "a bonus goes to bonuses")
        assertEquals(cad("-540.52"), byCategory[cat("taxes.income_tax")])
        assertEquals(cad("-172.02"), byCategory[cat("payroll.cpp_qpp")])
        assertEquals(cad("-41.00"), byCategory[cat("work.union_dues")])

        assertTrue(stub.copy(printedNet = cad("2231.48")).netDisagrees)
        assertFailsWith<ValidationException> { books.payStubs.draft(chequing.id, stub.copy(deductions = listOf(PayDeduction(DeductionKind.OTHER, null, cad("5000"))))) }
        assertFailsWith<ValidationException> { books.payStubs.draft(chequing.id, stub.copy(employer = " ")) }
    }

    @Test
    fun `a pay stub read by AI comes in with its kinds and charity deductions found`() {
        val doc = books.documents.import(books.groups().single().id, "p".encodeToByteArray(), "p.jpg", "image/jpeg").document
        val answer = """{"employer":"Hôpital Sainte-Marie","pay_date":"2026-09-18","currency":"CAD","gross_pay":2500.00,
            |"deductions":[{"kind":"income_tax_quebec","description":"Impôt Québec","amount":310.5},{"kind":"qpp","description":"RRQ","amount":150.2},
            |{"kind":"qpip","description":"RQAP","amount":12.3},{"kind":"other","description":"Centraide","amount":5}],"net_pay":2022.0}""".trimMargin()
        books.ai.saveReading(doc.id, "pay_stub", "hfm/pay_stub/v1", answer, true, "claude-opus-5-5", DocumentDraft(DocumentKind.PAY_STUB))
        val read = books.ai.payStub(doc.id, Currency.CAD)!!
        assertEquals(listOf(DeductionKind.INCOME_TAX, DeductionKind.CPP_QPP, DeductionKind.EI_QPIP, DeductionKind.CHARITY), read.deductions.map { it.kind })
        assertEquals(cad("2500.00"), read.gross, "without earnings lines the gross pay is one line")
        assertFalse(read.netDisagrees)
        val txn = books.payStubs.record(chequing.id, read, documentId = doc.id)
        assertEquals(listOf(doc.id), books.documents.documentsFor(DocumentEntity.TRANSACTION, txn.id).map { it.id })
    }

    @Test
    fun `donations come from flagged categories, with receipts, eligible amounts and totals per person`() {
        val member = books.members.create("Sam", ca.schippers.hfm.domain.MemberKind.ADULT).id
        val food = books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 3, 2), cad("-100.00"), "Food Bank", listOf(SplitDraft(cat("gifts.charity"), cad("-100.00"))), memberId = member))
        val gala = books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 5, 9), cad("-250.00"), "Hospital Foundation", listOf(SplitDraft(cat("gifts.charity"), cad("-250.00")))))
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2026, 6, 1), cad("-75.00"), "Party", listOf(SplitDraft(cat("gifts.political"), cad("-75.00")))))
        books.transactions.create(TransactionDraft(chequing.id, LocalDate(2025, 12, 31), cad("-40.00"), "Last year", listOf(SplitDraft(cat("gifts.charity"), cad("-40.00")))))
        books.payStubs.record(chequing.id, stub, member)

        val gifts = books.donations.list(2026)
        assertEquals(listOf("Food Bank", "Hospital Foundation", "Party", "Maple Logistics"), gifts.map { it.payee })
        assertEquals(TaxFlag.POLITICAL, gifts[2].kind)
        assertEquals(cad("10.00"), gifts[3].amount, "a payroll charity deduction counts")
        assertTrue(gifts[3].payroll && gifts[3].hasReceipt, "its receipt is the T4")
        assertEquals("United Way", gifts[3].memo)

        books.donations.setReceipt(gala.id, DonationReceipt("Hospital Foundation", "1234 56789 rr 0001", "G-77", cad("150.00"), received = true))
        assertEquals("123456789RR0001", books.donations.list(2026)[1].receipt!!.registration)
        assertFailsWith<ValidationException> { books.donations.setReceipt(gala.id, DonationReceipt(registration = "12345")) }
        assertFailsWith<ValidationException> { books.donations.setReceipt(gala.id, DonationReceipt(eligible = cad("300.00"))) }

        val doc = books.documents.import(books.groups().single().id, "r".encodeToByteArray(), "r.pdf", "application/pdf").document
        books.documents.fileWithTransaction(doc.id, food.id)
        val totals = books.donations.totals(2026).associateBy { it.memberId }
        assertEquals(cad("110.00"), totals.getValue(member).charitable)
        assertEquals(0, totals.getValue(member).missingReceipts, "the food bank has its receipt filed, the payroll gift is on the T4")
        assertEquals(cad("150.00"), totals.getValue(null).charitable, "the eligible amount, not what was paid")
        assertEquals(cad("75.00"), totals.getValue(null).political)
    }
}
