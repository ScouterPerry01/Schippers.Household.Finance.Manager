# RANN's Roost pages for rann.ca

Content for the owner to publish on www.rann.ca. Not part of the apps' build.

| File | Publish at |
|---|---|
| `rann-roost-en.html` | https://www.rann.ca/rann-apps/rann-roost |
| `rann-roost-fr.html` | https://www.rann.ca/rann-apps/rann-roost/rann-roost-fr |
| `privacy-policy-en.md` | https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en |
| `privacy-policy-fr.md` | https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr |
| `images/` | Beside the home pages: they refer to `images/<file>` |

- The apps (About on the desktop, Settings on the phone) and the store listings link to the two privacy policy addresses above, so they must stay stable. Change them in `AboutScreen.kt`, the phone's `strings.xml` and `docs/store/*.md` if the addresses change.
- The home pages say "Coming soon" where the Microsoft Store, Linux and Google Play links will go; replace those once each is published.
- The English screenshots will be replaced when the English demo becomes an Ontario family with English names.
- The privacy policy covers the apps. If the rann.ca website itself uses cookies or analytics, it needs its own notice.
- The logo, icon and screenshots are © Perry Schippers, trading as RANN, and are not covered by the GPL (see `branding/README.md`).
