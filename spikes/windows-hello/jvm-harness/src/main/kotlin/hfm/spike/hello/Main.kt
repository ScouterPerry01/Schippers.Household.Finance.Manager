package hfm.spike.hello

import com.sun.jna.Native
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities

/**
 * Spike harness. Non-interactive:  available | dpapi-roundtrip | jna-probe
 * Interactive (Hello prompt):      verify [--window] | create-key <name> | derive-key <name>
 *                                  ngc-roundtrip <name> [--window] | cleanup <name>
 * --window opens a small Swing window and passes its HWND as owner, imitating the real app
 * (in Compose Desktop use `ComposeWindow.windowHandle` instead of JNA).
 */
fun main(args: Array<String>) {
    val exe = Path.of(System.getProperty("hfm.hello.exe") ?: error("set -Dhfm.hello.exe=<path to hfm-hello.exe>"))
    val hello = WindowsHelloClient(exe)
    val withWindow = "--window" in args
    val rest = args.filter { it != "--window" }
    val cmd = rest.firstOrNull() ?: "available"
    val name = rest.getOrNull(1) ?: "hfm-spike"

    var frame: JFrame? = null
    val hwnd: Long? = if (withWindow) {
        val f = openTestWindow().also { frame = it }
        Native.getWindowID(f).also { println("test window HWND = $it") }
    } else null

    try {
        when (cmd) {
            "available" -> println(hello.available())

            "verify" -> {
                val r = hello.verify("Unlock Household Finance Manager", hwnd)
                println(r)
                println(if (r.ok) "VERIFIED -> app would unlock" else "NOT verified (${r.status}) -> stay locked / fall back to password")
            }

            "create-key" -> println(hello.createKey(name))

            "derive-key" -> {
                // Run twice: the fingerprint must be identical (deterministic RSA-PKCS#1 v1.5 signature).
                val (key, r) = hello.deriveKey(name)
                println(r.copy(fields = r.fields - "key"))
                key?.let { println("derived key fingerprint = ${fp(it)} (signaturePkcs1v15=${r.fields["signaturePkcs1v15"]})") }
            }

            "ngc-roundtrip" -> {
                val created = hello.ngcCreate(name, hwnd)
                println("ngc-create: $created")
                if (!created.ok && created.exitCode != WindowsHelloClient.EXIT_KEY_EXISTS) return
                val vaultKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val (blob, wr) = hello.ngcWrap(name, vaultKey)
                println("ngc-wrap: exit=${wr.exitCode} status=${wr.status} blobLen=${blob?.length}")
                if (blob == null) return
                val (unwrapped, ur) = hello.ngcUnwrap(name, blob, hwnd)
                println("ngc-unwrap: exit=${ur.exitCode} status=${ur.status} hresult=${ur.fields["hresult"]}")
                println("round-trip match = ${unwrapped?.contentEquals(vaultKey)}  (vault key fp ${fp(vaultKey)})")
            }

            "cleanup" -> {
                println("delete-key: ${hello.deleteKey(name)}")
                println("ngc-delete: ${hello.ngcDelete(name)}")
            }

            "dpapi-roundtrip" -> dpapiRoundTrip()

            "jna-probe" -> jnaProbe()

            else -> println("unknown command $cmd")
        }
    } finally {
        frame?.let { SwingUtilities.invokeAndWait { it.dispose() } }
    }
}

private fun openTestWindow(): JFrame {
    var f: JFrame? = null
    SwingUtilities.invokeAndWait {
        f = JFrame("HFM Windows Hello spike").apply {
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            add(JLabel("  The Hello prompt should appear in front of / centred on this window."), BorderLayout.CENTER)
            size = Dimension(520, 320)
            setLocationRelativeTo(null)
            isVisible = true
            toFront()
        }
    }
    Thread.sleep(300) // let the window become foreground
    return f!!
}

/** Option B building block: DPAPI via JNA. Silent — bound to the Windows user account, no user presence. */
private fun dpapiRoundTrip() {
    val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val entropy = "HouseholdFinanceManager/dpapi/v1".toByteArray()
    val blob = Crypt32Util.cryptProtectData(secret, entropy, 0, "hfm", null)
    val back = Crypt32Util.cryptUnprotectData(blob, entropy, 0, null)
    println("DPAPI round-trip: blob=${blob.size} bytes, match=${back.contentEquals(secret)} (no prompt - that is the point and the weakness)")
}

/** Option C feasibility: ncrypt.dll is a flat C API, so the NGC (Passport KSP) path works from JNA without a helper. */
private interface NCrypt : StdCallLibrary {
    fun NCryptOpenStorageProvider(phProvider: PointerByReference, pszProviderName: String, dwFlags: Int): Int
    fun NCryptOpenKey(hProvider: com.sun.jna.Pointer, phKey: PointerByReference, pszKeyName: String, dwLegacyKeySpec: Int, dwFlags: Int): Int
    fun NCryptFreeObject(h: com.sun.jna.Pointer): Int

    companion object {
        val INSTANCE: NCrypt = Native.load("ncrypt", NCrypt::class.java, W32APIOptions.UNICODE_OPTIONS)
    }
}

private fun jnaProbe() {
    val prov = PointerByReference()
    val hr = NCrypt.INSTANCE.NCryptOpenStorageProvider(prov, "Microsoft Passport Key Storage Provider", 0)
    println("JNA NCryptOpenStorageProvider(MS_NGC) hr=0x%08X".format(hr))
    if (hr != 0) return
    val key = PointerByReference()
    val sid = com.sun.jna.platform.win32.Advapi32Util.getAccountByName(System.getProperty("user.name")).sidString
    val khr = NCrypt.INSTANCE.NCryptOpenKey(prov.value, key, "$sid//HouseholdFinanceManager//hfm-spike", 0, 0)
    println("JNA NCryptOpenKey(hfm-spike) hr=0x%08X (0x8009000D/0x80090016 = no key yet, 0 = exists)".format(khr))
    if (khr == 0) NCrypt.INSTANCE.NCryptFreeObject(key.value)
    NCrypt.INSTANCE.NCryptFreeObject(prov.value)
}

private fun fp(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).take(8).joinToString("") { "%02x".format(it) }
