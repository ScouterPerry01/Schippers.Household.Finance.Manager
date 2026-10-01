# Spike: Windows Hello unlock from the JVM (SEC-02)

**Question:** What is the most robust way for our Kotlin/JVM (Compose Desktop, JDK 21) app to use Windows Hello
user verification? Can Hello also protect a secret, so a Hello unlock can release the database key without the
password?

## Verdict

**It works, and Hello can protect a secret.** The most robust approach is a **small NativeAOT C# helper exe**
(`hfm-hello.exe`, **3.2 MB**, about **0.1 s** startup, no .NET runtime needed on the target). The JVM calls it through
`ProcessBuilder`. It returns one JSON object on stdout and uses the exit code for the outcome. Secrets go
over stdin/stdout only.

* **Verification only (yes/no):** `UserConsentVerifier` through `IUserConsentVerifierInterop::RequestVerificationForWindowAsync(HWND)`.
  The prompt is parented to our window. This is only a UI gate and **does not protect a secret by itself**.
* **Secret protection (what SEC-02 needs):** a Windows Hello key pair, which is TPM-backed when a TPM is present. Each
  private-key use needs a Hello gesture (PIN, face or fingerprint). The helper implements this in two ways:
  1. **`KeyCredentialManager` (recommended).** This is a documented public WinRT API that works in unpackaged Win32
     apps. The helper signs a per-vault random challenge, and the derived key is `HKDF-SHA256(signature)`. Hello keys
     are RSA-2048, and `RequestSignAsync` uses **RSASSA-PKCS1-v1_5 / SHA-256, which is deterministic**: the same key
     and the same challenge always give the same signature, so they always give the same wrapping key. The helper checks
     on every call that the signature is PKCS#1 v1.5 (`signaturePkcs1v15: true`). If Microsoft ever switched to
     randomized PSS, the check would catch it and the app would fall back to the password instead of failing silently.
     KeePassXC's Windows Hello quick-unlock uses the same approach (signature → hash → key).
  2. **CNG "Microsoft Passport Key Storage Provider" (NGC KSP).** An RSA key in the Hello container. The vault key is
     *encrypted* with the public key (no prompt) and *decrypted* with the Hello-gated private key. This is a true
     key wrap, it does not depend on signature determinism, and it supports a real owner HWND
     (`NCRYPT_WINDOW_HANDLE_PROPERTY`) plus prompt text (`NCRYPT_USE_CONTEXT_PROPERTY`). The downside is that the
     key-name format `{SID}//{domain}/{sub}/{name}` and `NgcCacheType` (which forces a gesture on every use) are
     **undocumented**. They are used by KeePassWinHello. Keep this as the fallback if the KeyCredentialManager prompt
     placement turns out to be unacceptable.
* **DPAPI + consent prompt (option B)** works (shown via JNA in the harness), but it is **convenience, not security**.
  See the security notes.
* **Pure JNA (option C)** works for the NCrypt path, because `ncrypt.dll` is a flat C API. The harness's `jna-probe`
  opens the Passport KSP from the JVM. `UserConsentVerifier` through JNA is possible but awkward: it needs
  RoGetActivationFactory, HSTRINGs, raw vtable calls and IAsyncInfo polling (`ConsentInterop.cs` is the recipe).
  `KeyCredentialManager` through JNA is **not practical** (IBuffer, several async generic interfaces). So the
  helper exe is the robust common path.

## What was verified here (non-interactively, Windows 11 26200)

| Check | Result |
|---|---|
| `hfm-hello available` (AOT build) | `userConsentVerifier: Available`, `keyCredentialManager: true`, `consentInteropFactory: true`, `ngcProvider: true`, exit 0 |
| `hfm-hello selftest` | Hand-computed parameterized IID algorithm verified: it matches the known `IAsyncOperation<Boolean>` IID, and a **live QueryInterface** on a real `IAsyncOperation<UserConsentVerifierAvailability>` succeeds |
| Missing-key paths (`delete-key`, `ngc-wrap`, `ngc-delete`) | `NotFound`, exit 3, `hresult 0x8009000D` |
| NativeAOT publish | No trim/AOT warnings. **3.2 MB** single exe, about **100 ms** per invocation |
| Framework-dependent build (for comparison) | 25 MB (`Microsoft.Windows.SDK.NET.dll` projection) plus a required .NET 10 runtime, about 0.8 s startup. **Not recommended** |
| JVM harness `available` via ProcessBuilder | Parsed JSON OK |
| JVM harness `dpapi-roundtrip` (JNA Crypt32) | Round-trip OK, **no prompt** |
| JVM harness `jna-probe` (JNA ncrypt) | `NCryptOpenStorageProvider(MS_NGC)` = 0. `NCryptOpenKey` = `0x8009000D` (no key yet) |

