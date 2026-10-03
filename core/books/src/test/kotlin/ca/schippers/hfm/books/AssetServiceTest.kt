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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var house: Asset
    private lateinit var furnace: Asset
    private lateinit var fridge: Asset
    private lateinit var visa: Account
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val assets get() = books.assets
    private val insurance get() = books.insurance

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("A.hfm"), "A", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        books.accounts.create(AccountDraft(group, "Chèques", AccountType.CHEQUING, Currency.CAD, cad("1000"), d("2015-01-01")))
        visa = books.accounts.create(AccountDraft(group, "Visa", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), d("2015-01-01")))
        books.creditCards.saveBenefit(CardBenefit("", visa.id, BenefitKind.PURCHASE_PROTECTION, days = 90))
        books.creditCards.saveBenefit(CardBenefit("", visa.id, BenefitKind.EXTENDED_WARRANTY, months = 12, maxYears = 3))
        house = assets.save(Asset("", group, AssetKind.HOME, "Maison", purchaseDate = d("2015-05-01"), purchasePrice = cad("400000"), valueMethod = ValueMethod.MANUAL, value = cad("650000"), inNetWorth = true))
        furnace = assets.save(
            Asset("", group, AssetKind.HEATING_COOLING, "Fournaise", house.id, purchaseDate = d("2020-06-01"), purchasePrice = cad("6000"), valueMethod = ValueMethod.DEPRECIATION, depreciationYears = 15, residualPercent = BigDecimal.ZERO, inNetWorth = true, location = "Sous-sol"),
        )
        val purchase = books.transactions.create(TransactionDraft(visa.id, d("2026-01-15"), cad("-1899"), "Brault & Martineau"))
        fridge = assets.save(Asset("", group, AssetKind.APPLIANCE, "Réfrigérateur", house.id, "LG", "LRMVS3006S", "SN-77120", d("2026-01-15"), purchasePrice = cad("1899"), transactionId = purchase.id))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `assets inside others, their value in net worth, and disposal`() {
        assertEquals(setOf(furnace.id, fridge.id), assets.children(house.id).map { it.id }.toSet())
        assertFailsWith<ValidationException>("a house cannot be inside its own furnace") { assets.save(house.copy(parentId = furnace.id)) }
        assertFailsWith<ValidationException>("delete what is inside first") { assets.delete(house.id) }
        // 6 of 15 years gone: 6,000 × 9/15.
        assertEquals(cad("3600"), furnace.valueOn(d("2026-06-01")))
        val worth = books.reports.netWorth(listOf(d("2026-06-01"))).value.single()
        assertEquals(cad("654600"), worth.assets, "the account, the house and the furnace; the fridge is not counted")

        val tv = assets.save(Asset("", group, AssetKind.ELECTRONICS, "Télé", purchasePrice = cad("800")))
        assets.dispose(tv.id, AssetStatus.SOLD, d("2026-03-01"), cad("200"))
        assertTrue(assets.list().none { it.id == tv.id }, "no longer listed")
        assertEquals(AssetStatus.SOLD, assets.get(tv.id).status)
        assertEquals(cad("0"), assets.get(tv.id).copy(valueMethod = ValueMethod.MANUAL, value = cad("500")).valueOn(d("2026-04-01")))
    }

    @Test
    fun `is it still covered`() {
        val manufacturer = assets.saveWarranty(AssetWarranty("", group, fridge.id, AssetWarrantyKind.MANUFACTURER, "LG", startDate = d("2026-01-15"), endDate = d("2027-01-15"), phone = "1-888-542-2623"))
        val card = assets.saveWarranty(AssetWarranty("", group, fridge.id, AssetWarrantyKind.CARD_EXTENDED, "Visa", cardAccountId = visa.id))
        assertEquals(d("2028-01-15"), assets.endOf(card), "the card doubles a one-year warranty")

        val march = assets.coverage(fridge.id, d("2026-03-01"))
        assertEquals(listOf(d("2027-01-15"), d("2028-01-15"), d("2026-04-15")), march.map { it.until }, "manufacturer, card warranty, card purchase protection")
        assertTrue(march.all { it.active })
        val later = assets.coverage(fridge.id, d("2027-06-01"))
        assertEquals(listOf(false, true), later.map { it.active }, "only the card's extension is left")

        val civic = books.vehicles.save(Vehicle("", group, "Civic"))
        books.vehicles.saveWarranty(Warranty("", civic.id, WarrantyKind.POWERTRAIN, "Honda", endDate = d("2030-01-01"), endKm = 100000))
        assertEquals(listOf("Réfrigérateur"), assets.findCovered("lg", d("2026-03-01")).map { it.name })
        val car = assets.findCovered("civic", d("2026-03-01")).single()
        assertTrue(car.isVehicle && car.covered)

        assertEquals(listOf(fridge.id), books.renewals(d("2026-12-01")).filter { it.kind == RenewalKind.ASSET_WARRANTY }.map { it.subjectId }, "60 days ahead")
        assets.saveClaim(WarrantyClaim("", manufacturer.id, d("2026-09-10"), "Ne refroidit plus", "Compresseur remplacé", covered = cad("640")))
        assertEquals("Ne refroidit plus", assets.claims(manufacturer.id).single().problem)
    }

    @Test
    fun `insurance policies, what they cover and what they do not`() {
        val alex = books.members.create("Alex", MemberKind.ADULT)
        val home = insurance.save(InsurancePolicy("", group, PolicyKind.HOME, "Desjardins Assurances", policyNumber = "H-4471", premium = cad("1200"), deductible = cad("1000"), startDate = d("2025-11-01"), renewalDate = d("2026-11-01"), assetIds = setOf(house.id)))
        val bike = assets.save(Asset("", group, AssetKind.SPORTS, "Vélo de montagne", purchasePrice = cad("2400")))
        val truck = books.vehicles.save(Vehicle("", group, "Camionnette"))
        val uninsured = insurance.uninsured(d("2026-10-01"))
        assertEquals(setOf(bike.id, truck.id), uninsured.map { it.id }.toSet(), "the furnace and the fridge are insured with the house")

        assertEquals(listOf(home.id), books.renewals(d("2026-10-15")).filter { it.kind == RenewalKind.INSURANCE_RENEWAL }.map { it.subjectId })
        insurance.renew(home.id, d("2027-11-01"), cad("1320"))
        assertEquals(listOf(d("2026-11-01") to cad("1320"), d("2025-11-01") to cad("1200")), insurance.premiums(home.id).map { it.startDate to it.premium }, "premiums year over year")
        assertFalse(books.renewals(d("2026-10-15")).any { it.kind == RenewalKind.INSURANCE_RENEWAL })

        val life = insurance.save(InsurancePolicy("", group, PolicyKind.LIFE, "Sun Life", insuredMemberId = alex.id, premium = cad("45"), frequency = PremiumFrequency.MONTHLY, coverage = cad("500000")))
        assertEquals(cad("540"), life.annualPremium)
        insurance.saveBeneficiary(PolicyBeneficiary("", life.id, "Sam", relationship = "Conjoint", sharePercent = BigDecimal(100)))
        insurance.saveBeneficiary(PolicyBeneficiary("", life.id, "Léa", relationship = "Enfant", sharePercent = BigDecimal(100), contingent = true))
        val summary = insurance.lifeSummary().single()
        assertEquals(cad("500000"), summary.policy.coverage)
        assertEquals(listOf("Sam", "Léa"), summary.beneficiaries.map { it.name })

        insurance.saveClaim(InsuranceClaim("", home.id, d("2026-07-20"), "Dégât d'eau au sous-sol", assetId = house.id, claimed = cad("8500"), deductible = cad("1000")))
        assertEquals(cad("8500"), insurance.claims(home.id).single().claimed)
    }
}
