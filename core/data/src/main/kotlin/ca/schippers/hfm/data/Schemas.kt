package ca.schippers.hfm.data

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Opens an encrypted SQLite database file with a raw 256-bit key. Implemented per platform. */
fun interface EncryptedDriverFactory {
    /** @throws WrongKeyException if the key does not decrypt the file. */
    fun open(file: Path, key: ByteArray): SqlDriver
}

class WrongKeyException(file: Path, cause: Throwable? = null) : Exception("Cannot decrypt $file", cause)

/**
 * Creates or upgrades a database to the schema version this build expects (NFR-11).
 * Before any upgrade the database file is copied to `backups/pre-upgrade/`, so a failed or
 * unwanted upgrade can always be undone.
 */
object SchemaManager {

    fun prepare(driver: SqlDriver, schema: SqlSchema<QueryResult.Value<Unit>>, file: Path, now: LocalDateTime = LocalDateTime.now()) {
        val current = userVersion(driver)
        when {
            current == 0L -> driver.transaction {
                schema.create(driver)
                setUserVersion(driver, schema.version)
            }
            current < schema.version -> {
                // Fold the write-ahead log into the main file so the copy is complete.
                driver.executeQuery(null, "PRAGMA wal_checkpoint(TRUNCATE)", { QueryResult.Value(Unit) }, 0)
                backupBeforeUpgrade(file, current, now)
                driver.transaction {
                    schema.migrate(driver, current, schema.version)
                    setUserVersion(driver, schema.version)
                }
            }
            current > schema.version -> throw NewerFormatException(current.toInt(), schema.version.toInt())
        }
    }

    fun userVersion(driver: SqlDriver): Long =
        driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        }, 0).value

    private fun setUserVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }

    internal fun backupBeforeUpgrade(file: Path, fromVersion: Long, now: LocalDateTime): Path {
        val dir = file.parent.resolve("backups").resolve("pre-upgrade")
        Files.createDirectories(dir)
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = dir.resolve("${file.fileName}.v$fromVersion.$stamp")
        Files.copy(file, target)
        return target
    }

    private fun SqlDriver.transaction(block: () -> Unit) {
        object : TransacterImpl(this) {}.transaction { block() }
    }
}
