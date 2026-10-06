package ca.schippers.hfm.books

import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.MoneyFormat
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

data class TransactionHit(val transaction: Transaction, val account: Account, val payeeName: String?)

/** OTH-03: everything that matches one search box, grouped by kind. */
data class SearchResults(
    val transactions: List<TransactionHit>,
    val accounts: List<Account>,
    val payees: List<Payee>,
    val categories: List<Category>,
    val bills: List<Bill>,
    val institutions: List<Institution>,
    /** Documents whose recognized text, title, merchant, notes or file name match (TX-06). */
    val documents: List<VaultDocument> = emptyList(),
    /** Contacts whose name, what-for line, details or notes match (CON-05). */
    val contacts: List<Contact> = emptyList(),
) {
    val isEmpty: Boolean
        get() = transactions.isEmpty() && accounts.isEmpty() && payees.isEmpty() && categories.isEmpty() && bills.isEmpty() &&
            institutions.isEmpty() && documents.isEmpty() && contacts.isEmpty()
}

/**
 * Global search (OTH-03, TX-06) across the records the user may see. Names match without regard
 * to case or accents ("epicerie" finds "Épicerie"). A number also finds transactions of that
 * amount, in or out. Documents match on the text read from them, their title, merchant, notes
 * and file name, in the groups the user may see.
 */
class SearchService internal constructor(private val books: Books) {

    fun search(query: String, locale: Locale = Locale.CANADA, limit: Int = 200): SearchResults {
        val text = query.trim()
        if (text.length < 2) return SearchResults(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val needle = fold(text)
        fun matches(s: String?) = s != null && fold(s).contains(needle)

        val accounts = books.accounts.all(includeClosed = true)
        val payees = books.payees.list(includeArchived = true)
        val matchingPayees = payees.filter { matches(it.name) }
        val amount = amountOf(text, locale)

        val accountById = accounts.associateBy { it.id }
        val payeeNames = payees.associate { it.id to it.name }
        val hits = books.groups().flatMap { group ->
            val q = books.ledger(group).ledgerQueries
            q.searchTxns(
                pattern = "%" + escapeLike(text) + "%",
                payeeIds = matchingPayees.map { it.id },
                // No amount in the query: a value no transaction can have.
                amount = amount ?: Long.MIN_VALUE,
                negativeAmount = amount?.let { -it } ?: Long.MIN_VALUE,
                limit = limit.toLong(),
            ).executeAsList().let { rows ->
                // The splits of every hit in a few queries, not one query per hit.
                val splits = rows.map { it.id }.chunked(500).flatMap { ids -> q.splitsForTxns(ids).executeAsList() }.groupBy { it.txn_id }
                rows.map { it to splits[it.id].orEmpty() }
            }.mapNotNull { (row, splits) ->
                val account = accountById[row.account_id] ?: return@mapNotNull null
                TransactionHit(
                    row.toTransaction(account.currency, splits, emptySet()),
                    account,
                    row.payee_id?.let(payeeNames::get) ?: row.payee_text,
                )
            }
        }.sortedByDescending { it.transaction.date }.take(limit)

        return SearchResults(
            transactions = hits,
            accounts = accounts.filter { matches(it.name) || matches(it.notes) },
            payees = matchingPayees,
            categories = books.categories.list(includeArchived = true).filter { matches(it.nameEn) || matches(it.nameFr) },
            bills = books.bills.list(includeInactive = true).filter { matches(it.name) || matches(it.payeeName) },
            institutions = books.institutions.list().filter { matches(it.name) },
            documents = books.documents.search(DocumentQuery(text = text, limit = limit)),
            contacts = books.contacts.list(ContactFilter(text = text, includeArchived = true)),
        )
    }

    companion object {
        /** Lower case without accents, for comparing names. */
        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

        /** LIKE wildcards in the query are matched literally; '!' is the escape character. */
        private fun escapeLike(s: String) = s.replace("!", "!!").replace("%", "!%").replace("_", "!_")

        /** "142,37", "142.37 $" or "-142.37" as minor units, assuming a two-decimal currency. */
        internal fun amountOf(text: String, locale: Locale): Long? {
            if (!text.any(Char::isDigit) || text.any { it.isLetter() && it !in "$" }) return null
            val value: BigDecimal = runCatching { MoneyFormat.parseDecimal(text, locale) }.getOrNull() ?: return null
            if (value.scale() > Currency.CAD.minorUnits) return null
            return value.movePointRight(Currency.CAD.minorUnits).abs().longValueExact()
        }
    }
}
