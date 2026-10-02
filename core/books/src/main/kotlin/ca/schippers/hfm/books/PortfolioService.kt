package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.CostBaseException
import ca.schippers.hfm.calc.invest.CostPool
import ca.schippers.hfm.calc.invest.Returns
import ca.schippers.hfm.calc.invest.ValuePoint
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import java.math.BigDecimal

/**
 * INV-06: how one account (or, with [account] null, all of those chosen) did over a period, in the
 * base currency. Money put in and taken out ([contributions], [withdrawals]) is not a gain;
 * [income] (interest, dividends, distributions, crypto rewards) and [costs] (fees charged
 * separately and foreign tax withheld) are part of it, and the rest is the change in market value.
 */
data class PerformanceLine(
    val account: Account?,
    val startValue: Money,
    val endValue: Money,
    val contributions: Money,
    val withdrawals: Money,
    val income: Money,
    val costs: Money,
    /** Over the time money was invested during the period: from its start, or from the first deposit. */
    val timeWeighted: BigDecimal?,
    /** A yearly rate; null when money was invested for less than a year, which is never annualized. */
    val timeWeightedAnnual: BigDecimal?,
    val moneyWeighted: BigDecimal?,
    val moneyWeightedAnnual: BigDecimal?,
) {
    /** Total return in money: the end value less the start value and the money put in, net. */
    val gain: Money get() = endValue - startValue - contributions + withdrawals
    val marketChange: Money get() = gain - income + costs
}

data class PortfolioPerformance(
    val from: LocalDate,
    val to: LocalDate,
    val accounts: List<PerformanceLine>,
    val total: PerformanceLine,
    val missingRates: Set<Currency>,
    /** Securities valued at book cost because they have no price. */
    val missingPrices: Set<Security>,
)

/** INV-06: returns per account and for the household, from the books' cash lines and holdings. */
class PortfolioService internal constructor(private val books: Books) {

    private val base: Currency get() = books.rates.baseCurrency

    /**
     * Performance from the start of [from] to the end of [to] of the investment accounts in
     * [accountIds] (all the user can see when null). Transfers between the chosen accounts are
     * moves inside the portfolio, not money put in or taken out.
     */
    fun performance(from: LocalDate, to: LocalDate, accountIds: Set<String>? = null): PortfolioPerformance {
        validate(from <= to, "error.endBeforeStart")
        val accounts = books.investments.accounts(includeClosed = true)
            .filter { (accountIds == null || it.id in accountIds) && it.openingDate <= to }
        val chosen = accounts.map { it.id }.toSet()
        val start = from.minus(DatePeriod(days = 1))
        val ctx = Context()
        val returnCategories = returnCategories()
        val histories = accounts.map { History(it, ctx, chosen, returnCategories, from, to) }
        val dates = (histories.flatMap { it.flowDates } + start + to).toSortedSet().toList()
        val series = histories.map { it.values(dates) }

        val lines = histories.mapIndexed { i, h -> line(h.account, dates, series[i], h) }
        val totalValues = dates.indices.map { d -> series.fold(BigDecimal.ZERO) { a, s -> a + s.values[d] } }
        // Moves between the chosen accounts are money in for one and out for the other, not for the whole.
        val totalFlows = dates.indices.map { d -> series.fold(BigDecimal.ZERO) { a, s -> a + s.flows[d] - s.internal[d] } }
        val total = line(null, dates, Series(totalValues, totalFlows, totalFlows), null, histories)
        return PortfolioPerformance(from, to, lines.sortedBy { it.account!!.name.lowercase() }, total, ctx.missingRates, ctx.missingPrices)
    }

    /** Value and money put in on each date; [internal] is the part that came from or went to another chosen account. */
    private class Series(val values: List<BigDecimal>, val flows: List<BigDecimal>, val internal: List<BigDecimal>)

