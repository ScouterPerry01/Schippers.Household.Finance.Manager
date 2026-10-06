package ca.schippers.hfm.books

import ca.schippers.hfm.calc.rules.Thresholds
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.ledger.LedgerDatabase
import ca.schippers.hfm.data.ledger.LedgerQueries
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.security.MessageDigest
import kotlin.math.abs
import ca.schippers.hfm.data.ledger.Statement as StatementRow
import ca.schippers.hfm.data.ledger.Statement_line as LineRow
import ca.schippers.hfm.data.ledger.Txn as TxnRow

enum class StatementStatus { OPEN, RECONCILED, UNDONE }

/** What happened to one statement line (section 8, steps 2 and 3). */
enum class LineStatus {
    /** Linked to a transaction that was already recorded (for example a phone capture). */
    MATCHED,

    /** A new transaction was added for it. */
    CREATED,

    /** A likely match that needs the user's confirmation. */
    PROPOSED,

    /** Already imported from an earlier statement (REC-10). */
    DUPLICATE,

    /** Needs a decision: link it, or create a transaction. */
    UNMATCHED,

    /** Deliberately left out. */
    IGNORED,
}

data class Statement(
    val id: String,
    val accountId: String,
    val sourceName: String?,
    val format: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate,
    val openingBalance: Money?,
    val closingBalance: Money?,
    val status: StatementStatus,
    val reconciledAt: Long?,
    val undoReason: String?,
)

data class StatementLine(
    val id: String,
    val lineNo: Int,
    val date: LocalDate,
    val amount: Money,
    val payee: String?,
    val memo: String?,
    val checkNumber: String?,
    val status: LineStatus,
    val transactionId: String?,
    /** REC-03: the group match this line is part of, if any. */
    val matchGroup: String? = null,
)

/**
 * REC-03: statement lines and recorded transactions matched together because their amounts add
 * up: one line for several transactions (a deposit of several cheques), or several lines for one
 * transaction (a purchase charged in two parts). [confirmed] is false while it is only proposed.
 */
data class MatchGroup(val id: String, val lines: List<StatementLine>, val transactionIds: List<String>, val confirmed: Boolean) {
    val total: Money get() = lines.map { it.amount }.reduce(Money::plus)
}

data class ImportResult(
    val statementId: String,
    val created: Int,
    val matched: Int,
    val proposed: Int,
    val duplicates: Int,
    /** REC-03: group matches proposed (several lines or several transactions adding up). */
    val groups: Int = 0,
)

/** Everything the reconciliation screen shows (section 8, steps 3 and 4). */
data class ReconciliationView(
    val statement: Statement,
    val lines: List<StatementLine>,
    /** Recorded transactions up to the statement date that are not cleared: outstanding, or in error. */
    val outstanding: List<Transaction>,
    /** Cleared by hand but not linked to a statement line, e.g. ticked off a paper statement. */
    val clearedByHand: List<Transaction>,
    /** Opening balance plus every cleared or reconciled transaction. */
    val clearedBalance: Money,
    /** Statement closing balance minus the cleared balance; must be zero to finish (REC-05). Null until the closing balance is known. */
    val difference: Money?,
    /** REC-03: group matches, proposed or confirmed. */
    val groups: List<MatchGroup> = emptyList(),
) {
    val unresolved: List<StatementLine> get() = lines.filter { it.status == LineStatus.PROPOSED || it.status == LineStatus.UNMATCHED }
    val canFinish: Boolean get() = statement.status == StatementStatus.OPEN && difference?.isZero == true && unresolved.isEmpty()
}

/** Saved with the statement when reconciliation is finished (REC-06). */
@Serializable
data class ReconciliationReport(
    val periodEnd: String,
    val closingBalanceMinor: Long,
    val currency: String,
    val cleared: List<ReportItem>,
    val outstanding: List<ReportItem>,
    /** REC-03: the group matches, each with its statement lines and the transactions they cover. */
    val groups: List<ReportGroup> = emptyList(),
)

@Serializable
data class ReportItem(val transactionId: String, val date: String, val payee: String? = null, val amountMinor: Long)

/** REC-03: in the report, statement lines matched together with transactions whose amounts add up. */
@Serializable
data class ReportGroup(val lines: List<ReportLine>, val transactions: List<ReportItem>)

@Serializable
data class ReportLine(val date: String, val payee: String? = null, val amountMinor: Long)

data class ImportSettings(
    /** Lines match recorded transactions with the same amount within this many days (REC-02). */
    val dateToleranceDays: Int = 5,
    /** Matches closer than this are accepted without asking. */
    val confidentDays: Int = 3,
    /**
     * REC-04: a purchase recorded in a foreign currency matches a line within this percentage of
     * its amount (a 2.5 % conversion fee and the day's spread; Rates and rules, 3.5 % built in); it
     * is always proposed, not linked.
     */
    val fxTolerancePercent: BigDecimal = Thresholds.statementFxPercent(),
)

/**
 * Statement import and reconciliation (section 8, REC-01 to REC-10).
 *
 * Importing records the statement and its lines, skips lines already imported, links lines to
 * transactions already recorded, and adds the rest as new cleared transactions, categorized by
 * rules (CAT-02). Reconciliation then compares the statement's closing balance with the cleared
 * balance; when they agree to the cent the period is locked.
 */
class StatementService internal constructor(private val books: Books) {

