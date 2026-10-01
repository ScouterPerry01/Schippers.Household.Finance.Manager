package ca.schippers.hfm.money

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    @Test
    fun `minor units follow ISO 4217 and crypto uses 8 decimals`() {
        assertEquals(2, Currency.CAD.minorUnits)
        assertEquals(0, Currency.of("JPY").minorUnits)
        assertEquals(3, Currency.of("BHD").minorUnits)
        assertEquals(8, Currency.BTC.minorUnits)
        assertTrue(Currency.BTC.isCrypto)
    }

    @Test
    fun `unknown and metal codes are rejected`() {
        assertFailsWith<IllegalArgumentException> { Currency.of("ZZZ") }
        assertFailsWith<IllegalArgumentException> { Currency.of("XAU") }
    }

    @Test
    fun `adding never loses a cent`() {
        // 0.1 + 0.2 is the classic floating point failure.
        assertEquals(cad("0.30"), cad("0.10") + cad("0.20"))
        assertEquals(cad("1000.00"), List(10_000) { cad("0.10") }.sum(Currency.CAD))
    }

    @Test
    fun `different currencies cannot be combined`() {
        assertFailsWith<CurrencyMismatchException> { cad("1") + Money.parse("1", Currency.USD) }
    }

    @Test
    fun `parse rejects more decimals than the currency allows`() {
        assertFailsWith<IllegalArgumentException> { cad("1.005") }
        assertEquals(100_000_000L, Money.parse("1", Currency.BTC).minorUnits)
        assertEquals(1L, Money.parse("0.00000001", Currency.BTC).minorUnits)
    }

    @Test
    fun `overflow throws instead of wrapping`() {
        assertFailsWith<ArithmeticException> { Money.ofMinor(Long.MAX_VALUE, Currency.CAD) + cad("0.01") }
    }

    @Test
    fun `multiplying rounds half up to the cent`() {
        // GST 5% on 10.10 = 0.505 -> 0.51
        assertEquals(cad("0.51"), cad("10.10").times(BigDecimal("0.05")))
        // QST 9.975% on 10.00 = 0.9975 -> 1.00
        assertEquals(cad("1.00"), cad("10.00").times(BigDecimal("0.09975")))
    }

    @Test
    fun `split distributes leftover cents`() {
        assertEquals(listOf(cad("33.34"), cad("33.33"), cad("33.33")), cad("100.00").split(3))
        assertEquals(listOf(cad("-33.33"), cad("-33.33"), cad("-33.34")).sortedBy { it.minorUnits },
            cad("-100.00").split(3).sortedBy { it.minorUnits })
    }

    @Test
    fun `convert uses the rate and rounds to the target currency`() {
        val usd = Money.parse("100.00", Currency.USD)
        assertEquals(cad("137.25"), usd.convert(Currency.CAD, BigDecimal("1.372468")))
        assertEquals(Money.parse("0.00145906", Currency.BTC),
            cad("200.00").convert(Currency.BTC, BigDecimal("0.0000072953")))
    }

    @Test
    fun `allocate always adds up to the original amount`() = runBlocking {
        checkAll(
            Arb.long(-10_000_000_000L..10_000_000_000L),
            Arb.list(Arb.int(0..1000), 1..12),
        ) { minor, weights ->
            val ratios = if (weights.all { it == 0 }) weights.map { BigDecimal.ONE } else weights.map { BigDecimal(it) }
            val amount = Money.ofMinor(minor, Currency.CAD)
            val parts = amount.allocate(ratios)
            assertEquals(amount, parts.sum(Currency.CAD))
            parts.zip(ratios).filter { it.second.signum() == 0 }.forEach { assertTrue(it.first.isZero) }
        }
    }

    @Test
    fun `addition and subtraction are inverse`() = runBlocking {
        checkAll(Arb.long(-1_000_000_000_000L..1_000_000_000_000L), Arb.long(-1_000_000_000_000L..1_000_000_000_000L)) { a, b ->
            val x = Money.ofMinor(a, Currency.CAD)
            val y = Money.ofMinor(b, Currency.CAD)
            assertEquals(x, x + y - y)
        }
    }
}
