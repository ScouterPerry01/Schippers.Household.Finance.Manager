package ca.schippers.hfm.calc.rules

import ca.schippers.hfm.calc.Province
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** Today on this computer, for a rule read where nothing gives a date (a new record's defaults). */
fun ruleToday(): LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

/**
 * Reminder lead times and windows (Rates and rules, area "reminders"): the figures the app used to
 * have built in, read by date so the household can change them (the owner's decision 2026-10-05).
 * Each is kept within [RuleLimits] when read, so a value entered before a limit existed still works.
 */
object LeadTimes {
    fun renewals(on: LocalDate): Int = days("reminder.renewals", on)
    fun warranty(on: LocalDate): Int = days("reminder.warranty", on)
    fun assetsReport(on: LocalDate): Int = days("reminder.assetsReport", on)
    fun cardPayment(on: LocalDate): Int = days("reminder.cardPayment", on)
    fun billsAgenda(on: LocalDate): Int = days("reminder.billsAgenda", on)
    fun billsForecast(on: LocalDate): Int = days("reminder.billsForecast", on)
    fun instalment(on: LocalDate): Int = days("reminder.instalment", on)
    fun medicalClaim(on: LocalDate): Int = days("reminder.medicalClaim", on)
    fun maturity(on: LocalDate): Int = days("reminder.maturity", on)
    fun insurance(on: LocalDate): Int = days("reminder.insurance", on)

    /** A new bill's reminders, in days before it is due, most days first. */
    fun newBill(on: LocalDate = ruleToday()): List<Int> =
        Rules.list("reminder.newBill", on).map { it.toInt().coerceIn(0, 365) }.distinct().sortedDescending()

    /** A new calendar event's reminder, in minutes before it starts. */
    fun newEvent(on: LocalDate = ruleToday()): Int = days("reminder.newEvent", on)
    fun refill(on: LocalDate = ruleToday()): Int = days("reminder.refill", on)
    fun maintenance(on: LocalDate = ruleToday()): Int = days("reminder.maintenance", on)
    fun loanRenewal(on: LocalDate = ruleToday()): Int = days("reminder.loanRenewal", on)
    fun medicalPlanDeadline(on: LocalDate = ruleToday()): Int = days("reminder.medicalPlanDeadline", on)

    /** How long a phone pairing invitation stays valid, in milliseconds. */
    fun syncInvitationMillis(on: LocalDate = ruleToday()): Long = days("reminder.syncInvitation", on) * 60_000L
    fun documentMatch(on: LocalDate = ruleToday()): Int = days("reminder.documentMatch", on)
    fun billMatch(on: LocalDate = ruleToday()): Int = days("reminder.billMatch", on)

    /** Years filed documents are kept before they are offered for discarding. */
    fun documentRetention(on: LocalDate = ruleToday()): Int = days("reminder.documentRetention", on)

    private fun days(key: String, on: LocalDate): Int = RuleLimits.clamp(key, Rules.decimal(key, on)).toInt()
}

/** Thresholds (Rates and rules, area "thresholds"), read by date like [LeadTimes]. */
object Thresholds {
    /** An account not reconciled for more than this many days is behind (REC-09). */
    fun reconcileBehind(on: LocalDate): Int = number("threshold.reconcileBehind", on).toInt()

    /** A bill above this share of its average is unusual: 1.25 for 125 %. */
    fun unusualBill(on: LocalDate): BigDecimal = number("threshold.unusualBill", on).movePointLeft(2)

    /** A budget is flagged once spending reaches this share of it: 1 for 100 %. */
    fun budgetAlert(on: LocalDate = ruleToday()): BigDecimal = number("threshold.budgetAlert", on).movePointLeft(2)

    /** Interest at or above this, in dollars, from one payer in a year comes with a T5. */
    fun t5Interest(on: LocalDate): BigDecimal = Rules.decimal("threshold.t5Interest", on)

    /** How far a foreign-currency statement line may differ, in percent (3.5). */
    fun statementFxPercent(on: LocalDate = ruleToday()): BigDecimal = Rules.decimal("threshold.statementFx", on).movePointRight(2)

    fun receiptTaxAmount(on: LocalDate = ruleToday()): BigDecimal = Rules.decimal("threshold.receiptTaxAmount", on)
    fun receiptTaxRate(on: LocalDate = ruleToday()): BigDecimal = Rules.decimal("threshold.receiptTaxRate", on)

    fun depreciationYears(on: LocalDate = ruleToday()): Int = number("threshold.depreciationYears", on).toInt()

    /** The residual value in percent (0 to 100). */
    fun depreciationResidualPercent(on: LocalDate = ruleToday()): BigDecimal = Rules.decimal("threshold.depreciationResidual", on).movePointRight(2)

    /** Every how many days automatic backups run by default: 0 off, 1 daily, 7 weekly. */
    fun backupEvery(on: LocalDate = ruleToday()): Int = number("threshold.backupEvery", on).toInt()
    fun backupKeep(on: LocalDate = ruleToday()): Int = number("threshold.backupKeep", on).toInt()

    /** UTL-01: a month's use above this share of the same month last year is unusual: 1.3 for 130 %. */
    fun unusualUtility(on: LocalDate = ruleToday()): BigDecimal = number("threshold.unusualUtility", on).movePointLeft(2)

    /** UTL-02: order fuel when a tank is expected to fall to this percentage of its capacity... */
    fun tankOrderLevel(on: LocalDate = ruleToday()): Int = number("threshold.tankOrderLevel", on).toInt()

    /** ...within this many days. */
    fun tankOrderDays(on: LocalDate = ruleToday()): Int = number("threshold.tankOrderDays", on).toInt()

    /** VOL-01: the hours of volunteer firefighting and search and rescue the two tax amounts require in [year]. */
    fun volunteerHours(year: Int): Int = number("threshold.volunteerHours", LocalDate(year, 12, 31)).toInt()

    /** When winter tires go on and come off in [province]: month to day. */
    fun winterTiresOn(on: LocalDate, province: Province?): Pair<Int, Int> = Rules.monthDay("vehicle.winterTiresOn", on, province)
    fun winterTiresOff(on: LocalDate, province: Province?): Pair<Int, Int> = Rules.monthDay("vehicle.winterTiresOff", on, province)

    /** The tax instalment due dates of [year], in order. */
    fun instalmentDueDates(year: Int): List<LocalDate> = (1..4).map { i ->
        val (month, day) = Rules.monthDay("instalment.due$i", LocalDate(year, 1, 1))
        dayIn(year, month, day)
    }

    /** [month]-[day] in [year]; February 29 is the 28th in other years. */
    fun dayIn(year: Int, month: Int, day: Int): LocalDate =
        if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) LocalDate(year, 2, 28) else LocalDate(year, month, day)

    private fun number(key: String, on: LocalDate): BigDecimal = RuleLimits.clamp(key, Rules.decimal(key, on))
}
