package ca.schippers.hfm.books

import ca.schippers.hfm.calc.PensionJurisdiction
import ca.schippers.hfm.calc.plans.RegisteredPlans
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.math.BigDecimal

enum class RoomPlan { RRSP, TFSA, FHSA }
enum class BeneficiaryKind { BENEFICIARY, SUCCESSOR_HOLDER, RESP_BENEFICIARY }
enum class GrantKind { CESG, QESI, BCTESG, CLB, OTHER }
enum class PensionKind { DEFINED_BENEFIT, DEFINED_CONTRIBUTION, QPP, CPP, OAS, OTHER }

/** Where a room figure comes from: the CRA (entered), worked out from the rules and the books, or not known. */
enum class RoomSource { ENTERED, ESTIMATED, UNKNOWN }

/**
 * A registered plan's details. [contributorMemberId] is the spouse who contributes to a spousal
 * RRSP (the room used is theirs) or an RESP subscriber; [minimumAgeMemberId] is the younger spouse
 * when their age was chosen for the RRIF minimum; [lifReferenceRate] is the LIF maximum's rate.
 */
data class PlanDetails(
    val accountId: String,
    val contributorMemberId: String? = null,
    val minimumAgeMemberId: String? = null,
    val lifReferenceRate: BigDecimal? = null,
    val notes: String? = null,
    /** PROV-05: the law a LIRA or LIF answers to; null means the holder's province. */
    val jurisdiction: PensionJurisdiction? = null,
)

/** INV-11. */
data class Beneficiary(
    val id: String,
    val accountId: String,
    val kind: BeneficiaryKind,
    val name: String,
    val memberId: String? = null,
    val relationship: String? = null,
    val sharePercent: BigDecimal? = null,
    val notes: String? = null,
)

/** The CRA's figure for one person, plan and year. For the RRSP, [limit] is the deduction limit and [unused] the unused contributions. */
data class RoomEntry(val id: String, val memberId: String, val plan: RoomPlan, val year: Int, val limit: Money, val unused: Money = Money.zero(limit.currency), val notes: String? = null)

/** A contribution made outside the books (payroll, in kind), or a negative one to leave a line out. */
data class RoomAdjustment(val id: String, val memberId: String, val plan: RoomPlan, val date: LocalDate, val amount: Money, val notes: String? = null)

/** Money into (positive) or out of (negative) a plan, as counted for room. */
data class PlanFlow(val accountId: String, val accountName: String, val date: LocalDate, val amount: Money, val adjustment: Boolean = false)

/**
 * INV-09: one person's room for one plan and year. [available] is the room for the year (null
 * when unknown), [left] what remains after this year's contributions; a negative [left] is an
 * over-contribution, and [penaltyPerMonth] the 1% a month the CRA charges on it.
 */
data class RoomStatus(
    val member: Member,
    val plan: RoomPlan,
    val year: Int,
    val source: RoomSource,
    val available: Money?,
    val contributions: Money,
    val withdrawals: Money,
    val flows: List<PlanFlow>,
    val left: Money?,
    val penaltyPerMonth: Money?,
    /** FHSA: what is left of the $40,000 lifetime limit. */
    val lifetimeLeft: Money? = null,
) {
    val over: Boolean get() = left?.isNegative == true
}

/** INV-10: a RRIF or LIF for one year. */
data class WithdrawalStatus(
    val account: Account,
    val year: Int,
    val valueJanuary1: Money?,
    val valueEntered: Boolean,
    val age: Int?,
    val minimum: Money?,
    /** LIF only, where the jurisdiction sets one. */
    val maximum: Money?,
    val withdrawn: Money,
    val firstYear: Boolean,
    /** LIF only: the jurisdiction whose rules apply. */
    val jurisdiction: PensionJurisdiction? = null,
    /** LIF only: last year's investment earnings as entered from the statement (M-27). */
    val lastYearEarnings: Money? = null,
) {
    val leftToWithdraw: Money? get() = minimum?.let { (it - withdrawn).let { left -> if (left.isNegative) Money.zero(left.currency) else left } }
    val overMaximum: Boolean get() = maximum != null && withdrawn > maximum
}

/** A grant received; [transactionId] is its deposit in the RESP's register. */
data class RespGrantRecord(
    val id: String,
    val accountId: String,
    val memberId: String,
    val date: LocalDate,
    val kind: GrantKind,
    val amount: Money,
    val notes: String?,
    val transactionId: String? = null,
)

/**
 * One RESP beneficiary across all the household's RESPs: contributions, grants expected and
 * received. [provincialGrant] is the grant of the province the beneficiary lives in (QESI in
 * Quebec, BCTESG in British Columbia), or null where there is none (PROV-04).
 */
