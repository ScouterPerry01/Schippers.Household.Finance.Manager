<!--
File path and name: docs/session-handover-2026-10-10.md
Modified On Timestamp: 2026-10-10 @ 21:15 EDT
Created On Timestamp: 2026-10-10 @ 21:15 EDT
File Description: Handover for the sessions of 2026-10-09 and 2026-10-10 (release 1.0.0, store assets, trailers).
Uses: docs/NOW.md, docs/session-log.md, docs/release-checklist.md, docs/store/.
Used By: Claude Code and the owner when resuming.
Purpose: Keep the detail that does not belong in NOW.md or the session log.
-->

# Session handover — 2026-10-10

## 1. What shipped

- **1.0.0:** tagged in both repositories (`v1.0.0`; desktop `f20b66e2`, phone `b22d727`). The desktop
  release workflow built every package; the GitHub release is still a **draft**. The MSIX is the `msix`
  artifact of run 37953543559.
- **Microsoft Store listing** (`a7d294a6`): `docs/store/microsoft-store.md` notes that 1.0.0 is the first
  submission (nothing was published on the Store before); every screenshot has a caption (max 200) and
  alt text (max 250) in English and French; the 16 desktop screenshots were retaken.
- **Store logos:** `branding/store/` (poster 1440x2160, box 2160x2160, tiles 300/150/71, super hero art
  3840x2160 and 1920x1080 without lettering), made by `tools/dev/make_store_logos.py`.
- **Phone app:** in Google Play **closed testing** (set up by the owner on 2026-10-10) with the signed
  bundle from the tag's Release run 37972874927 (`ranns-roost-mobile-1.0.0.aab`, version code 10000).
  Play's "native code without debug symbols" warning is expected (Google's ML Kit and AndroidX
  libraries, already stripped).

## 2. Trailers (not in the repository)

- 15 MP4s, 1920x1080, H.264 50 Mbps CBR, AAC 48 kHz stereo, about -16 LUFS, all under 60 s, in the
  owner's `Downloads\rann-trailers\en` (01 to 08) and `fr` (01 to 07), each with a thumbnail PNG, a `.vtt`
  and a title text. Live recordings of the demo household (desktop) and the emulator (phone).
- Narration: Azure AI Speech, voices en-CA Liam and fr-CA Thierry, Free F0 resource in resource group
  `rann-tools` (Canada Central). The key is in the owner's user environment (`AZURE_SPEECH_KEY`), never
  in a session. Script and narration text: `Downloads\rann-trailers\narration\` (`azure-voice.ps1`,
  `storyboard.json`).
- The recording and assembly scripts were session scratch files and were not kept; rebuilding the
  trailers means re-recording.
- Trailer 08 (phone companion) is for rann.ca or Google Play (YouTube link only), not the Microsoft Store.

## 3. Open items

- Owner: publish the draft GitHub release; first Microsoft Store submission; 14-day Play closed test,
  then production; rann.ca updates.
- Known in the trailers: the phone summary shows investment accounts at cash value; the emulator clock
  reads about 4 a.m.
- Done by the owner: the three unused `ANDROID_*` secrets were removed from this repository; the leftover
  `app/desktop/build/phone-stop` is gone.
