package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.i18n.Messages
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** HRS-01: a kind of work for a client, with its own hourly rate when it differs from the client's. */
data class WorkTask(val id: String, val name: String, val rate: Money? = null, val archived: Boolean = false)

/** HRS-01: a side-income client, with an hourly rate (in [currency]) and who does the work. */
data class WorkClient(
    val id: String,
    val groupId: String,
    val name: String,
    val currency: Currency,
    val rate: Money? = null,
    /** The address and other details printed on its invoices. */
    val details: String? = null,
    val memberId: String? = null,
    val archived: Boolean = false,
    val notes: String? = null,
    val tasks: List<WorkTask> = emptyList(),
)

/** HRS-01: time worked for a client; [rate] overrides the task's and the client's. Billed once, on [invoiceId]. */
data class WorkEntry(
    val id: String,
    val clientId: String,
    val date: LocalDate,
    val minutes: Int,
    val taskId: String? = null,
    val description: String? = null,
    val memberId: String? = null,
    /** HH:mm, when timed. */
    val startTime: String? = null,
    val rate: Money? = null,
    val invoiceId: String? = null,
    val billedDate: LocalDate? = null,
    val fromPhone: Boolean = false,
)

/**
 * HRS-01: hours worked for side income, per client and task, timed on the phone or entered on the
 * computer, and turned into invoice lines (SAL-04) in one step. Hours whose invoice was deleted
 * count as not billed again.
 */
class WorkHoursService internal constructor(private val books: Books) {

    private fun group(groupId: String, level: PermissionLevel = PermissionLevel.EDIT) = books.group(groupId).also { books.require(it, level) }

    fun clients(includeArchived: Boolean = false): List<WorkClient> = books.groups().flatMap { g ->
        val q = books.ledger(g).trackersQueries
        q.workClients().executeAsList().map { c ->
            val cur = Currency.of(c.currency)
            WorkClient(
                c.id, g.id, c.name, cur, c.rate_minor?.let { Money.ofMinor(it, cur) }, c.details, c.member_id, c.archived == 1L, c.notes,
                q.workTasks(c.id).executeAsList().map { t -> WorkTask(t.id, t.name, t.rate_minor?.let { Money.ofMinor(it, cur) }, t.archived == 1L) },
            )
        }
    }.filter { includeArchived || !it.archived }

    fun client(id: String): WorkClient = clients(true).firstOrNull { it.id == id } ?: throw ValidationException("error.notFound")

    /** Saves the client with its tasks; tasks left out are archived when hours use them, deleted otherwise. */
    fun saveClient(c: WorkClient): WorkClient {
        validate(c.name.isNotBlank(), "error.nameRequired")
        validate(c.rate == null || (c.rate.currency == c.currency && !c.rate.isNegative), "error.workRate")
        validate(c.tasks.all { it.name.isNotBlank() && (it.rate == null || (it.rate.currency == c.currency && !it.rate.isNegative)) }, "error.workTask")
        val existing = clients(true).firstOrNull { it.id == c.id }
        val group = group(existing?.groupId ?: c.groupId)
        val id = c.id.ifBlank { Ids.newId() }
        val q = books.ledger(group).trackersQueries
        val used = existing?.let { hours(it.id).mapNotNull { h -> h.taskId }.toSet() }.orEmpty()
        books.ledger(group).transaction {
            q.upsertWorkClient(id, c.name.trim(), c.details?.trim()?.ifEmpty { null }, c.rate?.minorUnits, c.currency.code, c.memberId, if (c.archived) 1 else 0, c.notes?.trim()?.ifEmpty { null }, books.now())
            val kept = c.tasks.map { it.copy(id = it.id.ifBlank { Ids.newId() }) }
            existing?.tasks?.filter { t -> kept.none { it.id == t.id } }?.forEach { t ->
                if (t.id in used) q.upsertWorkTask(t.id, id, t.name, t.rate?.minorUnits, 1) else q.deleteWorkTask(t.id)
            }
            kept.forEach { t -> q.upsertWorkTask(t.id, id, t.name.trim(), t.rate?.minorUnits, if (t.archived) 1 else 0) }
        }
        return client(id)
    }

    /** Deletes the client with its tasks and hours; invoices already made stay. */
    fun deleteClient(c: WorkClient) = books.ledger(group(c.groupId)).trackersQueries.deleteWorkClient(c.id)

    /** The hours of [clientId], or of every client, oldest first. */
    fun hours(clientId: String? = null): List<WorkEntry> = books.groups().flatMap { g ->
        val q = books.ledger(g).trackersQueries
        q.workClients().executeAsList().filter { clientId == null || it.id == clientId }.flatMap { c ->
            val cur = Currency.of(c.currency)
            q.workHours(c.id).executeAsList().map { h ->
                WorkEntry(
                    h.id, h.client_id, LocalDate.parse(h.date), h.minutes.toInt(), h.task_id, h.description, h.member_id, h.start_time,
                    h.rate_minor?.let { Money.ofMinor(it, cur) }, h.invoice_id, h.billed_date?.let(LocalDate::parse), h.device_id != null,
                )
            }
        }
    }.sortedWith(compareBy({ it.date }, { it.startTime.orEmpty() }))

