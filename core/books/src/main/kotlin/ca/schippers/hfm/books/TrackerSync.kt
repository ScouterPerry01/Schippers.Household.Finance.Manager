package ca.schippers.hfm.books

import ca.schippers.hfm.sync.PhoneTracker
import ca.schippers.hfm.sync.RefChore
import ca.schippers.hfm.sync.RefMeter
import ca.schippers.hfm.sync.RefTank
import ca.schippers.hfm.sync.RefTrackers
import ca.schippers.hfm.sync.RefVolunteerOrg
import ca.schippers.hfm.sync.RefWorkClient
import ca.schippers.hfm.sync.RefWorkTask
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.math.BigDecimal

/**
 * A decimal a phone sent ("12,5" too), or null when empty. At most [PHONE_DIGITS] digits before the
 * point and [PHONE_DECIMALS] after it: an exponent such as 1E999999999 would otherwise become a
 * billion characters when written out.
 */
internal fun phoneDecimal(text: String?): BigDecimal? {
    val n = text?.trim()?.ifEmpty { null }?.let { it.replace(',', '.').toBigDecimalOrNull() ?: throw ValidationException("error.invalidNumber") } ?: return null
    validate(n.precision() - n.scale() <= PHONE_DIGITS && n.scale() <= PHONE_DECIMALS, "error.invalidNumber")
    return n
}

/** Text a phone sent, trimmed and cut to [max] characters, or null when empty. */
internal fun phoneText(text: String?, max: Int = PHONE_TEXT): String? = text?.trim()?.take(max)?.ifEmpty { null }

private const val PHONE_DIGITS = 12
private const val PHONE_DECIMALS = 6
private const val PHONE_TEXT = 500

/**
 * UTL-01, UTL-02, HRS-01, CHO-01, VOL-01 on the phone: what its log forms send is stored as facts
 * (not documents to review), and what they pick from goes back with the reference data.
 */
internal class TrackerSync(private val books: Books) {

    private fun date(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

    private fun number(text: String?): BigDecimal? = phoneDecimal(text)

    /**
     * Stores one entry from the phone [deviceId]. Volunteer hours go where the computer puts them
     * ([VolunteerService.defaultGroup]), or in the phone's group [groupId] when the user may not add
     * there; the rest go where their meter, tank, client or chore is.
     */
    fun receive(t: PhoneTracker, groupId: String?, deviceId: String) {
        t.meter?.let { m ->
            books.utilities.addReading(m.meterId, date(m.date), number(m.value), number(m.onPeak), number(m.midPeak), number(m.offPeak), phoneText(m.note), deviceId)
            return
        }
        t.tank?.let { r ->
            books.utilities.addTankReading(r.tankId, date(r.date), number(r.percent), number(r.litres), phoneText(r.note), deviceId)
            return
        }
        t.hours?.let { h ->
            books.workHours.save(WorkEntry("", h.clientId, date(h.date), h.minutes, h.taskId, phoneText(h.description), startTime = h.startTime), deviceId)
            return
        }
        t.chore?.let { c ->
            books.chores.tick(c.choreId, date(c.date), deviceId)
            return
        }
        t.volunteer?.let { v ->
            validate(books.members.list(includeArchived = true).any { it.id == v.memberId }, "error.memberRequired")
            val kind = VolunteerKind.entries.firstOrNull { it.name == v.kind } ?: VolunteerKind.OTHER
            books.volunteer.save(
                VolunteerEntry(
                    "", books.volunteer.defaultGroup(v.memberId) ?: groupId ?: throw ValidationException("error.noEditableGroup"), v.memberId, phoneText(v.organization, MAX_NAME).orEmpty(), kind, date(v.date), v.minutes, v.contactId,
                    phoneText(v.activity),
                ),
                deviceId,
            )
            return
        }
        throw ValidationException("error.notFound")
    }

    /** What the phone's log forms pick from, in the groups the phone's user can see. */
    fun reference(today: LocalDate): RefTrackers {
        val places = runCatching { books.assets.list().associate { it.id to it.name } }.getOrDefault(emptyMap())
        val people = runCatching { books.members.list(includeArchived = true).associate { it.id to it.displayName } }.getOrDefault(emptyMap())
        val week = today.minus(DatePeriod(days = 7))
        // UTL-01: an unusual month of last month or the one before, as on the computer's reminders.
        val unusual = runCatching { books.utilities.unusual(today) }.getOrDefault(emptyList()).associateBy { it.meter.id }
        return RefTrackers(
            meters = runCatching { books.utilities.meters() }.getOrDefault(emptyList()).map { m ->
                val last = m.readings.lastOrNull()
                val flagged = unusual[m.id]?.month
                RefMeter(
                    m.id, m.name, m.kind.name, m.assetId?.let(places::get), m.timeOfUse, last?.value?.toPlainString(), last?.date?.toString(),
                    flagged?.let { "%04d-%02d".format(it.use.year, it.use.month) }, flagged?.changePercent?.let { (if (it.signum() > 0) "+" else "") + it.toPlainString() },
                )
            },
            tanks = runCatching { books.utilities.tanks() }.getOrDefault(emptyList()).map { t ->
                // UTL-02: the order reminder, as on the computer.
                val order = runCatching { books.utilities.order(t, today) }.getOrNull()
                RefTank(t.id, t.name, t.fuel.name, t.capacityLitres.toPlainString(), t.assetId?.let(places::get), t.readings.lastOrNull()?.percent?.toPlainString(), order?.date?.toString())
            },
            clients = runCatching { books.workHours.clients() }.getOrDefault(emptyList()).map { c ->
                RefWorkClient(c.id, c.name, c.tasks.filter { !it.archived }.map { RefWorkTask(it.id, it.name) })
            },
            chores = runCatching { books.chores.list() }.getOrDefault(emptyList()).map { c ->
                RefChore(
                    c.id, c.name, c.memberId, people[c.memberId].orEmpty(), c.amount?.toBigDecimal()?.toPlainString(), c.currency.code, c.points,
                    c.ticks.filter { it.date >= week }.map { it.date.toString() }.distinct(), c.severalADay,
                )
            },
            organizations = runCatching { books.volunteer.organizations() }.getOrDefault(emptyList()).take(MAX_ORGANIZATIONS).map {
                RefVolunteerOrg(it.organization, it.kind.name, it.contactId, it.memberId)
            },
        )
    }

    private companion object {
        const val MAX_ORGANIZATIONS = 50
        const val MAX_NAME = 120
    }
}
