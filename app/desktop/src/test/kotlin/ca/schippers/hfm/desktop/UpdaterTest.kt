package ca.schippers.hfm.desktop

import ca.schippers.hfm.update.Channel
import ca.schippers.hfm.update.UpdateFile
import ca.schippers.hfm.update.UpdateOffer
import ca.schippers.hfm.update.Version
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.prefs.Preferences
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** UPD-03 on the desktop: a download is kept only when it matches the signed list, and no `.part` is left behind. */
class UpdaterTest {

    @TempDir
    lateinit var temp: Path

    private val prefs = Preferences.userRoot().node("ca/schippers/hfm-test/updater-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() = prefs.removeNode()

    private val content = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    private fun offer(size: Long = content.size.toLong(), hash: String = sha) =
        UpdateOffer(Version.parse("9.9.9"), "2026-10-05", emptyMap(), UpdateFile("deb", "roost_9.9.9_amd64.deb", "https://example.invalid/roost_9.9.9_amd64.deb", size, hash))

    private fun updater(open: () -> InputStream) = Updater(
        prefs, Channel.DEB, { error("no check in this test") },
        object : UpdateSource {
            override fun manifest() = ByteArray(0)
            override fun signature() = ""
            override fun open(url: String) = open()
        },
        downloads = { temp },
    )

    @Test
    fun `a matching download is kept, without its part file`() = runBlocking {
        val u = updater { ByteArrayInputStream(content) }
        u.download(offer())
        assertIs<UpdateStatus.Ready>(u.status)
        assertEquals(listOf("roost_9.9.9_amd64.deb"), temp.listDirectoryEntries().map { it.fileName.toString() })
    }

    @Test
    fun `a download larger than announced leaves nothing behind`() = runBlocking {
        val u = updater { ByteArrayInputStream(content) }
        u.download(offer(size = 1000))
        assertEquals("update.badDownload", (u.status as UpdateStatus.Failed).messageKey)
        assertTrue(temp.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `a wrong hash leaves nothing behind`() = runBlocking {
        val u = updater { ByteArrayInputStream(content) }
        u.download(offer(hash = "0".repeat(64)))
        assertEquals("update.badDownload", (u.status as UpdateStatus.Failed).messageKey)
        assertTrue(temp.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `a network error mid-download leaves nothing behind`() = runBlocking {
        val u = updater {
            object : InputStream() {
                private var sent = 0
                override fun read(): Int = if (sent++ < 100_000) 7 else throw IOException("Connection reset")
            }
        }
        u.download(offer())
        assertEquals("update.downloadFailed", (u.status as UpdateStatus.Failed).messageKey)
        assertTrue(temp.listDirectoryEntries().isEmpty())
        assertTrue(Files.notExists(temp.resolve("roost_9.9.9_amd64.deb.part")))
    }
}
