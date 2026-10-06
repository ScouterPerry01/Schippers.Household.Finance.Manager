# Phase 5 exit check, technical part (2026-10-05)

The technical half of the Phase 5 exit check: upgrades from every earlier database, performance on 30 years of data, the tax calculations against published figures, and English/French parity. Each item says what was checked, against which source, and the result. Security observations are listed at the end for the security review; no security fixes were made here.

## 1. Upgrades from every earlier version (NFR-11)

- **What was checked:** a household saved by any earlier version opens in this one with its data intact, and its upgraded databases match a new one. Ledger versions 1 to 28 and core versions 1 to 7 were tested; ledger 29 and core 8 are the current versions.
- **Existing tests:** `MigrationTest` (core/data-jdbc) has one test per version, but ledger 3 and 16 and core 3 and 7 had none. These tests check the new tables, not the whole schema.
- **New test:** `UpgradeTest` (core/books) runs 35 cases, one per earlier version of each database. For each case:
  - It creates a household and fills it through the books: two accounts, categorized and split transactions, payees and a transfer.
  - It rebuilds the ledger (or the core database) at the old version from the saved schema snapshot. The rebuild copies every row and column that version had, encrypted with the household's own key.
  - It opens the household again, which runs the upgrade.
  - It compares what the screens show before and after: accounts and balances, every register with its running balances and splits, payees, the category count, spending by category, income and expense, net worth and search.
  - It compares the upgraded schema with a newly created one: every table's columns (type, not null, default, key), foreign keys with their delete rule, and indexes with their columns.
  - It confirms that a copy was saved before the upgrade.
- **Result:** all 35 cases pass. No data is lost. No upgraded database is missing a column, foreign key or index that a new database has.

## 2. Performance (NFR-02)

**How it was measured.** `./gradlew :core:books:performanceTest` uses 250,000 transactions, 50,000 documents, 30 years of investments and, new in this check, 2,000 contacts, each with a phone, an email and a client number, a quarter of them linked to payees. Screens must open in under 1 second and reports in under 3 seconds. Several other builds were running on the machine at the same time, so timings vary by about ±30 % between runs.

**New measurements for the Phase 5 screens:**
- the contacts list
- contacts text search
- a payee's contacts
- global search, which now includes contacts
- the tax package for one year
- the income tax estimate, with and without the package already built
- custom reports: 30 years by category and year, 30 years by payee and month, and one year by person and category
- the year in review

**Problems found and fixed:**

1. **The tax package took 15.8 s.**
   - The slip checklist and the donations list read each document link, and looked up each transaction, one query at a time. Each lookup searched every group, and each search cost about 3 ms.
   - Document counts are now read in one query per group (new query `documentCountsFor`). Donations read their transactions in one batch (`txnsByIds`).
   - The checklist reads only the year's transactions of each registered plan (`txnsForAccountBetween`) instead of the whole register with running balances.
   - The investment income report and the year's splits are now built once and shared by the package and its checklist.
   - The checklist looks up each person's province once.
2. **Many services fetched every account with its balance just to look up account names and types.** Computing the balances sums all 250,000 transactions, about 200 ms each time.
   - The new `AccountService.all()` returns the accounts without balances.
   - It is now used by 21 services that did not need balances: the reports, the investment income report, registered plans, the tax package, custom reports, the year in review, search, contacts and others.
3. **Custom report by payee over 30 years took 70.5 s.**
   - Rows and columns were sorted by a total recalculated across all cells at every comparison. The totals are now added up once.
   - Long ranges now read the table straight (`customSplitsWide`), as the other long-range reports do.
   - Payees and tags are read with the splits (`tagsBetween`) instead of one extra query per transaction.
   - Each date is parsed only once.
4. **Global search:**
   - The splits of the hits are read in one query (`splitsForTxns`), not one query per hit.
   - A word matching few transactions no longer walks the date index row by row; the table is read straight and only the matches are sorted.
5. **The slip checklist's query by category** now starts from the year's transactions instead of every split in those categories over 30 years.

Only queries were added or changed. The schema did not change, so there is no new database version.

**Before and after, in milliseconds** (before: the first run of this check; after: the last run):

