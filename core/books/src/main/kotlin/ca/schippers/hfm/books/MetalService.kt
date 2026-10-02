package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.Disposition
import ca.schippers.hfm.calc.invest.normalized
import ca.schippers.hfm.calc.metals.Metals
import ca.schippers.hfm.calc.metals.WeightUnit
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.sum
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import ca.schippers.hfm.data.ledger.Metal_item as ItemRow

enum class MetalForm { COIN, BAR, ROUND, JEWELLERY, OTHER }
enum class MetalStorage { HOME_SAFE, BANK_BOX, VAULT, DEALER, OTHER }

/**
 * PM-01, PM-03: one line of coins, bars or rounds. [cost] is what was paid in all, premium
 * included; [premiumPercent] adjusts the spot value (coins above melt, a buy-back discount below).
 */
data class MetalItem(
    val id: String,
    val accountId: String,
    val metal: Metal,
    val form: MetalForm,
    val description: String,
    val weight: BigDecimal,
    val unit: WeightUnit,
    val purity: BigDecimal,
    val quantity: Int,
    val serialNumbers: String? = null,
    val dealer: String? = null,
    val purchaseDate: LocalDate? = null,
    val cost: Money? = null,
    val premiumPercent: BigDecimal? = null,
    val storage: MetalStorage = MetalStorage.HOME_SAFE,
    val storageDetail: String? = null,
    val insured: Boolean = false,
    val insuranceNote: String? = null,
    val disposalDate: LocalDate? = null,
    val proceeds: Money? = null,
    val notes: String? = null,
) {
    val fineOunces: BigDecimal get() = Metals.fineOunces(quantity, weight, unit, purity)
    val held: Boolean get() = disposalDate == null
}

/** PM-02: an item's value on a date; null without a spot price. */
data class MetalValuation(val item: MetalItem, val spot: SpotPrice?, val value: Money?) {
    val gain: Money? get() = value?.let { v -> item.cost?.let { v - it } }
}

/** PM-01 to PM-04: precious metal items, their value from spot prices, sales and certificates. */
class MetalService internal constructor(private val books: Books) {

    fun accounts(): List<Account> = books.accounts.list().map { it.account }.filter { it.type == AccountType.PRECIOUS_METALS }

    fun items(accountId: String, includeSold: Boolean = false): List<MetalItem> {
        val (group, account) = books.accounts.locate(accountId)
        return books.ledger(group).metalsQueries.metalItems(accountId).executeAsList().map { it.toItem(account.currency) }.filter { includeSold || it.held }
    }

    fun save(item: MetalItem): MetalItem {
        val (group, account) = books.accounts.locate(item.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type == AccountType.PRECIOUS_METALS, "error.notMetalsAccount")
        validate(item.description.isNotBlank(), "error.nameRequired")
        validate(item.quantity > 0 && item.weight.signum() > 0, "error.quantityRequired")
        validate(item.purity.signum() > 0 && item.purity <= BigDecimal.ONE, "error.purity")
        listOfNotNull(item.cost, item.proceeds).forEach { validate(it.currency == account.currency && !it.isNegative, "error.currencyMismatch", account.currency.code) }
        val q = books.ledger(group).metalsQueries
        val id = item.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.metalItemById(id).executeAsOneOrNull()?.created_at ?: now
        val s = item.copy(id = id)
        q.upsertMetalItem(
            id, s.accountId, s.metal.name, s.form.name, s.description.trim(), s.weight.normalized().toPlainString(), s.unit.name, s.purity.normalized().toPlainString(),
            s.quantity.toLong(), s.serialNumbers.blankToNull(), s.dealer.blankToNull(), s.purchaseDate?.toString(), s.cost?.minorUnits,
            s.premiumPercent?.normalized()?.toPlainString(), s.storage.name, s.storageDetail.blankToNull(), if (s.insured) 1 else 0, s.insuranceNote.blankToNull(),
            s.disposalDate?.toString(), s.proceeds?.minorUnits, s.notes.blankToNull(), created, now,
        )
        books.session.audit("UPDATE", "metal_item", id)
        return s
    }