data class RespBeneficiaryStatus(
    val member: Member,
    val contributionsTotal: Money,
    val contributionsThisYear: Money,
    val cesgExpected: Money,
    val cesgReceived: Money,
    val provincialGrant: RegisteredPlans.ProvincialGrant?,
    val provincialExpected: Money,
    val provincialReceived: Money,
    val otherGrants: Money,
    /** CESG room carried forward: what extra contributions could still attract. */
    val cesgRoomLeft: Money,
    /** The lifetime contribution limit for the year (rule resp.lifetime). */
    val lifetimeLimit: Money = Money.ofMinor(RegisteredPlans.respLifetime(), contributionsTotal.currency),
) {
    val lifetimeLeft: Money get() = lifetimeLimit - contributionsTotal
}

data class Pension(
    val id: String,
    val memberId: String,
    val kind: PensionKind,
    val name: String,
    val administrator: String? = null,
    val memberNumber: String? = null,
    val startDate: LocalDate? = null,
    val normalRetirementAge: Int? = null,
    val indexed: Boolean = false,
    val survivorPercent: BigDecimal? = null,
    val accountId: String? = null,
    /** Who pays it, as the name appears on deposits (e.g. "Retraite Québec"). */
    val payer: String? = null,
    val notes: String? = null,
    val groupId: String? = null,
)

/** One yearly statement. Pension amounts are yearly. */
data class PensionStatement(
    val id: String,
    val pensionId: String,
    val year: Int,
    val pensionAdjustment: Money? = null,
    val accruedAnnual: Money? = null,
    val projectedAnnual: Money? = null,
    val value: Money? = null,
    val contributions: Money? = null,
    val notes: String? = null,
)

data class PensionSummary(val pension: Pension, val latest: PensionStatement?, val paymentsThisYear: Money)

/** Something about a plan that needs attention, for the reminders (INV-09, INV-10). */
data class PlanWarning(val key: String, val args: List<Any>, val subjectId: String)

/** INV-09 to INV-11 and pensions: contribution room, RRIF/LIF withdrawals, beneficiaries, RESP grants. */
class PlanService internal constructor(private val books: Books) {

    private val cad get() = books.rates.baseCurrency
    private fun zero(c: Currency = cad) = Money.zero(c)

    // --- Plan details and beneficiaries (INV-11) -----------------------------------------------

    fun registeredAccounts(includeClosed: Boolean = false): List<Account> =
        books.accounts.all(includeClosed).filter { it.type.isRegistered }

