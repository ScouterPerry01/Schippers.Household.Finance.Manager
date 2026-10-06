# Getting started with accounts and transactions

This guide takes you from an empty household to accounts that agree with the bank, in five steps. It uses only the options you need on day one; each step links to the full description in [Accounts](accounts).

## Before you start {#before-you-start}

@index: first steps; set up accounts; new household

Have these at hand:

- the last statement of each account you want to follow (chequing, savings, credit cards, lines of credit, loans), with its closing date and closing balance;
- if you can, a way to download transactions from your bank's website, as OFX, QFX, QBO or CSV files;
- if you used Quicken before, a QIF export of all its accounts.

> Tip: Add the people in the household first, under [Household members](members), so you can mark who owns each account.

## Step 1: add your accounts {#add-accounts}

1. In the menu, open **Accounts** and choose **Add account**.
2. Type an **Account name** you will recognize, such as Joint chequing.
3. Choose the **Type**: Chequing, Savings, Credit card, Mortgage and so on. It cannot be changed later.
4. Leave **Currency (e.g. CAD, USD, BTC)** at CAD unless the account is in another currency.
5. For **Opening balance** and **Opening date**, use the closing balance and closing date of the last statement. For a credit card or a loan, type what you owe as a negative amount, such as -1250.
6. Optionally choose the **Institution**, type the **Account number** (only the last four digits are shown) and tick the **Owners**.
7. Choose **Save**. The account appears in the list and its register opens.

Repeat for each account. For a credit card, choose **Card details** in its register afterwards to enter the rates and the annual fee. All the fields are described in [Fields of the account form](accounts#account-fields).

## Step 2: bring in your history {#bring-in-history}

You can start from today and only add new transactions, or bring in what you already have:

- From Quicken: choose **Import from Quicken…** under the account list, pick the QIF file, check where each Quicken account goes and choose **Import**. See [Import from Quicken](accounts#quicken-import). In that case, you can skip step 1 for the accounts Quicken creates.
- From the bank: open the account, choose **Import statement…** and pick the downloaded file. For a CSV file, check the columns once; the layout is remembered. See [Import a statement](accounts#import-statement).

Imported transactions come in already categorized by your rules and payees, and already cleared.

## Step 3: enter transactions as they happen {#enter-transactions}

For cash spending, cheques, or anything you want in the books before the statement arrives:

1. Open the account and go to the form under the register.
2. Check the **Date** (+ and - change the day).
3. Type the **Payee**. Picking a known payee fills in the amount and category used last time.
4. Choose the **Category**, or Transfer: and the other account when money moves between your accounts, such as a credit card payment.
5. Type the amount as a **Payment** (money out) or a **Deposit** (money in).
6. Press Enter. The form clears for the next one, keeping the date.

When the statement is imported later, these transactions are matched with its lines instead of being added twice. For several categories on one receipt, use **Split…**. See [Enter a transaction](accounts#enter-transaction).

## Step 4: categorize {#categorize}

Categories make budgets and reports useful.

1. On the Dashboard, look under Needs your attention for a line such as 5 transactions have no category.
2. In each register, click a transaction showing (uncategorized), choose its **Category** and save.
3. For payees that come back every month, add a rule under [Category rules](rules) so the next imports are categorized by themselves, or set a default category for the payee under [Payees](payees).

## Step 5: reconcile each statement {#reconcile-statements}

Reconciling proves the account agrees with the bank to the cent, and locks the period.

![Reconciling a statement](images/accounts-reconcile.png)

1. Import the statement (step 2). The reconciliation opens. With a paper statement, choose **Reconcile…**, then **Enter a paper statement**, and type the date and closing balance.
2. Check the **Statement date** and the **Closing balance on the statement**; correct them and choose **Apply** if needed.
3. Under Needs your attention, settle each line: **Same transaction**, **Add as new**, **Link to a recorded transaction** or **Ignore**.
4. Under Recorded but not on this statement, tick what does appear on the statement; leave cheques not yet cashed unticked.
5. When the Difference is zero, choose **Finish reconciliation**.

The account list then shows Reconciled to with the statement date. See [Reconcile a statement](accounts#reconcile).

## Keeping up {#keeping-up}

@index: routine; monthly routine

A simple routine keeps the books right:

- Each week or so, enter receipts and cash spending, or send them from the phone.
- Each month, when a statement is out, import it and reconcile it.
- Glance at the Dashboard: Needs your attention lists overdue bills, statement lines waiting, transactions with no category and accounts not reconciled in more than 45 days. See [Dashboard](dashboard).

## Where to go next {#next}

- Set up your bills and pay days so you are reminded before they are due: [Bills](bills).
- Set monthly spending targets: [Budgets](budgets).
- Follow your investments and registered plans: [Investments](investments) and [Registered plans](plans).
- Enter the terms of your loans and mortgage: [Loans and mortgages](loans).
- See where the money goes: [Reports](reports).
