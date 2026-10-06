package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.medical.CoverageRule
import ca.schippers.hfm.calc.medical.CoverageUse
import ca.schippers.hfm.calc.medical.Medical
import ca.schippers.hfm.calc.medical.Window
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.math.BigDecimal
import ca.schippers.hfm.data.ledger.Med_claim as ClaimRow
import ca.schippers.hfm.data.ledger.Med_coverage as CoverageRow
import ca.schippers.hfm.data.ledger.Med_expense as ExpenseRow
import ca.schippers.hfm.data.ledger.Med_plan as PlanRow

/** MED-03: the kinds of plan. */
enum class MedPlanKind { GROUP_HEALTH, DENTAL, VISION, PRIVATE_HEALTH, TRAVEL, HSA, RAMQ, CDCP, OTHER }

/** MED-02, MED-06: kinds of service, for coverage rules and expenses. */
enum class MedService {
    PRESCRIPTION, DENTAL_PREVENTIVE, DENTAL_BASIC, DENTAL_MAJOR, ORTHODONTICS, EYE_EXAM, EYEWEAR, PHYSIOTHERAPY, MASSAGE, CHIROPRACTIC,
    PSYCHOLOGY, OSTEOPATHY, NATUROPATHY, PODIATRY, ACUPUNCTURE, SPEECH_THERAPY, HEARING, MEDICAL_EQUIPMENT, HOSPITAL, AMBULANCE, LAB,
    TRAVEL_MEDICAL, PREMIUM, MEDICAL_TRAVEL, OTHER,
}

enum class ClaimStatus { SUBMITTED, PAID, DENIED }

/** MED-07: where an expense stands: a plan still to claim, a claim waiting for payment, or done. */
enum class ExpenseStage { TO_SUBMIT, WAITING, CLOSED }

/** MED-09: how a plan counts its claim deadline: days after the service, or after the plan year ends. */
enum class ClaimDeadlineRule { AFTER_SERVICE, AFTER_PLAN_YEAR }

/** A person covered by a plan, and the order in which the plan pays for them (1 = first). */
data class PlanPerson(val memberId: String, val priority: Int = 1)

/** MED-01, MED-03: a plan, the people it covers, its plan year and how long claims may wait. */
data class MedPlan(
    val id: String,
    val groupId: String,
    val kind: MedPlanKind,
    val name: String,
    val insurer: String? = null,
    val policyNumber: String? = null,
    val certificateNumber: String? = null,
    val memberId: String? = null,
    val yearStartMonth: Int = 1,
    val yearStartDay: Int = 1,
    val claimDays: Int = LeadTimes.medicalPlanDeadline(),
    /** A Health Spending Account's yearly credit. */
    val hsaAmount: Money? = null,
    val active: Boolean = true,
    val notes: String? = null,
    val people: List<PlanPerson> = emptyList(),
    /** MED-09: [claimDays] counts from the date of service, or from the end of the plan year (0 = by its last day). */
    val claimRule: ClaimDeadlineRule = ClaimDeadlineRule.AFTER_SERVICE,
) {
    /** MED-09: the last day to claim an expense from [serviceDate] with this plan. */
    fun claimDeadline(serviceDate: LocalDate): LocalDate = when (claimRule) {
        ClaimDeadlineRule.AFTER_SERVICE -> Medical.claimDeadline(serviceDate, claimDays)
        ClaimDeadlineRule.AFTER_PLAN_YEAR -> Medical.planYearClaimDeadline(serviceDate, yearStartMonth, yearStartDay, claimDays)
    }

    /** The longest a deadline can be after the service, to know which expenses may still have one. */
    val longestClaimDays: Int get() = if (claimRule == ClaimDeadlineRule.AFTER_PLAN_YEAR) 366 + claimDays else claimDays
}

