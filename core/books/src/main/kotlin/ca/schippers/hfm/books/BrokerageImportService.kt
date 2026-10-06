package ca.schippers.hfm.books

import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.importers.DateOrder
import ca.schippers.hfm.importers.ImportOptions
import ca.schippers.hfm.importers.ImportedAction
import ca.schippers.hfm.importers.ImportedInvestmentAction
import ca.schippers.hfm.importers.ImportedInvestmentStatement
import ca.schippers.hfm.importers.ImportedSecurity
import ca.schippers.hfm.importers.InvestmentImporters
import ca.schippers.hfm.importers.QifFile
import ca.schippers.hfm.importers.QifInvestment
import ca.schippers.hfm.importers.QifParser
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.FieldExtractor
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.MathContext
import java.security.MessageDigest

/**
 * INV-05 and OTH-05: brings brokerage statements (OFX, CSV) and Quicken investment history into
 * an investment account. Each action is imported once: the file's own id is kept, or for files
 * without ids a fingerprint of the line, so importing the same file again adds nothing.
 */
class BrokerageImportService internal constructor(private val books: Books) {

    private val investments get() = books.investments

    /** Reads a brokerage file with the first importer that recognises it. */
    fun read(fileName: String, content: ByteArray, defaultCurrency: Currency = books.rates.baseCurrency): List<ImportedInvestmentStatement> {
        val importer = InvestmentImporters.forFile(fileName, content.copyOf(minOf(content.size, 4096)))
            ?: throw ValidationException("error.importUnknownFormat")
        return try {
            importer.readInvestments(content.inputStream(), ImportOptions(defaultCurrency))
        } catch (e: ca.schippers.hfm.importers.ImportException) {
            throw ValidationException("error.importFailed", e.message ?: "")
        }
    }

    /** The investment account whose number ends like the statement's, if there is exactly one. */
    fun suggestAccount(statement: ImportedInvestmentStatement): String? {
        val hint = statement.accountNumberHint?.filter(Char::isLetterOrDigit)?.takeLast(4) ?: return null
        return investments.accounts().filter { it.numberMasked?.filter(Char::isLetterOrDigit)?.endsWith(hint) == true }.singleOrNull()?.id
    }

    /**
     * Imports [statement] into [accountId]: new securities, the actions not already there, prices
     * from the file, and the statement's holdings and cash for reconciliation (REC-08).
     */
    fun import(accountId: String, statement: ImportedInvestmentStatement): InvestmentImportResult {
        val account = books.accounts.get(accountId)
        validate(account.type.kind == AccountKind.INVESTMENT, "error.notInvestmentAccount")
        val warnings = ArrayList(statement.warnings.map(books::note))
        val group = books.accounts.locate(accountId).first
        var created = 0
        val ids = statement.securities.associate { s ->
            val (security, isNew) = resolve(s, statement.currency, group.id)
            if (isNew) created++
            s.key to security.id
        }
        for (s in statement.securities) {
            val price = s.price ?: continue
            val date = s.priceDate ?: continue
            if (price.signum() > 0) investments.setPrice(ids.getValue(s.key), date, price, IMPORTED)
        }
        val txns = investments.transactions(accountId)
        val existing = txns.mapNotNull { it.externalId }.toMutableSet()
        // Documents read by AI carry no ids of their own: a trade or income already entered by hand,
        // or imported from another document, is matched by what it is (INV-05).
        val unmatched = if (statement.format == AI_FORMAT) txns.toMutableList() else mutableListOf()
        val seen = HashMap<String, Int>()
        var added = 0
        var already = 0
        for (a in statement.actions.sortedBy { it.date }) {
            val external = a.externalId ?: fingerprint(a, seen)
            if (external in existing) { already++; continue }
            val twin = unmatched.firstOrNull { same(it, a, a.securityKey?.let(ids::get), account.currency) }
            if (twin != null) { unmatched -= twin; already++; continue }
            runCatching { add(account, a, a.securityKey?.let(ids::get), external, warnings) }
                .onSuccess { ok -> if (ok) { added++; existing += external } else already++ }
                .onFailure { e -> warnings += "${a.date} ${a.action}: ${(e as? ValidationException)?.message(books.language) ?: e.message}" }
        }
        val saved = statement.asOf != null && (statement.positions.isNotEmpty() || statement.cash != null)
        val statementId = if (saved) {
            investments.saveStatement(
                accountId, statement.asOf!!, statement.cash?.let { Money.of(it, account.currency) },
                statement.positions.mapNotNull { p -> ids[p.securityKey]?.let { it to p.quantity } }.toMap(), statement.format,
            ).id
        } else {
            null
        }
        books.session.audit("IMPORT", "investments", accountId, "$added actions")
        return InvestmentImportResult(added, already, created, saved, warnings.distinct(), statementId)
    }

