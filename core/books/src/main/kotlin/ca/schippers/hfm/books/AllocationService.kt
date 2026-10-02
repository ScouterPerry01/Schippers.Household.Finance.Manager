package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.Allocation
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

/** INV-07: the ways a portfolio can be divided. */
enum class AllocationBy { CLASS, REGION, CURRENCY, ACCOUNT }

/**
 * One part of the portfolio. [key] is an asset class or [AllocationService.CRYPTO], a region, a
 * currency code or an account id, depending on how it is divided; [percent] and [target] are out of 100.
 */
data class AllocationSlice(val key: String, val value: Money, val percent: BigDecimal, val target: BigDecimal?) {
    /** Percentage points above (positive) or below the target. */
    val drift: BigDecimal? get() = target?.let { percent - it }
}

/** A target allocation in percent, with how far a part may drift (in percentage points) before it is flagged. */
data class AllocationTarget(val weights: Map<String, BigDecimal>, val tolerance: BigDecimal = BigDecimal(5))

/** Whose target: the household's, a person's or an account group's. */
sealed interface TargetScope {
    val key: String
    data object Household : TargetScope { override val key = "household" }
    data class Person(val memberId: String) : TargetScope { override val key = "member:$memberId" }
    data class Group(val groupId: String) : TargetScope { override val key = "group:$groupId" }
}

data class AllocationReport(
    val by: AllocationBy,
    val date: LocalDate,
    val slices: List<AllocationSlice>,
    val total: Money,
    val target: AllocationTarget?,
    val missingRates: Set<Currency>,
    val missingPrices: Set<Security>,
    /** Balanced or global funds with no mix entered, counted whole in their class or region. */
    val unsplit: Set<Security>,
) {
    /** Parts beyond the target's tolerance. */
    val offTarget: List<AllocationSlice> get() = target?.let { t -> slices.filter { s -> s.drift?.abs()?.let { it > t.tolerance } == true } }.orEmpty()
}

/**
 * INV-07: asset allocation by class, region, currency and account, in the base currency, against
 * a target, with rebalancing suggestions. Targets hold only percentages and are kept in the
 * household settings.
 */
class AllocationService internal constructor(private val books: Books) {

    private val base: Currency get() = books.rates.baseCurrency

    /** The investment accounts in [accountIds] (all when null) divided [by], on [date]. */
    fun allocation(by: AllocationBy, date: LocalDate, accountIds: Set<String>? = null, scope: TargetScope? = null): AllocationReport {
        val missingRates = HashSet<Currency>()
        val missingPrices = HashSet<Security>()
        val unsplit = HashSet<Security>()
        val totals = LinkedHashMap<String, BigDecimal>()
        fun add(key: String, amount: Money?, currency: Currency) {
            if (amount == null || amount.isZero) return
            val inBase = books.rates.convert(amount, base, date) ?: run { missingRates += currency; return }
            totals[key] = (totals[key] ?: BigDecimal.ZERO) + inBase.toBigDecimal()
        }
        fun addSplit(parts: Map<String, BigDecimal>, value: Money) {
            val inBase = books.rates.convert(value, base, date) ?: run { missingRates += value.currency; return }
            for ((k, v) in Allocation.split(inBase.toBigDecimal(), parts, base.minorUnits)) totals[k] = (totals[k] ?: BigDecimal.ZERO) + v
        }
        val accounts = books.investments.accounts(includeClosed = true).filter { accountIds == null || it.id in accountIds }
        for (a in accounts) {
            val h = books.investments.holdings(a.id, date)
            for (p in h.holdings) {
                val value = p.marketValue ?: p.bookCost.also { missingPrices += p.security }
                val s = p.security
                when (by) {
                    AllocationBy.CLASS -> s.classMix?.let { mix -> addSplit(mix.mapKeys { it.key.name }, value) }
                        ?: run { if (s.assetClass == AssetClass.BALANCED) unsplit += s; add(s.assetClass.name, value, a.currency) }
                    AllocationBy.REGION -> s.regionMix?.let { mix -> addSplit(mix.mapKeys { it.key.name }, value) }
                        ?: run { if (s.region == Region.GLOBAL) unsplit += s; add(s.region.name, value, a.currency) }
                    AllocationBy.CURRENCY -> add(s.currency.code, value, a.currency)
                    AllocationBy.ACCOUNT -> add(a.id, value, a.currency)
                }
            }
            // A wallet's balance is its coins; other accounts' balance is cash.
            val cashKey = when (by) {
                AllocationBy.CLASS -> if (a.currency.isCrypto) CRYPTO else AssetClass.CASH.name
                AllocationBy.REGION -> regionOf(a.currency)
                AllocationBy.CURRENCY -> a.currency.code
                AllocationBy.ACCOUNT -> a.id
            }
            add(cashKey, h.cash, a.currency)
            if (a.type == AccountType.PRECIOUS_METALS) {
                val key = when (by) {
                    AllocationBy.CLASS -> AssetClass.COMMODITY.name
                    AllocationBy.REGION -> Region.GLOBAL.name
                    AllocationBy.CURRENCY -> a.currency.code
                    AllocationBy.ACCOUNT -> a.id
                }
                add(key, h.metals, a.currency)
            }
        }
        val target = scope?.takeIf { by != AllocationBy.ACCOUNT }?.let { target(it, by) }
        val shares = Allocation.shares(totals)
        val keys = totals.keys + target?.weights?.keys.orEmpty()
        val slices = keys.map { k ->
            AllocationSlice(k, Money.of(totals[k] ?: BigDecimal.ZERO, base), shares[k] ?: BigDecimal.ZERO, target?.let { it.weights[k] ?: BigDecimal.ZERO })
        }.sortedByDescending { it.value }
        val total = Money.of(totals.values.fold(BigDecimal.ZERO, BigDecimal::add), base)
        return AllocationReport(by, date, slices, total, target, missingRates, missingPrices, unsplit)
    }

