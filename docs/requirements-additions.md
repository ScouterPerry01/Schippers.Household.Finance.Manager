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