    /**
     * Whether [txn], already in the books, is the action [a]: same kind and security, within three
     * days (a trade date against a settlement date), and the same units, or for income and fees
     * the same amount.
     */
    private fun same(txn: InvestmentTxn, a: ImportedInvestmentAction, securityId: String?, currency: Currency): Boolean {
        val kind = when (a.action) {
            ImportedAction.BUY -> InvestmentKind.BUY
            ImportedAction.SELL -> InvestmentKind.SELL
            ImportedAction.DIVIDEND, ImportedAction.INTEREST, ImportedAction.DISTRIBUTION -> InvestmentKind.INCOME
            ImportedAction.REINVEST -> InvestmentKind.REINVEST
            ImportedAction.RETURN_OF_CAPITAL -> InvestmentKind.RETURN_OF_CAPITAL
            ImportedAction.FEE -> InvestmentKind.FEE
            else -> return false
        }
        if (txn.kind != kind || txn.securityId != securityId) return false
        if (kotlin.math.abs(txn.date.toEpochDays() - a.date.toEpochDays()) > 3) return false
        val units = a.quantity
        return if (kind in setOf(InvestmentKind.BUY, InvestmentKind.SELL, InvestmentKind.REINVEST) && units != null) {
            txn.quantity?.let { (it - units).abs() < BigDecimal("0.0001") } == true
        } else {
            a.amount?.let { txn.amount == Money.of(it, currency) } == true
        }
    }

    /** Adds one imported action; cash deposits and withdrawals become ordinary register lines. */
    private fun add(account: Account, a: ImportedInvestmentAction, securityId: String?, external: String, warnings: MutableList<String>): Boolean {
        val c = account.currency
        fun m(v: BigDecimal?) = v?.let { Money.of(it, c) } ?: Money.zero(c)
        val base = InvestmentTxn(
            "", account.id, a.date, InvestmentKind.BUY, securityId, a.quantity, a.price, m(a.amount), m(a.fees), m(a.withheld),
            externalId = external, memo = a.memo,
        )
        val txn = when (a.action) {
            ImportedAction.BUY -> base
            ImportedAction.SELL -> base.copy(kind = InvestmentKind.SELL)
            ImportedAction.DIVIDEND -> base.copy(kind = InvestmentKind.INCOME, incomeType = IncomeType.DIVIDEND, quantity = null, price = null)
            ImportedAction.INTEREST -> base.copy(kind = InvestmentKind.INCOME, incomeType = IncomeType.INTEREST, quantity = null, price = null)
            ImportedAction.DISTRIBUTION -> base.copy(kind = InvestmentKind.INCOME, incomeType = IncomeType.DISTRIBUTION, quantity = null, price = null)
            ImportedAction.REINVEST -> base.copy(kind = InvestmentKind.REINVEST, incomeType = incomeOf(a.incomeAction))
            ImportedAction.RETURN_OF_CAPITAL -> base.copy(kind = InvestmentKind.RETURN_OF_CAPITAL)
            ImportedAction.SPLIT -> base.copy(kind = InvestmentKind.SPLIT, ratio = a.ratio)
            ImportedAction.TRANSFER_IN -> {
                warnings += books.text("importNote.unitsAtMarket", a.date.toString())
                base.copy(kind = InvestmentKind.TRANSFER_IN, amount = m(a.quantity?.let { q -> a.price?.let { q.multiply(it) } }))
            }
            ImportedAction.TRANSFER_OUT -> base.copy(kind = InvestmentKind.TRANSFER_OUT)
            ImportedAction.FEE -> base.copy(kind = InvestmentKind.FEE, quantity = null, price = null)
            ImportedAction.CASH_IN, ImportedAction.CASH_OUT -> {
                val amount = m(a.amount).let { if (a.action == ImportedAction.CASH_OUT) -it else it }
                if (books.transactions.register(account.id).any { it.transaction.date == a.date && it.transaction.amount == amount }) return false
                books.transactions.create(TransactionDraft(account.id, a.date, amount, a.memo, memo = a.memo))
                return true
            }
        }
        investments.save(txn)
        return true
    }

