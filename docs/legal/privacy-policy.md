# Privacy policy: RANN's Roost and RANN's Roost Mobile

*Version française : [privacy-policy.fr.md](privacy-policy.fr.md)*

Effective: October 3, 2026

RANN's Roost (for Windows and Linux) and RANN's Roost Mobile (for Android) are published by Perry Schippers, trading as RANN, in Canada ("RANN", "we").

## In short

- **Your financial information stays on your own devices.** RANN does not receive, collect, sell or share it. There is no RANN account, no RANN server, no advertising and no analytics.
- The apps contact the internet only for the things listed below, most of them only after you turn them on, and never send amounts, account names, notes or anything about the people in your household.

## What stays on your devices

Everything you enter or capture (accounts, transactions, documents, health and medical records, assets, users and settings) is kept in your household folder on your computer, encrypted with AES-256 under keys protected by your password. Backups are encrypted the same way and saved where you choose. On the phone, captures wait in storage encrypted with a key held by Android's secure key store until they reach your computer, and the app is excluded from Android cloud backups. RANN never has a copy and cannot recover your data or your password.

## Between your phone and your computer

The phone sends captures straight to your computer over your home network. Every exchange is encrypted end to end with a key the two devices create when you pair them. Nothing passes through RANN or any other company.

## What the apps send over the internet

All requests use HTTPS. Each service sees your device's internet address, as for any web page, and applies its own privacy policy.

| Service | When | What it learns |
|---|---|---|
| Bank of Canada (bankofcanada.ca) | Automatically, when your household uses a currency other than the Canadian dollar | The currency codes and dates of the exchange rates needed |
| ExchangeRate-API (open.er-api.com) | Only if you turn on the second rate source (off by default) | That rates were requested |
| Yahoo Finance (finance.yahoo.com) | Only if you turn on security or metal prices (off by default) | The symbols of the securities you hold, and the four metal symbols |
| CoinGecko (coingecko.com) | Only if you turn on crypto-asset prices (off by default) | The names of the crypto-assets you hold |
| mempool.space | Only when you ask to update a watch-only Bitcoin wallet | That wallet's public addresses |
| GitHub (github.com) | Only on copies that check for updates (Linux packages and the Android app from GitHub), only if you agreed when asked, at most once a day | That a copy of the app is checking for updates; updates you choose to download |

The Microsoft Store and Google Play versions never check GitHub: the store updates them.

## The phone app and Google

On Android, text recognition, the document scanner and the QR code scanner are provided by Google (ML Kit and Google Play services). They run on your phone and your images are read on your phone. Google's components may send Google technical information about their use, such as the device model and how the features perform, under [Google's privacy policy](https://policies.google.com/privacy). If you install the app from Google Play, Google handles its download and updates.

## The stores

When you get an app from the Microsoft Store or Google Play, the store handles the download, any payment and updates under its own privacy policy. The stores may give RANN statistics (such as the number of installs) and reports of crashes they collect under their own terms. These do not contain your financial information.

## When you contact RANN

If you write to info-rann-apps@NorthMail.ca or open an issue on GitHub, we receive what you send (your address or GitHub name and your message) and use it only to answer you and improve the apps. GitHub issues are public and hosted by GitHub in the United States. We keep messages only as long as needed to help you. Please never send passwords, recovery keys, backups or financial details.

## Your rights and who to contact

RANN is responsible for the personal information it holds and follows Canada's Personal Information Protection and Electronic Documents Act (PIPEDA) and Quebec's Act respecting the protection of personal information in the private sector (Law 25). The person in charge of the protection of personal information is RANN, at **info-rann-apps@NorthMail.ca**.

You may ask to see, correct or delete the personal information RANN holds about you (in practice, your messages). If you are not satisfied with our answer, you may complain to the Office of the Privacy Commissioner of Canada or, in Quebec, to the Commission d'accès à l'information.

## Children

The apps are meant for adults managing their household's finances, not for children.

## Changes

A new version of this policy will be published at the same address with a new effective date, and noted in the apps' release notes.
