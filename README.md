# RANN's Roost

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="branding/logo/Rann_Roost_Kite_Left_Facing_White_Text.png">
  <img src="branding/logo/Rann_Roost_Kite_Left_Facing.png" alt="RANN's Roost logo: a brahminy kite in flight" width="360">
</picture>

A personal, family and household finance application for Canada, in every province and territory, in English and French. Published by RANN; called Household Finance Manager until October 2026.

- **Desktop app, RANN's Roost** (Windows and Linux) holds the household's books:
  - **Bookkeeping:** accounts in any currency, statement import (OFX, QFX, QBO, CSV) and Quicken, GnuCash or Moneydance history (QIF), reconciliation to the cent, categories and rules, templates, bills and subscriptions (Home or Business, statements, account numbers, utility readings, paying in part, property taxes in instalments), budgets, savings goals, account alerts and a cash flow forecast.
  - **Documents and AI reading:** receipts, bills and statements read on the computer, from the phone or from saved emails, kept in an encrypted vault, viewed page by page with zoom, receipts itemized by hand; optional AI reading with the user's own Anthropic key (receipts by item, statements into the books), nothing sent before the user chooses.
  - **Investing and borrowing:** holdings, adjusted cost base, returns, allocation, investment income for tax slips, registered plans and pensions, mortgages and loans with Canadian compounding, optional market prices, crypto-assets and precious metals.
  - **Taxes:** the slips to expect, donations, instalments, pay stubs, sales taxes for every province and territory, an income tax estimate per person (federal, provincial and territorial, Quebec), medical expenses, and a year-end package for the accountant. Every rate, limit and threshold can be changed with an effective date (Rates and rules).
  - **Home and family:** medical plans and claims, health records, the home, vehicles and other assets with warranties, insurance and maintenance (seasonal checklists included), utilities, trips, pets, family money (shared expenses, family loans, allowances and chores), side income (invoices, hours worked, rental properties), card rewards, volunteer hours, contacts linked to the records they serve, and an emergency and estate summary.
  - **Calendar:** appointments with reminders, agenda to year views, work and school schedules, children's activities, and the calendars brought in from phones.
  - **Reports:** charts with drill-down, a custom report builder, reports by person or chosen accounts, saved and scheduled reports, the year in review.
  - Several users with private account groups, encrypted backups, a full user manual in its own window, short help for every screen, and Walk-Me guides that take you step by step through common tasks, all in the Help menu.
- **Android companion app, RANN's Roost Mobile** (sold separately on Google Play; not in this repository) captures receipts, bills, expenses, voice notes, readings, trips (with stops, breaks, photos and addresses) and fuel on the spot, finds stations nearby (opt-in) and sends them to the desktop over your home Wi-Fi (or through a cloud folder, email or USB), shows balances, budgets, what is due, a 60-day agenda and the day's schedules with reminders, can sync calendars both ways (opt-in), and carries the household's contacts, with new ones sent back for review.

All data stays on your own computer, encrypted. See the [release notes](docs/releases/1.0.0.en.md) ([français](docs/releases/1.0.0.fr.md)) for the full list and the known limits.

Status: **1.0.0 is being prepared.** Every phase of the [development plan](docs/development-plan.md) is built, including Phase 5 (AI reading, the tax package, transfer away from home, reports, the estate summary, usability and every Could item), Rates and rules, taxes for every province and territory, Contacts, calendars, seasonal checklists, trips and trackers, what the owner's real-phone test asked for, bills with statements and instalments, column headings, the Help menu and Walk-Me guides. Every exit check and security review is done, the manual and help were audited against every screen, and the release dry run passes on every package. Builds until the release are 0.9.x previews; the release steps are in [docs/release-checklist.md](docs/release-checklist.md). See the [requirements](docs/Household%20Finance%20Manager%20%E2%80%94%20Software%20Requirements%20Specification.md).

## Get the apps

