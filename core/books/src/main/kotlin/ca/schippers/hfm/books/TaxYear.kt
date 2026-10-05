package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.invest.SlipKind
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** TAX-01: the slips and receipts a person files with, federal and Quebec (RL). */
enum class SlipType(val quebec: Boolean = false) {
    T4, T4A, T4A_P, T4A_OAS, T4E, T4RSP, T4RIF, T4FHSA, T5, T3, T5008, T2202, RRSP_RECEIPT,
    RL1(true), RL2(true), RL3(true), RL6(true), RL8(true), RL16(true), RL18(true), RL24(true),
    OTHER,
}

enum class SlipStatus { EXPECTED, RECEIVED, NOT_EXPECTED }

/**
 * TAX-01: one slip on a person's checklist for a tax year: expected from the books ([reason] says
 * why) or added by the user ([manual]). [id] links the slip's documents. [memberId] is null for the
 * household, when the books do not say whose it is.
 */
data class ChecklistSlip(
    val year: Int,
    val memberId: String?,
    val key: String,
    val type: SlipType,
    val issuer: String,
    val groupId: String,
    val status: SlipStatus,
    val manual: Boolean,
    val reason: SlipReason?,
    val documents: Int,
) {
    val id: String get() = "$year|${memberId.orEmpty()}|$key"
}

/** Why the books expect a slip: income on a category, plan activity, or the investment income report. */
enum class SlipReason { EMPLOYMENT, PENSION, BENEFITS, INTEREST, TUITION, CHILD_CARE, RRSP_CONTRIBUTION, PLAN_WITHDRAWAL, FHSA, INVESTMENT_INCOME, SALES }

/** TAX-01: the slip checklist per person and tax year. */
class SlipChecklistService internal constructor(private val books: Books) {

    /** The slips for [year]: those the books expect, with what the user marked, and those the user added. */
    fun checklist(year: Int): List<ChecklistSlip> {
        val expected = LinkedHashMap<Triple<String?, String, SlipType>, Expected>()
        fun expect(member: String?, issuer: String, type: SlipType, group: String, reason: SlipReason) {
            val key = "${type.name.lowercase()}:${issuer.trim().lowercase()}"
            expected.putIfAbsent(Triple(member, key, type), Expected(member, key, type, issuer.trim(), group, reason))
            // Quebec issues its own slip for most federal ones (Relevé), to people who file there.
            if (filesInQuebec(member)) QUEBEC[type]?.let { rl -> expect(member, issuer, rl, group, reason) }
        }
        fromCategories(year, ::expect)
        fromPlans(year, ::expect)
        fromInvestments(year, ::expect)

        val stored = books.groups().flatMap { g -> books.ledger(g).taxYearQueries.slipChecks(year.toLong()).executeAsList().map { g.id to it } }
        val marks = stored.associate { (_, r) -> (r.member_id.ifEmpty { null } to r.slip_key) to r }
        val slips = expected.values.map { e ->
            val mark = marks[e.member to e.key]
            ChecklistSlip(year, e.member, e.key, e.type, e.issuer, e.group, mark?.status?.let(SlipStatus::valueOf) ?: SlipStatus.EXPECTED, false, e.reason, 0)
        } + stored.filter { (_, r) -> r.manual == 1L }.map { (g, r) ->
            ChecklistSlip(year, r.member_id.ifEmpty { null }, r.slip_key, SlipType.valueOf(r.slip_type), r.issuer, g, SlipStatus.valueOf(r.status), true, null, 0)
        }
        return slips.map { it.copy(documents = books.documents.documentsFor(ENTITY, it.id).size) }
            .sortedWith(compareBy<ChecklistSlip>({ it.memberId == null }, { it.memberId }, { it.type.quebec }, { it.type.ordinal }, { it.issuer.lowercase() }))
    }

    /** Marks a slip received, not expected this year, or expected again. */
    fun setStatus(slip: ChecklistSlip, status: SlipStatus) {
        val group = books.group(slip.groupId).also { books.require(it, PermissionLevel.EDIT) }
        val q = books.ledger(group).taxYearQueries
        if (status == SlipStatus.EXPECTED && !slip.manual) q.deleteSlipCheck(slip.year.toLong(), slip.memberId.orEmpty(), slip.key)
        else q.upsertSlipCheck(slip.year.toLong(), slip.memberId.orEmpty(), slip.key, slip.type.name, slip.issuer, status.name, if (slip.manual) 1 else 0)
    }

