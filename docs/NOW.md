<!--
File path and name: docs/NOW.md
Modified On Timestamp: 2026-10-10 @ 21:40 EDT
Created On Timestamp: 2026-10-09 @ 08:14 EDT
File Description: The ONE short "where are we right now" file. Read this first when resuming.
Uses: docs/session-log.md, the current handover file.
Used By: Claude Code at the start of every session; the end-session skill rewrites it.
Purpose: Keep current state out of the always-loaded CLAUDE.md.
-->

# NOW — RANN's Roost

> **This file is OVERWRITTEN at the end of every session, never appended to. Keep it under ~40 lines.**
> History goes in `docs/session-log.md`; detail in the day's handover file.

**As of:** 2026-10-10 21:40 EDT

**Branch:** `main`, pushed. Tag `v1.0.0` (`f20b66e2`). The repository is public. The phone app
(Rann.Roost.Mobile, also 1.0.0) is in **closed testing** on Google Play.

**Just finished:** release 1.0.0, **published** on GitHub 2026-10-10 21:30 EDT (Linux packages,
signed `SHA256SUMS` and `update.json`; update checks now see 1.0.0). First Microsoft Store submission
sent and **in certification**. rann.ca updated by the owner. Store listing captions and alt text,
retaken screenshots, Store logos (`branding/store/`), 15 trailers (8 EN, 7 FR, Azure neural voices)
kept outside the repository. Detail: `docs/session-handover-2026-10-10.md`.

**NEXT (in order):**
1. Microsoft Store certification result (usually one to three business days): fix anything it
   reports, then check the live listing in English and French.
2. Owner: keep the 12+ Play closed testers opted in for 14 days in a row, then apply for production
   (Rann.Roost.Mobile `docs/release-checklist.md` § 3).

**Waiting on the developer:** the certification result and the closed test.

**To resume, read ONLY:** this file; `docs/session-handover-2026-10-10.md` § 3 (open items) if needed.

**Standing state:** no Android code in this repository. Public 1.0 = end of Phase 5.