    private val json = Json { ignoreUnknownKeys = true }

    // --- Import ---------------------------------------------------------------------------------

    fun import(
        accountId: String,
        imported: ImportedStatement,
        sourceName: String? = null,
        fileBytes: ByteArray? = null,
        settings: ImportSettings = ImportSettings(),
    ): ImportResult {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(imported.currency == account.currency, "error.importCurrency", imported.currency.code, account.currency.code)
        validate(imported.lines.isNotEmpty() || imported.closingBalance != null, "error.importEmpty")
        val ledger = books.ledger(group)
        val q = ledger.ledgerQueries
        val fileHash = fileBytes?.let(::sha256)
        if (fileHash != null) {
            validate(q.statementByFile(accountId, fileHash).executeAsOneOrNull() == null, "error.statementAlreadyImported")
        }

        val statementId = Ids.newId()
        val periodEnd = imported.periodEnd ?: imported.lines.maxOfOrNull { it.date } ?: today()
        val counts = IntArray(4) // created, matched, proposed, duplicates
        var groups = 0
        ledger.transaction {
            q.insertStatement(
                statementId, accountId, sourceName, imported.format, fileHash, imported.periodStart?.toString(), periodEnd.toString(),
                imported.openingBalance?.minorUnits, imported.closingBalance?.minorUnits, books.now(), books.userId,
            )
            val used = HashSet<String>()
            val occurrences = HashMap<String, Int>()
            // Lines that match nothing one to one wait for the group matches (REC-03) before a new
            // transaction is added for them.
            val pending = ArrayList<PendingLine>()
            imported.lines.forEachIndexed { index, line ->
                val externalId = line.externalId ?: fingerprint(line.date, line.amount, line.payee, occurrences)
                val lineId = Ids.newId()
                fun record(status: LineStatus, txnId: String?) = q.insertStatementLine(
                    lineId, statementId, index.toLong(), externalId, line.date.toString(), line.amount.minorUnits,
                    line.payee, line.memo, line.checkNumber, status.name, txnId,
                )

                val existing = q.txnByExternalId(accountId, externalId).executeAsOneOrNull()?.id
                    ?: ledger.matchingQueries.groupedLineByExternalId(accountId, externalId).executeAsOneOrNull()
                if (existing != null) {
                    record(LineStatus.DUPLICATE, existing)
                    counts[3]++
                    return@forEachIndexed
                }
                val candidate = bestCandidate(q, accountId, line.date, line.amount, line.payee, settings, used)
                    ?: fxCandidate(q, accountId, line.date, line.amount, settings, used)?.let { it to false }
                when {
                    candidate != null && candidate.second -> {
                        used += candidate.first.id
                        linkTransaction(ledger, candidate.first.id, externalId)
                        record(LineStatus.MATCHED, candidate.first.id)
                        counts[1]++
                    }
                    candidate != null -> {
                        used += candidate.first.id
                        record(LineStatus.PROPOSED, candidate.first.id)
                        counts[2]++
                    }
                    else -> pending += PendingLine(lineId, index, externalId, line.date, line.amount, line.payee, line.memo, line.checkNumber)
                }
            }
            // REC-03: lines and recorded transactions whose amounts add up are proposed together,
            // never linked without asking.
            val grouped = HashSet<String>()
            for ((lines, txnIds) in proposeGroups(ledger, accountId, pending, used, settings)) {
                val groupId = Ids.newId()
                lines.forEach { p ->
                    q.insertStatementLine(p.id, statementId, p.index.toLong(), p.externalId, p.date.toString(), p.amount.minorUnits, p.payee, p.memo, p.checkNumber, LineStatus.PROPOSED.name, null)
                    ledger.matchingQueries.setLineGroup(groupId, LineStatus.PROPOSED.name, null, p.id)
                    grouped += p.id
                }
                txnIds.forEach { ledger.matchingQueries.insertMatch(groupId, statementId, it) }
                counts[2] += lines.size
                groups++
            }
            pending.filter { it.id !in grouped }.forEach { p ->
                val txn = createFromLine(account, p.date, p.amount, p.payee, p.memo, p.externalId)
                q.insertStatementLine(p.id, statementId, p.index.toLong(), p.externalId, p.date.toString(), p.amount.minorUnits, p.payee, p.memo, p.checkNumber, LineStatus.CREATED.name, txn.id)
                counts[0]++
            }
        }
        books.session.audit("IMPORT", "statement", statementId, "${imported.format}: ${imported.lines.size} lines")
        return ImportResult(statementId, counts[0], counts[1], counts[2], counts[3], groups)
    }

    /** A statement line not matched one to one during an import, waiting for a group match or a new transaction. */
    private class PendingLine(
        val id: String, val index: Int, val externalId: String, val date: LocalDate, val amount: Money,
        val payee: String?, val memo: String?, val checkNumber: String?,
    )

