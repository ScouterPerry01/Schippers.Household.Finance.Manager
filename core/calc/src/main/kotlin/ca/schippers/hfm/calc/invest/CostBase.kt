package ca.schippers.hfm.calc.invest

import ca.schippers.hfm.calc.CALC
import ca.schippers.hfm.calc.rules.intOrEarliest
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import java.math.RoundingMode

/** What a holding event does to a pool of identical units (INV-03, CRA average cost). */
enum class CostEventKind {
    /** Units bought, reinvested or moved in; [CostEvent.amount] is their total cost including fees. */
    ACQUIRE,

    /** Units sold; [CostEvent.amount] is the proceeds after fees. Gives a capital gain or loss. */
    DISPOSE,

    /** Units moved out without a sale (to another account); their share of the cost goes with them. */
    REMOVE,

    /** Return of capital: lowers the cost. Below zero, the excess is a capital gain and the cost stays at zero. */
    RETURN_OF_CAPITAL,

    /** Reinvested (notional) distribution: raises the cost without adding units. */
    ADD_TO_COST,

    /** Split or consolidation: units are multiplied by [CostEvent.ratio]; the cost is unchanged. */
    SPLIT,
}

/**
 * One event in a pool. [amount] is in the pool's currency (Canadian dollars for tax). Events on
 * the same date are applied in [order].
 */
data class CostEvent(
    val date: LocalDate,
    val kind: CostEventKind,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val amount: Money? = null,
    val ratio: BigDecimal? = null,
    val order: Int = 0,
    /** Who or what the event came from, passed back with dispositions. */
    val ref: String? = null,
)

/** A sale (or a return of capital beyond the cost) and its capital gain or loss. */
data class Disposition(
    val date: LocalDate,
    val quantity: BigDecimal,
    val proceeds: Money,
    val cost: Money,
    val ref: String?,
    /** A loss with units of the same pool acquired within 30 days before or after (superficial loss, to check). */
    val possibleSuperficialLoss: Boolean = false,
) {
    val gain: Money get() = proceeds - cost
}

data class PoolState(val quantity: BigDecimal, val cost: Money) {
    /** Cost per unit, unrounded; zero when no units are held. */
    val perUnit: BigDecimal get() = if (quantity.signum() == 0) BigDecimal.ZERO else cost.toBigDecimal().divide(quantity, CALC)
}

data class CostResult(val state: PoolState, val dispositions: List<Disposition>, val history: List<Pair<CostEvent, PoolState>>)

class CostBaseException(message: String) : IllegalArgumentException(message)

/**
 * Average cost of a pool of identical units, as Canadian tax rules require for the adjusted cost
 * base: each purchase adds its full cost, each sale takes away the average cost of the units sold.
 * Results are exact to the cent; selling every unit takes away exactly the remaining cost.
 */
object CostBase {

    fun run(events: List<CostEvent>, zero: Money): CostResult {
        val pool = CostPool(zero)
        val history = ArrayList<Pair<CostEvent, PoolState>>()
        val sorted = events.sortedWith(compareBy({ it.date }, { it.order }))
        for (e in sorted) {
            pool.apply(e)
            history += e to pool.state
        }
        return CostResult(pool.state, flagSuperficial(pool.dispositions, sorted), history)
    }

    /** Marks losses with units of the same pool acquired within 30 days (rule superficial.loss.days) before or after. */
    fun flagSuperficial(dispositions: List<Disposition>, events: List<CostEvent>): List<Disposition> {
        val acquisitions = events.filter { it.kind == CostEventKind.ACQUIRE }.map { it.date }
        return dispositions.map { d ->
            val loss = d.gain.isNegative && d.quantity.signum() > 0
            val days = if (loss) intOrEarliest("superficial.loss.days", d.date) else 0
            if (loss && acquisitions.any { kotlin.math.abs(d.date.daysUntil(it)) <= days }) d.copy(possibleSuperficialLoss = true) else d
        }
    }
}

/**
 * One pool, event by event, for callers that move cost between pools (a merger takes the cost of
 * the old shares to the new ones). Events must be applied in date order.
 */
class CostPool(private val zero: Money) {
    private var quantity = BigDecimal.ZERO
    private var cost = zero
    val dispositions = ArrayList<Disposition>()

    val state: PoolState get() = PoolState(quantity.normalized(), cost)

    /** Applies [e] and returns the cost that left the pool (for a sale or a move out), or zero. */
    fun apply(e: CostEvent): Money {
        when (e.kind) {
            CostEventKind.ACQUIRE -> {
                quantity += e.quantity
                cost += e.amount ?: zero
            }
            CostEventKind.DISPOSE, CostEventKind.REMOVE -> {
                if (e.quantity > quantity) throw CostBaseException("More units sold or moved out on ${e.date} than held (${e.quantity.toPlainString()} of ${quantity.normalized().toPlainString()})")
                val share = if (e.quantity.compareTo(quantity) == 0) cost else Money.of(cost.toBigDecimal().multiply(e.quantity).divide(quantity, CALC), cost.currency, RoundingMode.HALF_UP)
                quantity -= e.quantity
                cost -= share
                if (e.kind == CostEventKind.DISPOSE) dispositions += Disposition(e.date, e.quantity, e.amount ?: zero, share, e.ref)
                return share
            }
            CostEventKind.RETURN_OF_CAPITAL -> {
                cost -= e.amount ?: zero
                if (cost.isNegative) {
                    dispositions += Disposition(e.date, BigDecimal.ZERO, -cost, zero, e.ref)
                    cost = zero
                }
            }
            CostEventKind.ADD_TO_COST -> cost += e.amount ?: zero
            CostEventKind.SPLIT -> {
                val ratio = e.ratio ?: throw CostBaseException("A split needs a ratio")
                if (ratio.signum() <= 0) throw CostBaseException("A split ratio must be positive")
                quantity = quantity.multiply(ratio).normalized()
            }
        }
        return zero
    }
}

/** Without trailing zeros, but never in exponent form: 500, not 5E+2. */
fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
