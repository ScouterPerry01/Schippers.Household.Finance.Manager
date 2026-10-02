# Household Finance Manager

A personal, family and household finance application for Canada (Quebec first), in English and French.

- **Desktop app** (Windows and Linux) holds the household's books: accounts, transactions, reconciliation, bills, investments, registered plans, medical claims, assets and reports. All data stays on your own computer, encrypted.
- **Android companion app** captures receipts, bills and expenses on the spot and sends them to the desktop over your home Wi-Fi.

Status: **Phase 1 (core desktop) complete**; Phase 2 (phone capture) next. See the [requirements](docs/Household%20Finance%20Manager%20%E2%80%94%20Software%20Requirements%20Specification.md) and the [development plan](docs/development-plan.md).

## Project layout

| Module | Contents |
|---|---|
| `core/money` | Exact money type (minor units, never floating point), currencies, locale formatting and parsing |
| `core/calc` | Financial calculations shared by both apps: amortization (more in later phases) |
| `core/domain` | Ids, roles, permissions, account types |
| `core/security` | Argon2id, AES-256-GCM, X25519 key sealing, recovery keys |
| `core/data` | Household folder format, key ring, SQLDelight schemas, schema upgrades |
| `core/data-jdbc` | Encrypted SQLite (SQLCipher v4) driver for the desktop |
| `core/books` | Bookkeeping rules: categories, payees, institutions, members, accounts, transactions, splits, transfers |
| `core/i18n` | English and French text |
| `core/importers`, `core/ocr`, `core/sync` | Interfaces for statement import, text recognition and phone sync |
| `app/desktop` | Compose Desktop application |
| `app/android` | Android companion application |
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

## Support

GitHub Issues, or email info-rann-apps@NorthMail.ca.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE). Code contributions are not accepted at this time; see [CONTRIBUTING.md](CONTRIBUTING.md).
