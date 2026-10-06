package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import ca.schippers.hfm.data.ledger.Home_project_rebate as RebateRow

/** SEA-05: what kind of energy upgrade a home project is. */
enum class EnergyUpgrade { INSULATION, AIR_SEALING, HEAT_PUMP, WINDOWS_DOORS, WATER_HEATER, SOLAR, THERMOSTAT, OTHER }

/** SEA-05: where a rebate or grant application stands. */
enum class RebateStatus { PLANNED, APPLIED, APPROVED, RECEIVED, REFUSED }

/**
 * SEA-05: a rebate or grant applied for on a home project (a utility's heat pump rebate, a
 * government energy grant), in the project's currency: [amount] is what was asked for or
 * expected, [received] what came in. Only a received amount lowers the project's net cost.
 */
data class ProjectRebate(
    val id: String,
    val projectId: String,
    val program: String,
    val status: RebateStatus,
    val reference: String? = null,
    val amount: Money? = null,
    val applied: LocalDate? = null,
    val decided: LocalDate? = null,
    val receivedOn: LocalDate? = null,
    val received: Money? = null,
    val notes: String? = null,
)

/**
 * SEA-05: rebates and grants on home projects, and the papers kept with them (applications,
 * approval letters, the energy advisor's reports), linked in the vault to the rebate or the project.
 */
class ProjectRebateService internal constructor(private val books: Books) {

    fun list(p: HomeProject): List<ProjectRebate> =
        books.ledger(books.group(p.groupId)).projectRebatesQueries.rebates(p.id).executeAsList().map { it.toRebate(p.currency) }

    fun save(p: HomeProject, r: ProjectRebate): ProjectRebate {
        validate(r.program.isNotBlank(), "error.nameRequired")
        validate(listOfNotNull(r.amount, r.received).all { it.currency == p.currency && !it.isNegative }, "error.projectCost")
        validate(r.status != RebateStatus.RECEIVED || r.received != null, "error.rebateReceived")
        validate(r.applied == null || r.receivedOn == null || r.receivedOn >= r.applied, "error.endBeforeStart")
        val group = editable(p.groupId)
        val id = r.id.ifBlank { Ids.newId() }
        books.ledger(group).projectRebatesQueries.upsertRebate(
            id, p.id, r.program.trim(), r.status.name, r.reference.blankToNull(), r.amount?.minorUnits, r.applied?.toString(), r.decided?.toString(),
            r.receivedOn?.toString(), r.received?.minorUnits, r.notes.blankToNull(), books.now(),
        )
        return list(p).first { it.id == id }
    }

    fun delete(p: HomeProject, rebateId: String) = books.ledger(editable(p.groupId)).projectRebatesQueries.deleteRebate(rebateId)

    /** What came in from rebates and grants on the project. */
    fun received(p: HomeProject): Money = list(p).filter { it.status == RebateStatus.RECEIVED }.mapNotNull { it.received }.fold(Money.zero(p.currency)) { a, m -> a + m }

    /** What is still expected: applied for or approved, not yet received. */
    fun pending(p: HomeProject): Money = list(p).filter { it.status == RebateStatus.APPLIED || it.status == RebateStatus.APPROVED }
        .mapNotNull { it.amount }.fold(Money.zero(p.currency)) { a, m -> a + m }

    /** What the project cost once its rebates and grants came in. */
    fun netCost(p: HomeProject): Money = p.spent - received(p)

    // --- Papers ---------------------------------------------------------------------------------

    /** The papers kept with the project itself (the contract, the energy audits). */
    fun projectDocuments(p: HomeProject): List<VaultDocument> = books.documents.documentsFor(PROJECT, p.id)

    fun attachToProject(p: HomeProject, documentId: String) = books.documents.link(documentId, PROJECT, p.id)

    fun detachFromProject(p: HomeProject, documentId: String) = books.documents.unlink(documentId, PROJECT, p.id)

    /** The papers of one rebate (the application, the approval letter). */
    fun documents(rebateId: String): List<VaultDocument> = books.documents.documentsFor(REBATE, rebateId)

    fun attach(rebateId: String, documentId: String) = books.documents.link(documentId, REBATE, rebateId)

    fun detach(rebateId: String, documentId: String) = books.documents.unlink(documentId, REBATE, rebateId)

    private fun editable(groupId: String): GroupInfo = books.group(groupId).also { books.require(it, PermissionLevel.EDIT) }

    private fun RebateRow.toRebate(c: Currency) = ProjectRebate(
        id, project_id, program, RebateStatus.valueOf(status), reference, amount_minor?.let { Money.ofMinor(it, c) }, applied_date?.let(LocalDate::parse),
        decision_date?.let(LocalDate::parse), received_date?.let(LocalDate::parse), received_minor?.let { Money.ofMinor(it, c) }, notes,
    )

    companion object {
        /** How the vault names what a document is linked to. */
        const val PROJECT = "project"
        const val REBATE = "rebate"
    }
}
