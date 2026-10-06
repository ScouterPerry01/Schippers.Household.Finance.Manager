package ca.schippers.hfm.importers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** ARC-04: every importer is listed once, in one registry, behind one interface. */
class ImporterRegistryTest {

    @Test
    fun `one list holds the statement, brokerage, crypto exchange and QIF readers`() {
        assertEquals(
            listOf("statements:ofx", "statements:csv", "investments:ofx", "investments:investment-csv", "crypto-exchange:crypto-exchange", "qif:qif"),
            ImporterRegistry.all.map { "${it.kind}:${it.id}" },
        )
        assertEquals(ImporterRegistry.all.size, ImporterRegistry.all.map { "${it.kind}:${it.id}" }.toSet().size, "ids are unique within a kind")
        // The older lists are views of the registry, in the same order as before.
        assertEquals(listOf("ofx", "csv"), Importers.all.map { it.id })
        assertEquals(listOf(OfxImporter::class, InvestmentCsvImporter::class), InvestmentImporters.all.map { it::class })
    }

    @Test
    fun `each kind finds its reader by the file's name and first bytes`() {
        assertIs<OfxImporter>((ImporterRegistry.forFile(ImportKind.STATEMENTS, "download.qbo", "OFXHEADER:100".toByteArray()) as StatementFormat).importer)
        val kraken = "\"txid\",\"refid\",\"time\",\"type\",\"subtype\",\"aclass\",\"asset\",\"amount\",\"fee\",\"balance\"\n".toByteArray()
        assertSame(CryptoExchangeImporter, ImporterRegistry.forFile(ImportKind.CRYPTO_EXCHANGE, "ledgers.csv", kraken))
        assertNull(ImporterRegistry.forFile(ImportKind.CRYPTO_EXCHANGE, "bank.csv", "Date,Amount".toByteArray()))
        assertSame(QifParser, ImporterRegistry.forFile(ImportKind.QIF, "export.txt", "﻿!Type:Bank\nD03/01/2026\nT-12.00\n^\n".toByteArray()))
        assertSame(QifParser, ImporterRegistry.forFile(ImportKind.QIF, "QUICKEN.QIF", ByteArray(0)))
        assertNull(ImporterRegistry.forFile(ImportKind.QIF, "bank.csv", "Date,Amount".toByteArray()))
    }

    @Test
    fun `reading through the registry gives what the reader gives`() {
        val qif = "!Type:Bank\nD03/15/2026\nT-12.50\nPMetro\n^\n".toByteArray()
        val read = ImporterRegistry.forFile(ImportKind.QIF, "a.qif", qif)!!.read(qif)
        assertEquals(QifParser.parse(qif), read)
        assertEquals("Metro", read.transactions.single().payee)
    }
}