/** MED-02: what a plan pays for one kind of service. [percent] is out of 100. */
data class MedCoverage(
    val id: String,
    val planId: String,
    val service: MedService,
    val percent: BigDecimal,
    val deductible: Money? = null,
    val annualMax: Money? = null,
    val perVisitMax: Money? = null,
    val frequencyMonths: Int? = null,
    val notes: String? = null,
) {
    val rule: CoverageRule
        get() = CoverageRule(percent, deductible?.toBigDecimal(), annualMax?.toBigDecimal(), perVisitMax?.toBigDecimal(), frequencyMonths)
}

/** MED-07: one claim to one plan, with what was asked and what was paid. */
data class MedClaim(
    val id: String,
    val expenseId: String,
    val planId: String,
    val status: ClaimStatus,
    val submitted: LocalDate,
    val claimed: Money,
    val paidDate: LocalDate? = null,
    val paid: Money? = null,
    val reference: String? = null,
    val transactionId: String? = null,
    val notes: String? = null,
)

/**
 * MED-06: one medical or dental expense. [paidDate] is when it was paid (the date that counts for
 * the tax credit), by default the date of service. MED-10: [outOfPocket] is the cost less every
 * reimbursement received.
 */
data class MedExpense(
    val id: String,
    val groupId: String,
    val memberId: String,
    val service: MedService,
    val serviceDate: LocalDate,
    val amount: Money,
    val providerId: String? = null,
    val paidDate: LocalDate? = null,
    val description: String? = null,
    val transactionId: String? = null,
    val medicationId: String? = null,
    val taxEligible: Boolean = true,
    val closed: Boolean = false,
    val notes: String? = null,
    val claims: List<MedClaim> = emptyList(),
) {
    val reimbursed: Money get() = claims.filter { it.status == ClaimStatus.PAID }.mapNotNull { it.paid }.fold(Money.zero(amount.currency), Money::plus)
    val outOfPocket: Money get() = amount - reimbursed
    val taxDate: LocalDate get() = paidDate ?: serviceDate
}

/** MED-07: where an expense stands, the next plan to claim and what it should pay. */
data class ExpenseStatus(val stage: ExpenseStage, val nextPlan: MedPlan? = null, val expected: Money? = null, val deadline: LocalDate? = null)

/** MED-04: what is left of a plan's coverage for one person, and when a limited service is covered again. */
data class CoverageLeft(val memberId: String, val plan: MedPlan, val coverage: MedCoverage, val annualLeft: Money?, val nextEligible: LocalDate?)

/** MED-12, MED-14: one person's eligible expenses for the tax credit. */
data class MedicalTaxPerson(
    val member: Member,
    val province: Province,
    /** Expenses paid in reach of the year (the 12 months before it too), out of pocket. */
    val expenses: List<MedExpense>,
    val best: Window?,
    val calendar: Window,
    /** An adult dependant other than a spouse: claimed on their own line (federal 33199). */
    val otherDependant: Boolean,
)

/**
 * MED-12, MED-14: the year's medical expense credit figures; [family] covers everyone but other
 * dependants, claimed together (federal line 33099).
 */
data class MedicalTaxReport(
    val year: Int,
    val people: List<MedicalTaxPerson>,
    val family: Window?,
    /**
     * MED-14: Quebec's line 381, when someone in the household files in Quebec: everyone's
     * expenses together, adult dependants included (Quebec has no separate line for them), only
     * those Quebec accepts, over their own best 12 months. Null when no one files in Quebec or
     * nothing counts.
     */
    val quebec: Window? = null,
    /** MED-14: expenses in reach that Quebec does not accept (massage therapy; naturopathy and osteopathy from 2026). */
    val quebecLeftOut: List<MedExpense> = emptyList(),
)

/**
 * MED-01 to MED-15: medical and dental plans, expenses and claims, and the medical expense tax
 * credit. Plans and expenses are kept in the account group the user chooses, as health records
 * are. Amounts are in Canadian dollars.
 */
class MedicalService internal constructor(private val books: Books) {

    private val cad = Currency.CAD

