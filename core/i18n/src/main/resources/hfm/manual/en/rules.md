# Category rules

Category rules give imported transactions their category automatically. A rule says: when the description of an imported line contains this text (and, optionally, the amount is in this range), use this category. The screen is in the **Settings** group of the menu, under **Category rules**.

## How rules are applied {#how-rules-apply}

@index: automatic categorization; auto-categorize; import rules; categorize imports; matching

Rules are used when a statement is imported into an account, for example an OFX, QFX or CSV file from your bank, or a statement read by AI. See [Accounts](accounts).

For each line of the statement:

1. If the line is already in the books (same bank reference), it is skipped as a duplicate.
2. If it matches a transaction already entered, for example one you typed or one captured on the phone, the two are linked (or, when the match is not certain, proposed for you to confirm), and the transaction keeps its category.
3. Otherwise a new transaction is created, and its category is chosen in this order:
  - the first category rule that matches, trying the rules from the top of the list to the bottom;
  - if no rule matches, the default category of the payee (see [Payees](payees));
  - if the payee has none, the category of the payee's most recent transaction, when that transaction had a single category;
  - otherwise the line comes in without a category, for you to fill in.

The same order is used when you choose to add a new transaction for a statement line during reconciliation.

A rule matches when:

- The line's description, as the bank printed it, contains the rule's text anywhere, ignoring capitals. "HYDRO" matches "HYDRO-QUEBEC PAIEMENT" and "Hydro One".
- And, if the rule has amount limits, the size of the amount is within them. The limits apply to the size of the amount, whether money in or money out: a limit of 100 matches a payment of 100 and a deposit of 100.

Rules never change transactions already in the books. Adding, or deleting, a rule only affects lines imported afterwards. You can always change a category afterwards in the register.

> Note: Rules apply to imported statements only. When you type a transaction yourself, the register suggests the payee's last amount and category instead.

## The Category rules screen {#screen}

The left side lists the rules, with **Add** above the list. Each rule reads as its text in quotation marks, an arrow, and its category, such as "HYDRO-QUEBEC" → Electricity. The list is in the order the rules are tried: a new rule goes to the bottom, and **Move up** and **Move down** change its place.

The right side begins with the reminder "When an imported transaction's description contains the text, it gets the category. Rules are tried from top to bottom." Below it is the selected rule, the form for a new rule, or "Select a rule, or add a new one."

## Add a rule {#add-rule}

1. Click **Add**.
2. Type the text in **Description contains**.
3. Pick the **Category**.
4. If the line should also get a particular payee, pick it in **Also file under payee**.
5. If needed, fill in **Amount at least** and **Amount at most**.
6. Click **Save**. It becomes available once the text and the category are filled in.

### The rule form {#rule-fields}

- **Description contains**: the text to look for in the bank's description, for example HYDRO-QUEBEC or PAIE. Required. Capitals do not matter. Use a piece that is always there and is specific enough: the name without store numbers, cities or dates.
- **Category**: the category to give. Required. Archived categories are not offered.
- **Also file under payee**: optional. A payee to give the line too, whatever its aliases would find, such as Hydro-Québec for every line containing HYDRO. Leave it at (keep the payee found) to let the aliases and the bank's text name the payee as usual. Archived payees are not offered.
- **Amount at least**: optional. The rule matches only when the size of the amount is at least this much. Typed in the household's base currency (Canadian dollars in most households).
- **Amount at most**: optional. The rule matches only when the size of the amount is at most this much. When both are filled in, the minimum cannot be larger than the maximum ("The minimum amount is larger than the maximum.").
- **Save**: adds a new rule at the bottom of the list, or saves the changes to a rule, which keeps its place.

A rule with amount limits matches only lines in the same currency as its limits. A rule with no limits matches lines in every currency.

> Tip: Use amount limits to tell apart lines with the same description. For example, a payroll deposit of at least 1,000 is Salary and wages, while a smaller one from the same employer is an expense refund.

## See, change, move or delete a rule {#rule-details}

Click a rule in the list. The right side shows its summary and, when it has them, its limits ("Amount at least" and "Amount at most" with their amounts) and its payee ("Payee:" and the name). Below are the buttons and, under **Edit**, the rule form filled in with the rule.

- **Move up**: tries the rule one place sooner. Greyed out for the first rule.
- **Move down**: tries the rule one place later. Greyed out for the last rule.
- **Delete**: removes the rule at once, without asking. It cannot be undone, but you can add the same rule again. Transactions already imported keep their categories.
- The form under **Edit**: change the text, category, payee or limits, then click **Save**. The rule keeps its place in the list.

The places stay numbered in order: moving or deleting a rule never leaves two rules in the same place.

## Order matters {#order}

@index: rule priority; first match

The first rule that matches wins, so specific rules must come before general ones. For example, "COSTCO GAS" → Fuel must be above "COSTCO" → Groceries; otherwise "COSTCO" would also catch the gas station lines.

New rules go to the bottom. When you add a specific rule after a general one, select it and click **Move up** until it is above the general rule.

## Rules and payees {#rules-and-payees}

- Payee aliases on [Payees](payees) clean up the name of an imported line. Rules choose its category.
- A rule is tried before the payee's default category, so a rule can override the default for some lines, such as large purchases at a store whose default is Groceries.
- A rule's payee, when it has one, wins over the payee the aliases would find.
- Every rule you add, change, move or delete is recorded in the activity log on the [Users](users) screen.
- Administrators and members can add, change, move and delete rules. Viewers see the list and each rule, without the buttons.