| Screen or report | Before | After | Limit |
|---|---|---|---|
| Tax package, one year | 15,807 (not measured before this check) | 1,192 | 3,000 |
| Custom report, 30 years by category and year | 5,848 (first measurement) | 1,467 | 3,000 |
| Custom report, 30 years by payee and month | 70,549 (first measurement) | 1,494 | 3,000 |
| Investment income and gains, one tax year | 1,584 | 176 | 3,000 |
| Global search ("pharmacie") | 900 | 417 | 1,000 |
| Global search with contacts ("roy 12", a rare word) | 1,276 (first measurement) | 567 | 1,000 |
| Dashboard data | 715 | 353 | 1,000 |
| Portfolio returns, 30 years | 287 | 157 | 3,000 |
| Asset allocation | 273 | 138 | 1,000 |

**New measurements, all within their limits:**
- Contacts list (2,000): 41
- Contacts text search: 37
- A payee's contacts: 19
- Income tax estimate: 2,142 in all, 869 when the package is already built
- Custom report, one year by person and category: 334
- Year in review: 2,464

**Earlier measurements, unchanged within the machine's variation:**
- Account list: 245
- Largest register: 141
- Documents: 16
- Text inside 50,000 documents: 19
- Income and expense over 30 years: 945
- Spending by category over 30 years: 957
- Spending by payee over 30 years: 1,585
- Net worth over 30 years: 1,536

## 3. Calculations against published figures

New test class `PublishedFiguresTest` (core/calc), 17 tests. Each test cites its source in a comment. All figures were read on canada.ca, revenuquebec.ca, quebec.ca, fin.canada.ca and bcbudget.gov.bc.ca, in October 2026.

### OAS recovery tax
- **ESDC worked example, 2025:** "$100,000 - $93,454 = $6,546 x 0.15 = $981.90". The estimate gives 981.90.
- **Full recovery:** the full pension is recovered at ESDC's published incomes, $152,062 for 2025 and $148,451 for 2024. The thresholds of $93,454 and $90,997 match.
- **Result:** matches.

### Canada workers benefit
- **Sources:** CRA, line 45300, "How much you can get" (2025), and Schedule 6 (2024).
- **Checked:** the maximums of $1,633 and $2,813 (2025) and $1,590 and $2,739 (2024), and the benefit reaching zero at the published incomes: $37,742 and $49,393 (2025), $36,749 and $48,093 (2024).
- **Quebec residents (form 5005-S6 2025):** a single person's maximum of $3,812.06 at $14,170.05, and zero at $33,230.35.
- **Result:** matches.

### Refundable medical expense supplement
- **Sources:** CRA Federal Worksheet 2025, line 45200, and the line 45200 page.
- **Checked:** $1,504 at $33,294; nothing at $63,374; nothing below $4,390 of earned income.
- **Result:** matches.

### Quebec work premium
- **Sources:** Schedule P 2024 and 2025, and Finances Québec's 2026 parameters.
- **Checked:** the estimate reaches exactly the published maximums for all 12 cases (3 years, 4 household types):

| Year | Person alone | Couple | Single parent | Couple with children |
|---|---|---|---|---|
| 2024 | 1,152.34 | 1,797.07 | 2,980.20 | 3,873.00 |
| 2025 | 1,185.52 | 1,848.34 | 3,066.00 | 3,983.50 |
| 2026 | 1,207.33 | 1,882.45 | 3,122.40 | 4,057.00 |

- **Result:** matches.

### Quebec refundable medical expense credit
- **Source:** Revenu Québec's line 462 table (2025): expenses up to $1,877 give nothing at a family income of $32,800.
- **Checked:** the estimate gives 0.00 at $1,877 and 0.25 at $1,878.
- **Result:** matches.

### Health services fund contribution
- **Source:** Schedule F 2025.
- **Checked:** 68.70, 150, 319.40 and the $1,000 maximum at the published thresholds of $18,130 and $63,060.
- **Result:** matches.