    private fun incomeOf(action: ImportedAction?) = when (action) {
        ImportedAction.INTEREST -> IncomeType.INTEREST
        ImportedAction.DISTRIBUTION -> IncomeType.DISTRIBUTION
        else -> IncomeType.DIVIDEND
    }

    /** The existing security for an imported one, or a new one in [groupId]. */
    private fun resolve(s: ImportedSecurity, currency: Currency, groupId: String): Pair<Security, Boolean> {
        val (symbol, exchange) = splitSymbol(s.symbol)
        investments.findSecurity(symbol, s.name)?.let { return it to false }
        val kind = when {
            s.kind == "MUTUAL_FUND" -> SecurityKind.MUTUAL_FUND
            s.kind == "BOND" -> SecurityKind.BOND
            s.kind == "OPTION" -> SecurityKind.OPTION
            Regex("""\b(ETF|FNB)\b""", RegexOption.IGNORE_CASE).containsMatchIn(s.name) || s.name.contains("ishares", ignoreCase = true) -> SecurityKind.ETF
            s.kind == "OTHER" -> SecurityKind.OTHER
            else -> SecurityKind.STOCK
        }
        val security = investments.saveSecurity(
            Security(
                "", symbol, exchange, s.name, kind, currency,
                assetClass = if (kind == SecurityKind.BOND) AssetClass.FIXED_INCOME else AssetClass.EQUITY,
                multiplier = when (kind) {
                    SecurityKind.OPTION -> BigDecimal(100)
                    SecurityKind.BOND -> BigDecimal("0.01")
                    else -> BigDecimal.ONE
                },
                notes = s.identifier?.takeIf { it != symbol }?.let { "CUSIP/ISIN $it" },
            ),
            groupId,
        )
        return security to true
    }

    // --- Quicken (OTH-05) -----------------------------------------------------------------------