    /**
     * The trades that bring [report] to its target: amounts to buy (positive) or sell per part.
     * With [sell] false only [newMoney] is placed, in the parts below target.
     */
    fun rebalance(report: AllocationReport, newMoney: Money = Money.zero(base), sell: Boolean = false): Map<String, Money> {
        val target = report.target ?: throw ValidationException("error.noTarget")
        validate(newMoney.currency == base, "error.currencyMismatch", base.code)
        validate(!newMoney.isNegative, "error.amountPositive")
        val current = report.slices.associate { it.key to it.value.toBigDecimal() }
        return Allocation.rebalance(current, target.weights, newMoney.toBigDecimal(), sell, base.minorUnits).mapValues { Money.of(it.value, base) }
    }

    // --- Targets --------------------------------------------------------------------------------

    fun target(scope: TargetScope, by: AllocationBy = AllocationBy.CLASS): AllocationTarget? =
        books.setting(settingKey(scope, by))?.takeIf { it.isNotBlank() }?.let { text ->
            val stored = json.decodeFromString(StoredTarget.serializer(), text)
            AllocationTarget(stored.weights.mapValues { BigDecimal(it.value) }, BigDecimal(stored.tolerance))
        }

    /** Saves [target] (percentages adding up to 100), or removes it when null. */
    fun setTarget(scope: TargetScope, by: AllocationBy, target: AllocationTarget?) {
        validate(by != AllocationBy.ACCOUNT, "error.noTarget")
        if (scope is TargetScope.Group) books.require(books.group(scope.groupId), PermissionLevel.EDIT)
        val text = target?.let { t ->
            val weights = t.weights.filterValues { it.signum() != 0 }
            validate(Allocation.isComplete(weights), "error.mixNot100")
            validate(t.tolerance.signum() >= 0, "error.invalidNumber")
            json.encodeToString(StoredTarget.serializer(), StoredTarget(weights.mapValues { it.value.stripTrailingZeros().toPlainString() }, t.tolerance.stripTrailingZeros().toPlainString()))
        }
        books.putSetting(settingKey(scope, by), text.orEmpty())
        books.session.audit("UPDATE", "allocation_target", scope.key, by.name)
    }

    private fun settingKey(scope: TargetScope, by: AllocationBy) = "allocation.target.${scope.key}.${by.name}"

    /** Cash counts in the region of its currency. */
    private fun regionOf(currency: Currency): String = when {
        currency.isCrypto -> Region.GLOBAL.name
        currency == Currency.CAD -> Region.CANADA.name
        currency == Currency.USD -> Region.US.name
        else -> Region.INTERNATIONAL.name
    }

    @Serializable
    private data class StoredTarget(val weights: Map<String, String>, val tolerance: String)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** The asset class of crypto-asset wallets, which are not securities. */
        const val CRYPTO = "CRYPTO"
    }
}
