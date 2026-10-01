using System.Security.Cryptography;
using System.Text;
using Windows.Security.Credentials;
using Windows.Security.Credentials.UI;
using Windows.Security.Cryptography;

namespace HfmHello;

internal static class Commands
{
    /// Default challenge for derive-key. Production: use a random per-vault 32-byte salt stored next to the wrapped key.
    private static readonly byte[] DefaultChallenge = Encoding.UTF8.GetBytes("HouseholdFinanceManager/hello-unlock/v1");
    private static readonly byte[] HkdfInfo = Encoding.UTF8.GetBytes("hfm-hello derive-key v1");
    private static readonly TimeSpan PromptTimeout = TimeSpan.FromMinutes(2);

    public static Result Available()
    {
        var r = Result.Of(ExitCodes.Ok, "Available");
        r.Set("os", Environment.OSVersion.VersionString);

        UserConsentVerifierAvailability ucv;
        try { ucv = UserConsentVerifier.CheckAvailabilityAsync().GetAwaiter().GetResult(); }
        catch (Exception e) { return Result.Fail(ExitCodes.Error, "Error", "CheckAvailabilityAsync: " + e.Message, e.HResult); }
        r.Set("userConsentVerifier", ucv.ToString());

        bool kcm = false;
        try { kcm = KeyCredentialManager.IsSupportedAsync().GetAwaiter().GetResult(); }
        catch (Exception e) { r.Set("keyCredentialManagerError", e.Message); }
        r.Set("keyCredentialManager", kcm);

        r.Set("consentInteropFactory", ConsentInterop.InteropFactoryAvailable(out int hr));
        if (hr < 0) r.Set("consentInteropHresult", $"0x{hr:X8}");

        r.Set("ngcProvider", NgcCommands.ProviderAvailable(out int nhr));
        if (nhr != 0) r.Set("ngcProviderHresult", $"0x{nhr:X8}");

        if (ucv != UserConsentVerifierAvailability.Available)
        {
            r.ExitCode = ExitCodes.Unavailable;
            r.Fields[0] = new("status", "Unavailable");
        }
        return r;
    }

    public static Result Verify(Options o)
    {
        string reason = o.Arg(0, "reason");
        var avail = UserConsentVerifier.CheckAvailabilityAsync().GetAwaiter().GetResult();
        if (avail != UserConsentVerifierAvailability.Available)
            return Result.Of(ExitCodes.Unavailable, "Unavailable").Set("availability", avail.ToString());

        IntPtr owner = o.OwnerWindow();
        string mode;
        UserConsentVerificationResult res;
        if (owner != IntPtr.Zero && Native.IsWindow(owner) != 0)
        {
            mode = "interop-hwnd";
            res = (UserConsentVerificationResult)ConsentInterop.RequestVerificationForWindow(owner, reason, PromptTimeout);
        }
        else
        {
            // No usable owner window: fall back to the plain API (dialog may appear behind other windows).
            mode = "unparented";
            using var _ = ForegroundHelper.Start();
            res = UserConsentVerifier.RequestVerificationAsync(reason).GetAwaiter().GetResult();
        }

        int code = res switch
        {
            UserConsentVerificationResult.Verified => ExitCodes.Ok,
            UserConsentVerificationResult.DeviceNotPresent or UserConsentVerificationResult.NotConfiguredForUser
                or UserConsentVerificationResult.DisabledByPolicy => ExitCodes.Unavailable,
            UserConsentVerificationResult.DeviceBusy => ExitCodes.Error,
            _ => ExitCodes.NotVerified, // Canceled, RetriesExhausted
        };
        return Result.Of(code, res.ToString()).Set("mode", mode).Set("ownerHwnd", owner.ToInt64());
    }

    public static Result CreateKey(Options o)
    {
        string name = o.Arg(0, "name");
        if (!KeyCredentialManager.IsSupportedAsync().GetAwaiter().GetResult())
            return Result.Of(ExitCodes.Unavailable, "Unavailable");

        var option = o.Replace ? KeyCredentialCreationOption.ReplaceExisting : KeyCredentialCreationOption.FailIfExists;
        KeyCredentialRetrievalResult created;
        using (ForegroundHelper.Start())
            created = KeyCredentialManager.RequestCreateAsync(name, option).GetAwaiter().GetResult();

        var r = FromStatus(created.Status);
        if (created.Status == KeyCredentialStatus.Success)
            r.Set("publicKeySha256", Fingerprint(created.Credential.RetrievePublicKey()));
        return r;
    }

