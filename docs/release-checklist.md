# Release day checklist: RANN's Roost 1.0.0

For the owner. Everything that touches a secret (the release key, the Android upload key, their
passwords and the repository secrets) is done **in your own PowerShell window**, never in a Claude
session and never with `!` in one. Commands assume the repository at
`C:\Users\PSchi\source\repos\Schippers.Household.Finance.Manager` and Git, `gh` (signed in) and the
JDK at `C:\Program Files\Android\openjdk\jdk-21.0.8`.

Background: [ADR 0008](adr/0008-signed-releases-and-updates.md) (signed releases and update checks),
`.github/workflows/release.yml`, the store texts in [`docs/store/`](store), the rann.ca pages in
[`website/`](../website). Release notes: [`docs/releases/1.0.0.en.md`](releases/1.0.0.en.md) and
[`.fr.md`](releases/1.0.0.fr.md).

## 0. Before the day

- [ ] `main` builds and the last CI run is green: `gh run list --branch main -L 3`.
- [ ] A release dry run on the current `main` passes (it builds every package, installs each Linux
      package and runs the self-check inside it, with a throwaway key; it publishes nothing):

      gh workflow run release.yml --ref main
      gh run watch (gh run list --workflow release.yml -L 1 --json databaseId --jq '.[0].databaseId')

- [ ] The Windows package opens normally when installed (not only the self-check): see
      [section 6](#6-check-the-msix-on-this-computer).
- [ ] The real-phone test of the companion is done (sideload the GitHub APK over USB with `adb install`).
- [ ] The release notes read well in both languages. The GitHub release body is the English file;
      `update.json` carries both.

## 1. The release key (once, ever)

Losing this key orphans every installed copy's update check; leaking it lets someone sign updates.
Keep the secret file offline (a USB key or your password manager) with a second copy.

In your own PowerShell window:

    cd C:\Users\PSchi\source\repos\Schippers.Household.Finance.Manager
    $env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
    .\gradlew :tools:release:run --args="keygen E:\RANN-keys\rann-release.key"

(Use your own offline folder instead of `E:\RANN-keys`; the tool refuses to overwrite an existing key.)
It writes the secret to that file and the public key to
`core/update/src/main/resources/hfm/update/release-key.pub`, and prints the key id.

- [ ] Add the secret to the repository, without it showing on the screen:

      gh secret set HFM_RELEASE_KEY --body (Get-Content -Raw E:\RANN-keys\rann-release.key).Trim()

- [ ] Commit the **public** key (only that file):

      git add core/update/src/main/resources/hfm/update/release-key.pub
      git commit -m "RANN's release public key"
      git push

## 2. The Android upload key (once, ever)

Google Play App Signing keeps the app signing key; this upload key signs what you upload and the
GitHub APK. In your own PowerShell window:

    $kt = "C:\Program Files\Android\openjdk\jdk-21.0.8\bin\keytool.exe"
    & $kt -genkeypair -v -keystore E:\RANN-keys\upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload

keytool asks for the keystore password, your name and organization (RANN), and the key password.
Keep `upload.jks` and both passwords offline, with a second copy.

- [ ] The four secrets (each `gh secret set` without a value asks for it, hidden):

      gh secret set ANDROID_KEYSTORE_BASE64 --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("E:\RANN-keys\upload.jks")))
      gh secret set ANDROID_KEYSTORE_PASSWORD
      gh secret set ANDROID_KEY_ALIAS
      gh secret set ANDROID_KEY_PASSWORD

  The alias is `upload` if you used the command above. `--body` keeps PowerShell from adding a line
  ending to the value, which the workflow's `base64 -d` would refuse.

- [ ] Check they are there (names only, never values): `gh secret list`. Expect `HFM_RELEASE_KEY` and
      the four `ANDROID_*`.

## 3. Version 1.0.0 and the tag

- [ ] In `gradle.properties`, change `hfm.version=0.9.0` to `hfm.version=1.0.0` (the phone's version
      code becomes 10000). Then:

      git pull
      .\gradlew build
      git commit -am "Version 1.0.0"
      git push

- [ ] Wait for CI on that commit to pass (`gh run watch`), then tag it:

      git tag -a v1.0.0 -m "RANN's Roost 1.0.0"
      git push origin v1.0.0