    /** Records the sale of an item; its capital gain or loss appears with the others. */
    fun sell(itemId: String, accountId: String, date: LocalDate, proceeds: Money): MetalItem {
        val item = items(accountId, includeSold = true).firstOrNull { it.id == itemId } ?: throw ValidationException("error.securityNotFound")
        validate(proceeds.isPositive, "error.amountPositive")
        return save(item.copy(disposalDate = date, proceeds = proceeds))
    }

    fun delete(accountId: String, itemId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        books.ledger(group).metalsQueries.deleteMetalItem(itemId)
    }

    /** Each item held on [date] with its value from that day's spot price (or the latest before). */
    fun valuations(accountId: String, date: LocalDate): List<MetalValuation> {
        val account = books.accounts.get(accountId)
        return items(accountId, includeSold = true).filter { it.purchaseDate == null || it.purchaseDate <= date }
            .filter { it.disposalDate == null || it.disposalDate > date }
            .map { item ->
                val spot = books.prices.spot(item.metal, date)
                val value = spot?.let { s ->
                    val inBase = Metals.value(item.fineOunces, Money.of(s.cadPerOz, Currency.CAD), item.premiumPercent)
                    if (account.currency == Currency.CAD) inBase else books.rates.convert(inBase, account.currency, date)
                }
                MetalValuation(item, spot, value)
            }
    }

    /** The value of everything held on [date]; an item without a spot price counts at its cost. */
    fun value(accountId: String, date: LocalDate): Money {
        val account = books.accounts.get(accountId)
        return valuations(accountId, date).map { it.value ?: it.item.cost ?: Money.zero(account.currency) }.sum(account.currency)
    }

    /**
     * Capital gains and losses on items sold through [through], each item on its own cost. Bullion
     * bought at several times is strictly identical property under the tax rules; recording each
     * purchase as its own item and selling it whole keeps the figures close.
     */
    fun gains(through: LocalDate): List<CapitalGain> = accounts().flatMap { a ->
        items(a.id, includeSold = true).filter { it.disposalDate != null && it.disposalDate <= through && it.proceeds != null }.map { item ->
            val zero = Money.zero(a.currency)
            CapitalGain(metalSecurity(item.metal), a.ownerMemberIds, Disposition(item.disposalDate!!, item.fineOunces, item.proceeds!!, item.cost ?: zero, item.id))
        }
    }

    /** PM-04: certificates and photos kept in the vault for an item. */
    fun documents(itemId: String): List<VaultDocument> = books.documents.documentsFor(ENTITY, itemId)

    fun attach(itemId: String, documentId: String) = books.documents.link(documentId, ENTITY, itemId)

    fun detach(itemId: String, documentId: String) = books.documents.unlink(documentId, ENTITY, itemId)

    fun metalSecurity(m: Metal) = Security("metal:${m.name}", m.name, null, m.name.lowercase().replaceFirstChar { it.uppercase() }, SecurityKind.OTHER, books.rates.baseCurrency, AssetClass.COMMODITY, Region.GLOBAL)

    private fun ItemRow.toItem(c: Currency) = MetalItem(
        id, account_id, Metal.valueOf(metal), MetalForm.valueOf(form), description, BigDecimal(weight), WeightUnit.valueOf(weight_unit), BigDecimal(purity),
        quantity.toInt(), serial_numbers, dealer, purchase_date?.let(LocalDate::parse), cost_minor?.let { Money.ofMinor(it, c) }, premium_percent?.let(::BigDecimal),
        MetalStorage.valueOf(storage), storage_detail, insured == 1L, insurance_note, disposal_date?.let(LocalDate::parse), proceeds_minor?.let { Money.ofMinor(it, c) }, notes,
    )

    companion object {
        const val ENTITY = "metal_item"
    }
}
