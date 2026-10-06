# Privacy, data and security

This chapter explains where your information is kept, how it is protected, what little ever leaves your computer and why, how passwords and recovery keys work, and how to keep a copy safe or move to a new computer.

In short: your financial information stays on your computer and your phone, encrypted. RANN does not receive or collect it. There is no RANN account, no advertising and no analytics.

## Where your data lives {#where-data-lives}

@index: data location; storage; local data

### The household folder {#household-folder}

@index: household folder; .hfm folder; data file

A household is a folder on your computer, made when you created it, with a name that ends in .hfm, such as Tremblay Family.hfm. Everything about the household is inside:

- a key ring file, which holds the login names and the locked keys of each user, but no names of people, amounts or institutions;
- an encrypted core database: the users, people, categories, payees, budgets, settings and the activity log, which every user of the household can read;
- one encrypted database for each account group: its accounts, transactions, documents' details, investments and every other record kept in that group;
- the document vault: one encrypted file for each receipt, bill or other document;
- copies taken automatically before the app upgrades a database to a newer version, in case something goes wrong.

Treat the folder as one thing: do not rename, move or edit the files inside it. To move the household, see [Moving to a new computer](privacy-data#new-computer).

> Important: anyone who deletes the folder deletes the household. Keep backups, on another disk; see [Backups](backups).

### Encryption {#encryption}

@index: encryption; AES-256; encrypted; private accounts; account groups

Every database in the household is encrypted with AES-256, each with its own random key. Those keys are locked in turn with each user's password, and a second time with that user's recovery key. Nothing in the folder can be read without a password or recovery key that opens it.

The password is never stored. When you sign in, the app works out a key from it in a deliberately slow way, so that someone who steals the folder cannot try millions of guesses.

Account groups add a second protection inside the household:

- Shared groups can be opened by every administrator and by the other users given access.
- A member's private group can be opened only by its owner and the people they share it with. An administrator cannot open it either, unless the owner shares it, and even someone who copies the folder cannot read it without the owner's password.

See [Users](users) for roles and access.

### The document vault {#document-vault}

@index: vault; documents; receipts; encrypted documents

Receipts, bills and other papers you import or receive from the phone are kept in the vault inside the household folder, each sealed in its own encrypted file under its account group's key. A document can be read only by those who can open its group. Documents are read (text recognition) on this computer; nothing is sent anywhere unless you choose to read one with AI.

Files of up to 50 MB can be kept. The Old documents tab of [Documents](documents) lists filed documents older than the six years the Canada Revenue Agency asks you to keep tax records; nothing is ever deleted automatically.

### Settings kept on this computer {#computer-settings}

@index: preferences; per-computer settings

A few choices are saved on the computer rather than in the household: the language, colours and text size, the auto-lock time, the menu layout of each user, the list of recent households and the answer to the update question. They hold nothing about your finances. See [Settings kept on this computer](basics#computer-settings).

The key for [AI reading](ai), if you add one, is kept in your system's own secure store: the Windows Credential Manager on Windows, or your desktop's keyring on Linux. When Linux has no keyring running, the key is kept only in memory until you close the app.

### Account numbers {#account-numbers}

@index: account number; masked number; Show number

Full account numbers are kept encrypted like everything else, and the screens show only their last digits. To see a full number, use **Show number** on the account: the app asks for your password first.

### On the phone {#on-the-phone}

@index: phone storage; PIN

RANN's Roost Mobile keeps its settings, the lists and contacts it receives from the computer and its queue of captures and new contacts in files encrypted with a key held in the phone's own secure hardware. It is locked by a PIN, with fingerprint or face unlock if you choose, and it is left out of the phone's cloud backups. A phone never holds the household's keys: it can only send captures and new contacts, and receive short lists. See [RANN's Roost Mobile](phone-app).

What goes to the phone: the household's name and language, the base currency, the accounts and their balances, the categories, payees, members and pets, vehicles and metered equipment with their readings, the bills due in the next 60 days, this month's budgets and maintenance, and the contacts its owner can see (not archived ones). Full account numbers, contacts' account and client numbers, transactions, documents and health records do not go to the phone. Only what its owner can see on this computer goes to a phone: another user's private groups never do. See [What the phone receives](phones#sent-to-phone).

## Passwords and the recovery key {#passwords}

@index: password; master password; recovery key

### The password {#master-password}

@index: master password; password length; passphrase

Each user signs in with their own login name and password. A password has at least 12 characters. A passphrase of a few unrelated words is easy to remember and hard to guess. Do not reuse a password from another service.

There is no server, so no one can reset a forgotten password by email: not RANN, not an administrator. Only the user's recovery key can.

### The recovery key {#recovery-key}

@index: recovery key; lost password; forgotten password

Each user receives a recovery key when their account is made: when the household is created for the first administrator, and when an administrator adds a user. It is 54 letters and digits in groups of four, shown once.

- Print it or write it down and keep it somewhere safe, away from the computer, such as with your important papers.
- It works with every backup of the household too.
- It stays the same when you change your password, and keeps working after a reset.
- Whoever has your recovery key and your login name can set a new password and open what you can open. Guard it like a house key.

### Changing a password {#change-password}

@index: change password

To change your password, go to [Users](users) and click **Change my password…**. Enter your current password, then the new one twice. Your recovery key stays the same.

### A forgotten password {#forgotten-password}

@index: forgot password; reset password

On the Unlock screen, click **Forgot your password?**, then enter your login name, your recovery key and a new password. See [Reset password with recovery key](basics#reset-screen).

If both the password and the recovery key are lost, the household cannot be opened by anyone, and neither can its backups. This is the price of a household that nobody else can read.

## Locking {#locking}

@index: lock; auto-lock; step away

When the household is locked, its keys are wiped from memory. Use **Lock** in the top bar when you step away, and choose under [Security](security) how soon the household locks by itself after no keyboard or mouse activity (10 minutes to start with). Closing the window locks too. On a computer that others use, a short time is safer. See [Locking](basics#locking).

## What leaves this computer {#what-leaves}

@index: internet; network; online; data sent; third parties

RANN's Roost works without the internet. It goes online only in the cases below, most of them off until you turn them on. RANN itself receives nothing in any of them.

### Exchange rates {#exchange-rates}

@index: Bank of Canada; exchange rates; ExchangeRate-API

When your household has an account or an amount in another currency, the app downloads the Bank of Canada's daily exchange rates. Only the list of rates is downloaded; nothing about you is sent.

If you turn on the second source under [Rates and prices](rates), rates for currencies the Bank of Canada does not publish are downloaded once a day from ExchangeRate-API, a free public service, in the same way.

### Market prices {#market-prices}

@index: prices; Yahoo Finance; CoinGecko; stock prices

Price downloads are off until you turn them on under [Rates and prices](rates):

- stock and ETF prices, and gold, silver, platinum and palladium prices, from Yahoo Finance;
- crypto-asset prices from CoinGecko.

Each sends the symbols of what you hold, such as XIC or BTC, and nothing else about you or your amounts.

### Watch-only wallets {#watch-only}

@index: Bitcoin; watch-only wallet; mempool.space; xpub

When you ask the app to update a watch-only Bitcoin wallet, it asks mempool.space, a public block explorer, about the wallet's addresses. That service learns those addresses and, like any website, your computer's internet (IP) address, which can tell roughly where you are; nothing else about you. Never enter a private key or seed phrase: the app refuses them. See [Investments](investments).

### AI reading {#ai-reading}

@index: AI; Claude; Anthropic; cloud reading

AI reading is off until you turn it on under [AI reading](ai) and add your own Anthropic key. Then, for documents that are hard to read on this computer, you can send pages to Claude, Anthropic's AI:

- only the pages you approve are sent, after you crop them and hide what you choose, such as a full account number; hidden areas are replaced by flat blocks before the picture leaves the computer;
- Anthropic receives them to read them, under your own account, which pays a few cents per reading;
- nothing else about your household leaves the computer.

### Phone transfers {#phone-transfers}

@index: Wi-Fi; phone sync; transfer folder; cloud folder

RANN's Roost Mobile talks only to this computer, over your home Wi-Fi, encrypted between the two. Nothing goes through the internet. The phone sends captures and new contacts, and receives the summary and contacts described in [On the phone](#on-the-phone). Away from home, the phone can leave its captures and new contacts, encrypted, in a folder of your own Google Drive, OneDrive, Dropbox or Nextcloud; that service sees only files it cannot read. RANN holds no account with those services. See [Phones](phones).

### Update checks {#update-checks}

@index: update check; GitHub

On Linux copies installed from a .deb or .rpm package or an AppImage, and only if you agreed, the app checks GitHub once a day for a new version. GitHub sees your computer's internet address and that the app is in use, as for any web page. Every update is checked against RANN's signature before it can be installed. Other copies are updated by their store and make no check.

### Links you open {#links}

The buttons under [About](about), such as **Read the privacy policy** and **Source code on GitHub**, open those pages in your web browser, like any link.

### What never leaves {#never-leaves}

Your accounts, transactions, balances, documents, health records, passwords and keys never leave your computer and phone, except in a backup or export that you place yourself, and in the pages you choose to send for AI reading.

The privacy policy, linked from [About](about), says what each outside service learns.

## Backups {#backups}

@index: backup; restore; hfmbak; copy of the household

A backup is a single file (.hfmbak) holding a copy of the whole household folder. It stays encrypted: restoring it needs the same password or recovery key as the household. Each backup is checked right after it is made.

By default, the app makes a backup every day into a folder beside the household, keeping 10 versions. Choose a folder on another disk (an external drive, a network drive or a cloud folder) so a broken disk or a lost computer does not take the backups with it. Because backups are encrypted, a cloud folder sees only files it cannot read. If no backup has worked for 7 days, the Dashboard tells you.

See [Backups](backups) for every setting, and [Restoring from a backup](basics#restore) for bringing one back.

## Export all data {#export}

@index: export; CSV; JSON; open formats; unencrypted

**Export all data…**, on the [Backups](backups) screen, saves everything you can see in open formats (CSV and JSON) in a single zip file, so your data can always be used elsewhere. Before it starts, the app warns you:

> Important: the export is not encrypted. Anyone with the file can read your accounts, transactions, account numbers and documents. Save it somewhere safe and delete it when you no longer need it.

## Moving to a new computer {#new-computer}

@index: new computer; move the household; migrate

1. On the old computer, open the household and click **Back up now** under [Backups](backups). Wait for "Backup saved and checked".
2. Copy the newest .hfmbak file from the backup folder to the new computer, with a USB key or your cloud folder.
3. Install RANN's Roost on the new computer and start it.
4. On the Welcome screen, click **Restore from a backup…**, choose the file, then choose where to put the household.
5. Sign in with your usual login name and password.

Then, on the new computer:

- check the folders under [Backups](backups) (the backup folder), [Phones](phones) (the transfer folder) and [Documents](documents) (the watched folder): they may point to places that do not exist there;
- choose the language, colours, text size and auto-lock time again, since they belong to each computer;
- add your key again under [AI reading](ai) if you use it;
- if a phone can no longer reach the computer, pair it again under [Phones](phones).

Each user of the household signs in on the new computer with their own password, as before.

## Sharing a computer or a household {#sharing}

@index: shared computer; several users; family members

- Give each adult their own user under [Users](users), with the role that suits them: Administrator, Member or Viewer. Each signs in with their own password and sees only what they may.
- Lock the household when you step away, and keep the auto-lock time short.
- Each user's phones send to that user only: another user signed in at the time cannot read them, and the phone waits until its owner opens the household.
- The activity log, under [Users](users), records who changed what.
