package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AccountDraft
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
import java.nio.file.Files

/**
 * A sample household with a few months of made-up activity, created in a temporary folder.
 * Started with `./gradlew :app:desktop:runDemo`; never touches real households.
 */
object DemoHousehold {
    const val LOGIN = "demo"
    const val PASSWORD = "demo-password"

    fun create(store: HouseholdStore): HouseholdSession {
        val dir = Files.createTempDirectory("hfm-demo").resolve("Demo.hfm")
        val created = store.create(dir, "Famille Démo", LOGIN, "Alex Demo", PASSWORD.toCharArray())
        val books = Books(created.session)
        fill(books)
        return created.session
    }

    private fun fill(books: Books) {
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
        val group = books.groups().first().id
        val today = today()
        val start = today.minus(DatePeriod(months = 3))

        val alex = books.members.create("Alex", MemberKind.ADULT)
        val sam = books.members.create("Sam", MemberKind.ADULT)
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
        books.transactions.create(
            TransactionDraft(visa.id, today, cad("-137.25"), "Amazon.com", originalAmount = Money.parse("-100.00", Currency.USD)),
        )
        books.transactions.transfer(TransferDraft(chequing.id, usd.id, today, cad("274.50"), Money.parse("200.00", Currency.USD), "Achat de dollars US"))
        importStatement(books, chequing, today)
        addBills(books, chequing, savings, visa, today)
        addCalendarAndHealth(books, group, chequing, alex, sam, lea, today)
        addPetAndCarRecords(books, group, visa, rex, civic, today)
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
     * and a monthly bank fee was never entered.
     */
    private fun importStatement(books: Books, account: Account, today: LocalDate) {
        val end = LocalDate(today.year, today.month, 1).minus(DatePeriod(days = 1))
        val recorded = books.transactions.register(account.id).map { it.transaction }.filter { it.date <= end }
        val payees = books.payees.list().associate { it.id to it.name }
        val fee = Money.parse("-4.95", Currency.CAD)
        val lines = recorded.mapIndexed { i, t ->
            val name = t.payeeId?.let(payees::get) ?: if (t.transfer != null) "VIREMENT" else "?"
            val shift = if (name.startsWith("Vidéotron")) 4 else 0
            ImportedLine("D$i", t.date.plus(DatePeriod(days = shift)).let { if (it > end) end else it }, t.amount, name.uppercase(), null, null)
        } + ImportedLine("FEE", end, fee, "FRAIS MENSUELS DU FORFAIT", null, null)
        val closing = account.openingBalance + recorded.map { it.amount }.fold(Money.zero(Currency.CAD), Money::plus) + fee
        books.statements.import(
            account.id,
            ImportedStatement("OFX", "0045678", Currency.CAD, account.openingDate, end, account.openingBalance, closing, lines),
            "releve-desjardins.ofx",
        )
    }
}
