package ca.schippers.hfm.books

import ca.schippers.hfm.calc.assets.Depreciation
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.math.BigDecimal
import ca.schippers.hfm.data.ledger.Asset as AssetRow
import ca.schippers.hfm.data.ledger.Warranty as WarrantyRow

/** AST-01: kinds of asset; vehicles have their own section. */
enum class AssetKind { HOME, COTTAGE, RV, BOAT, TRAILER, APPLIANCE, HEATING_COOLING, ELECTRONICS, COMPUTER, FURNITURE, JEWELLERY, ART, TOOLS, SPORTS, MUSICAL, OTHER }

enum class ValueMethod { NONE, MANUAL, DEPRECIATION }

/** AST-05: what became of an asset. */
enum class AssetStatus { ACTIVE, SOLD, GIVEN_AWAY, DISCARDED }

/**
 * AST-01 to AST-05: something the household owns, with its purchase, where it is, who owns it and
 * what it is worth. [parentId] puts it inside another asset (AST-02).
 */
data class Asset(
    val id: String,
    val groupId: String,
    val kind: AssetKind,
    val name: String,
    val parentId: String? = null,
    val make: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val purchaseDate: LocalDate? = null,
    val seller: String? = null,
    val purchasePrice: Money? = null,
    val transactionId: String? = null,
    val location: String? = null,
    val ownerMemberId: String? = null,
    val valueMethod: ValueMethod = ValueMethod.NONE,
    /** MANUAL: the estimate entered, on [valueDate]. */
    val value: Money? = null,
    val valueDate: LocalDate? = null,
    val depreciationYears: Int? = null,
    val residualPercent: BigDecimal? = null,
    val inNetWorth: Boolean = false,
    val status: AssetStatus = AssetStatus.ACTIVE,
    val disposalDate: LocalDate? = null,
    val disposalPrice: Money? = null,
    val disposalTransactionId: String? = null,
    val notes: String? = null,
    /** MNT-03: what its meter counts, if it has one. */
    val meter: MeterUnit? = null,
) {
    val currency: Currency get() = purchasePrice?.currency ?: value?.currency ?: Currency.CAD

    /** AST-03: what it is worth on [date]; nothing once disposed of, or before it was bought. */
    fun valueOn(date: LocalDate): Money? {
        if (disposalDate != null && date >= disposalDate) return Money.zero(currency)
        if (purchaseDate != null && date < purchaseDate) return Money.zero(currency)
        return when (valueMethod) {
            ValueMethod.NONE -> null
            ValueMethod.MANUAL -> value
            ValueMethod.DEPRECIATION -> {
                val price = purchasePrice ?: return null
                val start = purchaseDate ?: return price
                Depreciation.straightLine(price, start, depreciationYears ?: 10, residualPercent ?: BigDecimal.ZERO, date)
            }
        }
    }
}

/** WAR-01: kinds of warranty or service plan. */
enum class AssetWarrantyKind { MANUFACTURER, EXTENDED, CARD_EXTENDED, SERVICE_CONTRACT, OTHER }

/** WAR-01: a warranty or service plan for an asset. */
data class AssetWarranty(
    val id: String,
    val groupId: String,
    val assetId: String,
    val kind: AssetWarrantyKind,
    val provider: String? = null,
    val coverage: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val endHours: Int? = null,
    val phone: String? = null,
    /** CARD_EXTENDED: the card that extends the manufacturer's warranty (CC-04). */
    val cardAccountId: String? = null,
    val notes: String? = null,
)

/** WAR-03: one claim under a warranty. */
data class WarrantyClaim(
    val id: String,
    val warrantyId: String,
    val date: LocalDate,
    val problem: String,
    val outcome: String? = null,
    val covered: Money? = null,
    val paid: Money? = null,
    val notes: String? = null,
)

/** WAR-04: one coverage of an item, and until when; [until] null when it has no end date. */
data class CoverageStatus(val label: String, val kindKey: String, val until: LocalDate?, val active: Boolean, val warranty: AssetWarranty? = null)

/** WAR-04: an asset or vehicle found by a search, and what still covers it. */
data class CoveredItem(val id: String, val name: String, val isVehicle: Boolean, val coverage: List<CoverageStatus>) {
    val covered: Boolean get() = coverage.any { it.active }
}

