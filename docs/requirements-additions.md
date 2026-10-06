# Requirements added after the SRS

Requests from the owner after Phase 1 (2026-10-02). They use the SRS's priority definitions and
are delivered before Phase 2.

## Exchange rates

| ID | Requirement | Priority |
| --- | --- | --- |
| FX-07 | The user can follow currencies that no account uses (for travel, comparisons); their daily rates are downloaded too. | Should |
| FX-08 | An optional second rate source, off by default, for currencies the Bank of Canada does not publish (about 160 currencies, today's rate only), with the source's attribution shown. Bank of Canada rates are always preferred, and manual rates are never replaced. | Could |

## Calendar and events

| ID | Requirement | Priority |
| --- | --- | --- |
| CAL-01 | Appointments and events of any kind (medical, bank, garage, home, personal, other): title, date, start time or all day, duration, location, notes. | Should |
| CAL-02 | Events can repeat (same patterns as bills, BILL-02) and can be linked to a household member, an account and a health provider. | Should |
| CAL-03 | Reminders at chosen lead times (for example 1 day and 1 hour before), on the desktop now and on the phone in Phase 2. | Should |
| CAL-04 | One calendar showing events, bills, medication refills and health follow-ups, by month and as an agenda. | Should |
| CAL-05 | An occurrence can be marked done or cancelled without changing the series. | Could |
| CAL-06 | Events and health records are stored in an account group chosen by the user, so private ones are encrypted from other household users (HH-11). | Should |

## Health records

| ID | Requirement | Priority |
| --- | --- | --- |
| HLT-01 | Per household member: medications with dose, instructions, prescriber, pharmacy, prescription number, start and end dates. | Should |
| HLT-02 | Refills: last fill date, days of supply and refills remaining give the next refill date; recording a refill updates them. | Should |
| HLT-03 | Refill reminders a chosen number of days before the next refill, and a prompt to renew when no refills remain. | Should |
| HLT-04 | Conditions (diagnosed date, status: active, managed, resolved), allergies (reaction, severity) and immunizations (date, next dose due). | Should |
| HLT-05 | Medical tests with date, result, units, reference range and a follow-up date that appears in the calendar. | Should |
| HLT-06 | Medical appointments are calendar events (CAL-01) linked to the person and the provider. | Should |
| HLT-07 | A directory of health providers: doctors, dentists, pharmacies, clinics, labs, specialists. | Should |
| HLT-08 | Health records will link to medical expenses and claims (MED-06 to MED-10) when those are built in Phase 4. | Could |
| HLT-09 | A printable health summary per person (medications, conditions, allergies) for appointments and emergencies (EST-01). | Could |

Health information is an organizational aid, not medical advice (as TAX-04 for tax figures).

## Savings goals in one account

Brings forward BUD-02 (sinking funds) and BUD-03 (savings goals) from Phase 5.

| ID | Requirement | Priority |
| --- | --- | --- |
| GOAL-01 | Any number of goals per account, each with a name, target amount, optional target date and notes. | Should |
| GOAL-02 | A planned set-aside per goal (amount and schedule, such as each pay or monthly), posted automatically on schedule or entered by hand. Set-asides earmark part of the balance; no money moves. | Should |
| GOAL-03 | The account shows its balance divided into goals and an unassigned remainder, with a warning when the goals add up to more than the balance. | Should |
| GOAL-04 | Progress per goal: amount set aside, percentage, amount still needed, the set-aside needed per period to reach the target date, and whether the goal is on track. | Should |
| GOAL-05 | Spending from a goal (optionally linked to the purchase), moving money between goals, and marking a goal reached or archived. | Should |
| GOAL-06 | A history of each goal's set-asides, spending and moves. | Could |

## Pets

| ID | Requirement | Priority |
| --- | --- | --- |
| PET-01 | Pet records: name, species, breed, sex, date of birth, colour, microchip number, spayed or neutered, photo (Phase 2 vault), notes, archived when no longer in the household. | Should |
| PET-02 | Municipal licence: number, issuing municipality, expiry date, with a renewal reminder; pet insurance: insurer, policy number, renewal date. | Should |
| PET-03 | Pets have the same health records as people (HLT-01 to HLT-05): vaccines with next dose due, medications and refills, conditions, allergies, tests. | Should |
| PET-04 | Veterinarians, groomers and kennels in the provider directory (HLT-07); vet, grooming and boarding appointments in the calendar (CAL-01). | Should |
| PET-05 | Transactions can be recorded for a pet, as for a person; cost per pet per year by category (food, vet, grooming, licence, insurance, boarding, supplies). | Should |

## Vehicles

The vehicle part of section 11 (AST, WAR, INS, MNT), brought forward from Phase 4.

| ID | Requirement | Priority |
| --- | --- | --- |
| VEH-01 | Vehicle records: name, make, model, year, trim, colour, VIN, licence plate, fuel type, main driver, purchase date, price, seller and odometer; status active, sold or retired, with the disposal date and price (AST-01, AST-05). | Should |
| VEH-02 | Registration (SAAQ) renewal date and insurance (insurer, policy number, renewal date) with reminders (INS-03). | Should |
| VEH-03 | Warranties per vehicle: kind, provider, end date and kilometre limit, with an expiry reminder 60 days before (WAR-01, WAR-02). | Should |
| VEH-04 | Odometer readings entered directly or taken from fuel and service entries; the average distance per day is used to forecast kilometre-based maintenance (MNT-03). | Should |
| VEH-05 | Maintenance tasks scheduled by time, by distance or both, from starter templates (oil, tire rotation, seasonal tire swap, brakes, air filters, inspection) or custom (MNT-01, MNT-02). | Should |
| VEH-06 | Service log: date, odometer, tasks done, provider or do-it-yourself, cost, notes; completing a task restarts its schedule (MNT-04). | Should |
| VEH-07 | Fuel and charging log: date, odometer, litres or kWh, cost, full tank; consumption in L/100 km (or kWh/100 km) and cost per km (MNT-07). | Should |
| VEH-08 | Fuel and service entries can create the matching transaction in an account, linked to the vehicle. | Should |
| VEH-09 | Any transaction can be linked to a vehicle. | Should |
| VEH-10 | Cost of ownership per vehicle and year by category, and cost per km (MNT-06). Logbook entries without a linked transaction are included so nothing is missed or counted twice. | Should |
| VEH-11 | Due maintenance, renewals and warranty expiries appear in the reminders and the calendar (MNT-05). | Should |

## Maintenance for every asset

Details of section 11's maintenance requirements (MNT-01 to MNT-06) as built in Phase 4c
(2026-10-03), beyond what the SRS states.

| ID | Requirement | Priority |
| --- | --- | --- |
| MNT-10 | An asset other than a vehicle may have a meter that counts engine hours or kilometres (an RV); its tasks can then repeat by use as well as by months, whichever comes first. | Should |
| MNT-11 | Seasonal tasks (spring opening, winterization, tire swaps) come back every year on their date. | Should |
| MNT-12 | The phone's meter form lists metered assets beside vehicles, in their own unit; the reading goes straight to the asset (MNT-03). | Should |
| MNT-13 | Cost of ownership (MNT-06) adds each asset's share of the premiums of the policies that name it, split evenly between the things a policy covers; the purchase price is shown apart, not as a running cost. | Could |
| MNT-14 | Maintenance due this month or overdue, on vehicles and other assets, is one list: on the desktop, in the reminders and calendar, and on the phone with a notification when a task becomes due soon or due (MNT-05). | Should |

## Categories

| ID | Requirement | Priority |
| --- | --- | --- |
| CAT-06 | Public transit is split into Transit passes and Fares and tickets; Pets gains Licence, Insurance and Boarding. Existing households receive the new categories when upgraded. | Should |

## Provinces and territories

Requested on 2026-10-02, during Phase 3: full support for every province and territory, not only
Quebec. Built before Phase 3d.

| ID | Requirement | Priority |
| --- | --- | --- |
| PROV-01 | The household has a province or territory, chosen when it is created (existing households are in Quebec) and changeable later; a person who lives or files elsewhere can have their own. | Must |
| PROV-02 | Bank holidays follow the province or territory for "last business day" bills: Family Day and its equivalents, the Civic Holiday, Fête nationale in Quebec, the territorial days. | Must |
| PROV-03 | Default categories follow the province: Quebec-only ones (school taxes, Quebec Family Allowance, solidarity credit, EI/QPIP) only in Quebec; Employment Insurance and provincial benefits elsewhere. Changing province adds the new province's categories and removes none. | Should |
| PROV-04 | Provincial RESP grants follow the beneficiary's province: the QESI in Quebec, the B.C. Training and Education Savings Grant in British Columbia; none where the province has none. | Should |
| PROV-05 | Each LIRA and LIF records the law it answers to (a province, or federal), by default the holder's province; the LIF maximum follows it (none in Saskatchewan and Prince Edward Island). | Must |
| PROV-06 | Pension and tax wording follow the province: the QPP offered first in Quebec and the CPP elsewhere; Quebec forms (TP-21.4.39, RL slips) named only for Quebec. | Should |
| PROV-07 | Later phases use the province too: provincial medical expense credit totals (MED-14) and provincial slips (RL-3, RL-16 in Quebec) in the tax and medical reports. | Should |

Not covered yet: unlocking rules for locked-in plans (age 55 or 65, small balances, financial
hardship), which differ by jurisdiction and are left to the institution; provincial grants that are
suspended (Saskatchewan's SAGES).

## Updates

Details of SEC-08 and DIST-05 as built in Phase 4d (2026-10-03), beyond what the SRS states
(ADR 0008).

| ID | Requirement | Priority |
| --- | --- | --- |
| UPD-01 | A copy that can check for updates asks once, on first start, whether to check once a day, saying what GitHub learns; nothing is requested before the answer. The choice can be changed later (About on the desktop, Settings on the phone). | Must |
| UPD-02 | Only copies no store updates check: Linux .deb, .rpm and AppImage, and the Android build from GitHub Releases. The Microsoft Store, Flathub and Google Play builds never check. | Must |
| UPD-03 | An update is offered only from a list signed with RANN's release key, and a download is kept only if it matches the signed size and SHA-256; anything else is refused and deleted. | Must |
| UPD-04 | Every release file has a minisign signature and is listed in a signed SHA256SUMS, so a download can also be checked by hand. | Should |

## Navigation

Owner's request (2026-10-04), to be built in Phase 5g with the other usability work.

| ID | Requirement | Priority |
| --- | --- | --- |
| NAV-01 | The desktop's sections are grouped under a few headings instead of one long list, with reference lists and setup (categories, payees, rules, institutions, phones, AI reading, users, backups, security, About) under Settings. | Should |
| NAV-02 | Each user chooses the menu's place: a list on the left with collapsible groups, or a menu bar at the top with drop-down menus. The choice is remembered on that computer. | Should |
| NAV-03 | Both menus show the same groups and the same counts (such as documents waiting for review), and work from the keyboard (NFR-08). | Should |

## Contacts

Owner's request (2026-10-05): one place for the people and organizations the household deals with, linked to the rest of the app, standing on its own in the menu (not under Settings), with several of a kind told apart.

| ID | Requirement | Priority |
| --- | --- | --- |
| CON-01 | A contact is an organization or a person (who can work at an organization), with one or more kinds (bank, investment firm, insurer, pharmacy, family doctor, specialist, lawyer, contractor, employer, school, utility, government...), a "What for" line written by the user, and the household members it serves. | Must |
| CON-02 | A contact keeps labelled phones and emails, account or client numbers (masked; showing one asks for the password and is logged), address, website, hours and notes. | Must |
| CON-03 | Contacts are kept in an account group like health records: the household's shared group by default, or a private group chosen when adding one. | Must |
| CON-04 | A contact is linked with a role to records of the app (accounts, loans, investments, policies, health providers, medications, appointments, bills, payees, pets, vehicles, assets, contractors or one of their jobs, estate papers); its page lists them by role, and each record shows and picks its contacts. | Must |
| CON-05 | Contacts are filtered by kind, by the person served and by what they are linked to, and found by the global search. | Should |
| CON-06 | Contacts can be gathered from the records that already hold contact details, and two contacts merged, likely duplicates proposed, nothing merged or moved without the user's confirmation. | Should |
| CON-07 | The phone shows the contacts its user can see (never account or client numbers), with tap to call, email or map, and can send new contacts to the desktop, where they are reviewed before being added. | Should |

## Calendar views, schedules and activities

Owner's request (2026-10-06), before 1.0.

| ID | Requirement | Priority |
| --- | --- | --- |
| CAL-07 | The calendar has year, month, week and day views beside the agenda, with today, previous and next, and a date picker. | Should |
| CAL-08 | Each user chooses what the calendar shows: each kind (appointments and events, bills, health, maintenance and seasonal tasks, renewals, schedules, imported calendars) and each person; the choice is remembered per user on that computer. | Should |
| CAL-09 | Work and school schedules per person: days of the week with start and end times, repeating weekly or on a rotation of several weeks (shift work), with exceptions (holidays, sick days, professional development days) and the province's bank holidays off when chosen. | Should |
| CAL-10 | Each day shows a thin bar per person with a schedule that day (name, work or school, start and end times); week and day views also shade the scheduled hours. | Should |
| CAL-11 | Children's activities (practices, games, lessons) are events for the child, with who drives each way (carpool) and the cost, which can become a transaction. | Could |

## Calendars from each person's own accounts

| ID | Requirement | Priority |
| --- | --- | --- |
| CSY-01 | On the phone, with the user's permission, the calendars Android already syncs (Google, Outlook or Exchange, Samsung and others) can be read; the user picks which calendars to bring in. The app never signs in to a calendar account itself. | Should |
| CSY-02 | Upcoming items of the chosen calendars (a set number of days ahead) are sent to the desktop with the phone's other transfers, encrypted end to end, and updated or removed when they change on the phone. | Should |
| CSY-03 | Each brought-in calendar, and any single item, is private (only its owner sees it), busy only (others see the person busy at those times, without details) or shared (details visible to those who can see the chosen account group). Private by default. | Should |
| CSY-04 | Brought-in items show in the calendar as read-only, marked with their source; reminders are left to the calendar they come from. | Should |
| CSY-05 | iCalendar (.ics) files can be imported on the desktop as a one-time copy. | Could |

## Seasonal checklists

| ID | Requirement | Priority |
| --- | --- | --- |
| SEA-01 | Pool and yard (and garden) are kinds of asset, with starter tasks (pool opening and closing, chemicals, pump and filter; lawn, irrigation shut-off and start-up, outside taps, snow blower, lawn mower), beside fuller seasonal starter tasks for homes, cottages and vehicles (outside taps, eavestroughs, air conditioner cover, wiper blades, block heater, emergency kit). | Should |
| SEA-02 | A seasonal checklist per change of season (spring, summer, fall, winter): every task of that season across vehicles, homes, cottages, pools, yards and other assets, with its due date, ticked off one by one; ticking records the service in the log. | Should |
| SEA-03 | Seasons start on dates the household can change (Rates and rules), with Quebec's winter tire dates kept. | Should |
| SEA-04 | The seasonal checklist is on the phone too; ticks made there reach the desktop. | Should |
| SEA-05 | Renovations and energy upgrades (insulation, heat pump, windows, solar) recorded as home projects with their receipts, the rebates and grants applied for and received, and their effect on the home's cost base. | Could |

## Trips and vehicles on the phone

| ID | Requirement | Priority |
| --- | --- | --- |
| TRP-01 | A trip is started and ended on the phone: vehicle, driver, date and time, odometer and place at each end; the phone takes one location fix at Start and at Arrive only (no background tracking), with the user's permission. | Should |
| TRP-02 | Saved places: name, category (home, work, client, store, fuel, garage, medical, other), address and a matching radius; a location fix is matched to the nearest saved place, or the user names a new one. Places are kept on the user's devices only and never sent to a map service. | Should |
| TRP-03 | Distance comes from the odometer readings (the CRA's basis); time travelled from the two times. The purpose (business, employment, medical, personal) is suggested from the place and confirmed. | Should |
| TRP-04 | A trip records whether the vehicle tows a trailer (which one) or carries a heavy load, and the passengers. | Should |
| TRP-05 | Fuel and charging are entered on the phone too (litres or kWh, cost, full or partial, station as a saved place); consumption (L/100 km or kWh/100 km) is shown separately for normal driving, towing and heavy loads. | Should |
| TRP-06 | Trips reach the desktop's trip log and odometer readings, so distance-based maintenance is forecast from real driving; the CRA business-use log and medical travel use them. | Should |
| TRP-07 | Vehicle details add engine, transmission, drive, fuel tank or battery capacity, tire sizes (summer and winter), oil type and capacity, towing capacity and gross vehicle weight, and personal or commercial use. | Should |
| TRP-08 | Vehicle budget forecast: fuel or energy and maintenance for the coming months from the recent driving, consumption by kind of trip and prices. | Should |
| TRP-09 | For commercial vehicles: a logbook export the CRA accepts, kilometres per province or state (for fuel tax reports such as IFTA) and inspection reminders (annual safety inspection, CVOR or NSC renewals). | Could |
| TRP-10 | EV charging: home or public, kWh and cost, cost per km compared with fuel. | Should |

## Other trackers

| ID | Requirement | Priority |
| --- | --- | --- |
| UTL-01 | Utility meters (electricity, natural gas, water) per property: readings entered on the phone or the desktop, use per month compared with the same month last year, unusual use flagged. | Should |
| UTL-02 | Fuel tanks (propane, heating oil): level readings and deliveries, use per month, and a reminder to order before the tank is low. | Should |
| HRS-01 | Hours worked for side income, per client and task, started and stopped on the phone or entered on the desktop; unbilled hours become invoice lines (SAL-04). | Should |
| CHO-01 | Chores per child, ticked on the phone, earning amounts that feed the child's allowance and money (HH-03). | Could |
| VOL-01 | Volunteer hours per person and organization (volunteer firefighter and search and rescue tax credits need 200 hours; students' community service hours), with a yearly total. | Could |
