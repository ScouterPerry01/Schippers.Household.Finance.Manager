# Home and assets

The **Home and assets** screen keeps track of the home and everything worth keeping track of: where it is, what it cost and what it is worth, its warranties, its maintenance, the work done on the home and the contractors who did it, and the insurance that covers it all. Vehicles have their own screen, [Vehicles](vehicles), but they appear here too where it helps: in the maintenance list, the **Is it covered?** search and the insurance. It is in the **Home and family** group of the menu.

## The screen at a glance {#overview}
@index: home inventory; possessions; property; belongings

"The home and everything worth keeping track of (vehicles have their own section): where it is, what it cost and is worth, its warranties and what insures it."

The screen has six tabs:

- **Assets**: the list of assets, each opening a full record with value, warranties, documents and maintenance. See [Assets tab](assets#assets-tab).
- **Maintenance**: what is due on the vehicles and everything else, in one list. See [Maintenance tab](assets#maintenance-tab).
- **Projects**: renovations and repairs on a home, with budget, costs and the home's cost base. See [Projects tab](assets#projects-tab).
- **Contractors**: the people who work on the home and vehicles, with their jobs and ratings. See [Contractors tab](assets#contractors-tab).
- **Is it covered?**: find an item and see at once whether a warranty or protection still covers it. See [Is it covered? tab](assets#covered-tab).
- **Insurance**: home, auto, life and other policies, their premiums, beneficiaries and claims, and what no policy covers. See [Insurance tab](assets#insurance-tab).

Amounts on this screen are in the household's base currency, except insurance policies and their claims, which are in Canadian dollars. An asset saved before keeps the currency of its amounts.

## Assets tab {#assets-tab}

### The asset list {#asset-list}

At the top, **Add an asset** and **Show those sold or discarded** (unticked by default; tick it to include assets no longer owned).

Assets are listed alphabetically, each one under the asset it is part of, indented: the furnace and the dishwasher under the house, the outboard motor under the boat. Each line shows the name (with its status if no longer owned), the kind, make, model and location, and on the right what it is worth today, or the price paid in grey when its value is not tracked. Click a line to open the asset.

When the list is empty: "No assets yet: start with the home, then add appliances, electronics and other things worth insuring."

### Add or edit an asset {#asset-form}
@index: appliance; electronics; jewellery; furniture; serial number; RV; boat; cottage

The dialog is titled **Add an asset** or **Asset**.

- **Kind**: **Home**, **Cottage**, **RV**, **Boat**, **Trailer**, **Appliance** (the default), **Heating and cooling**, **Electronics**, **Computer**, **Furniture**, **Jewellery**, **Art**, **Tools**, **Sports equipment**, **Musical instrument** or **Other**. The kind decides which usual maintenance tasks are offered, which category a service payment gets, and whether a home can have projects (Home and Cottage only).
- **Name**: for example "House (Maple Street)" or "Kitchen fridge". Required.
- **Part of**: another asset this one belongs to, or "(none)". The list shows it underneath, and a policy that covers the parent covers it too (a home policy covers its contents). An asset cannot be part of itself or of one of its own parts. An asset that has parts cannot be deleted until they are moved or deleted.
- **Make**, **Model** and **Serial number**: from the label or the invoice. They can be searched on the **Is it covered?** tab, and are useful for a warranty or insurance claim.
- **Bought on**: the purchase date. It starts the depreciation, and is the starting point of maintenance tasks never done.
- **Bought from**: the store or seller.
- **Price paid**: in the household's base currency. Needed for depreciation and for a home's cost base.
- The purchase in the books: see [Find the purchase](assets#find-purchase).
- **Location (room, garage, cottage)**: where it is. Searched by **Is it covered?** too.
- **Owner**: a household member, or **The household** (the default).
- **Meter**: "(none)", **Hours of use** or **Kilometres**. Choose a meter for an asset whose maintenance follows its use, such as a boat engine or generator (hours) or an RV or trailer (kilometres). It allows tasks by use and meter readings. See [Meter readings](assets#meter).
- Value: see [Value and net worth](assets#value).
- **Status**: see [Sold, given away or discarded](assets#disposal).
- **Notes**.

Buttons:

- **Save**: saves the asset. A new asset must be saved before warranties, documents and maintenance can be added; they then appear below.
- **Delete** (once saved): asks "Delete "name" with its warranties, maintenance tasks, services and meter readings? Transactions linked to it are kept." and, once confirmed, deletes the asset with all of these. It cannot be undone. Marking it sold, given away or discarded keeps its history instead.
- **Close**: closes the dialog. Changes not saved with **Save** are lost.

You need the **Edit** permission on the asset's group to add or change it.

### Find the purchase {#find-purchase}
@index: link transaction; receipt; proof of purchase

Linking an asset to its purchase in your accounts keeps the proof of purchase at hand and lets the app know which credit card paid for it, for the card's purchase protection and extended warranty.

- The line shows "Purchase: date · payee · amount" when one is linked, or "No purchase linked from the books."
- **Find the purchase**: type at least two letters of the payee or memo, for example "Best Buy".
- **Matching purchases**: the payments found (spending only), with date, payee and amount. Choose one to link it. It also fills **Bought on**, **Price paid** and **Bought from** when they are empty.
- **Unlink**: removes the link (save to keep the change).

### Value and net worth {#value}
@index: net worth; market value; estimate

- **Value**: how the asset's worth is followed:
  - **Not tracked** (the default): no value. The asset does not count in net worth.
  - **Estimate entered**: you enter **Estimated value**; it is dated the day you save. Update it now and then, for example from a municipal assessment for a home.
  - **Falls each month from the price**: the value starts at **Price paid** on **Bought on** and falls evenly each month. See [Depreciation](assets#depreciation).
- **Count in net worth**: shown when the value is tracked. When ticked, the asset's value on each date is added to the household's net worth: on the [Dashboard](dashboard) and in the **Net worth** report under [Reports](reports). Before its purchase date, and from its disposal date, it counts for nothing.

> Tip: Count the home and the cottage in net worth. Appliances and electronics lose value quickly; following them with depreciation is useful for insurance, but you may prefer to leave them out of net worth.

### Depreciation {#depreciation}
@index: depreciation; straight line; residual value

With **Falls each month from the price**:

- **Over (years)**: how many years the value takes to fall to its lowest; 10 by default (set in [Rates and rules](rates-rules)), from 1 to 100.
- **Down to (% of price)**: the value it keeps at the end, as a percentage of the price; 0 by default (also from [Rates and rules](rates-rules)), from 0 to 100.

The value falls by the same amount each full month after the purchase, until it reaches that percentage of the price, and stays there. Example: a fridge bought $1,800 over 10 years down to 10 %: it loses $13.50 a month and is worth $180 after 10 years. A price is required with this method.

### Sold, given away or discarded {#disposal}

- **Status**: **Owned** (the default), **Sold**, **Given away** or **Discarded**.
- **Date**: when it left the household. Required when the status is not **Owned**.
- **Sale price**: shown when **Sold**.

From the date, the asset is worth nothing in net worth, and it leaves the list (unless **Show those sold or discarded** is ticked), the maintenance lists, the **Is it covered?** search, the uninsured list and the reminders.

### Warranties and coverage {#coverage}
@index: warranty; purchase protection; extended warranty; credit card benefits

Once the asset is saved, **Warranties and coverage** lists what covers it, each with "until date", "ended date", "ended: 500 hours of use reached" or "no end date":

- each warranty you added (click one to open it);
- for an asset whose purchase is linked to a credit card payment, the card's own benefits that apply, such as **Purchase protection** or **Extended warranty**, as set on the card under [Accounts](accounts).

"No warranty recorded." when there is none. **Add a warranty** adds one.

You are reminded 60 days before a warranty ends (the default lead time, set in [Rates and rules](rates-rules)): the reminder appears at the top of the window and in the system notification, and leads to this screen. For a warranty limited by hours of use, the end is the earlier of its end date and the day the hour meter should reach the limit, at the asset's usual use per day (see [Meter readings](assets#meter)).

### Add or edit a warranty {#warranty-form}

The dialog is titled **Warranty**.

- **Kind of warranty**: **Manufacturer's warranty** (the default), **Extended warranty**, **Credit card extended warranty**, **Service contract** or **Other warranty**.
- **Provider**: the manufacturer, store or company.
- **Card**: only for **Credit card extended warranty**, and required then: the credit card that paid for the item. "With no end date of its own, it adds the card's extended warranty to the manufacturer's, when that one is short enough (see the card's benefits)." For this, the asset needs a manufacturer's warranty with an end date, and the card needs an extended warranty benefit. The end date is then worked out by itself.
- **What it covers**: for example "parts and labour" or "compressor only".
- **Start**: the purchase date is proposed.
- **End**: the end date. It cannot be before the start. Leave it empty for a card's extended warranty to be calculated, or for a warranty with no end.
- **Or up to (hours of use)**: for a warranty limited by hours of use, as noted on it, such as 500. It counts when the asset's **Meter** is **Hours of use**: the warranty ends when the latest meter reading reaches the limit, even before its end date ("ended: 500 hours of use reached"), and it is then no longer reminded. While it runs, the reminder comes 60 days before the day the meter should reach the limit at the usual use per day, which needs readings at least two weeks apart. With only hours and no end date, the reminder needs those readings.
- **Phone number for claims**.
- **Notes**.
- **Save**; **Delete** for a saved warranty: it asks "Delete this warranty (kind) with its claims?" and, once confirmed, deletes the warranty and its claims.

### Warranty claims {#warranty-claims}

Once the warranty is saved, the dialog shows its **Claims**, each with the date, problem, outcome and amount covered, and a **Delete**, which asks "Delete the claim "problem" of date?" first. To add one:

- **Problem**: what went wrong. Required.
- **Outcome**: for example "repaired" or "replaced".
- **Covered**: the amount the warranty paid for, in the household's base currency.
- **Add a claim**: records it, dated today.

Under the claims, **Proof of purchase and warranty documents** lets you attach the receipt and the warranty card with **Attach a file…** or **From the review inbox**.

### Photos and documents {#photos}
@index: home inventory photos; insurance inventory

**Photos and documents** attaches photos, invoices, manuals and appraisals to the asset, with **Attach a file…** or **From the review inbox**. They are kept in the [Documents](documents) vault. Photos of every room and valuable are what an insurer asks for after a fire or a burglary: the **Assets and warranties** report under [Reports](reports) gives a home inventory.

## Maintenance on an asset {#asset-maintenance}
@index: home maintenance; furnace filter; gutters; chimney; winterize

Once an asset is saved, the bottom of its dialog shows **Maintenance**: "Each task repeats after a number of months, a number of hours or kilometres on its meter, or every year in its season, whichever comes first."

- **Add usual tasks**: shown when there are usual tasks for this kind of asset. See [Usual tasks](assets#usual-tasks).
- **Add a task**: see [Add or edit a task](assets#task-form).
- **Enter a meter reading** and "Meter: reading": shown when the asset has a meter. See [Meter readings](assets#meter).

Each active task shows its interval, "last done date" (or "counting from date" while it was never done), its state (**Next**, **Due soon** in bold, **Due now** in red) with the due date or use, a forecast "at your usual use, around date" for a task by use, and the buttons **Record as done** and **Edit**. Paused tasks follow, marked "(paused)". "No maintenance tasks yet." when there are none.

The rules for when a task is due are the same as for vehicles: see [When a task is due](vehicles#task-due). Hours or kilometres replace the odometer.

### Usual tasks {#usual-tasks}

**Add usual tasks** adds the common tasks for the asset's kind that it does not already have. Seasonal tasks fall due on their date each year; the others count from today.

- Home and cottage: **Clean the gutters** (yearly, October 30), **Test smoke and CO detectors** (every 6 months), **Flush the water heater** (yearly), **Chimney sweep** (yearly, September 1), **Test the sump pump** (yearly, March 15).
- Home, cottage, and heating and cooling: **Furnace filter** (every 3 months), **Heating and cooling service** (yearly, September 15).
- Cottage: **Empty the septic tank** (every 36 months), **Open the cottage** (May 1), **Close the cottage** (October 15).
- RV: **De-winterize** (April 15), **Winterize** (October 15), **Roof sealant** (May 1), **Propane system inspection** (yearly), **Generator service** (yearly or 150 hours).
- RV and trailer: **Wheel bearings** (yearly or 20,000 km).
- RV and boat: **Battery check** (every 6 months).
- Boat: **Spring launch** (May 1), **Winterize and store** (October 1), **Engine oil and filter** (yearly or 100 hours), **Water pump impeller** (every 24 months or 300 hours).
- Trailer: **Lights and brakes check** (April 15).

An interval by hours or kilometres is only added when the asset's **Meter** counts that unit; otherwise the task repeats by months only.

### Add or edit a task {#task-form}

- **Task**: the name. Required.
- Without a meter: "Repeat it every so many months. For a seasonal task, enter when it was last done: it comes back a year later." With a meter: "Fill in one or both: the task falls due at whichever comes first."
- **Every (months)**: from 1 to 240; 12 proposed.
- **Every (hours of use)** or **Every (km)**: shown with a meter. At least one interval is required.
- **Last done on**: when it was last done, or the date to count from; today proposed. Once a service records the task, the latest such service is used.
- **Hours when last done** or **Kilometres when last done**: shown with a meter; the latest reading proposed.
- **Remind me (days before)**: 14 by default for a new task (set in [Rates and rules](rates-rules)).
- **Remind me (hours before)** or **Remind me (km before)**: shown with a meter; 10 hours or 500 km by default.
- **Notes**: the filter size, the part number.
- **Active** (when editing): untick to pause the task.
- **Delete** (when editing): asks "Delete the task "name"? Services already logged are kept." and, once confirmed, deletes it.

### Meter readings {#meter}
@index: hours meter; engine hours

**Enter a meter reading** opens a dialog with **Date** (today) and the reading in **Hours of use** or **Kilometres**, a whole number. Readings from services count too. The latest reading decides tasks by use; the readings of the last year (at least two weeks apart) give the usual use per day for the forecast and the use on the cost line.

### Service log {#service-log}

Under **Service log**, every service on the asset, the most recent first: date, tasks done (or notes, or "Service"), the provider or "Done myself", the meter reading, the parts, "payment entered", the cost and **Edit**. **Log a service** adds one; **Record as done** on a task opens the same form with the task ticked.

### Log a service {#service-form}

- **Date**: required; today by default.
- The meter reading (**Hours of use** or **Kilometres**), when the asset has a meter.
- **Tasks done**: tick each task this service did; its schedule starts over.
- **Contractor or provider**: who did it; unavailable when **Done myself** is ticked.
- **Done myself**.
- **Parts**: for example "16x25x1 filter".
- **Cost**.
- **Notes**.
- **Also enter the payment in an account**, **Paid from** and **Category**: as for a vehicle service (see [Also enter the payment](vehicles#payment)). The category proposed is home maintenance, or the cottage and RV category for a cottage, RV, boat or trailer. The payment is linked to the asset.
- **Delete** (when editing): asks "Delete the service of date? A payment entered with it stays in its account." and, once confirmed, deletes it; the payment stays in its account.

### Cost of ownership {#ownership-cost}

Below the service log: "Cost of ownership in year: amount", with "insurance about amount", the use ("120 h used") and the cost per hour or per kilometre when known. "Running costs are the payments linked to it and the services logged without a payment. The insurance is its share of the premiums of the policies that name it."

The insurance share is the yearly premium of each active policy that names the asset, divided among the items the policy names, for the part of the year so far. The **Maintenance and cost of ownership** report under [Reports](reports) gives the same for every vehicle and asset.

## Maintenance tab {#maintenance-tab}
@index: what is due; upcoming maintenance

"Maintenance on the vehicles and everything else, in one list. The phone shows it too."

Two buttons choose how far ahead to look:

- **This month** (the default): tasks due soon or overdue, and those next due by the end of the month.
- **Next 12 months**: also those next due in the coming year.

Each line shows the task, the asset or vehicle (marked "vehicle"), its state and due date or use, and a forecast for tasks by use. **Open** opens the asset's dialog, or the [Vehicles](vehicles) screen for a vehicle. "Nothing due." when the list is empty.

Tasks **Due soon** and **Due now** also appear in the reminders at the top of the window and in the system notification, and next due dates on the [Calendar](calendar).

## Projects tab {#projects-tab}
@index: renovation; home improvement; capital improvement; repair; budget

"Projects done or under way on a home. Capital improvements add to its cost base, which matters if the home is ever sold and is not the principal residence for every year."

At the top, **Add a project**. For each home or cottage that has a price paid or improvements, a line "Home: cost base amount, of which improvements amount". Then the projects, each with its name, status, home, "Capital improvement" or "Repair", the budget, and what was spent, in red when over budget. Click a project to see and add its costs; **Edit** opens its form. "No projects yet." when there are none.

### Add or edit a project {#project-form}

- **Project**: the name, for example "New roof" or "Finish the basement".
- **Status**: **Planned** (the default), **Under way** or **Done**. Only projects under way or done count in the cost base.
- **Home**: the home or cottage it is for, among assets of the kind Home or Cottage; or **Nothing in particular**. The first home is proposed.
- **Start** and **End**: the end cannot be before the start.
- **Budget**: what you plan to spend.
- **A capital improvement**: ticked by default. "A new roof, a finished basement or an addition is a capital improvement. Painting or fixing a leak is a repair." Untick it for a repair.
- **Notes**.
- **Delete** (when editing): asks "Delete the project "name" with its costs? It no longer counts in the home's cost base." and, once confirmed, deletes it with its costs.

### Project costs {#project-costs}

Click a project to open its costs. The top line reads "Spent amount of a budget of amount". Each cost shows its date, what it was for, the contractor and the amount, with **✕** to delete it: it asks "Delete the cost "description" of amount?" first, and the window closes once it is deleted. To add one:

- **Date**: today by default.
- **What it was for**: for example "Shingles" or "Deposit". If left empty, the project's name is used.
- **Amount**: required, not zero. A negative amount records a refund or credit.
- **Contractor**: shown when contractors are recorded; who was paid.
- **Add the cost**: records it.

Project costs are kept with the project only: they are not payments in an account, and they do not change budgets or account balances. Enter the payments in the register as usual.

### Cost base {#cost-base}
@index: adjusted cost base; ACB; principal residence; capital gain

The cost base of a home is its **Price paid** plus what was spent on its capital improvement projects under way or done. Planned projects and repairs do not count. When a property that was not your principal residence for every year it was owned is sold, the capital gain is the selling price less this cost base (and selling costs). Keep the invoices: attach them to the home's **Photos and documents**.

> Note: This is information for your records, not tax advice. The rules for the principal residence exemption are the CRA's.

## Contractors tab {#contractors-tab}
@index: tradesman; plumber; electrician; roofer; ratings

At the top, **Add a contractor** and **Show archived**. Each contractor shows the name and trade, the phone, email and website, the average rating in stars, and the number of jobs, with **Edit**. Click a contractor to see and add jobs. "No contractors yet." when there are none.

### Add or edit a contractor {#contractor-form}

- **Name**: required.
- **Trade**: for example "Plumber" or "Roofer".
- **Phone**, **Email** and **Website**.
- **Notes**: who to ask for, licence number, warranty on the work.
- **Archived (no longer used)** (when editing): hides the contractor unless **Show archived** is ticked. Its jobs are kept.

### Jobs and ratings {#jobs}

Click a contractor to open a dialog with its name, listing its jobs: date, description, stars and cost, with **✕** to delete one: it asks "Delete the job "job" of date? The contractor's rating is worked out again." first, and the dialog closes once it is deleted. Under **A new job**:

- **Date**: today by default.
- **Job**: what was done. Required.
- **Cost**: what it cost.
- **Rating**: **Not rated**, or one to five stars. The contractor's rating is the average of its rated jobs, to one decimal.
- **For**: the asset the job was for, or **Nothing in particular**.
- **Add the job**: records it and closes the dialog.

Like project costs, jobs are a record only; they do not enter payments in an account.

## Is it covered? tab {#covered-tab}
@index: warranty lookup; find item; serial number search

When something breaks, look it up here before paying for a repair.

- **Find an item**: "By name, make, model or serial number; vehicles too." The location of an asset and the VIN of a vehicle are searched too. Leave it empty to list everything.

Each item found shows its name ("vehicle" for a vehicle) and, in colour, "under warranty or protection" or "no warranty". Below, each warranty or card benefit with its end date, still-active ones in normal text and ended ones greyed. An asset warranty whose hours of use are reached shows "ended: hours of use reached"; a vehicle warranty is ended once the odometer passes its kilometres. Assets no longer owned and vehicles no longer in use are not searched.

To record a warranty claim, open the asset on the **Assets** tab, then the warranty, and use [Warranty claims](assets#warranty-claims).

## Insurance tab {#insurance-tab}
@index: insurance policy; home insurance; tenant insurance; auto insurance; life insurance; disability insurance; premiums

At the top, **Add a policy**. Each policy shows its kind and insurer ("inactive" for one no longer in force), the policy number, "renews date", "covers amount", and the premium per year. Click a policy to open it. "No policies yet." when there are none.

Below the list come two summaries: [Not covered by any policy](assets#uninsured) and [Life and disability cover](assets#life-cover).

### Add or edit a policy {#policy-form}

The dialog is titled **Add a policy** or **Insurance policy**.

- **Kind**: **Home** (the default), **Tenant**, **Condo owner**, **Auto**, **RV**, **Boat**, **Life**, **Disability**, **Critical illness**, **Long-term care**, **Travel**, **Umbrella liability** or **Other**. Life, disability, critical illness and long-term care are "life kinds": they have beneficiaries and a benefit amount instead of items covered.
- **Insurer**: required.
- **Broker**.
- **Policy number**.
- **Person insured**: a household member, or **The household**. Shown in the life summary and in the emergency summary.
- **Premium**: the amount of each payment.
- **Paid**: **Monthly**, **Quarterly**, **Twice a year** or **Yearly**. The premium per year shown in the list is the premium times the number of payments a year; it is also what is shared among items in their cost of ownership.
- **Deductible**.
- **Coverage limit** (or **Benefit amount** for a life kind): the most the policy pays.
- **Coverage details**: for example "replacement cost, sewer backup included".
- **Term starts** and **Renewal date**: the current term. The renewal date gives a reminder from 30 days before, until 30 days after (the default, set in [Rates and rules](rates-rules)). Correcting **Term starts** with the same premium moves the current term to the new date in **Premiums year over year** too.
- **What it covers**: not for life kinds. One box per asset still owned and per vehicle in use (marked "vehicle"). Tick what the policy covers. Covering a home also covers everything that is part of it.
- **Active**: untick it when the policy ends; it is then left out of the uninsured check, the cost of ownership, the reminders and the emergency summary.
- **Notes**.
- **Save**: saves the policy. A new premium, or a change of premium, is added to the premium history, dated from the term start (or today when there is none).
- **Delete** (once saved): asks "Delete the policy kind · insurer with its premiums, claims and beneficiaries?" and, once confirmed, deletes it with all of these.
- **Close**: closes the dialog; unsaved changes are lost.

Once saved, the dialog also shows the premium history, renewal, beneficiaries (life kinds), claims and **Policy documents**, where you attach the policy wording and declarations page with **Attach a file…** or **From the review inbox**.

### Renew a policy {#renew}
@index: renewal; premium increase

**Premiums year over year** lists each term's start date and premium, so you can see how the premium changed. To renew:

- **Next renewal date**: the end of the new term. Required, and after the current renewal date.
- **New premium**: the premium of the new term; leave it empty if it did not change.
- **Renew**: the term now starts on the old renewal date and ends on the new one, the premium is updated, and the term joins the history. **Term starts** and **Renewal date** in the form show the new term at once.

### Beneficiaries {#beneficiaries}

For a life kind, **Beneficiaries** lists who receives the benefit, with their share and "contingent" for a backup beneficiary, and a **Delete** for each, which asks "Remove name from the beneficiaries?" first. To add one:

- **Name**: required.
- **Share (%)**: from more than 0 to 100, or empty.
- **contingent**: tick it for a contingent beneficiary, who receives the benefit only if the first ones cannot.
- **Add**.

Beneficiaries are recorded for reference; the designation filed with the insurer is what counts. They appear in the life summary and in the [Emergency and estate](estate) summary.

### Insurance claims {#insurance-claims}

**Claims** lists each claim with its date, description, status and amount paid. **Add a claim**, or a click on a claim, opens **Insurance claim**:

- **Date**: the date of the loss or of the claim; today by default.
- **Claim number**: the insurer's reference.
- **Description**: what happened. Required.
- **Item**: the asset or vehicle concerned, or "(none)".
- **Status**: **open**, **paid**, **refused** or **closed**.
- **Amount claimed**, **Deductible**, **Amount paid** and **Date paid**.

**Save** keeps it. Amounts cannot be negative.

### Not covered by any policy {#uninsured}

"Assets and vehicles no active policy covers, directly or as part of something insured (a home policy covers its contents)." A policy counts while it is active and its renewal date has not passed. Each item is listed in red with its value (or price). Tick the item under **What it covers** in the right policy, or note that you choose not to insure it.

### Life and disability cover {#life-cover}

For estate planning, the active life, disability, critical illness and long-term care policies are listed with the insurer, the person insured, the benefit amount, and the beneficiaries with their shares, or "No beneficiary recorded."

## Reminders {#reminders}

This screen produces these reminders, shown at the top of the window and as a system notification:

- a warranty ending within 60 days, by its end date or, for a limit of hours of use, by the day the meter should reach it;
- an insurance policy to renew within 30 days, or up to 30 days late;
- maintenance tasks **Due soon** or **Due now** on assets still owned.

The 60 and 30 days are the defaults; they can be changed in [Rates and rules](rates-rules).

Their dates also appear on the [Calendar](calendar).

## Related reports {#reports}

Under [Reports](reports):

- **Assets and warranties**: the home inventory, with values and warranties.
- **Maintenance and cost of ownership**: what each vehicle and asset costs to keep.
- **Net worth**: includes the assets counted in net worth.

## Contacts {#linked-contacts}

@index: contact; linked contact; Link a contact

The forms of a saved asset, insurance policy and contractor end with Contacts. An asset shows who services or insures it (Service, Insurer). A policy shows its Insurer, Broker and Advisor as contacts, with their phones; the Insurer and Broker typed as text on the policy stay as they are. A contractor shows the contact made for it (Contact).

Click a contact to open its page in [Contacts](contacts); **Remove link** takes the link away, and **Link a contact…** picks one, in its role, or creates a **New contact…** and links it. See [Contacts on other screens](contacts#on-other-screens).
