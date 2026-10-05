package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.ContactKind
import ca.schippers.hfm.books.LinkRole
import ca.schippers.hfm.books.RenewalKind
import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.security.KdfParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The sample household builds, and the reminders and reports the screens show work on it. */
class DemoHouseholdTest {

    @Test
    fun `the English demo is an Ontario family`() = check(Language.ENGLISH, house = "House", boat = "Princecraft pontoon") { books ->
        assertEquals(Province.ON, books.province)
        assertTrue(books.accounts.list().any { it.account.name == "TD Visa" })
        assertTrue(books.members.list().any { it.displayName == "Maya" })
    }

    @Test
    fun `the French demo is a Quebec family`() = check(Language.FRENCH, house = "Maison", boat = "Ponton") { books ->
        assertEquals(Province.QC, books.province)
        assertTrue(books.accounts.list().any { it.account.name == "Visa Desjardins" })
        assertTrue(books.members.list().any { it.displayName == "Léa" })
    }

    private fun check(language: Language, house: String, boat: String, more: (Books) -> Unit) {
        val session = DemoHousehold.create(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING), language)
        session.use {
            val books = Books(it)
            val today = today()
            val renewals = books.renewals(today)
            assertTrue(renewals.any { r -> r.kind == RenewalKind.MEDICAL_CLAIM }, "the child's eye exam claim is due within a month: $renewals")
            assertTrue(renewals.any { r -> r.kind == RenewalKind.CARD_ANNUAL_FEE })
            assertTrue(renewals.any { r -> r.kind == RenewalKind.ASSET_WARRANTY }, "the TV's protection plan ends within 60 days: $renewals")
            assertTrue(renewals.any { r -> r.kind == RenewalKind.INSURANCE_RENEWAL }, "the home policy renews within 30 days: $renewals")
            assertTrue(books.insurance.uninsured(today).any { u -> u.name.startsWith("Kayaks") })
            val due = books.upkeepDue(today)
            assertTrue(due.any { u -> !u.vehicle && u.subjectName.startsWith(house) }, "the furnace filter is overdue: $due")
            assertTrue(books.assetMaintenance.upkeep(today).any { u -> u.subjectName.startsWith(boat) && u.status.forecastDate != null })
            assertTrue(books.medical.taxReport(today.year).people.isNotEmpty())
            books.portfolio.performance(kotlinx.datetime.LocalDate(today.year, 1, 1), today)
            books.taxSlips.report(today.year - 1)
            books.fxGains.report(today.year, today)
            // CON-01 to CON-06: everything gathered into contacts, told apart by what each is for.
            val contacts = books.contacts.list()
            assertTrue(books.contacts.proposals().isEmpty(), "every institution, provider and insurer has its contact")
            assertTrue(contacts.count { ContactKind.PHARMACY in it.kinds && it.purpose != null } >= 2, "two pharmacies, each with its what-for line")
            assertTrue(contacts.any { c -> c.person && c.organizationId != null && books.contacts.links(c.id).any { it.link.role == LinkRole.ADVISOR } }, "the advisor at the bank")
            assertTrue(contacts.any { c -> books.contacts.links(c.id).any { it.link.role == LinkRole.LENDER } }, "the bank lends the mortgage")
            more(books)
        }
    }
}
