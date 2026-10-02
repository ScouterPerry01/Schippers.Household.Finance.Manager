package ca.schippers.hfm.desktop

import ca.schippers.hfm.books.Books
import ca.schippers.hfm.books.CaptureConverter
import ca.schippers.hfm.books.DeviceNotPairedException
import ca.schippers.hfm.books.PairingRejectedException
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.ocr.desktop.PdfPages
import ca.schippers.hfm.security.DecryptionException
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PairResponse
import ca.schippers.hfm.sync.SyncClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.BindException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Listens for paired phones on the local network while the household is unlocked (section 3).
 * Every body is sealed with the phone's pair key, so plain HTTP is enough; requests are handled
 * one at a time. Stopped when the household locks.
 */
class SyncServer(private val books: Books, private val today: () -> LocalDate, private val onChange: () -> Unit) : AutoCloseable {

    private var server: HttpServer? = null

    /** The port actually used: the usual one, or the next free one. */
    var port: Int = 0
        private set

    val running: Boolean get() = server != null

    private val json = Json { ignoreUnknownKeys = true }

    private val converter = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>): ByteArray = PdfPages.fromJpegs(pages)
        override fun recognize(content: ByteArray): OcrResult? = runCatching { DesktopOcr.reader.read(content).result }.getOrNull()
    }

    /** @throws IOException when no port could be opened. */
    fun start() {
        if (server != null) return
        var last: IOException? = null
        for (candidate in SyncClient.DEFAULT_PORT until SyncClient.DEFAULT_PORT + 10) {
            try {
                val s = HttpServer.create(InetSocketAddress(candidate), 4)
                s.createContext(SyncClient.PAIR_PATH) { ex -> handle(ex) { pair(ex) } }
                s.createContext(SyncClient.SYNC_PATH) { ex -> handle(ex) { sync(ex) } }
                s.executor = Executors.newSingleThreadExecutor { r -> Thread(r, "hfm-sync").apply { isDaemon = true } }
                s.start()
                server = s
                port = candidate
                return
            } catch (e: BindException) {
                last = e
            }
        }
        throw last ?: IOException("No free port")
    }

    private fun pair(ex: HttpExchange): Pair<Int, ByteArray> {
        val request = json.decodeFromString(PairRequest.serializer(), ex.requestBody.use { it.readNBytes(MAX_PAIR_BYTES) }.decodeToString())
        val response = books.sync.pair(request, System.currentTimeMillis())
        onChange()
        return 200 to json.encodeToString(PairResponse.serializer(), response).encodeToByteArray()
    }

    private fun sync(ex: HttpExchange): Pair<Int, ByteArray> {
        val device = ex.requestHeaders.getFirst(SyncClient.DEVICE_HEADER) ?: return 400 to ByteArray(0)
        val body = ex.requestBody.use { it.readNBytes(MAX_SYNC_BYTES) }
        val answer = books.sync.handle(device, body, converter, System.currentTimeMillis(), today())
        onChange()
        return 200 to answer
    }

    private fun handle(ex: HttpExchange, block: () -> Pair<Int, ByteArray>) {
        val (status, body) = if (ex.requestMethod != "POST") {
            405 to ByteArray(0)
        } else {
            try {
                block()
            } catch (_: DeviceNotPairedException) {
                403 to ByteArray(0)
            } catch (_: PairingRejectedException) {
                401 to ByteArray(0)
            } catch (_: DecryptionException) {
                400 to ByteArray(0)
            } catch (_: Exception) {
                500 to ByteArray(0)
            }
        }
        try {
            ex.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
        } finally {
            ex.close()
        }
    }

    override fun close() {
        server?.stop(0)
        (server?.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
        server = null
    }

    companion object {
        private const val MAX_PAIR_BYTES = 64 * 1024
        private const val MAX_SYNC_BYTES = 200 * 1024 * 1024

        /**
         * The computer's address on the home network, for the QR code: a private IPv4 address of a
         * network adapter that is up, preferring the usual home ranges over virtual adapters.
         */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { nic to it } }
                .sortedBy { (nic, addr) ->
                    val name = (nic.displayName + nic.name).lowercase()
                    val virtual = listOf("virtual", "vmware", "hyper-v", "vethernet", "wsl", "docker", "vbox").any { it in name }
                    (if (virtual) 10 else 0) + (if (addr.hostAddress.startsWith("192.168.")) 0 else 1)
                }
                .firstOrNull()?.second?.hostAddress
        }.getOrNull()
    }
}