    /**
     * REC-03: group matches among the [pending] lines and the recorded transactions not yet used:
     * first one line for several transactions, then several lines for one transaction. Amounts
     * must add up to the cent, every transaction and line must fall within the date tolerance of
     * the other side, and all must go the same way (money in or money out). Up to [MAX_GROUP] items
     * on the many side, among the [MAX_GROUP_CANDIDATES] closest in date.
     */
    private fun proposeGroups(
        ledger: LedgerDatabase,
        accountId: String,
        pending: List<PendingLine>,
        used: MutableSet<String>,
        settings: ImportSettings,
    ): List<Pair<List<PendingLine>, List<String>>> {
        val out = ArrayList<Pair<List<PendingLine>, List<String>>>()
        val taken = HashSet<String>()
        fun window(date: LocalDate) =
            LocalDate.fromEpochDays(date.toEpochDays() - settings.dateToleranceDays).toString() to LocalDate.fromEpochDays(date.toEpochDays() + settings.dateToleranceDays).toString()
        // One line, several transactions.
        for (p in pending) {
            val (from, to) = window(p.date)
            val candidates = ledger.matchingQueries.groupCandidates(accountId, from, to).executeAsList()
                .filter { it.id !in used && sameWay(it.amount_minor, p.amount.minorUnits) && abs(it.amount_minor) < abs(p.amount.minorUnits) }
                .sortedBy { abs(LocalDate.parse(it.date).daysUntil(p.date)) }
                .take(MAX_GROUP_CANDIDATES)
            val subset = subsetSum(candidates.map { it.amount_minor }, p.amount.minorUnits) ?: continue
            val ids = subset.map { candidates[it].id }
            used += ids
            taken += p.id
            out += listOf(p) to ids
        }
        // Several lines, one transaction.
        val left = pending.filter { it.id !in taken }
        if (left.size >= 2) {
            val from = LocalDate.fromEpochDays(left.minOf { it.date }.toEpochDays() - settings.dateToleranceDays).toString()
            val to = LocalDate.fromEpochDays(left.maxOf { it.date }.toEpochDays() + settings.dateToleranceDays).toString()
            for (t in ledger.matchingQueries.groupCandidates(accountId, from, to).executeAsList()) {
                if (t.id in used) continue
                val date = LocalDate.parse(t.date)
                val lines = left.filter { it.id !in taken && sameWay(it.amount.minorUnits, t.amount_minor) && abs(it.amount.minorUnits) < abs(t.amount_minor) && abs(it.date.daysUntil(date)) <= settings.dateToleranceDays }
                    .sortedBy { abs(it.date.daysUntil(date)) }
                    .take(MAX_GROUP_CANDIDATES)
                val subset = subsetSum(lines.map { it.amount.minorUnits }, t.amount_minor) ?: continue
                val chosen = subset.map { lines[it] }.sortedBy { it.index }
                used += t.id
                taken += chosen.map { it.id }
                out += chosen to listOf(t.id)
            }
        }
        return out
    }

    private fun sameWay(a: Long, b: Long) = a != 0L && (a > 0) == (b > 0)

    /** The smallest set of 2 to [MAX_GROUP] of [amounts] (by index) adding up to [target], or null. */
    private fun subsetSum(amounts: List<Long>, target: Long): List<Int>? {
        for (size in 2..minOf(MAX_GROUP, amounts.size)) {
            val chosen = IntArray(size)
            fun search(start: Int, depth: Int, sum: Long): Boolean {
                if (depth == size) return sum == target
                for (i in start until amounts.size) {
                    chosen[depth] = i
                    if (search(i + 1, depth + 1, sum + amounts[i])) return true
                }
                return false
            }
            if (search(0, 0, 0L)) return chosen.toList()
        }
        return null
    }

    /**
     * The closest recorded transaction with the same amount within the tolerance. The flag says
     * whether it is confident enough to link without asking: close in date, and the only
     * candidate or a similar payee.
     */
    private fun bestCandidate(
        q: LedgerQueries,
        accountId: String,
        date: LocalDate,
        amount: Money,
        payee: String?,
        settings: ImportSettings,
        used: Set<String>,
    ): Pair<TxnRow, Boolean>? {
        val from = LocalDate.fromEpochDays(date.toEpochDays() - settings.dateToleranceDays)
        val to = LocalDate.fromEpochDays(date.toEpochDays() + settings.dateToleranceDays)
        val candidates = q.matchCandidates(accountId, amount.minorUnits, from.toString(), to.toString()).executeAsList()
            .filter { it.id !in used }
            .sortedBy { abs(LocalDate.parse(it.date).daysUntil(date)) }
        val best = candidates.firstOrNull() ?: return null
        val days = abs(LocalDate.parse(best.date).daysUntil(date))
        val similar = payee != null && similarPayee(payee, best.payee_text ?: best.payee_id?.let { id -> books.payees.list(true).firstOrNull { it.id == id }?.name })
        val confident = days <= settings.confidentDays && (candidates.size == 1 || similar)
        return best to confident
    }

    /**
     * REC-04: the purchase in a foreign currency closest in amount to the line, within the
     * tolerance and the date window, with the same sign.
     */
    private fun fxCandidate(q: LedgerQueries, accountId: String, date: LocalDate, amount: Money, settings: ImportSettings, used: Set<String>): TxnRow? {
        val from = LocalDate.fromEpochDays(date.toEpochDays() - settings.dateToleranceDays)
        val to = LocalDate.fromEpochDays(date.toEpochDays() + settings.dateToleranceDays)
        return q.fxCandidates(accountId, from.toString(), to.toString()).executeAsList()
            .filter { it.id !in used && withinFx(it.amount_minor, amount.minorUnits, settings) }
            .minByOrNull { abs(it.amount_minor - amount.minorUnits) }
            ?.let { q.txnById(it.id).executeAsOne() }
    }