**Not verified, because it needs a human:** every command that shows a Hello prompt (`verify`, `create-key`,
`derive-key`, `ngc-create`, `ngc-unwrap`). This includes signature determinism on real hardware and prompt
placement. See [Manual test](#manual-test-please-run-and-fill-in).

## Files

```
spikes/windows-hello/
  README.md
  helper/                          C# helper (net10.0-windows10.0.19041.0, NativeAOT)
    HelloHelper.csproj
    Program.cs                     CLI, JSON output, exit codes
    Commands.cs                    available / verify / create-key / derive-key / delete-key / selftest; foreground workaround
    ConsentInterop.cs              IUserConsentVerifierInterop::RequestVerificationForWindowAsync via raw vtable (AOT- and JNA-friendly)
    NgcCommands.cs                 Passport KSP: ngc-create / ngc-wrap / ngc-unwrap / ngc-delete
    Native.cs                      P/Invoke (combase, user32, ncrypt)
  jvm-harness/                     standalone Gradle build (Kotlin 2.4.20, JNA 5.19.1)
    settings.gradle.kts, build.gradle.kts
    src/main/kotlin/hfm/spike/hello/WindowsHelloClient.kt   <- reusable client (ProcessBuilder + mini JSON)
    src/main/kotlin/hfm/spike/hello/Main.kt                 <- harness commands, DPAPI and JNA demos, --window HWND test
```

## Helper contract

`hfm-hello <command> [args]` prints **one JSON line** on stdout (`ok`, `command`, `exitCode`, `status`, plus
command-specific fields).

| Exit | Meaning |
|---|---|
| 0 | OK / verified |
| 1 | Not verified: user cancelled, retries exhausted, user chose password, device locked |
| 2 | Hello unavailable: no device, not set up, disabled by policy, or an RDP session |
| 3 | Key not found |
| 4 | Key already exists |
| 5 | Unexpected error (`error`, `hresult`) |
| 64 | Usage error |

| Command | Prompt? | Notes |
|---|---|---|
| `available` | no | Run at startup and in Settings to decide whether to show "Unlock with Windows Hello". |
| `verify "<reason>" [--hwnd H]` | yes | Yes/no only. With no `--hwnd`, the foreground window (normally ours) is the owner. If no window is found, it falls back to plain `RequestVerificationAsync`. |
| `create-key <name> [--replace]` | yes | KeyCredentialManager. Returns `publicKeySha256`. |
| `derive-key <name> [--challenge B64]` | yes | Returns `key` (base64, 32 bytes) = HKDF-SHA256(ikm=signature, salt=challenge, info="hfm-hello derive-key v1"). |
| `delete-key <name>` | no | |
| `ngc-create <name> [--hwnd H] [--reason R] [--replace]` | yes | RSA-2048 in the Passport KSP, `NgcCacheType=AUTH_MANDATORY` (best effort, reported as `authMandatory`). |
| `ngc-wrap <name>` + stdin base64 secret | no | Encrypts with the exported public key (RSA PKCS#1 v1.5). Returns `blob`. |
| `ngc-unwrap <name> [--hwnd H] [--reason R]` + stdin base64 blob | yes | `NCryptDecrypt` with the Hello-gated private key. Returns `secret`. |
| `ngc-delete <name>` | no | |
| `selftest` | no | Internal checks. |

### Prompt placement (window parenting and foreground)

A Win32 app that calls `UserConsentVerifier.RequestVerificationAsync` or `KeyCredentialManager` from a background
process often gets the Hello dialog **behind** the app window, or only a flashing taskbar icon. Mitigations in this
spike:

* `verify` uses **`IUserConsentVerifierInterop::RequestVerificationForWindowAsync(HWND, message, riid, out op)`**
  (`UserConsentVerifierInterop.h`, IID `39E050C3-4E74-441A-8DC0-B81104DF949C`). The JVM passes its window handle.
  In Compose Desktop that is `ComposeWindow.windowHandle` (a `Long`). In plain AWT/Swing it is JNA
  `Native.getWindowID(frame)`. The `riid` is `IAsyncOperation<UserConsentVerificationResult>` =
  `fd596ffd-2318-558f-9dbe-d21df43764a5` (computed and verified in `selftest`).
* NGC commands set `NCRYPT_WINDOW_HANDLE_PROPERTY` on the key handle, which gives the same effect.
* `KeyCredentialManager` has **no ForWindow variant**. While its prompt is pending, the helper runs a best-effort
  watcher that finds the `Credential Dialog Xaml Host` window and calls `SetForegroundWindow` (the same trick other
  password managers use). This works because Windows lets a process launched by the foreground process (our JVM)
  take the foreground. If the user switches away before clicking unlock, this can still fail. The manual test
  checks it.
* Always start the helper from the UI action itself ("Unlock with Windows Hello" button), while our window is in the
  foreground.

## Recommended production design

**Keys**

* `DEK`: a random 256-bit vault key that encrypts the database (for example, the SQLCipher key).
* `KEK_pw = Argon2id(password, salt)` wraps the DEK (AES-256-GCM). The **password slot is always present**.
  Hello is only an extra slot, because Hello keys are device-bound and are destroyed if the user resets their Hello
  PIN, resets Windows or moves to a new PC.
* **Hello slot (Windows only, per machine, never synced or backed up):**
  `{ keyName: "hfm-<vaultId>", challenge: 32 random bytes, nonce, ct = AES-GCM(KEK_hello, DEK), publicKeySha256 }`,
  stored in local app data. It is *not* stored in the synced or backed-up vault.

**Flows**

1. *Enable Hello* (Settings, vault already unlocked, ask for the password again): call `available`, then
   `create-key hfm-<vaultId>` (prompt), then `derive-key … --challenge <random>` (prompt). Wrap the DEK with
   `KEK_hello` and store the slot. Wipe `KEK_hello`.
2. *Unlock after the inactivity lock (SEC-02):* the lock screen shows a password field and, if a Hello slot exists and
   `available` is OK, an "Unlock with Windows Hello" button. Call `derive-key` and then AES-GCM-unwrap the DEK.
   * exit 1 (cancelled): stay on the lock screen.
   * exit 2/3, or a GCM authentication failure: tell the user "Windows Hello unlock is no longer valid. Use your
     password", then offer to re-enrol.
   * Optionally offer Hello automatically on focus, but never loop the prompt.
3. *Lock:* close the DB, zero the DEK and any derived keys (`ByteArray.fill(0)`), and drop references.
4. *Disable Hello / change vault:* `delete-key` and delete the slot.

**Fallback variant (NGC KSP):** the flows are the same, but enabling uses `ngc-create` + `ngc-wrap(DEK)` (one
prompt), and unlock uses `ngc-unwrap` (one prompt, properly parented). Pick this if the manual test shows the
KeyCredentialManager prompt reliably ends up behind the window.

**Linux:** password only for v1. The Secret Service (libsecret / GNOME Keyring / KWallet over D-Bus) is unlocked
by the login session and does not check user presence. It is the equivalent of DPAPI, so it could at most back a
"remember on this device" convenience, not a "Hello" equivalent. fprintd/polkit-based verification is a gate without
key protection and is not worth the complexity. (macOS, if ever needed: LocalAuthentication plus a Keychain item with
`SecAccessControl .userPresence` is the true equivalent.)

**Code layout:** put a `BiometricUnlock` interface in common code. Implementations: `WindowsHelloUnlock`
(this helper) and `NoBiometricUnlock` (Linux and others). Check `available` lazily and cache the result per
session.

## Packaging implications

* **Size:** about 3.2 MB for `hfm-hello.exe` (NativeAOT, size-optimized, win-x64). No .NET install is needed.
  Add a win-arm64 build later if ARM devices matter (`-r win-arm64`).
* **Build:** NativeAOT needs the **.NET 10 SDK plus the MSVC linker** (VS Build Tools "Desktop development with
  C++"). In this environment `dotnet publish` failed until `vswhere.exe` was on PATH:
  `PATH="C:\Program Files (x86)\Microsoft Visual Studio\Installer;$PATH"`. Build on a Windows CI runner and treat
  the exe as a prebuilt resource of the Gradle build. Do not make the main Gradle build depend on `dotnet`.
* **Compose Desktop:** ship the exe through `nativeDistributions { appResourcesRootDir.set(...) }` (in a `windows/`
  subfolder). At runtime, resolve it from `System.getProperty("compose.application.resources.dir")`.
* **Code signing:** Authenticode-sign `hfm-hello.exe` with the same certificate as the app or MSI. Unsigned helper
  exes trigger SmartScreen and antivirus heuristics. Optionally have the app pin the helper's SHA-256 and check it
  before execution.
* **MSIX:** works. In a packaged app, KeyCredentialManager keys are scoped to the **package identity**, so keys
  created by an unpackaged build are not visible after switching to MSIX (and the reverse). Users must re-enrol,
  which is harmless because the password slot is always there. MSIX also gives the helper a non-user-writable
  install location. For MSI, install per-machine under Program Files for the same reason.

## Security notes

* **Option A (Hello key) vs option B (DPAPI + consent prompt):**
  * With **DPAPI**, any code running as the same Windows user (malware, a patched jar, a debugger attached to the
    JVM) can call `CryptUnprotectData` **without any prompt**. The `UserConsentVerifier` prompt is then only a UI
    check that can be skipped. DPAPI protects against other OS users and against offline disk theft (via the
    Windows password), but not against same-user code. It is essentially "password remembered on this PC".
  * With the **Hello key**, the private key never leaves the TPM or the Hello container, and every use requires a
    fresh user gesture (PIN, face or fingerprint) answered in a secure-desktop-style system dialog. Same-user
    malware can at most *ask* the user for a Hello gesture (phishing), and cannot silently obtain the key.
* `UserConsentVerifier` alone (the `verify` command) is fine to *re-confirm presence*, but must never be the only
  thing that stands between the app and a stored DB key.
* Secrets never go on argv (other processes can read it). They go over stdin/stdout pipes, which only the parent
  JVM can read. The helper zeroes its buffers (best effort). The JVM must zero its `ByteArray`s.
* Hello "PIN" counts as Hello. The PIN is TPM-anti-hammering-protected and device-bound, so it is acceptable as a
  second factor for local unlock. If policy should require biometrics only, that cannot be enforced from the app.
* Hello is usually unavailable inside RDP sessions, and `available` reports this. Fall back to the password.
* Run the helper only from a trusted location (Program Files / MSIX). In a per-user install under `%LOCALAPPDATA%`,
  same-user malware could replace it. It could equally patch the JVM app, so this is defence in depth, not a new
  hole.
* Derivation robustness: `derive-key` depends on deterministic PKCS#1 v1.5 signatures and verifies the padding on
  every call. Any drift (key re-created, padding change) shows up as a GCM authentication failure, which leads to a
  password fallback and re-enrolment. The user is never locked out.

## Manual test (please run and fill in)

Build (once), from `spikes/windows-hello/helper` in PowerShell:

```powershell
$env:PATH = "C:\Program Files (x86)\Microsoft Visual Studio\Installer;" + $env:PATH
dotnet publish -c Release -r win-x64
$h = ".\bin\Release\net10.0-windows10.0.19041.0\win-x64\publish\hfm-hello.exe"
```

Direct helper tests. Expected output is shown after the `#`.

```powershell
& $h available                         # exit 0, userConsentVerifier=Available
& $h verify "Unlock Household Finance Manager"; $LASTEXITCODE   # Hello prompt in front of the terminal -> 0 "Verified"; press Cancel -> 1 "Canceled"
& $h create-key hfm-spike; $LASTEXITCODE                         # prompt -> 0 "Success" + publicKeySha256
& $h derive-key hfm-spike                                        # prompt -> "key":"<b64>", "signaturePkcs1v15":true
& $h derive-key hfm-spike                                        # prompt -> SAME "key" value  (determinism check)
& $h derive-key hfm-spike --challenge AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=   # different key than above
& $h ngc-create hfm-spike; $LASTEXITCODE                         # prompt -> 0 "Created", authMandatory true/false
$blob = ("q83vEjRWeJCrze8SNFZ4kKvN7xI0VniQq83vEjRWeJA=" | & $h ngc-wrap hfm-spike | ConvertFrom-Json).blob   # no prompt
$blob | & $h ngc-unwrap hfm-spike                                # prompt -> "secret":"q83vEjRWeJCrze8SNFZ4kKvN7xI0VniQq83vEjRWeJA="
$blob | & $h ngc-unwrap hfm-spike                                # prompts AGAIN immediately? (=> NgcCacheType honoured)
```

From the JVM, with a real app window as owner (from `spikes/windows-hello/jvm-harness`, Git Bash):

```bash
export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"
G=$(ls ~/.gradle/wrapper/dists/gradle-9.6.0-bin/*/gradle-9.6.0/bin/gradle)
"$G" -q run --args="verify --window"              # prompt should be in front of / centred on the Swing window
"$G" -q run --args="derive-key hfm-spike"         # run twice -> identical "derived key fingerprint"
"$G" -q run --args="ngc-roundtrip hfm-spike2 --window"   # 2 prompts, "round-trip match = true"
"$G" -q run --args="cleanup hfm-spike"; "$G" -q run --args="cleanup hfm-spike2"
```

Clean up the direct tests: `& $h delete-key hfm-spike; & $h ngc-delete hfm-spike`.

| Manual check | Result |
|---|---|
| `verify` prompt appears in front (terminal / `--window`) | |
| `create-key` → `derive-key` twice gives the same key, `signaturePkcs1v15` true | |
| KeyCredentialManager prompt in front of the window (foreground workaround works?) | |
| `ngc-create` / `ngc-unwrap` round-trip OK, parented to the window | |
| `authMandatory` honoured (second unwrap prompts again) | |
| Cancel → exit 1 for each prompt type | |
