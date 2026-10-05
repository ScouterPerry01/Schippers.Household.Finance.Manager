# Utilisateurs

Les utilisateurs sont les personnes qui se connectent au ménage, chacune avec son propre nom d'utilisateur et son propre mot de passe. Cet écran décide qui peut se connecter, ce que chaque personne peut ouvrir, et montre qui a fait quoi. Il se trouve dans le groupe **Réglages** du menu, sous **Utilisateurs**.

## Utilisateurs, rôles et groupes de comptes {#concepts}

@index: compte d'utilisateur; connexion; identifiant; rôle; permission; droits d'accès; groupe de comptes; groupe privé; groupe partagé

Trois idées vont ensemble :

- Un utilisateur est un compte de connexion : un nom d'utilisateur, un nom affiché, un mot de passe et une clé de récupération. Un utilisateur n'est pas la même chose qu'un [membre du ménage](members) : un membre est une personne que vos dossiers concernent, et un enfant peut être membre sans jamais être utilisateur.
- Un rôle dit quel genre d'utilisateur est une personne : Administrateur, Membre ou Lecteur. Voir [Rôles](users#roles).
- Les groupes de comptes contiennent les comptes. Un groupe partagé est pour le ménage ; un groupe privé appartient à un seul utilisateur. L'onglet **Accès** indique, pour chaque groupe, qui peut l'ouvrir et à quel niveau. Quand vous ajoutez un compte à l'écran [Comptes](accounts), vous choisissez son groupe. Un utilisateur peut créer son propre groupe privé avec **Créer mon groupe privé** là où l'application l'offre, par exemple dans le [Calendrier](calendar).

L'accès est protégé par le chiffrement, pas seulement par les écrans : chaque groupe de comptes a sa propre clé, et un utilisateur à qui on n'a pas donné accès à un groupe n'en détient pas du tout la clé. Un groupe privé reste privé, même pour les administrateurs, à moins que son propriétaire ne leur donne accès.

## Rôles {#roles}

@index: administrateur; admin; rôle membre; lecteur; lecture seulement

- Administrateur : tout, y compris les utilisateurs et les réglages, et tous les groupes de comptes partagés. Les administrateurs ajoutent des utilisateurs, changent les rôles, fixent la province du ménage, modifient les membres du ménage et donnent accès aux groupes partagés. Ils ne voient pas un groupe privé à moins que son propriétaire ne leur donne accès.
- Membre : ses propres comptes privés, et les groupes partagés qu'on lui donne. Un membre peut changer son propre nom et son mot de passe, et donner accès à ses propres groupes privés.
- Lecteur : lecture seulement, dans les groupes qu'on lui donne. À l'onglet Accès, un lecteur peut recevoir au plus Consultation. Un membre qui avait Saisie seulement ou Modification et devient lecteur peut seulement consulter ; le tableau affiche alors Consultation. Un lecteur voit aussi les [animaux](pets) du ménage sans pouvoir les changer.

Le ménage garde toujours au moins un administrateur actif. L'application refuse de changer le rôle du dernier administrateur ou de l'empêcher de se connecter, avec « Le ménage doit garder au moins un administrateur actif. »

## L'écran Utilisateurs {#screen}

En haut de l'écran :

