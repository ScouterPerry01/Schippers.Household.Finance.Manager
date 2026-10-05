# RANN's Roost

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="branding/logo/Rann_Roost_Kite_Left_Facing_White_Text.png">
  <img src="branding/logo/Rann_Roost_Kite_Left_Facing.png" alt="RANN's Roost logo: a brahminy kite in flight" width="360">
</picture>

A personal, family and household finance application for Canada, in every province and territory, in English and French. Published by RANN; called Household Finance Manager until October 2026.

- **Desktop app, RANN's Roost** (Windows and Linux) holds the household's books: accounts, transactions, reconciliation, bills, investments, registered plans, medical claims, assets, taxes (slips to expect, donations and their receipts, instalments, pay stubs, sales tax, a year-end package for the accountant), an emergency and estate summary, family money (shared expenses, family loans, allowances), a trip log, home projects and contractors, side income (invoices and rental properties), card rewards, and reports. All data stays on your own computer, encrypted.
- **Android companion app, RANN's Roost Mobile** captures receipts, bills and expenses on the spot and sends them to the desktop over your home Wi-Fi.

Status: **Phase 2 (phone capture, Quicken import, user accounts) complete**: with Phase 1, this is the first usable release. **Phase 3 (wealth) complete**: loans and mortgages, investments with returns, asset allocation and tax slips, registered plans and pensions, market prices, crypto-assets, precious metals, foreign exchange gains, and card benefits. Phase 4 (home and health): medical plans and claims, the home and other assets, warranties, insurance, maintenance, and release hardening (signed releases and update checks, privacy policy, store texts) done. Phase 5 (extras) comes next and is part of the public 1.0; builds until then are 0.9.x previews. See the [requirements](docs/Household%20Finance%20Manager%20%E2%80%94%20Software%20Requirements%20Specification.md) and the [development plan](docs/development-plan.md).

## Project layout

| Module | Contents |
|---|---|
| `core/money` | Exact money type (minor units, never floating point), currencies, locale formatting and parsing |
| `core/calc` | Financial calculations shared by both apps: amortization and loan schedules with prepayments, rate changes and what-ifs; adjusted cost base; time-weighted and money-weighted returns; allocation and rebalancing; T5, T3, RL-3 and RL-16 boxes; foreign exchange gains with the $200 exemption; registered plan rules (RRIF minimums, LIF maximums by jurisdiction, TFSA and FHSA limits, RESP grants); precious metal weights and values; medical plan coverage and the best 12-month period for the medical expense credit; asset depreciation; provinces and bank holidays; schedules for bills and events |
| `core/domain` | Ids, roles, permissions, account types |
| `core/security` | Argon2id, AES-256-GCM, X25519 key sealing and pair keys, recovery keys; watch-only Bitcoin addresses from an extended public key |
| `core/data` | Household folder format, key ring, encrypted document vault, SQLDelight schemas and upgrades |
| `core/data-jdbc` | Encrypted SQLite (SQLCipher v4) driver for the desktop |
| `core/books` | Bookkeeping services: accounts, transactions, credit cards with supplementary cards and benefits, reconciliation, bills, budgets, goals, loans and mortgages, investments and brokerage import, portfolio returns, market prices, crypto-assets, precious metals, registered plans and pensions, reports, calendar, health, medical plans and claims, home and other assets, warranties and insurance, pets, vehicles, documents, phone sync, Quicken import, users |
| `core/i18n` | English and French text |
| `core/importers` | Statement import (OFX/QFX/QBO, CSV), brokerage statements (OFX, broker CSV), crypto exchange histories (Kraken, Coinbase, Shakepay, Newton) and Quicken QIF |
| `core/ocr` | Text recognition interface and the field extractor shared with the phone |
| `core/ai` | Cloud AI reading with the user's own key (ADR 0009): document schemas, Claude through Anthropic's SDK, checks on every answer, the key in the system's secret store |
| `core/ocr-desktop` | PaddleOCR on ONNX Runtime, PDF reading and HEIC photos (with the decoder the user installs), for the desktop |
| `core/sync` | Pairing invitation, sealed transfer bundles and the phone's client |
| `core/update` | Signed release list, version comparison and download checks, shared by the desktop and the phone (ADR 0008) |
| `app/desktop` | Compose Desktop application, including the listener for phones |
| `app/android` | Android companion: capture, encrypted queue, transfer, summaries; `play` and `github` flavours |
| `tools/release` | Makes the release key pair and signs releases (run by the release workflow) |
| `tools/natives` | Builds the small Windows helper that reads HEIC through Windows Imaging Component |
| `website/` | Text and images for the home page and privacy policy on rann.ca (Google Sites), in English and French |
| `branding/` | The RANN's Roost logo, Store tiles and icons (not covered by the GPL; see its README) |
| `spikes/` | Phase 0 feasibility experiments (not part of the build) |

Architecture decisions are recorded in [docs/adr](docs/adr).

## Building

Requirements: JDK 21. For the Android app, the Android SDK (API 37).

```sh
./gradlew test                      # all core tests
./gradlew :app:desktop:run          # run the desktop app
./gradlew :app:desktop:runDemo      # try it with a throw-away sample household (-Plang=fr for French)
./gradlew :core:books:performanceTest  # NFR-02: times screens and reports on 30 years of data
./gradlew :app:desktop:packageMsix  # Microsoft Store package (on Windows, with the Windows SDK)
./gradlew :app:desktop:packageMsi   # Windows installer for testing (on Windows)
./gradlew :app:desktop:packageDeb   # Linux package (on Linux)
./gradlew :app:android:assembleGithubDebug   # phone app (the play flavour has no update check)
```

The demo opens on any section with `-Psection=` (for example `DOCUMENTS`, `PHONES`, `USERS`, `VEHICLES`, `GOALS`, `LOANS`, `INVESTMENTS`, `PLANS`, `RATES`, `MEMBERS`), and on one account with `-Paccount=` (with `-Psection=ACCOUNTS`). In English the sample household is a family in Ottawa, Ontario; in French (`-Plang=fr`) a family in Quebec City. Its users are `demo` / `demo-password` (administrator) and `sam` / `sam-demo-password` (member).

## Support

GitHub Issues, or email info-rann-apps@NorthMail.ca.

## Licence

The source code is GPL-3.0-or-later. See [LICENSE](LICENSE).

The names RANN, RANN's Roost and RANN's Roost Mobile and the logo are © Perry Schippers, trading as RANN, and are not covered by the GPL (section 7(e)); see [branding/README.md](branding/README.md). A modified version you publish needs its own name and icon.

Code contributions are not accepted at this time; see [CONTRIBUTING.md](CONTRIBUTING.md).
