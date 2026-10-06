# Exit check: calendars, seasons, trips and trackers (2026-10-06)

Date: 2026-10-06. This is the exit check, requirements and technical, of the features built on 2026-10-06 after the Phase 5 exit check: CAL-07 to CAL-11, CSY-01 to CSY-05, SEA-01 to SEA-05, TRP-01 to TRP-10, UTL-01, UTL-02, HRS-01, CHO-01 and VOL-01 (`requirements-additions.md`; the decisions log rows of 2026-10-06 in `development-plan.md`). It was checked against the code on branch `worktree-agent-a329eb4bd9d24961d`, from main at 719fb7e.

For each requirement, the check covered the screens, the storage, access and private groups (HH-11), permissions, the English and French texts, the manual, help and the phone. Each one is marked as follows:

- **Met**: built, with texts and manual.
- **Fixed**: a gap found during this check and fixed on this branch. Changes in the books come with tests, and the manual is updated in English and French.
- **Gap**: left open, with the reason.

A security review of the same features runs in parallel. Security fixes are left to it; what this check noticed is listed at the end.

Full build: 855 tests, 0 failures, 1 skipped (848 before this check, main at 719fb7e; main was merged at ae26205 before the final build).

## Calendar views, schedules and activities

- CAL-07 (year, month, week and day views, with today, previous, next and a date picker): Met.
- CAL-08 (show and hide each kind and person, remembered per user on that computer): Met.
  - Fixed: items brought in from a phone could not be hidden by person. They now belong to the household member their owner's user is linked to, under Users.
  - Not changed: events copied from an .ics file become ordinary events, so they come under **Appointments and events**, not **Brought-in calendars**. The manual says so (a one-time copy).
- CAL-09 (schedules: weekly or rotations, exceptions, bank holidays): Met. Rotations go up to 8 weeks.
  - Fixed: the time fields' error was the hard-coded "HH:MM". It now reads "Enter the time as HH:MM, for example 09:30." in the user's language.
