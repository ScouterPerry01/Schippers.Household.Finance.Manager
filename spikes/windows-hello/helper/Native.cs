using System.Runtime.InteropServices;

namespace HfmHello;

/// Raw Win32 / WinRT-ABI imports. Everything here is blittable so it is NativeAOT-friendly,
/// and every one of these calls could equally be made from the JVM via JNA.
internal static unsafe class Native
{
    // ---- combase (WinRT ABI) ------------------------------------------------------------------
    [DllImport("combase.dll")] private static extern int RoInitialize(int initType);
    [DllImport("combase.dll")] public static extern int RoGetActivationFactory(IntPtr activatableClassId, Guid* iid, IntPtr* factory);
    [DllImport("combase.dll")] public static extern int WindowsCreateString(char* sourceString, uint length, IntPtr* hstring);
    [DllImport("combase.dll")] public static extern int WindowsDeleteString(IntPtr hstring);

    public static void RoInitializeMta()
    {
        // RO_INIT_MULTITHREADED = 1. S_FALSE / RPC_E_CHANGED_MODE are fine: the apartment already exists.
        _ = RoInitialize(1);
    }

    public static IntPtr CreateHString(string s)
    {
        IntPtr h;
        fixed (char* p = s)
        {
            Marshal.ThrowExceptionForHR(WindowsCreateString(p, (uint)s.Length, &h));
        }
        return h;
    }

    // ---- user32 ---------------------------------------------------------------------------------
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern int SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern int IsWindow(IntPtr hwnd);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr FindWindowW(string? className, string? windowName);
    [DllImport("kernel32.dll")] public static extern IntPtr GetConsoleWindow();

    // ---- ncrypt (CNG key storage) ---------------------------------------------------------------
    public const string MS_NGC_KEY_STORAGE_PROVIDER = "Microsoft Passport Key Storage Provider";
    public const string NCRYPT_RSA_ALGORITHM = "RSA";
    public const string NCRYPT_LENGTH_PROPERTY = "Length";
    public const string NCRYPT_KEY_USAGE_PROPERTY = "Key Usage";
    public const string NCRYPT_WINDOW_HANDLE_PROPERTY = "HWND Handle";
    public const string NCRYPT_USE_CONTEXT_PROPERTY = "Use Context";
    /// Not in the public ncrypt.h; used by KeePassWinHello & co. Value 1 = "auth mandatory":
    /// every private-key use requires a fresh Hello gesture (no NGC auth caching).
    public const string NCRYPT_NGC_CACHE_TYPE_PROPERTY = "NgcCacheType";
    public const uint NCRYPT_NGC_CACHE_TYPE_PROPERTY_AUTH_MANDATORY_FLAG = 1;
    public const uint NCRYPT_ALLOW_ALL_USAGES = 0x00ffffff;
    public const uint NCRYPT_PAD_PKCS1_FLAG = 0x2;
    public const uint NCRYPT_OVERWRITE_KEY_FLAG = 0x80;
    public const string BCRYPT_RSAPUBLIC_BLOB = "RSAPUBLICBLOB";

    public const int NTE_BAD_KEYSET = unchecked((int)0x80090016);
    public const int NTE_NO_KEY = unchecked((int)0x8009000D);
    public const int NTE_EXISTS = unchecked((int)0x8009000F);
    public const int NTE_USER_CANCELLED = unchecked((int)0x80090036);
    public const int NTE_NOT_SUPPORTED = unchecked((int)0x80090029);
    public const int NTE_DEVICE_NOT_READY = unchecked((int)0x80090030);
    public const int HRESULT_CANCELLED = unchecked((int)0x800704C7); // HRESULT_FROM_WIN32(ERROR_CANCELLED)

    [DllImport("ncrypt.dll", CharSet = CharSet.Unicode)]
    public static extern int NCryptOpenStorageProvider(out IntPtr phProvider, string pszProviderName, uint dwFlags);

    [DllImport("ncrypt.dll", CharSet = CharSet.Unicode)]
    public static extern int NCryptOpenKey(IntPtr hProvider, out IntPtr phKey, string pszKeyName, uint dwLegacyKeySpec, uint dwFlags);

    [DllImport("ncrypt.dll", CharSet = CharSet.Unicode)]
    public static extern int NCryptCreatePersistedKey(IntPtr hProvider, out IntPtr phKey, string pszAlgId, string pszKeyName, uint dwLegacyKeySpec, uint dwFlags);

    [DllImport("ncrypt.dll", CharSet = CharSet.Unicode)]
    public static extern int NCryptSetProperty(IntPtr hObject, string pszProperty, byte* pbInput, uint cbInput, uint dwFlags);

    [DllImport("ncrypt.dll")]
    public static extern int NCryptFinalizeKey(IntPtr hKey, uint dwFlags);

    [DllImport("ncrypt.dll", CharSet = CharSet.Unicode)]
    public static extern int NCryptExportKey(IntPtr hKey, IntPtr hExportKey, string pszBlobType, IntPtr pParameterList, byte* pbOutput, uint cbOutput, out uint pcbResult, uint dwFlags);

    [DllImport("ncrypt.dll")]
    public static extern int NCryptDecrypt(IntPtr hKey, byte* pbInput, uint cbInput, IntPtr pPaddingInfo, byte* pbOutput, uint cbOutput, out uint pcbResult, uint dwFlags);

    [DllImport("ncrypt.dll")]
    public static extern int NCryptDeleteKey(IntPtr hKey, uint dwFlags);

    [DllImport("ncrypt.dll")]
    public static extern int NCryptFreeObject(IntPtr hObject);

    public static int SetDword(IntPtr h, string prop, uint value) => NCryptSetProperty(h, prop, (byte*)&value, 4, 0);

    public static int SetPtr(IntPtr h, string prop, IntPtr value) => NCryptSetProperty(h, prop, (byte*)&value, (uint)IntPtr.Size, 0);

    public static int SetString(IntPtr h, string prop, string value)
    {
        fixed (char* p = value + "\0")
        {
            return NCryptSetProperty(h, prop, (byte*)p, (uint)((value.Length + 1) * 2), 0);
        }
    }
}
