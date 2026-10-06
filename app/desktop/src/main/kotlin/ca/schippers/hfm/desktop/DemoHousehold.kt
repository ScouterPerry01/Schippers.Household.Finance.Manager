package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Account
import ca.schippers.hfm.sync.CalendarInstance
import ca.schippers.hfm.sync.CalendarSnapshot
import ca.schippers.hfm.sync.CalendarVisibility
import ca.schippers.hfm.books.CardReward
import ca.schippers.hfm.books.Contact
import ca.schippers.hfm.books.ContactDetail
import ca.schippers.hfm.books.ContactFilter
import ca.schippers.hfm.books.ContactKind
import ca.schippers.hfm.books.Contractor
import ca.schippers.hfm.books.ContractorJob
import ca.schippers.hfm.books.DetailType
import ca.schippers.hfm.books.GatherDecision
import ca.schippers.hfm.books.HomeProject
import ca.schippers.hfm.books.Invoice
import ca.schippers.hfm.books.InvoiceLine
import ca.schippers.hfm.books.InvoiceStatus
import ca.schippers.hfm.books.InvoiceTax
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.LinkTarget
import ca.schippers.hfm.books.ProjectStatus
import ca.schippers.hfm.books.RentalProperty
import ca.schippers.hfm.books.RewardKind
import ca.schippers.hfm.books.RewardUnit
import ca.schippers.hfm.books.SearchService
import ca.schippers.hfm.books.Trip
import ca.schippers.hfm.books.TripPurpose
import ca.schippers.hfm.books.Allowance
import ca.schippers.hfm.books.AllowanceFrequency
import ca.schippers.hfm.books.AllowanceKind
import ca.schippers.hfm.books.FamilyLoan
import ca.schippers.hfm.books.ShareEntry
import ca.schippers.hfm.books.SharePerson
import ca.schippers.hfm.books.ContactRole
import ca.schippers.hfm.books.DeductionKind
import ca.schippers.hfm.books.EstateContact
import ca.schippers.hfm.books.EstatePlan
import ca.schippers.hfm.books.DonationReceipt
import ca.schippers.hfm.books.PayDeduction
import ca.schippers.hfm.books.PayEarning
import ca.schippers.hfm.books.PayStub
import ca.schippers.hfm.books.TaxAuthority
import ca.schippers.hfm.books.MeterUnit
import ca.schippers.hfm.books.AssetServiceRecord
import ca.schippers.hfm.books.ValueMethod
import ca.schippers.hfm.books.PremiumFrequency
import ca.schippers.hfm.books.PolicyKind
import ca.schippers.hfm.books.PolicyBeneficiary
import ca.schippers.hfm.books.InsurancePolicy
import ca.schippers.hfm.books.AssetWarrantyKind
import ca.schippers.hfm.books.AssetWarranty
import ca.schippers.hfm.books.AssetKind
import ca.schippers.hfm.books.Asset
import ca.schippers.hfm.books.AccountDraft
import ca.schippers.hfm.books.AllocationBy
import ca.schippers.hfm.books.AllocationTarget
import ca.schippers.hfm.books.AmountKind
import ca.schippers.hfm.books.BillDraft
import ca.schippers.hfm.books.BillKind
import ca.schippers.hfm.books.BenefitKind
import ca.schippers.hfm.books.BudgetPeriod
import ca.schippers.hfm.books.PaymentMethod
import ca.schippers.hfm.books.Allergy
import ca.schippers.hfm.books.ConditionStatus
import ca.schippers.hfm.books.EventCategory
import ca.schippers.hfm.books.EventDraft
import ca.schippers.hfm.books.HealthCondition
import ca.schippers.hfm.books.HealthProvider
import ca.schippers.hfm.books.HealthTest
import ca.schippers.hfm.books.Immunization
import ca.schippers.hfm.books.MedCoverage
import ca.schippers.hfm.books.MedExpense
import ca.schippers.hfm.books.MedPlan
import ca.schippers.hfm.books.MedPlanKind
import ca.schippers.hfm.books.MedService
import ca.schippers.hfm.books.Medication
import ca.schippers.hfm.books.PlanPerson
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.books.ProviderKind
import ca.schippers.hfm.books.FuelEntry
import ca.schippers.hfm.books.FuelType
import ca.schippers.hfm.books.PaymentDraft
import ca.schippers.hfm.books.Pet
import ca.schippers.hfm.books.SavingsGoal
import ca.schippers.hfm.books.ServiceRecord
import ca.schippers.hfm.books.Sex
import ca.schippers.hfm.books.Species
import ca.schippers.hfm.books.Vehicle
import ca.schippers.hfm.books.Warranty
import ca.schippers.hfm.books.WarrantyKind
import ca.schippers.hfm.books.Severity
import kotlinx.datetime.LocalTime
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.calc.schedule.BusinessDayAdjust
import ca.schippers.hfm.calc.schedule.Frequency
import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.CardBenefit
import ca.schippers.hfm.books.CardHolder
import ca.schippers.hfm.books.CreditCardTerms
import ca.schippers.hfm.books.LoanDetails
import ca.schippers.hfm.books.CryptoIncomeKind
import ca.schippers.hfm.books.Metal
import ca.schippers.hfm.books.MetalForm
import ca.schippers.hfm.books.MetalItem
import ca.schippers.hfm.books.MetalStorage
import ca.schippers.hfm.calc.metals.WeightUnit
import ca.schippers.hfm.books.Beneficiary
import ca.schippers.hfm.books.BeneficiaryKind
import ca.schippers.hfm.books.GrantKind
import ca.schippers.hfm.books.Pension
import ca.schippers.hfm.books.PensionKind
import ca.schippers.hfm.books.PensionStatement
import ca.schippers.hfm.books.RoomEntry
import ca.schippers.hfm.books.RoomPlan
import ca.schippers.hfm.books.AssetClass
import ca.schippers.hfm.books.IncomeType
import ca.schippers.hfm.books.InvestmentKind
import ca.schippers.hfm.books.InvestmentTxn
import ca.schippers.hfm.books.Region
import ca.schippers.hfm.books.Security
import ca.schippers.hfm.books.SecurityKind
import ca.schippers.hfm.books.TargetScope
import ca.schippers.hfm.books.Transaction
import ca.schippers.hfm.calc.loan.Compounding
import ca.schippers.hfm.calc.loan.LoanPlan
import ca.schippers.hfm.calc.loan.LoanProjection
import ca.schippers.hfm.calc.loan.LoanTerms
import ca.schippers.hfm.calc.loan.PaymentFrequency
import ca.schippers.hfm.books.Institution
import ca.schippers.hfm.books.SplitDraft
import ca.schippers.hfm.books.TransactionDraft
import ca.schippers.hfm.books.TransferDraft
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.importers.ImportedLine
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files

/**
 * A sample household with a few months of made-up activity, created in a temporary folder.
 * Started with `./gradlew :app:desktop:runDemo`; never touches real households. In English it is
 * a family in Ottawa, Ontario; in French a family in Quebec City, so each language's screenshots
 * show names, places and taxes that fit it.
 */
object DemoHousehold {
    const val LOGIN = "demo"
    const val PASSWORD = "demo-password"

    /** Whether the household being built is the English one (Ontario). */
    private var english = false

    /** The French (Quebec) or English (Ontario) version of a name. */
    private fun l(fr: String, en: String) = if (english) en else fr

    fun create(store: HouseholdStore, language: ca.schippers.hfm.i18n.Language = ca.schippers.hfm.i18n.Language.ENGLISH): HouseholdSession {
        english = language == ca.schippers.hfm.i18n.Language.ENGLISH
        // The manual's pictures put it in a folder of their own (hfm.demo.dir), so no user name shows in its paths.
        val parent = System.getProperty("hfm.demo.dir")?.let { java.nio.file.Path.of(it) } ?: Files.createTempDirectory("hfm-demo")
        val dir = parent.resolve("Demo.hfm")
        val created = store.create(
            dir, l("Famille Démo", "Demo Family"), LOGIN, "Alex Demo", PASSWORD.toCharArray(),
            locale = l("fr-CA", "en-CA"), province = l("QC", "ON"),
            // As a household created in the app gets it: the shared group named in its language.
            sharedGroupName = ca.schippers.hfm.i18n.Messages.get(language, "group.sharedName"),
        )
        val books = Books(created.session).also { it.language = language }
        fill(books)
        // The demo shows a household already set up; its statement is left open to try reconciling,
        // so the Getting started guide is hidden rather than asking for that last step.
        books.putSetting(gettingStartedSetting(books.userId), "1")
        return created.session
    }

    private fun fill(books: Books) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
        val group = books.groups().first().id
        val today = today()
        val start = today.minus(DatePeriod(months = 3))

        val alex = books.members.create("Alex", MemberKind.ADULT, LocalDate(1984, 5, 14))
        val sam = books.members.create("Sam", MemberKind.ADULT, LocalDate(1986, 11, 2))
        val lea = books.members.create(l("Léa", "Maya"), MemberKind.CHILD, LocalDate(2015, 6, 12))
        val desjardins = books.institutions.create(Institution("", l("Desjardins", "TD Canada Trust"), institutionNumber = l("815", "004"), transitNumber = l("30123", "01234")))
        val bank = books.institutions.create(Institution("", l("Banque Nationale", "Tangerine"), institutionNumber = l("006", "614")))

        val chequing = books.accounts.create(
            AccountDraft(group, l("Compte conjoint", "Joint chequing"), AccountType.CHEQUING, Currency.CAD, cad("2450.00"), start, desjardins.id, l("815-30123-0045678", "004-01234-5045678"), setOf(alex.id, sam.id)),
        )
        val savings = books.accounts.create(AccountDraft(group, l("Épargne", "Savings"), AccountType.HIGH_INTEREST_SAVINGS, Currency.CAD, cad("8000.00"), start, desjardins.id))
        val visa = books.accounts.create(AccountDraft(group, l("Visa Desjardins", "TD Visa"), AccountType.CREDIT_CARD, Currency.CAD, cad("0"), start, desjardins.id, "4540123412341234"))
        books.creditCards.saveTerms(
            visa.id,
            CreditCardTerms(
                cad("8000"), BigDecimal("0.1995"), statementDay = 20, dueDay = 10, minPaymentPercent = BigDecimal("0.05"), minPaymentFloor = cad("10"),
                annualFee = cad("110"), annualFeeDate = today.plus(DatePeriod(days = 20)).minus(DatePeriod(years = 1)),
            ),
        )
        // CC-04, CC-05: Sam holds a supplementary card; the card's insurance as on its certificate.
        books.creditCards.saveHolder(CardHolder("", visa.id, "Alex", alex.id, "1234", isPrimary = true))
        val samCard = books.creditCards.saveHolder(CardHolder("", visa.id, "Sam", sam.id, "5678"))
        books.creditCards.saveBenefit(CardBenefit("", visa.id, BenefitKind.PURCHASE_PROTECTION, days = 90, limit = cad("1000")))
        books.creditCards.saveBenefit(CardBenefit("", visa.id, BenefitKind.EXTENDED_WARRANTY, months = 12, maxYears = 3))
        books.creditCards.saveBenefit(CardBenefit("", visa.id, BenefitKind.TRAVEL_MEDICAL, days = 15, limit = cad("5000000"), notes = "Under 65"))
        val usd = books.accounts.create(AccountDraft(group, l("Compte US", "US dollar account"), AccountType.CHEQUING, Currency.USD, Money.parse("500.00", Currency.USD), start, bank.id))

