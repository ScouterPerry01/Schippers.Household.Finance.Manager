package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UTL-01, UTL-02, HRS-01, CHO-01, VOL-01: meters and tanks, hours worked, chores and volunteer hours. */
class TrackersTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books
    private lateinit var chequing: Account
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val group get() = books.groups().single().id
    private val today = LocalDate(2026, 10, 6)

    @BeforeEach
    fun setUp() {
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("5000.00"), LocalDate(2025, 1, 1)))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `a meter's use by month, an unusual month, and the cost per unit from its bill`() {
        val bill = books.bills.create(BillDraft(BillKind.BILL, "Hydro", cad("100.00"), chequing.id, Recurrence.MONTHLY, LocalDate(2025, 10, 1)))
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "Hydro meter", MeterKind.ELECTRICITY, timeOfUse = true, billId = bill.id))
        // 1,000 kWh a month for a year, but 1,400 in January 2026 (the January before used 1,000).
        var total = BigDecimal.ZERO
        var date = LocalDate(2024, 12, 1)
        books.utilities.addReading(meter.id, date, total)
        while (date < LocalDate(2026, 10, 1)) {
            total += BigDecimal(if (date == LocalDate(2026, 1, 1)) 1400 else 1000)
            date = date.plus(DatePeriod(months = 1))
            books.utilities.addReading(meter.id, date, null, onPeak = total.multiply(BigDecimal("0.2")), midPeak = total.multiply(BigDecimal("0.3")), offPeak = total.multiply(BigDecimal("0.5")))
        }
        val s = books.utilities.summary(books.utilities.meter(meter.id), today)
        assertEquals(22, s.months.size)
        val january = s.months.first { it.use.year == 2026 && it.use.month == 1 }
        assertEquals(0, january.use.amount.compareTo(BigDecimal(1400)))
        assertEquals(listOf(january), s.unusual, "140 % of last January is above 130 %")
        assertEquals(BigDecimal("40"), january.changePercent)
        // 12 bills of $100 over the last 12 months' 12,400 kWh.
        assertEquals("0.0968", s.cost!!.perUnit.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString())
        assertFailsWith<ValidationException> { books.utilities.addReading(meter.id, today, BigDecimal(-1)) }
        assertFailsWith<ValidationException> { books.utilities.addReading(meter.id, today, null) }
    }

    @Test
    fun `a propane tank's level, use and order reminder, and a delivery paid from an account`() {
        val tank = books.utilities.saveTank(FuelTank("", group, "Cottage propane", FuelKind.PROPANE, BigDecimal(500), supplier = "Propane Nord"))
        books.utilities.addTankReading(tank.id, LocalDate(2026, 8, 1), BigDecimal(80))
        // 300 litres used in 30 days... then 100 litres delivered and the gauge read in litres.
        books.utilities.addTankReading(tank.id, LocalDate(2026, 8, 31), null, litres = BigDecimal(100))
        val delivery = books.utilities.addDelivery(tank.id, LocalDate(2026, 9, 10), BigDecimal(250), cad("275.00"), chequing.id)
        books.utilities.addTankReading(tank.id, LocalDate(2026, 9, 30), BigDecimal(40))
        val t = books.utilities.tank(tank.id)
        val paid = books.transactions.get(delivery.transactionId!!)
        assertEquals(cad("-275.00"), paid.amount)
        val status = books.utilities.status(t, today)
        // 400 L - 100 L in August (30 days); 100 + 250 - 200 = 150 L in September (30 days): 450 L in 60 days, 7.5 a day.
        assertEquals(0, status.projection!!.dailyUse!!.compareTo(BigDecimal("7.5")))
        assertEquals(0, status.projection.levelToday.compareTo(BigDecimal("155")))
        // 25 % is 125 litres: from 200 on September 30, 75 litres at 7.5 a day is 10 days.
        assertEquals(LocalDate(2026, 10, 10), status.projection.orderDate)
        assertEquals(0, status.pricePerLitre!!.perUnit.compareTo(BigDecimal("1.1")))
        val orders = books.renewals(today).filter { it.kind == RenewalKind.FUEL_ORDER }
        assertEquals(listOf(LocalDate(2026, 10, 10)), orders.map { it.date }, "within the 14 days of Rates and rules")
        assertEquals(4, orders.single().daysLeft)
        assertTrue(books.utilities.orders(LocalDate(2026, 9, 1)).isEmpty(), "not yet due then")
        books.utilities.deleteDelivery(t, delivery, withTransaction = true)
        assertTrue(runCatching { books.transactions.get(delivery.transactionId) }.isFailure, "the payment went with it")
        assertFailsWith<ValidationException> { books.utilities.addTankReading(tank.id, today, BigDecimal(120)) }
    }

    @Test
    fun `unbilled hours become invoice lines in one step, and are then billed`() {
        val alex = books.members.create("Sam", MemberKind.ADULT)
        val client = books.workHours.saveClient(
            WorkClient("", group, "Lee family", Currency.CAD, cad("40.00"), "12 Elm St.", alex.id, tasks = listOf(WorkTask("", "Tutoring"), WorkTask("", "Exam prep", cad("55.00")))),
        )
        val tutoring = client.tasks.first { it.name == "Tutoring" }
        val exam = client.tasks.first { it.name == "Exam prep" }
        val a = books.workHours.save(WorkEntry("", client.id, LocalDate(2026, 9, 8), 90, tutoring.id, startTime = "16:00"))
        val b = books.workHours.save(WorkEntry("", client.id, LocalDate(2026, 9, 15), 60, tutoring.id))
        val c = books.workHours.save(WorkEntry("", client.id, LocalDate(2026, 9, 22), 120, exam.id))
        assertEquals(cad("60.00"), books.workHours.amount(a))
        assertEquals(cad("110.00"), books.workHours.amount(c))
        val invoice = books.workHours.invoice(client.id, listOf(a.id, b.id, c.id), LocalDate(2026, 9, 30))
        assertEquals("Lee family", invoice.customer)
        assertEquals(alex.id, invoice.memberId)
        assertEquals(listOf(InvoiceLine("Tutoring (2026-09-08 to 2026-09-15)", "2.50", "40.00"), InvoiceLine("Exam prep (2026-09-22)", "2.00", "55.00")), invoice.lines)
        assertEquals(cad("210.00"), invoice.total)
        assertTrue(books.workHours.unbilled(client.id).isEmpty())
        assertEquals(LocalDate(2026, 9, 30), books.workHours.hours(client.id).first().billedDate)
        assertFailsWith<ValidationException>("nothing left to bill") { books.workHours.invoice(client.id, listOf(a.id), LocalDate(2026, 9, 30)) }
        // A deleted invoice leaves its hours to bill again.
        books.invoices.delete(invoice)
        assertEquals(3, books.workHours.unbilled(client.id).size)
        assertFailsWith<ValidationException> { books.workHours.save(WorkEntry("", client.id, today, 0)) }
        assertFailsWith<ValidationException> { books.workHours.save(WorkEntry("", client.id, today, 30, startTime = "25:00")) }
        // Another client's hours cannot be reached through their id.
        val other = books.workHours.saveClient(WorkClient("", group, "Other family", Currency.CAD))
        assertFailsWith<ValidationException> { books.workHours.save(WorkEntry(a.id, other.id, today, 30)) }
        assertEquals(client.id, books.workHours.hours().single { it.id == a.id }.clientId)
        // Tasks with hours are kept, archived, when removed from the client.
        val saved = books.workHours.saveClient(books.workHours.client(client.id).copy(tasks = listOf(tutoring)))
        assertEquals(listOf(false, true), saved.tasks.sortedBy { it.name != "Tutoring" }.map { it.archived })
    }

    @Test
    fun `chores a user may not mark paid are not paid either`() {
        val kid = books.members.create("Emma", MemberKind.CHILD)
        val shared = group
        val family = books.session.createGroup("Family", private = false)
        val sam = books.users.add("sam", "Sam", ca.schippers.hfm.domain.Role.MEMBER, "password2-long".toCharArray()).userId
        books.session.setPermission(shared, sam, ca.schippers.hfm.domain.PermissionLevel.EDIT)
        books.session.setPermission(family, sam, ca.schippers.hfm.domain.PermissionLevel.CAPTURE_ONLY)
        books.allowances.save(Allowance("", shared, kid.id, cad("5.00"), AllowanceFrequency.WEEKLY, LocalDate(2026, 9, 5), null, null, emptyList()))
        val dishes = books.chores.save(Chore("", family, kid.id, "Dishes", Currency.CAD, cad("1.00")))
        books.chores.tick(dishes.id, LocalDate(2026, 10, 1))
        books.session.close()
        books = Books(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING).unlock(temp.resolve("T.hfm"), "sam", "password2-long".toCharArray()))
        val allowance = books.allowances.list().single()
        assertFailsWith<ca.schippers.hfm.data.AccessDeniedException> { books.chores.pay(allowance, today) }
        assertTrue(books.allowances.list().single().entries.isEmpty(), "no allowance entry for chores left unpaid")
        assertEquals(cad("1.00"), books.chores.earnings(kid.id, Currency.CAD, today).unpaid)
    }

    @Test
    fun `chores earn money paid with the allowance, and points`() {
        val kid = books.members.create("Emma", MemberKind.CHILD)
        val allowance = books.allowances.save(Allowance("", group, kid.id, cad("5.00"), AllowanceFrequency.WEEKLY, LocalDate(2026, 9, 5), null, null, emptyList()))
        val dishes = books.chores.save(Chore("", group, kid.id, "Dishes", Currency.CAD, cad("1.00")))
        val bed = books.chores.save(Chore("", group, kid.id, "Make the bed", Currency.CAD, points = 2))
        books.chores.tick(dishes.id, LocalDate(2026, 10, 1))
        books.chores.tick(dishes.id, LocalDate(2026, 10, 2))
        books.chores.tick(bed.id, LocalDate(2026, 10, 2))
        val late = books.chores.tick(dishes.id, LocalDate(2026, 10, 8))
        val e = books.chores.earnings(kid.id, Currency.CAD, today)
        assertEquals(cad("2.00"), e.unpaid)
        assertEquals(2, e.points)
        assertEquals(cad("2.00"), books.chores.pay(allowance, today))
        val money = books.allowances.list().single()
        assertEquals(AllowanceKind.EARNED, money.entries.single().kind)
        assertEquals(cad("2.00"), books.allowances.status(money, today).balance)
        assertEquals(cad("0.00"), books.chores.earnings(kid.id, Currency.CAD, today).unpaid)
        assertNull(books.chores.pay(money, today), "nothing more to pay")
        val paidTick = books.chores.get(dishes.id).ticks.first()
        assertFailsWith<ValidationException>("a paid chore stays") { books.chores.untick(books.chores.get(dishes.id), paidTick.id) }
        books.chores.untick(books.chores.get(dishes.id), late.id)
        assertEquals(2, books.chores.get(dishes.id).ticks.size)
    }

    @Test
    fun `volunteer hours add up by year, and the firefighting hours are checked against 200`() {
        val sam = books.members.create("Sam", MemberKind.ADULT)
        repeat(20) { i -> books.volunteer.save(VolunteerEntry("", group, sam.id, "Fire department", VolunteerKind.FIREFIGHTER, LocalDate(2026, 1 + i % 9, 1 + i), 8 * 60)) }
        repeat(3) { i -> books.volunteer.save(VolunteerEntry("", group, sam.id, "Search and rescue", VolunteerKind.SEARCH_RESCUE, LocalDate(2026, 6, 1 + i), 10 * 60)) }
        books.volunteer.save(VolunteerEntry("", group, sam.id, "Food bank", VolunteerKind.OTHER, LocalDate(2026, 6, 2), 3 * 60))
        books.volunteer.save(VolunteerEntry("", group, sam.id, "Fire department", VolunteerKind.FIREFIGHTER, LocalDate(2025, 12, 31), 60))
        val year = books.volunteer.year(sam.id, 2026)
        assertEquals((160 + 30 + 3) * 60, year.minutes)
        assertEquals(190 * 60, year.emergencyMinutes)
        assertEquals(200, year.thresholdHours)
        assertTrue(!year.meetsThreshold, "190 hours")
        books.volunteer.save(VolunteerEntry("", group, sam.id, "Search and rescue", VolunteerKind.SEARCH_RESCUE, LocalDate(2026, 7, 1), 10 * 60))
        assertTrue(books.volunteer.year(sam.id, 2026).meetsThreshold, "the two services count together")
        assertEquals("Fire department", books.volunteer.year(sam.id, 2026).byOrganization.first().first)
        assertFailsWith<ValidationException> { books.volunteer.save(VolunteerEntry("", group, sam.id, " ", VolunteerKind.OTHER, today, 60)) }
    }
}
