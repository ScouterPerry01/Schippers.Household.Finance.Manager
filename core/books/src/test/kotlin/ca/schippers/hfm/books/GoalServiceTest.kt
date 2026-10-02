package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var savings: Account
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("G.hfm"), "G", "perry", "Perry", "pw".toCharArray()).session)
        val group = books.groups().single().id
        savings = books.accounts.create(AccountDraft(group, "Épargne", AccountType.HIGH_INTEREST_SAVINGS, Currency.CAD, cad("8000"), d(1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun trip() = books.goals.save(
        SavingsGoal("", savings.id, "Voyage en Gaspésie", cad("3000"), d(12, 31), cad("250"), Recurrence.MONTHLY, d(1, 15)),
    )

    @Test
    fun `scheduled set-asides are entered once, up to the target`() {
        val goal = trip()
        assertEquals(9, books.goals.postScheduled(d(9, 20)), "January 15 to September 15")
        assertEquals(0, books.goals.postScheduled(d(9, 20)), "nothing twice")
        val p = books.goals.progress(goal, d(9, 20))
        assertEquals(cad("2250"), p.saved)
        assertEquals(75, p.percent)
        assertEquals(cad("750"), p.remaining)

        books.goals.postScheduled(d(12, 31))
        assertEquals(cad("3000"), books.goals.progress(goal, d(12, 31)).saved, "stops at the target")
        assertTrue(books.goals.progress(goal, d(12, 31)).reached)
    }

    @Test
    fun `progress says what is needed and whether the plan is on track`() {
        val goal = trip()
        books.goals.postScheduled(d(3, 1)) // January and February
        val p = books.goals.progress(goal, d(3, 1))
        assertEquals(cad("500"), p.saved)
        assertEquals(d(12, 15), p.projectedDate, "ten more set-asides of 250")
        assertEquals(true, p.onTrack)
        assertEquals(cad("250"), p.neededPerPeriod, "2,500 over the 10 remaining pay dates")

        val late = books.goals.save(books.goals.get(goal.id).copy(targetDate = d(6, 30)))
        val q = books.goals.progress(late, d(3, 1))
        assertEquals(false, q.onTrack)
        assertEquals(cad("625"), q.neededPerPeriod, "2,500 over March to June")
    }

    @Test
    fun `the account balance is divided into goals and the unassigned rest`() {
        val trip = trip()
        val car = books.goals.save(SavingsGoal("", savings.id, "Auto neuve", cad("15000")))
        books.goals.setAside(car.id, d(2, 1), cad("5000"))
        books.goals.postScheduled(d(2, 1))
        val view = books.goals.forAccount(savings.id, d(2, 1))
        assertEquals(cad("8000"), view.balance)
        assertEquals(cad("5250"), view.earmarked, "5,000 for the car and the January 15 set-aside")
        assertEquals(cad("2750"), view.unassigned)
        assertFalse(view.overCommitted)

        books.goals.setAside(car.id, d(2, 2), cad("3000"))
        assertTrue(books.goals.forAccount(savings.id, d(2, 2)).overCommitted, "8,250 earmarked in an 8,000 account")
        assertEquals(listOf(trip.id, car.id).sorted(), books.goals.forAccount(savings.id, d(2, 2)).goals.map { it.goal.id }.sorted())
    }

    @Test
    fun `moving between goals, spending and history`() {
        val trip = books.goals.save(SavingsGoal("", savings.id, "Voyage", cad("3000")))
        val car = books.goals.save(SavingsGoal("", savings.id, "Auto", cad("15000")))
        books.goals.setAside(trip.id, d(1, 10), cad("2000"))
        books.goals.move(trip.id, car.id, d(1, 11), cad("500"))
        books.goals.spend(trip.id, d(7, 1), cad("1200"), memo = "Hôtel")
        assertEquals(cad("300"), books.goals.progress(trip, d(7, 1)).saved)
        assertEquals(cad("500"), books.goals.progress(car, d(7, 1)).saved)
        assertEquals(listOf(GoalEntryKind.SPENT, GoalEntryKind.MOVE_OUT, GoalEntryKind.SET_ASIDE), books.goals.entries(trip.id).map { it.kind })
    }

    @Test
    fun `validation`() {
        assertFailsWith<ValidationException> { books.goals.save(SavingsGoal("", savings.id, " ", cad("10"))) }
        assertFailsWith<ValidationException> { books.goals.save(SavingsGoal("", savings.id, "X", cad("0"))) }
        assertFailsWith<ValidationException> { books.goals.save(SavingsGoal("", savings.id, "X", Money.parse("10", Currency.USD))) }
        assertFailsWith<ValidationException> { books.goals.save(SavingsGoal("", savings.id, "X", cad("10"), contribution = cad("5"))) }
        val goal = books.goals.save(SavingsGoal("", savings.id, "X", cad("10")))
        assertFailsWith<ValidationException> { books.goals.move(goal.id, goal.id, d(1, 1), cad("1")) }
    }
}
