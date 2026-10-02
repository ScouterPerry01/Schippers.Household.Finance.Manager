package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.CostBase
import ca.schippers.hfm.calc.invest.CostBaseException
import ca.schippers.hfm.calc.invest.CostEvent
import ca.schippers.hfm.calc.invest.CostEventKind
import ca.schippers.hfm.calc.invest.CostPool
import ca.schippers.hfm.calc.invest.Disposition
import ca.schippers.hfm.calc.invest.normalized
import ca.schippers.hfm.data.ledger.LedgerDatabase
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.MathContext
import ca.schippers.hfm.data.ledger.Inv_txn as InvRow
import ca.schippers.hfm.data.ledger.Security as SecurityRow

enum class SecurityKind { STOCK, ETF, MUTUAL_FUND, BOND, GIC, OPTION, OTHER }
enum class AssetClass { EQUITY, FIXED_INCOME, CASH, BALANCED, REAL_ESTATE, COMMODITY, OTHER }
enum class Region { CANADA, US, INTERNATIONAL, EMERGING, GLOBAL, OTHER }

/**
 * INV-01: a security. The same id is used in every account group that holds it, so prices and
 * changes follow it everywhere the user can edit. [multiplier] turns quantity times price into
 * value: 100 for option contracts, 0.01 for bonds quoted per 100 of face value.
 */
data class Security(
    val id: String,
    val symbol: String?,
    val exchange: String?,
    val name: String,
    val kind: SecurityKind,
    val currency: Currency,
    val assetClass: AssetClass = AssetClass.EQUITY,
    val region: Region = Region.CANADA,
    val multiplier: BigDecimal = BigDecimal.ONE,
    val maturity: LocalDate? = null,
    val couponRate: BigDecimal? = null,
    val notes: String? = null,
    val archived: Boolean = false,
) {
    /** "XIC" or the name when there is no symbol. */
    val label: String get() = symbol ?: name
}

/** INV-02 transaction kinds. Income carries an [IncomeType]. */
enum class InvestmentKind { BUY, SELL, INCOME, REINVEST, RETURN_OF_CAPITAL, NOTIONAL_DISTRIBUTION, SPLIT, MERGER, TRANSFER_IN, TRANSFER_OUT, FEE }
enum class IncomeType { DIVIDEND, INTEREST, DISTRIBUTION }

/**
 * One investment transaction. [amount] is the gross value in the account's currency (quantity times
 * price, the income, the return of capital, the fee, or for shares moved in, their book cost);
 * [fees] is the commission and [withheld] the foreign tax withheld. For a split or merger, [ratio]
 * is the new units per old unit; a merger exchanges [quantity] units of [securityId] for units of
 * [otherSecurityId].
 */
data class InvestmentTxn(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val kind: InvestmentKind,
    val securityId: String? = null,
    val quantity: BigDecimal? = null,
    val price: BigDecimal? = null,
    val amount: Money,
    val fees: Money = Money.zero(amount.currency),
    val withheld: Money = Money.zero(amount.currency),
    val incomeType: IncomeType? = null,
    val ratio: BigDecimal? = null,
    val otherSecurityId: String? = null,
    val externalId: String? = null,
    val memo: String? = null,
) {
    /** What the transaction does to the account's cash. */
    val cashEffect: Money
        get() = when (kind) {
            InvestmentKind.BUY -> -(amount + fees)
            InvestmentKind.SELL -> amount - fees
            InvestmentKind.INCOME -> amount - withheld
            InvestmentKind.REINVEST -> -fees - withheld
            InvestmentKind.RETURN_OF_CAPITAL -> amount
            InvestmentKind.FEE -> -amount
            else -> Money.zero(amount.currency)
        }
}

/** A position in one account. Book cost and value are in the account's currency. */
data class Holding(
    val accountId: String,
    val security: Security,
    val quantity: BigDecimal,
    val bookCost: Money,
    val price: BigDecimal?,
    val priceDate: LocalDate?,
    /** Null when there is no price, or no exchange rate for the security's currency. */
    val marketValue: Money?,
) {
    val gain: Money? get() = marketValue?.let { it - bookCost }
}

