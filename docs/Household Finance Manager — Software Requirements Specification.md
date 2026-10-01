# Household Finance Manager — Software Requirements Specification

Oct 1, 2026 · @Perry

## 1. Introduction

Everything requested is technically feasible with current, proven technology. The one area with real external limits is automatic bank data feeds, so statement import is treated as the primary path and live feeds as optional.

### 1.1 Purpose

This document specifies a personal, family and household finance application. A full desktop application holds the master data and does the heavy work. An Android companion app captures expenses, receipts and documents on the spot and sends them to the desktop.

### 1.2 Feasibility summary

| Capability | Feasible? | Notes |
| --- | --- | --- |
| Read images of bills, receipts, invoices, statements | Yes | On-device OCR plus optional AI extraction; user confirms every extracted value |
| Manual data entry | Yes | Standard forms on desktop and phone |
| Multiple banks, credit cards, loans, mortgages | Yes | Unlimited accounts per institution |
| Multi-currency | Yes | Per-account currency, daily exchange rates, realized and unrealized FX gain/loss |
| Bitcoin and other crypto | Yes | Public wallet address tracking (read-only) plus manual and CSV import |
| RRSP, RRIF, TFSA, pensions, stocks, precious metals | Yes | Holdings, lots, contributions, minimum withdrawals, price updates |
| Reconciliation with statements | Yes | Import OFX/QFX/CSV/PDF statements and match against recorded transactions |
| Bill scheduling, reminders | Yes | Recurring schedules, desktop and phone notifications |
| Medical, warranty, plan and maintenance tracking | Yes | Dedicated modules linked to transactions and documents |
| Phone-to-computer transfer by email | Yes, but not recommended as the main method | See section 3 for better options; email kept as a fallback |
| Automatic live bank feeds | Partially | Excluded by decision; statement import covers this need |

### 1.3 Scope

In scope: tracking, categorizing, reconciling, planning, reporting and document storage for a household's finances.

Out of scope: moving money (paying bills, transferring funds, trading). The application records and reminds; the user pays through their bank. This avoids the security and regulatory burden of handling payments.

### 1.4 Intended users

- **Household administrator** — the person who manages the books on the desktop.
- **Household members** — spouse, partner or adult children who capture expenses on their own phones.
- **Read-only advisor** (optional) — an accountant or executor who can be given an export or read-only view.

The primary market assumed is Canada (Quebec), so the application must support English and French, CAD as the default currency, and Canadian account types and tax rules.

### 1.5 Priority definitions

Each requirement carries a priority: **Must** (required for first release), **Should** (important, planned for an early release) or **Could** (desirable, later release).

## 2. System architecture

The desktop application is the single master copy of the household's data; each Android device is a lightweight capture and viewing companion that sends its items to the desktop.

&#91;embedded content: system architecture · phone, transfer options, desktop\]

Captures flow left to right into the desktop's review inbox; the desktop sends reference data (accounts, categories, upcoming bills) back to paired phones over the same channel.

### 2.1 What runs where

| Function | Desktop | Android |
| --- | --- | --- |
| Capture receipts, bills, documents | Yes (scan, import, drag-and-drop) | Yes (camera, share-to) |
| Quick manual expense entry | Yes | Yes |
| Odometer and hour-meter readings | Yes | Yes |
| Full editing of all records | Yes | No |
| Reconciliation | Yes | No |
| Reports and charts | Full library | Read-only summaries |
| Bill and maintenance reminders | Yes | Yes |
| Investment and loan management | Yes | View balances only |
| Settings, categories, rules | Yes | No (received from desktop) |

### 2.2 Architecture requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| ARC-01 | The desktop application stores all data locally in one encrypted household data file and works without internet. | Must |
| ARC-02 | The Android app works offline and never holds the full database, only its queue and a small summary cache. | Must |
| ARC-03 | All financial calculations (interest, ACB, FX, amortization) live in one shared library used by both apps, so figures always agree. | Must |
| ARC-04 | Importers (statement formats, exchange CSVs, brokerage files) are plug-ins, so new institutions can be added without changing the core. | Should |
| ARC-05 | Apple platforms (iOS, iPadOS, macOS) are out of scope; Android is the only mobile companion. | Could |

Recommended technology, for the development team to confirm: Kotlin Multiplatform with Compose would let the desktop and Android apps share most of their code, including the calculation library. The database would be SQLite with SQLCipher encryption, a proven combination for local, encrypted personal data. This stack produces Windows installers (MSI/EXE, accepted by the Microsoft Store) and Linux packages (.deb, .rpm, with AppImage or Flatpak via extra tooling) from one code base, and is well supported by Claude Code.

## 3. Mobile-to-desktop data transfer

The email method works and is kept as a fallback, but the recommended primary method is direct encrypted sync over the home Wi-Fi, with a personal cloud folder for when the user is away from home.

### 3.1 Evaluation of the email approach

The idea is sound and can be built as follows. The phone app packages the captured items (data plus photos) into one encrypted attachment. It opens a pre-filled email to the user's own address with a tagged subject such as `[HFM-CAPTURE] 2026-10-01 a1b2c3`. On the desktop, the application signs in to the mailbox (Gmail, Outlook.com, or any IMAP provider), finds messages with that tag, downloads and decrypts the attachment, imports the items, then moves the email to an archive folder.

The data must travel in the encrypted attachment, not in the subject line. A subject line is short, visible and unencrypted.

Drawbacks that make email a poor primary method:

- **Privacy** — financial data and receipt images sit on the email provider's servers indefinitely unless deleted. Encryption is mandatory.
- **Access approval** — reading a Gmail mailbox from a third-party app requires a restricted permission that Google must approve, including a paid security assessment. Microsoft has a similar, lighter process.
- **Reliability** — attachments are limited to about 20–25 MB, messages can be caught by spam filters, and delivery is not instant.
- **User friction** — the user must tap Send for each batch unless the app stores email credentials on the phone, which is a security risk.

### 3.2 Recommended transfer methods

| Method | When used | How it works | Priority |
| --- | --- | --- | --- |
| Direct Wi-Fi sync | Phone and computer on the same home network | Phone is paired once by scanning a QR code on the desktop. Afterwards the phone finds the desktop automatically and sends items over an encrypted connection. No internet or third party involved. | Must |
| Personal cloud folder | Away from home, or desktop is off | Phone writes encrypted bundles to a folder in the user's own Google Drive, OneDrive, Dropbox or Nextcloud. Desktop watches that folder and imports new bundles. The provider sees only unreadable encrypted files. | Should |
| Email to self | Fallback when the above are unavailable | As described in 3.1, with encrypted attachment and tagged subject. | Could |
| Manual file transfer | No network | Export a bundle file and move it by USB cable or SD card. | Could |

