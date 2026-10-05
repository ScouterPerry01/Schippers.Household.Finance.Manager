# ADR 0002: Household storage and encryption

Status: Accepted (Phase 0, 2026-10-01). **Note the deviation from the wording of ARC-01, described below.**

## Context

- ARC-01: all data stored locally, encrypted, usable offline.
- SEC-01: AES-256 encryption at rest, key derived from the master password.
- SEC-07: a printed recovery key, since there is no server.
- HH-05 to HH-11: several users, each with their own password. Access is per account group, and **private accounts are unreadable by other users even by opening the data file directly** (HH-11).
- NFR-02: 250,000 transactions and 50,000 documents must stay fast.

HH-11 rules out a single database file under a single key: anyone who can open that file can read everything in it. Encrypting individual columns inside one database would break SQL queries, sums and indexes, which the reports depend on.

## Decision

A household is a **folder** (for example `Schippers.hfm/`), treated by the application as one unit:

```
Schippers.hfm/
  household.json          key ring: no financial data (see below)
  core.db                 SQLCipher: users, groups, permissions, categories, payees, settings, audit log
  ledger-<group>.db       SQLCipher: one per account group: accounts, transactions, documents, change log
  vault/                  encrypted document files (Phase 2)
  backups/pre-upgrade/    automatic copies taken before schema upgrades (NFR-11)
```

### Keys

- Every **partition** (`core.db` and each ledger) has its own random 256-bit key. SQLCipher v4 uses it directly as a raw key: AES-256-CBC pages with HMAC-SHA512 page authentication.
- Every **user** has an X25519 key pair.
  - The private key is wrapped with AES-256-GCM under a key derived from the user's password with Argon2id (64 MiB, 3 passes).
  - It is wrapped a second time under a key derived (HKDF) from the user's printed **recovery key** (SEC-07).
- A **grant** gives a user access to a partition. It is the partition key sealed to that user's public key (X25519 + HKDF-SHA256 + AES-256-GCM).
  - Granting therefore needs only the grantee's public key, never their password.
  - A user without a grant does not have the partition key at all (HH-11).
- Every wrapped value carries authenticated data binding it to its household, user and partition, so values cannot be swapped between entries.

### Who gets which key

- **`core.db`:** every user.
- **Shared groups:** every administrator, plus each member granted a permission.
- **Private groups:** the owner, plus whoever the owner shares with. Administrators are not included unless the owner shares the group (HH-09).

`household.json` is the only unencrypted file. It holds login names, salts, KDF settings, public keys and sealed keys, but no names of people, amounts or institutions.

- **Tampering:** public keys used for new grants are always read from the authenticated `core.db`, never from the header, so editing the header cannot redirect a grant to an attacker's key.
- **Safe writes:** the header is written atomically and the previous version is kept as `household.json.bak`.

## Deviation from ARC-01

ARC-01 says "one encrypted household data file". This design uses one encrypted household **folder**, for two reasons:

1. HH-11 requires per-user encryption.
2. 50,000 scanned documents do not belong inside a SQLite file.

The user still handles a household as one item:

- **Backups (BAK-01) and moving to a new computer (BAK-05):** produce a single encrypted archive file.
- **On Linux:** the folder can be associated with the application.

## Consequences

- **Reports across groups:** reports covering several groups run one query per open ledger and combine the results. The number of groups is small (typically under 10).
- **Transfers between groups:** a transfer between accounts in different groups is stored as two linked rows (same `transfer_id`), one in each ledger.
- **Revocation:** removing a grant stops future access. A user who already held the key could have copied it, so true revocation needs key rotation (re-encrypting the ledger under a new key). Not built yet. Removing a phone (SYNC-08) does not need it: a phone never holds household keys, only its pair key (ADR 0006).
- **Windows Hello (ADR 0003):** Hello unlock adds another wrapping of the user's private key. Nothing else changes.
- **Upgrades:** every database records its schema version in `PRAGMA user_version`. Upgrades checkpoint the write-ahead log and copy the file before migrating, and a database written by a newer version of the application is refused.

## Verified in Phase 0

`core/data-jdbc` tests check that:

- files are not plain SQLite;
- wrong keys and passwords fail;
- private groups cannot be opened by an administrator without a grant;
- grants and sharing work;
- the recovery key resets the password;
- a tampered header cannot redirect grants;
- upgrades back up first and keep the data.

## Added in Phase 2 (2026-10-02)

**Document vault** (`vault/<partition>/<document>.hfmdoc`, `DocumentVault`):
- One file per document, sealed with AES-256-GCM under a key derived (HKDF) from its group's partition key.
- The household, partition and document ids are the authenticated data, so a file cannot be swapped with another or moved to another group.
- Whoever lacks the group's key cannot read the group's documents, as for its ledger (HH-11). Backups already include `vault/`.

**Data sealed for one user** (`HouseholdSession.sealFor` / `openSealed`):
- Sealed to the user's public key (the same sealed box as grants), with a context bound in.
- Used for each phone's pair key, so only the phone's owner can read its captures (ADR 0006).

**User management** (`setRole`, `setActive`, `changePassword`, `linkMember`):
- A new administrator receives the keys of every shared group. A former administrator keeps only the groups granted explicitly.
- A deactivated user cannot sign in.
- The household always keeps at least one active administrator.
- Changing a password re-wraps the private key; the recovery key stays valid.

**Schema versions:** core 7 (pets, paired devices; the province or territory in Phase 3; metal spot prices in Phase 3d; saved and scheduled reports in Phase 5e), ledger 25 (goals, vehicles, document review fields; loans in Phase 3a; securities, prices, investment transactions and statements in Phase 3b, kept per group so private holdings stay private; registered plan details, contribution room, RESP grants and pensions in Phase 3c; LIF jurisdictions and the B.C. grant for provinces and territories; crypto-asset wallet details and precious metal items in Phase 3d; fund mixes for the asset allocation, investment tax slips, cardholders and card benefits in Phase 3e; medical plans, expenses and claims in Phase 4a; assets, warranties and insurance policies in Phase 4b; maintenance tasks, meter readings and the service log for assets in Phase 4c; an index for the documents list in the Phase 4 exit check; AI readings of documents and the AI usage log in Phase 5a, ADR 0009; what was learned from the user's corrections per merchant, OCR-07; sales taxes per transaction and refunds linked to their purchase in Phase 5b, TX-04 and TX-05; the official receipts of donations, OTH-01; the tax slip checklist and tax instalments, TAX-01 and TAX-03; estate records in Phase 5f, EST-02; shared expenses, family loans and allowances in Phase 5h, HH-03, HH-04 and LN-07; the trip log, contractors, home projects, invoices, rental properties and card rewards, OTH-02, MNT-08, MNT-09, SAL-04, SAL-05 and CC-03). Each step is a verified migration with an upgrade test from every earlier version.

`core/data-jdbc` tests (`DocumentVaultTest`, `UserManagementTest`, `MigrationTest`) cover these.

