# Troubleshooting

This chapter lists the problems people meet most often, with the message the app shows, what it means and what to do. Look for the words of the message you see; the Search tab of the manual finds them too.

## Signing in {#signing-in}

@index: sign-in problems; cannot open household

### Wrong login name or password {#wrong-password}

@index: Wrong login name or password; cannot log in

The Unlock screen says "Wrong login name or password." It does not say which one is wrong, so that nobody can find out which login names exist.

- Check the login name: it is the short name chosen when your user was made, not your full name. Capitals do not matter in the login name.
- Capitals do matter in the password: check that Caps Lock is off, and that the keyboard is in the language you expect.
- Make sure you are opening the right household: its folder is shown under the title.
- If your user was made unable to sign in (under Users, **Can sign in** unticked), ask an administrator to tick it again.
- If you have forgotten the password, see the next section.

### A forgotten password {#forgot-password}

@index: forgotten password; lost password; reset

1. On the Unlock screen, click **Forgot your password?**.
2. Enter your **Login name**, your **Recovery key** and a **New password** twice.
3. Click **Reset password**. The household opens with the new password.

An administrator cannot reset another user's password for them: each user needs their own recovery key. See [Reset password with recovery key](basics#reset-screen).

### That recovery key is not valid {#invalid-key}

@index: That recovery key is not valid; recovery key refused

- The key must be the one of the user named in **Login name**: each user has their own.
- Check for typing mistakes. Dashes, spaces and capitals do not matter, and I, L and O are read as 1, 1 and 0, but every other character does. The last two characters catch most typing mistakes.
- A key from another household does not work, even for the same person.