Because the application will be published, the cloud folder method should request the narrowest access each provider offers: Google Drive access limited to files the app creates, and a OneDrive app folder. These avoid the restricted-scope security review that full mailbox access requires, which is another reason email stays a fallback only.

### 3.3 Transfer requirements (all methods)

| ID | Requirement | Priority |
| --- | --- | --- |
| SYNC-01 | The phone app works fully offline and queues captured items until a transfer is possible. | Must |
| SYNC-02 | Every captured item has a unique ID so it is never imported twice, even if sent twice. | Must |
| SYNC-03 | Bundles are encrypted end to end with a key created at pairing; only the paired desktop can read them. | Must |
| SYNC-04 | The desktop confirms receipt; the phone then marks items as delivered and can free storage. | Must |
| SYNC-05 | Imported items land in a **Review inbox** on the desktop, not straight into the books, until the user accepts them. | Must |
| SYNC-06 | The desktop can send reference data back to the phone: account list, categories, payees, upcoming bills, recent balances. | Should |
| SYNC-07 | Several phones (household members) can be paired to one desktop; each item records who captured it. | Should |
| SYNC-08 | A paired phone can be revoked from the desktop if lost. | Must |
| SYNC-09 | Transfer status (pending, sent, imported, failed) is visible on both devices. | Should |

## 4. Document capture, OCR and manual entry

Any bill, receipt, invoice or statement can be photographed or imported, read automatically, and turned into a draft transaction the user confirms. Manual entry is always available as an equal alternative.

### 4.1 Capture sources

| ID | Requirement | Priority |
| --- | --- | --- |
| CAP-01 | Phone camera capture with automatic edge detection, crop, de-skew and glare reduction. | Must |
| CAP-02 | Multi-page capture into one document (long receipts, multi-page bills). | Must |
| CAP-03 | Import of existing images and PDFs on phone and desktop (JPG, PNG, HEIC, PDF). | Must |
| CAP-04 | Desktop drag-and-drop and a watched "inbox" folder for scanned or downloaded documents. | Should |
| CAP-05 | Android "Share to" support, so an e-receipt or PDF from another app can be sent straight in. | Should |
| CAP-06 | Import of e-receipts forwarded from email (desktop reads a chosen mailbox folder). | Could |
| CAP-07 | Quick capture: one tap to photo, with amount and category optional so nothing slows the user down at the till. | Must |
| CAP-08 | Voice note attached to a capture ("lunch with client, split with Paul"). | Could |

### 4.2 Extraction

| ID | Requirement | Priority |
| --- | --- | --- |
| OCR-01 | Text recognition runs on the device by default, so documents need not leave the user's hardware. | Must |
| OCR-02 | Extracts: merchant or biller, date, total, taxes (GST/HST, QST/PST), currency, payment method, last 4 card digits, invoice number, due date, account number. | Must |
| OCR-03 | Extracts line items from itemized receipts, so one receipt can be split across categories (groceries vs. household vs. pharmacy). | Should |
| OCR-04 | Recognizes English and French documents. | Must |
| OCR-05 | Each extracted field shows a confidence level; low-confidence fields are highlighted for review. | Must |
| OCR-06 | Optional cloud AI extraction for hard-to-read documents, as specified in 4.5. | Should |
| OCR-07 | Learns from corrections: once the user fixes a merchant's layout or category, future documents from that merchant extract better. | Should |
| OCR-08 | Detects document type (receipt, bill, invoice, bank statement, card statement, investment statement, pay stub, insurance EOB). | Should |
| OCR-09 | Statement images or PDFs are parsed into a list of transactions for reconciliation (section 8). | Should |
| OCR-10 | Duplicate detection warns when the same receipt or bill appears to be captured twice. | Must |

OCR engines: on Android, Google ML Kit text recognition (fast and strong on phone photos, but proprietary and Android-only). ML Kit does not run on Windows or Linux, so the desktop uses an open-source engine: PaddleOCR through ONNX Runtime is recommended for photographed receipts, with Tesseract as a lighter fallback that works best on clean flat scans. An F-Droid build would use the desktop engine on Android.

### 4.3 Manual entry

| ID | Requirement | Priority |
| --- | --- | --- |
| MAN-01 | Forms for every record type: transaction, transfer, split, bill, asset, warranty, maintenance item, investment trade. | Must |
| MAN-02 | Auto-complete for payees, categories and amounts based on history. | Must |
| MAN-03 | Built-in calculator in amount fields. | Should |
| MAN-04 | Keyboard-first entry on desktop for fast bulk input. | Should |
| MAN-05 | Templates for repeated entries (weekly groceries, monthly allowance). | Should |

### 4.4 Document vault

Every captured image or PDF is stored in an encrypted document vault and linked to the records it supports (transaction, bill, warranty, medical claim, asset). Documents are searchable by their recognized text, tags, date and amount. Retention rules can flag documents old enough to discard (for example, receipts older than the 6-year Canada Revenue Agency retention period).

### 4.5 Cloud AI extraction (opt-in)

Each request sends the document image together with a JSON instruction file (a JSON Schema) that defines exactly which fields to return and in what format; the AI replies with JSON that the application validates before the user reviews it.

1. On-device OCR runs first. If confidence is low, or the user taps **Read with AI**, the application offers cloud extraction.
2. The application builds one request: the image or PDF page, a short instruction prompt for that document type, and the JSON Schema for that type (receipt, utility bill, card statement, EOB, etc.).
3. The AI provider returns JSON that follows the schema. The Claude API supports this directly: an image block plus a schema for structured output in a single request.
4. The application validates the JSON against the schema and checks the arithmetic (line items and taxes add up to the total). If validation fails, it retries once, then falls back to manual entry.
5. The result lands in the review inbox with AI-read fields marked, never straight into the books.

A simplified receipt schema:

