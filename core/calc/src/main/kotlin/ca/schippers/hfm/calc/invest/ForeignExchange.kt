package ca.schippers.hfm.calc.invest

import java.math.BigDecimal

/**
 * FX-05: an individual's foreign exchange gains and losses on capital transactions. The CRA lets
 * an individual leave out the first $200 of the year's net gain or loss (Income Tax Act s. 39(2));
 * only the part beyond it is a capital gain or loss.
 */
object ForeignExchange {

    val EXEMPTION: BigDecimal = BigDecimal(200)

    /** The part of the year's [net] foreign exchange gain (or loss, negative) to report. */
    fun reportable(net: BigDecimal): BigDecimal = when {
        net.abs() <= EXEMPTION -> BigDecimal.ZERO.setScale(net.scale())
        net.signum() > 0 -> net - EXEMPTION
        else -> net + EXEMPTION
    }
}
