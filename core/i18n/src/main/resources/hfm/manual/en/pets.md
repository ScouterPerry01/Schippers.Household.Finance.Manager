# Pets

The **Pets** screen keeps a card for each animal in the household: its details, microchip, municipal licence, pet insurance and what it costs. From each card you reach the pet's health records and add a vet appointment. It is in the **Home and family** group of the menu.

## The screen at a glance {#overview}
@index: animals; dog; cat; pet card

At the top:

- **Show former pets**: unticked by default. Tick it to see the pets marked **No longer in the household** as well.
- **Add a pet**: opens a blank pet form. See [Add or edit a pet](pets#pet-form).

Below is one card per pet. When there is none, the screen says "No pets yet."

Pets belong to the whole household, not to an account group: every user sees them. Administrators and members can add, change and delete them. A user with the role **Viewer** sees the cards but has no **Add a pet** and no **Delete**, and saving a change gives an error. See [Users](users).

## The pet card {#pet-card}

Each card shows:

- the name, with "(no longer with us)" for a former pet;
- the animal and breed, the sex (with "neutered" or "spayed" when ticked), the age, the colour and the owner ("owner Sam"). The age is in months under two years, in years after; "about" is added when the date of birth is estimated;
- "Microchip" and its number, when recorded;
- the municipal licence line, "Licence number (municipality): renew by date", and the insurance line, "Insurance insurer, policy number: renew by date". Each line appears only when its date is entered. It is highlighted in bold within 30 days of the date, and red with "expired" once the date has passed;
- "This year: amount · Last 12 months: amount": what the pet cost, from the register. See [Costs](pets#costs);
- the four largest cost categories of the last 12 months, or "No costs recorded. In the register, choose this pet under "For"." when there are none.

And four buttons:

- **Health records**: opens the [Health](health) screen with this pet chosen. See [Health records](pets#health-records).
- **Add appointment**: adds a calendar appointment for the pet. See [Appointments](pets#appointments).
- **Costs**: opens the costs over several years. See [Costs](pets#costs).
- **Edit**: opens the pet's form.

## Add or edit a pet {#pet-form}

The form is titled **Add a pet** or **Edit pet**. Only the name is required; **Save** stays unavailable until it is filled.

### Details {#details}

- **Name**: the pet's name. Required.
- **Animal**: **Dog** (the default), **Cat**, **Bird**, **Fish**, **Rabbit**, **Small rodent**, **Reptile**, **Horse** or **Other**. It is shown on the card and after the name in the Health screen's list, for example "Rex (dog)".
- **Breed**: for example "Labrador retriever".
- **Colour**: for example "black and white"; useful if the pet is lost.
- **Sex**: **Male**, **Female**, or "(none)" if not known.
- **Spayed or neutered**: tick it when the pet has been spayed or neutered. Many municipalities charge less for the licence of a sterilized pet.
- **Date of birth**: as year-month-day. It gives the age on the card.
- **Estimated**: tick it when the date of birth is a guess, for example for a rescued pet. The card then shows "about" before the age.
- **Microchip number**: the number of the chip, shown on the card. Keep it handy to update the registry if you move.

### Municipal licence {#licence}
@index: dog licence; cat licence; municipal registration

Under **Municipal licence**:

- **Licence number**: the number of the tag or licence.
- **Municipality**: the city or town that issued it.
- **Expires**: when the licence must be renewed. It drives the licence line on the card and the reminder. See [Reminders](pets#reminders).

### Pet insurance {#insurance}

Under **Pet insurance**:

- **Insurer**: the company.
- **Policy number**.
- **Renewal date**: when the policy renews. It drives the insurance line on the card and the reminder.

The premiums themselves are payments in the register; choose the pet under "For" when you enter them so they count in its costs.

### Owner, notes and status {#owner-status}

- **Owner**: a household member, or **The whole household** (the default). Shown on the card.
- **Notes**: food, vet's instructions, the name of the pet sitter, anything useful.
- **No longer in the household** (when editing): tick it when the pet has died or been rehomed. See [Former pets](pets#former-pets).
- **Delete** (when editing): see [Delete a pet](pets#delete).

**Save** keeps the changes; **Cancel** closes the form without them.

## Former pets {#former-pets}

A pet marked **No longer in the household** is hidden from the screen, from the **Person or pet** list of the Health screen, and from the reminders. Its records and costs are kept. Tick **Show former pets** to see its card again, marked "(no longer with us)", and untick **No longer in the household** if it comes back.

## Delete a pet {#delete}

**Delete** in the form (not shown to a viewer) asks "Delete name? Transactions recorded for this pet are kept." Once confirmed, the pet is removed. The transactions that named it stay in their accounts. It cannot be undone; marking the pet as no longer in the household is usually better, since it keeps its history.

## Health records {#health-records}
@index: vet records; pet vaccines; pet medication

**Health records** opens the [Health](health) screen with the pet chosen in **Person or pet**. There you record its medications and refills, vaccines (rabies, for example, with the next dose due), allergies, conditions and tests, exactly as for a person. Its veterinarian, groomer and kennel go on the **Providers** tab, with the kinds **Veterinarian**, **Groomer** and **Kennel or pet sitter**.

A pet's health records are kept in the first shared group, since a pet belongs to the household. The **Health summary…** button is not offered for a pet.

## Appointments {#appointments}

**Add appointment** opens the calendar's appointment form with today's date, the kind **Pets**, and the pet chosen under **Who**. Fill in what it is (for example "Annual check-up"), the date and time, the place, the provider and the reminders, then save. The appointment appears on the [Calendar](calendar) and on the pet's **Appointments** tab in the Health screen. The form's fields are described in the [Calendar](calendar) chapter.

## Costs {#costs}
@index: pet expenses; vet bills; pet food

What a pet costs comes from the register: every transaction line where the pet is chosen under "For". Food, vet bills, grooming, insurance premiums and the licence all count when they name the pet. The amounts are converted to Canadian dollars at the rate of the day; a refund reduces the cost.

The card shows this year's total (since January 1) and the last 12 months, with the four largest categories of the last 12 months.

### The costs dialog {#costs-dialog}

**Costs** opens "What name costs", covering January 1 four years ago to today:

- **By year**: the total of each year.
- **By category**: the total of each category, largest first; lines with no category appear as "(uncategorized)".
- **Total**.
- A note when some amounts in another currency have no exchange rate and are left out.
- "Includes every transaction recorded for it (the "For" or "Vehicle" field in the register), in Canadian dollars at the rate of the day."

**Close** closes it.

## Reminders {#reminders}
@index: licence renewal; insurance renewal; reminder

For each pet still in the household, the municipal licence's **Expires** date and the insurance's **Renewal date** produce a reminder from 30 days before the date, and stay as long as the date is past and not updated, for example "Rex: municipal licence (Sherbrooke) in 12 days" or "Rex: pet insurance (insurer) 3 days overdue". It appears at the top of the window and in the system notification, and leads to the Pets screen. The dates also appear on the [Calendar](calendar).

Once renewed, edit the pet and enter the new date: the reminder stops.
