# Manual and help audit, 2026-10-07

The owner asked to verify that the Help (F1, `hfm/help/<lang>`) and the user manual (`hfm/manual/<lang>`) are current and cover everything, and to fix what is not. This is the inventory and what was found and fixed, in English and French. The Walk-Me guides were out of scope (tested the same day) and were not changed, apart from typographic apostrophes in their French text.

## Method

- Inventory of every desktop `Section` (39), its tabs, dialogs and forms (`FormDialog`, `AlertDialog`, tab enums in `app/desktop`), and every phone screen and form (`app/android/.../ui`, notifications and calendar writing).
- Label coverage: a script listed, for each desktop source file, every `model.t("…")` label of up to five words (from `messages_en.properties`) not found in its manual chapters, and the same for the phone's `stringResource` labels against the phone chapters. Of 472 desktop and 58 phone hits, nearly all were status lines, empty-list messages or formatted values; the real gaps are in the tables below.
- Each area was read against its code for behaviour (bill classification, statements, account number, utility readings, part payments, instalments and roll-over, column headings, itemize, document pages and zoom, trips with stops, breaks, notes and photos, address lookup, stations nearby, add a station, calendar views, schedules, activities, calendar sync both ways, the agenda and its icon, lock time, language switch, seasonal checklist, utilities, hours, chores, volunteer hours, the Help menu, About, Walk-Me, and the older screens).
- Help topics: one per screen; `HelpTopicsTest` already checked that every Section has a topic in both languages and a manual chapter; a new test checks that no help topic file is left out of the list.
- Checks: `ManualTest`, `HelpTopicsTest`, `MessageKeysTest`, `MessagesTest`, `WalkMeTest`, and a script that compares section ids between English and French, link targets, and French typography.

## Summary

- Screens, tabs, dialogs and forms checked: 303 lines below (each covers one or several manual sections; all 59 chapters, with their 1,093 sections and subsections, and all 43 help topics were read, in each language).
- Manual: 10 added, 102 fixed, 2 n.a., 189 ok.
- Help: 5 added, 38 fixed, 67 n.a., 193 ok.
- New manual sections: Lists and their columns (basics), Step 8 File a first receipt (quick start), The phone's calendars and location (privacy and data), In the Manual window and In a document's pages (shortcuts), On your phone (start: calendar), Utilities and Volunteer hours (start: home and family), two troubleshooting sections for the phone, The household name (members); 13 glossary terms.
- Code and text fixes found on the way: the fuel-order line of the Calendar showed a raw key (`calendar.open.UTILITIES` added, test derived from the renewal kinds); the tank delivery's account was labelled Paid into (now Paid from); the hours invoice hint said to add the sales taxes (they are copied from the last invoice); French typographic apostrophes in the manual, help, Walk-Me and labels; spaces before ";" in the French help and three phone texts.
- Pictures: none added. Every desktop chapter already has current pictures (retaken with the column headings on 2026-10-07). The phone agenda and the phone Settings tab have none; phone pictures need the emulator, not used this round. The French pictures still show the 69 labels whose straight apostrophe became typographic; retaking them is optional.

