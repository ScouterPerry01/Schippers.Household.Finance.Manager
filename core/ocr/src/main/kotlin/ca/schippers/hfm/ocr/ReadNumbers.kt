package ca.schippers.hfm.ocr

import java.math.BigDecimal

/**
 * The numbers of a reading (an AI answer), taken only when they are of a sensible size: at most 12
 * digits before the point and 10 after. An answer is outside input that a document can try to steer,
 * and "1E999999999" is a valid JSON number whose digits, once written out or rounded to the cent,
 * would take all the memory (security review 2026-10-07).
 */
object ReadNumbers {
    const val MAX_WHOLE_DIGITS = 12
    const val MAX_DECIMALS = 10

    /** [text] as a number, or null when it is not one or is out of size. */
    fun parse(text: String): BigDecimal? = text.toBigDecimalOrNull()?.takeIf(::sensible)

    fun sensible(n: BigDecimal): Boolean = n.precision() - n.scale() <= MAX_WHOLE_DIGITS && n.scale() <= MAX_DECIMALS
}
