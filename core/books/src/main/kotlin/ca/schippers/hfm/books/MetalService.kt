package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.Disposition
import ca.schippers.hfm.calc.invest.normalized
import ca.schippers.hfm.calc.metals.Metals
import ca.schippers.hfm.calc.metals.WeightUnit
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.ClearedStatus
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

/**
 * M-34: the accounts an item's money moved through: [paidFromAccountId] paid its cost on the
 * purchase date, [depositedToAccountId] received the proceeds of its sale. Null for none.
 */
data class MetalMoney(val paidFromAccountId: String? = null, val depositedToAccountId: String? = null)

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

    /**
     * Saves [item]. With [money] (M-34), the cost leaves the account paid from on the purchase date
     * and the proceeds arrive in the account deposited to on the sale date, as lines that reports
     * leave out (the metal's value counts instead); a null [money] keeps the accounts recorded, and
     * the lines follow the item's dates and amounts. Lines already reconciled change only once
     * [confirmReconciled].
     */
    fun save(item: MetalItem, money: MetalMoney? = null, confirmReconciled: Boolean = false): MetalItem {
        val (group, account) = books.accounts.locate(item.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type == AccountType.PRECIOUS_METALS, "error.notMetalsAccount")
        validate(item.description.isNotBlank(), "error.nameRequired")
        validate(item.quantity > 0 && item.weight.signum() > 0, "error.quantityRequired")
        validate(item.purity.signum() > 0 && item.purity <= BigDecimal.ONE, "error.purity")
        listOfNotNull(item.cost, item.proceeds).forEach { validate(it.currency == account.currency && !it.isNegative, "error.currencyMismatch", account.currency.code) }
        val q = books.ledger(group).metalsQueries
        val id = item.id.ifBlank { Ids.newId() }
        moveMoney(item.copy(id = id), account, money ?: money(id), confirmReconciled)
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

    /**
     * Records the sale of an item; its capital gain or loss appears with the others. With
     * [depositedToAccountId], the proceeds arrive in that account (M-34).
     */
    fun sell(itemId: String, accountId: String, date: LocalDate, proceeds: Money, depositedToAccountId: String? = null): MetalItem {
        val item = items(accountId, includeSold = true).firstOrNull { it.id == itemId } ?: throw ValidationException("error.securityNotFound")
        validate(proceeds.isPositive, "error.amountPositive")
        val money = depositedToAccountId?.let { money(itemId).copy(depositedToAccountId = it) }
        return save(item.copy(disposalDate = date, proceeds = proceeds), money)
    }

    /** Deletes an item and the money lines of its purchase and sale (M-34). */
    fun delete(accountId: String, itemId: String, confirmReconciled: Boolean = false) {
        val (group, _) = books.accounts.locate(accountId)
        books.require(group, PermissionLevel.EDIT)
        deleteLines(itemId, confirmReconciled)
        books.ledger(group).metalsQueries.deleteMetalItem(itemId)
    }

    /** M-34: the accounts the item's purchase was paid from and its sale deposited to. */
    fun money(itemId: String): MetalMoney {
        val lines = linkedLines(itemId)
        return MetalMoney(lines.firstOrNull { it.amount_minor < 0 }?.account_id, lines.firstOrNull { it.amount_minor > 0 }?.account_id)
    }

    private fun linkedLines(itemId: String) = books.groups().flatMap { books.ledger(it).ledgerQueries.txnsForInvestment(itemId).executeAsList() }

    private fun deleteLines(itemId: String, confirmReconciled: Boolean) {
        for (g in books.groups()) {
            if (books.ledger(g).ledgerQueries.txnsForInvestment(itemId).executeAsList().isNotEmpty()) books.transactions.deleteForInvestment(g, itemId, confirmReconciled)
        }
    }

    /**
     * Rewrites the item's money lines: the purchase (money out) and the sale (money in) each only
     * when its account, date or amount differs from the line recorded.
     */
    private fun moveMoney(item: MetalItem, account: Account, money: MetalMoney, confirmReconciled: Boolean) {
        data class Line(val accountId: String, val date: LocalDate, val amount: Money, val memo: String)
        val purchase = money.paidFromAccountId?.let {
            validate(item.cost?.isPositive == true && item.purchaseDate != null, "error.metalPaidNeedsCost")
            Line(it, item.purchaseDate!!, -item.cost!!, books.text("generated.buy", item.description))
        }
        val sale = money.depositedToAccountId?.let {
            validate(item.proceeds?.isPositive == true && item.disposalDate != null, "error.metalDepositNeedsSale")
            Line(it, item.disposalDate!!, item.proceeds!!, books.text("generated.sell", item.description))
        }
        for (line in listOfNotNull(purchase, sale)) {
            val (g, other) = books.accounts.locate(line.accountId)
            books.require(g, PermissionLevel.EDIT)
            validate(other.currency == account.currency && other.id != account.id, "error.currencyMismatch", account.currency.code)
        }
        val recorded = books.groups().flatMap { g -> books.ledger(g).ledgerQueries.txnsForInvestment(item.id).executeAsList().map { g to it } }
        val changed = listOf(purchase to true, sale to false).filter { (line, out) ->
            val side = recorded.map { it.second }.filter { (it.amount_minor < 0) == out }
            if (line == null) side.isNotEmpty() else side.singleOrNull()?.let { it.account_id == line.accountId && it.date == line.date.toString() && it.amount_minor == line.amount.minorUnits } != true
        }
        val stale = changed.flatMap { (_, out) -> recorded.filter { (it.second.amount_minor < 0) == out } }
        if (!confirmReconciled && stale.any { it.second.cleared == ClearedStatus.RECONCILED.name }) throw ReconciledChangeException()
        stale.forEach { (g, row) -> books.transactions.deleteInvestmentLine(g, row.id, confirmReconciled = true) }
        val payee = item.dealer?.ifBlank { null } ?: item.description
        for (line in changed.mapNotNull { it.first }) {
            books.transactions.createForInvestment(TransactionDraft(line.accountId, line.date, line.amount, payee, memo = line.memo), item.id, trade = true)
        }
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