    public static Result DeriveKey(Options o)
    {
        string name = o.Arg(0, "name");
        byte[] challenge = o.Challenge is null ? DefaultChallenge : Convert.FromBase64String(o.Challenge);

        var opened = KeyCredentialManager.OpenAsync(name).GetAwaiter().GetResult();
        if (opened.Status != KeyCredentialStatus.Success) return FromStatus(opened.Status);

        KeyCredentialOperationResult signed;
        using (ForegroundHelper.Start())
            signed = opened.Credential.RequestSignAsync(CryptographicBuffer.CreateFromByteArray(challenge)).GetAwaiter().GetResult();
        if (signed.Status != KeyCredentialStatus.Success) return FromStatus(signed.Status);

        CryptographicBuffer.CopyToByteArray(signed.Result, out byte[] signature);
        // The Hello key is RSA-2048 and RequestSignAsync uses RSASSA-PKCS1-v1_5/SHA-256, which is deterministic:
        // same key + same challenge => same signature => same derived key. Verify the signature against the public
        // key so a padding change (e.g. to PSS) would be detected as "non-deterministic" rather than silently
        // producing a different key.
        byte[] pub = PublicKeyBytes(opened.Credential);
        bool pkcs1Verified = VerifyPkcs1(pub, challenge, signature);

        byte[] key = HKDF.DeriveKey(HashAlgorithmName.SHA256, signature, 32, salt: challenge, info: HkdfInfo);
        var r = Result.Of(ExitCodes.Ok, "Success")
            .Set("key", Convert.ToBase64String(key))
            .Set("algorithm", "HKDF-SHA256(RSA-PKCS1v15-SHA256 signature over challenge)")
            .Set("signaturePkcs1v15", pkcs1Verified)
            .Set("publicKeySha256", Convert.ToHexString(SHA256.HashData(pub)));
        CryptographicOperations.ZeroMemory(signature);
        CryptographicOperations.ZeroMemory(key);
        return r;
    }

    public static Result DeleteKey(Options o)
    {
        string name = o.Arg(0, "name");
        try
        {
            KeyCredentialManager.DeleteAsync(name).GetAwaiter().GetResult();
            return Result.Of(ExitCodes.Ok, "Deleted");
        }
        catch (Exception e) when (e.HResult == Native.NTE_NO_KEY || e.HResult == Native.NTE_BAD_KEYSET
                                  || e.HResult == unchecked((int)0x80070490) /* ERROR_NOT_FOUND */)
        {
            return Result.Fail(ExitCodes.KeyNotFound, "NotFound", e.Message, e.HResult);
        }
    }

    public static Result SelfTest()
    {
        var r = Result.Of(ExitCodes.Ok, "Ok");
        r.Set("iidAsyncOpUcvResult", ConsentInterop.IID_IAsyncOperation_UCVResult.ToString());
        // Cross-check the hand-computed parameterized-IID algorithm against a LIVE WinRT object of the sibling type
        // IAsyncOperation<UserConsentVerifierAvailability> (same enum-in-namespace shape, but non-interactive):
        // QueryInterface with the computed IID must succeed.
        try
        {
            var op = UserConsentVerifier.CheckAvailabilityAsync();
            IntPtr unk = WinRT.MarshalInspectable<object>.FromManaged(op);
            Guid iid = ConsentInterop.ParameterizedIid(
                "pinterface({9fc2b0bb-e446-44e2-aa61-9cab8f636af2};enum(Windows.Security.Credentials.UI.UserConsentVerifierAvailability;i4))");
            int hr = System.Runtime.InteropServices.Marshal.QueryInterface(unk, in iid, out IntPtr typed);
            r.Set("iidLiveQueryInterface", hr == 0);
            if (typed != IntPtr.Zero) System.Runtime.InteropServices.Marshal.Release(typed);
            System.Runtime.InteropServices.Marshal.Release(unk);
            op.GetAwaiter().GetResult();
        }
        catch (Exception e) { r.Set("iidLiveQueryInterface", "n/a: " + e.Message); }
        // Sanity check of the IID algorithm against a well-known value: IAsyncOperation<bool> = cdb5efb3-5788-509d-9be1-71ccb8a3362a
        var boolIid = ConsentInterop.ParameterizedIid("pinterface({9fc2b0bb-e446-44e2-aa61-9cab8f636af2};b1)");
        r.Set("iidAlgorithmOk", boolIid == new Guid("cdb5efb3-5788-509d-9be1-71ccb8a3362a"));
        r.Set("consentInteropFactory", ConsentInterop.InteropFactoryAvailable(out _));
        r.Set("ngcProvider", NgcCommands.ProviderAvailable(out _));
        r.Set("userSid", NgcCommands.CurrentUserSid());
        r.Set("foregroundHwnd", Native.GetForegroundWindow().ToInt64());
        return r;
    }

