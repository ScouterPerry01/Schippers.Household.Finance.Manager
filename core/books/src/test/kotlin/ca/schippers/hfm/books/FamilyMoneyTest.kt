package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
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

/** HH-04, LN-07, HH-03: shared expenses, family loans and allowances. */
class FamilyMoneyTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val group get() = books.groups().single().id

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("F.hfm"), "F", "perry", "Perry", "password1".toCharArray()).session)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `a trip's expenses are shared by weight, and settling up takes the fewest payments`() {
        val trip = books.sharedExpenses.save(null, group, "Trip to Quebec", Currency.CAD, listOf(SharePerson("", "Alex"), SharePerson("", "Sam"), SharePerson("", "Paul")))
        val (alex, sam, paul) = trip.people.map { it.id }
        val all = mapOf(alex to 1, sam to 1, paul to 1)
        books.sharedExpenses.saveEntry(trip.id, ShareEntry("", LocalDate(2026, 7, 1), "Hotel", cad("300.00"), alex, all))
        books.sharedExpenses.saveEntry(trip.id, ShareEntry("", LocalDate(2026, 7, 2), "Gas", cad("100.00"), paul, all))
        // Paul's family of two shares the dinner double.
        books.sharedExpenses.saveEntry(trip.id, ShareEntry("", LocalDate(2026, 7, 2), "Dinner", cad("90.00"), sam, mapOf(alex to 1, sam to 1, paul to 2)))
        val g = books.sharedExpenses.get(trip.id)
        // Alex: +300 -100 -33.34 -22.50 (the gas's odd cent is the first person's); Sam: +90 -100 -33.33 -22.50; Paul: +100 -100 -33.33 -45.
        assertEquals(mapOf(alex to cad("144.16"), sam to cad("-65.83"), paul to cad("-78.33")), books.sharedExpenses.balances(g))
        assertEquals(cad("0.00"), books.sharedExpenses.balances(g).values.reduce(Money::plus), "the balances add up to nothing")
        val plan = books.sharedExpenses.settleUp(g)
        assertEquals(listOf(Settlement(paul, alex, cad("78.33")), Settlement(sam, alex, cad("65.83"))), plan)

        plan.forEach { books.sharedExpenses.saveEntry(trip.id, ShareEntry("", LocalDate(2026, 7, 10), "Settle", it.amount, it.from, settlesTo = it.to)) }
        assertEquals(emptyList(), books.sharedExpenses.settleUp(books.sharedExpenses.get(trip.id)))
        assertFailsWith<ValidationException> { books.sharedExpenses.saveEntry(trip.id, ShareEntry("", LocalDate(2026, 7, 3), "Bad", cad("10.00"), alex, emptyMap())) }
        assertFailsWith<ValidationException>("someone with entries stays") {
            books.sharedExpenses.save(trip.id, group, "Trip", Currency.CAD, g.people.filter { it.id != paul })
        }
    }

    @Test
    fun `a family loan charges simple interest on what is still owed, payments to interest first`() {
        val loan = books.familyLoans.save(FamilyLoan("", group, "Mom and Dad", "Maya", cad("10000.00"), LocalDate(2026, 1, 1), 365, null, false, emptyList()))
        // 3.65 % a year is a dollar a day on $10,000.
        books.familyLoans.addPayment(loan, LocalDate(2026, 1, 31), cad("1030.00"))
        val l = books.familyLoans.list().single()
        val s = books.familyLoans.status(l, LocalDate(2026, 3, 2))
        assertEquals(cad("30.00") + cad("27.00"), s.interest, "30 days on 10,000, then 30 days on 9,000")
        assertEquals(cad("9000.00"), s.principalLeft)
        assertEquals(cad("9027.00"), s.balance)
        val free = books.familyLoans.save(FamilyLoan("", group, "Alex", "Sam", cad("500.00"), LocalDate(2026, 5, 1), 0, null, false, emptyList()))
        books.familyLoans.addPayment(free, LocalDate(2026, 6, 1), cad("200.00"))
        assertEquals(cad("300.00"), books.familyLoans.status(books.familyLoans.list().first { it.id == free.id }, LocalDate(2026, 10, 1)).balance)
        assertFailsWith<ValidationException> { books.familyLoans.addPayment(free, LocalDate(2026, 4, 1), cad("10.00")) }
    }

    @Test
    fun `an allowance is owed until paid, and the child's money adds up`() {
        val maya = books.members.create("Maya", MemberKind.CHILD).id
        val a = books.allowances.save(Allowance("", group, maya, cad("10.00"), AllowanceFrequency.WEEKLY, LocalDate(2026, 9, 6), null, null, emptyList()))
        books.allowances.addEntry(a, LocalDate(2026, 9, 6), cad("10.00"), AllowanceKind.PAID)
        books.allowances.addEntry(a, LocalDate(2026, 9, 13), cad("10.00"), AllowanceKind.PAID)
        books.allowances.addEntry(a, LocalDate(2026, 9, 20), cad("15.00"), AllowanceKind.EARNED, "Raked leaves")
        books.allowances.addEntry(a, LocalDate(2026, 9, 25), cad("12.50"), AllowanceKind.SPENT, "Book")
        val s = books.allowances.status(books.allowances.list().single(), LocalDate(2026, 10, 5))
        assertEquals(cad("50.00"), s.due, "five Sundays from Sept 6 to Oct 4")
        assertEquals(cad("30.00"), s.owed)
        assertEquals(cad("22.50"), s.balance)
        assertEquals(LocalDate(2026, 10, 11), s.nextDate)

        val monthly = books.allowances.save(Allowance("", group, maya, cad("20.00"), AllowanceFrequency.MONTHLY, LocalDate(2026, 1, 31), null, null, emptyList()))
        assertEquals(LocalDate(2026, 3, 31), books.allowances.dates(monthly, LocalDate(2026, 3, 31)).last(), "a month-end allowance stays at the month's end")
    }
}
