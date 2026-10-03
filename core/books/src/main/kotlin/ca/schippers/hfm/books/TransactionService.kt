package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.ledger.LedgerDatabase
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.MathContext
import ca.schippers.hfm.data.ledger.Txn as TxnRow
import ca.schippers.hfm.data.ledger.Txn_split as SplitRow

/** One entry in a record's change history (TX-08). */
data class Change(val at: Long, val userId: String?, val action: String, val before: String?, val after: String?)

/**
 * Transactions, splits and transfers (TX-01 to TX-03, TX-08).
 *
 * Rules enforced here:
 * - amounts are in the account's currency, and splits always add up exactly to the amount;
 * - a transfer is one movement recorded in both accounts and never counts as income or spending;
 * - creating needs at least capture permission on the account's group, changing needs edit permission;
 * - reconciled transactions only change with explicit confirmation, and every change is logged.
 */
class TransactionService internal constructor(private val books: Books) {

    private val json = Json { encodeDefaults = false }

    // --- Reading --------------------------------------------------------------------------------

    /**
     * The transactions of an account, oldest first, with the running balance (TX-01). With
     * [limit], only the most recent ones are returned, so the register opens quickly however long
     * the history (NFR-02); the running balance still counts every earlier transaction.
     */
    fun register(accountId: String, limit: Int? = null): List<RegisterRow> {
        val (group, account) = books.accounts.locate(accountId)
        val q = books.ledger(group).ledgerQueries
        val rows = q.latestForAccount(accountId, (limit ?: Int.MAX_VALUE).toLong()).executeAsList()
        val from = rows.lastOrNull()?.date ?: return emptyList()
        val splits = q.splitsForAccountFrom(accountId, from).executeAsList().groupBy { it.txn_id }
        val tags = q.tagsForAccountFrom(accountId, from).executeAsList().groupBy({ it.txn_id }, { it.tag_id })
        // Running balances, worked backwards from today's balance, so earlier history is never read.
        var balance = q.accountBalance(accountId).executeAsOne()
        val result = ArrayList<RegisterRow>(rows.size)
        for (row in rows) {
            result += RegisterRow(
                row.toTransaction(account.currency, splits[row.id].orEmpty(), tags[row.id].orEmpty().toSet()),
                Money.ofMinor(balance, account.currency),
            )
            balance -= row.amount_minor
        }
        return result.asReversed()
    }

