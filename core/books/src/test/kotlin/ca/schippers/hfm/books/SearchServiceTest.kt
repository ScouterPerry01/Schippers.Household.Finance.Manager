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
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchServiceTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(day: Int) = LocalDate(2026, 3, day)

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("S.hfm"), "S", "perry", "Perry", "pw".toCharArray()).session)
        val group = books.groups().single().id
        chequing = books.accounts.create(AccountDraft(group, "Compte chèques", AccountType.CHEQUING, Currency.CAD, cad("0"), d(1), notes = "Desjardins Lévis"))
        books.transactions.create(TransactionDraft(chequing.id, d(2), cad("-142.37"), "Épicerie Ste-Foy"))
        books.transactions.create(TransactionDraft(chequing.id, d(3), cad("-12.00"), "Café", memo = "réunion 100% budget"))
        books.transactions.create(TransactionDraft(chequing.id, d(4), cad("142.37"), "Remboursement"))
        books.bills.create(BillDraft(BillKind.BILL, "Hydro-Québec", cad("130"), chequing.id, Recurrence.MONTHLY, d(12)))
        books.institutions.create(Institution("", "Caisse Desjardins de Lévis"))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `names match without case or accents`() {
        val r = books.search.search("EPICERIE")
        assertEquals(listOf("Épicerie Ste-Foy"), r.transactions.map { it.payeeName })
        assertEquals(listOf("Épicerie"), r.categories.filter { it.systemKey == "food.groceries" }.map { it.nameFr })
        assertEquals(listOf("Compte chèques"), books.search.search("cheques").accounts.map { it.name })
        assertEquals(listOf("Hydro-Québec"), books.search.search("hydro-quebec").bills.map { it.name })
        assertEquals(1, books.search.search("levis").institutions.size)
        assertEquals(1, books.search.search("levis").accounts.size, "account notes are searched")
    }

    @Test
    fun `amounts find money in and out, in either language's format`() {
        val french = books.search.search("142,37", Locale.CANADA_FRENCH).transactions.map { it.transaction.amount }
        assertEquals(setOf(cad("-142.37"), cad("142.37")), french.toSet())
        assertEquals(2, books.search.search("142.37 $", Locale.CANADA).transactions.size)
    }

    @Test
    fun `memos are searched and LIKE wildcards are literal`() {
        assertEquals(listOf("Café"), books.search.search("100% budget").transactions.map { it.payeeName })
        assertTrue(books.search.search("1_0").transactions.isEmpty())
    }

    @Test
    fun `short or empty queries return nothing`() {
        assertTrue(books.search.search("a").isEmpty)
        assertTrue(books.search.search("   ").isEmpty)
        assertTrue(books.search.search("zzzz").isEmpty)
    }
}
