package ca.schippers.hfm.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.schippers.hfm.security.ReleaseSignature
import ca.schippers.hfm.update.Channel
import ca.schippers.hfm.update.UpdateCheck
import ca.schippers.hfm.update.UpdateOffer
import ca.schippers.hfm.update.UpdateRejected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties
import java.util.prefs.Preferences

/** This copy's version, written into the app by the build (gradle.properties). */
object AppVersion {
    val current: String by lazy {
        Properties().apply {
            AppVersion::class.java.getResourceAsStream("/hfm/version.properties")?.use { load(it) }
        }.getProperty("version") ?: "0.0.0"
    }
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val checkedAt: Long) : UpdateStatus
    data class Available(val offer: UpdateOffer) : UpdateStatus
    data class Downloading(val offer: UpdateOffer, val fraction: Float) : UpdateStatus

    /** Downloaded and checked. An AppImage has replaced itself and needs a restart. */
    data class Ready(val offer: UpdateOffer, val file: Path, val replacedAppImage: Boolean) : UpdateStatus

    /** [messageKey] is user text; [detail] says what went wrong. */
    data class Failed(val messageKey: String, val detail: String) : UpdateStatus
}

/** Where releases come from: GitHub Releases, or a folder in the demo. */
interface UpdateSource {
    fun manifest(): ByteArray
    fun signature(): String
    fun open(url: String): InputStream
}

/**
 * DIST-05 and SEC-08 on the desktop. Only Linux packages from GitHub Releases check for updates:
 * the Microsoft Store and Flathub update their own installs, and a build from source has none.
 * The owner decided (2026-10-03) to ask once on first start; nothing is requested before the answer.
 */
class Updater(
    private val prefs: Preferences,
    val channel: Channel?,
    private val check: () -> UpdateCheck,
    private val source: UpdateSource,
    val current: String = AppVersion.current,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where a .deb or .rpm is saved; the user's Downloads folder unless given. */
    private val downloads: () -> Path = ::downloadsFolder,
) {
    /** True or false once the user answered; null while not asked yet. */
    var enabled: Boolean? by mutableStateOf(prefs.get(PREF_CHECK, null)?.let { it == "on" })
        private set

    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set

    /** Checks and downloads the user starts carry on whichever screen is shown. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startCheck() {
        scope.launch { checkNow() }
    }

    fun startDownload(offer: UpdateOffer) {
        scope.launch { download(offer) }
    }

    /** Whether to ask the first-start question now. */
    val shouldAsk: Boolean get() = channel != null && enabled == null

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.put(PREF_CHECK, if (on) "on" else "off")
        if (!on && status !is UpdateStatus.Downloading) status = UpdateStatus.Idle
    }

    /** Checks once a day while the app runs, if the user turned checks on. */
    suspend fun checkIfDue() {
        if (channel == null || enabled != true) return
        if (clock() - prefs.getLong(PREF_LAST_CHECK, 0) < DAY_MILLIS) return
        checkNow()
    }

    suspend fun checkNow() {
        val channel = channel ?: return
        status = UpdateStatus.Checking
        status = try {
            val offer = withContext(Dispatchers.IO) { check().evaluate(source.manifest(), source.signature(), current, channel) }
            prefs.putLong(PREF_LAST_CHECK, clock())
            if (offer == null) UpdateStatus.UpToDate(clock()) else UpdateStatus.Available(offer)
        } catch (e: CancellationException) {
            status = UpdateStatus.Idle
            throw e
        } catch (e: UpdateRejected) {
            UpdateStatus.Failed("update.rejected", e.message.orEmpty())
        } catch (e: Exception) {
            UpdateStatus.Failed("update.unreachable", e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Downloads [offer] beside its final place, checks its size and SHA-256 against the signed
     * manifest, and only then keeps it (SEC-08: checked before install). An AppImage then replaces
     * the running file, so the launcher keeps working; a .deb or .rpm waits in Downloads.
     */
    suspend fun download(offer: UpdateOffer) {
        status = UpdateStatus.Downloading(offer, 0f)
        status = try {
            withContext(Dispatchers.IO) {
                val appImage = System.getenv("APPIMAGE")?.takeIf { channel == Channel.APPIMAGE }?.let(Path::of)
                val dir = appImage?.parent ?: downloads()
                Files.createDirectories(dir)
                val part = dir.resolve(offer.file.name + ".part")
                // Whatever goes wrong before the file is in place (too large, a bad hash, a network
                // or disk error, the user cancelling), no partial download is left behind.
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    source.open(offer.file.url).use { input ->
                        Files.newOutputStream(part).use { out ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                size += n
                                if (size > offer.file.size) throw UpdateRejected("The download is larger than announced")
                                digest.update(buffer, 0, n)
                                out.write(buffer, 0, n)
                                status = UpdateStatus.Downloading(offer, size.toFloat() / offer.file.size)
                            }
                        }
                    }
                    UpdateCheck.verifyDownload(offer.file, size, UpdateCheck.sha256Hex(digest))
                    if (appImage != null) {
                        part.toFile().setExecutable(true)
                        Files.move(part, appImage, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                        UpdateStatus.Ready(offer, appImage, replacedAppImage = true)
                    } else {
                        val file = dir.resolve(offer.file.name)
                        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING)
                        UpdateStatus.Ready(offer, file, replacedAppImage = false)
                    }
                } catch (e: Throwable) {
                    runCatching { Files.deleteIfExists(part) }
                    throw e
                }
            }
        } catch (e: CancellationException) {
            status = UpdateStatus.Available(offer)
            throw e
        } catch (e: UpdateRejected) {
            UpdateStatus.Failed("update.badDownload", e.message.orEmpty())
        } catch (e: Exception) {
            UpdateStatus.Failed("update.downloadFailed", e.message ?: e.javaClass.simpleName)
        }
    }

    /** The terminal command that installs a downloaded package, for people who prefer it. */
    fun installCommand(file: Path): String? = when (channel) {
        Channel.DEB -> "sudo apt install \"${file.toAbsolutePath()}\""
        Channel.RPM -> "sudo dnf install \"${file.toAbsolutePath()}\""
        else -> null
    }

    companion object {
        private const val PREF_CHECK = "updates.check"
        private const val PREF_LAST_CHECK = "updates.lastCheck"
        private const val DAY_MILLIS = 24 * 60 * 60_000L

        /** The real updater; in the demo, `-Pupdate=<folder>` reads a test release from that folder. */
        fun create(prefs: Preferences, demo: Boolean): Updater {
            val folder = System.getProperty("hfm.demo.update")?.takeIf { demo }?.let(Path::of)
            if (folder != null) {
                val key = ReleaseSignature.PublicKey.parse(Files.readString(folder.resolve("release-key.pub")))
                return Updater(prefs, Channel.DEB, { UpdateCheck(key) }, FolderSource(folder), downloads = { folder.resolve("downloads") })
            }
            return Updater(prefs, detectChannel(), UpdateCheck::release, GitHubSource)
        }

        /** Which Linux package this copy is, or null when it does not check (see the class). */
        fun detectChannel(env: Map<String, String> = System.getenv()): Channel? {
            if (!System.getProperty("os.name").lowercase().startsWith("linux")) return null
            if (env["FLATPAK_ID"] != null) return null
            if (env["APPIMAGE"] != null) return Channel.APPIMAGE
            // Set by the installed app's launcher; absent when run from Gradle.
            if (System.getProperty("jpackage.app-path") == null) return null
            return when {
                Files.exists(Path.of("/var/lib/dpkg/info/ranns-roost.list")) -> Channel.DEB
                Files.isDirectory(Path.of("/var/lib/rpm")) || Files.isDirectory(Path.of("/usr/lib/sysimage/rpm")) -> Channel.RPM
                else -> null
            }
        }
    }
}

