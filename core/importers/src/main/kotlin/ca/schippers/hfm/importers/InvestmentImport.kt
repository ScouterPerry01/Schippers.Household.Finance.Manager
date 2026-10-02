package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import kotlinx.datetime.LocalDate
import java.io.InputStream
import java.math.BigDecimal

/** What an imported investment action does (INV-02). */
enum class ImportedAction { BUY, SELL, DIVIDEND, INTEREST, DISTRIBUTION, REINVEST, RETURN_OF_CAPITAL, SPLIT, TRANSFER_IN, TRANSFER_OUT, FEE, CASH_IN, CASH_OUT }

/** A security as a brokerage file describes it. [key] links actions and positions to it. */
data class ImportedSecurity(
    val key: String,
    val symbol: String?,
    val name: String,
    /** CUSIP or ISIN when the file gives one. */
    val identifier: String?,
    val kind: String?,
    val price: BigDecimal?,
    val priceDate: LocalDate?,
)

/**
 * One brokerage action. Quantities, prices and amounts are positive; [action] says the direction.
 * [amount] is the gross value (quantity times price, or the income); [fees] and [withheld] are
 * separate. For a split, [ratio] is the new units per old unit.
 */
data class ImportedInvestmentAction(
    val externalId: String?,
    val date: LocalDate,
    val action: ImportedAction,
    val securityKey: String?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal?,
    val fees: BigDecimal?,
    val withheld: BigDecimal?,
    val ratio: BigDecimal?,
    /** For reinvested income: what kind of income it was. */
    val incomeAction: ImportedAction?,
    val memo: String?,
)

data class ImportedPosition(val securityKey: String, val quantity: BigDecimal, val price: BigDecimal?)

/** An investment account's statement: securities, actions, and (when given) the holdings and cash at [asOf] (REC-08). */
data class ImportedInvestmentStatement(
    val format: String,
    val accountNumberHint: String?,
    val currency: Currency,
    val asOf: LocalDate?,
    val cash: BigDecimal?,
    val securities: List<ImportedSecurity>,
    val actions: List<ImportedInvestmentAction>,
    val positions: List<ImportedPosition>,
    /** Rows that could not be read, in English, for the import summary. */
    val warnings: List<String> = emptyList(),
)

/** Plug-in contract for brokerage files (INV-05, ARC-04). */
interface InvestmentImporter {
    val id: String
    fun canReadInvestments(fileName: String, head: ByteArray): Boolean
    fun readInvestments(input: InputStream, options: ImportOptions = ImportOptions()): List<ImportedInvestmentStatement>
}

object InvestmentImporters {
    val all: List<InvestmentImporter> = listOf(OfxImporter(), InvestmentCsvImporter())

    fun forFile(fileName: String, head: ByteArray): InvestmentImporter? = all.firstOrNull { it.canReadInvestments(fileName, head) }
}
