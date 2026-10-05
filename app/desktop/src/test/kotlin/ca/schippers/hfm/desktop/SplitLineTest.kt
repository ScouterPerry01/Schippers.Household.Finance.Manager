package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.SplitDraft
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/** TX-02: saving a split again keeps what the split window does not show. */
class SplitLineTest {

    @Test
    fun `a split line keeps its person and tax flag`() {
        val line = SplitLine("cat-pharmacy", "Drops", "12.50", memberId = "member-sam", taxFlag = TaxFlag.MEDICAL)
        line.memo = "Eye drops"
        assertEquals(
            SplitDraft("cat-pharmacy", Money.parse("-12.50", Currency.CAD), "Eye drops", "member-sam", TaxFlag.MEDICAL),
            line.toDraft(Money.parse("12.50", Currency.CAD), negative = true),
        )
        assertEquals(null, SplitLine(null, " ", "5").toDraft(Money.parse("5", Currency.CAD), negative = false).memo)
    }
}
