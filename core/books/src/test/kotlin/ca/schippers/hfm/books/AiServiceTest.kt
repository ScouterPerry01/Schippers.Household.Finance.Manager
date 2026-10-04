package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.DocumentDraft
import ca.schippers.hfm.ocr.DocumentKind
import ca.schippers.hfm.ocr.Extracted
import ca.schippers.hfm.ocr.FieldSource
import ca.schippers.hfm.ocr.OcrLine
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Section 4.5 in the books: settings off by default, readings kept with documents, the usage log (AI-01, AI-05, AI-06, HH-11). */
class AiServiceTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("A.hfm")

    private fun draft() = DocumentDraft(
        DocumentKind.RECEIPT,
        merchant = Extracted("Pharmacie Jean Coutu", 0.95f, FieldSource.CLOUD_AI),
        date = Extracted(LocalDate(2026, 9, 30), 0.95f, FieldSource.CLOUD_AI),
        total = Extracted(Money.parse("43.21", Currency.CAD), 0.95f, FieldSource.CLOUD_AI),
    )

    @Test
    fun `each user's settings start off and asking first`() {
        Books(store.create(dir, "A", "perry", "Perry", "password1".toCharArray()).session).use { books ->
            assertEquals(AiSettings(enabled = false, confirmEach = true, model = null), books.ai.settings())
            books.ai.saveSettings(AiSettings(enabled = true, confirmEach = false, model = "claude-sonnet-5-5"))
            assertEquals(AiSettings(true, false, "claude-sonnet-5-5"), books.ai.settings())
            books.users.add("marie", "Marie", Role.MEMBER, "password2".toCharArray())
        }
        Books(store.unlock(dir, "marie", "password2".toCharArray())).use { marie ->
            assertEquals(AiSettings(), marie.ai.settings(), "Marie chooses for herself")
        }
    }

    @Test
    fun `a reading is kept with its document and its fields are marked as read by AI`() {
        Books(store.create(dir, "A", "perry", "Perry", "password1".toCharArray()).session).use { books ->
            val group = books.groups().single().id
            val doc = books.documents.import(group, "photo".encodeToByteArray(), "IMG_1.jpg", "image/jpeg").document
            books.documents.recordText(doc.id, 1, OcrResult(listOf(OcrLine("JEAN C0UTU ?? 4?.21", 0.4f)), 10), "paddle", LocalDate(2026, 10, 1))
            val answer = """{"merchant":"Pharmacie Jean Coutu","date":"2026-09-30","total":43.21,"currency":"CAD"}"""
            val read = books.ai.saveReading(doc.id, "receipt", "hfm/receipt/v1", answer, true, "claude-opus-5-5", draft())

            assertEquals("Pharmacie Jean Coutu", read.merchant)
            assertEquals(Money.parse("43.21", Currency.CAD), read.amount)
            assertEquals(LocalDate(2026, 9, 30), read.date)
            assertEquals(FieldSource.CLOUD_AI, read.draft!!.total!!.source)
            assertEquals("JEAN C0UTU ?? 4?.21", read.text, "the recognised text stays")
            val kept = books.ai.reading(doc.id)!!
            assertEquals(answer, kept.answer)
            assertEquals("hfm/receipt/v1", kept.schemaVersion)

            val audit = books.session.core.coreQueries.recentAudit(50).executeAsList()
            assertTrue(audit.any { it.action == "AI_READ" && it.entity_id == doc.id })
            val details = audit.mapNotNull { it.details }
            assertTrue(details.none { "Coutu" in it || "43" in it }, "what the document holds stays out of the shared log: $details")
        }
    }

    @Test
    fun `the usage log keeps each request in the document's group, seen only by whoever sent it`() {
        val start = System.currentTimeMillis() - 1000
        lateinit var privateDoc: String
        Books(store.create(dir, "A", "perry", "Perry", "password1".toCharArray()).session).use { books ->
            val shared = books.groups().single().id
            val doc = books.documents.import(shared, "bill".encodeToByteArray(), "bill.pdf", "application/pdf").document
            books.ai.record(doc.id, "anthropic", "claude-opus-5-5", "bill", 2000, 300, BigDecimal("0.0140"), false)
            books.ai.record(doc.id, "anthropic", "claude-opus-5-5", "bill", 2100, 310, BigDecimal("0.01462"), true)
            val log = books.ai.usage(start, System.currentTimeMillis() + 1000)
            assertEquals(listOf(true, false), log.map { it.succeeded })
            assertEquals(BigDecimal("0.014620"), log[0].costUsd)
            assertEquals("bill.pdf", log[0].documentLabel)
            val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2".toCharArray()).userId
            books.users.setAccess(shared, marie, PermissionLevel.EDIT)
        }
        Books(store.unlock(dir, "marie", "password2".toCharArray())).use { marie ->
            assertEquals(emptyList(), marie.ai.usage(start, System.currentTimeMillis() + 1000), "Perry's key, Perry's log")
            val own = marie.session.createGroup("Marie - privé", private = true)
            privateDoc = marie.documents.import(own, "stub".encodeToByteArray(), "paie.jpg", "image/jpeg").document.id
            marie.ai.record(privateDoc, "anthropic", "claude-haiku-4-5", "pay_stub", 1800, 400, BigDecimal("0.0038"), true)
            assertEquals(1, marie.ai.usage(start, System.currentTimeMillis() + 1000).size)
        }
        Books(store.unlock(dir, "perry", "password1".toCharArray())).use { perry ->
            assertTrue(perry.groups().none { it.isPrivate }, "Marie's private group, its document and its log entry are not readable by Perry")
            assertEquals(2, perry.ai.usage(start, System.currentTimeMillis() + 1000).size)
        }
    }

    private inline fun <T> Books.use(block: (Books) -> T): T = try { block(this) } finally { session.close() }
}
