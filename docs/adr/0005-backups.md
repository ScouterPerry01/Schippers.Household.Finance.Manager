# ADR 0005: Backups, restore and full export

Status: Accepted (Phase 1, 2026-10-01)

## Context

The SRS requires:

- **BAK-01:** encrypted backups on a schedule, daily by default, keeping a set number of versions.
- **BAK-02:** backups to a local, external or network drive, or a cloud folder.
- **BAK-03:** one-click restore, with a test-restore check.
- **BAK-04:** a reminder after 7 days without a backup.
- **BAK-05:** moving to a new computer from a backup.
- **EXP-01:** a full export in open formats.

There is no server. A household is a folder of encrypted databases (ADR 0002).

## Decision

**Format.** A backup is one `.hfmbak` file: a ZIP of the household's files plus `manifest.json`, which holds the SHA-256 of every file.
- **Contents:** the key ring `household.json`, every `*.db` with its `-wal` log, and the document vault once it exists. Earlier backups are not included.
- **Encryption:** nothing is decrypted to make a backup.
  - Each database stays encrypted with its own key.
  - The key ring stays protected by each user's password and recovery key.
  - So a backup reveals no financial data, and restoring it needs the same credentials as the household.
  - The only readable data is the login names in the key ring, the same as in the household folder.
- **Other users' private ledgers:** included as they are, still encrypted to their owners. Any user can back up the whole household without being able to read the private parts.

**Consistent copies.** While the files are copied, every open database holds its write lock.
- Readers carry on; writers wait (a 15-second busy timeout).
- The database file and its write-ahead log are copied together, so the copy matches the moment of the backup.
- SQLite's online backup API is not used, because it would write a *decrypted* copy.

**Test-restore (BAK-03).** Every backup is checked as soon as it is written:
- every file against its checksum;
- no database stored unencrypted;
- each database the user can open is extracted to a temporary folder, opened with its key, and passes `PRAGMA quick_check`.

Old versions beyond the number to keep are deleted only after a backup passes this check.

**Restore (BAK-03, BAK-05).** A backup is always restored into a *new* folder, never over a household; the restored household then unlocks as usual. Restoring on another computer is how a household moves to a new computer. Archive paths are checked so a damaged or hostile backup cannot write outside the target folder.

**Schedule (BAK-01, BAK-04).**
- **Settings:** stored in the household: folder, frequency (off, daily, weekly) and versions to keep (10 by default).
- **Default:** a new household gets daily backups into a folder beside it. The screen recommends an external drive, a network drive or a cloud folder instead (BAK-02).
- **Runs:** while a household is open, the schedule is checked every 30 minutes.
- **Reminder:** the dashboard flags 7 days without a successful backup.

**Full export (EXP-01).** Every table of every database the user can open is written as CSV (one file per table) and JSON (one file per database) in a ZIP, with a README explaining the format.
- The tables are read generically, so new tables are exported without code changes.
- The export is deliberately not encrypted, so the user is warned before saving.
- Other users' private groups are not exported.

## Consequences

- Backups grow with the document vault (Phase 2). Incremental backups can be added later without changing the format: the manifest already lists files with their checksums.
- A household's folder name is part of the backup file name.
