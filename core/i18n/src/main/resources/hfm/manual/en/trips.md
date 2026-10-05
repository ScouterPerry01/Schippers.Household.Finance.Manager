# Trip log

The trip log keeps the trips you make by car for work or for medical care. From it, RANN's Roost adds up each person's kilometres by purpose, works out the share of each vehicle's driving that was for work, and turns a long medical trip into a medical expense. It is in the **Home and family** group of the menu, under **Trip log**.

@index: mileage log; logbook; kilometres; business use of a vehicle; work kilometres; vehicle expenses

> Note: The CRA expects a logbook of business or employment driving (date, destination, purpose and distance) to support a claim for vehicle expenses, along with the total kilometres driven in the year. RANN's Roost does not give tax advice; check the CRA's rules before claiming.

## The Trip log screen {#screen}

At the top of the screen:

- **Tax year**: the calendar year shown, from this year back six years. The cards and the list show only the trips dated in that year.
- **Add a trip**: opens the [trip dialog](#trip-dialog) for a new trip.

New trips are stored in the shared account group you are allowed to add to (or the first group you can add to). Saving, changing or deleting needs permission to change records there.

### The summary cards {#cards}

When the year has trips or vehicle readings, cards appear above the list.

One card per person (or **Household** for trips not tied to anyone) lists their kilometres for the year by purpose, such as "Business: 1,240 km" and "Medical: 392 km". Round trips count twice their one-way distance.

One card per vehicle that has work trips or odometer readings in the year shows:

- **For work**: the kilometres of the year's **Business** and **Employment** trips with this vehicle.
- **Driven in all**: the kilometres driven in the year, from the vehicle's odometer readings: the last reading of the year less the first. It needs at least two readings in the year; otherwise the card asks you to add odometer readings at the start and end of the year.
- **Work share**: the work kilometres as a percentage of all the kilometres driven, rounded and never above 100 %. This is the business-use share asked for when claiming vehicle expenses.

Odometer readings are entered on the [Vehicles](vehicles) screen, or sent from the phone with **Odometer or hours** (see [RANN's Roost Mobile](phone-app#odometer-form)). Inactive vehicles keep their cards for the years they were used.

@index: work share; odometer; business-use percentage

### The list of trips {#trip-list}

The year's trips are listed newest first. Each line shows:

- the date;
- where from and where to ("Home → Client office"), with **↺** for a round trip;
- the purpose, the person, the vehicle and the notes;
- **Add to medical expenses**, for a **Medical** trip of 40 km or more one way (see [Medical travel](#medical-travel)); once the trip is added, it reads **Added to medical expenses** and can no longer be clicked;
- the distance, doubled for a round trip.

Click a trip to change or delete it. With no trips in the year, the list says so.

## Add a trip dialog {#trip-dialog}

The same dialog adds a trip (**Add a trip**) or changes one (**Edit the trip**).

- **Date**: the day of the trip, as YYYY-MM-DD. Today by default. It decides which year the trip counts in.
- **Purpose**: why you drove. It decides which totals the trip counts in:
  - **Business**: driving for a business you run, such as visiting clients for your side work. Counts as work kilometres for the vehicle.
  - **Employment**: driving your employer requires, other than getting to and from work. Counts as work kilometres for the vehicle.
  - **Medical**: driving to medical care. Can become a medical expense when it is 40 km or more one way.
  - **Personal**: anything else. Counted in the person's totals only.
  A new trip starts as **Business**.
- **From**: where you left from, such as "Home". Optional.
- **To**: where you went, such as a client's address or a hospital. Required.
- **Kilometres one way**: the distance one way, such as 23.5 (a comma also works). Required, more than zero and under 10,000. It is kept to one decimal.
- **Round trip**: on when you came back the same way; the trip then counts twice the distance. On by default.
- **Person**: who made the trip, or **Household**. It decides which person's card the trip counts in and is suggested as the patient when adding it to medical expenses.
- **Vehicle**: the vehicle used, or **No vehicle**. Only trips with a vehicle count towards that vehicle's work share. A new trip proposes the first vehicle in use. A saved trip keeps its own choice: **No vehicle** stays **No vehicle**, and a vehicle sold or retired since stays in the list for that trip.
- **Notes**: anything to remember, such as the client or the reason for the visit.
- When **Medical** is chosen, a reminder explains that a medical trip counts as a medical expense when the care is 40 km or more away, one way, and not available closer to home. The 40 km is a figure of **Rates and rules** (Medical travel: minimum distance), read for the trip's date.
- **Delete**: shown when changing a trip. Asks "Delete the trip of date to destination?" and, once confirmed, deletes it. It cannot be undone. A medical expense made from the trip stays on the Medical claims screen.

## Medical travel {#medical-travel}

@index: medical travel; travel expenses for medical care; 40 km; 80 km; medical expense tax credit; METC; rate per kilometre

When medical care is not available near home and you travel 40 km or more one way to get it, the cost of the trip can count as a medical expense for the medical expense tax credit. The CRA's simplified method lets you claim a set rate per kilometre instead of keeping every receipt; the CRA publishes the rate for each province every year. (Trips of 80 km or more one way can also allow other costs, such as meals and lodging; record those on the [Medical claims](medical) screen.)

A **Medical** trip whose one-way distance is 40 km or more shows **Add to medical expenses** in the list.

### Add to medical expenses dialog {#to-medical-dialog}

- The first line shows the trip's date and destination, followed by a reminder that the CRA publishes a rate per kilometre for each province and territory every year, and that the rate shown comes from **Rates and rules**.
- **Person**: the patient, whose medical expense it becomes. The trip's person by default, or else the first member of the household. The rate follows this person's province or territory (the household's, unless the person has their own), as the travel begins where they live.
- Under the person, a line says where the rate comes from: the rate for the province in effect from a date, built in (with the CRA source) or the household's own; or that there is no rate for the province yet.
- **Rate per kilometre**: in dollars, such as 0.62 or 0.605 (the CRA's rates can have a half cent). It is filled in with the Medical travel: rate per kilometre of Rates and rules for the trip's date and the person's province: the CRA publishes each year's rates early in the next year, so until then the last year's rate is shown. You can change it for this trip. A rate typed for a year in an earlier version of the app is still used for that year.
- **Keep this rate for province from January 1, year, in Rates and rules**: for an administrator only. When checked and the rate was changed, the rate is also added to Rates and rules as the household's own for that province from January 1 of the trip's year, so it is filled in for the next trips.

**Save** is available once a person and a rate are chosen. It:

1. keeps the rate in Rates and rules, when the box above is checked;
2. adds a medical expense for the person on the [Medical claims](medical) screen, of the type **Travel for medical care**, dated and paid on the trip's date, for the rate times the trip's kilometres (doubled for a round trip), described as the destination and the distance.

The expense then counts in the medical claim like any other. A trip is added only once: its button then reads **Added to medical expenses**. To add it again, for example at another rate, delete the expense on the Medical claims screen first; the button comes back.