    fun details(accountId: String): PlanDetails {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).plansQueries.plan(accountId).executeAsOneOrNull()?.let {
            PlanDetails(it.account_id, it.contributor_member_id, it.minimum_age_member_id, it.lif_reference_rate?.let(::BigDecimal), it.notes, PensionJurisdiction.of(it.jurisdiction))
        } ?: PlanDetails(accountId)
    }

    fun saveDetails(d: PlanDetails) {
        val (group, account) = books.accounts.locate(d.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.isRegistered, "error.notRegisteredPlan")
        validate(d.lifReferenceRate == null || (d.lifReferenceRate.signum() > 0 && d.lifReferenceRate < BigDecimal("0.25")), "error.rateRange")
        // M-28: a spousal RRSP uses its contributor's room; without one it would count against nobody's.
        validate(account.type != AccountType.SPOUSAL_RRSP || d.contributorMemberId != null, "error.contributorRequired")
        books.ledger(group).plansQueries.upsertPlan(d.accountId, d.contributorMemberId, d.minimumAgeMemberId, d.lifReferenceRate?.toPlainString(), d.notes.blankToNull(), d.jurisdiction?.code)
        books.session.audit("UPDATE", "registered_plan", d.accountId)
    }

    fun beneficiaries(accountId: String): List<Beneficiary> {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).plansQueries.beneficiaries(accountId).executeAsList().map {
            Beneficiary(it.id, it.account_id, BeneficiaryKind.valueOf(it.kind), it.name, it.member_id, it.relationship, it.share_percent?.let(::BigDecimal), it.notes)
        }
    }

    fun saveBeneficiary(b: Beneficiary): Beneficiary {
        val (group, account) = books.accounts.locate(b.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.isRegistered, "error.notRegisteredPlan")
        validate(b.name.isNotBlank(), "error.nameRequired")
        validate(b.kind != BeneficiaryKind.RESP_BENEFICIARY || (account.type == AccountType.RESP && b.memberId != null), "error.respBeneficiary")
        validate(b.sharePercent == null || (b.sharePercent.signum() > 0 && b.sharePercent <= BigDecimal(100)), "error.sharePercent")
        val saved = b.copy(id = b.id.ifBlank { Ids.newId() }, name = b.name.trim())
        books.ledger(group).plansQueries.upsertBeneficiary(
            saved.id, saved.accountId, saved.kind.name, saved.memberId, saved.name, saved.relationship.blankToNull(), saved.sharePercent?.toPlainString(), saved.notes.blankToNull(),
        )
        books.session.audit("UPDATE", "plan_beneficiary", saved.accountId)
        return saved
    }

    fun deleteBeneficiary(accountId: String, id: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).plansQueries.deleteBeneficiary(id)
    }

    // --- Contribution room (INV-09) ------------------------------------------------------------

    fun roomEntries(): List<RoomEntry> = books.groups().flatMap { g ->
        books.ledger(g).plansQueries.rooms().executeAsList().map {
            RoomEntry(it.id, it.member_id, RoomPlan.valueOf(it.plan), it.year.toInt(), Money.ofMinor(it.limit_minor, cad), Money.ofMinor(it.unused_minor, cad), it.notes)
        }
    }

    /** Records the CRA's figure, replacing any earlier one for the same person, plan and year, in [groupId]. */
    fun saveRoom(entry: RoomEntry, groupId: String) {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.EDIT)
        validate(!entry.limit.isNegative && !entry.unused.isNegative, "error.amountPositive")
        roomEntries().filter { it.memberId == entry.memberId && it.plan == entry.plan && it.year == entry.year }.forEach { deleteRoom(it.id) }
        books.ledger(group).plansQueries.upsertRoom(
            entry.id.ifBlank { Ids.newId() }, entry.memberId, entry.plan.name, entry.year.toLong(), entry.limit.minorUnits, entry.unused.minorUnits, entry.notes.blankToNull(),
        )
    }

    fun deleteRoom(id: String) = editable().forEach { books.ledger(it).plansQueries.deleteRoom(id) }

    fun adjustments(): List<RoomAdjustment> = books.groups().flatMap { g ->
        books.ledger(g).plansQueries.adjustments().executeAsList().map {
            RoomAdjustment(it.id, it.member_id, RoomPlan.valueOf(it.plan), LocalDate.parse(it.date), Money.ofMinor(it.amount_minor, cad), it.notes)
        }
    }

    fun addAdjustment(a: RoomAdjustment, groupId: String) {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.EDIT)
        validate(!a.amount.isZero, "error.invalidNumber")
        books.ledger(group).plansQueries.insertAdjustment(Ids.newId(), a.memberId, a.plan.name, a.date.toString(), a.amount.minorUnits, a.notes.blankToNull())
    }

    fun deleteAdjustment(id: String) = editable().forEach { books.ledger(it).plansQueries.deleteAdjustment(id) }

    /** The people who have a plan of this kind, or a CRA figure for it. */
    fun peopleWith(plan: RoomPlan): List<Member> {
        val members = books.members.list(includeArchived = false)
        val ids = registeredAccounts(includeClosed = true).filter { planOf(it.type) == plan }.flatMap { ownersForRoom(it) }.toSet() +
            roomEntries().filter { it.plan == plan }.map { it.memberId }
        return members.filter { it.id in ids }
    }

    /** The plan whose room an account's contributions use; spousal RRSPs use the RRSP room. */
    private fun planOf(type: AccountType): RoomPlan? = when (type) {
        AccountType.RRSP, AccountType.SPOUSAL_RRSP -> RoomPlan.RRSP
        AccountType.TFSA -> RoomPlan.TFSA
        AccountType.FHSA -> RoomPlan.FHSA
        else -> null
    }

    /** M-28: open spousal RRSPs whose contributor is not chosen yet; their deposits count against nobody's room. */
    fun spousalWithoutContributor(): List<Account> =
        registeredAccounts().filter { it.type == AccountType.SPOUSAL_RRSP && details(it.id).contributorMemberId == null }

    /** Whose room an account uses: the contributor of a spousal RRSP, otherwise its owner. */
    private fun ownersForRoom(account: Account): List<String> =
        if (account.type == AccountType.SPOUSAL_RRSP) listOfNotNull(details(account.id).contributorMemberId) else account.ownerMemberIds.toList()

    /**
     * Money moving in from outside plans of the same kind counts as contributions; for a TFSA, money
     * out is a withdrawal. A line with no transfer and no category came from outside the books (cash
     * imported from a brokerage file, M-32) and counts too.
     */
    private fun flows(memberId: String, plan: RoomPlan, from: LocalDate, to: LocalDate): List<PlanFlow> {
        val all = books.accounts.all(includeClosed = true).associateBy { it.id }
        val excluded: Set<AccountType> = when (plan) {
            RoomPlan.RRSP -> setOf(AccountType.RRSP, AccountType.SPOUSAL_RRSP, AccountType.RRIF, AccountType.SPOUSAL_RRIF, AccountType.FHSA, AccountType.LIRA, AccountType.LIF, AccountType.PENSION)
            RoomPlan.TFSA -> setOf(AccountType.TFSA)
            RoomPlan.FHSA -> setOf(AccountType.FHSA)
        }
        val accounts = all.values.filter { planOf(it.type) == plan && memberId in ownersForRoom(it) }
        val lines = accounts.flatMap { account ->
            books.transactions.register(account.id).map { it.transaction }
                .filter { it.date in from..to && (it.transfer != null || fromOutsideBooks(it)) }
                .filter { t -> t.transfer == null || all[t.transfer.otherAccountId]?.type !in excluded }
                .filter { t -> t.amount.isPositive || plan == RoomPlan.TFSA }
                .map { PlanFlow(account.id, account.name, it.date, it.amount.let { m -> if (m.currency == cad) m else books.rates.convert(m, cad, it.date) ?: zero() }) }
        }
        val adjustments = adjustments().filter { it.memberId == memberId && it.plan == plan && it.date in from..to }
            .map { PlanFlow("", it.notes ?: "", it.date, it.amount, adjustment = true) }
        return (lines + adjustments).sortedBy { it.date }
    }

    /** M-32: a deposit or withdrawal recorded without a transfer or a category, such as cash imported from a brokerage file. */
    internal fun fromOutsideBooks(t: Transaction): Boolean = t.transfer == null && t.investmentId == null && t.splits.all { it.categoryId == null }

    fun room(memberId: String, plan: RoomPlan, year: Int, today: LocalDate): RoomStatus {
        val member = books.members.list(includeArchived = true).first { it.id == memberId }
        val entries = roomEntries().filter { it.memberId == memberId && it.plan == plan }
        return when (plan) {
            RoomPlan.RRSP -> rrspRoom(member, year, entries.firstOrNull { it.year == year })
            RoomPlan.TFSA -> tfsaRoom(member, year, entries)
            RoomPlan.FHSA -> fhsaRoom(member, year, entries, today)
        }
    }

    /**
     * RRSP: the deduction limit and unused contributions from the notice of assessment, less what
     * was contributed from March of [year] to the end of February of the next year (contributions
     * in the first 60 days of a year belong to the previous year's notice). Over-contributions up
     * to $2,000 are not penalized. The 60 days, the $2,000 and the 1 % a month are rules
     * (rrsp.deadline.days, rrsp.excess.buffer, rrsp.excess.tax).
     */
    private fun rrspRoom(member: Member, year: Int, entry: RoomEntry?): RoomStatus {
        val from = LocalDate(year, 1, 1).plus(DatePeriod(days = RegisteredPlans.rrspDeadlineDays(year)))
        val to = LocalDate(year + 1, 1, 1).plus(DatePeriod(days = RegisteredPlans.rrspDeadlineDays(year + 1) - 1))
        val flows = flows(member.id, RoomPlan.RRSP, from, to)
        val contributions = flows.map { it.amount }.sum(cad)
        val available = entry?.let { it.limit - it.unused }
        val left = available?.let { it - contributions }
        val excess = left?.let { -it - Money.of(RegisteredPlans.rrspExcessBuffer(year), cad) }?.takeIf { it.isPositive }
        return RoomStatus(
            member, RoomPlan.RRSP, year, if (entry != null) RoomSource.ENTERED else RoomSource.UNKNOWN, available, contributions, zero(), flows, left,
            excess?.times(RegisteredPlans.excessTaxPerMonth("rrsp", year)),
        )
    }

    /**
     * TFSA: the CRA's room at January 1 of the latest year entered, carried forward with each year's
     * limit, contributions and withdrawals (a withdrawal comes back the next January); without an
     * entry, every limit since the person turned 18, less what the books show.
     */
    private fun tfsaRoom(member: Member, year: Int, entries: List<RoomEntry>): RoomStatus {
        val base = entries.filter { it.year <= year }.maxByOrNull { it.year }
        val birthYear = member.birthDate?.year
        val startYear = base?.year ?: birthYear?.let { maxOf(2009, it + RegisteredPlans.tfsaAge(year)) }
        if (startYear == null) {
            val flows = flows(member.id, RoomPlan.TFSA, LocalDate(year, 1, 1), LocalDate(year, 12, 31))
            return RoomStatus(member, RoomPlan.TFSA, year, RoomSource.UNKNOWN, null, flows.filter { it.amount.isPositive }.map { it.amount }.sum(cad),
                -flows.filter { it.amount.isNegative }.map { it.amount }.sum(cad), flows, null, null)
        }
        var room = base?.limit ?: Money.parse(RegisteredPlans.tfsaLimit(startYear).toString(), cad)
        var y = startYear
        while (true) {
            val flows = flows(member.id, RoomPlan.TFSA, LocalDate(y, 1, 1), LocalDate(y, 12, 31))
            val contributions = flows.filter { it.amount.isPositive }.map { it.amount }.sum(cad)
            val withdrawals = -flows.filter { it.amount.isNegative }.map { it.amount }.sum(cad)
            if (y == year) {
                val left = room - contributions
                return RoomStatus(
                    member, RoomPlan.TFSA, year, if (base != null) RoomSource.ENTERED else RoomSource.ESTIMATED, room, contributions, withdrawals, flows, left,
                    if (left.isNegative) (-left).times(RegisteredPlans.excessTaxPerMonth("tfsa", year)) else null,
                )
            }
            room = room - contributions + withdrawals + Money.parse(RegisteredPlans.tfsaLimit(y + 1).toString(), cad)
            y++
        }
    }

    /** FHSA: the year's room (rule fhsa.annual, $8,000) from the year the first FHSA was opened, with carry-forward, within the lifetime limit (rule fhsa.lifetime, $40,000). */
    private fun fhsaRoom(member: Member, year: Int, entries: List<RoomEntry>, today: LocalDate): RoomStatus {
        val accounts = registeredAccounts(includeClosed = true).filter { it.type == AccountType.FHSA && member.id in it.ownerMemberIds }
        val opened = (accounts.map { it.openingDate.year } + entries.map { it.year }).minOrNull()
        val flows = flows(member.id, RoomPlan.FHSA, LocalDate(year, 1, 1), LocalDate(year, 12, 31))
        val contributions = flows.map { it.amount }.sum(cad)
        if (opened == null || opened > year) {
            return RoomStatus(member, RoomPlan.FHSA, year, RoomSource.UNKNOWN, null, contributions, zero(), flows, null, null)
        }
        val byYear = (opened..year).associateWith { y -> flows(member.id, RoomPlan.FHSA, LocalDate(y, 1, 1), LocalDate(y, 12, 31)).sumOf { it.amount.minorUnits } }
        val rooms = RegisteredPlans.fhsaRoom(opened, year, byYear)
        val entry = entries.firstOrNull { it.year == year }
        val available = entry?.limit ?: Money.ofMinor(rooms.last().available, cad)
        val left = available - contributions
        val lifetimeLeft = Money.ofMinor(RegisteredPlans.fhsaLifetime(year) * 100L - byYear.values.sum(), cad)
        return RoomStatus(
            member, RoomPlan.FHSA, year, if (entry != null) RoomSource.ENTERED else RoomSource.ESTIMATED, available, contributions, zero(), flows, left,
            if (left.isNegative) (-left).times(RegisteredPlans.excessTaxPerMonth("fhsa", year)) else null, lifetimeLeft,
        )
    }

    // --- RRIF and LIF withdrawals (INV-10) -----------------------------------------------------

    /**
     * The plan's value on January 1 of [year] from the statement (else the books at December 31 are
     * used), and for a LIF last year's investment earnings. M-27: a null leaves the stored figure as
     * it is; [clearValueJanuary1] goes back to the books' value.
     */
    fun setValueJanuary1(accountId: String, year: Int, value: Money?, lastYearEarnings: Money? = null) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).plansQueries
        val stored = q.planValue(accountId, year.toLong()).executeAsOneOrNull()
        val newValue = value?.minorUnits ?: stored?.value_minor
        if (newValue == null) {
            validate(lastYearEarnings == null, "error.planValueFirst")
            return
        }
        q.putPlanValue(accountId, year.toLong(), newValue, lastYearEarnings?.minorUnits ?: stored?.last_year_earnings_minor)
    }

    /** Forgets the January 1 value entered for [year], so the books' value at December 31 is used again. */
    fun clearValueJanuary1(accountId: String, year: Int) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).plansQueries.deletePlanValue(accountId, year.toLong())
    }

    fun withdrawals(year: Int): List<WithdrawalStatus> =
        registeredAccounts().filter { it.type in setOf(AccountType.RRIF, AccountType.SPOUSAL_RRIF, AccountType.LIF) }.map { withdrawalStatus(it, year) }

    fun withdrawalStatus(account: Account, year: Int): WithdrawalStatus {
        val (group, _) = books.accounts.locate(account.id)
        val entered = books.ledger(group).plansQueries.planValue(account.id, year.toLong()).executeAsOneOrNull()
        val firstYear = account.openingDate.year >= year
        val value = entered?.let { Money.ofMinor(it.value_minor, account.currency) }
            ?: if (firstYear) null else runCatching { books.investments.holdings(account.id, LocalDate(year - 1, 12, 31)).totalValue }.getOrNull()
        val d = details(account.id)
        val members = books.members.list(includeArchived = true)
        val who = members.firstOrNull { it.id == d.minimumAgeMemberId } ?: account.ownerMemberIds.singleOrNull()?.let { id -> members.firstOrNull { it.id == id } }
        val age = who?.birthDate?.let { ageOnJanuary1(it, year) }
        val minimum = when {
            firstYear -> Money.zero(account.currency)
            value == null || age == null -> null
            else -> RegisteredPlans.rrifMinimum(value, age, year)
        }
        val holder = account.ownerMemberIds.singleOrNull()
        val jurisdiction = if (account.type == AccountType.LIF) d.jurisdiction ?: PensionJurisdiction.Provincial(books.provinceOf(holder)) else null
        val maximum = if (jurisdiction != null && RegisteredPlans.lifHasMaximum(jurisdiction, year) && value != null && age != null && !firstYear) {
            RegisteredPlans.lifMaximum(
                value, age, d.lifReferenceRate ?: RegisteredPlans.lifReferenceRate(year), entered?.last_year_earnings_minor?.let { Money.ofMinor(it, account.currency) }, year,
            )
        } else {
            null
        }
        return WithdrawalStatus(
            account, year, value, entered != null, age, minimum, maximum, withdrawn(account, year), firstYear, jurisdiction,
            entered?.last_year_earnings_minor?.let { Money.ofMinor(it, account.currency) },
        )
    }

    /** What left the plan in [year]: money moved anywhere but another retirement plan (a TFSA counts), and tax withheld. */
    private fun withdrawn(account: Account, year: Int): Money {
        val all = books.accounts.all(includeClosed = true).associateBy { it.id }
        val taxes = books.categories.list(includeArchived = true).filter { it.systemKey?.startsWith("taxes") == true }.map { it.id }.toSet()
        return books.transactions.register(account.id).map { it.transaction }
            .filter { it.date.year == year && it.amount.isNegative }
            .filter { t ->
                val other = t.transfer?.let { all[it.otherAccountId] }
                (t.transfer != null && other?.type !in RETIREMENT) || (t.transfer == null && t.splits.any { it.categoryId in taxes })
            }
            .map { -it.amount }.sum(account.currency)
    }

    private fun ageOnJanuary1(birth: LocalDate, year: Int): Int = year - birth.year - if (birth.month.ordinal == 0 && birth.day == 1) 0 else 1

    // --- RESP ------------------------------------------------------------------------------------------

    fun grants(accountId: String): List<RespGrantRecord> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).plansQueries.grantsFor(accountId).executeAsList().map {
            RespGrantRecord(it.id, it.account_id, it.member_id, LocalDate.parse(it.date), GrantKind.valueOf(it.kind), Money.ofMinor(it.amount_minor, account.currency), it.notes, it.txn_id)
        }
    }

    /** Records a grant received; it also enters the deposit in the RESP's register. */
    fun recordGrant(accountId: String, memberId: String, date: LocalDate, kind: GrantKind, amount: Money, notes: String? = null) {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type == AccountType.RESP, "error.notResp")
        validate(amount.isPositive && amount.currency == account.currency, "error.amountPositive")
        val category = books.categories.list().firstOrNull { it.systemKey == "income.benefits.resp_grants" }?.id
        val line = books.transactions.create(TransactionDraft(accountId, date, amount, kind.label, listOf(SplitDraft(category, amount)), notes, memberId = memberId))
        books.ledger(group).plansQueries.insertGrant(Ids.newId(), accountId, memberId, date.toString(), kind.name, amount.minorUnits, line.id, notes.blankToNull())
    }

    /** M-29: the grant recorded with a deposit in an RESP's register, if it is one. */
    fun grantForTransaction(accountId: String, transactionId: String): RespGrantRecord? {
        val (_, account) = books.accounts.locate(accountId)
        if (account.type != AccountType.RESP) return null
        return grants(accountId).firstOrNull { it.transactionId == transactionId }
    }

    /**
     * M-29: deletes a grant record with its deposit in the register (a reconciled deposit only once
     * [confirmReconciled]). A deposit already deleted from the register is simply not there.
     */
    fun deleteGrant(accountId: String, grantId: String, confirmReconciled: Boolean = false) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).plansQueries
        val grant = q.grantsFor(accountId).executeAsList().firstOrNull { it.id == grantId } ?: return
        grant.txn_id?.takeIf { id -> books.ledger(group).ledgerQueries.txnById(id).executeAsOneOrNull() != null }
            ?.let { books.transactions.delete(it, confirmReconciled) }
        q.deleteGrant(grantId)
        books.session.audit("DELETE", "resp_grant", grantId)
    }

    private val GrantKind.label get() = when (this) {
        GrantKind.CESG -> "CESG / SCEE"
        GrantKind.QESI -> "QESI / IQEE"
        GrantKind.BCTESG -> "BCTESG"
        GrantKind.CLB -> "CLB / BEC"
        GrantKind.OTHER -> "RESP grant / Subvention REEE"
    }

    /**
     * Every RESP beneficiary: contributions (a contribution names its beneficiary, or is shared
     * equally among the plan's beneficiaries), the CESG and QESI the rules give, and what was received.
     */
    fun respBeneficiaries(year: Int, today: LocalDate = books.today()): List<RespBeneficiaryStatus> {
        val resps = registeredAccounts(includeClosed = true).filter { it.type == AccountType.RESP }
        val all = books.accounts.all(includeClosed = true).associateBy { it.id }
        val members = books.members.list(includeArchived = true).associateBy { it.id }
        val byMember = HashMap<String, MutableMap<Int, Long>>()
        val received = HashMap<Pair<String, GrantKind>, Long>()
        for (account in resps) {
            val beneficiaries = beneficiaries(account.id).filter { it.kind == BeneficiaryKind.RESP_BENEFICIARY }.mapNotNull { it.memberId }
            if (beneficiaries.isEmpty()) continue
            books.transactions.register(account.id).map { it.transaction }
                .filter { it.amount.isPositive && (if (it.transfer != null) all[it.transfer.otherAccountId]?.type != AccountType.RESP else fromOutsideBooks(it)) }
                .forEach { t ->
                    val minor = t.amount.convertTo(t.date).minorUnits
                    val targets = t.memberId?.takeIf { it in beneficiaries }?.let(::listOf) ?: beneficiaries
                    Money.ofMinor(minor, cad).split(targets.size).zip(targets).forEach { (share, id) ->
                        byMember.getOrPut(id) { HashMap() }.merge(t.date.year, share.minorUnits, Long::plus)
                    }
                }
            grants(account.id).forEach { g -> received.merge(g.memberId to g.kind, g.amount.minorUnits, Long::plus) }
        }
        val ids = (byMember.keys + received.keys.map { it.first }).distinct()
        return ids.mapNotNull { id ->
            val member = members[id] ?: return@mapNotNull null
            val contributions = byMember[id].orEmpty()
            val birthYear = member.birthDate?.year
            val cesg = birthYear?.let { RegisteredPlans.grants(RegisteredPlans.CESG, it, contributions, year) }.orEmpty()
            val provincial = RegisteredPlans.provincialGrant(books.provinceOf(id), year)
            val asOf = minOf(today, LocalDate(year, 12, 31))
            val provincialExpected = when (provincial) {
                RegisteredPlans.ProvincialGrant.QESI -> birthYear?.let { RegisteredPlans.grants(RegisteredPlans.QESI, it, contributions, year).sumOf { g -> g.grant } } ?: 0L
                RegisteredPlans.ProvincialGrant.BCTESG -> member.birthDate?.takeIf { RegisteredPlans.bctesgEligible(it, asOf) }?.let { RegisteredPlans.bctesgAmount(asOf) } ?: 0L
                null -> 0L
            }
            val provincialKind = provincial?.let { GrantKind.valueOf(it.name) }
            fun m(v: Long) = Money.ofMinor(v, cad)
            RespBeneficiaryStatus(
                member, m(contributions.filterKeys { it <= year }.values.sum()), m(contributions[year] ?: 0L),
                m(cesg.sumOf { it.grant }), m(received[id to GrantKind.CESG] ?: 0L), provincial, m(provincialExpected), m(provincialKind?.let { received[id to it] } ?: 0L),
                m(GrantKind.entries.filter { it != GrantKind.CESG && it != provincialKind }.sumOf { received[id to it] ?: 0L }), m(cesg.lastOrNull()?.roomLeft ?: 0L), m(RegisteredPlans.respLifetime(year)),
            )
        }.sortedBy { it.member.displayName }
    }

    private fun Money.convertTo(date: LocalDate): Money = if (currency == cad) this else books.rates.convert(this, cad, date) ?: Money.zero(cad)

    // --- Pensions ---------------------------------------------------------------------------------------

    fun pensions(): List<Pension> = books.groups().flatMap { g ->
        books.ledger(g).plansQueries.pensions().executeAsList().map {
            Pension(
                it.id, it.member_id, PensionKind.valueOf(it.kind), it.name, it.administrator, it.member_number, it.start_date?.let(LocalDate::parse),
                it.normal_retirement_age?.toInt(), it.indexed == 1L, it.survivor_percent?.let(::BigDecimal), it.account_id, it.payer, it.notes, g.id,
            )
        }
    }.sortedBy { it.name.lowercase() }

    fun savePension(p: Pension, groupId: String): Pension {
        val group = books.group(p.groupId ?: groupId)
        books.require(group, PermissionLevel.EDIT)
        validate(p.name.isNotBlank(), "error.nameRequired")
        validate(p.normalRetirementAge == null || p.normalRetirementAge in 50..75, "error.invalidNumber")
        val saved = p.copy(id = p.id.ifBlank { Ids.newId() }, name = p.name.trim(), groupId = group.id)
        books.ledger(group).plansQueries.upsertPension(
            saved.id, saved.memberId, saved.kind.name, saved.name, saved.administrator.blankToNull(), saved.memberNumber.blankToNull(), saved.startDate?.toString(),
            saved.normalRetirementAge?.toLong(), if (saved.indexed) 1 else 0, saved.survivorPercent?.toPlainString(), saved.accountId, saved.payer.blankToNull(),
            saved.notes.blankToNull(), books.now(),
        )
        books.session.audit("UPDATE", "pension", saved.id)
        return saved
    }

    fun deletePension(p: Pension) {
        val group = books.group(p.groupId ?: return)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).plansQueries.deletePension(p.id)
    }

    fun statements(p: Pension): List<PensionStatement> {
        val group = books.group(p.groupId ?: return emptyList())
        return books.ledger(group).plansQueries.pensionStatements(p.id).executeAsList().map {
            fun m(v: Long?) = v?.let { x -> Money.ofMinor(x, cad) }
            PensionStatement(it.id, it.pension_id, it.year.toInt(), m(it.pension_adjustment_minor), m(it.accrued_annual_minor), m(it.projected_annual_minor), m(it.value_minor), m(it.contributions_minor), it.notes)
        }
    }

    fun saveStatement(p: Pension, s: PensionStatement): PensionStatement {
        val group = books.group(p.groupId ?: throw ValidationException("error.accessDenied"))
        books.require(group, PermissionLevel.EDIT)
        validate(s.year in 1950..2200, "error.invalidNumber")
        val saved = s.copy(id = s.id.ifBlank { statements(p).firstOrNull { it.year == s.year }?.id ?: Ids.newId() }, pensionId = p.id)
        books.ledger(group).plansQueries.upsertPensionStatement(
            saved.id, p.id, saved.year.toLong(), saved.pensionAdjustment?.minorUnits, saved.accruedAnnual?.minorUnits, saved.projectedAnnual?.minorUnits,
            saved.value?.minorUnits, saved.contributions?.minorUnits, saved.notes.blankToNull(),
        )
        return saved
    }

    fun deleteStatement(p: Pension, statementId: String) {
        val group = books.group(p.groupId ?: return)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).plansQueries.deletePensionStatement(statementId)
    }

    /** Each pension with its latest statement and the payments received in [year]. */
    fun pensionSummaries(year: Int): List<PensionSummary> = pensions().map { p ->
        PensionSummary(p, statements(p).firstOrNull(), payments(p, year).map { it.amount.convertTo(it.date) }.sum(cad))
    }

    /**
     * Payments received in [year]: lines in the pension's income category for that person, or,
     * without a person, from the payer named on the pension.
     */
    fun payments(p: Pension, year: Int): List<DrillRow> {
        val key = when (p.kind) {
            PensionKind.QPP, PensionKind.CPP -> "income.pension.qpp_cpp"
            PensionKind.OAS -> "income.pension.oas"
            else -> "income.pension.employer"
        }
        val category = books.categories.list(includeArchived = true).firstOrNull { it.systemKey == key }?.id ?: return emptyList()
        val filter = ReportFilter(LocalDate(year, 1, 1), LocalDate(year, 12, 31))
        val forPerson = books.reports.drillDown(filter.copy(memberId = p.memberId), categoryId = category)
        val byPayer = p.payer?.let { payer ->
            books.reports.drillDown(filter, categoryId = category).filter { it.payee?.contains(payer, ignoreCase = true) == true }
        }.orEmpty()
        return (forPerson + byPayer).distinctBy { it.transactionId to it.amount }.sortedBy { it.date }
    }

    // --- Warnings for the reminders ---------------------------------------------------------------------

    /**
     * Over-contributions this year, RRIF and LIF minimums not yet withdrawn from November on, RRSPs
     * to convert in the year their holder turns 71, and spousal RRSPs without a contributor (M-28).
     */
    fun warnings(today: LocalDate): List<PlanWarning> {
        val out = ArrayList<PlanWarning>()
        val year = today.year
        for (plan in RoomPlan.entries) {
            for (m in peopleWith(plan)) {
                val r = runCatching { room(m.id, plan, if (plan == RoomPlan.RRSP) year - (if (today.month.ordinal < 2) 1 else 0) else year, today) }.getOrNull() ?: continue
                if (r.over && (plan != RoomPlan.RRSP || r.penaltyPerMonth != null)) out += PlanWarning("planWarning.over.$plan", listOf(m.displayName, -r.left!!), m.id)
            }
        }
        if (today.month.ordinal >= 10) {
            for (w in withdrawals(year)) {
                val left = w.leftToWithdraw ?: continue
                if (left.isPositive) out += PlanWarning("planWarning.minimum", listOf(w.account.name, left), w.account.id)
            }
        }
        val members = books.members.list().associateBy { it.id }
        for (a in registeredAccounts().filter { it.type in setOf(AccountType.RRSP, AccountType.SPOUSAL_RRSP) && it.status == AccountStatus.OPEN }) {
            val owner = a.ownerMemberIds.singleOrNull()?.let(members::get) ?: continue
            val birth = owner.birthDate ?: continue
            if (year - birth.year == RegisteredPlans.rrspLastAge(year) && a.balanceOrHoldings(today).isPositive) out += PlanWarning("planWarning.convert", listOf(a.name, owner.displayName), a.id)
        }
        spousalWithoutContributor().forEach { out += PlanWarning("planWarning.noContributor", listOf(it.name), it.id) }
        respBeneficiaries(year).filter { it.lifetimeLeft.isNegative }.forEach { out += PlanWarning("planWarning.respOver", listOf(it.member.displayName, -it.lifetimeLeft), it.member.id) }
        return out
    }

    private fun Account.balanceOrHoldings(today: LocalDate): Money = runCatching { books.investments.holdings(id, today).totalValue }.getOrDefault(openingBalance)

    private fun editable() = books.groups().filter { it.level == PermissionLevel.EDIT }

    private companion object {
        /** Plans that money can move between without being withdrawn. */
        val RETIREMENT = setOf(
            AccountType.RRSP, AccountType.SPOUSAL_RRSP, AccountType.RRIF, AccountType.SPOUSAL_RRIF, AccountType.LIRA, AccountType.LIF, AccountType.PENSION,
        )
    }
}