    /**
     * Adds or changes hours. New hours can be added by anyone who may capture in the client's group
     * (the phone's timer); changing them needs an editor. [deviceId] names the phone they came from.
     */
    fun save(e: WorkEntry, deviceId: String? = null): WorkEntry {
        val c = client(e.clientId)
        validate(e.minutes in 1..MAX_MINUTES, "error.workMinutes")
        validate(e.startTime == null || TIME.matches(e.startTime), "error.invalidTime")
        validate(e.taskId == null || c.tasks.any { it.id == e.taskId }, "error.notFound")
        validate(e.rate == null || (e.rate.currency == c.currency && !e.rate.isNegative), "error.workRate")
        // Any client's: an id of another client's hours must not let the right to add change them.
        val existing = e.id.takeIf { it.isNotBlank() }?.let { id -> hours().firstOrNull { it.id == id } }
        validate(existing == null || existing.clientId == c.id, "error.notFound")
        val group = group(c.groupId, if (existing == null) PermissionLevel.CAPTURE_ONLY else PermissionLevel.EDIT)
        val id = e.id.ifBlank { Ids.newId() }
        books.ledger(group).trackersQueries.upsertWorkHours(
            id, c.id, e.taskId, e.description?.trim()?.ifEmpty { null }, e.memberId ?: c.memberId, e.date.toString(), e.startTime, e.minutes.toLong(), e.rate?.minorUnits,
            e.invoiceId, e.billedDate?.toString(), deviceId, books.now(),
        )
        return hours(c.id).first { it.id == id }
    }

    fun delete(e: WorkEntry) = books.ledger(group(client(e.clientId).groupId)).trackersQueries.deleteWorkHours(e.id)

    /** The hourly rate that applies: the entry's own, else its task's, else the client's. */
    fun rate(e: WorkEntry, c: WorkClient = client(e.clientId)): Money? = e.rate ?: c.tasks.firstOrNull { it.id == e.taskId }?.rate ?: c.rate

    /** The entry's hours at its rate, to the cent; null without a rate. */
    fun amount(e: WorkEntry, c: WorkClient = client(e.clientId)): Money? = rate(e, c)?.let { r ->
        Money.of((r.toBigDecimal() * hoursOf(e.minutes)).setScale(r.currency.minorUnits, RoundingMode.HALF_UP), r.currency)
    }

    /** The hours not on an invoice that still exists. */
    fun unbilled(clientId: String? = null): List<WorkEntry> {
        val invoices = books.invoices.list().map { it.id }.toSet()
        return hours(clientId).filter { it.invoiceId == null || it.invoiceId !in invoices }
    }

    /**
     * SAL-04: a draft invoice to the client for [entryIds] (hours not billed yet), one line per task
     * and rate with its hours as the quantity, numbered as the next of [issueDate]'s year. The hours
     * are marked billed on it in the same database transaction (the invoice and the hours are in the
     * client's group), so either both happen or neither. Sales taxes follow the person's last invoice, if any.
     */
    fun invoice(clientId: String, entryIds: Collection<String>, issueDate: LocalDate): Invoice {
        val c = client(clientId)
        val chosen = unbilled(clientId).filter { it.id in entryIds }
        validate(chosen.isNotEmpty(), "error.workNothingToBill")
        validate(chosen.all { rate(it, c) != null }, "error.workNoRate")
        val group = group(c.groupId)
        val lines = chosen.groupBy { (c.tasks.firstOrNull { t -> t.id == it.taskId }?.name ?: it.description ?: Messages.get(books.language, "hours.line")) to rate(it, c)!! }
            .map { (key, l) ->
                val (name, rate) = key
                val from = l.minOf { it.date }
                val to = l.maxOf { it.date }
                val period = if (from == to) from.toString() else Messages.get(books.language, "hours.period", from.toString(), to.toString())
                InvoiceLine("$name ($period)", hoursOf(l.sumOf { it.minutes }).toPlainString(), rate.toBigDecimal().toPlainString())
            }
        val previous = books.invoices.list().filter { it.memberId == c.memberId }.maxByOrNull { it.issueDate }
        val ledger = books.ledger(group)
        return ledger.transactionWithResult {
            val invoice = books.invoices.save(
                Invoice(
                    "", c.groupId, books.invoices.nextNumber(issueDate.year), c.name, issueDate, c.currency, lines,
                    customerDetails = c.details, memberId = c.memberId, taxes = previous?.taxes.orEmpty(),
                ),
            )
            chosen.forEach { ledger.trackersQueries.billWorkHours(invoice.id, issueDate.toString(), it.id) }
            invoice
        }
    }

    companion object {
        /** One entry is at most a day. */
        const val MAX_MINUTES = 24 * 60
        private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        /** Minutes as hours to two decimals: 90 is 1.50. */
        fun hoursOf(minutes: Int): BigDecimal = BigDecimal(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
    }
}