    /** Adds a slip the books cannot know about (a T4A from a contract, an RL-24 from a day camp...). */
    fun add(year: Int, memberId: String?, type: SlipType, issuer: String, groupId: String): ChecklistSlip {
        validate(issuer.isNotBlank(), "error.slipIssuer")
        val group = books.group(groupId).also { books.require(it, PermissionLevel.EDIT) }
        val key = "manual:${type.name.lowercase()}:${issuer.trim().lowercase()}"
        books.ledger(group).taxYearQueries.upsertSlipCheck(year.toLong(), memberId.orEmpty(), key, type.name, issuer.trim(), SlipStatus.EXPECTED.name, 1)
        return ChecklistSlip(year, memberId, key, type, issuer.trim(), groupId, SlipStatus.EXPECTED, true, null, 0)
    }

    /** Removes a slip the user added; its documents stay in the vault. */
    fun remove(slip: ChecklistSlip) {
        validate(slip.manual, "error.slipNotManual")
        val group = books.group(slip.groupId).also { books.require(it, PermissionLevel.EDIT) }
        books.ledger(group).taxYearQueries.deleteSlipCheck(slip.year.toLong(), slip.memberId.orEmpty(), slip.key)
    }

    private fun filesInQuebec(memberId: String?): Boolean = books.provinceOf(memberId) == Province.QC

    /** Income and payments on categories that come with a slip, by payee and person. */
    private fun fromCategories(year: Int, expect: (String?, String, SlipType, String, SlipReason) -> Unit) {
        val byKey = books.categories.list(includeArchived = true).mapNotNull { c -> c.systemKey?.let { it to c.id } }.toMap()
        val wanted = CATEGORY_SLIPS.keys.mapNotNull { k -> byKey[k]?.let { it to k } }.toMap()
        if (wanted.isEmpty()) return
        val accounts = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        for (g in books.groups()) {
            val rows = books.ledger(g).taxYearQueries.slipSourceSplits(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString(), wanted.keys).executeAsList()
            // Bank interest: no T5 under $50 a year from one payer (Rates and rules).
            val t5From = Thresholds.t5Interest(LocalDate(year, 12, 31)).movePointRight(2).toLong()
            val interest = rows.filter { wanted[it.category_id] == "income.investment.interest" }.groupBy { it.member_id to it.payee_text.orEmpty() }
                .filterValues { lines -> lines.sumOf { it.amount_minor } >= t5From }.keys
            for (r in rows) {
                val categoryKey = wanted[r.category_id] ?: continue
                val (type, reason) = CATEGORY_SLIPS.getValue(categoryKey)
                val payee = r.payee_text?.takeIf { it.isNotBlank() } ?: continue
                if (type == SlipType.RL24 && !filesInQuebec(r.member_id)) continue
                if (type == SlipType.T5 && (r.member_id to payee) !in interest) continue
                if (accounts[r.account_id]?.type?.isRegistered == true) continue
                val member = r.member_id ?: accounts[r.account_id]?.ownerMemberIds?.singleOrNull()
                expect(member, payee, type, g.id, reason)
            }
        }
    }

    /** Registered plans: RRSP contribution receipts, and slips for money taken out. */
    private fun fromPlans(year: Int, expect: (String?, String, SlipType, String, SlipReason) -> Unit) {
        val institutions = books.institutions.list().associate { it.id to it.name }
        val all = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        for (a in books.plans.registeredAccounts(includeClosed = true)) {
            val issuer = a.institutionId?.let(institutions::get) ?: a.name
            // M-32: cash imported from a brokerage file has no transfer, and still came from outside.
            val lines = books.transactions.register(a.id).map { it.transaction }.filter { it.date.year == year && (it.transfer != null || books.plans.fromOutsideBooks(it)) }
            val outside = lines.filter { t -> t.transfer == null || all[t.transfer.otherAccountId]?.type?.isRegistered != true }
            val owner = a.ownerMemberIds.singleOrNull()
            when (a.type) {
                AccountType.RRSP, AccountType.SPOUSAL_RRSP -> {
                    if (outside.any { it.amount.isPositive }) {
                        val contributor = if (a.type == AccountType.SPOUSAL_RRSP) books.plans.details(a.id).contributorMemberId else owner
                        expect(contributor, issuer, SlipType.RRSP_RECEIPT, a.groupId, SlipReason.RRSP_CONTRIBUTION)
                    }
                    if (outside.any { it.amount.isNegative }) expect(owner, issuer, SlipType.T4RSP, a.groupId, SlipReason.PLAN_WITHDRAWAL)
                }
                AccountType.RRIF, AccountType.SPOUSAL_RRIF, AccountType.LIF ->
                    if (outside.any { it.amount.isNegative }) expect(owner, issuer, SlipType.T4RIF, a.groupId, SlipReason.PLAN_WITHDRAWAL)
                AccountType.FHSA -> if (lines.isNotEmpty()) expect(owner, issuer, SlipType.T4FHSA, a.groupId, SlipReason.FHSA)
                else -> Unit
            }
        }
    }