private fun downloadsFolder(): Path {
    val home = Path.of(System.getProperty("user.home"))
    return home.resolve("Downloads").takeIf(Files::isDirectory) ?: home
}

/** GitHub Releases over HTTPS, never redirected to plain HTTP (SEC-06). */
private object GitHubSource : UpdateSource {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()

    override fun manifest(): ByteArray = small(UpdateCheck.MANIFEST_URL)
    override fun signature(): String = small(UpdateCheck.SIGNATURE_URL).toString(Charsets.UTF_8)

    override fun open(url: String): InputStream {
        require(url.startsWith("https://")) { "Only HTTPS is allowed" }
        val response = client.send(request(url, Duration.ofMinutes(30)), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private fun small(url: String): ByteArray = open(url).use { body ->
        body.readNBytes(UpdateCheck.MAX_MANIFEST_BYTES + 1).also {
            if (it.size > UpdateCheck.MAX_MANIFEST_BYTES) throw UpdateRejected("The update list is too large")
        }
    }

    private fun request(url: String, timeout: Duration) =
        HttpRequest.newBuilder(URI(url)).timeout(timeout).header("User-Agent", "RANNsRoost/${AppVersion.current}").GET().build()
}

/** The demo's test release: update.json, its signature and the test public key in one folder. */
private class FolderSource(private val folder: Path) : UpdateSource {
    override fun manifest(): ByteArray = Files.readAllBytes(folder.resolve(UpdateCheck.MANIFEST))
    override fun signature(): String = Files.readString(folder.resolve(UpdateCheck.MANIFEST + ".minisig"))
    override fun open(url: String): InputStream = Files.newInputStream(folder.resolve(url.substringAfterLast('/')))
}
