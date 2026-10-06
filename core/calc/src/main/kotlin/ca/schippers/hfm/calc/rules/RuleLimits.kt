package ca.schippers.hfm.calc.rules

import java.math.BigDecimal

/**
 * The ranges some rules must stay within, beyond what their type allows: a password of at least 8
 * characters, a key derivation no cheaper than the OWASP minimum (19 MiB, 2 passes), a backup count
 * of at least one. A value outside is refused when entered ([allows]) and brought into range when
 * read ([clamp]), so an older value can never weaken them.
 */
object RuleLimits {

    private val RANGES: Map<String, ClosedRange<BigDecimal>> = mapOf(
        "security.password.minLength" to 8..64,
        "security.argon2.memory" to 19..4096,
        "security.argon2.iterations" to 2..50,
        "reminder.newEvent" to 0..(60 * 24 * 60),
        "reminder.refill" to 0..60,
        "reminder.loanRenewal" to 0..365,
        "reminder.medicalPlanDeadline" to 1..3650,
        "reminder.syncInvitation" to 1..60,
        "reminder.documentRetention" to 1..100,
        "threshold.unusualBill" to 100..1000,
        "threshold.budgetAlert" to 1..1000,
        "threshold.depreciationYears" to 1..100,
        "threshold.backupEvery" to 0..365,
        "threshold.backupKeep" to 1..365,
        "threshold.unusualUtility" to 100..1000,
        "threshold.tankOrderLevel" to 1..90,
        "threshold.tankOrderDays" to 0..365,
        "threshold.volunteerHours" to 1..2000,
    ).mapValues { (_, r) -> BigDecimal(r.first)..BigDecimal(r.last) }

    /** The allowed range of [key], or null when its type is the only limit. */
    fun range(key: String): ClosedRange<BigDecimal>? = RANGES[key]

    /** Whether [value] (already checked against the rule's type) is within [key]'s range. */
    fun allows(key: String, value: String): Boolean {
        val range = RANGES[key] ?: return true
        val n = value.trim().toBigDecimalOrNull() ?: return false
        return n in range && (key in FRACTIONS || n.stripTrailingZeros().scale() <= 0)
    }

    /** Ranged rules that may have decimals; the others are whole numbers. */
    private val FRACTIONS = setOf("threshold.unusualBill", "threshold.budgetAlert", "threshold.unusualUtility")

    fun clamp(key: String, value: BigDecimal): BigDecimal {
        val range = RANGES[key] ?: return value
        return value.max(range.start).min(range.endInclusive)
    }
}
