package ca.schippers.hfm.calc

import java.math.BigDecimal
import java.math.MathContext

/** Precision for intermediate rate calculations: 34 significant digits (IEEE decimal128). */
internal val CALC: MathContext = MathContext.DECIMAL128

/** Computes [base]^(numerator/denominator) exactly enough for money work, without floating point. */
internal fun rationalPower(base: BigDecimal, numerator: Int, denominator: Int): BigDecimal {
    require(base.signum() > 0) { "Base must be positive" }
    require(denominator > 0) { "Denominator must be positive" }
    val raised = if (numerator >= 0) {
        base.pow(numerator, CALC)
    } else {
        BigDecimal.ONE.divide(base.pow(-numerator, CALC), CALC)
    }
    return nthRoot(raised, denominator)
}

/** Newton's method n-th root at [CALC] precision. */
internal fun nthRoot(value: BigDecimal, n: Int): BigDecimal {
    require(value.signum() > 0) { "Value must be positive" }
    require(n > 0) { "Root must be positive" }
    if (n == 1) return value
    val nBig = BigDecimal(n)
    val nMinus1 = BigDecimal(n - 1)
    // Start from the floating point estimate, then refine to full precision.
    var x = BigDecimal(Math.pow(value.toDouble(), 1.0 / n), CALC)
    val tolerance = BigDecimal.ONE.movePointLeft(CALC.precision - 2)
    repeat(100) {
        val next = nMinus1.multiply(x, CALC)
            .add(value.divide(x.pow(n - 1, CALC), CALC), CALC)
            .divide(nBig, CALC)
        if (next.subtract(x).abs().compareTo(tolerance) <= 0) return next
        x = next
    }
    return x
}