- **Changer mon mot de passe…** : ouvre la fenêtre pour changer votre propre mot de passe. Chaque utilisateur l'a. Voir [Changer mon mot de passe](users#change-password).
- **Ajouter un utilisateur** : ouvre la fenêtre pour ajouter un utilisateur. Affiché seulement aux administrateurs. Voir [Ajouter un utilisateur](users#add-user).

En dessous, trois onglets : **Utilisateurs**, **Accès** et **Activité**.

### Onglet Utilisateurs {#users-tab}

Une courte note explique que chaque personne se connecte avec son propre nom et mot de passe, et que ce qu'elle peut ouvrir dépend de son rôle et de l'accès que lui donne chaque groupe de comptes.

Chaque utilisateur a une fiche avec :

- Son nom, suivi de « (vous) » pour le vôtre.
- « connexion » et son nom d'utilisateur, son rôle, « est » et le membre du ménage auquel il est lié (s'il y a lieu), et « ne peut pas se connecter » s'il a été empêché de se connecter.
- **Modifier** : ouvre la fenêtre [Modifier l'utilisateur](users#edit-user). Les administrateurs le voient sur chaque fiche ; les autres utilisateurs seulement sur la leur.

### Onglet Accès {#access-tab}

@index: permissions; aucun accès; consultation; saisie seulement; modification; donner accès; partager un groupe de comptes

L'onglet Accès est un tableau. Chaque ligne est un groupe de comptes que vous pouvez voir, avec « privé » ou « partagé » sous son nom. Chaque colonne est un utilisateur qui peut se connecter.

Dans chaque case, un niveau :

- Aucun accès : l'utilisateur ne voit ni le groupe ni ses comptes, et n'en détient pas la clé.
- Consultation : lecture seulement. L'utilisateur voit les comptes, les opérations et les documents du groupe, mais ne peut pas les changer.
- Saisie seulement : l'utilisateur peut ajouter des reçus et des opérations, par exemple à partir du téléphone, mais ne peut rien changer d'autre.
- Modification : tout, dans le groupe.

Certaines cases ne sont pas des listes :

- propriétaire : le propriétaire d'un groupe privé l'a toujours, en Modification.
- administrateur : dans un groupe partagé, les administrateurs l'ont toujours, en Modification.

Qui peut changer une case :

- Un administrateur peut changer toute case d'un groupe partagé, et d'un groupe privé qu'il peut ouvrir.
- Le propriétaire d'un groupe privé peut changer les cases de son groupe.
- Les autres voient les niveaux mais ne peuvent pas les changer.

Dans la colonne d'un lecteur, le sélecteur offre seulement Aucun accès et Consultation.

Un changement s'applique tout de suite, sans bouton Enregistrer, et il est inscrit dans le journal d'activité. Donner un niveau au-dessus de Aucun accès remet à l'utilisateur la clé du groupe ; choisir Aucun accès la lui retire.

> Remarque : Retirer l'accès empêche la personne d'ouvrir le groupe à partir de ce moment. Cela ne peut pas reprendre ce qu'elle a déjà vu ou exporté pendant qu'elle y avait accès.

> Conseil : Pour qu'un adolescent photographie des reçus dans le groupe partagé de la famille sans tout voir le reste, faites-en un Membre et donnez-lui Saisie seulement sur ce groupe.

### Onglet Activité {#activity-tab}

@index: journal d'activité; journal d'audit; historique; qui a fait quoi

L'onglet Activité liste ce que les utilisateurs ont fait, les plus récents en premier : jusqu'aux 300 dernières entrées.

- **Activité de** : affiché aux administrateurs. Choisissez **Tout le monde** ou un utilisateur. Les autres utilisateurs ne voient que leur propre activité, avec la note « Votre propre activité. »

Chaque ligne montre :

- La date et l'heure.
- Le nom de l'utilisateur.
- Ce qui a été fait et sur quoi, comme « Créé · compte », « Accès modifié · groupe de comptes », « Conciliation · relevé » ou « Numéro de compte affiché · compte ».
- Où : « ménage » pour ce qui est gardé pour tout le ménage (utilisateurs, catégories, réglages, sauvegardes), ou le nom du groupe de comptes où le changement a été fait.

Les changements aux opérations viennent des groupes de comptes que vous pouvez ouvrir : vous ne voyez jamais d'entrées d'un groupe privé auquel vous n'avez pas accès. Le journal ne montre jamais de montants ni le contenu des documents.

Les actions inscrites comprennent : Créé, Modifié, Supprimé, Importé, Rapproché, Conciliation, Conciliation annulée, Payé, Lié, Budget fixé, Budget retiré, Taux saisi, Rôle modifié, Accès modifié, Autorisé à se connecter, Empêché de se connecter, Mot de passe changé, Mot de passe réinitialisé avec la clé de récupération, Numéro de compte affiché, Téléphone jumelé, Téléphone retiré, Sauvegarde, Réglages de sauvegarde et Données exportées.

## Ajouter un utilisateur {#add-user}

Seul un administrateur peut ajouter un utilisateur.

1. Cliquez sur **Ajouter un utilisateur**.
2. Remplissez le formulaire (voir plus bas).
3. Cliquez sur **Enregistrer**. Le bouton devient disponible une fois le nom, le nom d'utilisateur et les deux mots de passe remplis, et les deux mots de passe identiques.
4. Notez ou imprimez la clé de récupération du nouvel utilisateur, qui n'est affichée qu'une fois. Voir [La clé de récupération du nouvel utilisateur](users#new-recovery-key).

### Formulaire Ajouter un utilisateur {#add-user-fields}

- **Nom** : le nom affiché dans les écrans et dans le journal d'activité, comme Sam. Obligatoire. Il peut être changé plus tard.
- **Nom d'utilisateur** : ce que la personne tape pour se connecter, comme sam. Obligatoire, sans espaces. Il ne doit pas déjà être utilisé par un autre utilisateur, majuscules ou non. Il ne peut pas être changé plus tard.
- **Rôle** : Administrateur, Membre ou Lecteur. La valeur par défaut est Membre. Une ligne sous les listes explique le rôle choisi. Voir [Rôles](users#roles).
- **Cet utilisateur est le membre du ménage** : le [membre du ménage](members) qu'est cet utilisateur, ou (aucun). Facultatif. Il lie le compte de connexion à la personne ; l'onglet Utilisateurs affiche alors « est » et le nom de la personne.
- **Nouveau mot de passe** : le mot de passe de l'utilisateur. L'indice donne la longueur minimale (au moins 12 caractères). Une phrase de quelques mots est facile à retenir et difficile à deviner.
- **Nouveau mot de passe de nouveau** : le même mot de passe encore. Tant que les deux diffèrent, le champ indique « Les deux mots de passe sont différents. »
- **Annuler** : ferme la fenêtre sans ajouter personne.

Un nouvel administrateur reçoit tout de suite les clés de tous les groupes de comptes partagés. Un nouveau membre ou lecteur ne voit que ce que vous lui donnez dans l'onglet Accès.

### La clé de récupération du nouvel utilisateur {#new-recovery-key}

@index: clé de récupération; mot de passe oublié

Une fois l'utilisateur ajouté, la fenêtre « Clé de récupération de » suivie de son nom montre une longue clé en groupes de quatre caractères. Remettez cette clé à la personne pour qu'elle l'imprime ou la note et la garde en lieu sûr. C'est le seul moyen de revenir si son mot de passe est oublié, et elle ne sera plus affichée.

- **Imprimer** : imprime une page avec la clé, le nom du ménage et l'explication, par la fenêtre d'impression du système. La clé n'est pas enregistrée dans un fichier.
- **Copier** : copie la clé, par exemple pour la coller dans un gestionnaire de mots de passe. Ne la gardez pas dans un fichier sur le même ordinateur.
- **La clé est conservée** : ferme la fenêtre une fois la clé notée en lieu sûr.

Voir [La clé de récupération](security#recovery-key) pour savoir comment la clé sert.

## Modifier l'utilisateur {#edit-user}

Cliquez sur **Modifier** sur la fiche d'un utilisateur.

- **Nom** : le nom affiché de l'utilisateur. Chaque utilisateur peut changer le sien ; un administrateur peut changer celui de n'importe qui.

Les administrateurs voient aussi :

- **Cet utilisateur est le membre du ménage** : lier ou délier le membre du ménage qu'est cet utilisateur.
- **Rôle** : changer le rôle de l'utilisateur. Vous ne pouvez pas changer votre propre rôle ; la liste est grisée sur votre propre fiche. La ligne en dessous explique le rôle choisi.
- **Peut se connecter** : décochez-le pour empêcher l'utilisateur de se connecter sans le supprimer. Ses entrées passées restent, avec son nom. Cochez-le de nouveau pour le laisser revenir. Il n'est pas affiché sur votre propre fiche : vous ne pouvez pas vous bloquer vous-même.

Cliquez sur **Enregistrer** pour appliquer les changements, ou sur **Annuler** pour les laisser tomber.

Quand un utilisateur cesse d'être administrateur, il ne garde que les groupes partagés qu'on lui a donnés explicitement dans l'onglet Accès. Quand un utilisateur devient administrateur, il reçoit tous les groupes partagés.

> Remarque : Il n'y a aucun moyen de supprimer un utilisateur. L'empêcher de se connecter garde intact l'historique de qui a fait quoi.

## Changer mon mot de passe {#change-password}

@index: changer le mot de passe; nouveau mot de passe

Chaque utilisateur peut changer son propre mot de passe.

1. Cliquez sur **Changer mon mot de passe…** en haut de l'écran.
2. Tapez votre **Mot de passe actuel**.
3. Tapez le **Nouveau mot de passe**, d'au moins 12 caractères.
4. Tapez-le de nouveau dans **Nouveau mot de passe de nouveau**.
5. Cliquez sur **Enregistrer**. Le bouton devient disponible une fois les trois champs remplis et les deux nouveaux mots de passe identiques.

Si le mot de passe actuel est erroné, l'application indique « Le mot de passe actuel n'est pas le bon. » et rien ne change.

Votre clé de récupération reste la même et fonctionne encore si vous oubliez le nouveau mot de passe. Un administrateur ne peut pas changer le mot de passe d'un autre utilisateur : un utilisateur qui a oublié le sien utilise sa clé de récupération à l'écran de déverrouillage. Voir [Mot de passe oublié](security#forgot-password).

## Qui peut faire quoi sur cet écran {#permissions}

- Chaque utilisateur : voir la liste des utilisateurs, modifier son propre nom, changer son propre mot de passe, voir le tableau Accès des groupes qu'il peut ouvrir, voir sa propre activité.
- Le propriétaire d'un groupe privé : donner ou retirer l'accès à ce groupe.
- Les administrateurs : ajouter des utilisateurs, modifier n'importe quel utilisateur, changer les rôles, empêcher ou permettre la connexion, lier les utilisateurs aux membres du ménage, donner accès aux groupes partagés, voir l'activité de tout le monde.
