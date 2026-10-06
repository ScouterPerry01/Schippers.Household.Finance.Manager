# 2026 figures check (October 5, 2026)

A sweep of every built-in rules file (`core/calc/src/main/resources/hfm/rules/*.rules`) for values whose latest date is before 2026. Only official sources were used: CRA, Revenu Québec, RAMQ, Finances Québec, the provinces' and territories' finance departments and their consolidated Income Tax Acts. Each figure added carries its source in the rules file.

## Added

- Medical expense credit threshold, 2026 (`tax.prov.medical`):
  - Alberta $2,942: Personal Income Tax Act s. 12(1) ($2,884) and s. 44.2 (indexed by 2 % for 2026, rounded to the dollar).
  - New Brunswick $2,854: Income Tax Act s. 26(1) and 16.1 (the running amount is indexed unrounded; $2,797.58 for 2025 x 1.020 = $2,853.53). The same chain from $1,947 gives the official $2,724 for 2024 and $2,798 for 2025.
  - Yukon $2,890: the Yukon credit is the federal medical amount times the Yukon/federal conversion rate, so the threshold is the federal $2,890.
  - Northwest Territories $2,890: Income Tax Act s. 2.23 and 2.13 index it exactly as the federal amount, which it matched in 2024 and 2025.
- Provincial and territorial alternative minimum tax shares, 2026 (`tax.prov.amt`), read in each Income Tax Act:
  - Changed: British Columbia 40 % (5.6 % / 14 %), Newfoundland and Labrador 62.1 % (8.7 % / 14 %), Yukon 45.71 % (6.4 % / 14 %). These follow the provincial or territorial lowest rate over the new federal 14 %.
  - Unchanged: Ontario 24.63 % (5.05 % / 20.5 % federal minimum tax rate), Prince Edward Island and Nova Scotia 57.5 %, New Brunswick 57 %, Manitoba and Saskatchewan 50 %, Alberta 35 %, Northwest Territories 45 %.
- Quebec drug insurance premium, 2026 (`tax.qc.drugPremium`): a maximum of $777.50. This is half of RAMQ's $766 (July 2025 to June 2026) plus half of $789 (from July 2026), worked out the same way as Schedule K 2025's $755. The exemptions and rates stay those of Schedule K 2025 (see below).
- Yukon dividend tax credit: lines for 2025 (Worksheet YT428 2025) and 2026 (Government of Yukon), both 12.02 % eligible and 0.67 % other.
- Prince Edward Island age amount, 2026: $6,510 above $36,600, unchanged (Income Tax Act s. 10(2), consolidated to May 29, 2026).

## Re-verified, no change needed

- Quebec work premium 2026 and health services fund thresholds 2026: these match Finances Québec's 2026 parameters. Each published 2026 maximum equals rate x (ceiling - exclusion) using the rates in the file.
- Yukon dividend tax credit 2024 to 2026: 12.02 % and 0.67 %.
- Dividend tax credit rates in the acts for 2026: New Brunswick (14 % and 2.75 %), Manitoba (8 % and 0.7835 %), British Columbia (12 % and 1.96 %), Alberta (8.12 % and 2.18 %), Nova Scotia (8.85 % and 1.5 %), Prince Edward Island (10.5 % and 1.3 %), Newfoundland and Labrador (6.3 % and 3.2 %), Northwest Territories (11.5 % and 6 %).
- Fixed medical thresholds still in force for 2026: Manitoba $1,728, Nova Scotia $1,637 (Income Tax Act consolidated to September 3, 2026), Prince Edward Island $1,678.
- Sales taxes: no rate change in 2026.

## Corrections

None. One discrepancy is noted but left as printed: Form YT428 2025 applies 43.9 % to the additional tax for minimum tax purposes but 44.14 % to the carryover. The Yukon act uses one conversion rate for both, which is 6.4 % / 14.5 % = 44.14 %.

## Not published yet (2025 values stay in force)

- Canada workers benefit for residents of Quebec, Alberta and Nunavut, 2026: the CRA's 2026 indexation page gives only the general amounts, and Schedule 6 2026 (5005-S6, 5009-S6, 5014-S6) is not out.
- Nunavut medical expense threshold and alternative minimum tax share, 2026: the Nunavut legislation site and the Government of Nunavut income tax page could not be read. The Government of Nunavut 2026 rate sheet does not give them.
- Schedule K 2026 (Quebec drug insurance) exemption thresholds and premium rates.
- Medical travel kilometre rates for 2026: the CRA publishes them early in 2027.
- GST/HST credit and Canada child benefit for the 2026 base year: these are paid from July 2027 and are not published yet.
