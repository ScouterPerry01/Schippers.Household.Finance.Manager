# Household Finance Manager — Development Plan

Based on the *Household Finance Manager — Software Requirements Specification* (Oct 1, 2026) and its phase roadmap (`SRS Phases.png`). Requirement IDs refer to the SRS.

## Guiding principles

- **Build the Must-level foundations in from the start.** Encryption, the multi-user and permission model, exact decimal money, the audit trail and EN/FR localization are designed into Phase 0, even where their screens come later.
- **Calculations come first and are fully tested.** All financial math (interest, amortization, ACB, FX, RRIF/LIF minimums, reconciliation) lives in one shared module with no user interface, under automated tests (ARC-03, NFR-14).
- **Every phase ends with something the household uses.**
- **Release milestones:**
  - *First usable release* = end of Phase 2 (private household use; Google Play closed testing).
  - *Public 1.0* = end of Phase 4, when every **Must** requirement is complete.
  - Phase 5 delivers Should/Could extras after 1.0.

## Technology stack (confirms SRS §2.2)

| Concern | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin Multiplatform + Compose Multiplatform | Desktop on the JVM, plus Android |
| Database | SQLDelight over SQLite + SQLCipher | SQLCipher for Android on the phone; an SQLCipher-capable JDBC driver (e.g. `sqlite-jdbc-crypt`) on the desktop |
| Money | Custom `Money` type: a `Long` in minor units, with the scale set by the currency (ISO 4217) | Crypto to 8 decimals. Never floating point (NFR-04) |
| Dates | `kotlinx-datetime` | |
| Cryptography | libsodium (lazysodium) or the Tink library; Argon2id to derive keys from passwords | Database key, vault, sync bundles, pairing |
| OS secret storage | Windows Credential Manager / Linux Secret Service (`java-keyring` or JNA) | AI API keys (AI-02); a small native helper for Windows Hello (SEC-02) |
| OCR | Android: ML Kit. Desktop: PaddleOCR on ONNX Runtime, with Tesseract as fallback | Both behind one shared `OcrEngine` interface |
| Sync transport | Small server inside the desktop app (Ktor) on the local network; phones find it automatically (mDNS) | TLS plus end-to-end encrypted bundles |
| Charts | Compose charting library, or custom drawing on Canvas | Clicking a bar or slice drills down to the transactions (RPT-01) |
| Export | OpenPDF / PDFBox, Apache POI or FastExcel | RPT-04 |
| Build / CI | Gradle and GitHub Actions; packaging with `jpackage` / Conveyor / Compose installers | MSIX/MSI, .deb, .rpm, AppImage; Flatpak-ready from the start |

## Module layout

```
:core:money        Money, Currency, FX conversion, rounding
:core:calc         interest, amortization, ACB, RRIF/LIF, contribution room, forecasting
:core:domain       entities, use cases, permission checks
:core:data         SQLDelight schema, migrations, repositories, encryption
:core:importers    importer plug-in API + OFX/QFX/QBO/CSV/QIF/exchange/brokerage importers
:core:sync         bundle format, crypto, protocol (shared by both apps)
:core:ocr          OcrEngine interface, field extractors, AI JSON-Schema client
:app:desktop       Compose Desktop UI, sync server, vault, backup, reports
:app:android       Compose Android UI, capture, queue, sync client
```

---

## Phase 0: Foundations

**Goal:** a project that builds, with the hard architectural decisions settled before any features.

- Repository, GPL-3.0 licence, contributor agreement, contribution guide, issue templates.
- CI (build, tests, lint) on Windows, Linux and Android.
- **Security design:**
  - The master password unlocks the database through Argon2id key derivation (SEC-01).
  - A printed recovery key is generated at setup (SEC-07).
  - Per-user keys wrap private account groups (HH-11). Prototype this now; it is the hardest part of the design.
