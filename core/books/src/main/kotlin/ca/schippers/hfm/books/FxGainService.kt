package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.CostBaseException
import ca.schippers.hfm.calc.invest.CostEvent
import ca.schippers.hfm.calc.invest.CostEventKind
import ca.schippers.hfm.calc.invest.CostPool
import ca.schippers.hfm.calc.invest.Disposition
import ca.schippers.hfm.calc.invest.ForeignExchange
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One holding of a foreign currency: every non-registered account in it with the same owners,
 * pooled like identical property. [cost] is the adjusted cost base in the base currency.
 */
data class CurrencyHolding(
    val currency: Currency,
    val ownerMemberIds: Set<String>,
    val accountIds: Set<String>,
    val balance: Money,
    val cost: Money,
    /** The balance at the rate of the day; null without a rate. */
    val value: Money?,
) {
    val unrealized: Money? get() = value?.let { it - cost }
}

data class FxDisposal(val currency: Currency, val ownerMemberIds: Set<String>, val disposition: Disposition)

/** One person's foreign exchange result for [year], with the year's exemption (rule fx.exemption, $200) applied. */
data class PersonFx(val member: Member?, val net: Money, val year: Int) {
    val reportable: Money get() = Money.of(ForeignExchange.reportable(net.toBigDecimal(), year), net.currency)
}

data class FxReport(
    val year: Int,
    /** Holdings at the end of the year (or on the day, for the current year). */
    val holdings: List<CurrencyHolding>,
    val disposals: List<FxDisposal>,
    val people: List<PersonFx>,
    val missingRates: Set<Currency>,
    val problems: List<String>,
)

/**
 * FX-05: realized and unrealized foreign exchange gains and losses on foreign currency held in
 * non-registered bank and investment accounts, in the base currency. Money coming into a foreign
 * account is bought at what the other account paid for it (a transfer) or at the day's rate;
 * money going out is sold at what the other account received or at the day's rate. Moves between
 * accounts of the same holding are neither. Debts in a foreign currency (cards, loans) and
 * crypto-assets (CR-06) are not included.
 */
class FxGainService internal constructor(private val books: Books) {

    private val base: Currency get() = books.rates.baseCurrency

    fun report(year: Int, today: LocalDate): FxReport {
        val end = minOf(LocalDate(year, 12, 31), today)
        val missing = HashSet<Currency>()
        val problems = ArrayList<String>()
        val zero = Money.zero(base)
        val accounts = books.accounts.all(includeClosed = true)
            .filter { it.currency != base && !it.currency.isCrypto && !it.type.isRegistered && it.type.kind in setOf(AccountKind.BANK, AccountKind.INVESTMENT) }
        data class Key(val currency: Currency, val owners: Set<String>)
        val pools = accounts.groupBy { Key(it.currency, it.ownerMemberIds) }
        val holdings = ArrayList<CurrencyHolding>()
        val disposals = ArrayList<FxDisposal>()
        for ((key, members) in pools) {
            val ids = members.map { it.id }.toSet()
            val pool = CostPool(zero)
            fun rate(date: LocalDate) = books.rates.rate(key.currency, base, date) ?: run { missing += key.currency; null }
            // Opening balances and every line, oldest first.
            val events = ArrayList<Triple<LocalDate, Long, Money?>>()
            for (a in members) {
                if (!a.openingBalance.isZero) events += Triple(a.openingDate, a.openingBalance.minorUnits, null)
                for (row in books.transactions.register(a.id)) {
                    val t = row.transaction
                    if (t.date > end) continue
                    val link = t.transfer
                    if (link != null && link.otherAccountId in ids) continue
                    events += Triple(t.date, t.amount.minorUnits, link?.let { counterpart(it, t.date) })
                }
            }
            // Money in before money out on the same day.
            for ((date, minor, other) in events.sortedWith(compareBy({ it.first }, { if (it.second > 0) 0 else 1 }))) {
                val amount = Money.ofMinor(minor, key.currency)
                // What the other side paid or received, else the day's rate.
                val inBase = other?.abs() ?: rate(date)?.let { amount.abs().convert(base, it) } ?: continue
                try {
                    if (minor > 0) {
                        pool.apply(CostEvent(date, CostEventKind.ACQUIRE, amount.toBigDecimal(), inBase))
                    } else {
                        pool.apply(CostEvent(date, CostEventKind.DISPOSE, amount.abs().toBigDecimal(), inBase))
                    }
                } catch (e: CostBaseException) {
                    problems += "${key.currency.code}, $date: ${e.message}"
                }
            }
            pool.dispositions.filter { it.date.year == year }.forEach { disposals += FxDisposal(key.currency, key.owners, it) }
            val state = pool.state
            val balance = Money.of(state.quantity, key.currency)
            holdings += CurrencyHolding(key.currency, key.owners, ids, balance, state.cost, rate(end)?.let { balance.convert(base, it) })
        }
        val people = (books.members.list(includeArchived = true).map { it as Member? } + null).mapNotNull { m ->
            val mine = disposals.filter { d -> if (m == null) d.ownerMemberIds.isEmpty() else m.id in d.ownerMemberIds }
            if (mine.isEmpty()) return@mapNotNull null
            val net = mine.fold(BigDecimal.ZERO) { acc, d -> acc + d.disposition.gain.toBigDecimal().divide(BigDecimal(d.ownerMemberIds.size.coerceAtLeast(1)), base.minorUnits, RoundingMode.HALF_UP) }
            PersonFx(m, Money.of(net, base), year)
        }
        return FxReport(year, holdings.sortedBy { it.currency.code }, disposals.sortedBy { it.disposition.date }, people, missing, problems)
    }

    /** The other side of a transfer in the base currency: what it paid or received. Null when it is in a third currency without a rate. */
    private fun counterpart(link: TransferLink, date: LocalDate): Money? {
        val (group, other) = runCatching { books.accounts.locate(link.otherAccountId) }.getOrNull() ?: return null
        val row = books.ledger(group).ledgerQueries.txnsByTransfer(link.transferId).executeAsList().firstOrNull { it.account_id == other.id } ?: return null
        val amount = Money.ofMinor(row.amount_minor, other.currency)
        return books.rates.convert(amount, base, date)
    }
}
