# Rates and rules

Rates and rules lists every rate, limit, threshold and lead time the app applies in its calculations: registered plan limits, sales taxes, income tax brackets, medical and investment figures, reminder lead times, security settings, bank holidays and more. Each value has the date it takes effect and, when it differs across Canada, the province or territory it is for. The screen is in the **Settings** group of the menu, under **Rates and rules**, just after **Rates and prices**.

## What this screen is for {#purpose}

@index: tax rates; limits; thresholds; lead times; official figures; government rates; TFSA limit; sales tax rate; GST; HST; PST; QST; tax brackets; rates and rules

The figures governments set change every year or so: a new TFSA limit each January, a new tax bracket after a budget, a sales tax rate that goes up or down. The app ships with the official values, each with its date and its source, and uses the one in effect on the date of whatever it calculates.

When a new figure is announced before the app is updated, an administrator can enter it here, with the date it takes effect. From that date on, the app uses it everywhere the rule is applied. Nothing else needs to change: the calculations, reports and reminders that use the rule pick up the new value by themselves.

> Note: The values built into the app are never changed or removed. A value you add takes their place from its date on, and the built-in values stay in the history.

## How the value in effect is chosen {#how-values-apply}

@index: effective date; date of a rate; province or territory; per province; value in effect; which value applies

For each calculation, the app looks at the date of what it calculates (a transaction, a tax year, a contribution, a reminder) and, for a rule that differs by province, the province or territory that applies (usually the household's, or the person's own when set in [Household members](members)). Then:

1. If the province or territory has values of its own, the latest one dated on or before that date applies.
2. Otherwise, the latest value for everywhere (or, for a federal figure, the only one) dated on or before that date applies.
3. When a built-in value and a household value have the same date, the household value applies.
4. If no value is dated on or before that date, the rule has no value yet, and the screen says "No value yet".

A few consequences:

- A value applies to everything dated on or after its date, until a later value. A value dated in the future applies only once that date comes; until then, the current one stays in effect.
- A value dated in the past also applies to past dates: calculations and reports for those dates use it from then on.
- A province's own value always comes before the value for everywhere, whatever their dates. To change a figure in a province that has its own value, add the new value for that province.

## The list of rules {#rule-list}

@index: areas; find a rule; rule key

The left side lists the rules, grouped by area, such as Registered plans, Sales taxes, Income tax, Reminders and lead times, Thresholds, Security and Bank holidays. The list grows as new rules are added to the app.

- **Filter by name or key**: type part of a rule's name or of its key to shorten the list. Capitals and accents do not matter, so "quebec" finds "Québec". When nothing matches, the list says "No rule matches." Empty the box to see every rule again.

Each rule shows its name and, under it, its key, such as tfsa.limit: a short code that names the rule the same way in English and in French. Click a rule to show it on the right. Until you choose one, the right side explains the screen and says "Choose a rule on the left to see its values."

## A rule's details {#rule-details}

At the top of the right side:

- The rule's name, and "Key:" with its key.
- What the rule is and where the app uses it.
- Its type (see [Types of values](rates-rules#types)) and either "Can differ by province or territory" or "The same everywhere in Canada".

### Types of values {#types}

@index: percentage; fraction; brackets; month and day; yes or no; list of numbers

- Rate, as a percentage: such as 5 % or 9.975 %. Shown as a percentage in your language (9.975% in English, 9,975 % in French). It cannot be more than 100 %.
- Amount in dollars: such as $7,000. Shown with the dollar sign, and cents when there are any.
- Number: a plain number, which may have decimals, such as an age or a factor.
- Number of days: a whole number, such as 30 days.
- Brackets: a rate on income above each threshold, such as income tax brackets. Shown as "15 % above $0 · 20.5 % above $58,523": each rate applies to the part of the income above its threshold, up to the next threshold.
- Day of the year: a month and a day, such as November 15.
- Yes or no: whether something applies.
- List of numbers: several numbers read in the order the rule's description gives.

## In effect today {#in-effect}

@index: current rate; today's value

Under **In effect today**, the screen shows the value that applies today, with "since" and the date it took effect.

For a rule that can differ by province or territory, there is one line for **Everywhere** (the value used where a province has none of its own), then one line per province and territory: yours first, marked "(your province)", then the others in alphabetical order. A line whose value comes from the value for everywhere says so ("the value for everywhere"). Your province is the one set for the household in [Household members](members).

For a rule that is the same everywhere, there is a single line.

## History of values {#history}

@index: past rates; rate history; source of a rate; built-in value; household value

Under **History of values**, every value of the rule, newest first. Each line shows:

- The date the value takes effect.
- For a rule that can differ by province: the province or territory, or Everywhere.
- The value, formatted as described in [Types of values](rates-rules#types).
- Where it comes from: "Built in" with its source, such as the Canada Revenue Agency or a provincial government, for a value that ships with the app; or "Household value · added by" the user who added it, with the date and time it was added.
- "Note:" and the note entered with a household value, if any.
- **Delete**: shown on household values, for an administrator. It asks first: "Delete the household value … from …? The value before it applies again from that date." Once deleted, the value before it applies again from that date. To undo, add the value again. Built-in values have no Delete: to change one, add a household value with the same date.

## Add a value {#add-value}

@index: new rate; new limit; change a rate; override a rate; enter a rate

Under **Add a value**, an administrator enters a new value of the rule. The screen reminds you: "A value applies from its date to everything dated on or after it, until a later value. Built-in values are never changed: they stay in the history." Users who are not administrators see "Only an administrator can add or delete values. You can see them here." instead of the form.

- **Effective from**: the date the value takes effect, as YYYY-MM-DD. Today by default. Type + or - to move a day forward or back. For a yearly figure, enter January 1 of its year; for a tax change, the date the government announced it takes effect.
- **Province or territory**: shown only for a rule that can differ by province. Choose one province or territory, or **All provinces and territories** (the default) for the value for everywhere. A value for all provinces applies only where a province has no value of its own: the screen lists the provinces and territories that keep their own ("Provinces and territories with a value of their own keep it:").
- The value: a field that fits the rule's type, filled in with the value in effect today (in your province, for a rule that differs by province) so you can change only what is new. See [The value editor](rates-rules#value-editor).
- **Note**: optional. Where the figure comes from, such as "Federal budget, April 2027". It is shown in the history.
- **Add a value**: saves the value. It applies at once to every calculation dated on or after its date. "Value added." confirms it, and the value appears in the history marked as a household value. It is kept in the household file, so it is in every backup and on every computer that opens the household.

If the value cannot be saved, the reason is shown under the form:

- "This value does not fit: check its form." when the value cannot be read, or is out of range: a negative amount, a rate over 100 %, brackets whose thresholds are not in increasing order, or a day that does not exist.
- "This rate is the same everywhere, so it has no province." when a province is given for a rule that is the same everywhere.
- "That rule does not exist." if the rule is no longer known to the app.

### The value editor {#value-editor}

@index: typing a percentage; decimal comma; bracket table

Numbers are typed the way you write them in your language: 9.975 in English, 9,975 in French (a dot alone also works in French). Spaces and grouping, such as 7 500 or 7,500, are accepted. The field turns red with "This value does not fit: check its form." as long as what is typed cannot be used.

- **Rate (%)**: for a rate, type the percentage, not the fraction: 9.975 for 9.975 %, 13 for 13 %. The % sign may be typed or left out. The app keeps it as a fraction (0.09975).
- **Amount ($)**: for an amount, type dollars, such as 7000 or 2834.50. A $ sign may be typed.
- **Value**: for a plain number, type the number. For a yes or no rule, choose Yes or No in the list.
- **Days**: a whole number of days, 0 or more.
- **Month and day (MM-DD)**: for a day of the year, the month and day as numbers, such as 11-15 for November 15.
- **Values, separated by ;**: for a list, type the numbers in the order the rule describes, separated by semicolons, such as 0.0528; 0.0540. They are kept as typed, not as percentages.

For brackets, the editor is a small table with one row per bracket:

- **Income above ($)**: the threshold, in dollars. The first bracket's is usually 0.
- **Rate (%)**: the rate on the part of the income above that threshold, as a percentage, such as 20.5.
- **Add a bracket**: adds an empty row.
- **Remove**: removes that row. The last row cannot be removed.

Rows may be entered in any order: they are sorted by threshold when saved. Empty rows are left out. Every bracket must be entered, not only the one that changes: the new value replaces the whole set of brackets from its date.

### What changes {#what-changes}

@index: before and after; preview of a change

Under the value, a line shows what the new value changes: "Before" the date, the value in effect the day before it, and "From" the date, the new value (or "?" while the value cannot be read). For one province, the value before is that province's.

When a later value already exists for the same province (or for everywhere), the line adds "Until" its date, "when a later value takes over": the new value applies only up to then.

## Who can change rates and rules {#permissions}

- Administrators: add values and delete household values.
- Members and viewers: see every rule, its value in effect and its history. The **Add a value** form and the **Delete** buttons are not shown.

Each value added or deleted is recorded in the activity log, with who did it.

## Examples {#examples}

### Entering next year's TFSA limit {#example-tfsa}

@index: TFSA dollar limit; new TFSA limit; contribution room

The government announces the TFSA dollar limit for the coming year in November. If it is announced before the app is updated:

1. Open **Rates and rules** and type "tfsa" in **Filter by name or key**.
2. Click **TFSA dollar limit** (Registered plans).
3. Under **Add a value**, set **Effective from** to January 1 of the coming year, such as 2027-01-01.
4. In **Amount ($)**, type the new limit, such as 7500.
5. In **Note**, type where it comes from, such as "CRA, announced November 2026".
6. Check the line under it: "Before 2027-01-01: $7,000. From 2027-01-01: $7,500."
7. Click **Add a value**.

From January 1, contribution room on [Registered plans](plans) includes the new limit. If the app later ships the same figure built in, both have the same date and your value applies; you can delete yours.

### A new HST rate {#example-hst}

@index: harmonized sales tax; HST change; sales tax change

Suppose a province charging the HST announces that its rate goes from 13 % to 14 % on July 1:

1. Open **Rates and rules** and find the HST rule under Sales taxes.
2. Under **Add a value**, set **Effective from** to the date of the change, such as 2027-07-01.
3. In **Province or territory**, choose the province. Choosing **All provinces and territories** would not change a province that has its own rate.
4. In **Rate (%)**, type 14.
5. Check the line under it, then click **Add a value**.

Purchases dated before July 1 keep the old rate; those dated July 1 or later use the new one.

### A new tax bracket {#example-bracket}

@index: income tax bracket; tax brackets change; federal budget

Suppose a budget lowers the rate of a bracket or adds one from January 1:

1. Open **Rates and rules** and choose the brackets rule under Income tax (federal, or your province's).
2. Under **Add a value**, set **Effective from** to January 1 of the year, such as 2027-01-01.
3. The table starts with the brackets in effect today. Change the rate or threshold that changes, and use **Add a bracket** for a new one, with its **Income above ($)** and its **Rate (%)**.
4. Check the line under it: it shows the brackets before and after.
5. Click **Add a value**.

The whole table is the new value: brackets left as they were stay the same, and the app uses the new set for every tax year from 2027 on.

### A figure not published yet {#example-unpublished}

@index: unpublished figure; 2026 forms; disability supplement; Schedule 6

Some figures are only published on the year's tax forms, which come out late in the year or early the next. Until then, the app keeps the latest published value and its source says so. For example, the Canada workers benefit disability supplement for Quebec, Alberta and Nunavut keeps its 2025 values until the CRA publishes Schedule 6 for 2026. When it does:

1. Open **Rates and rules** and type "cwb" in **Filter by name or key**.
2. Click **Canada workers benefit disability supplement** (Income tax).
3. Under **Add a value**, set **Effective from** to January 1 of the year, such as 2026-01-01, and choose the province or territory, such as Alberta.
4. In **Values, separated by ;**, type the ten numbers in the order the rule's description gives, from lines 30 to 38 of that province's Schedule 6, such as 910; 0.26; 0.26; 860; 38583; 51237; 51237; 51237; 0.15; 0.075 (an illustration, not the published figures).
5. In **Note**, type where they come from, such as "CRA, Schedule 6 for residents of Alberta (5009-S6) 2026".
6. Click **Add a value**.

The income tax estimate on the [Taxes](taxes#estimate-refundable) screen uses the new figures for 2026 and later.

## Related chapters {#related}

- [Rates and prices](rates): exchange rates and market prices, which are downloaded rather than set by law.
- [Users](users): who is an administrator.
- [Registered plans](plans), [Taxes](taxes) and [Medical claims](medical): screens that use these rules.
