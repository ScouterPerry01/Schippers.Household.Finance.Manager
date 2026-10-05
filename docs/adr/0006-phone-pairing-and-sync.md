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
- Each answer can carry accounts, categories, payees, people and pets, vehicles and other assets with a meter (each with its unit, km or hours), bills due in the next 60 days, this month's budgets, and maintenance due this month or overdue (MNT-05). A meter reading from the phone names a vehicle or a metered asset.
- It is sent only when its hash changed.

**Users (HH-02, HH-11, HH-12):**
- A phone belongs to the user who paired it. Its pair key is stored sealed for that user's public key, so other household users cannot open its captures.
- While another user is signed in, the desktop answers 409. The phone keeps its items and says why.
- A member's captures go to their private group and their own review inbox; an administrator's go to the shared group.

**Transfer files, away from home (section 3.2, Phase 5c):**
- The same sealed request travels as a file: a short header (`ROOSTSYNC1`, then the desktop id, device id, direction and time as JSON) and the sealed body. The header only says whose it is; the same ids are bound into the encryption, so changing it makes the body fail to open.
- Names say which way a file goes: `to-desktop-<device>-<time>.roostsync` and `to-phone-<device>-<time>.roostsync`.
- Cloud folder: the phone writes requests into a folder of the user's own cloud storage chosen with Android's folder picker; the computer sees the same folder through the provider's own app. RANN registers no app with Google, Microsoft or Dropbox and holds no account token. Every 20 seconds the desktop imports new requests, removes them, and leaves a reply (acknowledgements and reference data, as over Wi-Fi) for the phone to collect and delete. Requests for another household or for a user not signed in are left alone; unreadable ones are moved to `Not imported`; uncollected replies are removed after 60 days.
- Email and USB: the phone shares the same file. On the desktop it is imported by hand, dropped on the Documents screen or put in the documents folder; there is no reply, so the phone shows the items as sent until a later transfer confirms them. The desktop never signs in to a mailbox.
- Items are still imported once, whatever the route (SYNC-02).

**Removal (SYNC-08):** a removed phone gets 403 and unpairs itself. It never held any household key, so no key rotation is needed.

**Phone storage (SYNC-01, SEC-03):**
- Settings, reference data and the queue are files encrypted with an AES-256-GCM key held in the Android Keystore.
- The app is excluded from cloud backups.
- It is locked by a PIN (PBKDF2, 120,000 rounds), with optional fingerprint or face unlock.

## Consequences

- Plain HTTP needs `cleartextTrafficPermitted` on Android. This is acceptable because bodies are already end-to-end encrypted; the transport is never trusted.
- Windows asks once whether the app may accept connections on private networks.
- Wi-Fi transfer needs the phone on the same network as the computer; away from home, transfer files go through the user's own cloud folder, email or USB.
- Tested: `SyncServiceTest` and `MultiUserTest` in `core/books`, and `SyncServerTest` (real HTTP on localhost, and transfer files) in `app/desktop`. On the Android emulator: pairing, reference data, a quick expense reaching the desktop inbox, and (Phase 4c) the maintenance list and a boat's hour reading reaching the desktop.
