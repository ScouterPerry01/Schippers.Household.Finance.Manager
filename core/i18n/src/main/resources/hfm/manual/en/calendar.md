# Calendar

The Calendar brings together your appointments and every date the rest of the app already knows: bills coming due, prescriptions to refill, vaccines, renewals and maintenance. One look shows what the coming weeks hold. Calendar is in the Money group of the menu.

## What the calendar shows {#overview}
@index: agenda; schedule; appointments; due dates; what is coming up

The calendar shows five kinds of items. Each has a coloured mark on the left of its line, and the items that come from other screens have a button that opens the screen where they are managed.

### Appointments {#appointments}
@index: appointment; event; meeting

Appointments and events are the only items you create on the Calendar itself: a doctor's visit, a meeting with your financial planner, a garage appointment, a school event, a reminder to renew a passport. They can repeat and carry reminders. See [Add or edit an appointment](calendar#appointment-form).

Appointments added from the [Health](health) and [Pets](pets) screens are the same appointments and appear here too. A medical appointment started from the Health screen is stored in your private group by default.

### Bills {#bills-on-calendar}

Every due date of your active bills, income and scheduled transfers, from the [Bills](bills) screen, marked **Bill**, with the amount ("≈" when it is only expected) and whether it is to pay, paid or skipped. Paid and skipped dates stay on the calendar so you see the whole picture. **Open bills** goes to the Bills screen, where you mark them paid.

### Health due dates {#health-due}

From the [Health](health) screen, marked **Health**, with the person's name:

- "Refill: ..." when a medication is due to be refilled;
- "Follow-up: ..." when a test has a follow-up date;
- "Vaccine due: ..." when an immunization is due.

**Open health** goes to the Health screen.

### Renewals {#renewals}
@index: renewal; expiry; licence renewal; insurance renewal; warranty expiry; mortgage renewal

Marked **Renewal**, dates when something expires or must be renewed, gathered from other screens:

- a pet's municipal licence or pet insurance ([Pets](pets));
- a vehicle's registration, insurance or warranty end ([Vehicles](vehicles));
- a loan or mortgage term ending ([Loans and mortgages](loans));
- a credit card's annual fee ([Accounts](accounts));
- an insurance claim to send ([Medical claims](medical));
- a warranty ending on a home item, or an insurance policy renewal ([Home and assets](assets));
- a tax instalment ([Taxes](taxes)).

The button on the line opens the screen where it is managed, such as **Open vehicles** or **Open loans**.

### Maintenance {#maintenance}

Marked **Maintenance**, the next due date of each maintenance task on your vehicles and on your home and other assets, such as an oil change or a furnace inspection. The button opens [Vehicles](vehicles) or [Home and assets](assets).

## The Agenda tab {#agenda-tab}

**Agenda** lists everything from today to 60 days ahead, day by day. Each day has a heading with the day of the week and the date, and "Today" or "Tomorrow" when it applies. "Nothing in the next 60 days." means the agenda is empty.

An appointment's line shows:

- its time, or "All day";
- its title, with "(done)" or "(cancelled)" when marked; a cancelled one is struck through;
- its kind, its length, where it is, who it is for, the provider, the related account and how it repeats.

### Marking appointments {#marking}
@index: done; cancel one occurrence

The buttons on an appointment's line act on that one date only. For a repeating appointment, the other dates are not touched.

- **Done**: marks it done. It stays on the calendar, greyed, with "(done)", and no more reminders are given for it.
- **Cancel this one**: marks only this date cancelled, for example a weekly class that does not take place one week. It is struck through and no reminders are given for it.
- **Undo**: shown on a date marked done or cancelled. Removes the mark.
- **Edit**: opens the appointment's form, which changes all its dates.

## The Month tab {#month-tab}

**Month** shows a whole month as a grid, weeks starting on Monday. Today's date is in bold.

- **◀** and **▶**: previous and next month.
- **Today**: back to the current month.
- Each day shows up to four items, then "+n" for the others. Appointments show their time and title; other items show their name. Done appointments and paid or skipped bills are greyed; cancelled appointments are struck through.
- Click an appointment to change it.
- Click a bill, health item, renewal or maintenance item to open the screen where it is managed.
- Click an empty part of a day to add an appointment on that day.

"Click a day to add an appointment; click an appointment to change it." reminds you of this at the top.

## Add or edit an appointment {#appointment-form}

**Add an appointment** opens the form for today; clicking a day in the Month tab opens it for that day; clicking an appointment, or **Edit**, opens it to change. **Save** is available once **What** is filled in. **Cancel** closes the form without saving.

