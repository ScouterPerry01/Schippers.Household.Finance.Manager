package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.invest.InvestmentIncome
import ca.schippers.hfm.calc.invest.SlipKind
import ca.schippers.hfm.calc.invest.TaxSlips
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

/** A slip entered from the slip itself, for a whole account (and, for a T3 or RL-16, one fund). */
data class EnteredSlip(
    val id: String,
    val accountId: String,
    val year: Int,
    val kind: SlipKind,
    val securityId: String?,
    /** Box code to amount in dollars. */
    val boxes: Map<String, BigDecimal>,
    val notes: String? = null,
)

/**
 * One slip as one person reports it: the boxes from the slip entered, or else estimated from the
 * books, shared equally between the account's owners ([shareOf] of them).
 */
data class SlipLine(
    val kind: SlipKind,
    val account: Account,
    val security: Security?,
    val boxes: Map<String, Money>,
    val estimate: Map<String, Money>,
    val entered: EnteredSlip?,
    val shareOf: Int,
)

/** A capital gain or loss as one person reports it (Schedule 3): their share of a joint sale. */
data class GainShare(val gain: CapitalGain, val shareOf: Int) {
    private fun part(m: Money) = if (shareOf <= 1) m else Money.of(m.toBigDecimal().divide(BigDecimal(shareOf), m.currency.minorUnits, RoundingMode.HALF_UP), m.currency)
    val proceeds: Money get() = part(gain.disposition.proceeds)
    val cost: Money get() = part(gain.disposition.cost)
    val amount: Money get() = proceeds - cost
}

/** INV-08: one person's investment income and capital gains for a tax year. */
data class PersonInvestmentIncome(
    /** Null for accounts with no owner recorded. */
    val member: Member?,
    val province: Province,
    val slips: List<SlipLine>,
    val gains: List<GainShare>,
    /** Crypto-asset rewards (staking, mining), which no slip reports. */
    val cryptoIncome: Money,
) {
    fun totals(kind: SlipKind): Map<String, Money> = slips.filter { it.kind == kind }.flatMap { it.boxes.entries }
        .groupBy({ it.key }, { it.value }).mapValues { (_, list) -> list.reduce(Money::plus) }

    val netGain: Money? get() = gains.map { it.amount }.reduceOrNull(Money::plus)
    val taxableGain: Money? get() = netGain?.let { Money.of(it.toBigDecimal().multiply(TaxSlips.INCLUSION_RATE), it.currency, RoundingMode.HALF_UP) }
}

data class InvestmentIncomeReport(val year: Int, val people: List<PersonInvestmentIncome>, val missingRates: Set<Currency>)

/**
 * INV-08 and PROV-07: the investment income of non-registered accounts by person and tax year, as
 * it appears on T5 and T3 slips (and RL-3 and RL-16 for people in Quebec), with capital gains.
 * Amounts are estimated from the books until the slip is entered: dividends from a Canadian fund
 * or company count as eligible, income from a foreign security or with foreign tax withheld as
 * foreign income, and a fund's distributions as other income, since only the slip gives its
 * breakdown.
 */
class TaxSlipService internal constructor(private val books: Books) {

    private val cad = Currency.CAD

    /** Non-registered accounts that can receive investment income slips. */
    fun accounts(): List<Account> = books.investments.accounts(includeClosed = true).filter { !it.type.isRegistered && it.type != AccountType.PRECIOUS_METALS }

    /** The boxes estimated from the books for a whole account's slip (before it is shared between owners). */
    fun estimate(accountId: String, year: Int, kind: SlipKind, securityId: String?): Map<String, BigDecimal> {
        val income = estimates(year, HashSet())[Key(accountId, TaxSlips.federalOf(kind), securityId.orEmpty())] ?: return emptyMap()
        return TaxSlips.boxes(kind, income, year)
    }

    fun report(year: Int): InvestmentIncomeReport {
        val missing = HashSet<Currency>()
        val securities = books.investments.securities(includeArchived = true).associateBy { it.id }
        val accounts = accounts()
        val entered = slips(year).associateBy { Key(it.accountId, it.kind, it.securityId.orEmpty()) }
        val estimates = estimates(year, missing)
        return InvestmentIncomeReport(year, peopleOf(year, accounts, securities, entered, estimates), missing)
    }