## 4. What the release workflow does

The tag starts **Release** (`gh run watch (gh run list --workflow release.yml -L 1 --json databaseId --jq '.[0].databaseId')`):

1. **Version:** stops unless the tag is `v` + `hfm.version` and the public key is in the repository.
2. **Linux packages** (Ubuntu): builds the .deb, .rpm and AppImage, installs the .deb (Ubuntu) and the
   .rpm (Fedora container) and runs the self-check inside each, and inside the AppImage.
3. **Microsoft Store package** (Windows): builds the unsigned MSIX and runs the self-check in its app
   image. Kept as the `msix` artifact (the Store signs it).
4. **Android:** builds the GitHub APK and the Play bundle (AAB), both signed with the upload key. The
   AAB is kept as the `android` artifact; it is not put on GitHub Releases.
5. **Sign and draft the release:** signs every Linux package and the APK with the release key, writes
   `SHA256SUMS` and `update.json` (with both release notes), checks every signature with `minisign`,
   and creates a **draft** GitHub release `v1.0.0` with the English notes. Nothing is public yet.

If a job fails, nothing is published. Fix on `main`, delete the tag (`git push origin :v1.0.0`,
`git tag -d v1.0.0`) and the draft if one was made, and tag again.

## 5. Publish the GitHub release

- [ ] Look at the draft: `gh release view v1.0.0` (or on GitHub, Releases). Expect the .deb, .rpm,
      AppImage and APK, a `.minisig` for each, `SHA256SUMS`, `update.json` and their `.minisig`.
- [ ] Check one download yourself:

      gh release download v1.0.0 --pattern "*.deb*" --pattern "SHA256SUMS*" -D $env:TEMP\rel100
      # with minisign installed (winget install jedisct1.minisign):
      minisign -Vm "$env:TEMP\rel100\ranns-roost_1.0.0_amd64.deb" -p core\update\src\main\resources\hfm\update\release-key.pub

- [ ] Optionally add the French notes under the English ones in the release text (GitHub shows one text).
- [ ] Publish: `gh release edit v1.0.0 --draft=false --latest`. From now on, Linux copies and the GitHub
      APK that agreed to check will see later releases through `releases/latest`.

## 6. Check the MSIX on this computer

Download the artifacts of the tag's run:

    $run = gh run list --workflow release.yml -L 1 --json databaseId --jq '.[0].databaseId'
    gh run download $run -n msix -D $env:TEMP\rel100\msix
    gh run download $run -n android -D $env:TEMP\rel100\android

The downloaded MSIX is unsigned, so Windows will not install it as a file; check it from a local build
of the same commit instead (developer mode is on):

    .\gradlew :app:desktop:packageMsix
    Add-AppxPackage -Register app\desktop\build\msix\layout\AppxManifest.xml
    $fam = (Get-AppxPackage RANN.SchippersHouseholdFinanceManager).PackageFamilyName
    Start-Process "shell:AppsFolder\$fam!RANNsRoost"

- [ ] The welcome or unlock window opens (a "Failed to launch JVM" box means a JDK module is missing
      from the packaged runtime; see `suggestRuntimeModules` in the README).
- [ ] Close it, then `Get-AppxPackage RANN.SchippersHouseholdFinanceManager | Remove-AppxPackage`.

The packaged app reads your real settings (recent households, language), but its writes are kept
apart; your real household is not touched unless you open it.

## 7. Microsoft Store (Partner Center)

Partner Center > Apps and games > **RANN's Roost** > Start submission (before the name reservation lapses).

- [ ] **Packages:** upload `RANNsRoost-1.0.0-x64.msix` from the `msix` artifact. Partner Center checks
      the identity (`RANN.SchippersHouseholdFinanceManager`, publisher `CN=A48DF0F5-…`) against the
      reservation; a mismatch means `app/desktop/packaging/msix/store-identity.properties` is out of date.
- [ ] **Restricted capability `runFullTrust`:** explain that this is a desktop (Win32) app packaged as
      MSIX, which needs full trust to run.
