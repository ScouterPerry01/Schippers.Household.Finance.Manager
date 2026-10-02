package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.io.InputStream

/**
 * Plug-in contract for statement and history importers (ARC-04, NFR-13): OFX/QFX/QBO, CSV,
 * later QIF (Quicken, GnuCash, Moneydance), exchange and brokerage files. New formats are added by
 * writing another implementation and listing it in [Importers]; the core does not change.
 */
interface StatementImporter {
    /** Stable identifier stored with saved settings, e.g. "ofx" or "csv". */
    val id: String
    val fileExtensions: Set<String>

    /** Quick check on the file name and first bytes, so the right importer can be suggested. */
    fun canRead(fileName: String, head: ByteArray): Boolean

    /** Reads every account statement in the file; an OFX file may hold several accounts. */
    fun read(input: InputStream, options: ImportOptions = ImportOptions()): List<ImportedStatement>
}

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportOptions(
    /** Currency to assume when the file does not say. */
    val defaultCurrency: Currency = Currency.CAD,
    /** Importer-specific settings, e.g. a saved CSV column mapping for the institution (REC-01). */
    val mapping: Map<String, String> = emptyMap(),
)

data class ImportedStatement(
    val format: String,
    val accountNumberHint: String?,
    val currency: Currency,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val openingBalance: Money?,
    val closingBalance: Money?,
    val lines: List<ImportedLine>,
)

data class ImportedLine(
    /** The institution's own transaction id (OFX FITID) when the file has one. */
    val externalId: String?,
    val date: LocalDate,
    val amount: Money,
    val payee: String?,
    val memo: String?,
    val checkNumber: String?,
)

object Importers {
    val all: List<StatementImporter> = listOf(OfxImporter(), CsvImporter())

    fun forFile(fileName: String, head: ByteArray): StatementImporter? = all.firstOrNull { it.canRead(fileName, head) }
}
