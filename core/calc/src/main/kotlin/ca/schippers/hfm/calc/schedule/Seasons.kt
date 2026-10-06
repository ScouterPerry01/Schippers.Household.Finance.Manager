package ca.schippers.hfm.calc.schedule

import ca.schippers.hfm.calc.rules.Rules
import ca.schippers.hfm.calc.rules.Thresholds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** SEA-02: the four seasons of the seasonal checklist, in the order of the year from spring. */
enum class Season { SPRING, SUMMER, FALL, WINTER }

/** One season of one year: from [start] up to the day before [end]. */
data class SeasonWindow(val season: Season, val start: LocalDate, val end: LocalDate) {
    operator fun contains(day: LocalDate): Boolean = day >= start && day < end
}

/**
 * SEA-03: when each season starts, from Rates and rules (season.springStart...: the astronomical
 * seasons by default, which the household can change). Winter runs over the new year.
 */
object Seasons {

    /** The month and day [season] starts, as the rules give them on [on]. */
    fun startOf(season: Season, on: LocalDate): Pair<Int, Int> = Rules.monthDay("season.${season.name.lowercase()}Start", on)

    /** The day [season] starts in [year] (winter: the one that starts at the end of [year]). */
    fun start(season: Season, year: Int): LocalDate {
        val (m, d) = startOf(season, LocalDate(year, 1, 1))
        return Thresholds.dayIn(year, m, d)
    }

    /** The season [day] falls in, with its dates. */
    fun windowOf(day: LocalDate): SeasonWindow {
        // Every season start from the year before to the year after, in order: the last one on or before the day.
        val starts = (day.year - 1..day.year + 1).flatMap { y -> Season.entries.map { it to start(it, y) } }.sortedBy { it.second }
        val i = starts.indexOfLast { it.second <= day }
        return SeasonWindow(starts[i].first, starts[i].second, starts[i + 1].second)
    }

    /** The season [season] as it is now (when [today] is in it), or the next time it comes. */
    fun window(season: Season, today: LocalDate): SeasonWindow {
        var w = windowOf(today)
        // At most three steps: the following seasons in turn.
        while (w.season != season) w = windowOf(w.end)
        return w
    }

    /** The four seasons, the current one first, each as it is now or will next be. */
    fun coming(today: LocalDate): List<SeasonWindow> {
        val out = ArrayList<SeasonWindow>()
        var w = windowOf(today)
        repeat(4) {
            out += w
            w = windowOf(w.end)
        }
        return out
    }

    /** SEA-01: whether [day] is within the part of the year from [from] to [to] (month and day; it may run over the new year). */
    fun inPart(day: LocalDate, from: Pair<Int, Int>, to: Pair<Int, Int>): Boolean {
        val md = day.month.number * 100 + day.day
        val f = from.first * 100 + from.second
        val t = to.first * 100 + to.second
        return if (f <= t) md in f..t else md >= f || md <= t
    }

    /** SEA-01: [day], or the next start of the part of the year from [from] to [to] when [day] is outside it. */
    fun intoPart(day: LocalDate, from: Pair<Int, Int>, to: Pair<Int, Int>): LocalDate {
        if (inPart(day, from, to)) return day
        val thisYear = Thresholds.dayIn(day.year, from.first, from.second)
        return if (thisYear > day) thisYear else Thresholds.dayIn(day.year + 1, from.first, from.second)
    }

    /** "06-01" as (6, 1); null when blank or not a valid month and day. */
    fun parseMonthDay(text: String?): Pair<Int, Int>? {
        val parts = text?.trim()?.split('-')?.takeIf { it.size == 2 } ?: return null
        val m = parts[0].toIntOrNull() ?: return null
        val d = parts[1].toIntOrNull() ?: return null
        return runCatching { Thresholds.dayIn(2024, m, d) }.getOrNull()?.let { m to d }
    }

    fun formatMonthDay(md: Pair<Int, Int>): String = "%02d-%02d".format(md.first, md.second)
}