- **Data model v1:**
  - Household, users, members, roles, account groups and permissions. The schema is complete now, even though the user screens come in Phase 2.
  - Institutions, accounts, transactions with splits and transfers.
  - Categories, payees with aliases, tags, documents, audit trail.
  - IDs that stay unique across devices (SYNC-02).
- Database version tracking and automatic upgrades, with a backup taken before every upgrade (NFR-11, NFR-05).
- `Money` and `Currency` with property-based tests; the first `:core:calc` tests.
- EN/FR resource setup, locale-aware formatting (NFR-06/07), base theme with dark mode.
- **Technical spikes:**
  - SQLCipher JDBC with SQLDelight on Windows and Linux.
  - PaddleOCR on ONNX Runtime on the JVM.
  - Windows Hello from the JVM.

**Exit:** the app creates and opens an encrypted household file, and tests pass on all platforms in CI.

**Admin task:** register a Microsoft Partner Center developer account.

## Phase 1: Core desktop

**Goal:** the household's full bookkeeping on the desktop, including statement import and reconciliation. Runs as a single administrator user.

- **Security:** auto-lock, with Windows Hello as an option (SEC-02); masked sensitive fields that need re-authentication to reveal (SEC-04).
- **Institutions and accounts:** all ACC-02 account types (basic balance tracking for the investment and loan types until Phase 3), owners, status, opening balances (ACC-01–05).
- **Credit cards:** limits, rates, statement and due days, minimum payment rule, statement cycle tracking (CC-01/02).
- **Transactions:**
  - Register view, splits, transfers (TX-01–03).
  - Keyboard-first entry, auto-complete, calculator in amount fields, templates (MAN-01–05).
  - Search and filter (TX-06), bulk edit (TX-07), audit trail (TX-08).
- **Categories, rules and payees:**
  - Bilingual default category tree (CAT-01), rules (CAT-02), tags (CAT-04).
  - Payee aliases (§7.4), income categories (SAL-01).
- **Multi-currency:**
  - One currency per account, with a household base currency.
  - Bank of Canada daily rates plus manual override.
  - Original and converted amounts with the rate charged; FX transfers (FX-01–04).
- **Statement import:**
  - Importer plug-in API (ARC-04).
  - OFX/QFX/QBO, and CSV with a saved column mapping per institution (REC-01).
  - Duplicate protection for transactions and statements (REC-10).
- **Reconciliation:**
  - The five-step flow: automatic matching with adjustable tolerances (REC-02), three exception lists, running difference (REC-05), balance check, period lock.
  - Saved reconciliation report (REC-06), undo with a logged reason (REC-07), last-reconciled dashboard (REC-09).
- **Bills and reminders:**
  - Bill records and every recurrence rule, including "last business day" (BILL-01/02).
  - Desktop reminders (BILL-04), calendar and list views (BILL-05).
  - Mark as paid creates or links the transaction (BILL-06).
  - Annual and irregular expenses (BILL-11), subscription tracker (BILL-10), cash-flow forecast (BILL-08).
- **Budgets:** monthly and annual category budgets with optional rollover (BUD-01).
- **Core reports:**
  - Dashboard, net worth, income and expense, spending by category or payee, budget vs. actual.
  - Account register, reconciliation history, bills and subscriptions.
  - Click-to-drill-down (RPT-01), period comparison (RPT-02), PDF/CSV/print export (RPT-04).
- **Backups and export:**
  - Scheduled encrypted backups, restore and test-restore, moving to a new computer (BAK-01–03, 05).
  - Backup reminder (BAK-04), full CSV/JSON export (EXP-01).
- **Other:** global search (OTH-03); performance test with 250k transactions and 50k documents added to CI (NFR-02).

**Exit:** the household runs its books on the desktop for one month. At least one account of each type (bank, card) reconciles to zero, and restoring from a backup has been verified.

## Phase 2: Phone capture

**Goal:** the phone feeds the desktop, Quicken is retired, and every household member has their own user account. **Phases 1 and 2 together = first usable release.**

