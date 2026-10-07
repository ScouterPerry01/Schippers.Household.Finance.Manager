# Members and users
@about: Add the people in the household, then give a sign-in to those who use the app.

## Open Household members {#members}
@screen: MEMBERS
@done: screen
@manual: members#about-members

Household members are the people the books are about: account owners, patients, students, taxpayers. In the menu, open **Settings**, then **Household members**.

## Add a person {#add-person}
@screen: MEMBERS
@target: members.add
@done: shown member.name
@manual: members#add-person

Click **Add**. Only an administrator can add or change household members.

## Describe the person {#person-form}
@target: member.save
@done: added member
@manual: members#person-fields

- **Name**: what the family calls the person, such as Alex or Grandma.
- **Relationship**: **Adult**, **Child** or **Other dependant**.
- **Date of birth (YYYY-MM-DD)**: optional; used where age matters.
- **Lives in**: **Same as the household**, unless the person lives in another province or territory.

Click **Save**, then add everyone else the same way, children included.

## Open Users {#users}
@screen: USERS UsersTab.USERS
@done: screen
@manual: users#concepts

Users are the people who sign in. A child can be a household member without ever signing in. In the menu, open **Settings**, then **Users**.

## Add a user {#add-user}
@screen: USERS UsersTab.USERS
@target: users.add
@done: shown user.dialog
@manual: users#add-user

Click **Add a user**. Only an administrator can.

## Fill in the user {#user-form}
@target: user.dialog.save
@done: shown user.recovery
@manual: users#add-user-fields

- **Name** and **Login name**: the login name is what they type to sign in, without spaces.
- **Role**: **Administrator** (everything), **Member** (their own private accounts and the shared groups they are given) or **Viewer** (read only).
- **This user is the household member**: the person this user is.
- **New password** and **New password again**: their password, following the rules shown.

Click **Save**.

## Their recovery key {#recovery-key}
@target: user.recovery
@manual: users#new-recovery-key

The new user's recovery key is shown once. Print it or copy it and give it to them to keep somewhere safe, then click **The key is saved**.

## Give access to the accounts {#access}
@screen: USERS UsersTab.ACCESS
@done: screen
@manual: users#access-tab

On the **Access** tab, each row is an account group and each column a user. Choose **No access**, **View**, **Capture only** or **Edit** in each cell. A member sees a shared group only when given access to it.
