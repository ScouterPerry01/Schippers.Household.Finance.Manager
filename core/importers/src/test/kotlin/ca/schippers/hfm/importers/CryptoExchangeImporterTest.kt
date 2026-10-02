package ca.schippers.hfm.importers

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CR-03: exchange histories, from small samples in each exchange's layout. */
class CryptoExchangeImporterTest {

    private fun n(s: String) = BigDecimal(s)

    @Test
    fun `Kraken ledger`() {
        val csv = """
            "txid","refid","time","type","subtype","aclass","asset","wallet","amount","fee","balance"
            "L1","D1","2026-01-10 14:00:00","deposit","","currency","ZCAD","spot / main","1000.0000","0.0000","1000.0000"
            "L2","T1","2026-01-11 09:30:00","trade","","currency","ZCAD","spot / main","-500.0000","1.3000","498.7000"
            "L3","T1","2026-01-11 09:30:00","trade","","currency","XXBT","spot / main","0.0058000000","0.0000000000","0.0058000000"
            "L4","S1","2026-02-01 00:00:00","staking","","currency","DOT.S","spot / main","0.1500000000","0","0.15"
            "L5","W1","2026-03-01 12:00:00","withdrawal","","currency","XXBT","spot / main","-0.0050000000","0.0000150000","0.0007850000"
            "L6","E1","2026-03-02 12:00:00","earn","allocation","currency","DOT","spot / main","-1","0","0"
        """.trimIndent()
        assertTrue(CryptoExchangeImporter.canRead("ledgers.csv", csv.toByteArray()))
        val f = CryptoExchangeImporter.read(csv.toByteArray())
        assertEquals("Kraken", f.exchange)
        assertEquals(listOf(CryptoEventKind.DEPOSIT, CryptoEventKind.TRADE, CryptoEventKind.INCOME, CryptoEventKind.WITHDRAWAL), f.events.map { it.kind })
        val trade = f.events[1]
        assertEquals("CAD", trade.sentCurrency)
        assertEquals(n("500.0000"), trade.sent)
        assertEquals("BTC", trade.receivedCurrency)
        assertEquals(n("1.3000"), trade.fee)
        assertEquals("CAD", trade.feeCurrency)
        assertEquals("DOT", f.events[2].receivedCurrency, "staked DOT counts as DOT")
        assertEquals(n("0.0000150000"), f.events[3].fee)
        assertTrue(f.warnings.isEmpty(), "moves into Kraken's earn wallet are skipped quietly")
    }

    @Test
    fun `Coinbase report with its preamble`() {
        val csv = """
            Transactions
            User,Perry,abc123

            ID,Timestamp,Transaction Type,Asset,Quantity Transacted,Price Currency,Spot Price Currency,Spot Price at Transaction,Subtotal,Total (inclusive of fees and/or spread),Fees and/or Spread,Notes
            a1,2026-01-15 14:22:01 UTC,Buy,ETH,0.25,CAD,CAD,CA${'$'}4000.00,CA${'$'}1000.00,"CA${'$'}1,014.95",CA${'$'}14.95,Bought 0.25 ETH
            a2,2026-02-01 10:00:00 UTC,Staking Income,ETH,0.0012,CAD,CAD,CA${'$'}4100,CA${'$'}4.92,CA${'$'}4.92,CA${'$'}0,
            a3,2026-03-01 10:00:00 UTC,Convert,ETH,-0.1,CAD,CAD,CA${'$'}4200,CA${'$'}420,CA${'$'}420,CA${'$'}0,Converted 0.1 ETH to 0.0049 BTC
            a4,2026-03-05 10:00:00 UTC,Send,BTC,-0.0049,CAD,CAD,CA${'$'}85000,,,,To bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu
            a5,2026-03-06 10:00:00 UTC,Mystery,BTC,1,CAD,CAD,1,1,1,0,
        """.trimIndent()
        val f = CryptoExchangeImporter.read(csv.toByteArray())
        assertEquals("Coinbase", f.exchange)
        assertEquals(listOf(CryptoEventKind.TRADE, CryptoEventKind.INCOME, CryptoEventKind.TRADE, CryptoEventKind.WITHDRAWAL), f.events.map { it.kind })
        assertEquals(n("1014.95"), f.events[0].sent, "the total, fees included")
        assertEquals(n("0.25"), f.events[0].received)
        assertEquals("BTC", f.events[2].receivedCurrency)
        assertEquals(n("0.0049"), f.events[2].received)
        assertEquals(n("0.0049"), f.events[3].sent)
        assertEquals("a1", f.events[0].externalId)
        assertEquals(1, f.warnings.size)
    }

    @Test
    fun `Shakepay summary, newer layout`() {
        val csv = """
            Date,Amount Debited,Asset Debited,Amount Credited,Asset Credited,Market Value,Market Value Currency,Book Cost,Book Cost Currency,Type,Spot Rate,Buy / Sell Rate,Description
            2026-01-05T15:00:00+00,,,500,CAD,500,CAD,,,Interac e-Transfer,,,Deposit
            2026-01-06T15:00:00+00,250,CAD,0.0029,BTC,250,CAD,250,CAD,Buy,86206,86206,Bought BTC
            2026-01-07T05:00:00+00,,,0.00000150,BTC,0.13,CAD,,,Reward,86000,,ShakingSats
            2026-02-01T15:00:00+00,0.0029,BTC,,,250,CAD,,,Send,,,Sent to wallet
        """.trimIndent()
        val f = CryptoExchangeImporter.read(csv.toByteArray())
        assertEquals("Shakepay", f.exchange)
        assertEquals(listOf(CryptoEventKind.DEPOSIT, CryptoEventKind.TRADE, CryptoEventKind.INCOME, CryptoEventKind.WITHDRAWAL), f.events.map { it.kind })
        assertEquals("CAD", f.events[0].receivedCurrency)
        assertEquals(LocalDate(2026, 1, 6), f.events[1].date)
    }

    @Test
    fun `Newton history`() {
        val csv = """
            Date,Type,Received Quantity,Received Currency,Sent Quantity,Sent Currency,Fee Amount,Fee Currency,Tag
            2026-01-20 10:00:00,TRADE,0.1,ETH,410.50,CAD,0,CAD,
            2026-01-21 10:00:00,DEPOSIT,1000,CAD,,,,,
            2026-01-22 10:00:00,DEPOSIT,25,CAD,,,,,Referral
        """.trimIndent()
        val f = CryptoExchangeImporter.read(csv.toByteArray())
        assertEquals("Newton", f.exchange)
        assertEquals(listOf(CryptoEventKind.TRADE, CryptoEventKind.DEPOSIT, CryptoEventKind.INCOME), f.events.map { it.kind })
        assertEquals("CAD", f.events[0].sentCurrency)
    }

    @Test
    fun `other files are refused`() {
        assertFailsWith<ImportException> { CryptoExchangeImporter.read("a,b,c\n1,2,3".toByteArray()) }
        assertEquals("BTC", CryptoExchangeImporter.normalize("XXBT"))
        assertEquals("CAD", CryptoExchangeImporter.normalize("ZCAD"))
        assertEquals("ETH", CryptoExchangeImporter.normalize("ETH2.S"))
    }
}
