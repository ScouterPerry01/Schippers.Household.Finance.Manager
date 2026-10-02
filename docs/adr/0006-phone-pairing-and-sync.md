# ADR 0006: Phone pairing and transfer

Status: Accepted (Phase 2, 2026-10-02)

## Context

SRS section 3 (SYNC-01 to SYNC-09), with HH-02, HH-11 and HH-12:
- The phone captures receipts, bills, quick expenses and odometer readings, and keeps them in an encrypted queue while offline.
- The desktop imports each item once and confirms, so the phone can delete its copy.
- A lost phone can be removed. Several phones per desktop are supported.
- Data never passes through a third party.

The development plan first proposed Ktor with TLS and mDNS discovery.

## Decision

**Transport:**
- A small HTTP listener inside the desktop app (JDK `HttpServer`). It runs only while a household is open, on port 47311 (or the next free one), and stops when the household locks.
- Requests are handled one at a time.
- No TLS and no discovery: the phone gets the computer's address from the pairing QR code.
- If the address changes, the user pairs again. Discovery can be added later without changing the protocol.

**Pairing (SYNC-03):**
- The desktop has a long-term X25519 key pair per household, kept in the encrypted `core.db`.
- The QR code holds `hfmpair:v1:<base64url JSON>`: the desktop id, name, public key, host, port and a 12-hex-digit one-time code. It is valid for 10 minutes and works once.
- Because it is a link, the phone's camera app can open the companion app too; it can also be pasted.
- The phone makes its own key pair and sends its public key with a proof: HMAC-SHA256 under the one-time code over both public keys.
- Both sides derive the pair key: X25519, then HKDF with both public keys.
- The desktop answers with a proof under the pair key, so the phone knows it reached the right computer.

**Bundles:**
- Every request and answer is JSON, gzipped and sealed with AES-256-GCM under the pair key.
- The desktop id, device id and direction are bound in as authenticated data, so an answer cannot be replayed as a request and one phone's bundle cannot pass as another's.

**Items (SYNC-02, SYNC-04):**
- Each item has an id made on the phone. The desktop records every id received, so an item sent twice is acknowledged but imported once.
- The phone deletes an item's content only after the desktop lists it as imported.
- Received items go to the review inbox, never straight into the books (SYNC-05):
  - pages become a JPEG, or one PDF when there are several (CAP-02);
  - a quick expense without a photo becomes a short note;
  - an odometer reading is applied to the vehicle directly.
- The phone's OCR text is used. Without it, the desktop reads the file itself.
- Typed fields win over what was read.

**Reference data (SYNC-06, RPT-06, BILL-04):**
- Each answer can carry accounts, categories, payees, people and pets, vehicles, bills due in the next 60 days, and this month's budgets.
- It is sent only when its hash changed.

**Users (HH-02, HH-11, HH-12):**
- A phone belongs to the user who paired it. Its pair key is stored sealed for that user's public key, so other household users cannot open its captures.
- While another user is signed in, the desktop answers 409. The phone keeps its items and says why.
- A member's captures go to their private group and their own review inbox; an administrator's go to the shared group.

**Removal (SYNC-08):** a removed phone gets 403 and unpairs itself. It never held any household key, so no key rotation is needed.

**Phone storage (SYNC-01, SEC-03):**
- Settings, reference data and the queue are files encrypted with an AES-256-GCM key held in the Android Keystore.
- The app is excluded from cloud backups.
- It is locked by a PIN (PBKDF2, 120,000 rounds), with optional fingerprint or face unlock.

## Consequences

- Plain HTTP needs `cleartextTrafficPermitted` on Android. This is acceptable because bodies are already end-to-end encrypted; the transport is never trusted.
- Windows asks once whether the app may accept connections on private networks.
- The phone must be on the same network as the computer. Cloud-folder and email transfer (§3.1, §3.2) can reuse the same sealed bundles later (Phase 5).
- Tested: `SyncServiceTest` and `MultiUserTest` in `core/books`, and `SyncServerTest` (real HTTP on localhost) in `app/desktop`. On the Android emulator: pairing, reference data, and a quick expense reaching the desktop inbox.
