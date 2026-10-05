package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

private fun Books.editable(groupId: String) = group(groupId).also { require(it, PermissionLevel.EDIT) }

// --- Contractors (MNT-08) ----------------------------------------------------------------------------

data class ContractorJob(
    val id: String,
    val date: LocalDate,
    val description: String,
    val cost: Money? = null,
    /** 1 to 5. */
    val rating: Int? = null,
    val assetId: String? = null,
    val notes: String? = null,
)

data class Contractor(
    val id: String,
    val groupId: String,
    val name: String,
    val trade: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
    val jobs: List<ContractorJob> = emptyList(),
) {
    /** The average of the jobs' ratings, to one decimal; null before any is rated. */
    val rating: BigDecimal? get() = jobs.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.let { BigDecimal(it.sum()).divide(BigDecimal(it.size), 1, RoundingMode.HALF_UP) }
}

/** MNT-08: the people who work on the home and vehicles, rated by their past jobs. */
class ContractorService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<Contractor> = books.groups().flatMap { g ->
        val q = books.ledger(g).extrasQueries
        q.contractors().executeAsList().map { r ->
            Contractor(
                r.id, g.id, r.name, r.trade, r.phone, r.email, r.website, r.notes, r.archived == 1L,
                q.contractorJobs(r.id).executeAsList().map { j ->
                    ContractorJob(j.id, LocalDate.parse(j.date), j.description, j.cost_minor?.let { Money.ofMinor(it, Currency.of(j.currency)) }, j.rating?.toInt(), j.asset_id, j.notes)
                },
            )
        }
    }.filter { includeArchived || !it.archived }

    fun save(c: Contractor): Contractor {
        validate(c.name.isNotBlank(), "error.nameRequired")
        val group = books.editable(list(true).firstOrNull { it.id == c.id }?.groupId ?: c.groupId)
        val id = c.id.ifBlank { Ids.newId() }
        fun t(v: String?) = v?.trim()?.ifEmpty { null }
        books.ledger(group).extrasQueries.upsertContractor(id, c.name.trim(), t(c.trade), t(c.phone), t(c.email), t(c.website), t(c.notes), if (c.archived) 1 else 0)
        return list(true).first { it.id == id }
    }

    fun delete(c: Contractor) = books.ledger(books.editable(c.groupId)).extrasQueries.deleteContractor(c.id)

    fun saveJob(c: Contractor, job: ContractorJob): ContractorJob {
        validate(job.description.isNotBlank(), "error.jobDescription")
        validate(job.rating == null || job.rating in 1..5, "error.jobRating")
        val id = job.id.ifBlank { Ids.newId() }
        books.ledger(books.editable(c.groupId)).extrasQueries.upsertContractorJob(
            id, c.id, job.date.toString(), job.description.trim(), job.cost?.minorUnits, (job.cost?.currency ?: books.reports.base).code, job.rating?.toLong(), job.assetId, job.notes?.trim()?.ifEmpty { null },
        )
        return job.copy(id = id)
    }

    fun deleteJob(c: Contractor, jobId: String) = books.ledger(books.editable(c.groupId)).extrasQueries.deleteContractorJob(jobId)
}

// --- Home improvement projects (MNT-09) --------------------------------------------------------------

enum class ProjectStatus { PLANNED, UNDER_WAY, DONE }

data class ProjectCost(val id: String, val date: LocalDate, val description: String, val amount: Money, val contractorId: String? = null)

data class HomeProject(
    val id: String,
    val groupId: String,
    val name: String,
    val status: ProjectStatus,
    val currency: Currency,
    val assetId: String? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val budget: Money? = null,
    /** A capital improvement (a new roof, a finished basement) adds to the home's cost base; a repair does not. */
    val capital: Boolean = true,
    val notes: String? = null,
    val costs: List<ProjectCost> = emptyList(),
) {
    val spent: Money get() = costs.fold(Money.zero(currency)) { a, c -> a + c.amount }
}

/** The home's cost base: what it cost, plus its capital improvements. */
data class CostBase(val purchase: Money?, val improvements: Money, val projects: List<HomeProject>) {
    val total: Money? get() = purchase?.let { it + improvements }
}

/** MNT-09: home improvement projects, their budget and costs, and what they add to the home's cost base. */
class HomeProjectService internal constructor(private val books: Books) {

