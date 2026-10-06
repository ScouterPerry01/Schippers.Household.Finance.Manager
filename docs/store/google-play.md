# Google Play listing: RANN's Roost Mobile

Texts for Play Console (DIST-03, DIST-07), checked by `tools/dev/check_store_texts.py`. The Play
build is the `play` flavour: no update check and no permission to install packages (ADR 0008).

- Privacy policy URL: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en (French: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr)
- Contact email: info-rann-apps@NorthMail.ca
- Category: Finance. Price: free (decided in the SRS, 16.2).
- App icon: `branding/store/play-icon-512.png`. Feature graphic: `branding/store/play-feature-1024x500.png`.
- Phone screenshots: `docs/store/screenshots/phone-en/` and `phone-fr/` (1080 × 2160, within Play's 2:1 limit; five per language: capture, summary, Coming up with refills, contacts, sent), taken on the emulator from the sample household (`tools/dev/README.md`).

## English (en-CA)

### App name (max 30)

RANN's Roost Mobile

### Short description (max 80)

Snap receipts, see what's due and get reminders, for RANN's Roost. Encrypted.

### Full description (max 4,000)

RANN's Roost Mobile is the free companion to RANN's Roost, the household finance app for Windows and Linux. It needs RANN's Roost on your computer.

CAPTURE ON THE SPOT
• Photograph a receipt or a bill: the edges are found, the page is straightened and the text is read on your phone.
• Several pages become one document; you can also share PDFs, photos or an email's text from another app.
• Note a quick expense, or the odometer of a car or the hours of a boat or RV.
• Dictate a note, or record a short voice note that goes with the capture.
• Add a new contact on the spot: your computer shows it for review.

SENT TO YOUR COMPUTER, NOT TO OUR CLOUD
• Pair the phone once by scanning a code shown on your computer.
• Captures wait on the phone, encrypted, and go straight to your computer over your home Wi-Fi, encrypted end to end. Nothing goes through RANN or anyone else.
• Away from home, they can go through a cloud folder of your own (Google Drive, OneDrive, Dropbox or Nextcloud) or by email, sealed so that only your computer can open them.
• Each capture lands in your review inbox on the computer, ready to match with a statement line.

AT A GLANCE
• Your accounts, this month's budgets, bills coming due, appointments, medication refills and maintenance due, sent by your computer.
• The household's contacts: the doctor, the dentist, the bank or the contractor, with their phone numbers and addresses.
• Reminders for bills, maintenance, appointments and refills, and a notice when a budget reaches 80 % and 100 %.

PRIVATE
• Locked by a PIN, with fingerprint or face unlock if you wish.
• No RANN account, no advertising, no analytics. Text is read on the phone by Google's ML Kit.
• Every member of the household can pair their own phone; their captures go to their own inbox, and the phone shows only what they may see on the computer.

In English and French, for every province and territory.

## Français (fr-CA)

### Nom de l'application (max 30)

RANN's Roost Mobile

### Brève description (max 80)

Reçus, échéances et rappels pour RANN's Roost sur votre ordinateur. Chiffré.

### Description complète (max 4 000)

RANN's Roost Mobile est le compagnon gratuit de RANN's Roost, l'application de finances du ménage pour Windows et Linux. Il nécessite RANN's Roost sur votre ordinateur.

CAPTUREZ SUR LE MOMENT
• Photographiez un reçu ou une facture : les bords sont détectés, la page est redressée et le texte est lu sur votre téléphone.
• Plusieurs pages forment un seul document; vous pouvez aussi partager des PDF, des photos ou le texte d'un courriel depuis une autre application.
• Notez une dépense rapide, ou le compteur kilométrique d'une auto ou les heures d'un bateau ou d'un VR.
• Dictez une note, ou enregistrez une courte note vocale qui accompagne la capture.
• Ajoutez un nouveau contact sur le moment : votre ordinateur vous le présente pour vérification.

ENVOYÉ À VOTRE ORDINATEUR, PAS À NOTRE NUAGE
• Jumelez le téléphone une seule fois en numérisant un code affiché sur votre ordinateur.
• Les captures attendent sur le téléphone, chiffrées, et vont directement à votre ordinateur par le Wi-Fi de la maison, chiffrées de bout en bout. Rien ne passe par RANN ni par qui que ce soit.
• Loin de la maison, elles peuvent passer par un dossier infonuagique à vous (Google Drive, OneDrive, Dropbox ou Nextcloud) ou par courriel, scellées pour que seul votre ordinateur puisse les ouvrir.
• Chaque capture arrive dans votre boîte de révision sur l'ordinateur, prête à être jumelée à une ligne de relevé.

EN UN COUP D'ŒIL
• Vos comptes, les budgets du mois, les factures à payer, les rendez-vous, les renouvellements d'ordonnance et l'entretien à faire, envoyés par votre ordinateur.
• Les contacts du ménage : le médecin, le dentiste, la banque ou l'entrepreneur, avec leurs numéros de téléphone et leurs adresses.
• Rappels de factures, d'entretien, de rendez-vous et de renouvellements, et un avis quand un budget atteint 80 % et 100 %.

CONFIDENTIEL
• Verrouillé par un NIP, avec déverrouillage par empreinte ou visage si vous le souhaitez.
• Pas de compte RANN, pas de publicité, pas d'outil d'analyse. Le texte est lu sur le téléphone par ML Kit de Google.
• Chaque membre du ménage peut jumeler son propre téléphone; ses captures vont dans sa propre boîte de révision, et le téléphone ne montre que ce qu'il peut voir sur l'ordinateur.

En français et en anglais, pour toutes les provinces et tous les territoires.

## Data safety form

Answers for Play Console, following the owner's decision (2026-10-03) to keep Google's ML Kit and
declare its diagnostics. Google's guidance: https://developers.google.com/ml-kit/android-data-disclosure
and Play's definitions: https://support.google.com/googleplay/android-developer/answer/10787469

Reviewed on 2026-10-06 against what the phone app now sends and receives. Everything the app
exchanges with the household goes only to the user's own paired computer, sealed end to end
(ADR 0006): over the home network, or as a sealed file left in a cloud folder the user chooses or
shared by email. Play does not count end-to-end encrypted data as collected, and RANN's servers
never see it. That covers:

- From the phone: photos, PDFs and shared files, the text read from them, shared email text, quick
  expenses, odometer and hours readings, typed or dictated notes, voice notes (WAV audio), new
  contacts the user types, maintenance tasks ticked as done in the seasonal checklist (date,
  optional cost, reading and note), and what the log forms record (UTL-01, UTL-02, HRS-01,
  CHO-01, VOL-01): utility meter readings and fuel tank levels, hours worked for a client (date,
  start, time, task and note; the running timer stays on the phone), chores ticked as done, and
  volunteer hours (person, organization, kind, date, time and activity); trips (times, odometers,
  the places at each end: a saved place's name, or the coordinates of an unnamed end), fill-ups
  and charges, and the places saved on the phone with their coordinates.
- From the phone, only when the user turns on "Calendars on this phone" (CSY-01 to CSY-03): the
  items of the phone's calendars the user ticks (title, place, start and end; never descriptions,
  guests or reminders), for the days ahead chosen, read with `READ_CALENDAR` through Android's
  CalendarContract. The app never signs in to a calendar account and never writes to a calendar.
- To the phone: account balances, bills due, this month's budgets, maintenance due, the season's
  maintenance checklist (task and vehicle or asset names, due and done dates), what the log
  forms pick from (utility meters and fuel tanks with their last reading, side-income clients and
  their tasks, the children's chores with what each is worth, organizations volunteered for), calendar
  events (appointments), each person's work and school hours today and tomorrow, the saved places
  (name, category, address and coordinates) and trailers of the groups the user may see,
  medication refills and the contacts the user may see (names, kinds, phone numbers, emails,
  addresses, hours; never account or client numbers). Calendar items brought in from phones never
  go back to any phone.
- On the phone only: event, refill, bill, maintenance and budget reminders are scheduled and shown
  by the app itself (local notifications); nothing is sent to raise them.

The answers:

- **Does the app collect or share user data?** Yes, through Google's ML Kit only. RANN itself collects and shares nothing.
- **Data types to declare (collected, not shared):**
  - App info and performance → *Diagnostics* (ML Kit's performance metrics, event types and error codes).
  - Device or other IDs → *Device or other IDs* (ML Kit's per-installation identifier).
- **For each type:**
  - Purpose: *Analytics*.
  - Processed ephemerally: no.
  - Collection required: yes, since it cannot be turned off.
- **Is all of the user data collected by your app encrypted in transit?** Yes (ML Kit uses HTTPS; the phone-to-computer transfer is encrypted end to end).
- **Can users request deletion?** RANN holds no user data. Everything the app keeps is on the phone and is deleted when the app is uninstalled. Answer "No" to providing a way to request deletion, and explain this in the description field if Play Console asks.
- **Not collected (end-to-end encrypted, to the user's own computer):** photos and videos, files and documents, audio (voice notes), financial information (including hours worked, chores earned and utility readings), health information (medication refills, medical appointments), calendar events (those from the computer, and those read from the phone's own calendars when the user turns it on), the household's contacts, and location (precise and approximate: one fix at the start and end of a trip, or when saving a place, matched to saved places on the phone; only a place's name, or the coordinates of an unnamed end or a saved place, reach the user's computer). None of these is declared.
- **Not collected at all:** background location (the app has none), the phone's own address book (the app never reads it), app activity, web history and personal identifiers.

For the owner to confirm before submitting:

1. That the end-to-end encryption exception is applied as above, rather than declaring photos, files, audio, financial and health information, calendar events and contacts as "collected". Both RANN's computer program and the cloud folder or email only ever hold sealed files; if in doubt, declaring them (purpose: App functionality, not shared, optional where the user chooses to capture) is the cautious answer.
2. Dictation: **Dictate the note** hands over to Android's speech input (an intent to the phone's speech service, often Google's), which handles the voice under its own policy; the app receives only the words. Not declared, since the app itself sends no audio for it. The privacy policy (`website/privacy-policy-en.md` and `-fr.md`) says so under "The phone app and Google" (Dictation, added for 1.0.0).
3. Permissions Play will ask about: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` (trips, 2026-10-06: asked only when the user taps **Use my location**, **Add the place where I am** or **Find the nearest saved station**; foreground only, one fix at a time, no `ACCESS_BACKGROUND_LOCATION`, so no background location declaration is needed), `RECORD_AUDIO` (asked only when the user first records a voice note), `POST_NOTIFICATIONS` (reminders), `SCHEDULE_EXACT_ALARM` (reminders on the minute; off by default on Android 14 and later until the user allows it, so no exact-alarm declaration should be needed, but check the Play Console's questions on it), `RECEIVE_BOOT_COMPLETED` (reminders after a restart), `READ_CALENDAR` (asked only when the user turns on "Calendars on this phone" in Settings, after the app says why; only the calendars ticked are read, and only sent to the user's computer), `USE_BIOMETRIC` and network access. The Play build has no `REQUEST_INSTALL_PACKAGES`.
4. That Google's ML Kit page still lists the same categories for text recognition, the document scanner and the code scanner: Google may update it.
5. Location: the trip coordinates fall under the same end-to-end exception as the other data sent to the user's computer. If in doubt, the cautious answer is to declare *Location → Approximate location* and *Precise location* as collected, for *App functionality*, not shared, optional.
