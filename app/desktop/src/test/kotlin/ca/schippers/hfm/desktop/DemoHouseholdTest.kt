package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.RenewalKind
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.security.KdfParams
import kotlin.test.Test
import kotlin.test.assertTrue

/** The sample household builds, and the reminders and reports the screens show work on it. */
class DemoHouseholdTest {

    @Test
    fun `the demo household builds and its reminders and reports run`() {
        val session = DemoHousehold.create(HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING))
        session.use {
            val books = Books(it)
            val today = today()
            val renewals = books.renewals(today)
            assertTrue(renewals.any { r -> r.kind == RenewalKind.MEDICAL_CLAIM }, "Léa's eye exam claim is due within a month: $renewals")
            assertTrue(renewals.any { r -> r.kind == RenewalKind.CARD_ANNUAL_FEE })
            assertTrue(renewals.any { r -> r.kind == RenewalKind.ASSET_WARRANTY }, "the TV's protection plan ends within 60 days: $renewals")
            assertTrue(renewals.any { r -> r.kind == RenewalKind.INSURANCE_RENEWAL }, "the home policy renews within 30 days: $renewals")
            assertTrue(books.insurance.uninsured(today).any { u -> u.name.startsWith("Kayaks") })
            val due = books.upkeepDue(today)
            assertTrue(due.any { u -> !u.vehicle && u.subjectName.startsWith("Maison") }, "the furnace filter is overdue: $due")
            assertTrue(books.assetMaintenance.upkeep(today).any { u -> u.subjectName.startsWith("Ponton") && u.status.forecastDate != null })
            assertTrue(books.medical.taxReport(today.year).people.isNotEmpty())
            books.portfolio.performance(kotlinx.datetime.LocalDate(today.year, 1, 1), today)
            books.taxSlips.report(today.year - 1)
            books.fxGains.report(today.year, today)
        }
    }
}
