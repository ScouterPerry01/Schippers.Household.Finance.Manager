package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// --- Shared expenses (HH-04) -------------------------------------------------------------------------

data class SharePerson(val id: String, val name: String, val memberId: String? = null)

/**
 * An expense paid by [paidBy] and shared by [weights] (person id to share; equal shares when every
 * weight is 1), or, with [settlesTo], money [paidBy] gave [settlesTo] to settle up.
 */
data class ShareEntry(
    val id: String,
    val date: LocalDate,
    val description: String,
    val amount: Money,
    val paidBy: String,
    val weights: Map<String, Int> = emptyMap(),
    val settlesTo: String? = null,
)

data class ShareGroup(
    val id: String,
    val groupId: String,
    val name: String,
    val currency: Currency,
    val archived: Boolean,
    val people: List<SharePerson>,
    val entries: List<ShareEntry>,
)

/** One payment that settles balances: [from] gives [to] [amount]. */
data class Settlement(val from: String, val to: String, val amount: Money)

/** HH-04: expenses shared within the household or with others, who owes whom, and settling up. */
class SharedExpenseService internal constructor(private val books: Books) {

    private val weightsJson = MapSerializer(String.serializer(), Int.serializer())

    fun list(): List<ShareGroup> = books.groups().flatMap { g ->
        val q = books.ledger(g).familyMoneyQueries
        q.shareGroups().executeAsList().map { r ->
            val cur = Currency.of(r.currency)
            ShareGroup(
                r.id, g.id, r.name, cur, r.archived == 1L,
                q.sharePeople(r.id).executeAsList().map { SharePerson(it.id, it.name, it.member_id) },
                q.shareEntries(r.id).executeAsList().map { e ->
                    ShareEntry(
                        e.id, LocalDate.parse(e.date), e.description, Money.ofMinor(e.amount_minor, cur), e.paid_by,
                        e.weights?.let { runCatching { Json.decodeFromString(weightsJson, it) }.getOrNull() }.orEmpty(), e.settles_to,
                    )
                },
            )
        }
    }

    fun get(id: String): ShareGroup = list().firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    /** Creates or renames a group with its people; people already used in an entry cannot be removed. */
    fun save(id: String?, groupId: String, name: String, currency: Currency, people: List<SharePerson>, archived: Boolean = false): ShareGroup {
        validate(name.isNotBlank(), "error.nameRequired")
        validate(people.count { it.name.isNotBlank() } >= 2, "error.sharePeople")
        val existing = id?.let(::get)
        val group = books.group(existing?.groupId ?: groupId).also { books.require(it, PermissionLevel.EDIT) }
        val q = books.ledger(group).familyMoneyQueries
        val gid = existing?.id ?: Ids.newId()
        val used = existing?.entries.orEmpty().flatMap { e -> listOfNotNull(e.paidBy, e.settlesTo) + e.weights.keys }.toSet()
        val kept = people.filter { it.name.isNotBlank() }.map { it.copy(id = it.id.ifBlank { Ids.newId() }) }
        validate(existing == null || existing.people.filter { it.id in used }.all { u -> kept.any { it.id == u.id } }, "error.sharePersonUsed")
        books.ledger(group).transaction {
            q.upsertShareGroup(gid, name.trim(), currency.code, if (archived) 1 else 0, books.now())
            existing?.people?.filter { p -> kept.none { it.id == p.id } }?.forEach { q.deleteSharePerson(it.id) }
            kept.forEach { q.upsertSharePerson(it.id, gid, it.name.trim(), it.memberId) }
        }
        return get(gid)
    }