    // --- Plans (MED-01 to MED-03) ----------------------------------------------------------------

    fun plans(includeInactive: Boolean = true): List<MedPlan> = books.groups().flatMap { g ->
        val q = books.ledger(g).medicalQueries
        q.plans().executeAsList().map { it.toPlan(g.id, q.planPeople(it.id).executeAsList().map { p -> PlanPerson(p.member_id, p.priority.toInt()) }) }
    }.filter { includeInactive || it.active }

    fun plan(id: String): MedPlan = plans().firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    fun savePlan(p: MedPlan): MedPlan {
        validate(p.name.isNotBlank(), "error.nameRequired")
        validate(p.yearStartMonth in 1..12 && p.yearStartDay in 1..28, "error.invalidDate")
        validate(p.claimDays in (if (p.claimRule == ClaimDeadlineRule.AFTER_PLAN_YEAR) 0 else 1)..3650, "error.invalidNumber")
        validate(p.hsaAmount == null || (p.hsaAmount.currency == cad && !p.hsaAmount.isNegative), "error.currencyMismatch", cad.code)
        validate(p.people.all { it.priority in 1..9 }, "error.invalidNumber")
        val group = editable(p.groupId)
        val ledger = books.ledger(group)
        val q = ledger.medicalQueries
        val id = p.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.planById(id).executeAsOneOrNull()?.created_at ?: now
        ledger.transaction {
            q.upsertPlan(
                id, p.kind.name, p.name.trim(), p.insurer.blankToNull(), p.policyNumber.blankToNull(), p.certificateNumber.blankToNull(), p.memberId,
                p.yearStartMonth.toLong(), p.yearStartDay.toLong(), p.claimDays.toLong(), p.hsaAmount?.minorUnits, if (p.active) 1 else 0, p.notes.blankToNull(), created, now, p.claimRule.name,
            )
            q.clearPlanPeople(id)
            p.people.distinctBy { it.memberId }.forEach { q.addPlanPerson(id, it.memberId, it.priority.toLong()) }
        }
        books.session.audit("UPDATE", "med_plan", id)
        return plan(id)
    }

    fun deletePlan(id: String) {
        val p = plan(id)
        val group = editable(p.groupId)
        validate(books.groups().none { g -> books.ledger(g).medicalQueries.claims().executeAsList().any { it.plan_id == id } }, "error.planHasClaims")
        books.ledger(group).medicalQueries.deletePlan(id)
        books.contacts.forgetLinks(LinkTarget.MEDICAL_PLAN, listOf(id))
    }

    fun coverages(planId: String): List<MedCoverage> {
        val p = plan(planId)
        return books.ledger(books.group(p.groupId)).medicalQueries.coverages(planId).executeAsList().map { it.toCoverage() }
    }

    fun saveCoverage(c: MedCoverage): MedCoverage {
        val p = plan(c.planId)
        val group = editable(p.groupId)
        validate(c.percent.signum() >= 0 && c.percent <= BigDecimal(100), "error.percentRange")
        listOfNotNull(c.deductible, c.annualMax, c.perVisitMax).forEach { validate(it.currency == cad && !it.isNegative, "error.currencyMismatch", cad.code) }
        validate(c.frequencyMonths == null || c.frequencyMonths in 1..120, "error.invalidNumber")
        val id = c.id.ifBlank { Ids.newId() }
        books.ledger(group).medicalQueries.upsertCoverage(
            id, c.planId, c.service.name, c.percent.stripTrailingZeros().toPlainString(), c.deductible?.minorUnits, c.annualMax?.minorUnits, c.perVisitMax?.minorUnits,
            c.frequencyMonths?.toLong(), c.notes.blankToNull(),
        )
        return c.copy(id = id)
    }

    fun deleteCoverage(planId: String, coverageId: String) {
        val group = editable(plan(planId).groupId)
        books.ledger(group).medicalQueries.deleteCoverage(coverageId)
    }

