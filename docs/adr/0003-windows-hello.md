# ADR 0003: Windows Hello unlock

Status: Accepted, pending the manual test in `spikes/windows-hello/README.md` (Phase 0, 2026-10-01)

## Context

SEC-02: the desktop app locks after inactivity; unlock is by password, with Windows Hello as an option on Windows. The app runs on the JVM, which has no Windows Hello API.

## Decision

- A small native helper, `hfm-hello.exe`, written in C# and compiled with NativeAOT.
  - It is a 3.2 MB single file that starts in about 0.1 s and needs no .NET runtime installed.
  - The app calls it through `ProcessBuilder`, and it answers in JSON.
- **To protect a secret:**
  - The helper uses `KeyCredentialManager` to sign a random per-household challenge with the user's Windows Hello key.
  - The signature is RSA PKCS#1 v1.5, which is deterministic, so a stable 32-byte key can be derived from it with HKDF.
  - That key wraps the user's X25519 private key (ADR 0002) in a per-machine **Hello slot**.
  - KeePassXC uses the same approach.
- **The Hello slot is optional.**
  - It is never synced or backed up.
  - Creating it requires the password.
  - The password always keeps working.
  - If anything fails, the app falls back to the password and offers to re-enrol.
- **Linux:** password only. Linux Secret Service would add no proof that the user is present.
- **Fallback if needed:** the Microsoft Passport key storage provider through JNA (as KeePassWinHello does). It does real key wrapping and attaches the prompt to our window, but it relies on undocumented details.

## Consequences

- **Build and signing:**
  - The helper is built in Windows CI, which needs the MSVC linker.
  - It is shipped as a prebuilt resource in the desktop app.
  - It is signed with Authenticode.
  - The main Gradle build does not depend on `dotnet`.
- **MSIX:** under MSIX packaging, Hello keys are tied to the package identity, so users re-enrol once after the switch.
- **Not yet confirmed:** the `KeyCredentialManager` prompt cannot be attached to our window. The helper brings the prompt to the front, and the manual test must confirm this is good enough.
