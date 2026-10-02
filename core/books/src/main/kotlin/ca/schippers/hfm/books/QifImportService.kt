package ca.schippers.hfm.books

import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.importers.DateOrder
import ca.schippers.hfm.importers.QifAccountKind
import ca.schippers.hfm.importers.QifFile
import ca.schippers.hfm.importers.QifParser
import ca.schippers.hfm.importers.QifTransaction
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.FieldExtractor
import kotlinx.datetime.LocalDate

/**
 * Where one QIF account goes: an existing account ([targetAccountId]) or a new one. Accounts left
 * out ([include] false) are skipped, and transfers to them are imported as ordinary lines.
 */
data class QifAccountPlan(
    val qifName: String,
    val kind: QifAccountKind,
    val transactions: Int,
    val targetAccountId: String? = null,
    val newName: String = qifName,
    val newType: AccountType = AccountType.CHEQUING,
    val currency: Currency = Currency.CAD,
    val include: Boolean = true,
)

data class QifPreview(
    val plans: List<QifAccountPlan>,
    val transactions: Int,
    val investmentActions: Int,
    val from: LocalDate?,
    val to: LocalDate?,
    /** Null when the file does not say whether 03/04 is March 4 or April 3: the user chooses. */
    val dateOrder: DateOrder?,
    val warnings: List<String>,
)

data class QifImportResult(
    val accountsCreated: Int,
    val transactions: Int,
    val transfers: Int,
    val alreadyThere: Int,
    val categoriesCreated: Int,
    val investmentActionsKept: Int,
    val warnings: List<String>,
)

/**
 * OTH-05: imports a Quicken history from its QIF export (also GnuCash and Moneydance QIF).
 *
 * Each QIF account becomes a new account or is added to an existing one. Quicken writes every
 * transfer twice, once in each account, so a transfer is created once and its twin skipped. A
 * transaction already in the account (same date, amount and payee) is not added again, so an
 * import can safely be repeated. Categories are matched to the existing tree by name in either
 * language and created when missing; classes become tags.
 *
 * Investment holdings arrive with the investment module (Phase 3). Until then the cash moving in
 * and out of investment accounts is imported, and the QIF file itself is kept in the vault so the
 * full investment history can be read from it then.
 */
class QifImportService internal constructor(private val books: Books) {

    fun preview(content: ByteArray, currency: Currency = books.rates.baseCurrency): QifPreview {
        val file = QifParser.parse(content)
        val existing = books.accounts.list().map { it.account }
        val counts = file.transactions.groupingBy { it.account }.eachCount() + file.investments.groupingBy { it.account }.eachCount()
        val names = (file.accounts.map { it.name to it.kind } + file.transactions.map { it.account to QifAccountKind.BANK }).distinctBy { it.first }
        val plans = names.map { (name, kind) ->
            val match = existing.firstOrNull { DocumentService.similarNames(it.name, name) && FieldExtractor.fold(it.name) == FieldExtractor.fold(name) }
            QifAccountPlan(name, kind, counts[name] ?: 0, match?.id, name, typeFor(kind, name), match?.currency ?: currency)
        }
        val order = file.dateOrder ?: DateOrder.MONTH_DAY
        val dates = file.transactions.mapNotNull { it.date.toLocalDate(order) }
        return QifPreview(plans, file.transactions.size, file.investments.size, dates.minOrNull(), dates.maxOrNull(), file.dateOrder, file.warnings)
    }

