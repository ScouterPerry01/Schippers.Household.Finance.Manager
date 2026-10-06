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

    @Test
    fun `Phase 5 records leave no name, number, amount, note or file name in the audit log`() {
        val group = books.groups().single().id
        // Contacts (CON-01 to CON-07): saved, a number revealed, two merged, one deleted.
        val doctor = books.contacts.save(
            Contact(
                "", group, "Dr Tremblay Secretname", person = true, purpose = "Sam dermatology",
                details = listOf(ContactDetail(type = DetailType.NUMBER, label = "Patient", value = "123456789")), notes = "typed contact note",
            ),
        )
        books.contacts.revealNumber(doctor.id, books.contacts.get(doctor.id).numbers.single().id, "pw".toCharArray())
        val twin = books.contacts.save(Contact("", group, "Dr Tremblay Secretname", person = true))
        books.contacts.merge(doctor.id, twin.id, MergeChoices())
        books.contacts.delete(books.contacts.save(Contact("", group, "Gone Contactname")).id)
        // Rates and rules: a household value with a typed note.
        books.rateRules.add("threshold.budgetAlert", null, LocalDate(2026, 1, 1), "87.65", "typed rule note")
        // Category rules: a payee text and an amount range.
        val groceries = books.categories.list().first { it.kind == CategoryKind.EXPENSE && it.parentId != null }
        books.rules.delete(books.rules.create("Costco Secretstore", groceries.id, amountMin = Money.parse("777.77", Currency.CAD)).id)
        // AI reading: settings and a reading kept with its document, whose file name the user chose.
        books.ai.saveSettings(AiSettings(enabled = true))
        val doc = books.documents.import(group, "receipt".encodeToByteArray(), "Perry private receipt.jpg", "image/jpeg").document
        books.ai.saveReading(doc.id, "receipt", "hfm/receipt/v1", """{"merchant":"Secretstore","total":55.55}""", true, "claude-opus-5-5", ca.schippers.hfm.ocr.DocumentDraft())

        val details = books.session.core.coreQueries.recentAudit(200).executeAsList().mapNotNull { it.details }
        for (secret in listOf("Tremblay", "Secretname", "123456789", "dermatology", "typed", "87.65", "8765", "Costco", "Secretstore", "777.77", "77777", "55.55", "private receipt")) {
            assertTrue(details.none { secret in it }, "\"$secret\" is not in the audit log: $details")
        }
    }
}
