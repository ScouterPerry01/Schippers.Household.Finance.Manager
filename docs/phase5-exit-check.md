# Phase 5 exit check: requirements

Date: 2026-10-05. Checked against the code on branch `worktree-agent-af23be26b6aef4f0e` (from main at a3ac905): for each requirement, the screen, the storage, access and private groups (HH-11), the English and French texts, and the manual chapter in both languages. The check covers every Phase 5 requirement, every requirement in `requirements-additions.md`, and every SRS Should item not explicitly deferred.

- **Met**: built, with texts and manual.
- **Fixed**: a gap found during this check and fixed on this branch, with tests where the change is in the books, and the manual updated in English and French.
- **Gap**: left open, with the reason.

Across the board: English and French message files have the same keys (MessageKeysTest, MessagesTest); the manual and help chapters have the same sections in both languages (ManualTest, HelpTopicsTest); Android `values` and `values-fr` match. Private groups are enforced in the books layer (`Books.groups()` only returns groups the user may open), so screens and reports cannot show another user's private records.

Full build: 646 tests, 0 failures, 1 skipped (632 before this check).

## Phase 5a: AI reading and smarter reading

- AI-01 (opt-in, ask each time): Met. Fixed: Read with AI is no longer offered to a user who could not save the reading (view only on the document's group) but would still be billed.
- AI-02 (user's own key in the system keyring): Met. The keyring's own error texts are in English (see Hard-coded English).
- AI-03 (versioned schemas and prompts, extendable): Met for the eight built-in types. Gap: a custom type with a new id is accepted and sent, but its answer is shown as an empty "Other" document (no generic view of the fields). Medium: a key and value view for custom types.
- AI-04 (see what is sent, crop or blur): Met (Hide an area, Keep only). Fixed: a page can be left out (Leave out this page), and a document longer than 20 pages now says only the first 20 can be sent instead of dropping the rest silently; the failure text and the manual already told the user to leave pages out.
- AI-05 (answer checked against the schema and the sums): Met.
- AI-06 (usage log): Met. The provider is stored but not shown as a column; requests retried by the SDK after a timeout are billed but not logged.
- AI-07 (other providers): Met as architecture only (`AiProvider` interface); Claude is the only provider. Accepted for a Could item.
- OCR-03 (line items to split a receipt): Met through AI reading (Split by items). Gap: on-device reading does not extract line items. Large; owner to confirm that AI-only is acceptable.
- OCR-06 (optional cloud AI): Met.
- OCR-07 (learns from corrections): Met for store name, kind and category. Fixed: Learned stores… on the Documents screen lists what was learned and forgets a store (the forget query existed but nothing called it). Gap: no learning of a store's layout (which field sits where). Large.
- OCR-08 (document type detection): Met, desktop and phone.
- OCR-09 (statements parsed into transactions): Met through AI reading for bank and card statements. Gap: no local parsing of text PDFs, investment statements not parsed into transactions. Medium to large.

## Phase 5b: tax package

- CAT-05 (tax flags): Met.
- TX-04 (sales tax per transaction): Met.
- TX-05 (refunds linked to the purchase): Met.
- SAL-02 (pay stubs): Met.
- OTH-01 (donations and receipts): Met.
- TAX-01 (slip checklist): Met. Fixed: a slip added by hand went to the first shared group with the scan attached later; Add a slip now has Store in, starting on the user's private group.
- TAX-02 (year-end package): Met (PDF, Excel, CSV and a folder for the accountant; no tax-software format). Fixed: Schedule 9 and Schedule 11 showed in English in the Line column; French now reads Annexe.
- TAX-03 (instalment reminders): Met.
- Tax summary report row (section 12): Met by substitution: the year-end package on the Taxes screen; there is no entry under Reports.

## Phase 5c: transfer away from home

- Section 3.2, personal cloud folder: Met (the desktop watches the folder; each provider through its own sync app, as decided).
- Section 3.1, email or USB with an encrypted file: Met (Share as a file…, Import a transfer file…). Gap: the email subject has no tag or id and is not addressed to the user; items imported by hand get no confirmation back to the phone until the next Wi-Fi or folder transfer. Small; the tag only matters if a mailbox reader is ever added.

## Phase 5d: budgets

- BUD-04 (phone alerts at 80 % and 100 %): Met. Fixed: amounts in the notification used a decimal point in French, and alerts were marked as shown even when Android refused notifications, so they were lost.
- BUD-05 (budget from the last 12 months): Met in function. Gap (privacy and access, listed with the security observations): budgets live in the household-wide database, so suggestions built from private spending are visible to other users, and setting or removing a budget has no role check. Medium.

## Phase 5e: reports

- RPT-03 (report builder): Met. Fixed: a saved or scheduled custom report kept the currency left from another report (the custom report has no currency choice), so a scheduled PDF could cover only one currency's accounts. Fixed: no category filter; Category (with subcategories) added and saved. Fixed: deleting a saved report happened at once; it now asks first.
- RPT-05 (scheduled reports): Met. PDFs are written unencrypted (see security observations).
- RPT-07 (by person, household or chosen accounts): Met. Year in review, foreign exchange, investment income, budget and reconciliation reports cannot be narrowed to chosen accounts (documented).
- Currency exposure: Met for foreign cash in non-registered accounts. Gap: foreign securities, registered accounts and foreign debts are not counted, so exposure is understated. Medium (Could).
- Year in review: Met (figures and a table). Gap: no charts, and only one previous year for comparison. Small to medium (Could).

## Phase 5f: estate and health summary

- EST-01 (emergency summary): Met. Fixed: kept documents came only from the newest 1,000 documents, so older kept papers were missing. Fixed: health and dental plans were not listed; a section is added.
- EST-02 (will, power of attorney, mandate, safe deposit box, keys): Met.
- EST-03 (print or encrypted PDF): Met. Fixed: the printed and saved PDF now says Nothing recorded. under an empty section, as the screen and the manual say.
- HLT-09 (printable health summary): Fixed: there was only Save as PDF; Print health summary added. The PDF reused the emergency summary's line "it lists the household's accounts"; it now has its own line.

## Phase 5g: usability

- OTH-04 (onboarding): Met as the Getting started guide (owner's decision: a checklist, not a modal wizard).
- NFR-09 (account, receipt and first reconciliation without the manual): Fixed: the guide had no receipt step and ticked the statement step on import; it now has A first receipt and ticks A first statement, reconciled only once a statement is reconciled. The demo keeps the guide hidden, as before.
- NFR-12 (help, tooltips, searchable guide in both languages): Met for help (F1) and the searchable manual. Fixed in part: the small ✕ buttons now show a tooltip. Gap: other controls have no tooltips. Medium.
- NFR-08 (WCAG 2.1 AA): Met for keyboard, text size, dark mode and tables under charts. Fixed: twelve ✕ buttons were read as "multiplication sign" by screen readers; they now say Remove or Delete. Gap: the outline colour used for hint text (44 places) is at or just under 4.5:1 on the light theme; the top menu button's description hides its count and has no open or closed state. Small.
- NAV-01, NAV-02 (grouped menu, left or top, remembered per computer): Met.
- NAV-03 (same groups and counts, keyboard): Met, with the screen reader note under NFR-08.

## Phase 5h: Could items

- CAP-06 (e-receipts from saved .eml files): Met. Fixed: logos and tracking pixels shown inside an HTML receipt were imported as receipts and kept the email's own text from being saved; small inline pictures are now left out, larger ones (a photo mailed from a phone) kept. Gap: the email PDF uses a standard font, so non-Latin characters are lost; the watched folder moves an unreadable file to Imported without saying so. Small.
- CAP-08 (voice notes): Met. Fixed: at the 60-second limit the recording stopped and the minute was lost; it is now kept as if Stop had been tapped.
- CC-03 (card rewards): Met.
- LN-07 (family loans): Met. The rate shows a decimal point in French.
- SAL-04 (invoices): Met.
- SAL-05 (rental properties): Met. Fixed: French expense lines had no space before the colon.
- HH-03 (allowances), HH-04 (shared expenses): Met. The settlement text is saved in the language in use at the time.
- MNT-07 (fuel log), MNT-08 (contractors), MNT-09 (home projects): Met.
- OTH-02 (trip log), MED-11 (medical travel), MED-13 (which spouse claims): Met.
- Trips, contractors, projects, invoices, rentals and family money are kept in the first shared group, with no Store in choice. Small, optional.

## Contacts

- CON-01, CON-02, CON-03, CON-04: Met. Fixed (CON-02): editing the dotted form of a number saved the dots as the new number; it is now refused with a request to retype the number.
- CON-05 (filters and global search): Met in code. Fixed: the manual's search section did not mention contacts.
- CON-06 (gather, merge, duplicates): Met. Fixed: vehicles (insurer, warranty providers with their phone, garages from the service log) and other assets' warranty providers were not gathered. Gap: a medical plan's insurer cannot be gathered, since a contact cannot be linked to a medical plan.
- CON-07 (phone contacts): Met; account and client numbers are never sent.

## Requirements added after the SRS

- FX-07, FX-08: Met.
- CAL-01: Met.
- CAL-02 (same patterns as bills): Fixed: events lacked twice a month, the month's last day or last business day, and moving off weekends and holidays; the stored recurrence already held them, only the form lacked them.
- CAL-03 (reminders, desktop and phone): Met on the desktop. Gap: the phone receives no events or refills, so it cannot remind of them. Medium to large (sync data, notifications, texts, manual).
- CAL-04, CAL-05, CAL-06: Met.
- HLT-01: Met.
- HLT-02 (refills): Fixed: a capture-only user could not record a refill (it saved the whole medication, which needs edit rights), although the manual says they can.
- HLT-03 to HLT-08: Met.
- HLT-09: see Phase 5f.
- GOAL-01 to GOAL-03, GOAL-06: Met.
- GOAL-04 (progress): Fixed: the amount still needed was computed but not shown.
- GOAL-05 (spending linked to the purchase): Fixed: the Use form now offers the account's recent payments to link.
- PET-01 (pet record with photo): Fixed: no photo could be attached; Photo and papers added.
- PET-02 to PET-04: Met.
- PET-05 (cost per pet per year by category): Fixed: categories were only over five years; Period now chooses one year. Grooming is part of "Pet supplies and grooming" rather than its own category.
- VEH-01, VEH-02 (registration wording follows the province), VEH-04 to VEH-09, VEH-11: Met.
- VEH-03 (warranties and reminders): Met. Fixed: a warranty with no provider showed its kind as a raw English name in reminders, the calendar and the assets report.
- VEH-10 (cost of ownership): Met for categories and cost per km. Gap: no year by category view and no share of policy premiums for vehicles (assets have it, MNT-13). Small to medium.
- MNT-10 to MNT-12, MNT-14: Met.
- MNT-13: Met for assets; not applied to vehicles (see VEH-10).
- CAT-06: Met.
- PROV-01 to PROV-07: Met. Not modelled: Newfoundland's own holidays and Yukon's Heritage Day.
- UPD-01, UPD-02, UPD-04: Met.
- UPD-03 (signed list, size and hash checked): Met. A partial download is left behind in one error case (see security observations).

## SRS Should items re-checked

- ARC-04 (importers as plug-ins): Gap in part: the importer interfaces exist but the lists of importers are fixed in code, and the crypto exchange and QIF readers are outside the interface. Small.
- NFR-13 (modular design): Gap in part: account types and report kinds are closed lists. Accepted as is for 1.0.
- SYNC-06, SYNC-07: Met.
- SYNC-09 (status on both devices): Met. Fixed: the reason under a refused item was the desktop's English exception text; it now comes in the phone's language. The desktop lists counts per phone but not the failed items.
- CAP-04: Met.
- CAP-05 (share to the phone app): Met for one picture or PDF and several pictures. Gap: several PDFs, mixed shares and shared email text are not accepted. Small.
- MAN-03, MAN-04: Met.
- MAN-05 (templates): Fixed (owner's decision to build them): named transaction templates per account group (payee, category or split lines, memo, person, tags, optional amount, optional account), picked above a register's entry form by typing their name, made from a transaction with Save as template, and edited or deleted (asks first) under Templates…; kept in the group's ledger, so private groups stay private (ledger schema version 30).
- ACC-06 (low-balance, over-limit and unusual-activity alerts per account): Fixed: Alerts… on a bank or card register sets, per account and off by default, a low-balance threshold, over the credit limit and a share of the limit used, and unusual activity (a transaction more than so many times the account's median of the year before, or a first transaction with a payee above an amount, over the last 7 days, transfers left out). Alerts show in the reminders and the system notification, on the Dashboard under Needs your attention and above the register; unusual ones can be dismissed. Settings are kept in the account's group ledger (HH-11; ledger schema version 30).
- CC-04, CC-05: Met.
- LN-03, LN-05, LN-06: Met.
- FX-05, FX-06: Met.
- CR-02 (watch-only addresses): Met for Bitcoin, updated when the user clicks Sync now (not on a schedule).
- CR-03, CR-06: Met.
- INV-05 (brokerage statements in CSV, OFX and PDF): Met for CSV and OFX. Gap: PDF trade confirmations and investment statements are not read into transactions (AI reading gives only a summary). Medium.
- INV-06, INV-07, INV-08, INV-11: Met.
- PM-03, PM-04: Met.
- TX-07 (bulk edit): Fixed: never built since Phase 1. Choose transactions… in a register now categorizes, tags or moves several transactions at once (moves within the same group and currency, never off a bank statement), with each change in the history.
- TX-08: Met.
- EXP-02 (export of chosen transactions as QIF, OFX or CSV): Fixed: never built. The same choice exports CSV, QIF or OFX; the three files are read back by the app's own importers in tests, each export is in the activity log, and the window says the file is not encrypted.
- CAT-03 (learning suggestions shown for confirmation): Met in the register and for documents. Fixed (owner's decision): on statement import the payee's default or last category is still filled in, but the transaction is marked "(to review)"; Categories to review in the register keeps each one or all in one click, or opens it in the form, and changing the category clears the mark. Categories set by rules (CAT-02) are not marked (ledger schema version 30).
- SAL-03 (sales of personal items with buyer, price and the asset): Fixed for assets: the sale deposit can be linked (its payee is the buyer) and the gain or loss against the purchase price is shown; vehicles show the gain or loss too. Fixed: a vehicle sale is now linked to its deposit the same way (Find the sale, the buyer from its payee, the gain or loss in the form and the overview; ledger schema version 30).
- AST-02, AST-03, AST-04: Met.
- AST-05 (disposal linked to the sale): Fixed with SAL-03, for assets and vehicles.
- REC-03 (one-to-many and many-to-one matching): Fixed: on import, a line equal to two to four recorded transactions together, or two to four lines equal to one transaction (to the cent, within the date tolerance), is proposed as a group and never linked without asking (Same money / Not the same); Match several… in the reconciliation groups lines and transactions by hand when the totals are equal; Undo match undoes a whole group; groups are kept by the reconciliation undo, recognized on re-import, and listed in the reconciliation report (new Report button under Statements). Foreign currency purchases are not grouped (ledger schema version 30).
- REC-04, REC-07, REC-08, REC-09: Met.
- BILL-07, BILL-08, BILL-09: Met.
- BILL-10 (subscriptions): Met; the renewal date cannot be entered, the next due date stands in for it.
- MED-02, MED-04, MED-05: Met.
- MED-08 (EOB capture and matching): Fixed: the matching existed in the books but no screen used it; a document of the kind Explanation of benefits now lists the waiting claims it may answer and attaches to one in a click.
- MED-09 (claim deadline reminders, including by plan year end): Fixed: a plan's deadline can now be counted from the end of the plan year (Deadline counted from: The end of the plan year, with 0 or more days after it), and reminders and the calendar use it (ledger schema version 30).
- MED-14 (federal and Quebec totals, dependants): Met for federal and dependants. Gap: no separate Quebec total; adult dependants are labelled as claimed apart in Quebec too. Needs checking against Revenu Québec's rules before changing (another agent owns the tax rules).
- MED-15: Met.
- WAR-03 (claim log): Met for assets. Fixed: vehicle warranties now have a claim log too (date, problem, outcome, cost covered, cost paid), and saving a vehicle warranty again keeps its claims (ledger schema version 30).
- INS-02, INS-05: Met.
- INS-04 (insurance claims with documents): Fixed: documents could not be attached to a claim; Claim documents added.
- MNT-02: Met.
- MNT-03 (readings from the phone, photo or typed): Met for typed readings; no photo of the odometer.
- MNT-06: Met for assets; vehicles as under VEH-10.
- Section 12 Should reports: investment income and gains, assets and warranties: Met. Cash flow forecast: Gap in part (lists on the Bills Forecast tab, no line chart, no low-balance threshold, not under Reports). Debt summary: Gap in part (no line per debt to payoff, no interest paid per year). Maintenance: Met with tables (no timeline chart).
- RPT-06: Met.
- BUD-02, BUD-03: Met as GOAL-01 to GOAL-06.
- HH-02 (administrator approves imports): Gap: members file their own captures; the administrator sees only those that went to shared groups. Partly in tension with HH-12; owner's decision.
- HH-08 (permissions per group or account): Met per group; not per account (an account in its own group is the workaround).
- HH-11, HH-12, HH-13: Met.
- BAK-04: Met.
- NFR-03 (capture to saved under 5 seconds): Not measured. The design saves before reading and sending; needs a timed check on a real phone.
- DIST-05: Met (UPD-01 to UPD-04).
- DIST-06: Met; the README does not link to the manual files.
- DIST-07: Met; the store listings (2026-10-04) do not yet mention the Phase 5 features. Refresh before release.
- Windows Hello (SEC-02 option): after 1.0, as decided.

## Hard-coded English shown to users

Left as they are (small, each needs a text key or a mapping): the keyring errors (AI key), the reasons a custom AI type is rejected and the details of an invalid AI answer, the AI page-count error, the update errors in About (Updater, left to the security review), the network error inside "Rates could not be updated" (crypto sync), the investment import warnings, the transfer folder's "Not imported" folder name, "RESP grant" in grant transaction labels, the "HH:MM" hint of the event time field.

## Security observations (for the security review; not changed here)

- Contacts: links are stored in the contact's group. A shared contact linked to a record in a private group stores that record's id in the shared file, and the Linked to filter does not check that the record can be seen, so other users can learn that a contact is linked to some private record. About five lines to filter by visible targets.
- Budgets: kept in the household-wide database, readable by every user; budget suggestions built from private spending are therefore visible to others; setting or removing a budget has no role check (a viewer can change them); the Privacy and data chapter wrongly says budgets are kept in each group's encrypted file.
- Updates (UPD-03): on the desktop, a download larger than announced, or an I/O error mid-download, leaves the `.part` file in Downloads; the phone deletes it.
- Files written unencrypted with no warning: scheduled report PDFs (to any folder, possibly a synced one) and the accountant folder of the tax package (slips and receipts, no activity log entry). The new transaction export warns and is logged.
- Printing the emergency or health summary writes an unencrypted temporary PDF that stays until the app exits (documented).
- Wrong passwords when revealing an account or contact number are not logged or limited.
- Transfer folder: files are read whole with no size limit, and a file that keeps failing is retried every 20 seconds.
- The tray notifications name medications, people and appointments, which shows on a shared screen.
- The phone receives contacts' notes and What for lines; gathering puts institution and transit numbers into notes.
- Scheduled reports are made on a background thread using the same books as the screen; worth confirming the database driver is safe for that.
- The watch-only Bitcoin lookup tells mempool.space the user's IP address; the manual says nothing else is sent.
