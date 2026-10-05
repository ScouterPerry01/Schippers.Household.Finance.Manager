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
) {
    val eligible: Money get() = receipt?.eligible ?: amount

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
        return books.groups().flatMap { g ->
            val q = books.ledger(g).donationQueries
            val rows = q.donationSplits(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString(), flagged.keys).executeAsList()
            val receipts = rows.map { it.txn_id }.distinct().chunked(500).flatMap { q.donationsFor(it).executeAsList() }.associateBy { it.txn_id }
            rows.groupBy { Triple(it.txn_id, it.member_id, it.tax_flag?.let(TaxFlag::valueOf) ?: flagged.getValue(it.category_id!!)) }
                .mapNotNull { (key, splits) ->
                    val (txnId, memberId, kind) = key
                    val txn = books.transactions.get(txnId)
                    val currency = txn.amount.currency
                    val amount = Money.ofMinor(-splits.sumOf { it.amount_minor }, currency)
                    if (amount.isNegative || amount.isZero) return@mapNotNull null
                    val receipt = receipts[txnId]?.let {
                        DonationReceipt(it.charity, it.registration, it.receipt_number, it.eligible_minor?.let { m -> Money.ofMinor(m, currency) }, it.received == 1L)
                    }
                    val documents = books.documents.documentsFor(DocumentEntity.TRANSACTION, txnId).size
                    val memo = splits.mapNotNull { it.memo }.distinct().joinToString(", ").ifEmpty { null }
                    Donation(txnId, g.id, txn.date, txn.payeeText, memberId, kind, amount, receipt, documents, memo, payroll = txn.amount.isPositive)
                }
        }.sortedBy { it.date }
    }

    /** Totals per person (null for the household) in Canadian dollars; gifts in other currencies are left out. */
    fun totals(year: Int): List<DonationTotals> = list(year).filter { it.amount.currency == Currency.CAD }.groupBy { it.memberId }.map { (member, gifts) ->
        fun sum(kind: TaxFlag) = gifts.filter { it.kind == kind }.fold(Money.zero(Currency.CAD)) { a, d -> a + d.eligible }
        DonationTotals(member, sum(TaxFlag.CHARITABLE), sum(TaxFlag.POLITICAL), gifts.count { !it.hasReceipt })
    }

    /**
     * Records what the receipt for [transactionId] says. A charity's registration number has nine
     * digits, RR and four digits; the eligible amount cannot exceed what was given.
     */
    fun setReceipt(transactionId: String, receipt: DonationReceipt) {
        val txn = books.transactions.get(transactionId)
        val group = books.group(books.accounts.get(txn.accountId).groupId)
        books.require(group, PermissionLevel.EDIT)
        val registration = receipt.registration?.uppercase()?.replace(Regex("[\\s-]"), "")?.ifEmpty { null }
        validate(registration == null || REGISTRATION.matches(registration), "error.donationRegistration")
        receipt.eligible?.let { validate(it.currency == txn.amount.currency && !it.isNegative && it.minorUnits <= -txn.amount.minorUnits, "error.donationEligible") }
        books.ledger(group).donationQueries.upsertDonation(
            transactionId, receipt.charity?.trim()?.ifEmpty { null }, registration, receipt.receiptNumber?.trim()?.ifEmpty { null },
            receipt.eligible?.minorUnits, if (receipt.received) 1 else 0,
        )
    }

    private companion object {
        val REGISTRATION = Regex("\\d{9}RR\\d{4}")
    }
}
