package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-04, CR-05, PM-02: optional price downloads, tested with recorded responses and no network. */
class PriceServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private lateinit var books: Books
    private lateinit var group: String
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-10-02")

    @BeforeEach
    fun setUp() {
        books = Books(store.create(temp.resolve("P.hfm"), "P", "perry", "Perry", "pw".toCharArray()).session)
        group = books.groups().single().id
    }

    @AfterEach
    fun tearDown() = books.session.close()

    private fun yahoo(currency: String, closes: String) = """
        {"chart":{"result":[{"meta":{"currency":"$currency","symbol":"X","exchangeTimezoneName":"America/Toronto"},
        "timestamp":[1790775000,1790861400,1790947800],
        "indicators":{"quote":[{"close":[$closes]}]}}],"error":null}}
    """.trimIndent()

    @Test
    fun `nothing is downloaded until a feed is turned on`() {
        val calls = ArrayList<String>()
        val result = books.prices.updateAll(today) { calls += it; error("offline") }
        assertEquals(0, result.total)
        assertTrue(calls.isEmpty(), "no request leaves the computer")
    }

    @Test
    fun `security closes from Yahoo Finance, never over a manual price`() {
        val xic = books.investments.saveSecurity(Security("", "XIC", "TSX", "iShares XIC", SecurityKind.ETF, Currency.CAD), group)
        val fund = books.investments.saveSecurity(Security("", "TDB902", null, "TD Canadian Index", SecurityKind.MUTUAL_FUND, Currency.CAD), group)
        val us = books.investments.saveSecurity(Security("", "BRK.B", "NYSE", "Berkshire Hathaway B", SecurityKind.STOCK, Currency.USD), group)
        assertEquals("XIC.TO", books.prices.quoteSymbol(xic))
        assertNull(books.prices.quoteSymbol(fund), "funds keep manual prices")
        assertEquals("BRK-B", books.prices.quoteSymbol(us))

        books.investments.setPrice(xic.id, d("2026-09-30"), BigDecimal("41.00"))
        books.prices.setEnabled(PriceFeed.SECURITIES, true)
        val urls = ArrayList<String>()
        val result = books.prices.updateAll(today) { url ->
            urls += url
            if ("XIC.TO" in url) yahoo("CAD", "39.71,null,39.85") else yahoo("CAD", "1,2,3")
        }
        assertTrue(urls.any { "XIC.TO" in it } && urls.none { "TDB902" in it })
        assertEquals(2, result.securities, "two closes for XIC; the day without a close is skipped")
        assertEquals(1, result.problems.size, "a U.S. stock quoted in CAD is refused")
        assertEquals(BigDecimal("41"), books.investments.price(xic.id, d("2026-09-30"))!!.second, "the manual price stays")
        assertEquals(d("2026-10-02") to BigDecimal("39.85"), books.investments.price(xic.id, today))
    }

    @Test
    fun `coin prices from CoinGecko value the wallets`() {
        books.accounts.create(AccountDraft(group, "Bitcoin", AccountType.CRYPTO_WALLET, Currency.BTC, Money.parse("0.5", Currency.BTC), d("2026-01-01")))
        assertEquals(listOf(Currency.BTC), books.prices.coinsHeld())
        assertEquals("bitcoin", books.prices.coinId(Currency.BTC))
        books.prices.setEnabled(PriceFeed.CRYPTO, true)
        val result = books.prices.updateAll(today) { url ->
            assertTrue("coins/bitcoin/market_chart" in url && "vs_currency=cad" in url)
            """{"prices":[[1790726400000,84012.55],[1790812800000,85540.10],[1790863920000,86001.99]]}"""
        }
        assertEquals(2, result.crypto, "one price a day; the latest of the day wins")
        assertEquals(BigDecimal("86001.99"), books.rates.cadPerUnit(Currency.BTC, d("2026-10-01")))
        val worth = books.reports.netWorth(listOf(d("2026-10-01"))).value.single().assets
        assertEquals(Money.parse("43001.00", Currency.CAD), worth, "0.5 BTC at 86,001.99")

        books.rates.setManual(Currency.BTC, d("2026-10-01"), BigDecimal("80000"))
        books.prices.applyCoinGecko(Currency.BTC, """{"prices":[[1790863920000,99999]]}""")
        assertEquals(BigDecimal("80000"), books.rates.cadPerUnit(Currency.BTC, d("2026-10-01")), "manual rates are never replaced")
    }

    @Test
    fun `metal spot prices converted from USD`() {
        books.rates.setManual(Currency.USD, d("2026-09-29"), BigDecimal("1.3900"))
        books.prices.setEnabled(PriceFeed.METALS, true)
        val result = books.prices.updateAll(today) { yahoo("USD", "2650.00,2655.50,2661.20") }
        assertEquals(12, result.metals, "three days for each of four metals")
        val gold = books.prices.spot(Metal.GOLD, today)!!
        assertEquals(BigDecimal("3699.07"), gold.cadPerOz, "2,661.20 USD at 1.39")
        books.prices.setSpot(Metal.SILVER, today, BigDecimal("44.10"))
        books.prices.applyYahooMetal(Metal.SILVER, yahoo("USD", "1,1,99"))
        assertEquals(BigDecimal("44.1"), books.prices.spot(Metal.SILVER, today)!!.cadPerOz, "the manual price stays")
    }
}
