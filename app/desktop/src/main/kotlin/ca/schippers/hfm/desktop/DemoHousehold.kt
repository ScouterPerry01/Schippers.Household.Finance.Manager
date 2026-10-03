package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AccountDraft
import ca.schippers.hfm.books.AllocationBy
import ca.schippers.hfm.books.AllocationTarget
import ca.schippers.hfm.books.AmountKind
import ca.schippers.hfm.books.BillDraft
import ca.schippers.hfm.books.BillKind
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
import ca.schippers.hfm.books.Medication
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
 * Started with `./gradlew :app:desktop:runDemo`; never touches real households.
 */
object DemoHousehold {
    const val LOGIN = "demo"
    const val PASSWORD = "demo-password"

    fun create(store: HouseholdStore, language: ca.schippers.hfm.i18n.Language = ca.schippers.hfm.i18n.Language.ENGLISH): HouseholdSession {
        val dir = Files.createTempDirectory("hfm-demo").resolve("Demo.hfm")
        val created = store.create(dir, "Famille Démo", LOGIN, "Alex Demo", PASSWORD.toCharArray())
        val books = Books(created.session).also { it.language = language }
        fill(books)
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
        val lea = books.members.create("Léa", MemberKind.CHILD, LocalDate(2015, 6, 12))
        val desjardins = books.institutions.create(Institution("", "Desjardins", institutionNumber = "815", transitNumber = "30123"))
        val bank = books.institutions.create(Institution("", "Banque Nationale", institutionNumber = "006"))

        val chequing = books.accounts.create(
            AccountDraft(group, "Compte conjoint", AccountType.CHEQUING, Currency.CAD, cad("2450.00"), start, desjardins.id, "815-30123-0045678", setOf(alex.id, sam.id)),
        )
        val savings = books.accounts.create(AccountDraft(group, "Épargne", AccountType.HIGH_INTEREST_SAVINGS, Currency.CAD, cad("8000.00"), start, desjardins.id))
        val visa = books.accounts.create(AccountDraft(group, "Visa Desjardins", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), start, desjardins.id, "4540123412341234"))
        books.creditCards.saveTerms(visa.id, CreditCardTerms(cad("8000"), BigDecimal("0.1995"), statementDay = 20, dueDay = 10, minPaymentPercent = BigDecimal("0.05"), minPaymentFloor = cad("10")))
        val usd = books.accounts.create(AccountDraft(group, "Compte US", AccountType.CHEQUING, Currency.USD, Money.parse("500.00", Currency.USD), start, bank.id))

        // A cottage mortgage renewed two years ago, with its term ending soon (LN-01 to LN-04).
        val firstPayment = LocalDate(start.year, start.month, 1).minus(DatePeriod(months = 22))
        val terms = LoanTerms(cad("148000"), BigDecimal("0.0489"), Compounding.SEMI_ANNUAL, 21 * 12, PaymentFrequency.MONTHLY)
        val owedAtStart = LoanProjection.project(LoanPlan(terms, firstPayment)).balanceOn(start.minus(DatePeriod(days = 1)))
        val mortgage = books.accounts.create(AccountDraft(group, "Hypothèque du chalet", AccountType.MORTGAGE, Currency.CAD, -owedAtStart, start, desjardins.id, "MTG-7745120"))
        books.loans.save(
            LoanDetails(
                mortgage.id, terms.principal, terms.annualRate, amortizationMonths = terms.amortizationMonths, firstPaymentDate = firstPayment,
                termEnd = today.plus(DatePeriod(days = 100)), paymentAccountId = chequing.id, lastPaidDate = start.minus(DatePeriod(days = 1)),
            ),
        )

        // A dog and a car (PET-01, VEH-01), created first so the monthly activity can refer to them.
        val rex = books.pets.save(
            Pet(
                "", "Rex", Species.DOG, "Golden retriever", Sex.MALE, LocalDate(2021, 5, 3), neutered = true, colour = "Doré", microchip = "985141000123456",
                licenceNumber = "2026-04127", licenceMunicipality = "Ville de Québec", licenceExpiry = today.plus(DatePeriod(days = 21)),
                insurer = "Trupanion", policyNumber = "TP-88213", insuranceRenewal = today.plus(DatePeriod(months = 5)), ownerMemberId = lea.id,
            ),
        )
        val civic = books.vehicles.save(
            Vehicle(
                "", group, "Civic", "Honda", "Civic", 2021, "EX", "Gris", "2HGFE2F59MH512345", "F42 KLM", FuelType.GASOLINE, sam.id,
                LocalDate(2023, 4, 12), cad("24500"), "Honda de Sainte-Foy", 38_200, Currency.CAD, today.plus(DatePeriod(months = 7)),
                "Desjardins Assurances", "AUT-5521873", today.plus(DatePeriod(days = 12)),
            ),
        )
        var odometer = 61_200