    /** REC-04: whether [t], a purchase in a foreign currency, can be linked to a statement line of [amount]. */
    fun isFxMatch(t: Transaction, amount: Money): Boolean =
        t.originalAmount != null && t.transfer == null && t.amount.currency == amount.currency && withinFx(t.amount.minorUnits, amount.minorUnits, ImportSettings())

    private fun withinFx(recorded: Long, statement: Long, settings: ImportSettings): Boolean {
        if (recorded == 0L || (recorded > 0) != (statement > 0)) return false
        val allowed = BigDecimal(abs(recorded)).multiply(settings.fxTolerancePercent).divide(BigDecimal(100))
        return BigDecimal(abs(statement - recorded)) <= allowed
    }

    /**
     * REC-04: the statement gives what a foreign purchase really cost. The transaction takes the
     * statement's amount and the difference goes to the foreign exchange fee category; the rate
     * recorded stays as entered.
     */
    private fun postFxFee(txnId: String, statementAmount: Money) {
        val t = books.transactions.get(txnId)
        val difference = statementAmount - t.amount
        if (difference.isZero) return
        validate(t.originalAmount != null && t.transfer == null, "error.linkAmount")
        val fee = books.categories.list(includeArchived = true).firstOrNull { it.systemKey == "financial.fx_fees" }?.id
        val splits = t.splits.map { SplitDraft(it.categoryId, it.amount, it.memo, it.memberId, it.taxFlag) }
        val existing = splits.indexOfFirst { it.categoryId == fee && fee != null }
        val adjusted = if (existing >= 0) {
            splits.mapIndexed { i, s -> if (i == existing) s.copy(amount = s.amount + difference) else s }
        } else {
            splits + SplitDraft(fee, difference, books.text("generated.fxFee"))
        }
        val payee = t.payeeId?.let { id -> books.payees.list(true).firstOrNull { it.id == id }?.name } ?: t.payeeText
        // The fee line is not a change of category: a category still to review stays so (CAT-03).
        val (group, _) = books.accounts.locate(t.accountId)
        val suggested = books.ledger(group).suggestedCategoriesQueries.suggestionFor(txnId).executeAsOneOrNull()
        books.transactions.update(
            txnId,
            TransactionDraft(t.accountId, t.date, statementAmount, payee, adjusted, t.memo, t.memberId, t.cleared, t.originalAmount, t.fxRate, t.tagIds, t.assetId, t.cardHolderId),
        )
        suggested?.let { books.ledger(group).suggestedCategoriesQueries.markSuggested(it.txn_id, it.category_id, it.created_at) }
        // The audit log is in core.db, which every household user can read: no amounts (HH-11).
        books.session.audit("UPDATE", "txn", txnId, "fx fee")
    }

    private fun linkTransaction(ledger: LedgerDatabase, txnId: String, externalId: String) {
        ledger.ledgerQueries.setExternalId(externalId, books.now(), txnId)
        val txn = ledger.ledgerQueries.txnById(txnId).executeAsOne()
        if (txn.cleared == ClearedStatus.UNCLEARED.name) books.transactions.setCleared(txnId, ClearedStatus.CLEARED)
    }

    /**
     * A new cleared transaction for a statement line, categorized by rule, then by the payee's
     * habits. A category from the payee's habits is marked as suggested, to review (CAT-03); one
     * from a rule is not.
     */
    private fun createFromLine(account: Account, date: LocalDate, amount: Money, payeeText: String?, memo: String?, externalId: String): Transaction {
        val rule = payeeText?.let { books.rules.find(it, amount) }
        val payeeName = rule?.payeeId?.let { id -> books.payees.list(true).firstOrNull { it.id == id }?.name }
            ?: payeeText?.let { books.payees.match(it)?.name ?: PayeeService.cleanName(it) }
        val habit = if (rule?.categoryId != null) {
            null
        } else {
            payeeName?.let { books.payees.match(it)?.defaultCategoryId }
                ?: payeeName?.let { books.transactions.suggest(account.id, it)?.splits?.singleOrNull()?.categoryId }
        }
        val categoryId = rule?.categoryId ?: habit
        val txn = books.transactions.create(
            TransactionDraft(
                account.id, date, amount, payeeName,
                listOfNotNull(categoryId?.let { SplitDraft(it, amount) }),
                memo = memo, cleared = ClearedStatus.CLEARED,
            ),
        )
        val (group, _) = books.accounts.locate(account.id)
        val ledger = books.ledger(group)
        ledger.ledgerQueries.setExternalId(externalId, books.now(), txn.id)
        if (habit != null) ledger.suggestedCategoriesQueries.markSuggested(txn.id, habit, books.now())
        return txn
    }

    // --- Reconciliation -------------------------------------------------------------------------

