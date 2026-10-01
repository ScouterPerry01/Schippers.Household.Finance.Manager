package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.AccountDraft
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
        books.members.create("Léa", MemberKind.CHILD, LocalDate(2015, 6, 12))
        val desjardins = books.institutions.create(Institution("", "Desjardins", institutionNumber = "815", transitNumber = "30123"))
        val bank = books.institutions.create(Institution("", "Banque Nationale", institutionNumber = "006"))

        val chequing = books.accounts.create(
            AccountDraft(group, "Compte conjoint", AccountType.CHEQUING, Currency.CAD, cad("2450.00"), start, desjardins.id, "815-30123-0045678", setOf(alex.id, sam.id)),
        )
        val savings = books.accounts.create(AccountDraft(group, "Épargne", AccountType.HIGH_INTEREST_SAVINGS, Currency.CAD, cad("8000.00"), start, desjardins.id))
        val visa = books.accounts.create(AccountDraft(group, "Visa Desjardins", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), start, desjardins.id, "4540123412341234"))
        books.creditCards.saveTerms(visa.id, CreditCardTerms(cad("8000"), BigDecimal("0.1995"), statementDay = 20, dueDay = 10, minPaymentPercent = BigDecimal("0.05"), minPaymentFloor = cad("10")))
        val usd = books.accounts.create(AccountDraft(group, "Compte US", AccountType.CHEQUING, Currency.USD, Money.parse("500.00", Currency.USD), start, bank.id))

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
            add(TransactionDraft(visa.id, on(24), cad("-71.20"), "Petro-Canada", listOf(SplitDraft(cat("transport.fuel"), cad("-71.20")))))
            add(TransferDraft(chequing.id, visa.id, on(10), cad("566.57")))
            add(TransferDraft(chequing.id, savings.id, on(16), cad("500.00"), memo = "Épargne mensuelle"))
            month = month.plus(DatePeriod(months = 1))
        }
        books.transactions.create(
            TransactionDraft(visa.id, today, cad("-137.25"), "Amazon.com", originalAmount = Money.parse("-100.00", Currency.USD)),
        )
        books.transactions.transfer(TransferDraft(chequing.id, usd.id, today, cad("274.50"), Money.parse("200.00", Currency.USD), "Achat de dollars US"))
    }
}
