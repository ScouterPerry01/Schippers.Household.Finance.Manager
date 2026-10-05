# Categories

Categories sort money in and money out: Groceries, Electricity, Salary and wages. Every transaction, or each split of a transaction, can have a category, and budgets, reports and the tax package add amounts up by category. The screen is in the **Settings** group of the menu, under **Categories**.

## How categories work {#about-categories}

@index: category tree; subcategory; expense category; income category; classification

- There are two kinds: expense categories (money going out) and income categories (money coming in). A subcategory always has the same kind as its parent.
- Categories form a tree: Food, then Groceries, Restaurants and Coffee and snacks under it. A subcategory can have its own subcategories.
- Each category has a name in English and a name in French. The app shows the name in the language in use, so a bilingual household can switch at any time.
- A category can carry a tax treatment, such as Medical expenses or Charitable donations, which gathers its amounts for tax time.

## The default categories {#default-categories}

@index: starting categories; built-in categories; preset categories

A new household starts with a full set of Canadian categories, in both languages: Housing, Utilities, Food, Transportation, Health, Insurance, Children, Personal, Pets, Leisure, Travel, Gifts and donations, Education, Financial costs, Taxes, Payroll deductions, Work expenses, Self-employment expenses and Miscellaneous for spending; Employment income, Self-employment income, Rental income, Pensions, Government benefits, Investment income, Gifts received, Refunds and reimbursements, Tax refunds and Other income for income.

- Some defaults depend on the household's province: for example the Quebec Family Allowance and the Solidarity tax credit, or Employment Insurance outside Quebec. When the province is changed on [Household members](members), the defaults for the new province are added; nothing is removed.
- When a new version of the app brings new default categories, they are added once to existing households. A default category you archived is not brought back.
- Some default categories have a special meaning for the app, whatever you rename them to. For example Salary and wages counts as employment income in the tax package, and the payroll deduction categories (CPP / QPP contributions, EI / QPIP premiums, Pension plan contributions, Group RRSP contributions, Union and professional dues) go to their own lines of it. Pay stubs and foreign exchange fees also use their default categories. Renaming such a category is safe; it keeps its meaning.

> Tip: Rather than building your own tree, start with the defaults and archive what you do not need. Archiving keeps the special meanings intact.

## The Categories screen {#screen}

The left side lists every category in tree order: expense categories first, then income categories, each subcategory indented under its parent. A category with a tax treatment shows it after its name, such as "Child care · Child care expenses". Archived categories are greyed out.

The right side has three buttons at the top and the form below them:

- **New expense category**: starts a new top-level expense category.
- **New income category**: starts a new top-level income category.
- **New subcategory**: shown when a category is selected. Starts a new category under the one selected, of the same kind.

When nothing is selected, the form area says "Select a category to edit it, or add a new one."

## Add a category {#add-category}

1. To add a top-level category, click **New expense category** or **New income category**. To add one inside another, click the parent in the list, then **New subcategory**.
2. The form's title says what you are making: "New expense category", "New income category" or "New subcategory of" and the parent's name.
3. Type the **Name in English** and the **Name in French**.
4. Choose a **Tax treatment** if it applies.
5. Click **Save**. The new category is selected in the list.

### The category form {#category-fields}

- **Name in English**: the name shown when the app is in English. Required, unless the French name is filled in: if you leave one name empty when creating a category, the other is used for both.
- **Name in French**: the name shown when the app is in French.
- **Tax treatment**: (none), or one of the tax lines below. A new subcategory starts with its parent's treatment, which you can change. See [Tax treatment](categories#tax-treatment).
- **Archived (hidden from lists)**: shown for a category already saved. See [Archive a category](categories#archive-category).
- **Save**: saves the category. Nothing is saved until you click it.

## Tax treatment {#tax-treatment}

@index: tax flag; tax category; deductible; tax credit; donations; child care; tuition; moving expenses; employment expenses; self-employment; medical expenses

The tax treatment links a category to a part of the tax year. Every amount in that category, in any account, is then gathered for that purpose, for the person the transaction or split is for (or the account's owner).

- Medical expenses: marks spending on health care. The medical expense credit itself is worked out from the claims on the [Medical claims](medical) screen.
- Charitable donations: payments appear on the **Donations** tab of [Taxes](taxes), by person, with their official receipts.
- Political contributions: the same, as political contributions.
- Child care expenses: gathered in the year-end tax package as child care expenses.
- Tuition: gathered in the tax package as tuition.
- Self-employment: in the tax package, money in counts as business income and money out as business expenses.
- Employment expenses: gathered in the tax package as employment expenses.
- Moving expenses: gathered in the tax package as moving expenses.

A single split of a transaction can also carry its own tax treatment, which then wins over its category's. Changing a category's tax treatment changes every year's totals, past ones included, the next time you look at them.

> Note: The tax treatment is an aid to gather amounts, not tax advice. Check what you can claim with the CRA or Revenu Québec.

## Change a category {#change-category}

1. Click the category in the list.
2. Change its names, its tax treatment or its archived box.
3. Click **Save**.

A new name shows at once on every transaction, budget and report that uses the category, past ones included, and on the phone at its next update. A category cannot be moved under another parent, and there is no way to change its kind or its place in the list: to reorganize, create the category where you want it and archive the old one.

## Archive a category {#archive-category}

@index: delete a category; hide a category; remove a category

There is no delete button for categories: past transactions must keep theirs. To stop using one:

1. Click it in the list.
2. Tick **Archived (hidden from lists)**.
3. Click **Save**.

An archived category is no longer offered in the category pickers (transactions, bills, budgets, rules, payees, the phone). Transactions that already use it keep it, and reports still count them under it. To use it again, clear the box and click **Save**.

## Where categories are used {#where-used}

- The register of each account: the category of a transaction or of each split. See [Accounts](accounts).
- Statement imports: [Category rules](rules) and each payee's default category choose the category of new lines.
- [Payees](payees): a default category per payee.
- [Bills](bills): the category a bill is entered in when paid.
- [Budgets](budgets): a budget is set per category and compared with the actual amounts.
- [Reports](reports): spending and income by category, and custom reports.
- [Taxes](taxes): donations and the tax package, through the tax treatment.
- [Documents](documents): the category of a transaction made from a receipt.
- The phone: categories for receipts captured on RANN's Roost Mobile. See [Phones](phones).
