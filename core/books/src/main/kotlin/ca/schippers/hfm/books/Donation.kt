package ca.schippers.hfm.books

import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate

/** OTH-01: what an official donation receipt says. [eligible] is null when the whole gift is eligible. */
data class DonationReceipt(
    val charity: String? = null,
    val registration: String? = null,
    val receiptNumber: String? = null,
    val eligible: Money? = null,
    val received: Boolean = false,
    /** True when the receipt is for this gift alone, false when kept for the whole transaction. */
    val ownReceipt: Boolean = false,
)

/**
 * OTH-01: a donation in the books: the part of a transaction on charitable or political categories,
 * by the person who gave it, with its receipt details and the documents filed with it.
 */
data class Donation(
    val transactionId: String,
    val groupId: String,
    val date: LocalDate,
    val payee: String?,
    val memberId: String?,
    val kind: TaxFlag,
    val amount: Money,
    val receipt: DonationReceipt?,
    val documents: Int,
    /** The split's memo: for a payroll gift, the charity (United Way, Centraide...). */
    val memo: String? = null,
    /** Given through payroll: the T4 slip (box 46) is the receipt. */
    val payroll: Boolean = false,
    /**
     * This entry's part of the eligible amount of a receipt kept for the whole transaction (before
     * receipts were kept per gift), when the transaction holds gifts by several people or of both
     * kinds: shared in proportion to the gifts, so it is counted once.
     */
    val eligibleShare: Money? = null,
) {
    val eligible: Money get() = eligibleShare ?: receipt?.eligible ?: amount

    /** Received when marked so, when a document is filed with the transaction, or on the T4 for payroll gifts. */
    val hasReceipt: Boolean get() = receipt?.received == true || documents > 0 || payroll
}

/** OTH-01: one person's eligible totals for a year, in Canadian dollars. */
data class DonationTotals(val memberId: String?, val charitable: Money, val political: Money, val missingReceipts: Int)

class DonationService internal constructor(private val books: Books) {

    /**
     * The donations dated in [year], oldest first: every split on a category flagged CHARITABLE or
     * POLITICAL (or a split flagged so itself), one entry per transaction, person and kind. Refunds
     * of a donation reduce it.
     */
    fun list(year: Int): List<Donation> {
        val flagged = books.categories.list(includeArchived = true).filter { it.taxFlag == TaxFlag.CHARITABLE || it.taxFlag == TaxFlag.POLITICAL }
            .associate { it.id to it.taxFlag!! }
        val documents = books.documents.countsFor(DocumentEntity.TRANSACTION)
        val currencies = books.accounts.all(includeClosed = true).associate { it.id to it.currency }
        return books.groups().flatMap { g ->
            val q = books.ledger(g).donationQueries
            val ledger = books.ledger(g).ledgerQueries
            val rows = q.donationSplits(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString(), flagged.keys).executeAsList()
            val txnIds = rows.map { it.txn_id }.distinct().chunked(500)
            val shared = txnIds.flatMap { q.donationsFor(it).executeAsList() }.associateBy { it.txn_id }
            // M-43: a receipt for one gift (person and kind) within the transaction.
            val own = txnIds.flatMap { q.donationReceiptsFor(it).executeAsList() }.associateBy { Triple(it.txn_id, it.member_key, it.kind) }
            val txns = txnIds.flatMap { ledger.txnsByIds(it).executeAsList() }.associateBy { it.id }
            rows.groupBy { Triple(it.txn_id, it.member_id, it.tax_flag?.let(TaxFlag::valueOf) ?: flagged.getValue(it.category_id!!)) }
                .mapNotNull { (key, splits) ->
                    val (txnId, memberId, kind) = key
                    // The transaction's own row, read with the others from this group's ledger (NFR-02).
                    val txn = txns[txnId] ?: return@mapNotNull null
                    val currency = currencies[txn.account_id] ?: return@mapNotNull null
                    val amount = Money.ofMinor(-splits.sumOf { it.amount_minor }, currency)
                    if (amount.isNegative || amount.isZero) return@mapNotNull null
                    val receipt = own[Triple(txnId, memberId.orEmpty(), kind.name)]?.let {
                        DonationReceipt(it.charity, it.registration, it.receipt_number, it.eligible_minor?.let { m -> Money.ofMinor(m, currency) }, it.received == 1L, ownReceipt = true)
                    } ?: shared[txnId]?.let {
                        DonationReceipt(it.charity, it.registration, it.receipt_number, it.eligible_minor?.let { m -> Money.ofMinor(m, currency) }, it.received == 1L)
                    }
                    val memo = splits.mapNotNull { it.memo }.distinct().joinToString(", ").ifEmpty { null }
                    Donation(
                        txnId, g.id, LocalDate.parse(txn.date), txn.payee_text, memberId, kind, amount, receipt,
                        documents[txnId] ?: 0, memo, payroll = txn.amount_minor > 0,
                    )
                }
                .groupBy { it.transactionId }.values.flatMap { entries ->
                    // A receipt kept for the whole transaction is shared by the gifts without their own.
                    val sharing = entries.filter { it.receipt?.ownReceipt != true }
                    val eligible = sharing.firstOrNull()?.receipt?.eligible
                    if (entries.size == 1 || sharing.size < 2 || eligible == null) entries
                    else {
                        val shares = sharing.zip(eligible.allocate(sharing.map { it.amount.toBigDecimal() })).associate { (d, share) -> d to share }
                        entries.map { d -> shares[d]?.let { d.copy(eligibleShare = it) } ?: d }
                    }
                }
        }.sortedBy { it.date }
    }