**2a: Document vault and desktop OCR**
- Encrypted document vault: documents linked to records, text search, retention flags (§4.4).
- Desktop import by drag-and-drop and a watched folder (CAP-03/04).
- Desktop OCR extracting the OCR-02 fields in EN/FR, with confidence levels and duplicate detection (OCR-01, 02, 04, 05, 10).
- Captured bill creates or updates the bill record (BILL-03).

**2b: Wi-Fi pairing and sync**
- QR-code pairing: the phone scans the desktop's public key and address, and both sides derive a shared key (SYNC-03).
- Unique item IDs (SYNC-02), the desktop confirms receipt so the phone can mark items delivered (SYNC-04).
- Revoking a lost phone (SYNC-08), transfer status on both devices (SYNC-09).
- Reference data sent back to the phone (SYNC-06); several phones per desktop (SYNC-07).
- Desktop review inbox (SYNC-05).

**2c: Android app**
- PIN or biometric lock; encrypted offline queue (SEC-03, SYNC-01).
- One-tap capture with edge detection, crop and de-skew; multi-page documents (CAP-01/02/07).
- Share-to from other apps (CAP-05); quick manual expense entry.
- ML Kit OCR (OCR-01).
- Phone reminders for bills (BILL-04); read-only summaries (RPT-06).
- Odometer and hour-meter entry. The screen is built here, but the readings are used in Phase 4.

**2d: Quicken import and user accounts**
- QIF import from Quicken, GnuCash and Moneydance: accounts, categories, payees, transactions, splits, transfers, investment history (OTH-05).
- User accounts, desktop sign-in, roles (HH-05/06).
- Account groups and per-group permissions (HH-07/08); per-user ownership and default privacy (HH-09).
- Every view filtered by what the signed-in user may see (HH-10); private-account encryption (HH-11).
- Captures routed to each member's review inbox (HH-12); per-user activity log (HH-13); member phone access (HH-02).

**Exit:**
- A receipt photographed on a member's phone reaches that member's review inbox and matches a statement line.
- A full Quicken history imports and balances.
- Each user sees only what they are allowed to see.

**Admin task:** start the Google Play closed test (12 testers, 14 days) during 2c.

## Phase 3: Wealth

- **Investments:**
  - Securities master and every transaction type (INV-01/02).
  - ACB across non-registered accounts (INV-03).
  - Daily prices plus manual prices (INV-04); brokerage import (INV-05).
  - Performance and allocation (INV-06/07); income report for tax slips (INV-08).
- **Registered plans:** RRSP/spousal RRSP, RRIF, LIRA/LIF, TFSA, FHSA, RESP rules.
  - Contribution room per person with over-contribution warnings (INV-09).
  - RRIF/LIF minimum withdrawals and reminders (INV-10).
  - Beneficiaries and successor holders (INV-11).
- **Pensions:** defined benefit and defined contribution plans, CPP/QPP, OAS. Annual statements and payments received.
- **Loans and mortgages:**
  - Canadian semi-annual compounding and every payment frequency (LN-01).
  - Amortization schedule (LN-02); prepayments and rate changes (LN-03).
  - Renewal reminders (LN-04); escrowed property tax and insurance (LN-05); what-if calculator (LN-06).
- **Bitcoin and crypto:**
  - Wallets to 8 decimals (CR-01) with buys, sells, transfers, fees and income (CR-04).
  - Prices (CR-05), ACB (CR-06).
  - Exchange CSV importers (CR-03); watch-only address/xpub lookup (CR-02).
- **Precious metals:** item records (PM-01), spot-price valuation (PM-02), storage location (PM-03), certificates in the vault (PM-04).
- **Remaining FX and credit-card work:**
  - Realized and unrealized FX gains and losses; reports in base or original currency (FX-05/06).
  - FX-fee posting during reconciliation (REC-04); investment reconciliation (REC-08).
  - Card benefits and supplementary cards (CC-04/05).
