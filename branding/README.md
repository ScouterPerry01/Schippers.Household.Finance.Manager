<!--
File path and name: branding/README.md
Modified On Timestamp: 2026-10-10 @ 02:26 EDT
Created On Timestamp: 2026-10-03 @ 08:07 EDT
File Description: What the branding folder holds and the licence of the name and logo.
Uses: the files in branding/.
Used By: anyone building or redistributing RANN's Roost.
Purpose: Keep the name and logo out of the GPL and say where each image is used.
-->

# RANN's Roost name and logo

© Perry Schippers, trading as RANN. All rights reserved.

The files in this folder, and the icons made from them elsewhere in the repository, are **not covered
by the GPL**. The licence of the source code (GPL-3.0-or-later, see `LICENSE`) does not grant any
right to the names "RANN", "RANN's Roost" and "RANN's Roost Mobile" or to this logo (GPL-3.0
section 7(e)).

You may build and run the application from this repository with them for your own use. If you
publish a modified version, or a build of your own, give it a different name and icon.

| Folder | Contents |
|---|---|
| `logo/` | The main logo: the kite with "RANN" and "ROOST" in white, and the kite alone, on a transparent background |
| `msix/Assets/` | Microsoft Store tiles and icons, packaged into the MSIX |
| `store/` | Microsoft Store logos: 2:3 poster art, 1:1 box art, 16:9 super hero art and the app tile icons (300, 150 and 71 px), drawn by `tools/dev/make_store_logos.py` |
| `desktop/` | Windows installer icon (`.ico`, 16 to 256 px) and Linux menu icon (512 px), made from the tiles |

Icons made from these files: `app/desktop/src/main/resources/hfm/branding/icon.png` (window, taskbar
and tray). The phone app's launcher icon (on the sky blue `#3D8FCB`), Google Play icon and feature
graphic are in its own repository, Rann.Roost.Mobile.

Questions: info-rann-apps@NorthMail.ca.
