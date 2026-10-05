package ca.schippers.hfm.calc.invest

import ca.schippers.hfm.calc.rules.decimalOrEarliest
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * FX-05: an individual's foreign exchange gains and losses on capital transactions. The CRA lets
 * an individual leave out the first $200 of the year's net gain or loss (Income Tax Act s. 39(2));
 * only the part beyond it is a capital gain or loss. The $200 is rule fx.exemption.
 */
object ForeignExchange {

    /** The exemption for [year] (rule fx.exemption). */
    fun exemption(year: Int): BigDecimal = decimalOrEarliest("fx.exemption", LocalDate(year, 12, 31))

    /** The part of [year]'s [net] foreign exchange gain (or loss, negative) to report. */
    fun reportable(net: BigDecimal, year: Int = java.time.LocalDate.now().year): BigDecimal {
        val exemption = exemption(year)
        return when {
            net.abs() <= exemption -> BigDecimal.ZERO.setScale(net.scale())
            net.signum() > 0 -> net - exemption
            else -> net + exemption
        }
    }
}
