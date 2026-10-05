package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * RPT-03: the choices behind a saved report, as the reports screen makes them: the report, its
 * period, filters and, for a custom report, its layout. Names are the screen's own enum names.
 */
@Serializable
data class ReportDefinition(
    val kind: String,
    val preset: String,
    val customFrom: String? = null,
    val customTo: String? = null,
    val groupId: String? = null,
    val memberId: String? = null,
    val tagId: String? = null,
    /** RPT-07: a chosen set of accounts, such as the cottage's. */
    val accountIds: Set<String>? = null,
    val currency: String? = null,
    val compare: String? = null,
    val layout: CustomLayout? = null,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun fromJson(text: String): ReportDefinition? = runCatching { JSON.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/** RPT-05: how often a saved report is made: for each month, quarter or year once it has ended. */
enum class ReportSchedule { MONTHLY, QUARTERLY, YEARLY }

/** A finished period a scheduled report is made for: "2026-09", "2026-Q3" or "2025". */
data class ReportPeriod(val id: String, val from: LocalDate, val to: LocalDate)

/**
 * RPT-03: a report the user saved. [definition] is the screen's choices as JSON (report, period,
 * filters, layout), kept as the apps write it; no amounts are stored.
 */
data class SavedReport(
    val id: String,
    val name: String,
    val definition: String,
    val schedule: ReportSchedule? = null,
    /** RPT-05: where the scheduled PDF is written. */
    val folder: String? = null,
    val lastPeriod: String? = null,
)

/** RPT-03, RPT-05: saved reports, each the signed-in user's own, and which scheduled ones are due. */
class SavedReportService internal constructor(private val books: Books) {

    private val q get() = books.session.core.savedReportQueries

    fun list(): List<SavedReport> = q.savedReports(books.userId).executeAsList().map {
        SavedReport(it.id, it.name, it.definition, it.schedule?.let(ReportSchedule::valueOf), it.folder, it.last_period)
    }

    fun save(report: SavedReport): SavedReport {
        validate(report.name.isNotBlank(), "error.nameRequired")
        validate(report.schedule == null || !report.folder.isNullOrBlank(), "error.reportFolder")
        val id = report.id.ifBlank { Ids.newId() }
        val existing = list().firstOrNull { it.id == id }
        // A new or changed schedule starts with the period that just ended, not with every past one.
        val last = if (existing?.schedule == report.schedule) report.lastPeriod else null
        q.upsertSavedReport(id, books.userId, report.name.trim(), report.definition, report.schedule?.name, report.folder?.trim()?.ifEmpty { null }, last, books.now())
        return report.copy(id = id, name = report.name.trim(), lastPeriod = last)
    }

    fun delete(id: String) = q.deleteSavedReport(id, books.userId)

    /** RPT-05: the scheduled reports with a finished period not made yet, as of [today]. */
    fun due(today: LocalDate): List<Pair<SavedReport, ReportPeriod>> = list().mapNotNull { r ->
        val schedule = r.schedule ?: return@mapNotNull null
        val period = lastFinished(schedule, today)
        if (period.id == r.lastPeriod) null else r to period
    }

    fun markMade(id: String, period: ReportPeriod) = q.setReportRun(period.id, id)

    companion object {
        /** The last month, quarter or year that ended before [today]. */
        fun lastFinished(schedule: ReportSchedule, today: LocalDate): ReportPeriod {
            val monthStart = LocalDate(today.year, today.month, 1)
            return when (schedule) {
                ReportSchedule.MONTHLY -> {
                    val from = monthStart.minus(DatePeriod(months = 1))
                    ReportPeriod("%04d-%02d".format(from.year, from.month.ordinal + 1), from, monthStart.minus(DatePeriod(days = 1)))
                }
                ReportSchedule.QUARTERLY -> {
                    val thisQuarter = LocalDate(today.year, (today.month.ordinal / 3) * 3 + 1, 1)
                    val from = thisQuarter.minus(DatePeriod(months = 3))
                    ReportPeriod("${from.year}-Q${from.month.ordinal / 3 + 1}", from, from.plus(DatePeriod(months = 3)).minus(DatePeriod(days = 1)))
                }
                ReportSchedule.YEARLY -> ReportPeriod((today.year - 1).toString(), LocalDate(today.year - 1, 1, 1), LocalDate(today.year - 1, 12, 31))
            }
        }
    }
}