/**
 * AST-01 to AST-05 and WAR-01 to WAR-04: the home and other assets, their value in net worth,
 * their warranties and claims, and whether something is still covered. Kept in the account group
 * the user chooses.
 */
class AssetService internal constructor(private val books: Books) {

    // --- Assets (AST-01 to AST-05) --------------------------------------------------------------

    fun list(includeDisposed: Boolean = false): List<Asset> = books.groups().flatMap { g ->
        books.ledger(g).assetsQueries.assets().executeAsList().map { it.toAsset(g.id) }
    }.filter { includeDisposed || it.status == AssetStatus.ACTIVE }

    fun get(id: String): Asset = list(includeDisposed = true).firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    /** AST-02: the assets directly inside [parentId] (top-level ones when null). */
    fun children(parentId: String?, includeDisposed: Boolean = false): List<Asset> = list(includeDisposed).filter { it.parentId == parentId }

    fun save(a: Asset): Asset {
        validate(a.name.isNotBlank(), "error.nameRequired")
        listOfNotNull(a.purchasePrice, a.value, a.disposalPrice).forEach { validate(!it.isNegative, "error.amountPositive") }
        validate(a.depreciationYears == null || a.depreciationYears in 1..100, "error.invalidNumber")
        validate(a.residualPercent == null || (a.residualPercent.signum() >= 0 && a.residualPercent <= BigDecimal(100)), "error.percentRange")
        validate(a.valueMethod != ValueMethod.DEPRECIATION || a.purchasePrice != null, "error.depreciationNeedsPrice")
        validate(a.status == AssetStatus.ACTIVE || a.disposalDate != null, "error.disposalDate")
        val id = a.id.ifBlank { Ids.newId() }
        // A parent must exist and never be the asset itself or one of its descendants.
        a.parentId?.let { p ->
            validate(p != id && p !in descendants(id), "error.parentLoop")
            get(p)
        }
        val group = editable(a.groupId)
        val q = books.ledger(group).assetsQueries
        val now = books.now()
        val created = q.assetById(id).executeAsOneOrNull()?.created_at ?: now
        q.upsertAsset(
            id, a.parentId, a.kind.name, a.name.trim(), a.make.blankToNull(), a.model.blankToNull(), a.serialNumber.blankToNull(), a.purchaseDate?.toString(),
            a.seller.blankToNull(), a.purchasePrice?.minorUnits, a.currency.code, a.transactionId, a.location.blankToNull(), a.ownerMemberId, a.valueMethod.name,
            a.value?.minorUnits, a.valueDate?.toString(), a.depreciationYears?.toLong(), a.residualPercent?.stripTrailingZeros()?.toPlainString(),
            if (a.inNetWorth) 1 else 0, a.status.name, a.disposalDate?.toString(), a.disposalPrice?.minorUnits, a.disposalTransactionId, a.notes.blankToNull(), created, now,
            a.meter?.name,
        )
        books.session.audit("UPDATE", "asset", id)
        return get(id)
    }

    fun delete(id: String) {
        val a = get(id)
        val q = books.ledger(editable(a.groupId)).assetsQueries
        validate(q.childCount(id).executeAsOne() == 0L, "error.assetHasChildren")
        q.deleteAsset(id)
        books.session.audit("DELETE", "asset", id)
    }

    /** AST-05: records that an asset was sold, given away or discarded, with the sale's transaction (SAL-03). */
    fun dispose(id: String, status: AssetStatus, date: LocalDate, price: Money? = null, transactionId: String? = null): Asset {
        validate(status != AssetStatus.ACTIVE, "error.disposalDate")
        return save(get(id).copy(status = status, disposalDate = date, disposalPrice = price, disposalTransactionId = transactionId))
    }