    /** Non-registered investment accounts: T5 and T3 (RL-3, RL-16) from the investment income report, T5008 for sales. */
    private fun fromInvestments(year: Int, expect: (String?, String, SlipType, String, SlipReason) -> Unit) {
        val institutions = books.institutions.list().associate { it.id to it.name }
        fun issuer(a: Account) = a.institutionId?.let(institutions::get) ?: a.name
        for (person in books.taxSlips.report(year).people) {
            for (line in person.slips) {
                if (line.boxes.values.all { it.isZero }) continue
                val type = when (line.kind) { SlipKind.T5 -> SlipType.T5; SlipKind.T3 -> SlipType.T3; else -> continue }
                val name = if (type == SlipType.T3) line.security?.name ?: issuer(line.account) else issuer(line.account)
                expect(person.member?.id, name, type, line.account.groupId, SlipReason.INVESTMENT_INCOME)
            }
        }
        for (a in books.taxSlips.accounts().filter { it.type != AccountType.CRYPTO_WALLET }) {
            if (books.investments.transactions(a.id).none { it.kind == InvestmentKind.SELL && it.date.year == year }) continue
            val owners = a.ownerMemberIds.ifEmpty { setOf<String?>(null) }
            for (o in owners) expect(o, issuer(a), SlipType.T5008, a.groupId, SlipReason.SALES)
        }
    }

    private data class Expected(val member: String?, val key: String, val type: SlipType, val issuer: String, val group: String, val reason: SlipReason)

    companion object {
        /** Documents (the slips themselves) are linked under this entity, with [ChecklistSlip.id]. */
        const val ENTITY = "taxslip"

        private val CATEGORY_SLIPS = mapOf(
            "income.employment" to (SlipType.T4 to SlipReason.EMPLOYMENT),
            "income.employment.salary" to (SlipType.T4 to SlipReason.EMPLOYMENT),
            "income.employment.bonus" to (SlipType.T4 to SlipReason.EMPLOYMENT),
            "income.pension.employer" to (SlipType.T4A to SlipReason.PENSION),
            "income.pension.qpp_cpp" to (SlipType.T4A_P to SlipReason.PENSION),
            "income.pension.oas" to (SlipType.T4A_OAS to SlipReason.PENSION),
            "income.pension.rrif" to (SlipType.T4RIF to SlipReason.PENSION),
            "income.benefits.ei" to (SlipType.T4E to SlipReason.BENEFITS),
            "income.benefits.ei_qpip" to (SlipType.T4E to SlipReason.BENEFITS),
            "income.investment.interest" to (SlipType.T5 to SlipReason.INTEREST),
            "education.tuition" to (SlipType.T2202 to SlipReason.TUITION),
            "children.childcare" to (SlipType.RL24 to SlipReason.CHILD_CARE),
        )

        /** The Quebec slip that goes with a federal one, for people filing in Quebec. */
        private val QUEBEC = mapOf(
            SlipType.T4 to SlipType.RL1, SlipType.T4A to SlipType.RL2, SlipType.T4A_P to SlipType.RL2, SlipType.T4RSP to SlipType.RL2,
            SlipType.T4RIF to SlipType.RL2, SlipType.T5 to SlipType.RL3, SlipType.T3 to SlipType.RL16, SlipType.T5008 to SlipType.RL18,
            SlipType.T2202 to SlipType.RL8,
        )
    }
}

/** TAX-03: who instalments are paid to. */
enum class TaxAuthority { CRA, REVENU_QUEBEC }

enum class InstalmentState { PAID, PARTLY_PAID, DUE, LATE }

/**
 * TAX-03: one instalment: what is owed by [dueDate], and how much of it the payments recorded so far
 * cover, payments counting towards the earliest instalments first.
 */
data class Instalment(
    val accountId: String,
    val groupId: String,
    val memberId: String?,
    val year: Int,
    val authority: TaxAuthority,
    val dueDate: LocalDate,
    val amount: Money,
    val covered: Money,
    val state: InstalmentState,
)

