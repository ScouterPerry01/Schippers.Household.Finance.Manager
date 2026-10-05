# Animaux

L’écran **Animaux** garde une fiche pour chaque animal du ménage : ses renseignements, sa micropuce, sa licence municipale, son assurance et ce qu’il coûte. De chaque fiche, vous accédez au dossier de santé de l’animal et ajoutez un rendez-vous chez le vétérinaire. Il se trouve dans le groupe **Maison et famille** du menu.

## L’écran en bref {#overview}
@index: animaux de compagnie; chien; chat; fiche de l’animal

En haut :

- **Afficher les anciens animaux** : décoché par défaut. Cochez-le pour voir aussi les animaux marqués **N’est plus dans le ménage**.
- **Ajouter un animal** : ouvre un formulaire vierge. Voir [Ajouter ou modifier un animal](pets#pet-form).

Les animaux appartiennent à tout le ménage, pas à un groupe de comptes : tous les utilisateurs les voient. Les administrateurs et les membres peuvent les ajouter, les modifier et les supprimer. Un utilisateur au rôle **Lecteur** voit les fiches, mais n’a ni **Ajouter un animal** ni **Supprimer**, et l’enregistrement d’un changement donne une erreur. Voir [Utilisateurs](users).

En dessous se trouve une fiche par animal. Quand il n’y en a aucun, l’écran indique « Aucun animal pour l’instant. »

## La fiche de l’animal {#pet-card}

Chaque fiche affiche :

- le nom, avec « (n’est plus avec nous) » pour un ancien animal ;
- l’espèce et la race, le sexe (avec « stérilisé » ou « stérilisée » si coché), l’âge, la couleur et le propriétaire (« à Sam »). L’âge est en mois avant deux ans, en années ensuite ; « environ » est ajouté quand la date de naissance est approximative ;
- « Micropuce » et son numéro, s’il est inscrit ;
- la ligne de la licence municipale, « Licence numéro (municipalité) : à renouveler d’ici le date », et celle de l’assurance, « Assurance assureur, police numéro : à renouveler d’ici le date ». Chaque ligne paraît seulement si sa date est entrée. Elle est en gras dans les 30 jours qui précèdent la date, et en rouge avec « échue le » une fois la date passée ;
- « Cette année : montant · 12 derniers mois : montant » : ce que l’animal a coûté, d’après le registre. Voir [Coûts](pets#costs) ;
- les quatre plus grandes catégories de coûts des 12 derniers mois, ou « Aucun coût inscrit. Dans le registre, choisissez cet animal dans « Pour ». » s’il n’y en a pas.

Et quatre boutons :

- **Dossier de santé** : ouvre l’écran [Santé](health) avec cet animal choisi. Voir [Dossier de santé](pets#health-records).
- **Ajouter un rendez-vous** : ajoute un rendez-vous au calendrier pour l’animal. Voir [Rendez-vous](pets#appointments).
- **Coûts** : ouvre les coûts sur plusieurs années. Voir [Coûts](pets#costs).
- **Modifier** : ouvre le formulaire de l’animal.

## Ajouter ou modifier un animal {#pet-form}

Le formulaire s’intitule **Ajouter un animal** ou **Modifier l’animal**. Seul le nom est obligatoire ; **Enregistrer** reste inaccessible tant qu’il est vide.

### Renseignements {#details}

- **Nom** : le nom de l’animal. Obligatoire.
- **Animal** : **Chien** (par défaut), **Chat**, **Oiseau**, **Poisson**, **Lapin**, **Petit rongeur**, **Reptile**, **Cheval** ou **Autre**. Il paraît sur la fiche et après le nom dans la liste de l’écran Santé, par exemple « Rex (chien) ».
- **Race** : par exemple « Labrador retriever ».
- **Couleur** : par exemple « noir et blanc » ; utile si l’animal se perd.
- **Sexe** : **Mâle**, **Femelle**, ou « (aucun) » si vous ne savez pas.
- **Stérilisé** : cochez-le quand l’animal a été stérilisé. Bien des municipalités demandent moins cher pour la licence d’un animal stérilisé.
- **Date de naissance** : année-mois-jour. Elle donne l’âge sur la fiche.
- **Approximative** : cochez-la quand la date de naissance est une estimation, par exemple pour un animal adopté. La fiche affiche alors « environ » devant l’âge.
- **Numéro de micropuce** : le numéro de la puce, affiché sur la fiche. Gardez-le à portée de main pour mettre le registre à jour si vous déménagez.

### Licence municipale {#licence}
@index: licence de chien; licence de chat; enregistrement municipal

Sous **Licence municipale** :

- **Numéro de licence** : le numéro de la médaille ou de la licence.
- **Municipalité** : la ville qui l’a délivrée.
- **Échéance** : quand la licence doit être renouvelée. Elle commande la ligne de licence de la fiche et le rappel. Voir [Rappels](pets#reminders).

### Assurance pour animaux {#insurance}

Sous **Assurance pour animaux** :

- **Assureur** : la compagnie.
- **Numéro de police**.
- **Date de renouvellement** : quand la police se renouvelle. Elle commande la ligne d’assurance de la fiche et le rappel.

Les primes elles-mêmes sont des paiements du registre ; choisissez l’animal dans « Pour » quand vous les inscrivez, pour qu’elles comptent dans ses coûts.

### Propriétaire, notes et statut {#owner-status}

- **Propriétaire** : un membre du ménage, ou **Tout le ménage** (par défaut). Affiché sur la fiche.
- **Notes** : nourriture, consignes du vétérinaire, nom du gardien, tout ce qui est utile.
- **N’est plus dans le ménage** (en modification) : cochez-le quand l’animal est mort ou a été donné. Voir [Anciens animaux](pets#former-pets).
- **Supprimer** (en modification) : voir [Supprimer un animal](pets#delete).

**Enregistrer** garde les changements ; **Annuler** ferme le formulaire sans eux.

## Anciens animaux {#former-pets}

Un animal marqué **N’est plus dans le ménage** est caché de l’écran, de la liste **Personne ou animal** de l’écran Santé et des rappels. Son dossier et ses coûts sont conservés. Cochez **Afficher les anciens animaux** pour revoir sa fiche, marquée « (n’est plus avec nous) », et décochez **N’est plus dans le ménage** s’il revient.

## Supprimer un animal {#delete}

**Supprimer** dans le formulaire (absent pour un lecteur) demande « Supprimer nom? Les opérations inscrites pour cet animal sont conservées. » Une fois confirmé, l’animal est supprimé. Les opérations qui le nommaient restent dans leurs comptes. C’est définitif ; marquer l’animal comme n’étant plus dans le ménage est habituellement préférable, puisque son historique est conservé.

## Dossier de santé {#health-records}
@index: dossier vétérinaire; vaccins de l’animal; médicaments de l’animal

**Dossier de santé** ouvre l’écran [Santé](health) avec l’animal choisi dans **Personne ou animal**. Vous y inscrivez ses médicaments et renouvellements, ses vaccins (la rage, par exemple, avec la prochaine dose), ses allergies, ses problèmes de santé et ses examens, exactement comme pour une personne. Son vétérinaire, son toiletteur et sa pension vont à l’onglet **Professionnels**, avec les types **Vétérinaire**, **Toiletteur** et **Pension ou gardien d’animaux**.

Le dossier de santé d’un animal est gardé dans le premier groupe partagé, puisqu’un animal appartient au ménage. Le bouton **Sommaire de santé…** n’est pas offert pour un animal.

## Rendez-vous {#appointments}

**Ajouter un rendez-vous** ouvre le formulaire de rendez-vous du calendrier avec la date du jour, le type **Animaux** et l’animal choisi sous **Qui**. Indiquez de quoi il s’agit (par exemple « Examen annuel »), la date et l’heure, l’endroit, le professionnel et les rappels, puis enregistrez. Le rendez-vous paraît au [Calendrier](calendar) et à l’onglet **Rendez-vous** de l’animal dans l’écran Santé. Les champs du formulaire sont décrits au chapitre [Calendrier](calendar).

## Coûts {#costs}
@index: dépenses pour l’animal; frais de vétérinaire; nourriture pour animaux

Ce que coûte un animal vient du registre : chaque ligne d’opération où l’animal est choisi dans « Pour ». La nourriture, les frais de vétérinaire, le toilettage, les primes d’assurance et la licence comptent tous quand ils nomment l’animal. Les montants sont convertis en dollars canadiens au taux du jour ; un remboursement réduit le coût.

La fiche montre le total de l’année (depuis le 1er janvier) et des 12 derniers mois, avec les quatre plus grandes catégories des 12 derniers mois.

### La boîte des coûts {#costs-dialog}

**Coûts** ouvre « Ce que coûte nom », du 1er janvier d’il y a quatre ans jusqu’à aujourd’hui :

- **Par année** : le total de chaque année.
- **Par catégorie** : le total de chaque catégorie, du plus grand au plus petit ; les lignes sans catégorie paraissent comme « (non catégorisé) ».
- **Total**.
- Une remarque quand des montants dans une autre devise n’ont pas de taux de change et sont exclus.
- « Comprend toutes les opérations inscrites pour lui (le champ « Pour » ou « Véhicule » du registre), en dollars canadiens au taux du jour. »

**Fermer** la ferme.

## Rappels {#reminders}
@index: renouvellement de licence; renouvellement d’assurance; rappel

Pour chaque animal encore dans le ménage, l’**Échéance** de la licence municipale et la **Date de renouvellement** de l’assurance donnent un rappel à partir de 30 jours avant la date, qui reste tant que la date est passée et n’a pas été changée, par exemple « Rex : licence municipale (Sherbrooke) dans 12 jours » ou « Rex : assurance de l’animal (assureur) en retard de 3 jours ». Il paraît en haut de la fenêtre et dans la notification du système, et mène à l’écran Animaux. Les dates paraissent aussi au [Calendrier](calendar).

Une fois le renouvellement fait, modifiez l’animal et entrez la nouvelle date : le rappel cesse.

## Contacts {#linked-contacts}

@index: contact; contact lié; Lier un contact

Le formulaire d’un animal enregistré se termine par Contacts : le vétérinaire, un toiletteur ou une pension (Service) et l’assureur. L’Assureur tapé sur l’animal reste tel quel.

Cliquez sur un contact pour ouvrir sa page dans [Contacts](contacts) ; **Retirer le lien** enlève le lien, et **Lier un contact…** en choisit un, dans son rôle, ou crée un **Nouveau contact…** et le lie. Voir [Les contacts dans les autres écrans](contacts#on-other-screens).
