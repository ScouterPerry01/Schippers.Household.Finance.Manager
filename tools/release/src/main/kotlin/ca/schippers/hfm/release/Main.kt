package ca.schippers.hfm.release

import ca.schippers.hfm.security.ReleaseSignature
import ca.schippers.hfm.update.Channel
import ca.schippers.hfm.update.ReleaseKey
import ca.schippers.hfm.update.UpdateCheck
import ca.schippers.hfm.update.UpdateFile
import ca.schippers.hfm.update.UpdateManifest
import ca.schippers.hfm.update.Version
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val PUBLIC_KEY_RESOURCE = "core/update/src/main/resources/hfm/update/release-key.pub"
private val SAFE_NAME = Regex("[A-Za-z0-9._+-]+")

fun main(args: Array<String>) {
    try {
        when (args.firstOrNull()) {
            "keygen" -> keygen(Path.of(args.getOrNull(1) ?: usage()))
            "sign-release" -> signRelease(
                version = args.getOrNull(1) ?: usage(),
                dir = Path.of(args.getOrNull(2) ?: usage()),
                notesEn = args.getOrNull(3)?.let(Path::of),
                notesFr = args.getOrNull(4)?.let(Path::of),
            )
            "verify" -> verify(Path.of(args.getOrNull(1) ?: usage()))
            else -> usage()
        }
    } catch (e: Exception) {
        System.err.println("error: ${e.message}")
        exitProcess(1)
    }
}

private fun usage(): Nothing {
    System.err.println(
        """
        usage: release keygen <secret key file>
               release sign-release <version> <folder> [notes-en.md] [notes-fr.md]   (key in HFM_RELEASE_KEY)
               release verify <file>                                                 (checks <file>.minisig)
        """.trimIndent(),
    )
    exitProcess(2)
}

/** Makes RANN's key pair: the secret goes only to [secretFile]; the public key into the apps. */
private fun keygen(secretFile: Path) {
    require(!secretFile.exists()) { "$secretFile already exists; a new key would orphan every installed copy" }
    val key = ReleaseSignature.SecretKey.generate()
    secretFile.toAbsolutePath().parent?.let(Files::createDirectories)
    secretFile.writeText(key.toText() + "\n")
    runCatching { Files.setPosixFilePermissions(secretFile, PosixFilePermissions.fromString("rw-------")) }
    val public = Path.of(PUBLIC_KEY_RESOURCE)
    Files.createDirectories(public.parent)
    public.writeText(key.publicKey.toText())
    println("Secret key written to ${secretFile.toAbsolutePath()} (keep it offline and add it as the HFM_RELEASE_KEY secret).")
    println("Public key ${key.publicKey.keyIdHex} written to $PUBLIC_KEY_RESOURCE:")
    print(key.publicKey.toText())
}

/**
 * Signs every file in [dir] and writes SHA256SUMS and update.json (with their signatures).
 * Files the apps can install (.deb, .rpm, .AppImage, .apk) are listed in update.json.
 */
private fun signRelease(version: String, dir: Path, notesEn: Path?, notesFr: Path?) {
    val secret = System.getenv("HFM_RELEASE_KEY")?.takeIf { it.isNotBlank() }
        ?: error("HFM_RELEASE_KEY is not set")
    val key = ReleaseSignature.SecretKey.parse(secret)
    require(key.publicKey.key.contentEquals(ReleaseKey.publicKey.key)) {
        "HFM_RELEASE_KEY does not match the public key built into the apps ($PUBLIC_KEY_RESOURCE)"
    }
    Version.parse(version)
    val files = dir.listDirectoryEntries().filter { it.isRegularFile() && !generated(it.name) }.sortedBy { it.name }
    require(files.isNotEmpty()) { "No files in $dir" }
    files.forEach { require(SAFE_NAME.matches(it.name)) { "Unsafe file name for a download address: ${it.name}" } }

    val sums = files.associateWith { sha256(it) }
    val manifest = UpdateManifest(
        version = version,
        date = LocalDate.now().toString(),
        notes = buildMap {
            notesEn?.let { put("en", it.readText().trim()) }
            notesFr?.let { put("fr", it.readText().trim()) }
        },
        files = files.mapNotNull { f ->
            kindOf(f.name)?.let { kind ->
                UpdateFile(kind.kind, f.name, "${UpdateCheck.RELEASES}/download/v$version/${f.name}", f.fileSize(), sums.getValue(f))
            }
        },
    )
    dir.resolve("SHA256SUMS").writeText(files.joinToString("") { "${sums.getValue(it)}  ${it.name}\n" })
    dir.resolve(UpdateCheck.MANIFEST).writeText(manifest.toJson() + "\n")

    // listOf: a Path is itself Iterable, so `files + path` would add its name parts instead.
    for (f in files + listOf(dir.resolve("SHA256SUMS"), dir.resolve(UpdateCheck.MANIFEST))) {
        val signature = ReleaseSignature.sign(Files.readAllBytes(f), key, "file:${f.name}\tversion:$version")
        dir.resolve("${f.name}.minisig").writeText(signature)
        println("signed ${f.name}")
    }
    println("update.json lists: ${manifest.files.joinToString { "${it.kind} ${it.name}" }}")
}

/** Checks [file] against [file].minisig with the public key built into the apps. */
private fun verify(file: Path) {
    val comment = ReleaseSignature.verify(Files.readAllBytes(file), Path.of("$file.minisig").readText(), ReleaseKey.publicKey)
    println("OK ${file.name}: $comment")
}

private fun generated(name: String) = name.endsWith(".minisig") || name == "SHA256SUMS" || name == UpdateCheck.MANIFEST

private fun kindOf(name: String): Channel? = when {
    name.endsWith(".deb") -> Channel.DEB
    name.endsWith(".rpm") -> Channel.RPM
    name.endsWith(".AppImage") -> Channel.APPIMAGE
    name.endsWith(".apk") -> Channel.APK
    else -> null
}

private fun sha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return UpdateCheck.sha256Hex(digest)
}
