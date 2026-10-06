package ca.schippers.hfm.desktop

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The unencrypted PDF a summary is printed from does not wait for the app to close to be removed. */
class PrintFilesTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `the print file is removed after the delay`() {
        val file = File(temp.toFile(), "hfm-summary-1.pdf").apply { writeText("%PDF-1.7 health summary") }
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            PrintFiles.removeLater(file, delayMs = 50, on = executor, retryMs = 20)
            assertTrue(file.exists(), "kept while the printer may still read it")
            Thread.sleep(400)
            assertFalse(file.exists())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a file still in use is tried again until it can be removed`() {
        // A folder with something in it cannot be deleted, as Windows refuses a file a viewer holds open.
        val busy = File(temp.toFile(), "hfm-report-2.pdf").apply { mkdir() }
        val inside = File(busy, "held").apply { writeText("x") }
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            PrintFiles.removeLater(busy, delayMs = 10, on = executor, retryMs = 20)
            Thread.sleep(150)
            assertTrue(busy.exists(), "still held")
            inside.delete()
            executor.awaitTermination(300, TimeUnit.MILLISECONDS)
            assertFalse(busy.exists(), "removed once released")
        } finally {
            executor.shutdownNow()
        }
    }
}