- CAL-10 (a bar per person; week and day views shade the hours; today's and tomorrow's on the phone): Met.
- CAL-11 (children's activities: drivers each way, cost that can become a transaction): Met on the desktop. Fixed:
  - **Drivers on the phone.** Who drives there and who drives back now reach the phone's Coming up list ("drives there: Perry · drives back: Sophie's mom"). The day's own carpool turn wins over the series' drivers. `RefEvent` has two optional fields, and the reference data's format is 5, so a phone that kept its copy with an older app asks for all of it again (PhoneEventsTest).
  - **"cost recorded" after a deletion.** Deleting the cost's transaction left "cost recorded" on the date for good, and the cost could never be recorded again. Deleting the transaction now clears the link in every group the user may change, so the date offers **Record the cost** again.
  - **Recording twice.** Only the screen prevented it before. A cost whose transaction is still there is now refused by the books too, with "The cost of this date is already recorded…" (SchedulesActivitiesTest).
  - **Manual:** updated in both languages (calendar, phone app).

## Calendars from each person's own accounts

- CSY-01 (the phone reads the calendars Android syncs, with permission; the user picks): Met. READ_CALENDAR is asked only when the feature is turned on, after an explanation, and the app never signs in.
- CSY-02 (upcoming items sent encrypted, updated or removed): Met. Each chosen calendar is sent again once a day even when unchanged, since its window moves on; that is by design.
- CSY-03 (private, busy only or shared, per calendar and per item, private by default): Met.
  - A busy-only item reaches the group for others as a time range only.
  - The calendar's name reaches that group only when the whole calendar is shared.
  - See the observations for past items left in the shared group after a change.
- CSY-04 (read-only, marked with the source, reminders left to the source): Met.
  - Fixed in the manual: brought-in items show in every view (Day and Week too), not only Agenda and Month.
- CSY-05 (.ics import as a one-time copy): Met.
  - Fixed: a file over the size limit gave "Too large" in English through the generic error. It now gives "The calendar file is larger than 5 MB." in the user's language.
  - Fixed: the import was not one database transaction, so a failure halfway left the events copied so far. It is now all or nothing.

## Seasonal checklists

- SEA-01 (pools and yards, fuller seasonal starter tasks): Met.
  - Gap (content): there is no lawn-care task of its own (first mowing, fertilizing, aerating); mower service and yard clean-up stand for it. Wiper blades are scheduled in the fall only.
  - Starter tasks are a starting point and the user adds their own, so this is left for the owner to decide.
- SEA-02 (a checklist per season across vehicles and assets; ticking records the service): Met. Fixed: a task ticked twice on the same date, on the computer and on a phone or on two phones, gave two services. It is now recorded once (SeasonalChecklistTest). Not changed:
  - A weekly task shows done for the whole season after one tick.
  - The desktop accepts a date ahead (the phone, up to tomorrow).
  - The start of a season is not announced on the dashboard; the due tasks are, as maintenance.
- SEA-03 (season dates in Rates and rules, Quebec's winter tire dates kept): Met. Start dates entered out of order are sorted silently; the four defaults are in order.
- SEA-04 (the checklist on the phone, ticks reach the desktop): Met. Fixed: a tick the computer refused (a task deleted since, or an asset the user may only view) stayed "Done, waiting to be sent" for good and counted in the progress. Only ticks still on their way count now; a refused one shows as to do again, with its reason on the Sent tab.
- SEA-05 (energy upgrades with rebates and grants, effect on the cost base): Met.

## Trips and vehicles on the phone

- TRP-01 (start and arrive on the phone, one location fix at each end, no background tracking): Met. Only fine and coarse location are declared, asked when the user taps. The arrival time is when **Arrive** is pressed; the desktop can correct it.
- TRP-02 (saved places matched on the device, never sent to a map service): Met. Places from the phone get a 150 m radius and no address; both can be changed on the desktop.
- TRP-03 (distance from the odometers, time from the two times, purpose suggested and confirmed): Met.
- TRP-04 (towing, heavy load, passengers): Met.
- TRP-05 (fuel and charging on the phone, consumption apart for normal, towing and heavy): Met. The station is chosen among saved places; a new one is saved from the Places list or a trip.
- Fill-ups from the phone and their payment (decided: left as is, with the manual saying so). They go to the Fuel tab without a payment, as before.
  - Fixed: such a line now says "no payment entered yet". The manual (phone app, vehicles) explains how to add it with **Edit**, then **Also enter the payment in an account**.
  - The manual also warns that a payment imported from a card statement and linked to the vehicle would count the cost twice.
  - Why no payment on the phone: a statement import would then enter the same payment again, with no review step in between.
- TRP-06 (trips feed the trip log, the odometer, the CRA business-use log and medical travel): Met. Gap: a trip's starting odometer is not checked against the vehicle's last reading, and neither is a fill-up's.
  - A trip logged on the wrong vehicle, or a typing slip, adds a reading that the latest odometer (the highest reading) and the forecast's pace then follow.
  - A warning on the phone and the desktop when the start is below the last reading, or far above it, is a small change. It needs a decision on how strict it should be.
- TRP-07 (more vehicle details): Met.
- TRP-08 (forecast and Transport budget): Fixed. A plug-in hybrid's forecast counted fuel only. It now adds its charging:
  - kWh/100 km from one full charge to the next. When charges have no odometer, as home charging often has none: every kWh charged over the last year divided by the distance the readings show.
  - The recent price of a kWh.
  - A kWh column and an electricity column in the table, included in the total.
  - The electricity goes to the EV charging budget line.
  - Fixed with it: the Costs tab put a plug-in hybrid's charges without a payment under Fuel; each fill-up now goes by what it bought (TripsOnPhoneTest).
  - The manual is updated in both languages.
- TRP-09 (CRA logbook, km per province or state, inspection and CVOR or NSC reminders): Met.
- TRP-10 (EV charging at home or in public, cost per km against fuel): Met.

Kilometres in the vehicle-use and per-province lines of the Trip log now follow the language's number format, so French gets a decimal comma. The logbook table keeps plain numbers, since it is also the CSV export.

## Other trackers

- UTL-01 (meters, use per month against last year, unusual use flagged): Met.
  - The flag shows on the Utilities screen only, not among the dashboard reminders or on the phone. The requirement asks for the flag, not a reminder; left as is.
  - Fixed: the change against last year ("+35.2 %") used a decimal point in French.
- UTL-02 (tank levels and deliveries, use per month, a reminder to order): Met. Fixed: with only one reading, or before any use was measured, a tank already at or below its order level never reminded. A reading at or below the order level now reminds at once (UsageTest, manual). Gap: the order reminder is not on the phone, whose reference data has no renewals; the phone shows the tank's last level. Medium (a new list in the reference data, a notification); not asked by the requirement.
- HRS-01 (hours per client and task, timed on the phone or entered, unbilled hours to invoice lines): Met. The invoice and marking the hours billed are two steps; a failure between them would leave the hours unbilled with the invoice made. Low risk (both in the same ledger, no outside call between).
- CHO-01 (chores per child, ticked on the phone, feeding the allowance): Met. Fixed: paying chores added the allowance entry first and then marked each chore paid. A chore in a group the payer may not change stopped the marking halfway, so the next **Pay** paid again. The books now check every chore can be marked before anything is entered (TrackersTest). Not changed: the same chore ticked twice the same day earns twice (a chore can be done twice a day; the phone shows "done that day").
- VOL-01 (volunteer hours, the 200-hour threshold, a yearly total): Met. Observation: on the desktop, new hours go by default to the first shared group, while hours from the phone go to the phone's group (the user's private one for a member). Personal volunteer hours are not sensitive, but the two defaults differ.

## Across the features

- English and French: the message files have 5,027 keys in each language with the same keys (MessageKeysTest, MessagesTest), and Android's `values` and `values-fr` have the same 391 strings. The manual and help chapters have the same sections (ManualTest, HelpTopicsTest).
- Hard-coded English: the two found ("Too large", "HH:MM") are fixed. What remains are unit symbols (L, kWh, km), which French writes the same way, and the box mark of the printed checklist.
- Viewers: the books refuse every change a viewer makes (role checks in each service). The new screens do not grey out their buttons for viewers, so a viewer learns it from the error.
- Phone: everything the phone receives comes from the groups its user can see. The computer refuses a phone whose owner is not the signed-in user.

## Technical

### Upgrades (NFR-11)

`UpgradeTest` reads the versions from the schema itself (`1 until LedgerDatabase.Schema.version`), so ledger 30 to 31 is covered with no change. It rebuilds the filled household at ledger 30 from its snapshot (`schemas/30.db`), upgrades it, and compares the data and the schema with a new database: all ledger versions 1 to 30 and core versions 1 to 7 pass. `MigrationTest` checks the tables version 31 adds.

### Performance (NFR-02)

**How it was measured.** `./gradlew :core:books:performanceTest` has a new test, `calendars, trips and trackers`. It adds the round's data to the 30-year household of the Phase 5 check (250,000 transactions, 50,000 documents, 2,000 contacts):

- **Calendar:** a year of calendar with 30 repeating events, 10 weekly activities with carpool turns every other week, and four people on two-week rotating schedules with 25 exceptions each. A phone calendar of 1,500 items is brought in (private, busy-only and shared), which also gives the user a private group.
- **Utilities:** ten years of weekly readings on three meters, and a propane tank read monthly with deliveries.
- **Trips:** one vehicle with 5,000 trips over ten years, between saved places, with towing and heavy loads, and a fill-up about every 600 km.
- **Seasonal checklist:** five homes or cottages, each with a pool, a yard and a trailer, and five more vehicles, all with their starter tasks.

The limits are the same: a screen under 1 second, a report or a whole-year view under 3 seconds. Other builds were running on the machine at the same time (±30 %).

**Found and fixed:**

- **Calendar, 0.8–1.2 s for a week or a month, at or over the limit.** The fixed cost was the card payment dates. The calendar asked for every renewal and then threw away the cards' payment dates, which had worked out every account's balance, then worked them all out again for its own period. The cards' annual fees are now asked for apart (`CreditCardService.annualFees`), and the payment dates once. The calendar now takes 0.6 s; the rest is mostly the balances that the payment dates need (about 0.2 s) and the loans' renewals (about 0.2 s).
- **Seasonal checklist, 1.8–2.1 s, over the limit.** Each asset was looked up three times through the list of every asset, and each vehicle twice, for its tasks, its readings and its log. The checklist, the calendar's maintenance and the dashboard's upkeep now read each asset and vehicle once, from the group they are in (`AssetService.locate` by id, and internal `taskStatuses` and `services` that take the group). The checklist now takes 0.5 s.
- No index was needed: trips, readings, deliveries and brought-in items already have theirs (ledger 31). No schema change.

**Timings after the fixes (ms):**

| Measurement | Limit | Time |
| --- | --- | --- |
| Calendar, year view (all kinds, one year) | 3,000 | 741 |
| Calendar, month view | 1,000 | 623 |
| Calendar, week view | 1,000 | 627 |
| Calendar reminders | 1,000 | 22 |
| Utilities, meters with ten years of readings | 1,000 | 19 |
| Utilities, use by month of every meter | 1,000 | 2 |
| Utilities, add a reading | 1,000 | 51 |
| Utilities, tank status and order reminder | 1,000 | 24 |
| Trip log, one year | 1,000 | 12 |
| Trip log, kilometres by province | 1,000 | 12 |
| CRA logbook of a year | 1,000 | 155 |
| Vehicle odometer readings with 5,000 trips | 1,000 | 57 |
| Fuel by kind of driving, ten years | 3,000 | 72 |
| Vehicle forecast | 1,000 | 281 |
| Transport budget suggestions | 1,000 | 295 |
| Seasonal checklist (25 assets, 6 vehicles) | 1,000 | 475 |
| What the phone receives (reference data) | 3,000 | 2,045 |

Every earlier measurement still passes in the same run. Some examples: dashboard data 293, account list 192, year in review 2,640, the 30-year custom reports 1,177 and 1,243.

What the phone receives is not a screen: it is built in the background on each transfer. At 2 s on this household it is within the limit, but it is the slowest of the round. It adds up the balances of every account, the month's budget, 2,000 contacts, the coming events and the seasonal checklist. Worth watching if households grow larger.

### Schema

No schema change. Two queries were added to `EventActivities.sq` (the cost link of a date, and clearing a deleted transaction's link); the ledger stays at version 31.

## Security observations (for the security review; not changed here)

- **Trips are kept in the trip's own group, not the vehicle's.** `TripService.store` checks the permission on the group the trip is saved in: the phone's group, or the desktop's working group. It does not check the vehicle's group. Two consequences:
  - A user who may only view a vehicle's group can add trips to that vehicle, and their odometers become readings of that vehicle.
  - An administrator's phone sends to the shared group, so trips of a vehicle in their private group land in the shared group, with places and times.
  - Fill-ups, by contrast, check the vehicle's group.
  - A fix is about five lines: store the trip in the vehicle's group, or require capture on it.
- Brought-in calendars (CSY-03): when a shared calendar is switched to private or busy only, items from today on are replaced, but past shared items (kept up to a year) and the calendar's name stay readable in the group for others until they age out. Busy-only rows there keep the phone's event id, colour and device id (no title or place).
- Places: a phone with capture rights can rename any place of its group. A rename of a place deleted on the desktop brings it back.
- The phone offers seasonal ticks for assets its user may only view (the computer refuses them, with the reason now shown).
- A fill-up and a seasonal tick from the phone are saved before their sync record, not in one database transaction. A crash between the two would record them again when the phone resends. Trips are safe (they keep the phone's id).
- The fuel form's **Save** stays enabled while saving, so a double tap saves two fill-ups.