    /** Totals per person (null for the household) in Canadian dollars; gifts in other currencies are left out. */
    fun totals(year: Int): List<DonationTotals> = totals(list(year))

    /** The totals of donations already listed. */
    internal fun totals(donations: List<Donation>): List<DonationTotals> = donations.filter { it.amount.currency == Currency.CAD }.groupBy { it.memberId }.map { (member, gifts) ->
        fun sum(kind: TaxFlag) = gifts.filter { it.kind == kind }.fold(Money.zero(Currency.CAD)) { a, d -> a + d.eligible }
        DonationTotals(member, sum(TaxFlag.CHARITABLE), sum(TaxFlag.POLITICAL), gifts.count { !it.hasReceipt })
    }

    /**
     * Records what the receipt for one gift in [transactionId] says: the gift by [memberId] (null
     * for the household) of [kind] (M-43). A charity's registration number has nine digits, RR and
     * four digits; the eligible amount cannot exceed what was given.
     */
    fun setReceipt(transactionId: String, receipt: DonationReceipt, memberId: String? = null, kind: TaxFlag? = null) {
        val txn = books.transactions.get(transactionId)
        val group = books.group(books.accounts.get(txn.accountId).groupId)
        books.require(group, PermissionLevel.EDIT)
        val registration = receipt.registration?.uppercase()?.replace(Regex("[\\s-]"), "")?.ifEmpty { null }
        validate(registration == null || REGISTRATION.matches(registration), "error.donationRegistration")
        // The gift is the donation lines, not the transaction: a payroll gift is part of a deposit.
        val giftKind = kind ?: txn.splits.firstNotNullOfOrNull { flagOf(it) } ?: TaxFlag.CHARITABLE
        val member = memberId ?: txn.splits.firstOrNull { flagOf(it) == giftKind }?.let { it.memberId ?: txn.memberId }
        val gift = -txn.splits.filter { flagOf(it) == giftKind && (it.memberId ?: txn.memberId) == member }.sumOf { it.amount.minorUnits }
        receipt.eligible?.let { validate(it.currency == txn.amount.currency && !it.isNegative && it.minorUnits <= gift, "error.donationEligible") }
        books.ledger(group).donationQueries.upsertDonationReceipt(
            transactionId, member.orEmpty(), giftKind.name, receipt.charity?.trim()?.ifEmpty { null }, registration,
            receipt.receiptNumber?.trim()?.ifEmpty { null }, receipt.eligible?.minorUnits, if (receipt.received) 1 else 0,
        )
    }

    /** A split's donation kind: its own flag, or its category's; null when it is not a donation. */
    private fun flagOf(split: Split): TaxFlag? {
        val flag = split.taxFlag ?: split.categoryId?.let { id -> books.categories.list(includeArchived = true).firstOrNull { it.id == id }?.taxFlag }
        return flag?.takeIf { it == TaxFlag.CHARITABLE || it == TaxFlag.POLITICAL }
    }

    private companion object {
        val REGISTRATION = Regex("\\d{9}RR\\d{4}")
    }
}