```json
{
  "$id": "hfm/receipt/v1",
  "type": "object",
  "required": ["merchant", "date", "total", "currency"],
  "properties": {
    "merchant": { "type": "string" },
    "date": { "type": "string", "format": "date" },
    "currency": { "type": "string", "pattern": "^[A-Z]{3}$" },
    "subtotal": { "type": "number" },
    "taxes": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "enum": ["GST", "HST", "QST", "PST", "OTHER"] },
          "amount": { "type": "number" }
        }
      }
    },
    "total": { "type": "number" },
    "payment_method": { "type": "string" },
    "card_last4": { "type": "string", "pattern": "^[0-9]{4}$" },
    "line_items": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "description": { "type": "string" },
          "quantity": { "type": "number" },
          "amount": { "type": "number" }
        }
      }
    }
  }
}
```

| ID | Requirement | Priority |
| --- | --- | --- |
| AI-01 | Off by default; enabled in settings, with an option to confirm before each document is sent. | Must |
| AI-02 | Each user supplies their own AI provider API key, stored in Windows Credential Manager or the Linux Secret Service. The publisher runs no server and pays no usage costs. | Must |
| AI-03 | One versioned JSON Schema file and instruction prompt per document type, shipped with the app and extendable by advanced users. | Should |
| AI-04 | Before sending, the user sees what will be sent and can crop or blur areas such as full account numbers. | Should |
| AI-05 | Responses validated against the schema and arithmetic checks before display. | Must |
| AI-06 | Usage log: date, document, provider and estimated cost. | Could |
| AI-07 | Provider layer allows other cloud providers or a local model to be added later. | Could |

## 5. Accounts

The application tracks an unlimited number of accounts across any number of financial institutions, each in its own currency, and rolls them up into one household net worth.

### 5.1 Institutions and account types

| ID | Requirement | Priority |
| --- | --- | --- |
| ACC-01 | Institution records: name, branch, transit and institution numbers, contacts, website, notes. | Must |
| ACC-02 | Account types: chequing, savings, high-interest savings, GIC/term deposit, credit card, line of credit, HELOC, loan, mortgage, cash, prepaid/gift card, crypto wallet, investment and registered accounts (section 6), asset accounts (property, vehicles). | Must |
| ACC-03 | Each account records owner(s): individual or joint, so reports can be split by person. | Must |
| ACC-04 | Opening balance, opening date, account number (stored masked), currency and status (open, closed, dormant). | Must |
| ACC-05 | Closed accounts are kept with full history and hidden from day-to-day views. | Must |
| ACC-06 | Low-balance, over-limit and unusual-activity alerts per account. | Should |

### 5.2 Credit cards

| ID | Requirement | Priority |
| --- | --- | --- |
| CC-01 | Credit limit, interest rates (purchase, cash advance, promotional), statement day, payment due day, minimum payment rule. | Must |
| CC-02 | Statement cycle tracking: statement balance, minimum due, due date, and paid status. | Must |
| CC-03 | Rewards tracking: points, cash back or miles earned and redeemed. | Could |
| CC-04 | Annual fee and card benefits (travel insurance, extended warranty, purchase protection) linked to purchases made with the card. | Should |
| CC-05 | Supplementary cards linked to the main card, with spending by cardholder. | Should |

### 5.3 Loans and mortgages

| ID | Requirement | Priority |
| --- | --- | --- |
| LN-01 | Principal, interest rate (fixed or variable), compounding (Canadian mortgages compound semi-annually), amortization, term, payment frequency (monthly, bi-weekly, accelerated bi-weekly, weekly). | Must |
| LN-02 | Amortization schedule that splits each payment into principal and interest automatically. | Must |
| LN-03 | Prepayments, lump sums and rate changes, with a recalculated schedule and interest saved. | Should |
| LN-04 | Mortgage term renewal date reminders (e.g. 120 days ahead). | Must |
| LN-05 | Property tax and insurance amounts paid through the mortgage tracked separately. | Should |
| LN-06 | What-if calculator: effect of extra payments, a different rate or a different amortization. | Should |
| LN-07 | Personal loans between family members, with optional interest and a repayment log. | Could |

### 5.4 Multiple currencies

| ID | Requirement | Priority |
| --- | --- | --- |
| FX-01 | Each account has one currency; the household has a base currency (default CAD) for totals and reports. | Must |
| FX-02 | Daily exchange rates downloaded from a public source (e.g. Bank of Canada), with manual override. | Must |
| FX-03 | Foreign-currency transactions store the original amount, the converted amount and the rate actually charged, so card FX fees can be seen. | Must |
| FX-04 | Transfers between accounts in different currencies record both amounts and the effective rate. | Must |
| FX-05 | Unrealized and realized exchange gains and losses in reports. | Should |
| FX-06 | Reports viewable in base currency or in the original currency. | Should |

### 5.5 Bitcoin and other crypto-assets

| ID | Requirement | Priority |
| --- | --- | --- |
| CR-01 | Wallet accounts for Bitcoin and other coins, holding quantities to 8 decimal places (satoshis). | Must |
| CR-02 | Watch-only tracking from public addresses or extended public keys (xpub), with automatic balance and transaction lookup. Private keys and seed phrases are never requested or stored. | Should |
| CR-03 | Import of exchange transaction histories by CSV (e.g. Kraken, Coinbase, Shakepay, Newton). | Should |
| CR-04 | Buys, sells, transfers between own wallets, network fees, and income (interest, mining, staking). | Must |
| CR-05 | Prices from a public market data source, converted to the base currency. | Must |
| CR-06 | Cost basis tracking using adjusted cost base (ACB), as required by the Canada Revenue Agency, with capital gain/loss on disposals. | Should |

### 5.6 Optional live bank feeds

Automatic download from banks is excluded by decision (see 16.1). Canadian banks do not yet offer a universal open banking connection to personal apps; it is being introduced under the federal consumer-driven banking framework. Until then, live feeds require a paid third-party aggregator and sharing bank credentials with it. The application must therefore work fully from imported statement files (OFX, QFX, QBO, CSV) and statement PDFs or images, and has no live-feed component.

## 6. Investments and registered plans

The application tracks every holding the household owns, inside and outside registered plans, with cost, current value, income and the Canadian contribution and withdrawal rules that apply to each plan.

### 6.1 Account types

