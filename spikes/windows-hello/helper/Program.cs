// hfm-hello — Windows Hello helper for Household Finance Manager (SEC-02 spike).
//
// Contract with the JVM:
//   * one command per process invocation, arguments on argv, secrets ONLY via stdin/stdout (never argv),
//   * exactly one JSON object on stdout (UTF-8, single line), diagnostics on stderr,
//   * process exit code mirrors the outcome (see ExitCodes).
//
// Commands
//   available                                   Non-interactive capability probe (never prompts).
//   verify   <reason> [--hwnd H]                UserConsentVerifier prompt (Hello PIN/face/fingerprint). Yes/no only.
//   create-key <name> [--replace]               KeyCredentialManager: create a Hello key (prompts).
//   derive-key <name> [--challenge B64]         KeyCredentialManager: sign challenge (prompts) -> HKDF -> 32-byte key.
//   delete-key <name>                           KeyCredentialManager: delete the Hello key (no prompt).
//   ngc-create <name> [--hwnd H] [--reason R] [--replace]
//                                               NCrypt "Microsoft Passport KSP": create a Hello-protected RSA key (prompts).
//   ngc-wrap <name>     (stdin: base64 secret)  Encrypt secret with the key's PUBLIC part (no prompt) -> base64 blob.
//   ngc-unwrap <name> [--hwnd H] [--reason R]   (stdin: base64 blob) Decrypt with the Hello-protected private key (prompts).
//   ngc-delete <name>                           Delete the NGC key (no prompt).
//   selftest                                    Non-interactive internal checks (IID computation, KSP open, ...).
//
// H may be decimal or 0x-hex. If --hwnd is omitted, the current foreground window is used as owner.

using System.Text;
using System.Text.Json;

namespace HfmHello;

internal static class ExitCodes
{
    public const int Ok = 0;
    public const int NotVerified = 1;   // user cancelled / failed / too many attempts
    public const int Unavailable = 2;   // no Hello on this device / not set up / disabled by policy
    public const int KeyNotFound = 3;
    public const int KeyExists = 4;
    public const int Error = 5;         // unexpected failure (details in JSON "error"/"hresult")
    public const int Usage = 64;
}

internal sealed class Result
{
    public int ExitCode = ExitCodes.Ok;
    public readonly List<KeyValuePair<string, object?>> Fields = new();

    public Result Set(string key, object? value) { Fields.Add(new(key, value)); return this; }

    public static Result Of(int exitCode, string status) =>
        new Result { ExitCode = exitCode }.Set("status", status);

    public static Result Fail(int exitCode, string status, string error, int? hresult = null)
    {
        var r = Of(exitCode, status).Set("error", error);
        if (hresult is int hr) r.Set("hresult", $"0x{hr:X8}");
        return r;
    }
}

internal static class Program
{
    private static int Main(string[] args)
    {
        Console.OutputEncoding = new UTF8Encoding(false);
        Native.RoInitializeMta();

        string command = args.Length > 0 ? args[0] : "";
        Result result;
        try
        {
            var opts = Options.Parse(args.Skip(1).ToArray());
            result = command switch
            {
                "available" => Commands.Available(),
                "verify" => Commands.Verify(opts),
                "create-key" => Commands.CreateKey(opts),
                "derive-key" => Commands.DeriveKey(opts),
                "delete-key" => Commands.DeleteKey(opts),
                "ngc-create" => NgcCommands.Create(opts),
                "ngc-wrap" => NgcCommands.Wrap(opts),
                "ngc-unwrap" => NgcCommands.Unwrap(opts),
                "ngc-delete" => NgcCommands.Delete(opts),
                "selftest" => Commands.SelfTest(),
                _ => Result.Fail(ExitCodes.Usage, "Usage",
                    "usage: hfm-hello available | verify <reason> [--hwnd H] | create-key <name> [--replace] | " +
                    "derive-key <name> [--challenge B64] | delete-key <name> | ngc-create <name> [--hwnd H] [--reason R] [--replace] | " +
                    "ngc-wrap <name> (stdin) | ngc-unwrap <name> [--hwnd H] [--reason R] (stdin) | ngc-delete <name> | selftest"),
            };
        }
        catch (UsageException e)
        {
            result = Result.Fail(ExitCodes.Usage, "Usage", e.Message);
        }
        catch (Exception e)
        {
            result = Result.Fail(ExitCodes.Error, "Error", $"{e.GetType().Name}: {e.Message}", e.HResult);
        }

        WriteJson(command, result);
        return result.ExitCode;
    }

    private static void WriteJson(string command, Result r)
    {
        using var stdout = Console.OpenStandardOutput();
        using (var w = new Utf8JsonWriter(stdout))
        {
            w.WriteStartObject();
            w.WriteBoolean("ok", r.ExitCode == ExitCodes.Ok);
            w.WriteString("command", command);
            w.WriteNumber("exitCode", r.ExitCode);
            foreach (var (k, v) in r.Fields)
            {
                switch (v)
                {
                    case null: w.WriteNull(k); break;
                    case bool b: w.WriteBoolean(k, b); break;
                    case int i: w.WriteNumber(k, i); break;
                    case long l: w.WriteNumber(k, l); break;
                    default: w.WriteString(k, v.ToString()); break;
                }
            }
            w.WriteEndObject();
        }
        stdout.WriteByte((byte)'\n');
    }
}

internal sealed class UsageException(string message) : Exception(message);

internal sealed class Options
{
    public readonly List<string> Positional = new();
    public IntPtr? Hwnd;
    public string? Challenge;
    public string? Reason;
    public bool Replace;

    public static Options Parse(string[] args)
    {
        var o = new Options();
        for (int i = 0; i < args.Length; i++)
        {
            string a = args[i];
            string Next() => i + 1 < args.Length ? args[++i] : throw new UsageException($"missing value for {a}");
            switch (a)
            {
                case "--hwnd": o.Hwnd = ParseHwnd(Next()); break;
                case "--challenge": o.Challenge = Next(); break;
                case "--reason": o.Reason = Next(); break;
                case "--replace": o.Replace = true; break;
                default:
                    if (a.StartsWith("--")) throw new UsageException($"unknown option {a}");
                    o.Positional.Add(a);
                    break;
            }
        }
        return o;
    }

    public string Arg(int index, string what) =>
        index < Positional.Count && Positional[index].Length > 0 ? Positional[index] : throw new UsageException($"missing <{what}>");

    private static IntPtr ParseHwnd(string s)
    {
        long v = s.StartsWith("0x", StringComparison.OrdinalIgnoreCase)
            ? long.Parse(s.AsSpan(2), System.Globalization.NumberStyles.HexNumber)
            : long.Parse(s);
        return new IntPtr(v);
    }

    /// Owner window for the Hello prompt: explicit --hwnd, else the current foreground window
    /// (normally the JVM app window, because the user just clicked "Unlock with Windows Hello").
    public IntPtr OwnerWindow() => Hwnd ?? Native.GetForegroundWindow();

    /// Reads one line of base64 from stdin (secrets never travel via argv: argv is visible to other processes).
    public static byte[] ReadBase64Stdin(string what)
    {
        string? line = Console.In.ReadLine();
        if (string.IsNullOrWhiteSpace(line)) throw new UsageException($"expected base64 {what} on stdin");
        try { return Convert.FromBase64String(line.Trim()); }
        catch (FormatException) { throw new UsageException($"stdin {what} is not valid base64"); }
    }
}
