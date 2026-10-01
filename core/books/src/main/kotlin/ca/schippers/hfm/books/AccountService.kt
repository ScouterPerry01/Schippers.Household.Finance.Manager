package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.domain.AccountStatus
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import ca.schippers.hfm.data.ledger.Account as AccountRow

/** Accounts across every account group the user can see (ACC-01 to ACC-05). */
class AccountService internal constructor(private val books: Books) {

    /** All visible accounts with their balances. Closed accounts are hidden unless asked for (ACC-05). */
    fun list(includeClosed: Boolean = false): List<AccountSummary> = books.groups().flatMap { group ->
        val ledger = books.ledger(group).ledgerQueries
        val owners = ledger.accountOwners().executeAsList().groupBy({ it.account_id }, { it.member_id })
        val balances = ledger.balances().executeAsList().associateBy { it.id }
        ledger.accounts().executeAsList()
            .map { it.toAccount(group.id, owners[it.id].orEmpty().toSet()) }
            .filter { includeClosed || it.status != AccountStatus.CLOSED }
            .map { account ->
                val b = balances[account.id]
                AccountSummary(
                    account,
                    Money.ofMinor(b?.balance ?: account.openingBalance.minorUnits, account.currency),
                    Money.ofMinor(b?.cleared_balance ?: account.openingBalance.minorUnits, account.currency),
                )
            }
    }

    fun get(accountId: String): Account = locate(accountId).second

    fun create(draft: AccountDraft): Account {
        val group = books.group(draft.groupId)
        books.require(group, PermissionLevel.EDIT)
        validate(draft.name.isNotBlank()) { "Account name is required" }
        validate(draft.openingBalance.currency == draft.currency) { "The opening balance must be in the account's currency" }
        validate(draft.type != AccountType.CRYPTO_WALLET || draft.currency.isCrypto) { "A crypto wallet holds a crypto-asset such as BTC" }
        val ledger = books.ledger(group)
        val id = Ids.newId()
        val now = books.now()
        ledger.transaction {
            ledger.ledgerQueries.insertAccount(
                id, draft.institutionId, draft.name.trim(), draft.type.name, draft.currency.code,
                mask(draft.number), draft.number?.trim()?.ifEmpty { null },
                draft.openingBalance.minorUnits, draft.openingDate.toString(), AccountStatus.OPEN.name, draft.notes, now, now,
            )
            draft.ownerMemberIds.forEach { ledger.ledgerQueries.insertAccountOwner(id, it) }
        }
        books.session.audit("CREATE", "account", id)
        return get(id)
    }

    /**
     * Saves changes to an account. The type and currency cannot change once created, since every
     * transaction depends on them. Pass [newNumber] to replace the stored account number.
     */
    fun update(account: Account, newNumber: String? = null) {
        val (group, existing) = locate(account.id)
        books.require(group, PermissionLevel.EDIT)
        validate(account.name.isNotBlank()) { "Account name is required" }
        validate(account.type == existing.type && account.currency == existing.currency) { "Account type and currency cannot be changed" }
        validate(account.openingBalance.currency == existing.currency) { "The opening balance must be in the account's currency" }
        val ledger = books.ledger(group)
        val full = newNumber?.trim()?.ifEmpty { null } ?: ledger.ledgerQueries.accountById(account.id).executeAsOne().number_full
        ledger.transaction {
            ledger.ledgerQueries.updateAccount(
                account.institutionId, account.name.trim(), if (newNumber != null) mask(newNumber) else existing.numberMasked, full,
                account.openingBalance.minorUnits, account.openingDate.toString(), account.status.name, account.notes,
                books.now(), account.id,
            )
            ledger.ledgerQueries.deleteAccountOwners(account.id)
            account.ownerMemberIds.forEach { ledger.ledgerQueries.insertAccountOwner(account.id, it) }
        }
        books.session.audit("UPDATE", "account", account.id)
    }

    /** Closes an account: history is kept, but it is hidden from day-to-day views (ACC-05). */
    fun close(accountId: String) = update(get(accountId).copy(status = AccountStatus.CLOSED))

    /** The full account number, only after the user re-enters their password (SEC-04). */
    fun revealNumber(accountId: String, password: CharArray): String? {
        val (group, _) = locate(accountId)
        if (!books.session.verifyPassword(password)) throw AccessDeniedException("Wrong password")
        books.session.audit("REVEAL", "account", accountId)
        return books.ledger(group).ledgerQueries.accountById(accountId).executeAsOne().number_full
    }

    internal fun locate(accountId: String): Pair<GroupInfo, Account> {
        for (group in books.groups()) {
            val ledger = books.ledger(group).ledgerQueries
            val row = ledger.accountById(accountId).executeAsOneOrNull() ?: continue
            val owners = ledger.accountOwners().executeAsList().filter { it.account_id == accountId }.map { it.member_id }.toSet()
            return group to row.toAccount(group.id, owners)
        }
        throw AccessDeniedException("Account not found or not accessible")
    }

    companion object {
        /** "•••• 1234": only the last four characters stay visible (SEC-04). */
        fun mask(number: String?): String? {
            val clean = number?.filter { it.isLetterOrDigit() } ?: return null
            if (clean.isEmpty()) return null
            return if (clean.length <= 4) "••••" else "•••• ${clean.takeLast(4)}"
        }
    }
}

private fun AccountRow.toAccount(groupId: String, owners: Set<String>): Account {
    val currency = Currency.of(currency)
    return Account(
        id = id,
        groupId = groupId,
        institutionId = institution_id,
        name = name,
        type = AccountType.valueOf(type),
        currency = currency,
        numberMasked = number_masked,
        openingBalance = Money.ofMinor(opening_balance_minor, currency),
        openingDate = LocalDate.parse(opening_date),
        status = AccountStatus.valueOf(status),
        notes = notes,
        ownerMemberIds = owners,
    )
}