    /** Income per account, slip kind and fund, in Canadian dollars at each transaction's date. */
    private fun estimates(year: Int, missing: MutableSet<Currency>): Map<Key, InvestmentIncome> {
        val securities = books.investments.securities(includeArchived = true).associateBy { it.id }
        val estimates = HashMap<Key, InvestmentIncome>()
        for (a in accounts()) {
            for (t in books.investments.transactions(a.id)) {
                if (t.date.year != year) continue
                val s = t.securityId?.let(securities::get)
                val trust = s != null && s.kind in TRUSTS && s.currency == cad
                val key = Key(a.id, if (trust) SlipKind.T3 else SlipKind.T5, if (trust) s.id else "")
                fun inCad(m: Money) = if (m.isZero) BigDecimal.ZERO else books.rates.convert(m, cad, t.date)?.toBigDecimal() ?: run { missing += m.currency; BigDecimal.ZERO }
                val amount = inCad(t.amount)
                val income = when (t.kind) {
                    InvestmentKind.INCOME, InvestmentKind.REINVEST -> {
                        val withheld = inCad(t.withheld)
                        when {
                            withheld.signum() > 0 || (s != null && s.currency != cad) -> InvestmentIncome(foreignIncome = amount, foreignTax = withheld)
                            t.incomeType == IncomeType.DIVIDEND -> InvestmentIncome(eligibleDividends = amount)
                            t.incomeType == IncomeType.INTEREST -> InvestmentIncome(interest = amount)
                            else -> InvestmentIncome(otherIncome = amount)
                        }
                    }
                    InvestmentKind.NOTIONAL_DISTRIBUTION -> InvestmentIncome(capitalGains = amount)
                    InvestmentKind.RETURN_OF_CAPITAL -> if (trust) InvestmentIncome(returnOfCapital = amount) else null
                    else -> null
                } ?: continue
                estimates[key] = (estimates[key] ?: InvestmentIncome()) + income
            }
        }
        return estimates
    }

    private fun peopleOf(year: Int, accounts: List<Account>, securities: Map<String, Security>, entered: Map<Key, EnteredSlip>, estimates: Map<Key, InvestmentIncome>): List<PersonInvestmentIncome> {
        val members = books.members.list(includeArchived = true)
        val accountById = accounts.associateBy { it.id }
        val gains = books.investments.acb(LocalDate(year, 12, 31)).gains.filter { it.disposition.date.year == year }
        val cryptoCategory = books.categories.list(includeArchived = true).firstOrNull { it.systemKey == "income.investment.crypto" }?.id
        val people = (members.map { it as Member? } + null).mapNotNull { m ->
            val province = books.provinceOf(m?.id)
            val mine = accounts.filter { a -> if (m == null) a.ownerMemberIds.isEmpty() else m.id in a.ownerMemberIds }
            val ids = mine.map { it.id }.toSet()
            fun share(a: Account) = a.ownerMemberIds.size.coerceAtLeast(1)
            val lines = ArrayList<SlipLine>()
            val keys = (estimates.keys + entered.keys.map { it.federal() }).filter { it.accountId in ids }.distinct()
            for (k in keys) {
                val a = accountById.getValue(k.accountId)
                val income = estimates[k] ?: InvestmentIncome()
                val kinds = listOf(k.kind) + if (province.isQuebec || entered.containsKey(k.copy(kind = TaxSlips.quebecOf(k.kind)))) listOf(TaxSlips.quebecOf(k.kind)) else emptyList()
                for (kind in kinds) {
                    val estimate = TaxSlips.boxes(kind, income, year)
                    val slip = entered[k.copy(kind = kind)]
                    val boxes = slip?.boxes ?: estimate
                    if (boxes.isEmpty()) continue
                    lines += SlipLine(kind, a, k.securityId.takeIf { it.isNotEmpty() }?.let(securities::get), part(boxes, share(a)), part(estimate, share(a)), slip, share(a))
                }
            }
            val myGains = gains.filter { g -> if (m == null) g.ownerMemberIds.isEmpty() else m.id in g.ownerMemberIds }
                .map { GainShare(it, it.ownerMemberIds.size.coerceAtLeast(1)) }
            val wallets = books.accounts.list(includeClosed = true).map { it.account }
                .filter { it.type == AccountType.CRYPTO_WALLET && (if (m == null) it.ownerMemberIds.isEmpty() else m.id in it.ownerMemberIds) }
            val crypto = cryptoCategory?.let { c ->
                wallets.map { w ->
                    val total = books.reports.subtreeTotals(ReportFilter(LocalDate(year, 1, 1), LocalDate(year, 12, 31), setOf(w.id))).value[c]
                    total?.let { Money.of(it.toBigDecimal().divide(BigDecimal(w.ownerMemberIds.size.coerceAtLeast(1)), 2, RoundingMode.HALF_UP), it.currency) }
                }.filterNotNull().fold(Money.zero(books.rates.baseCurrency), Money::plus)
            } ?: Money.zero(books.rates.baseCurrency)
            if (lines.isEmpty() && myGains.isEmpty() && crypto.isZero) null
            else PersonInvestmentIncome(m, province, lines.sortedWith(compareBy({ it.kind }, { it.account.name }, { it.security?.label })), myGains, crypto)
        }
        return people
    }

