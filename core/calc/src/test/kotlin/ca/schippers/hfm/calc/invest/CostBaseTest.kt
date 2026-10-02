package ca.schippers.hfm.calc.invest

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CostBaseTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun d(s: String) = LocalDate.parse(s)
    private val zero = cad("0")
    private fun buy(date: String, qty: String, cost: String) = CostEvent(d(date), CostEventKind.ACQUIRE, BigDecimal(qty), cad(cost))
    private fun sell(date: String, qty: String, proceeds: String) = CostEvent(d(date), CostEventKind.DISPOSE, BigDecimal(qty), cad(proceeds))

    /**
     * CRA guide T4037, identical properties: 400 shares at $15, then 300 at $19, give an average
     * cost of $16.71 a share; 200 are then sold at $21.
     */
    @Test
    fun `CRA identical properties example`() {
        val r = CostBase.run(listOf(buy("2019-03-01", "400", "6000"), buy("2020-05-01", "300", "5700"), sell("2023-06-01", "200", "4200")), zero)
        val sale = r.dispositions.single()
        assertEquals(BigDecimal("16.71"), BigDecimal("11700").divide(BigDecimal("700"), 2, RoundingMode.HALF_UP))
        assertEquals(cad("3342.86"), sale.cost)
        assertEquals(cad("857.14"), sale.gain)
        assertEquals(BigDecimal("500"), r.state.quantity)
        assertEquals(cad("8357.14"), r.state.cost)
    }

    @Test
    fun `commissions, return of capital and a split`() {
        val r = CostBase.run(
            listOf(
                buy("2024-01-10", "100", "5010"), // 100 at 50 plus 10 commission
                buy("2024-03-10", "50", "3010"),
                sell("2024-06-10", "75", "5240"), // 75 at 70 less 10 commission
                CostEvent(d("2024-12-31"), CostEventKind.RETURN_OF_CAPITAL, amount = cad("75")),
                CostEvent(d("2024-12-31"), CostEventKind.ADD_TO_COST, amount = cad("40"), order = 1),
                CostEvent(d("2025-02-01"), CostEventKind.SPLIT, ratio = BigDecimal("2")),
            ),
            zero,
        )
        assertEquals(cad("4010"), r.dispositions.single().cost)
        assertEquals(cad("1230"), r.dispositions.single().gain)
        assertEquals(BigDecimal("150"), r.state.quantity)
        assertEquals(cad("3975"), r.state.cost)
    }

    @Test
    fun `return of capital beyond the cost is a gain`() {
        val r = CostBase.run(listOf(buy("2024-01-10", "10", "100"), CostEvent(d("2024-06-30"), CostEventKind.RETURN_OF_CAPITAL, amount = cad("130"))), zero)
        assertEquals(zero, r.state.cost)
        assertEquals(cad("30"), r.dispositions.single().gain)
    }

    @Test
    fun `selling everything leaves no cost behind`() {
        val r = CostBase.run(listOf(buy("2024-01-10", "3", "100"), sell("2024-02-10", "1", "40"), sell("2024-03-10", "2", "70")), zero)
        assertEquals(cad("33.33"), r.dispositions[0].cost)
        assertEquals(cad("66.67"), r.dispositions[1].cost)
        assertEquals(zero, r.state.cost)
        assertEquals(0, r.state.quantity.signum())
    }

    @Test
    fun `moving units out takes their cost without a gain`() {
        val r = CostBase.run(listOf(buy("2024-01-10", "4", "100"), CostEvent(d("2024-02-10"), CostEventKind.REMOVE, BigDecimal("1"))), zero)
        assertTrue(r.dispositions.isEmpty())
        assertEquals(cad("75"), r.state.cost)
    }

    @Test
    fun `a loss with a repurchase within 30 days is flagged`() {
        val r = CostBase.run(listOf(buy("2024-01-10", "10", "1000"), sell("2024-05-01", "10", "800"), buy("2024-05-20", "10", "790")), zero)
        assertTrue(r.dispositions.single().possibleSuperficialLoss)
        val clean = CostBase.run(listOf(buy("2024-01-10", "10", "1000"), sell("2024-05-01", "10", "800"), buy("2024-07-20", "10", "790")), zero)
        assertFalse(clean.dispositions.single().possibleSuperficialLoss)
    }

    @Test
    fun `fractional fund units`() {
        val r = CostBase.run(listOf(buy("2024-01-10", "12.345", "250"), buy("2024-02-10", "10.1", "210"), sell("2024-03-10", "5.5", "120")), zero)
        assertEquals(BigDecimal("16.945"), r.state.quantity)
        assertEquals(cad("112.72"), r.dispositions.single().cost)
    }

    @Test
    fun `selling more than held is refused`() {
        assertFailsWith<CostBaseException> { CostBase.run(listOf(buy("2024-01-10", "1", "10"), sell("2024-02-10", "2", "30")), zero) }
    }
}
