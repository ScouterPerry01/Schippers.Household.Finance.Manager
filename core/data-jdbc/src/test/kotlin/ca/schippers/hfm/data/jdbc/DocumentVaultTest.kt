package ca.schippers.hfm.data.jdbc

import ca.schippers.hfm.data.AccessDeniedException
import ca.schippers.hfm.data.DocumentVault
import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.domain.Role
import ca.schippers.hfm.security.DecryptionException
import ca.schippers.hfm.security.KdfParams
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Section 4.4, SEC-01, HH-11: documents are encrypted per group and bound to their place. */
class DocumentVaultTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val dir get() = temp.resolve("V.hfm")
    private val receipt = "IGA Extra\nTOTAL 42,17 \$\nTPS 1,83 TVQ 3,65".encodeToByteArray()

    @Test
    fun `documents are encrypted, round-trip, and detect tampering or moving`() {
        store.create(dir, "V", "perry", "Perry", "pw".toCharArray()).session.use { session ->
            val partition = session.core.coreQueries.groups().executeAsList().single().partition_id
            val vault = session.vault(partition)
            vault.put("doc1", receipt)
            assertContentEquals(receipt, vault.get("doc1"))

            val file = dir.resolve(DocumentVault.DIR).resolve(partition).resolve("doc1${DocumentVault.EXTENSION}")
            val raw = Files.readAllBytes(file)
            assertFalse(raw.decodeToString().contains("IGA"), "no plain text on disk")

            // Renamed to another document's name: the authenticated data no longer matches.
            Files.copy(file, file.resolveSibling("doc2${DocumentVault.EXTENSION}"))
            assertFailsWith<DecryptionException> { vault.get("doc2") }

            raw[raw.size - 5] = (raw[raw.size - 5].toInt() xor 1).toByte()
            Files.write(file, raw)
            assertFailsWith<DecryptionException> { vault.get("doc1") }

            vault.delete("doc1")
            assertFalse(vault.exists("doc1"))
            assertFailsWith<IllegalArgumentException> { vault.put("../escape", receipt) }
        }
    }

    @Test
    fun `a private group's documents cannot be read by another user`() {
        val created = store.create(dir, "V", "perry", "Perry", "pw".toCharArray())
        created.session.addUser("marie", "Marie", Role.MEMBER, "mpw".toCharArray())
        created.session.close()
        val privatePartition = store.unlock(dir, "marie", "mpw".toCharArray()).use { marie ->
            val groupId = marie.createGroup("Marie - privé", private = true)
            val partition = marie.core.coreQueries.groups().executeAsList().first { it.id == groupId }.partition_id
            marie.vault(partition).put("therapy", receipt)
            partition
        }
        store.unlock(dir, "perry", "pw".toCharArray()).use { perry ->
            assertFailsWith<AccessDeniedException> { perry.vault(privatePartition) }
            assertTrue(Files.exists(dir.resolve(DocumentVault.DIR).resolve(privatePartition)), "the file is there, but sealed")
        }
    }
}