/** TAX-03: instalment schedules from the CRA's and Revenu Québec's reminders, and whether they are paid. */
class InstalmentService internal constructor(private val books: Books) {

    /** Every instalment due in [year], earliest first, with what the payments cover, as of [today]. */
    fun schedule(year: Int, today: LocalDate = books.today()): List<Instalment> {
        val category = books.categories.list(includeArchived = true).firstOrNull { it.systemKey == "taxes.instalments" }?.id
        return books.groups().flatMap { g ->
            val q = books.ledger(g).taxYearQueries
            q.instalments(year.toLong()).executeAsList().groupBy { Triple(it.account_id, it.member_id, it.authority) }.flatMap { (key, rows) ->
                val (accountId, member, authority) = key
                val currency = books.accounts.get(accountId).currency
                val payments = if (category == null) emptyList() else q.instalmentPayments(accountId, LocalDate(year, 1, 1).toString(), LocalDate(year + 1, 1, 31).toString(), category)
                    .executeAsList()
                    .filter { member.isEmpty() || it.member_id == null || it.member_id == member }
                    .filter { p -> (TaxAuthority.valueOf(authority) == TaxAuthority.REVENU_QUEBEC) == QUEBEC.containsMatchIn(p.payee_text.orEmpty()) }
                var paid = -payments.sumOf { it.amount_minor }
                rows.sortedBy { it.due_date }.map { r ->
                    val due = LocalDate.parse(r.due_date)
                    val covered = minOf(maxOf(paid, 0), r.amount_minor)
                    paid -= r.amount_minor
                    val state = when {
                        covered >= r.amount_minor -> InstalmentState.PAID
                        due < today -> InstalmentState.LATE
                        covered > 0 -> InstalmentState.PARTLY_PAID
                        else -> InstalmentState.DUE
                    }
                    Instalment(accountId, g.id, member.ifEmpty { null }, year, TaxAuthority.valueOf(authority), due, Money.ofMinor(r.amount_minor, currency), Money.ofMinor(covered, currency), state)
                }
            }
        }.sortedBy { it.dueDate }
    }

    /**
     * Sets one person's instalments to [authority] for [year], paid from [accountId]: one amount per
     * due date (March 15, June 15, September 15 and December 15), a zero leaving a date out.
     */
    fun save(accountId: String, memberId: String?, year: Int, authority: TaxAuthority, amounts: List<Money>) {
        validate(amounts.size == 4, "error.instalmentAmounts")
        val account = books.accounts.get(accountId)
        validate(amounts.all { it.currency == account.currency && !it.isNegative }, "error.instalmentAmounts")
        val group = books.group(account.groupId).also { books.require(it, PermissionLevel.EDIT) }
        val q = books.ledger(group).taxYearQueries
        books.ledger(group).transaction {
            q.deleteInstalments(accountId, memberId.orEmpty(), year.toLong(), authority.name)
            amounts.forEachIndexed { i, m ->
                if (!m.isZero) q.insertInstalment(accountId, memberId.orEmpty(), year.toLong(), authority.name, dueDate(year, i).toString(), m.minorUnits)
            }
        }
    }

    /**
     * Instalments not yet paid, due within [withinDays] or overdue by up to the instalment window of
     * Rates and rules (30 days built in), as reminders.
     */
    internal fun renewals(today: LocalDate, withinDays: Int): List<Renewal> {
        val overdue = LeadTimes.instalment(today)
        val members = books.members.list(includeArchived = true).associateBy { it.id }
        return (schedule(today.year, today) + if (today.month == kotlinx.datetime.Month.JANUARY) schedule(today.year - 1, today) else emptyList())
            .filter { it.state != InstalmentState.PAID && today.daysUntil(it.dueDate) in -overdue..withinDays }
            .map { i ->
                // The detail is the authority's code; the apps name it in the user's language.
                Renewal(RenewalKind.TAX_INSTALMENT, "${i.accountId}|${i.memberId.orEmpty()}|${i.authority}", i.memberId?.let { members[it]?.displayName }.orEmpty(),
                    i.dueDate, today.daysUntil(i.dueDate), i.authority.name)
            }
    }

    companion object {
        /** The [index]th (0 to 3) instalment due date of [year]: March, June, September and December 15, as Rates and rules give them. */
        fun dueDate(year: Int, index: Int): LocalDate = Thresholds.instalmentDueDates(year)[index]

        private val QUEBEC = Regex("revenu\\s*qu[eé]bec|\\bRQ\\b|minist[eè]re du revenu", RegexOption.IGNORE_CASE)
    }
}
