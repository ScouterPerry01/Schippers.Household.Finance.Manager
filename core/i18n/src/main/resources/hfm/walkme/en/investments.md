# Investment accounts and holdings
@about: Add a brokerage or plan account, bring in what it holds, and keep its prices and transactions up to date.

## Add the investment account {#account}
@screen: ACCOUNTS
@target: accounts.add
@done: shown account.dialog
@manual: investments#investments-screen

Investment accounts are created on the **Accounts** screen. Click **Add account**.

## Its type and owners {#type}
@target: account.dialog.save
@done: added account
@manual: accounts#investment-accounts

Choose the **Type**: **Brokerage (non-registered)**, **RRSP**, **TFSA**, **FHSA**, **RESP** and so on. Tick its **Owners**: they decide whose capital gains and whose contribution room it counts for. Enter the cash in the account as the **Opening balance**. Click **Save**.

## Open Investments {#open}
@screen: INVESTMENTS
@done: screen
@manual: investments#account-list

In the menu, open **Investing and borrowing**, then **Investments**, and click the account in the list.

## Import from your brokerage {#import}
@screen: INVESTMENTS
@target: investments.import
@manual: investments#import-statement

If your brokerage lets you download an OFX, QFX or CSV file, click **Import statement…** and pick it: the securities, buys, sells and dividends come in at once, and importing again adds nothing twice.

Otherwise, enter what the account holds by hand, as follows.

## Add a transaction {#add}
@screen: INVESTMENTS
@target: investments.add
@done: shown investments.txn
@manual: investments#transaction-dialog

Click **Add transaction**.

## Units you already hold {#units}
@target: investments.txn.save
@manual: investments#transaction-fields

For what you held before you started, choose the **Type** Units moved in, pick the **Security** (or **New security…** to add it), the **Quantity**, and their original book cost from your statement: the adjusted cost base depends on it. For a purchase, choose Buy, with the **Price** and the commission.

Click **Save**. Each holding then shows on the **Holdings** tab with its market value and gain.

## Keep prices current {#prices}
@screen: INVESTMENTS
@target: investments.updatePrices
@manual: investments#update-prices

Click **Update prices** to enter today's price of each security. Prices for stocks and ETFs can also be downloaded: turn it on under Rates and prices.