- **Reports:** debt summary, investment portfolio, investment income and capital gains, registered plans.

**Exit:** every `:core:calc` routine is checked against published reference figures (CRA examples, bank amortization tables, RRIF minimum-withdrawal tables).

## Phase 4: Home and health

- **Medical plans and claims:**
  - Plans with coordination of benefits between spouses' plans, all MED-03 plan types (MED-01/03).
  - Coverage rules and remaining annual maximums (MED-02/04); plan documents in the vault (MED-05).
  - Expenses and the full claim lifecycle (MED-06/07); EOB matching (MED-08).
  - Submission deadline reminders (MED-09); out-of-pocket amounts (MED-10).
  - Best 12-month window report, federal and Quebec totals, PDF receipt bundle (MED-12/14/15).
- **Home inventory:**
  - Asset records and parent/child assets (AST-01/02); value feeding net worth (AST-03).
  - Inventory report (AST-04); disposal linked to sales (AST-05, SAL-03).
- **Warranties:** records (WAR-01), expiry reminders (WAR-02), claim log (WAR-03), "is this still covered?" lookup (WAR-04).
- **Insurance:** policies (INS-01), links to assets (INS-02), renewals and premium history (INS-03), claims (INS-04), life insurance summary (INS-05).
- **Maintenance:**
  - Tasks scheduled by time, usage or season (MNT-01); starter templates (MNT-02).
  - Odometer and hour-meter forecasting (MNT-03); service log (MNT-04).
  - Combined "due this month" list on desktop and phone (MNT-05); cost of ownership (MNT-06).
- **Reports:** medical expenses, assets and warranties, maintenance.
- **Release hardening:**
  - Signed builds and signed updates (SEC-08); update checks (DIST-05).
  - Privacy policy (DIST-04); EN/FR store listings (DIST-07); TAX-04 notice; PRV-01–03 review.
- **Selling decisions (early in this phase):**
  - Recheck Flathub paid-app status.
  - Choose the Linux storefront and start merchant onboarding; allow for verification lead time.
  - Prepare the product page, terms and refund policy.

**Exit (= Public 1.0):**
- All **Must** requirements are complete.
- Microsoft Store submission is made.
- Linux paid builds go live with checksums.
- The Android app is released publicly on Google Play and GitHub Releases.

## Phase 5: Extras (post-1.0)

- **Budgets and goals:** sinking funds (BUD-02), savings goals (BUD-03), phone budget alerts (BUD-04), budget built from the last 12 months (BUD-05).
- **Tax package:**
  - Slip checklist (TAX-01) and year-end package (TAX-02); instalment reminders (TAX-03).
  - Tax flags on categories (CAT-05), sales tax per transaction (TX-04), pay stubs (SAL-02), donations (OTH-01).
- **Cloud transfer:** Google Drive / OneDrive / Dropbox / Nextcloud bundle folder with narrow access scopes (§3.2).
- **Email fallback and manual file transfer:** email to self with an encrypted attachment and tagged subject (§3.1); USB or SD-card bundle.
- **Custom reports:** report builder (RPT-03), scheduled reports (RPT-05), reports by person or account group (RPT-07), currency exposure, year-in-review.
- **Estate summary:** emergency summary (EST-01), will and power-of-attorney locations (EST-02), encrypted export for a spouse or executor (EST-03).
- **AI extraction:**
  - Opt-in Claude API image + JSON Schema extraction (AI-01–05), with a preview and blurring step before sending.
  - Usage log (AI-06); additional providers (AI-07).
  - Smarter OCR: line items, learning from corrections, document-type detection, statement parsing (OCR-03/06–09).
- **Usability:** onboarding wizard (OTH-04), built-in help in both languages (NFR-12), WCAG 2.1 AA pass (NFR-08).
- **Remaining Could items** as time allows (CC-03, LN-07, SAL-04/05, HH-03/04, MNT-07–09, OTH-02, etc.).

---

## Cross-cutting work (every phase)

