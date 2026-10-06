# Quick Start

This chapter takes you from the first start of RANN's Roost to a working household, in about half an hour. It skips everything that can wait. Each step links to the chapter that tells the whole story, if you want it.

## Before you begin {#before}

@index: what you need; first steps

Have these at hand:

- a place for the household on your computer, such as your Documents folder;
- a password of at least 12 characters, without your login name, that you will remember (a few words in a row work well);
- a printer or a pen and paper, for the recovery key;
- the current balances of your bank accounts and cards;
- a recent statement downloaded from your bank's website, as an OFX, QFX, QBO or CSV file (look for "Download transactions" or "Export" on the bank's site);
- for backups, an external drive or a cloud folder (OneDrive, Google Drive, Dropbox);
- optionally, an Android phone with RANN's Roost Mobile installed.

## Step 1: Start the app {#first-start}

@index: first start; update question

Start RANN's Roost. The start screen, **Welcome**, appears.

On a Linux copy installed from a .deb or .rpm package or an AppImage, the app first asks **Check for updates?**. Click **Check once a day** to hear about new versions, or **Don't check**. Only the check itself goes out; nothing about your household is sent. You can change your answer later under [About](about). Other copies, such as those from the Microsoft Store or Flathub, do not ask: the store updates them.

> Tip: the **English** and **Français** buttons at the top switch the language at any time. The app remembers your choice on this computer.

## Step 2: Create your household {#create}

@index: create a household; new household