| Plan or account | Key tracking needs |
| --- | --- |
| RRSP and spousal RRSP | Contribution room, contributions by tax year (including first-60-days contributions), contributor vs. annuitant, Home Buyers' Plan and Lifelong Learning Plan repayments |
| RRIF and spousal RRIF | Required annual minimum withdrawal by age, withdrawals taken, tax withheld |
| LIRA / LIF | Locked-in rules, LIF minimum and maximum annual withdrawals |
| TFSA | Contribution room, contributions, withdrawals and the room they restore the following year |
| FHSA | Contribution room, lifetime limit, qualifying withdrawal |
| RESP | Contributions per beneficiary, government grants (CESG, QESI in Quebec), educational assistance payments |
| Workplace pensions (defined benefit, defined contribution), CPP/QPP, OAS | Annual statements, estimated pension, pension adjustment, payments received |
| Non-registered brokerage | Full cost basis and capital gains tracking |
| Precious metals | Physical bullion and coins, by metal, weight and purity, with storage location |
| Other | Private company shares, real estate held for investment, collectibles |

### 6.2 Holdings and transactions

| ID | Requirement | Priority |
| --- | --- | --- |
| INV-01 | Securities master: stocks, ETFs, mutual funds, bonds, GICs, options, with ticker, exchange, currency and asset class. | Must |
| INV-02 | Transactions: buy, sell, dividend, reinvested dividend (DRIP), interest, return of capital, split, merger, transfer in/out, fees, foreign tax withheld. | Must |
| INV-03 | Adjusted cost base (ACB) per security across all non-registered accounts, as Canadian tax rules require. | Must |
| INV-04 | Daily price updates from a market data source, with manual price entry for anything not quoted. | Must |
| INV-05 | Import of brokerage statements and trade confirmations (CSV, OFX, PDF). | Should |
| INV-06 | Performance: total return, time-weighted and money-weighted (personal) rate of return, by account and household. | Should |
| INV-07 | Asset allocation by class, region, currency and account, against a target allocation, with rebalancing suggestions. | Should |
| INV-08 | Income report: dividends, interest and distributions by year, for tax slips (T3, T5, RL-3, RL-16). | Should |
| INV-09 | Contribution room tracker per person for RRSP, TFSA and FHSA, with over-contribution warnings. | Must |
| INV-10 | RRIF/LIF minimum withdrawal calculation and reminder each year. | Must |
| INV-11 | Beneficiary and successor holder recorded per plan. | Should |

### 6.3 Precious metals

| ID | Requirement | Priority |
| --- | --- | --- |
| PM-01 | Item records: metal (gold, silver, platinum, palladium), form (bar, coin, round), weight in troy ounces or grams, purity, quantity, serial numbers, dealer, purchase price and premium. | Must |
| PM-02 | Valuation from daily spot prices, with an optional premium or discount per item. | Must |
| PM-03 | Storage location (home safe, bank box, vault) and insurance coverage. | Should |
| PM-04 | Photos and certificates of authenticity stored in the document vault. | Should |

## 7. Transactions, categorization, purchases and sales

Every money movement is one transaction record with a payee, category, optional tags and links to its supporting documents; the application suggests categories automatically and the user stays in control.

### 7.1 Transactions

| ID | Requirement | Priority |
| --- | --- | --- |
| TX-01 | Fields: date, account, payee, amount, currency, category, memo, tags, household member, cleared/reconciled status, linked documents. | Must |
| TX-02 | Split transactions across several categories, people or tax treatments. | Must |
| TX-03 | Transfers between own accounts recorded once, appearing in both accounts, never counted as income or spending. | Must |
| TX-04 | Sales tax recorded per transaction (GST/HST, QST/PST), useful for self-employed household members. | Should |
| TX-05 | Refunds and returns linked to the original purchase. | Should |
| TX-06 | Fast search and filter by any field, including text inside linked documents. | Must |
| TX-07 | Bulk edit (re-categorize, tag, move to another account). | Should |
| TX-08 | Full change history (audit trail) per transaction: who changed what, when. | Should |

### 7.2 Categories and tags

| ID | Requirement | Priority |
| --- | --- | --- |
| CAT-01 | Default category tree in English and French (housing, utilities, groceries, transport, health, insurance, children, pets, gifts, travel, etc.), fully editable, two or more levels deep. | Must |
| CAT-02 | Rules: "if payee contains X and amount is Y, set category Z", applied on import and capture. | Must |
| CAT-03 | Learning suggestions from past choices, shown for confirmation rather than applied silently. | Should |
| CAT-04 | Tags across categories for projects and events ("2026 kitchen renovation", "Florida trip", "wedding"). | Must |
| CAT-05 | Tax flags on categories (medical, charitable, child care, moving, employment, business-use) feeding the tax report. | Should |

### 7.3 Sales and income

| ID | Requirement | Priority |
| --- | --- | --- |
| SAL-01 | Income categories: salary, self-employment, rental, pension, government benefits, investment income, gifts. | Must |
| SAL-02 | Pay stub capture splitting gross pay into deductions (tax, CPP/QPP, EI/QPIP, union dues, pension, benefits). | Should |
| SAL-03 | Sales of personal items (vehicle, furniture, online marketplace sales) recorded with buyer, price, and the original asset it came from, so gains or losses are visible. | Should |
| SAL-04 | Simple invoicing for household side income (tutoring, crafts, rentals) with payment status. | Could |
| SAL-05 | Rental property income and expenses grouped per property. | Could |

### 7.4 Payees

Payees have a single clean name with any number of aliases ("AMZN MKTP CA\*2X4" → Amazon), a default category, address, contact details, and links to related bills, warranties and assets.

## 8. Reconciliation

Each bank, credit card, loan and investment statement is imported and matched line by line against the transactions already recorded, so the user confirms that the books agree with the institution to the cent.

The reconciliation process runs in five steps:

1. **Import the statement** — OFX/QFX/QBO or CSV download, or a PDF/image parsed by OCR. The user enters or confirms the statement's opening balance, closing balance and period.
2. **Automatic matching** — statement lines are matched to recorded transactions by amount, date (within a tolerance, e.g. ±5 days) and payee. Phone-captured receipts usually match here.
3. **Review exceptions** — three lists: statement lines with no match (create a transaction or link to one), recorded transactions not on the statement (outstanding or in error), and possible matches needing confirmation.
4. **Balance check** — the difference between the statement closing balance and the cleared balance must reach zero before the reconciliation can be finished.
5. **Lock** — the period is marked reconciled. Later changes to locked transactions require confirmation and are logged.

