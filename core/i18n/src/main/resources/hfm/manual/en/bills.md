# Bills

Bills keeps track of everything that comes back on a schedule: rent or mortgage, hydro and heating, phone and internet, insurance, property tax, subscriptions, your pay, and regular transfers to savings. It reminds you before each due date, records the payment when you mark it paid, and projects your account balances so you can see a shortfall coming. Bills is in the Money group of the menu.

RANN's Roost does not pay bills for you. You pay through your bank as usual; the app records, reminds and forecasts.

## What the Bills screen does {#overview}
@index: bill payment; recurring payments; scheduled transactions; due dates; reminders

The title bar has two buttons:

- **Export calendar…**: saves your due dates as a calendar file. See [Export calendar](bills#export-calendar).
- **Add a bill**: opens the bill form. See [Add or edit a bill](bills#bill-form).

Under it are five tabs:

- **To pay**: what is overdue, due today and due in the next 30 days, with the buttons to pay, skip or enter an amount.
- **All bills**: every bill, income and transfer you set up.
- **Calendar**: a month view of due dates.
- **Subscriptions**: what each subscription costs a year.
- **Cash flow forecast**: each account's projected balance over 30, 60 or 90 days.

## Bills, income and transfers {#bill-types}
@index: recurring income; pay day; payday; scheduled transfer; automatic savings

One form handles three types of scheduled items:

- **Bill**: money you pay out on a schedule. Marking it paid records money out of the paying account.
- **Income**: money you receive on a schedule, such as your pay, a pension, the Canada Child Benefit or rent from a tenant. Marking it received records money into the account. Its amount is shown in colour.
- **Transfer**: money you move between two of your own accounts on a schedule, such as a monthly transfer to savings or a credit card payment from chequing. Marking it paid records a transfer between the two accounts.

## Add or edit a bill {#bill-form}

**Add a bill** opens an empty form titled "Add a bill"; **Edit** on any line opens the same form titled "Edit bill". **Save** is available once the bill has a name and an account. **Cancel** closes the form without saving.

### Type and name {#type-and-name}

- **Type**: Bill, Income or Transfer. See [Bills, income and transfers](bills#bill-types). The type changes which fields follow. Default: Bill.
- **Name**: how the bill is called in every list, reminder and calendar, for example "Hydro-Québec", "Rent" or "Pay - Marie". Required.

### Accounts and category {#accounts-and-category}

- **Paid from** (for a bill or transfer) or **Deposited to** (for income): the account the money leaves or arrives in. The bill's amount is in this account's currency. It is used by the forecast and by the overdraft warning, and the payment is recorded in it. It can be chosen only when you create the bill; to change it later, create a new bill. Required.
- **To account** (transfer only): the account the money goes to. It must be a different account.
- **Category** (bill and income only): the category the payment is recorded under, for example Utilities: Electricity or Income: Salary. It decides where the payment counts in budgets, reports and tax figures. "(none)" records the payment without a category.

### Payee {#payee}

Shown for a bill or income, not for a transfer.

- **Payee**: who you pay, or who pays you. It becomes the payee of the recorded transaction. If empty, the bill's name is used. It also helps the app recognize a scanned or downloaded bill as this one.
- **Your account number with the payee**: the account or customer number printed on the bill. Optional. When you scan or import a bill, the app compares the last four digits with this number to find the right bill. It is shown nowhere else.

### Amount {#amount}

- **Amount**: the amount of each payment, in the account's currency, as a positive number. For a variable bill, enter a typical amount. You can type a simple sum, such as 45.20+12. If left empty, the amount is zero.
- **Amount is**: how sure the amount is.
  - **Fixed**: the same every time, such as rent or a subscription. The amount is used as is.
  - **Variable**: changes every time, such as hydro or a credit card. Until you enter the actual amount of a due date, the app expects the average of the last three amounts paid (or the amount you entered, before any payment). The amount is shown with "≈".
  - **Estimated**: an amount you guess, such as a yearly property tax bill not yet received. Shown with "≈" until you enter the actual amount.

For Variable and Estimated bills, the **To pay** tab offers **Enter amount** to record the actual amount when the bill arrives. See [Enter amount](bills#enter-amount).

### Payment method {#payment-method}

- **Payment method**: how this bill is paid: Pre-authorized debit, Online banking, Credit card, Cheque, Cash or Other. Default: Online banking. It is shown on the **To pay** list as a reminder, for example so you know which bills you must pay yourself and which come out on their own. It does not change how the payment is recorded.

### How the bill repeats {#repeats}
@index: frequency; biweekly; semi-monthly; monthly; quarterly; annual; every two weeks; twice a month

- **Repeats**: how often the bill comes due.
  - **Once**: a single due date.
  - **Weekly**, **Every two weeks**: from the first due date, every 7 or 14 days. Every two weeks suits most pay schedules.
  - **Twice a month**: on the first due date's day and on a second day each month, such as the 1st and the 15th.
  - **Monthly**, **Quarterly**, **Twice a year**, **Yearly**: every 1, 3, 6 or 12 months.
  - **Every … days**, **Every … weeks**, **Every … months**: any other interval; enter the number under **Every**.
- **Every**: shown for the "Every …" choices. The number of days, weeks or months between due dates, 1 or more.
- **Second day**: shown for Twice a month. The second day of the month, 1 to 31; enter 0 for the last day of the month. The first day is the day of the first due date.
- **Day of the month**: shown for monthly, quarterly, twice-a-year, yearly and every-… -months bills.
  - **Same day each time**: the first due date's day. In a shorter month it moves to the month's last day (a bill due on the 31st falls on the 30th in April and on the 28th or 29th in February).
  - **Last day of the month**.
  - **Last business day**: the last weekday of the month that is not a bank holiday.

### Weekends and holidays {#weekends-holidays}
@index: business day; bank holiday; statutory holiday

- **On weekends and holidays**: what happens when a due date falls on a Saturday, a Sunday or a bank holiday.
  - **Keep the date**: the due date stays as is. Default.
  - **Move to the business day before**: for payments that must arrive on time, such as a pre-authorized debit processed the business day before.
  - **Move to the next business day**: for deposits, such as a pay day that moves to the Monday.

Bank holidays are the federal holidays banks observe, plus the holidays of the household's province or territory (for example the Fête nationale in Quebec, Family Day, or the Civic Holiday). A holiday that falls on a weekend is observed on the following weekday.

### First and last due dates {#due-dates}

- **First due date**: the first date the bill is due, as YYYY-MM-DD. All later due dates are counted from it. Default: today. Required. To change the schedule of an existing bill from now on, you can set this to the next due date.
- **Last due date (optional)**: the last date the bill comes due, for example the end of a lease or of a loan. Leave empty for no end. It cannot be before the first due date.

### Reminders {#reminders}
@index: bill reminder; notification; due date alert

- **Remind me (days before)**: how many days before each due date you want a reminder, separated by commas, for example 7, 1 (the default). Each number is 0 to 365. Leave it empty for no advance reminder.

Whatever you enter here, a bill that is due today or overdue is always in the reminders. See [Reminders and notifications](bills#reminder-banner).

### Subscription {#subscription-fields}
@index: subscription; streaming; cancel subscription; free trial; renewal

- **Subscription**: tick it for subscriptions such as streaming, software, magazines or a gym, to follow them on the **Subscriptions** tab with their yearly cost.
- **Cancel by (reminder)**: shown when **Subscription** is ticked. The date by which you must cancel to avoid the next renewal or the end of a free trial, as YYYY-MM-DD. Starting 7 days before that date, a reminder says "cancel within ... days to avoid renewal". It is shown on the Subscriptions tab.

### Active and Delete {#active-and-delete}

These appear only when you edit an existing bill.

- **Active**: untick it to put a bill on hold, for example a seasonal bill or a cancelled service whose history you want to keep. An inactive bill disappears from To pay, the Calendar tab, the Subscriptions tab, the forecast, the reminders and the Calendar screen. It stays on **All bills**, marked "(inactive)", where you can make it active again.
- **Delete**: asks "Delete ... and its payment history? Transactions already recorded are kept." and, on confirmation, deletes the bill and which dates were paid or skipped. The transactions recorded when you marked it paid stay in the accounts. This cannot be undone.

## The To pay tab {#to-pay-tab}
@index: overdue bills; upcoming bills; agenda

**To pay** lists the due dates of all active bills, income and transfers in four groups, each with its count:

- **Overdue**: due dates already passed and not yet paid or skipped, looking back up to one year.
- **Due today**.
- **Next 30 days**.
- **Paid recently**: due dates of the last 31 days (and any later ones) already marked paid, newest first.

"Nothing due in the next 30 days." means all four are empty.

Each line shows the due date, the name, the payment method, the account (and "→ account" for a transfer), the date it was paid if it was, and the amount ("≈" in front means the amount is expected, not known). The buttons on the line are described below. **Edit** opens the bill's form.

### Mark paid or Mark received {#mark-paid}
@index: record payment; pay a bill

**Mark paid** (for a bill or transfer) or **Mark received** (for income) opens a small form. It says when the bill was due, and that a transaction will be added to the account and matched with the bank statement when you import it.

- **Date paid**: the date of the payment, as YYYY-MM-DD. Default: today.
- **Amount**: the amount actually paid or received. Default: the expected amount. It must be more than zero.

**Save** then:

- for a bill: records money out of the paying account, with the bill's payee (or name) as payee, its category, and its name as memo;
- for income: records money into the account the same way;
- for a transfer: records a transfer from the paying account to the other account;
- marks that due date paid. It moves to **Paid recently** and the next due date takes its place.

When you later import the bank statement, the import matches the statement line with this transaction instead of adding it twice. The amounts you pay on a variable bill also set its expected amount (the average of the last three).

### Enter amount {#enter-amount}

**Enter amount** appears for Variable and Estimated bills. When the actual bill arrives, enter its amount:

- **Amount**: the amount of this due date only. Required.

The line then shows the amount without "≈", and the forecast and reminders use it. Nothing is recorded in the account until you mark it paid.

> Tip: When you scan or import a paper or e-bill on the Documents screen, Record the amount on this bill does the same thing and keeps the bill with it. See [Record the amount on a bill](documents#record-on-bill).

### Skip {#skip}

**Skip** passes over this one due date, for example a month with no bill or a payment you will not make. The due date is marked skipped at once, with no question, and leaves the list; nothing is recorded in the account. The screen offers no way to bring a skipped date back.

### Undo a payment {#undo-payment}

Under **Paid recently**, **Undo** reverses a payment marked by mistake: the transaction that was recorded for it is deleted from the account, and the due date goes back to the list as not paid. It asks no question.

> Note: If you already reconciled that transaction with a statement, change it from the account's register instead.

### Overdraft warnings {#shortfall-warning}
@index: overdraft; NSF; insufficient funds; low balance

A red line "The paying account would go below zero." appears on a bill whose payment, according to the forecast for the next 30 days, would take a bank account below zero. See [The Cash flow forecast tab](bills#forecast-tab).

## The All bills tab {#all-bills-tab}

**All bills** lists every bill, income and transfer, including inactive ones (marked "(inactive)"). Each line shows the name, the type, how it repeats, the next due date ("next ..."), and the amount ("≈" for variable or estimated amounts). **Edit** opens the bill's form.

"No bills yet. Add rent, utilities, insurance, subscriptions, pay and regular transfers." means none are set up.

## The Calendar tab {#calendar-tab}

**Calendar** shows a month, weeks starting on Monday, with each day's due dates and amounts. Use **◀** and **▶** to change month. Today's date is in bold.

- Due dates still to pay are in normal text; overdue ones in red.
- Paid due dates are greyed; skipped ones are paler still.
- A day shows up to three bills, then "+n" for the others.

The [Calendar](calendar) screen shows the same due dates together with appointments, health and renewal dates.

## The Subscriptions tab {#subscriptions-tab}
@index: subscription cost; yearly cost

**Subscriptions** lists every active bill marked **Subscription**, the most expensive first. Each line shows how it repeats, the amount, the next due date, the "cancel by" date if you set one, and its cost per year, in bold. At the bottom, "All subscriptions: ... a year" adds them up (one total per currency).

The yearly cost is the amount multiplied by the number of payments in a year: 12 for monthly, about 26 for every two weeks, 24 for twice a month, 4 for quarterly, and so on. A one-time item counts for nothing.

"No subscriptions. Tick "Subscription" on a bill to track it here." means none are marked.

## The Cash flow forecast tab {#forecast-tab}
@index: cash flow; forecast; projected balance; will I have enough money

**Cash flow forecast** projects the balance of each account from today, using the bills, income and transfers still to pay.

- **30 days**, **60 days**, **90 days**: how far ahead to look. Default: 30 days.

For each account with something scheduled (or with a balance already below zero), a card shows:

- "Today ... · in the end ... · lowest ...": today's balance, the balance at the end of the period, and the lowest point in between.
- A red warning "n payments would overdraw this account" when payments would take a bank account below zero.
- One line per scheduled item: the date, the bill's name and the balance after it. Balances below zero are in red.

How it is counted:

- Today's balance is the account's current balance in the books, so import or enter recent transactions first.
- Overdue items not yet paid are counted today.
- Variable bills count their expected amount; amounts you entered count as entered.
- A transfer counts on both accounts. A transfer into an account in another currency is not counted on that account, since the amount it will arrive in is not known.
- Overdraft warnings are given for bank accounts only, not for credit cards or loans.
- Spending you did not schedule as a bill (groceries, gas) is not in the forecast.

## Export calendar {#export-calendar}
@index: iCalendar; ics; Google Calendar; Outlook; calendar file

**Export calendar…** saves the due dates of the next twelve months as a calendar file (bills.ics by default), in the iCalendar format that Google Calendar, Outlook, Apple Calendar and most calendar programs can import.

- Only due dates still to pay are included, as all-day events.
- Each event is named after the bill with its amount, for example "Hydro · ≈ 142.00 $".
- The file is a snapshot: export again after you change your bills or mark them paid.

## Reminders and notifications {#reminder-banner}
@index: reminder banner; notification; alert

Reminders about bills appear in two places:

- A coloured banner at the top of every other screen, such as "3 reminders  Hydro: due in 7 days (≈ 142.00 $) · Rent: due today (1,450.00 $)". Click it to open the screen of the first reminder. The banner also carries reminders for appointments, medication refills, renewals and maintenance.
- A system notification from RANN's Roost, checked every few minutes while the household is open. Each reminder is announced once per session.

A bill is in the reminders:

- on each day that matches one of its **Remind me (days before)** numbers ("due in 7 days", "due tomorrow");
- on its due date ("due today");
- every day it is overdue ("3 days overdue"), until you mark it paid or skip it;
- for a subscription, every day from 7 days before its **Cancel by (reminder)** date.

## Bills from scanned documents {#bills-from-documents}

A paper bill or e-bill imported on the [Documents](documents) screen can be recorded on its bill: the app recognizes the bill by your account number with the payee or by the payee's name, and **Record the amount on this bill** sets the actual amount for the nearest due date and keeps the document with it. Fill in **Payee** and **Your account number with the payee** on your bills so they are recognized.

## Where bills appear elsewhere {#elsewhere}

- The [Dashboard](dashboard) shows the bills of the next 7 days and their total.
- The [Calendar](calendar) shows every due date with appointments and other dates.
- Payments recorded from bills are ordinary transactions: they count in [Budgets](budgets), [Reports](reports) and tax figures under the bill's category.

## Who can do what {#permissions}

- Adding, editing, deleting, skipping, entering an amount and undoing a payment need **Edit** permission on the account group of the paying account.
- Marking a due date paid or received needs at least **Capture only** permission.
- A bill is visible to everyone who can open the account group of its paying account. See [Users](users).
