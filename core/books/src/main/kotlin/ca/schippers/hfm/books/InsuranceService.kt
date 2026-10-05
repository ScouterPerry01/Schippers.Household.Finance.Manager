package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import ca.schippers.hfm.data.ledger.Insurance_claim as ClaimRow
import ca.schippers.hfm.data.ledger.Insurance_policy as PolicyRow

/** INS-01: kinds of policy. */
enum class PolicyKind { HOME, TENANT, CONDO, AUTO, RV, BOAT, LIFE, DISABILITY, CRITICAL_ILLNESS, LONG_TERM_CARE, TRAVEL, UMBRELLA, OTHER }

enum class PremiumFrequency(val perYear: Int) { MONTHLY(12), QUARTERLY(4), SEMI_ANNUAL(2), ANNUAL(1) }

enum class InsuranceClaimStatus { OPEN, PAID, DENIED, CLOSED }

/** INS-01, INS-02: a policy and the assets and vehicles it covers. [coverage] is the limit or, for life insurance, the benefit. */
data class InsurancePolicy(
    val id: String,
    val groupId: String,
    val kind: PolicyKind,
    val insurer: String,
    val broker: String? = null,
    val policyNumber: String? = null,
    val insuredMemberId: String? = null,
    val premium: Money? = null,
    val frequency: PremiumFrequency = PremiumFrequency.ANNUAL,
    val deductible: Money? = null,
    val coverage: Money? = null,
    val coverageNotes: String? = null,
    val startDate: LocalDate? = null,
    val renewalDate: LocalDate? = null,
    val active: Boolean = true,
    val notes: String? = null,
    val assetIds: Set<String> = emptySet(),
) {
    val annualPremium: Money? get() = premium?.times(BigDecimal(frequency.perYear))
}

/** INS-03: the premium of one term. */
data class PremiumRecord(val id: String, val policyId: String, val startDate: LocalDate, val premium: Money, val notes: String? = null)

/** INS-05: who receives a life policy's benefit. */
data class PolicyBeneficiary(
    val id: String,
    val policyId: String,
    val name: String,
    val memberId: String? = null,
    val relationship: String? = null,
    val sharePercent: BigDecimal? = null,
    val contingent: Boolean = false,
)

/** INS-04: a claim on a policy. */
data class InsuranceClaim(
    val id: String,
    val policyId: String,
    val date: LocalDate,
    val description: String,
    val assetId: String? = null,
    val claimNumber: String? = null,
    val status: InsuranceClaimStatus = InsuranceClaimStatus.OPEN,
    val claimed: Money? = null,
    val deductible: Money? = null,
    val paid: Money? = null,
    val paidDate: LocalDate? = null,
    val transactionId: String? = null,
    val notes: String? = null,
)

/** INS-02: an asset or vehicle no active policy covers, directly or through what it is part of. */
data class UninsuredItem(val id: String, val name: String, val isVehicle: Boolean, val value: Money?)

/** INS-05: one life, disability, critical illness or long-term care policy, for estate planning. */
data class LifeCover(val policy: InsurancePolicy, val beneficiaries: List<PolicyBeneficiary>)

/**
 * INS-01 to INS-05: insurance policies, what they cover, their premiums year over year, claims,
 * beneficiaries, and assets left uninsured. Kept in the account group the user chooses.
 */
class InsuranceService internal constructor(private val books: Books) {

    fun policies(includeInactive: Boolean = true): List<InsurancePolicy> = books.groups().flatMap { g ->
        val q = books.ledger(g).assetsQueries
        q.policies().executeAsList().map { it.toPolicy(g.id, q.policyAssets(it.id).executeAsList().toSet()) }
    }.filter { includeInactive || it.active }

    fun policy(id: String): InsurancePolicy = policies().firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    /**
     * Saves the policy. A new premium, or a changed one, joins the premium history, dated from the
     * term start; a corrected term start with the same premium moves that term in the history too.
     */
    fun save(p: InsurancePolicy): InsurancePolicy = write(p, correctsStart = true)