data class AccountHoldings(val account: Account, val cash: Money, val holdings: List<Holding>, val metals: Money? = null) {
    private val zero get() = Money.zero(account.currency)
    val bookCost: Money get() = holdings.map { it.bookCost }.sum(account.currency)

    /** Cash plus holdings at market value (a holding without a price at its book cost), plus precious metals (PM-02). */
    val totalValue: Money get() = cash + holdings.map { it.marketValue ?: it.bookCost }.fold(zero) { a, b -> a + b } + (metals ?: zero)
    val missingPrices: List<Security> get() = holdings.filter { it.marketValue == null }.map { it.security }
}

/**
 * INV-03: the adjusted cost base of one security across every non-registered account with the
 * same owners, in the household's base currency.
 */
data class AcbPool(val security: Security, val ownerMemberIds: Set<String>, val accountIds: Set<String>, val quantity: BigDecimal, val acb: Money) {
    val perUnit: BigDecimal? get() = if (quantity.signum() == 0) null else acb.toBigDecimal().divide(quantity, MathContext.DECIMAL64)
}

data class CapitalGain(val security: Security, val ownerMemberIds: Set<String>, val disposition: Disposition)

data class AcbReport(val pools: List<AcbPool>, val gains: List<CapitalGain>, val missingRates: Set<Currency>, val problems: List<String>)

/** REC-08: one line of a brokerage statement against the books. */
data class PositionCheck(val security: Security, val statement: BigDecimal, val books: BigDecimal) {
    val matches: Boolean get() = statement.compareTo(books) == 0
}

data class InvestmentStatement(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val cash: Money?,
    val positions: Map<String, BigDecimal>,
    val reconciled: Boolean,
    val source: String?,
)

data class StatementCheck(val statement: InvestmentStatement, val booksCash: Money, val positions: List<PositionCheck>) {
    val cashMatches: Boolean get() = statement.cash == null || statement.cash == booksCash
    val matches: Boolean get() = cashMatches && positions.all { it.matches }
}

data class InvestmentImportResult(val added: Int, val alreadyThere: Int, val securitiesCreated: Int, val statementSaved: Boolean, val warnings: List<String>)

/** INV-01 to INV-05, REC-08: securities, prices, investment transactions, holdings, ACB and imports. */
class InvestmentService internal constructor(private val books: Books) {

    // --- Securities (INV-01) ----------------------------------------------------------------------

    /** Every security in the groups the user can see, once each. */
    fun securities(includeArchived: Boolean = false): List<Security> =
        books.groups().flatMap { g -> books.ledger(g).investmentsQueries.securities().executeAsList().map { it.toSecurity() } }
            .distinctBy { it.id }.filter { includeArchived || !it.archived }.sortedBy { it.name.lowercase() }

    fun security(id: String): Security = securities(includeArchived = true).firstOrNull { it.id == id } ?: throw ValidationException("error.securityNotFound")

    /**
     * Saves a security in every group that already holds it, or for a new one in [groupId] (by
     * default the first group the user can edit).
     */
    fun saveSecurity(security: Security, groupId: String? = null): Security {
        validate(security.name.isNotBlank(), "error.nameRequired")
        validate(security.multiplier.signum() > 0, "error.invalidNumber")
        val s = security.copy(
            id = security.id.ifBlank { Ids.newId() }, symbol = security.symbol.blankToNull()?.uppercase(), exchange = security.exchange.blankToNull()?.uppercase(),
            name = security.name.trim(), notes = security.notes.blankToNull(),
        )
        val holders = editableGroups().filter { has(it, s.id) }
        val targets = holders.ifEmpty { listOf(groupId?.let(books::group) ?: editableGroups().firstOrNull() ?: throw ValidationException("error.accessDenied")) }
        targets.forEach { g -> books.require(g, PermissionLevel.EDIT); write(books.ledger(g), s) }
        return s
    }

