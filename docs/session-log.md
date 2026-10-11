<!--
File path and name: docs/session-log.md
Modified On Timestamp: 2026-10-10 @ 21:15 EDT
Created On Timestamp: 2026-10-09 @ 08:14 EDT
File Description: Append-only session history, newest first.
Uses: N/A (historical record).
Used By: Claude Code on demand; the end-session skill adds a block at the top.
Purpose: Preserve what happened, session by session, without paying for it in context.
-->

# RANN's Roost — Session log

**Append-only. Add each new session as a block at the TOP (just below this header). Do not rewrite or condense
earlier blocks. Keep each block to ~5-10 lines - detail belongs in that day's handover file.**

When this file passes ~40 KB, move it to `docs/archive/session-log-through-<date>.md` and start a fresh one.
Earlier history (before 2026-10-09) is in git log and `docs/development-plan.md`.

---

## 2026-10-09 08:20 -> 2026-10-10 21:15 EDT - Release 1.0.0, Store listing and assets, trailers

- Shipped: `v1.0.0` (`f20b66e2`, GitHub release still a draft); repository made public; `a7d294a6` Store
  captions and alt text, retaken screenshots, Store logos in `branding/store/` (`make_store_logos.py`).
- Outside the repository: 15 trailers (8 EN, 7 FR, Azure voices Liam and Thierry) in the owner's Downloads.
- Phone app 1.0.0 is in Google Play closed testing (Rann.Roost.Mobile).
- Decisions: 1.0.0 is the first Microsoft Store submission; trailer 08 (phone) stays off the Microsoft Store.
- Builds: CI green on `main` (Windows, Ubuntu, performance); this last commit is
  documentation only (no build run). Detail: `docs/session-handover-2026-10-10.md`.

## 2026-10-09 07:50 -> 08:20 EDT - Claude Code context set up

- Ran `organize-project-context` (repo had no CLAUDE.md): added CLAUDE.md, docs/NOW.md, this log,
  `.claude/settings.json` deny list, `end-session` skill, `test-runner` agent; `.gitignore` ignores
  `.claude/worktrees/` and `.claude/settings.local.json`.
- Branch `docs/context-slim` from `f18f6e12`; documentation only, no source/test/CI change; no build run.