    /** The active plans that cover [memberId] for [service], in the order they pay (coordination of benefits). */
    fun plansFor(memberId: String, service: MedService): List<MedPlan> = plans(includeInactive = false)
        .mapNotNull { p -> p.people.firstOrNull { it.memberId == memberId }?.let { p to it.priority } }
        .filter { (p, _) -> p.kind == MedPlanKind.HSA || coverages(p.id).any { it.service == service } }
        .sortedWith(compareBy({ it.second }, { it.first.kind == MedPlanKind.HSA }))
        .map { it.first }

    // --- Expenses (MED-06, MED-10) --------------------------------------------------------------

    fun expenses(memberId: String? = null): List<MedExpense> = books.groups().flatMap { g ->
        val q = books.ledger(g).medicalQueries
        val claims = q.claims().executeAsList().groupBy { it.expense_id }
        q.expenses().executeAsList().map { it.toExpense(g.id, claims[it.id].orEmpty().map { c -> c.toClaim() }) }
    }.filter { memberId == null || it.memberId == memberId }.sortedByDescending { it.serviceDate }

    fun expense(id: String): MedExpense = expenses().firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    fun saveExpense(e: MedExpense): MedExpense {
        validate(e.amount.currency == cad && e.amount.isPositive, "error.amountPositive")
        validate(e.memberId.isNotBlank(), "error.personRequired")
        validate(e.paidDate == null || e.paidDate >= e.serviceDate.minus(DatePeriod(years = 1)), "error.invalidDate")
        val group = editable(e.groupId)
        val q = books.ledger(group).medicalQueries
        val id = e.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.expenseById(id).executeAsOneOrNull()?.created_at ?: now
        q.upsertExpense(
            id, e.memberId, e.providerId, e.service.name, e.serviceDate.toString(), e.paidDate?.toString(), e.description.blankToNull(), e.amount.minorUnits,
            e.transactionId, e.medicationId, if (e.taxEligible) 1 else 0, if (e.closed) 1 else 0, e.notes.blankToNull(), created, now,
        )
        books.session.audit("UPDATE", "med_expense", id)
        return expense(id)
    }

    fun deleteExpense(id: String) {
        val e = expense(id)
        books.ledger(editable(e.groupId)).medicalQueries.deleteExpense(id)
        books.session.audit("DELETE", "med_expense", id)
    }

    /** MED-07: closes an expense (nothing more to claim) or opens it again. */
    fun close(id: String, closed: Boolean = true) {
        val e = expense(id)
        books.ledger(editable(e.groupId)).medicalQueries.setExpenseClosed(if (closed) 1 else 0, books.now(), id)
    }

    /** Health-category payments from [from] to [to] not yet recorded as medical expenses, to add in one click. */
    fun unrecorded(from: LocalDate, to: LocalDate): List<DrillRow> {
        val linked = books.groups().flatMap { g -> books.ledger(g).medicalQueries.linkedTransactions().executeAsList().filterNotNull() }.toSet()
        val health = books.categories.list(includeArchived = true).firstOrNull { it.systemKey == "health" }?.id ?: return emptyList()
        return books.reports.drillDown(ReportFilter(from, to), categoryId = health)
            .filter { it.amount.isNegative && it.transactionId !in linked }
            .filter { r -> books.categories.list(includeArchived = true).firstOrNull { it.id == r.categoryId }?.systemKey != "health.otc" }
            .distinctBy { it.transactionId }
    }

    /** Records a payment from the books as a medical expense for [memberId]. */
    fun fromTransaction(transactionId: String, memberId: String, service: MedService, groupId: String, providerId: String? = null): MedExpense {
        val t = books.transactions.get(transactionId)
        validate(t.amount.isNegative, "error.amountPositive")
        val amount = if (t.amount.currency == cad) -t.amount else books.rates.convert(-t.amount, cad, t.date) ?: throw ValidationException("error.noRate", t.amount.currency.code)
        val payee = t.payeeId?.let { id -> books.payees.list(true).firstOrNull { it.id == id }?.name } ?: t.payeeText
        return saveExpense(MedExpense("", groupId, memberId, service, t.date, amount, providerId, description = payee, transactionId = transactionId))
    }

