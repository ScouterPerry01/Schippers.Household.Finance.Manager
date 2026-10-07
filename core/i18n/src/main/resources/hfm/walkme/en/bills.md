# Bills
@about: Set up a bill that repeats, with its reminders, then mark it paid when you pay it.

## Open Bills {#open}
@screen: BILLS BillsTab.AGENDA
@done: screen
@manual: bills#overview

In the menu, open **Money**, then **Bills**. The **To pay** tab lists what is overdue and due soon.

## Add a bill {#add}
@screen: BILLS
@target: bills.add
@done: shown bill.dialog
@manual: bills#bill-form

Click **Add a bill**.

## Name and account {#name}
@target: bill.dialog
@manual: bills#type-and-name

- **Type**: **Bill**, **Income** (such as your pay) or **Transfer** (such as to savings).
- **Name**: as you want to see it, such as Hydro or Rent.
- **Home or business**, **Bill category** and **Subcategory**: what the bill is for; the subcategory fills in the **Category** for you.
- **Paid from**: the account the money leaves. It cannot be changed later.

## Amount and schedule {#schedule}
@target: bill.dialog
@manual: bills#repeats

- **Amount** and **Amount is**: **Fixed** for the same amount each time, **Variable** for a bill such as hydro, **Estimated** for a guess.
- **Repeats**: such as **Monthly** or **Every two weeks**.
- **First due date**: the next date it is due.

## Reminders and save {#reminders}
@target: bill.dialog.save
@done: added bill
@manual: bills#reminders

**Remind me (days before)**: for example 7, 1 for a week before and the day before. A bill due today or overdue is always reminded.

Click **Save**. The bill's next due date appears on the **To pay** tab, the calendar and the cash flow forecast.

## Mark it paid {#pay}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@done: shown bills.pay
@manual: bills#mark-paid

When you pay it, click **Mark paid** on its line.

## Record the payment {#record}
@target: bills.pay.save
@manual: bills#mark-paid

Check the **Date paid** and the **Amount**, then click **Save**. A transaction is added to the account, and the next due date takes its place. When you import the bank statement, its line is matched with this transaction instead of being added twice.
