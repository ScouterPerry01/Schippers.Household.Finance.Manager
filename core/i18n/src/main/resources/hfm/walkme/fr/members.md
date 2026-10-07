# Membres et utilisateurs
@about: Ajoutez les personnes du ménage, puis donnez un accès à celles qui utilisent l’application.

## Ouvrir Membres du ménage {#members}
@screen: MEMBERS
@done: screen
@manual: members#about-members

Les membres du ménage sont les personnes dont parlent les livres : titulaires de comptes, patients, étudiants, contribuables. Dans le menu, ouvrez **Réglages**, puis **Membres du ménage**.

## Ajouter une personne {#add-person}
@screen: MEMBERS
@target: members.add
@done: shown member.name
@manual: members#add-person

Cliquez sur **Ajouter**. Seul un administrateur peut ajouter ou modifier les membres du ménage.

## Décrire la personne {#person-form}
@target: member.save
@done: added member
@manual: members#person-fields

- **Nom** : comme la famille appelle la personne, par exemple Alex ou Grand-maman.
- **Lien** : **Adulte**, **Enfant** ou **Autre personne à charge**.
- **Date de naissance (AAAA-MM-JJ)** : facultative ; sert là où l’âge compte.
- **Habite au ou en** : **Comme le ménage**, sauf si la personne habite dans une autre province ou un autre territoire.

Cliquez sur **Enregistrer**, puis ajoutez tous les autres de la même façon, enfants compris.

## Ouvrir Utilisateurs {#users}
@screen: USERS UsersTab.USERS
@done: screen
@manual: users#concepts

Les utilisateurs sont les personnes qui ouvrent une session. Un enfant peut être membre du ménage sans jamais ouvrir de session. Dans le menu, ouvrez **Réglages**, puis **Utilisateurs**.

## Ajouter un utilisateur {#add-user}
@screen: USERS UsersTab.USERS
@target: users.add
@done: shown user.dialog
@manual: users#add-user

Cliquez sur **Ajouter un utilisateur**. Seul un administrateur peut le faire.

## Remplir l’utilisateur {#user-form}
@target: user.dialog.save
@done: shown user.recovery
@manual: users#add-user-fields

- **Nom** et **Nom d’utilisateur** : le nom d’utilisateur est ce que la personne tape pour ouvrir une session, sans espaces.
- **Rôle** : **Administrateur** (tout), **Membre** (ses propres comptes privés et les groupes partagés qu’on lui donne) ou **Lecteur** (lecture seulement).
- **Cet utilisateur est le membre du ménage** : la personne qu’est cet utilisateur.
- **Nouveau mot de passe** et **Nouveau mot de passe de nouveau** : son mot de passe, selon les règles affichées.

Cliquez sur **Enregistrer**.

## Sa clé de récupération {#recovery-key}
@target: user.recovery
@manual: users#new-recovery-key

La clé de récupération du nouvel utilisateur n’est affichée qu’une fois. Imprimez-la ou copiez-la et remettez-la-lui pour qu’il la garde en lieu sûr, puis cliquez sur **La clé est conservée**.

## Donner accès aux comptes {#access}
@screen: USERS UsersTab.ACCESS
@done: screen
@manual: users#access-tab

À l’onglet **Accès**, chaque ligne est un groupe de comptes et chaque colonne un utilisateur. Choisissez **Aucun accès**, **Consultation**, **Saisie seulement** ou **Modification** dans chaque case. Un membre ne voit un groupe partagé que si on lui en donne l’accès.