    private fun write(p: InsurancePolicy, correctsStart: Boolean): InsurancePolicy {
        validate(p.insurer.isNotBlank(), "error.nameRequired")
        listOfNotNull(p.premium, p.deductible, p.coverage).forEach { validate(!it.isNegative, "error.amountPositive") }
        val group = editable(p.groupId)
        val ledger = books.ledger(group)
        val q = ledger.assetsQueries
        val id = p.id.ifBlank { Ids.newId() }
        val now = books.now()
        val previous = q.policyById(id).executeAsOneOrNull()
        ledger.transaction {
            q.upsertPolicy(
                id, p.kind.name, p.insurer.trim(), p.broker.blankToNull(), p.policyNumber.blankToNull(), p.insuredMemberId, p.premium?.minorUnits, p.frequency.name,
                p.deductible?.minorUnits, p.coverage?.minorUnits, p.coverageNotes.blankToNull(), p.startDate?.toString(), p.renewalDate?.toString(),
                if (p.active) 1 else 0, p.notes.blankToNull(), previous?.created_at ?: now, now,
            )
            q.clearPolicyAssets(id)
            p.assetIds.forEach { q.addPolicyAsset(id, it) }
            // INS-03: a new premium is kept in the history, dated from the term it starts.
            if (p.premium != null && (previous == null || previous.premium_minor != p.premium.minorUnits)) {
                q.upsertPremium(Ids.newId(), id, (p.startDate ?: books.today()).toString(), p.premium.minorUnits, null)
            } else if (correctsStart && previous?.start_date != null && p.startDate != null && previous.start_date != p.startDate.toString()) {
                q.premiums(id).executeAsList().filter { it.start_date == previous.start_date }
                    .forEach { q.upsertPremium(it.id, id, p.startDate.toString(), it.premium_minor, it.notes) }
            }
        }
        books.session.audit("UPDATE", "insurance_policy", id)
        return policy(id)
    }

    fun delete(id: String) {
        val p = policy(id)
        books.ledger(editable(p.groupId)).assetsQueries.deletePolicy(id)
        books.session.audit("DELETE", "insurance_policy", id)
    }

    /** INS-03: renews a policy for a new term: the renewal date moves on and the new premium joins the history. */
    fun renew(id: String, newRenewal: LocalDate, premium: Money?): InsurancePolicy {
        val p = policy(id)
        validate(p.renewalDate == null || newRenewal > p.renewalDate, "error.invalidDate")
        val renewed = write(p.copy(startDate = p.renewalDate ?: p.startDate, renewalDate = newRenewal, premium = premium ?: p.premium), correctsStart = false)
        // The term starts at the old renewal date, even when the premium did not change.
        if (premium == null || premium == p.premium) {
            val q = books.ledger(books.group(p.groupId)).assetsQueries
            p.premium?.let { q.upsertPremium(Ids.newId(), id, (p.renewalDate ?: books.today()).toString(), it.minorUnits, null) }
        }
        return renewed
    }

    fun premiums(policyId: String): List<PremiumRecord> {
        val p = policy(policyId)
        return books.ledger(books.group(p.groupId)).assetsQueries.premiums(policyId).executeAsList()
            .map { PremiumRecord(it.id, it.policy_id, LocalDate.parse(it.start_date), Money.ofMinor(it.premium_minor, Currency.CAD), it.notes) }
            .distinctBy { it.startDate }
    }

    fun deletePremium(policyId: String, premiumId: String) {
        books.ledger(editable(policy(policyId).groupId)).assetsQueries.deletePremium(premiumId)
    }

    // --- Beneficiaries (INS-05) -------------------------------------------------------------------

    fun beneficiaries(policyId: String): List<PolicyBeneficiary> {
        val p = policy(policyId)
        return books.ledger(books.group(p.groupId)).assetsQueries.beneficiaries(policyId).executeAsList().map {
            PolicyBeneficiary(it.id, it.policy_id, it.name, it.member_id, it.relationship, it.share_percent?.let(::BigDecimal), it.contingent == 1L)
        }
    }

    fun saveBeneficiary(b: PolicyBeneficiary): PolicyBeneficiary {
        validate(b.name.isNotBlank(), "error.nameRequired")
        validate(b.sharePercent == null || (b.sharePercent.signum() > 0 && b.sharePercent <= BigDecimal(100)), "error.percentRange")
        val id = b.id.ifBlank { Ids.newId() }
        books.ledger(editable(policy(b.policyId).groupId)).assetsQueries.upsertBeneficiary(
            id, b.policyId, b.name.trim(), b.memberId, b.relationship.blankToNull(), b.sharePercent?.stripTrailingZeros()?.toPlainString(), if (b.contingent) 1 else 0,
        )
        return b.copy(id = id)
    }

    fun deleteBeneficiary(policyId: String, id: String) {
        books.ledger(editable(policy(policyId).groupId)).assetsQueries.deleteBeneficiary(id)
    }

    /** INS-05: life, disability, critical illness and long-term care cover, with beneficiaries. */
    fun lifeSummary(): List<LifeCover> = policies(includeInactive = false)
        .filter { it.kind in setOf(PolicyKind.LIFE, PolicyKind.DISABILITY, PolicyKind.CRITICAL_ILLNESS, PolicyKind.LONG_TERM_CARE) }
        .map { LifeCover(it, beneficiaries(it.id)) }