    private fun line(account: Account?, dates: List<LocalDate>, s: Series, h: History?, all: List<History> = listOfNotNull(h)): PerformanceLine {
        val points = dates.indices.map { ValuePoint(dates[it], s.values[it], if (it == 0) BigDecimal.ZERO else s.flows[it]) }
        // Rates cover the time money was invested: coins bought last month earned their gain in a month.
        val firstHeld = points.indexOfFirst { it.value.signum() > 0 }.takeIf { it >= 0 } ?: points.lastIndex
        val days = points[firstHeld].date.daysUntil(points.last().date)
        val twr = Returns.timeWeighted(points)
        val mwrAnnual = Returns.moneyWeighted(points)
        fun money(v: BigDecimal) = Money.of(v, base)
        val flows = points.drop(1).map { it.flow }
        return PerformanceLine(
            account, money(s.values.first()), money(s.values.last()),
            money(flows.filter { it.signum() > 0 }.fold(BigDecimal.ZERO, BigDecimal::add)),
            money(flows.filter { it.signum() < 0 }.fold(BigDecimal.ZERO, BigDecimal::add).negate()),
            money(all.fold(BigDecimal.ZERO) { a, x -> a + x.income }), money(all.fold(BigDecimal.ZERO) { a, x -> a + x.costs }),
            twr, twr?.let { Returns.annualized(it, days) },
            mwrAnnual?.let { Returns.overDays(it, days) }, mwrAnnual?.takeIf { days >= 365 },
        )
    }

    /** Investment income, investment and crypto fees, and foreign tax withheld, with their subcategories. */
    private fun returnCategories(): Set<String> {
        val all = books.categories.list(includeArchived = true)
        val roots = all.filter { it.systemKey in RETURN_KEYS }.map { it.id }.toMutableSet()
        var grew = true
        while (grew) {
            val more = all.filter { it.parentId in roots && it.id !in roots }.map { it.id }
            grew = more.isNotEmpty()
            roots += more
        }
        return roots
    }

    /** Prices and rates read once for every account, and what was missing. */
    private inner class Context {
        val missingRates = HashSet<Currency>()
        val missingPrices = HashSet<Security>()
        val book = books.investments.PriceBook()

        /** [amount] of [from] in [to] on [date]; zero, with the currency noted, when there is no rate. */
        fun convert(amount: BigDecimal, from: Currency, to: Currency, date: LocalDate): BigDecimal {
            if (from == to || amount.signum() == 0) return amount
            val rate = book.rate(from, to, date)
            if (rate == null) { missingRates += from; return BigDecimal.ZERO }
            return amount.multiply(rate)
        }
    }

