using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

namespace HfmHello;

/// UserConsentVerifier via IUserConsentVerifierInterop::RequestVerificationForWindowAsync (UserConsentVerifierInterop.h).
/// This is the Win32-app variant of RequestVerificationAsync that takes an owner HWND, so the Hello dialog
/// is parented to (and appears in front of) our app window instead of popping up behind it.
///
/// Done with raw vtable calls instead of the C#/WinRT projection so that it is trivially NativeAOT-safe and so the
/// code doubles as a recipe for a pure-JNA port (RoGetActivationFactory + vtable slot 6 + IAsyncInfo polling).
internal static unsafe class ConsentInterop
{
    private const string ClassName = "Windows.Security.Credentials.UI.UserConsentVerifier";
    private static readonly Guid IID_IUserConsentVerifierInterop = new("39E050C3-4E74-441A-8DC0-B81104DF949C");
    private static readonly Guid IID_IAsyncInfo = new("00000036-0000-0000-C000-000000000046");
    private static readonly Guid PIID_IAsyncOperation = new("9fc2b0bb-e446-44e2-aa61-9cab8f636af2");

    /// IID of IAsyncOperation&lt;UserConsentVerificationResult&gt;, computed per the WinRT parameterized-interface rules
    /// (UUIDv5/SHA-1 over the type signature, namespace 11f47ad5-7b73-42c0-abae-878b1e16adee).
    public static readonly Guid IID_IAsyncOperation_UCVResult = ParameterizedIid(
        $"pinterface({{{PIID_IAsyncOperation:D}}};enum(Windows.Security.Credentials.UI.UserConsentVerificationResult;i4))");

    public static Guid ParameterizedIid(string signature)
    {
        byte[] ns = { 0x11, 0xf4, 0x7a, 0xd5, 0x7b, 0x73, 0x42, 0xc0, 0xab, 0xae, 0x87, 0x8b, 0x1e, 0x16, 0xad, 0xee };
        byte[] data = ns.Concat(Encoding.UTF8.GetBytes(signature)).ToArray();
        byte[] h = SHA1.HashData(data);
        h[6] = (byte)((h[6] & 0x0f) | 0x50);
        h[8] = (byte)((h[8] & 0x3f) | 0x80);
        return new Guid(h.AsSpan(0, 16), bigEndian: true);
    }

    /// Returns the raw UserConsentVerificationResult value (0 = Verified, see enum).
    public static int RequestVerificationForWindow(IntPtr hwnd, string message, TimeSpan timeout)
    {
        IntPtr hClass = Native.CreateHString(ClassName);
        IntPtr hMsg = Native.CreateHString(message);
        IntPtr factory = IntPtr.Zero, op = IntPtr.Zero, info = IntPtr.Zero;
        try
        {
            Guid iid = IID_IUserConsentVerifierInterop;
            Marshal.ThrowExceptionForHR(Native.RoGetActivationFactory(hClass, &iid, &factory));

            // IInspectable = 6 slots, RequestVerificationForWindowAsync is slot 6.
            var vtbl = *(void***)factory;
            var requestForWindow = (delegate* unmanaged[Stdcall]<IntPtr, IntPtr, IntPtr, Guid*, IntPtr*, int>)vtbl[6];
            Guid riid = IID_IAsyncOperation_UCVResult;
            Marshal.ThrowExceptionForHR(requestForWindow(factory, hwnd, hMsg, &riid, &op));

            Marshal.ThrowExceptionForHR(Marshal.QueryInterface(op, in IID_IAsyncInfo, out info));
            var infoVtbl = *(void***)info;
            var getStatus = (delegate* unmanaged[Stdcall]<IntPtr, int*, int>)infoVtbl[7];
            var getErrorCode = (delegate* unmanaged[Stdcall]<IntPtr, int*, int>)infoVtbl[8];

            // Poll IAsyncInfo.Status (Started=0, Completed=1, Canceled=2, Error=3). The dialog lives in another
            // process (CredentialUIBroker), so we need no message pump; polling keeps this ABI-only.
            var deadline = DateTime.UtcNow + timeout;
            int status;
            while (true)
            {
                Marshal.ThrowExceptionForHR(getStatus(info, &status));
                if (status != 0) break;
                if (DateTime.UtcNow > deadline) throw new TimeoutException("Hello prompt not answered in time");
                Thread.Sleep(50);
            }
            if (status == 2) return (int)Windows.Security.Credentials.UI.UserConsentVerificationResult.Canceled;
            if (status == 3)
            {
                int hr;
                getErrorCode(info, &hr);
                Marshal.ThrowExceptionForHR(hr);
            }

            // IAsyncOperation<T>: put_Completed=6, get_Completed=7, GetResults=8.
            var opVtbl = *(void***)op;
            var getResults = (delegate* unmanaged[Stdcall]<IntPtr, int*, int>)opVtbl[8];
            int result;
            Marshal.ThrowExceptionForHR(getResults(op, &result));
            return result;
        }
        finally
        {
            if (info != IntPtr.Zero) Marshal.Release(info);
            if (op != IntPtr.Zero) Marshal.Release(op);
            if (factory != IntPtr.Zero) Marshal.Release(factory);
            Native.WindowsDeleteString(hMsg);
            Native.WindowsDeleteString(hClass);
        }
    }

    /// Non-interactive: proves the interop factory can be activated in this (unpackaged) process.
    public static bool InteropFactoryAvailable(out int hresult)
    {
        IntPtr hClass = Native.CreateHString(ClassName);
        IntPtr factory = IntPtr.Zero;
        try
        {
            Guid iid = IID_IUserConsentVerifierInterop;
            hresult = Native.RoGetActivationFactory(hClass, &iid, &factory);
            return hresult >= 0;
        }
        finally
        {
            if (factory != IntPtr.Zero) Marshal.Release(factory);
            Native.WindowsDeleteString(hClass);
        }
    }
}
