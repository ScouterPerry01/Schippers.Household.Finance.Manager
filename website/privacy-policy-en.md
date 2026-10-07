# Privacy policy: RANN's Roost and RANN's Roost Mobile

*Version française : https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr*

Effective: October 7, 2026

RANN's Roost (for Windows and Linux) and RANN's Roost Mobile (for Android) are published by Perry Schippers, trading as RANN, in Canada ("RANN", "we"). This policy covers both apps. It is published at https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en.

## In short

- **Your financial information stays on your own devices.** RANN does not receive, collect, sell or share it. There is no RANN account, no RANN server, no advertising and no analytics.
- The apps use the internet only for the things listed below. Most happen only after you turn them on. None of them sends amounts, account names, notes or anything about the people in your household, except the optional features you choose to use with your own accounts, which are described in their own sections.

## What stays on your devices

Everything you enter or capture stays in your household folder on your computer: accounts, transactions, documents, health and medical records, assets, users and settings. It is encrypted with AES-256, under keys protected by your password. Backups are encrypted the same way and saved where you choose.

On the phone, captures wait in encrypted storage until they reach your computer. The key is held in Android's secure key store, and the app is excluded from Android cloud backups.

RANN never has a copy and cannot recover your data or your password.

## Between your phone and your computer

The phone sends captures straight to your computer over your home network. Every exchange is encrypted end to end with a key the two devices create when you pair them. Nothing passes through RANN or any other company.

## Calendars on your phone

The phone app can bring the calendars your phone already shows (Google, Outlook or Exchange, Samsung and others) to your computer and, only if you choose "Both ways", write your household's coming items into a calendar on the phone. Nothing is read until you allow calendar access in the app's Settings and tick calendars.

- The app reads only the calendars you tick, for the number of days ahead you choose: each item's title, place, start and end. It does not read descriptions, guests, attachments or reminders. Reading never changes your calendars.
- The app's agenda also shows the calendars you tick, for the coming 60 days, beside what your computer sent. It reads them on the phone each time you open the agenda, only to show them there; that reading sends nothing anywhere.
- The app never signs in to your calendar accounts. It reads what Android already keeps on the phone.
- What it reads goes only to your own computer, encrypted end to end like your captures, over your home network or through your own cloud folder or email. RANN and the cloud or email provider cannot read it.
- On your computer, each calendar is kept private, shown to the other people of your household as busy times only, or shared with them, as you choose. Private calendars are kept in your own encrypted account group.
- Turning it off stops all reading, and the calendars brought in before are removed from your computer at the next transfer. You can also withdraw the permission in Android's settings at any time.

Writing into a calendar is a separate choice, "Both ways", off unless you pick it. Android then asks you to allow the app to write to your calendars.

- The app writes the household's appointments and events, work and school hours, and bills due for the coming 60 days, taken from what your computer already sends to the phone and only what your user may see there.
- You choose where. "RANN's Roost calendar on this phone only" is a calendar of the app's own that belongs to no account: it stays on the phone and is never synced. Other apps on your phone that you have allowed to read calendars can see it, as they can see your other calendars.
- If you choose a calendar of one of your accounts (for example Google or Outlook), Android syncs what the app writes there with that account's provider, under that provider's own privacy policy, and anyone you share that calendar with sees it. RANN receives none of it.
- In either calendar, a medical appointment is written only as "Health appointment", with no place or details, and a bill only as "Bill due" with its name, never its amount.
- The app changes or deletes only the items it wrote itself, and never sends them back to your computer as calendar items. Turning "Both ways" off, choosing another calendar or unpairing the phone removes every item it wrote, and the phone-only calendar.

## Location on the phone

The phone app uses your location only if you allow it, and only for trips: it takes one location fix when you start a trip, one when you arrive, and one when you save a place or look for the nearest saved fuel station. It never follows your phone in the background and never asks for location when it starts.

- The fix is compared with your saved places on the phone itself. No map service, address lookup or other company is asked.
- What goes to your computer, encrypted end to end like everything else, is the name of the place, or its coordinates when you leave it unnamed, and the coordinates of a place you save so the next trip recognizes it.
- On your computer, places and trips are kept in the account group your phone sends to, as your other captures are: the people of your household who can open that group see them, and their phones receive the saved places of the groups they can open. Keep them in your own private group if they are for you alone.
- The trip in progress and the places saved on the phone are kept in the phone's encrypted storage.
- You can refuse or withdraw the permission in Android's settings at any time; trips then work by choosing places or typing their names.