    /** Finds a security by symbol (and exchange and currency when given) or by name, among those the user can see. */
    fun findSecurity(symbol: String?, name: String?, currency: Currency? = null): Security? {
        val all = securities(includeArchived = true)
        val sym = symbol.blankToNull()?.uppercase()
        return sym?.let { s -> all.firstOrNull { it.symbol == s && (currency == null || it.currency == currency) } ?: all.firstOrNull { it.symbol == s } }
            ?: name.blankToNull()?.let { n -> all.firstOrNull { it.name.equals(n, ignoreCase = true) } }
    }

    fun deleteSecurity(id: String) {
        for (g in editableGroups()) {
            val q = books.ledger(g).investmentsQueries
            if (q.securityById(id).executeAsOneOrNull() == null) continue
            validate(q.securityUseCount(id).executeAsOne() == 0L, "error.securityInUse")
            q.deleteSecurity(id)
        }
    }

    // --- Prices (INV-04) ----------------------------------------------------------------------------

    /** Records a price in every group that holds the security. Manual prices are never replaced by downloads. */
    fun setPrice(securityId: String, date: LocalDate, price: BigDecimal, source: String = MANUAL) {
        validate(price.signum() > 0, "error.invalidNumber")
        val groups = editableGroups().filter { has(it, securityId) }
        validate(groups.isNotEmpty(), "error.securityNotFound")
        for (g in groups) {
            val q = books.ledger(g).investmentsQueries
            val existing = q.prices(securityId).executeAsList().firstOrNull { it.date == date.toString() }
            if (source != MANUAL && existing?.source == MANUAL) continue
            q.putPrice(securityId, date.toString(), price.normalized().toPlainString(), source)
        }
    }

    fun deletePrice(securityId: String, date: LocalDate) =
        editableGroups().forEach { books.ledger(it).investmentsQueries.deletePrice(securityId, date.toString()) }

    /** The latest price on or before [date], from any group that holds the security. */
    fun price(securityId: String, date: LocalDate): Pair<LocalDate, BigDecimal>? =
        books.groups().mapNotNull { g -> books.ledger(g).investmentsQueries.priceOnOrBefore(securityId, date.toString()).executeAsOneOrNull() }
            .maxByOrNull { it.date }?.let { LocalDate.parse(it.date) to BigDecimal(it.price) }

    fun prices(securityId: String): List<Pair<LocalDate, BigDecimal>> =
        books.groups().flatMap { g -> books.ledger(g).investmentsQueries.prices(securityId).executeAsList() }
            .distinctBy { it.date }.sortedByDescending { it.date }.map { LocalDate.parse(it.date) to BigDecimal(it.price) }

    // --- Transactions (INV-02) ----------------------------------------------------------------------

    /** Investment and other accounts that hold securities. */
    fun accounts(includeClosed: Boolean = false): List<Account> =
        books.accounts.list(includeClosed).map { it.account }.filter { it.type.kind == AccountKind.INVESTMENT }

