package ca.schippers.hfm.sync

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The phone's agenda: the reference data it reads (format 7) and how it puts the coming 60 days together. */
class AgendaTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val family = ChosenCalendar(5, "Family", "me@example.com", 0xFF3366)

    private fun reference(
        events: List<RefEvent> = emptyList(),
        schedules: List<RefSchedule> = emptyList(),
        bills: List<RefBill> = emptyList(),
        refills: List<RefRefill> = emptyList(),
        maintenance: List<RefDue> = emptyList(),
        maintenanceAhead: List<RefDue> = emptyList(),
        seasonal: RefSeasonal? = null,
        renewals: List<RefRenewal> = emptyList(),
        tanks: List<RefTank> = emptyList(),
    ) = ReferenceData(
        "H", "en", "CAD", bills = bills, maintenance = maintenance, events = events, refills = refills, seasonal = seasonal,
        trackers = RefTrackers(tanks = tanks), schedules = schedules, renewals = renewals, maintenanceAhead = maintenanceAhead,
    )

    // --- The reference data (format 7) ---------------------------------------------------------

    @Test
    fun `renewals and maintenance further ahead go to the phone and come back the same`() {
        val ref = reference(
            renewals = listOf(RefRenewal("REGISTRATION", "v-1", "Civic", "2026-10-31")),
            maintenanceAhead = listOf(RefDue("t-1", "Civic", "Oil change", "OK", "2026-11-20")),
        )
        val text = SyncJson.encodeToString(ReferenceData.serializer(), ref)
        assertTrue("\"renewals\"" in text && "\"maintenanceAhead\"" in text)
        assertTrue("plate" !in text.lowercase(), "no plate or policy number field")
        assertEquals(ref, SyncJson.decodeFromString(ReferenceData.serializer(), text))
    }

    @Test
    fun `an older desktop's data has no renewals, and an older phone ignores them`() {
        val old = """{"householdName":"H","language":"en","baseCurrency":"CAD","schedules":[],"generatedAtMillis":5}"""
        val read = SyncJson.decodeFromString(ReferenceData.serializer(), old)
        assertTrue(read.renewals.isEmpty() && read.maintenanceAhead.isEmpty())
        // Without them, the data is written exactly as before, so older phones read it as they did.
        val plain = SyncJson.encodeToString(ReferenceData.serializer(), ReferenceData("H", "en", "CAD"))
        assertTrue("renewals" !in plain && "maintenanceAhead" !in plain)
        // A copy kept by an app reading format 6 dropped them: it is fetched again in full.
        assertEquals(7, ReferenceData.FORMAT)
        assertNull(ReferenceData.knownVersion("v", storedFormat = 6))
        assertEquals("v", ReferenceData.knownVersion("v", storedFormat = 7))
    }

    // --- Day by day ------------------------------------------------------------------------------

    @Test
    fun `today first, empty days skipped, items without a time before the others`() {
        val ref = reference(
            events = listOf(
                RefEvent("e1|2026-10-07", "Dentist", "2026-10-07", "14:30", "MEDICAL", "12 Main St", "Léa"),
                RefEvent("e2|2026-10-09", "Soccer", "2026-10-09", "18:00", "ACTIVITY", forWhom = "Léa", driverThere = "Perry"),
                RefEvent("e3|2026-10-05", "Last week", "2026-10-05"),
                RefEvent("e4|2026-12-10", "Too far", "2026-12-10"),
            ),
            schedules = listOf(RefSchedule("Alex", "WORK", "2026-10-07", "08:00", "16:30", "Office")),
            bills = listOf(RefBill("Hydro", "2026-10-07", "84.20", "CAD", false, listOf(3)), RefBill("Old", "2026-10-01", "1", "CAD", false, emptyList())),
        )
        val days = Agenda.days(Agenda.items(ref, emptyList(), today))
        assertEquals(listOf(today, LocalDate.of(2026, 10, 9)), days.map { it.date }, "no empty day, nothing past or beyond 60 days")
        assertEquals(listOf("Hydro", "Alex", "Dentist"), days.first().items.map { it.title }, "the bill, then 08:00, then 14:30")
        assertEquals("Perry", (days[1].items.single() as AgendaItem.Event).event.driverThere)
    }

    @Test
    fun `what is overdue is shown on today, marked late`() {
        val ref = reference(
            refills = listOf(RefRefill("m1", "Ventolin", "2026-10-01"), RefRefill("m2", "Metformin", "2026-10-20")),
            maintenance = listOf(RefDue("t1", "Civic", "Oil", "DUE", "2026-09-30"), RefDue("t2", "Mower", "Blades", "SOON", null, 25, "HOURS"), RefDue("t3", "Civic", "Tires", "OK", "2026-10-25")),
            maintenanceAhead = listOf(RefDue("t4", "House", "Furnace filter", "OK", "2026-11-15"), RefDue("t3", "Civic", "Tires", "OK", "2026-10-25")),
            tanks = listOf(RefTank("k1", "Propane", "PROPANE", "500", orderDate = "2026-10-02")),
        )
        val items = Agenda.items(ref, emptyList(), today)
        val onToday = items.filter { it.date == today }
        assertEquals(setOf("Ventolin", "Civic: Oil", "Mower: Blades", "Propane"), onToday.map { it.title }.toSet())
        assertTrue((onToday.first { it is AgendaItem.Refill } as AgendaItem.Refill).late)
        assertTrue((onToday.first { it is AgendaItem.TankOrder } as AgendaItem.TankOrder).late, "order now")
        assertEquals(1, items.count { it.title == "Civic: Tires" }, "a task in both lists once")
        assertEquals(LocalDate.of(2026, 11, 15), items.first { it.title == "House: Furnace filter" }.date)
    }

    @Test
    fun `a tank's order date comes once, with the renewals when the computer sends them`() {
        val ref = reference(
            renewals = listOf(RefRenewal("FUEL_ORDER", "k1", "Propane", "2026-10-20"), RefRenewal("PET_LICENCE", "p1", "Rex", "2026-11-30")),
            tanks = listOf(RefTank("k1", "Propane", "PROPANE", "500", orderDate = "2026-10-20")),
        )
        val items = Agenda.items(ref, emptyList(), today)
        assertEquals(listOf("Propane", "Rex"), items.map { it.title })
        assertTrue(items.all { it is AgendaItem.Renewal })
        assertTrue(Agenda.items(reference(renewals = listOf(RefRenewal("PET_LICENCE", "p1", "Rex", "2026-12-06"))), emptyList(), today).isEmpty(), "past the 60 days")
    }

    @Test
    fun `seasonal tasks on their due date, a done weekly task again on its date`() {
        val tasks = listOf(
            RefSeasonalTask("s1", "h", "House", "Gutters", false, "TO_DO", dueDate = "2026-10-25"),
            RefSeasonalTask("s2", "h", "House", "Leaves", false, "DONE", doneOn = "2026-10-04", again = "2026-10-11"),
            RefSeasonalTask("s3", "h", "House", "Winter tires", false, "DONE", doneOn = "2026-10-01"),
            RefSeasonalTask("s4", "h", "House", "Hoses", false, "DUE", dueDate = "2026-10-01"),
        )
        val items = Agenda.items(reference(seasonal = RefSeasonal("FALL", "2026-09-22", "2026-12-21", tasks)), emptyList(), today).associateBy { it.title }
        assertEquals(LocalDate.of(2026, 10, 25), items.getValue("House: Gutters").date)
        assertEquals(LocalDate.of(2026, 10, 11), items.getValue("House: Leaves").date)
        assertTrue("House: Winter tires" !in items, "done, not repeating")
        assertEquals(today, items.getValue("House: Hoses").date)
        assertTrue((items.getValue("House: Hoses") as AgendaItem.Seasonal).late)
    }

    @Test
    fun `the phone's own calendars, all-day items on each day and timed ones on their day`() {
        val phone = listOf(
            family to CalendarInstance("1", "2026-10-06", "2026-10-08", title = "Grandma visits"),
            family to CalendarInstance("2", "2026-10-10", "2026-10-10", "09:00", "10:00", "Yoga", "Studio"),
            family to CalendarInstance("3", "2026-10-06", "2026-10-07", "22:00", "02:00", "Night shift"),
            family to CalendarInstance("4", "2026-10-01", "2026-10-01", "09:00", "10:00", "Past"),
        )
        val days = Agenda.days(Agenda.items(null, phone, today))
        assertEquals(listOf(today, today.plusDays(1), LocalDate.of(2026, 10, 10)), days.map { it.date })
        assertEquals(listOf("Grandma visits", "Night shift"), days[0].items.map { it.title })
        assertTrue((days[0].items[1] as AgendaItem.Phone).continued, "began yesterday")
        assertEquals("Family", (days[2].items.single() as AgendaItem.Phone).calendar.name)
        assertEquals("09:00", days[2].items.single().time)
    }

    // --- The month view ---------------------------------------------------------------------------

    @Test
    fun `the months reached, their grids and each day's marks`() {
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), Agenda.months(today), "to December 5")
        assertEquals(LocalDate.of(2026, 12, 5), Agenda.lastDay(today))
        val sunday = Agenda.grid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(listOf(null, null, null, null, LocalDate.of(2026, 10, 1)), sunday.take(5), "October 1 2026 is a Thursday")
        assertEquals(35, sunday.size)
        val monday = Agenda.grid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(3, monday.indexOf(LocalDate.of(2026, 10, 1)))
        assertEquals(0, monday.size % 7)

        val ref = reference(
            events = listOf(RefEvent("e|2026-10-09", "Soccer", "2026-10-09", "18:00"), RefEvent("f|2026-10-09", "Piano", "2026-10-09", "16:00")),
            bills = listOf(RefBill("Hydro", "2026-10-09", "84.20", "CAD", false, emptyList())),
        )
        val days = Agenda.days(Agenda.items(ref, emptyList(), today))
        assertEquals(listOf(AgendaKind.EVENT, AgendaKind.BILL) to 3, Agenda.marks(days)[LocalDate.of(2026, 10, 9)])
        assertEquals(0, Agenda.indexOf(days, today), "a day without anything goes to the next one")
        assertNull(Agenda.indexOf(days, LocalDate.of(2026, 10, 10)))
    }
}
