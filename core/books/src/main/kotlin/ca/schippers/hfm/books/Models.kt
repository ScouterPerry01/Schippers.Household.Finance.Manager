package ca.schippers.hfm.books

import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.domain.ClearedStatus
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** An account group as the signed-in user sees it. */
data class GroupInfo(
    val id: String,
    val name: String,
    val partitionId: String,
    val ownerUserId: String?,
    val level: PermissionLevel,
) {
    val isPrivate: Boolean get() = ownerUserId != null
}

data class Member(
    val id: String,
    val displayName: String,
    val kind: MemberKind,
    val birthDate: LocalDate?,
    val archived: Boolean,
)

data class Institution(
    val id: String,
    val name: String,
    val branch: String? = null,
    val institutionNumber: String? = null,
    val transitNumber: String? = null,
    val website: String? = null,
    val phone: String? = null,
    val notes: String? = null,
)

data class Category(
    val id: String,
    val parentId: String?,
    val systemKey: String?,
    val nameEn: String,
    val nameFr: String,
    val kind: CategoryKind,
    val taxFlag: TaxFlag?,
    val sortOrder: Int,
    val archived: Boolean,
) {
    fun name(language: Language): String = if (language == Language.FRENCH) nameFr else nameEn
}

data class Payee(
    val id: String,
    val name: String,
    val defaultCategoryId: String?,
    val archived: Boolean,
)

data class Account(
    val id: String,
    val groupId: String,
    val institutionId: String?,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    /** Masked number, e.g. "•••• 1234" (SEC-04). The full number needs re-authentication. */
    val numberMasked: String?,
    val openingBalance: Money,
    val openingDate: LocalDate,
    val status: AccountStatus,
    val notes: String?,
    val ownerMemberIds: Set<String>,
)

data class AccountSummary(val account: Account, val balance: Money, val clearedBalance: Money)

data class AccountDraft(
    val groupId: String,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingBalance: Money,
    val openingDate: LocalDate,
    val institutionId: String? = null,
    val number: String? = null,
    val ownerMemberIds: Set<String> = emptySet(),
    val notes: String? = null,
)

data class Split(
    val id: String,
    val categoryId: String?,
    val amount: Money,
    val memo: String?,
    val memberId: String?,
    val taxFlag: TaxFlag?,
)

data class SplitDraft(
    val categoryId: String?,
    val amount: Money,
    val memo: String? = null,
    val memberId: String? = null,
    val taxFlag: TaxFlag? = null,
)

/** Both sides of a transfer share [transferId]; [otherAccountId] is the account on the other side (TX-03). */
data class TransferLink(val transferId: String, val otherAccountId: String)

data class Transaction(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val payeeId: String?,
    val payeeText: String?,
    /** Amount in the account's currency: negative for money out, positive for money in. */
    val amount: Money,
    /** FX-03: the original foreign amount and the rate actually charged. */
    val originalAmount: Money?,
    val fxRate: BigDecimal?,
    val memo: String?,
    val memberId: String?,
    val cleared: ClearedStatus,
    val transfer: TransferLink?,
    val splits: List<Split>,
    val tagIds: Set<String>,
    val createdBy: String?,
) {
    val isSplit: Boolean get() = splits.size > 1
}

data class TransactionDraft(
    val accountId: String,
    val date: LocalDate,
    val amount: Money,
    val payeeName: String? = null,
    /** Empty means a single uncategorized split for the whole amount. */
    val splits: List<SplitDraft> = emptyList(),
    val memo: String? = null,
    val memberId: String? = null,
    val cleared: ClearedStatus = ClearedStatus.UNCLEARED,
    val originalAmount: Money? = null,
    val fxRate: BigDecimal? = null,
    val tags: Set<String> = emptySet(),
)

/**
 * Money moved between two of the household's own accounts (TX-03). [amount] leaves [fromAccountId]
 * in its currency; [toAmount] arrives in [toAccountId]'s currency and is required when the two
 * currencies differ (FX-04).
 */
data class TransferDraft(
    val fromAccountId: String,
    val toAccountId: String,
    val date: LocalDate,
    val amount: Money,
    val toAmount: Money? = null,
    val memo: String? = null,
    val memberId: String? = null,
)

data class RegisterRow(val transaction: Transaction, val runningBalance: Money)

/** Suggested values when a known payee is typed (MAN-02). */
data class PayeeSuggestion(val payeeId: String, val amount: Money?, val splits: List<SplitDraft>)