    // --- Claims (MED-07 to MED-09) --------------------------------------------------------------

    /**
     * What [plan] should pay on [expense]: its rule for the service on what earlier plans in the
     * order did not pay, after this person's earlier use of the rule in the plan year. A Health
     * Spending Account pays everything up to its yearly credit.
     */
    fun expected(expense: MedExpense, plan: MedPlan): Money {
        val rule = ruleFor(plan, expense.service) ?: return Money.zero(cad)
        val yearStart = Medical.planYearStart(expense.serviceDate, plan.yearStartMonth, plan.yearStartDay)
        val hsa = plan.kind == MedPlanKind.HSA
        val earlier = expenses().filter { e ->
            e.id != expense.id && e.serviceDate >= yearStart && (e.serviceDate < expense.serviceDate || (e.serviceDate == expense.serviceDate && e.id < expense.id)) &&
                (hsa || (e.memberId == expense.memberId && e.service == expense.service)) && e.claims.any { it.planId == plan.id }
        }.sortedBy { it.serviceDate }
        var use = CoverageUse()
        for (e in earlier) {
            val claim = e.claims.first { it.planId == plan.id }
            val got = when (claim.status) {
                ClaimStatus.PAID -> claim.paid ?: Money.zero(cad)
                ClaimStatus.SUBMITTED -> claim.claimed
                ClaimStatus.DENIED -> Money.zero(cad)
            }.toBigDecimal()
            use = CoverageUse(use.paid + got, use.deductibleMet + Medical.deductibleUsed(e.amount.toBigDecimal(), rule, use))
        }
        // Coordination of benefits: this plan sees what the others did not pay.
        val others = expense.claims.filter { it.planId != plan.id }.fold(BigDecimal.ZERO) { a, c ->
            a + when (c.status) {
                ClaimStatus.PAID -> c.paid?.toBigDecimal() ?: BigDecimal.ZERO
                ClaimStatus.SUBMITTED -> c.claimed.toBigDecimal()
                ClaimStatus.DENIED -> BigDecimal.ZERO
            }
        }
        return Money.of(Medical.expected(expense.amount.toBigDecimal() - others, rule, use), cad)
    }

    private fun ruleFor(plan: MedPlan, service: MedService): CoverageRule? =
        if (plan.kind == MedPlanKind.HSA) CoverageRule(BigDecimal(100), annualMax = plan.hsaAmount?.toBigDecimal())
        else coverages(plan.id).firstOrNull { it.service == service }?.rule

    /** MED-07: where [expense] stands, and the next plan to claim with its deadline (MED-09). */
    fun status(expense: MedExpense): ExpenseStatus {
        if (expense.closed) return ExpenseStatus(ExpenseStage.CLOSED)
        if (expense.claims.any { it.status == ClaimStatus.SUBMITTED }) return ExpenseStatus(ExpenseStage.WAITING)
        val claimed = expense.claims.map { it.planId }.toSet()
        val next = plansFor(expense.memberId, expense.service).firstOrNull { it.id !in claimed }
        if (next == null || !expense.outOfPocket.isPositive) return ExpenseStatus(ExpenseStage.CLOSED)
        return ExpenseStatus(ExpenseStage.TO_SUBMIT, next, expected(expense, next), next.claimDeadline(expense.serviceDate))
    }

    /** Sends [expenseId] to [planId]; the amount claimed is what is left to be paid unless given. */
    fun submit(expenseId: String, planId: String, date: LocalDate, claimed: Money? = null, reference: String? = null): MedClaim {
        val e = expense(expenseId)
        plan(planId)
        validate(e.claims.none { it.planId == planId }, "error.alreadyClaimed")
        val amount = claimed ?: e.outOfPocket
        validate(amount.currency == cad && amount.isPositive && amount <= e.amount, "error.amountPositive")
        return writeClaim(e, MedClaim("", expenseId, planId, ClaimStatus.SUBMITTED, date, amount, reference = reference))
    }

