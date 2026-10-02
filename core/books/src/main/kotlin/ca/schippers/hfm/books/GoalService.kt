package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ca.schippers.hfm.data.ledger.Goal_entry as EntryRow
import ca.schippers.hfm.data.ledger.Savings_goal as GoalRow

enum class GoalStatus { ACTIVE, REACHED, ARCHIVED }
enum class GoalEntryKind { SET_ASIDE, SCHEDULED, SPENT, MOVE_IN, MOVE_OUT, RELEASED }

/**
 * GOAL-01, GOAL-02: part of an account's balance earmarked for a purpose. An empty [id] means a
 * new goal. Amounts are in the account's currency.
 */
data class SavingsGoal(
    val id: String,
    val accountId: String,
    val name: String,
    val target: Money,
    val targetDate: LocalDate? = null,
    /** The planned set-aside each period, on [recurrence] from [scheduleStart]; null when set aside by hand. */
    val contribution: Money? = null,
    val recurrence: Recurrence? = null,
    val scheduleStart: LocalDate? = null,
    val autoPost: Boolean = true,
    val lastPosted: LocalDate? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val sortOrder: Int = 0,
    val notes: String? = null,
)

data class GoalEntry(val id: String, val goalId: String, val date: LocalDate, val kind: GoalEntryKind, val amount: Money, val transactionId: String?, val memo: String?)

/** GOAL-04: how far a goal has come and whether it will be reached in time. */
data class GoalProgress(
    val goal: SavingsGoal,
    val saved: Money,
    val remaining: Money,
    /** 0 to 100. */
    val percent: Int,
    /** The set-aside per period (or per month without a schedule) that reaches the target on its date. */
    val neededPerPeriod: Money?,
    /** When the planned set-asides reach the target; null without a plan or beyond 50 years. */
    val projectedDate: LocalDate?,
    /** Null when there is no target date or no plan to compare with. */
    val onTrack: Boolean?,
) {
    val reached: Boolean get() = !remaining.isPositive
}

/** GOAL-03: an account's balance divided into its goals and the unassigned rest. */
data class AccountGoals(val account: Account, val balance: Money, val goals: List<GoalProgress>, val earmarked: Money, val unassigned: Money) {
    val overCommitted: Boolean get() = unassigned.isNegative
}

/**
 * Savings goals and sinking funds kept inside ordinary accounts (GOAL-01 to GOAL-06). Goals live
 * in the ledger of their account's group, so they have the same privacy as the account.
 */
