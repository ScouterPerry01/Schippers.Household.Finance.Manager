# RANN's Roost Mobile

RANN's Roost Mobile is the companion app for Android phones. It photographs receipts, bills and other documents, records quick expenses and odometer readings, and sends them to RANN's Roost on your computer. In return it shows a summary of your balances, bills due, budgets and maintenance, and reminds you of bills and budgets.

The phone is not a second copy of your books: the computer is the master copy. The phone keeps only what is waiting to be sent and the latest summary from the computer. Under its icon, the app is called RANN's Roost.

For a short walk-through, see [Getting started with the phone app](start-phone). For the computer side, see [Phones](phones).

@index: Android; mobile app; phone app; companion app; capture; scan receipts; RANN's Roost Mobile

## Two editions {#editions}

@index: Google Play; GitHub; APK

RANN's Roost Mobile comes in two editions that work the same way:

- The Google Play edition, kept up to date by Google Play.
- The GitHub edition, installed from RANN's releases on GitHub. It can check for updates itself and install them after checking RANN's signature (see [Updates](#updates)). Android may ask you to allow RANN's Roost Mobile to install apps the first time.

## Privacy on the phone {#privacy}

@index: encryption; Android Keystore; backup; privacy

- Everything the app keeps (its settings, the captures waiting to be sent and the summary) is encrypted with a key kept in the phone's secure key store. The key never leaves the phone.
- The app is excluded from Android's cloud backups, so none of it is copied to Google.
- Captures go only to your computer, encrypted with the key made when you paired. Away from home, they may go through a folder of your own cloud storage, still encrypted.
- The only other connection is the daily update check of the GitHub edition, if you allow it. It sends nothing about you or your household.
- Once your computer confirms it received a capture, the phone deletes its copy of the pictures and details.

## The lock {#lock}

@index: PIN; NIP; fingerprint; face unlock; biometrics; lock

The app is locked by a PIN, and optionally by your fingerprint or face, so receipts and balances stay private even when someone else has your phone.

### Choose a PIN {#choose-pin}

The first time you open the app, it asks you to **Choose a PIN to lock this app**: 4 to 8 digits. Type it and tap **OK**, then **Enter the PIN again** and tap **OK**. If the two are different, the app says so and you start again.

The PIN is not stored on the phone, only a scrambled check of it. A forgotten PIN cannot be recovered: the app can only start over, erasing what it keeps (see [Forgot your PIN](phone-app#forgot-pin)). Choose one you will remember.

### Unlock the app {#unlock}

- **Enter your PIN**, then **OK**. A wrong PIN shows **Wrong PIN** and clears the box.
- **Use fingerprint or face**: shown when the phone supports it and **Unlock with fingerprint or face** is on in Settings. Android's own prompt appears; **Cancel** returns to the PIN.

The app locks again when you come back to it after more than a minute away.

- **Forgot your PIN?**: see [Forgot your PIN](phone-app#forgot-pin).

### Forgot your PIN {#forgot-pin}

@index: forgotten PIN; reset PIN; lost PIN; erase the app

A forgotten PIN cannot be recovered, by you or by the computer. To use the app again, it starts over:

1. On the lock screen, tap **Forgot your PIN?**.
2. Read the warning **Erase this app's data?**. It explains what is erased.
3. Tap **Erase and start over**, or **Cancel** to keep everything and try your PIN again.
4. The app asks you to choose a new PIN, as the first time.
5. Pair the phone with the computer again. See [Pair a phone](phones#pair).

What is erased, on this phone only: captures not yet sent to the computer (photos, receipts, notes, odometer readings), the list of what was sent, the pairing with the computer, the summary received from it, and every setting, including fingerprint unlock and the transfer folder. It cannot be undone.

What stays: everything the computer already received, and the household on the computer. The old pairing can no longer send anything; remove it from the list on the computer's **Phones** screen. See [The list of phones](phones#phone-list).

> Tip: Captures not yet sent cannot be sent without the PIN. Once the phone is paired again, capture them again.

## The first start {#first-start}

- On Android 13 and later, the app asks whether it may show notifications. Allow it to get bill, budget and maintenance reminders.
- The GitHub edition asks **Check for updates?** once: **Check once a day** or **Don't check**. You can change it later in [Settings](#updates).
- Until the phone is paired, the Capture and Settings tabs show **Not paired yet** and a **Pair with a computer** button.

## The main tabs {#tabs}

Four tabs run along the bottom of the screen:

- **Capture**: photograph or record something new. See [The Capture tab](#capture-tab).
- **Sent**: what you captured and how far it got. See [The Sent tab](#sent-tab).
- **Summary**: balances, bills, maintenance and budgets from your computer. See [The Summary tab](#summary-tab).
- **Settings**: pairing, the transfer folder, the lock and updates. See [The Settings tab](#settings-tab).

When a newer version is available (GitHub edition), a band at the top of the other tabs says so; tap it to go to Settings.

## Pair with a computer {#pair-screen}

@index: pair; pairing; QR code; scan code; pairing text

Pairing connects this phone to RANN's Roost on your computer. Do it once, at home, on the same Wi-Fi as the computer.

1. On the computer, open the household, go to **Phones** and click **Pair a phone**. A QR code appears.
2. On the phone, tap **Pair with a computer** (on the Capture or Settings tab).
3. Tap **Scan the code** and point the camera at the QR code.

The screen says **Pairing…**, then the app returns to the Capture tab, shows **Paired with** and your household's name, and sends right away anything already waiting.

Other ways to pair:

- Scan the QR code with the phone's own camera app: it opens RANN's Roost Mobile and pairs (after you unlock the app).
- **Or paste the pairing text**: when the camera cannot read the screen, click **Copy as text** on the computer, get the text to the phone, paste it here and tap **OK**.

**Cancel** returns to the tabs without pairing.

Messages:

- **This is not a pairing code from RANN's Roost.**: the code or text is not a pairing code.
- **Cannot reach the computer.**: check that both are on the same Wi-Fi, that the household is open on the computer, and that Windows allows RANN's Roost on private networks.
- **The computer refused.**: the code may have expired (each lasts 10 minutes and works once). Show a new one on the computer.

The phone is paired to the user signed in on the computer at that moment. Pairing again, to the same or another computer, replaces the earlier pairing.

## The Capture tab {#capture-tab}

At the top, a card shows the pairing: **Paired with** your household and **Last transfer** with its date and time (or **Nothing sent yet**), or **Not paired yet** with a **Pair with a computer** button. You can capture before pairing: everything waits on the phone.

The buttons:

- **Receipt**: scan a receipt.
- **Bill**: scan a bill or statement.
- **Other document**: scan anything else to keep, such as a warranty or a letter.
- **Quick expense**: record a purchase without a photo.
- **Odometer or hours**: record a vehicle's odometer or an equipment's hours of use.

The kind you choose decides how the capture is filed on the computer: a bill as a bill, a receipt or quick expense as a receipt, another document as whatever the computer reads it to be.

### Scanning {#scanning}

@index: document scanner; multi-page; camera; crop

**Receipt**, **Bill** and **Other document** open Android's document scanner. It finds the edges of the page, crops and straightens it. You can:

- take up to 10 pages for one document (a long receipt, a bill with several pages);
- retake or adjust a page before finishing;
- import a picture from the phone's gallery instead of taking one.

When you finish, the [capture form](#capture-form) opens. Backing out of the scanner captures nothing.

Pages are sent as pictures no larger than 2,400 pixels on their longest side, which keeps them readable and the transfers small. Several pages become one PDF on the computer.

### Sharing from other apps {#share-into}

@index: share; e-receipt; PDF receipt; e-bill

In another app (email, a store's app, your files, the photo gallery), use **Share** and choose RANN's Roost Mobile to send it:

- a PDF, such as an e-receipt or an e-bill, is kept as it is;
- one or more pictures become the pages of one document.

After you unlock the app, the capture form opens as a **Document** capture, with the file's name kept. Shared pictures are read like scanned pages; a PDF is not read on the phone, and the computer reads it instead. Save it as usual.

## The capture form {#capture-form}

The form's title is the kind of capture. With several pages it shows how many. For scanned pages, **Reading the document…** shows while the phone reads the first page; it then fills in the store, the date and the total it found, which you can correct. The text is read on the phone itself, in English and French, and goes to the computer with the capture.

**Everything is optional: you can finish on the computer.**

- **Store or biller**: the store, restaurant or company. Filled in from the reading. On the computer it becomes the document's merchant, and in the Sent list it is the capture's name.
- **Date (YYYY-MM-DD)**: the date on the receipt or bill, such as 2026-10-05. Filled in from the reading; today for a quick expense. A date the computer cannot read is ignored and the read date is kept.
- **Amount**: the total, such as 42.17 or 42,17 (a dollar sign is ignored). It is rounded to the cent. Anything that is not a number is left out, and the computer uses what it read.
- **Paid with**: an account from your computer, or **(none)**. On the computer it becomes the account offered first when a transaction is created from the capture.
- **Category**: a spending category from your computer, shown under its parent such as "Food › Groceries", or **(none)**. On the computer it becomes the category offered first for that transaction.
- **For**: a member of the household or a pet, or **(the household)**. On the computer it becomes the person or pet offered first for that transaction.
- **Note**: anything to remember, such as "Lunch with client" or "Return by Nov. 3". On the computer it becomes the document's notes.

**Paid with**, **Category** and **For** appear once the phone has received a summary from the computer, after the first transfer. They are sent with the capture and kept with the document on the computer. When you review it there and choose **New transaction from this document**, the form starts with them; you can still change them. See [New transaction from this document](documents#new-transaction).

The buttons:

- **Cancel**: closes the form; nothing is kept.
- **Save**: puts the capture in the queue and tries to send it at once. The app goes to the **Sent** tab.

The values you type win over what the phone or the computer read.

### Voice notes {#voice-note}

@index: dictation; voice note; speech

Under **Note**:

- **Dictate the note**: speak and Android types your words into the note, in Canadian English or French following the phone's language. Each dictation is added to the end of the note.
- **Record a voice note**: records your voice, up to a minute; the first time, Android asks to allow the microphone. **Recording… (up to a minute)** shows while it records. Tap **Stop recording** to finish. It then shows **Voice note kept** and its length, with **Delete** to discard it and record again.

A recorded voice note is sent with the capture and kept with its document on the computer, where you can play it while reviewing. The microphone is used only while you record.

### Quick expense {#quick-expense}

@index: cash purchase; expense without receipt

**Quick expense** opens the same form with no pages and today's date. Use it for a cash purchase or anything without a receipt. On the computer it becomes a short text document with the store, date, amount and note, waiting on the **To review** tab of Documents like the others.

## Odometer or hours {#odometer-form}

@index: odometer; mileage; meter reading; hours of use; kilometres

Records a reading for a vehicle, or for equipment measured in hours of use (a generator, a tractor, a boat motor), so the computer can track maintenance due by distance or hours and the year's kilometres for the [Trip log](trips).

- **Vehicle or equipment**: the vehicles and metered equipment from your computer, each with its latest reading, such as "Civic (84,210 km)". The list fills in after the first transfer.
- **Odometer (km)** or **Hours of use**, following the item chosen: the reading, in whole numbers, up to 7 digits.
- **Date (YYYY-MM-DD)**: today by default.
- **Cancel**: closes without saving.
- **Save**: available once an item and a reading are entered. The reading joins the queue and is sent at once if possible.

On the computer, the reading is added straight to the vehicle on the Vehicles screen, or to the equipment's meter on Home and assets, without review.

## The Sent tab {#sent-tab}

@index: queue; outbox; send; transfer status

The Sent tab lists what you captured, newest first, and how far it got.

### Send now {#send-now}

**Send now** sends everything waiting to the computer at once, and fetches a fresh summary even when there is nothing to send. While it works, it shows **Sending…**. The result appears under it:

- **Sent.**: the computer received the items.
- **Nothing new to send. Summary updated.**
- **Pair with your computer first.**
- **Your computer is not in reach. Items wait here and are sent automatically on Wi-Fi.**: the phone is not on the same network, or the household is not open on the computer.
- **Your computer is not in reach: the captures were left, encrypted, in your transfer folder.**: shown instead when a transfer folder is chosen. The computer imports them, and the phone collects the confirmation next time.
- **The transfer folder could not be written to. Choose it again in Settings.**
- **Someone else is signed in on the computer.**: the phone belongs to another user of the household. Your items wait until you open the household there.
- **This phone was removed on the computer. Pair it again to send.**: the phone is no longer paired; its captures stay on the phone.
- **Sending failed. Try again later.**

### Share as a file {#share-file}

**Share as a file…** makes one or more encrypted transfer files of every capture not yet confirmed, then opens Android's share menu so you can send them by email, save them to a USB key or your files, or pass them to another app. Each file holds up to about 15 MB of pictures, so large batches make several files. The button is available when something is still waiting.

On the computer, import the files with **Import a transfer file…** on the Phones screen, or drop them on the Documents screen. The shared items show **Sent** until a later transfer, over Wi-Fi or through the transfer folder, confirms them.

### The list of captures {#queue}

Each capture shows its name (the store, or the kind of capture, or the vehicle and reading), its kind, the amount if any, and when it was captured. On the right, its status:

- **Waiting**: not yet received by the computer.
- **Sent**: left in the transfer folder or shared as a file, waiting for the computer's confirmation.
- **On the computer**: received. The phone's copy of the pictures and details is deleted; the line stays for 30 days, then disappears.
- **Not accepted**: the computer could not store it; the reason is shown in red. It is tried again at each transfer.

**Delete**, on any capture not yet on the computer, removes it from the phone at once, without asking. It cannot be recovered.

### When the app sends {#sending}

You rarely need **Send now**:

- Saving a capture tries to send at once.
- In the background, the app sends soon after a capture and then every hour while something is waiting, when the phone is on Wi-Fi (or another unmetered network).
- With a transfer folder chosen, the app also checks every hour on any connection: it collects the computer's confirmations and, when the computer is out of reach, leaves new captures in the folder.

Items are sent in small batches, oldest first. A capture already left in the folder is not written there again, but the phone still sends it over Wi-Fi when it can; the computer receives each capture only once.

## The Summary tab {#summary-tab}

@index: balances; bills due; budgets; maintenance due

The Summary shows figures from your computer, as of the last transfer: the household's name, then **From your computer** and the date and time of that transfer. Before the first transfer it asks you to pair.

- **Accounts**: each account and its balance.
- **Bills due**: the bills due in the next 60 days that are not yet paid, up to 15, with the due date and the amount, or **about** an amount when it is estimated.
- **Maintenance this month**: shown when something is due: each task, such as "Civic: Oil change", with **due now**, **due soon** or its date.
- **Budgets this month**: each spending category with a budget: what was spent of the budget, such as "$412.30 of $600.00".

The figures do not change until the next transfer. Tap **Send now** on the Sent tab to refresh them.

## Notifications {#notifications}

@index: reminders; bill reminder; budget alert; maintenance reminder; notifications

From the latest summary, the phone shows notifications, each once, checked after every transfer and about every 12 hours:

- **Bill reminders**: when a bill is due within its reminder days, as set on the computer ("Hydro is due in 3 days", "due tomorrow", "due today").
- **Budget alerts**: when a category's spending this month reaches 80 % of its budget, and again when the budget is used up. They use only figures from a transfer made this month.
- **Maintenance reminders**: when a task becomes due soon, and when it is due.

They appear only if you allowed notifications. Each kind has its own channel in Android's notification settings, where you can turn it off.

## The Settings tab {#settings-tab}

The pairing card is at the top, as on the Capture tab.

### Unpair {#unpair}

**Unpair**, shown when paired, makes the phone forget the computer at once. Captures not yet sent stay on the phone until you pair again. The computer still lists the phone: use **Remove** on its Phones screen to stop it there too.

### Away from home {#transfer-folder}

@index: transfer folder; Google Drive; OneDrive; Dropbox; Nextcloud; cloud folder

Shown when paired. When the computer is not in reach, captures can be left, encrypted, in a folder of your own Google Drive, OneDrive, Dropbox or Nextcloud; the service only sees files it cannot read. Choose the same folder in RANN's Roost on the computer (see [Phones](phones#away-from-home)).

- The line shows **Transfer folder:** and the folder, or **No transfer folder chosen.**
- **Choose a folder…** (or **Change the folder…**): opens Android's folder picker. Choose your cloud service in its menu, then the folder, and allow access. The service's app must be installed on the phone.
- **Stop using it**: the phone stops using the folder and gives back its access. Nothing in the folder is deleted.

### Change PIN {#change-pin}

**Change PIN** asks you to choose a new PIN, 4 to 8 digits, and to enter it again.

### Unlock with fingerprint or face {#biometric}

Shown when the phone has a fingerprint reader or face unlock set up. When on, the lock screen offers **Use fingerprint or face**. The PIN always works too.

### Updates {#updates}

@index: update; new version; signature

Shown in the GitHub edition only; the Google Play edition is updated by Google Play.

- **Check for updates once a day**: when on, the app looks on GitHub at most once a day, while it is open, for a newer version. Only the check goes out: GitHub sees your phone's internet address, as for any web page. Nothing about your captures or household is sent.
- The status: **Update checks are off.**, **Checking…**, **Version … is up to date.**, or **Version … is available.** with what is new.
- **Download, check and install**: downloads the new version, checks it against RANN's signature and its announced size and fingerprint, then hands it to Android, which asks you to confirm the install. A bar shows the download.
- **Check now**: checks at once.

Messages when something goes wrong:

- **The update was refused because it could not be confirmed as a release signed by RANN. Nothing was installed.**
- **The download did not match RANN's signed release, so it was deleted.**
- **GitHub could not be reached to check for updates.**, with the reason.
- **The update could not be installed.**, with the reason.

### About and privacy policy {#about}

A short note reminds you that your data stays on your phone and your computer. **Privacy policy** opens RANN's privacy policy on rann.ca in your browser, in the phone's language.
