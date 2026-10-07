# Pay a bill in part
@about: Pay part of a bill now and the rest later, and see what is still due.

## Open To pay {#open}
@screen: BILLS BillsTab.AGENDA
@done: screen
@manual: bills#to-pay-tab

In the menu, open **Money**, then **Bills**, and the **To pay** tab.

## Type the amount you pay now {#amount}
@screen: BILLS BillsTab.AGENDA
@target: bills.toPay
@manual: bills#pay-in-part

On the bill's line, the **To pay** column starts at what is still outstanding. Type the part you pay now, such as half.

A variable or estimated bill whose amount is not set yet is paid in full by what you pay: first click **Set the bill's amount** on its line.

## Mark it paid {#pay}
@target: bills.markPaid
@done: shown bills.pay
@manual: bills#pay-in-part

Click **Mark paid** on the same line.

## Record the payment {#record}
@target: bills.pay.save
@manual: bills#pay-in-part

The form shows **Still due** and the amount you typed. Check the **Date paid** and click **Save**. The payment is recorded as its own transaction.

## Pay the rest later {#rest}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@manual: bills#pay-in-part

The bill stays on the list with the rest under **Outstanding**, and is still reminded about. When you pay the rest, click **Mark paid** again: it proposes what is left. Paying more than is due asks you first.