    // ---------------------------------------------------------------------------------------------

    private static Result FromStatus(KeyCredentialStatus s) => s switch
    {
        KeyCredentialStatus.Success => Result.Of(ExitCodes.Ok, "Success"),
        KeyCredentialStatus.NotFound => Result.Of(ExitCodes.KeyNotFound, "NotFound"),
        KeyCredentialStatus.CredentialAlreadyExists => Result.Of(ExitCodes.KeyExists, "CredentialAlreadyExists"),
        KeyCredentialStatus.UserCanceled => Result.Of(ExitCodes.NotVerified, "UserCanceled"),
        KeyCredentialStatus.UserPrefersPassword => Result.Of(ExitCodes.NotVerified, "UserPrefersPassword"),
        KeyCredentialStatus.SecurityDeviceLocked => Result.Of(ExitCodes.NotVerified, "SecurityDeviceLocked"),
        _ => Result.Of(ExitCodes.Error, s.ToString()),
    };

    private static byte[] PublicKeyBytes(KeyCredential c)
    {
        CryptographicBuffer.CopyToByteArray(c.RetrievePublicKey(), out byte[] pub);
        return pub; // X509 SubjectPublicKeyInfo (DER)
    }

    private static string Fingerprint(Windows.Storage.Streams.IBuffer pub)
    {
        CryptographicBuffer.CopyToByteArray(pub, out byte[] bytes);
        return Convert.ToHexString(SHA256.HashData(bytes));
    }

    private static bool VerifyPkcs1(byte[] spki, byte[] data, byte[] sig)
    {
        try
        {
            using var rsa = RSA.Create();
            rsa.ImportSubjectPublicKeyInfo(spki, out _);
            return rsa.VerifyData(data, sig, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        }
        catch { return false; }
    }
}

/// Workaround for the classic "Windows Hello prompt opens behind my window" problem of KeyCredentialManager in
/// unpackaged Win32 apps (KeyCredentialManager has no ForWindow interop variant). While a prompt is pending we look
/// for the Hello dialog window and bring it to the foreground. Our process may do that because it was started by
/// the foreground app (the JVM) — Windows grants foreground rights to a process launched by the foreground process.
internal sealed class ForegroundHelper : IDisposable
{
    private static readonly string[] DialogClasses = { "Credential Dialog Xaml Host" };
    private readonly CancellationTokenSource _cts = new();

    public static ForegroundHelper Start()
    {
        var h = new ForegroundHelper();
        var t = new Thread(() => h.Run(h._cts.Token)) { IsBackground = true };
        t.Start();
        return h;
    }

    private void Run(CancellationToken ct)
    {
        var until = DateTime.UtcNow.AddSeconds(15);
        while (!ct.IsCancellationRequested && DateTime.UtcNow < until)
        {
            foreach (var cls in DialogClasses)
            {
                IntPtr w = Native.FindWindowW(cls, null);
                if (w != IntPtr.Zero)
                {
                    if (Native.GetForegroundWindow() != w) Native.SetForegroundWindow(w);
                    return;
                }
            }
            Thread.Sleep(100);
        }
    }

    public void Dispose() => _cts.Cancel();
}
