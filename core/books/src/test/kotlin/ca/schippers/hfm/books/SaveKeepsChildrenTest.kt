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
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Saving a record again must keep what hangs off it: an upsert that deletes and re-inserts the
 * row would take its readings, prices, statements, coverage or claims with it (ON DELETE CASCADE).
 */
class SaveKeepsChildrenTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("K.hfm"), "K", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `a vehicle keeps its log`() {
        val v = books.vehicles.save(Vehicle("", group, "Civic"))
        books.vehicles.addReading(v.id, d("2026-01-10"), 41000)
        books.vehicles.saveWarranty(Warranty("", v.id, WarrantyKind.MANUFACTURER, "Honda", endDate = d("2028-01-01")))
        val task = books.vehicles.saveTask(MaintenanceTask("", v.id, "Vidange", intervalMonths = 6))
        books.vehicles.saveService(ServiceRecord("", v.id, d("2026-02-01"), 41500, taskIds = setOf(task.id)))
        books.vehicles.save(books.vehicles.get(v.id).copy(colour = "Bleu"))
        books.vehicles.saveTask(books.vehicles.tasks(v.id).single().copy(intervalMonths = 12))
        assertEquals(2, books.vehicles.readings(v.id).size, "the reading, and the one the service recorded")
        assertEquals(1, books.vehicles.warranties(v.id).size)
        assertEquals(1, books.vehicles.services(v.id).size)
        assertEquals(setOf(task.id), books.vehicles.services(v.id).single().taskIds)
    }

    @Test
    fun `a security keeps its prices`() {
        val brokerage = books.accounts.create(AccountDraft(group, "Courtage", AccountType.BROKERAGE, Currency.CAD, cad("1000"), d("2026-01-01")))
        val xic = books.investments.saveSecurity(Security("", "XIC", "TSX", "iShares XIC", SecurityKind.ETF, Currency.CAD))
        books.investments.save(InvestmentTxn("", brokerage.id, d("2026-01-05"), InvestmentKind.BUY, xic.id, BigDecimal(10), BigDecimal(40), cad("400")))
        books.investments.setPrice(xic.id, d("2026-02-01"), BigDecimal(41))
        books.investments.saveSecurity(xic.copy(name = "iShares Core S&P/TSX"))
        assertEquals(1, books.investments.prices(xic.id).size)
    }

    @Test
    fun `a pension keeps its statements, a medical plan its coverage, an expense its claims`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        val pension = books.plans.savePension(Pension("", alex.id, PensionKind.DEFINED_BENEFIT, "RREGOP"), group)
        books.plans.saveStatement(pension, PensionStatement("", pension.id, 2025, accruedAnnual = cad("12000")))
        books.plans.savePension(books.plans.pensions().single().copy(administrator = "Retraite Québec"), group)
        assertEquals(1, books.plans.statements(books.plans.pensions().single()).size)

        val plan = books.medical.savePlan(MedPlan("", group, MedPlanKind.DENTAL, "Dentaire", people = listOf(PlanPerson(alex.id))))
        books.medical.saveCoverage(MedCoverage("", plan.id, MedService.DENTAL_BASIC, BigDecimal(80)))
        books.medical.savePlan(books.medical.plan(plan.id).copy(insurer = "SSQ"))
        assertEquals(1, books.medical.coverages(plan.id).size)

        val e = books.medical.saveExpense(MedExpense("", group, alex.id, MedService.DENTAL_BASIC, d("2026-03-01"), cad("200")))
        books.medical.submit(e.id, plan.id, d("2026-03-02"))
        books.medical.saveExpense(books.medical.expense(e.id).copy(description = "Obturation"))
        assertEquals(1, books.medical.expense(e.id).claims.size)
    }

    @Test
    fun `a policy keeps its premiums and beneficiaries, a warranty its claims`() {
        val policy = books.insurance.save(InsurancePolicy("", group, PolicyKind.LIFE, "Sun Life", premium = cad("40")))
        books.insurance.saveBeneficiary(PolicyBeneficiary("", policy.id, "Sam"))
        books.insurance.save(books.insurance.policy(policy.id).copy(broker = "Courtier"))
        assertEquals(1, books.insurance.beneficiaries(policy.id).size)
        assertEquals(1, books.insurance.premiums(policy.id).size)

        val fridge = books.assets.save(Asset("", group, AssetKind.APPLIANCE, "Frigo"))
        val w = books.assets.saveWarranty(AssetWarranty("", group, fridge.id, AssetWarrantyKind.MANUFACTURER, "LG"))
        books.assets.saveClaim(WarrantyClaim("", w.id, d("2026-05-01"), "Bruit"))
        books.assets.saveWarranty(w.copy(phone = "1-888-000-0000"))
        assertEquals(1, books.assets.claims(w.id).size)
    }
}
