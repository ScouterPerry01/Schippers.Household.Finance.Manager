package ca.schippers.hfm.desktop

import org.junit.jupiter.api.io.TempDir
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Section 3.2, Phase 5 security review: what the desktop reads from and removes in the shared transfer folder. */
class TransferFolderTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `only old reply files are removed, never the user's own files`() {
        val now = System.currentTimeMillis()
        val old = FileTime.fromMillis(now - REPLY_KEPT_MS - 60_000)
        fun file(name: String) = Files.write(temp.resolve(name), byteArrayOf(1)).also { Files.setLastModifiedTime(it, old) }
        file("to-phone-3f9a1c2e-20260801-101010-000.roostsync")
        file("to-phone-list.docx")
        file("to-phone.roostsync")
        Files.createDirectory(temp.resolve("to-phone-folder.roostsync")).also { Files.setLastModifiedTime(it, old) }
        Files.write(temp.resolve("to-phone-3f9a1c2e-20261005-101010-000.roostsync"), byteArrayOf(1))

        assertEquals(listOf("to-phone-3f9a1c2e-20260801-101010-000.roostsync"), staleReplies(temp, now).map { it.name })
    }

    @Test
    fun `a transfer file larger than any the phone writes is not read`() {
        val small = Files.write(temp.resolve("small.roostsync"), byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), readTransferFile(small))
        val huge = temp.resolve("huge.roostsync")
        // A sparse file: its size is past the limit without writing 200 MB.
        RandomAccessFile(huge.toFile(), "rw").use { it.setLength(MAX_TRANSFER_BYTES + 1) }
        assertNull(readTransferFile(huge))
    }
}
