# Household Finance Manager

A personal, family and household finance application for Canada, in every province and territory, in English and French.

- **Desktop app** (Windows and Linux) holds the household's books: accounts, transactions, reconciliation, bills, investments, registered plans, medical claims, assets and reports. All data stays on your own computer, encrypted.
- **Android companion app** captures receipts, bills and expenses on the spot and sends them to the desktop over your home Wi-Fi.

Status: **Phase 2 (phone capture, Quicken import, user accounts) complete**: with Phase 1, this is the first usable release. **Phase 3 (wealth) complete**: loans and mortgages, investments with returns, asset allocation and tax slips, registered plans and pensions, market prices, crypto-assets, precious metals, foreign exchange gains, and card benefits. Phase 4 (home and health) in progress: medical plans and claims done. See the [requirements](docs/Household%20Finance%20Manager%20%E2%80%94%20Software%20Requirements%20Specification.md) and the [development plan](docs/development-plan.md).

## Project layout

| Module | Contents |
|---|---|
| `core/money` | Exact money type (minor units, never floating point), currencies, locale formatting and parsing |
| `core/calc` | Financial calculations shared by both apps: amortization and loan schedules with prepayments, rate changes and what-ifs; adjusted cost base; time-weighted and money-weighted returns; allocation and rebalancing; T5, T3, RL-3 and RL-16 boxes; foreign exchange gains with the $200 exemption; registered plan rules (RRIF minimums, LIF maximums by jurisdiction, TFSA and FHSA limits, RESP grants); precious metal weights and values; medical plan coverage and the best 12-month period for the medical expense credit; provinces and bank holidays; schedules for bills and events |
| `core/domain` | Ids, roles, permissions, account types |
| `core/security` | Argon2id, AES-256-GCM, X25519 key sealing and pair keys, recovery keys; watch-only Bitcoin addresses from an extended public key |
| `core/data` | Household folder format, key ring, encrypted document vault, SQLDelight schemas and upgrades |
| `core/data-jdbc` | Encrypted SQLite (SQLCipher v4) driver for the desktop |
| `core/books` | Bookkeeping services: accounts, transactions, credit cards with supplementary cards and benefits, reconciliation, bills, budgets, goals, loans and mortgages, investments and brokerage import, portfolio returns, market prices, crypto-assets, precious metals, registered plans and pensions, reports, calendar, health, medical plans and claims, pets, vehicles, documents, phone sync, Quicken import, users |
| `core/i18n` | English and French text |
| `core/importers` | Statement import (OFX/QFX/QBO, CSV), brokerage statements (OFX, broker CSV), crypto exchange histories (Kraken, Coinbase, Shakepay, Newton) and Quicken QIF |
| `core/ocr` | Text recognition interface and the field extractor shared with the phone |
| `core/ocr-desktop` | PaddleOCR on ONNX Runtime and PDF reading, for the desktop |
| `core/sync` | Pairing invitation, sealed transfer bundles and the phone's client |
| `app/desktop` | Compose Desktop application, including the listener for phones |
| `app/android` | Android companion: capture, encrypted queue, transfer, summaries |
| `spikes/` | Phase 0 feasibility experiments (not part of the build) |

Architecture decisions are recorded in [docs/adr](docs/adr).

## Building

Requirements: JDK 21. For the Android app, the Android SDK (API 37).

```sh
./gradlew test                      # all core tests
./gradlew :app:desktop:run          # run the desktop app
./gradlew :app:desktop:runDemo      # try it with a throw-away sample household (-Plang=fr for French)
./gradlew :core:books:performanceTest  # NFR-02: times screens and reports on 30 years of data
./gradlew :app:desktop:packageMsi   # Windows installer (on Windows)
./gradlew :app:desktop:packageDeb   # Linux package (on Linux)
./gradlew :app:android:assembleDebug
```

The demo opens on any section with `-Psection=` (for example `DOCUMENTS`, `PHONES`, `USERS`, `VEHICLES`, `GOALS`, `LOANS`, `INVESTMENTS`, `PLANS`, `RATES`, `MEMBERS`), and on one account with `-Paccount=`. Its users are `demo` / `demo-password` (administrator) and `sam` / `sam-demo-password` (member).

## Support

GitHub Issues, or email info-rann-apps@NorthMail.ca.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE). Code contributions are not accepted at this time; see [CONTRIBUTING.md](CONTRIBUTING.md).