    fun list(): List<HomeProject> = books.groups().flatMap { g ->
        val q = books.ledger(g).extrasQueries
        q.homeProjects().executeAsList().map { r ->
            val cur = Currency.of(r.currency)
            HomeProject(
                r.id, g.id, r.name, ProjectStatus.valueOf(r.status), cur, r.asset_id, r.start_date?.let(LocalDate::parse), r.end_date?.let(LocalDate::parse),
                r.budget_minor?.let { Money.ofMinor(it, cur) }, r.capital == 1L, r.notes,
                q.projectCosts(r.id).executeAsList().map { c -> ProjectCost(c.id, LocalDate.parse(c.date), c.description, Money.ofMinor(c.amount_minor, cur), c.contractor_id) },
            )
        }
    }

    fun save(p: HomeProject): HomeProject {
        validate(p.name.isNotBlank(), "error.nameRequired")
        validate(p.end == null || p.start == null || p.end >= p.start, "error.endBeforeStart")
        val group = books.editable(list().firstOrNull { it.id == p.id }?.groupId ?: p.groupId)
        val id = p.id.ifBlank { Ids.newId() }
        books.ledger(group).extrasQueries.upsertHomeProject(
            id, p.assetId, p.name.trim(), p.status.name, p.start?.toString(), p.end?.toString(), p.budget?.minorUnits, p.currency.code, if (p.capital) 1 else 0, p.notes?.trim()?.ifEmpty { null },
        )
        return list().first { it.id == id }
    }

    fun delete(p: HomeProject) = books.ledger(books.editable(p.groupId)).extrasQueries.deleteHomeProject(p.id)

    fun addCost(p: HomeProject, date: LocalDate, description: String, amount: Money, contractorId: String? = null) {
        validate(amount.currency == p.currency && !amount.isZero, "error.projectCost")
        books.ledger(books.editable(p.groupId)).extrasQueries.insertProjectCost(Ids.newId(), p.id, date.toString(), description.trim().ifEmpty { p.name }, amount.minorUnits, contractorId)
    }

    fun deleteCost(p: HomeProject, costId: String) = books.ledger(books.editable(p.groupId)).extrasQueries.deleteProjectCost(costId)

    /** The cost base of the home [assetId]: its purchase price and the capital projects done or under way on it. */
    fun costBase(assetId: String): CostBase {
        val asset = books.assets.get(assetId)
        val projects = list().filter { it.assetId == assetId && it.capital && it.status != ProjectStatus.PLANNED }
        val cur = asset.purchasePrice?.currency ?: books.reports.base
        val improvements = projects.filter { it.currency == cur }.fold(Money.zero(cur)) { a, p -> a + p.spent }
        return CostBase(asset.purchasePrice, improvements, projects)
    }
}

// --- Invoices (SAL-04) -------------------------------------------------------------------------------

enum class InvoiceStatus { DRAFT, SENT, PAID, CANCELLED }

@Serializable
data class InvoiceLine(val description: String, val quantity: String, val unitPrice: String) {
    fun amount(currency: Currency): Money =
        Money.of((BigDecimal(quantity) * BigDecimal(unitPrice)).setScale(currency.minorUnits, RoundingMode.HALF_UP), currency)
}

/** A sales tax charged on an invoice, for a GST/HST or QST registrant: its name and rate in basis points. */
@Serializable
data class InvoiceTax(val name: String, val rateBp: Int)

data class Invoice(
    val id: String,
    val groupId: String,
    val number: String,
    val customer: String,
    val issueDate: LocalDate,
    val currency: Currency,
    val lines: List<InvoiceLine>,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val dueDate: LocalDate? = null,
    val customerDetails: String? = null,
    val paidDate: LocalDate? = null,
    val memberId: String? = null,
    val taxes: List<InvoiceTax> = emptyList(),
    val notes: String? = null,
) {
    val subtotal: Money get() = lines.fold(Money.zero(currency)) { a, l -> a + l.amount(currency) }
    fun tax(t: InvoiceTax): Money = Money.of((subtotal.toBigDecimal() * BigDecimal(t.rateBp)).divide(BigDecimal(10_000), currency.minorUnits, RoundingMode.HALF_UP), currency)
    val total: Money get() = taxes.fold(subtotal) { a, t -> a + tax(t) }
    fun overdue(today: LocalDate): Boolean = status == InvoiceStatus.SENT && dueDate != null && dueDate < today
}

/** SAL-04: simple invoices for household side income (tutoring, crafts, rentals), and whether they are paid. */
class InvoiceService internal constructor(private val books: Books) {

    private val linesJson = ListSerializer(InvoiceLine.serializer())
    private val taxesJson = ListSerializer(InvoiceTax.serializer())

