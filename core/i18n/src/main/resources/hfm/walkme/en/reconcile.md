# Reconcile a statement
@about: Check the books against the bank's statement, to the cent, and lock the period.

## Open the account {#open-account}
@screen: ACCOUNTS
@target: accounts.row
@done: shown register.reconcile
@manual: accounts#reconcile

On the **Accounts** screen, click the account. An imported statement is reconciled from its register; a paper statement too.

## Start the reconciliation {#start}
@target: register.reconcile
@done: shown reconcile.screen
@manual: accounts#reconcile

Click **Reconcile…**. It continues the statement in progress, such as the one just imported.

With no statement in progress, Statements opens: click **Enter a paper statement**, type the **Statement date** and the **Closing balance on the statement**, and click **Save**.

## The statement's balance {#balance}
@target: reconcile.closing
@manual: accounts#reconcile-balance

Check the **Statement date** and the **Closing balance on the statement** against the statement; they are filled in from the file when it gives them. Change them if needed and click **Apply**.

The difference between the statement and the books' cleared balance shows beside them, green at zero.

## Settle each line {#lines}
@target: reconcile.screen
@manual: accounts#reconcile-attention

Under Needs your attention, choose one action for each line:

- **Same transaction**: it is the transaction proposed.
- **Add as new**: you had not recorded it; it is added.
- **Link to a recorded transaction**: pick the one it is.
- **Ignore**: not a real transaction.

## Tick what cleared {#outstanding}
@target: reconcile.screen
@manual: accounts#reconcile-outstanding

Under Recorded but not on this statement, tick each transaction that does appear on the statement. Leave cheques not yet cashed unticked: they wait for the next statement.

## Finish {#finish}
@target: reconcile.finish
@done: added reconciled
@manual: accounts#reconcile-finish

When the difference is zero and every line is settled, click **Finish reconciliation**. The cleared transactions are marked reconciled and locked, and the account list shows the new date.

If the difference will not go to zero, look for a transaction entered twice, an amount typed wrong, or a line ignored by mistake.
