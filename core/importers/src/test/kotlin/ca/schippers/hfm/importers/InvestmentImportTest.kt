package ca.schippers.hfm.importers

import ca.schippers.hfm.money.Currency
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvestmentImportTest {

    private val ofx = """
        OFXHEADER:100
        DATA:OFXSGML
        VERSION:102
        CHARSET:1252

        <OFX>
        <INVSTMTMSGSRSV1><INVSTMTTRNRS><INVSTMTRS>
        <DTASOF>20260930
        <CURDEF>CAD
        <INVACCTFROM><BROKERID>disnat.com<ACCTID>12345678</INVACCTFROM>
        <INVTRANLIST><DTSTART>20260101<DTEND>20260930
        <BUYSTOCK><INVBUY><INVTRAN><FITID>T1<DTTRADE>20260115<MEMO>Achat XIC</INVTRAN>
          <SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID><UNITS>100<UNITPRICE>38.50<COMMISSION>9.95<TOTAL>-3859.95</INVBUY><BUYTYPE>BUY</BUYSTOCK>
        <INCOME><INVTRAN><FITID>T2<DTTRADE>20260331</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
          <INCOMETYPE>DIV<TOTAL>24.10<WITHHOLDING>0</INCOME>
        <REINVEST><INVTRAN><FITID>T3<DTTRADE>20260630</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
          <INCOMETYPE>CGLONG<TOTAL>-38.90<UNITS>1<UNITPRICE>38.90</REINVEST>
        <SELLSTOCK><INVSELL><INVTRAN><FITID>T4<DTTRADE>20260815</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
          <UNITS>-40<UNITPRICE>41.00<COMMISSION>9.95<TOTAL>1630.05</INVSELL><SELLTYPE>SELL</SELLSTOCK>
        <INVBANKTRAN><STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260110<TRNAMT>5000.00<FITID>T0<NAME>Dépôt</STMTTRN><SUBACCTFUND>CASH</INVBANKTRAN>
        <SPLIT><INVTRAN><FITID>T5<DTTRADE>20260901</INVTRAN><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID>
          <OLDUNITS>61<NEWUNITS>122<NUMERATOR>2<DENOMINATOR>1</SPLIT>
        </INVTRANLIST>
        <INVPOSLIST><POSSTOCK><INVPOS><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID><HELDINACCT>CASH<POSTYPE>LONG
          <UNITS>122<UNITPRICE>21.10<MKTVAL>2574.20<DTPRICEASOF>20260930</INVPOS></POSSTOCK></INVPOSLIST>
        <INVBAL><AVAILCASH>2770.10<MARGINBALANCE>0<SHORTBALANCE>0</INVBAL>
        </INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1>
        <SECLISTMSGSRSV1><SECLIST>
        <STOCKINFO><SECINFO><SECID><UNIQUEID>464286103<UNIQUEIDTYPE>CUSIP</SECID><SECNAME>iShares Core S&amp;P/TSX Capped Composite<TICKER>XIC<UNITPRICE>21.10<DTASOF>20260930</SECINFO></STOCKINFO>
        </SECLIST></SECLISTMSGSRSV1>
        </OFX>
    """.trimIndent()

    @Test
    fun `OFX investment statement`() {
        val st = OfxImporter().readInvestments(ofx.byteInputStream(charset("windows-1252"))).single()
        assertEquals("12345678", st.accountNumberHint)
        assertEquals(LocalDate(2026, 9, 30), st.asOf)
        assertEquals(BigDecimal("2770.10"), st.cash)
        val xic = st.securities.single()
        assertEquals("XIC", xic.symbol)
        assertEquals("iShares Core S&P/TSX Capped Composite", xic.name)
        assertEquals("STOCK", xic.kind)
        val byId = st.actions.associateBy { it.externalId }
        assertEquals(ImportedAction.BUY, byId.getValue("T1").action)
        assertEquals(BigDecimal("3850.00"), byId.getValue("T1").amount)
        assertEquals(BigDecimal("9.95"), byId.getValue("T1").fees)
        assertEquals(ImportedAction.DIVIDEND, byId.getValue("T2").action)
        assertEquals(BigDecimal("24.10"), byId.getValue("T2").amount)
        assertEquals(ImportedAction.REINVEST, byId.getValue("T3").action)
        assertEquals(ImportedAction.DISTRIBUTION, byId.getValue("T3").incomeAction)
        assertEquals(BigDecimal("40"), byId.getValue("T4").quantity)
        assertEquals(BigDecimal("1640.00"), byId.getValue("T4").amount)
        assertEquals(ImportedAction.CASH_IN, byId.getValue("T0").action)
        assertEquals(0, BigDecimal("2").compareTo(byId.getValue("T5").ratio))
        assertEquals(BigDecimal("122"), st.positions.single().quantity)
    }

    @Test
    fun `English broker CSV`() {
        val csv = """
            Transaction Date,Settlement Date,Action,Symbol,Description,Quantity,Price,Gross Amount,Commission,Net Amount,Currency
            2026-01-15 00:00:00 AM,2026-01-17,Buy,XEQT.TO,ISHARES CORE EQUITY ETF,50,31.20,-1560.00,-4.95,-1564.95,CAD
            2026-03-28 00:00:00 AM,2026-03-28,Dividend,XEQT.TO,ISHARES CORE EQUITY ETF,0,0,,0,12.40,CAD
            2026-04-02 00:00:00 AM,2026-04-02,Contribution,,TFSA CONTRIBUTION,0,0,,0,"1,000.00",CAD
            2026-05-01 00:00:00 AM,2026-05-01,Journal,,SOMETHING ODD,0,0,,0,0,CAD
            2026-06-10 00:00:00 AM,2026-06-12,Sell,XEQT.TO,ISHARES CORE EQUITY ETF,-20,33.00,660.00,-4.95,655.05,CAD
        """.trimIndent()
        val importer = InvestmentCsvImporter()
        assertTrue(importer.canReadInvestments("activity.csv", csv.toByteArray()))
        val st = importer.readInvestments(csv.byteInputStream()).single()
        assertEquals(Currency.CAD, st.currency)
        assertEquals(listOf(ImportedAction.BUY, ImportedAction.DIVIDEND, ImportedAction.CASH_IN, ImportedAction.SELL), st.actions.map { it.action })
        val buy = st.actions[0]
        assertEquals("XEQT.TO", buy.securityKey)
        assertEquals(BigDecimal("1560.00"), buy.amount)
        assertEquals(BigDecimal("4.95"), buy.fees)
        assertEquals(BigDecimal("1000.00"), st.actions[2].amount)
        assertEquals(BigDecimal("20"), st.actions[3].quantity)
        assertEquals(1, st.warnings.size, "the journal line is reported, not guessed")
        assertEquals("ISHARES CORE EQUITY ETF", st.securities.single().name)
    }

    @Test
    fun `French broker CSV with semicolons and decimal commas`() {
        val csv = """
            Date de transaction;Type de transaction;Symbole;Description;Quantité;Prix;Montant net;Commission;Devise
            2026-02-03;Achat;ZAG;BMO OBLIGATIONS AGREGEES;100;13,85;-1 394,95;9,95;CAD
            2026-02-28;Intérêts;;INTERETS SUR ENCAISSE;;;3,12;;CAD
            2026-03-31;Distribution;ZAG;BMO OBLIGATIONS AGREGEES;;;4,50;;CAD
        """.trimIndent()
        val st = InvestmentCsvImporter().readInvestments(csv.byteInputStream()).single()
        assertEquals(listOf(ImportedAction.BUY, ImportedAction.INTEREST, ImportedAction.DISTRIBUTION), st.actions.map { it.action })
        assertEquals(BigDecimal("1385.00"), st.actions[0].amount)
        assertEquals(BigDecimal("3.12"), st.actions[1].amount)
        assertEquals(null, st.actions[1].securityKey)
        assertNotNull(st.securities.singleOrNull { it.symbol == "ZAG" })
    }

    @Test
    fun `tax withheld goes on its income, not into fees (M-31)`() {
        val csv = """
            Transaction Date,Action,Symbol,Description,Quantity,Price,Net Amount,Currency
            2026-03-15,Dividend,VTI,VANGUARD TOTAL STOCK MARKET ETF,0,0,50.00,USD
            2026-03-15,Dividend,XEQT,ISHARES CORE EQUITY ETF,0,0,12.40,USD
            2026-03-15,Non-resident withholding tax,VTI,VANGUARD TOTAL STOCK MARKET ETF,0,0,-7.50,USD
            2026-04-01,Foreign tax,,US TAX,0,0,-3.00,USD
        """.trimIndent()
        val st = InvestmentCsvImporter().readInvestments(csv.byteInputStream()).single()
        assertEquals(listOf(ImportedAction.DIVIDEND, ImportedAction.DIVIDEND, ImportedAction.FEE), st.actions.map { it.action })
        assertEquals(BigDecimal("7.50"), st.actions.single { it.securityKey == "VTI" }.withheld, "on the VTI dividend")
        assertEquals(null, st.actions.single { it.securityKey == "XEQT" }.withheld)
        assertEquals(BigDecimal("3.00"), st.actions[2].amount, "no income that day: kept as a fee")
        assertEquals("importWarning.withheldAlone 5", ca.schippers.hfm.domain.UserText.decode(st.warnings.single()) { key, args -> (listOf(key) + args).joinToString(" ") })
    }

    @Test
    fun `QIF security list`() {
        val qif = "!Type:Security\nNiShares XIC\nSXIC\nTStock\n^\nNFidelity Canadian\nSFID231\nTMutual Fund\n^\n"
        val file = QifParser.parse(qif)
        assertEquals(listOf("XIC", "FID231"), file.securities.map { it.symbol })
        assertEquals("Mutual Fund", file.securities[1].type)
    }
}