    private fun part(boxes: Map<String, BigDecimal>, shareOf: Int): Map<String, Money> =
        boxes.mapValues { (_, v) -> Money.of(v.divide(BigDecimal(shareOf), 2, RoundingMode.HALF_UP), cad) }

    private data class Key(val accountId: String, val kind: SlipKind, val securityId: String) {
        fun federal() = copy(kind = TaxSlips.federalOf(kind))
    }

    // --- Slips entered ------------------------------------------------------------------------

    /** The slips entered for [year] in every group the user can see. */
    fun slips(year: Int): List<EnteredSlip> = books.groups().flatMap { g ->
        books.ledger(g).taxSlipsQueries.slipsForYear(year.toLong()).executeAsList().map {
            EnteredSlip(it.id, it.account_id, it.year.toInt(), SlipKind.valueOf(it.kind), it.security_id.ifEmpty { null }, decode(it.boxes), it.notes)
        }
    }

    /** Saves a slip's boxes; it replaces any slip already entered for the same account, year, kind and fund. */
    fun save(slip: EnteredSlip): EnteredSlip {
        val (group, account) = books.accounts.locate(slip.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(!account.type.isRegistered, "error.slipRegistered")
        validate(slip.boxes.keys.all { it in TaxSlips.boxCodes(slip.kind) }, "error.invalidNumber")
        val q = books.ledger(group).taxSlipsQueries
        val sec = slip.securityId.orEmpty()
        val existing = q.slipByKey(slip.accountId, slip.year.toLong(), slip.kind.name, sec).executeAsOneOrNull()
        val id = existing?.id ?: slip.id.ifBlank { Ids.newId() }
        val now = books.now()
        q.upsertSlip(id, slip.accountId, slip.year.toLong(), slip.kind.name, sec, encode(slip.boxes.filterValues { it.signum() != 0 }), slip.notes.blankToNull(), existing?.created_at ?: now, now)
        books.session.audit(if (existing == null) "CREATE" else "UPDATE", "tax_slip", id)
        return slip.copy(id = id)
    }

    fun delete(accountId: String, slipId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).taxSlipsQueries.deleteSlip(slipId)
        books.session.audit("DELETE", "tax_slip", slipId)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private fun encode(boxes: Map<String, BigDecimal>) = json.encodeToString(mapSerializer, boxes.mapValues { it.value.setScale(2, RoundingMode.HALF_UP).toPlainString() })
    private fun decode(text: String) = json.decodeFromString(mapSerializer, text).mapValues { BigDecimal(it.value) }

    companion object {
        /** Canadian funds are trusts that issue T3 slips; companies, bonds and foreign securities give T5 slips. */
        private val TRUSTS = setOf(SecurityKind.ETF, SecurityKind.MUTUAL_FUND)
    }
}
