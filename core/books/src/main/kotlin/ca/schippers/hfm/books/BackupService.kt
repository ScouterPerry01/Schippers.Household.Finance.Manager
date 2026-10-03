package ca.schippers.hfm.books

import ca.schippers.hfm.data.BackupInfo
import ca.schippers.hfm.data.Backups
import ca.schippers.hfm.data.EncryptedDriverFactory
import ca.schippers.hfm.data.VerifyResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class BackupFrequency { OFF, DAILY, WEEKLY }

/** BAK-01 / BAK-02: where backups go (local, external, network or cloud folder), how often, how many kept. */
data class BackupSettings(val dir: Path?, val frequency: BackupFrequency, val keep: Int)

data class BackupStatus(val lastSuccess: Instant?, val lastProblem: String?)

data class BackupRun(val backup: BackupInfo, val check: VerifyResult, val pruned: Int)

/**
 * Scheduled backups for the open household (BAK-01 to BAK-04). Each run writes a backup, checks
 * that it can be restored (BAK-03), and only then deletes versions beyond the number to keep.
 */
class BackupService internal constructor(private val books: Books) {

    fun settings(): BackupSettings = BackupSettings(
        dir = books.setting(DIR)?.let { Path.of(it) },
        frequency = books.setting(FREQUENCY)?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() } ?: BackupFrequency.DAILY,
        keep = books.setting(KEEP)?.toIntOrNull() ?: 10,
    )

    fun saveSettings(settings: BackupSettings) {
        validate(settings.keep in 1..365, "error.backupKeep")
        settings.dir?.let { books.putSetting(DIR, it.toAbsolutePath().toString()) }
        books.putSetting(FREQUENCY, settings.frequency.name)
        books.putSetting(KEEP, settings.keep.toString())
        books.session.audit("BACKUP_SETTINGS", "household", books.session.householdId, "${settings.frequency} keep ${settings.keep}")
    }

    fun status(): BackupStatus = BackupStatus(
        books.setting(LAST_SUCCESS)?.toLongOrNull()?.let(Instant::ofEpochMilli),
        books.setting(LAST_PROBLEM)?.ifBlank { null },
    )

    /** True when a scheduled backup should run now. */
    fun isDue(now: Instant): Boolean {
        val s = settings()
        if (s.frequency == BackupFrequency.OFF || s.dir == null) return false
        val last = status().lastSuccess ?: return true
        val interval = if (s.frequency == BackupFrequency.DAILY) Duration.ofHours(20) else Duration.ofDays(7)
        return Duration.between(last, now) >= interval
    }

    /** BAK-04: no successful backup in the last 7 days. */
    fun needsReminder(now: Instant): Boolean {
        val last = status().lastSuccess ?: return true
        return Duration.between(last, now) > Duration.ofDays(7)
    }

    fun backups(): List<BackupInfo> = settings().dir?.let { Backups.list(it, books.session.householdId) }.orEmpty()

    /** Backs up now, checks the copy, and prunes old versions only if the check passed. */
    fun backUpNow(drivers: EncryptedDriverFactory, now: LocalDateTime = LocalDateTime.now()): BackupRun {
        val s = settings()
        val dir = s.dir ?: throw ValidationException("error.backupFolder")
        try {
            val backup = Backups.create(books.session, dir, now)
            val check = Backups.verify(backup.file, books.session, drivers)
            val pruned = if (check.ok) Backups.prune(dir, books.session.householdId, s.keep).size else 0
            if (check.ok) {
                books.putSetting(LAST_SUCCESS, backup.createdAt.toEpochMilli().toString())
                books.putSetting(LAST_PROBLEM, "")
            } else {
                books.putSetting(LAST_PROBLEM, check.problems.joinToString("; "))
            }
            return BackupRun(backup, check, pruned)
        } catch (e: Exception) {
            books.putSetting(LAST_PROBLEM, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun verify(backup: Path, drivers: EncryptedDriverFactory): VerifyResult = Backups.verify(backup, books.session, drivers)

    /**
     * EXP-01: everything this user can see, in open formats: one CSV file per table and one JSON
     * file per database, in a ZIP. The export is NOT encrypted; the user is warned before saving.
     */
    fun exportAll(target: Path) {
        val databases = books.session.readableTables()
        Files.createDirectories(target.toAbsolutePath().parent)
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(README.toByteArray())
            zip.closeEntry()
            for ((database, tables) in databases) {
                val folder = database.replace(Regex("""[\\/:*?"<>|]"""), "-")
                for (table in tables) {
                    zip.putNextEntry(ZipEntry("$folder/${table.name}.csv"))
                    zip.write(csv(table.columns, table.rows).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                val json = JsonObject(
                    tables.associate { t ->
                        t.name to JsonArray(t.rows.map { row -> JsonObject(t.columns.zip(row).associate { (c, v) -> c to (v?.let(::JsonPrimitive) ?: JsonNull) }) })
                    },
                )
                zip.putNextEntry(ZipEntry("$folder.json"))
                zip.write(json.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        books.session.audit("EXPORT", "household", books.session.householdId, target.fileName.toString())
    }

    private fun csv(columns: List<String>, rows: List<List<String?>>): String = buildString {
        fun quote(s: String?) = when {
            s == null -> ""
            s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } -> "\"" + s.replace("\"", "\"\"") + "\""
            else -> s
        }
        append('﻿')
        append(columns.joinToString(",", transform = ::quote)).append("\r\n")
        rows.forEach { append(it.joinToString(",", transform = ::quote)).append("\r\n") }
    }

    private companion object {
        const val DIR = "backup.dir"
        const val FREQUENCY = "backup.frequency"
        const val KEEP = "backup.keep"
        const val LAST_SUCCESS = "backup.lastSuccess"
        const val LAST_PROBLEM = "backup.lastProblem"

        val README = """
            RANN's Roost - full data export
            ===============================

            This archive contains all the data you could see when it was made, in open formats:
            one CSV file per table (UTF-8, comma separated) and one JSON file per database.

            Amounts are in minor units (cents; satoshis for crypto-assets) in columns ending
            with "_minor", with the currency in a separate column. Dates are YYYY-MM-DD.
            Identifiers link rows between tables.

            THIS EXPORT IS NOT ENCRYPTED. Keep it somewhere safe, or delete it when done.
        """.trimIndent() + "\n"
    }
}
