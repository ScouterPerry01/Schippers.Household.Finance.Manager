package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.schedule.BusinessDays
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdSession
import ca.schippers.hfm.domain.AccessPolicy
import ca.schippers.hfm.domain.AccountGroupAccess
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import kotlinx.datetime.LocalDate

/**
 * The household's books for one signed-in user: the entry point the apps use for everything in
 * Phase 1. Every operation checks the user's permission on the account group concerned
 * (HH-08, HH-10); private groups the user was not granted cannot even be opened (HH-11).
 */
class Books(val session: HouseholdSession, internal val clock: () -> Long = System::currentTimeMillis) {

    internal val core get() = session.core.coreQueries
    val userId: String get() = session.userId
    val role: Role by lazy { session.role }

    val members = MemberService(this)
    val institutions = InstitutionService(this)
    val categories = CategoryService(this)
    val payees = PayeeService(this)
    val accounts = AccountService(this)
    val transactions = TransactionService(this)
    val creditCards = CreditCardService(this)
    val rules = RuleService(this)
    val statements = StatementService(this)
    val bills = BillService(this)
    val rates = RateService(this)
    val reports = ReportService(this)
    val budgets = BudgetService(this)
    val backups = BackupService(this)
    val search = SearchService(this)
    val calendar = CalendarService(this)
    val health = HealthService(this)
    val goals = GoalService(this)
    val pets = PetService(this)
    val vehicles = VehicleService(this)
    val investments = InvestmentService(this)
    val brokerage = BrokerageImportService(this)
    val plans = PlanService(this)
    val prices = PriceService(this)
    val loans = LoanService(this)
    val documents = DocumentService(this)
    val sync = SyncService(this)
    val quicken = QifImportService(this)
    val users = UserService(this)

    /** PROV-01: the household's province or territory, whose rules apply unless a person has their own. */
    val province: Province get() = Province.of(core.household().executeAsOne().province) ?: Province.QC

    /** The province whose rules apply to [memberId]: their own when set, otherwise the household's. */
    fun provinceOf(memberId: String?): Province =
        memberId?.let { id -> members.list(includeArchived = true).firstOrNull { it.id == id }?.province } ?: province

    /** Changes the household's province: bank holidays follow, and its default categories are added. */
    fun setProvince(p: Province) {
        requireAdmin(this)
        core.setHouseholdProvince(p.name)
        BusinessDays.province = p
        categories.addForProvince()
        session.audit("UPDATE", "household", null, "province ${p.name}")
    }

    init {
        BusinessDays.province = province
        categories.ensureDefaults()
    }

    /** Account groups the user can see at all, with their permission level. */
    fun groups(): List<GroupInfo> = core.groups().executeAsList().mapNotNull { row ->
        val grants = core.permissionsForGroup(row.id).executeAsList()
            .associate { it.user_id to PermissionLevel.valueOf(it.level) }
        val level = AccessPolicy.levelFor(userId, role, AccountGroupAccess(row.id, row.owner_user_id, grants))
        if (level == PermissionLevel.NONE || !session.canOpen(row.partition_id)) {
            null
        } else {
            GroupInfo(row.id, row.name, row.partition_id, row.owner_user_id, level)
        }
    }

    internal fun group(groupId: String): GroupInfo =
        groups().firstOrNull { it.id == groupId } ?: throw AccessDeniedException("You do not have access to this account group")

    internal fun require(group: GroupInfo, level: PermissionLevel) {
        if (!group.level.allows(level)) throw AccessDeniedException("You do not have permission to change ${group.name}")
    }

    internal fun ledger(group: GroupInfo) = session.ledger(group.partitionId)

    internal fun now(): Long = clock()

    /** Today in the computer's time zone, from the same clock as [now]. */
    internal fun today(): LocalDate = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    /**
     * Everything that must be renewed within [withinDays] of [today], or is overdue: pet licences and
     * insurance, vehicle papers and warranties, and loan terms (each with its own lead time, LN-04).
     */
    fun renewals(today: LocalDate, withinDays: Int = 30): List<Renewal> =
        (pets.renewals(today, withinDays) + vehicles.renewals(today, withinDays) + loans.renewals(today, withinDays)).sortedBy { it.date }

    /** Tags for projects and events (CAT-04). */
    fun tags(): List<Tag> = core.tags().executeAsList().map { Tag(it.id, it.name) }

    /** Household-wide settings, such as saved CSV column mappings per institution (REC-01). */
    fun setting(key: String): String? = core.getSetting(key).executeAsOneOrNull()

    fun putSetting(key: String, value: String) = core.putSetting(key, value)
}

/**
 * A rule was broken. [key] names a message in `core/i18n` so the apps can show it in the user's
 * language; the exception message itself is the English text, for logs.
 */
class ValidationException(val key: String, vararg val args: Any) :
    IllegalArgumentException(Messages.get(Language.ENGLISH, key, *args)) {
    fun message(language: Language): String = Messages.get(language, key, *args)
}

/** Thrown when a reconciled transaction would change without explicit confirmation (section 8, step 5). */
class ReconciledChangeException : IllegalStateException("This transaction is reconciled; confirm the change first")

internal fun validate(condition: Boolean, key: String, vararg args: Any) {
    if (!condition) throw ValidationException(key, *args)
}
