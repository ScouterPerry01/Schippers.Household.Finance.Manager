# Privacy policy: RANN's Roost and RANN's Roost Mobile

*Version française : https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr*

Effective: October 6, 2026

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

The phone app can bring the calendars your phone already shows (Google, Outlook or Exchange, Samsung and others) to your computer. It is off until you turn it on in the app's Settings, and Android then asks you for calendar access.

- The app reads only the calendars you tick, for the number of days ahead you choose: each item's title, place, start and end. It does not read descriptions, guests, attachments or reminders, and it never changes your calendars.
- The app never signs in to your calendar accounts. It reads what Android already keeps on the phone.
- What it reads goes only to your own computer, encrypted end to end like your captures, over your home network or through your own cloud folder or email. RANN and the cloud or email provider cannot read it.
- On your computer, each calendar is kept private, shown to the other people of your household as busy times only, or shared with them, as you choose. Private calendars are kept in your own encrypted account group.
- Turning it off stops all reading, and the calendars brought in before are removed from your computer at the next transfer. You can also withdraw the permission in Android's settings at any time.

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

Some optional features are still being added to the apps before version 1.0. They are described here in advance, so this policy does not need to change when they arrive. All of them are off until you turn them on, and each one says what it sends before it sends anything.

**Reading documents with AI.** If on-device reading is not good enough, you can ask an AI service, such as Anthropic's Claude, to read a receipt or bill.
- The image of that one document goes to the AI service, with instructions listing the fields to return. You can be asked to confirm each document, and you can blur parts such as account numbers before it is sent.
- You use your own account with the AI service. Your key for it is kept in your computer's secure credential store, not by RANN.
- The AI service handles the image under its own terms and privacy policy. RANN receives nothing.

**Transfer through a cloud folder or email.** Instead of the home network, the phone can leave captures in a cloud folder you own (Google Drive, OneDrive, Dropbox or Nextcloud), or send them to your own email address.
- Every capture is encrypted on the phone with your pairing key before it leaves, so the cloud or email provider stores only files it cannot read. It does see their size and when they were sent.
- The app asks only for access to its own folder, not to your other files.

## The phone app and Google

On Android, text recognition, the document scanner and the QR code scanner are provided by Google (ML Kit and Google Play services). They run on your phone, and Google states that your images and the text read from them are not sent to its servers.

Google's components do send Google technical information about their use: the device model and Android version, the app's name and version, an identifier for the installation that is not meant to identify you, and how the features perform. This is governed by [Google's privacy policy](https://policies.google.com/privacy). Neither RANN nor you can turn it off, and it contains nothing from your documents or your household.

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
