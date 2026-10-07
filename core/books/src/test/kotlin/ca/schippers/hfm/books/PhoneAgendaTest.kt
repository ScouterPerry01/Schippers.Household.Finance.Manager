package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.sync.RefRenewal
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The phone's agenda: renewals, maintenance further ahead and 60 days of schedules, only what the phone's user may see (HH-11). */
class PhoneAgendaTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("A.hfm")
    private val today = LocalDate(2026, 10, 5)
    private val now = 1_790_000_000_000L

    private fun household(): Books = Books(store.create(dir, "A", "perry", "Perry", "password1".toCharArray()).session)
    private fun marie() = Books(store.unlock(dir, "marie", "password2-long".toCharArray()))
    private fun perry() = Books(store.unlock(dir, "perry", "password1".toCharArray()))

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    private fun d(month: Int, day: Int) = LocalDate(2026, month, day)

    private fun weekly(group: String, member: String, label: String) = PersonSchedule(
        "", group, member, ScheduleKind.SCHOOL, label, d(9, 1), null, 1, false, null,
        listOf(ScheduleShift(0, DayOfWeek.WEDNESDAY, LocalTime(8, 30), LocalTime(15, 0))), emptyList(),
    )

    @Test
    fun `the phone's agenda gets renewals, later maintenance and schedules its user can see, not another user's private ones`() {
        household().use { books ->
            val shared = books.groups().single().id
            val lea = books.members.create("Léa", MemberKind.CHILD)
            val civic = books.vehicles.save(Vehicle("", shared, "Civic", plate = "ABC 123", registrationRenewal = d(11, 10), insurer = "Desjardins", policyNumber = "P-998877"))
            books.vehicles.saveTask(MaintenanceTask("", civic.id, "Vidange", intervalMonths = 6, startDate = d(5, 15)))
            books.schedules.save(weekly(shared, lea.id, "École"))
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(shared, marie, PermissionLevel.VIEW)
        }
        marie().use { m ->
            val own = m.session.createGroup("Marie - privé", private = true)
            val lea = m.members.list().first { it.displayName == "Léa" }
            val car = m.vehicles.save(Vehicle("", own, "Marie's car", registrationRenewal = d(10, 20)))
            m.vehicles.saveTask(MaintenanceTask("", car.id, "Pneus", intervalMonths = 6, startDate = d(5, 20)))
            m.schedules.save(weekly(own, lea.id, "Secret"))
            val ref = m.sync.reference(today, now)
            assertTrue("Marie's car" in ref.renewals.map { it.subject } && "Marie's car" in ref.maintenanceAhead.map { it.subject }, "her phone gets her own")
        }
        perry().use { p ->
            val ref = p.sync.reference(today, now)
            assertEquals(RefRenewal("REGISTRATION", ref.renewals.first { it.subject == "Civic" }.subjectId, "Civic", "2026-11-10"), ref.renewals.first { it.subject == "Civic" })
            assertTrue(ref.renewals.none { it.subject == "Marie's car" }, "not Marie's private vehicle: ${ref.renewals}")
            val ahead = ref.maintenanceAhead.single()
            assertEquals("Civic" to "Vidange", ahead.subject to ahead.task)
            assertEquals("2026-11-15", ahead.dueDate, "next due after this month, within 60 days")
            assertTrue(ref.maintenance.none { it.taskId == ahead.taskId }, "not twice")
            val school = ref.schedules.filter { it.person == "Léa" }
            assertEquals("2026-12-02", school.maxOf { it.date }, "every Wednesday up to the 60th day")
            assertTrue(school.all { it.label == "École" }, "not Marie's private schedule: $school")
            val text = kotlinx.serialization.json.Json.encodeToString(ca.schippers.hfm.sync.ReferenceData.serializer(), ref)
            assertTrue("P-998877" !in text && "ABC 123" !in text, "no policy number or plate")
        }
    }
}
