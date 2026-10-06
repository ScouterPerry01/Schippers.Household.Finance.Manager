package ca.schippers.hfm.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Section 3.1: what a shared transfer file shows outside its encryption. */
class BundleFileTest {

    @Test
    fun `the email subject id is the start of the household's and the phone's ids, as in the file name`() {
        val desktopId = "5C1E0A9B-77D2-4C1A-9F00-2B3C4D5E6F70"
        val deviceId = "3f9a1c2e-0b7d-4e55-8a10-aa11bb22cc33"
        val id = BundleFile.subjectId(desktopId, deviceId)
        assertEquals("5c1e0a-3f9a1c2e", id)
        val name = BundleFile.name(BundleFile.Header(desktopId, deviceId, Direction.TO_DESKTOP, 0))
        assertTrue(name.startsWith("to-desktop-" + id.substringAfter('-') + "-"), name)
        assertEquals("[RANN's Roost]", BundleFile.SUBJECT_TAG)
    }
}
