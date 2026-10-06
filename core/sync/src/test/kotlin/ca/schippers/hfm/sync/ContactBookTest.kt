package ca.schippers.hfm.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CON-07: contacts on the phone, their search and links, and the protocol's compatibility. */
class ContactBookTest {

    private val bank = RefContact("rbc", "RBC Royal Bank", kinds = listOf("BANK"), purpose = "Chequing and mortgage", phones = listOf(RefContactDetail("1 800 769-2511", "Branch")))
    private val jane = RefContact("jane", "Jane Roy", person = true, organizationId = "rbc", organizationName = "RBC Royal Bank", jobTitle = "Advisor", kinds = listOf("FINANCIAL_ADVISOR"))
    private val doctor = RefContact("doc", "Dre Émilie Gagnon", person = true, kinds = listOf("FAMILY_DOCTOR"), purpose = "Médecin de famille", forWhom = listOf("Léa"))
    private val lone = RefContact("lone", "Sam Lee", person = true, organizationId = "elsewhere", organizationName = "Desjardins")

    @Test
    fun `people are listed under their organization, the rest by name`() {
        val rows = ContactBook.rows(listOf(lone, jane, doctor, bank))
        assertEquals(listOf("doc" to 0, "rbc" to 0, "jane" to 1, "lone" to 0), rows.map { it.first.id to it.second })
    }

    @Test
    fun `search ignores accents and case, and matches phones by their digits`() {
        val all = listOf(bank, jane, doctor, lone)
        assertEquals(listOf("doc"), ContactBook.filter(all, "medecin", null).map { it.id })
        assertEquals(listOf("doc"), ContactBook.filter(all, "EMILIE", null).map { it.id })
        assertEquals(listOf("doc"), ContactBook.filter(all, "lea", null).map { it.id }, "for whom")
        assertEquals(listOf("rbc", "jane"), ContactBook.filter(all, "royal", null).map { it.id }, "a person by their organization")
        assertEquals(listOf("rbc"), ContactBook.filter(all, "18007692511", null).map { it.id })
        assertEquals(listOf("rbc"), ContactBook.filter(all, "769-2511", null).map { it.id })
        assertEquals(listOf("jane"), ContactBook.filter(all, "", "FINANCIAL_ADVISOR").map { it.id })
        assertEquals(listOf("jane"), ContactBook.filter(all, "roy", "FINANCIAL_ADVISOR").map { it.id })
        assertEquals(listOf("BANK", "FINANCIAL_ADVISOR", "FAMILY_DOCTOR"), ContactBook.kinds(all, listOf("BANK", "FINANCIAL_ADVISOR", "FAMILY_DOCTOR", "DENTIST")))
    }

    @Test
    fun `links dial, write, open a map and a website`() {
        assertEquals("tel:+16135550101,22", ContactBook.telUri("+1 (613) 555-0101,22"))
        assertEquals("mailto:info@example.ca", ContactBook.mailtoUri(" info@example.ca "))
        assertEquals("geo:0,0?q=12%20rue%20Saint-Jean%20Qu%C3%A9bec", ContactBook.geoUri("12 rue Saint-Jean\nQuébec"))
        assertEquals("https://rbc.com", ContactBook.webUri("rbc.com"))
        assertEquals("http://example.ca", ContactBook.webUri("http://example.ca"))
        assertEquals("https://ca.ranns.roost.mobile/queue", ContactBook.webUri("content://ca.ranns.roost.mobile/queue"))
        assertEquals("https://intent:#Intent;end", ContactBook.webUri("intent:#Intent;end"))
    }

    @Test
    fun `older phones and desktops still read each other`() {
        // An older desktop's reference data, without contacts, reads as before.
        val old = """{"householdName":"H","language":"en","baseCurrency":"CAD","accounts":[],"generatedAtMillis":5}"""
        val ref = SyncJson.decodeFromString(ReferenceData.serializer(), old)
        assertTrue(ref.contacts.isEmpty())
        // A newer desktop's data, read by an app that does not know contacts (unknown fields are skipped).
        val withContacts = SyncJson.encodeToString(ReferenceData.serializer(), ref.copy(contacts = listOf(bank)))
        assertTrue("\"contacts\"" in withContacts)
        assertEquals(ref, SyncJson.decodeFromString(ReferenceData.serializer(), withContacts).copy(contacts = emptyList()))
        // A request without contacts is written exactly as before; one with contacts keeps its items readable.
        val request = SyncRequest(1, emptyList(), "v1")
        assertEquals("""{"sentAtMillis":1,"items":[],"referenceVersion":"v1"}""", SyncJson.encodeToString(SyncRequest.serializer(), request))
        val withContact = SyncRequest(1, emptyList(), "v1", listOf(PhoneContact("p1", 1, "Plomberie Roy", phones = listOf(RefContactDetail("418 555-0199")))))
        val read = SyncJson.decodeFromString(SyncRequest.serializer(), SyncJson.encodeToString(SyncRequest.serializer(), withContact))
        assertEquals("Plomberie Roy", read.contacts.single().name)
        // A copy kept by an app reading an older format asks for everything again.
        assertNull(ReferenceData.knownVersion("abc", storedFormat = 1))
        assertEquals("abc", ReferenceData.knownVersion("abc", storedFormat = ReferenceData.FORMAT))
        assertNull(ReferenceData.knownVersion(null, storedFormat = ReferenceData.FORMAT))
    }
}
