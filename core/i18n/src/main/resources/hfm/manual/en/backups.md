# Backups

Backups are copies of the whole household, made automatically, checked, and kept in a folder you choose, so you can recover from a broken disk, a lost or stolen computer, or a mistake, and move the household to a new computer. The screen is in the **Settings** group of the menu, under **Backups**. It also holds **Export all data**.

## What a backup is {#about-backups}

@index: backup; copy; hfmbak; restore point; disaster recovery

- A backup is one file ending in .hfmbak, named after the household with the date and time, such as Schippers-20261005-143012.hfmbak.
- It holds everything: every account group, every document, every user's keys. Nothing is left out because of who made it.
- It stays encrypted. Opening it needs a user's password or recovery key, exactly like the household itself, so a backup in a cloud folder does not reveal your finances.
- Each backup is checked right after it is made: every file against its checksum, that no database is stored unencrypted, and that the databases you can open open correctly. Only a backup that passes counts as a success.
- Older backups are deleted only after a new one has passed its check, so you never end up with fewer good copies.

> Important: The backups of a household open with the same passwords and recovery keys as the household. Keep your recovery key (see [Security](security#recovery-key)): without a password or a recovery key, nobody, RANN included, can open a backup.

## Automatic backups out of the box {#defaults}

A new household is backed up every day from the start, into a folder beside it named after the household followed by " - backups", keeping 10 versions (the defaults, which can be changed in [Rates and rules](rates-rules)). That protects against mistakes, but not against losing the disk: choose a folder on another drive as soon as you can.

## Backup settings {#settings}

@index: backup folder; backup schedule; external drive; cloud folder; OneDrive; Google Drive; Dropbox

At the top of the screen, the folder in use, or "No backup folder chosen."

- **Choose folder…**: opens a folder picker. The folder chosen is saved at once. For real protection, choose an external drive, a network drive or a cloud folder (OneDrive, Google Drive, Dropbox), not the same disk as the household.
- **Automatic backups**: Off, Every day or Every week. Saved as soon as you pick it.
  - Every day: a backup is made when the last successful one is at least 20 hours old.
  - Every week: when the last successful one is at least 7 days old.
  - Off: no automatic backups. **Back up now** still works.
- **Versions to keep**: how many backups of this household to keep in the folder, from 1 to 365. The default is 10, from [Rates and rules](rates-rules). Once a new backup has passed its check, the oldest ones beyond this number are deleted. Other files in the folder are never touched.
- **Save**: saves **Versions to keep**. A number outside 1 to 365 is refused ("Keep between 1 and 365 versions.").

The schedule runs only while the household is open and unlocked: the app looks every 30 minutes whether a backup is due. If the computer was off, the backup is made soon after you next open the household.

These settings belong to the household, not to the computer, and changes are recorded in the activity log. Only an administrator can change them: for other users the folder button, the schedule, the number to keep and **Save** are greyed out, with the note "Only an administrator can change where, how often and how many backups are kept. Anyone can back up now."

> Tip: A cloud folder synchronized by OneDrive, Google Drive or Dropbox is an easy off-site copy. The backups stay encrypted there.

## Back up now {#back-up-now}

- **Back up now**: makes a backup immediately, checks it, then deletes the versions beyond the number to keep. Available once a folder is chosen. While it runs, the button reads "Backing up…". Writing to the household pauses for the moment the files are copied, usually a fraction of a second.

Then the result shows:

- "Backup saved and checked:" with the file name, or "The backup check failed:" with the problems found.
- "Last backup:" with the date and time of the last successful backup, or "No backup yet." It turns red when there has been no successful backup for 7 days.
- "Last problem:" in red, when the last attempt failed, with the reason (for example a folder that is not reachable, such as an unplugged drive).

If no backup has succeeded in the last 7 days, the dashboard's review list also says "No successful backup in the last 7 days". See [Dashboard](dashboard).

## Backups in the folder {#backup-list}

"Backups in the folder" with their number lists the backups of this household found in the folder, newest first. Backups of other households in the same folder are not listed. Each line shows the date and time, the file name and its size in KB.

- **Check**: tests that backup again, as when it was made: every file against its checksum, no unencrypted database, and the databases you can open opened and checked for damage. The result shows under the line: "The backup is complete and readable" with the number of databases checked, or "The backup check failed:" and the problems.

> Tip: Check an older backup now and then, especially one on a drive you rarely plug in.

## Restore a backup {#restore}

@index: restore; recover; new computer; move household; transfer to another computer

A backup is restored from the start screen, never over an open household. The same steps move the household to a new computer.

1. Lock the household with **Lock** at the top of the window (or close the app and start it again).
2. On the start screen, click **Restore from a backup…**.
3. Pick the backup file. The picker shows "Household backups (.hfmbak)".
4. Choose the folder where the restored household should go ("Choose where to put the restored household").
5. The app checks every file against its checksum and restores the household into a new folder inside the one you chose, named after the backup, such as Schippers.hfm (or Schippers (2).hfm if that name is taken). An existing household is never overwritten.
6. The unlock screen opens. Sign in with your login name and password, or use **Forgot your password?** with your recovery key.

If the backup is damaged, the restore stops and says why; nothing is restored.

After a restore:

- Set the backup folder and schedule again if the restored copy is on a new computer.
- Settings kept on each computer, such as the auto-lock delay, the theme and the text size, are not in the backup. See [Display and accessibility](display) and [Security](security).
- Keys for AI reading are kept in the computer's secret store, not in the household: save your key again under [AI reading](ai).

> Important: Restoring creates a second copy of the household. Once you are sure the restored copy is the one to use, stop using the old one, so changes do not end up split between two copies.

## Export all data {#export}

@index: export; CSV; JSON; open format; data portability; leave the app

Export all data saves everything you can see in open formats, so your data can always be used elsewhere, even without RANN's Roost.

1. Click **Export all data…**.
2. Read the warning: the export is NOT encrypted. Anyone with the file can read your accounts, transactions, account numbers and documents.
3. Click **Continue**, or **Cancel** to stop.
4. Choose where to save the file. The name suggested is household-export.zip.
5. The screen says "Exported to" and where.

The ZIP file holds:

- A README.txt that explains the contents.
- One CSV file per table (UTF-8, comma separated) and one JSON file per database, for every account group you can open.
- A documents folder with every document's original file.

Amounts are in cents in the columns ending in _minor (satoshis for crypto-assets), with the currency in its own column. Dates are YYYY-MM-DD. Only what you can open is exported: a private group you have no access to is left out. The export is recorded in the activity log, without the file name.

> Important: An export is not a backup: it cannot be restored into RANN's Roost, and it is not encrypted. Keep it somewhere safe and delete it when you no longer need it.