### RAMQ drug insurance premium
- **Sources:** Schedule K 2024 and 2025, and Finances Québec Information Bulletin 2025-8, table 5.
- **Checked:** nothing at the exemption; $392.00 single and $196.50 couple at $5,000 above it; the maximums of $755 (2025) and $737.50 (2024).
- **Result:** matches.
- **Known approximation, not changed:** for part-year coverage, Schedule K caps the premium at $755 less a monthly amount for each exempt month ($62.00 from January to June, $63.83 from July to December). The estimate takes 1/12 of the premium per month instead. The two can differ by a few dollars, depending on which months were covered.

### Minimum tax
- **Quebec:** TP-776.42, exemptions of $175,000 (2024), $179,990 (2025) and $183,680 (2026), at 19 %. Matches.
- **Federal, 2024:** a gain worked through Form T691 2024: exemption of $173,205 (line 95), 20.5 % (line 97), 100 % of capital gains, and 50 % of credits. Matches.
- **Finance Canada's AMT examples** (the Daniel and Caroline cases) involve the capital gains exemption and stock options, which the estimate does not model, as the manual says. They were not used.

### Federal basic personal amount
- **Source:** CRA Federal Worksheet 2024 and 2025, line 30000.
- **Checked:** the phase-out formula (14,538 + 1,591 × (253,414 − income) ÷ 75,532), and the 2024 end points.
- **Result:** matches.

### Tuition
- **Sources:** CRA provincial tax information for 2025 (5001-PC to 5014-PC) and guide P105.
- **Checked:** NL, PE, NS, NB, MB, BC, YT, NT and NU give a credit on this year's fees with a $5,000 transfer; ON, SK and AB only carry unused amounts forward; the federal transfer is $5,000. New Brunswick restored its credit in 2019.
- **Result:** matches. The CRA publishes no numeric tuition example.

### Sales taxes in all 13 jurisdictions
- **Sources:** CRA's GST/HST rates page and Revenu Québec.
- **Rates in force on 2026-10-05:** GST 5 %; HST 13 % in ON and 15 % in NB, NL and PE; HST 14 % in NS since 2025-04-01; PST 7 % in BC and 6 % in SK; RST 7 % in MB; QST 9.975 % in Quebec; GST only in AB, YT, NT and NU.
- **Result:** all match the rules and the existing `SalesTaxesTest`.
- **No rate changes announced** for 2025–2026, only changes to what is taxed: B.C. professional services from 2026-10-01, and Manitoba cloud services from 2026-01-01.

### Provincial tax payable
- **Source:** B.C. Ministry of Finance, Budget 2025, table A4, "Interprovincial Comparisons of Provincial Personal Income Taxes Payable". It covers a single wage earner in 10 provinces at 30,000 to 150,000 of income.
- **Method:** the estimate was run with 2025 CPP/QPP, EI and QPIP contributions, within 1.5 % or $40.
- **Matches:** BC, SK, MB, ON, NB, PE and NL, and Quebec from 80,000, within 0.3 %. Nova Scotia matches the 2024 table.
- **Left out of the 2025 comparison, each explained in the test:**
  - Alberta: the table was made before its 8 % bracket.
  - Nova Scotia: the table was made before its 2025 indexation and new basic amount.
  - Newfoundland and Labrador at 30,000: the table was made before its 2025 low-income reduction.
  - Saskatchewan at 30,000: the table appears to count the refundable low-income tax credit.
  - Quebec at 30,000 and 50,000: the estimate is 306 and 164 higher than the table. A hand calculation along the TP-1 lines agrees with the estimate. The cause could not be found in the table's notes, and nothing was changed. This is open for the owner.

### Discrepancies found and fixed

1. **Ontario low-income individuals and families tax (LIFT) credit was missing.**
   - It is 5.05 % of employment income, up to $875, less 5 % of adjusted net income above $32,500 ($65,000 for a family). It comes after the Ontario tax reduction, and does not apply when Ontario's additional tax for minimum tax purposes applies.
   - Source: CRA, Schedule ON428-A 2024 and 2025, and Form ON428, line 62140.
   - Without it, a single Ontario earner at $30,000 was estimated to pay $767 more Ontario tax than the B.C. table shows. After the fix the result is $300, the health premium only, as the table shows.
   - New rule `tax.prov.lift` and new estimate line `LOW_INCOME_CREDIT`.
