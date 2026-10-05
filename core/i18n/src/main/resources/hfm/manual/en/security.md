# Security

Your household is stored encrypted on this computer and opens only with a user's password or recovery key. This chapter covers the **Security** screen (the auto-lock delay and the household's password rules), locking, passwords and the recovery key. The screen is in the **Settings** group of the menu, under **Security**.

## How your household is protected {#protection}

@index: encryption; encrypted; master password; data protection; security model

- Everything is encrypted on disk: accounts, transactions, documents, settings. A copy of the household folder, or of a backup, is unreadable without a user's password or recovery key.
- Each user has their own password. Each account group has its own key, which only the users given access to it hold. See [Users](users).
- There is no RANN account and no server. Nobody, RANN included, can reset a forgotten password. The recovery key is the way back in.
- While the household is open, its keys are in the computer's memory. Locking closes the household and wipes them from memory.

## The Security screen {#screen}

The screen has one setting for this computer, a reminder, and the household's [password rules](security#password-rules).

### Lock after inactivity {#auto-lock}

@index: auto-lock; automatic lock; inactivity; timeout; idle; screen lock

- **Lock after inactivity**: 1 minute, 5, 10, 15, 30 or 60 minutes, or Never. The default is 10 minutes. After this long without keyboard or mouse activity in the app's window, the household locks itself and its keys are wiped from memory. Takes effect as soon as you pick it.

What happens when it locks:

- The unlock screen appears, showing the household's folder. Sign in again with your login name and password to continue where you were.
- Anything not yet saved in an open form or dialog is lost.
- Phones can no longer send captures until the household is unlocked again. See [Phones](phones).
- Scheduled backups and other background work wait until the household is open again.

The time is checked about every 15 seconds, so the lock can come a few seconds after the delay. With Never, the household stays open until you lock it or close the app.

This setting applies to this computer, for every household opened on it, and every user. It is not stored in the household or its backups.

> Tip: On a computer others can use, choose a short delay, such as 5 minutes. On your own computer in a locked room, a longer one is more comfortable.

### The recovery reminder {#recovery-reminder}

