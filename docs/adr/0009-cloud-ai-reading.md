# ADR 0009: Cloud AI reading

Status: Accepted (Phase 5a, 2026-10-04). Foundations built in 5a.1; screens in 5a.2; line items, learning, document types and statements in 5a.3.

## Context

- Section 4.5 and AI-01 to AI-07: optional reading of hard documents by a cloud AI, off by default (AI-01, Must), with each user's own API key kept in the operating system's secret store (AI-02, Must), one versioned JSON Schema and instruction per document type (AI-03), a preview where the user can crop or blur before sending (AI-04), answers checked against the schema and their arithmetic (AI-05, Must), a usage log (AI-06) and room for other providers (AI-07).
- OCR-03 and OCR-06 to OCR-09 build on it.
- ARC-01, PRV-01 to PRV-03, HH-11: nothing leaves the computer unless the user asks, and private groups stay private. The privacy policy already describes this feature (AI reading with the user's own account).
- Owner's decisions (2026-10-04): Claude only at 1.0, with the provider layer ready for others or a local model; JNA for the secret store.

## Decision

**Module.** `core/ai`, desktop only. The phone keeps reading on the device (ML Kit).

**Provider.** `AiProvider` is one call: page images, instructions and schema in, JSON text and token counts out (AI-07). `ClaudeProvider` uses Anthropic's official Java SDK (`com.anthropic:anthropic-java`), one Messages request per reading:
- the pages as JPEG images, the instructions as the system prompt, the schema as structured output (`output_config.format`, `json_schema`);
- Claude Opus 5.5 by default, with Claude Sonnet 5.5 and Claude Haiku 4.5 offered in settings; effort `medium` where the model takes it;
- with Opus 5.5, the server-side fallback (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`): a refused request may be answered by another model the service chooses, and the log records the model that answered;
- the stop reason is checked before anything is read: a refusal, or an answer cut short, is reported as such;
- errors are told apart for the screen: wrong or missing key, limit or no credit left, network, refusal, too long, other.

**Document types (AI-03).** Eight are shipped in `core/ai/src/main/resources/hfm/ai`: receipt, bill, invoice, card statement, bank statement, investment statement, pay stub and explanation of benefits. Each has a schema with a version (`$id`, such as `hfm/receipt/v1`) and a one-line instruction after a shared one. Amounts are JSON numbers, read exactly as written (never through floating point). An advanced user can add `<id>.json` and `<id>.txt` files in a folder; a file with a shipped id replaces it. A schema the API cannot take (an open object, a `$ref`) is listed with its reason and not used. Limits the API does not accept (`minimum`, `maxLength`, `pattern` and similar) are removed before sending and checked locally, as Anthropic's Python and TypeScript SDKs do.

**Checks (AI-05).** Every answer is checked on the computer against the full schema, whatever the API promises, and its sums are checked: line items against the subtotal, subtotal and taxes against the total, a statement's opening balance and transactions against its closing balance, gross pay less deductions against net pay, an explanation of benefits' lines against its total, holdings against an account's value. A failed answer is asked for once more, saying what was wrong; if the second one fails too, the user enters the fields by hand (section 4.5, step 4). Fields read by AI are marked as such in the review inbox (`FieldSource.CLOUD_AI`), with more confidence when their sums were checked.

**What is sent (AI-04).** Only page images the user has seen: cropped, with chosen areas blurred into flat blocks before the picture is encoded, so what is hidden never leaves the computer, and at most 2,000 pixels on the long side. No text, account data or household data is sent beyond what is visible on the pages.

**Key (AI-02).** Kept per household and user under `RANN's Roost/ai/<provider>/<household>/<user>` in Windows Credential Manager or, on Linux, the desktop keyring through the Secret Service (libsecret), both through JNA. Where neither works (a Linux desktop with no keyring running), the key lasts until the app closes. The key is never stored in the household files or backups.

**Storage.** Ledger schema version 18 (in each group's ledger, so private groups stay private):
- `document_ai`: the latest checked answer of a document, with its type, schema version and model, for the screens that use its lines (5a.3);
- `ai_usage` (AI-06): every request, with its tokens and estimated cost in US dollars from the list price, including requests whose answer was refused; kept when its document is deleted. Each user sees only their own requests, since they pay for them.

Each user's settings (on or off, ask before each document, model) are household settings keyed by user; they hold no secret. The shared audit log records that a document was read by AI and with which model, never what it holds.

## Consequences

- Reading a document costs the user a few cents on their own Anthropic account; the estimate in the log comes from list prices and may differ from the bill.
- The service may change: models are chosen by id, and the SDK's version is pinned in the version catalog.
- Not checked in this project's tests: a real request to the service, which needs a key. `ClaudeProviderTest` checks the exact request the SDK sends and how answers and errors come back, against a local server; the owner makes the first real reading with their own key.
- Tested: `AiReadingTest` (shipped types, schema checks, removed limits, user folder, sums, review fields, retry and give-up, usage, blurring, the key store with a real Windows Credential Manager entry), `ClaudeProviderTest`, `AiServiceTest` (settings per user, readings kept, usage per user and group, nothing private in the audit log), and the upgrade from ledger version 17.

## Screens (5a.2, 2026-10-04)

- **AI reading** (its own section, between Phones and Users): turning it on, asking before each document, the model with its list price, the key (saved, checked with a request that costs nothing, removed), the document types with the folder for added ones (`%APPDATA%\RANN's Roost\ai-types` or `~/.config/ranns-roost/ai-types`) and files that could not be used, and the usage log by month, year or everything with its total.
- **Review dialog:** once AI reading is on, "Read with AI", with a note when the fields read on this computer are uncertain (section 4.5, step 1), and after a reading which model read it and when. Fields read by AI are marked "read by AI".
- **Before sending (AI-04):** every page (an image, or each PDF page at 150 DPI, at most 20), the document type, tools to hide areas and to keep only part of a page, how many pages go to Anthropic for which model, and an estimated cost. Nothing is sent before Send. With "ask before each document" turned off, the pages are sent as they are.
- **Errors** are told apart: wrong key, limit or no credit, no connection, refusal, too long, answer that could not be checked, other.
- **Demo:** keys are kept in memory only, so trying the demo leaves nothing in the system's store; `-PaiUrl=` points it at `tools/dev/fake_anthropic.py`, a local stand-in, to try the screens without a key or cost.
- **Packaged self-check:** the SDK's client builds and the bundled Java runtime can make a TLS 1.3 connection with elliptic-curve keys; nothing is sent.
- **Checked** in the demo in English and French against the stand-in: the key check, the preview with a hidden area (the stand-in's copy of the picture showed the card number as flat blocks), the reading reaching the review fields marked as read by AI, and the usage log.

## Statements read into reconciliation (OCR-09, 5a.3, 2026-10-04)

A bank or credit card statement read by AI can go straight into an account as a statement (format `AI`): the review dialog offers the accounts of the right kind (bank accounts, or cards and lines of credit), the one whose number ends like the statement's chosen first, and "Reconcile with this statement". The import is the one downloaded statements use: lines already in the books are matched, close ones proposed, the rest added, and reconciliation opens with the result. Card statements print charges and the balance owed as positive; they become negative in the books. The document's file is the statement's file, so the same document cannot be imported twice. Statements, pay stubs and explanations of benefits no longer offer to be attached to a single transaction. Tested by `AiServiceTest` (matching, signs, no second import) and in the demo against the stand-in.