    /** Records what a plan paid (from the explanation of benefits), and the deposit when known. */
    fun recordPayment(claimId: String, date: LocalDate, amount: Money, transactionId: String? = null): MedClaim {
        val (e, c) = locateClaim(claimId)
        validate(amount.currency == cad && !amount.isNegative && amount <= e.amount, "error.amountPositive")
        return writeClaim(e, c.copy(status = if (amount.isZero) ClaimStatus.DENIED else ClaimStatus.PAID, paidDate = date, paid = amount, transactionId = transactionId ?: c.transactionId))
    }

    fun deny(claimId: String, date: LocalDate): MedClaim {
        val (e, c) = locateClaim(claimId)
        return writeClaim(e, c.copy(status = ClaimStatus.DENIED, paidDate = date, paid = Money.zero(cad)))
    }

    fun saveClaim(c: MedClaim): MedClaim = writeClaim(expense(c.expenseId), c)

    fun deleteClaim(claimId: String) {
        val (e, _) = locateClaim(claimId)
        books.ledger(editable(e.groupId)).medicalQueries.deleteClaim(claimId)
    }

    /** MED-08: claims waiting for payment that an explanation of benefits for [amount] on [date] may answer, likeliest first. */
    fun eobCandidates(amount: Money, date: LocalDate): List<Pair<MedExpense, MedClaim>> = expenses().flatMap { e ->
        e.claims.filter { it.status == ClaimStatus.SUBMITTED && it.submitted <= date && it.claimed >= amount }.map { e to it }
    }.sortedBy { (_, c) -> (c.claimed - amount).toBigDecimal() }

    private fun writeClaim(e: MedExpense, c: MedClaim): MedClaim {
        val q = books.ledger(editable(e.groupId)).medicalQueries
        val id = c.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.claimById(id).executeAsOneOrNull()?.created_at ?: now
        q.upsertClaim(
            id, c.expenseId, c.planId, c.status.name, c.submitted.toString(), c.claimed.minorUnits, c.paidDate?.toString(), c.paid?.minorUnits,
            c.reference.blankToNull(), c.transactionId, c.notes.blankToNull(), created, now,
        )
        books.session.audit("UPDATE", "med_claim", id)
        return c.copy(id = id)
    }

    private fun locateClaim(claimId: String): Pair<MedExpense, MedClaim> =
        expenses().firstNotNullOfOrNull { e -> e.claims.firstOrNull { it.id == claimId }?.let { e to it } } ?: throw ValidationException("error.notFound")

    // --- Coverage left (MED-04) and deadlines (MED-09) -----------------------------------------

    /** For every plan covering [memberId]: what is left of each yearly maximum, and when limited services are covered again. */
    fun coverageLeft(memberId: String, today: LocalDate): List<CoverageLeft> {
        val all = expenses()
        return plans(includeInactive = false).filter { p -> p.people.any { it.memberId == memberId } }.flatMap { p ->
            val yearStart = Medical.planYearStart(today, p.yearStartMonth, p.yearStartDay)
            fun used(service: MedService?): Money = all.filter { e ->
                e.serviceDate >= yearStart && (p.kind == MedPlanKind.HSA || (e.memberId == memberId && e.service == service))
            }.flatMap { it.claims.filter { c -> c.planId == p.id } }.fold(Money.zero(cad)) { a, c ->
                a + when (c.status) { ClaimStatus.PAID -> c.paid ?: Money.zero(cad); ClaimStatus.SUBMITTED -> c.claimed; ClaimStatus.DENIED -> Money.zero(cad) }
            }
            if (p.kind == MedPlanKind.HSA) {
                val credit = MedCoverage("", p.id, MedService.OTHER, BigDecimal(100), annualMax = p.hsaAmount)
                listOf(CoverageLeft(memberId, p, credit, p.hsaAmount?.let { (it - used(null)).let { left -> if (left.isNegative) Money.zero(cad) else left } }, null))
            } else {
                coverages(p.id).map { c ->
                    val last = all.filter { it.memberId == memberId && it.service == c.service }.maxOfOrNull { it.serviceDate }
                    val next = Medical.nextEligible(last, c.frequencyMonths)?.takeIf { it > today }
                    CoverageLeft(memberId, p, c, c.annualMax?.let { (it - used(c.service)).let { left -> if (left.isNegative) Money.zero(cad) else left } }, next)
                }
            }
        }
    }

