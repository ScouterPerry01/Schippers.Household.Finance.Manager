package ca.schippers.hfm.books

import ca.schippers.hfm.calc.schedule.Recurrence
import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarHealthTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var shared: String
    private lateinit var marie: Member
    private val dir get() = temp.resolve("C.hfm")
    private fun d(m: Int, day: Int) = LocalDate(2026, m, day)
    private fun at(m: Int, day: Int, h: Int, min: Int = 0) = LocalDateTime(2026, m, day, h, min)

    @BeforeEach
    fun setUp() {
        books = Books(store.create(dir, "C", "perry", "Perry", "pw".toCharArray()).session)
        shared = books.groups().single().id
        marie = books.members.create("Marie", MemberKind.ADULT)
    }

    @AfterEach
    fun tearDown() = books.session.close()

    // --- Calendar --------------------------------------------------------------------------------

    @Test
    fun `timed event reminders follow their lead times`() {
        books.calendar.create(EventDraft(shared, "Garage: winter tires", EventCategory.VEHICLE, d(10, 15), LocalTime(9, 30), 60, "Garage Tremblay", reminderMinutes = listOf(1440, 60)))
        assertTrue(books.calendar.reminders(at(10, 13, 10)).isEmpty(), "two days before: no reminder yet")
        assertEquals(listOf(1410L), books.calendar.reminders(at(10, 14, 10)).map { it.minutesBefore }, "the day before")
        assertEquals(listOf(30L), books.calendar.reminders(at(10, 15, 9)).map { it.minutesBefore })
        assertTrue(books.calendar.reminders(at(10, 15, 10)).isEmpty(), "after it started")
    }

    @Test
    fun `repeating events, and done or cancelled occurrences`() {
        val e = books.calendar.create(EventDraft(shared, "Bank advisor", EventCategory.FINANCIAL, d(10, 6), LocalTime(14, 0), recurrence = Recurrence.MONTHLY, endDate = d(12, 31)))
        assertEquals(listOf(d(10, 6), d(11, 6), d(12, 6)), books.calendar.occurrences(d(10, 1), d(12, 31)).map { it.date })
        books.calendar.mark(e.id, d(11, 6), OccurrenceMark.CANCELLED)
        assertEquals(OccurrenceMark.CANCELLED, books.calendar.occurrences(d(11, 1), d(11, 30)).single().mark)
        assertTrue(books.calendar.reminders(at(11, 6, 13)).isEmpty(), "a cancelled occurrence has no reminder")
        books.calendar.mark(e.id, d(11, 6), null)
        assertEquals(1, books.calendar.reminders(at(11, 6, 13)).size)
    }

    @Test
    fun `all-day events remind from 8 in the morning`() {
        books.calendar.create(EventDraft(shared, "Property tax due", EventCategory.HOME, d(10, 20), reminderMinutes = listOf(2 * 1440)))
        assertEquals(listOf(2 * 1440L), books.calendar.reminders(at(10, 18, 8)).map { it.minutesBefore })
        assertTrue(books.calendar.reminders(at(10, 18, 7)).isEmpty())
    }

    @Test
    fun `private events are not visible to other users`() {
        books.session.addUser("marie", "Marie", Role.MEMBER, "mpw".toCharArray())
        books.session.close()
        store.unlock(dir, "marie", "mpw".toCharArray()).use { session ->
            val mine = Books(session)
            val private = session.createGroup("Marie - private", private = true)
            mine.calendar.create(EventDraft(private, "Therapist", EventCategory.MEDICAL, d(10, 8), LocalTime(16, 0)))
            assertEquals(1, mine.calendar.list().size)
        }
        books = Books(store.unlock(dir, "perry", "pw".toCharArray()))
        assertTrue(books.calendar.list().isEmpty())
    }

    @Test
    fun `the calendar combines events, bills and health due dates`() {
        val account = books.accounts.create(AccountDraft(shared, "Chequing", AccountType.CHEQUING, Currency.CAD, Money.parse("0", Currency.CAD), d(1, 1)))
        books.bills.create(BillDraft(BillKind.BILL, "Rent", Money.parse("1450", Currency.CAD), account.id, Recurrence.MONTHLY, d(10, 1)))
        books.calendar.create(EventDraft(shared, "Dentist", EventCategory.MEDICAL, d(10, 9), LocalTime(10, 0), memberId = marie.id))
        books.health.saveMedication(Medication("", shared, marie.id, "Levothyroxine", "50 mcg", null, null, null, null, null, null, 30, 2, d(9, 10), 5, true, null))
        val items = books.calendar.items(d(10, 1), d(10, 31))
        assertEquals(listOf("Bill", "Event", "Health"), items.map { it::class.simpleName })
        assertEquals(listOf(d(10, 1), d(10, 9), d(10, 10)), items.map { it.date })
    }

    @Test
    fun `validation`() {
        assertFailsWith<ValidationException> { books.calendar.create(EventDraft(shared, " ", EventCategory.OTHER, d(10, 1))) }
        assertFailsWith<ValidationException> { books.calendar.create(EventDraft(shared, "X", EventCategory.OTHER, d(10, 5), endDate = d(10, 1))) }
        assertFailsWith<AccessDeniedException> { books.calendar.get("missing") }
    }

    // --- Health ----------------------------------------------------------------------------------

    @Test
    fun `refill dates, reminders and renewal`() {
        val pharmacy = books.health.saveProvider(HealthProvider("", shared, "Pharmacie Jean Coutu", ProviderKind.PHARMACY, "418-555-0100", null, null, false))
        val med = books.health.saveMedication(
            Medication("", shared, marie.id, "Atorvastatin", "20 mg", "1 tablet at bedtime", null, pharmacy.id, "RX-12345", d(1, 1), null, 30, 1, d(9, 1), 5, true, null),
        )
        assertEquals(d(10, 1), med.nextRefill)
        assertTrue(books.health.refillReminders(d(9, 20)).isEmpty())
        assertEquals(listOf(4), books.health.refillReminders(d(9, 27)).map { it.daysLeft })

        val refilled = books.health.recordFill(med.id, d(9, 29), daysSupply = 90, quantity = "90 tablets")
        assertEquals(d(12, 28), refilled.nextRefill)
        assertEquals(0, refilled.refillsRemaining)
        assertTrue(refilled.needsRenewal)
        assertEquals(1, books.health.fills(med.id).size)
        assertEquals(listOf("Atorvastatin"), books.health.medications(marie.id).map { it.name })
    }

    @Test
    fun `conditions, allergies, tests and immunizations per person`() {
        val other = books.members.create("Léa", MemberKind.CHILD)
        books.health.saveCondition(HealthCondition("", shared, marie.id, "Hypothyroidism", d(3, 2), ConditionStatus.MANAGED, null, null))
        books.health.saveAllergy(Allergy("", shared, other.id, "Peanuts", "Hives", Severity.SEVERE, null))
        val test = books.health.saveTest(HealthTest("", shared, marie.id, "TSH", d(9, 15), "2.1", "mIU/L", "0.4–4.0", null, d(12, 15), null))
        books.health.saveImmunization(Immunization("", shared, other.id, "Influenza", d(10, 1), null, LocalDate(2027, 10, 1), null))

        assertEquals(listOf("Hypothyroidism"), books.health.conditions(marie.id).map { it.name })
        assertTrue(books.health.allergies(marie.id).isEmpty())
        assertEquals(Severity.SEVERE, books.health.allergies(other.id).single().severity)
        assertEquals(1, books.health.due(d(12, 1), d(12, 31)).filterIsInstance<HealthDue.TestFollowUp>().size)

        books.health.delete(test)
        assertTrue(books.health.tests().isEmpty())
    }

    // --- Exchange rates (FX-07, FX-08) -----------------------------------------------------------

    @Test
    fun `followed currencies and the optional second source`() {
        books.rates.follow(Currency.EUR)
        books.rates.follow(Currency.of("XOF"))
        assertEquals(setOf(Currency.EUR), books.rates.neededCurrencies(), "Bank of Canada publishes EUR")
        assertEquals(setOf(Currency.of("XOF")), books.rates.notOnBankOfCanada())

        val urls = mutableListOf<String>()
        val boc = """{"observations":[{"d":"2026-10-01","FXEURCAD":{"v":"1.6100"}}]}"""
        val open = """{"result":"success","time_last_update_utc":"Thu, 01 Oct 2026 00:02:31 +0000","base_code":"CAD","rates":{"CAD":1,"XOF":400,"EUR":0.5}}"""
        val fetch = { url: String -> urls += url; if ("bankofcanada" in url) boc else open }

        assertEquals(1, books.rates.updateAll(d(10, 1), fetch), "second source off by default")
        books.rates.openSourceEnabled = true
        books.rates.updateAll(d(10, 1), fetch)
        assertTrue(urls.last().startsWith("https://open.er-api.com/"))
        assertEquals(0, BigDecimal("0.0025").compareTo(books.rates.cadPerUnit(Currency.of("XOF"), d(10, 1))))
        assertEquals(0, BigDecimal("1.61").compareTo(books.rates.cadPerUnit(Currency.EUR, d(10, 1))), "Bank of Canada rate kept")
        assertEquals(RateSource.OPEN, books.rates.list(Currency.of("XOF"), d(10, 1), d(10, 1)).single().source)

        books.rates.unfollow(Currency.of("XOF"))
        assertTrue(books.rates.notOnBankOfCanada().isEmpty())
        assertFalse(books.rates.followed().contains(Currency.of("XOF")))
    }
}
