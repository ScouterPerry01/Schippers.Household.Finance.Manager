# Finding your way

This chapter describes what you see before a household is open (the start screens), then the main window: the top bar, the menu, the search, locking, messages, the controls used in every form, and the reminders and notifications. The other chapters assume you know what is here.

## The start screens {#start-screens}

@index: start screen; sign in; open a household

When RANN's Roost starts, and whenever a household is locked, it shows the start screens. The top bar already holds the **English** and **Français** buttons and **Help (F1)**, so you can change the language and read the help before signing in.

### Welcome {#welcome-screen}

@index: Welcome screen; recent households

The Welcome screen is the first one you see. It shows the app's name and these choices:

- **Create a new household**: opens the [Create a household](basics#create-screen) screen, to start a new set of books.
- **Open an existing household**: opens a folder chooser. Choose the household's folder itself (its name ends with .hfm, such as Tremblay Family.hfm). The [Unlock household](basics#unlock-screen) screen then opens for it.
- **Restore from a backup…**: brings back a household from a backup file (.hfmbak), for example after a broken disk or to move to a new computer. See [Restoring from a backup](basics#restore).
- **About and privacy**: shows the [About](about) page (version, updates, privacy, licence, support) before any household is open. **Back** returns to Welcome.
- **Recent households**: the last five households opened on this computer, most recent first. Click one to go straight to its Unlock screen. A household whose folder was moved, renamed or deleted no longer appears; use **Open an existing household** to find it again.

### Restoring from a backup {#restore}

@index: restore; backup file; hfmbak; new computer

1. On Welcome, click **Restore from a backup…**.
2. Choose the backup file. The chooser shows Household backups (.hfmbak).
3. Choose where to put the restored household: pick a folder, such as Documents.
4. The app makes a new household folder there, named after the backup (with (2), (3) and so on added if the name is taken), and the button reads **Restoring…** meanwhile. A backup is never restored over an existing household.
5. The Unlock screen opens for the restored household. Sign in with the login name and password that were in use when the backup was made, or reset the password with your recovery key.

If the backup cannot be restored, a message under the button says why. See [Backups](backups) for making backups and [Privacy, data and security](privacy-data#new-computer) for moving to a new computer.

### Create a household {#create-screen}

@index: create a household; new household; household folder; administrator

This screen makes a new, empty household. The province, your name and your password can be changed later; the folder, the household name and the login name cannot.

- **Choose folder…**: required. Opens a folder chooser for the Location: the folder where the household will be kept, such as Documents. The chosen path is shown beside the button. The app makes a new folder inside it named after the household with .hfm at the end (Tremblay Family.hfm). That folder must not already exist with files in it; if it does, choose another location or another name.
- **Household name**: required. The household's name, used for the folder's name and shown to the phones you pair. Avoid characters your system does not allow in folder names, such as / or :.
- **Province or territory**: where the household lives. Its rules apply: bank holidays used to move bill dates, default categories, provincial RESP grants, the rules of locked-in plans and the provincial tax forms. Required: nothing is chosen at first, so pick yours. It can be changed later under [Household members](members), and a person who lives elsewhere can have their own.
- **Your name**: required. Your name as shown in the app, for example in the activity log and the user list.
- **Login name**: required. The name you type to sign in. Keep it short and without spaces. It cannot be changed later.
- **Master password**: required. The built-in [password rules](security#password-rules) apply, since the household does not exist yet: at least 12 characters, without the login name; the line under the field says so. An administrator can change the rules later for the whole household. A passphrase of a few words is easy to remember and hard to guess. It protects the household's encryption keys: it is never stored, and no one can reset it for you.
- **Confirm master password**: the same password again, to catch typing mistakes.
- **Back**: returns to Welcome without creating anything.
- **Create household**: available once the folder, the household name, the province or territory, your name and the login name are filled in. If the password breaks the rules the screen says what it lacks, such as "The password needs at least 12 characters."; if the two passwords differ it says "The passwords do not match." Otherwise the app creates the household, which takes a few seconds (a circle turns while it works).

The new household uses the Canadian dollar as its base currency. It starts with one shared account group and you as its only user, an Administrator. The household is created in the language the app is using.

### Your recovery key {#recovery-key-screen}

@index: recovery key; print the key; forgotten password

Right after a household is created, this screen shows your recovery key: 54 letters and digits in groups of four separated by dashes.

There is no server that can reset your password. If you forget it, this key is the only way back into your household. Print it or write it down and keep it somewhere safe, away from this computer.

- **Print**: opens the system's print dialog and prints a page with the key, the household's name and why to keep it. The page goes straight to the printer: the key is not saved in a file on this computer.
- **Copy**: copies the key to the clipboard, so you can paste it into a password manager. Clear the clipboard afterwards if others use this computer.
- **I have saved my recovery key**: opens the household. The key is not shown again.

> Important: each user has their own recovery key. When an administrator adds a user under [Users](users), that user's key is shown once in the same way, to be given to them.

If the household locks while this screen is shown (see [Locking](basics#locking)), the key is gone; the household is still fine and opens with your password.

### Unlock household {#unlock-screen}

@index: unlock; sign in; log in

This screen opens a household. The household's folder is shown under the title.

- **Login name**: your login name for this household. Capitals do not matter.
- **Password**: your password. Capitals do matter.
- **Back**: returns to Welcome.
- **Unlock**: available once both are filled in. Opening can take a moment, on purpose: the password is checked in a way that makes guessing slow, and a circle turns meanwhile. If the login name or password is wrong, the screen says "Wrong login name or password." without saying which one.
- **Forgot your password?**: opens [Reset password with recovery key](basics#reset-screen).

Once unlocked, the household opens on the Dashboard. A user who was made unable to sign in (under Users, **Can sign in** unticked) cannot unlock it.

### Reset password with recovery key {#reset-screen}

@index: reset password; forgotten password; recovery key

Use this screen when a password is forgotten. It sets a new password using that user's recovery key.

- **Login name**: the login name of the user whose password is forgotten.
- **Recovery key**: that user's recovery key. Type it with or without the dashes and spaces, in capitals or not; the letters I and L are read as the digit 1 and the letter O as 0, so look-alikes do no harm. The last two characters are a check: a typing mistake is caught.
- **New password**: the new password. It must follow the household's [password rules](security#password-rules) (at least 12 characters by default); they are checked once the key has opened the household, and a password that breaks them is refused with what it lacks.
- **Confirm master password**: the new password again.
- **Back**: returns to the Unlock screen.
- **Reset password**: available once the login name and key are filled in. If the key is mistyped or does not belong to that user, the screen says "That recovery key is not valid." Otherwise the password is changed and the household opens.

The recovery key keeps working after the reset.

## The update question {#update-question}

@index: updates; Check for updates?; update check

On Linux copies installed from a .deb or .rpm package or an AppImage, the very first start asks **Check for updates?**. RANN's Roost can check GitHub once a day for a new version and tell you when one is ready. Only the check itself goes out: GitHub sees your computer's internet address and that the app is in use, as for any web page. Nothing about your household is sent.

- **Check once a day**: turns the daily check on. The first check runs right away.
- **Don't check**: no checks are made.

The question is asked once per computer; change the answer later with **Check for updates once a day** under [About](about). Copies from the Microsoft Store or Flathub never ask, because those stores update them, and a copy built from the source code has no update check.

## The main window {#main-window}

@index: main window; layout

Once a household is open, the window has, from top to bottom: the top bar, any banners, and below them the menu (on the left, or across the top) beside the screen you chose.

### The top bar {#top-bar}

@index: top bar; language; Lock button

The top bar is always visible. From left to right:

- The app's name, RANN's Roost.
- **English** and **Français**: switch the whole app to that language at once; the button of the language in use is greyed. The choice is remembered on this computer and is used the next time the app starts. Before you ever choose, the app follows your computer's language. Lines the app writes itself in the books from then on (such as automatic memos) are written in the language in use. The manual and help follow the same choice.
- **Search (Ctrl+F)**: the search box, shown only while a household is open. See [Searching](basics#search).
- **Help (F1)**: opens the short Help panel on the topic for the screen shown. See [Help (F1) and the Manual](welcome#help-and-manual).
- **Manual**: opens this manual in its own window. Shift+F1 opens it on the chapter for the screen shown. See [Using the Manual window](welcome#manual-window).
- **Lock**: shown only while a household is open. Locks the household at once. See [Locking](basics#locking).

### Banners {#banners}

@index: banner; update banner; reminder banner

Two kinds of coloured banner can appear under the top bar:

- The update banner, "Version x of RANN's Roost is available. See About.", on copies that check for updates. Click it to go to [About](about), where you can download the update. It stays until you visit About.
- The reminder banner, such as "3 reminders", followed by the first three reminders. It appears on every screen except the one the reminders belong to. Click it to go to the screen of the first reminder. See [Reminders and notifications](basics#reminders).

### The menu {#menu}

@index: menu; navigation; side menu; menu at the top

The menu leads to every screen. The **Dashboard** and **Contacts** stand on their own at the top, outside the groups; the other screens are in five groups: Money, Investing and borrowing, Reports and taxes, Home and family, and Settings.

The menu can be shown in two ways, and each user's choice is remembered on this computer:

- As a list on the left (the starting layout). Click a group's name to fold it closed (▸) or open (▾). The group of the screen you are on is always open. Settings starts closed. The groups you leave closed are remembered for you on this computer. The list grows wider when the text size is larger, so names stay on one line.
- As a bar across the top. Each group is a button that opens a drop-down list of its screens. The group and screen in use are in bold.

- **Menu at the top**: at the bottom of the list on the left; moves the menu to a bar across the top.
- **Menu on the left**: at the end of the bar at the top; moves the menu back to the left.

Counts in parentheses show what waits for you. Documents shows the number of documents waiting on its To review tab, for example Documents (3). When a group is folded closed, or in the bar at the top, the group's name shows the total of its screens' counts, such as Money (3).

### Menu groups and screens {#menu-groups}

@index: screens; sections of the app

- Dashboard: an overview of balances, what needs attention and the [Getting started guide](quick-start#guide). See [Dashboard](dashboard).
- Contacts: the household's banks, advisors, insurers, doctors, pharmacies and everyone else you deal with, each with what it is for, and linked to the records it concerns. See [Contacts](contacts).
- Money: [Accounts](accounts), [Documents](documents), [Bills](bills), [Budgets](budgets), [Savings goals](goals), [Family money](family), [Side income](side) and [Calendar](calendar).
- Investing and borrowing: [Investments](investments), [Registered plans](plans) and [Loans and mortgages](loans).
- Reports and taxes: [Reports](reports) and [Taxes](taxes).
- Home and family: [Health](health), [Medical claims](medical), [Emergency and estate](estate), [Pets](pets), [Vehicles](vehicles), [Trip log](trips) and [Home and assets](assets).
- Settings: [Household members](members), [Users](users), [Categories](categories), [Payees](payees), [Category rules](rules), [Institutions](institutions), [Rates and prices](rates), [Phones](phones), [AI reading](ai), [Backups](backups), [Security](security), [Display and accessibility](display) and [About](about).

What each user sees inside these screens depends on their role and on the account groups they may open; see [Users](users).

## Searching {#search}

@index: search; find; Ctrl+F; global search

One search box looks through the whole household.

### The search box {#search-box}

- **Search (Ctrl+F)**: in the top bar. Click it, or press Ctrl+F from anywhere in the household, type at least two characters and press Enter. Shorter searches are ignored.

The search looks through everything you are allowed to see, including closed accounts and archived payees and categories. Capitals and accents do not matter: epicerie finds Épicerie.

- Transactions whose payee, memo or other text contains what you typed. If you type an amount, such as 45.99, transactions of that amount are found too, whether money went out or came in.
- Accounts by name or notes; payees by name; categories by their English or French name; bills by name or payee; institutions by name.
- Documents by the text read from them, their title, store or biller, notes and file name.
- Contacts, archived ones included, by name, What for, job title, organization, address, website, hours, notes, phone numbers and emails; three or more digits also find a phone number however it is written. Account and client numbers are never searched.

### The results window {#search-results}

@index: search results

The results open in a window titled Search: followed by what you typed. They are grouped by kind, each group with the number found:

- Transactions: the date, the account, the payee, the memo and the amount, newest first, up to 200. Click one to open its account's register, scrolled to that transaction and with it opened in the entry form, ready to change.
- Accounts: click one to open that account.
- Payees, Categories, Bills and Institutions: click one to open that screen.
- Documents: click one to open the Documents screen on that document.
- Contacts: click one to open the Contacts screen on that contact.

When nothing matches, the window says "Nothing found.". **Close** closes the window without going anywhere.

## Locking {#locking}

@index: lock; auto-lock; inactivity; sign out

A locked household is closed: its keys are wiped from memory and nothing can be read until someone signs in again. Locking returns to the [Unlock household](basics#unlock-screen) screen for the same household.

- **Lock**: in the top bar; locks at once. Use it when you step away.
- Closing the window also locks the household before the app quits.
- Auto-lock: after a set time without keyboard or mouse activity, the household locks by itself. It is 10 minutes to start with; choose 1, 5, 10, 15, 30 or 60 minutes, or Never, under [Security](security). This setting applies to this computer. The check runs every 15 seconds, so the lock can come a few seconds after the time chosen.

While the household is locked, phones cannot send to this computer, reminders and notifications stop, and automatic backups, scheduled reports and price downloads wait until it is open again. Anything not saved in an open form is lost, so save before you step away.

## Messages and errors {#messages}

@index: error message; Cannot save; warning

The app tells you about problems in a few ways:

- Under a field, in red: the value cannot be used as typed, such as a date that is not YYYY-MM-DD or an unknown currency code. The field's outline turns red too. Fix the value; the Save button often stays greyed until it is valid.
- Under a form or button, in red: the action was refused, such as "Wrong login name or password." or "Something went wrong:" followed by details.
- The Cannot save window: shown when a change is refused, with the reason, such as "You do not have permission to do this.", "A name is required." or "Enter the date as YYYY-MM-DD.". Click **OK**, correct what it says and try again. Nothing was changed.
- **Change a reconciled transaction?**: shown when you change a transaction that is part of a completed reconciliation. Changing it will make the account no longer agree with that statement, and the change is recorded in the history. **Change it** goes ahead; **Cancel** leaves it as it was.
- Confirmation windows before deleting: they name what will be deleted and what is kept. Deleting cannot be undone.

The [Troubleshooting](troubleshooting) chapter explains the most common messages and what to do.

## Common controls {#controls}

@index: controls; forms; fields

The same few controls are used on every screen.

### Text fields {#text-fields}

Type in the box; the label above it says what goes there. Fields marked "optional" in their label can be left empty. Password fields show dots instead of the characters. Notes fields can hold several lines.

### Date fields {#date-fields}

@index: date format; YYYY-MM-DD; ISO date

Dates are typed as year-month-day with dashes, such as 2026-03-05 for March 5, 2026: the Canadian standard format, the same in English and French. The outline turns red while the date is not valid.

- Type + at the end of a valid date to move it one day later, or - to move it one day earlier. Repeat to keep moving.
- Many date fields are filled with today's date to start with.

### Amount fields and the calculator {#amount-fields}

@index: amount; calculator; decimal comma; negative amount

Amounts are typed the way your language writes them: 1,234.56 in English, 1 234,56 in French (a single dot is accepted as the decimal point in French too, since many keypads have only a dot). A dollar sign, the currency code and spaces are ignored. A minus sign or parentheses, as in (45.00), make the amount negative.

Every amount field is also a calculator: type 12.50 + 3.25, 3 * 4.99 or (100 - 20) / 4 and the result shows under the field, such as = $15.75. The signs × and ÷ work too. Only the final result is rounded to the cent. A question mark under the field means it cannot be read as an amount.

### Drop-down pickers {#pickers}

@index: drop-down; picker; list

A picker shows the current choice with an arrow. Click it to open its list, then click a choice. You can also type in it: the list keeps only the choices that contain what you typed, which is quickest in long lists such as categories or accounts. Some pickers have a first choice such as (none) or Everyone, used when nothing in particular is chosen. Long lists show their first 200 matches; type more letters to narrow them.

### Suggestion fields {#suggestion-fields}

@index: payee field; suggestions; autocomplete

A suggestion field, such as Payee in a register, accepts any text and offers matching names you already use as you type. Click a suggestion to take it, or keep typing a new name: a payee typed that does not exist yet is created when you save.

### Checkboxes {#checkboxes}

A ticked box turns an option on. The text beside it says what the option does.

### Forms in a window: Save and Cancel {#form-dialogs}

@index: Save; Cancel; dialog; form window

Many forms open in a window over the screen, such as **Add account** or **Add a bill**.

- **Save**: saves and closes the window. It is greyed while something required is missing or invalid. If the change is refused, a Cannot save window says why and your entries stay in the form.
- **Cancel**: closes the window without saving anything. Escape does the same.

Other screens show their form beside a list, with its own **Save** button; selecting another item in the list without saving drops what was typed.

### File and folder choosers {#file-choosers}

@index: file chooser; choose folder

Buttons that end with … (such as **Import statement…** or **Choose folder…**) open your system's chooser. The kind of file expected is shown in its file type list, such as Bank statements (OFX, QFX, QBO, CSV). Cancel in the chooser to change nothing.

## Reminders and notifications {#reminders}

@index: reminders; notifications; tray; alerts

The app reminds you of what is coming due, as long as a household is open.

### What is reminded {#what-is-reminded}

- Bills, pay and transfers, on the days chosen in **Remind me (days before)**, on the due date and when overdue; for a subscription, the date to cancel by to avoid a renewal. See [Bills](bills).
- Calendar events and appointments, at the times chosen for them. See [Calendar](calendar).
- Medication refills, and prescriptions with no refills left. See [Health](health).
- Renewals: pet licences and pet insurance, vehicle registration and insurance, warranties, loan and mortgage terms that end, card annual fees, medical claims to send, insurance policies and tax instalments.
- Maintenance due on vehicles, the home and other assets, by date or by kilometres or hours.
- Registered plan warnings, such as over-contributions, RRIF and LIF minimum withdrawals, and RRSPs to convert by the end of the year you turn 71. See [Registered plans](plans).

### The reminder banner {#reminder-banner}

The coloured banner under the top bar counts the reminders and shows the first three. It appears on every screen except the one the reminders belong to (bill reminders are not repeated on the Bills screen, for example). Click it to open the screen of the first reminder. The banner follows the language in use.

### Computer notifications and the tray icon {#tray}

@index: system notification; tray icon; notification area

While the app runs, its icon sits in the notification area of the taskbar (the system tray); hovering over it shows RANN's Roost. While a household is open, the app looks for new reminders every five minutes and shows a computer notification with how many there are and up to four of them. Each reminder is announced once while the household stays open; the banner keeps showing it until it is dealt with.

> Note: notifications need the household open. If the app is closed or locked, nothing is announced; the reminders appear when you next open it.

### The Dashboard {#dashboard-attention}

The Dashboard's Needs your attention list gathers other things to look at: overdue bills, statement lines that need a decision, transactions without a category, accounts not reconciled in more than 45 days (a figure you can change in [Rates and rules](rates-rules)) and no successful backup in the last 7 days. See [Dashboard](dashboard).

## Work done while the household is open {#background}

@index: background tasks; automatic

Some work happens by itself, only while a household is open:

- Missing Bank of Canada exchange rates are downloaded when the household uses another currency, and the price downloads you turned on run. See [Rates and prices](rates).
- Planned set-asides for savings goals are entered on their dates. See [Savings goals](goals).
- Paired phones can send their captures over the home Wi-Fi, and the transfer folder is checked for captures sent from away. See [Phones](phones).
- Files saved in the watched folder of Documents are imported. See [Documents](documents).
- Scheduled reports are made when their period has ended. See [Reports](reports).
- Automatic backups run on their schedule. See [Backups](backups).
- On copies that check for updates, the update check runs once a day if you agreed.

When the app is offline, these simply wait; everything else works without the internet.

## Settings kept on this computer {#computer-settings}

@index: per-computer settings; preferences

Most choices are saved in the household and follow it to any computer. A few belong to the computer instead, so each computer can differ:

- the language (English or Français);
- the colours and text size, under [Display and accessibility](display);
- the auto-lock time, under [Security](security);
- the menu on the left or at the top, and which menu groups are left closed, for each user;
- the list of recent households;
- the answer to the update question;
- the key for AI reading, kept in the computer's own secure store, under [AI reading](ai).