    /**
     * MED-09: claims still to send whose deadline is within [withinDays], or passed within the
     * medical claim window of Rates and rules, as reminders.
     */
    fun deadlines(today: LocalDate, withinDays: Int = LeadTimes.medicalClaim(today)): List<Renewal> {
        val overdue = LeadTimes.medicalClaim(today)
        val names = books.members.list(includeArchived = true).associate { it.id to it.displayName }
        // Only expenses recent enough to have a deadline ahead (or just passed) are looked at (NFR-02).
        val longest = plans(includeInactive = false).maxOfOrNull { it.longestClaimDays } ?: return emptyList()
        val since = today.minus(DatePeriod(days = longest + overdue))
        return expenses().filter { !it.closed && it.serviceDate >= since }.mapNotNull { e ->
            val s = status(e)
            val deadline = s.deadline ?: return@mapNotNull null
            if (s.stage != ExpenseStage.TO_SUBMIT) return@mapNotNull null
            val days = today.daysUntil(deadline)
            if (days > withinDays || days < -overdue) null
            else Renewal(RenewalKind.MEDICAL_CLAIM, e.id, listOfNotNull(names[e.memberId], e.description).joinToString(" · "), deadline, days, s.nextPlan?.name)
        }
    }

    // --- Tax credit (MED-12, MED-14) -------------------------------------------------------------

    /**
     * The year's eligible expenses per person, out of pocket, with the 12-month period ending in
     * the year that holds the most (federal line 33099 and Quebec line 381 both allow it). The
     * household's own figures (everyone but adult dependants) are claimed together, usually by
     * one spouse; adult dependants are claimed on their own line federally. When someone files in
     * Quebec, Quebec's total puts everyone together and keeps only what Quebec accepts (MED-14).
     */
    fun taxReport(year: Int): MedicalTaxReport {
        val reachStart = LocalDate(year - 1, 1, 2)
        val end = LocalDate(year, 12, 31)
        val eligible = expenses().filter { it.taxEligible && it.outOfPocket.isPositive && it.taxDate in reachStart..end }
        fun pairs(list: List<MedExpense>) = list.map { it.taxDate to it.outOfPocket.toBigDecimal() }
        val members = books.members.list(includeArchived = true)
        val people = members.mapNotNull { m ->
            val mine = eligible.filter { it.memberId == m.id }
            if (mine.isEmpty()) null
            else MedicalTaxPerson(m, books.provinceOf(m.id), mine.sortedBy { it.taxDate }, Medical.bestWindow(pairs(mine), year), Medical.calendarYear(pairs(mine), year), m.kind == MemberKind.DEPENDANT)
        }
        val family = people.filter { !it.otherDependant }.flatMap { it.expenses }
        // MED-14: Quebec claims the expenses of the whole household, dependants included, on one line.
        val inQuebec = people.isNotEmpty() && members.any { !it.archived && books.provinceOf(it.id).isQuebec }
        val everyone = people.flatMap { it.expenses }
        val (quebecCounted, quebecLeftOut) = if (inQuebec) everyone.partition(::countsInQuebec) else emptyList<MedExpense>() to emptyList()
        val quebec = if (inQuebec) Medical.bestWindow(pairs(quebecCounted), year) else null
        // Those left out are listed for the year, and for the months before it that Quebec's period reaches.
        val leftOut = quebecLeftOut.filter { e -> e.taxDate.year == year || (quebec != null && e.taxDate in quebec.start..quebec.end) }.sortedBy { it.taxDate }
        return MedicalTaxReport(year, people, Medical.bestWindow(pairs(family), year), quebec, leftOut)
    }

