package ca.schippers.hfm.importers

/**
 * ARC-04: what a file is read into. Each kind has its own result: bank and card statements,
 * brokerage statements, a crypto exchange's history, or a Quicken (QIF) file.
 */
class ImportKind<T> private constructor(val id: String) {
    override fun toString(): String = id

    companion object {
        val STATEMENTS = ImportKind<List<ImportedStatement>>("statements")
        val INVESTMENTS = ImportKind<List<ImportedInvestmentStatement>>("investments")
        val CRYPTO_EXCHANGE = ImportKind<CryptoExchangeFile>("crypto-exchange")
        val QIF = ImportKind<QifFile>("qif")
    }
}

/**
 * ARC-04: one file format the app can import. [canRead] is a quick look at the name and the first
 * bytes, so the right reader is found; [read] reads the whole file. Every importer is listed once,
 * in [ImporterRegistry].
 */
interface FileImporter<T> {
    /** Stable identifier, e.g. "ofx" or "csv"; unique within its [kind]. */
    val id: String
    val kind: ImportKind<T>
    fun canRead(fileName: String, head: ByteArray): Boolean
    fun read(bytes: ByteArray, options: ImportOptions = ImportOptions()): T
}

/** A statement importer as a [FileImporter]; [importer] is the reader itself (the CSV one also previews and maps columns). */
class StatementFormat(val importer: StatementImporter) : FileImporter<List<ImportedStatement>> {
    override val id: String get() = importer.id
    override val kind = ImportKind.STATEMENTS
    override fun canRead(fileName: String, head: ByteArray) = importer.canRead(fileName, head)
    override fun read(bytes: ByteArray, options: ImportOptions) = importer.read(bytes.inputStream(), options)
}

/** A brokerage importer as a [FileImporter]. */
class InvestmentFormat(val importer: InvestmentImporter) : FileImporter<List<ImportedInvestmentStatement>> {
    override val id: String get() = importer.id
    override val kind = ImportKind.INVESTMENTS
    override fun canRead(fileName: String, head: ByteArray) = importer.canReadInvestments(fileName, head)
    override fun read(bytes: ByteArray, options: ImportOptions) = importer.readInvestments(bytes.inputStream(), options)
}

/**
 * ARC-04: the one list of importers. A new format is added by writing a [FileImporter] (or a
 * [StatementImporter] or [InvestmentImporter]) and listing it here; the screens and services ask
 * the registry, so nothing else changes. For each kind, the first importer that recognises a file
 * reads it, in the order listed.
 */
object ImporterRegistry {

    private val ofx = OfxImporter()

    val all: List<FileImporter<*>> = listOf(
        StatementFormat(ofx),
        StatementFormat(CsvImporter()),
        InvestmentFormat(ofx),
        InvestmentFormat(InvestmentCsvImporter()),
        CryptoExchangeImporter,
        QifParser,
    )

    /** The importers of one kind, in order. */
    @Suppress("UNCHECKED_CAST") // Each importer's kind is checked; ImportKind<T> holds the type.
    fun <T> of(kind: ImportKind<T>): List<FileImporter<T>> = all.filter { it.kind === kind } as List<FileImporter<T>>

    /** The first importer of [kind] that recognises the file, or null. */
    fun <T> forFile(kind: ImportKind<T>, fileName: String, head: ByteArray): FileImporter<T>? = of(kind).firstOrNull { it.canRead(fileName, head) }
}