| ID | Requirement | Priority |
| --- | --- | --- |
| REC-01 | Statement import formats: OFX, QFX, QBO, CSV (with a saved column mapping per institution), PDF and images. | Must |
| REC-02 | Automatic matching with adjustable date and amount tolerances. | Must |
| REC-03 | One-to-many and many-to-one matching (one deposit covering several cheques; one receipt paid in two charges). | Should |
| REC-04 | Foreign currency lines matched on original amount, with FX fee differences posted automatically to a fee category. | Should |
| REC-05 | Running difference shown at all times during reconciliation. | Must |
| REC-06 | Reconciliation report per statement (cleared items, outstanding items, adjustments) saved with the statement image. | Must |
| REC-07 | Undo of a completed reconciliation, with reason logged. | Should |
| REC-08 | Investment account reconciliation: holdings quantity and cash balance per statement. | Should |
| REC-09 | Dashboard showing the last reconciled date of every account, highlighting accounts more than 45 days behind. | Should |
| REC-10 | Duplicate protection when the same statement or overlapping periods are imported twice. | Must |

## 9. Bills and payment scheduling

The application keeps a calendar of every recurring and one-time bill, reminds the user before each is due, and turns a captured bill into a scheduled payment in one step.

| ID | Requirement | Priority |
| --- | --- | --- |
| BILL-01 | Bill records: payee, account number with the payee, amount (fixed, variable or estimated), due date, paying account, payment method (pre-authorized debit, online banking, card, cheque), category. | Must |
| BILL-02 | Recurrence: weekly, bi-weekly, semi-monthly, monthly, quarterly, semi-annual, annual, custom, and "last business day". | Must |
| BILL-03 | A captured bill image auto-creates or updates the matching bill with its actual amount and due date. | Must |
| BILL-04 | Reminders at user-chosen lead times (e.g. 7 days and 1 day before), on both desktop and phone. | Must |
| BILL-05 | Bill calendar view and a list view of upcoming, due today, overdue and paid. | Must |
| BILL-06 | Mark as paid creates the transaction (or links to it when it arrives on a statement). | Must |
| BILL-07 | Pre-authorized debits flagged so the user is warned when the paying account will be short. | Should |
| BILL-08 | Cash flow forecast: projected balance of each account for the next 30/60/90 days from scheduled bills, income and transfers. | Should |
| BILL-09 | Variable bill history (hydro, gas, water) with year-over-year comparison and unusual-amount alerts. | Should |
| BILL-10 | Subscription tracker: all recurring charges listed with annual cost, renewal date and cancellation reminders. | Should |
| BILL-11 | Annual and irregular expenses (property tax, insurance premiums, licence renewals) included in the calendar and forecast. | Must |
| BILL-12 | Export of the bill calendar to the user's own calendar (iCal file). | Could |

The application never pays bills itself (see 1.3). It records, reminds and forecasts; payment happens through the user's bank.

## 10. Medical expenses, health and dental plans

The application tracks each medical and dental expense from the receipt through every insurance claim to the amount the household paid itself, and produces the totals needed for the federal and Quebec medical expense tax credits.

### 10.1 Plans

