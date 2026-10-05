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
- **Interest** of $50 or more in the year from the same payer: a T5. Below $50, no slip is expected.
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

Some people pay their income tax during the year in instalments instead of at filing: usually those whose tax owing at filing is more than $3,000 ($1,800 in Quebec) this year and in either of the two years before, such as retirees or the self-employed. The CRA and Revenu Québec send reminders with the amounts. Instalments are due March 15, June 15, September 15 and December 15; a payment made the next business day after a weekend or holiday is on time.

The **Instalments** tab lists the instalments of the tax year, grouped by person and authority, such as "Jean · Canada Revenue Agency". Click the heading, or **Change** beside it, to change them. Each instalment shows:

- its due date;
- its state: **Paid**, **Partly paid** (with the amount paid so far, such as "$400.00 paid"), **Due**, or **Late** in red when the date has passed and it is not fully paid;
- its amount.

- **Set up instalments…**: enters a new schedule. See [Instalments for a year](taxes#instalment-window).

"No instalments for this year." means none were entered for the year shown.

Instalments not fully paid that are due within 30 days, or up to 30 days late, appear among the app's reminders as "tax instalment", with the authority, and lead to this screen.

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
- **Use the books' figures again**: shown once you have changed a figure or the age box; puts every figure back as the books have it.

The red line under them is the reminder that this is an estimate. "No one to estimate yet" means the household has no adult member: add them under [Household members](members).

The left side lists the figures used; the right side shows the result and the calculation.

### Figures used {#estimate-figures}

@index: estimate inputs; override tax figures

Under **Figures used**, a line says whose rates apply: those of the province or territory the person lives in (their **Lives in** on Household members), or else the household's. Then:

- **65 or older on December 31**: ticked when the person's birth date says they are 65 or older at the end of the year. It gives the age amount, and counts RRIF income as pension income. Change it when the birth date is not entered.

Each figure is an amount in dollars, with a line under it saying where it comes from: **From the year-end package** and the package items added together, **Not in the books** when the books have no source for it, or **Entered here, not saved** once you change it. **Use the books** beside a changed figure puts the books' amount back. What you enter here is not saved, does not change the books, and is forgotten when you choose another person or year or leave the screen. A blank amount counts as zero. An amount can be a sum, such as 1200 + 350.

The figures are grouped as on a return.

Income:

- **Employment income**: from **Employment income** in the package (salary, wages, bonuses on pay stubs and income categories).
- **Pension income eligible for the pension amount**: **Other pensions** (an employer pension), plus **RRIF income** from age 65. It gives the pension income amount.
- **Other income (OAS, CPP or QPP, EI, plan withdrawals)**: **Old Age Security pension**, **CPP or QPP benefits** and **Employment Insurance benefits**, plus **RRIF income** before 65. Add here any other taxable income the books do not show, such as RRSP withdrawals.
- **Interest and other investment income**: from **Interest and other investment income**.
- **Eligible dividends (taxable amount)** and **Other dividends (taxable amount)**: the grossed-up amounts of the T5 and T3 slips (T5 boxes 25 and 11, T3 boxes 50 and 32), from the investment income report. They give the dividend tax credit.
- **Taxable capital gains**: from **Taxable capital gains** (half of the net gains of the year).
- **Self-employment income, net of expenses**: **Self-employment income** less **Self-employment expenses**; it may be negative.

Deductions:

- **RRSP deduction**: from **RRSP contributions**. The deduction cannot be more than the RRSP deduction limit on the notice of assessment; enter a smaller amount if needed.
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
- **Spouse's or partner's net income**: empty means no spouse amount is claimed. Enter the net income of a spouse or common-law partner to claim the spouse amount, which falls as their income rises. In Quebec, it is added to the person's own income to form the family income that reduces the age and retirement amounts and sets the medical expense threshold.

Tax already paid:

- **Income tax deducted**: from **Income tax deducted** (income tax taken off pay, federal and Quebec together).
- **Instalments paid**: from **Instalments paid** to the CRA and to Revenu Québec.

### The result {#estimate-result}

@index: balance owing; refund estimate; average tax rate; marginal tax rate

The box at the top right gives:

- **Federal tax**: the federal tax after credits (after the Quebec abatement for a Quebec resident).
- The provincial or territorial tax, named after the province or territory: after credits, with any surtax, reduction or health premium.
- **Total income tax**: the two together.
- **Deducted at source and instalments**: **Income tax deducted** and **Instalments paid**.
- **Balance owing** or **Refund**: the total income tax less what was already paid. A balance owing is due by April 30 of the next year.
- **Average rate**: the total income tax as a share of total income.
- **Marginal rate**: the tax on one more dollar of ordinary income (such as interest), federal and provincial together; it is what an RRSP deduction saves, roughly, on each dollar.

When the rates for the year are not yet in Rates and rules, a red line says that those of the latest year known are used.

### The calculation {#estimate-lines}

@index: tax brackets; basic personal amount; non-refundable credits; surtax; Ontario Health Premium; Quebec abatement

Under the result, the calculation line by line, in three parts. Each line shows its amount, and below its name how it was worked out (a rate of an amount, or the amount it is based on).

Income:

- **Total income**: all the income figures added together.
- **CPP or QPP enhanced contributions (deduction)**: the part of the contributions that is deducted rather than credited.
- **Deductions**: the deductions, with that part.
- **Net income** and **Taxable income**: total income less the deductions (never below zero). The estimate counts no other deduction, so the two are the same.

Federal, then the province or territory:

- **Tax bracket**: one line per bracket reached, with its rate, the income taxed at that rate and where the bracket starts.
- **Tax on taxable income**: the brackets added together.
- Credit amounts: **Basic personal amount** (reduced at high incomes where the rules say so), **Age amount** (from 65, reduced above an income threshold), **Senior supplementary amount** (Saskatchewan), **Spouse or common-law partner amount**, **Canada employment amount** (federal and Yukon), **CPP or QPP contributions (base part)**, **EI premiums**, **Pension income amount** and **Medical expenses above the threshold**.
- **Total of the credit amounts**, and **Non-refundable tax credits**: that total at the lowest rate.
- **Top-up or supplemental tax credit**: the federal top-up credit, which keeps 15 % on credit amounts above the first bracket since the lowest rate went down in 2025, or Alberta's supplemental credit on credit amounts above its 8 % bracket.
- **Donation tax credit**: the first $200 at the lowest rate, the rest at a higher rate, and, where the rules have one, a still higher rate on gifts matched by income in the top bracket.
- **Dividend tax credit**: a share of the taxable amount of eligible and other dividends.
- **Tax after credits**: never below zero, since these credits are not refundable.
- **Refundable Quebec abatement**: for a Quebec resident, 16.5 % of the federal tax after credits.
- **Surtax** and **Tax reduction**: Ontario's surtax on its tax above two thresholds, Ontario's tax reduction, and British Columbia's low-income tax reduction.
- **Health premium**: the Ontario Health Premium, by tiers of taxable income.
- **Tax**: the federal, or the provincial or territorial, tax.

### Quebec residents {#estimate-quebec}

@index: Quebec income tax estimate; deduction for workers; Revenu Québec estimate

A person who lives in Quebec on December 31 pays federal tax, reduced by the Quebec abatement, and Quebec tax worked out from Quebec's own rules:

- **Deduction for workers**: 6 % of employment income, up to a maximum, deducted from income for the Quebec tax.
- Quebec's brackets and credits at its own rate. QPP, EI and QPIP give no Quebec credit, since the basic personal amount already allows for them.
- The age amount and the retirement income amount are reduced together on family income (the person's and their spouse's).
- The medical expense credit is 20 % of the expenses above 3 % of family income.
- QPP replaces CPP: the base part of the contributions is a federal credit, and the rest a deduction on both returns.

### What the estimate leaves out {#estimate-left-out}

The estimate does not count: tuition, amounts transferred from a spouse or child, carry-forwards (unused tuition, donations or losses), low-income tax reductions other than Ontario's and British Columbia's, refundable credits and benefits (GST/HST credit, Canada workers benefit, Quebec's solidarity credit), the OAS recovery tax, the alternative minimum tax, political contributions, foreign tax credits, the Canada caregiver amount and the eligible dependant amount, Quebec's amount for a person living alone, its health services fund contribution and its prescription drug insurance premium, and Nova Scotia's 2024 supplements to the spouse and age amounts. The spouse amount in Yukon is not reduced with the basic personal amount at high incomes. These can change the result: the return is what counts.

### Where the rates come from {#estimate-rates}

@index: income tax rates; tax rates by province; rate changes

Every rate, amount and threshold the estimate uses (brackets, basic personal amounts, credit rates, the age, spouse, pension and employment amounts, medical expense thresholds, donation and dividend credit rates, Ontario's surtax and health premium, CPP and QPP, and Quebec's deduction for workers) is a value in **Rates and rules**, under **Income tax**, by date and by province or territory. The app comes with the official figures for 2024, 2025 and 2026, each with its source. An administrator can add a value for a year or a province there, for example when a budget changes a rate; the estimate uses it from its date. A year whose figures are not there yet uses the latest ones.

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
- **Save**: saves them with the transaction. **Cancel**: closes.

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
