# Category rules

Category rules give imported transactions a category automatically. When an imported transaction's description contains the text of a rule, it gets that rule's category. Rules are in Settings, under Category rules.

## How rules are used

When you import a statement, each new line is checked against the rules, from top to bottom. The first rule that matches gives the category. If no rule matches, the app uses the payee's default category, then the category you used before for that payee in that account. You can always change the category afterwards.

## Add a rule

- Click Add.
- In Description contains, type the text to look for, for example HYDRO-QUEBEC or PAIE.
- Pick the Category.
- Optionally pick a payee under Also file under payee: matching lines also get that payee, whatever the bank's text says.
- Optionally fill in Amount at least and Amount at most, to limit the rule to a range. This helps when one store sells very different things, or when a deposit from your employer can be pay or an expense refund.
- Click Save.

## Review, change, move or remove a rule

Each rule in the list reads as the text, an arrow, and the category. Select a rule to see its amount limits and payee, and to change them in the form below; it keeps its place. Move up and Move down change the order the rules are tried in: put specific rules above general ones. To remove a rule, click Delete. Removing a rule does not change transactions already imported.

Payee aliases, on the Payees screen, clean up names; rules choose categories. The two work well together.