    fun import(content: ByteArray, fileName: String?, groupId: String, plans: List<QifAccountPlan>, dateOrder: DateOrder, today: LocalDate): QifImportResult {
        val file = QifParser.parse(content)
        val warnings = ArrayList(file.warnings)
        val included = plans.filter { it.include }.associateBy { it.qifName }

        // Accounts, with Quicken's opening balance (a transfer of the account to itself) as the opening balance.
        val accountIds = HashMap<String, String>()
        var accountsCreated = 0
        val openings = file.transactions.filter { it.transferAccount == it.account }.associateBy { it.account }
        for (plan in included.values) {
            accountIds[plan.qifName] = plan.targetAccountId ?: run {
                val opening = openings[plan.qifName]
                val dates = file.transactions.filter { it.account == plan.qifName }.mapNotNull { it.date.toLocalDate(dateOrder) }
                val openingDate = opening?.date?.toLocalDate(dateOrder) ?: dates.minOrNull() ?: today
                accountsCreated++
                books.accounts.create(
                    AccountDraft(groupId, plan.newName.ifBlank { plan.qifName }, plan.newType, plan.currency, Money.exact(opening?.amount ?: java.math.BigDecimal.ZERO, plan.currency), openingDate),
                ).id
            }
            if (plan.targetAccountId != null && openings[plan.qifName] != null) {
                warnings += "Opening balance of \"${plan.qifName}\" not applied: it was imported into an existing account."
            }
        }

        val categories = CategoryResolver(file)
        var imported = 0
        var transfers = 0
        var already = 0
        // Quicken writes each transfer in both accounts: the first creates it, the twin is skipped.
        val pendingTwins = HashMap<String, Int>()
        val existingLines = HashMap<String, MutableMap<String, Int>>()

        fun existingCount(accountId: String): MutableMap<String, Int> = existingLines.getOrPut(accountId) {
            books.transactions.register(accountId).map { key(it.transaction.date, it.transaction.amount, it.transaction.payeeText) }.groupingBy { it }.eachCount().toMutableMap()
        }

        val ordered = file.transactions.mapNotNull { t -> t.date.toLocalDate(dateOrder)?.let { it to t } ?: run { warnings += "Invalid date in ${t.account}"; null } }.sortedBy { it.first }
        for ((date, t) in ordered) {
            if (t.transferAccount == t.account) continue
            val accountId = accountIds[t.account] ?: continue
            val account = books.accounts.get(accountId)
            val amount = Money.exact(t.amount, account.currency)

            val otherId = t.transferAccount?.let { accountIds[it] }
            if (otherId != null && t.splits.isEmpty()) {
                val other = books.accounts.get(otherId)
                val twinKey = "${date}|${amount.abs().minorUnits}|${setOf(accountId, otherId).sorted()}"
                val waiting = pendingTwins[twinKey] ?: 0
                if (waiting > 0) {
                    pendingTwins[twinKey] = waiting - 1
                    continue
                }
                if (other.currency == account.currency && !amount.isZero) {
                    // Already there from an earlier import of the same file: skip it and its twin.
                    val counts = existingCount(accountId)
                    val k = key(date, amount, null)
                    if ((counts[k] ?: 0) > 0) {
                        counts[k] = counts.getValue(k) - 1
                        if (file.transactions.any { it.account == t.transferAccount }) pendingTwins[twinKey] = waiting + 1
                        already++
                        continue
                    }
                    val draft = if (amount.isNegative) TransferDraft(accountId, otherId, date, amount.abs(), memo = t.memo ?: t.payee) else TransferDraft(otherId, accountId, date, amount, memo = t.memo ?: t.payee)
                    books.transactions.transfer(draft)
                    // The twin is expected only when the other account is in the file too.
                    if (file.transactions.any { it.account == t.transferAccount }) pendingTwins[twinKey] = waiting + 1
                    transfers++
                    continue
                }
                warnings += "Transfer between accounts in different currencies on $date imported as two separate lines."
            }

            val counts = existingCount(accountId)
            val k = key(date, amount, t.payee)
            val present = counts[k] ?: 0
            if (present > 0) {
                counts[k] = present - 1
                already++
                continue
            }

            val splits = if (t.splits.isNotEmpty()) {
                t.splits.map { s ->
                    val memo = s.memo ?: s.transferAccount?.let { "→ $it" }
                    if (s.transferAccount != null) warnings += "A split line moving money to \"${s.transferAccount}\" on $date was imported as an uncategorized line."
                    SplitDraft(s.category?.let { categories.resolve(it, s.amount.signum() > 0) }, Money.exact(s.amount, account.currency), memo)
                }
            } else {
                val category = t.category?.let { categories.resolve(it, t.amount.signum() > 0) }
                val note = t.transferAccount?.let { "→ $it" }
                if (note != null) warnings += "A transfer to \"${t.transferAccount}\", which was not imported, became an ordinary line on $date."
                listOfNotNull(if (category != null || note != null) SplitDraft(category, amount, note) else null)
            }
            books.transactions.create(
                TransactionDraft(
                    accountId, date, amount, t.payee, splits, listOfNotNull(t.memo, t.number?.let { "#$it" }).joinToString(" ").ifBlank { null },
                    cleared = when (t.cleared?.lowercaseChar()) {
                        'x', 'r' -> ClearedStatus.RECONCILED
                        '*', 'c' -> ClearedStatus.CLEARED
                        else -> ClearedStatus.UNCLEARED
                    },
                    tags = t.classes.toSet(),
                ),
            )
            imported++
        }

        // Investment history waits for the investment module; the file itself is kept.
        if (file.investments.isNotEmpty()) {
            val doc = books.documents.import(groupId, content, fileName ?: "quicken.qif", "application/qif").document
            books.documents.update(
                doc.id,
                DocumentDetails("Quicken (QIF)", null, today, "Quicken", null, keepForever = true, notes = "${file.investments.size} investment actions kept for the investment module"),
            )
            books.documents.setStatus(doc.id, DocumentStatus.FILED)
            warnings += "${file.investments.size} investment actions are kept with the imported file until the investment module arrives."
        }
        books.session.audit("IMPORT", "qif", null, "${imported + transfers} transactions")
        return QifImportResult(accountsCreated, imported, transfers, already, categories.created, file.investments.size, warnings.distinct())
    }