    fun statements(accountId: String): List<Statement> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).ledgerQueries.statementsForAccount(accountId).executeAsList().map { it.toStatement(account) }
    }

    /** A statement typed in by hand, for reconciling against a paper statement (step 1). */
    fun createManual(accountId: String, periodEnd: LocalDate, closingBalance: Money, periodStart: LocalDate? = null): Statement {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(closingBalance.currency == account.currency, "error.currencyMismatch", account.currency.code)
        val id = Ids.newId()
        books.ledger(group).ledgerQueries.insertStatement(
            id, accountId, null, "MANUAL", null, periodStart?.toString(), periodEnd.toString(), null, closingBalance.minorUnits, books.now(), books.userId,
        )
        return statement(id)
    }

    fun statement(statementId: String): Statement {
        val (_, row) = locateStatement(statementId)
        return row.toStatement(books.accounts.get(row.account_id))
    }

    /** The user enters or corrects the statement's period and balances (step 1). */
    fun updateBalances(statementId: String, periodStart: LocalDate?, periodEnd: LocalDate, opening: Money?, closing: Money?) {
        val (group, row) = locateOpen(statementId)
        val account = books.accounts.get(row.account_id)
        listOfNotNull(opening, closing).forEach { validate(it.currency == account.currency, "error.currencyMismatch", account.currency.code) }
        books.ledger(group).ledgerQueries.updateStatementBalances(periodStart?.toString(), periodEnd.toString(), opening?.minorUnits, closing?.minorUnits, statementId)
    }

    fun view(statementId: String): ReconciliationView {
        val (group, row) = locateStatement(statementId)
        val account = books.accounts.get(row.account_id)
        val q = books.ledger(group).ledgerQueries
        val statement = row.toStatement(account)
        val lines = q.statementLines(statementId).executeAsList().map { it.toLine(account) }
        val groups = groupsOf(books.ledger(group), statementId, lines)
        val linked = lines.mapNotNullTo(HashSet()) { it.transactionId }
        groups.forEach { linked += it.transactionIds }
        val unreconciled = q.unreconciledThrough(row.account_id, statement.periodEnd.toString()).executeAsList().filter { it.id !in linked }
        val outstanding = unreconciled.filter { it.cleared == ClearedStatus.UNCLEARED.name }.map { books.transactions.get(it.id) }
        val clearedByHand = unreconciled.filter { it.cleared == ClearedStatus.CLEARED.name }.map { books.transactions.get(it.id) }
        val cleared = Money.ofMinor(q.clearedBalance(row.account_id).executeAsOne(), account.currency)
        return ReconciliationView(statement, lines, outstanding, clearedByHand, cleared, statement.closingBalance?.minus(cleared), groups)
    }

    private fun groupsOf(ledger: LedgerDatabase, statementId: String, lines: List<StatementLine>): List<MatchGroup> {
        val txns = ledger.matchingQueries.matchesForStatement(statementId).executeAsList().groupBy({ it.group_id }, { it.txn_id })
        return lines.filter { it.matchGroup != null }.groupBy { it.matchGroup!! }.map { (id, members) ->
            MatchGroup(id, members, txns[id].orEmpty(), members.all { it.status == LineStatus.MATCHED })
        }
    }

    // --- Group matches (REC-03) -----------------------------------------------------------------

    /**
     * REC-03: matches the chosen statement lines with the chosen transactions together, when their
     * amounts add up to the cent: one line for several transactions, several lines for one, or
     * several of each. The user chose them, so they are linked at once; [unlinkGroup] undoes it.
     */
    fun matchGroup(statementId: String, lineIds: Collection<String>, transactionIds: Collection<String>): MatchGroup {
        val (group, row) = locateOpen(statementId)
        val ledger = books.ledger(group)
        val q = ledger.ledgerQueries
        validate(lineIds.isNotEmpty() && transactionIds.isNotEmpty() && lineIds.size + transactionIds.size >= 3, "error.groupSize")
        val lines = lineIds.distinct().map { id -> q.statementLineById(id).executeAsOneOrNull()?.takeIf { it.statement_id == statementId } ?: throw ValidationException("error.lineState") }
        validate(lines.all { it.status in setOf(LineStatus.PROPOSED.name, LineStatus.UNMATCHED.name, LineStatus.IGNORED.name) }, "error.lineState")
        val txns = transactionIds.distinct().map { id -> q.txnById(id).executeAsOneOrNull()?.takeIf { it.account_id == row.account_id } ?: throw ValidationException("error.lineState") }
        validate(txns.all { it.external_id == null && it.cleared != ClearedStatus.RECONCILED.name }, "error.alreadyLinked")
        val taken = ledger.matchingQueries.matchesForStatement(statementId).executeAsList().map { it.txn_id }.toSet()
        validate(txns.none { it.id in taken }, "error.alreadyLinked")
        val account = books.accounts.get(row.account_id)
        val lineTotal = lines.sumOf { it.amount_minor }
        val txnTotal = txns.sumOf { it.amount_minor }
        validate(lineTotal == txnTotal, "error.groupTotal", Money.ofMinor(lineTotal, account.currency), Money.ofMinor(txnTotal, account.currency))
        val groupId = Ids.newId()
        ledger.transaction {
            // A line still proposed in another group leaves that group first.
            lines.mapNotNull { it.match_group }.distinct().forEach { rejectGroupIn(ledger, it) }
            txns.forEach { ledger.matchingQueries.insertMatch(groupId, statementId, it.id) }
            linkGroup(ledger, groupId, lines.sortedBy { it.line_no }.map { it.id }, txns.map { it.id })
        }
        books.session.audit("MATCH", "statement", statementId, "group")
        return view(statementId).groups.first { it.id == groupId }
    }

    /** REC-03: accepts a proposed group match. */
    fun confirmGroup(groupId: String) {
        val (group, lines, statement) = locateGroup(groupId)
        validate(lines.all { it.status == LineStatus.PROPOSED.name }, "error.lineState")
        val ledger = books.ledger(group)
        val txnIds = ledger.matchingQueries.matchTxns(groupId).executeAsList()
        val txns = txnIds.map { ledger.ledgerQueries.txnById(it).executeAsOneOrNull() }
        validate(txns.all { it != null && it.external_id == null && it.cleared != ClearedStatus.RECONCILED.name }, "error.alreadyLinked")
        validate(lines.sumOf { it.amount_minor } == txns.sumOf { it!!.amount_minor }, "error.linkAmount")
        ledger.transaction { linkGroup(ledger, groupId, lines.map { it.id }, txnIds) }
        books.session.audit("MATCH", "statement", statement.id, "group")
    }

    /** REC-03: turns down a proposed group match; its lines need a decision again. */
    fun rejectGroup(groupId: String) {
        val (group, lines, _) = locateGroup(groupId)
        validate(lines.all { it.status == LineStatus.PROPOSED.name }, "error.lineState")
        val ledger = books.ledger(group)
        ledger.transaction { rejectGroupIn(ledger, groupId) }
    }

    /** REC-03: undoes a confirmed group match: its transactions are no longer cleared by it and its lines need a decision again. */
    fun unlinkGroup(groupId: String) {
        val (group, lines, _) = locateGroup(groupId)
        validate(lines.all { it.status == LineStatus.MATCHED.name }, "error.lineState")
        val ledger = books.ledger(group)
        ledger.transaction {
            ledger.matchingQueries.matchTxns(groupId).executeAsList().forEach { id ->
                ledger.ledgerQueries.setExternalId(null, books.now(), id)
                books.transactions.setCleared(id, ClearedStatus.UNCLEARED)
            }
            ledger.matchingQueries.deleteMatchGroup(groupId)
            lines.forEach { ledger.matchingQueries.setLineGroup(null, LineStatus.UNMATCHED.name, null, it.id) }
        }
    }

    /**
     * Links a group: the first transaction takes the first line's bank id, the others the same id
     * with #2, #3..., so none can be matched again; every line points at the first transaction and
     * is recognized on a later import of the same lines (REC-10).
     */
    private fun linkGroup(ledger: LedgerDatabase, groupId: String, lineIds: List<String>, txnIds: List<String>) {
        val first = ledger.ledgerQueries.statementLineById(lineIds.first()).executeAsOne()
        txnIds.forEachIndexed { i, id -> linkTransaction(ledger, id, if (i == 0) first.external_id else "${first.external_id}#${i + 1}") }
        lineIds.forEach { ledger.matchingQueries.setLineGroup(groupId, LineStatus.MATCHED.name, txnIds.first(), it) }
    }

    private fun rejectGroupIn(ledger: LedgerDatabase, groupId: String) {
        ledger.matchingQueries.linesInGroup(groupId).executeAsList().forEach { ledger.matchingQueries.setLineGroup(null, LineStatus.UNMATCHED.name, null, it.id) }
        ledger.matchingQueries.deleteMatchGroup(groupId)
    }

    private fun locateGroup(groupId: String): Triple<GroupInfo, List<LineRow>, StatementRow> {
        for (group in books.groups()) {
            val lines = books.ledger(group).matchingQueries.linesInGroup(groupId).executeAsList()
            if (lines.isEmpty()) continue
            val (_, statement) = locateOpen(lines.first().statement_id)
            return Triple(group, lines, statement)
        }
        throw ValidationException("error.notFound")
    }

    /** Accepts a proposed match (step 3). */
    fun confirm(lineId: String) {
        val (group, line, statement) = locateLine(lineId)
        validate(line.status == LineStatus.PROPOSED.name && line.txn_id != null, "error.lineState")
        val ledger = books.ledger(group)
        val account = books.accounts.get(statement.account_id)
        val txnId = line.txn_id!!
        ledger.transaction {
            postFxFee(txnId, Money.ofMinor(line.amount_minor, account.currency))
            linkTransaction(ledger, txnId, line.external_id)
            ledger.ledgerQueries.setLineStatus(LineStatus.MATCHED.name, txnId, lineId)
        }
        books.session.audit("MATCH", "statement", statement.id, lineId)
    }

    /** Links a line to a transaction the user picked (step 3). */
    fun link(lineId: String, transactionId: String) {
        val (group, line, statement) = locateLine(lineId)
        validate(line.status in setOf(LineStatus.PROPOSED.name, LineStatus.UNMATCHED.name, LineStatus.IGNORED.name), "error.lineState")
        val ledger = books.ledger(group)
        val txn = ledger.ledgerQueries.txnById(transactionId).executeAsOneOrNull()
        validate(txn != null && txn.account_id == statement.account_id, "error.lineState")
        val foreign = txn!!.original_currency != null && txn.transfer_id == null
        validate(txn.amount_minor == line.amount_minor || (foreign && withinFx(txn.amount_minor, line.amount_minor, ImportSettings())), "error.linkAmount")
        validate(txn.external_id == null, "error.alreadyLinked")
        val account = books.accounts.get(statement.account_id)
        ledger.transaction {
            // A line proposed in a group match leaves it (REC-03).
            line.match_group?.let { rejectGroupIn(ledger, it) }
            postFxFee(transactionId, Money.ofMinor(line.amount_minor, account.currency))
            linkTransaction(ledger, transactionId, line.external_id)
            ledger.ledgerQueries.setLineStatus(LineStatus.MATCHED.name, transactionId, lineId)
        }
    }

    /** Adds a new transaction for a line that matches nothing recorded (step 3). */
    fun createTransaction(lineId: String): Transaction {
        val (group, line, statement) = locateLine(lineId)
        validate(line.status in setOf(LineStatus.PROPOSED.name, LineStatus.UNMATCHED.name, LineStatus.IGNORED.name), "error.lineState")
        val account = books.accounts.get(statement.account_id)
        val ledger = books.ledger(group)
        lateinit var txn: Transaction
        ledger.transaction {
            line.match_group?.let { rejectGroupIn(ledger, it) }
            txn = createFromLine(account, LocalDate.parse(line.date), Money.ofMinor(line.amount_minor, account.currency), line.payee, line.memo, line.external_id)
            ledger.ledgerQueries.setLineStatus(LineStatus.CREATED.name, txn.id, lineId)
        }
        return txn
    }

    /** Undoes a match or a created transaction, so the line can be resolved differently. */
    fun unlink(lineId: String) {
        val (group, line, _) = locateLine(lineId)
        validate(line.status == LineStatus.MATCHED.name || line.status == LineStatus.CREATED.name, "error.lineState")
        // REC-03: a line of a group match undoes the whole group.
        line.match_group?.let { unlinkGroup(it); return }
        val ledger = books.ledger(group)
        ledger.transaction {
            line.txn_id?.let { id ->
                if (line.status == LineStatus.CREATED.name) {
                    books.transactions.delete(id)
                } else {
                    ledger.ledgerQueries.setExternalId(null, books.now(), id)
                    books.transactions.setCleared(id, ClearedStatus.UNCLEARED)
                }
            }
            ledger.ledgerQueries.setLineStatus(LineStatus.UNMATCHED.name, null, lineId)
        }
    }

    fun ignore(lineId: String) {
        val (group, line, _) = locateLine(lineId)
        validate(line.status == LineStatus.PROPOSED.name || line.status == LineStatus.UNMATCHED.name, "error.lineState")
        val ledger = books.ledger(group)
        ledger.transaction {
            line.match_group?.let { rejectGroupIn(ledger, it) }
            ledger.ledgerQueries.setLineStatus(LineStatus.IGNORED.name, null, lineId)
        }
    }

    /**
     * Step 5: when the difference is zero and every line is resolved, all cleared transactions of
     * the account become reconciled and the statement is locked with its report (REC-06).
     */
    fun finish(statementId: String): ReconciliationReport {
        val view = view(statementId)
        validate(view.statement.closingBalance != null, "error.closingRequired")
        validate(view.unresolved.isEmpty(), "error.unresolvedLines")
        validate(view.difference?.isZero == true, "error.difference", view.difference ?: "")
        val (group, row) = locateOpen(statementId)
        val account = books.accounts.get(row.account_id)
        val ledger = books.ledger(group)
        val q = ledger.ledgerQueries
        val payeeNames = books.payees.list(true).associate { it.id to it.name }
        fun item(t: TxnRow) = ReportItem(t.id, t.date, t.payee_id?.let(payeeNames::get) ?: t.payee_text, t.amount_minor)

        lateinit var report: ReconciliationReport
        ledger.transaction {
            val toReconcile = q.clearedForAccount(row.account_id).executeAsList()
            report = ReconciliationReport(
                periodEnd = row.period_end,
                closingBalanceMinor = row.closing_balance_minor!!,
                currency = account.currency.code,
                cleared = toReconcile.map { r -> item(q.txnById(r.id).executeAsOne()) },
                outstanding = view.outstanding.map { t -> item(q.txnById(t.id).executeAsOne()) },
                groups = view.groups.map { g ->
                    ReportGroup(
                        g.lines.map { l -> ReportLine(l.date.toString(), l.payee, l.amount.minorUnits) },
                        g.transactionIds.mapNotNull { id -> q.txnById(id).executeAsOneOrNull()?.let(::item) },
                    )
                },
            )
            toReconcile.forEach { books.transactions.setCleared(it.id, ClearedStatus.RECONCILED) }
            q.completeStatement(books.now(), books.userId, json.encodeToString(ReconciliationReport.serializer(), report), statementId)
        }
        books.session.audit("RECONCILE", "statement", statementId, "${report.cleared.size} items")
        return report
    }

    fun report(statementId: String): ReconciliationReport? {
        val (_, row) = locateStatement(statementId)
        return row.report_json?.let { json.decodeFromString(ReconciliationReport.serializer(), it) }
    }

    /**
     * REC-07: reopens the most recent reconciliation of an account; the reason is logged. The
     * reconciled statement stays in the history as undone, with its reason and report, and an open
     * copy of it (same period, balances and lines, still linked to their transactions) takes its
     * place, so the period can be reconciled again without importing the file a second time.
     * Returns the id of that open statement.
     */
    fun undo(statementId: String, reason: String): String {
        validate(reason.isNotBlank(), "error.reasonRequired")
        val (group, row) = locateStatement(statementId)
        books.require(group, PermissionLevel.EDIT)
        validate(row.status == StatementStatus.RECONCILED.name, "error.lineState")
        val latest = books.ledger(group).ledgerQueries.statementsForAccount(row.account_id).executeAsList()
            .filter { it.status == StatementStatus.RECONCILED.name }
            .maxByOrNull { it.period_end }
        validate(latest?.id == statementId, "error.undoLatestOnly")
        val report = report(statementId)
        val ledger = books.ledger(group)
        val q = ledger.ledgerQueries
        val reopenedId = Ids.newId()
        ledger.transaction {
            report?.cleared?.forEach { item ->
                if (q.txnById(item.transactionId).executeAsOneOrNull()?.cleared == ClearedStatus.RECONCILED.name) {
                    books.transactions.setCleared(item.transactionId, ClearedStatus.CLEARED, confirmReconciled = true)
                }
            }
            q.undoStatement(reason.trim(), statementId)
            // The undone statement keeps the file's fingerprint, so the same file is still refused
            // on import (REC-10); the open copy carries the lines instead.
            q.insertStatement(
                reopenedId, row.account_id, row.source_name, row.format, null, row.period_start, row.period_end,
                row.opening_balance_minor, row.closing_balance_minor, books.now(), books.userId,
            )
            // REC-03: group matches come along, under new ids.
            val groupIds = HashMap<String, String>()
            q.statementLines(statementId).executeAsList().forEach { l ->
                val id = Ids.newId()
                q.insertStatementLine(id, reopenedId, l.line_no, l.external_id, l.date, l.amount_minor, l.payee, l.memo, l.check_number, l.status, l.txn_id)
                l.match_group?.let { old -> ledger.matchingQueries.setLineGroup(groupIds.getOrPut(old) { Ids.newId() }, l.status, l.txn_id, id) }
            }
            ledger.matchingQueries.matchesForStatement(statementId).executeAsList().forEach { m ->
                groupIds[m.group_id]?.let { ledger.matchingQueries.insertMatch(it, reopenedId, m.txn_id) }
            }
        }
        // The reason stays with the statement in the group's ledger; the shared audit log only records the undo (HH-11).
        books.session.audit("UNDO_RECONCILE", "statement", statementId)
        return reopenedId
    }

    /** REC-09: the last reconciled statement date of every visible account (null if never). */
    fun lastReconciled(): Map<String, LocalDate?> {
        val result = HashMap<String, LocalDate?>()
        for (summary in books.accounts.list()) result[summary.account.id] = null
        for (group in books.groups()) {
            books.ledger(group).ledgerQueries.lastReconciled().executeAsList().forEach { r ->
                result[r.account_id] = LocalDate.parse(r.period_end)
            }
        }
        return result
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun locateStatement(statementId: String): Pair<GroupInfo, StatementRow> {
        for (group in books.groups()) {
            val row = books.ledger(group).ledgerQueries.statementById(statementId).executeAsOneOrNull() ?: continue
            return group to row
        }
        throw AccessDeniedException("Statement not found or not accessible")
    }

    private fun locateOpen(statementId: String): Pair<GroupInfo, StatementRow> {
        val (group, row) = locateStatement(statementId)
        books.require(group, PermissionLevel.EDIT)
        validate(row.status == StatementStatus.OPEN.name, "error.statementLocked")
        return group to row
    }

    private fun locateLine(lineId: String): Triple<GroupInfo, LineRow, StatementRow> {
        for (group in books.groups()) {
            val q = books.ledger(group).ledgerQueries
            val line = q.statementLineById(lineId).executeAsOneOrNull() ?: continue
            val (_, statement) = locateOpen(line.statement_id)
            return Triple(group, line, statement)
        }
        throw AccessDeniedException("Statement line not found or not accessible")
    }

    private fun StatementRow.toStatement(account: Account) = Statement(
        id, account_id, source_name, format, period_start?.let(LocalDate::parse), LocalDate.parse(period_end),
        opening_balance_minor?.let { Money.ofMinor(it, account.currency) }, closing_balance_minor?.let { Money.ofMinor(it, account.currency) },
        StatementStatus.valueOf(status), reconciled_at, undo_reason,
    )

    private fun LineRow.toLine(account: Account) = StatementLine(
        id, line_no.toInt(), LocalDate.parse(date), Money.ofMinor(amount_minor, account.currency), payee, memo, check_number,
        LineStatus.valueOf(status), txn_id, match_group,
    )

    companion object {
        /**
         * Id for a line whose file gives none (CSV): date, amount and payee, plus how many identical
         * lines came before it in the same file, so two real coffees on the same day both import.
         */
        internal fun fingerprint(date: LocalDate, amount: Money, payee: String?, occurrences: MutableMap<String, Int>): String {
            val base = "$date|${amount.minorUnits}|${amount.currency.code}|${payee?.trim()?.uppercase().orEmpty()}"
            val n = occurrences.merge(base, 1, Int::plus)!!
            return "fp:" + sha256("$base|$n".toByteArray()).take(32)
        }

        internal fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Payees are similar when their first meaningful word matches ("IGA EXTRA #8123" and "IGA"). */
        internal fun similarPayee(a: String, b: String?): Boolean {
            if (b == null) return false
            fun key(s: String) = s.uppercase().split(Regex("[^\\p{L}\\p{N}]+")).firstOrNull { it.length >= 2 && !it.all(Char::isDigit) }
            val ka = key(a)
            return ka != null && ka == key(b)
        }

        /** REC-03: at most this many items on the many side of a proposed group match. */
        private const val MAX_GROUP = 4

        /** REC-03: the closest candidates in date looked at for a proposed group match. */
        private const val MAX_GROUP_CANDIDATES = 12

        private fun today(): LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
    }
}
