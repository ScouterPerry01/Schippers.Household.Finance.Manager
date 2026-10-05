# Medical claims

The **Medical claims** screen follows each medical or dental expense from the receipt, through every claim to an insurance plan, to what the household paid itself. It knows what each plan covers, sends each claim to the right plan in the right order, warns before the deadline to claim, shows what is left of each plan's yearly maximums, and feeds the medical expense tax credit. It is in the **Home and family** group of the menu.

> Important: The app does not give medical or tax advice. Amounts it expects a plan to pay are estimates from what you entered from your benefit booklet; the plan decides. Check the CRA's or Revenu Québec's rules before claiming the tax credit.

## The screen at a glance {#overview}
@index: health insurance; dental insurance; group insurance; benefits; claims

The screen has three tabs:

- **Expenses and claims**: each expense and where its claims stand. See [Expenses and claims tab](medical#expenses-tab).
- **Plans**: the household's plans, who they cover and what they pay. See [Plans tab](medical#plans-tab).
- **Coverage left**: what is left of each plan's yearly maximums, per person. See [Coverage left tab](medical#coverage-left).

Amounts on this screen are in Canadian dollars.

A good order to start: add your plans first, with their coverage, then record expenses. Without plans, every expense is simply closed with nothing to claim, but it still counts for the tax credit.

## How claims work {#how-claims-work}
@index: coordination of benefits; primary plan; secondary plan; spouse's plan

In Canada, when a person is covered by more than one plan, the plans pay in turn: usually the plan where the person is the employee pays first, then the spouse's plan pays part of what is left. Children are usually claimed first under the plan of the parent whose birthday comes first in the year; check your plans' rules.

The app follows this order. For each expense it looks at the active plans that cover the person, keeps those that cover that kind of care (a Health Spending Account covers everything), and sorts them by the order you set for that person in each plan. It then proposes the first plan not yet claimed: "To send to Sun Life by 2027-03-01 (about $60.00 back)". When that plan has paid, it proposes the next plan for what is still unpaid, and so on, until no plan is left or nothing is left to pay.

## Expenses and claims tab {#expenses-tab}

### The expense list {#expense-list}

At the top of the tab:

- **Person**: shows only one person's expenses, or **Everyone**. A new expense is proposed for the person chosen here.
- **Add an expense**: opens a blank expense. See [Add or edit an expense](medical#expense-form).
- **From the books (3)**: shown when payments in a health category are not yet recorded here; the number says how many. See [From the books](medical#from-books).
- **Only those still open**: ticked by default, it hides closed expenses. Untick it to see them all.

Each line shows the date of service, the person, the kind of care and description, and where it stands:

- "To send to plan by date (about amount back)": a plan is still to be claimed; the date is the deadline, and the amount what the plan should pay by its rules.
- "Waiting for payment": a claim was sent and has no answer yet.
- "Closed": nothing more to claim, because you closed it, no plan covers it, or nothing is left to pay.

On the right are the cost and, once something was reimbursed, "out of pocket" and what the household paid itself. Click a line to open the expense.

### Add or edit an expense {#expense-form}
@index: receipt; dental bill; physiotherapy; glasses; prescription receipt

The dialog is titled **Add an expense** or **Medical expense**.

- **Person treated**: the household member who received the care. Required. Only household members are offered; pets are not.
- **Kind of care**: the kind of service, such as **Prescriptions**, **Dental: cleaning and check-up**, **Physiotherapy** or **Eye exam**. It decides which coverage of each plan applies, so it is worth choosing well. The full list is in [Kinds of care](medical#kinds-of-care).
- **Date of service**: the day the care was received. Required. It decides the plan year the expense falls in, and the deadline to claim (the date of service plus the plan's **Days to send a claim**).
- **Date paid**: "If not the date of service". The date the bill was paid, which is the date that counts for the tax credit. Leave it empty when it is the same day. It cannot be more than a year before the date of service.
- **Cost**: what the care cost, in dollars. Required and above zero.
- **Description**: for example "Cleaning and X-rays" or the clinic's name. Shown in the list and in the medical expenses report.
- **Provider**: the provider from the **Providers** tab of the [Health](health) screen, or "(none)".
- **Medication**: shown when the kind of care is **Prescriptions** and the person has medications on the Health screen. It links the expense to that medication.
- **Counts for the medical expense tax credit**: ticked by default. Untick it for an expense the CRA does not accept (for example a cosmetic procedure or a non-prescription product). Only ticked expenses count in the tax credit figures and go into the receipts PDF.
- **Notes**.

Buttons:

- **Save**: saves the expense. A new expense must be saved before claims and receipts can be added; the claims part then appears below.
- **Close: nothing more to claim** (once saved): marks the expense closed, even if a plan could still be claimed. Use it when you decide not to claim, or when a plan's answer settled everything. It becomes **Open again**, which removes the mark.
- **Delete** (once saved): removes the expense with its claims, right away and without asking. It cannot be undone.
- **Close** (at the bottom): closes the dialog. Changes not saved with **Save** are lost.

### Claims on an expense {#claims}

Once the expense is saved, the **Claims** part lists each claim: the plan and its status (**sent**, **paid** or **refused**), the date sent and amount claimed, the date and amount paid, and the claim number. For a claim still waiting there are two buttons:

- **Record the payment**: see [Record the payment](medical#record-payment).
- **Refused**: marks the claim refused today, with nothing paid. The next plan in the order is then proposed.

Every claim also has **Delete**, which removes it at once, for example if it was entered by mistake. The expense then goes back to proposing that plan.

Below the claims:

- "Out of pocket: amount (reimbursed amount)": the cost less every payment received. This is what counts for the tax credit.
- Where the expense stands, as in the list.
- **Send to plan** when a plan is still to be claimed. See [Send to a plan](medical#send-claim).

### Send to a plan {#send-claim}

Choose **Send to plan** once you have sent the claim, on paper, online or through the pharmacy. The dialog says "By the plan's rules, about amount should come back."

- **Date sent**: today by default.
- **Amount claimed**: what you asked the plan to pay. It proposes what is still out of pocket. It must be above zero and no more than the cost.
- **Claim number**: the plan's reference, if it gave one.

**Save** records the claim as sent. The expense then shows "Waiting for payment" and the deadline reminder stops. A plan can be claimed only once per expense.

### Record the payment {#record-payment}
@index: explanation of benefits; EOB; reimbursement

When the plan answers, choose **Record the payment** on the claim. The dialog says "From the explanation of benefits or the deposit. Enter 0 if the claim was refused."

- **Date paid**: today by default.
- **Amount paid**: what the plan paid; the amount claimed is proposed. It cannot be negative or more than the cost.

**Save** marks the claim **paid**, or **refused** if the amount is 0. The out-of-pocket amount goes down by what was paid. If another plan covers the person, it is proposed next for what is left. Recording the payment here does not enter the deposit in an account; the deposit is entered in the account like any other.

### Receipts and explanations of benefits {#receipts}
@index: attach receipt; scan; review inbox

Under the claims, the dialog lists the documents attached:

- **Receipts (2)**: the receipts of the expense. These are the files put in the receipts PDF for the tax credit.
- **Explanation of benefits: plan (1)**: one line per claim, for the plan's statement.

Each line has:

- **Attach a file…**: choose a file on the computer (a PDF or a picture). It is added to the [Documents](documents) vault, filed, and attached here. A file of a kind the app cannot read is refused.
- **From the review inbox**: shown when documents are waiting in the review inbox, for example a receipt photographed with the phone. Pick one to attach it; it is marked as filed.

The files attached are listed below with their title and date.

### Close or reopen an expense {#close-expense}

An expense closes by itself when no plan is left to claim or nothing is left to pay. **Close: nothing more to claim** closes it by hand; **Open again** reopens it. A closed expense still counts for the tax credit. With **Only those still open** ticked, closed expenses are hidden from the list.

### From the books {#from-books}
@index: import from transactions; unrecorded health payments

If you pay medical bills from accounts recorded in the app, you do not have to type them twice. **From the books (number)** opens **Health payments not yet recorded**: "Payments in a health category over the last year that are not yet medical expenses. Choose the person and the kind of care, then add each one."

It lists every payment from the last year whose category is the Health category or one of its subcategories, except over-the-counter purchases, and that is not yet linked to a medical expense. Each line shows the date and payee, the account and the amount, with:

- **Person treated**: the first household member is proposed.
- **Kind of care**: **Other care** is proposed.
- **Add**: creates the expense, with the transaction's date as date of service, its amount as cost (converted to Canadian dollars at the day's rate for an account in another currency) and the payee as description. The expense is linked to the payment, so it is not offered again.

When everything is recorded, the dialog says "Every health payment is recorded." Open each new expense afterwards to correct the date of service or add the receipt.

## Plans tab {#plans-tab}
@index: employer plan; group benefits; RAMQ; Canadian Dental Care Plan; CDCP; HSA

The **Plans** tab lists the household's plans: their name (with "inactive" for one no longer in force), their kind, the insurer, and the people covered with their order, for example "Alex (pays first), Sam (pays second)". Click a plan to open it, or choose **Add a plan**.

When there is none: "No plans yet: add your employer's health and dental plans, a Health Spending Account, RAMQ or the Canadian Dental Care Plan."

### Add or edit a plan {#plan-form}

The dialog is titled **Add a plan** or **Plan**.

- **Kind of plan**: **Group health (employer)**, **Dental**, **Vision**, **Private health**, **Travel medical**, **Health Spending Account**, **RAMQ prescription drug plan**, **Canadian Dental Care Plan** or **Other plan**. Choosing a kind fills in **Name** when it is still empty. A **Health Spending Account** works differently: see [Health Spending Account](medical#hsa).
- **Name**: what you call the plan, for example "Sun Life (Alex's work)". Required. It appears in "Send to …" and in reminders.
- **Insurer**: the company that pays the claims.
- **Policy or group number** and **Certificate or member number**: from the plan card, for your claims.
- **Plan member**: the household member who holds the plan, usually the employee, or "(none)". For your reference.
- Who is covered and in what order: see [Who is covered and in what order](medical#coverage-order).
- **Plan year starts (month)** and **Plan year starts (day)**: when the plan's year begins, for example 1 and 1 for a calendar year, or 7 and 1 for a plan that renews on July 1. The month is 1 to 12 and the day 1 to 28. Yearly maximums and deductibles start over on this date.
- **Days to send a claim**: "After the date of service; often 365, or until a set date after the plan year ends." From 1 to 3650; 365 by default. The deadline of each expense is its date of service plus this number of days.
- **Yearly credit**: only for a Health Spending Account. See [Health Spending Account](medical#hsa).
- **Active**: ticked while the plan is in force. Untick it when the plan ends: it is no longer proposed for new claims, no longer counted in **Coverage left**, and shows "inactive" in the list. Its past claims are kept.
- **Notes**.

Buttons:

- **Save**: saves the plan. A new plan must be saved before coverage and booklets can be added.
- **Delete** (once saved): removes the plan at once, without asking. A plan that already has claims cannot be deleted; untick **Active** instead.
- **Close**: closes the dialog; changes not saved are lost.

### Who is covered and in what order {#coverage-order}

"Who the plan covers, and in what order it pays for each: first for the plan member and their children, second for a spouse who has a plan of their own."

The form shows one choice per household member:

- **Not covered**: the plan does not cover this person (the default).
- **pays first**: this plan is claimed first for this person.
- **pays second**: claimed after the plan that pays first.
- **pays third**: claimed after the first two.

Example: Alex and Sam each have a plan at work and cover each other. On Alex's plan, set Alex and the children to **pays first** and Sam to **pays second**. On Sam's plan, set Sam to **pays first** and Alex and the children to **pays second** (or follow the birthday rule for the children, as your plans require).

When two plans have the same order for a person, a Health Spending Account is claimed after the other one.

### Coverage for each kind of care {#coverage-form}
@index: deductible; percentage; annual maximum; per visit maximum; frequency limit

Once the plan is saved (and it is not a Health Spending Account), the **Coverage** part lists "What the plan pays for each kind of care, from the benefit booklet." Each line shows the kind of care and its rule, for example "80 % · deductible $25.00 · up to $500.00 a year · once every 24 months". Click a line to change it, or choose **Add coverage**.

A plan covers a kind of care only if it has a coverage line for it. Without a line, the plan is skipped for that kind of care.

The **Coverage** dialog:

- **Kind of care**: the kind of service this rule is for; **Prescriptions** by default. Add one line per kind your booklet lists.
- **Percentage paid**: the share the plan pays, from 0 to 100; 80 is proposed. A "%" sign may be typed.
- **Deductible**: the amount the person pays first each plan year before the plan pays, for this kind of care. Optional.
- **Most per visit**: the most the plan pays for one expense, for example $30 per massage. Optional.
- **Most per year**: the most the plan pays in a plan year for this person and this kind of care, for example $500 for physiotherapy. Optional. What is left appears on the **Coverage left** tab.
- **Once every (months)**: "For example 24 for an eye exam every two years, 9 for a dental recall." From 1 to 120. Optional. The **Coverage left** tab then shows when the service is covered again.
- **Save**, **Cancel**, and **Delete** for a saved line (it deletes at once).

### Health Spending Account {#hsa}
@index: health care spending account; HCSA; wellness account

A Health Spending Account is a yearly amount the employer puts aside to reimburse health and dental costs, often what the other plans did not pay.

For a plan of the kind **Health Spending Account**:

- **Yearly credit**: the amount available each plan year, in dollars.
- It has no coverage lines: it pays 100 % of what is left of any expense, up to what remains of the yearly credit.
- The credit is shared by everyone the account covers: what is used for one person is no longer there for the others.
- Put it last in the order (for example **pays second** after the group plan), so it pays what the other plans leave.

### Benefit booklets {#booklets}

Once the plan is saved, **Benefit booklets** lets you attach the plan's booklet or card, with **Attach a file…** or **From the review inbox**, as for receipts. They are kept in the [Documents](documents) vault.

## Coverage left tab {#coverage-left}
@index: remaining coverage; maximum left; eligible again

"What is left of each plan's yearly maximums this plan year, and when a limited service is covered again."

For each household member covered by an active plan, the tab lists every plan and kind of care:

- "$350.00 left this year": the yearly maximum less what the plan paid or was claimed for this person and this kind of care since the start of the current plan year. A claim still waiting counts at the amount claimed; a refused claim counts for nothing.
- "covered again from date": for a kind of care with **Once every (months)**, the date of the person's last such service plus that many months, when that date is still to come.
- "no yearly limit" when neither applies.
- For a Health Spending Account, the line reads **Health Spending Account credit** and shows what is left of the yearly credit for the whole account.

## What a plan is expected to pay {#expected-payment}

The amount in "about … back" and in "By the plan's rules, about … should come back" is worked out from the coverage you entered:

1. Start from the cost, less what plans earlier in the order paid (or were claimed, while waiting).
2. Take off what is left of the deductible for this person and kind of care in the plan year.
3. Apply the **Percentage paid**.
4. Limit it to **Most per visit**, then to what is left of **Most per year**.

Expenses earlier in the same plan year use up the deductible and the maximum first. It is only an estimate: the plan's answer is what you record with **Record the payment**.

## Claim deadlines and reminders {#deadlines}
@index: claim deadline; reminder; time limit

The deadline to send an expense to the next plan is its date of service plus that plan's **Days to send a claim**. From 30 days before the deadline, until 30 days after it, an expense still to send appears in the reminders at the top of the window and in the system notification, as "claim to send" with the person, the description and the plan. Clicking the reminder opens the Medical claims screen. The deadline also appears on the [Calendar](calendar).

Sending the claim, closing the expense, or recording the last plan's payment ends the reminder.

## The medical expense tax credit {#tax-credit}
@index: METC; line 33099; line 33199; line 381; medical expense credit; CRA; Revenu Québec

The **Medical expenses** report under [Reports](reports) gathers the year's figures for the non-refundable medical expense tax credit. It shows the costs, reimbursements and out-of-pocket amounts of the expenses paid in the year, per person, a table of every expense with its date paid and date of service, and then the credit. The year-end package on the Taxes screen uses the same figures (see [Year-end package](taxes#year-end-package)).

In short, as the report explains: federally (line 33099) and in Quebec (line 381), expenses paid in any 12 consecutive months ending in the year can be claimed, once. The household's own expenses (spouses and children under 18) are claimed together, usually by one spouse; an adult dependant's are claimed separately (federal line 33199). Only the amount above a threshold counts: 3 % of net income federally (or a set amount if lower), 3 % of family income in Quebec.

What the app counts:

- only expenses with **Counts for the medical expense tax credit** ticked;
- only what is out of pocket: the cost less what plans paid;
- on the date paid, or the date of service when there is no date paid.

People whose kind under **Household members** is **Other dependant** are treated as adult dependants and claimed on their own line; adults and children are part of the household's claim. See [Household members](members).

### The best 12-month period {#best-period}
@index: 12-month period; best period; any 12 months

The report finds, for the household and for each person, the 12-month period ending in the chosen year with the most eligible out-of-pocket expenses: "Best 12 months for the household: date to date, amount out of pocket." The table shows, for each person, the province, the best period, its total, the calendar-year total for comparison, and the line it is claimed on.

The best period always ends on the date of an expense in the year. When two periods give the same total, the earlier one is chosen, which leaves later expenses for next year's claim.

> Important: An expense can be claimed only once. If last year's return already used some of these months, choose your period so they do not overlap.

### Which spouse should claim {#which-spouse}
@index: spouse; lower income spouse; net income line 23600

When the report is for **Everyone** and the household has at least two adults, **Which spouse should claim?** compares the two:

- **Net income, name**: each spouse's expected net income for the year (line 23600 of the return). Leave other adults empty. The books do not hold net income, so you type it in; it is not saved.
- **CRA fixed amount, year**: the fixed amount the CRA sets each year. It is filled in for the years the app knows (2023 to 2026; $2,890 for 2026, as published by the CRA); enter it for later years from the CRA's website.

With two incomes entered, the report shows for each spouse "Claimed by name: amount counts for the federal credit": the best period's total less 3 % of that spouse's net income, or less the fixed amount when that is lower. Then one of:

- "name should claim: amount more counts for the credit." Usually the spouse with the lower net income.
- "Either spouse: the same amount counts."
- "Neither: the expenses are below both thresholds this time."

"Indicative only, not tax advice. The credit is not refundable: the spouse who claims needs enough tax to pay to use it." In Quebec, the provincial threshold uses the family's income, so it is the same whoever claims.

### Receipts as one PDF {#receipts-pdf}
@index: receipts bundle; CRA review; proof

Under the best period, **Receipts for the household as one PDF…** saves one PDF with a cover page listing every eligible expense of the period (date, person, kind of care, description, out-of-pocket amount and total), followed by every receipt attached to those expenses. Adult dependants have their own button, **Receipts for name as one PDF…**. Keep the file in case the CRA or Revenu Québec asks for the receipts.

## Kinds of care {#kinds-of-care}
@index: services; dental; vision; paramedical

The **Kind of care** list, used for expenses and coverage:

- **Prescriptions**
- **Dental: cleaning and check-up**, **Dental: basic care**, **Dental: major care**, **Orthodontics**
- **Eye exam**, **Glasses and contact lenses**
- **Physiotherapy**, **Massage therapy**, **Chiropractic**, **Psychology**, **Osteopathy**, **Naturopathy**, **Podiatry**, **Acupuncture**, **Speech therapy**
- **Hearing aids**, **Medical equipment and supplies**
- **Hospital**, **Ambulance**, **Laboratory tests**
- **Care while travelling**, **Travel for medical care**
- **Plan premiums**: premiums you pay yourself for a private health plan, which can count for the tax credit.
- **Other care**

A medical trip logged in the [Trip log](trips) can be added here as **Travel for medical care**.