    fun list(): List<Invoice> = books.groups().flatMap { g ->
        books.ledger(g).extrasQueries.invoices().executeAsList().map { r ->
            Invoice(
                r.id, g.id, r.number, r.customer, LocalDate.parse(r.issue_date), Currency.of(r.currency),
                runCatching { json.decodeFromString(linesJson, r.lines) }.getOrDefault(emptyList()), InvoiceStatus.valueOf(r.status),
                r.due_date?.let(LocalDate::parse), r.customer_details, r.paid_date?.let(LocalDate::parse), r.member_id,
                r.taxes?.let { runCatching { json.decodeFromString(taxesJson, it) }.getOrNull() }.orEmpty(), r.notes,
            )
        }
    }

    /** The next number for [year]: "2026-001", then "2026-002"... */
    fun nextNumber(year: Int): String {
        val used = list().mapNotNull { Regex("^$year-(\\d+)$").find(it.number)?.groupValues?.get(1)?.toIntOrNull() }
        return "%d-%03d".format(year, (used.maxOrNull() ?: 0) + 1)
    }

    fun save(i: Invoice): Invoice {
        validate(i.customer.isNotBlank(), "error.invoiceCustomer")
        validate(i.number.isNotBlank() && list().none { it.number == i.number.trim() && it.id != i.id }, "error.invoiceNumber")
        validate(i.lines.isNotEmpty() && i.lines.all { it.description.isNotBlank() && it.quantity.toBigDecimalOrNull() != null && it.unitPrice.toBigDecimalOrNull() != null }, "error.invoiceLines")
        validate(i.dueDate == null || i.dueDate >= i.issueDate, "error.endBeforeStart")
        val group = books.editable(list().firstOrNull { it.id == i.id }?.groupId ?: i.groupId)
        val id = i.id.ifBlank { Ids.newId() }
        books.ledger(group).extrasQueries.upsertInvoice(
            id, i.number.trim(), i.customer.trim(), i.customerDetails?.trim()?.ifEmpty { null }, i.issueDate.toString(), i.dueDate?.toString(), i.currency.code,
            i.status.name, i.paidDate?.toString(), i.memberId, json.encodeToString(linesJson, i.lines), i.taxes.takeIf { it.isNotEmpty() }?.let { json.encodeToString(taxesJson, it) },
            i.notes?.trim()?.ifEmpty { null },
        )
        return list().first { it.id == id }
    }

    /**
     * Deletes the invoice; with [withDeposit], also the deposit recorded when it was marked paid
     * ([deposit]), which is deleted first. [confirmReconciled] as for any transaction.
     */
    fun delete(i: Invoice, withDeposit: Boolean = false, confirmReconciled: Boolean = false) {
        val group = books.editable(i.groupId)
        if (withDeposit) deposit(i)?.let { books.transactions.delete(it.id, confirmReconciled) }
        books.ledger(group).extrasQueries.deleteInvoice(i.id)
    }

    /**
     * Marks the invoice paid on [date]; with [accountId], also records the deposit there, on
     * self-employment income less the sales tax collected, which is kept on the deposit (TX-04).
     */
    fun markPaid(i: Invoice, date: LocalDate, accountId: String?): Invoice {
        val paid = save(i.copy(status = InvoiceStatus.PAID, paidDate = date))
        if (accountId != null) {
            val category = books.categories.list().firstOrNull { it.systemKey == "income.self_employment" }?.id
            val txn = books.transactions.create(
                TransactionDraft(accountId, date, i.total, i.customer, listOf(SplitDraft(category, i.total, "${i.number}")), "${i.number}", i.memberId),
            )
            val taxes = i.taxes.mapNotNull { t -> runCatching { ca.schippers.hfm.ocr.TaxName.valueOf(t.name.uppercase()) }.getOrNull()?.let { it to i.tax(t) } }.toMap()
            if (taxes.isNotEmpty()) books.transactions.setSalesTaxes(txn.id, taxes)
            books.putSetting("$DEPOSIT_KEY.${paid.id}", txn.id)
        }
        return paid
    }

    /**
     * The deposit recorded when the invoice was marked paid, while it is still in the books. For an
     * invoice paid before the deposit was remembered, the deposit [markPaid] wrote is looked for:
     * on the paid date, for the total, with the invoice number as memo, in an account of its currency.
     */
    fun deposit(i: Invoice): Transaction? {
        books.setting("$DEPOSIT_KEY.${i.id}")?.ifBlank { null }?.let { id -> return runCatching { books.transactions.get(id) }.getOrNull() }
        val paid = i.paidDate ?: return null
        return books.accounts.list(includeClosed = true).map { it.account }.filter { it.currency == i.currency }.firstNotNullOfOrNull { a ->
            books.transactions.register(a.id).map { it.transaction }.firstOrNull { it.date == paid && it.amount == i.total && it.memo == i.number }
        }
    }