If neither the password nor the recovery key can be found, the household cannot be opened by anyone, nor its backups. Start a new household; see [Privacy, data and security](privacy-data#forgotten-password).

### The household locked itself {#locked-itself}

@index: auto-lock; locked by itself; signed out

The household locks after a time without keyboard or mouse activity (10 minutes to start with). Sign in again; anything saved is still there, but a form left open and not saved is lost. To lock later, or never, change **Lock after inactivity** under [Security](security). The setting applies to this computer only.

### The household is not in Recent households {#not-in-recent}

@index: cannot find household; missing household

The list shows the last five households opened on this computer whose folder still exists. If the folder was moved or renamed, click **Open an existing household** and choose the folder itself, whose name ends in .hfm. If it was deleted, restore it from a backup with **Restore from a backup…**; see [Restoring from a backup](basics#restore).

### Creating a household fails {#create-fails}

@index: Something went wrong; folder is not empty

If **Create household** shows "Something went wrong:" followed by a message that the folder is not empty, a folder with the household's name already exists in the chosen location and holds files. Choose another location or another household name. The button stays greyed until the folder, household name, your name and login name are all filled in.

## Saving and permissions {#saving}

@index: cannot save; save problems

### Cannot save {#cannot-save}

@index: Cannot save; error window

The Cannot save window gives the reason in a sentence, such as "A name is required." or "Enter the date as YYYY-MM-DD.". Click **OK**, correct what it says and save again. Nothing was changed. The reference chapter of the screen explains each field's rules.

### You do not have permission to do this {#permission}

@index: You do not have permission to do this; access denied; read only

Your role, or the access you were given to that account group, does not allow this change. Viewers can only read; members can change their own private accounts and the shared groups they were given Edit access to; Capture only allows adding receipts and transactions. Viewers also cannot change the household's lists (categories, payees, rules, institutions, rates), and only administrators change household members, backup settings and the price downloads. Ask an administrator to change your access under [Users](users).

### Dates and numbers are refused {#dates-numbers}

@index: Enter the date as YYYY-MM-DD; One of the numbers is not valid; invalid date

- Dates are typed as YYYY-MM-DD, such as 2026-03-05, in both languages. The field turns red until the date is valid.
- Amounts use the separators of your language (1,234.56 in English, 1 234,56 in French). A question mark under an amount field means it cannot be read. Remove letters and stray characters; a $ sign or the currency code is fine.
- Times are typed as HH:MM, such as 09:30.

See [Common controls](basics#controls).

### Change a reconciled transaction? {#reconciled}

@index: reconciled transaction; locked statement

This question appears when you change a transaction that is part of a completed reconciliation. If you go ahead with **Change it**, the account no longer agrees with that statement, and the change is recorded in the history. If the reconciliation itself was wrong, undo it instead: under the account's **Statements**, **Undo reconciliation** reopens the most recent one (give a reason; only the most recent can be undone). See [Accounts](accounts).

## Importing statements {#importing}

@index: import problems; statement import; OFX; CSV

### This file type cannot be imported {#import-format}

@index: This file type cannot be imported

Statements are imported from OFX, QFX, QBO or CSV files. Download the statement again from your bank's website, choosing one of those formats (often called Quicken, Money, Microsoft Money, QuickBooks, OFX or "spreadsheet (CSV)"). A PDF statement cannot be imported this way. Keep it in [Documents](documents), where [AI reading](ai), if you use it, can read it and offer **Reconcile with this statement**, or enter a paper statement by hand from the account's **Statements**. Quicken's own export (QIF) is brought in with **Import from Quicken…**; see [Accounts](accounts).

### The file could not be read {#import-failed}

@index: The file could not be read

The file has the right extension but its content is damaged or not what it claims. Download it again; open a CSV in a text editor to check that it holds transactions. If the bank offers several formats, try another.

### The statement is in another currency {#import-currency}

@index: The statement is in; currency mismatch

"The statement is in USD, but the account is in CAD." means the file belongs to an account in another currency. Import it into that account instead, or create an account in the statement's currency. An account's currency cannot be changed.

### Already imported {#already-imported}

@index: This statement file has already been imported into this account; duplicates

"This statement file has already been imported into this account." The very same file was imported before; nothing is added twice. When a new file overlaps an earlier one by a few days, the lines already imported are marked Already imported and are not added again.

### The statement contains no transactions {#import-empty}

@index: The statement contains no transactions

The file holds no transactions for the dates chosen on the bank's site. Download it again for a period that has activity.

### A file with several accounts {#several-accounts}

@index: This file holds several accounts

Some banks put all your accounts in one file. The app asks "This file holds several accounts. Which one belongs to this account?" and lists each with its account number, currency and number of transactions. Click the right one. To import the others, open each account and import the same file again, choosing its line.

### CSV columns are wrong {#csv}

@index: CSV columns; date format; decimal comma; signs reversed

In the CSV window, check each choice against the preview:

- **The first row contains column names**: tick it when the first row is headings, so it is not read as a transaction.
- **Date column** and **Date format**: dates read wrongly (March 4 instead of April 3) mean the wrong format is chosen.
- **Amounts**: choose One column, negative for money out, or Separate withdrawal and deposit columns, as the file has them.
- **Amounts use a decimal comma (1 234,56)**: tick it for files from French-language banking sites.
- **Purchases are shown as positive numbers (reverse the signs)**: tick it when purchases come in as deposits, as many credit card files do.

The layout is remembered for the institution, or for the account when it has none, so the next import needs no changes.

### Lines do not match {#not-matching}

@index: matching; To confirm; No match; Same transaction; Add as new

After an import, each statement line has a status: Matched, Added, To confirm, Already imported, No match or Ignored. The lines under Needs your attention want a decision:

- To confirm: the app found a recorded transaction that is probably the same. Click **Same transaction** if it is, or **Add as new** if it is not.
- No match: click **Add as new**, or **Link to a recorded transaction** to pair it with one entered by hand. A line can only be linked to a transaction of the same amount, and a transaction can be linked to only one line.
- **Ignore**: for a line that should not be in the books.
- **Undo match**: undoes a wrong match.

When a transaction entered by hand differs slightly (a tip, a currency conversion), correct its amount first, then link it. Category rules and payee aliases help future imports; see [Category rules](rules) and [Payees](payees).

### The difference is not zero {#difference}

@index: The difference must be zero to finish; reconciliation difference; Some statement lines still need a decision

To finish a reconciliation, enter **Closing balance on the statement** and make the difference zero.

- "Some statement lines still need a decision.": decide each line under Needs your attention first.
- A difference left: look for a transaction recorded twice, one missing or an amount typed wrong. Under Recorded but not on this statement, tick only the transactions that appear on the statement.
- Check the account's opening balance and date: a wrong opening balance shows as a difference on the first reconciliation.

## Documents and reading {#documents}

@index: document problems; receipts; scan

### A file is not accepted {#file-not-accepted}

@index: Could not be read; The file is empty or larger than 50 MB; This kind of file cannot be kept in the vault

- "Could not be read: …. Use a PDF, JPEG, PNG or HEIC file.": the vault accepts PDF files, photos (JPEG, PNG, HEIC) and saved emails. Save or print other files as PDF first.
- "The file is empty or larger than 50 MB.": scan at a lower resolution, or split a long PDF.
- "1 was already in the vault.": the very same file was imported before; it is not kept twice.

### The text was read badly {#ocr}

@index: text recognition; OCR; Check this: it was hard to read; blurry receipt

Documents are read on this computer. Fields the app was not sure of are marked "Check this: it was hard to read"; check them against the picture and correct them before saving.

- For a better reading, photograph the receipt flat, in good light, filling the frame, without shadows; scan at 300 dpi.
- Faded thermal receipts read poorly; photograph them soon after the purchase.
- For hard documents, you can have them read by AI with your own key; see [AI reading](ai).

### HEIC photos {#heic}

@index: HEIC; iPhone photos; HEIF; no HEIC decoder

HEIC is the photo format of iPhones and some Android phones. The app reads it with a decoder installed on the computer.

- "Kept in the vault but not read, since this computer has no HEIC decoder": the photo is safe in the vault, but it could not be read or shown.
- On Windows, install HEIF Image Extensions and HEVC Video Extensions from the Microsoft Store, then open the photo again.
- On Linux, install your distribution's HEIC support (libheif with its HEVC plugin, such as libheif-plugin-libde265), then restart the app.
- Or set the phone or camera to save photos as JPEG.

### Possible duplicate {#duplicate}

@index: Possible duplicate; duplicate document

"Possible duplicate: another document has the same store, date and amount." The same receipt may have come twice, for example from the phone and from a scan. Open both; delete one if they are the same, or keep both if they are really two purchases.

### AI reading problems {#ai}

@index: AI reading errors; Anthropic key; The key was refused

- "To read with AI, add your key under AI reading.": no key is saved on this computer yet; see [AI reading](ai).
- "The key was refused. Check it under AI reading.": the key was mistyped, deleted or disabled at console.anthropic.com. Use **Check key (free)** after saving it.
- "Anthropic refused for now: too many requests, or no credit left on your account.": wait a little, or add credit to your Anthropic account.
- "Anthropic could not be reached. Check the internet connection.": the computer is offline or a firewall blocks the connection.
- "Claude declined to read this document." or "The answer could not be checked, even after asking again; enter the fields by hand.": enter the fields yourself; nothing is lost.
- "This computer has no keyring running, so the key is kept only until you close the app.": on Linux without a keyring, enter the key again after each start, or start your desktop's keyring.

## The phone {#phone}

@index: phone problems; pairing; RANN's Roost Mobile; Wi-Fi

### The pairing code expired {#code-expired}

@index: This code has expired; pairing code

A pairing code works once, for 10 minutes; the window counts down. "This code has expired. Close and pair again.": close the window and click **Pair a phone** again for a new code.

### Phones cannot reach this computer {#cannot-reach}

@index: Phones cannot reach this computer; firewall; not connected to a local network

The Phones screen says whether phones can reach the computer, and at which address.

- "This computer is not connected to a local network.": connect the computer to your home Wi-Fi or network.
- "Phones cannot reach this computer:" followed by a reason: the app could not start listening for phones. Lock the household and open it again; if the message stays, restart the computer.
- Check that the phone is on the same Wi-Fi as the computer, not on mobile data or a guest network.
- On Windows, when asked whether to allow the app on networks, allow it on private networks. If you refused, allow RANN's Roost in the Windows firewall settings, and make sure your home network is set as private.
- If the computer's address changed (a new router, for example), pair the phone again.

### Nothing arrives from the phone {#nothing-arrives}

@index: captures not arriving; To review

- The household must be open on the computer: phones cannot send to a locked household.
- A phone sends only to the user who paired it. If another user is signed in, the phone keeps its captures and says why; they arrive when its owner opens the household.
- Captures never go straight into the books: look on the To review tab of [Documents](documents). The Documents entry of the menu shows how many wait.
- On the Phones screen, each phone shows when it last sent and how many items were received.

### Transfer files and the transfer folder {#transfer-files}

@index: transfer folder; roostsync; away from home

- "The transfer folder could not be read:": the folder was moved or the cloud service's app is not running. Choose it again under [Phones](phones).
- "… is for another household.": the file belongs to another household; open that household.
- "… is from a phone that is not paired with this household.": pair the phone first.
- "… is from …'s phone; … must open the household to import it.": only the phone's owner can import it; it waits for them.
- "… is not a RANN's Roost transfer file.": the file is damaged or of another kind.

### A lost or replaced phone {#lost-phone}

@index: lost phone; stolen phone; Remove phone

On [Phones](phones), click **Remove** beside the phone: it can no longer send or receive anything. A phone never holds the household's keys, so nothing more is needed. **Forget** then deletes a removed phone from the list. Pair the new phone as usual.

## Backups {#backups}

@index: backup problems; backup failed

- "No successful backup in the last 7 days" on the Dashboard: open [Backups](backups), read Last problem, then click **Back up now**.
- "Choose a backup folder first.": choose a folder with **Choose folder…**.
- The backup folder is on a drive that is not connected: plug it in, or choose another folder. A cloud folder needs its service's app running.
- "The backup check failed:": the file in the folder is damaged. Make a new backup at once, and check the drive.
- "Keep between 1 and 365 versions.": enter a number in that range in **Versions to keep**.
- Restoring: a backup opens with the passwords and recovery keys that were in use when it was made.

## Updates {#updates}

@index: update problems; update failed

These messages appear under Updates in [About](about), on copies that check for updates.

- "GitHub could not be reached to check for updates.": the computer is offline or GitHub is unavailable. The app tries again the next day, or click **Check now**.
- "The update was refused because it could not be confirmed as a release signed by RANN. Nothing was installed.": the update list did not carry RANN's valid signature. Your copy is unchanged; try again later.
- "The download did not match RANN's signed release, so it was deleted.": the file was damaged or altered on the way. Click **Download and check** again.
- "The download failed.": check the connection and the free space in Downloads, then try again.
- "This copy does not check for updates.": copies from the Microsoft Store and Flathub are updated by the store; a copy built from the source code is updated by building it again.

## Reminders and notifications {#reminders}

@index: no notifications; reminders missing

- Reminders and notifications need the household open; nothing is announced while it is closed or locked.
- A bill reminds you only on the days entered in **Remind me (days before)**, and only while the bill is **Active**.
- If no computer notification appears but the banner shows reminders, check that notifications are allowed for RANN's Roost in your system's settings (on Windows, Settings, System, Notifications).

## Getting help {#getting-help}

@index: support; contact; report a problem; GitHub Issues

If this chapter does not solve the problem, write to info-rann-apps@NorthMail.ca, or open an issue on GitHub with **GitHub Issues** under [About](about). Say what you did, what you expected, the exact message and the version shown under About.

> Important: never send passwords, recovery keys, backups or financial details, by email or on GitHub.