2. **The low-income tax reductions of New Brunswick, Nova Scotia, Newfoundland and Labrador and Prince Edward Island were missing.**
   - Each gives amounts for the person, a spouse or eligible dependant, other children and (P.E.I.) age 65, less a rate of adjusted family income above a base. One spouse claims it for the family.
   - Sources: CRA Forms NB428, NS428, NL428 and PE428 for 2024 and 2025. Each value is cited in `incometax.rules`.
   - New Brunswick at $30,000 was estimated $550 too high before the fix; it now matches the table.
   - New rule `tax.prov.familyLowIncomeReduction`.
   - P.E.I.'s $250 age reduction for a spouse is not counted, because the estimate does not know the spouse's age.

Both new rules have English and French texts in Rates and rules. The manual's tax chapter, in both languages, now lists them, and no longer says that these reductions are left out.

### Figures that may need 2026 updates (not changed here)
- The four Atlantic reductions have no 2026 forms yet, so they keep their 2025 values.
- Schedule K 2026 (drug premium) is not published. A derived maximum of about $777.48 comes from RAMQ's July 2026 to June 2027 premium of $789; it is unofficial and was not entered.
- These belong with the 2026 figures work.

## 4. English and French

**Message files** (4,085 keys in each language):
- The same keys in both files, with matching placeholders, and no single apostrophes.
- Every key used in the code exists.
- No French value is still in English.
- About 100 values are identical in English and French; all are cognates or proper names.
- Android strings (198) match.

**Help (40 topics):**
- 10 French topics had fallen behind sentences added to the English; they are now added: users, privacy, rates, assets, backups, documents, estate, getting started and investments.

**Manual (56 chapters):**
- The chapters, heading ids and links match (ManualTest).
- The differences in sentence counts come from French quotation marks and abbreviations, not from missing text.

**Rate rule texts:**
- RateRuleTextsTest covers every rule in every area. The two new rules have texts in both languages.

**Store texts:**
- Microsoft Store and Google Play match field by field, with 15 features each, and the same screenshot sets.
- The only difference is the Play short description: "Private, encrypted." becomes "Chiffré." in French, because of the 80-character limit. This was left as it is.

**Hard-coded English fixed:**
- Four OK buttons now use the new key `common.ok`.
- The first shared group of a new household was always named "Shared". It is now in the household's language (`group.sharedName`, "Partagé").
- The fallback name when restoring a backup is now translated.
- The RESP "other grant" description is now in both languages, like the other grant names.
- The document file names in the tax package export ("Donation - …", "Medical - …") now follow the language, and the type of medical service is translated.
- The backup check problems used to be shown in English. They now carry a key and arguments (`backupProblem.*`), as the registered-plan warnings already did.
- The notes from the Quicken, brokerage CSV and crypto exchange imports, the crypto cost warning and the CoinGecko price warning are now in the user's language (`importNote.*`). The importers return an `ImportNote` with a key and arguments.
- Errors from a single imported line now show the validation message in the user's language, not the English one.

**Left as they are:**
- The "Imported" and "Not imported" subfolders of watched and transfer folders keep their English names, because renaming them would break existing users' folders.
- Descriptions stored by the crypto exchange importer ("Kraken trade", and so on) and the investment CSV importer's "Tax withheld" are data written into the books.
- The marker notes `KEPT_NOTE` and `READ_NOTE` are used to recognize documents.
- The "Desktop" and "Phone" fallback device names appear only when the computer has no name.
- Third-party attribution lines are kept as their providers publish them.
- The French help and manual mix ’ and '. This is style only.

## 5. Tests and build

- Full `./gradlew build` passes, Android lint included.
- 683 tests in all, including the new `UpgradeTest` (35 cases) and `PublishedFiguresTest` (17 tests). 1 test is skipped and none fail.
- `performanceTest` passes.

## Security observations (for the security review, not changed here)

- The backup check's English problem texts used to include exception messages. They still do, as arguments (for example "cannot be opened: …"), which may show file paths to the user. This is harmless, but worth knowing.
- `Backups.restore` still throws an English `BackupException` message, built from the check's problems.
- The upgrade test showed that the database files are encrypted with the household's partition keys at every schema version. The test only replaced the files, using the key the app already holds; no key ever leaves the app.
