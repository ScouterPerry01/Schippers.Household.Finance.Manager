<!--
File path and name: docs/NOW.md
Modified On Timestamp: 2026-10-09 @ 08:14 EDT
Created On Timestamp: 2026-10-09 @ 08:14 EDT
File Description: The ONE short "where are we right now" file. Read this first when resuming.
Uses: docs/session-log.md, the current handover file.
Used By: Claude Code at the start of every session; the end-session skill rewrites it.
Purpose: Keep current state out of the always-loaded CLAUDE.md.
-->

# NOW — RANN's Roost

> **This file is OVERWRITTEN at the end of every session, never appended to. Keep it under ~40 lines.**
> History goes in `docs/session-log.md`; detail in the day's handover file.

**As of:** 2026-10-09 morning

**Branch:** `docs/context-slim` (Claude Code context setup, not yet committed). `main` = `f18f6e12` locally —
the rewritten history (phone app removed, noreply email), **not yet pushed**.

**Just finished:** Phone app moved to Rann.Roost.Mobile; history rewritten; Store prices set; Claude Code
context set up (CLAUDE.md, this file, session log, deny list, end-session skill, test-runner agent).

**NEXT (in order):**
1. Owner force-pushes the rewritten `main` (`git push --force origin main`, then `git branch -u origin/main`)
   and ticks GitHub Settings > Emails "Keep my email addresses private" and "Block command line pushes that
   expose my email".
2. Push/merge `docs/context-slim` after the force-push.
3. Make the repository public (owner approved 2026-10-09; history scanned, no secrets).
4. Check Rann.Roost.Mobile CI goes green (it can fetch the submodule once this repo is public); release dry
   runs in both repositories.

**Waiting on the developer:** step 1 (force-push and GitHub email settings).

**To resume, read ONLY:** this file; `docs/development-plan.md` § Decisions log (search for it) if a decision
is needed.

**Standing state:** no Android code in this repository. Public 1.0 = end of Phase 5.
