# Taxes

Taxes gathers the tax year in one place: the slips each person should receive, donations and their official receipts, tax instalments, and a year-end package of figures for the return. It is in the **Reports and taxes** group of the menu.

The app does not prepare or file a return. It gathers what the books already know, so that at tax time you, or your accountant, have the figures, the slips and the receipts together.

> Important: These figures help prepare a return; they are not tax advice. The slips and receipts are what counts: check every amount against them, and ask a tax professional when in doubt.

## The Taxes screen {#taxes-screen}

@index: tax; income tax; tax return; tax season

Under the title, five tabs:

- **Slips**: the slips to expect from employers, payers and institutions, and whether they have arrived. See [Slips](taxes#slips).
- **Donations**: every donation in the books, by person, with its official receipt. See [Donations](taxes#donations).
- **Instalments**: the instalment schedule from the CRA or Revenu Québec, and what has been paid. See [Instalments](taxes#instalments).
- **Year-end package**: each person's figures for the return, with the line each goes on, and a folder for the accountant. See [Year-end package](taxes#year-end-package).
- **Estimate**: an estimate of each person's federal and provincial or territorial income tax, and of the balance owing or the refund. See [Estimate](taxes#estimate).

Each tab has its own **Tax year** at the top.

- On **Slips**, **Year-end package** and **Estimate**, the year starts as the tax year being prepared: last year until the end of June, then the current year. You can choose the current year or one of the six before.
- On **Donations**, the year starts as the current year, with the six before also offered.
- On **Instalments**, the year starts as the current year; next year and the three before are also offered, so you can enter next year's schedule when the reminder arrives.

Much of what this screen shows comes from the way transactions are entered through the year: see [Through the year](taxes#through-the-year).

## Slips {#slips}

@index: tax slips; T4; T4A; T5; T3; T5008; RL-1; Relevé 1; RL-24; tax receipts; checklist

The **Slips** tab is a checklist of the slips each person should receive for the tax year, worked out from the books, plus any you add by hand. Most slips arrive by the end of February; T3 and RL-16 slips by March 31.

The list is grouped by person, the household last. Each person's heading says how many slips have arrived, such as "3 of 5 received"; slips marked not expected are not counted. Each slip shows:

- its kind, such as "T4, employment income";
- who it comes from (the employer, payer or institution);
- why it is expected, such as "Salary or wages from this payer", or "Added by hand";
- its status: **Expected**, **Received** or **Not expected**. After March 31 of the following year, slips still expected are shown in red: they should have arrived;
- the number of files attached, if any.

- **Add a slip…**: adds a slip the books cannot know about. See [Add a slip](taxes#add-slip).

Click a slip to change its status and attach it. See [A slip's window](taxes#slip-window).

"No slips expected yet" means nothing in the year's books calls for a slip so far. Slips appear as salary, pensions, benefits, plan activity and investment income are recorded.

### How the expected slips are found {#expected-slips}

@index: slip expected; why slip; Quebec slips; Relevé

The app looks at the year's transactions, by payee and by person (the **For** field of the transaction line, or else the account's only owner):

- Pay on **Salary and wages**, **Bonuses and commissions** or **Employment income**: a T4, "T4, employment income", from the payee.
- **Employer pension**: a T4A. **QPP / CPP**: a T4A(P). **Old Age Security**: a T4A(OAS). **RRIF and annuity payments**: a T4RIF.
- **Employment Insurance** or **Employment Insurance / QPIP**: a T4E.
- **Interest** of $50 or more in the year from the same payer: a T5. Below $50, no slip is expected (the threshold is in [Rates and rules](rates-rules)).
- **Tuition** paid: a T2202 from the school.
- **Child care** paid, for a person who files in Quebec: an RL-24.
- Money moved into an RRSP from outside the registered plans (a transfer from another account, or a deposit with no transfer and no category, such as cash imported from a brokerage file): an RRSP contribution receipt, for the owner (for a spousal RRSP, for the contributor). Money taken out of an RRSP: a T4RSP. Out of a RRIF, spousal RRIF or LIF: a T4RIF. Any transfer in or out of an FHSA: a T4FHSA. The issuer is the plan's institution, or the account name.
- Investment income in a non-registered account: a T5 from the institution, or a T3 from each Canadian fund, as worked out in the investment income report (see [Investment income and capital gains](reports#investment-income)).
- A sale of securities in a non-registered investment account: a T5008 for each owner.

For a person who files in Quebec (from the province set for them, or the household's), the matching Relevé is expected too: RL-1 with a T4, RL-2 with a T4A, T4A(P), T4RSP or T4RIF, RL-3 with a T5, RL-16 with a T3, RL-18 with a T5008, RL-8 with a T2202.

Income in registered plans gives no slip until money comes out. A transaction without a payee gives no slip, since there is no one to expect it from.

### A slip's window {#slip-window}

Click a slip to open its window. The title is the kind of slip; the first line gives the person, the issuer and the year, then why it is expected.

- **Status**: **Expected**, **Received** or **Not expected**. Mark it received when it arrives, or not expected when it will not come this year (an account under the interest threshold, a slip sent to someone else). Setting an expected slip back to **Expected** clears your mark.
- **Slip files**: the slip itself, kept in the documents vault. The count is shown in brackets.
  - **Attach a file…**: choose a PDF or photo of the slip on this computer. It is stored in the vault, filed, and linked to this slip.
  - **From the review inbox**: shown when documents are waiting in the review inbox (for example a slip photographed with the phone). Choose one to link it to this slip and file it.
- **Remove this slip**: only for a slip added by hand. Removes it from the checklist at once; the files attached stay in the vault.
- **Save**: saves the status. **Cancel**: closes without changing the status. Files are attached at once, without waiting for **Save**.

The slips attached here are copied into the folder for the accountant (see [Folder for the accountant](taxes#accountant-folder)). Changing the status or adding slips needs the right to edit the account group the slip belongs to.

### Add a slip {#add-slip}

**Add a slip…** opens "Add a slip for" the tax year shown.

- **Person**: who receives the slip, or **Household** when it is no one in particular.
- **Slip**: the kind of slip, such as **T4A, pension or other income** (the default), **RL-24, child care**, **T2202, tuition** or **Other slip**. Every federal and Quebec slip the checklist knows is offered.
- **From (employer, institution or payer)**: who sends the slip. Required.
- **Save**: adds it to the checklist as expected; it is marked "Added by hand". **Cancel**: closes.

Use it for a slip the books cannot predict: a T4A for contract work, an RL-24 from a day camp, a slip from a payer you do not record.

## Donations {#donations}

@index: donation; charity; charitable donation; political contribution; donation receipt; tax receipt; gifts

The **Donations** tab shows every payment on a category whose tax treatment is charitable donations or political contributions (such as the categories **Charitable donations** and **Political contributions**), or on a split line marked so, dated in the tax year. Each person's gifts are shown apart, from the **For** field of the transaction line.

Spouses or common-law partners may claim each other's donations; claiming them together on one return usually gives a larger credit, and donations can be carried forward up to five years.

At the top, a card per person gives:

- **Charitable**: the total eligible amount of charitable gifts.
- **Political**: the total eligible amount of political contributions, when there are any.
- **Receipts missing**: how many gifts still have no receipt, in red.

The totals count gifts in Canadian dollars only.

Under the cards, each gift shows its date, the person, the charity (or the payee), its kind and receipt status, and its amount:

- **Receipt received**: marked received in its window, or a file is attached to the transaction.
- **On the T4 slip (box 46)**: a gift deducted from pay (see [Pay from a pay stub](taxes#pay-stub)); the T4 is the receipt. Such a gift shows the charity from the line's memo, such as "United Way, through Acme payroll".
- **Receipt missing**: in red.
- **Eligible …**: shown under the amount when the receipt's eligible amount is smaller than the gift.

A refund of a donation on the same transaction reduces it; a gift that comes to nothing is not shown. Click a gift to enter its receipt.

### Official receipt {#official-receipt}

@index: registration number; charity number; eligible amount; advantage

The window shows the gift's date, payee and amount, then:

- **Charity or party, as on the receipt**: the name to show and to give the accountant. It starts as the payee (or, for a payroll gift, the line's memo).
- **Registration number**: for charitable gifts only. Nine digits, RR and four digits, such as 123456789RR0001; spaces and dashes are removed. Any other form is refused. Optional.
- **Receipt number**: as printed. Optional.
- **Eligible amount**: when you received something in return (a dinner, an auction prize), the receipt shows a smaller eligible amount: enter it. Leave it empty when the whole gift is eligible. It cannot be more than the gift: the donation lines of the transaction, so for a gift through payroll the amount given, not the whole deposit. The eligible amount is what the totals and the year-end package count.
- **Receipt received**: tick when you have the receipt. It starts ticked when a file is already attached to the transaction.
- **Receipt files**: the receipt itself. **Attach a file…** stores a file in the vault and links it to the transaction; **From the review inbox** links a document waiting there. These files go into the folder for the accountant.
- **Save**: saves the receipt details. **Cancel**: closes without saving them.

Each gift has its own receipt details: when one payment holds gifts for two people (or a charitable gift and a political one), each appears on its own line here, and each line keeps its own receipt. A receipt recorded with an earlier version for the whole payment still applies to the gifts that have none of their own, its eligible amount shared between them in proportion to each gift, so it is counted once. Saving needs the right to edit the account's group.

## Instalments {#instalments}

@index: tax instalments; instalment payments; quarterly tax; CRA reminder; Revenu Québec; acomptes provisionnels

Some people pay their income tax during the year in instalments instead of at filing: usually those whose tax owing at filing is more than $3,000 ($1,800 in Quebec) this year and in either of the two years before, such as retirees or the self-employed. The CRA and Revenu Québec send reminders with the amounts. Instalments are due March 15, June 15, September 15 and December 15 (dates kept in [Rates and rules](rates-rules)); a payment made the next business day after a weekend or holiday is on time.

The **Instalments** tab lists the instalments of the tax year, grouped by person and authority, such as "Jean · Canada Revenue Agency". Click the heading, or **Change** beside it, to change them. Each instalment shows:

- its due date;
- its state: **Paid**, **Partly paid** (with the amount paid so far, such as "$400.00 paid"), **Due**, or **Late** in red when the date has passed and it is not fully paid;
- its amount.

- **Set up instalments…**: enters a new schedule. See [Instalments for a year](taxes#instalment-window).

"No instalments for this year." means none were entered for the year shown.

Instalments not fully paid that are due within 30 days, or up to 30 days late (the default window, set in [Rates and rules](rates-rules)), appear among the app's reminders as "tax instalment", with the authority, and lead to this screen.

### Instalments for a year {#instalment-window}

The window "Instalments for" the year:

- **Person**: who the instalments are for, or **Household**.
- **Paid to**: **Canada Revenue Agency** or **Revenu Québec**. A person in Quebec usually has two schedules, one for each.
- **Paid from**: the bank account the payments come out of. Only bank accounts are offered. Required.
- **Due March 15, …**, **Due June 15, …**, **Due September 15, …**, **Due December 15, …**: the four amounts from the reminder, without a minus sign. An empty or zero amount leaves that date out.
- **Save**: saves the schedule. Changing the person, the authority or the account of an existing schedule moves it: the old one is replaced. **Cancel**: closes.

To remove a schedule, open it and empty the four amounts, then save. Saving needs the right to edit the account's group.

### How payments count {#instalment-payments}

@index: Tax instalments category

Payments count towards a schedule when they are:

- on the **Tax instalments** category,
- in the account chosen in **Paid from**,
- dated from January 1 of the tax year to January 31 of the next year,
- for the same person, or for no one in particular,
- and to the right authority: a payment counts for Revenu Québec when its payee names it ("Revenu Québec", "RQ" or "ministère du Revenu"), and for the CRA otherwise.

Payments count towards the earliest instalments first. The amounts paid go into the year-end package as **Instalments paid**.

## Year-end package {#year-end-package}

@index: tax package; tax summary; accountant; T1; return figures; line numbers

The **Year-end package** tab shows each person's figures for the return, gathered from the books, with the federal line each one goes on. Choose the **Tax year** and the **Person**; the household (amounts that belong to no one in particular) comes last.

Each line shows the item, where it comes from (a payer, an account, a period), the line or form, and the amount. Lines from the same source are added together. Amounts in other currencies are converted to Canadian dollars at each transaction's date. Registered plans' own transactions are left out.

"Nothing for this year yet" means no pay, deductions, credits or slips have been recorded for the year.

### What each section gathers {#package-sections}

@index: employment income; CPP; EI; QPIP; RRSP deduction; FHSA deduction; union dues; child care; moving expenses; medical expenses; tuition; income tax deducted; self-employment; T2125; foreign tax; taxable dividends

- Employment: **Employment income** (line 10100) from salary, wages, bonuses and commissions; **CPP or QPP contributions** (line 30800) and **EI premiums** (line 31200) deducted from pay. In Quebec, EI and QPIP premiums share one category and appear as **EI and QPIP premiums**, with no single federal line.
- Pensions and benefits: **Old Age Security pension** (11300), **CPP or QPP benefits** (11400), **Other pensions** (11500), **RRIF income**, **Employment Insurance benefits** (11900).
- Investments: **Taxable dividends** (12000, the grossed-up amounts of T5 boxes 11 and 25 and T3 boxes 32 and 50), **Interest and other investment income** (12100, from slips and from the **Interest** category), **Taxable capital gains** (12700: half of the net gains, including capital gains on slips, when positive), **Foreign tax paid** (form T2209). These come from the investment income report: see [Investment income and capital gains](reports#investment-income).
- Self-employment: **Self-employment income** and **Self-employment expenses** (form T2125), from categories whose tax treatment is self-employment; **Sales tax paid on business expenses**, by tax, from the sales tax recorded on those expenses (see [Sales tax included](taxes#sales-tax)).
- Deductions: **Pension plan contributions** (20700), **RRSP contributions** (20800, from contributions to RRSPs and from group RRSP deductions on pay), **FHSA contributions** (20805), **Union and professional dues** (21200), **Child care expenses** (21400), **Moving expenses** (21900), **Other employment expenses** (22900).
- Credits: **Medical expenses** (33099) as the Medical expenses report claims them: the expenses of the spouses and children together, over the household's best 12-month period, in the household's package, since one spouse claims them all; and **Medical expenses for an adult dependant** (33199), one line per adult dependant over their own best period, also in the household's package. The medical receipts of those periods go with the household's package in the folder for the accountant. Then **Tuition** (Schedule 11), **Charitable donations** (Schedule 9) and **Political contributions** (40900), at their eligible amounts.
- Tax already paid: **Income tax deducted** (43700, income tax taken off pay; tax paid at filing is not counted) and **Instalments paid** (47600, by authority).

The person for each amount is the person the transaction line is for, or else the account's only owner; otherwise it goes under the household.

### Notes under the package {#package-notes}

Under the lines, and at the end of every export, notes say what is still missing and what to check:

- Slips still expected: the slips of that person still marked **Expected** on the **Slips** tab, to chase before filing.
- For RRSP contributions: the deduction for a year uses contributions from March 2 of that year to March 1 of the next; contributions in the first 60 days of the year may have been claimed the year before.
- For a person in Quebec: Quebec's return has its own lines; the Relevés give the amounts for it.
- The notice that these figures are not tax advice.

### Folder for the accountant {#accountant-folder}

@index: accountant package; export tax package; send to accountant

**Folder for the accountant…** asks for a folder, then creates in it a folder "Tax package" and the year. In it, for every person in the package (not only the one shown):

- a PDF and an Excel file of the person's summary, named after the person;
- a folder named after the person, with a copy of each document behind the figures: the slips attached on the **Slips** tab, the donation receipts attached to the gifts, and the receipts of the medical expenses counted.

The folder opens when it is done, and a line says where it was saved. Making it again replaces the files of the same name.

### Export one summary {#export-summary}

The **CSV**, **Excel** and **PDF** buttons beside **Folder for the accountant…** save the summary of the person shown only, with its notes, after asking where. The formats are those of the reports: see [The table, export and print](reports#table-export).

## Estimate {#estimate}

@index: income tax estimate; tax estimate; refund; balance owing; how much tax; tax calculator; marginal rate; average rate

The **Estimate** tab works out roughly how much income tax a person will pay for a year, from the figures of the year-end package and the rates of the person's province or territory, and compares it with the tax already deducted from pay and paid in instalments. It shows the result, every step of the calculation, and where each figure comes from. You can change any figure to see its effect, for example to try an RRSP contribution before the deadline.

> Important: This is an estimate, not a return and not tax advice. It applies the main rules to the figures shown and leaves some out (see [What the estimate leaves out](taxes#estimate-left-out)). Your return, your tax software or your accountant, and your notice of assessment have the final word.

At the top:

- **Tax year**: the year to estimate. It starts as the tax year being prepared (last year until the end of June, then the current year); the current year and the six before are offered. Built-in rates start with 2024: for an earlier year, the tab says there are no rates.
- **Person**: whose tax to estimate. The adults and adult dependants of the household are offered, and anyone else with figures in the package. Each person files their own return, so each has their own estimate.
- **Use the books' figures again**: shown once you have changed a figure or the age box; puts every figure back as the books have it, and forgets what was kept for this person and year.

The red line under them is the reminder that this is an estimate. "No one to estimate yet" means the household has no adult member: add them under [Household members](members).

The left side lists the figures used; the right side shows the result and the calculation.

### Figures used {#estimate-figures}

@index: estimate inputs; override tax figures

Under **Figures used**, a line says whose rates apply: those of the province or territory the person lives in (their **Lives in** on Household members), or else the household's. Then:

- **65 or older on December 31**: ticked when the person's birth date says they are 65 or older at the end of the year. It gives the age amount, and counts RRIF income as pension income. Change it when the birth date is not entered; the change is kept.

Each figure is an amount in dollars, with a line under it saying where it comes from: **From the year-end package** and the package items added together, **Not in the books** when the books have no source for it, or **Entered here, kept for this person and year** once you change it. What you enter is kept with the books, for this person and this year, and comes back the next time you open the estimate; it never changes a transaction or the package. **Use the books** beside a changed figure forgets it and puts the books' amount back. A user who cannot change any account group sees **Entered here, not kept: you cannot change any account group**, and what they enter is forgotten when they choose another person or year or leave the screen. A blank amount counts as zero. An amount can be a sum, such as 1200 + 350.

The figures are grouped as on a return.

Income:

- **Employment income**: from **Employment income** in the package (salary, wages, bonuses on pay stubs and income categories).
- **Pension income eligible for the pension amount**: **Other pensions** (an employer pension), plus **RRIF income** from age 65. It gives the pension income amount.
- **Old Age Security pension**: from **Old Age Security pension**, the pension received before any amount withheld. It gives the OAS recovery tax at high incomes.
- **Other income (CPP or QPP, EI, plan withdrawals)**: **CPP or QPP benefits** and **Employment Insurance benefits**, plus **RRIF income** before 65. Add here any other taxable income the books do not show, such as RRSP withdrawals.
- **Interest and other investment income**: from **Interest and other investment income**.
- **Eligible dividends (taxable amount)** and **Other dividends (taxable amount)**: the grossed-up amounts of the T5 and T3 slips (T5 boxes 25 and 11, T3 boxes 50 and 32), from the investment income report. They give the dividend tax credit.
- **Taxable capital gains**: from **Taxable capital gains** (half of the net gains of the year).
- **Self-employment income, net of expenses**: **Self-employment income** less **Self-employment expenses**; it may be negative.

Deductions:

- **RRSP deduction**: from **RRSP contributions**, the contributions of the year. The deduction cannot be more than the RRSP deduction limit on the notice of assessment: enter that limit under **RRSP deduction limit** (see below) and the estimate keeps the deduction within it.
- **FHSA deduction**: from **FHSA contributions**.
- **Registered pension plan contributions**: from **Pension plan contributions** deducted from pay.
- **Union and professional dues**: from **Union and professional dues**.
- **Child care expenses**: from **Child care expenses**. Usually the spouse with the lower income claims them, within limits per child; enter the amount that can be claimed.
- **Other deductions (moving, employment expenses)**: **Moving expenses** and **Other employment expenses**.

Credits:

- **CPP or QPP contributions**: from **CPP or QPP contributions** on pay stubs. The base part is a credit; the enhanced part and the second contribution are a deduction (see [The calculation](taxes#estimate-lines)).
- **EI premiums (and QPIP in Quebec)**: from **EI premiums**, or **EI and QPIP premiums** in Quebec.
- **Charitable donations**: from **Charitable donations**, at their eligible amounts. Spouses may claim each other's: enter here the gifts this person will claim.
- **Medical expenses claimed**: not filled in, because the household's medical expenses are claimed together by one spouse. A line under the field gives the household's claim from the package; enter it for the spouse who claims it. The threshold (3 % of net income or the fixed amount) is applied by the estimate, so enter the expenses themselves.
- **Tuition fees of the year (T2202, RL-8)**: from **Tuition** in the package, the fees paid on categories whose tax treatment is tuition. The amount that counts is the one on the student's T2202 slip (the RL-8 in Quebec): check it, and enter it if it differs. Under the field, the T2202 and RL-8 slips of the person's slip checklist are listed with their status. The student claims tuition first, as far as needed to bring their own tax to zero: see [Tuition and carry-forwards](taxes#estimate-carry-forward).
- **Tuition to transfer to a spouse or parent**: for the student. Empty means nothing is transferred. Enter the amount the student wants to transfer: the estimate transfers at most what is left of this year's fees after their own claim, and no more than $5,000 less the part of this year's fees they used (in Quebec, to a parent or grandparent only, with no dollar maximum). What is not transferred is carried forward.
- **Tuition transferred from a student**: for the spouse, parent or grandparent who receives it: the amount the student transfers, as the student's estimate works it out. At most $5,000 counts, federally and in the provinces and territories that still have the credit; in Quebec, it is fees transferred by a child or grandchild, credited at 8 %.

Family: the household situation that the spouse amount, the refundable credits and the benefits depend on.

- **Spouse's or partner's net income**: empty means the person has no spouse or common-law partner, and counts as single. Enter the net income of a spouse or common-law partner: it claims the spouse amount, which falls as their income rises, and makes the family figures those of a couple (the workers benefit, the GST/HST credit, the child benefit and, in Quebec, the work premium and the premiums). In Quebec, it is also added to the person's own income to form the family income that reduces the age and retirement amounts and sets the medical expense threshold.
- **Spouse's or partner's working income**: their employment and self-employment income, for a couple's Canada workers benefit and Quebec work premium. Empty counts as zero.
- **Children under 18 living with this person** and **Of whom under 6**: counted from the household members of kind child who have a birth date (**From Household members**), by their age on December 31. Children make a single person a single parent for the workers benefit and the GST/HST credit, and give the Canada child benefit. Change them when the members list is not complete, or for a parent who does not live with the children.
- **Months covered by the public prescription drug plan (Quebec)**: shown for a Quebec resident only. Empty means covered all year (12 months), the usual case for someone without a group plan. Enter 0 for a year covered by a group plan (through work, or a spouse's or parent's plan) or when exempt (under 18, a full-time student under 26 living with their parents, last-resort assistance, 65 or older with at least 94 % of the maximum Guaranteed Income Supplement), or the number of months covered by the public plan. The premium is counted for those months.

Carried forward from earlier years: the balances that last year's notice of assessment gives. Each is kept for this person and year. Empty means none.

- **Unused federal tuition amounts**: the federal tuition amounts not used yet.
- **Unused provincial or Quebec tuition amounts**: the provincial or territorial ones; for a Quebec resident, the unused fees on the Quebec notice of assessment. They can still be claimed in Ontario, Saskatchewan and Alberta, which no longer give a credit for new fees.
- **Donations of the last five years not claimed yet**: gifts of the five previous years not claimed. With this year's, they are claimed up to 75 % of net income; the rest is carried forward.
- **Net capital losses of other years**: at their taxable amount (half the losses), as the notice of assessment gives them. They only reduce this year's taxable capital gains, so they lower taxable income, not net income.
- **Unused RRSP contributions**: contributions made in earlier years and not deducted yet. They are deducted with this year's.
- **RRSP deduction limit**: the limit on the notice of assessment. Empty means no limit is applied. When it is entered, the RRSP deduction (this year's contributions and the unused ones) stays within it, and the rest is carried forward.
- **Minimum tax carried forward (federal)**: additional tax paid for the alternative minimum tax in the seven previous years and not recovered yet (Form T691 of those years). In a year without minimum tax, it brings the regular federal tax down to the minimum tax, and the provincial or territorial tax by its share.

Tax already paid:

- **Income tax deducted**: from **Income tax deducted** (income tax taken off pay, federal and Quebec together).
- **Instalments paid**: from **Instalments paid** to the CRA and to Revenu Québec.

### The result {#estimate-result}

@index: balance owing; refund estimate; average tax rate; marginal tax rate

The box at the top right gives:

- **Federal tax**: the federal tax after credits (after the Quebec abatement for a Quebec resident).
- The provincial or territorial tax, named after the province or territory: after credits, with any surtax, reduction or health premium.
- **Total income tax**: the two together.
- **Other amounts on the return**: shown when there are any: the OAS recovery tax and, in Quebec, the contribution to the health services fund and the prescription drug insurance premium. They are added to the balance.
- **Refundable credits**: shown when there are any: the Canada workers benefit, the refundable medical expense supplement and, in Quebec, the work premium and the refundable credit for medical expenses. They come off the balance even below zero.
- **Deducted at source and instalments**: **Income tax deducted** and **Instalments paid**.
- **Balance owing** or **Refund**: the total income tax, plus the other amounts on the return, less the refundable credits and what was already paid. A balance owing is due by April 30 of the next year.
- **Average rate**: the total income tax as a share of total income.
- **Marginal rate**: the tax on one more dollar of ordinary income (such as interest), federal and provincial together, with the OAS recovery tax and Quebec's contributions; it is what an RRSP deduction saves, roughly, on each dollar.

When the rates for the year are not yet in Rates and rules, a red line says that those of the latest year known are used.

Under it, **Carried forward** shows, when there is any, what becomes of each balance this year: tuition (federal, and provincial or Quebec), donations, net capital losses and unused RRSP contributions. Each line gives what is available (with this year's own fees or gifts), what is used this year, what a student transfers, and what is **Left** for the following years. Enter what is left in next year's estimate under Carried forward from earlier years; the notice of assessment confirms it.

### The calculation {#estimate-lines}

@index: tax brackets; basic personal amount; non-refundable credits; surtax; Ontario Health Premium; Quebec abatement

Under the result, the calculation line by line, in three parts. Each line shows its amount, and below its name how it was worked out (a rate of an amount, or the amount it is based on).

Income:

- **Total income**: all the income figures added together.
- **CPP or QPP enhanced contributions (deduction)**: the part of the contributions that is deducted rather than credited.
- **RRSP deduction, with unused contributions**: shown when unused contributions or a deduction limit are entered: the RRSP deduction, with what could be claimed below its name.
- **Deductions**: the deductions, with that part.
- **Social benefits repayment (deduction)**: the OAS recovery tax, deducted from net income: 15 % of net income above the threshold, at most the OAS received.
- **Net income**: total income less the deductions (never below zero).
- **Net capital losses of other years**: the losses carried forward that are applied, up to this year's taxable capital gains.
- **Taxable income**: net income less those losses; without them, the two are the same.

Federal, then the province or territory:

- **Tax bracket**: one line per bracket reached, with its rate, the income taxed at that rate and where the bracket starts.
- **Tax on taxable income**: the brackets added together.
- Credit amounts: **Basic personal amount** (reduced at high incomes where the rules say so), **Age amount** (from 65, reduced above an income threshold), **Senior supplementary amount** (Saskatchewan), **Spouse or common-law partner amount**, **Canada employment amount** (federal and Yukon), **CPP or QPP contributions (base part)**, **EI premiums**, **Pension income amount**, **Medical expenses above the threshold**, **Tuition amount** (the student's own claim, as far as needed to bring the tax to zero) and **Tuition transferred from a student**.
- **Total of the credit amounts**, and **Non-refundable tax credits**: that total at the lowest rate.
- **Top-up or supplemental tax credit**: the federal top-up credit, which keeps 15 % on credit amounts above the first bracket since the lowest rate went down in 2025, or Alberta's supplemental credit on credit amounts above its 8 % bracket.
- **Donation tax credit**: the first $200 at the lowest rate, the rest at a higher rate, and, where the rules have one, a still higher rate on gifts matched by income in the top bracket. This year's gifts and those carried forward count, up to 75 % of net income.
- **Dividend tax credit**: a share of the taxable amount of eligible and other dividends.
- **Tax after credits**: never below zero, since these credits are not refundable.
- **Adjusted taxable income for the minimum tax**, **Minimum tax** and **Additional tax for minimum tax purposes**: shown only when the alternative minimum tax is more than the tax after credits; the tax is then the minimum tax. In a province or territory, **Additional tax for minimum tax purposes** is its share of the federal one. **Minimum tax carryover recovered**: minimum tax of earlier years taken off the tax.
- **Refundable Quebec abatement**: for a Quebec resident, 16.5 % of the federal tax after credits.
- **Surtax** and **Tax reduction**: Ontario's surtax on its tax above two thresholds, Ontario's tax reduction, and the low-income tax reductions of British Columbia, New Brunswick, Nova Scotia, Newfoundland and Labrador and Prince Edward Island (the last four for the family, claimed by one spouse).
- **Low-income individuals and families tax credit**: Ontario's LIFT credit, 5.05 % of employment income up to $875, reduced above $32,500 of net income ($65,000 for a family).
- **Health premium**: the Ontario Health Premium, by tiers of taxable income.
- **Tax**: the federal, or the provincial or territorial, tax.

Other amounts on the return, when there are any: the **OAS recovery tax**, and in Quebec the **Contribution to the health services fund** and the **Prescription drug insurance premium**, with **Other amounts on the return** adding them up.

Refundable credits, when the person has any: each with its amount; when it is reduced for income, a line **Before the reduction** and a line **Reduction for income** (a rate of the income above a threshold) come before it. **Refundable credits** adds them up. See [Refundable credits and benefits](taxes#estimate-refundable).

Benefits paid from July to June of the two following years: the **GST/HST credit (Canada Groceries and Essentials Benefit)** and, with children, the **Canada child benefit**, worked out the same way on the year's family net income. They are paid outside the return and are not in the balance.

### Quebec residents {#estimate-quebec}

@index: Quebec income tax estimate; deduction for workers; Revenu Québec estimate

A person who lives in Quebec on December 31 pays federal tax, reduced by the Quebec abatement, and Quebec tax worked out from Quebec's own rules:

- **Deduction for workers**: 6 % of employment income, up to a maximum, deducted from income for the Quebec tax.
- Quebec's brackets and credits at its own rate. QPP, EI and QPIP give no Quebec credit, since the basic personal amount already allows for them.
- The age amount and the retirement income amount are reduced together on family income (the person's and their spouse's).
- The medical expense credit is 20 % of the expenses above 3 % of family income.
- QPP replaces CPP: the base part of the contributions is a federal credit, and the rest a deduction on both returns.
- Net capital losses of other years are deducted from Quebec income as on the federal return.
- **Contribution to the health services fund** (Schedule F): 1 % of income other than employment income (pensions, investment and self-employment income; not OAS, and dividends at their actual amount) above a threshold ($18,130 for 2025), at most $150; above a second threshold ($63,060 for 2025), $150 plus 1 % of the income above it, at most $1,000.
- **Prescription drug insurance premium** (Schedule K): for the months covered by the RAMQ public plan, a rate of family income (the person's and their spouse's Quebec net income) above an exemption that depends on the household ($19,890 for a person alone in 2025, more for a couple or with children), up to the year's maximum ($755 for 2025). A couple each pays their own, on their family income at the couple's rates.
- **Tuition amount**: Quebec's credit is 8 % of the tuition fees, on a line of its own after the donation credit. The student uses it as far as it brings the Quebec tax to zero; what is left of this year's fees can go to a parent or grandparent (not to a spouse), and the rest is carried forward.

### OAS recovery tax and minimum tax {#estimate-minimum-tax}

@index: OAS clawback; OAS recovery tax; social benefits repayment; alternative minimum tax; AMT; minimum tax carryover; T691; TP-776.42

- OAS recovery tax: when net income (before this deduction) is above a threshold ($93,454 for 2025), 15 % of the excess is repaid, up to the Old Age Security received. The repayment is deducted from net income and added to the balance; tax withheld from the OAS for it is part of the tax deducted.
- Alternative minimum tax: a second calculation, at 20.5 % above a large exemption ($177,882 for 2025), on an adjusted taxable income that counts capital gains in full, dividends at their actual amount, and only half of some deductions (union dues, child care, moving and employment expenses, the enhanced CPP or QPP); only half of the non-refundable credits and 80 % of the donation credit are allowed. When it is more than the regular federal tax, the difference is added, the province or territory adds its share (for example 24.63 % in Ontario), and the difference can be recovered in the next seven years when the regular tax is higher than the minimum tax. Quebec has its own minimum tax, at 19 % above its own exemption; the deduction for workers is half added back.

The minimum tax rarely applies, mostly in a year with a large capital gain. The estimate leaves out its rarer adjustments: stock options, donated securities, the capital gains exemption, the special foreign tax credit, and the minimum tax carried forward on the Quebec return.

### Refundable credits and benefits {#estimate-refundable}

@index: Canada workers benefit; CWB; refundable medical expense supplement; Quebec work premium; prime au travail; refundable tax credit for medical expenses; GST/HST credit; Canada Groceries and Essentials Benefit; Canada child benefit; CCB

Unlike the other credits, refundable credits are paid even when there is no tax to reduce. The estimate counts:

- Canada workers benefit: 27 % of working income (employment and self-employment) above $3,000, up to a maximum, which is higher for a family (a couple, or a single parent); it falls by 15 % of adjusted family net income above a threshold. For a couple, part of the lower earner's working income is left out of the family income. Quebec, Alberta and Nunavut set their own figures (in Quebec, for example, 37.3 % above $2,400 for a single person, falling by 20 %).
- Refundable medical expense supplement: 25 % of the medical expenses claimed (above the threshold), up to a maximum, for a person with at least a minimum of earned income; it falls by 5 % of family net income above a threshold.
- Quebec work premium: a rate of work income above $2,400 ($3,600 for a couple), up to a ceiling: 11.6 % without children, 30 % for a single parent, 25 % for a couple with children; it falls by 10 % of family income above that ceiling.
- Quebec refundable tax credit for medical expenses: 25 % of the medical expenses claimed on the Quebec return, up to a maximum, for a person with a minimum of work income; it falls by 5 % of family income above a threshold.

A couple's family credits are claimed by one spouse: the estimate shows them for the person estimated, so count them only once. Eligibility rules the books cannot check (age, full-time studies, residence) are not applied.

Two benefits are shown apart, because they are paid outside the return, from the July after the year to the following June, on the year's family net income: the GST/HST credit (renamed the Canada Groceries and Essentials Benefit, and increased by 25 % from July 2026), for adults and children under 19, with a supplement for single people; and the Canada child benefit, per child under 6 and from 6 to 17, which falls by a rate that depends on the number of children above two thresholds. The amounts are for the whole year of payments, as the family is on December 31.

### Tuition and carry-forwards {#estimate-carry-forward}

@index: tuition credit; tuition transfer; carry-forward; unused tuition; T2202; RL-8; unused RRSP contributions; net capital loss; donation carry-forward

Some amounts move from one year to the next. The estimate applies them as the return does, and shows under **Carried forward** what is left for next year.

- Tuition: a student first claims the unused amounts carried forward, then this year's fees, but only as much as needed to bring their federal tax to zero after the basic personal, age, spouse, CPP, EI, Canada employment and pension amounts (Schedule 11). Of this year's fees left, up to $5,000, less the part of this year's fees they used, can go to a spouse or common-law partner, a parent or a grandparent; the rest is carried forward for as long as needed, and an amount carried forward can never be transferred. The provinces and territories follow the same pattern at their lowest rate; Ontario, Saskatchewan and Alberta no longer give a credit for new fees but still allow the amounts carried forward. In Quebec, the credit is 8 % of the fees; the student uses it first, and what is left of this year's fees can go to a parent or grandparent.
- Donations: gifts can be claimed in their year or in any of the five following years, up to 75 % of net income in a year. The estimate claims the gifts carried forward and this year's together, up to that limit.
- Net capital losses: a net capital loss can reduce the taxable capital gains of any later year. The estimate applies what is carried forward up to this year's taxable capital gains.
- Unused RRSP contributions: contributions not deducted in the year they were made can be deducted in a later year, within the RRSP deduction limit.

The estimate claims each balance as fully as it can. On the return, some of them (donations, RRSP contributions, Quebec tuition) can be kept for a later year instead: your tax software or accountant can say whether that is better.

### What the estimate leaves out {#estimate-left-out}

The estimate does not count: amounts transferred from a spouse or child other than tuition, the education and textbook amounts some provinces and territories still have, the Canada training credit, non-capital losses of other years, Manitoba's family tax benefit, the disability supplement of the Canada workers benefit and the other refundable credits and benefits (Quebec's solidarity credit, provincial benefits), the adjustments of the minimum tax for stock options, donated securities and the capital gains exemption, the minimum tax carried forward on the Quebec return, political contributions, foreign tax credits, the Canada caregiver amount and the eligible dependant amount, Quebec's amount for a person living alone, the deductions and exclusions of the health services fund contribution other than employment income and OAS, and Nova Scotia's 2024 supplements to the spouse and age amounts. The spouse amount in Yukon is not reduced with the basic personal amount at high incomes. These can change the result: the return is what counts.

### Where the rates come from {#estimate-rates}

@index: income tax rates; tax rates by province; rate changes

Every rate, amount and threshold the estimate uses (brackets, basic personal amounts, credit rates, the age, spouse, pension and employment amounts, medical expense thresholds, donation and dividend credit rates, Ontario's surtax and health premium, CPP and QPP, Quebec's deduction for workers, the tuition credits and their transfer maximums, the donation limit, the Canada workers benefit, the refundable medical expense supplements, Quebec's work premium, the GST/HST credit, the Canada child benefit, the OAS recovery tax, the alternative minimum taxes, and Quebec's health services fund contribution and prescription drug insurance premium) is a value in [Rates and rules](rates-rules), under **Income tax**, by date and by province or territory. The app comes with the official figures for 2024, 2025 and 2026, each with its source. An administrator can add a value for a year or a province there, for example when a budget changes a rate; the estimate uses it from its date. A year whose figures are not there yet uses the latest ones.

## Through the year: what feeds the tax screens {#through-the-year}

@index: tax treatment; year round tax preparation

The Taxes screen only reads the books. These are the places, through the year, where what you enter changes it.

### Pay from a pay stub {#pay-stub}

@index: pay stub; pay slip; payroll; paycheque; gross pay; net pay; deductions at source; source deductions

Entering pay from its stub puts the gross pay and every deduction in the books, not only the net deposit, so the year-end package has employment income, CPP or QPP, EI or QPIP, income tax deducted, pension and union dues, and the slips checklist expects the T4.

Open it with **Pay stub…** under a new transaction in a bank account's register (see [Accounts](accounts)), or with **Record the pay…** on a pay stub in Documents, where the fields are filled in from an AI reading when the stub was read by AI, and typed by hand otherwise (see [Documents](documents)).

- **Employer**: the employer, as it will appear as the payee. Required. The slips checklist expects a T4 from this name.
- **Pay date**: the date of the deposit, as YYYY-MM-DD. Today by default, or the date read from the stub.
- **For**: the person paid. **(the household)** by default; choose the person so the pay, deductions and slips go to the right return.
- **Paid into**: shown only when opened from Documents: the bank account the pay went into.
- Earnings: one line per earnings line of the stub (salary, overtime, vacation pay, bonus).
  - **Description**: as printed. A description containing bonus, commission, prime, incentive or gratification goes to **Bonuses and commissions**; any other to **Salary and wages**.
  - **Amount**: the amount earned.
  - **✕** removes a line (one line always stays). **Add earnings** adds a line.
- Deductions: one line per amount taken off the pay. A new stub starts with **Income tax**, **CPP / QPP** and **EI / QPIP**.
  - **Kind**: **Income tax**, **CPP / QPP**, **EI / QPIP**, **Union dues**, **Pension plan**, **Group insurance**, **Group RRSP**, **Charity** or **Other**. It chooses the category: **Income tax**, **CPP / QPP contributions**, **EI / QPIP premiums**, **Union and professional dues**, **Pension plan contributions**, **Group insurance**, **Group RRSP contributions**, **Charitable donations** or **Other payroll deductions**.
  - **Description**: optional, kept as the line's memo. For a **Charity** deduction, enter the charity (United Way, Centraide): it appears on the **Donations** tab.
  - **Amount**: without a minus sign. A line left empty is skipped.
  - **✕** removes a line. **Add a deduction** adds a line of kind **Other**.
- The line under the lines shows "Gross … − deductions … = net …", the deposit that will be recorded. When the stub was read by AI and its printed net pay differs, a red line says so: check the amounts or add a missing line.
- **Save**: records one deposit of the net pay, split into each earnings line and each deduction (as a negative line). From Documents, the stub is filed with the deposit. **Save** is available once an earnings amount is entered and an account is known. **Cancel**: closes.

The deposit must be more than zero: deductions that add up to the gross pay or more are refused.

### Sales tax included {#sales-tax}

@index: GST; HST; QST; PST; sales tax; input tax credit; TPS; TVQ

**Sales tax…**, under an existing transaction in a register (not a transfer or an investment transaction), records the GST, HST, QST or PST the receipt shows.

- **GST**, **HST**, **QST**, **PST**: the amounts on the receipt, without a minus sign. Leave the others empty.
- **Calculate from the total**: proposes the taxes included in the transaction's amount, at the rates in effect on its date in the account owner's province (or the household's), from [Rates and rules](rates-rules). Check them against the receipt before saving. See [Sales tax on a purchase](accounts#sales-tax).
- **Save**: saves them with the transaction. **Cancel**: closes.

Sales tax rates by province: the HST (13 % in Ontario; 15 % in New Brunswick, Newfoundland and Labrador and Prince Edward Island; 14 % in Nova Scotia since April 1, 2025) replaces the GST; elsewhere the GST of 5 % applies, with the QST of 9.975 % in Quebec, a PST of 7 % in British Columbia or 6 % in Saskatchewan, or the RST of 7 % in Manitoba; Alberta and the territories have the GST only. These are the rates as of 2026; every rate and its history is kept, and can be changed, in [Rates and rules](rates-rules).

The transaction's amount does not change. On expenses whose category's tax treatment is self-employment, the year-end package adds the sales taxes up as **Sales tax paid on business expenses**, by tax, which a registered business can claim as input tax credits. Leave it empty otherwise.

### Tax treatment of categories {#tax-treatment}

@index: tax flag; tax category; deductible

Each category has a **Tax treatment** (see [Categories](categories)): medical expenses, charitable donations, political contributions, child care expenses, tuition, self-employment, employment expenses or moving expenses. The Donations tab, the year-end package and the medical expense figures use it. Payments on a category with the right treatment are picked up automatically; check new categories you create.

### Investment slips and capital gains {#investment-slips}

The T5 and T3 amounts, and the capital gains, are worked out in the Investment income and capital gains report, where each slip can be entered from the slip when it arrives so its boxes replace the estimate: see [Investment income and capital gains](reports#investment-income) and [Enter a slip](reports#enter-slip).

### Medical expenses and which spouse claims {#medical-claim}

@index: medical expense credit; which spouse

Medical expenses entered on the Medical claims screen (see [Medical claims](medical)) give the **Medical expenses** lines of the household's package, the same figures as the Medical expenses report. The report finds the best 12-month period, makes one PDF of the receipts, and helps choose which spouse should claim: see [Medical expenses](reports#medical-expenses) and [Which spouse should claim](reports#who-claims).

### Registered plan contributions {#plan-contributions}

RRSP and FHSA contributions in the package are the money moved into those plans from your other accounts, by the person whose room they use, as on the Registered plans screen: see [Registered plans](plans).

## Canadian and Quebec tax notions {#tax-notions}

@index: CRA; Canada Revenue Agency; Revenu Québec; Relevé; notice of assessment; non-refundable credit

A few words used on this screen, in brief:

- CRA and Revenu Québec: the Canada Revenue Agency collects federal income tax everywhere; people who live in Quebec on December 31 also file a Quebec return with Revenu Québec.
- Slips and Relevés: the T slips (T4, T5 and others) report income for the federal return; in Quebec, the matching Relevés (RL-1, RL-3 and others) report it for the Quebec return.
- Deduction and credit: a deduction (RRSP, union dues, child care) lowers taxable income; a credit (medical, donations, tuition) lowers the tax itself. Most credits are not refundable: they cannot bring tax below zero.
- Instalments: tax paid during the year by people with little or no tax taken at source.
- Notice of assessment: the CRA's answer to a return, with next year's RRSP deduction limit.

The CRA and Revenu Québec publish the rules each year; the amounts and lines here follow the federal return.
