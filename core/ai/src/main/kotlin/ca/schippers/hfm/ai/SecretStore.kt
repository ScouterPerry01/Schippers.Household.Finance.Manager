package ca.schippers.hfm.ai

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference

/**
 * Where a user's AI key is kept (AI-02): the operating system's own secret store, never the
 * household files. Keys are stored per household and user, so each user brings their own.
 */
interface SecretStore {
    /** Shown in settings: "Windows Credential Manager", "Secret Service" or "this session only". */
    val kind: Kind

    fun get(name: String): String?

    /** Throws [SecretStoreException] when the store refuses, for example with no keyring running. */
    fun put(name: String, secret: String)

    fun delete(name: String)

    enum class Kind { WINDOWS, SECRET_SERVICE, SESSION }

    companion object {
        /** The store for this computer, or one that lasts until the app closes when there is none. */
        fun forThisComputer(): SecretStore {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.startsWith("windows") -> runCatching { WindowsCredentials() }.getOrNull()
                os.startsWith("linux") -> runCatching { LinuxSecretService() }.getOrNull()
                else -> null
            } ?: SessionSecrets()
        }

        /** The name a household user's key for [provider] is kept under. */
        fun keyName(provider: String, householdId: String, userId: String) = "RANN's Roost/ai/$provider/$householdId/$userId"
    }
}

class SecretStoreException(message: String) : Exception(message)

/** Keeps keys in memory only: the user enters the key again after restarting. */
class SessionSecrets : SecretStore {
    private val keys = HashMap<String, String>()
    override val kind = SecretStore.Kind.SESSION
    override fun get(name: String) = synchronized(keys) { keys[name] }
    override fun put(name: String, secret: String) = synchronized(keys) { keys[name] = secret }
    override fun delete(name: String) { synchronized(keys) { keys.remove(name) } }
}

// --- Windows Credential Manager ---------------------------------------------------------------------

@Structure.FieldOrder(
    "Flags", "Type", "TargetName", "Comment", "LastWrittenLow", "LastWrittenHigh", "CredentialBlobSize", "CredentialBlob",
    "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName",
)
class WinCredential(p: Pointer? = null) : Structure(p) {
    @JvmField var Flags = 0
    @JvmField var Type = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWrittenLow = 0
    @JvmField var LastWrittenHigh = 0
    @JvmField var CredentialBlobSize = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist = 0
    @JvmField var AttributeCount = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null

    init {
        if (p != null) read()
    }
}

interface Advapi32 : Library {
    fun CredWriteW(credential: WinCredential, flags: Int): Boolean
    fun CredReadW(target: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
    fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean
    fun CredFree(buffer: Pointer)
}

/** Generic credentials of the signed-in Windows user, visible in Control Panel > Credential Manager. */
class WindowsCredentials : SecretStore {
    private val api: Advapi32 = Native.load("Advapi32", Advapi32::class.java)
    override val kind = SecretStore.Kind.WINDOWS

    override fun get(name: String): String? {
        val ref = PointerByReference()
        if (!api.CredReadW(WString(name), CRED_TYPE_GENERIC, 0, ref)) return null
        try {
            val cred = WinCredential(ref.value)
            return cred.CredentialBlob?.getByteArray(0, cred.CredentialBlobSize)?.decodeToString()
        } finally {
            api.CredFree(ref.value)
        }
    }

    override fun put(name: String, secret: String) {
        val bytes = secret.encodeToByteArray()
        val blob = Memory(bytes.size.toLong().coerceAtLeast(1)).apply { write(0, bytes, 0, bytes.size) }
        try {
            val cred = WinCredential().apply {
                Type = CRED_TYPE_GENERIC
                TargetName = WString(name)
                Comment = WString("RANN's Roost: AI reading key")
                CredentialBlobSize = bytes.size
                CredentialBlob = blob
                Persist = CRED_PERSIST_LOCAL_MACHINE
                UserName = WString("RANN's Roost")
            }
            if (!api.CredWriteW(cred, 0)) throw SecretStoreException("Credential Manager refused the key (error ${Native.getLastError()})")
        } finally {
            blob.clear()
            blob.close()
        }
    }

    override fun delete(name: String) {
        api.CredDeleteW(WString(name), CRED_TYPE_GENERIC, 0)
    }

    private companion object {
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
    }
}

// --- Linux Secret Service (libsecret) ---------------------------------------------------------------

@Structure.FieldOrder("name", "type")
class SecretAttribute : Structure() {
    @JvmField var name: String? = null
    @JvmField var type = 0
}

@Structure.FieldOrder("name", "flags", "attributes", "reserved", "reserved1", "reserved2", "reserved3", "reserved4", "reserved5", "reserved6", "reserved7")
class SecretSchema : Structure() {
    @JvmField var name: String? = null
    @JvmField var flags = 0
    @Suppress("UNCHECKED_CAST")
    @JvmField var attributes: Array<SecretAttribute> = SecretAttribute().toArray(32) as Array<SecretAttribute>
    @JvmField var reserved = 0
    @JvmField var reserved1: Pointer? = null
    @JvmField var reserved2: Pointer? = null
    @JvmField var reserved3: Pointer? = null
    @JvmField var reserved4: Pointer? = null
    @JvmField var reserved5: Pointer? = null
    @JvmField var reserved6: Pointer? = null
    @JvmField var reserved7: Pointer? = null
}

interface LibSecret : Library {
    fun secret_password_store_sync(schema: SecretSchema, collection: String?, label: String, password: String, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?): Boolean
    fun secret_password_lookup_sync(schema: SecretSchema, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?): Pointer?
    fun secret_password_clear_sync(schema: SecretSchema, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?): Boolean
    fun secret_password_free(password: Pointer)
}

interface GLib : Library {
    fun g_error_free(error: Pointer)
}

/** The desktop's keyring (GNOME Keyring, KWallet) through the Secret Service and libsecret. */
class LinuxSecretService : SecretStore {
    private val lib: LibSecret = Native.load("secret-1", LibSecret::class.java)
    private val glib: GLib = Native.load("glib-2.0", GLib::class.java)
    private val schema = SecretSchema().apply {
        name = "ca.ranns.roost.Key"
        attributes[0].name = "application"
        attributes[1].name = "key"
        write()
    }
    override val kind = SecretStore.Kind.SECRET_SERVICE

    override fun get(name: String): String? {
        val error = PointerByReference()
        val found = lib.secret_password_lookup_sync(schema, null, error, "application", APP, "key", name, null)
        failed(error)?.let { throw SecretStoreException(it) }
        return found?.let { p -> try { p.getString(0, "UTF-8") } finally { lib.secret_password_free(p) } }
    }

    override fun put(name: String, secret: String) {
        val error = PointerByReference()
        val ok = lib.secret_password_store_sync(schema, null, "RANN's Roost: AI reading key", secret, null, error, "application", APP, "key", name, null)
        failed(error)?.let { throw SecretStoreException(it) }
        if (!ok) throw SecretStoreException("the keyring refused the key")
    }

    override fun delete(name: String) {
        val error = PointerByReference()
        lib.secret_password_clear_sync(schema, null, error, "application", APP, "key", name, null)
        failed(error)
    }

    /** The GError's message, freed; null when there was none. */
    private fun failed(error: PointerByReference): String? {
        val e = error.value ?: return null
        // struct GError { GQuark domain; gint code; gchar *message; }
        val message = e.getPointer(8)?.getString(0, "UTF-8") ?: "Secret Service error"
        glib.g_error_free(e)
        return message
    }

    private companion object {
        const val APP = "RANN's Roost"
    }
}
