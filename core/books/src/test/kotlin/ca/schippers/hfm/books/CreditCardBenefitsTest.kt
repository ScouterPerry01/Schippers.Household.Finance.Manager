package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreditCardBenefitsTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var visa: Account
    private lateinit var chequing: Account
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val cards get() = books.creditCards

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("C.hfm"), "C", "perry", "Perry", "pw".toCharArray()).session)
        val group = books.groups().single().id
        visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), d("2026-01-01")))
        chequing = books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("5000"), d("2026-01-01")))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `supplementary cards and spending by cardholder`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        val main = cards.saveHolder(CardHolder("", visa.id, "Alex", alex.id, "4321"))
        assertTrue(main.isPrimary, "the first cardholder is the main one")
        val sam = cards.saveHolder(CardHolder("", visa.id, "Sam", lastDigits = "1234"))
        assertEquals(listOf(main.id, sam.id), cards.holders(visa.id).map { it.id })
        assertFailsWith<ValidationException> { cards.saveHolder(CardHolder("", visa.id, "Léa", lastDigits = "12")) }

        fun buy(date: String, amount: String, holder: CardHolder?) =
            books.transactions.create(TransactionDraft(visa.id, d(date), cad(amount), "Store", cardHolderId = holder?.id))
        val samPurchase = buy("2026-02-01", "-100", sam)
        buy("2026-02-02", "20", sam) // a refund
        buy("2026-02-03", "-50", main)
        buy("2026-02-04", "-25", null) // no card given: the main cardholder's
        books.transactions.transfer(TransferDraft(chequing.id, visa.id, d("2026-02-20"), cad("500"))) // a payment
        assertEquals(sam.id, books.transactions.get(samPurchase.id).cardHolderId)
        val spending = cards.spendingByHolder(visa.id, d("2026-02-01"), d("2026-02-28"))
        assertEquals(listOf(sam.id to cad("80"), main.id to cad("75")), spending.map { it.holder!!.id to it.spent })

        // Sam becomes the main cardholder when chosen; a cardholder already used is archived, not deleted.
        cards.saveHolder(sam.copy(isPrimary = true))
        assertEquals(listOf(sam.id), cards.holders(visa.id).filter { it.isPrimary }.map { it.id })
        cards.deleteHolder(visa.id, main.id)
        assertEquals(listOf(sam.id), cards.holders(visa.id).map { it.id })
        assertTrue(cards.holders(visa.id, includeArchived = true).single { it.id == main.id }.archived)
    }

    @Test
    fun `benefits that cover a purchase, and the annual fee reminder`() {
        cards.saveBenefit(CardBenefit("", visa.id, BenefitKind.PURCHASE_PROTECTION, days = 90, limit = cad("1000")))
        cards.saveBenefit(CardBenefit("", visa.id, BenefitKind.EXTENDED_WARRANTY, months = 12, maxYears = 5))
        cards.saveBenefit(CardBenefit("", visa.id, BenefitKind.TRAVEL_MEDICAL, days = 15))
        val tv = books.transactions.create(TransactionDraft(visa.id, d("2026-01-10"), cad("-899.99"), "Best Buy"))

        val march = cards.coverage(tv, d("2026-03-01"))
        assertEquals(mapOf(BenefitKind.PURCHASE_PROTECTION to d("2026-04-10"), BenefitKind.EXTENDED_WARRANTY to null), march.associate { it.benefit.kind to it.until })
        assertEquals(listOf(BenefitKind.EXTENDED_WARRANTY), cards.coverage(tv, d("2026-05-01")).map { it.benefit.kind }, "protection over after 90 days")
        // Groceries are not goods the protection covers; a split with something that is, is listed.
        books.transactions.create(TransactionDraft(visa.id, d("2026-01-12"), cad("-80"), "IGA", listOf(SplitDraft(books.categories.list().first { it.systemKey == "food.groceries" }.id, cad("-80")))))
        assertEquals(listOf(tv.id), cards.protectedPurchases(visa.id, d("2026-03-01")).map { it.first.id })
        assertTrue(cards.protectedPurchases(visa.id, d("2026-05-01")).isEmpty())

        cards.saveTerms(visa.id, CreditCardTerms(annualFee = cad("120"), annualFeeDate = d("2025-11-15")))
        val reminder = books.renewals(d("2026-10-20")).single { it.kind == RenewalKind.CARD_ANNUAL_FEE }
        assertEquals(d("2026-11-15"), reminder.date)
        assertEquals(26, reminder.daysLeft)
        assertNull(books.renewals(d("2026-06-01")).firstOrNull { it.kind == RenewalKind.CARD_ANNUAL_FEE })
    }

    @Test
    fun `available credit and the share of the limit used`() {
        val terms = CreditCardTerms(creditLimit = cad("5000"))
        assertEquals(cad("3800"), terms.availableCredit(cad("1200")))
        assertEquals(24, terms.limitUsedPercent(cad("1200")))
        assertEquals(cad("-100"), terms.availableCredit(cad("5100")), "over the limit")
        assertEquals(0, terms.limitUsedPercent(cad("-50")), "a credit balance uses none of it")
        assertNull(CreditCardTerms().availableCredit(cad("1200")))
    }

    @Test
    fun `payment due dates come back every month, as reminders and on the calendar`() {
        assertEquals(
            listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31")),
            CreditCardTerms(dueDay = 31).dueDates(d("2026-01-15"), d("2026-04-15")),
            "in a shorter month the payment is due on its last day",
        )
        val noon = java.time.LocalDate.of(2026, 2, 10).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val feb10 = Books(books.session) { noon }
        feb10.creditCards.saveTerms(visa.id, CreditCardTerms(dueDay = 15, cashAdvanceRate = java.math.BigDecimal("0.2299")))
        assertTrue(feb10.renewals(d("2026-02-10")).none { it.kind == RenewalKind.CARD_PAYMENT_DUE }, "nothing owed, nothing to pay")

        feb10.transactions.create(TransactionDraft(visa.id, d("2026-02-01"), cad("-250"), "Store"))
        val due = feb10.renewals(d("2026-02-10")).single { it.kind == RenewalKind.CARD_PAYMENT_DUE }
        assertEquals(visa.id to d("2026-02-15"), due.subjectId to due.date)
        assertEquals(5, due.daysLeft)
        assertTrue(feb10.renewals(d("2026-02-01")).none { it.kind == RenewalKind.CARD_PAYMENT_DUE }, "announced a week ahead")
        val march = feb10.calendar.items(d("2026-03-01"), d("2026-03-31")).filterIsInstance<CalendarItem.Renewal>().map { it.renewal }
        assertEquals(listOf(d("2026-03-15")), march.filter { it.kind == RenewalKind.CARD_PAYMENT_DUE }.map { it.date })

        // The cash advance rate is in the debt summary.
        assertEquals(java.math.BigDecimal("0.2299"), feb10.loans.debtSummary(d("2026-02-10")).single { it.account.id == visa.id }.cashAdvanceRate)
    }
}
