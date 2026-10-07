# Municipal property taxes
@about: Set up your property tax bill, then enter each year's tax bill with its instalments.

## Open Bills {#open}
@screen: BILLS BillsTab.ALL
@done: screen
@manual: bills#instalments

Property taxes come once a year and are paid in instalments on dates printed on the tax bill. In the menu, open **Money**, then **Bills**.

## Add the property tax bill {#add}
@screen: BILLS
@target: bills.add
@done: shown bill.dialog
@manual: bills#instalments

Click **Add a bill**.

## Describe it {#describe}
@target: bill.dialog.save
@done: added bill
@manual: bills#classification

- **Name**: such as Property taxes.
- **Home or business**: Home. **Bill category**: the housing category; **Subcategory**: Property Taxes. It sets **Repeats** to **Instalments on set dates** for you.
- **Paid from**: the account you pay it from.
- **Amount**: the year's total, or a typical amount.

Click **Save**.

## Open the bill again {#edit}
@screen: BILLS BillsTab.ALL
@target: bills.edit
@done: shown bill.dialog
@manual: bills#statements

When the tax bill arrives, find the bill on the **All bills** tab and click **Edit**.

## Add the tax bill {#statement}
@target: bills.addStatement
@done: shown bills.statement
@manual: bills#statements

Under **Statements**, click **Add a statement**. You can also capture the paper bill on the Documents screen; see the guide Capture and process a bill.

## Enter the instalments {#instalments}
@target: bills.addInstalment
@manual: bills#instalments

Fill in the amount and the dates from the tax bill. Click **Add an instalment** for each instalment, with its **Due date** and **Amount**, then click **Save**.

Some cities send two bills a year, such as an interim bill and a final one: add each as its own statement.

## Pay each instalment {#pay}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@manual: bills#mark-paid

Each instalment is now a due date of the bill, with its reminders, shown as instalment 2 of 3. Pay each one with **Mark paid**, in full or in part.

Until next year's bill is entered, next year's instalments are proposed on the same dates with this year's amounts, marked as estimated.
