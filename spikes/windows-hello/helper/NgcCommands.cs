using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Security.Principal;

namespace HfmHello;

/// Windows Hello key via CNG: the "Microsoft Passport Key Storage Provider" (MS_NGC_KEY_STORAGE_PROVIDER in ncrypt.h).
/// The private key lives in the user's Hello container (TPM-backed when a TPM is present); every private-key
/// operation needs a Hello gesture. Unlike KeyCredentialManager this
///   * supports DECRYPT, so we can wrap a random secret (the vault key) instead of deriving one from a signature, and
///   * accepts an owner HWND (NCRYPT_WINDOW_HANDLE_PROPERTY) + prompt text (NCRYPT_USE_CONTEXT_PROPERTY),
///     so the prompt is parented to the app window.
/// Same approach as KeePassWinHello. All calls are plain C exports of ncrypt.dll => directly portable to JNA.
internal static unsafe class NgcCommands
{
    private const string Domain = "HouseholdFinanceManager";
    private const string SubDomain = "";

    public static string CurrentUserSid() => WindowsIdentity.GetCurrent().User!.Value;

    /// NGC key names have the form "{SID}//{domain}/{subdomain}/{name}" (convention used by Windows for app keys).
    public static string KeyName(string name) => $"{CurrentUserSid()}//{Domain}/{SubDomain}/{name}";

    public static bool ProviderAvailable(out int hr)
    {
        hr = Native.NCryptOpenStorageProvider(out IntPtr prov, Native.MS_NGC_KEY_STORAGE_PROVIDER, 0);
        if (hr != 0) return false;
        Native.NCryptFreeObject(prov);
        return true;
    }

    public static Result Create(Options o)
    {
        string name = o.Arg(0, "name");
        using var prov = OpenProvider();
        uint flags = o.Replace ? Native.NCRYPT_OVERWRITE_KEY_FLAG : 0;
        int hr = Native.NCryptCreatePersistedKey(prov.H, out IntPtr key, Native.NCRYPT_RSA_ALGORITHM, KeyName(name), 0, flags);
        if (hr == Native.NTE_EXISTS) return Result.Fail(ExitCodes.KeyExists, "KeyExists", "NGC key already exists", hr);
        Check(hr, "NCryptCreatePersistedKey");
        using var k = new Handle(key);

        Check(Native.SetDword(key, Native.NCRYPT_LENGTH_PROPERTY, 2048), "set Length");
        Check(Native.SetDword(key, Native.NCRYPT_KEY_USAGE_PROPERTY, Native.NCRYPT_ALLOW_ALL_USAGES), "set Key Usage");
        // Best effort (undocumented): force a Hello gesture on every use instead of NGC's post-logon auth caching.
        int cacheHr = Native.SetDword(key, Native.NCRYPT_NGC_CACHE_TYPE_PROPERTY, Native.NCRYPT_NGC_CACHE_TYPE_PROPERTY_AUTH_MANDATORY_FLAG);
        SetPromptProperties(key, o, "Set up Windows Hello unlock for Household Finance Manager");

        hr = Native.NCryptFinalizeKey(key, 0); // <- Hello prompt
        if (hr != 0) return MapNcryptError(hr, "NCryptFinalizeKey");

        return Result.Of(ExitCodes.Ok, "Created")
            .Set("keyName", KeyName(name))
            .Set("authMandatory", cacheHr == 0)
            .Set("publicKeySha256", Convert.ToHexString(SHA256.HashData(ExportPublic(key))));
    }

    /// Encrypts a caller-supplied secret (base64 on stdin) with the key's public half. No Hello prompt.
    public static Result Wrap(Options o)
    {
        string name = o.Arg(0, "name");
        byte[] secret = Options.ReadBase64Stdin("secret");
        if (secret.Length > 245) throw new UsageException("secret too long for RSA-2048 PKCS#1 (max 245, keep it a 32-byte key)");

        using var prov = OpenProvider();
        int hr = Native.NCryptOpenKey(prov.H, out IntPtr key, KeyName(name), 0, 0);
        if (hr != 0) return MapNcryptError(hr, "NCryptOpenKey");
        using var k = new Handle(key);

        using var rsa = RsaFromBcryptPublicBlob(ExportPublic(key));
        // PKCS#1 v1.5 encryption padding: what the Passport KSP is known to support for NCryptDecrypt
        // (KeePassWinHello uses it). Padding-oracle concerns don't apply: every decrypt attempt costs a user gesture.
        byte[] blob = rsa.Encrypt(secret, RSAEncryptionPadding.Pkcs1);
        CryptographicOperations.ZeroMemory(secret);
        return Result.Of(ExitCodes.Ok, "Wrapped").Set("blob", Convert.ToBase64String(blob));
    }

