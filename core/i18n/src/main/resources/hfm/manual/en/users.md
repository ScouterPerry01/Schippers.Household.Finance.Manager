# Users

Users are the people who sign in to the household, each with their own login name and password. This screen decides who can sign in, what each person can open, and shows who did what. It is in the **Settings** group of the menu, under **Users**.

## Users, roles and account groups {#concepts}

@index: user account; sign-in; login; role; permission; access rights; account group; private group; shared group

Three ideas work together:

- A user is a sign-in account: a login name, a display name, a password and a recovery key. A user is not the same as a [household member](members): a member is a person your records are about, and a child can be a member without ever being a user.
- A role says what kind of user someone is: Administrator, Member or Viewer. See [Roles](users#roles).
- Account groups hold the accounts. A shared group is for the household; a private group belongs to one user. The **Access** tab says, for each group, who can open it and at what level. When you add an account on the [Accounts](accounts) screen, you choose its group. A user can make their own private group with **Create my private group** where the app offers it, for example on the [Calendar](calendar).

Access is enforced by encryption, not only by the screens: each account group has its own key, and a user who has not been given access to a group does not hold its key at all. A private group stays private even from administrators unless its owner gives them access.

## Roles {#roles}

@index: administrator; admin; member role; viewer; read only

- Administrator: everything, including users and settings, and every shared account group. Administrators add users, change roles, set the household's province, edit household members and give access to shared groups. They do not see a private group unless its owner gives them access.
- Member: their own private accounts, and the shared groups they are given. A member can change their own name and password, and give access to their own private groups.
- Viewer: read only, in the groups they are given. On the Access tab a viewer can be given View at most. A member who had Capture only or Edit and becomes a viewer can only view; the table then shows View. A viewer also sees the household's [pets](pets) without being able to change them.

The household always keeps at least one active administrator. The app refuses to change the role of the last administrator or to stop them from signing in, with "The household must keep at least one active administrator."

## The Users screen {#screen}

At the top of the screen:

- **Change my password…**: opens the dialog to change your own password. Every user has it. See [Change my password](users#change-password).
- **Add a user**: opens the dialog to add a user. Shown only to administrators. See [Add a user](users#add-user).

Below are three tabs: **Users**, **Access** and **Activity**.

### Users tab {#users-tab}

A short note explains that each person signs in with their own name and password, and that what they can open depends on their role and on the access each account group gives them.

Each user has a card with:

- Their name, with "(you)" after your own.
- "login" and their login name, their role, "is" and the household member they are linked to (if any), and "cannot sign in" if they were stopped from signing in.
- **Edit**: opens the [Edit user](users#edit-user) dialog. Administrators see it on every card; other users only on their own.

### Access tab {#access-tab}

@index: permissions; no access; view; capture only; edit; grant access; share an account group

The Access tab is a table. Each row is an account group you can see, with "private" or "shared" under its name. Each column is a user who can sign in.

In each cell, a level:

- No access: the user does not see the group or its accounts, and does not hold its key.
- View: read only. The user sees the accounts, transactions and documents of the group, but cannot change them.
- Capture only: the user can add receipts and transactions, for example from the phone, but cannot change anything else.
- Edit: everything in the group.

Some cells are not pickers:

- owner: the owner of a private group always has it, with Edit.
- administrator: in a shared group, administrators always have it, with Edit.

Who can change a cell:

- An administrator can change any cell of a shared group, and of a private group they can open.
- The owner of a private group can change the cells of their group.
- Others see the levels but cannot change them.

In a viewer's column, the picker offers only No access and View.

A change takes effect at once, without a Save button, and is recorded in the activity log. Giving a level above No access hands the user the group's key; setting No access takes the key away again.

> Note: Taking access away stops the person from opening the group from then on. It cannot take back what they already saw or exported while they had access.

> Tip: To let a teenager photograph receipts into the family's shared group without seeing everything else, make them a Member and give them Capture only on that group.

### Activity tab {#activity-tab}

@index: activity log; audit log; history; who did what

The Activity tab lists what users did, newest first: up to the 300 latest entries.

- **Whose activity**: shown to administrators. Choose **Everyone** or one user. Other users see only their own activity, with the note "Your own activity."

Each line shows:

- The date and time.
- The user's name.
- What was done and to what, such as "Created · account", "Changed access · account group", "Reconciled · statement" or "Showed an account number · account".
- Where: "household" for things kept for the whole household (users, categories, settings, backups), or the name of the account group the change was made in.

Changes to transactions come from the account groups you can open: you never see entries from a private group you have no access to. The log never shows amounts or the content of documents.

The actions recorded include: Created, Changed, Deleted, Imported, Matched, Reconciled, Undid a reconciliation, Paid, Linked, Set budget, Removed budget, Entered a rate, Changed role, Changed access, Allowed to sign in, Stopped from signing in, Changed password, Reset password with the recovery key, Showed an account number, Paired a phone, Removed a phone, Backup, Backup settings and Exported data.

## Add a user {#add-user}

Only an administrator can add a user.

1. Click **Add a user**.
2. Fill in the form (see below).
3. Click **Save**. It becomes available once the name, the login name and both passwords are filled in and the two passwords match.
4. Write down or print the new user's recovery key, which is shown once. See [The new user's recovery key](users#new-recovery-key).

### Add a user form {#add-user-fields}

- **Name**: the name shown on the screens and in the activity log, such as Sam. Required. It can be changed later.
- **Login name**: what the person types to sign in, such as sam. Required, without spaces. It must not already be used by another user, whatever the capitals. It cannot be changed later.
- **Role**: Administrator, Member or Viewer. The default is Member. A line under the pickers explains the role chosen. See [Roles](users#roles).
- **This user is the household member**: the [household member](members) this user is, or (none). Optional. It links the sign-in account to the person; the Users tab then shows "is" and the person's name.
- **New password**: the user's password. The hint shows the minimum length (at least 12 characters). A passphrase of a few words is easy to remember and hard to guess.
- **New password again**: the same password again. While the two differ, the field says "The two passwords are different."
- **Cancel**: closes the dialog without adding anyone.

A new administrator receives the keys of every shared account group at once. A new member or viewer sees only what you give them on the Access tab.

### The new user's recovery key {#new-recovery-key}

@index: recovery key; forgotten password

After the user is added, the dialog "Recovery key for" and their name shows a long key in groups of four characters. Give this key to the person to print or write down and keep somewhere safe. It is the only way back in if their password is forgotten, and it is not shown again.

- **Print**: prints a page with the key, the household's name and the explanation, through the system's print dialog. The key is not saved in a file.
- **Copy**: copies the key, for example to paste it into a password manager. Do not keep it in a file on the same computer.
- **The key is saved**: closes the dialog once the key is safely written down.

See [The recovery key](security#recovery-key) for how the key is used.

## Edit user {#edit-user}

Click **Edit** on a user's card.

- **Name**: the user's display name. Every user can change their own; an administrator can change anyone's.

Administrators also see:

- **This user is the household member**: link or unlink the household member this user is.
- **Role**: change the user's role. You cannot change your own role; the picker is greyed out on your own card. The line below explains the role chosen.
- **Can sign in**: clear it to stop the user from signing in without deleting them. Their past entries stay, with their name. Tick it again to let them back in. It is not shown on your own card: you cannot stop yourself.

Click **Save** to apply the changes, or **Cancel** to leave them.

When a user stops being an administrator, they keep only the shared groups they were given explicitly on the Access tab. When a user becomes an administrator, they receive every shared group.

> Note: There is no way to delete a user. Stopping them from signing in keeps the history of who did what intact.

## Change my password {#change-password}

@index: change password; new password

Every user can change their own password.

1. Click **Change my password…** at the top of the screen.
2. Type your **Current password**.
3. Type the **New password**, at least 12 characters.
4. Type it again in **New password again**.
5. Click **Save**. It becomes available once the three fields are filled in and the two new passwords match.

If the current password is wrong, the app says "The current password is not correct." and nothing changes.

Your recovery key stays the same and still works if you forget the new password. An administrator cannot change another user's password: a user who forgot theirs uses their recovery key on the unlock screen. See [Forgot your password](security#forgot-password).

## Who can do what on this screen {#permissions}

- Every user: see the list of users, edit their own name, change their own password, see the Access table for the groups they can open, see their own activity.
- The owner of a private group: give or take away access to that group.
- Administrators: add users, edit any user, change roles, stop or allow sign-in, link users to household members, give access to shared groups, see everyone's activity.