    /** How many transactions the account has in all, for "showing the last N of M". */
    fun count(accountId: String): Long {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).ledgerQueries.txnCountForAccount(accountId).executeAsOne()
    }

    fun get(transactionId: String): Transaction {
        val (group, row) = locate(transactionId)
        return load(books.ledger(group), row)
    }

    fun history(transactionId: String): List<Change> {
        val (group, _) = locate(transactionId)
        return books.ledger(group).ledgerQueries.changesFor(ENTITY, transactionId).executeAsList()
            .map { Change(it.at, it.user_id, it.action, it.before_json, it.after_json) }
    }

    /** Values from the payee's most recent transaction, offered when the payee is typed (MAN-02). */
    fun suggest(accountId: String, payeeName: String): PayeeSuggestion? {
        val payee = books.payees.match(payeeName) ?: return null
        val (group, account) = books.accounts.locate(accountId)
        val q = books.ledger(group).ledgerQueries
        val last = q.lastUseOfPayee(payee.id).executeAsOneOrNull()
        if (last == null || last.currency(group) != account.currency) {
            return PayeeSuggestion(payee.id, null, listOfNotNull(payee.defaultCategoryId?.let { SplitDraft(it, Money.zero(account.currency)) }))
        }
        val splits = q.splitsForTxn(last.id).executeAsList()
            .map { SplitDraft(it.category_id, Money.ofMinor(it.amount_minor, account.currency), it.memo, it.member_id, it.tax_flag?.let(TaxFlag::valueOf)) }
        return PayeeSuggestion(payee.id, Money.ofMinor(last.amount_minor, account.currency), splits)
    }

    // --- Ordinary transactions ------------------------------------------------------------------

    fun create(draft: TransactionDraft): Transaction {
        val (group, account) = books.accounts.locate(draft.accountId)
        books.require(group, PermissionLevel.CAPTURE_ONLY)
        val prepared = prepare(draft, account)
        val ledger = books.ledger(group)
        val id = Ids.newId()
        val now = books.now()
        ledger.transaction {
            ledger.ledgerQueries.insertTxn(
                id, account.id, draft.date.toString(), prepared.payeeId, draft.payeeName?.trim()?.ifEmpty { null },
                draft.amount.minorUnits, draft.originalAmount?.minorUnits, draft.originalAmount?.currency?.code,
                prepared.fxRate?.toPlainString(), draft.memo?.ifBlank { null }, draft.memberId, draft.cleared.name,
                null, null, books.userId, DESKTOP, now, now,
            )
            if (draft.assetId != null) ledger.ledgerQueries.setTxnAsset(draft.assetId, id)
            if (draft.cardHolderId != null) ledger.cardsQueries.setTxnCardHolder(draft.cardHolderId, id)
            writeChildren(ledger, id, prepared)
            logChange(ledger, id, "CREATE", null, snapshot(ledger, id))
        }
        return get(id)
    }

    fun update(transactionId: String, draft: TransactionDraft, confirmReconciled: Boolean = false): Transaction {
        val (group, row) = locate(transactionId)
        books.require(group, PermissionLevel.EDIT)
        validate(row.transfer_id == null, "error.editTransferAsTransfer")
        validate(row.investment_id == null, "error.editInInvestments")
        validate(draft.accountId == row.account_id, "error.cannotChangeAccount")
        guardReconciled(row, confirmReconciled)
        val account = books.accounts.get(row.account_id)
        val prepared = prepare(draft, account)
        val ledger = books.ledger(group)
        ledger.transaction {
            val before = snapshot(ledger, transactionId)
            ledger.ledgerQueries.updateTxn(
                draft.date.toString(), prepared.payeeId, draft.payeeName?.trim()?.ifEmpty { null }, draft.amount.minorUnits,
                draft.originalAmount?.minorUnits, draft.originalAmount?.currency?.code, prepared.fxRate?.toPlainString(),
                draft.memo?.ifBlank { null }, draft.memberId, draft.cleared.name, null, null, books.now(), transactionId,
            )
            ledger.ledgerQueries.setTxnAsset(draft.assetId, transactionId)
            ledger.cardsQueries.setTxnCardHolder(draft.cardHolderId, transactionId)
            ledger.ledgerQueries.deleteSplits(transactionId)
            ledger.ledgerQueries.deleteTxnTags(transactionId)
            writeChildren(ledger, transactionId, prepared)
            logChange(ledger, transactionId, "UPDATE", before, snapshot(ledger, transactionId))
        }
        return get(transactionId)
    }

    /** Deletes a transaction; deleting either side of a transfer deletes both sides. */
    fun delete(transactionId: String, confirmReconciled: Boolean = false) {
        val (group, row) = locate(transactionId)
        validate(row.investment_id == null, "error.editInInvestments")
        row.transfer_id?.let { return deleteTransfer(it, confirmReconciled) }
        books.require(group, PermissionLevel.EDIT)
        guardReconciled(row, confirmReconciled)
        val ledger = books.ledger(group)
        ledger.transaction {
            logChange(ledger, transactionId, "DELETE", snapshot(ledger, transactionId), null)
            ledger.ledgerQueries.deleteTxn(transactionId)
        }
    }

    fun setCleared(transactionId: String, status: ClearedStatus, confirmReconciled: Boolean = false) {
        val (group, row) = locate(transactionId)
        books.require(group, PermissionLevel.EDIT)
        if (row.cleared == status.name) return
        guardReconciled(row, confirmReconciled)
        val ledger = books.ledger(group)
        ledger.transaction {
            val before = snapshot(ledger, transactionId)
            ledger.ledgerQueries.setCleared(status.name, books.now(), transactionId)
            logChange(ledger, transactionId, "UPDATE", before, snapshot(ledger, transactionId))
        }
    }

    // --- Transfers ------------------------------------------------------------------------------

    /** Records a transfer once; it appears in both accounts (TX-03), with both amounts when currencies differ (FX-04). */
    fun transfer(draft: TransferDraft): Pair<Transaction, Transaction> {
        val plan = planTransfer(draft)
        val transferId = Ids.newId()
        val fromId = Ids.newId()
        val toId = Ids.newId()
        val now = books.now()
        writeBothSides(plan, { ledger ->
            insertTransferSide(ledger, fromId, plan.from, -plan.fromAmount, plan.toAmount, plan.rate, draft, transferId, plan.to.id, now)
        }, { ledger ->
            insertTransferSide(ledger, toId, plan.to, plan.toAmount, plan.fromAmount, plan.inverseRate, draft, transferId, plan.from.id, now)
        }, undoFrom = { ledger -> ledger.ledgerQueries.deleteTxn(fromId) })
        return get(fromId) to get(toId)
    }

    fun updateTransfer(transferId: String, draft: TransferDraft, confirmReconciled: Boolean = false) {
        val sides = transferSides(transferId)
        val fromRow = sides.firstOrNull { it.second.account_id == draft.fromAccountId }?.second
        val toRow = sides.firstOrNull { it.second.account_id == draft.toAccountId }?.second
        validate(fromRow != null && toRow != null && fromRow.id != toRow.id, "error.cannotChangeAccount")
        sides.forEach { (group, row) ->
            books.require(group, PermissionLevel.EDIT)
            guardReconciled(row, confirmReconciled)
        }
        val plan = planTransfer(draft)
        val now = books.now()
        for ((group, row) in sides) {
            val ledger = books.ledger(group)
            val isFrom = row.id == fromRow!!.id
            ledger.transaction {
                val before = snapshot(ledger, row.id)
                ledger.ledgerQueries.updateTxn(
                    draft.date.toString(), null, null,
                    if (isFrom) -plan.fromAmount.minorUnits else plan.toAmount.minorUnits,
                    if (plan.crossCurrency) (if (isFrom) plan.toAmount else plan.fromAmount).minorUnits else null,
                    if (plan.crossCurrency) (if (isFrom) plan.to.currency else plan.from.currency).code else null,
                    if (plan.crossCurrency) (if (isFrom) plan.rate else plan.inverseRate).toPlainString() else null,
                    draft.memo?.ifBlank { null }, draft.memberId, row.cleared, transferId, row.transfer_account_id, now, row.id,
                )
                logChange(ledger, row.id, "UPDATE", before, snapshot(ledger, row.id))
            }
        }
    }

    private fun deleteTransfer(transferId: String, confirmReconciled: Boolean) {
        val sides = transferSides(transferId)
        sides.forEach { (group, row) ->
            books.require(group, PermissionLevel.EDIT)
            guardReconciled(row, confirmReconciled)
        }
        for ((group, row) in sides) {
            val ledger = books.ledger(group)
            ledger.transaction {
                logChange(ledger, row.id, "DELETE", snapshot(ledger, row.id), null)
                ledger.ledgerQueries.deleteTxn(row.id)
            }
        }
    }

    private class TransferPlan(
        val from: Account,
        val to: Account,
        val fromGroup: GroupInfo,
        val toGroup: GroupInfo,
        val fromAmount: Money,
        val toAmount: Money,
    ) {
        val crossCurrency: Boolean get() = from.currency != to.currency

        /** Units of the destination currency per unit of the source currency. */
        val rate: BigDecimal get() = toAmount.toBigDecimal().divide(fromAmount.toBigDecimal(), MathContext.DECIMAL64)
        val inverseRate: BigDecimal get() = fromAmount.toBigDecimal().divide(toAmount.toBigDecimal(), MathContext.DECIMAL64)
    }

    private fun planTransfer(draft: TransferDraft): TransferPlan {
        validate(draft.fromAccountId != draft.toAccountId, "error.transferSameAccount")
        val (fromGroup, from) = books.accounts.locate(draft.fromAccountId)
        val (toGroup, to) = books.accounts.locate(draft.toAccountId)
        books.require(fromGroup, PermissionLevel.CAPTURE_ONLY)
        books.require(toGroup, PermissionLevel.CAPTURE_ONLY)
        validate(draft.amount.isPositive, "error.transferPositive")
        validate(draft.amount.currency == from.currency, "error.currencyMismatch", from.currency.code)
        val toAmount = when {
            from.currency == to.currency -> {
                validate(draft.toAmount == null || draft.toAmount == draft.amount, "error.transferSameCurrency")
                draft.amount
            }
            else -> {
                val received = draft.toAmount ?: throw ValidationException("error.transferToAmount", to.currency.code)
                validate(received.currency == to.currency && received.isPositive, "error.transferToAmount", to.currency.code)
                received
            }
        }
        return TransferPlan(from, to, fromGroup, toGroup, draft.amount, toAmount)
    }

    /**
     * Writes both sides. Within one ledger this is one database transaction. Across two ledgers
     * (two encrypted files) the second write is attempted after the first commits, and the first is
     * undone if the second fails, so a failure never leaves half a transfer.
     */
    private fun writeBothSides(
        plan: TransferPlan,
        writeFrom: (LedgerDatabase) -> Unit,
        writeTo: (LedgerDatabase) -> Unit,
        undoFrom: (LedgerDatabase) -> Unit,
    ) {
        val fromLedger = books.ledger(plan.fromGroup)
        val toLedger = books.ledger(plan.toGroup)
        if (plan.fromGroup.id == plan.toGroup.id) {
            fromLedger.transaction {
                writeFrom(fromLedger)
                writeTo(fromLedger)
            }
            return
        }
        fromLedger.transaction { writeFrom(fromLedger) }
        try {
            toLedger.transaction { writeTo(toLedger) }
        } catch (e: Throwable) {
            fromLedger.transaction { undoFrom(fromLedger) }
            throw e
        }
    }

    private fun insertTransferSide(
        ledger: LedgerDatabase,
        id: String,
        account: Account,
        amount: Money,
        otherAmount: Money,
        rate: BigDecimal,
        draft: TransferDraft,
        transferId: String,
        otherAccountId: String,
        now: Long,
    ) {
        val cross = otherAmount.currency != account.currency
        ledger.ledgerQueries.insertTxn(
            id, account.id, draft.date.toString(), null, null, amount.minorUnits,
            if (cross) otherAmount.minorUnits else null, if (cross) otherAmount.currency.code else null,
            if (cross) rate.toPlainString() else null, draft.memo?.ifBlank { null }, draft.memberId,
            ClearedStatus.UNCLEARED.name, transferId, otherAccountId, books.userId, DESKTOP, now, now,
        )
        logChange(ledger, id, "CREATE", null, snapshot(ledger, id))
    }

    private fun transferSides(transferId: String): List<Pair<GroupInfo, TxnRow>> {
        val sides = books.groups().flatMap { group ->
            books.ledger(group).ledgerQueries.txnsByTransfer(transferId).executeAsList().map { group to it }
        }
        if (sides.size != 2) throw AccessDeniedException("Both sides of this transfer must be accessible to change it")
        return sides
    }

    // --- Helpers --------------------------------------------------------------------------------

    private class Prepared(val payeeId: String?, val splits: List<SplitDraft>, val tagIds: List<String>, val fxRate: BigDecimal?)

    private fun prepare(draft: TransactionDraft, account: Account): Prepared {
        val currency = account.currency
        validate(draft.amount.currency == currency, "error.currencyMismatch", currency.code)
        val splits = draft.splits.ifEmpty { listOf(SplitDraft(null, draft.amount)) }
        validate(splits.all { it.amount.currency == currency }, "error.currencyMismatch", currency.code)
        val splitTotal = splits.map { it.amount }.sum(currency)
        validate(splitTotal == draft.amount, "error.splitTotal", splitTotal, draft.amount)
        val knownCategories = books.categories.list(includeArchived = true).mapTo(HashSet()) { it.id }
        validate(splits.all { it.categoryId == null || it.categoryId in knownCategories }, "error.unknownCategory")

        var fxRate = draft.fxRate
        draft.originalAmount?.let { original ->
            validate(original.currency != currency, "error.originalNotForeign")
            validate(original.signum == draft.amount.signum, "error.originalSign")
            if (fxRate == null && !original.isZero) {
                fxRate = draft.amount.toBigDecimal().divide(original.toBigDecimal(), MathContext.DECIMAL64).abs()
            }
        }
        fxRate?.let { validate(it.signum() > 0, "error.ratePositive") }

        val payeeId = draft.payeeName?.let { books.payees.findOrCreate(it)?.id }
        val tagIds = draft.tags.filter { it.isNotBlank() }.map { name ->
            books.core.insertTag(Ids.newId(), name.trim())
            books.core.tagByName(name.trim()).executeAsOne().id
        }
        return Prepared(payeeId, splits, tagIds, fxRate)
    }

    private fun writeChildren(ledger: LedgerDatabase, txnId: String, prepared: Prepared) {
        for (split in prepared.splits) {
            ledger.ledgerQueries.insertSplit(
                Ids.newId(), txnId, split.categoryId, split.amount.minorUnits, split.memo?.ifBlank { null }, split.memberId, split.taxFlag?.name,
            )
        }
        prepared.tagIds.forEach { ledger.ledgerQueries.insertTxnTag(txnId, it) }
    }

    // --- Investment cash lines (INV-02) -----------------------------------------------------------

    /**
     * Creates a cash line for an investment transaction. [trade] lines stay out of income and
     * spending reports: they carry no category lines, so the category totals never see them.
     */
    internal fun createForInvestment(draft: TransactionDraft, investmentId: String, trade: Boolean): Transaction {
        val created = create(draft)
        val (group, _) = locate(created.id)
        val q = books.ledger(group).ledgerQueries
        q.setTxnInvestment(investmentId, if (trade) 1 else 0, created.id)
        if (trade) q.deleteSplits(created.id)
        return get(created.id)
    }

    /** Deletes the cash lines of an investment transaction, logged like any deletion. */
    internal fun deleteForInvestment(group: GroupInfo, investmentId: String, confirmReconciled: Boolean) {
        val ledger = books.ledger(group)
        val rows = ledger.ledgerQueries.txnsForInvestment(investmentId).executeAsList()
        rows.forEach { guardReconciled(it, confirmReconciled) }
        ledger.transaction {
            for (row in rows) {
                logChange(ledger, row.id, "DELETE", snapshot(ledger, row.id), null)
                ledger.ledgerQueries.deleteTxn(row.id)
            }
        }
    }

    private fun guardReconciled(row: TxnRow, confirmed: Boolean) {
        if (row.cleared == ClearedStatus.RECONCILED.name && !confirmed) throw ReconciledChangeException()
    }

    private fun locate(transactionId: String): Pair<GroupInfo, TxnRow> {
        for (group in books.groups()) {
            val row = books.ledger(group).ledgerQueries.txnById(transactionId).executeAsOneOrNull() ?: continue
            return group to row
        }
        throw AccessDeniedException("Transaction not found or not accessible")
    }

    private fun load(ledger: LedgerDatabase, row: TxnRow): Transaction {
        val currency = Currency.of(ledger.ledgerQueries.accountById(row.account_id).executeAsOne().currency)
        val q = ledger.ledgerQueries
        return row.toTransaction(currency, q.splitsForTxn(row.id).executeAsList(), q.tagsForTxn(row.id).executeAsList().toSet())
    }

    private fun TxnRow.currency(group: GroupInfo): Currency =
        Currency.of(books.ledger(group).ledgerQueries.accountById(account_id).executeAsOne().currency)

    private fun logChange(ledger: LedgerDatabase, txnId: String, action: String, before: String?, after: String?) {
        ledger.ledgerQueries.insertChange(Ids.newId(), books.now(), books.userId, ENTITY, txnId, action, before, after)
    }

    private fun snapshot(ledger: LedgerDatabase, txnId: String): String {
        val q = ledger.ledgerQueries
        val row = q.txnById(txnId).executeAsOne()
        val snapshot = Snapshot(
            date = row.date,
            payee = row.payee_text,
            amountMinor = row.amount_minor,
            memo = row.memo,
            cleared = row.cleared,
            transferId = row.transfer_id,
            splits = q.splitsForTxn(txnId).executeAsList().map { SnapshotSplit(it.category_id, it.amount_minor, it.memo) },
            tags = q.tagsForTxn(txnId).executeAsList(),
        )
        return json.encodeToString(Snapshot.serializer(), snapshot)
    }

    @Serializable
    private data class Snapshot(
        val date: String,
        val payee: String? = null,
        val amountMinor: Long,
        val memo: String? = null,
        val cleared: String,
        val transferId: String? = null,
        val splits: List<SnapshotSplit> = emptyList(),
        val tags: List<String> = emptyList(),
    )

    @Serializable
    private data class SnapshotSplit(val categoryId: String? = null, val amountMinor: Long, val memo: String? = null)

    private companion object {
        const val ENTITY = "txn"
        const val DESKTOP = "desktop"
    }
}

internal fun TxnRow.toTransaction(currency: Currency, splits: List<SplitRow>, tagIds: Set<String>): Transaction = Transaction(
    id = id,
    accountId = account_id,
    date = LocalDate.parse(date),
    payeeId = payee_id,
    payeeText = payee_text,
    amount = Money.ofMinor(amount_minor, currency),
    originalAmount = original_amount_minor?.let { Money.ofMinor(it, Currency.of(original_currency!!)) },
    fxRate = fx_rate?.let(::BigDecimal),
    memo = memo,
    memberId = member_id,
    assetId = asset_id,
    cleared = ClearedStatus.valueOf(cleared),
    transfer = transfer_id?.let { TransferLink(it, transfer_account_id!!) },
    splits = splits.map {
        Split(it.id, it.category_id, Money.ofMinor(it.amount_minor, currency), it.memo, it.member_id, it.tax_flag?.let(TaxFlag::valueOf))
    },
    tagIds = tagIds,
    createdBy = created_by,
    investmentId = investment_id,
    cardHolderId = card_holder_id,
)
