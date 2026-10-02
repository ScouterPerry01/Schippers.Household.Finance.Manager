package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.importers.DateOrder
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
import kotlin.test.assertTrue

/** OTH-05: a Quicken history imports and balances. */
class QifImportServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private val today = LocalDate(2026, 10, 2)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    private val qif = """
        !Option:AutoSwitch
        !Account
        NCompte chèques
        TBank
        ^
        NVisa Desjardins
        TCCard
        ^
        NCELI
        TInvst
        ^
        !Clear:AutoSwitch
        !Type:Cat
        NAlimentation:Épicerie
        E
        ^
        NAnimaux:Toilettage
        E
        ^
        !Account
        NCompte chèques
        TBank
        ^
        !Type:Bank
        D01/02/2024
        T1,000.00
        PSolde d'ouverture
        L[Compte chèques]
        ^
        D15/01/2024
        T-187.32
        CX
        PIGA
        LAlimentation:Épicerie/Vacances
        ^
        D20/01/2024
        T-500.00
        PPaiement Visa
        L[Visa Desjardins]
        ^
        D25/01/2024
        T-1,000.00
        PCotisation CELI
        L[CELI]
        ^
        D31/01/2024
        T2,450.00
        PEmployeur inc.
        SSalaire
        ${'$'}2,600.00
        SImpôts:Fédéral
        ${'$'}-150.00
        ^
        !Account
        NVisa Desjardins
        TCCard
        ^
        !Type:CCard
        D10/01/2024
        T-62.00
        PToilettage Patte de velours
        LAnimaux:Toilettage
        ^
        D20/01/2024
        T500.00
        PPaiement
        L[Compte chèques]
        ^
        !Account
        NCELI
        TInvst
        ^
        !Type:Invst
        D25/01/2024
        NXIn
        T1,000.00
        L[Compte chèques]
        ${'$'}1,000.00
        ^
        D26/01/2024
        NBuy
        YFNB Indiciel
        I25.00
        Q40
        T1,000.00
        ^
    """.trimIndent().encodeToByteArray()

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("Q.hfm"), "Q", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun balance(name: String) = books.accounts.list().first { it.account.name == name }.balance

    @Test
    fun `a Quicken file imports, balances, and can be imported again without duplicates`() {
        val preview = books.quicken.preview(qif)
        assertEquals(DateOrder.DAY_MONTH, preview.dateOrder, "15/01 can only be day first")
        assertEquals(listOf(AccountType.CHEQUING, AccountType.CREDIT_CARD, AccountType.TFSA), preview.plans.map { it.newType })
        assertEquals(LocalDate(2024, 1, 10), preview.from, "the earliest date, read day first")

        val result = books.quicken.import(qif, "quicken.qif", group, preview.plans, DateOrder.DAY_MONTH, today)
        assertEquals(3, result.accountsCreated)
        assertEquals(3, result.transactions, "IGA, the pay split and the grooming")
        assertEquals(2, result.transfers, "Visa payment and TFSA contribution, each once")
        assertEquals(1, result.investmentActions, "the buy; the cash moved in is the chequing account's transfer")

        assertEquals(cad("1762.68"), balance("Compte chèques"), "1,000 - 187.32 - 500 - 1,000 + 2,450")
        assertEquals(cad("438.00"), balance("Visa Desjardins"), "-62 + 500")
        assertEquals(cad("0.00"), balance("CELI"), "1,000 moved in, then spent on 40 units")
        val celi = books.accounts.list().first { it.account.name == "CELI" }.account
        val holding = books.investments.holdings(celi.id, today).holdings.single()
        assertEquals("FNB Indiciel", holding.security.name)
        assertEquals(java.math.BigDecimal("40"), holding.quantity)
        assertEquals(cad("1000.00"), holding.bookCost)

        val chequing = books.accounts.list().first { it.account.name == "Compte chèques" }.account
        assertEquals(LocalDate(2024, 2, 1), chequing.openingDate)
        val iga = books.transactions.register(chequing.id).map { it.transaction }.first { it.payeeText == "IGA" }
        assertEquals(books.categories.list().first { it.systemKey == "food.groceries" }.id, iga.splits.single().categoryId, "matched by its French name")
        assertEquals(ClearedStatus.RECONCILED, iga.cleared)
        assertEquals(setOf("Vacances"), books.tags().filter { it.id in iga.tagIds }.map { it.name }.toSet())
        assertTrue(books.categories.list().any { it.nameFr == "Toilettage" }, "a category the tree did not have is created")
        val pay = books.transactions.register(chequing.id).map { it.transaction }.first { it.payeeText == "Employeur inc." }
        assertEquals(books.categories.list().first { it.systemKey == "income.employment.salary" }.id, pay.splits.first().categoryId, "found deeper in the tree")
        assertTrue(books.documents.search(DocumentQuery(text = "Quicken")).single().keepForever, "the file is kept as the record")

        // Imported again, into the same accounts: nothing is added.
        val again = books.quicken.import(qif, "quicken.qif", group, books.quicken.preview(qif).plans, DateOrder.DAY_MONTH, today)
        assertEquals(0, again.accountsCreated)
        assertEquals(0, again.transactions + again.transfers)
        assertEquals(5, again.alreadyThere)
        assertEquals(0, again.investmentActions)
        assertEquals(cad("1762.68"), balance("Compte chèques"))
        assertEquals(cad("438.00"), balance("Visa Desjardins"))
    }

    @Test
    fun `an account left out turns its transfers into ordinary lines`() {
        val plans = books.quicken.preview(qif).plans.map { if (it.qifName == "CELI") it.copy(include = false) else it }
        val result = books.quicken.import(qif, "q.qif", group, plans, DateOrder.DAY_MONTH, today)
        assertEquals(2, result.accountsCreated)
        assertTrue(result.warnings.any { "CELI" in it })
        assertEquals(cad("1762.68"), balance("Compte chèques"), "the money still left the account")
    }
}
