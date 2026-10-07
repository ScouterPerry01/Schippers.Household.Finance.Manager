# Mortgages
@about: Add your mortgage with what you owe, enter its terms, and record payments and renewals.

## Add the mortgage account {#account}
@screen: ACCOUNTS
@target: accounts.add
@done: shown account.dialog
@manual: loans#set-up-loan-account

The mortgage is an account. On the **Accounts** screen, click **Add account**.

## What you owe {#owed}
@target: account.dialog.save
@done: added account
@manual: loans#set-up-loan-account

Choose the **Type** **Mortgage**. For **Opening balance**, enter what you owe as a negative amount, such as -350000, and for **Opening date** the start of the current term. Click **Save**.

## Open Loans and mortgages {#open}
@screen: LOANS
@done: screen
@manual: loans#loans-screen

In the menu, open **Investing and borrowing**, then **Loans and mortgages**.

## Choose the mortgage {#choose}
@screen: LOANS
@target: loans.row
@done: shown loans.terms
@manual: loans#loan-list

Click the mortgage in the list on the left. Until its terms are entered, it says so in red.

## Enter the terms {#terms}
@target: loans.terms
@done: shown loans.termsDialog
@manual: loans#loan-terms

Click **Enter terms**.

## The terms of the current term {#fields}
@target: loans.termsDialog.save
@manual: loans#loan-terms

- **Principal**: the balance at the start of the current term.
- **Annual rate (%)** and **Rate type**.
- **Interest compounded**: **Semi-annually (Canadian mortgages)** for a fixed rate; many variable rates compound monthly: check your agreement.
- **Amortization (years)**: the time left to repay it in full.
- **Payment frequency**, **First payment** and **Lender's payment (if known)**, so the schedule matches the lender's statements.
- **Term ends (renewal date)**: you are reminded before the renewal.
- **Paid from**: the account the payments come from.

Click **Save**. The schedule, the next payment and the payoff date appear.

## Record each payment {#payment}
@screen: LOANS
@target: loans.recordPayment
@manual: loans#record-payment

Click **Record payment**: the interest, principal, property tax and insurance are worked out from what you owe. Click **Save**. If the payments come in with your bank statement as transfers to the mortgage, you do not need this.

## At renewal {#renew}
@screen: LOANS
@target: loans.renew
@manual: loans#renew

At the end of the term, click **Renew** and enter the new rate and the new term end. To compare prepayments or another payment first, use **What if…**.
