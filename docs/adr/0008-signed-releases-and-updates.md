# ADR 0008: Signed releases and update checks

Status: Accepted (Phase 4d, 2026-10-03)

## Context

- SEC-08 (Must): signed application updates, checked before install.
- DIST-01: the Windows build goes to the Microsoft Store. DIST-02: Linux .deb, .rpm and AppImage, with checksums. DIST-03: the Android app on Google Play and as a signed APK on GitHub Releases.
- DIST-05 (Should): the stores and Flathub update their own installs; .deb, .rpm, AppImage and APK builds check GitHub Releases.
- The owner's decisions (2026-10-03): Windows is Microsoft Store only; Linux builds are on GitHub Releases until the owner's storefront is up; the update check is asked about once, on first start.
- ARC-01 and PRV-02: nothing leaves the device unless the user agrees, and the user is told what does.

## Decision

**One release key, minisign format.**
- RANN's release key is an Ed25519 key pair. Signatures use the minisign format (BLAKE2b-512 of the file, signed; then a trusted comment signed with the signature), so anyone can check a download with the `minisign` tool and the public key in `core/update/src/main/resources/hfm/update/release-key.pub`.
- Every trusted comment names the file and the version (`file:<name>	version:<v>`), so a signature cannot be moved to another file.
- The secret key exists only offline with the owner and as the `HFM_RELEASE_KEY` secret of the release workflow. `tools/release keygen` makes the pair and writes the public key into the apps; the release workflow runs the tool directly, so the secret never passes through Gradle or its configuration cache.
- Bouncy Castle (already used) provides Ed25519 and BLAKE2b: no new library.

**Every release carries `SHA256SUMS`, `update.json` and a `.minisig` for every file.**
- `update.json` lists the version, the release notes in English and French, and for each installable file its kind (deb, rpm, appimage, apk), name, HTTPS address, size and SHA-256.

**Checking (`core/update`, shared by the desktop and the phone).**
1. Fetch `releases/latest/download/update.json` and its signature over HTTPS (never redirected to plain HTTP; at most 256 KB). `latest` never points to a draft or pre-release.
2. Verify the signature with the public key built into the app; the trusted comment must name `update.json`.
3. Offer the release only if its version is newer than this copy and it has a file for this copy's kind.
4. Download the file, refusing more bytes than announced, and keep it only if its size and SHA-256 match the signed manifest. Only then is it installed:
   - **AppImage:** the checked file replaces the running AppImage; the user restarts.
   - **.deb / .rpm:** the checked file is saved in Downloads and opened with the system's software installer; the terminal command is shown too.
   - **APK (GitHub build):** handed to Android's package installer, which asks the user to confirm and also refuses an APK not signed like the installed app.

**Which copies check.** Linux .deb, .rpm and AppImage installs, and the Android GitHub build. Not the Microsoft Store (MSIX), Flatpak or Google Play builds, which their stores update, nor a copy run from source. The Android app has two flavours with the same package `ca.schippers.hfm.companion`: `play` never checks and has no install permission; `github` checks and asks for `REQUEST_INSTALL_PACKAGES`.

**Asking first.** On a copy that checks, the first start asks whether to check once a day, saying what GitHub learns (the computer's or phone's internet address and that the app is in use) and that nothing about the household is sent. No request is made before the answer. The choice is per computer (desktop) or per phone, and can be changed under About (desktop) or Settings (phone).

**Windows.** The MSIX is unsigned when uploaded; the Microsoft Store signs it. No code-signing certificate is bought.

**Android signing.** Release builds are signed with the owner's upload key (`ANDROID_KEYSTORE_*` secrets). With Play App Signing, Google re-signs the Play build with the app signing key, so the Play and GitHub builds carry different certificates: a phone switching from one to the other must uninstall first. This is usual for apps published both ways.

**Release workflow (`.github/workflows/release.yml`).** A tag `v<version>` (which must match `hfm.version`):
- builds the .deb, .rpm and AppImage on Linux, the MSIX on Windows, and the GitHub APK and Play bundle;
- installs each Linux package (the .rpm on Fedora) and runs the self-check inside it (ADR 0004);
- signs everything, checks every signature with the real `minisign` tool, and creates a **draft** release. The owner publishes it.

The MSIX and the Play bundle are kept as workflow artifacts for the owner to upload. Run by hand, the workflow is a dry run with a throwaway key that publishes nothing.

## Consequences

- Losing the secret key would orphan every installed copy's update check: the owner keeps an offline copy. A leaked key would let an attacker sign updates; the remedy is a new key in a new version, which users install by hand.
- An attacker who controls the network can withhold updates (serve an old signed manifest, or nothing), but cannot make a copy install anything RANN did not sign.
- The update check is the only request the app makes on its own besides the Bank of Canada rates and the price feeds the user turned on (ADR 0007).
- Tested: `ReleaseSignatureTest` (round trip, tampered file, tampered comment, wrong key), `UpdateCheckTest` (newer, older, wrong file signature, other key, plain HTTP, download hash and size). An independent implementation (Python's `cryptography` and `hashlib`) verified a signature from the tool. The release workflow checks signatures with `minisign` itself. The desktop screens were checked in the demo with a signed test release, including a tampered download, which was refused and deleted.
