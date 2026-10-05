# Phones

On the Phones screen you pair RANN's Roost Mobile, the companion app for Android phones, with this computer, and manage the phones already paired. Paired phones send receipts, bills, other documents, quick expenses and odometer readings to this computer, and receive a summary of balances, bills and budgets in return. The screen is in the **Settings** group of the menu, under **Phones**.

For the phone side, see [RANN's Roost Mobile](phone-app). For a short walk-through, see [Getting started with the phone app](start-phone).

@index: Android; mobile app; companion app; sync; synchronize; pairing; QR code

## How phones and this computer work together {#how-it-works}

- The computer is the master copy. The phone keeps only its captures waiting to be sent, and a summary from the computer.
- At home, the phone sends over your Wi-Fi directly to this computer. Nothing goes through the internet or any RANN server.
- Everything between the phone and this computer is encrypted with a key the two made when they were paired. No other phone or computer can read it.
- Phones can reach this computer only while the household is open in RANN's Roost. Locking the household or closing the app stops the listener; the phone keeps its captures and sends them later.
- Away from home, the phone can leave its captures, still encrypted, in a folder of your own cloud storage, which this computer watches (see [Away from home](#away-from-home)).
- What arrives never goes straight into your books. Receipts, bills, documents and quick expenses wait on the **To review** tab of [Documents](documents); odometer and hour readings are added to the vehicle or equipment directly.

@index: encryption; Wi-Fi; local network; home network; privacy

### The listener {#listener}

While the household is open, RANN's Roost listens for phones on your home network. The line under the explanation says how things stand:

- "Phones can reach this computer at 192.168.1.20:47311 while the household is open.": all is well. The address is this computer's address on your home network and the port it listens on (47311, or the next free one up to 47320 if another program uses it).
- "Phones cannot reach this computer:" followed by the reason: the listener could not start, usually because no port was free.
- "This computer is not connected to a local network.": no home network was found. Connect to your Wi-Fi or wired network and come back to the screen.

When the computer has several network adapters, RANN's Roost picks the address of your home network and avoids those of virtual machines and similar adapters.

> Note: The first time, Windows may ask whether to allow RANN's Roost on networks. Allow it on private networks, or phones cannot reach it.

## Pair a phone {#pair}

@index: pair; pairing code; scan code

**Pair a phone**, at the top right, shows a pairing code. It is available only while the listener is running and a home network address was found.

### The pairing window {#pairing-window}

The window shows a QR code on the left and, on the right:

- the steps: on the phone, open RANN's Roost Mobile, tap **Pair with a computer** and scan the code; the phone's own camera app can also scan it and open RANN's Roost Mobile;
- a reminder that the phone must be on the same Wi-Fi as this computer, and the Windows network question;
- the time left: each code is valid for 10 minutes (the default, set in [Rates and rules](rates-rules)), then "This code has expired. Close and pair again.";
- the address and port the phone will use;
- **Copy as text**: copies the pairing link to the clipboard, for a phone whose camera cannot read the screen. Send it to the phone in a way you trust and paste it into **Or paste the pairing text** on the phone.

When the phone has paired, the window says "The phone is paired. This window closes by itself.", closes two seconds later, and the phone appears in the list. **Close** closes the window at any time; an unused code simply expires.

Each code works once. Opening the window again makes a new code; an earlier code stays valid until it expires.

### Who a phone belongs to {#owner}

A phone is paired to the user who is signed in when it pairs. Only that user can receive its captures: when someone else has the household open, the phone keeps its items and tells its owner that someone else is signed in. The captures come in the next time the owner opens the household.

Where the documents from a phone are stored depends on that user: an administrator's go to the shared account group, a member's go to their own private group when they have one. You can change this for each phone with **Edit**.

@index: several users; private group; shared group

## The list of phones {#phone-list}

Each paired phone has a card with:

- its name, as the phone gave it (its maker and model) or as you renamed it, with **(removed)** after a phone you removed;
- **paired** and the date and time it was paired;
- **last transfer** and the date and time it last sent something, or **nothing sent yet**;
- how many items it has sent ("12 items received");
- **documents go to** and the account group its documents are stored in.

The buttons:

- **Edit**: opens the [phone dialog](#phone-dialog).
- **Remove**: stops the phone at once, without asking. It can no longer send or receive anything: the next time it tries, it learns it was removed, unpairs itself and keeps its unsent captures. To use it again, pair it again. Use **Remove** for a lost, sold or replaced phone. What it already sent stays in RANN's Roost.
- **Forget**: shown for a removed phone. Takes it off the list.

@index: lost phone; stolen phone; revoke; remove a phone

### Phone dialog {#phone-dialog}

- **Name**: the name shown on the card, such as "Alex's phone". Required.
- **Store in**: the account group the phone's receipts, bills, documents and quick expenses are stored in. Only groups you can add to are offered; a private group shows "(private)" after its name. Changing it affects what the phone sends from now on; documents already received stay where they are.

## Away from home {#away-from-home}

@index: transfer folder; cloud folder; Google Drive; OneDrive; Dropbox; Nextcloud; email transfer; USB; roostsync

When the phone is not on your home Wi-Fi, it can leave its captures in a transfer folder of your own Google Drive, OneDrive, Dropbox or Nextcloud. That service's app copies the folder to this computer, and RANN's Roost imports from it. The files are encrypted with the phone's key, so the service only ever stores files it cannot read.

The **Away from home** card shows the folder in use ("Transfer folder: …") or "No transfer folder chosen.", and these buttons:

- **Choose a transfer folder…** (or **Change the folder…**): pick the folder as it appears on this computer, the same one you chose on the phone. RANN's Roost checks it right away and says how many captures it imported.
- **Stop using it**: shown once a folder is chosen. RANN's Roost stops watching the folder. Nothing in it is deleted.
- **Import a transfer file…**: pick a transfer file (a RANN's Roost transfer file, ending in .roostsync) that came another way, such as an email attachment you saved or a file copied by USB. You can also drop such a file on the [Documents](documents) screen. This works even when the listener is not running.

The result of the last check or import is shown at the bottom of the card.

### How the transfer folder is watched {#watching}

While the household is open, RANN's Roost looks in the transfer folder every 20 seconds:

- Each new file from a paired phone is imported, then deleted from the folder, and a confirmation file is left beside it for the phone. The phone collects it the next time it looks, and only then deletes its own copies. A file still being copied by the cloud service is left for the next round.
- A file from a phone that belongs to another user of the household is left in place until that user opens the household; the card says so.
- A file for another household, such as one that shares the same folder, is left alone.
- A file from a phone that is not, or no longer, paired, or a file that is not a transfer file, is moved to a "Not imported" folder inside the transfer folder.
- Confirmations a phone never collected are deleted after 60 days.

### Messages after importing a file {#import-messages}

- "… captures imported. The phone confirms them at its next transfer.": the file was read. "nothing new (already imported)" means its captures were already received. A file imported by hand leaves no confirmation in a folder: the phone shows the items as sent until its next transfer over Wi-Fi or by folder, which confirms them.
- "… is from …'s phone; … must open the household to import it.": the phone belongs to another user.
- "… is for another household."
- "… is from a phone that is not paired with this household.": pair the phone again.
- "… is not a RANN's Roost transfer file."

## What happens to what a phone sends {#received}

- A receipt, bill or other document becomes a document in [Documents](documents), on the **To review** tab. Several pages become one PDF; a single page stays a picture; a PDF shared on the phone stays as it is.
- The text read on the phone comes with it. When the phone could not read it, this computer reads it.
- What the person typed on the phone (store or biller, date, amount and note) replaces what was read. A bill is filed as a bill; a receipt or quick expense as a receipt.
- A quick expense without a photo becomes a short text document with the store, date, amount and note, to review like the others.
- A voice note recorded with a capture is kept with its document; play it from the document's review window.
- An odometer or hours reading is added to that vehicle's readings on the [Vehicles](vehicles) screen, or to that equipment's meter on [Home and assets](assets), with no review.
- Each capture is received exactly once, even when the phone sends it again.

Review each document on the **To review** tab: attach it to a transaction, record it on a bill, or file it.

## What the phone receives {#sent-to-phone}

After each transfer, the phone receives a fresh summary when anything in it changed: the household's name, the language the app is shown in on this computer, the base currency, your accounts and balances, the categories, up to 400 payees, the members and pets, the vehicles and metered equipment with their latest readings, the bills due in the next 60 days with their reminder days, this month's budgets for spending categories, and the maintenance due this month. The phone shows it on its **Summary** tab and uses it for its reminders and pick lists. See [The Summary tab](phone-app#summary-tab).