    fun delete(id: String) {
        val g = get(id)
        books.ledger(books.group(g.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.deleteShareGroup(id)
    }

    fun saveEntry(shareGroupId: String, entry: ShareEntry): ShareEntry {
        val g = get(shareGroupId)
        val ids = g.people.map { it.id }.toSet()
        validate(entry.amount.currency == g.currency && entry.amount.isPositive, "error.shareAmount")
        validate(entry.paidBy in ids && (entry.settlesTo == null || (entry.settlesTo in ids && entry.settlesTo != entry.paidBy)), "error.sharePerson")
        validate(entry.settlesTo != null || (entry.weights.isNotEmpty() && entry.weights.keys.all { it in ids } && entry.weights.values.all { it >= 0 } && entry.weights.values.sum() > 0), "error.shareSplit")
        val id = entry.id.ifBlank { Ids.newId() }
        books.ledger(books.group(g.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.upsertShareEntry(
            id, shareGroupId, entry.date.toString(), entry.description.trim().ifEmpty { "—" }, entry.amount.minorUnits, entry.paidBy,
            entry.weights.takeIf { entry.settlesTo == null }?.let { Json.encodeToString(weightsJson, it) }, entry.settlesTo,
        )
        return entry.copy(id = id)
    }

    fun deleteEntry(shareGroupId: String, entryId: String) {
        val g = get(shareGroupId)
        books.ledger(books.group(g.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.deleteShareEntry(entryId)
    }

    /** What each person is owed (positive) or owes (negative): what they paid less their shares. */
    fun balances(g: ShareGroup): Map<String, Money> {
        val minor = g.people.associate { it.id to 0L }.toMutableMap()
        for (e in g.entries) {
            minor.merge(e.paidBy, e.amount.minorUnits, Long::plus)
            if (e.settlesTo != null) {
                minor.merge(e.settlesTo, -e.amount.minorUnits, Long::plus)
            } else {
                for ((person, share) in shares(e.amount.minorUnits, e.weights)) minor.merge(person, -share, Long::plus)
            }
        }
        return minor.mapValues { Money.ofMinor(it.value, g.currency) }
    }

    /** The fewest payments that settle everyone: the largest debtor pays the largest creditor, and so on. */
    fun settleUp(g: ShareGroup): List<Settlement> {
        val owed = balances(g).mapValues { it.value.minorUnits }.filterValues { it != 0L }.toMutableMap()
        val out = ArrayList<Settlement>()
        while (true) {
            val debtor = owed.filterValues { it < 0 }.minByOrNull { it.value } ?: break
            val creditor = owed.filterValues { it > 0 }.maxByOrNull { it.value } ?: break
            val amount = minOf(-debtor.value, creditor.value)
            out += Settlement(debtor.key, creditor.key, Money.ofMinor(amount, g.currency))
            owed[debtor.key] = debtor.value + amount
            owed[creditor.key] = creditor.value - amount
            owed.entries.removeIf { it.value == 0L }
        }
        return out
    }

    /** [total] cents shared by weight, the leftover cents going to the largest remainders, so the shares add up exactly. */
    internal fun shares(total: Long, weights: Map<String, Int>): Map<String, Long> {
        val sum = weights.values.sum().toLong()
        if (sum == 0L) return emptyMap()
        val base = weights.mapValues { (_, w) -> total * w / sum }
        var left = total - base.values.sum()
        val order = weights.entries.sortedByDescending { (total * it.value) % sum }.map { it.key }
        val result = base.toMutableMap()
        for (k in order) {
            if (left == 0L) break
            if (weights.getValue(k) == 0) continue
            result[k] = result.getValue(k) + 1
            left--
        }
        return result
    }
}

// --- Family loans (LN-07) ----------------------------------------------------------------------------

data class LoanPayment(val id: String, val date: LocalDate, val amount: Money, val notes: String? = null)

data class FamilyLoan(
    val id: String,
    val groupId: String,
    val lender: String,
    val borrower: String,
    val principal: Money,
    val start: LocalDate,
    /** Yearly simple interest in basis points (500 = 5 %). */
    val rateBp: Int,
    val notes: String?,
    val closed: Boolean,
    val payments: List<LoanPayment>,
)

/** Where a family loan stands: interest owed so far, what was repaid, and what is left. */
data class FamilyLoanStatus(val interest: Money, val repaid: Money, val principalLeft: Money, val interestLeft: Money) {
    val balance: Money get() = principalLeft + interestLeft
}

/** LN-07: personal loans between family members, with optional interest and a repayment log. */
class FamilyLoanService internal constructor(private val books: Books) {

    fun list(): List<FamilyLoan> = books.groups().flatMap { g ->
        val q = books.ledger(g).familyMoneyQueries
        q.familyLoans().executeAsList().map { r ->
            val cur = Currency.of(r.currency)
            FamilyLoan(
                r.id, g.id, r.lender, r.borrower, Money.ofMinor(r.principal_minor, cur), LocalDate.parse(r.start_date), r.rate_bp.toInt(), r.notes, r.closed == 1L,
                q.loanPayments(r.id).executeAsList().map { LoanPayment(it.id, LocalDate.parse(it.date), Money.ofMinor(it.amount_minor, cur), it.notes) },
            )
        }
    }

    fun save(loan: FamilyLoan): FamilyLoan {
        validate(loan.lender.isNotBlank() && loan.borrower.isNotBlank(), "error.nameRequired")
        validate(loan.principal.isPositive, "error.loanPrincipal")
        validate(loan.rateBp in 0..5000, "error.loanRate")
        val group = books.group(list().firstOrNull { it.id == loan.id }?.groupId ?: loan.groupId).also { books.require(it, PermissionLevel.EDIT) }
        val id = loan.id.ifBlank { Ids.newId() }
        books.ledger(group).familyMoneyQueries.upsertFamilyLoan(
            id, loan.lender.trim(), loan.borrower.trim(), loan.principal.minorUnits, loan.principal.currency.code, loan.start.toString(), loan.rateBp.toLong(),
            loan.notes?.trim()?.ifEmpty { null }, if (loan.closed) 1 else 0,
        )
        return list().first { it.id == id }
    }

    fun delete(loan: FamilyLoan) = books.ledger(books.group(loan.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.deleteFamilyLoan(loan.id)

    fun addPayment(loan: FamilyLoan, date: LocalDate, amount: Money, notes: String? = null) {
        validate(amount.currency == loan.principal.currency && amount.isPositive, "error.loanPayment")
        validate(date >= loan.start, "error.loanPaymentDate")
        books.ledger(books.group(loan.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries
            .insertLoanPayment(Ids.newId(), loan.id, date.toString(), amount.minorUnits, notes?.trim()?.ifEmpty { null })
    }

    fun deletePayment(loan: FamilyLoan, paymentId: String) =
        books.ledger(books.group(loan.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.deleteLoanPayment(paymentId)

    /**
     * As of [today]: simple interest runs on the principal still owed, day by day; each payment goes
     * to the interest owed first, then to the principal. A loan marked repaid in full ([FamilyLoan.closed])
     * gains no interest after its last payment (or after its start, with no payment).
     */
    fun status(loan: FamilyLoan, today: LocalDate): FamilyLoanStatus {
        var principal = loan.principal.minorUnits.toBigDecimal()
        var interestOwed = java.math.BigDecimal.ZERO
        var interestTotal = java.math.BigDecimal.ZERO
        var paid = 0L
        var from = loan.start
        val rate = loan.rateBp.toBigDecimal().divide(java.math.BigDecimal(10_000))
        fun accrue(to: LocalDate) {
            val days = from.daysUntil(to).coerceAtLeast(0)
            val i = principal * rate * days.toBigDecimal() / java.math.BigDecimal(365)
            interestOwed += i
            interestTotal += i
            from = to
        }
        for (p in loan.payments.filter { it.date <= today }.sortedBy { it.date }) {
            accrue(p.date)
            var amount = p.amount.minorUnits.toBigDecimal()
            paid += p.amount.minorUnits
            val toInterest = amount.min(interestOwed)
            interestOwed -= toInterest
            amount -= toInterest
            principal = (principal - amount).max(java.math.BigDecimal.ZERO)
        }
        // Interest stops once the loan is marked repaid in full.
        if (!loan.closed) accrue(today)
        fun m(v: java.math.BigDecimal) = Money.ofMinor(v.setScale(0, java.math.RoundingMode.HALF_UP).toLong(), loan.principal.currency)
        return FamilyLoanStatus(m(interestTotal), Money.ofMinor(paid, loan.principal.currency), m(principal), m(interestOwed))
    }

    companion object {
        /** LN-07: a yearly rate typed in percent as basis points, rounded to the nearest (3.125 % is 313, 3.13 %). */
        fun rateBp(percent: java.math.BigDecimal): Int = percent.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
    }
}

// --- Allowances (HH-03) ------------------------------------------------------------------------------

enum class AllowanceFrequency { WEEKLY, BIWEEKLY, MONTHLY }

enum class AllowanceKind { PAID, EARNED, SPENT }

data class AllowanceEntry(val id: String, val date: LocalDate, val amount: Money, val kind: AllowanceKind, val notes: String? = null)

data class Allowance(
    val id: String,
    val groupId: String,
    val memberId: String,
    val amount: Money,
    val frequency: AllowanceFrequency,
    val start: LocalDate,
    val end: LocalDate?,
    val notes: String?,
    val entries: List<AllowanceEntry>,
)

/** Where a child's allowance stands: what was due so far, what is still owed, and the child's own money. */
data class AllowanceStatus(val due: Money, val owed: Money, val balance: Money, val nextDate: LocalDate?)

/** HH-03: children's allowances and the money they keep. */
class AllowanceService internal constructor(private val books: Books) {

    fun list(): List<Allowance> = books.groups().flatMap { g ->
        val q = books.ledger(g).familyMoneyQueries
        q.allowances().executeAsList().map { r ->
            val cur = Currency.of(r.currency)
            Allowance(
                r.id, g.id, r.member_id, Money.ofMinor(r.amount_minor, cur), AllowanceFrequency.valueOf(r.frequency), LocalDate.parse(r.start_date),
                r.end_date?.let(LocalDate::parse), r.notes,
                q.allowanceEntries(r.id).executeAsList().map { AllowanceEntry(it.id, LocalDate.parse(it.date), Money.ofMinor(it.amount_minor, cur), AllowanceKind.valueOf(it.kind), it.notes) },
            )
        }
    }

    fun save(a: Allowance): Allowance {
        validate(a.amount.isPositive, "error.allowanceAmount")
        validate(a.end == null || a.end >= a.start, "error.endBeforeStart")
        val group = books.group(list().firstOrNull { it.id == a.id }?.groupId ?: a.groupId).also { books.require(it, PermissionLevel.EDIT) }
        val id = a.id.ifBlank { Ids.newId() }
        books.ledger(group).familyMoneyQueries.upsertAllowance(
            id, a.memberId, a.amount.minorUnits, a.amount.currency.code, a.frequency.name, a.start.toString(), a.end?.toString(), a.notes?.trim()?.ifEmpty { null },
        )
        return list().first { it.id == id }
    }

    fun delete(a: Allowance) = books.ledger(books.group(a.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.deleteAllowance(a.id)

    /** Adds an entry to the child's money; returns its id. */
    fun addEntry(a: Allowance, date: LocalDate, amount: Money, kind: AllowanceKind, notes: String? = null): String {
        validate(amount.currency == a.amount.currency && amount.isPositive, "error.allowanceAmount")
        val id = Ids.newId()
        books.ledger(books.group(a.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries
            .insertAllowanceEntry(id, a.id, date.toString(), amount.minorUnits, kind.name, notes?.trim()?.ifEmpty { null })
        return id
    }

    fun deleteEntry(a: Allowance, entryId: String) =
        books.ledger(books.group(a.groupId).also { books.require(it, PermissionLevel.EDIT) }).familyMoneyQueries.deleteAllowanceEntry(entryId)

    /** The allowance days from the start up to [today] (or the end), each one owed until paid. */
    fun dates(a: Allowance, today: LocalDate): List<LocalDate> {
        val last = listOfNotNull(a.end, today).min()
        return occurrences(a).takeWhile { it <= last }.toList()
    }

    fun status(a: Allowance, today: LocalDate): AllowanceStatus {
        val cur = a.amount.currency
        val due = Money.ofMinor(a.amount.minorUnits * dates(a, today).size, cur)
        fun sum(kind: AllowanceKind) = a.entries.filter { it.kind == kind && it.date <= today }.fold(Money.zero(cur)) { s, e -> s + e.amount }
        val paid = sum(AllowanceKind.PAID)
        val next = occurrences(a).firstOrNull { it > today }?.takeIf { a.end == null || it <= a.end }
        return AllowanceStatus(due, (due - paid).let { if (it.isNegative) Money.zero(cur) else it }, paid + sum(AllowanceKind.EARNED) - sum(AllowanceKind.SPENT), next)
    }

    /** Each allowance day, counted from the start so a monthly one on the 31st stays at the month's end. */
    private fun occurrences(a: Allowance): Sequence<LocalDate> = generateSequence(0) { it + 1 }.map { n ->
        when (a.frequency) {
            AllowanceFrequency.WEEKLY -> a.start.plus(DatePeriod(days = 7 * n))
            AllowanceFrequency.BIWEEKLY -> a.start.plus(DatePeriod(days = 14 * n))
            AllowanceFrequency.MONTHLY -> a.start.plus(DatePeriod(months = n))
        }
    }
}
