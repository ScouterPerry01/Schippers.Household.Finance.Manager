package ca.schippers.hfm.importers

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QifParserTest {

    /** A small Quicken export: an account list, categories, a split, a transfer and an investment account. */
    private val quicken = """
        !Option:AutoSwitch
        !Account
        NCompte chèques
        TBank
        ^
        NVisa
        TCCard
        L5000.00
        ^
        NREER
        TInvst
        ^
        !Clear:AutoSwitch
        !Type:Cat
        NAlimentation
        E
        ^
        NAlimentation:Épicerie
        E
        ^
        NSalaire
        I
        T
        ^
        !Account
        NCompte chèques
        TBank
        ^
        !Type:Bank
        D1/ 2'24
        T1,000.00
        CX
        POpening Balance
        L[Compte chèques]
        ^
        D1/15'24
        T-187.32
        C*
        PIGA
        LAlimentation:Épicerie/Vacances
        ^
        D1/20'24
        T-500.00
        PVisa payment
        L[Visa]
        ^
        D1/31'24
        T2,450.00
        PEmployeur inc.
        SSalaire
        ${'$'}2,600.00
        SImpôts:Fédéral
        ERetenue
        ${'$'}-150.00
        ^
        !Account
        NREER
        TInvst
        ^
        !Type:Invst
        D2/1'24
        NBuy
        YFonds indiciel canadien
        I25.50
        Q10
        T255.00
        O9.99
        ^
    """.trimIndent()

    @Test
    fun `accounts, categories, transactions, splits, transfers and investments`() {
        val f = QifParser.parse(quicken)
        assertEquals(listOf("Compte chèques" to QifAccountKind.BANK, "Visa" to QifAccountKind.CREDIT_CARD, "REER" to QifAccountKind.INVESTMENT), f.accounts.map { it.name to it.kind })
        assertEquals(BigDecimal("5000.00"), f.accounts[1].creditLimit)
        assertEquals(listOf("Alimentation", "Épicerie"), f.categories[1].path)
        assertEquals(true, f.categories[2].income)

        val txns = f.transactions
        assertEquals(4, txns.size)
        assertEquals("Compte chèques", txns.first().transferAccount, "opening balance points at its own account")
        val iga = txns[1]
        assertEquals(BigDecimal("-187.32"), iga.amount)
        assertEquals(listOf("Alimentation", "Épicerie"), iga.category)
        assertEquals(listOf("Vacances"), iga.classes)
        assertEquals('*', iga.cleared)
        assertEquals("Visa", txns[2].transferAccount)
        assertEquals(listOf(BigDecimal("2600.00"), BigDecimal("-150.00")), txns[3].splits.map { it.amount })
        assertEquals("Retenue", txns[3].splits[1].memo)

        val buy = f.investments.single()
        assertEquals("Buy", buy.action)
        assertEquals("REER", buy.account)
        assertEquals(BigDecimal("10"), buy.quantity)
        assertEquals(DateOrder.MONTH_DAY, f.dateOrder, "day 15 and 20 can only be days")
        assertEquals(LocalDate(2024, 1, 15), iga.date.toLocalDate(f.dateOrder!!))
    }

    @Test
    fun `dates and amounts in their many spellings`() {
        assertEquals(LocalDate(1999, 1, 5), QifParser.date(" 1/ 5'99")!!.toLocalDate(DateOrder.MONTH_DAY))
        assertEquals(LocalDate(2023, 12, 31), QifParser.date("31/12/2023")!!.toLocalDate(DateOrder.DAY_MONTH))
        assertEquals(LocalDate(2023, 12, 31), QifParser.date("2023-12-31")!!.toLocalDate(DateOrder.DAY_MONTH))
        assertNull(QifParser.date("yesterday"))
        assertEquals(BigDecimal("1234.56"), QifParser.amount("1,234.56"))
        assertEquals(BigDecimal("-1234.56"), QifParser.amount("-1 234,56"))
        assertEquals(BigDecimal("-12.30"), QifParser.amount("(12.30)"))
        assertEquals(BigDecimal("1000"), QifParser.amount("1,000"))
    }

    @Test
    fun `an ambiguous file asks for the date order, and old Quicken files are Windows-1252`() {
        val f = QifParser.parse("!Type:Bank\nD03/04/2024\nT-10.00\nPDépanneur\n^\n")
        assertNull(f.dateOrder)
        assertEquals("Quicken", f.transactions.single().account)
        val latin = "!Type:Bank\nD03/04/2024\nT-10.00\nPDépanneur\n^\n".toByteArray(charset("windows-1252"))
        assertEquals("Dépanneur", QifParser.parse(latin).transactions.single().payee)
    }
}