    private fun descendants(id: String): Set<String> {
        val all = list(includeDisposed = true)
        val out = HashSet<String>()
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            frontier = all.filter { it.parentId in frontier && it.id !in out }.map { it.id }.toSet()
            out += frontier
        }
        return out
    }

    /** AST-03: the assets counted in net worth, worth on each of [dates], in the base currency. */
    fun netWorthValues(dates: List<LocalDate>): List<Money> {
        val base = books.rates.baseCurrency
        val counted = list(includeDisposed = true).filter { it.inNetWorth }
        return dates.map { date ->
            counted.mapNotNull { a -> a.valueOn(date)?.let { v -> books.rates.convert(v, base, date) } }.fold(Money.zero(base), Money::plus)
        }
    }

    // --- Warranties (WAR-01 to WAR-04) ----------------------------------------------------------

    fun warranties(assetId: String? = null): List<AssetWarranty> = books.groups().flatMap { g ->
        books.ledger(g).assetsQueries.warranties().executeAsList().map { it.toWarranty(g.id) }
    }.filter { assetId == null || it.assetId == assetId }

    fun saveWarranty(w: AssetWarranty): AssetWarranty {
        validate(w.startDate == null || w.endDate == null || w.endDate >= w.startDate, "error.endBeforeStart")
        validate(w.kind != AssetWarrantyKind.CARD_EXTENDED || w.cardAccountId != null, "error.cardRequired")
        val group = editable(w.groupId)
        val q = books.ledger(group).assetsQueries
        val id = w.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = q.warrantyById(id).executeAsOneOrNull()?.created_at ?: now
        q.upsertWarranty(
            id, w.assetId, w.kind.name, w.provider.blankToNull(), w.coverage.blankToNull(), w.startDate?.toString(), w.endDate?.toString(), w.endHours?.toLong(),
            w.phone.blankToNull(), w.cardAccountId, w.notes.blankToNull(), created, now,
        )
        return w.copy(id = id)
    }

    fun deleteWarranty(id: String) {
        val w = warranties().firstOrNull { it.id == id } ?: return
        books.ledger(editable(w.groupId)).assetsQueries.deleteWarranty(id)
    }

    fun claims(warrantyId: String): List<WarrantyClaim> {
        val w = warranties().firstOrNull { it.id == warrantyId } ?: return emptyList()
        return books.ledger(books.group(w.groupId)).assetsQueries.warrantyClaims(warrantyId).executeAsList().map {
            WarrantyClaim(it.id, it.warranty_id, LocalDate.parse(it.date), it.problem, it.outcome, it.covered_minor?.let { m -> Money.ofMinor(m, Currency.CAD) }, it.paid_minor?.let { m -> Money.ofMinor(m, Currency.CAD) }, it.notes)
        }
    }

    fun saveClaim(c: WarrantyClaim): WarrantyClaim {
        validate(c.problem.isNotBlank(), "error.descriptionRequired")
        val w = warranties().firstOrNull { it.id == c.warrantyId } ?: throw ValidationException("error.notFound")
        val id = c.id.ifBlank { Ids.newId() }
        books.ledger(editable(w.groupId)).assetsQueries.upsertWarrantyClaim(
            id, c.warrantyId, c.date.toString(), c.problem.trim(), c.outcome.blankToNull(), c.covered?.minorUnits, c.paid?.minorUnits, c.notes.blankToNull(),
        )
        return c.copy(id = id)
    }

    fun deleteClaim(warrantyId: String, claimId: String) {
        val w = warranties().firstOrNull { it.id == warrantyId } ?: return
        books.ledger(editable(w.groupId)).assetsQueries.deleteWarrantyClaim(claimId)
    }

    /**
     * When a warranty ends. A card's extended warranty with no end of its own adds the card
     * benefit's months to the manufacturer's warranty, if that one is short enough (CC-04).
     */
    fun endOf(w: AssetWarranty): LocalDate? {
        if (w.endDate != null || w.kind != AssetWarrantyKind.CARD_EXTENDED) return w.endDate
        val benefit = w.cardAccountId?.let { card -> runCatching { books.creditCards.benefits(card) }.getOrNull()?.firstOrNull { it.kind == BenefitKind.EXTENDED_WARRANTY } } ?: return null
        val manufacturer = warranties(w.assetId).firstOrNull { it.kind == AssetWarrantyKind.MANUFACTURER && it.endDate != null } ?: return null
        val start = manufacturer.startDate ?: get(w.assetId).purchaseDate ?: return null
        return Depreciation.extendedEnd(start, manufacturer.endDate!!, benefit.months ?: 0, benefit.maxYears)
    }

    /**
     * WAR-04: what covers an asset on [today]: its warranties, and, for something bought with a
     * card, the card's purchase protection and extended warranty (CC-04).
     */
    fun coverage(assetId: String, today: LocalDate): List<CoverageStatus> {
        val a = get(assetId)
        val own = warranties(assetId).sortedBy { it.kind.ordinal }.map { w ->
            val end = endOf(w)
            CoverageStatus(listOfNotNull(w.provider, w.coverage).joinToString(" · "), "assetWarranty.${w.kind}", end, end == null || end >= today, w)
        }
        val card = a.transactionId?.let { id -> runCatching { books.transactions.get(id) }.getOrNull() }
            ?.let { t -> books.creditCards.coverage(t, today).filter { it.until != null } }.orEmpty()
            .map { c -> CoverageStatus("", "benefit.${c.benefit.kind}", c.until, true) }
        return own + card
    }

    /** WAR-04: assets and vehicles whose name, make, model or serial number match [query], with what covers them. */
    fun findCovered(query: String, today: LocalDate): List<CoveredItem> {
        val q = query.trim().lowercase()
        fun matches(vararg fields: String?) = q.isEmpty() || fields.any { it?.lowercase()?.contains(q) == true }
        val assets = list().filter { matches(it.name, it.make, it.model, it.serialNumber, it.location) }
            .map { CoveredItem(it.id, it.name, false, coverage(it.id, today)) }
        val vehicles = books.vehicles.list().filter { matches(it.name, it.make, it.model, it.vin) }.map { v ->
            val odometer = books.vehicles.latestOdometer(v.id)?.odometer
            CoveredItem(
                v.id, v.name, true,
                books.vehicles.warranties(v.id).map { w -> CoverageStatus(listOfNotNull(w.provider, w.endKm?.let { "$it km" }).joinToString(" · "), "warrantyKind.${w.kind}", w.endDate, w.covers(today, odometer)) },
            )
        }
        return (assets + vehicles).sortedBy { it.name.lowercase() }
    }

    /** WAR-02: warranties ending within 60 days (by default), as reminders. */
    fun renewals(today: LocalDate, withinDays: Int = 60): List<Renewal> {
        val names = list().associate { it.id to it.name }
        return warranties().mapNotNull { w ->
            val name = names[w.assetId] ?: return@mapNotNull null
            val end = endOf(w) ?: return@mapNotNull null
            val days = today.daysUntil(end)
            if (days < 0 || days > withinDays) null else Renewal(RenewalKind.ASSET_WARRANTY, w.assetId, name, end, days, w.provider)
        }
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun editable(groupId: String): GroupInfo = books.group(groupId).also { books.require(it, PermissionLevel.EDIT) }

    private fun AssetRow.toAsset(groupId: String): Asset {
        val c = Currency.of(currency)
        return Asset(
            id, groupId, AssetKind.valueOf(kind), name, parent_id, make, model, serial_number, purchase_date?.let(LocalDate::parse), seller,
            purchase_price_minor?.let { Money.ofMinor(it, c) }, txn_id, location, owner_member_id, ValueMethod.valueOf(value_method),
            value_minor?.let { Money.ofMinor(it, c) }, value_date?.let(LocalDate::parse), depreciation_years?.toInt(), residual_percent?.let(::BigDecimal),
            in_net_worth == 1L, AssetStatus.valueOf(status), disposal_date?.let(LocalDate::parse), disposal_price_minor?.let { Money.ofMinor(it, c) }, disposal_txn_id, notes,
            meter?.let(MeterUnit::valueOf),
        )
    }

    private fun WarrantyRow.toWarranty(groupId: String) = AssetWarranty(
        id, groupId, asset_id, AssetWarrantyKind.valueOf(kind), provider, coverage, start_date?.let(LocalDate::parse), end_date?.let(LocalDate::parse),
        end_hours?.toInt(), phone, card_account_id, notes,
    )

    companion object {
        const val ENTITY = "asset"
        const val WARRANTY = "warranty"
    }
}
