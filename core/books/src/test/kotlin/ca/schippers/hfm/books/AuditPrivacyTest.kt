package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The audit log lives in core.db, which every user of the household can read, so it records what
 * happened but never amounts or text the user typed (owner's rule; Phase 3 and 4 security reviews).
 */
class AuditPrivacyTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("A.hfm"), "A", "perry", "Perry", "pw".toCharArray()).session)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `budgets and exports leave no amount or file name in the audit log`() {
        val groceries = books.categories.list().first { it.kind == CategoryKind.EXPENSE && it.parentId != null }
        books.budgets.set(groceries.id, BudgetPeriod.MONTHLY, Money.parse("1234.56", Currency.CAD), startMonth = LocalDate(2026, 10, 1))
        books.backups.exportAll(temp.resolve("Perry private export.zip"))

        val details = books.session.core.coreQueries.recentAudit(100).executeAsList().mapNotNull { it.details }
        assertTrue(details.none { "1234" in it || "1 234" in it }, "no amount: $details")
        assertTrue(details.none { "private export" in it }, "no file name typed by the user: $details")
    }
}
