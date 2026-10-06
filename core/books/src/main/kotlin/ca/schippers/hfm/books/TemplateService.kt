package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import ca.schippers.hfm.data.ledger.Txn_template as TemplateRow
import ca.schippers.hfm.data.ledger.Txn_template_line as LineRow

/** One line of a template: its category, and its amount when the template is split (MAN-05). */
data class TemplateLine(val categoryId: String?, val amount: Money? = null, val memo: String? = null)

/**
 * MAN-05: a named transaction template, kept in an account group's ledger. [accountId] limits it
 * to one account of that group; [amount] is optional (signed: negative for a payment). With
 * several [lines], each line has its amount and [amount] is their total.
 */
data class TxnTemplate(
    val id: String,
    val groupId: String,
    val name: String,
    val payee: String? = null,
    val memo: String? = null,
    val amount: Money? = null,
    val accountId: String? = null,
    val memberId: String? = null,
    val lines: List<TemplateLine> = emptyList(),
    val tags: Set<String> = emptySet(),
) {
    val isSplit: Boolean get() = lines.size > 1
    val categoryId: String? get() = lines.singleOrNull()?.categoryId
}

class TemplateService internal constructor(private val books: Books) {

    /** Every template the user can see, by group then name. */
    fun list(): List<TxnTemplate> = books.groups().flatMap { g -> templatesIn(g) }

    /** The templates offered in [accountId]'s register: its group's, for any account or for this one. */
    fun forAccount(accountId: String): List<TxnTemplate> {
        val (group, _) = books.accounts.locate(accountId)
        return templatesIn(group).filter { it.accountId == null || it.accountId == accountId }
    }

    fun get(id: String): TxnTemplate = list().firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    fun save(t: TxnTemplate): TxnTemplate {
        validate(t.name.isNotBlank(), "error.nameRequired")
        val group = books.group(if (t.id.isBlank()) t.groupId else get(t.id).groupId)
        books.require(group, PermissionLevel.EDIT)
        val ledger = books.ledger(group)
        val q = ledger.templatesQueries
        val id = t.id.ifBlank { Ids.newId() }
        validate(templatesIn(group).none { it.id != id && it.name.equals(t.name.trim(), ignoreCase = true) }, "error.templateName")
        val account = t.accountId?.let { a ->
            val (accountGroup, account) = books.accounts.locate(a)
            validate(accountGroup.id == group.id, "error.templateAccount")
            account
        }
        val lines = t.lines.filter { it.categoryId != null || it.amount != null || !it.memo.isNullOrBlank() }
        // A split template has an amount on every line; its amount is their total.
        val amount = if (lines.size > 1) {
            validate(lines.all { it.amount != null }, "error.amountRequired")
            val currency = lines.first().amount!!.currency
            validate(lines.all { it.amount!!.currency == currency }, "error.currencyMismatch", currency.code)
            lines.map { it.amount!! }.sum(currency)
        } else {
            t.amount
        }
        account?.let { a -> amount?.let { validate(it.currency == a.currency, "error.currencyMismatch", a.currency.code) } }
        val now = books.now()
        val created = q.templateById(id).executeAsOneOrNull()?.created_at ?: now
        val tags = t.tags.map { it.trim().replace('\n', ' ') }.filter { it.isNotEmpty() }.distinct().sorted()
        ledger.transaction {
            q.upsertTemplate(
                id, t.name.trim(), t.payee.blankToNull(), t.memo.blankToNull(), amount?.minorUnits, amount?.currency?.code, t.accountId, t.memberId,
                tags.joinToString("\n"), created, now,
            )
            q.clearTemplateLines(id)
            val single = lines.size == 1
            lines.forEachIndexed { i, l -> q.insertTemplateLine(id, i.toLong(), l.categoryId, if (single) null else l.amount?.minorUnits, l.memo.blankToNull()) }
        }
        books.session.audit("UPDATE", "txn_template", id)
        return get(id)
    }

    fun delete(id: String) {
        val t = get(id)
        val group = books.group(t.groupId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).templatesQueries.deleteTemplate(id)
        books.session.audit("DELETE", "txn_template", id)
    }

    /**
     * "Save as template": a new template named [name] from a recorded transaction, in its account's
     * group: payee, memo, person, category or split lines and tags, with its amount when
     * [keepAmount], for its account only when [thisAccountOnly]. Transfers and investment lines
     * cannot become templates.
     */
    fun fromTransaction(transactionId: String, name: String, keepAmount: Boolean = true, thisAccountOnly: Boolean = false): TxnTemplate {
        val txn = books.transactions.get(transactionId)
        validate(txn.transfer == null && txn.investmentId == null, "error.templateTransfer")
        val (group, _) = books.accounts.locate(txn.accountId)
        val payee = txn.payeeId?.let { id -> books.payees.list(true).firstOrNull { it.id == id }?.name } ?: txn.payeeText
        val tagNames = books.tags().filter { it.id in txn.tagIds }.map { it.name }.toSet()
        val lines = when {
            txn.isSplit && keepAmount -> txn.splits.map { TemplateLine(it.categoryId, it.amount, it.memo) }
            txn.isSplit -> emptyList()
            else -> listOfNotNull(txn.splits.firstOrNull()?.categoryId?.let { TemplateLine(it) })
        }
        return save(
            TxnTemplate(
                "", group.id, name, payee, txn.memo, txn.amount.takeIf { keepAmount }, txn.accountId.takeIf { thisAccountOnly }, txn.memberId, lines, tagNames,
            ),
        )
    }

    /**
     * The transaction a template gives in [accountId] on [date]. [amount] (signed) replaces the
     * template's own; one of the two is required. A template's amount in another currency than the
     * account's is left out.
     */
    fun draft(templateId: String, accountId: String, date: LocalDate, amount: Money? = null): TransactionDraft {
        val t = get(templateId)
        val account = books.accounts.get(accountId)
        val own = t.amount?.takeIf { it.currency == account.currency }
        val total = amount ?: own ?: throw ValidationException("error.amountRequired")
        validate(total.currency == account.currency, "error.currencyMismatch", account.currency.code)
        val splits = when {
            t.isSplit && own != null && total == own -> t.lines.map { SplitDraft(it.categoryId, it.amount!!, it.memo) }
            t.categoryId != null -> listOf(SplitDraft(t.categoryId, total, t.lines.single().memo))
            else -> emptyList()
        }
        return TransactionDraft(account.id, date, total, t.payee, splits, t.memo, t.memberId, tags = t.tags)
    }

    private fun templatesIn(group: GroupInfo): List<TxnTemplate> {
        val q = books.ledger(group).templatesQueries
        return q.templates().executeAsList().map { row -> row.toTemplate(group.id, q.templateLines(row.id).executeAsList()) }
    }

    private fun TemplateRow.toTemplate(groupId: String, lineRows: List<LineRow>): TxnTemplate {
        val c = currency?.let(Currency::of)
        return TxnTemplate(
            id, groupId, name, payee, memo, amount_minor?.let { m -> c?.let { Money.ofMinor(m, it) } }, account_id, member_id,
            lineRows.map { l -> TemplateLine(l.category_id, l.amount_minor?.let { m -> c?.let { Money.ofMinor(m, it) } }, l.memo) },
            tags.split('\n').filter { it.isNotBlank() }.toSet(),
        )
    }
}
