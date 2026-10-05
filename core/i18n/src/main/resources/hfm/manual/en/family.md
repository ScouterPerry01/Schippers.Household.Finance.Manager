# Family money

Family money keeps track of money that moves between people rather than between your accounts: expenses shared on a trip or with roommates, money lent within the family, and children's allowances. It is in the **Money** group of the menu, under **Family money**.

Nothing on this screen creates transactions in your accounts or changes your balances, budgets or reports. It is a separate notebook of who owes whom. When real money changes hands (you e-transfer a friend, you hand your child cash), record that payment in the account register as usual if you want it in your books too.

@index: money between people; IOU; who owes whom

## The Family money screen {#screen}

The screen has a short explanation at the top and three tabs:

- **Shared expenses**: groups of people who share costs, with each person's balance and the payments that settle everyone.
- **Family loans**: money lent between family members or friends, with optional simple interest and a repayment log.
- **Allowances**: a child's regular allowance, what is still owed to them, and the money they have.

The screen opens on **Shared expenses**. The tab you choose is kept only while you stay on the screen.

### Where the records are kept {#where-kept}

New groups, loans and allowances are stored in the shared account group you are allowed to add to (or, if there is none, the first group you can add to). They are listed together with those of every group you can see. Saving, changing or deleting needs permission to change records in that group: a user with read-only access sees the tab but gets an error when saving. See [Users](users).

### Deleting asks first {#immediate}

Every **✕** button on this screen (an expense, a repayment, an allowance entry) and every **Delete** button asks first, saying what will be deleted; **Cancel** keeps it. Once confirmed, there is no undo; enter it again if you removed it by mistake.

## Shared expenses {#shared-expenses}

@index: split the bill; split costs; roommates; trip expenses; settle up; cottage weekend

Use a shared group for anything where several people pay for things and the costs are split: a trip, a cottage weekend, a shared apartment, a gift bought together. Each person can be a member of the household or someone outside it.

The tab has two parts: on the left, the list of groups; on the right, the group you selected.

### The list of groups {#group-list}