| ID | Requirement | Priority |
| --- | --- | --- |
| MED-01 | Plan records: insurer, policy and certificate numbers, plan member, covered dependants, primary or secondary plan (coordination of benefits between spouses' plans). | Must |
| MED-02 | Coverage rules per category: percentage covered, deductible, annual maximum, per-visit maximum, frequency limits (e.g. one eye exam every 24 months, dental recall every 9 months). | Should |
| MED-03 | Plan types: employer group health, dental, vision, private health, travel medical, Health Spending Account, the Quebec public prescription drug plan (RAMQ), and the federal Canadian Dental Care Plan. | Must |
| MED-04 | Remaining annual maximums and next-eligible dates shown per person ("Marie: $320 left for massage therapy; next eye exam eligible March 2027"). | Should |
| MED-05 | Plan documents and benefit booklets in the document vault. | Should |

### 10.2 Expenses and claims

| ID | Requirement | Priority |
| --- | --- | --- |
| MED-06 | Medical expense records: patient, provider, date of service, service type, amount, receipt image, prescription where required. | Must |
| MED-07 | Claim lifecycle per expense: not submitted → submitted to primary → paid by primary → submitted to secondary → paid → closed, with amounts at each stage. | Must |
| MED-08 | Explanation of Benefits (EOB) capture and matching to the original expense. | Should |
| MED-09 | Reminders for unsubmitted claims before the insurer's submission deadline (often 12 months or by plan year end). | Should |
| MED-10 | Out-of-pocket amount calculated automatically = cost − all reimbursements. | Must |
| MED-11 | Mileage and travel to obtain medical care (when it qualifies) recorded per trip. | Could |

### 10.3 Tax support

| ID | Requirement | Priority |
| --- | --- | --- |
| MED-12 | Medical expense report for any 12-month period ending in the tax year, since the federal claim allows choosing the best 12-month window. | Must |
| MED-13 | Suggests which spouse should claim and which 12-month period gives the larger credit (indicative only, not tax advice). | Could |
| MED-14 | Separate totals for federal and Quebec claims, and for dependants. | Should |
| MED-15 | Export of the supporting receipts as one PDF bundle for the accountant or a tax review. | Should |

## 11. Assets, warranties, insurance and maintenance

Every significant thing the household owns — home, vehicles, RV, boat, appliances, electronics, tools — is an asset record that carries its purchase, warranties, insurance, maintenance schedule and full service history in one place.

### 11.1 Assets and home inventory

| ID | Requirement | Priority |
| --- | --- | --- |
| AST-01 | Asset records: name, type, make, model, serial number or VIN, purchase date, seller, price, linked purchase transaction, location (room, garage, cottage), owner, photos. | Must |
| AST-02 | Parent/child assets: a house contains its furnace, water heater and roof; an RV contains its generator, fridge and awning. | Should |
| AST-03 | Estimated current value (manual or depreciation rule) feeding net worth. | Should |
| AST-04 | Home inventory report with photos and values, for insurance claims after a loss. | Should |
| AST-05 | Disposal record: sold, given away or discarded, with sale price linked to SAL-03. | Should |

### 11.2 Warranties and service plans

| ID | Requirement | Priority |
| --- | --- | --- |
| WAR-01 | Warranty records: manufacturer warranty, extended warranty, credit card extended warranty (CC-04), service contract; provider, coverage, start and end dates or mileage/hours limits, claim phone number, proof of purchase. | Must |
| WAR-02 | Expiry reminders (e.g. 60 days before) so issues are reported while still covered. | Must |
| WAR-03 | Claim log: date, problem, outcome, cost covered, cost paid. | Should |
| WAR-04 | "Is this still covered?" lookup from the asset screen or by searching the item. | Must |
| WAR-05 | Product registration and recall notices noted against the asset. | Could |

### 11.3 Insurance policies

| ID | Requirement | Priority |
| --- | --- | --- |
| INS-01 | Policies: home, tenant, auto, RV, boat, life, disability, critical illness, long-term care, travel, umbrella liability; insurer, broker, policy number, premium, payment schedule, deductible, coverage limits, renewal date. | Must |
| INS-02 | Assets linked to the policies that cover them; uninsured assets flagged. | Should |
| INS-03 | Renewal reminders and premium history year over year. | Must |
| INS-04 | Insurance claim tracking (non-medical), with documents and payouts. | Should |
| INS-05 | Life insurance beneficiaries and coverage amounts summarized for estate planning. | Should |

### 11.4 Maintenance

| ID | Requirement | Priority |
| --- | --- | --- |
| MNT-01 | Maintenance tasks per asset, scheduled by time (every 6 months), usage (every 8,000 km, every 100 engine hours) or season (spring opening, fall winterization). | Must |
| MNT-02 | Starter templates: home (furnace filter, HVAC service, gutters, smoke/CO detectors, water heater flush, chimney, sump pump, septic), vehicle (oil, tires, brakes, seasonal tire swap, registration), RV (de-winterizing, roof sealant, bearings, propane certification, battery, generator). | Should |
| MNT-03 | Odometer and hour-meter readings captured from the phone (photo or typed), used to forecast when usage-based tasks fall due. | Should |
| MNT-04 | Service log: date, task, provider or do-it-yourself, parts, cost, linked receipt, odometer/hours at service. | Must |
| MNT-05 | Reminders on desktop and phone, with a combined "what's due this month" list across all assets. | Must |
| MNT-06 | Cost of ownership per asset: purchase, maintenance, repairs, insurance, fuel, licensing, per year and per km. | Should |
| MNT-07 | Fuel log for vehicles and RV: litres, price, distance, consumption (L/100 km). | Could |
| MNT-08 | Contractor and service provider directory with ratings and past jobs. | Could |
| MNT-09 | Home improvement project tracking, with costs that add to the home's cost base. | Could |

## 12. Reports and charts

The application ships with a full library of standard household reports, each filterable by date range, account, person, category and tag, and lets the user build and save custom reports.

### 12.1 Standard reports

| Report | What it answers | Typical chart | Priority |
| --- | --- | --- | --- |
| Dashboard | Net worth, cash available, bills due this week, budget status, items needing review | Summary tiles + mini charts | Must |
| Net worth | What the household owns and owes, and how it has changed | Line over time; stacked assets vs. liabilities | Must |
| Income and expense (cash flow) | Money in vs. out by month, quarter or year | Grouped bars per month | Must |
| Spending by category / payee | Where the money goes | Horizontal bars; treemap | Must |
| Budget vs. actual | Over or under budget by category | Bars with target markers | Must |
| Cash flow forecast | Projected balances from scheduled items | Line with low-balance threshold | Should |
| Account register and balances | Every transaction and running balance per account | Table | Must |
| Reconciliation history | When each account was last reconciled, outstanding items | Table | Must |
| Bills and subscriptions | Upcoming, overdue, annual cost of recurring charges | Calendar; table | Must |
| Debt summary | Balances, rates, payoff dates, interest paid per year | Line per debt to payoff | Should |
| Investment portfolio | Holdings, value, gain/loss, allocation, returns | Allocation donut; value line | Must |
| Investment income and capital gains | Dividends, interest and realized gains by tax year | Table | Should |
| Registered plans | Contribution room, contributions, RRIF minimums per person | Table | Must |
| Medical expenses | Costs, reimbursements, out-of-pocket, tax-credit totals | Stacked bars by person | Must |
| Tax summary | All tax-flagged categories for the year (medical, donations, child care, investment income) | Table | Should |
| Assets and warranties | Inventory, values, active and expiring warranties | Table | Should |
| Maintenance | Upcoming tasks, history and cost of ownership per asset | Timeline; bars | Should |
| Currency exposure | Holdings by currency and FX gains/losses | Bars | Could |
| Year-in-review | Annual summary comparing to previous years | Multiple charts | Could |

### 12.2 Report features

| ID | Requirement | Priority |
| --- | --- | --- |
| RPT-01 | Interactive charts: click a bar or slice to drill down to the transactions behind it. | Must |
| RPT-02 | Compare periods (this year vs. last year, month vs. same month last year). | Must |
| RPT-03 | Custom report builder: choose rows, columns, filters, grouping and chart type; save and reuse. | Should |
| RPT-04 | Export to PDF, Excel/CSV and print. | Must |
| RPT-05 | Scheduled reports (e.g. monthly summary generated on the 1st). | Could |
| RPT-06 | Phone app shows a small set of read-only summaries: account balances, bills due, budget remaining. | Should |
| RPT-07 | Reports by person, by household, or for a selected group of accounts (e.g. "cottage"). | Should |

## 13. Recommended additions

These features were not in the original request but are recommended because household finance users consistently need them and they reuse data the application already holds.

### 13.1 Budgeting and goals

| ID | Requirement | Priority |
| --- | --- | --- |
| BUD-01 | Monthly and annual budgets per category, with rollover of unspent amounts optional per category. | Must |
| BUD-02 | Sinking funds for irregular costs (car replacement, RV repairs, holidays): monthly set-aside with a target date. | Should |
| BUD-03 | Savings goals (emergency fund, down payment, travel) linked to accounts, with progress tracking. | Should |
| BUD-04 | Budget alerts on the phone when a category reaches 80% and 100%. | Should |
| BUD-05 | Budget built automatically from the last 12 months of actual spending as a starting point. | Could |

### 13.2 Household members, user accounts and permissions

| ID | Requirement | Priority |
| --- | --- | --- |
| HH-01 | Household members as people (adults, children, dependants) used for ownership, medical, plans and reporting. | Must |
| HH-02 | Per-member phone access limited to capture and their own summaries; the administrator approves imports. | Should |
| HH-03 | Children's allowance and savings tracking. | Could |
| HH-04 | Shared expense splitting between members or with people outside the household (roommates, trips with friends), with "who owes whom" balances. | Could |

Each household member gets a user account whose access is set by account group (Shared, Utilities, Household, etc.) and who is linked to their own bank accounts, cards and plans.

| ID | Requirement | Priority |
| --- | --- | --- |
| HH-05 | Each member has a user account with their own desktop sign-in and their own paired phone(s). | Must |
| HH-06 | Roles: Administrator (everything, including users and settings), Member (access as granted), Viewer (read-only as granted). | Must |
| HH-07 | User-defined account groups (e.g. Shared, Utilities, Household, Investments, Cottage); access is granted per group or per individual account. | Must |
| HH-08 | Permission levels per group or account: none, view, capture only, edit. | Should |
| HH-09 | Each user is linked to the bank accounts, credit cards, loans, registered plans and assets they own; their own items are visible to them by default and private unless shared. | Must |
| HH-10 | Reports, budgets, bill reminders and phone summaries show only what the signed-in user may see. | Must |
| HH-11 | Private accounts are encrypted with a key tied to the owner's password, so other users of the same computer cannot read them even by opening the data file directly. | Should |
| HH-12 | A member's phone captures go to that member's review inbox for their own accounts, and to the administrator's for shared accounts. | Should |
| HH-13 | Activity log per user, viewable by the administrator. | Should |

### 13.3 Tax support (Canada and Quebec)

| ID | Requirement | Priority |
| --- | --- | --- |
| TAX-01 | Tax slip checklist per person per year (T4, T4A, T4RSP, T4RIF, T5, T3, T5008, RRSP receipts, RL slips), marked as received and stored in the vault. | Should |
| TAX-02 | Year-end tax package: medical, charitable donations, child care, investment income, capital gains, RRSP/FHSA deductions, exported for an accountant or tax software. | Should |
| TAX-03 | Instalment payment schedule and reminders for those required to pay quarterly. | Could |
| TAX-04 | Clear notice that tax figures are organizational aids, not tax advice. | Must |

### 13.4 Estate and emergency information

| ID | Requirement | Priority |
| --- | --- | --- |
| EST-01 | "In case of emergency" summary: every institution, account (masked), policy, plan, advisor and where key documents are kept. | Should |
| EST-02 | Location of the will, power of attorney and mandate in case of incapacity (Quebec), safe deposit box and keys. | Should |
| EST-03 | Printable or encrypted-export version for a spouse or executor. | Should |

### 13.5 Other

| ID | Requirement | Priority |
| --- | --- | --- |
| OTH-01 | Charitable donation tracking with official receipts. | Should |
| OTH-02 | Vehicle and business-use mileage log (for employment or self-employment claims). | Could |
| OTH-03 | Global search across all records and document text from one search box. | Must |
| OTH-04 | Onboarding wizard that walks a new user through household, accounts, bills and first statement import. | Should |
| OTH-05 | Import from Quicken at launch through its QIF export (Quicken's own data file is proprietary and undocumented): accounts, categories, payees, transactions, splits, transfers and investment history. Also CSV, GnuCash files and QIF from Moneydance. | Must |

## 14. Security, privacy, backup and data portability

All financial data stays on the user's own devices and is encrypted at rest and in transit; nothing is sent to the developer or any third party without explicit opt-in.

### 14.1 Security

| ID | Requirement | Priority |
| --- | --- | --- |
| SEC-01 | Database and document vault encrypted at rest (AES-256), key derived from the user's master password. | Must |
| SEC-02 | Desktop app locks after inactivity; unlock by password, with Windows Hello on Windows as an option. | Must |
| SEC-03 | Phone app protected by device biometrics or PIN; captured items stored encrypted until sent. | Must |
| SEC-04 | Account numbers, card numbers and SINs stored masked by default; full values revealed only on request after re-authentication. | Must |
| SEC-05 | No storage of online banking passwords, card CVVs, or crypto private keys and seed phrases. | Must |
| SEC-06 | All network traffic (sync, price and rate downloads) uses TLS; sync bundles additionally end-to-end encrypted (SYNC-03). | Must |
| SEC-07 | Master password recovery via a printed recovery key generated at setup, since there is no server to reset it. | Must |
| SEC-08 | Signed application updates, checked before install. | Must |

### 14.2 Privacy

| ID | Requirement | Priority |
| --- | --- | --- |
| PRV-01 | No telemetry or analytics unless the user opts in; crash reports reviewed before sending. | Must |
| PRV-02 | Any cloud feature (AI extraction, live bank feeds, cloud folder sync) is off by default and explains what data leaves the device. | Must |
| PRV-03 | Compliant with PIPEDA and Quebec's Law 25 where the developer handles any personal data. | Must |

### 14.3 Backup and recovery

| ID | Requirement | Priority |
| --- | --- | --- |
| BAK-01 | Automatic encrypted backups on a schedule (daily by default), keeping a configurable number of versions. | Must |
| BAK-02 | Backup to a local drive, external drive, network drive or the user's own cloud folder. | Must |
| BAK-03 | One-click restore, with a test-restore check that verifies backups are readable. | Must |
| BAK-04 | Backup reminder if no successful backup in 7 days. | Should |
| BAK-05 | Ability to move the whole installation to a new computer from a backup. | Must |

### 14.4 Data portability

| ID | Requirement | Priority |
| --- | --- | --- |
| EXP-01 | Full export of all data in open formats (CSV and JSON) plus all documents as original files, so the user is never locked in. | Must |
| EXP-02 | Export of selected transactions as QIF/OFX/CSV for other software or an accountant. | Should |

## 15. Non-functional requirements

The application must be fast with decades of data, accurate to the cent, usable by non-experts, and maintainable for many years.

| ID | Area | Requirement | Priority |
| --- | --- | --- | --- |
| NFR-01 | Platforms | Desktop: Windows 10/11 and Linux (current Ubuntu LTS, Debian and Fedora releases). macOS is out of scope. Mobile: Android 10 or later, phones and tablets. | Must |
| NFR-02 | Performance | Handles 30 years of history (about 250,000 transactions and 50,000 documents) with screens opening in under 1 second and reports in under 3 seconds. | Must |
| NFR-03 | Performance | Phone capture-to-saved in under 5 seconds, including on-device text recognition. | Should |
| NFR-04 | Accuracy | All money stored as exact decimal values (never floating point); currency rounding per ISO 4217 rules; crypto to 8 decimals. | Must |
| NFR-05 | Integrity | Every change saved in a transaction-safe database; no data loss on power failure or crash. | Must |
| NFR-06 | Language | Full English and French interface, reports and default categories; user can switch at any time. | Must |
| NFR-07 | Localization | Date, number and currency formats follow the user's locale (e.g. 1 234,56 $ in French Canada). | Must |
| NFR-08 | Accessibility | Meets WCAG 2.1 AA: keyboard navigation, screen reader labels, adjustable text size, sufficient contrast, dark mode. | Should |
| NFR-09 | Usability | A new user can add an account, capture a receipt and reconcile a first statement without a manual, guided by the onboarding wizard. | Should |
| NFR-10 | Offline | All features work without internet except price/rate downloads, cloud sync and opt-in cloud services. | Must |
| NFR-11 | Updates | In-app update check; database upgrades are automatic and back up the data first. | Must |
| NFR-12 | Help | Built-in help, tooltips and searchable user guide in both languages. | Should |
| NFR-13 | Maintainability | Modular design so new account types, importers and report types can be added without changing the core. | Should |
| NFR-14 | Testing | Automated tests covering all calculations (interest, amortization, ACB, FX, RRIF minimums, reconciliation). | Must |

### 15.1 Distribution

The application is published publicly: Windows through the Microsoft Store, Linux and Android builds through GitHub.

| ID | Requirement | Priority |
| --- | --- | --- |
| DIST-01 | Signed Windows build submitted to the Microsoft Store (MSIX, or an MSI/EXE installer, which the Store also accepts). | Must |
| DIST-02 | Linux builds (.deb, .rpm, AppImage) sold through an online storefront, with checksums; source code on GitHub. Flatpak on Flathub once its paid-app support launches (16.3). | Must |
| DIST-03 | Free Android companion app on Google Play and as a signed APK on GitHub Releases; an F-Droid build is optional. | Must |
| DIST-04 | Published privacy policy (required by the Microsoft Store and Google Play) stating that data stays on the user's devices and describing opt-in cloud features. | Must |
| DIST-05 | Updates: the Microsoft Store, Google Play and Flathub update their own installs; .deb, .rpm, AppImage and APK builds check GitHub Releases for new versions. | Should |
| DIST-06 | GitHub repository with licence, user guide, contribution guide and issue templates; support through GitHub Issues and a dedicated support email address. | Should |
| DIST-07 | Store listings and in-app text in English and French. | Should |

## 16. Phased delivery plan and open questions

Build in five phases so the household can start using the core books and phone capture early, while the larger wealth and records modules follow.

&#91;embedded content: delivery roadmap · 5 phases, not to scale\]

Phases 1 and 2 together cover daily household bookkeeping; each later phase adds a self-contained module on top of the same data.

### 16.1 Decisions

| Question | Decision | Where reflected |
| --- | --- | --- |
| Audience | Personal and household use, and published publicly: Microsoft Store (Windows) and GitHub (Linux) | 15.1 Distribution |
| Desktop platforms | Windows and Linux; no macOS | NFR-01, 2.2 |
| Who builds it | The owner, using Claude Code | Technology choice in 2.2 |
| Live bank feeds | Excluded | 1.2, 5.6 |
| Cloud AI extraction | Accepted as opt-in, using image + JSON Schema requests | 4.5, OCR-06 |
| Household members | Configurable user accounts, access by account group, each linked to their own accounts and cards | 13.2 (HH-05 to HH-13) |
| Apple devices | Out of scope (no Apple hardware or developer account) | ARC-05 |
| Quicken import | Required in the first release, through QIF | OTH-05, Phase 2 |

### 16.2 Licensing, pricing and distribution decisions

| Topic | Decision | Notes |
| --- | --- | --- |
| Licence | GPL-3.0 (confirmed) | As sole copyright holder, the owner can still sell the Store version; others may share and modify the code but cannot release a closed-source copy. If outside contributions are accepted, contributors sign a contributor agreement so the licence can be changed later. |
| Microsoft Store pricing | Start free | The price can be changed later in Partner Center. Users who already installed it free keep it. |
| Google Play pricing | Start free | Google Play does not allow an app published as free to become paid later. Any income on Android must come from optional in-app donations or a one-time "supporter" purchase. |
| Android distribution | Google Play (developer account registered) | New personal Play accounts must run a closed test with at least 12 testers for 14 days before public release. Signed APK also on GitHub Releases. F-Droid undecided; it cannot sell apps, only show donation links. |
| Support | GitHub Issues and a dedicated support email address | DIST-06 |
| Linux packages | Flatpak on Flathub (primary), plus .deb, .rpm and AppImage | DIST-02 |

### 16.3 Income model

The desktop application is the paid product; the Android app is a free companion that only works with a desktop installation.

| Platform | How it is sold | Notes |
| --- | --- | --- |
| Windows | Paid in the Microsoft Store | Starts free; price set later in Partner Center |
| Linux | Paid ready-to-install builds (.deb, .rpm, AppImage) from an online storefront | A merchant-of-record storefront such as Paddle or Lemon Squeezy collects payment and handles sales taxes (GST/HST, QST, foreign VAT); itch.io and Gumroad are simpler alternatives |
| Linux (Flathub) | Deferred | Flathub has planned paid apps for several years, but the feature is not yet live ([source](https://tim.siosm.fr/blog/2025/11/24/building-better-app-store-flathub/)); a free Flathub listing would undercut the paid builds |
| Android | Free on Google Play and GitHub | Optional one-time supporter purchase on Google Play |
| Source code | Free on GitHub | Required by GPL-3.0; anyone may build it themselves, so the price pays for convenience, signed builds, updates and support |

No licence keys or copy protection: under GPL-3.0 anyone could remove them, and they add support burden without stopping copying.

Pricing decisions:

- **Price model:** fixed price on both Windows and Linux. The Microsoft Store supports fixed prices only, with optional free trials and temporary sale prices.
- **Separate purchases:** Windows and Linux are bought separately; no cross-platform entitlement or proof-of-purchase process is needed.
- **Linux storefront (deferred):** chosen before the first paid Linux release. Candidates: Paddle or Lemon Squeezy (merchant of record, handle sales taxes), itch.io or Gumroad (simpler), or a payment processor such as Nuvei (lower fees, but the seller handles sales taxes, checkout, refunds and chargebacks). Nothing in the design depends on this choice.

Note: existing products already cover parts of this scope (for example Quicken, Moneydance, GnuCash), but none combines all of it with Canadian registered plans, medical claims and asset maintenance. Reviewing them before development can help confirm priorities.