### What, kind, date and time {#what-and-when}

- **What**: the title, for example "Dentist - Léa" or "Meeting with the bank". It is what the agenda, the month grid and the reminders show. Required.
- **Kind**: Medical, Bank and finances, Vehicle, Home, Pets, Personal or Other. Default: Other (Medical or Pets when started from the Health or Pets screen). It is shown on the agenda line. The Health screen shows only Medical and Pets appointments.
- **Date**: the date, as YYYY-MM-DD. For a repeating appointment, the first date. Required.
- **Time**: the start time, as HH:MM in 24-hour time, for example 09:30 or 14:00 (14h00 also works). Default: 09:00. The field is marked "HH:MM" when the time cannot be read.
- **Minutes**: how long it lasts, in minutes, for example 30 or 90. Default: 60. Shown on the agenda as "1 hour" or "90 min". Optional.
- **All day**: tick it for an event without a time, such as a birthday or a day off. Time and Minutes are then hidden. Reminders for an all-day event count from 08:00 that day.

### Where, who, provider and account {#where-and-who}

- **Where**: the place, such as an address or "Video call". Optional.
- **Who**: the household member or pet it is for. Shown on the agenda line. The Health screen lists a person's or a pet's Medical and Pets appointments by this field. "(none)" for nobody in particular.
- **Provider**: a health provider from the Health screen (doctor, dentist, physiotherapist...). Shown on the agenda line. Optional.
- **Related account**: an account it is about, for example the mortgage for a renewal meeting at the bank. Shown on the agenda line. Optional.

### Repeats {#repeats}
@index: recurring appointment; repeating event

- **Repeats**: Once, Weekly, Every two weeks, Monthly, Quarterly, Twice a year, Yearly, Every … days, Every … weeks, or Every … months. Default: Once. A monthly appointment comes back on the same day of the month (in a shorter month, its last day).
- **Every**: shown for the "Every …" choices. The number of days, weeks or months between dates, 1 or more.
- **Last due date (optional)**: shown when the appointment repeats. The last date it can occur, as YYYY-MM-DD. Leave empty for no end. It cannot be before **Date**.

### Remind me {#remind-me}
@index: appointment reminder; alert before appointment

**Remind me** lists the times you can be reminded before the start. Tick as many as you like:

- **When it starts**
- **15 minutes before**
- **1 hour before**
- **2 hours before**
- **1 day before** (ticked by default)
- **2 days before**
- **7 days before**

From the earliest reminder you ticked until the appointment starts, it appears in the reminders banner at the top of the other screens, for example "Garage: winter tires: tomorrow at 09:30". A system notification is also shown when each reminder time you ticked comes up. Untick them all for no reminder. See [Reminders](calendar#reminders).

### Notes and Store in {#store-in}
@index: private appointment; privacy

- **Notes**: anything to remember, such as what to bring or questions to ask.
- **Store in**: the account group the appointment is kept in. Choose a shared group so the whole household sees it, or a group marked "(private)" so only you see it, for example for a medical appointment. It can be chosen only when the appointment is created. A new appointment goes to the first shared group you can add to.
- **Create my private group**: shown when you do not have a private group yet, with "You have no private group yet. Records in a shared group can be seen by everyone who can open that group." It creates your private group and chooses it.

### Delete an appointment {#delete-appointment}

**Delete** (shown when editing) asks "Delete ... and all its repeats?" and, on confirmation, deletes the appointment with all its dates and marks. This cannot be undone. To drop just one date of a repeating appointment, use **Cancel this one** on the agenda instead.

## Reminders {#reminders}
@index: notification; reminder banner

Reminders from the calendar, bills, medication refills, renewals and maintenance all appear together:

- in a coloured banner at the top of every screen except the one the reminder belongs to, such as "2 reminders  Dentist: tomorrow at 10:00 · Hydro: due in 7 days". Click the banner to open the screen of the first reminder;
- as a system notification from RANN's Roost, checked every few minutes while the household is open. Each reminder is announced once per session (for an appointment, once for each reminder time you ticked).

An appointment marked done or cancelled gives no reminder. For bill reminders, see [Reminders and notifications](bills#reminder-banner).

## Who can do what {#permissions}

- Adding an appointment needs at least **Capture only** permission on the group chosen under **Store in**.
- Changing, marking done or cancelled, and deleting need **Edit** permission on that group.
- An appointment in a private group is encrypted and seen only by its owner. Bills, health dates and the other items appear for those who can see the records they come from. See [Users](users).