Under the setting, a reminder: keep your printed recovery key somewhere safe; it is the only way back in if a password is forgotten. See [The recovery key](security#recovery-key).

## Lock now {#lock-now}

@index: lock; sign out; log out; step away

- **Lock**: at the top right of the window, shown while a household is open. Locks the household immediately, as the auto-lock would.

Closing the window also locks the household before the app quits.

## Unlock the household {#unlock}

The unlock screen ("Unlock household") appears when you open a household from the start screen, after a lock, and after a restore.

- **Login name**: your login name, as set when your user was added. Capitals do not matter.
- **Password**: your password. The text is hidden.
- **Unlock**: opens the household. Available once both fields are filled in. A wrong login name or password gives "Wrong login name or password." and nothing else, so nobody can tell which one was wrong.
- **Back**: returns to the start screen.
- **Forgot your password?**: opens the reset with the recovery key. See [Forgot your password](security#forgot-password).

A user who was stopped from signing in on the [Users](users) screen cannot unlock the household, even with the right password.

## Passwords {#passwords}

@index: password length; passphrase; strong password

- The first administrator chooses the master password when the household is created, typed twice (**Master password**, **Confirm master password**). No household exists yet, so the built-in password rules apply: at least 12 characters, not containing the login name.
- Users added later get a password chosen when they are added, and anyone can change their own. Both follow the household's password rules.
- A new password set with the recovery key follows the household's password rules too. They are checked once the recovery key has opened the household, before anything changes.

Under each password field, a line says what the rules ask for, such as "At least 12 characters. It may not contain the login name." See [Password rules](security#password-rules).

A passphrase of a few unrelated words, such as "maple canoe Thursday lantern", is easy to remember and hard to guess. Do not reuse a password from a website.

To change your password, use **Change my password…** on the [Users](users) screen. See [Change my password](users#change-password). Your recovery key stays the same.

## Password rules {#password-rules}

@index: password rules; password policy; password requirements; Argon2id; key derivation

The household's password rules say what every password chosen from now on must have. They are on the **Security** screen, in the **Password rules** card. An administrator sets them for the whole household; other users see them but cannot change them ("Only an administrator can change these rules.").

- **Shortest password, in characters (8 to 64)**: the fewest characters a password may have. The default is 12. Fewer than 8 or more than 64 is refused.
- **Needs a capital letter**: every new password must have at least one capital letter, accented ones included. Off by default.
- **Needs a small letter**: at least one small letter. Off by default.
- **Needs a digit**: at least one digit. Off by default.
- **Needs a symbol (anything other than a letter, a digit or a space)**: at least one symbol, such as ! or #. Off by default.
- **May not contain the login name**: the password may not contain the user's login name, in capitals or not. On by default. Login names of one or two characters are not looked for.
- **Save the password rules**: keeps the rules. Only what changed is saved, and it takes effect today. A line confirms: "Password rules saved. They apply from today to every password chosen from now on."

The rules apply wherever a password is chosen: adding a user, changing a password, and resetting one with the recovery key. A password that breaks them is refused with what it lacks, such as "The password needs a digit." Passwords already set keep working, even if they would not meet new rules; ask users to change theirs if you want them to.

Each change is kept in [Rates and rules](rates-rules) with the date it takes effect, like any other rule, so the rules in force on any date can be seen there.

### How passwords are protected {#password-cost}

@index: Argon2id; password hashing; key derivation cost

A password is never stored. It is turned into a key with Argon2id, a method designed to make guessing slow. The last line of the card gives its cost for new passwords: the memory, 64 MiB by default, and the number of passes, 3 by default. Both are values in [Rates and rules](rates-rules) (Password protection memory, Password protection passes). A higher cost makes each guess slower, and unlocking slightly slower too.

Each password keeps the cost it was set with, so changing it never locks anyone out: it applies to passwords chosen from then on. Less than 19 MiB or 2 passes is refused, the minimum recommended for Argon2id.

## The recovery key {#recovery-key}

@index: recovery key; recovery code; lost password; forgotten password; emergency access

Each user has a recovery key: a long code in groups of four letters and digits separated by dashes. It is shown once:

- To the first administrator, right after the household is created, on the "Your recovery key" screen.
- For each user added later, in the "Recovery key for" dialog, to hand to that person. See [The new user's recovery key](users#new-recovery-key).

On the "Your recovery key" screen:

- **Print**: prints the key through the system's print dialog, without saving it in a file.
- **Copy**: copies the key, so you can paste it into a password manager.
- **I have saved my recovery key**: opens the household. Only click it once the key is printed or written down.

How to keep it:

- Print it or write it down, and keep the paper somewhere safe away from this computer, such as with your important papers or in a safe.
- Do not keep it only in a file on the same computer: if the computer is lost, the key is lost with it.
- Anyone who has your login name and your recovery key can open the household as you. Guard it like the household's own key.

The recovery key never changes: changing your password, or resetting it with the key, keeps the same recovery key. It also opens the backups of the household.

## Forgot your password {#forgot-password}

@index: reset password; forgot password; locked out

1. On the unlock screen, click **Forgot your password?**.
2. On "Reset password with recovery key", type your **Login name**.
3. Type the **Recovery key**. Capitals, spaces and dashes do not matter, and the letters I, L and O are read as the digits 1 and 0. A typing mistake is caught at once: "That recovery key is not valid."
4. Type a **New password** that follows the household's [password rules](security#password-rules) (at least 12 characters by default), and again in **Confirm master password**.
5. Click **Reset password**. The household opens with the new password. A password that breaks the rules is refused with what it lacks, and the old password stays in place.

**Back** returns to the unlock screen without changing anything.

The reset is recorded in the activity log as "Reset password with the recovery key". The recovery key stays the same.

> Important: If both your password and your recovery key are lost, your user cannot be opened again. Another administrator can still sign in and add a new user for you, but what only your user could open, such as your private account group, stays closed.

## Related settings {#related}

- [Users](users): who can sign in, roles, and access to account groups.
- [Backups](backups): encrypted copies of the household.
- [Phones](phones): remove a phone that is lost or no longer used.
- [AI reading](ai): your Anthropic key, kept in the computer's secret store.