        // A cottage mortgage renewed two years ago, with its term ending soon (LN-01 to LN-04).
        val firstPayment = LocalDate(start.year, start.month, 1).minus(DatePeriod(months = 22))
        val terms = LoanTerms(cad("148000"), BigDecimal("0.0489"), Compounding.SEMI_ANNUAL, 21 * 12, PaymentFrequency.MONTHLY)
        val owedAtStart = LoanProjection.project(LoanPlan(terms, firstPayment)).balanceOn(start.minus(DatePeriod(days = 1)))
        val mortgage = books.accounts.create(AccountDraft(group, l("Hypothèque du chalet", "Cottage mortgage"), AccountType.MORTGAGE, Currency.CAD, -owedAtStart, start, desjardins.id, "MTG-7745120"))
        books.loans.save(
            LoanDetails(
                mortgage.id, terms.principal, terms.annualRate, amortizationMonths = terms.amortizationMonths, firstPaymentDate = firstPayment,
                termEnd = today.plus(DatePeriod(days = 100)), paymentAccountId = chequing.id, lastPaidDate = start.minus(DatePeriod(days = 1)),
            ),
        )

        // A dog and a car (PET-01, VEH-01), created first so the monthly activity can refer to them.
        val rex = books.pets.save(
            Pet(
                "", "Rex", Species.DOG, "Golden retriever", Sex.MALE, LocalDate(2021, 5, 3), neutered = true, colour = l("Doré", "Golden"), microchip = "985141000123456",
                licenceNumber = "2026-04127", licenceMunicipality = l("Ville de Québec", "City of Ottawa"), licenceExpiry = today.plus(DatePeriod(days = 21)),
                insurer = "Trupanion", policyNumber = "TP-88213", insuranceRenewal = today.plus(DatePeriod(months = 5)), ownerMemberId = lea.id,
            ),
        )
        val civic = books.vehicles.save(
            Vehicle(
                "", group, "Civic", "Honda", "Civic", 2021, "EX", l("Gris", "Grey"), "2HGFE2F59MH512345", l("F42 KLM", "CKMR 482"), FuelType.GASOLINE, sam.id,
                LocalDate(2023, 4, 12), cad("24500"), l("Honda de Sainte-Foy", "Ottawa Honda"), 38_200, Currency.CAD, today.plus(DatePeriod(months = 7)),
                l("Desjardins Assurances", "Intact Insurance"), "AUT-5521873", today.plus(DatePeriod(days = 12)),
            ),
        )
        var odometer = 61_200
        fun payStub(date: LocalDate) = PayStub(
            l("Employeur inc.", "Employer Inc."), date, listOf(PayEarning(l("Salaire", "Regular pay"), cad("4315.00"))),
            if (!english) {
                listOf(
                    PayDeduction(DeductionKind.INCOME_TAX, "Impôt fédéral", cad("410.00")), PayDeduction(DeductionKind.INCOME_TAX, "Impôt du Québec", cad("380.00")),
                    PayDeduction(DeductionKind.CPP_QPP, "RRQ", cad("255.00")), PayDeduction(DeductionKind.EI_QPIP, "AE", cad("55.00")), PayDeduction(DeductionKind.EI_QPIP, "RQAP", cad("17.00")),
                    PayDeduction(DeductionKind.UNION_DUES, "Cotisation syndicale", cad("38.00")), PayDeduction(DeductionKind.CHARITY, "Centraide", cad("10.00")),
                )
            } else {
                listOf(
                    PayDeduction(DeductionKind.INCOME_TAX, "Federal tax", cad("520.00")), PayDeduction(DeductionKind.INCOME_TAX, "Ontario tax", cad("280.00")),
                    PayDeduction(DeductionKind.CPP_QPP, "CPP", cad("245.00")), PayDeduction(DeductionKind.EI_QPIP, "EI", cad("72.00")),
                    PayDeduction(DeductionKind.UNION_DUES, "Union dues", cad("38.00")), PayDeduction(DeductionKind.CHARITY, "United Way", cad("10.00")),
                )
            },
        )

