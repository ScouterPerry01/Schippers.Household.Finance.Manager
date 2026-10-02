package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.importers.CryptoExchangeImporter
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CR-01 to CR-06. Expected ACB figures were worked out separately in Python. */
class CryptoServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private lateinit var alex: Member
    private lateinit var chequing: Account
    private val eth = Currency.of("ETH")
    private fun d(s: String) = LocalDate.parse(s)
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private fun btc(s: String) = Money.parse(s, Currency.BTC)
    private fun n(s: String) = BigDecimal(s)
    private val crypto get() = books.crypto

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("C.hfm"), "C", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
        alex = books.members.create("Alex", MemberKind.ADULT)
        chequing = books.accounts.create(AccountDraft(group, "Chequing", AccountType.CHEQUING, Currency.CAD, cad("20000"), d("2026-01-01")))
        for ((date, price) in listOf("2026-01-10" to "60000", "2026-03-01" to "70000", "2026-04-01" to "80000")) books.rates.setManual(Currency.BTC, d(date), n(price))
        books.rates.setManual(eth, d("2026-03-01"), n("3500"))
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun wallet(name: String, currency: Currency = Currency.BTC) =
        books.accounts.create(AccountDraft(group, name, AccountType.CRYPTO_WALLET, currency, Money.zero(currency), d("2026-01-01"), ownerMemberIds = setOf(alex.id)))

    private fun balance(a: Account) = books.accounts.list().first { it.account.id == a.id }.balance

    @Test
    fun `buy, reward, fee, conversion and sale give the right ACB`() {
        val hot = wallet("Shakepay BTC")
        val ether = wallet("Ether", eth)
        crypto.buy(hot.id, chequing.id, d("2026-01-10"), btc("0.1"), cad("6100"))
        crypto.income(hot.id, d("2026-03-01"), btc("0.001"), CryptoIncomeKind.STAKING)
        crypto.fee(hot.id, d("2026-03-01"), btc("0.0001"))
        crypto.convert(hot.id, ether.id, d("2026-03-01"), btc("0.05"), Money.parse("1", eth))
        crypto.sell(hot.id, chequing.id, d("2026-04-01"), btc("0.04"), cad("3200"))

        assertEquals(btc("0.0109"), balance(hot))
        val report = crypto.acb(d("2026-12-31"))
        val btcPool = report.pools.single { it.security.symbol == "BTC" }
        assertEquals(n("0.0109"), btcPool.quantity)
        assertEquals(cad("665.87"), btcPool.acb)
        val gains = report.gains.map { it.disposition }
        assertEquals(listOf(cad("0.89"), cad("445.55"), cad("756.43")), gains.map { it.gain }, "fee, conversion, sale")
        assertEquals(cad("3500"), report.pools.single { it.security.symbol == "ETH" }.acb, "the ether cost what the bitcoin given was worth")
        assertTrue(report.problems.isEmpty())

        val all = books.investments.acb(d("2026-12-31"))
        assertTrue(all.pools.any { it.security.id == "coin:BTC" }, "coins appear with securities in the gains view")
        val income = books.reports.incomeExpense(ReportFilter(d("2026-01-01"), d("2026-12-31")), Granularity.YEAR).value.single().income
        assertEquals(cad("70.00"), income, "the staking reward is income at its value; buys and sales are not")
    }

    @Test
    fun `moves between own wallets are not disposals, and sends are linked to receipts`() {
        val hot = wallet("Exchange BTC")
        val cold = wallet("Cold storage")
        crypto.buy(hot.id, chequing.id, d("2026-01-10"), btc("0.1"), cad("6000"))
        crypto.move(hot.id, cold.id, d("2026-03-01"), btc("0.02"), btc("0.0001"))
        // An exchange send and a watch-only receipt, recorded separately, then linked.
        books.transactions.create(TransactionDraft(hot.id, d("2026-03-05"), btc("-0.0100"), "Sent"))
        books.transactions.create(TransactionDraft(cold.id, d("2026-03-06"), btc("0.0099"), "Received"))
        assertEquals(1, crypto.linkTransfers())
        assertEquals(0, crypto.linkTransfers(), "nothing left to link")
        assertEquals(btc("0.0699"), balance(hot))
        assertEquals(btc("0.0299"), balance(cold))
        val gains = crypto.acb(d("2026-12-31")).gains
        assertEquals(2, gains.size, "only the two network fees are disposals")
        assertEquals(n("0.0998"), crypto.acb(d("2026-12-31")).pools.single().quantity)
    }

    @Test
    fun `exchange history imports once`() {
        val kraken = """
            "txid","refid","time","type","subtype","aclass","asset","wallet","amount","fee","balance"
            "L1","D1","2026-01-10 14:00:00","deposit","","currency","ZCAD","spot / main","1000.0000","0.0000","1000.0000"
            "L2","T1","2026-01-11 09:30:00","trade","","currency","ZCAD","spot / main","-500.0000","1.3000","498.7000"
            "L3","T1","2026-01-11 09:30:00","trade","","currency","XXBT","spot / main","0.0058000000","0.0000000000","0.0058000000"
            "L4","S1","2026-02-01 00:00:00","staking","","currency","DOT.S","spot / main","0.1500000000","0","0.15"
            "L5","W1","2026-03-01 12:00:00","withdrawal","","currency","XXBT","spot / main","-0.0050000000","0.0000150000","0.0007850000"
        """.trimIndent()
        val file = CryptoExchangeImporter.read(kraken.toByteArray())
        val plan = crypto.plan(file)
        assertEquals(listOf("CAD"), plan.fiat)
        assertEquals(listOf("BTC", "DOT"), plan.coins)
        val krakenCad = books.accounts.create(AccountDraft(group, "Kraken CAD", AccountType.CASH, Currency.CAD, cad("0"), d("2026-01-01")))
        val result = crypto.importExchange(file, group, mapOf("CAD" to krakenCad.id), emptyMap(), setOf(alex.id))
        assertEquals(4, result.added)
        assertEquals(2, result.walletsCreated)
        assertEquals(cad("498.70"), balance(krakenCad), "1,000 in, 501.30 out with the fee")
        val krakenBtc = books.accounts.list().first { it.account.name == "Kraken BTC" }.account
        assertEquals(btc("0.000785"), balance(krakenBtc), "0.0058 in, 0.005 out and the 0.000015 fee")
        val again = crypto.importExchange(file, group, mapOf("CAD" to krakenCad.id), mapOf("BTC" to krakenBtc.id, "DOT" to books.accounts.list().first { it.account.name == "Kraken DOT" }.account.id))
        assertEquals(0, again.added)
        assertEquals(4, again.alreadyThere)
        assertEquals(cad("498.70"), balance(krakenCad))
    }

    @Test
    fun `watch-only wallet from a public block explorer`() {
        val cold = wallet("Cold storage")
        assertFailsWith<ValidationException> {
            crypto.saveDetails(WalletDetails(cold.id, watch = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"))
        }
        val ours = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        crypto.saveDetails(WalletDetails(cold.id, cad("0"), watch = ours))
        assertEquals(WatchKind.ADDRESS, crypto.details(cold.id).watchKind)
        val txs = """[
          {"txid":"aa","status":{"confirmed":true,"block_time":1767700000},"fee":500,
           "vin":[{"prevout":{"scriptpubkey_address":"bc1qother","value":2000000}}],
           "vout":[{"scriptpubkey_address":"$ours","value":1000000},{"scriptpubkey_address":"bc1qother","value":999500}]},
          {"txid":"bb","status":{"confirmed":true,"block_time":1770000000},"fee":1000,
           "vin":[{"prevout":{"scriptpubkey_address":"$ours","value":1000000}}],
           "vout":[{"scriptpubkey_address":"bc1qshop","value":600000},{"scriptpubkey_address":"$ours","value":399000}]},
          {"txid":"cc","status":{"confirmed":false},"fee":1,"vin":[],"vout":[{"scriptpubkey_address":"$ours","value":5}]}
        ]"""
        val urls = ArrayList<String>()
        val result = crypto.sync(cold.id) { url -> urls += url; txs }
        assertEquals(listOf("https://mempool.space/api/address/$ours/txs/chain"), urls)
        assertEquals(2, result.added)
        assertEquals(Money.ofMinor(399000, Currency.BTC), balance(cold), "1,000,000 in, 600,000 sent and 1,000 fee")
        assertEquals(0, crypto.sync(cold.id) { txs }.added, "nothing twice")
    }

    @Test
    fun `an extended public key follows every used address`() {
        val cold = wallet("Hardware wallet")
        crypto.saveDetails(WalletDetails(cold.id, watch = "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"))
        val first = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        var statsCalls = 0
        val result = crypto.sync(cold.id) { url ->
            when {
                url.endsWith("/txs/chain") -> """[{"txid":"aa","status":{"confirmed":true,"block_time":1767700000},"fee":1,"vin":[{"prevout":{"scriptpubkey_address":"x","value":5}}],"vout":[{"scriptpubkey_address":"$first","value":250000}]}]"""
                else -> { statsCalls++; """{"chain_stats":{"tx_count":${if (url.endsWith(first)) 1 else 0}}}""" }
            }
        }
        assertEquals(1, result.addresses)
        assertEquals(41, statsCalls, "the used address, then 20 unused on each chain")
        assertEquals(Money.ofMinor(250000, Currency.BTC), balance(cold))
    }
}
