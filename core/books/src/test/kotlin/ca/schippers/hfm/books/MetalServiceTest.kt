package ca.schippers.hfm.books

import ca.schippers.hfm.calc.metals.WeightUnit
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PM-01 to PM-04. */
class MetalServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var safe: Account
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun n(s: String) = BigDecimal(s)
    private val today = d("2026-10-02")

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("M.hfm"), "M", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        safe = books.accounts.create(AccountDraft(group, "Métaux", AccountType.PRECIOUS_METALS, Currency.CAD, cad("0"), d("2024-01-01")))
        books.prices.setSpot(Metal.GOLD, d("2026-10-01"), n("3700"))
        books.prices.setSpot(Metal.SILVER, d("2026-10-01"), n("44"))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun item(description: String, metal: Metal, qty: Int, weight: String, unit: WeightUnit, purity: String, cost: String, premium: String? = null) =
        books.metals.save(
            MetalItem("", safe.id, metal, MetalForm.COIN, description, n(weight), unit, n(purity), qty, purchaseDate = d("2025-03-01"), cost = cad(cost),
                premiumPercent = premium?.let(::n), storage = MetalStorage.BANK_BOX, storageDetail = "Desjardins, coffret 112", insured = true),
        )

    @Test
    fun `items valued from spot prices count in net worth`() {
        item("Maple Leaf 1 oz", Metal.GOLD, 2, "1", WeightUnit.OZT, "0.9999", "6200")
        item("Rondelles d'argent", Metal.SILVER, 20, "1", WeightUnit.OZT, "0.999", "760", "8")
        val values = books.metals.valuations(safe.id, today).associateBy { it.item.description }
        assertEquals(cad("7399.26"), values.getValue("Maple Leaf 1 oz").value)
        assertEquals(cad("1199.26"), values.getValue("Maple Leaf 1 oz").gain)
        assertEquals(cad("949.45"), values.getValue("Rondelles d'argent").value)
        assertEquals(cad("8348.71"), books.investments.holdings(safe.id, today).totalValue)
        assertEquals(cad("8348.71"), books.reports.netWorth(listOf(today)).value.single().assets)
    }

    @Test
    fun `an item without a spot price counts at its cost`() {
        books.metals.save(MetalItem("", safe.id, Metal.PLATINUM, MetalForm.BAR, "Platine 10 g", n("10"), WeightUnit.G, n("0.9995"), 1, cost = cad("450")))
        assertNull(books.metals.valuations(safe.id, today).single().value)
        assertEquals(cad("450"), books.metals.value(safe.id, today))
    }

    @Test
    fun `a sale gives a capital gain`() {
        val coin = item("Maple Leaf 1 oz", Metal.GOLD, 1, "1", WeightUnit.OZT, "0.9999", "3100")
        books.metals.sell(coin.id, safe.id, d("2026-09-15"), cad("3600"))
        assertTrue(books.metals.items(safe.id).isEmpty(), "sold items leave the holdings")
        val gain = books.investments.acb(today).gains.single()
        assertEquals("metal:GOLD", gain.security.id)
        assertEquals(cad("500"), gain.disposition.gain)
        assertEquals(cad("0"), books.metals.value(safe.id, today))
    }

    @Test
    fun `buying and selling move money through the accounts chosen (M-34)`() {
        val chequing = books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("10000"), d("2024-01-01")))
        val savings = books.accounts.create(AccountDraft(group, "Épargne", AccountType.SAVINGS, Currency.CAD, cad("0"), d("2024-01-01")))
        fun balance(a: Account) = books.accounts.list().first { it.account.id == a.id }.balance
        val coin = MetalItem("", safe.id, Metal.GOLD, MetalForm.COIN, "Maple Leaf 1 oz", n("1"), WeightUnit.OZT, n("0.9999"), 2, dealer = "Monnaie royale", purchaseDate = d("2025-03-01"), cost = cad("6200"))
        val saved = books.metals.save(coin, MetalMoney(paidFromAccountId = chequing.id))
        assertEquals(cad("3800"), balance(chequing))
        assertEquals(MetalMoney(chequing.id, null), books.metals.money(saved.id))
        assertEquals(cad("3800") + cad("7399.26"), books.reports.netWorth(listOf(today)).value.single().assets, "the cash left, the metal's value came in")
        val year = books.reports.incomeExpense(ReportFilter(d("2025-01-01"), d("2025-12-31")), Granularity.YEAR).value.single()
        assertEquals(cad("0"), year.expense, "a purchase is not spending")

        books.metals.save(saved.copy(cost = cad("6300")), MetalMoney(paidFromAccountId = chequing.id))
        assertEquals(cad("3700"), balance(chequing), "the line follows the cost")
        books.metals.save(saved.copy(cost = cad("6300"), notes = "coffret"))
        assertEquals(cad("3700"), balance(chequing), "without accounts given, the account recorded stays")

        val line = books.transactions.register(chequing.id).single().transaction
        books.transactions.setCleared(line.id, ClearedStatus.RECONCILED)
        assertFailsWith<ReconciledChangeException> { books.metals.save(saved.copy(cost = cad("6000")), MetalMoney(paidFromAccountId = chequing.id)) }
        books.metals.sell(saved.id, safe.id, d("2026-09-15"), cad("7000"), depositedToAccountId = savings.id)
        assertEquals(cad("7000"), balance(savings))
        assertEquals(cad("3700"), balance(chequing), "the reconciled purchase is untouched")
        assertFailsWith<ValidationException> { books.metals.save(saved.copy(cost = null), MetalMoney(paidFromAccountId = chequing.id)) }

        assertFailsWith<ReconciledChangeException> { books.metals.delete(safe.id, saved.id) }
        books.metals.delete(safe.id, saved.id, confirmReconciled = true)
        assertEquals(cad("10000"), balance(chequing))
        assertEquals(cad("0"), balance(savings))
    }

    @Test
    fun `certificates are kept in the vault`() {
        val coin = item("Lingot 1 oz", Metal.GOLD, 1, "1", WeightUnit.OZT, "0.9999", "3100")
        val doc = books.documents.import(group, "certificat".toByteArray(), "certificat.pdf", "application/pdf").document
        books.metals.attach(coin.id, doc.id)
        assertEquals(listOf(doc.id), books.metals.documents(coin.id).map { it.id })
        books.metals.detach(coin.id, doc.id)
        assertTrue(books.metals.documents(coin.id).isEmpty())
    }

    @Test
    fun `items are checked`() {
        assertFailsWith<ValidationException> { item("", Metal.GOLD, 1, "1", WeightUnit.OZT, "0.9999", "1") }
        assertFailsWith<ValidationException> { item("Trop pur", Metal.GOLD, 1, "1", WeightUnit.OZT, "1.5", "1") }
        val chequing = books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("0"), d("2024-01-01")))
        assertFailsWith<ValidationException> { books.metals.save(MetalItem("", chequing.id, Metal.GOLD, MetalForm.COIN, "x", n("1"), WeightUnit.OZT, n("1"), 1)) }
    }
}