    fun transactions(accountId: String): List<InvestmentTxn> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).investmentsQueries.invTxnsForAccount(accountId).executeAsList().map { it.toTxn(account.currency) }
    }

    /**
     * Records [txn] (a new one when its id is blank, otherwise a replacement), with its cash lines
     * in the account's register: trades as lines that reports leave out, income under its
     * investment income category, fees and foreign tax as expenses.
     */
    fun save(txn: InvestmentTxn, confirmReconciled: Boolean = false): InvestmentTxn {
        val (group, account) = books.accounts.locate(txn.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type.kind == AccountKind.INVESTMENT, "error.notInvestmentAccount")
        check(txn, account)
        val ledger = books.ledger(group)
        listOfNotNull(txn.securityId, txn.otherSecurityId).forEach { ensureIn(group, it) }
        val id = txn.id.ifBlank { Ids.newId() }
        val saved = txn.copy(id = id, memo = txn.memo.blankToNull(), quantity = txn.quantity?.normalized(), price = txn.price?.normalized(), ratio = txn.ratio?.normalized())
        // The holdings must stay possible: nothing sold or moved out that is not held.
        requirePossible(account, transactions(account.id).filter { it.id != id } + saved)
        val previous = ledger.investmentsQueries.invTxnById(id).executeAsOneOrNull()
        if (previous != null) books.transactions.deleteForInvestment(group, id, confirmReconciled)
        val now = books.now()
        ledger.investmentsQueries.insertInvTxn(
            id, account.id, saved.date.toString(), saved.kind.name, saved.incomeType?.name, saved.securityId, saved.quantity?.toPlainString(),
            saved.price?.toPlainString(), saved.amount.minorUnits, saved.fees.minorUnits, saved.withheld.minorUnits, saved.ratio?.toPlainString(),
            saved.otherSecurityId, saved.externalId, saved.memo, previous?.created_at ?: now, now,
        )
        writeLines(account, saved)
        books.session.audit(if (previous == null) "CREATE" else "UPDATE", "inv_txn", id)
        return saved
    }

    fun delete(accountId: String, txnId: String, confirmReconciled: Boolean = false) {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        val q = books.ledger(group).investmentsQueries
        if (q.invTxnById(txnId).executeAsOneOrNull() == null) return
        requirePossible(account, transactions(accountId).filter { it.id != txnId })
        books.transactions.deleteForInvestment(group, txnId, confirmReconciled)
        q.deleteInvTxn(txnId)
        books.session.audit("DELETE", "inv_txn", txnId)
    }

    private fun requirePossible(account: Account, txns: List<InvestmentTxn>) {
        val zero = Money.zero(account.currency)
        val pools = HashMap<String, CostPool>()
        try {
            txns.sortedBy { it.date }.forEach { t -> applyTo(t, { pools.getOrPut(it) { CostPool(zero) } }, { it }) }
        } catch (_: CostBaseException) {
            throw ValidationException("error.notEnoughUnits")
        }
    }

    private fun check(t: InvestmentTxn, account: Account) {
        val c = account.currency
        listOf(t.amount, t.fees, t.withheld).forEach { validate(it.currency == c, "error.currencyMismatch", c.code) }
        validate(!t.amount.isNegative && !t.fees.isNegative && !t.withheld.isNegative, "error.amountPositive")
        val needsSecurity = t.kind !in setOf(InvestmentKind.FEE, InvestmentKind.INCOME)
        validate(!needsSecurity || t.securityId != null, "error.securityRequired")
        val needsQuantity = t.kind in setOf(InvestmentKind.BUY, InvestmentKind.SELL, InvestmentKind.REINVEST, InvestmentKind.TRANSFER_IN, InvestmentKind.TRANSFER_OUT, InvestmentKind.MERGER)
        validate(!needsQuantity || (t.quantity != null && t.quantity.signum() > 0), "error.quantityRequired")
        validate(t.kind !in setOf(InvestmentKind.SPLIT, InvestmentKind.MERGER) || (t.ratio != null && t.ratio.signum() > 0), "error.ratioRequired")
        validate(t.kind != InvestmentKind.MERGER || (t.otherSecurityId != null && t.otherSecurityId != t.securityId), "error.mergerTarget")
        validate(t.kind != InvestmentKind.REINVEST || t.incomeType != null, "error.incomeType")
        validate(t.withheld <= t.amount, "error.withheldTooLarge")
    }

    /** The register lines for [t]: trades left out of reports, income and costs categorized. */
    private fun writeLines(account: Account, t: InvestmentTxn) {
        val payee = t.securityId?.let { id -> runCatching { security(id) }.getOrNull() }?.label
        fun line(amount: Money, splits: List<SplitDraft>, trade: Boolean, memo: String?) {
            if (amount.isZero && splits.all { it.amount.isZero }) return
            books.transactions.createForInvestment(TransactionDraft(account.id, t.date, amount, payee, splits, memo ?: t.memo), t.id, trade)
        }
        val qty = t.quantity?.toPlainString()
        when (t.kind) {
            InvestmentKind.BUY -> line(t.cashEffect, emptyList(), true, describe(books.text("generated.invBuy"), qty, t.price))
            InvestmentKind.SELL -> line(t.cashEffect, emptyList(), true, describe(books.text("generated.invSell"), qty, t.price))
            InvestmentKind.RETURN_OF_CAPITAL -> line(t.amount, emptyList(), true, books.text("generated.roc"))
            InvestmentKind.INCOME, InvestmentKind.REINVEST -> {
                val splits = listOfNotNull(
                    SplitDraft(category(incomeKey(t.incomeType)), t.amount),
                    t.withheld.takeIf { it.isPositive }?.let { SplitDraft(category("taxes.foreign_tax"), -it) },
                )
                line(t.amount - t.withheld, splits, false, null)
                if (t.kind == InvestmentKind.REINVEST) line(-(t.amount + t.fees), emptyList(), true, describe(books.text("generated.reinvested"), qty, t.price))
            }
            InvestmentKind.FEE -> line(-t.amount, listOf(SplitDraft(category("financial.investment_fees"), -t.amount)), false, null)
            else -> Unit
        }
    }

    private fun describe(what: String, qty: String?, price: BigDecimal?) =
        listOfNotNull(what, qty, price?.let { "@ ${it.toPlainString()}" }).joinToString(" ")

    private fun incomeKey(type: IncomeType?) = when (type) {
        IncomeType.DIVIDEND -> "income.investment.dividends"
        IncomeType.INTEREST -> "income.investment.interest"
        IncomeType.DISTRIBUTION -> "income.investment.distributions"
        null -> "income.investment"
    }

    private fun category(key: String): String? = books.categories.list().firstOrNull { it.systemKey == key }?.id

    // --- Holdings ---------------------------------------------------------------------------------

    /** The account's cash and positions on [date] (today by default), at the latest prices. */
    fun holdings(accountId: String, date: LocalDate): AccountHoldings {
        val (group, account) = books.accounts.locate(accountId)
        val cash = Money.ofMinor(account.openingBalance.minorUnits + books.ledger(group).investmentsQueries.balanceOn(accountId, date.toString()).executeAsOne(), account.currency)
        val metals = if (account.type == AccountType.PRECIOUS_METALS) books.metals.value(accountId, date) else null
        return AccountHoldings(account, cash, holdingsFrom(account, transactions(accountId), date), metals)
    }

    /** Every investment account's holdings on [date]. */
    fun allHoldings(date: LocalDate): List<AccountHoldings> = accounts().map { holdings(it.id, date) }

    /** Market value of the account's securities on [date], in its currency (cash not included); for net worth. */
    fun securitiesValue(accountId: String, date: LocalDate): Money {
        val account = books.accounts.get(accountId)
        val metals = if (account.type == AccountType.PRECIOUS_METALS) books.metals.value(accountId, date) else Money.zero(account.currency)
        return holdingsFrom(account, transactions(accountId), date).map { it.marketValue ?: it.bookCost }.sum(account.currency) + metals
    }

    /**
     * The market value of the account's securities (and precious metals) on each of [dates], in
     * its currency, replaying its history once; for net worth and returns over many dates (NFR-02).
     * Values the same way as [securitiesValue].
     */
    internal fun securitiesValues(account: Account, dates: List<LocalDate>, book: PriceBook = PriceBook()): List<Money> {
        val zero = Money.zero(account.currency)
        val txns = transactions(account.id)
        val pools = LinkedHashMap<String, CostPool>()
        val sorted = dates.withIndex().sortedBy { it.value }
        val out = arrayOfNulls<Money>(dates.size)
        var next = 0
        for ((i, date) in sorted) {
            while (next < txns.size && txns[next].date <= date) applyTo(txns[next++], { pools.getOrPut(it) { CostPool(zero) } }, { it })
            var value = zero
            for ((sid, p) in pools) {
                val state = p.state
                if (state.quantity.signum() == 0) continue
                value += book.value(sid, state.quantity, account.currency, date) ?: state.cost
            }
            if (account.type == AccountType.PRECIOUS_METALS) value += books.metals.value(account.id, date)
            out[i] = value
        }
        return out.map { it!! }
    }

    /** Securities, prices and exchange rates read once, for valuing holdings on many dates. */
    internal inner class PriceBook {
        val securities: Map<String, Security> = securities(includeArchived = true).associateBy { it.id }
        private val prices = HashMap<String, List<Pair<LocalDate, BigDecimal>>>()
        private val rates = HashMap<Triple<Currency, Currency, LocalDate>, BigDecimal?>()

        /** The latest price on or before [date]. */
        fun price(securityId: String, date: LocalDate): BigDecimal? {
            val list = prices.getOrPut(securityId) { prices(securityId).reversed() }
            var lo = 0
            var hi = list.size - 1
            var found: BigDecimal? = null
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (list[mid].first <= date) { found = list[mid].second; lo = mid + 1 } else hi = mid - 1
            }
            return found
        }

        fun rate(from: Currency, to: Currency, date: LocalDate): BigDecimal? =
            if (from == to) BigDecimal.ONE else rates.getOrPut(Triple(from, to, date)) { books.rates.rate(from, to, date) }

        /** [quantity] units at the price on [date], in [currency]; null without a price or a rate. */
        fun value(securityId: String, quantity: BigDecimal, currency: Currency, date: LocalDate): Money? {
            val security = securities[securityId] ?: return null
            val px = price(securityId, date) ?: return null
            val native = Money.of(quantity.multiply(px).multiply(security.multiplier), security.currency)
            return if (native.currency == currency) native else rate(native.currency, currency, date)?.let { native.convert(currency, it) }
        }
    }

    private fun holdingsFrom(account: Account, txns: List<InvestmentTxn>, date: LocalDate): List<Holding> {
        val zero = Money.zero(account.currency)
        val pools = LinkedHashMap<String, CostPool>()
        fun pool(id: String) = pools.getOrPut(id) { CostPool(zero) }
        for (t in txns.filter { it.date <= date }) applyTo(t, { pool(it) }, { it })
        return pools.filter { it.value.state.quantity.signum() != 0 }.map { (securityId, p) ->
            val security = security(securityId)
            val price = price(securityId, date)
            val value = price?.let { (_, px) ->
                val native = Money.of(p.state.quantity.multiply(px).multiply(security.multiplier), security.currency)
                if (native.currency == account.currency) native else books.rates.convert(native, account.currency, date)
            }
            Holding(account.id, security, p.state.quantity, p.state.cost, price?.second, price?.first, value)
        }.sortedBy { it.security.name.lowercase() }
    }

    /**
     * Applies one transaction to the pools given by [pool], with amounts turned into the pools'
     * currency by [convert]. A merger moves the cost of the old units to the new security.
     */
    internal fun applyTo(t: InvestmentTxn, pool: (String) -> CostPool, convert: (Money) -> Money) {
        val sid = t.securityId ?: return
        val q = t.quantity ?: BigDecimal.ZERO
        fun ev(kind: CostEventKind, amount: Money? = null, quantity: BigDecimal = q) = CostEvent(t.date, kind, quantity, amount?.let(convert), t.ratio, ref = t.id)
        when (t.kind) {
            InvestmentKind.BUY, InvestmentKind.REINVEST -> pool(sid).apply(ev(CostEventKind.ACQUIRE, t.amount + t.fees))
            InvestmentKind.TRANSFER_IN -> pool(sid).apply(ev(CostEventKind.ACQUIRE, t.amount))
            InvestmentKind.SELL -> pool(sid).apply(ev(CostEventKind.DISPOSE, t.amount - t.fees))
            InvestmentKind.TRANSFER_OUT -> pool(sid).apply(ev(CostEventKind.REMOVE))
            InvestmentKind.RETURN_OF_CAPITAL -> pool(sid).apply(ev(CostEventKind.RETURN_OF_CAPITAL, t.amount))
            InvestmentKind.NOTIONAL_DISTRIBUTION -> pool(sid).apply(ev(CostEventKind.ADD_TO_COST, t.amount))
            InvestmentKind.SPLIT -> pool(sid).apply(ev(CostEventKind.SPLIT))
            InvestmentKind.MERGER -> {
                val moved = pool(sid).apply(ev(CostEventKind.REMOVE))
                pool(t.otherSecurityId!!).apply(CostEvent(t.date, CostEventKind.ACQUIRE, q.multiply(t.ratio!!).normalized(), moved, ref = t.id))
            }
            InvestmentKind.INCOME, InvestmentKind.FEE -> Unit
        }
    }

    // --- Adjusted cost base (INV-03) --------------------------------------------------------------

    /**
     * ACB per security across all non-registered accounts with the same owners, in the base
     * currency, converted at each transaction's date; and the capital gains and losses, with
     * possible superficial losses flagged. Registered plans are left out.
     */
    fun acb(through: LocalDate): AcbReport {
        val base = books.rates.baseCurrency
        val zero = Money.zero(base)
        val missing = HashSet<Currency>()
        val problems = ArrayList<String>()
        val accounts = accounts(includeClosed = true).filter { !it.type.isRegistered }
        val all = accounts.flatMap { a -> transactions(a.id).map { a to it } }.filter { it.second.date <= through }
            .sortedWith(compareBy({ it.second.date }, { it.second.id }))
        data class Key(val securityId: String, val owners: Set<String>)
        val pools = LinkedHashMap<Key, CostPool>()
        val events = HashMap<Key, MutableList<CostEvent>>()
        val poolAccounts = HashMap<Key, MutableSet<String>>()
        for ((account, t) in all) {
            val convert: (Money) -> Money = { m ->
                if (m.currency == base) m else books.rates.convert(m, base, t.date) ?: run { missing += m.currency; Money.zero(base) }
            }
            fun pool(securityId: String): CostPool {
                val key = Key(securityId, account.ownerMemberIds)
                poolAccounts.getOrPut(key) { HashSet() } += account.id
                return pools.getOrPut(key) { CostPool(zero) }
            }
            try {
                applyTo(t, ::pool, convert)
                t.securityId?.let { sid ->
                    val key = Key(sid, account.ownerMemberIds)
                    if (t.kind in setOf(InvestmentKind.BUY, InvestmentKind.REINVEST, InvestmentKind.TRANSFER_IN)) {
                        events.getOrPut(key) { ArrayList() } += CostEvent(t.date, CostEventKind.ACQUIRE, t.quantity ?: BigDecimal.ZERO)
                    }
                }
            } catch (e: CostBaseException) {
                problems += "${account.name}, ${t.date}: ${e.message}"
            }
        }
        val gains = pools.flatMap { (key, p) ->
            val security = security(key.securityId)
            CostBase.flagSuperficial(p.dispositions, events[key].orEmpty()).map { CapitalGain(security, key.owners, it) }
        }.sortedBy { it.disposition.date }
        val list = pools.filter { it.value.state.quantity.signum() != 0 || !it.value.state.cost.isZero }.map { (key, p) ->
            AcbPool(security(key.securityId), key.owners, poolAccounts[key].orEmpty(), p.state.quantity, p.state.cost)
        }
        // CR-06: crypto-assets are capital property too, pooled per coin and owner.
        val crypto = books.crypto.acb(through)
        return AcbReport(
            (list + crypto.pools).sortedBy { it.security.name.lowercase() }, (gains + crypto.gains + books.metals.gains(through)).sortedBy { it.disposition.date },
            missing + crypto.missingRates, problems + crypto.problems,
        )
    }

    // --- Statements and reconciliation (REC-08) ----------------------------------------------------

    fun statements(accountId: String): List<InvestmentStatement> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).investmentsQueries.invStatements(accountId).executeAsList().map {
            InvestmentStatement(
                it.id, it.account_id, LocalDate.parse(it.date), it.cash_minor?.let { m -> Money.ofMinor(m, account.currency) },
                json.decodeFromString(ListSerializer(Position.serializer()), it.positions).associate { p -> p.security to BigDecimal(p.quantity) },
                it.status == "RECONCILED", it.source,
            )
        }
    }

    /** Records a statement's cash and holdings (securities by id) to compare with the books. */
    fun saveStatement(accountId: String, date: LocalDate, cash: Money?, positions: Map<String, BigDecimal>, source: String? = null): InvestmentStatement {
        val (group, account) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(cash == null || cash.currency == account.currency, "error.currencyMismatch", account.currency.code)
        val id = Ids.newId()
        val encoded = json.encodeToString(ListSerializer(Position.serializer()), positions.map { Position(it.key, it.value.normalized().toPlainString()) })
        books.ledger(group).investmentsQueries.insertInvStatement(id, accountId, date.toString(), cash?.minorUnits, encoded, "OPEN", source, books.now(), null, null)
        return statements(accountId).first { it.id == id }
    }

    fun check(accountId: String, statementId: String): StatementCheck {
        val statement = statements(accountId).first { it.id == statementId }
        val books = holdings(accountId, statement.date)
        val held = books.holdings.associate { it.security.id to it.quantity }
        val ids = statement.positions.keys + held.keys
        val positions = ids.map { id -> PositionCheck(security(id), statement.positions[id] ?: BigDecimal.ZERO, held[id] ?: BigDecimal.ZERO) }
            .sortedBy { it.security.name.lowercase() }
        return StatementCheck(statement, books.cash, positions)
    }

    /** Marks the statement reconciled once cash and every holding agree. */
    fun reconcile(accountId: String, statementId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(check(accountId, statementId).matches, "error.statementDiffers")
        books.ledger(group).investmentsQueries.setInvStatementStatus("RECONCILED", books.now(), books.userId, statementId)
    }

    fun deleteStatement(accountId: String, statementId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).investmentsQueries.deleteInvStatement(statementId)
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun editableGroups() = books.groups().filter { it.level == PermissionLevel.EDIT }

    private fun has(group: GroupInfo, securityId: String) = books.ledger(group).investmentsQueries.securityById(securityId).executeAsOneOrNull() != null

    /** Copies a security (with its prices) into [group] if that ledger does not hold it yet. */
    internal fun ensureIn(group: GroupInfo, securityId: String) {
        if (has(group, securityId)) return
        val source = books.groups().firstOrNull { has(it, securityId) } ?: throw ValidationException("error.securityNotFound")
        val sq = books.ledger(source).investmentsQueries
        val ledger = books.ledger(group)
        write(ledger, sq.securityById(securityId).executeAsOne().toSecurity())
        sq.prices(securityId).executeAsList().forEach { ledger.investmentsQueries.putPrice(securityId, it.date, it.price, it.source) }
    }

    private fun write(ledger: LedgerDatabase, s: Security) {
        val now = books.now()
        val created = ledger.investmentsQueries.securityById(s.id).executeAsOneOrNull()?.created_at ?: now
        ledger.investmentsQueries.upsertSecurity(
            s.id, s.symbol, s.exchange, s.name, s.kind.name, s.currency.code, s.assetClass.name, s.region.name, s.multiplier.normalized().toPlainString(),
            s.maturity?.toString(), s.couponRate?.toPlainString(), s.notes, if (s.archived) 1 else 0, created, now,
        )
    }

    private fun SecurityRow.toSecurity() = Security(
        id, symbol, exchange, name, SecurityKind.valueOf(kind), Currency.of(currency), AssetClass.valueOf(asset_class), Region.valueOf(region),
        BigDecimal(multiplier), maturity?.let(LocalDate::parse), coupon_rate?.let(::BigDecimal), notes, archived == 1L,
    )

    private fun InvRow.toTxn(c: Currency) = InvestmentTxn(
        id, account_id, LocalDate.parse(date), InvestmentKind.valueOf(kind), security_id, quantity?.let(::BigDecimal), price?.let(::BigDecimal),
        Money.ofMinor(amount_minor, c), Money.ofMinor(fees_minor, c), Money.ofMinor(withheld_minor, c), income_type?.let(IncomeType::valueOf),
        ratio?.let(::BigDecimal), other_security_id, external_id, memo,
    )

    @Serializable
    private data class Position(val security: String, val quantity: String)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val MANUAL = "MANUAL"
    }
}
