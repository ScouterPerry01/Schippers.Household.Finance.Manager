package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** EST-01, EST-02: where each person's papers are, and the emergency summary from the books. */
class EstateTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    @Test
    fun `each person's record is kept where they choose, and the summary gathers the books`() {
        val dir = temp.resolve("E.hfm")
        val books = Books(store.create(dir, "E", "perry", "Perry", "password1".toCharArray()).session)
        val shared = books.groups().single().id
        val perry = books.members.create("Perry", MemberKind.ADULT)
        val bank = books.institutions.create(Institution("", "Desjardins", branch = "Sainte-Foy", phone = "1-800-224-7737"))
        val chequing = books.accounts.create(AccountDraft(shared, "Joint", AccountType.CHEQUING, Currency.CAD, cad("1500"), LocalDate(2026, 1, 1), bank.id, "123-45678-9", setOf(perry.id)))
        books.insurance.save(InsurancePolicy("", shared, PolicyKind.LIFE, "Beneva", policyNumber = "L-4410", insuredMemberId = perry.id, coverage = cad("250000")))
        val plan = EstatePlan(
            willLocation = "Notary's office, Me Tremblay", willDate = "2024-05-10", mandateLocation = "Filing cabinet, top drawer",
            safeDepositBox = "Desjardins Sainte-Foy, box 214", safeDepositKeys = "Kitchen drawer", organDonor = true,
            contacts = listOf(EstateContact(ContactRole.NOTARY, "Me Tremblay", "Tremblay & associés", "418-555-0101")),
        )
        books.estate.save(perry.id, shared, plan)
        books.estate.save(perry.id, shared, plan.copy(funeralWishes = "Simple service"))
        assertEquals(1, books.estate.records().size, "one record per person, updated in place")
        assertEquals("Simple service", books.estate.record(perry.id)!!.plan.funeralWishes)

        val s = books.estate.summary()
        assertEquals(listOf("Desjardins"), s.institutions.map { it.name })
        val account = s.accounts.single { it.account.id == chequing.id }
        assertEquals(listOf("Perry"), account.ownerNames)
        assertTrue(account.account.numberMasked.orEmpty().endsWith("789") && !account.account.numberMasked.orEmpty().contains("45678"), account.account.numberMasked)
        assertEquals("Perry" to "L-4410", s.policies.single().let { it.insuredName to it.policy.policyNumber })
        books.session.close()
    }

    @Test
    fun `a record kept in a private group is not seen by other household users`() {
        val dir = temp.resolve("P.hfm")
        val books = Books(store.create(dir, "P", "perry", "Perry", "password1".toCharArray()).session)
        val marieMember = books.members.create("Marie", MemberKind.ADULT).id
        books.users.add("marie", "Marie", Role.MEMBER, "password2".toCharArray(), marieMember)
        books.session.close()
        val marie = Books(store.unlock(dir, "marie", "password2".toCharArray()))
        val own = marie.session.createGroup("Marie - privé", private = true)
        marie.estate.save(marieMember, own, EstatePlan(willLocation = "My desk"))
        assertEquals("My desk", marie.estate.record(marieMember)!!.plan.willLocation)
        marie.session.close()
        val perry = Books(store.unlock(dir, "perry", "password1".toCharArray()))
        assertNull(perry.estate.record(marieMember))
        perry.session.close()
    }
}
