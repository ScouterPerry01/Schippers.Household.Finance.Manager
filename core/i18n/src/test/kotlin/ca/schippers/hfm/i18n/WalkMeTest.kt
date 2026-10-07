package ca.schippers.hfm.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HLP-03: the Walk-Me guide format is read as docs/walkme-format.md describes it. */
class WalkMeTest {

    private val sample = """
        # Pay a bill in part
        @about: Pay part of a bill now.

        ## Open To pay {#open}
        @screen: BILLS BillsTab.AGENDA
        @done: screen
        @manual: bills#to-pay-tab

        Open **Bills**, then the **To pay** tab.

        ## Record it {#record}
        @target: bills.pay.save
        @done: added bill

        - **Date paid**: today.
        - Click **Save**.

        ## Done {#done}

        Nothing else.
    """.trimIndent()

    @Test
    fun `a guide's steps, directions and text are read`() {
        val g = WalkMe.parse("pay-part", sample)
        assertEquals("Pay a bill in part", g.title)
        assertEquals("Pay part of a bill now.", g.about)
        assertEquals(listOf("open", "record", "done"), g.steps.map { it.id })
        val open = g.steps[0]
        assertEquals("BILLS", open.screen)
        assertEquals("BillsTab.AGENDA", open.tab)
        assertEquals(WalkMe.Condition("screen", null), open.done)
        assertEquals("bills#to-pay-tab", open.manual)
        assertNull(open.target)
        val record = g.steps[1]
        assertEquals("bills.pay.save", record.target)
        assertEquals(WalkMe.Condition("added", "bill"), record.done)
        assertNull(record.screen)
        assertEquals(2, record.blocks.size)
        assertEquals(listOf("Date paid", "Save"), WalkMe.boldLabels(record.text))
        val done = g.steps[2]
        assertNull(done.done)
        assertTrue(done.blocks.single() is Manual.Block.Paragraph)
    }

    @Test
    fun `a step without its id is refused`() {
        assertFailsWith<IllegalStateException> { WalkMe.parse("x", "# X\n\n## A step\n\nText.") }
    }

    @Test
    fun `the guides are grouped in both languages`() {
        for (language in Language.entries) {
            val groups = WalkMe.groups(language)
            assertEquals(listOf("start", "bills", "documents", "borrowing", "home", "taxes"), groups.map { it.id })
            assertTrue(groups.all { g -> g.guides.isNotEmpty() && g.title.isNotBlank() })
            assertTrue(WalkMe.guide(language, "household")!!.steps.first().screen == "WELCOME")
        }
    }
}