    /**
     * Imports the investment actions of a QIF file into the accounts given by QIF account name.
     * Cash moved in or out with another account in the file was already imported as a transfer
     * from that account's side; the rest becomes ordinary lines.
     */
    internal fun importQif(file: QifFile, dateOrder: DateOrder, accountIds: Map<String, String>, importedAccounts: Set<String>): InvestmentImportResult {
        val warnings = ArrayList<String>()
        var added = 0
        var already = 0
        var created = 0
        val bySecurity = file.securities.associateBy { FieldExtractor.fold(it.name) }
        for ((qifAccount, actions) in file.investments.groupBy { it.account }) {
            val accountId = accountIds[qifAccount] ?: continue
            val account = books.accounts.get(accountId)
            if (account.type.kind != AccountKind.INVESTMENT) {
                warnings += books.text("importNote.notInvestment", qifAccount, books.text("accountType.${account.type.name}"))
                continue
            }
            val group = books.accounts.locate(accountId).first
            val securityIds = HashMap<String, String>()
            fun securityFor(name: String): String = securityIds.getOrPut(name) {
                val listed = bySecurity[FieldExtractor.fold(name)]
                val type = listed?.type?.lowercase().orEmpty()
                val imported = ImportedSecurity(
                    name, listed?.symbol, name, null,
                    when {
                        "mutual" in type || "fund" in type -> "MUTUAL_FUND"
                        "bond" in type || "cd" in type -> "BOND"
                        "option" in type -> "OPTION"
                        else -> "STOCK"
                    },
                    null, null,
                )
                val (security, isNew) = resolve(imported, account.currency, group.id)
                if (isNew) created++
                security.id
            }
            val existing = investments.transactions(accountId).mapNotNull { it.externalId }.toMutableSet()
            val seen = HashMap<String, Int>()
            val dated = actions.mapNotNull { a -> a.date.toLocalDate(dateOrder)?.let { it to a } ?: run { warnings += books.text("importNote.invalidDate", qifAccount); null } }
            for ((date, a) in dated.sortedBy { it.first }) {
                val action = qifAction(a, date, warnings) ?: continue
                if (action.action in setOf(ImportedAction.CASH_IN, ImportedAction.CASH_OUT) && a.transferAccount != null && a.transferAccount in importedAccounts) continue
                val external = "qif:" + fingerprint(action.copy(securityKey = a.security), seen)
                if (external in existing) { already++; continue }
                runCatching { add(account, action, a.security?.let(::securityFor), external, warnings) }
                    .onSuccess { ok -> if (ok) { added++; existing += external } else already++ }
                    .onFailure { e -> warnings += "$date ${a.action} ${a.security.orEmpty()}: ${(e as? ValidationException)?.message(books.language) ?: e.message}" }
            }
        }
        return InvestmentImportResult(added, already, created, false, warnings.distinct())
    }

    /** Quicken documents kept by an earlier import whose investment history has not been read yet. */
    fun keptQuickenFiles(): List<VaultDocument> = runCatching { books.documents.search(DocumentQuery(text = "Quicken")) }.getOrDefault(emptyList())
        .filter { it.mimeType == "application/qif" && it.notes?.contains(KEPT_NOTE) == true }

