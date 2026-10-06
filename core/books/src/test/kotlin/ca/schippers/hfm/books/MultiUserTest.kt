package ca.schippers.hfm.books

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.CaptureFields
import ca.schippers.hfm.sync.CaptureItem
import ca.schippers.hfm.sync.CaptureKind
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlin.test.assertFalse
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** HH-05 to HH-13 with two people: what each sees, and where each one's phone captures go. */
class MultiUserTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("M.hfm")
    private val today = LocalDate(2026, 10, 2)
    private val now = 1_790_000_000_000L
    private fun cad(s: String) = Money.parse(s, Currency.CAD)
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    private fun perry() = Books(store.unlock(dir, "perry", "password1".toCharArray()))
    private fun marie() = Books(store.unlock(dir, "marie", "password2-long".toCharArray()))

    /** Perry (administrator) with a shared chequing account; Marie (member) with her own private card. */
    private fun household(): String {
        val books = Books(store.create(dir, "M", "perry", "Perry", "password1".toCharArray()).session)
        val shared = books.groups().single().id
        books.accounts.create(AccountDraft(shared, "Compte conjoint", AccountType.CHEQUING, Currency.CAD, cad("1000"), LocalDate(2026, 1, 1)))
        val marie = books.users.add("marie", "Marie", Role.MEMBER, "password2-long".toCharArray()).userId
        books.session.close()
        marie().use {
            val own = it.session.createGroup("Marie - privé", private = true)
            it.accounts.create(AccountDraft(own, "Mastercard de Marie", AccountType.CREDIT_CARD, Currency.CAD, cad("0"), LocalDate(2026, 1, 1)))
        }
        return marie
    }

    private fun Books.use(block: (Books) -> Unit) = try { block(this) } finally { session.close() }

    private fun pairPhone(books: Books, deviceId: String): ByteArray {
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Bureau", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest(deviceId, deviceId, SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        return PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
    }

    private fun send(books: Books, deviceId: String, key: ByteArray, merchant: String): SyncResponse {
        val item = CaptureItem("item-$deviceId-$merchant", CaptureKind.QUICK_EXPENSE, now, fields = CaptureFields(merchant = merchant, amount = "10.00"))
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), SyncRequest(now, listOf(item)), key, books.sync.desktopId, deviceId, Direction.TO_DESKTOP)
        return SyncCrypto.open(SyncResponse.serializer(), books.sync.handle(deviceId, sealed, noConversion, now, today), key, books.sync.desktopId, deviceId, Direction.TO_PHONE)
    }

    @Test
    fun `each user sees only what they may (HH-09, HH-10)`() {
        val marieId = household()
        marie().use { assertEquals(listOf("Mastercard de Marie"), it.accounts.list().map { a -> a.account.name }, "the shared account is not hers until granted") }
        perry().use {
            assertEquals(listOf("Compte conjoint"), it.accounts.list().map { a -> a.account.name }, "Marie's private card is hers alone")
            it.users.setAccess(it.groups().first { g -> !g.isPrivate }.id, marieId, PermissionLevel.VIEW)
        }
        marie().use { m ->
            assertEquals(setOf("Compte conjoint", "Mastercard de Marie"), m.accounts.list().map { it.account.name }.toSet())
            val shared = m.accounts.list().first { it.account.name == "Compte conjoint" }.account
            assertFailsWith<ca.schippers.hfm.data.AccessDeniedException>("view only") { m.transactions.create(TransactionDraft(shared.id, today, cad("-5"), "Café")) }
            assertEquals(PermissionLevel.VIEW, m.users.access().first { !it.group.isPrivate }.levels[marieId])
        }
    }

    @Test
    fun `phone captures go to their owner's inbox, and wait while someone else is signed in (HH-12)`() {
        household()
        val marieKey = marie().run {
            val key = pairPhone(this, "marie-phone")
            assertEquals("Stationnement", send(this, "marie-phone", key, "Stationnement").let { documents.inbox().single().merchant })
            assertTrue(documents.inbox().single().groupId == groups().first { it.isPrivate }.id, "stored in her private group")
            session.close()
            key
        }
        perry().use { p ->
            val perryKey = pairPhone(p, "perry-phone")
            send(p, "perry-phone", perryKey, "Épicerie")
            assertEquals(listOf("Épicerie"), p.documents.inbox().map { it.merchant }, "Marie's capture is not in Perry's inbox, nor readable by him")
            assertFailsWith<OwnerAwayException> { send(p, "marie-phone", marieKey, "Pharmacie") }
        }
    }

    @Test
    fun `only a phone's owner chooses where its captures, trips and places go`() {
        val marieId = household()
        val shared = perry().run {
            val id = groups().single().id
            users.setAccess(id, marieId, PermissionLevel.EDIT)
            session.close()
            id
        }
        marie().use { m -> pairPhone(m, "marie-phone") }
        perry().use { p ->
            pairPhone(p, "perry-phone")
            // Perry could otherwise send Marie's trips and places, with their coordinates, to the shared group he reads.
            assertFailsWith<AccessDeniedException> { p.sync.update("marie-phone", "Marie's phone", shared) }
            assertTrue(p.sync.devices().single { it.id == "marie-phone" }.groupId != shared)
            // As administrator he may still take a lost phone off.
            p.sync.revoke("marie-phone", now)
        }
        marie().use { m ->
            assertFailsWith<AccessDeniedException> { m.sync.revoke("perry-phone", now) }
            assertFailsWith<AccessDeniedException> { m.sync.forget("perry-phone") }
            m.sync.update("marie-phone", "Pixel", m.groups().single { it.isPrivate }.id)
            assertEquals("Pixel", m.sync.devices().single { it.id == "marie-phone" }.name)
            m.sync.forget("marie-phone")
        }
    }

    @Test
    fun `activity is visible to administrators, and to each user for themselves (HH-13)`() {
        val marieId = household()
        perry().use { p ->
            val all = p.users.activity()
            assertTrue(all.any { it.userId == marieId && it.action == "CREATE" && it.entity == "account_group" }, "Perry sees Marie creating her group")
            assertTrue(p.users.activity(marieId).all { it.userId == marieId })
        }
        marie().use { m -> assertTrue(m.users.activity().all { it.userId == marieId }, "Marie sees only her own activity") }
    }

    @Test
    fun `a viewer sees the pets but cannot change them`() {
        household()
        perry().use {
            it.users.add("vic", "Vic", Role.VIEWER, "viewer-password3".toCharArray())
            it.pets.save(Pet("", "Rex", Species.DOG))
        }
        Books(store.unlock(dir, "vic", "viewer-password3".toCharArray())).use { vic ->
            assertEquals(listOf("Rex"), vic.pets.list().map { it.name })
            assertFalse(vic.pets.canChange)
            assertFailsWith<AccessDeniedException> { vic.pets.save(Pet("", "Mimi", Species.CAT)) }
            assertFailsWith<AccessDeniedException> { vic.pets.delete(vic.pets.list().single().id) }
        }
    }

    @Test
    fun `budget suggestions leave out private spending, and a viewer cannot change budgets`() {
        val marieId = household()
        perry().use { it.session.setPermission(it.groups().single { g -> !g.isPrivate }.id, marieId, PermissionLevel.VIEW) }
        fun Books.spend(account: String, amount: String) {
            val a = accounts.list().first { it.account.name == account }.account
            val food = categories.list().first { it.systemKey == "food.groceries" }.id
            transactions.create(TransactionDraft(a.id, LocalDate(2026, 3, 5), cad(amount), "IGA", listOf(SplitDraft(food, cad(amount)))))
        }
        perry().use { it.spend("Compte conjoint", "-1200.00") }
        marie().use { m ->
            m.spend("Mastercard de Marie", "-2400.00")
            val food = m.categories.list().first { it.systemKey == "food" }.id
            // Only the shared account's groceries count: a budget is seen by everyone in the household.
            assertEquals(cad("100.00"), m.budgets.suggestions(today)[food])
        }
        perry().use { it.users.add("vic", "Vic", Role.VIEWER, "viewer-password3".toCharArray()) }
        Books(store.unlock(dir, "vic", "viewer-password3".toCharArray())).use { vic ->
            val food = vic.categories.list().first { it.systemKey == "food" }.id
            assertFailsWith<AccessDeniedException> { vic.budgets.set(food, BudgetPeriod.MONTHLY, cad("100.00"), startMonth = LocalDate(2026, 10, 1)) }
        }
        perry().use { it.budgets.set(it.categories.list().first { c -> c.systemKey == "food" }.id, BudgetPeriod.MONTHLY, cad("100.00"), startMonth = LocalDate(2026, 10, 1)) }
        Books(store.unlock(dir, "vic", "viewer-password3".toCharArray())).use { vic ->
            assertEquals(1, vic.budgets.list().size, "a viewer sees the budgets")
            assertFailsWith<AccessDeniedException> { vic.budgets.remove(vic.budgets.list().single().categoryId) }
            assertEquals(1, vic.budgets.list().size)
        }
    }
}