## Money: accounts, dashboard, budgets, savings goals

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Accounts › account list (columns Account, Balance) | fixed | ok | the new column headings were not mentioned |
| Accounts › totals, show closed accounts | ok | ok |  |
| Accounts › account types | ok | n.a. |  |
| Accounts › Add or edit account form | ok | ok |  |
| Accounts › Show the full account number | fixed | fixed | the wait after several wrong passwords (Too many wrong passwords…) was missing; help did not name Show number |
| Accounts › Close / reopen account | ok | ok |  |
| Accounts › register header and balances, alerts under the name | ok | ok |  |
| Accounts › buttons above the register | ok | fixed | help did not mention Choose transactions… or Card details |
| Accounts › register columns | fixed | n.a. | column headings, announced as headings, ✓ read as Cleared |
| Accounts › earlier transactions | ok | n.a. |  |
| Accounts › entry form, keyboard, payee suggestions | ok | ok |  |
| Accounts › Itemize… from the register | fixed | ok | what the items do to the form (split lines replace a split, total becomes the amount when none typed, uncategorized items take the form's category, nothing saved until Save) |
| Accounts › Split transaction dialog | ok | ok |  |
| Accounts › Transaction templates (use, save as, Templates… window, form) | ok | ok |  |
| Accounts › Edit/delete, RESP grant deposit question, change history, reconciled changes | ok | n.a. |  |
| Accounts › Choose transactions… (categorize, tag, move, export) | ok | fixed | see buttons line |
| Accounts › Transfers | ok | n.a. |  |
| Accounts › Sales tax dialog | ok | ok |  |
| Accounts › Refund dialog | ok | ok |  |
| Accounts › Pay stub dialog | ok | ok |  |
| Accounts › Import statement (OFX, CSV layout, several accounts, results) | ok | ok |  |
| Accounts › Categories to review | ok | ok |  |
| Accounts › Reconcile (balance, attention, groups, Match several…, lines, outstanding, finish, FX) | ok | ok |  |
| Accounts › Statements window, report, paper statement, undo reconciliation | ok | ok |  |
| Accounts › Account alerts | ok | ok |  |
| Accounts › Credit card details | ok | ok |  |
| Accounts › Cards and benefits (cards, spending by card, benefits, protected purchases) | ok | ok |  |
| Accounts › Rewards | ok | ok |  |
| Accounts › Import from Quicken | ok | ok |  |
| Accounts › permissions, metals and crypto, contacts | ok | n.a. |  |
| Dashboard › Getting started guide | fixed | fixed | the Walk-Me line and its Walk-Me guides button (added with the Help menu) were missing |
| Dashboard › tiles | fixed | ok | bills tile now counts what is still owed on a bill paid in part, or the amount set for the bill |
| Dashboard › Needs your attention | fixed | fixed | order was wrong (account alerts come first, missing rates before unusual use); help lacked unusual utility use |
| Dashboard › net worth chart, top spending, currencies | ok | ok |  |
| Budgets › screen, month/year, totals, bars, table | ok | ok |  |
| Budgets › choose category, budget form, delete | ok | ok |  |
| Budgets › monthly/yearly, subcategories, carry over, suggest, currencies | ok | ok |  |
| Goals › screen, account cards, goal lines, progress | ok | ok |  |
| Goals › goal form, planned set-asides, status | ok | ok |  |
| Goals › Set aside, Use (with purchase link), Move, History, Delete | ok | fixed | help did not mention linking the purchase (Purchase (optional)) |
| Help FR accounts/goals/dashboard | help: fixed | missing space before ; (French typography) |  |

## Money: bills, documents, contacts; settings: categories, payees, rules, institutions, AI reading

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Bills › overview, five tabs | ok | ok | checked against BillsScreen: tabs, title-bar buttons, column headings |
| Bills › Add or edit a bill (type, name, Home or business, Bill category, Subcategory, Business of, accounts, payee, masked account number, amount, method, repeats incl. Every … and Instalments on set dates, weekends, due dates, reminders, subscription, meter) | ok | ok | every label of BillForm found; behaviour matches |
| Bills › Statements and statement form (number, amount, issued, due date, readings, amount used, instalments) | ok | ok |  |
| Bills › Property taxes and other instalments, roll-over | ok | ok |  |
| Bills › To pay tab (groups, columns, unusual amount) | ok | ok | Paid recently 31 days, Skipped from a year back, Overdue a year back: match BillService.agenda |
| Bills › Mark paid / Paying in part / overpay question | ok | ok | matches BillService.markPaid |
| Bills › Set the bill's amount | fixed | ok | tip named the old button "Record the amount on this bill" and said it "does the same thing"; now Record it as this bill's statement, which also moves that period's due date and keeps the scanned bill with its statement |
| Bills › Skip, Unskip, Undo, overdraft warning | ok | ok |  |
| Bills › All bills, Calendar, Subscriptions, Cash flow forecast tabs; Export calendar; reminders | ok | ok |  |
| Bills › Bills from scanned documents (Create a bill from this, Attach to a bill) | ok | ok |  |
| Bills › permissions, contacts | ok | n.a. |  |
| Documents › title bar | fixed | fixed | "a title bar with two buttons": there are three; Learned stores… was not listed (added with a link to #learning). Help now has a short line on learned stores and Forget |
| Documents › Import files, drag and drop, watched folder, e-receipts, phone captures, import messages | ok | ok |  |
| Documents › To review, All documents, Old documents tabs | ok | ok |  |
| Documents › document window: preview, pages and zoom (◀ ▶, Page Up/Down, − + Fit, Ctrl wheel, Ctrl + - 0, 50–500 %) | ok | ok | matches DocumentViewer (zoom steps 0.5 to 5) |
| Documents › details, kinds, learning, duplicates, voice notes, keep and notes, recognised text | fixed | fixed | Kinds section named the old button "Record the amount on this bill"; now Record it as this bill's statement (or Create a bill from this when none matches) |
| Documents › Filing: attach, record on a bill, new transaction, split by items, Itemize by hand, file without attaching, summary documents, EOB match, attached to | ok | fixed | help's filing list named the old button "Record the amount on this bill" |
| Documents › Other actions (Save a copy…, Delete) | ok | ok |  |
| Documents › Read with AI: button, window (hide, keep only, leave out page, 20 pages), after reading, statement reconcile, investments, custom fields, pay stub | ok | ok |  |
| Documents › permissions, privacy | ok | n.a. |  |
| Categories › Spending categories tab, form, tax treatment, archive, where used | ok | ok |  |
| Categories › Bill lists tab (Add a Home/Business category, Add a subcategory, Under the heading, Spending category of its payments, Hidden…, Restore as built in, Restore the built-in lists) | ok | ok | matches ReferenceScreens |
| Payees › screen, form, aliases, archive, where used, contacts | ok | ok |  |
| Category rules › screen, form (Description contains, Category, Also file under payee, Amount at least/at most), order, move | ok | fixed | help's Add a rule omitted Also file under payee |
| Institutions › screen, form, shared, contacts | ok | fixed | help said "Phone number" and "any notes"; labels are Phone and Notes |
| AI reading › turn on, settings, key, document types, usage | ok | ok | models and prices match AiProvider |
| AI reading › When a reading fails | fixed | n.a. | the "Send from 1 to 20 pages." failure (no page left, or more than 20) was not listed |
| Contacts › screen, list, filters, contact page, show number, linked records, link to a record, delete | ok | ok |  |
| Contacts › merge (pick, fields, groups, confirm) | ok | ok |  |
| Contacts › add or edit form (Label (office, cell…), masked numbers, Store in, Archived) | ok | ok |  |
| Contacts › on other screens, gather | ok | ok |  |
| Contacts › contacts from the phone (Add as a new contact…, Add the details to, Discard) | ok | added | help had no word on reviewing contacts sent from the phone |
| Contacts › French manual list | fixed | fixed | French ";" without the space before it in the contact-page list (8 lines) and in the contacts, documents and rules help topics (7 places) |

## Investing, reports and taxes

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Investments › account list | fixed | ok | column headings Account / Market value named |
| Investments › Quicken history banner | ok | n.a. |  |
| Investments › one account: figures, buttons | ok | ok |  |
| Investments › Holdings tab | ok | ok | columns already listed as fields |
| Investments › Transactions tab | fixed | ok | headings Date, Type, Security, Quantity, Amount named |
| Investments › Statements tab | fixed | ok | headings Statement date, Cash, Holdings, Status named |
| Investments › Add or edit a transaction (kinds, fields, register lines, delete) | ok | ok |  |
| Investments › Update prices | ok | ok |  |
| Investments › Import a brokerage statement, results | ok | ok |  |
| Investments › Check a statement, PDF statements and trade confirmations | ok | ok |  |
| Investments › Securities list, security dialog, fund mix, price history | fixed | n.a. | securities list headings Symbol, Name, Kind, Asset class, Price named |
| Investments › Capital gains and ACB (rules, gains, superficial losses, ACB today) | ok | ok |  |
| Investments › Crypto wallet: figures, buttons, record dialog, details, sync, link transfers | fixed | ok | wallet list headings Date, Description, Amount named |
| Investments › Import from an exchange | ok | ok |  |
| Investments › Precious metals: figures, item dialog, sale, documents | fixed | ok | Sold list headings Item, Sold on, Proceeds named |
| Investments › Investment reports, contacts | ok | n.a. |  |
| Registered plans › Contribution room tab, room cards, RRSP/TFSA/FHSA rules, over-contribution | fixed | ok | contributions list headings Date, Account, Amount named |
| Registered plans › Enter the CRA's figure, Contribution outside the books | ok | ok |  |
| Registered plans › RRIF and LIF tab, withdrawal cards, minimum/maximum | ok | ok |  |
| Registered plans › Plan details dialog | ok | ok |  |
| Registered plans › RESP tab, grants, record a grant | ok | ok |  |
| Registered plans › Pensions tab, pension dialog, pension statements | ok | ok |  |
| Registered plans › Beneficiaries tab, beneficiary dialog | fixed | ok | headings Kind, Beneficiary, Share (%) named |
| Registered plans › Reminders, Registered plans report | ok | n.a. |  |
| Loans › screen and loan list | fixed | ok | headings Loan, Balance named |
| Loans › summary, buttons, terms dialog, payment calculation | ok | ok |  |
| Loans › Record payment, Prepayment, Renew, Rate change, Payment change | ok | ok |  |
| Loans › Payment schedule (By year, Every payment, export) | ok | ok |  |
| Loans › Changes tab | fixed | ok | headings Date, Change, Details, Notes, Actions named |
| Loans › What if, renewal reminders, debt report, contacts | ok | ok |  |
| Reports › list of reports (18 kinds, order matches code), filter bar, choose accounts | ok | ok |  |
| Reports › charts and drill-down, table/export/print, missing rates | ok | ok |  |
| Reports › Save dialog, open/remove saved, scheduled reports | ok | fixed | help said a scheduled report is made "the first time the household is open"; the app also checks every hour, catches up a year back; removal asks first |
| Reports › Income and expense, by category, income by category, by payee | ok | ok |  |
| Reports › Custom report, Year in review | ok | ok |  |
| Reports › Net worth, portfolio, allocation, target allocation, investment income, enter a slip, registered plans, FX | ok | n.a. |  |
| Reports › Medical expenses, Quebec total, who claims, assets and warranties, maintenance | ok | n.a. |  |
| Reports › Cash flow forecast, Debt summary, Budget vs actual, Reconciliation status, permissions | ok | n.a. |  |
| Taxes › screen | ok | ok |  |
| Taxes › Slips tab, expected slips, slip window, add a slip | fixed | ok | headings Slip, From, Status named |
| Taxes › Donations tab, official receipt | fixed | ok | headings Date, Person, Charity or party, Amount named |
| Taxes › Instalments tab, instalment window, payments | fixed | ok | headings Due date, Status, Amount named |
| Taxes › Year-end package, sections, notes, accountant folder, export | fixed | fixed | headings Item, From, Line, Amount named; help now mentions the notes (slips still expected, volunteer hours and the 200-hour check) |
| Taxes › Estimate (figures, result, calculation, Quebec, minimum tax, refundable, carry-forwards, left out, rates) | ok | ok |  |
| Taxes › Through the year (pay stub, sales tax, tax treatment, investment slips, medical, plan contributions), notions | ok | n.a. | Business bills (BILL-20) already in the package section |

## Home: vehicles, trip log, home and assets (seasonal checklist), utilities

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Vehicles › screen at a glance (Vehicle, Show sold and retired, Add a vehicle) | ok | ok | - |
| Vehicles › Add or edit a vehicle (details, technical, purchase, registration and insurance, status and sale, store in, delete) | ok | ok | checked every field against VehiclesScreen.kt |
| Vehicles › Overview tab, odometer readings, Enter odometer | fixed | fixed | readings list column headings (Date, Odometer, Source, Actions) added; help now says fill-ups, services and trips add readings |
| Vehicles › Maintenance tab, usual tasks, task form, when due, record as done | fixed | ok | column headings (Task, Due, Actions) added |
| Vehicles › Service log tab, service form, also enter the payment | fixed | ok | column headings (Date, Odometer, Service, Cost, Actions) added |
| Vehicles › Fuel tab, fill-up or charge form, consumption | fixed | ok | column headings (Date, Odometer, Quantity, Details, Cost, Actions) added |
| Vehicles › Forecast tab, Suggest for the budget | fixed | added | budget suggestion headings (Category, Monthly, Current budget, "no budget yet") added; help had no Forecast section |
| Vehicles › Warranties tab, warranty form, warranty claims | fixed | fixed | stale: said a distance-only warranty gives no reminder; the code (VehicleService.renewals) reminds by the day the odometer should reach the limit; contradicted the tab's own intro. Headings (Warranty, Coverage, Actions) added |
| Vehicles › Costs tab | fixed | ok | named the "Insurance premiums (share, estimate)" line of the by-year table |
| Vehicles › Reminders and calendar, Contacts | ok | n.a. | - |
| Trip log › screen, summary cards | ok | ok | - |
| Trip log › list of trips (legs, breaks, addresses, photos and notes from the phone) | fixed | ok | column headings (Date, Trip, Distance, Actions) added; stops/breaks/addresses/attachments already described and match ExtrasScreens.kt |
| Trip log › Add a trip dialog (odometer warning, stops kept, province, towing) | ok | ok | matches TripDialog |
| Trip log › Trips from the phone | ok | ok | - |
| Trip log › Places, Add or edit a place | ok | added | help had no Places/Logbook/province part; added "Places and logbook" |
| Trip log › Logbook | ok | added | FR help called it "carnet de route"; the label is "Registre" (fixed) |
| Trip log › Medical travel, Add to medical expenses dialog | ok | ok | - |
| Home and assets › screen at a glance | ok | ok | - |
| Home and assets › Assets tab: asset list, asset form, find the purchase, value, depreciation, disposal | fixed | ok | list headings (Name, Value) added |
| Home and assets › Warranties and coverage, warranty form, warranty claims, photos | fixed | ok | headings (Coverage, Ends) added; claims match WarrantyDialog |
| Home and assets › Maintenance on an asset: usual tasks, task form, meter readings, service log, service form, cost of ownership | fixed | ok | headings for tasks (Task, Due, Actions) and service log (Date, Service, Cost, Actions) added |
| Home and assets › Maintenance tab | fixed | ok | headings (Task, Due, Actions) added |
| Home and assets › Seasonal checklist tab, tick a task, print, season dates | fixed | ok | headings (✓ read as Done, Task, When, Status) added |
| Home and assets › Projects tab, project form, project costs, rebates and grants, cost base | fixed | ok | headings for projects (Project, Spent, Actions) and costs (Date, Description, Amount, Actions) added |
| Home and assets › Contractors tab, contractor form, jobs, job contacts | fixed | ok | headings (Name, Rating, Jobs, Actions; Date, Job, Cost, Actions) added |
| Home and assets › Is it covered? tab | ok | ok | - |
| Home and assets › Insurance tab, policy form, renew, beneficiaries, claims, uninsured, life cover | fixed | ok | headings (Policy, Premium; Claim, Status; Item, Value) added |
| Home and assets › Reminders, related reports, contacts | ok | n.a. | - |
| Utilities › screen (tabs, Show archived, permissions) | ok | ok | - |
| Utilities › Meters: cards and months | fixed | ok | headings (Month, Used, Last year, Change, Cost) and "Two readings are needed" added |
| Utilities › Add a meter dialog | fixed | ok | said nothing of the bill form's Meter field: it is the same link (one bill per meter), and that bill's statements add readings |
| Utilities › Readings window | fixed | fixed | readings from bill statements (typed or read from a scanned bill, not twice on a day, kept when the statement is deleted) were missing; headings (Date, Reading, Actions) added |
| Utilities › Fuel tanks: cards, use and order date | ok | ok | - |
| Utilities › Add a tank dialog | fixed | ok | Edit the tank (opens the same dialog) was not named |
| Utilities › Levels window | ok | ok | - |
| Utilities › Deliveries window | fixed | ok | Add the delivery button, its permission (change, not just add), headings (Date, Delivery, Actions) and the delivery-day rule added |
| Utilities › On the phone | ok | ok | - |

## Home and family: health, medical claims, emergency and estate, pets, family money, side income, volunteer hours

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Health › overview (top buttons) | fixed | fixed | the Print health summary button at the top was not listed; help did not mention printing |
| Health › Person or pet, privacy groups | ok | ok |  |
| Health › Medications tab, medication form, Record a refill, refill reminders | ok | ok |  |
| Health › Appointments tab | ok | ok |  |
| Health › Conditions, Allergies, Tests, Vaccines tabs and forms | ok | ok |  |
| Health › Providers tab and form | ok | ok |  |
| Health › Health summary, Save as PDF dialog, Print | fixed | fixed | the printed copy was said to be deleted when the app closes; it is removed about 2 minutes after printing (10 after opening in a viewer), retried each minute, and at the latest when the app closes |
| Health › linked contacts | ok | n.a. |  |
| Medical claims › Expenses and claims tab (list, expense form, claims, send, payment, receipts, close) | ok | ok |  |
| Medical claims › From the books dialog | ok | ok |  |
| Medical claims › Plans tab, plan form (plan year, Deadline counted from, Days to send a claim), coverage order, coverage form, HSA, booklets, plan contacts | ok | ok | plan-year deadlines already described |
| Medical claims › Coverage left tab | ok | ok |  |
| Medical claims › tax credit, best period, which spouse, receipts PDF, Quebec total | ok | ok | Quebec total (line 381) already described |
| Emergency and estate › Emergency summary tab, contents | ok | fixed | help left out the health and dental plans section |
| Emergency and estate › Save as PDF dialog | ok | ok |  |
| Emergency and estate › Print | fixed | ok | same stale "deleted when the app closes" as Health |
| Emergency and estate › Papers and wishes tab (person, keep in, papers, wishes, people to call, save, scans) | ok | ok |  |
| Emergency and estate › linked contacts | fixed | n.a. | a typed person also linked from Contacts (same name) is listed once in the summary, from Contacts: not said |
| Pets › screen, pet card, pet form (details, licence, insurance, owner/status, photo and papers), former pets, delete | ok | ok |  |
| Pets › costs dialog (period: last five years or one year) | ok | fixed | help did not say Costs gives years and categories for five years or one year |
| Pets › health records, appointments, reminders, contacts | ok | ok |  |
| Family money › screen and intro | fixed | ok | intro left out chores |
| Family money › Shared expenses (groups, selected group, settle up, expense list, group dialog, expense dialog) | ok | ok |  |
| Family money › Family loans (list, loan dialog, repayments window) | ok | ok |  |
| Family money › Allowances (list, dialog, child's money; Mark paid also pays chores) | ok | ok |  |
| Family money › Chores (list, chore dialog, history; once a day, several a day, points) | fixed | ok | column headings (Chore, Earned, This week, Actions) added |
| Side income › screen, where kept | ok | ok |  |
| Side income › Invoices list | fixed | ok | column headings (Number, Customer, Status, Total, Actions) added |
| Side income › New invoice dialog, invoice PDF, Mark as paid dialog, delete with deposit | ok | ok | checked against InvoicePdf.kt and the dialog |
| Side income › Hours worked list | fixed | ok | only the last eight entries are shown; column headings (Date, Task, Time, Amount, Billed) added |
| Side income › Client dialog, Hours dialog | ok | ok |  |
| Side income › Make an invoice (one transaction) | fixed | fixed | Total before taxes line, the per-line details, the "every hour needs a rate" refusal and the "Invoice … made as a draft" message were missing; help said to "add the sales taxes" although they follow the person's last invoice |
| Side income › Rental properties (list, property dialog) | ok | ok |  |
| Volunteer hours › screen, one group rule, cards | ok | ok | group rule matches Volunteer.defaultGroup |
| Volunteer hours › list | fixed | ok | column headings (Date, Organization, Time) added |
| Volunteer hours › Add volunteer hours dialog, tax package, phone | ok | ok |  |

## Calendar, calendars from phones, phones, the phone app (every phone screen)

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Calendar › overview and item kinds | fixed | fixed | Renewals list lacked fuel orders for tanks (they are calendar renewals with an Open button); Maintenance did not say it includes the seasonal checklist's tasks (Show panel box "Maintenance and seasonal tasks") |
| Calendar › views and moving around | ok | ok |  |
| Calendar › Show and hide | ok | ok |  |
| Calendar › Agenda tab, marking | ok | ok |  |
| Calendar › Day, Week, Month, Year tabs | ok | ok |  |
| Calendar › Add or edit an appointment (what/when, where/who, activity fields, repeats, remind me, store in, delete) | ok | ok |  |
| Calendar › Drivers for one date dialog | ok | ok |  |
| Calendar › Record the cost dialog | ok | ok |  |
| Calendar › Go to a date picker | ok | ok |  |
| Calendar › Work and school schedules window, schedule form, exceptions | ok | ok |  |
| Calendar › Reminders, On the phone | fixed | n.a. | said the phone gets work/school hours for today and tomorrow only; it now gets 60 days (agenda), shows them in the agenda, and writes them into a phone calendar with Both ways |
| Calendar › Contacts | ok | n.a. |  |
| Calendars from phones › how it works, both ways, who sees what | ok | ok |  |
| Calendars from phones › In the Calendar | fixed | ok | Day and Week views (items at their hours, All day line, no form on click) were not described |
| Calendars from phones › Phone calendars dialog | ok | ok |  |
| Calendars from phones › Import an .ics file dialog | fixed | ok | result line also counts single dates copied; list capped at 30 with "And … more."; Close |
| Phones › intro, how it works | fixed | fixed | listed only receipts, bills, documents, quick expenses and odometer readings; phones now also send trips, fill-ups, places, meter/tank readings, hours, chores, volunteer hours, seasonal ticks, contacts and calendars |
| Phones › listener, pairing window, owner | ok | ok |  |
| Phones › list of phones, phone dialog (Store in) | fixed | fixed | Store in also holds trips, places and log entries, seen by everyone who can open the group (as the dialog's hint says) |
| Phones › Away from home, watching, import messages | ok | ok |  |
| Phones › What happens to what a phone sends | fixed | n.a. | added trips (and their notes/photos), fill-ups, places, log entries, seasonal ticks, calendars, refusals shown on the phone |
| Phones › What the phone receives | fixed | added | summary contents stale (no events, schedules, refills, renewals, fuel orders, checklist, tracker lists, places, trailers; maintenance is 60 days, not this month); agenda mentioned. Help topic gained a short section. |
| Help › Sending from the phone (phone-transfer) | n.a. | fixed | said all captures wait in Documents; readings, trips, fill-ups, log entries and ticks are recorded straight away; stray voice-note line moved under Review |
| Phone › lock, choose PIN, unlock, forgot PIN | ok | n.a. |  |
| Phone › first start | ok | n.a. |  |
| Phone › main tabs | fixed | n.a. | Settings tab description listed only pairing, folder, lock and updates |
| Phone › pairing screen | ok | n.a. |  |
| Phone › Capture tab | fixed | n.a. | Stations nearby button missing from the list; where the app goes after saving or closing a form |
| Phone › scanning, sharing, several files | ok | n.a. |  |
| Phone › capture form, voice notes, quick expense | ok | n.a. |  |
| Phone › Odometer or hours | ok | n.a. |  |
| Phone › Seasonal checklist | ok | n.a. |  |
| Phone › Log forms (meter/tank, hours, chores, volunteer) | fixed | n.a. | tank's last level shown; empty-list messages; missing blank line before the Trip heading |
| Phone › Trip: start, where you are, under way, stop, notes and photos, arrive, places, location | ok | n.a. |  |
| Phone › Fuel or charge | ok | n.a. | French: space before ; added |
| Phone › Stations nearby, add a station by hand | ok | n.a. |  |
| Phone › Sent tab (send now, share as file, list, when it sends) | ok | n.a. |  |
| Phone › Summary tab | fixed | n.a. | column headings (Account/Balance, Due date and bill/Amount, Category/Spent of budget) not described |
| Phone › Agenda (day by day, month, phone calendars) | fixed | n.a. | message before the first transfer not described |
| Phone › Contacts tab, contact page, new contact | ok | n.a. |  |
| Phone › Notifications, lock screen | ok | n.a. |  |
| Phone › Settings: unpair, away from home, reminders on the minute | ok | n.a. |  |
| Phone › Settings: calendars on this phone, both ways | ok | n.a. |  |
| Phone › Settings: trips lookups, language, change PIN, biometric, ask for the PIN again | fixed | n.a. | sections were out of the screen's order and the Change PIN heading was empty (its text sat under Language); reordered as the screen shows them; French "téléphone;" spacing |
| Phone › Settings: updates | fixed | n.a. | Check now shown only while checks are on; the "checked, confirm the install" status not described |
| Phone › Settings: about, privacy policy | fixed | n.a. | policy opens in the app's language (follows the app language switch), not the phone's |
| Getting started with the phone app | fixed | n.a. | PIN relock time now configurable; app language; log buttons (seasonal, meter, hours, chores, volunteer, trip, fuel, stations) not mentioned; review step said only odometer readings skip review; summary step lacked Coming up and the agenda |

## Settings: household members, users, rates and prices, rates and rules, backups, security, display

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Household members › screen layout | fixed | fixed | the Household name field and Rename button (administrators) were not described anywhere |
| Household members › Household name | added (members#household-name) | fixed | new subsection: what the name is used for (phones' summary, invoices made out by no particular person), Rename greyed while empty or unchanged, logged, folder keeps its name |
| Household members › Province or territory | ok | ok |  |
| Household members › list of people | ok | ok | no column headings on this list |
| Household members › person form (name, relationship, date of birth, lives in, archived, schedules) | ok | ok (French ; spacing fixed) |  |
| Users › top buttons | ok | ok |  |
| Users › Users tab | ok | ok |  |
| Users › Access tab | fixed | ok | heading row (Account group, then each user) not described; greyed buttons already described |
| Users › Activity tab | fixed | ok (French ; spacing fixed) | column headings When, Whose activity, Action, Where not described |
| Users › Add a user / recovery key | ok | ok | password rules hint described |
| Users › Edit user | ok | ok |  |
| Users › Change my password | ok | ok |  |
| Rates and prices › rates in use | fixed | ok | column headings Currency, Rate, Date and source, Actions |
| Rates and prices › follow other currencies / second source / enter a rate | ok | ok (French ; spacing fixed) |  |
| Rates and prices › market prices / crypto-assets held | fixed | ok | column headings Currency, Price, CoinGecko name, Actions |
| Rates and prices › precious metals | fixed | ok | column headings Metal, Spot price |
| Rates and prices › recent rates | fixed | ok | column headings Date, Rate, Source, Actions |
| Rates and rules › list, filter, details, types | ok | ok (French ; spacing fixed) | built-in values with their official source described |
| Rates and rules › In effect today | fixed | ok | column headings (Province or territory,) Value, Since |
| Rates and rules › History of values | fixed | ok | column headings Effective from, (Province or territory,) Value, Actions (administrator) |
| Rates and rules › Add a value / value editor / examples | ok | ok | household value added by an administrator, delete asks first: matches the code |
| Backups › defaults / settings / Back up now | ok | ok (French ; spacing fixed) | 20 h / 7 days, keep 1-365 default 10, 30-minute look: match BackupService |
| Backups › Backups in the folder | fixed | ok | column headings When, File, Size, Actions |
| Backups › restore / export | ok | ok |  |
| Security › Lock after inactivity | ok | ok | choices 1, 5, 10, 15, 30, 60 minutes or Never match the code |
| Security › recovery reminder | ok | ok |  |
| Security › Password rules card | ok (French ; spacing fixed twice) | ok |  |
| Security › unlock / reset with recovery key | ok | ok |  |
| Display and accessibility › colours, text size, keyboard, notifications, Getting started guide, menu | ok (parent fixed "five steps") | ok (parent added guide part) |  |

## Getting started, finding your way, privacy, shortcuts, troubleshooting, glossary, Help menu, About; text fixes

| Screen › part | Manual | Help | Notes |
|---|---|---|---|
| Welcome › Opening the manual | fixed | n.a. | the Manual button is labelled Manual (Shift+F1) and opens the screen's chapter, like Shift+F1; Manual in the Help menu and Open the manual in the Help panel were not described |
| Welcome › The Help panel | fixed | n.a. | Open the manual button missing |
| Welcome › other sections | ok | n.a. |  |
| Basics › start screens, update question, top bar | fixed | n.a. | Manual (Shift+F1) button label and what it opens |
| Basics › The menu, Menu groups and screens | fixed | fixed | said five groups (Help is the sixth); Utilities, Volunteer hours and Rates and rules were missing from the list of screens; help getting-started listed five groups |
| Basics › Lists and their columns | added | n.a. | new section: column headings on every list, the Actions column, read as headings, greyed buttons for view-only groups |
| Basics › searching, locking, messages, controls, reminders, background work, computer settings | ok | ok |  |
| Quick start › Before you begin | fixed | n.a. | a receipt or bill added to what to have at hand |
| Quick start › File a first receipt | added | ok | the Getting started guide's fourth step (A first receipt) had no step in the quick start; later steps renumbered (ids kept) |
| Quick start › Pair your phone | fixed | n.a. | now says what else the phone does (trips, readings, logs, checklist, agenda) |
| Quick start › other steps | ok | ok |  |
| Getting started (help topic) | n.a. | fixed | Show the guide again, the Walk-Me guides button and the Help group were missing |
| Start: documents › File each document | fixed | n.a. | named the old button Record the amount on this bill; now Record it as this bill's statement and Create a bill from this |
| Start: bills and budgets › Add your bills, Pay bills | fixed | n.a. | bills created from a captured bill (Create a bill from this) and recording a bill's statement from Documents |
| Start: calendar › On your phone | added | n.a. | the phone agenda and the phone's calendars (Bring in only, Both ways) were not mentioned |
| Start: home and family › intro, Home and assets, Utilities, Volunteer hours, Family money | added | n.a. | utilities, the seasonal checklist, volunteer hours and chores had no getting-started steps |
| Start: settings, contacts, money, investing, reports, taxes | ok | n.a. | forks found no flow change |
| Start: phone | fixed | n.a. | see fork E (PIN timing, language, other buttons, review step) |
| Privacy and data › On the phone | fixed | n.a. | the list of what goes to the phone was stale (calendar items, hours, refills, checklist, places, trailers, renewals, log lists) and said no health records go (medication names with a refill near and appointments do) |
| Privacy and data › Phone transfers | fixed | n.a. | "Nothing goes through the internet" was too broad; now nothing of the household |
| Privacy and data › The phone's calendars and location | added | fixed | calendar reading/writing, location, address lookup (Google geocoder) and stations nearby (OpenStreetMap Overpass) were not in the manual's list of what leaves; privacy help had no phone lookups |
| Privacy and data › Update checks | fixed | fixed | the phone app from GitHub checks too; Google Play copies do not |
| Shortcuts › In the Manual window | added | fixed | Alt+Left/Alt+Right and Ctrl+F in the manual window were undocumented |
| Shortcuts › In a document's pages | added | fixed | Page Up/Down, Ctrl+plus/minus/0 and Ctrl+wheel in the document viewer were undocumented |
| Shortcuts › Quick reference | fixed | fixed | the new keys added; help lacked Shift+F1 and contacts in search results |
| Troubleshooting › The phone's calendars do not come in | added | n.a. | new |
| Troubleshooting › Trips: no position, address or stations | added | n.a. | new, with the phone's messages |
| Troubleshooting › other sections | ok | n.a. |  |
| Glossary | fixed | n.a. | 13 terms added (bill statement, instalments on set dates, bill lists, business bill, learned store, brought-in calendar, both ways, phone agenda, leg, work share, plan year, Health Spending Account, cost base) |
| Walk-Me guides, About | ok | ok | written with the Help menu (HLP-01 to HLP-04) this morning; checked against WalkMePanel, AboutScreen and Updater |
| Display › Getting started guide | fixed | fixed | said the guide stays until its first four steps are done (five: all but the phone); help lacked the Show again button and Shift+F1 |
| Calendar › renewal line of a fuel order | ok | ok | code fix: its Open utilities button showed a raw key (calendar.open.UTILITIES was missing); the test now derives the list from the renewal kinds |
| Utilities › Deliveries window | fixed | ok | code fix: the account picker was labelled Paid into (the pay stub's label); now Paid from, and the manual names it |
| Side income › Make an invoice hint | ok | ok | text fix: the on-screen hint said to add the sales taxes; they are copied from the person's last invoice |
| French texts (all chapters, help, Walk-Me, labels) | fixed | fixed | 1,909 straight apostrophes in the French manual, help and Walk-Me and 69 in the French labels made typographic; the app name kept as RANN's everywhere on the desktop (74 RANN’s made straight); missing spaces before ; in French help topics and three phone texts |

## Left as they are

- About 140 French on-screen messages have no space before ";" (the manual quotes three of them as they appear). Changing them is a separate pass over the interface texts.
- The French rates-and-rules examples "0,0528; 0,0540" show what to type in the field and keep their form.
- Picture candidates, phone only: the agenda (day by day and month) and the Settings tab.