- **New shared group…**: opens the [New shared group dialog](#group-dialog).
- The groups are listed by name, active groups first, then archived ones marked **(archived)**. Click a group to show it on the right. When no group is selected, the first one is shown.
- With no groups yet, the list says so and suggests starting one.

### The selected group {#group-view}

At the top are the group's name and two buttons:

- **Add an expense…**: opens the [Add an expense dialog](#expense-dialog).
- **Edit the group…**: opens the same dialog as **New shared group…**, to rename the group, change its people or archive it.

Below, one card per person shows where they stand:

- **is owed** an amount: this person paid more than their share; others owe them.
- **owes** an amount, in red: this person paid less than their share.
- **all even**: nothing owed either way.

A person's balance is everything they paid, less their share of every expense, adjusted for the settlement payments they made or received.

### To settle up {#settle-up}

When balances are not all even, **To settle up** lists the payments that make everyone even, one line each, such as "Sam → Alex: $42.50". The list uses the fewest payments: the person who owes the most pays the person owed the most, and so on until everyone is even.

- **Mark paid**: records that payment as a settlement in the group, dated today, described as **Settlement**. The balances and the list update right away. Use it once the money has actually changed hands. It does not create a transaction in your accounts.

### The expense list {#expense-list}

Below the cards, every expense and settlement of the group is listed, newest first:

- the date;
- what it was for, and under it either "Paid by Alex, shared by Alex, Sam, Léa" (only the people with a share of more than zero are named) or, for a settlement, "Sam paid Alex";
- the amount;
- **✕**: asks "Delete "description" (amount, date)? Who owes whom is worked out again." and, once confirmed, deletes that expense or settlement. The balances are worked out again without it.

To correct an expense, delete it and add it again.

### New shared group dialog {#group-dialog}

The same dialog creates a group (**New shared group…**) or changes one (**Edit the group…**).

- **Name (a trip, an apartment…)**: the group's name, shown in the list. Required.
- **People**: one line per person. A new group starts with two empty lines.
  - **Name**: the person's name as you want it on the cards and in the expense list. A line left without a name is ignored when you save.
  - **Household member**: optionally links the person to a member of the household. Choose **Not in the household** for a friend or relative outside it. Choosing a member fills in the name when the name is still empty. The link is for your reference; the balances work the same either way.
  - **✕**: removes that person's line.
- **Add a person**: adds an empty line.
- **Archive this group (all settled)**: shown only when editing. An archived group moves to the end of the list with **(archived)** after its name. Its expenses and balances are kept and you can still open it, add to it or unarchive it.
- **Delete**: shown only when editing. Asks "Delete the group "name" with all its expenses and settlements? Who owes whom is lost." and, once confirmed, deletes the group, its people and all its entries. It cannot be undone; archive a group to keep its history instead.
- **Save** saves the group; **Cancel** closes without saving.

Rules checked when you save:

- A group needs at least two people with a name.
- A person who already has an expense, a share or a settlement in the group cannot be removed. Delete those entries first.

The group's amounts are in the household's base currency, as it was when the group was created.

### Add an expense dialog {#expense-dialog}

- **Date**: when the expense was paid, as YYYY-MM-DD. Today by default.
- **What it was for**: a short description, such as "Groceries" or "Boat rental". If left empty, the expense shows a dash.
- **Amount**: what was paid, in the group's currency. Required and more than zero. A minus sign is ignored.
- **Paid by**: the person who paid. The first person of the group by default.
- **Shares**: one box per person, 1 by default. The amount is split in proportion to the shares:
  - 1 each splits equally;
  - 2 for someone who counts double, such as a couple sharing one line;
  - 0 leaves someone out of this expense.
  Only whole numbers can be typed. At least one person must have a share.

The split is exact to the cent: any leftover cents go to the people whose share was rounded down the most, so the shares always add up to the amount.

Saving adds the expense to the list and updates every card and the **To settle up** list.

## Family loans {#family-loans}

@index: personal loan; loan to a child; loan to a parent; lending money to family; IOU

Record here money lent between family members or friends: a parent helping with a car repair, a loan to a sibling, money a child borrowed. These loans are kept apart from the bank loans and mortgages on the [Loans and mortgages](loans) screen and are not part of your net worth.

The interest, if any, is simple interest on what is still owed, counted day by day at the yearly rate. Each repayment pays the interest owed so far first, then the amount lent.

> Note: The CRA has rules on loans at low or no interest between spouses and with family members (the attribution rules), which can make the income earned with the money taxable in the lender's hands. Ask an advisor before setting one up. RANN's Roost does not give tax advice.

### The list of loans {#loan-list}

- **Add a family loan…**: opens the [loan dialog](#loan-dialog) for a new loan.
- Each loan shows "Lender to Borrower" in bold, with **(repaid)** when closed, and below it the amount lent and its date, the rate if there is one ("2.50 % a year") and what has been repaid so far.
- On the right: what is still owed today, interest included, and, when some of it is interest, "including … of interest".
- Open loans are listed first, newest first; closed loans come after.
- Click a loan to open its [repayments](#repayments-dialog).

### Add a family loan dialog {#loan-dialog}

The same dialog is used to add a loan and, from the repayments window, to change one (**Edit the loan…**).

- **Lent by**: who lent the money. Any name; it need not be a household member. Required.
- **Lent to**: who borrowed it. Required.
- **Amount lent**: the amount lent, more than zero, in the household's base currency.
- **Date lent**: the day the money was lent, as YYYY-MM-DD. Interest, if any, runs from this day, and no repayment can be dated earlier. Today by default.
- **Interest rate (% a year)**: the yearly rate, such as 2.5 (a comma also works). Leave it empty for a loan with no interest. From 0 to 50 %; it is kept to two decimals, rounded to the nearest (3.125 becomes 3.13).
- **Notes**: anything to remember, such as what the money was for or the agreed repayment plan.
- **Repaid in full (close it)**: shown when changing a loan. A closed loan shows **(repaid)** and moves to the end of the list, and its interest stops: no interest is counted after its last repayment (after the date lent, when there is none). The amounts shown still come from the repayments recorded, so record the last repayment too; anything still shown as owed is what those repayments left. Unticking it lets the interest run again, up to today.
- **Delete**: shown when changing a loan. Asks "Delete the loan from lender to borrower with its repayments?" and, once confirmed, deletes the loan and all its repayments.

### Repayments window {#repayments-dialog}

Clicking a loan opens a window titled "Lender to Borrower".

- The first line sums up the loan today: **Still owed** (what is left of the amount lent plus the interest not yet paid), **interest so far** (all the interest counted since the loan was made) and **repaid** (the total of the repayments).
- The repayments are listed newest first, each with its date, its amount and **✕** to delete it. It asks "Delete the repayment of amount on date? The balance is worked out again." first; once it is deleted, the window closes (open the loan again to see the new figures).
- **Date** and **Repayment**: enter a new repayment, then click **Add the repayment**. The button is available once an amount is entered. A repayment cannot be dated before the loan. A repayment dated in the future counts only from its date.
- **Edit the loan…**: opens the [loan dialog](#loan-dialog).
- **Close**: closes the window.

## Allowances {#allowances}

@index: pocket money; kids' allowance; children's money; chores

An allowance is a fixed amount a child receives on a schedule. RANN's Roost counts each allowance day as owed to the child until you mark it paid, and keeps the child's own money: what they were paid, earned or received, less what they spent.

### The list of allowances {#allowance-list}

- **Set up an allowance…**: opens the [allowance dialog](#allowance-dialog). It appears once the household has at least one member (see [Household members](members)); it starts with the first member whose kind is a child, or else the first member.
- Each allowance shows the person, the amount and how often (such as "Léa · $10.00 a week"), and below it **Has** (the child's money today) and, while the allowance runs, **next on** (the next allowance day).
- When allowance days have passed without being paid, the line shows **… owed** in red and a **Mark paid** button. **Mark paid** records one payment, dated today, for the whole amount owed.
- Click an allowance to open [the child's money](#allowance-entries).

### Set up an allowance dialog {#allowance-dialog}

- **Person**: the household member who receives the allowance.
- **Amount**: the amount for each allowance day, more than zero, in the household's base currency.
- **How often**: **a week**, **every two weeks** or **a month**. Allowance days are counted from the first day: every 7 days, every 14 days, or the same day each month (an allowance starting on the 31st falls on the last day of shorter months).
- **First day**: the first allowance day, as YYYY-MM-DD. Today by default.
- **Last day (optional)**: the last day the allowance runs. Leave empty for no end. It cannot be before the first day. After it, no more allowance days are counted.
- **Notes**: anything to remember, such as what the allowance is meant to cover or the rules agreed with the child. Optional; several lines can be typed.
- **Delete**: shown when changing an allowance. Asks "Delete the allowance of name with all its entries?" and, once confirmed, deletes it with all its entries.

Changing the amount, frequency or dates later recounts what was due since the first day, at the new amount.

### Allowance and the child's money {#allowance-entries}

Clicking an allowance opens this window.

- The first line sums it up: **Due so far** (the number of allowance days up to today, or up to the last day, times the amount), **owed** (due so far less the allowance paid, never below zero) and **the child has** (allowance paid plus money earned or received, less money spent).
- The last 30 entries are listed newest first: date, what kind, the note, and the amount (spending shown as negative). **✕** asks "Delete this entry (kind, amount, date)?" and, once confirmed, deletes the entry; the window closes.
- To add an entry, fill in:
  - **Date**: today by default. An entry dated in the future counts only from its date.
  - **What**:
    - **Allowance paid**: you gave the child their allowance. It reduces what is owed and adds to the child's money.
    - **Earned or received**: money from chores, a birthday gift, a sale. It adds to the child's money only.
    - **Spent**: money the child spent. It is taken off the child's money.
  - **Amount**: more than zero.
  - **Notes**: such as "Raked the leaves" or "Book".
  Then click **Add**, which is available once an amount is entered.
- **Edit the allowance…**: opens the [allowance dialog](#allowance-dialog).
- **Close**: closes the window.

> Tip: Use the child's money as a savings jar you keep for them: record what they earn and spend, and **Has** always tells you how much is theirs.