    /**
     * MED-14: whether an eligible expense also counts for Quebec's credit (line 381), by the date of
     * the service: Quebec accepts care only from its own list of practitioners (Rates and rules,
     * medical.qc.*).
     */
    fun countsInQuebec(e: MedExpense): Boolean = QUEBEC_RULES[e.service]?.let { Medical.quebecAccepts(it, e.serviceDate) } ?: true

    /** The expenses in a 12-month period, for the receipts bundle (MED-15); [quebec] keeps only those Quebec accepts. */
    fun expensesIn(window: Window, memberIds: Set<String>? = null, quebec: Boolean = false): List<MedExpense> =
        expenses().filter { it.taxEligible && it.outOfPocket.isPositive && it.taxDate in window.start..window.end && (memberIds == null || it.memberId in memberIds) && (!quebec || countsInQuebec(it)) }
            .sortedBy { it.taxDate }

    // --- Documents (MED-05, MED-08) -------------------------------------------------------------

    fun documents(entity: String, id: String): List<VaultDocument> = books.documents.documentsFor(entity, id)

    fun attach(entity: String, id: String, documentId: String) = books.documents.link(documentId, entity, id)

    // --- Helpers ----------------------------------------------------------------------------------

    private fun editable(groupId: String): GroupInfo = books.group(groupId).also { books.require(it, PermissionLevel.EDIT) }

    private fun PlanRow.toPlan(groupId: String, people: List<PlanPerson>) = MedPlan(
        id, groupId, MedPlanKind.valueOf(kind), name, insurer, policy_number, certificate_number, member_id, year_start_month.toInt(), year_start_day.toInt(),
        claim_days.toInt(), hsa_amount_minor?.let { Money.ofMinor(it, cad) }, active == 1L, notes, people,
        runCatching { ClaimDeadlineRule.valueOf(claim_rule) }.getOrDefault(ClaimDeadlineRule.AFTER_SERVICE),
    )

    private fun CoverageRow.toCoverage() = MedCoverage(
        id, plan_id, MedService.valueOf(service), BigDecimal(percent), deductible_minor?.let { Money.ofMinor(it, cad) },
        annual_max_minor?.let { Money.ofMinor(it, cad) }, per_visit_max_minor?.let { Money.ofMinor(it, cad) }, frequency_months?.toInt(), notes,
    )

    private fun ExpenseRow.toExpense(groupId: String, claims: List<MedClaim>) = MedExpense(
        id, groupId, member_id, MedService.valueOf(service), LocalDate.parse(service_date), Money.ofMinor(amount_minor, cad), provider_id,
        paid_date?.let(LocalDate::parse), description, transaction_id, medication_id, tax_eligible == 1L, closed == 1L, notes, claims,
    )

    private fun ClaimRow.toClaim() = MedClaim(
        id, expense_id, plan_id, ClaimStatus.valueOf(status), LocalDate.parse(submitted_date), Money.ofMinor(claimed_minor, cad),
        paid_date?.let(LocalDate::parse), paid_minor?.let { Money.ofMinor(it, cad) }, reference, transaction_id, notes,
    )

    companion object {
        const val EXPENSE = "med_expense"
        const val CLAIM = "med_claim"
        const val PLAN = "med_plan"

        /** MED-14: the services Quebec's credit may refuse, with the rule that says whether it accepts them. */
        private val QUEBEC_RULES = mapOf(
            MedService.MASSAGE to "medical.qc.massage",
            MedService.NATUROPATHY to "medical.qc.naturopathy",
            MedService.OSTEOPATHY to "medical.qc.osteopathy",
        )
    }
}
