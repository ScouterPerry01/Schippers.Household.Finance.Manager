package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** EST-01: the people an executor or a spouse would need to reach. */
enum class ContactRole { EXECUTOR, LIQUIDATOR, POWER_OF_ATTORNEY, MANDATARY, LAWYER, NOTARY, FINANCIAL_ADVISOR, ACCOUNTANT, INSURANCE_ADVISOR, EMPLOYER, OTHER }

@Serializable
data class EstateContact(
    val role: ContactRole,
    val name: String,
    val organization: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
)

/**
 * EST-02: where one person's papers are and what they want; never the papers' content, and never a
 * password (only where the means to get in is kept). Every field is optional.
 */
@Serializable
data class EstatePlan(
    val willLocation: String? = null,
    val willDate: String? = null,
    /** Power of attorney for property (outside Quebec), or the Quebec mandate's financial part. */
    val powerOfAttorneyLocation: String? = null,
    /** Health care directive or power of attorney for personal care; in Quebec, the mandate in case of incapacity. */
    val mandateLocation: String? = null,
    val safeDepositBox: String? = null,
    val safeDepositKeys: String? = null,
    /** Where a password manager's recovery or a list of online accounts is kept. */
    val digitalAccounts: String? = null,
    val funeralWishes: String? = null,
    val organDonor: Boolean? = null,
    val otherDocuments: String? = null,
    val contacts: List<EstateContact> = emptyList(),
    val notes: String? = null,
)

data class EstateRecord(val id: String, val groupId: String, val memberId: String, val plan: EstatePlan, val updatedAt: Long)

/** EST-01: one account as the summary shows it: masked number, institution, owners, balance. */
data class SummaryAccount(val account: Account, val institution: Institution?, val ownerNames: List<String>, val balance: Money, val beneficiaries: List<Beneficiary>)

data class SummaryPolicy(val policy: InsurancePolicy, val insuredName: String?, val beneficiaries: List<PolicyBeneficiary>)

/**
 * EST-01: the "in case of emergency" summary: each person's papers and contacts, the institutions,
 * accounts (masked), insurance policies, registered plans with their beneficiaries, pensions, and the
 * documents kept for good. Only what the signed-in user can see.
 */
data class EmergencySummary(
    val records: List<EstateRecord>,
    val institutions: List<Institution>,
    val accounts: List<SummaryAccount>,
    val policies: List<SummaryPolicy>,
    val pensions: List<Pension>,
    val keptDocuments: List<VaultDocument>,
)

class EstateService internal constructor(private val books: Books) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun records(): List<EstateRecord> = books.groups().flatMap { g ->
        books.ledger(g).estateQueries.estateRecords().executeAsList().map {
            EstateRecord(it.id, g.id, it.member_id, runCatching { json.decodeFromString(EstatePlan.serializer(), it.plan) }.getOrDefault(EstatePlan()), it.updated_at)
        }
    }

    fun record(memberId: String): EstateRecord? = records().firstOrNull { it.memberId == memberId }

    /** Saves one person's record in [groupId] (a private group keeps it from other household users, HH-11). */
    fun save(memberId: String, groupId: String, plan: EstatePlan): EstateRecord {
        val existing = record(memberId)
        val group = books.group(existing?.groupId ?: groupId).also { books.require(it, PermissionLevel.EDIT) }
        validate(plan.contacts.all { it.name.isNotBlank() }, "error.nameRequired")
        val id = existing?.id ?: Ids.newId()
        books.ledger(group).estateQueries.upsertEstateRecord(id, memberId, json.encodeToString(EstatePlan.serializer(), plan), books.now())
        return EstateRecord(id, group.id, memberId, plan, books.now())
    }

    fun summary(): EmergencySummary {
        val members = books.members.list(includeArchived = true).associate { it.id to it.displayName }
        val institutions = books.institutions.list()
        val byId = institutions.associateBy { it.id }
        val registered = books.plans.registeredAccounts(includeClosed = false).map { it.id }.toSet()
        val accounts = books.accounts.list().map { s ->
            val a = s.account
            SummaryAccount(a, a.institutionId?.let(byId::get), a.ownerMemberIds.mapNotNull(members::get), s.balance, if (a.id in registered) books.plans.beneficiaries(a.id) else emptyList())
        }
        val policies = books.insurance.policies(includeInactive = false).map { p -> SummaryPolicy(p, p.insuredMemberId?.let(members::get), books.insurance.beneficiaries(p.id)) }
        val kept = books.documents.search(DocumentQuery(limit = 1000)).filter { it.keepForever }
        return EmergencySummary(records(), institutions, accounts, policies, books.plans.pensions(), kept)
    }
}