    /**
     * Reads the investment history from a kept Quicken file into the investment accounts with the
     * same names, and marks the file as read.
     */
    fun importKeptQuickenFile(documentId: String, dateOrder: DateOrder? = null): InvestmentImportResult {
        val file = QifParser.parse(books.documents.content(documentId))
        val accounts = investments.accounts(includeClosed = true)
        val names = file.investments.map { it.account }.distinct()
        val map = names.mapNotNull { n -> accounts.firstOrNull { FieldExtractor.fold(it.name) == FieldExtractor.fold(n) }?.let { n to it.id } }.toMap()
        val missing = names.filter { it !in map }
        val all = books.accounts.all(includeClosed = true).map { FieldExtractor.fold(it.name) }.toSet()
        val imported = file.accounts.map { it.name }.filter { FieldExtractor.fold(it) in all }.toSet() + file.transactions.map { it.account }.toSet()
        val result = importQif(file, dateOrder ?: file.dateOrder ?: DateOrder.MONTH_DAY, map, imported)
        val doc = books.documents.get(documentId)
        books.documents.update(documentId, DocumentDetails(doc.title ?: "Quicken (QIF)", doc.kind, doc.date, doc.merchant, doc.amount, doc.keepForever, notes = READ_NOTE))
        return result.copy(warnings = missing.map { "No investment account named \"$it\"; its actions were not imported." } + result.warnings)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** A stable id for a line without one; identical lines on the same day are numbered. */
    private fun fingerprint(a: ImportedInvestmentAction, seen: MutableMap<String, Int>): String {
        val text = listOf(a.date, a.action, a.securityKey, a.quantity?.stripTrailingZeros()?.toPlainString(), a.amount?.stripTrailingZeros()?.toPlainString()).joinToString("|")
        val n = seen.merge(text, 1, Int::plus)!!
        val digest = MessageDigest.getInstance("SHA-256").digest("$text#$n".toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    /** "XIC.TO" is XIC on the Toronto exchange; "XIC" stays as it is. */
    private fun splitSymbol(symbol: String?): Pair<String?, String?> {
        val s = symbol?.trim()?.uppercase()?.ifEmpty { null } ?: return null to null
        val exchange = mapOf(".TO" to "TSX", ".V" to "TSXV", ".NE" to "NEO", ".CN" to "CSE")
        exchange.entries.firstOrNull { s.endsWith(it.key) }?.let { return s.removeSuffix(it.key) to it.value }
        return s to null
    }

    /** Quicken's action names, with or without the trailing X that names another account. */
    private fun qifAction(a: QifInvestment, date: LocalDate, warnings: MutableList<String>): ImportedInvestmentAction? {
        val name = a.action.trim().lowercase()
        val base = if (name.endsWith("x") && name !in setOf("cash")) name.dropLast(1) else name
        val q = a.quantity?.abs()
        val price = a.price?.abs()
        val total = a.amount?.abs()
        val fees = a.commission?.abs()?.takeIf { it.signum() != 0 }
        fun gross(buy: Boolean) = if (q != null && price != null) q.multiply(price) else total?.let { if (buy) it - (fees ?: BigDecimal.ZERO) else it + (fees ?: BigDecimal.ZERO) }
        fun act(action: ImportedAction, amount: BigDecimal? = total, qty: BigDecimal? = null, px: BigDecimal? = null, income: ImportedAction? = null, ratio: BigDecimal? = null) =
            ImportedInvestmentAction(null, date, action, a.security, qty, px, amount, if (action in setOf(ImportedAction.BUY, ImportedAction.SELL, ImportedAction.REINVEST)) fees else null, null, ratio, income, a.memo)
        return when (base) {
            "buy" -> act(ImportedAction.BUY, gross(true), q, price)
            "sell" -> act(ImportedAction.SELL, gross(false), q, price)
            "div" -> act(ImportedAction.DIVIDEND)
            "intinc" -> act(ImportedAction.INTEREST)
            "cglong", "cgshort", "cgmid", "miscinc" -> act(ImportedAction.DISTRIBUTION)
            "reinvdiv" -> act(ImportedAction.REINVEST, total, q, price, ImportedAction.DIVIDEND)
            "reinvint" -> act(ImportedAction.REINVEST, total, q, price, ImportedAction.INTEREST)
            "reinvlg", "reinvsh", "reinvmd" -> act(ImportedAction.REINVEST, total, q, price, ImportedAction.DISTRIBUTION)
            "rtrncap" -> act(ImportedAction.RETURN_OF_CAPITAL)
            // Quicken writes a split as new shares per 10 old ones.
            "stksplit" -> act(ImportedAction.SPLIT, null, ratio = q?.divide(BigDecimal.TEN, MathContext.DECIMAL64))
            "shrsin" -> act(ImportedAction.TRANSFER_IN, total, q, price ?: total?.let { t -> q?.takeIf { it.signum() > 0 }?.let { t.divide(it, MathContext.DECIMAL64) } })
            "shrsout" -> act(ImportedAction.TRANSFER_OUT, null, q, price)
            "miscexp", "margint" -> act(ImportedAction.FEE)
            "xin", "contrib", "cash" -> if ((a.amount ?: BigDecimal.ZERO).signum() < 0 && base == "cash") act(ImportedAction.CASH_OUT) else act(ImportedAction.CASH_IN)
            "xout", "withdrw" -> act(ImportedAction.CASH_OUT)
            else -> { warnings += books.text("importNote.qifAction", date.toString(), a.action); null }
        }
    }

    companion object {
        const val IMPORTED = "IMPORT"

        /** The format of statements and trade confirmations read by AI. */
        const val AI_FORMAT = "AI"
        const val KEPT_NOTE = "investment actions kept for the investment module"
        const val READ_NOTE = "Investment history imported"
    }
}