class GoalService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<SavingsGoal> = books.groups().flatMap { g ->
        books.ledger(g).goalsQueries.goals().executeAsList().map { it.toGoal() }
    }.filter { includeArchived || it.status != GoalStatus.ARCHIVED }

    fun get(goalId: String): SavingsGoal = locate(goalId).second

    fun save(goal: SavingsGoal): SavingsGoal {
        val (group, account) = books.accounts.locate(goal.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(goal.name.isNotBlank(), "error.nameRequired")
        validate(goal.target.currency == account.currency, "error.currencyMismatch", account.currency.code)
        validate(goal.target.isPositive, "error.goalTarget")
        validate(goal.contribution == null || (goal.contribution.isPositive && goal.contribution.currency == account.currency), "error.goalContribution")
        validate((goal.contribution == null) == (goal.recurrence == null) && (goal.recurrence == null || goal.scheduleStart != null), "error.goalSchedule")
        val q = books.ledger(group).goalsQueries
        val now = books.now()
        val start = goal.scheduleStart.takeIf { goal.recurrence != null }
        with(goal) {
            if (id.isBlank()) {
                val newId = Ids.newId()
                q.insertGoal(
                    newId, accountId, name.trim(), target.minorUnits, targetDate?.toString(), contribution?.minorUnits, recurrence?.encode(),
                    start?.toString(), if (autoPost) 1 else 0, lastPosted?.toString(), status.name, sortOrder.toLong(), notes?.ifBlank { null }, now, now,
                )
                return get(newId)
            }
            val existing = get(id)
            validate(existing.accountId == accountId, "error.cannotChangeAccount")
            // A changed schedule starts afresh from its start date; earlier set-asides stay.
            val posted = if (existing.recurrence == recurrence && existing.scheduleStart == start) lastPosted else start?.let { LocalDate.fromEpochDays(it.toEpochDays() - 1) }
            q.updateGoal(
                name.trim(), target.minorUnits, targetDate?.toString(), contribution?.minorUnits, recurrence?.encode(), start?.toString(),
                if (autoPost) 1 else 0, posted?.toString(), status.name, sortOrder.toLong(), notes?.ifBlank { null }, now, id,
            )
            return get(id)
        }
    }

    fun delete(goalId: String) {
        val (group, _) = locate(goalId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).goalsQueries.deleteGoal(goalId)
    }

    // --- Entries (GOAL-02, GOAL-05, GOAL-06) --------------------------------------------------

    fun entries(goalId: String): List<GoalEntry> {
        val (group, goal) = locate(goalId)
        return books.ledger(group).goalsQueries.entriesFor(goalId).executeAsList().map { it.toEntry(goal) }
    }

    /** Earmarks [amount] more for the goal. */
    fun setAside(goalId: String, date: LocalDate, amount: Money, memo: String? = null): GoalEntry {
        validate(amount.isPositive, "error.amountPositive")
        return add(goalId, date, GoalEntryKind.SET_ASIDE, amount, null, memo)
    }

    /** Takes [amount] back out of the goal (for something else, or a correction). */
    fun release(goalId: String, date: LocalDate, amount: Money, memo: String? = null): GoalEntry {
        validate(amount.isPositive, "error.amountPositive")
        return add(goalId, date, GoalEntryKind.RELEASED, -amount, null, memo)
    }

    /** GOAL-05: the goal's money was spent on its purpose, optionally on [transactionId]. */
    fun spend(goalId: String, date: LocalDate, amount: Money, transactionId: String? = null, memo: String? = null): GoalEntry {
        validate(amount.isPositive, "error.amountPositive")
        return add(goalId, date, GoalEntryKind.SPENT, -amount, transactionId, memo)
    }

    /** Moves an earmark between two goals of the same account. */
    fun move(fromGoalId: String, toGoalId: String, date: LocalDate, amount: Money, memo: String? = null) {
        val (group, from) = locate(fromGoalId)
        val to = get(toGoalId)
        validate(from.accountId == to.accountId && fromGoalId != toGoalId, "error.goalMoveAccount")
        validate(amount.isPositive, "error.amountPositive")
        books.ledger(group).transaction {
            add(fromGoalId, date, GoalEntryKind.MOVE_OUT, -amount, null, memo)
            add(toGoalId, date, GoalEntryKind.MOVE_IN, amount, null, memo)
        }
    }

    fun deleteEntry(goalId: String, entryId: String) {
        val (group, _) = locate(goalId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).goalsQueries.deleteGoalEntry(entryId)
    }

    /**
     * GOAL-02: enters the scheduled set-asides due up to [today] that are not entered yet. A goal
     * stops receiving them once its target is reached. Returns how many were entered.
     */
    fun postScheduled(today: LocalDate): Int {
        var count = 0
        for (group in books.groups().filter { it.level.allows(PermissionLevel.EDIT) }) {
            val q = books.ledger(group).goalsQueries
            val balances = q.goalBalances().executeAsList().associate { it.goal_id to (it.total ?: 0L) }
            for (goal in q.goals().executeAsList().map { it.toGoal() }) {
                if (goal.status != GoalStatus.ACTIVE || !goal.autoPost) continue
                val rule = goal.recurrence ?: continue
                val per = goal.contribution ?: continue
                val start = goal.scheduleStart ?: continue
                val from = goal.lastPosted?.plus(DatePeriod(days = 1)) ?: start
                if (from > today) continue
                var saved = balances[goal.id] ?: 0L
                val dates = rule.occurrences(start, from, today)
                books.ledger(group).transaction {
                    for (date in dates) {
                        val left = goal.target.minorUnits - saved
                        if (left <= 0) break
                        val amount = minOf(per.minorUnits, left)
                        q.insertGoalEntry(Ids.newId(), goal.id, date.toString(), GoalEntryKind.SCHEDULED.name, amount, null, null, books.now())
                        saved += amount
                        count++
                    }
                    q.setLastPosted(today.toString(), goal.id)
                }
            }
        }
        return count
    }

    // --- Progress (GOAL-03, GOAL-04) ----------------------------------------------------------

    fun progress(goal: SavingsGoal, today: LocalDate): GoalProgress {
        val (group, _) = locate(goal.id)
        val saved = books.ledger(group).goalsQueries.goalBalances().executeAsList().firstOrNull { it.goal_id == goal.id }?.total ?: 0L
        return progress(goal, Money.ofMinor(saved, goal.target.currency), today)
    }

    fun forAccount(accountId: String, today: LocalDate): AccountGoals {
        val (group, account) = books.accounts.locate(accountId)
        val q = books.ledger(group).goalsQueries
        val saved = q.goalBalances().executeAsList().associate { it.goal_id to (it.total ?: 0L) }
        val goals = q.goals().executeAsList().map { it.toGoal() }
            .filter { it.accountId == accountId && it.status != GoalStatus.ARCHIVED }
            .map { progress(it, Money.ofMinor(saved[it.id] ?: 0L, account.currency), today) }
        val balance = Money.ofMinor(books.ledger(group).ledgerQueries.accountBalance(accountId).executeAsOne(), account.currency)
        val earmarked = Money.ofMinor(goals.sumOf { it.saved.minorUnits.coerceAtLeast(0) }, account.currency)
        return AccountGoals(account, balance, goals, earmarked, balance - earmarked)
    }

    /** Every account that has goals, for the Goals screen. */
    fun accounts(today: LocalDate): List<AccountGoals> = list().map { it.accountId }.distinct().map { forAccount(it, today) }

    private fun progress(goal: SavingsGoal, saved: Money, today: LocalDate): GoalProgress {
        val currency = goal.target.currency
        val remaining = (goal.target - saved).let { if (it.isNegative) Money.zero(currency) else it }
        val percent = if (goal.target.isPositive) (saved.minorUnits * 100 / goal.target.minorUnits).toInt().coerceIn(0, 100) else 100
        val rule = goal.recurrence
        val nextFrom = maxOf(today.plus(DatePeriod(days = 1)), goal.lastPosted?.plus(DatePeriod(days = 1)) ?: goal.scheduleStart ?: today)

        // Periods left until the target date: scheduled dates, or months without a schedule.
        val needed = goal.targetDate?.takeIf { remaining.isPositive }?.let { end ->
            val periods = if (rule != null && goal.scheduleStart != null) {
                rule.occurrences(goal.scheduleStart, nextFrom, end).size
            } else {
                monthsBetween(today, end)
            }
            if (periods <= 0) remaining else Money.ofMinor(ceilDiv(remaining.minorUnits, periods.toLong()), currency)
        }

        var projected: LocalDate? = null
        if (!remaining.isPositive) {
            projected = today
        } else if (rule != null && goal.contribution != null && goal.scheduleStart != null && goal.status == GoalStatus.ACTIVE) {
            val count = ceilDiv(remaining.minorUnits, goal.contribution.minorUnits).toInt()
            projected = rule.occurrences(goal.scheduleStart, nextFrom, today.plus(DatePeriod(years = 50)), limit = count).takeIf { it.size == count }?.last()
        }
        val onTrack = when {
            !remaining.isPositive -> true
            goal.targetDate == null || goal.contribution == null -> null
            else -> projected != null && projected <= goal.targetDate
        }
        return GoalProgress(goal, saved, remaining, percent, needed, projected, onTrack)
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun add(goalId: String, date: LocalDate, kind: GoalEntryKind, amount: Money, transactionId: String?, memo: String?): GoalEntry {
        val (group, goal) = locate(goalId)
        books.require(group, PermissionLevel.EDIT)
        validate(amount.currency == goal.target.currency, "error.currencyMismatch", goal.target.currency.code)
        val id = Ids.newId()
        books.ledger(group).goalsQueries.insertGoalEntry(id, goalId, date.toString(), kind.name, amount.minorUnits, transactionId, memo?.ifBlank { null }, books.now())
        return GoalEntry(id, goalId, date, kind, amount, transactionId, memo?.ifBlank { null })
    }

    private fun locate(goalId: String): Pair<GroupInfo, SavingsGoal> {
        for (group in books.groups()) {
            val row = books.ledger(group).goalsQueries.goalById(goalId).executeAsOneOrNull() ?: continue
            return group to row.toGoal()
        }
        throw AccessDeniedException("Goal not found or not accessible")
    }

    private fun GoalRow.toGoal(): SavingsGoal {
        val currency = books.accounts.get(account_id).currency
        return SavingsGoal(
            id, account_id, name, Money.ofMinor(target_minor, currency), target_date?.let(LocalDate::parse),
            contribution_minor?.let { Money.ofMinor(it, currency) }, recurrence?.let(Recurrence::decode), schedule_start?.let(LocalDate::parse),
            auto_post == 1L, last_posted?.let(LocalDate::parse), GoalStatus.valueOf(status), sort_order.toInt(), notes,
        )
    }

    private fun EntryRow.toEntry(goal: SavingsGoal) =
        GoalEntry(id, goal_id, LocalDate.parse(date), GoalEntryKind.valueOf(kind), Money.ofMinor(amount_minor, goal.target.currency), txn_id, memo)

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

    private fun monthsBetween(from: LocalDate, to: LocalDate): Int =
        ((to.year - from.year) * 12 + (to.month.ordinal - from.month.ordinal)).coerceAtLeast(0)

}