    private fun key(date: LocalDate, amount: Money, payee: String?) = "$date|${amount.minorUnits}|${payee?.let(FieldExtractor::fold)?.trim().orEmpty()}"

    private fun typeFor(kind: QifAccountKind, name: String): AccountType {
        val folded = FieldExtractor.fold(name)
        return when (kind) {
            QifAccountKind.CASH -> AccountType.CASH
            QifAccountKind.CREDIT_CARD -> AccountType.CREDIT_CARD
            QifAccountKind.LIABILITY -> if ("hypoth" in folded || "mortgage" in folded) AccountType.MORTGAGE else if ("marge" in folded || "line of credit" in folded) AccountType.LINE_OF_CREDIT else AccountType.LOAN
            QifAccountKind.ASSET -> AccountType.OTHER_ASSET
            QifAccountKind.INVESTMENT -> when {
                "celi" in folded || "tfsa" in folded -> AccountType.TFSA
                "reer" in folded || "rrsp" in folded -> AccountType.RRSP
                "reee" in folded || "resp" in folded -> AccountType.RESP
                "celiapp" in folded || "fhsa" in folded -> AccountType.FHSA
                else -> AccountType.BROKERAGE
            }
            QifAccountKind.BANK -> if ("epargne" in folded || "saving" in folded) AccountType.SAVINGS else AccountType.CHEQUING
        }
    }

    /** Finds or creates "Food:Groceries" level by level, matching names in English or French. */
    private inner class CategoryResolver(private val file: QifFile) {
        var created = 0
        private val cache = HashMap<String, String>()

        fun resolve(path: List<String>, positive: Boolean): String? {
            if (path.isEmpty()) return null
            cache[path.joinToString(":")]?.let { return it }
            val income = file.categories.firstOrNull { it.path.firstOrNull()?.let(FieldExtractor::fold) == FieldExtractor.fold(path.first()) }?.income ?: positive
            var parent: Category? = null
            for ((level, name) in path.withIndex()) {
                val all = books.categories.list(includeArchived = true)
                val folded = FieldExtractor.fold(name).trim()
                fun named(c: Category) = FieldExtractor.fold(c.nameEn) == folded || FieldExtractor.fold(c.nameFr) == folded
                // A Quicken top-level "Salary" may be a subcategory here (Income › Employment › Salary):
                // a name found exactly once anywhere in the tree is used rather than duplicated.
                val match = all.firstOrNull { it.parentId == parent?.id && named(it) }
                    ?: if (level == 0) all.filter(::named).singleOrNull() else null
                parent = match ?: books.categories.create(parent?.id, name, name, parent?.kind ?: if (income) CategoryKind.INCOME else CategoryKind.EXPENSE).also { created++ }
            }
            return parent!!.id.also { cache[path.joinToString(":")] = it }
        }
    }
}