    /** One account's cash lines, investment transactions and precious metals, read once. */
    private inner class History(
        val account: Account,
        private val ctx: Context,
        chosen: Set<String>,
        returnCategories: Set<String>,
        from: LocalDate,
        to: LocalDate,
    ) {
        private val cur = account.currency
        private val categoryKinds = books.categories.list(includeArchived = true).associate { it.id to it.kind }
        /** Each line's date and amount, for the cash balance. */
        private val cash = ArrayList<Pair<LocalDate, Long>>()
        /** Money put in (positive) or taken out, in the account's currency, by date. */
        private val cashFlows = HashMap<LocalDate, Long>()
        /** The part of [cashFlows] moved from or to another chosen account. */
        private val internalFlows = HashMap<LocalDate, Long>()
        var income = BigDecimal.ZERO
        var costs = BigDecimal.ZERO
        private val txns = books.investments.transactions(account.id).filter { it.date <= to }
        private val metals = if (account.type == AccountType.PRECIOUS_METALS) books.metals.items(account.id, includeSold = true) else emptyList()
        private val inRange: (LocalDate) -> Boolean = { it in from..to }
        val flowDates: Set<LocalDate>

        init {
            val (group, _) = books.accounts.locate(account.id)
            val rows = books.ledger(group).investmentsQueries.performanceLines(account.id).executeAsList().groupBy { it.id }
            for (lines in rows.values) {
                val first = lines.first()
                val date = LocalDate.parse(first.date)
                if (date > to) continue
                cash += date to first.amount_minor
                if (!inRange(date)) continue
                var earned = 0L
                for (s in lines) {
                    val category = s.category_id ?: continue
                    if (category !in returnCategories) continue
                    val amount = s.split_minor ?: 0L
                    earned += amount
                    val inBase = ctx.convert(Money.ofMinor(amount, cur).toBigDecimal(), cur, base, date)
                    if (categoryKinds[category] == CategoryKind.INCOME) income += inBase else costs -= inBase
                }
                // Lines written by an investment transaction move money within the account.
                if (first.investment_id != null) continue
                val flow = first.amount_minor - earned
                if (flow == 0L) continue
                cashFlows[date] = (cashFlows[date] ?: 0L) + flow
                if (first.transfer_account_id in chosen) internalFlows[date] = (internalFlows[date] ?: 0L) + flow
            }
            if (inRange(account.openingDate) && !account.openingBalance.isZero) {
                cashFlows[account.openingDate] = (cashFlows[account.openingDate] ?: 0L) + account.openingBalance.minorUnits
            }
            flowDates = cashFlows.keys +
                txns.filter { inRange(it.date) && it.kind in MOVES }.map { it.date } +
                metals.flatMap { listOfNotNull(it.purchaseDate, it.disposalDate) }.filter(inRange)
        }

        /** The account's value and the money put in, in the base currency, on each of [dates]. */
        fun values(dates: List<LocalDate>): Series {
            val values = ArrayList<BigDecimal>(dates.size)
            val flows = ArrayList<BigDecimal>(dates.size)
            val internal = ArrayList<BigDecimal>(dates.size)
            val pools = LinkedHashMap<String, CostPool>()
            val moved = HashMap<LocalDate, BigDecimal>()
            val zero = Money.zero(cur)
            var next = 0
            var balance = 0L
            var line = 0
            for (date in dates) {
                while (next < txns.size && txns[next].date <= date) {
                    val t = txns[next++]
                    // Securities moved in or out are money put in or taken out, at their value that day.
                    val before = t.securityId?.let { pools[it]?.state?.cost } ?: zero
                    try {
                        books.investments.applyTo(t, { pools.getOrPut(it) { CostPool(zero) } }, { it })
                    } catch (_: CostBaseException) {
                        // The investment screens report impossible histories; the value here simply skips the line.
                    }
                    // Securities moved in or out are money put in or taken out, at their value that day.
                    if (t.kind in MOVES && inRange(t.date)) {
                        val after = t.securityId?.let { pools[it]?.state?.cost } ?: zero
                        val sign = if (t.kind == InvestmentKind.TRANSFER_IN) BigDecimal.ONE else BigDecimal.ONE.negate()
                        val value = t.securityId?.let { valueOf(it, t.quantity ?: BigDecimal.ZERO, t.date) }
                            ?: (if (t.kind == InvestmentKind.TRANSFER_IN) t.amount else before - after).toBigDecimal()
                        moved[t.date] = (moved[t.date] ?: BigDecimal.ZERO) + value.multiply(sign)
                    }
                }
                while (line < cash.size && cash[line].first <= date) balance += cash[line++].second
                var value = if (account.openingDate <= date) Money.ofMinor(balance + account.openingBalance.minorUnits, cur).toBigDecimal() else BigDecimal.ZERO
                for ((sid, pool) in pools) {
                    val state = pool.state
                    if (state.quantity.signum() == 0) continue
                    value += valueOf(sid, state.quantity, date) ?: run {
                        ctx.book.securities[sid]?.let { ctx.missingPrices += it }
                        state.cost.toBigDecimal()
                    }
                }
                if (metals.isNotEmpty()) value += books.metals.value(account.id, date).toBigDecimal()
                var flow = Money.ofMinor(cashFlows[date] ?: 0L, cur).toBigDecimal() + (moved[date] ?: BigDecimal.ZERO)
                for (m in metals) {
                    if (m.purchaseDate == date && m.cost != null) flow += m.cost.toBigDecimal()
                    if (m.disposalDate == date && m.proceeds != null) flow -= m.proceeds.toBigDecimal()
                }
                values += ctx.convert(value, cur, base, date)
                flows += ctx.convert(flow, cur, base, date)
                internal += ctx.convert(Money.ofMinor(internalFlows[date] ?: 0L, cur).toBigDecimal(), cur, base, date)
            }
            return Series(values, flows, internal)
        }

        /** [quantity] units at the price on [date], in the account's currency; null without a price. */
        private fun valueOf(securityId: String, quantity: BigDecimal, date: LocalDate): BigDecimal? =
            ctx.book.value(securityId, quantity, cur, date)?.toBigDecimal()
    }

    companion object {
        private val RETURN_KEYS = setOf("income.investment", "financial.investment_fees", "financial.crypto_fees", "taxes.foreign_tax")
        private val MOVES = setOf(InvestmentKind.TRANSFER_IN, InvestmentKind.TRANSFER_OUT)
    }
}
