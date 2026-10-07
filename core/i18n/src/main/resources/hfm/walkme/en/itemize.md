# Itemize a receipt
@about: Split one receipt into its items, each with its category and its share of the sales taxes.

## Open the receipt {#open}
@screen: DOCUMENTS DocumentsTab.INBOX
@target: documents.review
@done: shown documents.window
@manual: documents#itemize

Itemize a receipt when one store sold things of different kinds, such as groceries and household supplies. On the Documents screen's **To review** tab, click **Review** on the receipt.

To split a transaction you type in a register instead, use **Itemize…** in its entry form.

## Check the total {#total}
@target: documents.window
@manual: documents#document-details

Check that **Kind** is **Receipt** and the **Total** matches the receipt: the items and taxes you type must add up to it.

## Open Itemize {#itemize}
@target: documents.itemize
@done: shown itemize.dialog
@manual: documents#itemize

Click **Itemize…**, next to **New transaction from this document**.

## Type the items {#items}
@target: itemize.add
@manual: documents#itemize

For each item, type its **Item** and **Amount** before taxes, choose its **Category**, and tick the tax chips charged on it, as the receipt marks them. **Add item** adds a line. Type a discount as a negative amount.

## The taxes {#taxes}
@target: itemize.dialog
@manual: documents#itemize

Under **Sales taxes on the receipt**, type the GST, HST, QST or PST printed on the receipt. The line under them shows the running total and whether it matches the receipt's total.

## Use the items {#use}
@target: itemize.use
@done: shown documents.transaction
@manual: documents#itemize

When they match, click **Use these items**. The new transaction form opens with one split line per category.

## Save the transaction {#save}
@target: documents.transaction.save
@done: added filed
@manual: documents#new-transaction

Choose **Paid with** and **For**, then click **Save**. The transaction is created with its split lines and sales taxes, and the receipt is attached to it.
