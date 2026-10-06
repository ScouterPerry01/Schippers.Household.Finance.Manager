package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ACC-06: low-balance, card limit and unusual-activity alerts per account. */
class AccountAlertServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var today: LocalDate
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun daysAgo(n: Int) = today.minus(DatePeriod(days = n))
    private val alerts get() = books.accountAlerts

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("A.hfm"), "A", "perry", "Perry", "password1".toCharArray()).session)
        group = books.groups().single().id
        today = books.today()
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun account(name: String, type: AccountType, opening: String) =
        books.accounts.create(AccountDraft(group, name, type, Currency.CAD, cad(opening), today.minus(DatePeriod(years = 2))))

    @Test
    fun `alerts are off by default and a low balance warns once set`() {
        val chequing = account("Chequing", AccountType.CHEQUING, "600")
        books.transactions.create(TransactionDraft(chequing.id, daysAgo(1), cad("-200"), "Hydro"))
        assertFalse(alerts.settings(chequing.id).anyOn)
        assertTrue(alerts.alerts(today).isEmpty(), "nothing is checked until asked")

        alerts.save(AlertSettings(chequing.id, lowBalance = cad("500")))
        val low = alerts.alerts(today).single()
        assertEquals(AccountAlertKind.LOW_BALANCE, low.kind)
        assertEquals(cad("400"), low.amount)
        assertEquals(cad("500"), low.threshold)
        assertFalse(low.dismissible)

        books.transactions.create(TransactionDraft(chequing.id, today, cad("150"), "Paie"))
        assertTrue(alerts.alerts(today).isEmpty(), "back above the threshold")
        alerts.save(AlertSettings(chequing.id))
        assertFalse(alerts.settings(chequing.id).anyOn, "all empty: off again")
        assertFailsWith<ValidationException>("only cards have a limit") { alerts.save(AlertSettings(chequing.id, overLimit = true)) }
    }

    @Test
    fun `a card warns near its limit and over it`() {
        val visa = account("Visa", AccountType.CREDIT_CARD, "0")
        books.creditCards.saveTerms(visa.id, CreditCardTerms(creditLimit = cad("1000")))
        alerts.save(AlertSettings(visa.id, nearLimitPercent = 90, overLimit = true))
        books.transactions.create(TransactionDraft(visa.id, daysAgo(2), cad("-850"), "Costco"))
        assertTrue(alerts.alerts(today).isEmpty(), "85 % used")
        books.transactions.create(TransactionDraft(visa.id, daysAgo(1), cad("-100"), "Esso"))
        val near = alerts.alerts(today).single()
        assertEquals(AccountAlertKind.NEAR_LIMIT, near.kind)
        assertEquals(95, near.percent)
        assertEquals(cad("950"), near.amount)
        books.transactions.create(TransactionDraft(visa.id, today, cad("-80"), "Metro"))
        assertEquals(listOf(AccountAlertKind.OVER_LIMIT), alerts.alerts(today).map { it.kind })
        assertFailsWith<ValidationException> { alerts.save(AlertSettings(visa.id, lowBalance = cad("10"))) }
        assertFailsWith<ValidationException> { alerts.save(AlertSettings(visa.id, nearLimitPercent = 0)) }
    }

    @Test
    fun `unusual activity - a much larger transaction or a new payee - until dismissed`() {
        val chequing = account("Chequing", AccountType.CHEQUING, "10000")
        // A year of ordinary spending: about $60 each.
        for (i in 0 until 20) books.transactions.create(TransactionDraft(chequing.id, daysAgo(30 + i * 10), cad(if (i % 2 == 0) "-55" else "-65"), "Metro"))
        books.transactions.create(TransactionDraft(chequing.id, daysAgo(40), cad("-900"), "Garage Tremblay"))
        alerts.save(AlertSettings(chequing.id, largeMultiple = BigDecimal("5"), newPayeeAbove = cad("200")))
        assertTrue(alerts.alerts(today).isEmpty())

        val big = books.transactions.create(TransactionDraft(chequing.id, daysAgo(2), cad("-450"), "Garage Tremblay"))
        val stranger = books.transactions.create(TransactionDraft(chequing.id, daysAgo(1), cad("-250"), "Electro Plus"))
        books.transactions.create(TransactionDraft(chequing.id, daysAgo(1), cad("-70"), "Dépanneur"))
        books.transactions.transfer(TransferDraft(chequing.id, account("Savings", AccountType.SAVINGS, "0").id, today, cad("3000")))
        val raised = alerts.alerts(today)
        assertEquals(listOf(AccountAlertKind.LARGE_TRANSACTION to big.id, AccountAlertKind.NEW_PAYEE to stranger.id), raised.map { it.kind to it.transactionId },
            "the garage is known but almost 7 times the usual $65; Electro Plus is new and over $200; the small new payee and the transfer are not unusual")
        assertEquals(cad("65"), raised.first().usual, "the median of the year before")
        assertEquals("Electro Plus", raised.last().payee)

        alerts.dismiss(big.id)
        assertEquals(listOf(stranger.id), alerts.alerts(today).map { it.transactionId }, "dismissed once looked at")
        assertTrue(alerts.alerts(today.plus(DatePeriod(days = 8))).none { it.transactionId != null }, "older than a week: no longer raised")
    }

    @Test
    fun `settings live with the account's group and need the right to change it`() {
        val chequing = account("Chequing", AccountType.CHEQUING, "100")
        alerts.save(AlertSettings(chequing.id, lowBalance = cad("500")))
        val vic = books.users.add("vic", "Vic", Role.VIEWER, "password3-long".toCharArray()).userId
        books.session.setPermission(group, vic, PermissionLevel.VIEW)
        books.session.close()
        books = Books(store.unlock(temp.resolve("A.hfm"), "vic", "password3-long".toCharArray()))
        assertEquals(1, alerts.alerts(today).size, "a viewer sees the alert")
        assertFailsWith<AccessDeniedException> { alerts.save(AlertSettings(chequing.id)) }
    }
}
