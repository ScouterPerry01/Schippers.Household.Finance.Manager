package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate

/** CHO-01: a chore done on [date], with what it earned then; paid with the allowance ([paidDate]). */
data class ChoreTick(
    val id: String,
    val date: LocalDate,
    val amount: Money? = null,
    val points: Int? = null,
    val allowanceEntryId: String? = null,
    val paidDate: LocalDate? = null,
    val fromPhone: Boolean = false,
) {
    val paid: Boolean get() = paidDate != null
}

/** CHO-01: a child's chore, worth an amount, points, or both. */
data class Chore(
    val id: String,
    val groupId: String,
    val memberId: String,
    val name: String,
    val currency: Currency,
    val amount: Money? = null,
    val points: Int? = null,
    val archived: Boolean = false,
    val ticks: List<ChoreTick> = emptyList(),
)

/** What a child has earned with chores: the money not paid yet, and the points. */
data class ChoreEarnings(val memberId: String, val unpaid: Money, val unpaidTicks: Int, val points: Int, val pointsThisMonth: Int)

/**
 * CHO-01: chores per child, ticked on the computer or on the phone (by a parent, or by the child on
 * their own phone). The money earned is paid with the allowance: paying it adds one "earned" entry
 * to the child's money (HH-03) and marks the chores paid.
 */
class ChoreService internal constructor(private val books: Books) {

    private fun group(groupId: String, level: PermissionLevel = PermissionLevel.EDIT) = books.group(groupId).also { books.require(it, level) }

    fun list(includeArchived: Boolean = false): List<Chore> = books.groups().flatMap { g ->
        val q = books.ledger(g).trackersQueries
        q.chores().executeAsList().map { c ->
            val cur = Currency.of(c.currency)
            Chore(
                c.id, g.id, c.member_id, c.name, cur, c.amount_minor?.let { Money.ofMinor(it, cur) }, c.points?.toInt(), c.archived == 1L,
                q.choreTicks(c.id).executeAsList().map { t ->
                    ChoreTick(t.id, LocalDate.parse(t.date), t.amount_minor?.let { Money.ofMinor(it, cur) }, t.points?.toInt(), t.allowance_entry_id, t.paid_date?.let(LocalDate::parse), t.device_id != null)
                },
            )
        }
    }.filter { includeArchived || !it.archived }

    fun get(id: String): Chore = list(true).firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    fun save(c: Chore): Chore {
        validate(c.name.isNotBlank(), "error.nameRequired")
        validate(c.amount == null || (c.amount.currency == c.currency && !c.amount.isNegative), "error.choreAmount")
        validate(c.points == null || c.points >= 0, "error.chorePoints")
        val group = group(list(true).firstOrNull { it.id == c.id }?.groupId ?: c.groupId)
        val id = c.id.ifBlank { Ids.newId() }
        books.ledger(group).trackersQueries.upsertChore(id, c.memberId, c.name.trim(), c.amount?.minorUnits, c.currency.code, c.points?.toLong(), if (c.archived) 1 else 0, books.now())
        return get(id)
    }

    /** Deletes the chore and its ticks; what was already paid stays in the child's money. */
    fun delete(c: Chore) = books.ledger(group(c.groupId)).trackersQueries.deleteChore(c.id)

    /** Ticks the chore as done on [date], earning what it is worth now. Anyone who may capture in its group can tick. */
    fun tick(choreId: String, date: LocalDate, deviceId: String? = null): ChoreTick {
        val c = get(choreId)
        validate(!c.archived, "error.choreArchived")
        val id = Ids.newId()
        books.ledger(group(c.groupId, PermissionLevel.CAPTURE_ONLY)).trackersQueries
            .insertChoreTick(id, c.id, date.toString(), c.amount?.minorUnits, c.points?.toLong(), null, null, deviceId, books.now())
        return get(choreId).ticks.first { it.id == id }
    }

    /** Removes a tick not paid yet. */
    fun untick(c: Chore, tickId: String) {
        val t = c.ticks.firstOrNull { it.id == tickId } ?: throw ValidationException("error.notFound")
        validate(!t.paid, "error.chorePaid")
        books.ledger(group(c.groupId)).trackersQueries.deleteChoreTick(tickId)
    }

    /** What [memberId] earned with chores up to [until], in [currency]: the money not paid and the points. */
    fun earnings(memberId: String, currency: Currency, until: LocalDate = books.today()): ChoreEarnings {
        val mine = list(true).filter { it.memberId == memberId }
        val ticks = mine.flatMap { it.ticks }.filter { it.date <= until }
        val unpaid = ticks.filter { !it.paid && it.amount?.currency == currency && it.amount.isPositive }
        return ChoreEarnings(
            memberId, unpaid.fold(Money.zero(currency)) { a, t -> a + t.amount!! }, unpaid.size,
            ticks.sumOf { it.points ?: 0 }, ticks.filter { it.date.year == until.year && it.date.month == until.month }.sumOf { it.points ?: 0 },
        )
    }

    /**
     * Pays the chores of the allowance's child done up to [date], with the allowance: one EARNED
     * entry for their total, and each chore marked paid by it. Returns what was paid, or null when
     * nothing was owed.
     */
    fun pay(a: Allowance, date: LocalDate): Money? {
        val total = earnings(a.memberId, a.amount.currency, date).unpaid
        if (!total.isPositive) return null
        val entry = books.allowances.addEntry(a, date, total, AllowanceKind.EARNED, Messages.get(books.language, "chores.paidNote"))
        markPaid(a.memberId, a.amount.currency, date, entry)
        return total
    }

    /** Marks the chores of [memberId] done up to [date] paid, for a child without an allowance (paid by hand). */
    fun markPaid(memberId: String, currency: Currency, date: LocalDate, allowanceEntryId: String? = null) {
        list(true).filter { it.memberId == memberId }.forEach { c ->
            val q = books.ledger(group(c.groupId)).trackersQueries
            c.ticks.filter { !it.paid && it.date <= date && it.amount?.currency == currency && it.amount.isPositive }.forEach { q.payChoreTick(allowanceEntryId, date.toString(), it.id) }
        }
    }
}