        // Only past activity: anything that would fall after today is skipped.
        fun add(draft: TransactionDraft) { if (draft.date <= today) books.transactions.create(draft) }
        fun add(draft: TransferDraft) { if (draft.date <= today) books.transactions.transfer(draft) }
        var month = LocalDate(start.year, start.month, 1)
        while (month <= today) {
            val m = month
            fun on(day: Int) = LocalDate(m.year, m.month, minOf(day, 28))
            // SAL-02: Alex's pay, entered from the pay stub: gross pay less each deduction.
            for (day in listOf(1, 15)) add(books.payStubs.draft(chequing.id, payStub(on(day)), alex.id))
            // OTH-01: a monthly gift to the food bank.
            add(TransactionDraft(chequing.id, on(20), cad("-25.00"), l("Moisson Québec", "Ottawa Food Bank"), listOf(SplitDraft(cat("gifts.charity"), cad("-25.00"))), memberId = sam.id))
            add(TransactionDraft(chequing.id, on(1), cad("-1450.00"), l("Propriétaire", "Landlord"), listOf(SplitDraft(cat("housing.rent"), cad("-1450.00")))))
            add(TransactionDraft(chequing.id, on(12), cad("-132.48"), l("Hydro-Québec", "Hydro Ottawa"), listOf(SplitDraft(cat("utilities.electricity"), cad("-132.48")))))
            add(TransactionDraft(chequing.id, on(18), cad("-95.00"), l("Vidéotron", "Rogers"), listOf(SplitDraft(cat("utilities.internet"), cad("-95.00")))))
            add(TransactionDraft(visa.id, on(6), cad("-187.32"), l("IGA", "Loblaws"), listOf(SplitDraft(cat("food.groceries"), cad("-187.32")))))
            add(
                TransactionDraft(
                    visa.id, on(13), cad("-243.90"), "Costco",
                    listOf(SplitDraft(cat("food.groceries"), cad("-168.40")), SplitDraft(cat("health.otc"), cad("-42.50")), SplitDraft(cat("children.clothing"), cad("-33.00"))),
                    cardHolderId = samCard.id,
                ),
            )
            add(TransactionDraft(visa.id, on(21), cad("-64.15"), l("Restaurant Chez Mimi", "Riverside Diner"), listOf(SplitDraft(cat("food.restaurants"), cad("-64.15")))))
            // VEH-07, VEH-08: fill-ups entered from the fuel log, with their payments.
            for ((day, litres, cost) in listOf(Triple(8, "41.8", "68.55"), Triple(24, "39.6", "64.95"))) {
                odometer += 640
                if (on(day) <= today) {
                    books.vehicles.saveFuel(
                        FuelEntry("", civic.id, on(day), odometer, BigDecimal(litres), cad(cost), station = "Petro-Canada"),
                        PaymentDraft(visa.id, cat("transport.fuel"), "Petro-Canada"),
                    )
                }
            }
            add(TransactionDraft(visa.id, on(4), cad("-74.99"), l("Mondou", "Pet Valu"), listOf(SplitDraft(cat("pets.food"), cad("-74.99"))), memberId = rex.id))
            add(TransactionDraft(chequing.id, on(1), cad("-56.50"), l("RTC", "OC Transpo"), listOf(SplitDraft(cat("transport.transit.pass"), cad("-56.50"))), memberId = lea.id, memo = l("Laissez-passer étudiant", "Student pass")))
            add(TransferDraft(chequing.id, visa.id, on(10), cad("566.57")))
            add(TransferDraft(chequing.id, savings.id, on(16), cad("500.00"), memo = l("Épargne mensuelle", "Monthly savings")))
            month = month.plus(DatePeriod(months = 1))
        }
        // OTH-01: a gala dinner, of which the receipt counts only part, and a political contribution.
        if (LocalDate(today.year, 5, 9) <= today) {
            val gala = books.transactions.create(
                TransactionDraft(visa.id, LocalDate(today.year, 5, 9), cad("-250.00"), l("Fondation du CHU de Québec", "CHEO Foundation"), listOf(SplitDraft(cat("gifts.charity"), cad("-250.00"))), memberId = alex.id),
            )
            books.donations.setReceipt(gala.id, DonationReceipt(l("Fondation du CHU de Québec", "CHEO Foundation"), null, "2026-0412", cad("150.00"), received = true))
            add(TransactionDraft(chequing.id, LocalDate(today.year, 6, 2), cad("-100.00"), l("Association de circonscription", "Riding association"), listOf(SplitDraft(cat("gifts.political"), cad("-100.00"))), memberId = sam.id))
        }
        // EST-02: where Alex's papers are and who to call.
        books.estate.save(
            alex.id, group,
            EstatePlan(
                willLocation = l("Chez la notaire, Me Gagnon (testament notarié)", "Lawyer's office, Ms. Patel (original); copy in the filing cabinet"),
                willDate = "2023-04-18",
                powerOfAttorneyLocation = l("Avec le testament, chez la notaire", "Filing cabinet, top drawer"),
                mandateLocation = l("Mandat de protection, chez la notaire", "Power of attorney for personal care, filing cabinet"),
                safeDepositBox = l("Desjardins Sainte-Foy, coffret 214", "TD Bank Sparks St., box 214"),
                safeDepositKeys = l("Tiroir du bureau, enveloppe bleue", "Desk drawer, blue envelope"),
                digitalAccounts = l("Trousse d'urgence du gestionnaire de mots de passe, dans le coffret", "Password manager's emergency kit, in the safe deposit box"),
                organDonor = true,
                contacts = listOf(
                    EstateContact(if (english) ContactRole.EXECUTOR else ContactRole.LIQUIDATOR, "Sam"),
                    EstateContact(if (english) ContactRole.LAWYER else ContactRole.NOTARY, l("Me Isabelle Gagnon", "Priya Patel"), l("Gagnon notaires", "Patel Law"), l(l("418-555-0142", "613-555-0142"), "613-555-0142")),
                    EstateContact(ContactRole.FINANCIAL_ADVISOR, l("Marc Lavoie", "Daniel Wong"), l("Desjardins", "TD Wealth"), l("418-555-0190", "613-555-0190")),
                ),
            ),
        )
        // HH-04, LN-07, HH-03: a weekend shared with friends, a loan within the family, and Maya's allowance.
        val weekend = books.sharedExpenses.save(
            null, group, l("Fin de semaine au chalet", "Cottage weekend"), Currency.CAD,
            listOf(SharePerson("", "Alex", alex.id), SharePerson("", "Sam", sam.id), SharePerson("", l("Julie et Marc", "Priya and Raj"))),
        )
        val (pAlex, pSam, pFriends) = weekend.people.map { it.id }
        val shares = mapOf(pAlex to 1, pSam to 1, pFriends to 2)
        books.sharedExpenses.saveEntry(weekend.id, ShareEntry("", today.minus(DatePeriod(days = 20)), l("Épicerie", "Groceries"), cad("212.40"), pAlex, shares))
        books.sharedExpenses.saveEntry(weekend.id, ShareEntry("", today.minus(DatePeriod(days = 19)), l("Location du bateau", "Boat rental"), cad("180.00"), pFriends, shares))
        books.sharedExpenses.saveEntry(weekend.id, ShareEntry("", today.minus(DatePeriod(days = 19)), l("Essence", "Gas"), cad("68.00"), pSam, shares))
        val loan = books.familyLoans.save(FamilyLoan("", group, l("Alex et Sam", "Alex and Sam"), "Gordon", cad("3000.00"), today.minus(DatePeriod(months = 5)), 0, l("Réparation de la voiture", "Car repair"), false, emptyList()))
        for (m in 1..4) books.familyLoans.addPayment(loan, today.minus(DatePeriod(months = 5 - m)), cad("250.00"))
        val allowance = books.allowances.save(Allowance("", group, lea.id, cad("10.00"), AllowanceFrequency.WEEKLY, today.minus(DatePeriod(days = 34)), null, null, emptyList()))
        for (w in 0..3) books.allowances.addEntry(allowance, today.minus(DatePeriod(days = 34 - 7 * w)), cad("10.00"), AllowanceKind.PAID)
        books.allowances.addEntry(allowance, today.minus(DatePeriod(days = 12)), cad("15.00"), AllowanceKind.EARNED, l("Ramassé les feuilles", "Raked the leaves"))
        books.allowances.addEntry(allowance, today.minus(DatePeriod(days = 6)), cad("12.99"), AllowanceKind.SPENT, l("Livre", "Book"))
        // TAX-03: Sam pays quarterly instalments on freelance income.
        books.instalments.save(chequing.id, sam.id, today.year, TaxAuthority.CRA, List(4) { cad("450.00") })
        for (month in listOf(3, 6, 9)) {
            add(TransactionDraft(chequing.id, LocalDate(today.year, month, 14), cad("-450.00"), l("Receveur général du Canada", "Receiver General for Canada"), listOf(SplitDraft(cat("taxes.instalments"), cad("-450.00"))), memberId = sam.id))
        }
        // LN-02: the mortgage payments since the demo starts, each split from the balance owed.
        while (true) {
            val payment = books.loans.nextPayment(mortgage.id, today)?.takeIf { it.date <= today } ?: break
            books.loans.recordPayment(mortgage.id, chequing.id, payment)
        }
        books.transactions.create(
            TransactionDraft(visa.id, today, cad("-137.25"), "Amazon.com", originalAmount = Money.parse("-100.00", Currency.USD)),
        )
        books.transactions.transfer(TransferDraft(chequing.id, usd.id, today, cad("274.50"), Money.parse("200.00", Currency.USD), l("Achat de dollars US", "US dollar purchase")))
        importStatement(books, chequing, today)
        addBills(books, chequing, savings, visa, today)
        addCalendarAndHealth(books, group, chequing, alex, sam, lea, today)
        addBroughtInCalendars(books, today)
        addPetAndCarRecords(books, group, visa, rex, civic, today)
        addAssets(books, group, visa, alex, sam, lea, civic, today)
        addExtras(books, group, chequing, visa, alex, sam, civic, today)
        addInvestments(books, group, alex, sam, desjardins, today)
        addPlans(books, group, chequing, savings, alex, sam, lea, desjardins, today)
        addCrypto(books, group, chequing, alex, today)
        addMetals(books, group, alex, sam, desjardins, today)
        addDocuments(books, group, today)
        addContacts(books, group, alex, sam, lea, rex)
        // HH-05: Sam signs in too, as a member who can view the shared accounts.
        val samUser = books.users.add("sam", "Sam Demo", ca.schippers.hfm.domain.Role.MEMBER, "member-demo-password".toCharArray(), sam.id).userId
        books.users.setAccess(group, samUser, ca.schippers.hfm.domain.PermissionLevel.VIEW)
        // GOAL-01 to GOAL-04: three goals sharing the savings account.
        val goals = books.goals
        goals.save(SavingsGoal("", savings.id, l("Voyage en Gaspésie", "Trip to Newfoundland"), cad("4000"), LocalDate(today.year + 1, 7, 1), cad("250"), Recurrence.MONTHLY, start.plus(DatePeriod(days = 15))))
        val car = goals.save(SavingsGoal("", savings.id, l("Remplacement de l'auto", "Replacement car"), cad("15000"), LocalDate(today.year + 3, 6, 1), cad("200"), Recurrence(Frequency.SEMI_MONTHLY, secondDay = 0), LocalDate(start.year, start.month, 15)))
        goals.setAside(car.id, start, cad("3000"), l("Départ", "Starting amount"))
        val emergency = goals.save(SavingsGoal("", savings.id, l("Fonds d'urgence", "Emergency fund"), cad("6000")))
        goals.setAside(emergency.id, start, cad("2500"))
        goals.postScheduled(today)
        val firstMonth = LocalDate(start.year, start.month, 1)
        books.budgets.set(cat("food"), BudgetPeriod.MONTHLY, cad("450.00"), rollover = true, startMonth = firstMonth)
        books.budgets.set(cat("food.restaurants"), BudgetPeriod.MONTHLY, cad("50.00"), startMonth = firstMonth)
        books.budgets.set(cat("transport"), BudgetPeriod.MONTHLY, cad("120.00"), startMonth = firstMonth)
        books.budgets.set(cat("utilities"), BudgetPeriod.MONTHLY, cad("250.00"), startMonth = firstMonth)
        books.budgets.set(cat("housing"), BudgetPeriod.MONTHLY, cad("1450.00"), startMonth = firstMonth)
    }

    /**
     * Three documents waiting in the inbox (SYNC-05): a grocery receipt that matches a card
     * purchase, this month's electricity bill (BILL-03), and a receipt with no transaction yet.
     * Their text is given directly so the demo starts quickly; real imports are read by OCR.
     */
    private fun addDocuments(books: Books, group: String, today: LocalDate) {
        val lastSixth = LocalDate(today.year, today.month, 6).let { if (it > today) it.minus(DatePeriod(months = 1)) else it }
        val hydro = books.bills.list().first { it.name == l("Hydro-Québec", "Hydro Ottawa") }
        val due = books.bills.occurrences(today, today.plus(DatePeriod(days = 40)), setOf(hydro.id)).first().dueDate
        val documents = if (english) listOf(
            "IMG_4127.jpg" to listOf(
                "Loblaws", "1250 Main St W", "MILK 2% 4L          6.49", "WHOLE CHICKEN      17.98", "PRODUCE            42.37",
                "GROCERY            51.90", "HOUSEHOLD        H 60.69", "SUBTOTAL          179.43", "H = HST 13%         7.89", "TOTAL             187.32",
                "VISA ************1234", "$lastSixth 17:42",
            ),
            "Hydro-Ottawa-bill.jpg" to listOf(
                "Hydro Ottawa", "Your electricity bill", "Bill date: ${due.minus(DatePeriod(days = 21))}", "Account number: 6 1234 5678 9",
                "Amount due \$138.91", "Due date: $due",
            ),
            "scan-0031.jpg" to listOf(
                "Canadian Tire #412", "Receipt # 412-88213", "WASHER FLUID -40     5.99", "H11 BULB           24.99", "SUBTOTAL           30.98",
                "HST                 4.03", "TOTAL              35.01", "INTERAC", "${today.minus(DatePeriod(days = 1))} 10:12",
            ),
        ) else listOf(
            "IMG_4127.jpg" to listOf(
                "IGA Extra Famille Jodoin", "1250, boul. Charest Ouest", "LAIT 2% 4L          6,49", "POULET ENTIER      17,98", "FRUITS ET LEGUMES  42,37",
                "EPICERIE          112,59", "SOUS-TOTAL        179,43", "TPS                 2,64", "TVQ                 5,25", "TOTAL             187,32",
                "VISA ************1234", "$lastSixth 17:42",
            ),
            "Hydro-Quebec-facture.jpg" to listOf(
                "Hydro-Québec", "Votre facture d'électricité", "Date de facturation : ${due.minus(DatePeriod(days = 21))}", "Numéro de compte : 6 1234 5678 9",
                "Montant à payer 138,91 \$", "Date d'échéance : $due",
            ),
            "scan-0031.jpg" to listOf(
                "Canadian Tire #412", "Receipt # 412-88213", "LAVE-GLACE -40       5,99", "AMPOULE H11         24,99", "SUBTOTAL            30,98",
                "GST                 1,55", "QST                 3,09", "TOTAL               35,62", "INTERAC", "${today.minus(DatePeriod(days = 1))} 10:12",
            ),
        )
        for ((name, lines) in documents) {
            val doc = books.documents.import(group, receiptImage(lines), name, "image/jpeg").document
            books.documents.recordText(doc.id, 1, OcrResult(lines.map { OcrLine(it, 0.96f) }, 0), "demo", today)
        }
    }

    /** A receipt-like image, so the review screen has something to show. */
    private fun receiptImage(lines: List<String>): ByteArray {
        val img = java.awt.image.BufferedImage(620, 80 + lines.size * 40, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = java.awt.Color(248, 246, 240)
            fillRect(0, 0, img.width, img.height)
            color = java.awt.Color(35, 35, 35)
            font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 22)
            lines.forEachIndexed { i, line -> drawString(line, 30, 60 + i * 40) }
            dispose()
        }
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    /** The car's maintenance history and warranty, and the dog's vet visit and vaccines. */
    /**
     * INV-01 to INV-04: a non-registered account with a sale (a capital gain), a TFSA and an RRSP,
     * bought over the past two years, with dividends, a reinvested distribution and current prices.
     */
    private fun addInvestments(books: Books, group: String, alex: Member, sam: Member, institution: Institution, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun n(s: String) = BigDecimal(s)
        val inv = books.investments
        val opened = today.minus(DatePeriod(years = 2))
        // The cash each account started with, so the demo's chequing account is not drawn on years before it opens.
        fun account(name: String, type: AccountType, owners: Set<String>, number: String, cash: String) =
            books.accounts.create(AccountDraft(group, name, type, Currency.CAD, cad(cash), opened, institution.id, number, owners))
        val brokerage = account(l("Courtage Disnat", "Brokerage (TD)"), AccountType.BROKERAGE, setOf(alex.id, sam.id), "DIS-4471230", "22000")
        val tfsa = account(l("CELI Alex", "TFSA Alex"), AccountType.TFSA, setOf(alex.id), l("CELI-5512", "TFSA-5512"), "14000")
        val rrsp = account(l("REER Sam", "RRSP Sam"), AccountType.RRSP, setOf(sam.id), l("REER-8820", "RRSP-8820"), "6000")
        fun security(symbol: String, name: String, kind: SecurityKind, assetClass: AssetClass = AssetClass.EQUITY, region: Region = Region.CANADA) =
            inv.saveSecurity(Security("", symbol, "TSX", name, kind, Currency.CAD, assetClass, region))
        val xic = security("XIC", "iShares Core S&P/TSX Capped Composite", SecurityKind.ETF)
        val vfv = security("VFV", "Vanguard S&P 500 Index ETF", SecurityKind.ETF, region = Region.US)
        val zag = security("ZAG", "BMO Aggregate Bond Index ETF", SecurityKind.ETF, AssetClass.FIXED_INCOME)
        val xeqt = security("XEQT", "iShares Core Equity ETF Portfolio", SecurityKind.ETF, region = Region.GLOBAL).let {
            // INV-07: an all-in-one fund divided by region, as on its fact sheet (approximate).
            inv.saveSecurity(it.copy(regionMix = mapOf(Region.CANADA to n("25"), Region.US to n("45"), Region.INTERNATIONAL to n("25"), Region.EMERGING to n("5"))))
        }
        books.allocation.setTarget(
            TargetScope.Household, AllocationBy.CLASS,
            AllocationTarget(mapOf("EQUITY" to n("70"), "FIXED_INCOME" to n("20"), "CASH" to n("5"), "COMMODITY" to n("5"))),
        )
        val ry = security("RY", "Royal Bank of Canada", SecurityKind.STOCK)

        fun day(monthsAgo: Int) = today.minus(DatePeriod(months = monthsAgo))
        fun buy(a: Account, s: Security, monthsAgo: Int, qty: String, price: String, fees: String = "9.95") = inv.save(
            InvestmentTxn("", a.id, day(monthsAgo), InvestmentKind.BUY, s.id, n(qty), n(price), Money.of(n(qty).multiply(n(price)), Currency.CAD), cad(fees)),
        )
        fun income(a: Account, s: Security, monthsAgo: Int, amount: String, type: IncomeType = IncomeType.DIVIDEND) =
            inv.save(InvestmentTxn("", a.id, day(monthsAgo), InvestmentKind.INCOME, s.id, amount = cad(amount), incomeType = type))

        buy(brokerage, xic, 23, "200", "33.10")
        buy(brokerage, ry, 23, "40", "128.40")
        buy(brokerage, vfv, 18, "50", "112.25")
        buy(brokerage, xic, 12, "100", "35.80")
        for (m in listOf(21, 18, 15, 12, 9, 6, 3)) income(brokerage, xic, m, if (m > 12) "58.20" else "84.30")
        for (m in listOf(20, 17, 14, 11, 8, 5, 2)) income(brokerage, ry, m, "55.20")
        inv.save(InvestmentTxn("", brokerage.id, day(4), InvestmentKind.SELL, ry.id, n("15"), n("162.50"), cad("2437.50"), cad("9.95")))
        inv.save(InvestmentTxn("", brokerage.id, LocalDate(today.year - 1, 12, 31), InvestmentKind.NOTIONAL_DISTRIBUTION, vfv.id, amount = cad("21.40")))

        buy(tfsa, xeqt, 22, "250", "26.90", "0")
        buy(tfsa, xeqt, 10, "220", "30.70", "0")
        inv.save(InvestmentTxn("", tfsa.id, day(1), InvestmentKind.REINVEST, xeqt.id, n("3"), n("34.10"), cad("102.30"), incomeType = IncomeType.DISTRIBUTION))

        buy(rrsp, zag, 19, "300", "13.60")
        buy(rrsp, xic, 19, "50", "33.90")
        for (m in listOf(18, 15, 12, 9, 6, 3)) income(rrsp, zag, m, "36.00", IncomeType.DISTRIBUTION)

        for ((s, price) in listOf(xic to "39.85", vfv to "141.30", zag to "13.92", xeqt to "34.60", ry to "171.20")) inv.setPrice(s.id, today, n(price))
        // REC-08: last month's statement for the brokerage account, waiting to be checked.
        val held = inv.holdings(brokerage.id, day(1))
        inv.saveStatement(brokerage.id, day(1), held.cash, held.holdings.associate { it.security.id to it.quantity }, "MANUAL")
    }

    /** PM-01 to PM-04: gold coins in a safe deposit box and silver rounds at home, valued from spot prices entered by hand. */
    private fun addMetals(books: Books, group: String, alex: Member, sam: Member, institution: Institution, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        for (m in 0..6) {
            val day = today.minus(DatePeriod(months = m))
            books.prices.setSpot(Metal.GOLD, day, BigDecimal(3700 - m * 45))
            books.prices.setSpot(Metal.SILVER, day, BigDecimal("44.10").subtract(BigDecimal(m)))
        }
        val safe = books.accounts.create(AccountDraft(group, l("Métaux précieux", "Precious metals"), AccountType.PRECIOUS_METALS, Currency.CAD, cad("0"), LocalDate(2024, 1, 1), institution.id, ownerMemberIds = setOf(alex.id, sam.id)))
        books.metals.save(
            MetalItem("", safe.id, Metal.GOLD, MetalForm.COIN, l("Feuille d'érable 1 oz", "Maple Leaf 1 oz"), BigDecimal.ONE, WeightUnit.OZT, BigDecimal("0.9999"), 3, "", l("Monnaie royale canadienne", "Royal Canadian Mint"),
                LocalDate(2024, 5, 14), cad("9480"), BigDecimal("3"), MetalStorage.BANK_BOX, l("Desjardins, coffret 112", "TD, safe deposit box 112"), true, l("Assurance habitation, avenant de 15 000 $", "Home insurance rider, $15,000")),
        )
        books.metals.save(
            MetalItem("", safe.id, Metal.SILVER, MetalForm.ROUND, l("Rondelles d'argent 1 oz", "Silver rounds 1 oz"), BigDecimal.ONE, WeightUnit.OZT, BigDecimal("0.999"), 25, dealer = "Silver Gold Bull",
                purchaseDate = LocalDate(2025, 2, 3), cost = cad("1060"), storage = MetalStorage.HOME_SAFE, storageDetail = l("Coffre du sous-sol", "Basement safe")),
        )
    }

    /**
     * CR-01 to CR-06: Bitcoin bought monthly on an exchange, part of it moved to cold storage with
     * its network fee, some converted to ether, and a staking reward. Prices are entered by hand
     * for the demo; the downloads stay off.
     */
    private fun addCrypto(books: Books, group: String, chequing: Account, alex: Member, today: LocalDate) {
        val eth = Currency.of("ETH")
        fun day(monthsAgo: Int) = today.minus(DatePeriod(months = monthsAgo))
        for (m in 0..12) {
            books.rates.setManual(Currency.BTC, day(m), BigDecimal(118000 - m * 3500))
            books.rates.setManual(eth, day(m), BigDecimal(5600 - m * 120))
        }
        fun wallet(name: String, currency: Currency) =
            books.accounts.create(AccountDraft(group, name, AccountType.CRYPTO_WALLET, currency, Money.zero(currency), day(12), ownerMemberIds = setOf(alex.id)))
        val exchange = wallet("Shakepay BTC", Currency.BTC)
        val cold = wallet(l("Ledger (stockage à froid)", "Ledger (cold storage)"), Currency.BTC)
        val ether = wallet("Ether", eth)
        val crypto = books.crypto
        for (m in listOf(3, 2, 1)) {
            val price = BigDecimal(118000 - m * 3500)
            crypto.buy(exchange.id, chequing.id, day(m), Money.of(BigDecimal(250).divide(price, java.math.MathContext.DECIMAL64), Currency.BTC), Money.parse("250", Currency.CAD))
        }
        crypto.move(exchange.id, cold.id, day(1).plus(DatePeriod(days = 2)), Money.parse("0.004", Currency.BTC), Money.parse("0.00002", Currency.BTC))
        crypto.convert(exchange.id, ether.id, day(1).plus(DatePeriod(days = 5)), Money.parse("0.0008", Currency.BTC), Money.parse("0.0168", eth))
        crypto.income(ether.id, today.minus(DatePeriod(days = 3)), Money.parse("0.00012", eth), CryptoIncomeKind.STAKING)
    }

    /**
     * INV-09 to INV-11 and pensions: CRA room figures, contributions this year, an RESP for Léa with
     * a grant received, a retired relative's RRIF and QPP pension, Sam's workplace pension, and
     * beneficiaries.
     */
    private fun addPlans(books: Books, group: String, chequing: Account, savings: Account, alex: Member, sam: Member, lea: Member, institution: Institution, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        val plans = books.plans
        val accounts = books.accounts.list().map { it.account }
        val tfsa = accounts.first { it.name == l("CELI Alex", "TFSA Alex") }
        val rrsp = accounts.first { it.name == l("REER Sam", "RRSP Sam") }
        val year = today.year
        val thisYear = LocalDate(year, 1, 1)
        fun day(monthsAgo: Int) = today.minus(DatePeriod(months = monthsAgo))

        plans.saveRoom(RoomEntry("", alex.id, RoomPlan.TFSA, year, cad("31500")), group)
        plans.saveRoom(RoomEntry("", sam.id, RoomPlan.RRSP, year, cad("18500")), group)
        books.transactions.transfer(TransferDraft(chequing.id, tfsa.id, day(1), cad("1000"), memo = l("Cotisation CELI", "TFSA contribution")))
        books.transactions.transfer(TransferDraft(chequing.id, rrsp.id, maxOf(day(2), LocalDate(year, 3, 2)), cad("1500"), memo = l("Cotisation REER", "RRSP contribution")))
        plans.saveBeneficiary(Beneficiary("", tfsa.id, BeneficiaryKind.SUCCESSOR_HOLDER, sam.displayName, sam.id, l("Conjoint", "Spouse")))
        plans.saveBeneficiary(Beneficiary("", rrsp.id, BeneficiaryKind.BENEFICIARY, alex.displayName, alex.id, l("Conjoint", "Spouse"), BigDecimal(100)))

        val resp = books.accounts.create(AccountDraft(group, l("REEE Léa", "RESP Maya"), AccountType.RESP, Currency.CAD, cad("6000"), thisYear.minus(DatePeriod(years = 3)), institution.id, l("REEE-2231", "RESP-2231"), setOf(alex.id, sam.id)))
        plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, lea.displayName, lea.id))
        books.transactions.transfer(TransferDraft(savings.id, resp.id, maxOf(day(2), thisYear), cad("2500"), memo = l("Cotisation REEE", "RESP contribution"), memberId = lea.id))
        plans.recordGrant(resp.id, lea.id, maxOf(day(1), thisYear), GrantKind.CESG, cad("500"))

        // A retired relative living with the household: a RRIF paying monthly, and the QPP.
        val gilles = books.members.create(l("Gilles", "Gordon"), MemberKind.ADULT, LocalDate(1952, 8, 20))
        val rrif = books.accounts.create(AccountDraft(group, l("FERR Gilles", "RRIF Gordon"), AccountType.RRIF, Currency.CAD, cad("85000"), LocalDate(2018, 1, 1), institution.id, l("FERR-1180", "RRIF-1180"), setOf(gilles.id)))
        val qppCategory = books.categories.list().first { it.systemKey == "income.pension.qpp_cpp" }.id
        for (m in 1..today.month.ordinal + 1) {
            val date = LocalDate(year, m, 15)
            if (date > today) continue
            books.transactions.transfer(TransferDraft(rrif.id, chequing.id, date, cad("500"), memo = l("Retrait FERR", "RRIF withdrawal")))
            books.transactions.create(TransactionDraft(chequing.id, LocalDate(year, m, 25).let { if (it > today) date else it }, cad("812.40"), l("Retraite Québec", "Service Canada"), listOf(SplitDraft(qppCategory, cad("812.40"))), memberId = gilles.id))
        }
        plans.saveBeneficiary(Beneficiary("", rrif.id, BeneficiaryKind.BENEFICIARY, l("Succession de Gilles", "Estate of Gordon"), relationship = l("Succession", "Estate"), sharePercent = BigDecimal(100)))
        val qpp = plans.savePension(Pension("", gilles.id, if (english) PensionKind.CPP else PensionKind.QPP, l("Rente de retraite du RRQ", "CPP retirement pension"), l("Retraite Québec", "Service Canada"), indexed = true, payer = l("Retraite Québec", "Service Canada")), group)
        plans.saveStatement(qpp, PensionStatement("", qpp.id, year - 1, projectedAnnual = cad("9748.80")))
        val municipal = plans.savePension(
            Pension("", sam.id, PensionKind.DEFINED_BENEFIT, l("Régime de retraite des employés municipaux", "OMERS Primary Pension Plan"), l("Ville de Québec", "City of Ottawa"), l("RREM-44817", "OM-44817"), normalRetirementAge = 65, indexed = true, survivorPercent = BigDecimal(60)),
            group,
        )
        plans.saveStatement(municipal, PensionStatement("", municipal.id, year - 1, cad("6200"), cad("14800"), cad("38200"), cad("96400"), cad("5150")))
    }

    private fun addPetAndCarRecords(books: Books, group: String, visa: Account, rex: Pet, civic: Vehicle, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
        val vehicles = books.vehicles
        vehicles.saveWarranty(Warranty("", civic.id, WarrantyKind.POWERTRAIN, "Honda Canada", LocalDate(2021, 3, 1), today.plus(DatePeriod(days = 50)), 100_000, "1-888-946-6329"))
        vehicles.saveWarranty(Warranty("", civic.id, WarrantyKind.CORROSION, "Honda Canada", LocalDate(2021, 3, 1), LocalDate(2026, 3, 1).plus(DatePeriod(years = 2))))
        val start = today.minus(DatePeriod(months = 3))
        val tasks = vehicles.addStarterTasks(civic.id, today) { ca.schippers.hfm.i18n.Messages.get(books.language, "task.$it") }.associateBy { it.templateKey }
        // An oil change three months ago, with its payment; and a do-it-yourself filter change without one.
        vehicles.saveService(
            ServiceRecord("", civic.id, start.plus(DatePeriod(days = 5)), 61_500, l("Garage Tremblay", "Main Street Auto"), cost = cad("94.85"), taskIds = setOfNotNull(tasks["oil"]?.id, tasks["tire_rotation"]?.id)),
            PaymentDraft(visa.id, cat("transport.maintenance"), l("Garage Tremblay", "Main Street Auto")),
        )
        vehicles.saveService(ServiceRecord("", civic.id, start.plus(DatePeriod(days = 40)), 63_300, diy = true, cost = cad("24.99"), notes = l("Filtre Canadian Tire", "Canadian Tire filter"), taskIds = setOfNotNull(tasks["cabin_filter"]?.id)))
        books.transactions.create(
            TransactionDraft(visa.id, start.plus(DatePeriod(days = 2)), cad("-1184.00"), l("Desjardins Assurances", "Intact Insurance"), listOf(SplitDraft(cat("transport.insurance"), cad("-1184.00"))), assetId = civic.id),
        )

        val vet = books.health.saveProvider(HealthProvider("", group, l("Hôpital vétérinaire Charlesbourg", "Riverside Animal Hospital"), ProviderKind.VET, l("418-555-0190", "613-555-0190"), null, null, false))
        books.health.saveProvider(HealthProvider("", group, l("Toilettage Patte de velours", "Pampered Paws Grooming"), ProviderKind.GROOMER, l("418-555-0133", "613-555-0133"), null, null, false))
        books.transactions.create(
            TransactionDraft(visa.id, start.plus(DatePeriod(days = 20)), cad("-287.40"), l("Hôpital vétérinaire Charlesbourg", "Riverside Animal Hospital"), listOf(SplitDraft(cat("pets.vet"), cad("-287.40"))), memberId = rex.id),
        )
        books.transactions.create(
            TransactionDraft(visa.id, LocalDate(today.year, 1, 15).let { if (it > today) start else it }, cad("-35.00"), l("Ville de Québec", "City of Ottawa"), listOf(SplitDraft(cat("pets.licence"), cad("-35.00"))), memberId = rex.id),
        )
        books.health.saveImmunization(Immunization("", group, rex.id, l("Rage", "Rabies"), start.plus(DatePeriod(days = 20)), vet.id, start.plus(DatePeriod(days = 20, years = 3)), null))
        books.health.saveImmunization(Immunization("", group, rex.id, "DHPP", start.plus(DatePeriod(days = 20)).minus(DatePeriod(years = 1)), vet.id, today.plus(DatePeriod(days = 18)), null))
        books.calendar.create(EventDraft(group, l("Toilettage de Rex", "Rex's grooming"), EventCategory.PET, today.plus(DatePeriod(days = 4)), LocalTime(13, 30), 90, l("Patte de velours", "Pampered Paws"), memberId = rex.id))
    }

    /**
     * CAL-09: Alex works office hours, Sam works twelve-hour shifts on a two-week rotation at the
     * hospital, and the child goes to school, with a professional development day coming up.
     */
    private fun addSchedules(books: Books, shared: String, alex: Member, sam: Member, child: Member, today: LocalDate) {
        fun t(h: Int, m: Int = 0) = LocalTime(h, m)
        fun dow(n: Int) = kotlinx.datetime.DayOfWeek(n)
        val weekdays = (1..5)
        books.schedules.save(
            ca.schippers.hfm.books.PersonSchedule(
                "", shared, alex.id, ca.schippers.hfm.books.ScheduleKind.WORK, l("Bureau", "Office"), today.minus(DatePeriod(years = 2)), null, 1, true, null,
                weekdays.map { ca.schippers.hfm.books.ScheduleShift(0, dow(it), t(8), t(16, 30)) },
            ),
        )
        // Days in week 1, then nights in week 2: the rotation starts on this week's Monday.
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        books.schedules.save(
            ca.schippers.hfm.books.PersonSchedule(
                "", shared, sam.id, ca.schippers.hfm.books.ScheduleKind.WORK, l("Hôpital de l'Enfant-Jésus", "Civic Hospital"), monday.minus(DatePeriod(days = 28)), null, 2, false, null,
                listOf(1, 2, 5).map { ca.schippers.hfm.books.ScheduleShift(0, dow(it), t(7), t(19)) } +
                    listOf(3, 4).map { ca.schippers.hfm.books.ScheduleShift(1, dow(it), t(19), t(7)) } +
                    ca.schippers.hfm.books.ScheduleShift(1, dow(6), t(7), t(19)),
            ),
        )
        val schoolYear = if (today.month.ordinal >= 7) today.year else today.year - 1
        // A weekday about ten days ahead is a professional development day.
        val pdDay = today.plus(DatePeriod(days = 10)).let { d -> if (d.dayOfWeek.ordinal >= 5) d.plus(DatePeriod(days = 7 - d.dayOfWeek.ordinal)) else d }
        books.schedules.save(
            ca.schippers.hfm.books.PersonSchedule(
                "", shared, child.id, ca.schippers.hfm.books.ScheduleKind.SCHOOL, l("École Saint-Roch", "Hopewell Public School"),
                LocalDate(schoolYear, 9, 2), LocalDate(schoolYear + 1, 6, 23), 1, true, null,
                weekdays.map { ca.schippers.hfm.books.ScheduleShift(0, dow(it), t(8, 15), t(15, 5)) },
                listOf(ca.schippers.hfm.books.ScheduleException(pdDay, true, reason = l("Journée pédagogique", "PD day"))),
            ),
        )
    }

    /**
     * CSY-01 to CSY-04: two calendars Alex brought in from the phone: work, busy only (one lunch shared,
     * one appointment kept private), and the family's, shared.
     */
    private fun addBroughtInCalendars(books: Books, today: LocalDate) {
        fun day(n: Int) = today.plus(DatePeriod(days = n)).toString()
        fun item(id: String, n: Int, start: String?, end: String?, title: String, place: String? = null, vis: CalendarVisibility? = null, days: Int = 0) =
            CalendarInstance(id, day(n), day(n + days), start, end, title, place, vis)
        val now = System.currentTimeMillis()
        val until = day(60)
        books.broughtIn.receive(
            "demo-phone",
            CalendarSnapshot(
                "demo-work", "3", now, l("Travail", "Work"), l("alex@employeur.ca", "alex@employer.ca"), 0x3F51B5, CalendarVisibility.BUSY, day(0), until,
                listOf(
                    item("101", 2, "10:00", "11:30", l("Planification trimestrielle", "Quarterly planning"), l("Salle 4", "Room 4")),
                    item("102", 3, "14:00", "15:00", l("Appel client", "Client call")),
                    item("103", 4, "12:00", "13:00", l("Dîner d'équipe", "Team lunch"), l("Café du Monde", "The Wellington"), CalendarVisibility.SHARED),
                    item("104", 5, "07:30", "08:15", l("Physiothérapie", "Physio"), vis = CalendarVisibility.PRIVATE),
                    item("105", 10, "09:00", "16:00", l("Formation", "Training day")),
                ),
            ),
            now,
        )
        books.broughtIn.receive(
            "demo-phone",
            CalendarSnapshot(
                "demo-family", "5", now, l("Famille", "Family"), "alex.demo@gmail.com", 0x43A047, CalendarVisibility.SHARED, day(0), until,
                listOf(
                    item("201", 6, null, null, l("Tournoi de soccer de Léa", "Maya's soccer tournament"), l("Parc Victoria", "Mooney's Bay"), days = 1),
                    item("202", 8, "17:30", "20:00", l("Souper chez grand-maman", "Dinner at Grandma's")),
                ),
            ),
            now,
        )
    }

    /** Appointments of several kinds, and health records kept in Alex's private group. */
    private fun addCalendarAndHealth(books: Books, shared: String, chequing: Account, alex: Member, sam: Member, lea: Member, today: LocalDate) {
        fun day(n: Int) = today.plus(DatePeriod(days = n))
        val private = books.session.createGroup(l("Alex Demo - privé", "Alex Demo - private"), private = true)
        val health = books.health
        val pharmacy = health.saveProvider(HealthProvider("", shared, l("Pharmacie Jean Coutu", "Shoppers Drug Mart"), ProviderKind.PHARMACY, l("418-555-0100", "613-555-0100"), l("1200, boul. Charest", "1200 Bank St"), null, false))
        val doctor = health.saveProvider(HealthProvider("", shared, l("Dre Gagnon (GMF Limoilou)", "Dr. Patel (Glebe Family Health Team)"), ProviderKind.DOCTOR, l(l("418-555-0142", "613-555-0142"), "613-555-0142"), null, null, false))
        val dentist = health.saveProvider(HealthProvider("", shared, l("Clinique dentaire Saint-Roch", "Elgin Street Dental"), ProviderKind.DENTIST, l(l("418-555-0177", "613-555-0177"), "613-555-0177"), null, null, false))

        val calendar = books.calendar
        calendar.create(EventDraft(shared, l("Pose des pneus d'hiver", "Winter tires on"), EventCategory.VEHICLE, day(1), LocalTime(9, 30), 60, l("Garage Tremblay", "Main Street Auto"), reminderMinutes = listOf(1440, 60)))
        calendar.create(EventDraft(shared, l("Rencontre conseillère Desjardins", "Meeting with the TD advisor"), EventCategory.FINANCIAL, day(6), LocalTime(14, 0), 45, l("Caisse Desjardins", "TD branch, Bank St"), accountId = chequing.id))
        calendar.create(EventDraft(shared, l("Nettoyage dentaire", "Dental cleaning"), EventCategory.MEDICAL, day(9), LocalTime(10, 15), 60, memberId = lea.id, providerId = dentist.id))
        calendar.create(EventDraft(private, l("Bilan annuel", "Annual physical"), EventCategory.MEDICAL, day(14), LocalTime(8, 40), 30, memberId = alex.id, providerId = doctor.id))
        calendar.create(EventDraft(shared, l("Ramonage de la cheminée", "Chimney sweep"), EventCategory.HOME, day(20), reminderMinutes = listOf(2 * 1440)))
        // CAL-11: the child's activities, with the carpool and the cost of each lesson.
        val cad = { v: String -> ca.schippers.hfm.money.Money.parse(v, ca.schippers.hfm.money.Currency.CAD) }
        val noahsMom = ca.schippers.hfm.books.Driver(name = l("Julie (maman de Noah)", "Jen (Noah's mom)"))
        // Swimming is on Wednesdays, from the last one before today.
        val lastWednesday = today.minus(DatePeriod(days = ((today.dayOfWeek.ordinal + 5) % 7).let { if (it == 0) 7 else it }))
        val swimming = calendar.create(
            EventDraft(
                shared, l("Cours de natation", "Swimming lessons"), EventCategory.ACTIVITY, lastWednesday, LocalTime(18, 0), 60, l("Centre aquatique", "Brewer Pool"),
                memberId = lea.id, recurrence = Recurrence.WEEKLY, endDate = day(60), reminderMinutes = listOf(120),
                driverThere = ca.schippers.hfm.books.Driver(memberId = sam.id), driverBack = noahsMom, cost = cad("15.00"),
            ),
        )
        calendar.recordCost(swimming.id, lastWednesday, chequing.id, books.categories.list().first { it.systemKey == "children.activities" }.id)
        // In two weeks the other family drives both ways.
        calendar.setDrivers(swimming.id, lastWednesday.plus(DatePeriod(days = 14)), noahsMom, noahsMom)
        val saturday = today.plus(DatePeriod(days = (5 - today.dayOfWeek.ordinal + 7) % 7))
        calendar.create(
            EventDraft(
                shared, l("Match de soccer", "Soccer game"), EventCategory.ACTIVITY, saturday, LocalTime(9, 30), 90, l("Parc Victoria", "Lansdowne Park"),
                memberId = lea.id, recurrence = Recurrence.WEEKLY, endDate = saturday.plus(DatePeriod(days = 42)), reminderMinutes = listOf(1440),
                driverThere = ca.schippers.hfm.books.Driver(memberId = alex.id), driverBack = ca.schippers.hfm.books.Driver(memberId = alex.id),
            ),
        )
        addSchedules(books, shared, alex, sam, lea, today)

        health.saveMedication(Medication("", private, alex.id, l("Atorvastatine", "Atorvastatin"), "20 mg", l("1 comprimé au coucher", "1 tablet at bedtime"), doctor.id, pharmacy.id, "RX-448120", day(-400), null, 30, 2, day(-27), 5, true, null))
        health.saveMedication(Medication("", private, alex.id, l("Vitamine D", "Vitamin D"), l("1000 UI", "1000 IU"), l("1 par jour", "1 a day"), null, null, null, null, null, null, null, null, 5, true, null))
        health.saveMedication(Medication("", shared, sam.id, l("Salbutamol (inhalateur)", "Salbutamol (inhaler)"), "100 mcg", l("Au besoin", "As needed"), doctor.id, pharmacy.id, "RX-310077", day(-700), null, 90, 0, day(-60), 7, true, null))
        health.saveCondition(HealthCondition("", private, alex.id, l("Hypercholestérolémie", "High cholesterol"), day(-420), ConditionStatus.MANAGED, doctor.id, null))
        health.saveCondition(HealthCondition("", shared, sam.id, l("Asthme", "Asthma"), LocalDate(2009, 4, 1), ConditionStatus.MANAGED, doctor.id, null))
        health.saveAllergy(Allergy("", shared, lea.id, l("Arachides", "Peanuts"), l("Urticaire", "Hives"), Severity.SEVERE, l("Épipen dans le sac d'école", "EpiPen in the school bag")))
        health.saveTest(HealthTest("", private, alex.id, l("Bilan lipidique (LDL)", "Lipid panel (LDL)"), day(-35), l("2,4", "2.4"), "mmol/L", l("< 3,5", "< 3.5"), doctor.id, day(150), null))
        health.saveImmunization(Immunization("", shared, lea.id, "Influenza", day(-340), pharmacy.id, day(25), null))

        // MED-01 to MED-10: each spouse's employer plan, paying first for its own member.
        val med = books.medical
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun pct(s: String) = BigDecimal(s)
        val alexPlan = med.savePlan(
            MedPlan(
                "", shared, MedPlanKind.GROUP_HEALTH, l("Assurance collective (Alex)", "Group benefits (Alex)"), l("SSQ Assurance", "Manulife"), "G-48812", "C-0045123", alex.id,
                people = listOf(PlanPerson(alex.id, 1), PlanPerson(lea.id, 1), PlanPerson(sam.id, 2)),
            ),
        )
        val samPlan = med.savePlan(
            MedPlan("", shared, MedPlanKind.GROUP_HEALTH, l("Régime d'employeur (Sam)", "Employer plan (Sam)"), l("Beneva", "Canada Life"), "77105", "S-2231", sam.id, people = listOf(PlanPerson(sam.id, 1), PlanPerson(alex.id, 2), PlanPerson(lea.id, 2))),
        )
        listOf(
            MedCoverage("", alexPlan.id, MedService.PRESCRIPTION, pct("80")),
            MedCoverage("", alexPlan.id, MedService.DENTAL_PREVENTIVE, pct("90"), frequencyMonths = 9),
            MedCoverage("", alexPlan.id, MedService.DENTAL_BASIC, pct("80"), annualMax = cad("1500")),
            MedCoverage("", alexPlan.id, MedService.MASSAGE, pct("80"), perVisitMax = cad("60"), annualMax = cad("500")),
            MedCoverage("", alexPlan.id, MedService.PHYSIOTHERAPY, pct("80"), annualMax = cad("600")),
            MedCoverage("", alexPlan.id, MedService.EYE_EXAM, pct("100"), perVisitMax = cad("100"), frequencyMonths = 24),
            MedCoverage("", alexPlan.id, MedService.EYEWEAR, pct("100"), annualMax = cad("200"), frequencyMonths = 24),
        ).forEach { med.saveCoverage(it) }
        med.saveCoverage(MedCoverage("", samPlan.id, MedService.PRESCRIPTION, pct("100"), deductible = cad("50")))
        med.saveCoverage(MedCoverage("", samPlan.id, MedService.MASSAGE, pct("100"), annualMax = cad("300")))
        med.saveCoverage(MedCoverage("", samPlan.id, MedService.DENTAL_BASIC, pct("50")))
        fun expense(who: Member, service: MedService, days: Int, amount: String, what: String, provider: HealthProvider? = null) =
            med.saveExpense(MedExpense("", shared, who.id, service, day(days), cad(amount), provider?.id, description = what))
        // Paid by the first plan, nothing more to claim.
        val cleaning = expense(lea, MedService.DENTAL_PREVENTIVE, -120, "185.00", l("Nettoyage et examen", "Cleaning and exam"), dentist)
        med.recordPayment(med.submit(cleaning.id, alexPlan.id, day(-119)).id, day(-110), cad("166.50"))
        val glasses = expense(alex, MedService.EYEWEAR, -380, "420.00", l("Lunettes (Lunetterie New Look)", "Glasses (Bank Street Optical)"))
        med.recordPayment(med.submit(glasses.id, alexPlan.id, day(-379)).id, day(-370), cad("200.00"))
        // Paid by Alex's plan; Sam's plan can take the rest.
        val massage = expense(alex, MedService.MASSAGE, -40, "120.00", l("Massothérapie", "Registered massage therapist"))
        med.recordPayment(med.submit(massage.id, alexPlan.id, day(-39)).id, day(-30), cad("60.00"))
        // Waiting for payment, still to send, and one about to expire.
        val inhaler = expense(sam, MedService.PRESCRIPTION, -5, "42.30", "Salbutamol", pharmacy)
        med.submit(inhaler.id, samPlan.id, day(-5))
        expense(sam, MedService.PHYSIOTHERAPY, -15, "95.00", l("Physiothérapie (épaule)", "Shoulder, after a fall"))
        expense(lea, MedService.EYE_EXAM, -340, "95.00", l("Examen de la vue", "Optometrist, yearly exam"))
    }

    /** OTH-02, MNT-08, MNT-09, SAL-04, SAL-05, CC-03: trips, contractors, a roof, Sam's invoices, a duplex and card rewards. */
    private fun addExtras(books: Books, group: String, chequing: Account, visa: Account, alex: Member, sam: Member, civic: Vehicle, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
        fun day(n: Int) = today.plus(DatePeriod(days = n))
        // OTH-02, MED-11: Sam's client visits, and a specialist far from home.
        for ((n, place, km) in listOf(Triple(-70, l("Client à Lévis", "Client in Kanata"), "28"), Triple(-41, l("Client à Montmagny", "Client in Smiths Falls"), "78"), Triple(-12, l("Client à Lévis", "Client in Kanata"), "28"))) {
            books.trips.save(Trip("", group, day(n), place, BigDecimal(km), true, TripPurpose.BUSINESS, civic.id, sam.id))
        }
        books.trips.save(Trip("", group, day(-25), l("Épicerie", "Groceries"), BigDecimal("6"), true, TripPurpose.PERSONAL, civic.id, alex.id))
        books.trips.save(Trip("", group, day(-33), l("Institut de cardiologie de Montréal", "Kingston Health Sciences Centre"), BigDecimal(if (english) "196" else "253"), true, TripPurpose.MEDICAL, civic.id, alex.id))
        // MNT-08, MNT-09: the roofer, the plumber, and the new roof on the house.
        val roofer = books.contractors.save(Contractor("", group, l("Toitures Gagnon", "Capital Roofing"), l("Couvreur", "Roofer"), l("418-555-0142", "613-555-0142"), website = l("toituresgagnon.example", "capitalroofing.example")))
        books.contractors.saveJob(roofer, ContractorJob("", day(-120), l("Nouveau toit", "New roof"), cad("14200.00"), 5))
        val plumber = books.contractors.save(Contractor("", group, l("Plomberie Roy", "Rideau Plumbing"), l("Plombier", "Plumber"), l("418-555-0177", "613-555-0177")))
        books.contractors.saveJob(plumber, ContractorJob("", day(-300), l("Chauffe-eau", "Water heater"), cad("1850.00"), 4))
        books.contractors.saveJob(plumber, ContractorJob("", day(-40), l("Fuite sous l'évier", "Leak under the sink"), cad("240.00"), 3))
        val house = books.assets.list().first { it.kind == AssetKind.HOME }
        val roof = books.homeProjects.save(HomeProject("", group, l("Nouveau toit", "New roof"), ProjectStatus.DONE, Currency.CAD, house.id, day(-130), day(-118), cad("15000.00")))
        books.homeProjects.addCost(roof, day(-130), l("Dépôt", "Deposit"), cad("4000.00"), roofer.id)
        books.homeProjects.addCost(roof, day(-118), l("Solde", "Balance"), cad("10200.00"), roofer.id)
        books.homeProjects.save(HomeProject("", group, l("Terrasse arrière", "Back deck"), ProjectStatus.PLANNED, Currency.CAD, house.id, budget = cad("6500.00")))
        // SAL-04: Sam's freelance invoices: one paid, one waiting, one overdue.
        // The sales taxes in effect on each invoice's date in Sam's province (Rates and rules): HST in Ontario, GST and QST in Quebec.
        fun taxes(on: LocalDate) = ca.schippers.hfm.calc.salestax.SalesTaxes.ratesOn(on, books.provinceOf(sam.id)).map { InvoiceTax(it.label, it.rate, it.onGst) }
        fun invoice(n: Int, customer: String, lines: List<InvoiceLine>, due: Int) = books.invoices.save(
            Invoice("", group, books.invoices.nextNumber(day(n).year), customer, day(n), Currency.CAD, lines, InvoiceStatus.SENT, day(n + due), memberId = sam.id, taxes = taxes(day(n))),
        )
        val first = invoice(-60, l("Atelier Lavoie inc.", "Lavoie Studio Inc."), listOf(InvoiceLine(l("Conception graphique", "Graphic design"), "12", "65.00")), 30)
        books.invoices.markPaid(first, day(-35), chequing.id)
        invoice(-40, l("Café du Quai", "Harbour Café"), listOf(InvoiceLine(l("Menus et affiches", "Menus and posters"), "8", "65.00"), InvoiceLine(l("Impression", "Printing"), "1", "120.00")), 30)
        invoice(-9, l("Atelier Lavoie inc.", "Lavoie Studio Inc."), listOf(InvoiceLine(l("Site Web : maquettes", "Website mock-ups"), "15", "65.00")), 30)
        // SAL-05: the duplex Alex and a sister own half each; its rent and costs carry its tag.
        val duplex = books.rentals.save(RentalProperty("", group, l("Duplex rue Cartier", "Duplex on Bank Street"), "", l("212, rue Cartier, Québec", "212 Bank Street, Ottawa"), shareBp = 5_000))
        val tag = books.tags().first { it.id == duplex.tagId }.name
        for (m in 3 downTo 1) {
            books.transactions.create(TransactionDraft(chequing.id, today.minus(DatePeriod(months = m)), cad("1150.00"), l("Locataire", "Tenant"), listOf(SplitDraft(cat("income.rental"), cad("1150.00"))), tags = setOf(tag)))
        }
        books.transactions.create(TransactionDraft(chequing.id, day(-50), cad("-1240.00"), l("Ville de Québec", "City of Ottawa"), listOf(SplitDraft(cat("housing.municipal_tax"), cad("-1240.00"))), tags = setOf(tag)))
        books.transactions.create(TransactionDraft(chequing.id, day(-38), cad("-240.00"), l("Plomberie Roy", "Rideau Plumbing"), listOf(SplitDraft(cat("housing.maintenance"), cad("-240.00"))), tags = setOf(tag)))
        // CC-03: points on the Visa.
        books.rewards.save(CardReward(visa.id, group, l("Desjardins BONUSDOLLARS", "TD Rewards"), RewardUnit.POINTS, BigDecimal("3"), BigDecimal("0.005")))
        books.rewards.addEntry(visa.id, day(-65), BigDecimal("4120"), RewardKind.EARNED)
        books.rewards.addEntry(visa.id, day(-35), BigDecimal("3985"), RewardKind.EARNED)
        books.rewards.addEntry(visa.id, day(-20), BigDecimal("5000"), RewardKind.REDEEMED, cad("25.00"), l("Carte-cadeau", "Gift card"))
    }

    /** AST, WAR, INS: the house and what is in it, warranties, and the household's policies. */
    private fun addAssets(books: Books, group: String, visa: Account, alex: Member, sam: Member, lea: Member, civic: Vehicle, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun day(n: Int) = today.plus(DatePeriod(days = n))
        val assets = books.assets
        val house = assets.save(
            Asset(
                "", group, AssetKind.HOME, l("Maison (rue des Érables)", "House (Maple Street)"), purchaseDate = LocalDate(2018, 6, 29), purchasePrice = cad("389000"),
                valueMethod = ValueMethod.MANUAL, value = cad("515000"), valueDate = day(-60), location = l("Québec", "Ottawa"), notes = l("Évaluation municipale 2025 : 498 300 $", "MPAC assessment: $498,300"),
            ),
        )
        assets.save(
            Asset(
                "", group, AssetKind.HEATING_COOLING, l("Thermopompe", "Heat pump"), house.id, "Mitsubishi", "MUZ-FS12", "MZ-221873", LocalDate(2022, 5, 10), l("Climatisation Laval", "Capital Heating & Cooling"), cad("6850"),
                location = l("Extérieur", "Outside"), valueMethod = ValueMethod.DEPRECIATION, depreciationYears = 15,
            ),
        )
        val fridgeBuy = books.transactions.create(TransactionDraft(visa.id, day(-45), cad("-1899.00"), l("Brault & Martineau", "The Brick")))
        val fridge = assets.save(
            Asset(
                "", group, AssetKind.APPLIANCE, l("Réfrigérateur", "Refrigerator"), house.id, "LG", "LRMVS3006S", "SN-77120", day(-45), l("Brault & Martineau", "The Brick"), cad("1899.00"), fridgeBuy.id, l("Cuisine", "Kitchen"),
                valueMethod = ValueMethod.DEPRECIATION, depreciationYears = 12,
            ),
        )
        assets.saveWarranty(AssetWarranty("", group, fridge.id, AssetWarrantyKind.MANUFACTURER, "LG Canada", l("Pièces et main-d'oeuvre", "Parts and labour"), day(-45), day(320), phone = "1-888-542-2623"))
        assets.saveWarranty(AssetWarranty("", group, fridge.id, AssetWarrantyKind.CARD_EXTENDED, l("Visa Desjardins", "TD Visa"), cardAccountId = visa.id))
        val tv = assets.save(Asset("", group, AssetKind.ELECTRONICS, l("Téléviseur 65 po", "65-inch TV"), house.id, "Samsung", "QN65Q80", purchaseDate = day(-700), purchasePrice = cad("1499.99"), location = l("Salon", "Living room")))
        assets.saveWarranty(AssetWarranty("", group, tv.id, AssetWarrantyKind.EXTENDED, l("Best Buy (plan de protection)", "Best Buy (protection plan)"), startDate = day(-700), endDate = day(30)))
        assets.save(Asset("", group, AssetKind.SPORTS, "Kayaks (2)", purchaseDate = day(-420), purchasePrice = cad("1250"), location = l("Chalet", "Cottage")))

        // MNT: the home's usual tasks, with the furnace filter overdue, and a pontoon boat with an hour meter.
        val upkeep = books.assetMaintenance
        fun task(key: String) = ca.schippers.hfm.i18n.Messages.get(books.language, "assetTemplate.$key")
        val homeTasks = upkeep.addStarterTasks(house.id, today, ::task).associateBy { it.templateKey }
        upkeep.saveService(
            AssetServiceRecord("", house.id, day(-100), diy = true, parts = l("Filtre MERV 11 (16x25x1)", "MERV 11 filter (16x25x1)"), cost = cad("32.99"), taskIds = setOfNotNull(homeTasks["furnace_filter"]?.id)),
        )
        val boat = assets.save(
            Asset("", group, AssetKind.BOAT, l("Ponton Princecraft", "Princecraft pontoon"), null, "Princecraft", "Vectra 21", purchaseDate = day(-800), purchasePrice = cad("38500"), location = l("Chalet", "Cottage"), meter = MeterUnit.HOURS),
        )
        upkeep.addReading(boat.id, day(-150), 212)
        upkeep.addReading(boat.id, day(-20), 268)
        val boatTasks = upkeep.addStarterTasks(boat.id, today, ::task).associateBy { it.templateKey }
        upkeep.saveService(
            AssetServiceRecord("", boat.id, day(-140), 214, l("Marina du Lac-Beauport", "Lakeside Marina"), parts = l("Huile 10W-30, filtre", "10W-30 oil, filter"), cost = cad("189.50"), taskIds = setOfNotNull(boatTasks["engine_oil"]?.id, boatTasks["boat_launch"]?.id)),
            PaymentDraft(visa.id, books.categories.list().first { it.systemKey == "leisure.cottage_rv" }.id, l("Marina du Lac-Beauport", "Lakeside Marina")),
        )

        val insurance = books.insurance
        insurance.save(
            InsurancePolicy(
                "", group, PolicyKind.HOME, l("Desjardins Assurances", "Intact Insurance"), l("Courtier Morin", "Wilson Insurance Brokers"), "H-2241897", alex.id, cad("1384"), deductible = cad("1000"),
                coverage = cad("520000"), startDate = day(-340), renewalDate = day(25), assetIds = setOf(house.id),
            ),
        )
        insurance.save(
            InsurancePolicy("", group, PolicyKind.AUTO, l("Desjardins Assurances", "Intact Insurance"), policyNumber = "A-7781020", premium = cad("96.50"), frequency = PremiumFrequency.MONTHLY, deductible = cad("500"), renewalDate = day(140), assetIds = setOf(civic.id)),
        )
        val life = insurance.save(
            InsurancePolicy("", group, PolicyKind.LIFE, "Sun Life", policyNumber = "L-500212", insuredMemberId = alex.id, premium = cad("42.15"), frequency = PremiumFrequency.MONTHLY, coverage = cad("500000"), coverageNotes = l("Temporaire 20 ans", "20-year term")),
        )
        insurance.saveBeneficiary(PolicyBeneficiary("", life.id, sam.displayName, sam.id, l("Conjoint", "Spouse"), BigDecimal(100)))
        insurance.saveBeneficiary(PolicyBeneficiary("", life.id, lea.displayName, lea.id, l("Enfant", "Child"), BigDecimal(100), contingent = true))
    }

    /**
     * CON-01 to CON-06: the contacts gathered from the institutions, providers, contractors, insurers
     * and estate contacts above (only records with the same name made one), each given what it is
     * for, then a few more: two banks, two pharmacies and several doctors told apart by that line.
     */
    private fun addContacts(books: Books, group: String, alex: Member, sam: Member, lea: Member, rex: Pet) {
        val contacts = books.contacts
        contacts.gather(group, contacts.proposals().flatMap { p -> p.sources.groupBy { SearchService.fold(it.name) }.values.map { GatherDecision(it) } })
        fun named(name: String) = contacts.list(ContactFilter(includeArchived = true)).first { it.name == name }
        fun update(name: String, change: (Contact) -> Contact) = contacts.save(change(named(name)))
        fun phone(label: String?, value: String) = ContactDetail(type = DetailType.PHONE, label = label, value = value)
        val accounts = books.accounts.list().map { it.account }

        val bank = update(l("Desjardins", "TD Canada Trust")) {
            it.copy(
                purpose = l("Compte conjoint, Visa, placements et hypothèque du chalet", "Joint chequing, Visa, investments and cottage mortgage"),
                details = it.details + phone(l("Service à la clientèle", "Customer service"), l("1 800 224-7737", "1 866 222-3456")) + phone(l("Cartes perdues", "Lost cards"), l("1 800 363-3380", "1 800 983-2582")),
                website = l("desjardins.com", "td.com"),
            )
        }
        update(l("Banque Nationale", "Tangerine")) { it.copy(purpose = l("Compte en dollars US", "US dollar account"), website = l("bnc.ca", "tangerine.ca")) }
        update(l("Desjardins Assurances", "Intact Insurance")) {
            it.copy(
                purpose = l("Assurance maison et auto", "Home and car insurance"),
                details = it.details + phone(l("Réclamations", "Claims"), l("1 888 776-8343", "1 855 464-6828")) + ContactDetail(type = DetailType.NUMBER, label = l("Numéro de client", "Client number"), value = "C-44719025"),
            )
        }
        update("Sun Life") { it.copy(purpose = l("Assurance vie d’Alex", "Alex's life insurance"), memberIds = setOf(alex.id)) }
        // The advisor named in Alex's estate papers works at the bank and looks after the RRSP and TFSA.
        val advisor = update(l("Marc Lavoie", "Daniel Wong")) {
            it.copy(person = true, organizationId = bank.id, jobTitle = l("Conseiller en placement", "Investment advisor"), purpose = l("REER et CELI", "RRSP and TFSA"), memberIds = setOf(alex.id, sam.id))
        }
        accounts.filter { it.type == AccountType.RRSP || it.type == AccountType.TFSA }.forEach { contacts.link(advisor.id, LinkRole.ADVISOR, LinkTarget.ACCOUNT, it.id) }

        update(l("Dre Gagnon (GMF Limoilou)", "Dr. Patel (Glebe Family Health Team)")) {
            it.copy(purpose = l("Médecin de famille de toute la famille", "Family doctor for all of us"), hours = l("Lun-ven 8 h-17 h; sans rendez-vous le samedi", "Mon-Fri 8-5; walk-in Saturday mornings"))
        }
        update(l("Pharmacie Jean Coutu", "Shoppers Drug Mart")) { it.copy(purpose = l("Ordonnances d’Alex et de Sam", "Alex's and Sam's prescriptions"), hours = l("Tous les jours 8 h-22 h", "Every day 8 a.m.-10 p.m.")) }
        update(l("Clinique dentaire Saint-Roch", "Elgin Street Dental")) { it.copy(purpose = l("Dentiste de la famille", "Family dentist")) }
        val vet = update(l("Hôpital vétérinaire Charlesbourg", "Riverside Animal Hospital")) { it.copy(purpose = l("Vétérinaire de Rex", "Rex's vet"), memberIds = setOf(rex.id)) }
        contacts.link(vet.id, LinkRole.VETERINARIAN, LinkTarget.PET, rex.id)

        fun add(c: Contact) = contacts.save(c.copy(groupId = group))
        add(
            Contact(
                "", group, l("Dre Nadia Bélanger", "Dr. Lisa Chen"), person = true, jobTitle = l("Dermatologue", "Dermatologist"), kinds = setOf(ContactKind.SPECIALIST),
                purpose = l("Dermatologue de Sam", "Sam's dermatologist"), memberIds = setOf(sam.id), details = listOf(phone(l("Clinique", "Clinic"), l("418-555-0161", "613-555-0161"))),
                address = l("2600, boul. Laurier, Québec", "1081 Carling Ave, Ottawa"), notes = l("Un rendez-vous par année; demander une référence à la Dre Gagnon.", "Once a year; ask Dr. Patel for a referral."),
            ),
        )
        add(
            Contact(
                "", group, l("Orthodontie Limoilou", "Ottawa Orthodontics"), kinds = setOf(ContactKind.SPECIALIST, ContactKind.DENTIST),
                purpose = l("Broches de Léa", "Maya's braces"), memberIds = setOf(lea.id), details = listOf(phone(null, l("418-555-0125", "613-555-0125"))),
            ),
        )
        add(
            Contact(
                "", group, l("Pharmacie Uniprix du Lac", "Rexall by the Lake"), kinds = setOf(ContactKind.PHARMACY),
                purpose = l("Près du chalet", "Near the cottage"), details = listOf(phone(null, l("418-555-0108", "613-555-0108"))), hours = l("Lun-sam 9 h-18 h", "Mon-Sat 9-6"),
            ),
        )
        val hydro = add(
            Contact(
                "", group, l("Hydro-Québec", "Hydro Ottawa"), kinds = setOf(ContactKind.UTILITY), purpose = l("Électricité de la maison", "Electricity at home"),
                details = listOf(phone(l("Service à la clientèle", "Customer service"), l("1 888 385-7252", "613-738-6400")), ContactDetail(type = DetailType.NUMBER, label = l("Numéro de compte", "Account number"), value = "6 1234 5678 9")),
                website = l("hydroquebec.com", "hydroottawa.com"),
            ),
        )
        books.bills.list().firstOrNull { it.name == l("Hydro-Québec", "Hydro Ottawa") }?.let { contacts.link(hydro.id, LinkRole.BILLER, LinkTarget.BILL, it.id) }
        add(
            Contact(
                "", group, l("Comptabilité Roy", "Kim Accounting"), kinds = setOf(ContactKind.ACCOUNTANT), purpose = l("Déclarations de revenus de Sam (travail autonome)", "Sam's freelance tax returns"),
                memberIds = setOf(sam.id), details = listOf(ContactDetail(type = DetailType.EMAIL, value = l("info@comptabiliteroy.example", "office@kimaccounting.example"))),
            ),
        )
        add(
            Contact(
                "", group, l("École Saint-Fidèle", "Glebe Elementary School"), kinds = setOf(ContactKind.SCHOOL), purpose = l("École de Léa", "Maya's school"),
                memberIds = setOf(lea.id), details = listOf(phone(l("Secrétariat", "Office"), l("418-555-0150", "613-555-0150")), phone(l("Service de garde", "Daycare"), l("418-555-0151", "613-555-0151"))),
            ),
        )
    }

    /** Bills from next month on (this month's are already entered), plus a few due within days. */
    private fun addBills(books: Books, chequing: Account, savings: Account, visa: Account, today: LocalDate) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
        fun next(day: Int): LocalDate {
            val thisMonth = LocalDate(today.year, today.month, day)
            return if (thisMonth > today) thisMonth else thisMonth.plus(DatePeriod(months = 1))
        }
        val bills = books.bills
        bills.create(BillDraft(BillKind.BILL, l("Loyer", "Rent"), cad("1450.00"), chequing.id, Recurrence.MONTHLY, next(1), l("Propriétaire", "Landlord"), categoryId = cat("housing.rent"), paymentMethod = PaymentMethod.CHEQUE))
        bills.create(
            BillDraft(
                BillKind.BILL, l("Hydro-Québec", "Hydro Ottawa"), cad("132.48"), chequing.id, Recurrence(Frequency.MONTHLY, adjust = BusinessDayAdjust.NEXT), next(12),
                l("Hydro-Québec", "Hydro Ottawa"), "6 1234 5678 9", AmountKind.VARIABLE, paymentMethod = PaymentMethod.PAD, categoryId = cat("utilities.electricity"),
            ),
        )
        bills.create(BillDraft(BillKind.BILL, l("Vidéotron", "Rogers"), cad("95.00"), chequing.id, Recurrence.MONTHLY, next(18), l("Vidéotron", "Rogers"), paymentMethod = PaymentMethod.PAD, categoryId = cat("utilities.internet")))
        bills.create(
            BillDraft(BillKind.BILL, l("Assurance habitation", "Home insurance"), cad("1184.00"), chequing.id, Recurrence.ANNUAL, today.plus(DatePeriod(days = 5)), l("Desjardins Assurances", "Intact Insurance"), categoryId = cat("housing.insurance")),
        )
        bills.create(
            BillDraft(
                BillKind.BILL, l("Diffusion en continu", "Streaming"), cad("18.99"), visa.id, Recurrence.MONTHLY, today.plus(DatePeriod(days = 3)), "StreamCo",
                paymentMethod = PaymentMethod.CARD, categoryId = cat("utilities.tv_streaming"), isSubscription = true, cancelBy = today.plus(DatePeriod(days = 3)),
            ),
        )
        bills.create(BillDraft(BillKind.INCOME, l("Paie", "Pay"), cad("3150.00"), chequing.id, Recurrence(Frequency.SEMI_MONTHLY, secondDay = 1), next(15), l("Employeur inc.", "Employer Inc."), categoryId = cat("income.employment.salary")))
        bills.create(BillDraft(BillKind.TRANSFER, l("Épargne mensuelle", "Monthly savings"), cad("500.00"), chequing.id, Recurrence.MONTHLY, next(16), transferAccountId = savings.id))
    }

    /**
     * A bank statement for the joint account up to the end of last month, as if downloaded: most
     * lines match what was entered, Vidéotron posts 4 days late (so it is proposed for confirmation),
     * a purchase in US dollars costs 2.5 % more than recorded (REC-04), and a monthly bank fee was
     * never entered.
     */
    private fun importStatement(books: Books, account: Account, today: LocalDate) {
        val end = LocalDate(today.year, today.month, 1).minus(DatePeriod(days = 1))
        books.transactions.create(
            TransactionDraft(
                account.id, end.minus(DatePeriod(days = 10)), Money.parse("-109.60", Currency.CAD), "Booking.com",
                originalAmount = Money.parse("-80.00", Currency.USD), fxRate = BigDecimal("1.37"),
            ),
        )
        val recorded = books.transactions.register(account.id).map { it.transaction }.filter { it.date <= end }
        val payees = books.payees.list().associate { it.id to it.name }
        val fee = Money.parse("-4.95", Currency.CAD)
        // The bank's conversion: 2.5 % on top of the rate entered.
        fun posted(t: Transaction) = if (t.originalAmount != null) Money.of(t.amount.toBigDecimal().multiply(BigDecimal("1.025")), Currency.CAD, RoundingMode.HALF_UP) else t.amount
        val lines = recorded.mapIndexed { i, t ->
            val name = t.payeeId?.let(payees::get) ?: if (t.transfer != null) l("VIREMENT", "TRANSFER") else "?"
            val shift = if (name.startsWith(l("Vidéotron", "Rogers"))) 4 else 0
            ImportedLine("D$i", t.date.plus(DatePeriod(days = shift)).let { if (it > end) end else it }, posted(t), name.uppercase(), null, null)
        } + ImportedLine("FEE", end, fee, l("FRAIS MENSUELS DU FORFAIT", "MONTHLY PLAN FEE"), null, null)
        val closing = account.openingBalance + recorded.map(::posted).fold(Money.zero(Currency.CAD), Money::plus) + fee
        books.statements.import(
            account.id,
            ImportedStatement("OFX", "0045678", Currency.CAD, account.openingDate, end, account.openingBalance, closing, lines),
            l("releve-desjardins.ofx", "td-statement.ofx"),
        )
    }
}