    /** The total of the invoices sent and not yet paid, one amount per currency, the base currency first. */
    fun outstanding(): List<Money> = list().filter { it.status == InvoiceStatus.SENT }.groupBy { it.currency }
        .map { (cur, l) -> l.fold(Money.zero(cur)) { a, i -> a + i.total } }
        .sortedWith(compareBy({ it.currency != books.reports.base }, { it.currency.code }))

    private companion object {
        /** The setting that remembers an invoice's deposit: `invoice.deposit.<invoice id>`. */
        const val DEPOSIT_KEY = "invoice.deposit"
    }
}

// --- Rental properties (SAL-05) ----------------------------------------------------------------------

data class RentalProperty(
    val id: String,
    val groupId: String,
    val name: String,
    val tagId: String,
    val address: String? = null,
    /** The household's share of the property, in basis points (10000 = all of it). */
    val shareBp: Int = 10_000,
    val assetId: String? = null,
    val notes: String? = null,
)

/** A property's year: income and expenses by category, from the transactions with its tag. */
data class RentalYear(val property: RentalProperty, val income: PivotTable, val expenses: PivotTable) {
    val net: Money get() = income.total - expenses.total

    /** The household's share of the net, as its share of the property. */
    val share: Money get() = Money.of((net.toBigDecimal() * BigDecimal(property.shareBp)).divide(BigDecimal(10_000), net.currency.minorUnits, RoundingMode.HALF_UP), net.currency)
}

/** SAL-05: rental properties, their income and expenses grouped by the property's tag. */
class RentalService internal constructor(private val books: Books) {

    fun list(): List<RentalProperty> = books.groups().flatMap { g ->
        books.ledger(g).extrasQueries.rentalProperties().executeAsList().map { r ->
            RentalProperty(r.id, g.id, r.name, r.tag_id, r.address, r.share_bp.toInt(), r.asset_id, r.notes)
        }
    }

    /**
     * Saves the property; a new one gets a tag of its own name, for tagging its transactions. A
     * renamed property renames its tag too, unless another tag already has the new name.
     */
    fun save(p: RentalProperty): RentalProperty {
        validate(p.name.isNotBlank(), "error.nameRequired")
        validate(p.shareBp in 1..10_000, "error.rentalShare")
        val name = p.name.trim()
        val existing = list().firstOrNull { it.id == p.id }
        val group = books.editable(existing?.groupId ?: p.groupId)
        val tagId = p.tagId.ifBlank {
            books.core.insertTag(Ids.newId(), name)
            books.core.tagByName(name).executeAsOne().id
        }
        if (existing != null && existing.name != name) {
            validate(books.core.tagByName(name).executeAsOneOrNull()?.let { it.id == tagId } ?: true, "error.rentalTagTaken", name)
            books.core.renameTag(name, tagId)
        }
        val id = p.id.ifBlank { Ids.newId() }
        books.ledger(group).extrasQueries.upsertRentalProperty(id, name, p.address?.trim()?.ifEmpty { null }, tagId, p.shareBp.toLong(), p.assetId, p.notes?.trim()?.ifEmpty { null })
        return list().first { it.id == id }
    }

    /**
     * Deletes the property; its transactions stay. With [removeTag], its tag is removed as well, from
     * the tag list and from every transaction that had it, unless another property uses the same tag.
     */
    fun delete(p: RentalProperty, removeTag: Boolean = false) {
        books.ledger(books.editable(p.groupId)).extrasQueries.deleteRentalProperty(p.id)
        if (removeTag && list().none { it.tagId == p.tagId }) {
            books.groups().forEach { g -> books.ledger(g).ledgerQueries.deleteTagEverywhere(p.tagId) }
            books.core.deleteTag(p.tagId)
        }
    }

    fun year(p: RentalProperty, year: Int, labels: (String) -> String, french: Boolean = false): RentalYear {
        val filter = ReportFilter(LocalDate(year, 1, 1), LocalDate(year, 12, 31), tagId = p.tagId)
        return RentalYear(
            p,
            books.customReports.run(filter, CustomLayout(ReportDimension.CATEGORY, null, ReportMeasure.INCOME, maxRows = 1000), labels, french),
            books.customReports.run(filter, CustomLayout(ReportDimension.CATEGORY, null, ReportMeasure.SPENDING, maxRows = 1000), labels, french),
        )
    }
}

// --- Card rewards (CC-03) ----------------------------------------------------------------------------

enum class RewardUnit { POINTS, CASH_BACK, MILES }

