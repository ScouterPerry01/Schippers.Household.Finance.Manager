# Loans and lines of credit
@about: Set up a car or personal loan with its schedule, and a line of credit with its limit and rate.

## Add the loan account {#account}
@screen: ACCOUNTS
@target: accounts.add
@done: shown account.dialog
@manual: accounts#loan-accounts

A loan is an account. On the **Accounts** screen, click **Add account**.

## What you owe {#owed}
@target: account.dialog.save
@done: added account
@manual: loans#set-up-loan-account

Choose the **Type**: **Loan** for a car, student or personal loan, **Line of credit** or **Home equity line of credit** for a line of credit. Enter what you owe as a negative **Opening balance**, such as -18500, on its **Opening date**. Click **Save**.

## A loan's terms {#loan-terms}
@screen: LOANS
@target: loans.row
@manual: loans#loan-terms

A loan with fixed payments gets its schedule on **Loans and mortgages**, in the menu under **Investing and borrowing**. Click the loan, then **Enter terms**.

## Fill in the terms {#terms}
@target: loans.terms
@done: shown loans.termsDialog
@manual: loans#loan-terms

Enter the **Principal**, the **Annual rate (%)**, **Interest compounded** (usually monthly for a loan), the **Amortization (years)**, the **Payment frequency** and the **First payment**, and the account it is **Paid from**. Click **Save**: the schedule, next payment and payoff date appear.

## Record the payments {#payments}
@screen: LOANS
@target: loans.recordPayment
@manual: loans#record-payment

Click **Record payment** for each payment, unless the payments come in with your bank statement as transfers to the loan.

## A line of credit {#line-of-credit}
@screen: ACCOUNTS
@target: accounts.row
@done: shown register.cardDetails
@manual: accounts#credit-accounts

A line of credit has no fixed schedule: it is not on Loans and mortgages. On the **Accounts** screen, click it to open its register.

## Its limit and rate {#limit}
@target: register.cardDetails
@manual: accounts#card-details

Click **Card details** and enter the **Credit limit** and the **Purchase rate (%)**. The register then shows the credit still available, and the Debt summary report under Reports lists the line with its rate.
