# Walk-Me guides: format and writing rules

A Walk-Me guide walks the user through one common task, step by step, in a panel docked on the right of the main window. Each step says where to go and what to do; **Show me** opens the right screen (and tab), the control to use is outlined on screen, and a step can move on by itself once the app sees it done. Guides are plain text files, so new ones can be added without touching the code.

## Files

- `core/i18n/src/main/resources/hfm/walkme/<lang>/contents.txt`: the groups and their guides, in the order of the Walk-Me screen. A line `= group-id Group title` starts a group; each following line is a guide id. Lines starting with `//` are comments.
- `core/i18n/src/main/resources/hfm/walkme/<lang>/<guide-id>.md`: one guide. English is `en`, French is `fr`. Both languages have the same groups, guides and steps, in the same order.
- Code: `ca.schippers.hfm.i18n.WalkMe` (parser) in core, `WalkMePanel.kt` (panel, list, targets) in the desktop app. `WalkMeGuidesTest` checks every guide in both languages.

## A guide

```
# Pay a bill in part
@about: Pay part of a bill now and the rest later, and see what is still due.

## Type the amount you pay now {#amount}
@screen: BILLS BillsTab.AGENDA
@target: bills.toPay
@done: shown bills.pay
@manual: bills#pay-in-part

On the bill's line, the **To pay** column starts at what is still outstanding.
Type the part you pay now, such as half.
```

- `# Title`: once, first line. `@about:` one line saying what the guide does; it is shown in the list.
- `## Step title {#step-id}`: one per step. The id is lower case letters, digits and hyphens, unique in the guide and the same in both languages.
- Under each step, these lines, all optional, the same in both languages:
  - `@screen: SECTION [TabEnum.NAME]`: the screen **Show me** opens: a menu section's name (`BILLS`, `ACCOUNTS`, `TAXES`... the `Section` enum), or `WELCOME` for the welcome screen before a household is open. A second word selects a tab, named by its enum and value (`BillsTab.ALL`, `TaxesTab.ESTIMATE`, `ReportKind.CUSTOM`); the screen marks its tabs with `Modifier.walkTab`.
  - `@target: id`: the control outlined on screen (a pulsing frame) and scrolled into view. The id is marked in the code with `Modifier.walkTarget("id")`, or is a dialog's `walkId` (`FormDialog(..., walkId = "bill.dialog")`, whose Save button is then `bill.dialog.save`), or a list's `addTarget`.
  - `@done: condition`: when the step counts as done; the panel then moves to the next step by itself, after a moment. Only a change while the step is shown counts, so going back to a step never skips it. The conditions:
    - `screen`: the step's screen (and tab) is the one shown.
    - `shown id`: a control or dialog with that id is on screen, such as a dialog that opened.
    - `added kind`: one more record of that kind than when the step was shown: `institution`, `account`, `member`, `user`, `bill`, `budget`, `goal`, `phone`, `document`, `filed` (a document filed out of To review), `statement`, `reconciled`, `backup`, `contact`, `vehicle` (the `WALK_KINDS` list in `WalkMePanel.kt`).
    - `open`: a household is open (after creating or unlocking one).
  - `@manual: chapter#section`: the manual page the step's link opens.
- The step's text follows, in the manual's Markdown subset (docs/manual-format.md): paragraphs, `- ` bullets, `1. ` numbered lines, `**bold**`, links.

## Writing rules

- Say where to go, what to click and what to type, in the order the user does it. A step does one thing, or fills one part of a form.
- Quote every button, field, tab and menu exactly as on screen, in **bold**: English labels are in `messages_en.properties`, French in `messages_fr.properties`, the phone's in `app/android/src/main/res/values*/strings.xml`. The test fails on a bold label the app does not show (a trailing … or : may be left out; for a label with a value, such as `To review ({0})`, the words around the value count).
- Steps done on the phone (pairing, capturing, trips, the seasonal checklist) are described in words, beginning with "On the phone"; they have no screen, target or condition, since the computer cannot point at the phone.
- Do not invent behaviour: check the screen's code and the manual. Keep each guide to about five to eight steps, with a manual link where the user may want more.
- French: Canadian French with the app's French labels, typographic apostrophes and French spacing, as in the manual.

## Adding a guide

1. Write `en/<id>.md` and `fr/<id>.md`, and list the id in both `contents.txt`.
2. Mark any new control with `Modifier.walkTarget("area.what")` (or a dialog's `walkId`), and any new tab row with `Modifier.walkTab(tab, selected) { select it }`.
3. A new kind of record for `added` goes into `WALK_KINDS`.
4. Run `./gradlew :app:desktop:test --tests "*WalkMeGuidesTest*"`.

## Progress

Each guide's step is remembered on this computer (Java preferences `walkme.step.<id>`), so a guide closed halfway resumes there; a guide followed to the end starts over next time and shows "followed to the end" in the list. The panel's width is remembered too. Nothing about Walk-Me is kept in the household.
