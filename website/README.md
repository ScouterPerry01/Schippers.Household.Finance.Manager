# RANN's Roost pages for rann.ca

Content for the owner to publish on www.rann.ca, built in the Google Sites editor (owner's choice,
2026-10-04). Not part of the apps' build.

| File | Page |
|---|---|
| `rann-roost-en.md` | https://www.rann.ca/rann-apps/rann-roost |
| `rann-roost-fr.md` | https://www.rann.ca/rann-apps/rann-roost/rann-roost-fr |
| `privacy-policy-en.md` | https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en |
| `privacy-policy-fr.md` | https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr |
| `images/` | Upload to Google Drive, then insert with Insert > Images > Select from Drive |

How to use them:
- Paste the text into Sites text boxes. Markdown is not read by Sites, so set headings and lists with its toolbar: `#` lines are titles, `##` headings, `###` subheadings, `-` lines bullets.
- Lines in [square brackets] in the home pages are instructions (images, layout, links), not text.
- Google Sites has no tables, so the policies use lists only.
- Give each image the alt text written beside it (image menu > Add alt text), for screen readers.

To keep in mind:
- The apps (About on the desktop, Settings on the phone) and the store listings link to the two privacy policy addresses above, so they must stay stable. If they change, update `AboutScreen.kt`, the phone's `strings.xml` and `docs/store/*.md`.
- Replace "Coming soon" with each store's link once the app is published there.
- The English screenshots will be replaced when the English demo becomes an Ontario family with English names.
- The privacy policy covers the apps. If the rann.ca site itself uses cookies or analytics (Google Sites can add Google Analytics), the site needs its own notice.
- The logo, icon and screenshots are © Perry Schippers, trading as RANN, and are not covered by the GPL (see `branding/README.md`).
