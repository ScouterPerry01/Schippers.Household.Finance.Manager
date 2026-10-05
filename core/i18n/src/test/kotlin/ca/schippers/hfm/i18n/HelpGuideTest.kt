package ca.schippers.hfm.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/** NFR-12: the guide's small Markdown subset. */
class HelpGuideTest {

    @Test
    fun `titles, subheadings, bullets and paragraphs are read`() {
        val t = HelpGuide.parse("bills", "# Bills\n\nRecurring bills and pay,\nwith reminders.\n\n## Add a bill\n- Click Add a bill\n- Choose how often\n")
        assertEquals("Bills", t.title)
        assertEquals(
            listOf(
                HelpGuide.Block.Paragraph("Recurring bills and pay, with reminders."),
                HelpGuide.Block.Heading("Add a bill"),
                HelpGuide.Block.Bullet("Click Add a bill"),
                HelpGuide.Block.Bullet("Choose how often"),
            ),
            t.blocks,
        )
    }
}