    // --- Claims (INS-04) --------------------------------------------------------------------------

    fun claims(policyId: String? = null): List<InsuranceClaim> = books.groups().flatMap { g ->
        books.ledger(g).assetsQueries.claims().executeAsList().map { it.toClaim() }
    }.filter { policyId == null || it.policyId == policyId }

    fun saveClaim(c: InsuranceClaim): InsuranceClaim {
        validate(c.description.isNotBlank(), "error.descriptionRequired")
        listOfNotNull(c.claimed, c.deductible, c.paid).forEach { validate(!it.isNegative, "error.amountPositive") }
        val q = books.ledger(editable(policy(c.policyId).groupId)).assetsQueries
        val id = c.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.claimById(id).executeAsOneOrNull()?.created_at ?: now
        q.upsertClaim(
            id, c.policyId, c.assetId, c.date.toString(), c.description.trim(), c.claimNumber.blankToNull(), c.status.name, c.claimed?.minorUnits,
            c.deductible?.minorUnits, c.paid?.minorUnits, c.paidDate?.toString(), c.transactionId, c.notes.blankToNull(), created, now,
        )
        books.session.audit("UPDATE", "insurance_claim", id)
        return c.copy(id = id)
    }

    fun deleteClaim(c: InsuranceClaim) {
        books.ledger(editable(policy(c.policyId).groupId)).assetsQueries.deleteClaim(c.id)
    }

    // --- Uninsured assets (INS-02) and renewals (INS-03) -------------------------------------------

    /**
     * INS-02: active assets and vehicles that no active policy covers, directly or through an asset
     * they are part of (the contents of an insured home are insured with it).
     */
    fun uninsured(today: LocalDate): List<UninsuredItem> {
        val covered = policies(includeInactive = false).filter { it.renewalDate == null || it.renewalDate >= today }.flatMap { it.assetIds }.toSet()
        val assets = books.assets.list()
        val byId = assets.associateBy { it.id }
        fun insured(a: Asset): Boolean {
            var current: Asset? = a
            val seen = HashSet<String>()
            while (current != null && seen.add(current.id)) {
                if (current.id in covered) return true
                current = current.parentId?.let(byId::get)
            }
            return false
        }
        return assets.filter { !insured(it) }.map { UninsuredItem(it.id, it.name, false, it.valueOn(today) ?: it.purchasePrice) } +
            books.vehicles.list().filter { it.id !in covered }.map { UninsuredItem(it.id, it.name, true, it.purchasePrice) }
    }

    /** INS-03: policies to renew within [withinDays], as reminders. */
    fun renewals(today: LocalDate, withinDays: Int = 30): List<Renewal> = policies(includeInactive = false).mapNotNull { p ->
        val date = p.renewalDate ?: return@mapNotNull null
        val days = today.daysUntil(date)
        if (days > withinDays || days < -30) null else Renewal(RenewalKind.INSURANCE_RENEWAL, p.id, p.insurer, date, days, p.policyNumber)
    }

    private fun editable(groupId: String): GroupInfo = books.group(groupId).also { books.require(it, PermissionLevel.EDIT) }

    private fun PolicyRow.toPolicy(groupId: String, assets: Set<String>): InsurancePolicy {
        val c = Currency.CAD
        return InsurancePolicy(
            id, groupId, PolicyKind.valueOf(kind), insurer, broker, policy_number, insured_member_id, premium_minor?.let { Money.ofMinor(it, c) },
            PremiumFrequency.valueOf(premium_frequency), deductible_minor?.let { Money.ofMinor(it, c) }, coverage_minor?.let { Money.ofMinor(it, c) }, coverage_notes,
            start_date?.let(LocalDate::parse), renewal_date?.let(LocalDate::parse), active == 1L, notes, assets,
        )
    }

    private fun ClaimRow.toClaim(): InsuranceClaim {
        val c = Currency.CAD
        return InsuranceClaim(
            id, policy_id, LocalDate.parse(date), description, asset_id, claim_number, InsuranceClaimStatus.valueOf(status), claimed_minor?.let { Money.ofMinor(it, c) },
            deductible_minor?.let { Money.ofMinor(it, c) }, paid_minor?.let { Money.ofMinor(it, c) }, paid_date?.let(LocalDate::parse), txn_id, notes,
        )
    }

    companion object {
        const val POLICY = "insurance_policy"
        const val CLAIM = "insurance_claim"
    }
}