enum class RewardKind { EARNED, REDEEMED, ADJUSTED }

data class CardReward(
    val accountId: String,
    val groupId: String,
    val program: String,
    val unit: RewardUnit,
    /** Units earned per dollar spent, for an estimate between statements. */
    val earnRate: BigDecimal? = null,
    /** What one unit is worth in dollars (1 for cash back). */
    val unitValue: BigDecimal? = null,
    val notes: String? = null,
)

data class RewardEntry(val id: String, val date: LocalDate, val units: BigDecimal, val kind: RewardKind, val value: Money? = null, val notes: String? = null)

/** Where a card's rewards stand: units earned, redeemed and left, their worth, and an estimate of this year's earning. */
data class RewardStatus(val earned: BigDecimal, val redeemed: BigDecimal, val balance: BigDecimal, val worth: Money?, val estimatedThisYear: BigDecimal?)

/** CC-03: rewards on credit cards: points, cash back or miles earned and redeemed. */
class RewardService internal constructor(private val books: Books) {

    fun programs(): List<CardReward> = books.groups().flatMap { g ->
        books.ledger(g).extrasQueries.cardRewards().executeAsList().map { r ->
            CardReward(r.account_id, g.id, r.program, RewardUnit.valueOf(r.unit), r.earn_rate?.toBigDecimalOrNull(), r.unit_value?.toBigDecimalOrNull(), r.notes)
        }
    }

    fun program(accountId: String): CardReward? = programs().firstOrNull { it.accountId == accountId }

    fun save(r: CardReward) {
        validate(r.program.isNotBlank(), "error.nameRequired")
        val group = books.editable(books.accounts.get(r.accountId).groupId)
        books.ledger(group).extrasQueries.upsertCardReward(r.accountId, r.program.trim(), r.unit.name, r.earnRate?.toPlainString(), r.unitValue?.toPlainString(), r.notes?.trim()?.ifEmpty { null })
    }

    fun remove(accountId: String) = books.ledger(books.editable(books.accounts.get(accountId).groupId)).extrasQueries.deleteCardReward(accountId)

    fun entries(accountId: String): List<RewardEntry> {
        val account = books.accounts.get(accountId)
        return books.ledger(books.group(account.groupId)).extrasQueries.rewardEntries(accountId).executeAsList().map { e ->
            RewardEntry(e.id, LocalDate.parse(e.date), BigDecimal(e.units), RewardKind.valueOf(e.kind), e.value_minor?.let { Money.ofMinor(it, account.currency) }, e.notes)
        }
    }

    /** Records units earned (from a statement), redeemed (with what they were worth) or adjusted (+ or -). */
    fun addEntry(accountId: String, date: LocalDate, units: BigDecimal, kind: RewardKind, value: Money? = null, notes: String? = null) {
        validate(units.signum() != 0 && (kind == RewardKind.ADJUSTED || units.signum() > 0), "error.rewardUnits")
        val group = books.editable(books.accounts.get(accountId).groupId)
        books.ledger(group).extrasQueries.insertRewardEntry(Ids.newId(), accountId, date.toString(), units.toPlainString(), kind.name, value?.minorUnits, notes?.trim()?.ifEmpty { null })
    }

    fun deleteEntry(accountId: String, entryId: String) = books.ledger(books.editable(books.accounts.get(accountId).groupId)).extrasQueries.deleteRewardEntry(entryId)

    /** As of [today]: the balance, its worth at the program's unit value, and this year's spending on the card times its earn rate. */
    fun status(accountId: String, today: LocalDate): RewardStatus {
        val program = program(accountId)
        val entries = entries(accountId).filter { it.date <= today }
        fun sum(kind: RewardKind) = entries.filter { it.kind == kind }.fold(BigDecimal.ZERO) { a, e -> a + e.units }
        val earned = sum(RewardKind.EARNED) + sum(RewardKind.ADJUSTED)
        val redeemed = sum(RewardKind.REDEEMED)
        val balance = earned - redeemed
        val account = books.accounts.get(accountId)
        val worth = program?.unitValue?.let { Money.of((balance * it).setScale(account.currency.minorUnits, RoundingMode.HALF_UP), account.currency) }
        val estimate = program?.earnRate?.let { rate ->
            val spent = books.customReports.run(
                ReportFilter(LocalDate(today.year, 1, 1), today, setOf(accountId)), CustomLayout(ReportDimension.YEAR, null, ReportMeasure.SPENDING), { it },
            ).total
            (spent.toBigDecimal() * rate).setScale(0, RoundingMode.DOWN)
        }
        return RewardStatus(earned, redeemed, balance, worth, estimate)
    }
}
