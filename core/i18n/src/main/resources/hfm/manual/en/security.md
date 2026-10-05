# Security

Your household is stored encrypted on this computer and opens only with a user's password or recovery key. This chapter covers the **Security** screen (the auto-lock delay), locking, passwords and the recovery key. The screen is in the **Settings** group of the menu, under **Security**.

## How your household is protected {#protection}

@index: encryption; encrypted; master password; data protection; security model

- Everything is encrypted on disk: accounts, transactions, documents, settings. A copy of the household folder, or of a backup, is unreadable without a user's password or recovery key.
- Each user has their own password. Each account group has its own key, which only the users given access to it hold. See [Users](users).
- There is no RANN account and no server. Nobody, RANN included, can reset a forgotten password. The recovery key is the way back in.
- While the household is open, its keys are in the computer's memory. Locking closes the household and wipes them from memory.

## The Security screen {#screen}

The screen has one setting and a reminder.

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

@index: password rules; password length; passphrase; strong password

- The first administrator chooses the master password when the household is created: at least 12 characters, typed twice (**Master password**, **Confirm master password**).
- Users added later get a password chosen when they are added, and anyone can change their own: at least 12 characters too.
- A new password set with the recovery key also needs at least 12 characters.

A passphrase of a few unrelated words, such as "maple canoe Thursday lantern", is easy to remember and hard to guess. Do not reuse a password from a website.

To change your password, use **Change my password…** on the [Users](users) screen. See [Change my password](users#change-password). Your recovery key stays the same.

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
4. Type a **New password** of at least 12 characters, and again in **Confirm master password**.
5. Click **Reset password**. The household opens with the new password.

**Back** returns to the unlock screen without changing anything.

The reset is recorded in the activity log as "Reset password with the recovery key". The recovery key stays the same.

> Important: If both your password and your recovery key are lost, your user cannot be opened again. Another administrator can still sign in and add a new user for you, but what only your user could open, such as your private account group, stays closed.

## Related settings {#related}

- [Users](users): who can sign in, roles, and access to account groups.
- [Backups](backups): encrypted copies of the household.
- [Phones](phones): remove a phone that is lost or no longer used.
- [AI reading](ai): your Anthropic key, kept in the computer's secret store.