    /// Decrypts a wrapped blob (base64 on stdin) with the Hello-protected private key. Hello prompt.
    public static Result Unwrap(Options o)
    {
        string name = o.Arg(0, "name");
        byte[] blob = Options.ReadBase64Stdin("blob");

        using var prov = OpenProvider();
        int hr = Native.NCryptOpenKey(prov.H, out IntPtr key, KeyName(name), 0, 0);
        if (hr != 0) return MapNcryptError(hr, "NCryptOpenKey");
        using var k = new Handle(key);
        SetPromptProperties(key, o, "Unlock Household Finance Manager");

        byte[] output = new byte[512];
        uint written;
        fixed (byte* pIn = blob)
        fixed (byte* pOut = output)
        {
            hr = Native.NCryptDecrypt(key, pIn, (uint)blob.Length, IntPtr.Zero, pOut, (uint)output.Length, out written,
                Native.NCRYPT_PAD_PKCS1_FLAG); // <- Hello prompt
        }
        if (hr != 0) return MapNcryptError(hr, "NCryptDecrypt");

        var r = Result.Of(ExitCodes.Ok, "Unwrapped").Set("secret", Convert.ToBase64String(output, 0, (int)written));
        CryptographicOperations.ZeroMemory(output);
        return r;
    }

    public static Result Delete(Options o)
    {
        string name = o.Arg(0, "name");
        using var prov = OpenProvider();
        int hr = Native.NCryptOpenKey(prov.H, out IntPtr key, KeyName(name), 0, 0);
        if (hr != 0) return MapNcryptError(hr, "NCryptOpenKey");
        hr = Native.NCryptDeleteKey(key, 0); // frees the handle on success
        if (hr != 0) { Native.NCryptFreeObject(key); return MapNcryptError(hr, "NCryptDeleteKey"); }
        return Result.Of(ExitCodes.Ok, "Deleted");
    }

    // ---------------------------------------------------------------------------------------------

    private static void SetPromptProperties(IntPtr key, Options o, string defaultReason)
    {
        IntPtr owner = o.OwnerWindow();
        if (owner != IntPtr.Zero) Native.SetPtr(key, Native.NCRYPT_WINDOW_HANDLE_PROPERTY, owner);
        Native.SetString(key, Native.NCRYPT_USE_CONTEXT_PROPERTY, o.Reason ?? defaultReason);
    }

    private static byte[] ExportPublic(IntPtr key)
    {
        Check(Native.NCryptExportKey(key, IntPtr.Zero, Native.BCRYPT_RSAPUBLIC_BLOB, IntPtr.Zero, null, 0, out uint size, 0), "NCryptExportKey(size)");
        byte[] buf = new byte[size];
        fixed (byte* p = buf)
            Check(Native.NCryptExportKey(key, IntPtr.Zero, Native.BCRYPT_RSAPUBLIC_BLOB, IntPtr.Zero, p, size, out size, 0), "NCryptExportKey");
        return buf.AsSpan(0, (int)size).ToArray();
    }

    /// BCRYPT_RSAKEY_BLOB: ULONG Magic, BitLength, cbPublicExp, cbModulus, cbPrime1, cbPrime2; then exponent, modulus (big-endian).
    private static RSA RsaFromBcryptPublicBlob(byte[] b)
    {
        int cbExp = BinaryPrimitives.ReadInt32LittleEndian(b.AsSpan(8));
        int cbMod = BinaryPrimitives.ReadInt32LittleEndian(b.AsSpan(12));
        var p = new RSAParameters
        {
            Exponent = b.AsSpan(24, cbExp).ToArray(),
            Modulus = b.AsSpan(24 + cbExp, cbMod).ToArray(),
        };
        var rsa = RSA.Create();
        rsa.ImportParameters(p);
        return rsa;
    }

    private static Handle OpenProvider()
    {
        Check(Native.NCryptOpenStorageProvider(out IntPtr prov, Native.MS_NGC_KEY_STORAGE_PROVIDER, 0), "NCryptOpenStorageProvider");
        return new Handle(prov);
    }

    private static Result MapNcryptError(int hr, string where) => hr switch
    {
        Native.NTE_NO_KEY or Native.NTE_BAD_KEYSET => Result.Fail(ExitCodes.KeyNotFound, "NotFound", where, hr),
        Native.NTE_USER_CANCELLED or Native.HRESULT_CANCELLED => Result.Fail(ExitCodes.NotVerified, "UserCanceled", where, hr),
        Native.NTE_NOT_SUPPORTED or Native.NTE_DEVICE_NOT_READY => Result.Fail(ExitCodes.Unavailable, "Unavailable", where, hr),
        _ => Result.Fail(ExitCodes.Error, "Error", where, hr),
    };

    private static void Check(int hr, string where)
    {
        if (hr != 0) throw new NcryptException(where, hr);
    }

    private sealed class NcryptException : Exception
    {
        public NcryptException(string where, int hr) : base($"{where} failed (0x{hr:X8})") { HResult = hr; }
    }

    private sealed class Handle(IntPtr h) : IDisposable
    {
        public IntPtr H { get; } = h;
        public void Dispose() { if (H != IntPtr.Zero) Native.NCryptFreeObject(H); }
    }
}
