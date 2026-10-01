package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.io.InputStream
import java.util.ServiceLoader

/**
 * Plug-in contract for statement and history importers (ARC-04, NFR-13): OFX/QFX/QBO, CSV,
 * QIF (Quicken, GnuCash, Moneydance), exchange and brokerage files. New institutions are added by
 * shipping a new implementation, registered through `META-INF/services`, without core changes.
 * First implementations arrive in Phase 1 (OFX, CSV) and Phase 2 (QIF).
 */
interface StatementImporter {
    /** Stable identifier stored with saved column mappings, e.g. "ofx" or "csv". */
    val id: String
    val fileExtensions: Set<String>

    /** Quick check on the first bytes of a file, so the right importer can be suggested. */
    fun canRead(fileName: String, head: ByteArray): Boolean

    fun read(input: InputStream, options: ImportOptions = ImportOptions()): ImportedStatement
}

data class ImportOptions(
    /** Currency to assume when the file does not say. */
    val defaultCurrency: Currency = Currency.CAD,
    /** Saved CSV column mapping for this institution (REC-01), as importer-specific settings. */
    val mapping: Map<String, String> = emptyMap(),
)

data class ImportedStatement(
    val accountNumberHint: String?,
    val currency: Currency,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val openingBalance: Money?,
    val closingBalance: Money?,
    val lines: List<ImportedLine>,
)

data class ImportedLine(
    /** The institution's own transaction id (OFX FITID) when present; used for duplicate protection (REC-10). */
    val externalId: String?,
    val date: LocalDate,
    val amount: Money,
    val payee: String?,
    val memo: String?,
    val checkNumber: String?,
)

object Importers {
    fun all(): List<StatementImporter> = ServiceLoader.load(StatementImporter::class.java).toList()

    fun forFile(fileName: String, head: ByteArray): List<StatementImporter> = all().filter { it.canRead(fileName, head) }
}
