package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.CategoryKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The audit log lives in core.db, which every user of the household can read, so it records what
 * happened but never amounts or text the user typed (owner's rule; Phase 3 and 4 security reviews).
 */
class AuditPrivacyTest {

    @TempDir
    lateinit var temp: Path

    private lateinit var books: Books

    @BeforeEach
    fun setUp() {
        val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
        books = Books(store.create(temp.resolve("A.hfm"), "A", "perry", "Perry", "pw".toCharArray()).session)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    @Test
    fun `budgets and exports leave no amount or file name in the audit log`() {
        val groceries = books.categories.list().first { it.kind == CategoryKind.EXPENSE && it.parentId != null }
        books.budgets.set(groceries.id, BudgetPeriod.MONTHLY, Money.parse("1234.56", Currency.CAD), startMonth = LocalDate(2026, 10, 1))
        books.backups.exportAll(temp.resolve("Perry private export.zip"))

        val details = books.session.core.coreQueries.recentAudit(100).executeAsList().mapNotNull { it.details }
        assertTrue(details.none { "1234" in it || "1 234" in it }, "no amount: $details")
        assertTrue(details.none { "private export" in it }, "no file name typed by the user: $details")
    }

    @Test
    fun `wrong passwords to reveal a number are logged without the password, and slow down after three`() {
        var now = 1_790_000_000_000L
        val clocked = Books(books.session) { now }
        val group = clocked.groups().single().id
        val contact = clocked.contacts.save(Contact("", group, "Bank", details = listOf(ContactDetail(type = DetailType.NUMBER, label = "Client", value = "987654321"))))
        val detail = clocked.contacts.get(contact.id).numbers.single().id
        val account = clocked.accounts.create(AccountDraft(group, "Chequing", ca.schippers.hfm.domain.AccountType.CHEQUING, Currency.CAD, Money.parse("0", Currency.CAD), LocalDate(2026, 1, 1)))

        assertFailsWith<AccessDeniedException> { clocked.contacts.revealNumber(contact.id, detail, "guess-one".toCharArray()) }
        assertFailsWith<AccessDeniedException> { clocked.accounts.revealNumber(account.id, "guess-two".toCharArray()) }
        assertFailsWith<AccessDeniedException> { clocked.contacts.revealNumber(contact.id, detail, "guess-three".toCharArray()) }
        // After three wrong passwords, even the right one waits 30 seconds, and is not checked meanwhile.
        assertEquals(30, assertFailsWith<TooManyAttemptsException> { clocked.contacts.revealNumber(contact.id, detail, "pw".toCharArray()) }.waitSeconds)
        now += 30_000
        assertFailsWith<AccessDeniedException> { clocked.contacts.revealNumber(contact.id, detail, "guess-four".toCharArray()) }
        assertEquals(60, assertFailsWith<TooManyAttemptsException> { clocked.accounts.revealNumber(account.id, "pw".toCharArray()) }.waitSeconds, "twice as long")
        now += 60_000
        assertEquals("987654321", clocked.contacts.revealNumber(contact.id, detail, "pw".toCharArray()))
        assertFailsWith<AccessDeniedException> { clocked.contacts.revealNumber(contact.id, detail, "guess-five".toCharArray()) }
        assertFailsWith<AccessDeniedException> { clocked.contacts.revealNumber(contact.id, detail, "guess-six".toCharArray()) }
        assertEquals("987654321", clocked.contacts.revealNumber(contact.id, detail, "pw".toCharArray()), "a right password starts the count over")

        val log = books.session.core.coreQueries.recentAudit(100).executeAsList()
        val failed = log.filter { it.action == "REVEAL_FAILED" }
        assertEquals(6, failed.size)
        assertEquals(setOf(contact.id, account.id), failed.mapNotNull { it.entity_id }.toSet())
        val text = log.flatMap { listOfNotNull(it.details, it.entity_id) }
        assertTrue(text.none { "guess" in it || "987654321" in it || "pw" == it }, "no password or number: $text")
    }

    @Test
    fun `Phase 5 records leave no name, number, amount, note or file name in the audit log`() {
        val group = books.groups().single().id
        // Contacts (CON-01 to CON-07): saved, a number revealed, two merged, one deleted.
        val doctor = books.contacts.save(
            Contact(
                "", group, "Dr Tremblay Secretname", person = true, purpose = "Sam dermatology",
                details = listOf(ContactDetail(type = DetailType.NUMBER, label = "Patient", value = "123456789")), notes = "typed contact note",
            ),
        )
        books.contacts.revealNumber(doctor.id, books.contacts.get(doctor.id).numbers.single().id, "pw".toCharArray())
        val twin = books.contacts.save(Contact("", group, "Dr Tremblay Secretname", person = true))
        books.contacts.merge(doctor.id, twin.id, MergeChoices())
        books.contacts.delete(books.contacts.save(Contact("", group, "Gone Contactname")).id)
        // Rates and rules: a household value with a typed note.
        books.rateRules.add("threshold.budgetAlert", null, LocalDate(2026, 1, 1), "87.65", "typed rule note")
        // Category rules: a payee text and an amount range.
        val groceries = books.categories.list().first { it.kind == CategoryKind.EXPENSE && it.parentId != null }
        books.rules.delete(books.rules.create("Costco Secretstore", groceries.id, amountMin = Money.parse("777.77", Currency.CAD)).id)
        // AI reading: settings and a reading kept with its document, whose file name the user chose.
        books.ai.saveSettings(AiSettings(enabled = true))
        val doc = books.documents.import(group, "receipt".encodeToByteArray(), "Perry private receipt.jpg", "image/jpeg").document
        books.ai.saveReading(doc.id, "receipt", "hfm/receipt/v1", """{"merchant":"Secretstore","total":55.55}""", true, "claude-opus-5-5", ca.schippers.hfm.ocr.DocumentDraft())

        val details = books.session.core.coreQueries.recentAudit(200).executeAsList().mapNotNull { it.details }
        for (secret in listOf("Tremblay", "Secretname", "123456789", "dermatology", "typed", "87.65", "8765", "Costco", "Secretstore", "777.77", "77777", "55.55", "private receipt")) {
            assertTrue(details.none { secret in it }, "\"$secret\" is not in the audit log: $details")
        }
    }

    @Test
    fun `calendars, places, trips and trackers leave no title, place, coordinate, amount or note in the audit log`() {
        val group = books.groups().single().id
        val cad = { a: String -> Money.parse(a, Currency.CAD) }
        val kid = books.members.create("Emma Secretkid", ca.schippers.hfm.domain.MemberKind.CHILD)
        // CSY-01 to CSY-05: a calendar brought in from a phone, with a private item, and an .ics file.
        books.broughtIn.receive(
            "phone-1",
            ca.schippers.hfm.sync.CalendarSnapshot(
                "s1", "7", 1_790_000_000_000L, "Secretcalendar", "perry@secret.ca", null, ca.schippers.hfm.sync.CalendarVisibility.BUSY, "2026-10-05", "2026-12-04",
                listOf(ca.schippers.hfm.sync.CalendarInstance("1", "2026-10-07", "2026-10-07", "10:00", "11:00", "Oncology Secretvisit", "Secretclinic", ca.schippers.hfm.sync.CalendarVisibility.PRIVATE)),
            ),
            1_790_000_000_000L,
        )
        val ics = listOf("BEGIN:VCALENDAR", "BEGIN:VEVENT", "DTSTART:20261008T090000", "SUMMARY:Secretics meeting", "LOCATION:Secretroom", "END:VEVENT", "END:VCALENDAR")
        books.icsImport.import(group, ics.joinToString("\r\n").encodeToByteArray())
        // CAL-09 to CAL-11: a schedule and an activity with drivers and a cost that became a transaction.
        books.schedules.save(
            PersonSchedule(
                "", group, kid.id, ScheduleKind.SCHOOL, "Secretschool", LocalDate(2026, 9, 1), null, 1, true, "typed schedule note",
                listOf(ScheduleShift(0, kotlinx.datetime.DayOfWeek.MONDAY, kotlinx.datetime.LocalTime(8, 0), kotlinx.datetime.LocalTime(15, 0))), emptyList(),
            ),
        )
        val account = books.accounts.create(AccountDraft(group, "Chequing", ca.schippers.hfm.domain.AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        val swim = books.calendar.create(
            EventDraft(
                group, "Secretswim lessons", EventCategory.ACTIVITY, LocalDate(2026, 10, 7), kotlinx.datetime.LocalTime(18, 0), 60, "Secretpool", memberId = kid.id,
                driverThere = Driver(name = "Secretdriver"), cost = cad("44.44"),
            ),
        )
        books.calendar.recordCost(swim.id, LocalDate(2026, 10, 7), account.id, null)
        // TRP-01 to TRP-10: a place with its coordinates, a trip and a fill-up.
        val car = books.vehicles.save(Vehicle("", group, "Secretcar", purchaseOdometer = 1_000))
        val cottage = books.places.save(Place("", group, "Secretcottage", PlaceCategory.OTHER, "12 Secretlane", 44.77123, -76.69456))
        books.trips.save(
            Trip(
                "", group, LocalDate(2026, 10, 3), "", java.math.BigDecimal.ZERO, false, TripPurpose.MEDICAL, car.id, endPlaceId = cottage.id, origin = "Secretorigin",
                notes = "typed trip note", startOdometer = 61_500, endOdometer = 61_678, passengers = "Secretpassenger",
            ),
        )
        books.vehicles.saveFuel(FuelEntry("", car.id, LocalDate(2026, 10, 4), 61_700, java.math.BigDecimal("62.9"), cad("98.76"), station = "Secretstation"))
        // UTL-01, UTL-02, HRS-01, CHO-01, VOL-01.
        val meter = books.utilities.saveMeter(UtilityMeter("", group, "Secretmeter", MeterKind.ELECTRICITY, notes = "typed meter note"))
        books.utilities.addReading(meter.id, LocalDate(2026, 10, 5), java.math.BigDecimal("4321.5"), notes = "typed reading note")
        val tank = books.utilities.saveTank(FuelTank("", group, "Secrettank", FuelKind.PROPANE, java.math.BigDecimal(500), supplier = "Secretsupplier"))
        books.utilities.addDelivery(tank.id, LocalDate(2026, 9, 10), java.math.BigDecimal(250), cad("654.32"), account.id)
        val client = books.workHours.saveClient(WorkClient("", group, "Secretclient", Currency.CAD, cad("88.88"), details = "Secretaddress"))
        books.workHours.save(WorkEntry("", client.id, LocalDate(2026, 10, 5), 90, description = "typed hours note"))
        books.workHours.invoice(client.id, books.workHours.unbilled(client.id).map { it.id }, LocalDate(2026, 10, 6))
        val chore = books.chores.save(Chore("", group, kid.id, "Secretchore", Currency.CAD, cad("3.33")))
        books.chores.tick(chore.id, LocalDate(2026, 10, 5))
        books.volunteer.save(VolunteerEntry("", group, kid.id, "Secretorg", VolunteerKind.SCHOOL, LocalDate(2026, 10, 4), 120, activity = "typed activity"))

        val details = books.session.core.coreQueries.recentAudit(300).executeAsList().mapNotNull { it.details }
        for (secret in listOf(
            "Secret", "secret", "Oncology", "typed", "44.44", "4444", "98.76", "9876", "4321", "654.32", "65432", "88.88", "8888", "3.33", "333", "44.77", "76.69", "62.9",
        )) {
            assertTrue(details.none { secret in it }, "\"$secret\" is not in the audit log: $details")
        }
    }
}
