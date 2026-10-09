<!--
File path and name: CLAUDE.md
Modified On Timestamp: 2026-10-09 @ 08:14 EDT
Created On Timestamp: 2026-10-09 @ 08:14 EDT
File Description: Project guidance for Claude Code. Holds ONLY durable instructions. Current state lives in
                 docs/NOW.md; history in docs/session-log.md.
Uses: docs/NOW.md, docs/*.md (read on demand), .claude/settings.json, .claude/skills/, .claude/agents/.
Used By: Claude Code when operating in this repository.
Purpose: Let each session continue the work correctly without relearning the project, at low context cost.
-->

# CLAUDE.md — RANN's Roost (desktop, core libraries, website text)

## Start here — and keep this file small

- **When resuming, read `docs/NOW.md` first.** It is the only "where are we" file and it is short.
- **This file is loaded on every turn, so it holds ONLY durable instructions.** Never add progress, dates of
  completed steps, counts, migration levels or "NEXT" here. At the end of a session use the **`end-session`
  skill**: it overwrites `docs/NOW.md`, adds the session block to `docs/session-log.md`, writes the day's
  handover, and leaves this file alone unless a durable rule changed.
- Never read a large file whole (`docs/development-plan.md` is ~100 KB, the SRS ~55 KB): search for the
  heading, then read that range.

## Project

RANN's Roost (formerly Household Finance Manager) is a personal and household finance app for Canada, in
English and French. This repository holds the **desktop app** (Windows and Linux, Compose Desktop), the shared
**core libraries** (plain Kotlin/JVM), release tooling and the text of the rann.ca pages. The **phone app lives
in a separate private repository, Rann.Roost.Mobile**, which builds this repo's core through a `roost`
submodule pinned to a commit here. Licence: GPL-3.0-or-later (except `branding/`).

## Environment

- JDK 21 (Temurin in CI). Gradle wrapper; Kotlin with warnings treated as errors (`build.gradle.kts`).
- CI: `.github/workflows/ci.yml` (tests + desktop jar on Windows and Ubuntu, plus the performance job);
  `release.yml` builds and signs releases from a tag `v<version>` (run by hand it is a dry run);
  `natives.yml` builds the Windows HEIC helper.
- Linux packaging can only be tested in CI (release dry run).
- Machine-specific setup (JDK path, emulator, phone pairing) is kept in Claude's local memory, not here.

## Solution layout (confirmed facts — avoid re-deriving)

- `core/money` exact `Money` (minor units) · `core/calc` all financial calculations and the built-in rates and
  rules (`src/main/resources/hfm/rules/*.rules`) · `core/domain` · `core/security` · `core/data` household
  format, vault, SQLDelight schemas and migrations · `core/data-jdbc` SQLCipher driver · `core/books`
  bookkeeping services · `core/i18n` EN/FR texts (`hfm/i18n/messages_en|fr.properties`) · `core/importers` ·
  `core/ocr`, `core/ocr-desktop` (PaddleOCR on ONNX) · `core/ai` (ADR 0009) · `core/sync` · `core/update`.
- `app/desktop` Compose Desktop app (and the phone listener) · `tools/release` keys and signing ·
  `tools/natives` Windows HEIC helper · `tools/dev` dev helper scripts (see its README) · `website/` rann.ca
  text · `branding/` logos and Store assets · `spikes/` Phase 0 experiments, not built.
- Migrations: `core/data/src/main/sqldelight/<ledger|core>/ca/schippers/hfm/data/<db>/<n>.sqm`; schema
  snapshots in `core/data/src/main/sqldelight/*/schemas/*.db` (one version behind; never edit them by hand).
- Architecture decisions: `docs/adr/`. Requirements: the SRS and `docs/requirements-additions.md`.

## Build and test

- Everything (run before every commit): `./gradlew build` — hand long runs to the `test-runner` agent.
- Core tests: `./gradlew test` · one module: `./gradlew :core:books:test`
- Performance (NFR-02, run when touching report queries or net worth): `./gradlew :core:books:performanceTest`
- Demo: `./gradlew :app:desktop:runDemo -Plang=en [-Psection=<SECTION>]` (a throw-away household each run).
- Manual / Store pictures: `./gradlew :app:desktop:manualScreenshots -Plang=en` (then `fr`), `storeScreenshots`.
- More commands: `README.md` § Building and `tools/dev/README.md`.

## Code rules

- Kotlin official style. Money is always `ca.schippers.hfm.money.Money`; never `Double`/`Float` for amounts.
- Every financial calculation lives in `core/calc`, tested against independent reference figures.
- Every user-facing string goes through `core/i18n`, in English **and** French; in `.properties` files write
  each apostrophe as `''` (MessagesTest fails otherwise); EN and FR key sets must match.
- Each rate rule needs `rateRule.<key>` and `.hint` texts in EN and FR, and an official source per value.
- Database changes only through new SQLDelight migrations (`.sqm`), additions only (no destructive table
  rebuild). Snapshot the current schema (`generateMain<Ledger|Core>DatabaseSchema`) BEFORE editing `.sq` files.
- Never `INSERT OR REPLACE` on a table referenced with ON DELETE CASCADE: use
  `INSERT ... ON CONFLICT(id) DO UPDATE` (`SaveKeepsChildrenTest` guards it).

## Working cadence

- One batch of risk at a time; build + test green before moving on. Review the diff before staging.
- Before committing: full `./gradlew build`, total the test results, and reread the last lines of every new
  file for leftover `Suppress("unused")` / `UNUSED_*` / `private fun unused(...)` junk (it has slipped in
  several times).
- Parallel agents: only one changes the schema per round unless each uses the same next migration file on
  disjoint tables; merge and build each branch before merging the next.
- Do not recompile `app/desktop` while a demo runs from build/classes (it breaks the demo midway).
- Windows CI after a change to `core:ocr` can take ~17 min without cache: not a hang.
- Write files containing quotes, apostrophes or backslashes with the Write/Edit tools, not shell here-documents.
- English first: demos and screenshots default to English; French is the second check.

## Guardrails (require human approval / never do)

- The personal guardrails in `~/.claude/CLAUDE.md` always apply (approval before commit/push/branch work).
- **Never re-add Android/phone code here** — it belongs to Rann.Roost.Mobile (not GPL, sold on Google Play).
- Keys, passwords, keystores and `gh secret set`: give the owner commands to run in **their own separate
  PowerShell window**; never via `!` in the session; never read or handle the values. Only the public
  `core/update/src/main/resources/hfm/update/release-key.pub` is committed.
- Never ask for a real Anthropic key; use `tools/dev/fake_anthropic.py` for AI reading in the demo.
- Never commit household data (`*.hfm/`, `*.hfmbak`) or real financial documents.
- Screenshots capture only the app's own window, and are checked by eye for anything private.
- Website text (`website/`) must paste into Google Sites: headings and lists only, no tables, no HTML; keep the
  four rann.ca page addresses stable (the apps and store listings link to them).
- Branch policy: work on a topic branch; `main` is updated after green CI with the owner's approval.

## Architecture & key decisions

- Durable decisions are ADRs in `docs/adr/` (0001 stack … 0010 rates and rules); the dated decisions log is
  `docs/development-plan.md` § Decisions log. Add an ADR rather than describing a decision here.

## Where the rest of the context lives

Read these on demand — they are NOT imported, so they cost nothing until needed.

| File | Contents |
| --- | --- |
| `docs/NOW.md` | **Read first.** Current step, next step, branch, what waits on the developer. Overwritten each session. |
| `docs/session-log.md` | Session history, newest first. Add a short block at the top each session. |
| `docs/development-plan.md` | Phases, stack, risks, decisions log (large: search headings). |
| `docs/Household Finance Manager — Software Requirements Specification.md` | The SRS (large: search). |
| `docs/adr/` | Architecture decision records. |
| `docs/release-checklist.md` | Release steps; `docs/releases/` release notes. |
| `docs/store/` | Store listing texts and screenshots (check with `tools/dev/check_store_texts.py`). |
| `docs/manual-format.md`, `docs/walkme-format.md` | Formats of the in-app manual and guided tours. |
| `tools/dev/README.md` | Dev helpers, screenshots, phone pairing for tests. |
| `.claude/skills/end-session/` | End-of-session routine. |
| `.claude/agents/test-runner.md` | Runs the Gradle build/tests and reports a short summary. |
