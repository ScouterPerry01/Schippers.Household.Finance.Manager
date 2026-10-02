package ca.schippers.hfm.data.jdbc

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ca.schippers.hfm.data.EncryptedDriverFactory
import ca.schippers.hfm.data.WrongKeyException
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.nio.file.Path
import java.sql.SQLException

/**
 * Opens SQLCipher v4 databases (AES-256, HMAC-SHA512 page authentication) with a raw 256-bit key.
 * The key comes from the household key ring, already derived with Argon2id, so SQLCipher's own
 * PBKDF2 step is skipped.
 */
class SqlCipherJdbcDriverFactory : EncryptedDriverFactory {

    override fun open(file: Path, key: ByteArray): SqlDriver {
        require(key.size == 32) { "Key must be 256 bits" }
        val properties = SQLiteMCSqlCipherConfig.getV4Defaults()
            .withRawUnsaltedKey(key)
            .build()
            .toProperties()
        properties["foreign_keys"] = "true"
        // Writers wait (rather than fail) while a backup briefly holds the write lock.
        properties["busy_timeout"] = "15000"
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", properties)
        try {
            // SQLCipher only checks the key when the first page is read.
            driver.executeQuery(null, "SELECT count(*) FROM sqlite_master", { c -> c.next(); QueryResult.Value(Unit) }, 0)
            driver.execute(null, "PRAGMA journal_mode = WAL", 0)
            driver.execute(null, "PRAGMA synchronous = FULL", 0)
        } catch (e: SQLException) {
            driver.close()
            throw WrongKeyException(file, e)
        }
        return driver
    }
}
