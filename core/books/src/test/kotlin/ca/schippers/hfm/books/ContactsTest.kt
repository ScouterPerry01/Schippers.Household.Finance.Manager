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

    @Test
    fun `merging two contacts keeps the fields chosen and combines details, links and people without duplicates`() = household().use { books ->
        val group = books.groups().single().id
        val lea = books.members.create("Léa", MemberKind.CHILD).id
        val rbc = books.contacts.save(
            Contact(
                "", group, "RBC Royal Bank", kinds = setOf(ContactKind.BANK), purpose = "Chequing", address = "1 Main St",
                details = listOf(
                    ContactDetail(type = DetailType.PHONE, value = "613 555-0101"),
                    ContactDetail(type = DetailType.EMAIL, value = "info@rbc.example"),
                    ContactDetail(type = DetailType.NUMBER, label = "Client", value = "ACC-12345678"),
                ),
            ),
        )
        val other = books.contacts.save(
            Contact(
                "", group, "RBC", kinds = setOf(ContactKind.INSURER), memberIds = setOf(lea), website = "rbc.example", hours = "9-5", address = "2 Bank St",
                details = listOf(
                    ContactDetail(type = DetailType.PHONE, label = "Office", value = "+1 (613) 555-0101"),
                    ContactDetail(type = DetailType.PHONE, label = "Cell", value = "613 555-0202"),
                    ContactDetail(type = DetailType.EMAIL, value = "INFO@rbc.example"),
                    ContactDetail(type = DetailType.NUMBER, value = "acc 12345678"),
                ),
            ),
        )
        books.contacts.save(Contact("", group, "Hydro Ottawa", kinds = setOf(ContactKind.UTILITY)))
        val jane = books.contacts.save(Contact("", group, "Jane Roy", person = true, organizationId = other.id, jobTitle = "Advisor"))
        val chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        val savings = books.accounts.create(AccountDraft(group, "Savings", AccountType.SAVINGS, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        books.contacts.link(rbc.id, LinkRole.BANK, LinkTarget.ACCOUNT, chequing.id)
        books.contacts.link(other.id, LinkRole.BANK, LinkTarget.ACCOUNT, chequing.id)
        books.contacts.link(other.id, LinkRole.BANK, LinkTarget.ACCOUNT, savings.id)

        // The same phone makes RBC a likely duplicate, listed first.
        val candidates = books.contacts.mergeCandidates(rbc.id)
        assertEquals(listOf("RBC" to true, "Hydro Ottawa" to false, "Jane Roy" to false), candidates.map { it.contact.name to it.likelySame })

        // Suggested: the kept contact's values, the other's where only it has one.
        val suggested = MergeChoices.suggested(rbc, other)
        assertEquals(setOf(MergeField.WEBSITE, MergeField.HOURS), suggested.fromOther)
        val choices = MergeChoices(suggested.fromOther + MergeField.NAME)
        val preview = books.contacts.mergePreview(rbc.id, other.id, choices)
        assertEquals(listOf("Jane Roy"), preview.people.map { it.name })
        assertEquals(false, preview.exposesPrivate)
        assertEquals(setOf("Chequing", "Savings"), preview.links.map { it.name }.toSet())
        assertEquals(rbc.id, books.contacts.get(rbc.id).id, "a preview changes nothing")
        assertEquals(2, books.contacts.links(other.id).size)

        val merged = books.contacts.merge(rbc.id, other.id, choices)
        assertEquals(rbc.id, merged.id)
        assertEquals("RBC", merged.name)
        assertEquals("Chequing", merged.purpose)
        assertEquals("1 Main St", merged.address, "the kept contact's address")
        assertEquals("rbc.example", merged.website)
        assertEquals("9-5", merged.hours)
        assertEquals(setOf(ContactKind.BANK, ContactKind.INSURER), merged.kinds)
        assertEquals(setOf(lea), merged.memberIds)
        assertEquals(listOf("613 555-0101" to "Office", "613 555-0202" to "Cell"), merged.phones.map { it.value to it.label }, "the same phone once, its label kept")
        assertEquals(listOf("info@rbc.example"), merged.emails.map { it.value })
        val number = merged.numbers.single()
        assertEquals("•••• 5678", number.value)
        assertEquals("ACC-12345678", books.contacts.revealNumber(merged.id, number.id, "password1".toCharArray()))
        assertEquals(setOf(chequing.id, savings.id), books.contacts.links(merged.id).map { it.link.targetId }.toSet())
        assertEquals(1, books.contacts.linkedTo(LinkTarget.ACCOUNT, chequing.id).size, "the same link once")
        assertEquals(listOf(jane.id), books.contacts.people(merged.id).map { it.id })
        assertFailsWith<AccessDeniedException> { books.contacts.get(other.id) }
        assertEquals(listOf("Hydro Ottawa", "Jane Roy", "RBC"), books.contacts.list().map { it.name })
        assertTrue(books.users.activity().any { it.action == "MERGE" && it.entity == "contact" && other.id in it.details.orEmpty() && rbc.id in it.details.orEmpty() })
        assertFailsWith<ValidationException> { books.contacts.merge(rbc.id, rbc.id, MergeChoices()) }
    }

    @Test
    fun `a person merged with the organization it works at no longer belongs to itself`() = household().use { books ->
        val group = books.groups().single().id
        val org = books.contacts.save(Contact("", group, "Clinique Roy"))
        val dr = books.contacts.save(Contact("", group, "Dr Roy", person = true, organizationId = org.id, jobTitle = "Doctor"))
        val merged = books.contacts.merge(dr.id, org.id, MergeChoices())
        assertEquals(null, merged.organizationId)
        assertEquals("Doctor", merged.jobTitle)
        assertEquals(listOf("Dr Roy"), books.contacts.list().map { it.name })
    }

    @Test
    fun `merging across groups needs edit rights on both, moves what the other holds, and flags a private contact made visible`() {
        val ids = HashMap<String, String>()
        household().use { books ->
            val shared = books.groups().single().id
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
            val vic = books.users.add("vic", "Vic", Role.VIEWER, "password3-long".toCharArray()).userId
            books.session.setPermission(shared, marie, PermissionLevel.EDIT)
            books.session.setPermission(shared, vic, PermissionLevel.VIEW)
            ids["shared"] = shared
            ids["lisa"] = books.contacts.save(Contact("", shared, "Dre Lisa Chen", kinds = setOf(ContactKind.FAMILY_DOCTOR))).id
            ids["hydro"] = books.contacts.save(Contact("", shared, "Hydro Ottawa")).id
            ids["hydro2"] = books.contacts.save(Contact("", shared, "Hydro Ottawa", purpose = "Cottage")).id
            ids["dentist"] = books.contacts.save(Contact("", shared, "Dr. Smile", kinds = setOf(ContactKind.DENTIST))).id
            ids["account"] = books.accounts.create(AccountDraft(shared, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("0"), LocalDate(2026, 1, 1))).id
        }
        Books(store.unlock(dir, "vic", "password3-long".toCharArray())).use { vic ->
            assertFailsWith<AccessDeniedException> { vic.contacts.merge(ids.getValue("hydro"), ids.getValue("hydro2"), MergeChoices()) }
            assertTrue(vic.contacts.mergeCandidates(ids.getValue("hydro")).isEmpty(), "a viewer is offered nothing to merge")
        }
        Books(store.unlock(dir, "marie", "password2-long".toCharArray())).use { marie ->
            val own = marie.session.createGroup("Marie - privé", private = true)
            ids["own"] = own
            val private = marie.contacts.save(
                Contact(
                    "", own, "Dr. Lisa Chen", purpose = "My doctor", notes = "Private notes",
                    details = listOf(ContactDetail(type = DetailType.NUMBER, label = "File", value = "FILE-998877")),
                ),
            )
            marie.contacts.link(private.id, LinkRole.OTHER, LinkTarget.ACCOUNT, ids.getValue("account"))
            ids["private"] = private.id
            ids["privateDentist"] = marie.contacts.save(Contact("", own, "Dr Smile", notes = "Mine")).id
            val shared = ids.getValue("lisa")
            // Kept in the shared group, the private contact's details become visible there: flagged.
            assertTrue(marie.contacts.mergePreview(shared, private.id, MergeChoices()).exposesPrivate)
            assertEquals(false, marie.contacts.mergePreview(shared, private.id, MergeChoices(setOf(MergeField.GROUP))).exposesPrivate)
            val merged = marie.contacts.merge(shared, private.id, MergeChoices(setOf(MergeField.WHAT_FOR, MergeField.NOTES)))
            assertEquals(ids.getValue("shared"), merged.groupId)
            assertEquals("My doctor", merged.purpose)
            assertEquals("Dre Lisa Chen", merged.name)
            assertEquals("FILE-998877", marie.contacts.revealNumber(merged.id, merged.numbers.single().id, "password2-long".toCharArray()), "the full number moved")
            assertEquals(listOf(ids.getValue("account")), marie.contacts.links(merged.id).map { it.link.targetId })
            assertFailsWith<AccessDeniedException> { marie.contacts.get(private.id) }

            // Kept in the private group: the shared contact moves there, out of the shared group.
            val moved = marie.contacts.merge(ids.getValue("dentist"), ids.getValue("privateDentist"), MergeChoices(setOf(MergeField.GROUP, MergeField.NOTES)))
            assertEquals(own, moved.groupId)
            assertEquals(ids.getValue("dentist"), moved.id)
            assertEquals(setOf(ContactKind.DENTIST), moved.kinds)
            assertEquals("Mine", moved.notes)
            assertEquals(1, marie.contacts.list().count { it.name.startsWith("Dr") && it.name.endsWith("Smile") })
        }
        Books(store.unlock(dir, "perry", "password1".toCharArray())).use { perry ->
            val lisa = perry.contacts.get(ids.getValue("lisa"))
            assertEquals("My doctor", lisa.purpose, "now shared, as the warning said")
            assertEquals("•••• 8877", lisa.numbers.single().value)
            assertTrue(perry.contacts.list().none { it.name.endsWith("Smile") }, "the dentist went to Marie's private group")
            assertEquals(listOf("Dre Lisa Chen", "Hydro Ottawa", "Hydro Ottawa"), perry.contacts.list().map { it.name })
        }
    }
}