- **Testing:**
  - Unit and property tests in `:core:*`.
  - Importers tested against anonymized real sample files.
  - Golden-file tests for reports.
  - Performance test in CI (NFR-02).
  - Upgrade tests from every earlier database version.
- **Security review** at the end of each phase, with special attention to sync, the vault and anything that leaves the device.
- **Translation:** every user-facing string lives in the EN/FR resource files from the first commit.
- **Documentation:** user guide and changelog updated as features ship.

## Key risks

1. **Per-user encryption (HH-11) inside a single shared database.** Prototyped in Phase 0.
2. **Desktop OCR quality** with PaddleOCR on the JVM. Spiked in Phase 0 and built in Phase 2a.
3. **SQLCipher on the JVM desktop.** Spiked in Phase 0.
4. **Windows Hello from the JVM.** Optional, so it can slip if needed.
5. **Scope.** This is a very large specification for a solo build. Stick strictly to the Must → Should → Could order within each phase.

## Decisions log

| Topic | Decision |
|---|---|
| "Must" priority | Must = public 1.0, reached at the end of Phase 4 |
| First usable release | End of Phase 2 (private household use) |
| BUD-01 basic budgets + budget vs. actual | Moved to Phase 1. Goals and sinking funds stay in Phase 5 |
| User accounts | Schema in Phase 0, screens and enforcement in Phase 2. Phase 1 runs as a single administrator |
| Apple platforms | Out of scope (ARC-05 needs no priority) |
| Flathub | Leaning toward it, but paid apps are not yet live. Keep builds Flatpak-ready; recheck early in Phase 4 |
| Linux storefront | Decided early in Phase 4, before the first paid Linux release. Doesn't affect the code |
| Microsoft Partner Center | Register during Phase 1 |
| Google Play closed test | Start during Phase 2c |
| Household storage (ARC-01) | A household is one encrypted **folder**: per-group encrypted databases plus an encrypted document vault. Backups and moves to a new computer are a single encrypted archive file (ADR 0002) |
| Shared code | Plain Kotlin/JVM libraries rather than Kotlin Multiplatform modules; no visible difference to users. Converting is possible later if iPhone or web is ever wanted (ADR 0001) |
| Windows Hello | NativeAOT helper exe with KeyCredentialManager-derived key; optional per-machine slot; Linux password only (ADR 0003) |
| Desktop OCR | PaddleOCR PP-OCRv5 (latin) via ONNX Runtime; Tesseract not needed (ADR 0004) |
| Microsoft Store | MSIX listing "Schippers Household Finance Manager", identity in `app/desktop/packaging/msix/store-identity.properties` |
| Android package | `ca.schippers.hfm.companion` (permanent once uploaded to Google Play) |
| Support | GitHub Issues and info-rann-apps@NorthMail.ca (DIST-06) |
| Windows Hello | Optional, off by default, opt-in per computer; low priority (the owner does not use it) |
| Contributor agreement | Undecided; until then, code contributions are not accepted. Draft in `docs/legal/contributor-license-agreement-DRAFT.md` |
| Backups | One encrypted `.hfmbak` per backup, checked after writing, restored into a new folder (ADR 0005) |
| Phase 1 status (2026-10-02) | Complete. Deferred to later phases: REC-03 (one-to-many matching), REC-04 (FX fee posting), statement image with the report (needs the vault), BILL-03 (bill from a capture), 50,000-document part of NFR-02 |
| Owner requests (2026-10-02) | Added before Phase 2 (see `docs/requirements-additions.md`): followed currencies and an optional second rate source, off by default (FX-07, FX-08); a calendar for appointments and events of any kind, with reminders (CAL-01 to CAL-06); a Health section per person (HLT-01 to HLT-07). Still to come: printable health summary (HLT-09) and the link to medical claims (HLT-08, Phase 4); calendar and reminders on the phone (Phase 2) |
| Working language | Features are built and reviewed in English first, then French |
