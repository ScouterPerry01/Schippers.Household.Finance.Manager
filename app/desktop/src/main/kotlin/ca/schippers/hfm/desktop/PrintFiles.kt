package ca.schippers.hfm.desktop

import java.awt.Desktop
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The PDF a summary or report is printed from. The system's print path needs a file, which is not
 * encrypted, so it is kept only as long as the printer or viewer needs it: removed a short while
 * after it is handed over (once the job is spooled; longer when it is only opened in a viewer, for
 * the user to print from there), tried again each minute while the system still holds it open,
 * and in any case when the app closes.
 */
internal object PrintFiles {

    /** After printing: the system's PDF handler has read and spooled the file by then. */
    const val PRINTED_DELAY_MS = 2 * 60_000L

    /** After opening in a viewer, where the user prints it themselves. */
    const val OPENED_DELAY_MS = 10 * 60_000L

    /** Further tries, a minute apart, while the file is still in use (Windows refuses to delete it). */
    const val RETRIES = 30

    private val scheduler: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "print-file-cleanup").apply { isDaemon = true } }
    }

    /** A new temporary PDF, removed when the app closes if nothing removed it before. */
    fun create(prefix: String): File = File.createTempFile(prefix, ".pdf").apply { deleteOnExit() }

    /** Prints [file] through the system (or opens it where there is no print action), then removes it. */
    fun printOrOpen(file: File) {
        val desktop = Desktop.getDesktop()
        val printed = desktop.isSupported(Desktop.Action.PRINT)
        try {
            if (printed) desktop.print(file) else desktop.open(file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        removeLater(file, if (printed) PRINTED_DELAY_MS else OPENED_DELAY_MS)
    }

    /** Removes [file] after [delayMs], then each minute while it cannot be removed, up to [RETRIES] times. */
    fun removeLater(file: File, delayMs: Long, on: ScheduledExecutorService = scheduler, retryMs: Long = 60_000L) {
        fun attempt(left: Int) {
            if (!file.exists() || file.delete() || left <= 0) return
            on.schedule({ attempt(left - 1) }, retryMs, TimeUnit.MILLISECONDS)
        }
        on.schedule({ attempt(RETRIES) }, delayMs, TimeUnit.MILLISECONDS)
    }
}
