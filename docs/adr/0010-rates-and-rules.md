# ADR 0010: Rates and rules

Status: Accepted (2026-10-05). Built: the engine and core table, the Rates and rules screen, and every area's figures (sales taxes, income tax, registered plans, investments, medical, lead times, thresholds, password rules, bank holidays).

## Context

- Owner's decisions (2026-10-05): cover sales taxes and federal and provincial income tax for all 13 provinces and territories; estimate income tax per person; let an administrator set the password rules; ship verified official figures; and make every rate, limit, threshold and lead time definable by the user, with the date it takes effect.
- Before this, such figures were constants in the code (TFSA limits, dividend rates, the CRA medical amount, RRIF factors, reminder lead times, the 12-character password), changed only by a new release.
- Rates change on known dates, often differently by province, and old transactions must keep the rate of their own date.

## Decision

**One mechanism for every figure.** A figure is a named rule (`tfsa.limit`, `sales.hst`, `tax.prov.brackets`, `security.password.minLength`...) with a type (rate, amount, number, days, brackets, month and day, yes or no, list), an area, and whether it differs by province. Code asks `Rules` for a rule's value on a date, for a province: the province's own value when it has one, otherwise the value for everywhere; of the values in effect, the latest date wins, and on the same date the household's value wins over the built-in one.

**Built-in values ship as text.** `core/calc/src/main/resources/hfm/rules/<area>.rules`, one file per area listed in `index.txt`; each value line has the key, province, effective date, value and its official source. Readable and reviewable, no database needed, and each area can be updated on its own. `RulesTest` checks that every value fits its rule and every rule has a value.

**The household's values are kept in its core database** (`rule_value`, core version 8, an addition only), added by an administrator on the Rates and rules screen with a date, a province or all, and a note; they take the built-in value's place from their date and can be removed, leaving the built-in history. They are loaded into `Rules` when the household opens and after each change, as the household's province is for bank holidays.

**Shared figures are one rule.** A figure used by two features is a single rule (the federal medical threshold and dividend credits serve both the slips and the income tax estimate), so one change reaches both.

**Texts.** Every rule has a name and an explanation in both languages (`rateRule.<key>`, `rateRule.<key>.hint`), checked by `RateRuleTextsTest`.

## Consequences

- A new year's limits or a budget's tax change can be entered by the user the day it is announced, without waiting for a release; a release still updates the built-in values with their sources.
- Figures not yet published for a year keep the last known value until one is added, and the estimate says which year's rates it used.
- The lookup is global to the open household (one household is open at a time), like the province for bank holidays.
- Password strength (Argon2id cost) is a rule too, applied to new passwords only; existing ones keep the parameters stored with them.
