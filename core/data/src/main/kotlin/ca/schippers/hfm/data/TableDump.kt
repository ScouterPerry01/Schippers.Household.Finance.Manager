package ca.schippers.hfm.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import java.util.Base64

/** The full contents of one table, read generically, for the open-format export (EXP-01). */
class TableDump(val name: String, val columns: List<String>, val rows: List<List<String?>>) {
    companion object {
        /** Every table in the database except SQLite's own, in name order. */
        fun all(driver: SqlDriver): List<TableDump> {
            val tables = driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name", { c ->
                val names = ArrayList<String>()
                while (c.next().value) names += c.getString(0)!!
                QueryResult.Value(names)
            }, 0).value
            return tables.map { table -> dump(driver, table) }
        }

        private fun dump(driver: SqlDriver, table: String): TableDump {
            val columns = driver.executeQuery(null, "PRAGMA table_info(\"$table\")", { c ->
                val cols = ArrayList<Pair<String, String>>()
                while (c.next().value) cols += c.getString(1)!! to (c.getString(2) ?: "")
                QueryResult.Value(cols)
            }, 0).value
            // Binary columns (public keys) are written as Base64; everything else as text.
            val select = columns.joinToString(", ") { (name, type) ->
                if (type.equals("BLOB", ignoreCase = true)) "hex(\"$name\")" else "CAST(\"$name\" AS TEXT)"
            }
            val rows = driver.executeQuery(null, "SELECT $select FROM \"$table\"", { c ->
                val out = ArrayList<List<String?>>()
                while (c.next().value) {
                    out += columns.indices.map { i ->
                        val value = c.getString(i)
                        if (columns[i].second.equals("BLOB", ignoreCase = true) && value != null) {
                            Base64.getEncoder().encodeToString(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                        } else {
                            value
                        }
                    }
                }
                QueryResult.Value(out)
            }, 0).value
            return TableDump(table, columns.map { it.first }, rows)
        }
    }
}
