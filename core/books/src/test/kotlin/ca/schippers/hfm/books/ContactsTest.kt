package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CON-01 to CON-06: contacts, what they are for, their links, filters, privacy and gathering. */
class ContactsTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("C.hfm")
    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    private fun household(): Books = Books(store.create(dir, "C", "perry", "Perry", "password1".toCharArray()).session)

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    @Test
    fun `a contact keeps its kinds, what-for line, people served and details, with numbers masked`() = household().use { books ->
        val group = books.groups().single().id
        val lea = books.members.create("Léa", MemberKind.CHILD).id
        val saved = books.contacts.save(
            Contact(
                "", group, "Clinique Saint-Roch", kinds = setOf(ContactKind.CLINIC, ContactKind.FAMILY_DOCTOR), purpose = "Léa's pediatrician", memberIds = setOf(lea),
                details = listOf(
                    ContactDetail(type = DetailType.PHONE, label = "Office", value = "418 555-0101"),
                    ContactDetail(type = DetailType.EMAIL, value = "info@clinique.example"),
                    ContactDetail(type = DetailType.NUMBER, label = "File", value = "FILE-778899"),
                ),
                hours = "Mon-Fri 8-17",
            ),
        )
        assertEquals("Clinique Saint-Roch · Léa's pediatrician", saved.label)
        assertEquals(setOf(ContactKind.CLINIC, ContactKind.FAMILY_DOCTOR), saved.kinds)
        val number = saved.numbers.single()
        assertEquals("•••• 8899", number.value, "shown masked")
        // Saved again unchanged, the full number stays.
        val again = books.contacts.save(saved.copy(purpose = "Pediatrician"))
        assertEquals("FILE-778899", books.contacts.revealNumber(again.id, number.id, "password1".toCharArray()))
        assertFailsWith<AccessDeniedException> { books.contacts.revealNumber(again.id, number.id, "wrong".toCharArray()) }
        // Removing a detail removes it.
        val fewer = books.contacts.save(again.copy(details = again.details.filter { it.type != DetailType.EMAIL }))
        assertEquals(2, fewer.details.size)
        assertFailsWith<ValidationException> { books.contacts.save(Contact("", group, " ")) }
    }

    @Test
    fun `a person belongs to an organization, and links show on both sides`() = household().use { books ->
        val group = books.groups().single().id
        val rbc = books.contacts.save(Contact("", group, "RBC Royal Bank", kinds = setOf(ContactKind.BANK), purpose = "Chequing and mortgage"))
        val jane = books.contacts.save(Contact("", group, "Jane Roy", person = true, organizationId = rbc.id, jobTitle = "Financial advisor", kinds = setOf(ContactKind.FINANCIAL_ADVISOR)))
        assertEquals(listOf(jane.id), books.contacts.people(rbc.id).map { it.id })

        val chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        books.contacts.link(rbc.id, LinkRole.BANK, LinkTarget.ACCOUNT, chequing.id)
        books.contacts.link(rbc.id, LinkRole.BANK, LinkTarget.ACCOUNT, chequing.id) // twice changes nothing
        books.contacts.link(jane.id, LinkRole.ADVISOR, LinkTarget.ACCOUNT, chequing.id)
        assertFailsWith<ValidationException> { books.contacts.link(rbc.id, LinkRole.PHARMACY, LinkTarget.ACCOUNT, chequing.id) }

        assertEquals(listOf("Chequing"), books.contacts.links(rbc.id).map { it.name })
        val onAccount = books.contacts.linkedTo(LinkTarget.ACCOUNT, chequing.id)
        assertEquals(setOf("RBC Royal Bank" to LinkRole.BANK, "Jane Roy" to LinkRole.ADVISOR), onAccount.map { it.contact.name to it.link.role }.toSet())

        books.contacts.unlink(onAccount.first { it.link.role == LinkRole.ADVISOR }.link)
        assertEquals(1, books.contacts.linkedTo(LinkTarget.ACCOUNT, chequing.id).size)
        books.contacts.delete(rbc.id)
        assertTrue(books.contacts.linkedTo(LinkTarget.ACCOUNT, chequing.id).isEmpty(), "links go with the contact")
        assertEquals(null, books.contacts.get(jane.id).organizationId)
        assertEquals("Chequing", books.accounts.get(chequing.id).name, "the record stays")
    }

    @Test
    fun `filters by kind, person served, link type and text with accents ignored`() = household().use { books ->
        val group = books.groups().single().id
        val lea = books.members.create("Léa", MemberKind.CHILD).id
        val rbc = books.contacts.save(Contact("", group, "RBC", kinds = setOf(ContactKind.BANK)))
        books.contacts.save(Contact("", group, "Desjardins", kinds = setOf(ContactKind.CREDIT_UNION, ContactKind.INSURER)))
        books.contacts.save(Contact("", group, "Dre Gagnon", person = true, kinds = setOf(ContactKind.FAMILY_DOCTOR), purpose = "Médecin de famille", memberIds = setOf(lea)))
        books.contacts.save(Contact("", group, "Old bank", kinds = setOf(ContactKind.BANK), archived = true))
        val chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        books.contacts.link(rbc.id, LinkRole.BANK, LinkTarget.ACCOUNT, chequing.id)

        assertEquals(listOf("RBC"), books.contacts.list(ContactFilter(kind = ContactKind.BANK)).map { it.name })
        assertEquals(listOf("Old bank", "RBC"), books.contacts.list(ContactFilter(kind = ContactKind.BANK, includeArchived = true)).map { it.name })
        assertEquals(listOf("Dre Gagnon"), books.contacts.list(ContactFilter(memberId = lea)).map { it.name })
        assertEquals(listOf("RBC"), books.contacts.list(ContactFilter(target = LinkTarget.ACCOUNT)).map { it.name })
        assertEquals(listOf("Dre Gagnon"), books.contacts.list(ContactFilter(text = "medecin")).map { it.name })
        assertEquals(listOf("Dre Gagnon"), books.search.search("médecin").contacts.map { it.name }, "global search finds contacts")
    }

    @Test
    fun `a contact kept in a private group is not seen by another user, and viewers cannot change contacts`() {
        household().use { books ->
            val shared = books.groups().single().id
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            val vic = books.users.add("vic", "Vic", Role.VIEWER, "password3-long".toCharArray()).userId
            books.session.setPermission(shared, marie, PermissionLevel.EDIT)
            books.session.setPermission(shared, vic, PermissionLevel.VIEW)
            books.contacts.save(Contact("", shared, "Hydro Ottawa", kinds = setOf(ContactKind.UTILITY)))
        }
        Books(store.unlock(dir, "marie", "password2-long".toCharArray())).use { marie ->
            val own = marie.session.createGroup("Marie - privé", private = true)
            marie.contacts.save(Contact("", own, "Dr. Private", kinds = setOf(ContactKind.SPECIALIST)))
            assertEquals(listOf("Dr. Private", "Hydro Ottawa"), marie.contacts.list().map { it.name })
        }
        Books(store.unlock(dir, "perry", "password1".toCharArray())).use { perry ->
            assertEquals(listOf("Hydro Ottawa"), perry.contacts.list().map { it.name }, "Marie's private contact stays hers")
            assertTrue(perry.search.search("Private").contacts.isEmpty())
        }
        Books(store.unlock(dir, "vic", "password3-long".toCharArray())).use { vic ->
            val hydro = vic.contacts.list().single()
            assertFailsWith<AccessDeniedException> { vic.contacts.save(hydro.copy(purpose = "Electricity")) }
        }
    }

    @Test
    fun `a contact is linked to a whole contractor or to one of its jobs, and job links go with the job`() = household().use { books ->
        val group = books.groups().single().id
        val laval = books.contractors.save(Contractor("", group, "Toitures Laval", trade = "Roofer"))
        val roof = books.contractors.saveJob(laval, ContractorJob("", LocalDate(2026, 5, 4), "Roof repair", cad("1200")))
        val gutters = books.contractors.saveJob(laval, ContractorJob("", LocalDate(2026, 6, 1), "Gutters"))
        val office = books.contacts.save(Contact("", group, "Toitures Laval", kinds = setOf(ContactKind.CONTRACTOR)))
        val marc = books.contacts.save(Contact("", group, "Marc Roy", person = true, organizationId = office.id))

        books.contacts.link(office.id, LinkRole.SAME_AS, LinkTarget.CONTRACTOR, laval.id)
        books.contacts.link(marc.id, LinkRole.DONE_BY, LinkTarget.CONTRACTOR_JOB, roof.id)
        books.contacts.link(marc.id, LinkRole.OTHER, LinkTarget.CONTRACTOR_JOB, gutters.id)
        assertEquals(listOf(LinkRole.DONE_BY, LinkRole.OTHER), LinkRole.forTarget(LinkTarget.CONTRACTOR_JOB))
        assertFailsWith<ValidationException> { books.contacts.link(marc.id, LinkRole.DONE_BY, LinkTarget.CONTRACTOR, laval.id) }

        // On the job: only its own link; the contractor's contact stays on the contractor.
        assertEquals(listOf("Marc Roy" to LinkRole.DONE_BY), books.contacts.linkedTo(LinkTarget.CONTRACTOR_JOB, roof.id).map { it.contact.name to it.link.role })
        assertEquals(listOf("Toitures Laval"), books.contacts.linkedTo(LinkTarget.CONTRACTOR, laval.id).map { it.contact.name })
        // On the contact's page: the job with its description, date, cost and contractor.
        val onPage = books.contacts.links(marc.id).first { it.link.role == LinkRole.DONE_BY }
        assertEquals(LinkTarget.CONTRACTOR_JOB, onPage.link.target)
        assertTrue(onPage.name.startsWith("Roof repair · 2026-05-04 · ") && "1,200.00" in onPage.name && onPage.name.endsWith(" · Toitures Laval"), onPage.name)
        assertTrue(books.contacts.candidates(LinkTarget.CONTRACTOR_JOB).any { it.id == gutters.id && it.name == "Gutters · 2026-06-01 · Toitures Laval" })
        assertEquals(listOf("Marc Roy"), books.contacts.list(ContactFilter(target = LinkTarget.CONTRACTOR_JOB)).map { it.name })

        // Deleting a job removes its links, not the contact.
        books.contractors.deleteJob(laval, roof.id)
        assertEquals(listOf(gutters.id), books.contacts.allLinks().filter { it.target == LinkTarget.CONTRACTOR_JOB }.map { it.targetId })
        assertEquals("Marc Roy", books.contacts.get(marc.id).name)
        // Deleting the contractor removes the links to it and to its jobs.
        books.contractors.delete(books.contractors.list().single())
        assertTrue(books.contacts.allLinks().isEmpty())
        assertEquals(2, books.contacts.list().size)
    }

    @Test
    fun `a job kept in a private group and its links stay out of other users' sight`() {
        household().use { books ->
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(books.groups().single().id, marie, PermissionLevel.EDIT)
        }
        Books(store.unlock(dir, "marie", "password2-long".toCharArray())).use { marie ->
            val shared = marie.groups().first { !it.isPrivate }.id
            val own = marie.session.createGroup("Marie - privé", private = true)
            val c = marie.contractors.save(Contractor("", own, "Dre Dentiste rénos"))
            val job = marie.contractors.saveJob(c, ContractorJob("", LocalDate(2026, 3, 2), "Private renovation"))
            val plumber = marie.contacts.save(Contact("", shared, "Plomberie Roy"))
            marie.contacts.link(plumber.id, LinkRole.DONE_BY, LinkTarget.CONTRACTOR_JOB, job.id)
            assertEquals(1, marie.contacts.links(plumber.id).size)
        }
        Books(store.unlock(dir, "perry", "password1".toCharArray())).use { perry ->
            val plumber = perry.contacts.list().single()
            assertTrue(perry.contacts.links(plumber.id).isEmpty(), "the private job is not shown on the shared contact")
            assertTrue(perry.contacts.candidates(LinkTarget.CONTRACTOR_JOB).isEmpty())
        }
    }

    @Test
    fun `existing records are gathered into contacts, with duplicates merged only when chosen`() = household().use { books ->
        val group = books.groups().single().id
        val sam = books.members.create("Sam", MemberKind.ADULT).id
        val desj = books.institutions.create(Institution("", "Desjardins", phone = "1 800 224-7737", website = "desjardins.com"))
        val tfsa = books.accounts.create(AccountDraft(group, "CELI", AccountType.TFSA, Currency.CAD, cad("0"), LocalDate(2026, 1, 1), institutionId = desj.id))
        val home = books.insurance.save(InsurancePolicy("", group, PolicyKind.HOME, "Desjardins Assurances"))
        val auto = books.insurance.save(InsurancePolicy("", group, PolicyKind.AUTO, "DÉSJARDINS", broker = "Courtier Roy"))
        val pharmacy = books.health.saveProvider(HealthProvider("", group, "Pharmacie Jean Coutu", ProviderKind.PHARMACY, "418 555-0199", null, null, false))
        val doctor = books.health.saveProvider(HealthProvider("", group, "Dre Gagnon", ProviderKind.DOCTOR, "(418) 555-0101", null, null, false))
        val med = books.health.saveMedication(Medication("", group, sam, "Ventolin", null, null, doctor.id, pharmacy.id, null, null, null, null, null, null, 5, true, null))
        books.contractors.save(Contractor("", group, "Plomberie Roy", trade = "Plumber", phone = "418-555-0101"))
        books.estate.save(sam, group, EstatePlan(contacts = listOf(EstateContact(ContactRole.NOTARY, "Me Tremblay", phone = "418 555-0300"))))

        val proposals = books.contacts.proposals()
        // "Desjardins" (institution) and "DÉSJARDINS" (auto insurer) look alike; "Desjardins Assurances" does not.
        val desjardins = proposals.single { p -> p.sources.any { it.target == LinkTarget.INSTITUTION } }
        assertEquals(2, desjardins.sources.size)
        assertTrue(desjardins.isDuplicate)
        // The doctor and the plumber share a phone number: proposed together, merged only if chosen.
        val samePhone = proposals.single { p -> p.sources.any { it.name == "Dre Gagnon" } }
        assertEquals(setOf("Dre Gagnon", "Plomberie Roy"), samePhone.sources.map { it.name }.toSet())

        // The user merges the two Desjardins, and keeps the doctor and plumber apart.
        val decisions = proposals.flatMap { p ->
            if (p === desjardins) listOf(GatherDecision(p.sources)) else p.sources.map { GatherDecision(listOf(it)) }
        }
        books.contacts.gather(group, decisions)

        val all = books.contacts.list()
        assertEquals(1, all.count { SearchService.fold(it.name) == "desjardins" }, "merged into one")
        val d = all.single { it.name == "Desjardins" }
        assertEquals(setOf(ContactKind.BANK, ContactKind.INSURER), d.kinds)
        assertEquals("1 800 224-7737", d.phones.single().value)
        val names = books.contacts.links(d.id).map { it.link.role to it.link.targetId }.toSet()
        assertTrue(LinkRole.SAME_AS to desj.id in names)
        assertTrue(LinkRole.INVESTMENT_FIRM to tfsa.id in names, "the TFSA at Desjardins")
        assertTrue(LinkRole.INSURER to auto.id in names)
        assertEquals(listOf("Desjardins Assurances"), books.contacts.linkedTo(LinkTarget.POLICY, home.id).map { it.contact.name })
        assertEquals(listOf("Courtier Roy"), books.contacts.linkedTo(LinkTarget.POLICY, auto.id).filter { it.link.role == LinkRole.BROKER }.map { it.contact.name })
        assertEquals(listOf("Pharmacie Jean Coutu"), books.contacts.linkedTo(LinkTarget.MEDICATION, med.id).filter { it.link.role == LinkRole.PHARMACY }.map { it.contact.name })
        val gagnon = all.single { it.name == "Dre Gagnon" }
        assertEquals(setOf(sam), gagnon.memberIds, "the doctor serves Sam, who takes the medication she prescribed")
        assertEquals(setOf(ContactKind.CONTRACTOR), all.single { it.name == "Plomberie Roy" }.kinds)
        assertEquals(listOf("Plumber"), listOfNotNull(all.single { it.name == "Plomberie Roy" }.purpose))
        assertEquals(listOf(LinkRole.ESTATE_NOTARY), books.contacts.linkedTo(LinkTarget.ESTATE, sam).map { it.link.role })

        // Everything was gathered: nothing more is offered, and the records are unchanged.
        assertTrue(books.contacts.proposals().isEmpty())
        assertEquals("Desjardins Assurances", books.insurance.policies().first { it.id == home.id }.insurer)
        assertEquals(2, books.health.providers().size)
    }

    @Test
    fun `a gathered record can be added to an existing contact`() = household().use { books ->
        val group = books.groups().single().id
        val existing = books.contacts.save(Contact("", group, "Plomberie Roy", kinds = setOf(ContactKind.CONTRACTOR)))
        val c = books.contractors.save(Contractor("", group, "plomberie roy", phone = "418-555-0101", email = "roy@example.com"))
        val p = books.contacts.proposals().single()
        assertEquals(listOf(existing.id), p.existing.map { it.id })
        books.contacts.gather(group, listOf(GatherDecision(p.sources, intoContactId = existing.id)))
        val merged = books.contacts.list().single()
        assertEquals(listOf("418-555-0101"), merged.phones.map { it.value })
        assertEquals(listOf("roy@example.com"), merged.emails.map { it.value })
        assertEquals(listOf(c.id), books.contacts.links(merged.id).map { it.link.targetId })
    }

    @Test
    fun `a record kept in a private group is gathered into that group, out of other users' sight`() {
        household().use { books ->
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(books.groups().single().id, marie, PermissionLevel.EDIT)
        }
        Books(store.unlock(dir, "marie", "password2-long".toCharArray())).use { marie ->
            val own = marie.session.createGroup("Marie - privé", private = true)
            marie.health.saveProvider(HealthProvider("", own, "Dre Psy", ProviderKind.SPECIALIST, "418 555-0111", null, null, false))
            val p = marie.contacts.proposals().single()
            assertEquals(own, p.sources.single().privateGroupId)
            val shared = marie.groups().first { !it.isPrivate }.id
            val made = marie.contacts.gather(shared, listOf(GatherDecision(p.sources))).single()
            assertEquals(own, made.groupId, "it stays in the private group, whatever group was chosen")
        }
        Books(store.unlock(dir, "perry", "password1".toCharArray())).use { perry ->
            assertTrue(perry.contacts.proposals().isEmpty())
            assertTrue(perry.contacts.list().isEmpty())
        }
    }
}
