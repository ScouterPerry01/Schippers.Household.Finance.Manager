package ca.schippers.hfm.calc.invest

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllocationTest {

    private fun n(s: String) = BigDecimal(s)
    private fun m(vararg pairs: Pair<String, String>) = pairs.associate { it.first to n(it.second) }

    @Test
    fun `a fund is split by its mix, to the cent`() {
        assertEquals(m("EQ" to "600.00", "FI" to "400.00"), Allocation.split(n("1000"), m("EQ" to "60", "FI" to "40")))
        val thirds = Allocation.split(n("100.01"), m("CA" to "33.33", "US" to "33.33", "INTL" to "33.34"))
        assertEquals(n("100.01"), thirds.values.fold(BigDecimal.ZERO, BigDecimal::add))
        assertFailsWith<IllegalArgumentException> { Allocation.split(n("100"), m("EQ" to "60", "FI" to "30")) }
        assertTrue(Allocation.isComplete(m("EQ" to "60", "FI" to "40")))
        assertFalse(Allocation.isComplete(m("EQ" to "110", "FI" to "-10")))
    }

    @Test
    fun `a full rebalance sells what is above target and buys what is below`() {
        val trades = Allocation.rebalance(m("EQ" to "70000", "FI" to "30000"), m("EQ" to "60", "FI" to "40"))
        assertEquals(m("EQ" to "-10000.00", "FI" to "10000.00"), trades)
        // A class with no target is sold off.
        val other = Allocation.rebalance(m("EQ" to "60000", "FI" to "39000", "GOLD" to "1000"), m("EQ" to "60", "FI" to "40"))
        assertEquals(m("FI" to "1000.00", "GOLD" to "-1000.00"), other)
        // New money is part of the total to divide.
        assertEquals(m("EQ" to "-7000.00", "FI" to "12000.00"), Allocation.rebalance(m("EQ" to "70000", "FI" to "30000"), m("EQ" to "60", "FI" to "40"), n("5000")))
    }

    @Test
    fun `new money only goes to the classes below target`() {
        // 105,000 at 60/40 is 63,000 and 42,000: fixed income is 12,000 short, equities are over.
        assertEquals(m("FI" to "5000.00"), Allocation.rebalance(m("EQ" to "70000", "FI" to "30000"), m("EQ" to "60", "FI" to "40"), n("5000"), sell = false))
        // Both short: shared by how short each is (2,000 and 18,000 of 120,000 at 60/40).
        assertEquals(m("EQ" to "2000.00", "FI" to "18000.00"), Allocation.rebalance(m("EQ" to "70000", "FI" to "30000"), m("EQ" to "60", "FI" to "40"), n("20000"), sell = false))
        // Three classes; the trades always add up to the new money exactly.
        val three = Allocation.rebalance(m("CA" to "1000", "US" to "1000", "INTL" to "0"), m("CA" to "33.33", "US" to "33.33", "INTL" to "33.34"), n("1000"), sell = false)
        assertEquals(n("1000.00"), three.values.fold(BigDecimal.ZERO, BigDecimal::add))
        assertEquals(n("1000.00"), three["INTL"])
        assertFailsWith<IllegalArgumentException> { Allocation.rebalance(m("EQ" to "1"), m("EQ" to "100"), n("-1"), sell = false) }
    }

    @Test
    fun `shares in percent`() {
        assertEquals(n("25"), Allocation.shares(m("A" to "1", "B" to "3")).getValue("A").stripTrailingZeros())
        assertEquals(n("0"), Allocation.shares(m("A" to "0")).getValue("A"))
    }
}