1. Click **Create a new household**.
2. Click **Choose folder…** and pick where the household will live, for example your Documents folder. The app makes a folder there named after the household.
3. Fill in the form:
  - **Household name**: a name for the household, such as Tremblay Family.
  - **Province or territory**: where you live. It sets bank holidays, default categories, provincial grants and tax forms. Nothing is chosen at first: pick yours.
  - **Your name**: how you appear in the app.
  - **Login name**: the short name you will type to sign in, without spaces.
  - **Master password** and **Confirm master password**: at least 12 characters, without your login name, typed twice. These are the built-in [password rules](security#password-rules); an administrator can change them later.
4. Click **Create household**.

You are the household's administrator: you can do everything, including adding other users later. [Finding your way](basics#create-screen) describes every field of this screen.

## Step 3: Save your recovery key {#recovery-key}

@index: recovery key; forgotten password

The next screen, **Your recovery key**, shows a long key made of letters and digits in groups of four.

> Important: no server can reset your password. If you forget it, this key is the only way back into your household. Nobody, not even RANN, can recover your data without it.

1. Click **Print** to print it, or write it down. Keep the paper somewhere safe, away from this computer.
2. Click **I have saved my recovery key**. The household opens on the Dashboard.

## Step 4: Use the Getting started guide {#guide}

@index: Getting started guide; setup steps

The Dashboard shows a **Getting started** card with six steps: the people, the accounts, the bills and pay, a first receipt, a first statement reconciled and the phone. Each step has a button that opens the right screen, and the next step to do is in bold. The card counts what is done and disappears once the first five steps are done. **Hide this guide** removes it for you; **Show the Getting started guide again**, under Display and accessibility, brings it back.

The steps below follow the same order.

## Step 5: Add the people {#people}

@index: household members; people

1. Click **Add people** in the guide. The **Household members** screen opens.
2. Click **Add**, then enter the person's **Name**, their **Relationship** (Adult, Child or Other dependant) and, if you like, their **Date of birth** as YYYY-MM-DD.
3. Click **Save**. Repeat for each person, yourself included.

People let accounts, expenses, health records and taxes belong to someone. See [Household members](members).

## Step 6: Add your accounts {#accounts}

@index: add an account; opening balance

1. Click **Add an account** in the guide. The **Add account** window opens.
2. Enter the **Account name** (such as Joint chequing), choose the **Type** (Chequing, Savings, Credit card, Mortgage, RRSP, TFSA and so on) and leave **Currency (e.g. CAD, USD, BTC)** on CAD unless the account is in another currency.
3. Enter the **Opening balance** and the **Opening date**: the balance on that date. The simplest is today's balance with today's date. For a credit card, enter what you owe.
4. Tick the **Owners** of the account, and click **Save**.

Add your main accounts the same way from the **Accounts** screen with **Add account**. The type and currency cannot be changed later, so check them before saving. See [Accounts](accounts).

## Step 7: Add bills and pay {#bills}

@index: add a bill; pay day; subscriptions

1. Click **Add bills** in the guide. The **Bills** screen opens; click **Add a bill**.
2. Choose the **Type**: Bill for money you pay, Income for your pay, Transfer for regular moves between your accounts.
3. Enter the **Name** (Hydro, Rent, Pay), the account it is paid from or deposited to, the **Amount**, and whether the **Amount is** Fixed, Variable or Estimated.
4. Choose how it **Repeats** (Monthly, Every two weeks and so on) and the **First due date**.
5. In **Remind me (days before)**, enter for example 7, 1 to be reminded a week and a day ahead.
6. Click **Save**.

Reminders then appear across the top of the app and as computer notifications. See [Bills](bills).

## Step 8: Import a first statement {#statement}

@index: import a statement; OFX; QFX; CSV

1. Click **Open an account** in the guide, or go to **Accounts**, and click the account.
2. Click **Import statement…** and choose the file downloaded from your bank.
3. For a CSV file, a window asks which column holds what (date, amount, description). Check the preview and click **Import**. The app remembers the layout for the next time.
4. The transactions come in, categorized where the app can guess, and the reconciliation screen opens. A summary says how many were added, matched or need a decision.

You can stop here and reconcile later. See [Getting started with money](start-money) and [Accounts](accounts).

## Step 9: Set up backups {#backups}

@index: backups; backup folder

From the first day, the app is set to make a backup every day into a folder beside the household, named after it with " - backups" at the end. That is on the same disk, so it does not protect you if the disk fails. Move it:

1. In the menu, open **Settings** and click **Backups**.
2. Click **Choose folder…** and pick a folder on an external drive, a network drive or a cloud folder.
3. Leave **Automatic backups** on Every day and **Versions to keep** as it is, and click **Save**.
4. Click **Back up now** to make the first one.

Backups stay encrypted. See [Backups](backups).

## Step 10: Pair your phone (optional) {#phone}

@index: pair a phone; QR code

1. Make sure the phone is on the same Wi-Fi as the computer.
2. Click **Pair a phone** in the guide, or open **Settings**, then **Phones**, and click **Pair a phone**. A QR code appears.
3. On the phone, open RANN's Roost Mobile, tap "Pair with a computer" and scan the code within 10 minutes.
4. If Windows asks whether to allow the app on networks, allow it on private networks.

From then on, receipts and bills you photograph arrive on the **To review** tab of **Documents** whenever the household is open. See [Getting started with the phone](start-phone).

## Where to go next {#next}

@index: next steps

Your household is working. When you are ready for more, read the getting-started chapter of each area:

- [Settings](start-settings): categories, payees, rules, users and display.
- [Contacts](start-contacts): gather and sort the people and organizations you deal with.
- [Money](start-money): the register, reconciliation and transfers.
- [Bills and budgets](start-bills-budgets): bills, budgets and savings goals.
- [Documents](start-documents): receipts, bills and the vault.
- [Calendar](start-calendar): appointments and reminders.
- [Investing](start-investing): investments, registered plans and loans.
- [Reports](start-reports): net worth, spending and your own reports.
- [Taxes](start-taxes): slips, donations and the year-end package.
- [Home and family](start-home-family): health, pets, vehicles, the home and family money.
- [The phone](start-phone): everything RANN's Roost Mobile can send.

To find your way around the main window, the menu and the search box, see [Finding your way](basics).
