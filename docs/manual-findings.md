# Problems found while writing the manual (2026-10-05)

Writing the user manual meant reading every screen against the code. These are the problems found, each with the decision taken (owner's instruction 2026-10-05: fix them all, on the recommendation). Status: **Fix** is to be changed in the app and the manual; **No change** says why the behaviour stays, and the manual already describes it.

Each fix also updates the manual's English and French chapters that describe the behaviour.

**Outcome (2026-10-05):** every Fix below is done, tested and in the manual, unless its line says otherwise after **Done:**.

## Accounts, register and dashboard

- **M-01** A closed account cannot be reopened. Fix: a Reopen button on a closed account.
- **M-02** Deleting a transaction in the register happens at once, without a question; the change history kept for each transaction is never shown. Fix: ask before deleting; a History view in the transaction dialog.
- **M-03** The Dashboard tile "Bills in the next 7 days" also counts bills overdue up to a year. Fix: the label says overdue or due in the next 7 days.
- **M-04** Account balances, the Accounts totals and the Dashboard's Cash available and Owing tiles include post-dated transactions. Fix: totals and tiles as of today; the account list shows the balance after post-dated transactions beside it when it differs.
- **M-05** After Undo reconciliation the statement cannot be reconciled again, and its file cannot be imported again. Fix: undoing returns the statement to Open, so it can be reconciled again. **Done:** undoing keeps the undone statement as a record, with its reason and report, and adds an open copy with the same lines that can be reconciled again. The file is still refused on import.
- **M-06** Card details: credit limit, cash advance rate, statement day and due day are kept but used nowhere; card statements have no screen. Fix: available credit and use of the limit on the card's register and the Owing tile; the payment due day as a reminder and on the Calendar; the cash advance rate in the Debt summary. **Done:** available credit and use of the limit; a payment-due reminder 7 days ahead and on the Calendar; the cash advance rate in the Debt summary.
- **M-07** The missing-rates message says "Add a rate under Exchange rates", but the screen is Rates and prices (Taux et cours). Fix the text.
- **M-08** Rewards: "Worth of one, in dollars" is really in the account's currency; entries can only be added after the program is saved once. Fix: the label names the account's currency; entries can be added at once.
- **M-09** Saving an existing split again drops each line's person and tax flag. Fix (bug).
- **M-10** A payee typed on a transfer is silently dropped. Fix: transfers keep their payee.
- **M-11** Quicken import makes new accounts in the base currency only, and offers only accounts in that currency. Fix: a currency per new account, and every account offered.
- **M-12** The Getting started guide cannot be shown again once hidden. Fix: Show the Getting started guide again, on the Display and accessibility screen.
- **M-13** The register has no filters. No change: this was the manual brief's assumption, not a problem; search finds transactions.

## Documents, bills, budgets, goals and calendar

- **M-14** The phone's Paid with, Category and For choices are sent with each capture but the desktop ignores them. Fix: the desktop uses them when the capture is filed.
- **M-15** "Record the pay…" appears in the document window only when AI reading is on. Fix: offered without AI too, typed by hand.
- **M-16** Changing a document's Kind does not update the filing choices until it is filed and reopened. Fix: the choices follow the kind chosen.
- **M-17** A skipped due date cannot be un-skipped. Fix: Unskip.
- **M-18** Undo of a bill payment that has been reconciled is silently refused. Fix: ask, as the register does, or say why not.
- **M-19** Deleting a budget, or a goal's history entry, happens without a question. Fix: ask first.
- **M-20** The bill history (average, same month last year, unusual amount) is computed but shown nowhere. Fix: shown on the To pay tab and in the bill form.
- **M-21** Bill reminder notifications come once per session, though meant once per household and day. Fix: once per household and day, remembered on this computer.
- **M-22** Calendar's button for a tax instalment had no label. Fixed with the manual (04bed33).
- **M-23** Payment method on a bill is a reminder only. No change: that is its purpose.
- **M-24** An appointment gives one notification per reminder time ticked. No change: each reminder time is meant to notify.
- **M-25** The transfer folder's "Imported" and "Not imported" folders keep English names in French. No change: the folder is shared with phones and computers whose languages can differ, so one fixed name is safer.

## Investing and borrowing

- **M-26** Editing or deleting an investment transaction whose lines are reconciled does nothing visible. Fix: ask, as the register does.
- **M-27** Plan details: Last year's investment earnings always opens empty, and saving erases the stored value; emptying Value on January 1 deletes the row. Fix (bug): the field shows the stored value; empty fields leave values alone.
- **M-28** A spousal RRSP without a contributor counts against nobody's room. Fix: the contributor is required for a spousal RRSP.
- **M-29** RESP grants cannot be removed; deleting the deposit leaves its grant. Fix: Delete on a grant; deleting a deposit asks whether to delete its grant.
- **M-30** Deleting a mortgage renewal does not restore the previous term end. Fix (bug). **Done:** the replaced term end is kept in its own columns (ledger version 26).
- **M-31** Brokerage CSV rows of withholding or foreign tax come in as fees. Fix: as tax withheld on the income, for the foreign tax credit.
- **M-32** Cash deposits imported into a registered plan from a brokerage file are not counted as contributions. Fix: counted as contributions.
- **M-33** Check a statement: the Cash field does not follow a change of the statement date. Fix.
- **M-34** Buying, editing or selling precious metals never moves money. Fix: an optional account paid from or deposited to.
- **M-35** A security's coupon and maturity are kept but unused. Fix: the maturity on the Calendar with a reminder; the coupon shown with the holding.
- **M-36** Wallets made by an exchange import belong to the signed-in user's member, and exchange cash accounts to no one. Fix: the import asks for the owner.

## Reports and taxes

- **M-37** Scheduled reports make only the latest missed period, though the hint says missed periods are made. Fix: every missed period, up to a year back.
- **M-38** Spending by payee shows Compare with but ignores it. Fix: the comparison columns.
- **M-39** Budget vs actual and Reconciliation status show the Accounts picker and Choose accounts… but ignore them. Fix: hidden where they do not apply.
- **M-40** Budget vs actual uses only the month of the To date, except for This year and Last year. Fix: the chosen period.
- **M-41** A saved report does not keep its tax year, plan year or review year. Fix.
- **M-42** A donation made through payroll (a deposit) refuses any eligible amount as more than the gift. Fix (bug).
- **M-43** Receipt details are kept per transaction, so one payment with gifts for two people shares one receipt. Fix: per split line. **Done:** a receipt per gift (person and kind) in a new table (ledger version 26, additions only); receipts recorded earlier for a whole payment still serve the gifts without their own, shared in proportion.
- **M-44** The year-end package's medical line uses each person's own best 12 months, while the Medical expenses report claims the household's together. Fix: the package follows the report.
- **M-45** The Medical expenses report's totals use the date of service, the credit the date paid. Fix: the paid date throughout, the service date shown.
- **M-46** Reconciliation status says more than 45 days is flagged, but nothing is. Fix: those rows stand out.
- **M-47** The CRA's fixed amount for the medical credit is built in only for 2023 to 2025. Fix: 2026 added from the CRA's published figure. **Done:** $2,890 for 2026, from the CRA's page on the adjustment of personal income tax and benefit amounts.
- **M-48** Spending by payee groups by payee or typed text; the custom report by text ignoring capitals. The Accounts picker's label is the same as Choose accounts…. Fix: one grouping; the picker is labelled Account group.

## Home and family

- **M-49** Editing a trip saved with no vehicle, or an inactive one, silently gives it the first active vehicle. Fix (bug).
- **M-50** "Add to medical expenses" on a trip adds a new expense each time it is clicked. Fix: once per trip; the button then says it is added. **Done:** the trip's expense is remembered in a household setting; deleting the expense frees the trip again.
- **M-51** A family loan marked repaid in full still gains interest. Fix: interest stops when it is closed.
- **M-52** A shared group can only be archived, not deleted. Fix: Delete, asked first.
- **M-53** An allowance's notes are kept but not shown. Fix: Notes in the allowance dialog.
- **M-54** New invoice numbers follow today's year, not the year of the Issued date. Fix.
- **M-55** The total waiting to be paid adds only invoices in the base currency. Fix: a total per currency.
- **M-56** Deleting an invoice leaves the deposit recorded for it. Fix: asks whether to delete the deposit too. **Done:** deleting an invoice offers to delete its deposit too.
- **M-57** The deposit for an invoice can only go to bank accounts in the invoice's currency. No change: money in another currency needs an exchange, recorded as a transfer.
- **M-58** Renaming a rental property does not rename its tag; deleting it leaves the tag. Fix: the tag follows the name; deleting asks whether to remove the tag.
- **M-59** A family loan's rate with more than two decimals is cut, not rounded. Fix: up to three decimals, rounded. **Done in part:** the rate is rounded to the nearest hundredth of a percent rather than cut; three decimals would need the rate stored in finer units.
- **M-60** The asset warranty's hours limit is never used; a vehicle warranty limited only by kilometres never reminds. Fix: ended and reminders by hours (from meter readings) and kilometres (from the odometer).
- **M-61** Medical plans and expenses always go to the default group, with no Store in choice as health records have. Fix: Store in. **Done:** Store in when adding a medical expense or plan.
- **M-62** An asset's prices and values are always in Canadian dollars. Fix: the household's base currency.
- **M-63** Many deletes do not ask first: medical expense, claim, plan and coverage; asset and warranty; insurance policy; vehicle task, service, fuel entry and warranty; project. Fix: every delete asks.
- **M-64** Pets need no permission to change. Fix: viewers cannot change pets.
- **M-65** Organ and tissue donor left untouched leaves the line out of the summary. Fix: shown as Not stated.
- **M-66** The Appointments tab never shows its empty-list text. Fix. **No change:** on checking, the empty-list text does show; the finding was mistaken.
- **M-67** A coverage's start date cannot be changed. Fix. **No change:** medical coverage has no start date. While checking, a real bug in insurance renewals was fixed instead: after Renew the form kept the old term start.
- **M-68** Project costs and contractor jobs are not transactions. No change: they record the work; the payment is a transaction in the register.

## Settings, start screens and phones

- **M-69** The Create screen starts on Quebec whatever the language. Fix: no province until one is chosen.
- **M-70** The recovery key screens have no Print button, though the text exists. Fix: Print.
- **M-71** A household cannot be renamed; login names cannot be changed. Fix: rename the household (administrator) and change a login name (Users). **Done:** the household is renamed on the Household members screen (administrator); a login name changes in Edit user.
- **M-72** Creating a household or resetting needs 12 characters, adding a user or changing a password only 8. Fix: 12 everywhere.
- **M-73** Category rules cannot be edited or reordered, the payee a rule can set has no field, and positions can repeat. Fix: Edit, Move up and Move down, the payee, positions kept in order.
- **M-74** Payee aliases can only be added. Fix: listed, with Remove.
- **M-75** Editing a category does not require its names, and its parent cannot be changed. Fix.
- **M-76** Non-administrators see the Members form as editable, then Save fails. Fix: read only for them.
- **M-77** Categories, payees, institutions, rules, rates and prices, backup and AI settings have no permission checks. Fix: viewers cannot change the lists; backup, AI and price-feed settings are for administrators. **Done:** viewers cannot change the lists, rates or prices; backup and price-feed settings are for administrators. AI settings stay open to members, since each user pays for their own readings with their own key.
- **M-78** The Access tab offers Capture only and Edit for a viewer, whose access is capped at View. Fix: only View offered.
- **M-79** The phone always gets English, because the language setting it reads is never written. Fix: the desktop sends its language. **Done:** the desktop sends its language; the phone app itself follows the phone's language.
- **M-80** The pairing window does not close once the phone is paired. Fix: it closes by itself.
- **M-81** A forgotten PIN on the phone cannot be reset. Fix: Forgot your PIN? erases the phone app's data after a warning, to pair again.
- **M-82** Login names ignore capitals; Windows Hello is not in the app; F1 and Shift+F1. No change: by design, after 1.0 as planned, and already handled.