## What the apps send over the internet

All requests use HTTPS. Each service sees your device's internet address, as for any web page, and applies its own privacy policy.

**Bank of Canada** (bankofcanada.ca)
- When: automatically, when your household uses a currency other than the Canadian dollar.
- What it learns: the currency codes and dates of the exchange rates needed.

**ExchangeRate-API** (open.er-api.com)
- When: only if you turn on the second rate source (off by default).
- What it learns: that rates were requested.

**Yahoo Finance** (finance.yahoo.com)
- When: only if you turn on security or metal prices (off by default).
- What it learns: the symbols of the securities you hold, and the four metal symbols.

**CoinGecko** (coingecko.com)
- When: only if you turn on crypto-asset prices (off by default).
- What it learns: the names of the crypto-assets you hold.

**mempool.space**
- When: only when you ask to update a watch-only Bitcoin wallet.
- What it learns: that wallet's public addresses.

**GitHub** (github.com)
- When: only on copies that check for updates, only if you agreed when asked, and at most once a day. These are the Linux packages and the Android app downloaded from GitHub.
- What it learns: that a copy of the app is checking for updates, and the updates you choose to download.

The Microsoft Store and Google Play versions never check GitHub: the store updates them.

## Optional features that use your own accounts

These features are off until you turn them on, and each one says what it sends before it sends anything.

**Reading documents with AI.** On the computer, if on-device reading is not good enough, you can ask Anthropic's Claude to read a document, such as a receipt, a bill or a bank, card or investment statement.
- Pictures of that document's pages go to the AI service, with instructions listing the fields to return. You see each page first (unless you turn that off), can leave pages out, and can hide parts such as account numbers: hidden parts are sent as plain grey, not blurred. Nothing is sent until you choose to send it.
- You use your own account with the AI service. Your key for it is kept in your computer's secure credential store, not by RANN.
- The AI service handles the image under its own terms and privacy policy. RANN receives nothing.

**Transfer through a cloud folder or email.** Instead of the home network, the phone can leave captures in a cloud folder you own (Google Drive, OneDrive, Dropbox or Nextcloud), or send them to your own email address.
- Every capture is encrypted on the phone with your pairing key before it leaves, so the cloud or email provider stores only files it cannot read. It does see their size and when they were sent.
- The app asks only for access to its own folder, not to your other files.

## The phone app and Google

On Android, text recognition, the document scanner and the QR code scanner are provided by Google (ML Kit and Google Play services). They run on your phone, and Google states that your images and the text read from them are not sent to its servers.

Google's components do send Google technical information about their use: the device model and Android version, the app's name and version, an identifier for the installation that is not meant to identify you, and how the features perform. This is governed by [Google's privacy policy](https://policies.google.com/privacy). Neither RANN nor you can turn it off, and it contains nothing from your documents or your household.

**Dictation.** If you tap Dictate the note, the phone's own speech input (often Google's) listens and hands the app only the words; it handles your voice under its own privacy policy. Voice notes you record in the app are not dictation: they stay on the phone until they reach your computer, encrypted end to end.

If you install the app from Google Play, Google handles its download and updates.

## The stores

When you get an app from the Microsoft Store or Google Play, the store handles the download, any payment and updates under its own privacy policy. The stores may give RANN statistics (such as the number of installs) and reports of crashes they collect under their own terms. These do not contain your financial information.

## When you contact RANN

If you write to info-rann-apps@NorthMail.ca or open an issue on GitHub, we receive what you send: your address or GitHub name, and your message. We use it only to answer you and improve the apps. GitHub issues are public and hosted by GitHub in the United States.

We keep messages only as long as needed to help you. Please never send passwords, recovery keys, backups or financial details.

## Your rights and who to contact

RANN is responsible for the personal information it holds. It follows Canada's Personal Information Protection and Electronic Documents Act (PIPEDA) and Quebec's Act respecting the protection of personal information in the private sector (Law 25). The person in charge of the protection of personal information is RANN, at **info-rann-apps@NorthMail.ca**.

You may ask to see, correct or delete the personal information RANN holds about you; in practice, that means your messages. If you are not satisfied with our answer, you may complain to the Office of the Privacy Commissioner of Canada or, in Quebec, to the Commission d'accès à l'information.

## Children

The apps are meant for adults managing their household's finances, not for children.

## Changes

Any new version of this policy will be published at the same address with a new effective date, and noted in the apps' release notes.