        // Only past activity: anything that would fall after today is skipped.
        fun add(draft: TransactionDraft) { if (draft.date <= today) books.transactions.create(draft) }
        fun add(draft: TransferDraft) { if (draft.date <= today) books.transactions.transfer(draft) }
        var month = LocalDate(start.year, start.month, 1)
        while (month <= today) {
            val m = month
            fun on(day: Int) = LocalDate(m.year, m.month, minOf(day, 28))
            add(TransactionDraft(chequing.id, on(1), cad("3150.00"), "Employeur inc.", listOf(SplitDraft(cat("income.employment.salary"), cad("3150.00")))))
            add(TransactionDraft(chequing.id, on(15), cad("3150.00"), "Employeur inc.", listOf(SplitDraft(cat("income.employment.salary"), cad("3150.00")))))
            add(TransactionDraft(chequing.id, on(1), cad("-1450.00"), "Propriétaire", listOf(SplitDraft(cat("housing.rent"), cad("-1450.00")))))
            add(TransactionDraft(chequing.id, on(12), cad("-132.48"), "Hydro-Québec", listOf(SplitDraft(cat("utilities.electricity"), cad("-132.48")))))
            add(TransactionDraft(chequing.id, on(18), cad("-95.00"), "Vidéotron", listOf(SplitDraft(cat("utilities.internet"), cad("-95.00")))))
            add(TransactionDraft(visa.id, on(6), cad("-187.32"), "IGA", listOf(SplitDraft(cat("food.groceries"), cad("-187.32")))))
            add(
                TransactionDraft(
                    visa.id, on(13), cad("-243.90"), "Costco",
                    listOf(SplitDraft(cat("food.groceries"), cad("-168.40")), SplitDraft(cat("health.otc"), cad("-42.50")), SplitDraft(cat("children.clothing"), cad("-33.00"))),
                ),
            )
            add(TransactionDraft(visa.id, on(21), cad("-64.15"), "Restaurant Chez Mimi", listOf(SplitDraft(cat("food.restaurants"), cad("-64.15")))))
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
            add(TransactionDraft(visa.id, on(4), cad("-74.99"), "Mondou", listOf(SplitDraft(cat("pets.food"), cad("-74.99"))), memberId = rex.id))
            add(TransactionDraft(chequing.id, on(1), cad("-56.50"), "RTC", listOf(SplitDraft(cat("transport.transit.pass"), cad("-56.50"))), memberId = lea.id, memo = "Laissez-passer étudiant"))
            add(TransferDraft(chequing.id, visa.id, on(10), cad("566.57")))
            add(TransferDraft(chequing.id, savings.id, on(16), cad("500.00"), memo = "Épargne mensuelle"))
            month = month.plus(DatePeriod(months = 1))
        }
        // LN-02: the mortgage payments since the demo starts, each split from the balance owed.
        while (true) {
            val payment = books.loans.nextPayment(mortgage.id, today)?.takeIf { it.date <= today } ?: break
            books.loans.recordPayment(mortgage.id, chequing.id, payment)
        }
        books.transactions.create(
            TransactionDraft(visa.id, today, cad("-137.25"), "Amazon.com", originalAmount = Money.parse("-100.00", Currency.USD)),
        )
        books.transactions.transfer(TransferDraft(chequing.id, usd.id, today, cad("274.50"), Money.parse("200.00", Currency.USD), "Achat de dollars US"))
        importStatement(books, chequing, today)
        addBills(books, chequing, savings, visa, today)
        addCalendarAndHealth(books, group, chequing, alex, sam, lea, today)
        addPetAndCarRecords(books, group, visa, rex, civic, today)
        addInvestments(books, group, alex, sam, desjardins, today)
        addPlans(books, group, chequing, savings, alex, sam, lea, desjardins, today)
        addCrypto(books, group, chequing, alex, today)
        addMetals(books, group, alex, sam, desjardins, today)
        addDocuments(books, group, today)
        // HH-05: Sam signs in too, as a member who can view the shared accounts.
        val samUser = books.users.add("sam", "Sam Demo", ca.schippers.hfm.domain.Role.MEMBER, "sam-demo-password".toCharArray(), sam.id).userId
        books.users.setAccess(group, samUser, ca.schippers.hfm.domain.PermissionLevel.VIEW)
        // GOAL-01 to GOAL-04: three goals sharing the savings account.
        val goals = books.goals
        goals.save(SavingsGoal("", savings.id, "Voyage en Gaspésie", cad("4000"), LocalDate(today.year + 1, 7, 1), cad("250"), Recurrence.MONTHLY, start.plus(DatePeriod(days = 15))))
        val car = goals.save(SavingsGoal("", savings.id, "Remplacement de l'auto", cad("15000"), LocalDate(today.year + 3, 6, 1), cad("200"), Recurrence(Frequency.SEMI_MONTHLY, secondDay = 0), LocalDate(start.year, start.month, 15)))
        goals.setAside(car.id, start, cad("3000"), "Départ")
        val emergency = goals.save(SavingsGoal("", savings.id, "Fonds d'urgence", cad("6000")))
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
        val hydro = books.bills.list().first { it.name == "Hydro-Québec" }
        val due = books.bills.occurrences(today, today.plus(DatePeriod(days = 40)), setOf(hydro.id)).first().dueDate
        val documents = listOf(
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
        val brokerage = account("Courtage Disnat", AccountType.BROKERAGE, setOf(alex.id, sam.id), "DIS-4471230", "22000")
        val tfsa = account("CELI Alex", AccountType.TFSA, setOf(alex.id), "CELI-5512", "14000")
        val rrsp = account("REER Sam", AccountType.RRSP, setOf(sam.id), "REER-8820", "6000")
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
        val safe = books.accounts.create(AccountDraft(group, "Métaux précieux", AccountType.PRECIOUS_METALS, Currency.CAD, cad("0"), LocalDate(2024, 1, 1), institution.id, ownerMemberIds = setOf(alex.id, sam.id)))
        books.metals.save(
            MetalItem("", safe.id, Metal.GOLD, MetalForm.COIN, "Feuille d'érable 1 oz", BigDecimal.ONE, WeightUnit.OZT, BigDecimal("0.9999"), 3, "", "Monnaie royale canadienne",
                LocalDate(2024, 5, 14), cad("9480"), BigDecimal("3"), MetalStorage.BANK_BOX, "Desjardins, coffret 112", true, "Assurance habitation, avenant de 15 000 $"),
        )
        books.metals.save(
            MetalItem("", safe.id, Metal.SILVER, MetalForm.ROUND, "Rondelles d'argent 1 oz", BigDecimal.ONE, WeightUnit.OZT, BigDecimal("0.999"), 25, dealer = "Silver Gold Bull",
                purchaseDate = LocalDate(2025, 2, 3), cost = cad("1060"), storage = MetalStorage.HOME_SAFE, storageDetail = "Coffre du sous-sol"),
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
        val cold = wallet("Ledger (stockage à froid)", Currency.BTC)
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
        val tfsa = accounts.first { it.name == "CELI Alex" }
        val rrsp = accounts.first { it.name == "REER Sam" }
        val year = today.year
        val thisYear = LocalDate(year, 1, 1)
        fun day(monthsAgo: Int) = today.minus(DatePeriod(months = monthsAgo))

        plans.saveRoom(RoomEntry("", alex.id, RoomPlan.TFSA, year, cad("31500")), group)
        plans.saveRoom(RoomEntry("", sam.id, RoomPlan.RRSP, year, cad("18500")), group)
        books.transactions.transfer(TransferDraft(chequing.id, tfsa.id, day(1), cad("1000"), memo = "Cotisation CELI"))
        books.transactions.transfer(TransferDraft(chequing.id, rrsp.id, maxOf(day(2), LocalDate(year, 3, 2)), cad("1500"), memo = "Cotisation REER"))
        plans.saveBeneficiary(Beneficiary("", tfsa.id, BeneficiaryKind.SUCCESSOR_HOLDER, sam.displayName, sam.id, "Conjoint"))
        plans.saveBeneficiary(Beneficiary("", rrsp.id, BeneficiaryKind.BENEFICIARY, alex.displayName, alex.id, "Conjoint", BigDecimal(100)))

        val resp = books.accounts.create(AccountDraft(group, "REEE Léa", AccountType.RESP, Currency.CAD, cad("6000"), thisYear.minus(DatePeriod(years = 3)), institution.id, "REEE-2231", setOf(alex.id, sam.id)))
        plans.saveBeneficiary(Beneficiary("", resp.id, BeneficiaryKind.RESP_BENEFICIARY, lea.displayName, lea.id))
        books.transactions.transfer(TransferDraft(savings.id, resp.id, maxOf(day(2), thisYear), cad("2500"), memo = "Cotisation REEE", memberId = lea.id))
        plans.recordGrant(resp.id, lea.id, maxOf(day(1), thisYear), GrantKind.CESG, cad("500"))

        // A retired relative living with the household: a RRIF paying monthly, and the QPP.
        val gilles = books.members.create("Gilles", MemberKind.ADULT, LocalDate(1952, 8, 20))
        val rrif = books.accounts.create(AccountDraft(group, "FERR Gilles", AccountType.RRIF, Currency.CAD, cad("85000"), LocalDate(2018, 1, 1), institution.id, "FERR-1180", setOf(gilles.id)))
        val qppCategory = books.categories.list().first { it.systemKey == "income.pension.qpp_cpp" }.id
        for (m in 1..today.month.ordinal + 1) {
            val date = LocalDate(year, m, 15)
            if (date > today) continue
            books.transactions.transfer(TransferDraft(rrif.id, chequing.id, date, cad("500"), memo = "Retrait FERR"))
            books.transactions.create(TransactionDraft(chequing.id, LocalDate(year, m, 25).let { if (it > today) date else it }, cad("812.40"), "Retraite Québec", listOf(SplitDraft(qppCategory, cad("812.40"))), memberId = gilles.id))
        }
        plans.saveBeneficiary(Beneficiary("", rrif.id, BeneficiaryKind.BENEFICIARY, "Succession de Gilles", relationship = "Succession", sharePercent = BigDecimal(100)))
        val qpp = plans.savePension(Pension("", gilles.id, PensionKind.QPP, "Rente de retraite du RRQ", "Retraite Québec", indexed = true, payer = "Retraite Québec"), group)
        plans.saveStatement(qpp, PensionStatement("", qpp.id, year - 1, projectedAnnual = cad("9748.80")))
        val municipal = plans.savePension(
            Pension("", sam.id, PensionKind.DEFINED_BENEFIT, "Régime de retraite des employés municipaux", "Ville de Québec", "RREM-44817", normalRetirementAge = 65, indexed = true, survivorPercent = BigDecimal(60)),
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
        val names = mapOf("oil" to "Vidange d'huile et filtre", "tire_rotation" to "Permutation des pneus", "winter_tires_on" to "Pose des pneus d'hiver",
            "winter_tires_off" to "Retrait des pneus d'hiver", "brakes" to "Inspection des freins", "cabin_filter" to "Filtre à air de l'habitacle",
            "engine_filter" to "Filtre à air du moteur", "inspection" to "Inspection annuelle")
        val start = today.minus(DatePeriod(months = 3))
        val tasks = vehicles.addStarterTasks(civic.id, today) { names.getValue(it) }.associateBy { it.templateKey }
        // An oil change three months ago, with its payment; and a do-it-yourself filter change without one.
        vehicles.saveService(
            ServiceRecord("", civic.id, start.plus(DatePeriod(days = 5)), 61_500, "Garage Tremblay", cost = cad("94.85"), taskIds = setOfNotNull(tasks["oil"]?.id, tasks["tire_rotation"]?.id)),
            PaymentDraft(visa.id, cat("transport.maintenance"), "Garage Tremblay"),
        )
        vehicles.saveService(ServiceRecord("", civic.id, start.plus(DatePeriod(days = 40)), 63_300, diy = true, cost = cad("24.99"), notes = "Filtre Canadian Tire", taskIds = setOfNotNull(tasks["cabin_filter"]?.id)))
        books.transactions.create(
            TransactionDraft(visa.id, start.plus(DatePeriod(days = 2)), cad("-1184.00"), "Desjardins Assurances", listOf(SplitDraft(cat("transport.insurance"), cad("-1184.00"))), assetId = civic.id),
        )

        val vet = books.health.saveProvider(HealthProvider("", group, "Hôpital vétérinaire Charlesbourg", ProviderKind.VET, "418-555-0190", null, null, false))
        books.health.saveProvider(HealthProvider("", group, "Toilettage Patte de velours", ProviderKind.GROOMER, "418-555-0133", null, null, false))
        books.transactions.create(
            TransactionDraft(visa.id, start.plus(DatePeriod(days = 20)), cad("-287.40"), "Hôpital vétérinaire Charlesbourg", listOf(SplitDraft(cat("pets.vet"), cad("-287.40"))), memberId = rex.id),
        )
        books.transactions.create(
            TransactionDraft(visa.id, LocalDate(today.year, 1, 15).let { if (it > today) start else it }, cad("-35.00"), "Ville de Québec", listOf(SplitDraft(cat("pets.licence"), cad("-35.00"))), memberId = rex.id),
        )
        books.health.saveImmunization(Immunization("", group, rex.id, "Rage", start.plus(DatePeriod(days = 20)), vet.id, start.plus(DatePeriod(days = 20, years = 3)), null))
        books.health.saveImmunization(Immunization("", group, rex.id, "DHPP", start.plus(DatePeriod(days = 20)).minus(DatePeriod(years = 1)), vet.id, today.plus(DatePeriod(days = 18)), null))
        books.calendar.create(EventDraft(group, "Toilettage de Rex", EventCategory.PET, today.plus(DatePeriod(days = 4)), LocalTime(13, 30), 90, "Patte de velours", memberId = rex.id))
    }

    /** Appointments of several kinds, and health records kept in Alex's private group. */
    private fun addCalendarAndHealth(books: Books, shared: String, chequing: Account, alex: Member, sam: Member, lea: Member, today: LocalDate) {
        fun day(n: Int) = today.plus(DatePeriod(days = n))
        val private = books.session.createGroup("Alex Demo - privé", private = true)
        val health = books.health
        val pharmacy = health.saveProvider(HealthProvider("", shared, "Pharmacie Jean Coutu", ProviderKind.PHARMACY, "418-555-0100", "1200, boul. Charest", null, false))
        val doctor = health.saveProvider(HealthProvider("", shared, "Dre Gagnon (GMF Limoilou)", ProviderKind.DOCTOR, "418-555-0142", null, null, false))
        val dentist = health.saveProvider(HealthProvider("", shared, "Clinique dentaire Saint-Roch", ProviderKind.DENTIST, "418-555-0177", null, null, false))

        val calendar = books.calendar
        calendar.create(EventDraft(shared, "Pose des pneus d'hiver", EventCategory.VEHICLE, day(1), LocalTime(9, 30), 60, "Garage Tremblay", reminderMinutes = listOf(1440, 60)))
        calendar.create(EventDraft(shared, "Rencontre conseillère Desjardins", EventCategory.FINANCIAL, day(6), LocalTime(14, 0), 45, "Caisse Desjardins", accountId = chequing.id))
        calendar.create(EventDraft(shared, "Nettoyage dentaire", EventCategory.MEDICAL, day(9), LocalTime(10, 15), 60, memberId = lea.id, providerId = dentist.id))
        calendar.create(EventDraft(private, "Bilan annuel", EventCategory.MEDICAL, day(14), LocalTime(8, 40), 30, memberId = alex.id, providerId = doctor.id))
        calendar.create(EventDraft(shared, "Ramonage de la cheminée", EventCategory.HOME, day(20), reminderMinutes = listOf(2 * 1440)))
        calendar.create(EventDraft(shared, "Cours de natation", EventCategory.PERSONAL, day(-3), LocalTime(18, 0), 60, memberId = lea.id, recurrence = Recurrence.WEEKLY, endDate = day(60), reminderMinutes = listOf(120)))

        health.saveMedication(Medication("", private, alex.id, "Atorvastatine", "20 mg", "1 comprimé au coucher", doctor.id, pharmacy.id, "RX-448120", day(-400), null, 30, 2, day(-27), 5, true, null))
        health.saveMedication(Medication("", private, alex.id, "Vitamine D", "1000 UI", "1 par jour", null, null, null, null, null, null, null, null, 5, true, null))
        health.saveMedication(Medication("", shared, sam.id, "Salbutamol (inhalateur)", "100 mcg", "Au besoin", doctor.id, pharmacy.id, "RX-310077", day(-700), null, 90, 0, day(-60), 7, true, null))
        health.saveCondition(HealthCondition("", private, alex.id, "Hypercholestérolémie", day(-420), ConditionStatus.MANAGED, doctor.id, null))
        health.saveCondition(HealthCondition("", shared, sam.id, "Asthme", LocalDate(2009, 4, 1), ConditionStatus.MANAGED, doctor.id, null))
        health.saveAllergy(Allergy("", shared, lea.id, "Arachides", "Urticaire", Severity.SEVERE, "Épipen dans le sac d'école"))
        health.saveTest(HealthTest("", private, alex.id, "Bilan lipidique (LDL)", day(-35), "2,4", "mmol/L", "< 3,5", doctor.id, day(150), null))
        health.saveImmunization(Immunization("", shared, lea.id, "Influenza", day(-340), pharmacy.id, day(25), null))
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
        bills.create(BillDraft(BillKind.BILL, "Loyer", cad("1450.00"), chequing.id, Recurrence.MONTHLY, next(1), "Propriétaire", categoryId = cat("housing.rent"), paymentMethod = PaymentMethod.CHEQUE))
        bills.create(
            BillDraft(
                BillKind.BILL, "Hydro-Québec", cad("132.48"), chequing.id, Recurrence(Frequency.MONTHLY, adjust = BusinessDayAdjust.NEXT), next(12),
                "Hydro-Québec", "6 1234 5678 9", AmountKind.VARIABLE, paymentMethod = PaymentMethod.PAD, categoryId = cat("utilities.electricity"),
            ),
        )
        bills.create(BillDraft(BillKind.BILL, "Vidéotron", cad("95.00"), chequing.id, Recurrence.MONTHLY, next(18), "Vidéotron", paymentMethod = PaymentMethod.PAD, categoryId = cat("utilities.internet")))
        bills.create(
            BillDraft(BillKind.BILL, "Assurance habitation", cad("1184.00"), chequing.id, Recurrence.ANNUAL, today.plus(DatePeriod(days = 5)), "Desjardins Assurances", categoryId = cat("housing.insurance")),
        )
        bills.create(
            BillDraft(
                BillKind.BILL, "Diffusion en continu", cad("18.99"), visa.id, Recurrence.MONTHLY, today.plus(DatePeriod(days = 3)), "StreamCo",
                paymentMethod = PaymentMethod.CARD, categoryId = cat("utilities.tv_streaming"), isSubscription = true, cancelBy = today.plus(DatePeriod(days = 3)),
            ),
        )
        bills.create(BillDraft(BillKind.INCOME, "Paie", cad("3150.00"), chequing.id, Recurrence(Frequency.SEMI_MONTHLY, secondDay = 1), next(15), "Employeur inc.", categoryId = cat("income.employment.salary")))
        bills.create(BillDraft(BillKind.TRANSFER, "Épargne mensuelle", cad("500.00"), chequing.id, Recurrence.MONTHLY, next(16), transferAccountId = savings.id))
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
            val name = t.payeeId?.let(payees::get) ?: if (t.transfer != null) "VIREMENT" else "?"
            val shift = if (name.startsWith("Vidéotron")) 4 else 0
            ImportedLine("D$i", t.date.plus(DatePeriod(days = shift)).let { if (it > end) end else it }, posted(t), name.uppercase(), null, null)
        } + ImportedLine("FEE", end, fee, "FRAIS MENSUELS DU FORFAIT", null, null)
        val closing = account.openingBalance + recorded.map(::posted).fold(Money.zero(Currency.CAD), Money::plus) + fee
        books.statements.import(
            account.id,
            ImportedStatement("OFX", "0045678", Currency.CAD, account.openingDate, end, account.openingBalance, closing, lines),
            "releve-desjardins.ofx",
        )
    }
}
