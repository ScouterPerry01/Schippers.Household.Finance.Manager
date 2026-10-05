# The user manual: format and writing rules

The manual is the complete book about RANN's Roost: every screen, every field and every option, and what each one changes. It opens in its own window (Manual button, or Shift+F1 for the chapter about the screen shown). The short Help panel (F1, `hfm/help`) stays as the quick answer; the manual is the full reference.

## Files

- `core/i18n/src/main/resources/hfm/manual/<lang>/contents.txt`: the parts and chapters in reading order. A line `= part-id Part title` starts a part; each following line is a chapter id. Lines starting with `//` are comments.
- `core/i18n/src/main/resources/hfm/manual/<lang>/<chapter-id>.md`: one chapter. English is `en`, French is `fr`. Both languages have the same chapters, in the same order, with the same section ids.
- A chapter whose id is a screen's help id (the `Section` name in lower case: `bills`, `accounts`, `ai`...) is the chapter opened from that screen.
- Code: `ca.schippers.hfm.i18n.Manual` (parser, index, search) and `ManualWindow.kt` in the desktop app. `ManualTest` checks both languages.

## Markdown subset

- `# Chapter title`: once, on the first line.
- `## Section title {#section-id}` and `### Subsection title {#subsection-id}`: every heading carries an explicit id, lower case letters, digits and hyphens, unique in the chapter, and **the same in English and French** (ids are made from the English title). Sections and subsections are the branches of the expandable table of contents, so give each screen, tab, dialog and form its own heading.
- Plain paragraphs, separated by a blank line.
- `- bullet`; `  - second level` (two spaces).
- `1. step`, `2. step`: numbered steps for a task.
- `- **Field name**: what it is, what to enter, and what it changes.` A field or option. The name is exactly the label on screen, and it goes into the index automatically. A line indented two spaces continues the item.
- Callouts: a line starting `> Tip: `, `> Note: ` or `> Important: ` (French: `> Conseil : `, `> Remarque : `, `> Important : `). Further `> ` lines continue it.
- `@index: term; other term; synonym` on its own line under a heading: extra index entries for that heading (concepts, abbreviations, synonyms people search for, such as RRSP, TFSA, T4, cheque). Separate with semicolons. Field names need no `@index`; they are indexed already.
- Inline: `**bold**` for labels of buttons, tabs and menus in running text; links `[words](chapter-id)` or `[words](chapter-id#section-id)`. Every link must point to a chapter and section that exist (the test checks).
- Nothing else: no tables, no images, no HTML, no code blocks, no italics, no `####`.

## Writing rules

- Write for a household, not an accountant: plain words, short sentences, Canadian spelling (cheque, colour, centre, categorize).
- Use the exact labels the user sees. English labels are in `core/i18n/src/main/resources/hfm/i18n/messages_en.properties`, French in `messages_fr.properties`; the screen code in `app/desktop/src/main/kotlin/ca/schippers/hfm/desktop/` shows which labels appear where. In the properties files `''` is one apostrophe and `{0}` is a value filled in.
- Describe every field and option on every screen, tab, form and dialog. For each, say what it means, what to enter (format, units, examples), whether it is required, the default, and above all what it affects: which totals, reports, reminders, calculations, tax amounts, other screens or the phone use it, and what happens when it is left empty or changed later.
- Describe every button and action: what it does, what it asks first, whether it can be undone.
- Say who can do it when permissions matter (administrator, member, read-only).
- Explain the Canadian rules the screen applies (for example RRSP deadlines, TFSA room, the medical expense credit) in a sentence or two, without legal advice.
- Never invent behaviour. When the code does not make it clear, describe only what it shows. Do not mention requirement ids (BILL-04), class names or the code.
- French is a full translation in Canadian French with the app's French labels, not a summary. Use French typography as the app's French texts do: a space before `:` and `;` (none before `?` and `!`), « guillemets » if quoting. Keep ids and link targets unchanged.
