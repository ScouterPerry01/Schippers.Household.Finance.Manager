package ca.schippers.hfm.sync

import kotlinx.serialization.Serializable

/**
 * Section 3.2: a sealed bundle as a file, for transfer away from home (a cloud folder the phone and
 * computer both sync) or by email or USB. The sealed body is exactly what travels over Wi-Fi; the
 * short header in front of it only says whose it is, so the desktop can find the pair key. The
 * header is not secret, and changing it makes the body fail to open, since the same ids are bound
 * into its encryption.
 */
object BundleFile {

    /** Every bundle file name ends with this; the desktop and phone look for nothing else. */
    const val EXTENSION = "roostsync"

    private val MAGIC = "ROOSTSYNC1\n".encodeToByteArray()

    @Serializable
    data class Header(val desktopId: String, val deviceId: String, val direction: Direction, val createdAtMillis: Long)

    class NotABundleException : IllegalArgumentException("Not a RANN's Roost transfer file")

    fun write(header: Header, sealed: ByteArray): ByteArray =
        MAGIC + SyncJson.encodeToString(Header.serializer(), header).encodeToByteArray() + '\n'.code.toByte() + sealed

    /** @throws NotABundleException when the bytes are not a bundle file. */
    fun read(bytes: ByteArray): Pair<Header, ByteArray> {
        if (bytes.size < MAGIC.size || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABundleException()
        val end = (MAGIC.size until minOf(bytes.size, MAGIC.size + 4096)).firstOrNull { bytes[it] == '\n'.code.toByte() } ?: throw NotABundleException()
        val header = runCatching { SyncJson.decodeFromString(Header.serializer(), bytes.copyOfRange(MAGIC.size, end).decodeToString()) }.getOrElse { throw NotABundleException() }
        return header to bytes.copyOfRange(end + 1, bytes.size)
    }

    /**
     * The file's name: which way it goes, the first characters of the device id, and when it was made,
     * such as `to-desktop-3f9a1c2e-20261005-142233.roostsync`.
     */
    fun name(header: Header): String {
        val time = java.time.Instant.ofEpochMilli(header.createdAtMillis).atZone(java.time.ZoneOffset.UTC)
        val stamp = "%04d%02d%02d-%02d%02d%02d-%03d".format(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, time.second, time.nano / 1_000_000)
        val way = if (header.direction == Direction.TO_DESKTOP) TO_DESKTOP else TO_PHONE
        return "$way-${devicePart(header.deviceId)}-$stamp.$EXTENSION"
    }

    /** Files for the desktop start with this; replies for a phone with [TO_PHONE] and its [devicePart]. */
    const val TO_DESKTOP = "to-desktop"
    const val TO_PHONE = "to-phone"

    fun devicePart(deviceId: String): String = deviceId.filter { it.isLetterOrDigit() }.take(8).lowercase()

    /** The phone's request as a file. */
    fun request(desktop: PairedDesktop, request: SyncRequest, nowMillis: Long): Pair<String, ByteArray> {
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, SyncCrypto.unb64(desktop.pairKey), desktop.desktopId, desktop.deviceId, Direction.TO_DESKTOP)
        val header = Header(desktop.desktopId, desktop.deviceId, Direction.TO_DESKTOP, nowMillis)
        return name(header) to write(header, sealed)
    }

    /**
     * The phone opens a reply file the desktop left in the folder.
     * @throws NotABundleException or [ca.schippers.hfm.security.DecryptionException] when it is not a reply for this phone.
     */
    fun reply(desktop: PairedDesktop, bytes: ByteArray): SyncResponse {
        val (header, sealed) = read(bytes)
        if (header.direction != Direction.TO_PHONE || header.deviceId != desktop.deviceId || header.desktopId != desktop.desktopId) throw NotABundleException()
        return SyncCrypto.open(SyncResponse.serializer(), sealed, SyncCrypto.unb64(desktop.pairKey), desktop.desktopId, desktop.deviceId, Direction.TO_PHONE)
    }
}
