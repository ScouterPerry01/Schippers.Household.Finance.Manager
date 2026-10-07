# Import a statement or a Quicken file
@about: Bring in your bank's downloaded transactions, or your whole history from Quicken.

## Download the statement {#download}
@manual: accounts#import-statement

On your bank's website, download the account's transactions, preferably as OFX, QFX or QBO (Quicken, Microsoft Money or QuickBooks format); otherwise as CSV.

Coming from Quicken, GnuCash or Moneydance instead? Export a QIF file with all accounts from that program, and skip to the step Import from Quicken.

## Open the account {#open-account}
@screen: ACCOUNTS
@target: accounts.row
@done: shown register.import
@manual: accounts#register

On the **Accounts** screen, click the account the statement is for. Its register opens on the right.

## Import the file {#import}
@target: register.import
@done: added statement
@manual: accounts#import-statement

Click **Import statement…** and pick the file you downloaded.

A CSV file opens a window to confirm which column is which: check the guess against the preview, tick **The first row contains column names** when it does, and choose **Import**. The layout is remembered for next time.

## Check what was imported {#results}
@target: reconcile.screen
@manual: accounts#import-results

The reconciliation opens with a summary of the import: new transactions, those already there, and those that need a decision. Categories filled in from a payee's habits are marked for review.

Follow the guide Reconcile a statement to finish it now, or **Back to the register** to do it later.

## Import from Quicken {#quicken}
@screen: ACCOUNTS
@target: accounts.quicken
@done: shown quicken.dialog
@manual: accounts#quicken-import

For a Quicken history, click **Import from Quicken…** under the account list and pick the QIF file.

## Choose where each account goes {#quicken-accounts}
@target: quicken.dialog
@manual: accounts#quicken-accounts

- **Dates in the file**: **Month first (03/14/2024)** or **Day first (14/03/2024)**, when the file does not say.
- **Store in**: the account group for the new accounts.
- For each Quicken account: tick it, then choose **Import into** a new account or one you have, and check its **Type** and currency.

Click **Import**. Importing the same file again adds nothing that is already there.