- **Windows:** from the Microsoft Store (Windows 10 version 1809 or later, 64-bit), which installs, signs and updates it.
- **Linux:** .deb, .rpm and AppImage packages (64-bit x86) on [GitHub Releases](https://github.com/ScouterPerry01/Schippers.Household.Finance.Manager/releases), each with a minisign signature and listed in `SHA256SUMS`. Check a download with `minisign -Vm <file> -p core/update/src/main/resources/hfm/update/release-key.pub`. These copies can check GitHub for updates once a day, if you agree when first asked.
- **Android:** RANN's Roost Mobile on Google Play (Android 10 or later), CAD $4.99. It needs RANN's Roost on the computer.
- HEIC photos need the system's decoder: Microsoft's HEIF and HEVC Video Extensions on Windows, libheif with its HEVC plugin (such as `libheif-plugin-libde265`) on Linux.

The [user manual](core/i18n/src/main/resources/hfm/manual/en) ([français](core/i18n/src/main/resources/hfm/manual/fr)) is built into the app (Shift+F1), with help for each screen (F1).

## Privacy

RANN's Roost has no RANN account, no RANN server, no advertising and no analytics. The household is one folder encrypted with AES-256 under keys protected by the users' passwords; the phone talks only to the household's own computer, encrypted end to end. The apps contact the internet only for the Bank of Canada exchange rates (when a currency other than the Canadian dollar is used) and for features the user turns on: price downloads, AI reading with the user's own key, the cloud folder or email transfer, and update checks (Linux packages only). The phone uses Google's ML Kit, whose diagnostics are disclosed. The full [privacy policy](https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en) ([français](https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr)) is kept in [`website/`](website).

## Project layout

| Module | Contents |
|---|---|
| `core/money` | Exact money type (minor units, never floating point), currencies, locale formatting and parsing |
| `core/calc` | Financial calculations shared by both apps: amortization and loan schedules with prepayments, rate changes and what-ifs; adjusted cost base; time-weighted and money-weighted returns; allocation and rebalancing; T5, T3, RL-3 and RL-16 boxes; foreign exchange gains with the $200 exemption; Rates and rules (every rate, limit and threshold by effective date and province, built-in values in `hfm/rules` with their sources); sales taxes for every province and territory; the income tax estimate (federal, provincial and territorial, Quebec); registered plan rules (RRIF minimums, LIF maximums by jurisdiction, TFSA and FHSA limits, RESP grants); precious metal weights and values; medical plan coverage and the best 12-month period for the medical expense credit; asset depreciation; provinces and bank holidays; schedules for bills and events |
| `core/domain` | Ids, roles, permissions, account types |
| `core/security` | Argon2id, AES-256-GCM, X25519 key sealing and pair keys, recovery keys; watch-only Bitcoin addresses from an extended public key |
| `core/data` | Household folder format, key ring, encrypted document vault, SQLDelight schemas and upgrades |
| `core/data-jdbc` | Encrypted SQLite (SQLCipher v4) driver for the desktop |
| `core/books` | Bookkeeping services: accounts, transactions, credit cards with supplementary cards and benefits, reconciliation, bills, budgets, goals, loans and mortgages, investments and brokerage import, portfolio returns, market prices, crypto-assets, precious metals, registered plans and pensions, reports, calendar (with schedules, activities and calendars brought in from phones), health, medical plans and claims, home and other assets, warranties and insurance, pets, vehicles and trips, seasonal maintenance, utilities and trackers (hours, chores, volunteering), documents, phone sync, Quicken import, users, contacts and their links, rates and rules, the income tax estimate |
| `core/i18n` | English and French text |
| `core/importers` | Statement import (OFX/QFX/QBO, CSV), brokerage statements (OFX, broker CSV), crypto exchange histories (Kraken, Coinbase, Shakepay, Newton) and Quicken QIF |
| `core/ocr` | Text recognition interface and the field extractor shared with the phone |
| `core/ai` | Cloud AI reading with the user's own key (ADR 0009): document schemas, Claude through Anthropic's SDK, checks on every answer, the key in the system's secret store |
| `core/ocr-desktop` | PaddleOCR on ONNX Runtime, PDF reading and HEIC photos (with the decoder the user installs), for the desktop |
| `core/sync` | Pairing invitation, sealed transfer bundles, the phone's client and its contact book (also built into the phone app) |
| `core/update` | Signed release list, version comparison and download checks for the Linux packages (ADR 0008) |
| `app/desktop` | Compose Desktop application, including the listener for phones |
| `tools/release` | Makes the release key pair and signs releases (run by the release workflow) |
| `tools/natives` | Builds the small Windows helper that reads HEIC through Windows Imaging Component |
| `website/` | Text and images for the home page and privacy policy on rann.ca (Google Sites), in English and French |
| `branding/` | The RANN's Roost logo, Store tiles and icons (not covered by the GPL; see its README) |
| `spikes/` | Phase 0 feasibility experiments (not part of the build) |

Architecture decisions are recorded in [docs/adr](docs/adr).

## Building

Requirements: JDK 21.

```sh
./gradlew test                      # all core tests
./gradlew :app:desktop:run          # run the desktop app
./gradlew :app:desktop:runDemo      # try it with a throw-away sample household (-Plang=fr for French)
./gradlew :core:books:performanceTest  # NFR-02: times screens and reports on 30 years of data
./gradlew :app:desktop:packageMsix  # Microsoft Store package (on Windows, with the Windows SDK)
./gradlew :app:desktop:packageMsi   # Windows installer for testing (on Windows)
./gradlew :app:desktop:packageDeb   # Linux package (on Linux)
./gradlew build                     # everything (run before every commit)
./gradlew :app:desktop:manualScreenshots -Plang=en   # retake the manual's pictures offscreen (and fr)
./gradlew :app:desktop:storeScreenshots -Plang=en    # retake the Microsoft Store pictures offscreen (and fr)
./gradlew :app:desktop:suggestRuntimeModules         # JDK modules the packaged runtime needs
```

An installed or packaged copy checks itself when started with `JAVA_TOOL_OPTIONS=-Dhfm.selfcheck=<report file>`: it loads the JDK modules it was packaged with, reads a receipt line with OCR (and from a HEIC photo when a decoder is installed), prepares the AI client and creates an encrypted household, writes the report and exits. The release workflow runs it inside every package. After adding a library or a JDK API, compare `suggestRuntimeModules` with `modules(...)` in `app/desktop/build.gradle.kts`.

Releases are built by `.github/workflows/release.yml` from a tag `v<version>` (see [docs/release-checklist.md](docs/release-checklist.md) and [ADR 0008](docs/adr/0008-signed-releases-and-updates.md)); run by hand, it is a dry run with a throwaway key that publishes nothing. Release notes live in `docs/releases/<version>.en.md` and `.fr.md`.

The demo opens on any section with `-Psection=` (for example `DOCUMENTS`, `CONTACTS`, `PHONES`, `USERS`, `VEHICLES`, `GOALS`, `LOANS`, `INVESTMENTS`, `PLANS`, `TAXES`, `RATES`, `RATE_RULES`, `MEMBERS`), and on one account with `-Paccount=` (with `-Psection=ACCOUNTS`). `-Pmanual=bills` (or `bills#bill-form`, or `screen` for the screen shown) also opens the manual in its window. In English the sample household is a family in Ottawa, Ontario; in French (`-Plang=fr`) a family in Quebec City. Its users are `demo` / `demo-password` (administrator) and `sam` / `member-demo-password` (member).

## Support

GitHub Issues, or email info-rann-apps@NorthMail.ca.

## Licence

The source code is GPL-3.0-or-later. See [LICENSE](LICENSE).

RANN's Roost Mobile, the Android companion, is not part of this repository and is not covered by the GPL: its code is in a separate private repository, and it is sold on Google Play. It builds on this repository's shared core (`core/money`, `core/calc`, `core/domain`, `core/security`, `core/sync`, `core/ocr`).

The names RANN, RANN's Roost and RANN's Roost Mobile and the logo are © Perry Schippers, trading as RANN, and are not covered by the GPL (section 7(e)); see [branding/README.md](branding/README.md). A modified version you publish needs its own name and icon.

Code contributions are not accepted at this time; see [CONTRIBUTING.md](CONTRIBUTING.md).
