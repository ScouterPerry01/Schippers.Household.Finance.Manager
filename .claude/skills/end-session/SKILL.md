---
name: end-session
description: "End-of-session routine for RANN's Roost - overwrite docs/NOW.md, add a short block to docs/session-log.md, write the day's handover. Use when the owner says wrap up, end the session, write the handover or update the docs."
---
<!--
File path and name: .claude/skills/end-session/SKILL.md
Modified On Timestamp: 2026-10-09 @ 08:14 EDT
Created On Timestamp: 2026-10-09 @ 08:14 EDT
File Description: Project version of ~/.claude/skills/wrap-up-session for RANN's Roost.
Uses: docs/NOW.md, docs/session-log.md, docs/session-handover-<date>.md, CLAUDE.md.
Used By: Claude Code at the end of a working session in this repository.
Purpose: Keep the read-first files small so every future session starts cheaply.
-->

# End the session (RANN's Roost)

Do these in order. Read only the top of each file you edit (offset/limit), never a whole large file.

1. **`docs/NOW.md` - OVERWRITE it.** Under ~40 lines: as-of date, branch + commit (and whether `main` is
   pushed), what just finished, NEXT, what waits on the owner, and exactly which handover sections to read
   to resume.
2. **`docs/session-log.md` - add ONE block at the top**, 5-10 lines:
   `## YYYY-MM-DD HH:MM -> HH:MM EDT - <title>`, then what shipped (with tags), decisions, and exactly how the
   build/tests were run (for example `./gradlew build`, totals from the test XML). If the file is over
   ~40 KB, first `git mv` it to `docs/archive/session-log-through-<date>.md` and start a fresh one.
3. **Handover detail goes in today's file**, `docs/session-handover-<YYYY-MM-DD>.md` (standard file header,
   numbered sections). Never add sections to an older day's handover.
4. **Decisions taken while the owner was away:** list them in the handover and in the report, so the owner can
   overturn any; dated decisions also go in `docs/development-plan.md` § Decisions log.
5. **`CLAUDE.md`: leave it alone** unless a durable rule, convention, guardrail or architecture decision
   changed. Never put progress, counts, schema versions, dates of completed work or "NEXT" in it. If you
   change it, say so.
6. Update the "Modified On Timestamp" of every file you touched, show the diff, and **wait for approval
   before committing**.
