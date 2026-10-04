# Google Play listing: RANN's Roost Mobile

Texts for Play Console (DIST-03, DIST-07), checked by `tools/dev/check_store_texts.py`. The Play
build is the `play` flavour: no update check and no permission to install packages (ADR 0008).

- Privacy policy URL: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en (French: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr)
- Contact email: info-rann-apps@NorthMail.ca
- Category: Finance. Price: free (decided in the SRS, 16.2).
- App icon: `branding/store/play-icon-512.png`. Feature graphic: `branding/store/play-feature-1024x500.png`.
- Phone screenshots: `docs/store/screenshots/phone-en/` and `phone-fr/` (1080 × 2160)

## English (en-CA)

### App name (max 30)

RANN's Roost Mobile

### Short description (max 80)

Snap receipts and bills for RANN's Roost on your computer. Private, encrypted.

### Full description (max 4,000)

RANN's Roost Mobile is the free companion to RANN's Roost, the household finance app for Windows and Linux. It needs RANN's Roost on your computer.

CAPTURE ON THE SPOT
• Photograph a receipt or a bill: the edges are found, the page is straightened and the text is read on your phone.
• Several pages become one document; you can also share a PDF or photos from another app.
• Note a quick expense, or the odometer of a car or the hours of a boat or RV.

SENT TO YOUR COMPUTER, NOT TO THE CLOUD
• Pair the phone once by scanning a code shown on your computer.
• Captures wait on the phone, encrypted, and go straight to your computer over your home Wi-Fi, encrypted end to end. Nothing goes through RANN or anyone else.
• Each capture lands in your review inbox on the computer, ready to match with a statement line.

AT A GLANCE
• This month's budgets, bills coming due and maintenance due for your vehicles and home, sent by your computer.
• Bill and maintenance reminders on the phone.

PRIVATE
• Locked by a PIN, with fingerprint or face unlock if you wish.
• No RANN account, no advertising, no analytics. Text is read on the phone by Google's ML Kit.
• Every member of the household can pair their own phone; their captures go to their own inbox.

In English and French, for every province and territory.

## Français (fr-CA)

### Nom de l'application (max 30)

RANN's Roost Mobile

### Brève description (max 80)

Capturez reçus et factures pour RANN's Roost sur votre ordinateur. Chiffré.

### Description complète (max 4 000)

RANN's Roost Mobile est le compagnon gratuit de RANN's Roost, l'application de finances du ménage pour Windows et Linux. Il nécessite RANN's Roost sur votre ordinateur.

CAPTUREZ SUR LE MOMENT
• Photographiez un reçu ou une facture : les bords sont détectés, la page est redressée et le texte est lu sur votre téléphone.
• Plusieurs pages forment un seul document; vous pouvez aussi partager un PDF ou des photos depuis une autre application.
• Notez une dépense rapide, ou le compteur kilométrique d'une auto ou les heures d'un bateau ou d'un VR.

ENVOYÉ À VOTRE ORDINATEUR, PAS AU NUAGE
• Jumelez le téléphone une seule fois en numérisant un code affiché sur votre ordinateur.
• Les captures attendent sur le téléphone, chiffrées, et vont directement à votre ordinateur par le Wi-Fi de la maison, chiffrées de bout en bout. Rien ne passe par RANN ni par qui que ce soit.
• Chaque capture arrive dans votre boîte de révision sur l'ordinateur, prête à être jumelée à une ligne de relevé.

EN UN COUP D'ŒIL
• Les budgets du mois, les factures à payer et l'entretien à faire sur vos véhicules et votre maison, envoyés par votre ordinateur.
• Rappels de factures et d'entretien sur le téléphone.

CONFIDENTIEL
• Verrouillé par un NIP, avec déverrouillage par empreinte ou visage si vous le souhaitez.
• Pas de compte RANN, pas de publicité, pas d'outil d'analyse. Le texte est lu sur le téléphone par ML Kit de Google.
• Chaque membre du ménage peut jumeler son propre téléphone; ses captures vont dans sa propre boîte de révision.

En français et en anglais, pour toutes les provinces et tous les territoires.

## Data safety form

Answers for Play Console, following the owner's decision (2026-10-03) to keep Google's ML Kit and
declare its diagnostics. Google's guidance: https://developers.google.com/ml-kit/android-data-disclosure

- **Does the app collect or share user data?** Yes, through Google's ML Kit only. RANN itself collects and shares nothing.
- **Data types to declare (collected, not shared):**
  - App info and performance → *Diagnostics* (ML Kit's performance metrics, event types and error codes).
  - Device or other IDs → *Device or other IDs* (ML Kit's per-installation identifier).
- **For each type:**
  - Purpose: *Analytics*.
  - Processed ephemerally: no.
  - Collection required: yes, since it cannot be turned off.
- **Encrypted in transit:** yes (ML Kit uses HTTPS; the phone-to-computer transfer is encrypted end to end).
- **Can users request deletion?** RANN holds no user data. Everything the app keeps is on the phone and is deleted when the app is uninstalled. Answer "No" to providing a way to request deletion, and explain this in the description field if Play Console asks.
- **Not collected:** photos, files and documents. They are read on the phone and go only to the user's own paired computer, which is not collection by the developer.
- **Not collected:** financial information, personal identifiers, location and contacts.

Check Google's page above when filling in the form: it lists the exact categories for each ML Kit API, and Google may update it.
