package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scheduled reports, price downloads and phone transfers use the same books as the screens, from
 * other threads. SQLDelight's JDBC driver gives each thread its own SQLite connection (its
 * transactions stay on that thread), the databases are in WAL mode and writers wait for one
 * another (busy_timeout): a report read while the screen writes sees whole transactions only.
 */
class ConcurrentAccessTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `a report made on another thread while the screen writes`() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        val books = Books(store.create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        try {
            val group = books.groups().single().id
            val account = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, Money.parse("0", Currency.CAD), LocalDate(2026, 1, 1)))
            val food = books.categories.list().first { it.systemKey == "food.groceries" }.id
            val errors = Collections.synchronizedList(ArrayList<Throwable>())
            val start = CountDownLatch(1)
            val writer = thread {
                start.await()
                runCatching {
                    repeat(60) { i ->
                        val amount = Money.parse("-10.00", Currency.CAD)
                        books.transactions.create(TransactionDraft(account.id, LocalDate(2026, 3, 1 + i % 28), amount, "IGA", listOf(SplitDraft(food, amount))))
                    }
                }.exceptionOrNull()?.let(errors::add)
            }
            val totals = Collections.synchronizedList(ArrayList<Money>())
            val reader = thread {
                start.await()
                runCatching {
                    repeat(60) {
                        val rows = books.reports.byCategory(ReportFilter(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31)), ca.schippers.hfm.domain.CategoryKind.EXPENSE).value
                        totals += rows.fold(Money.zero(Currency.CAD)) { a, r -> a + r.amount }
                    }
                }.exceptionOrNull()?.let(errors::add)
            }
            start.countDown()
            writer.join()
            reader.join()
            assertTrue(errors.isEmpty(), "no thread failed: $errors")
            // Each total is a whole number of transactions of $10, never a half-written one.
            assertTrue(totals.all { it.minorUnits % 1000 == 0L }, "$totals")
            assertEquals(60L, books.transactions.count(account.id))
        } finally {
            books.session.close()
        }
    }
}
