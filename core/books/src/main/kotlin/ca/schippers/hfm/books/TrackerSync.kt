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

/**
 * UTL-01, UTL-02, HRS-01, CHO-01, VOL-01 on the phone: what its log forms send is stored as facts
 * (not documents to review), and what they pick from goes back with the reference data.
 */
internal class TrackerSync(private val books: Books) {

    private fun date(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

    private fun number(text: String?): java.math.BigDecimal? = text?.trim()?.ifEmpty { null }?.let { it.replace(',', '.').toBigDecimalOrNull() ?: throw ValidationException("error.invalidNumber") }

    /** Stores one entry from the phone [deviceId]; volunteer hours go in [groupId], the rest where their meter, tank, client or chore is. */
    fun receive(t: PhoneTracker, groupId: String?, deviceId: String) {
        t.meter?.let { m ->
            books.utilities.addReading(m.meterId, date(m.date), number(m.value), number(m.onPeak), number(m.midPeak), number(m.offPeak), m.note, deviceId)
            return
        }
        t.tank?.let { r ->
            books.utilities.addTankReading(r.tankId, date(r.date), number(r.percent), number(r.litres), r.note, deviceId)
            return
        }
        t.hours?.let { h ->
            books.workHours.save(WorkEntry("", h.clientId, date(h.date), h.minutes, h.taskId, h.description, startTime = h.startTime), deviceId)
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
                VolunteerEntry("", groupId ?: throw ValidationException("error.noEditableGroup"), v.memberId, v.organization, kind, date(v.date), v.minutes, v.contactId, v.activity),
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
        return RefTrackers(
            meters = runCatching { books.utilities.meters() }.getOrDefault(emptyList()).map { m ->
                val last = m.readings.lastOrNull()
                RefMeter(m.id, m.name, m.kind.name, m.assetId?.let(places::get), m.timeOfUse, last?.value?.toPlainString(), last?.date?.toString())
            },
            tanks = runCatching { books.utilities.tanks() }.getOrDefault(emptyList()).map { t ->
                RefTank(t.id, t.name, t.fuel.name, t.capacityLitres.toPlainString(), t.assetId?.let(places::get), t.readings.lastOrNull()?.percent?.toPlainString())
            },
            clients = runCatching { books.workHours.clients() }.getOrDefault(emptyList()).map { c ->
                RefWorkClient(c.id, c.name, c.tasks.filter { !it.archived }.map { RefWorkTask(it.id, it.name) })
            },
            chores = runCatching { books.chores.list() }.getOrDefault(emptyList()).map { c ->
                RefChore(
                    c.id, c.name, c.memberId, people[c.memberId].orEmpty(), c.amount?.toBigDecimal()?.toPlainString(), c.currency.code, c.points,
                    c.ticks.filter { it.date >= week }.map { it.date.toString() }.distinct(),
                )
            },
            organizations = runCatching { books.volunteer.organizations() }.getOrDefault(emptyList()).take(MAX_ORGANIZATIONS).map {
                RefVolunteerOrg(it.organization, it.kind.name, it.contactId, it.memberId)
            },
        )
    }

    private companion object {
        const val MAX_ORGANIZATIONS = 50
    }
}