- [ ] **Pricing and availability:** price and markets (your choice); Canada at least.
- [ ] **Properties:** category Personal finance; privacy policy
      https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en; website https://www.rann.ca/rann-apps/rann-roost;
      support info-rann-apps@NorthMail.ca.
- [ ] **Age ratings:** the IARC questionnaire (no violence, no user interaction with strangers, no purchases in the app).
- [ ] **Store listings:** English (Canada) and French (Canada) from [`docs/store/microsoft-store.md`](store/microsoft-store.md),
      with the eight screenshots per language in `docs/store/screenshots/desktop-en` and `desktop-fr`
      and the tiles from `branding/`. Check the texts still fit: `python tools/dev/check_store_texts.py`.
- [ ] Submit. Certification usually takes one to three business days.

## 8. Google Play (Play Console)

Nothing has been uploaded to Play yet; the package `ca.ranns.roost.mobile` becomes permanent with the
first upload. Personal developer accounts need a closed test with at least 12 testers for 14 days in
a row before production.

- [ ] **Create the app:** RANN's Roost Mobile, default language English (Canada), App, Free.
- [ ] **Play App Signing:** accept Google-generated app signing key; the upload key is the one from step 2.
- [ ] **App content:** privacy policy URL (above); ads: none; App access: explain that the app needs
      RANN's Roost on a computer to pair, and give reviewers the steps (or a demo pairing) if Play asks;
      content rating questionnaire; target audience adults (18+); news app: no; government app: no;
      financial features: personal finance management only (no banking, loans or payments in the app);
      health: the app holds medication refills and appointments sent from the user's computer (answer
      the Health apps declaration if Play shows it).
- [ ] **Data safety:** fill it in from [`docs/store/google-play.md`](store/google-play.md#data-safety-form),
      after confirming its five points (end-to-end exception, dictation, permissions, ML Kit's current
      list, location).
- [ ] **Store listing:** English (Canada) and French (Canada) from `docs/store/google-play.md`, the icon
      `branding/store/play-icon-512.png`, the feature graphic `branding/store/play-feature-1024x500.png`
      and five phone screenshots per language from `docs/store/screenshots/phone-en` and `phone-fr`.
- [ ] **Internal testing:** Testing > Internal testing > Create release, upload
      `ranns-roost-mobile-1.0.0-play.aab` from the `android` artifact, release notes (short: "First
      release." / « Première version. »), roll out; install it from the Play link on your own phone and
      pair it with the computer.
- [ ] **Closed testing:** create a closed track, add at least 12 testers (an email list or a Google
      Group), promote the same release, and keep them opted in for 14 days in a row.
- [ ] **Production:** after the 14 days, apply for production access (Play asks about the test), then
      promote the release to production for Canada.

## 9. rann.ca (Google Sites)

- [ ] **Privacy policy pages** (`/privacy-policy-en` and `/privacy-policy-fr`): paste the changed
      sections from `website/privacy-policy-en.md` and `-fr.md`. Since the version on the site, check at
      least "Calendars on your phone" and "Location on the phone" (added with calendars and trips),
      "Optional features that use your own accounts" (now in the present tense, and AI reading of
      statements with hidden parts sent as plain grey), and the **Dictation** paragraph under "The phone
      app and Google". If the page changes, set its "Effective" date to the day you publish it, in both
      languages and in the files.
- [ ] **Home pages** (`/rann-roost` and `/rann-roost-fr`): under "Get RANN's Roost", replace each
      "Coming soon" with a button to the Microsoft Store listing, the GitHub release
      (https://github.com/ScouterPerry01/Schippers.Household.Finance.Manager/releases/latest) and,
      once in production, the Play listing.
- [ ] **Images:** upload `website/images/` to Google Drive and replace the pictures on both home pages
      (they were retaken on 2026-10-06), with the alt text written beside each in the page files.

## 10. After publishing

- [ ] Each store listing, the GitHub release and rann.ca link to one another and to the privacy policy.
- [ ] Install from the Microsoft Store on this computer and open your household once.
- [ ] Note the release in the development plan's decisions log (date, version, what went where).
- [ ] Keep `gradle.properties` at 1.0.0 until the next release; bump it (1.0.1 or 1.1.0) in the first
      commit of that release.
