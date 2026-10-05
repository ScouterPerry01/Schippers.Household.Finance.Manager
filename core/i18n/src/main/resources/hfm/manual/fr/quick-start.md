# Démarrage rapide

Ce chapitre vous mène du premier démarrage de RANN's Roost à un ménage prêt à l'emploi, en une demi-heure environ. Il laisse de côté tout ce qui peut attendre. Chaque étape renvoie au chapitre qui raconte toute l'histoire, si vous le souhaitez.

## Avant de commencer {#before}

@index: ce qu'il faut; premières étapes

Ayez sous la main :

- un endroit pour le ménage sur votre ordinateur, comme votre dossier Documents ;
- un mot de passe d'au moins 12 caractères, sans votre nom d'utilisateur, dont vous vous souviendrez (quelques mots à la suite font très bien l'affaire) ;
- une imprimante, ou un crayon et du papier, pour la clé de récupération ;
- les soldes actuels de vos comptes bancaires et de vos cartes ;
- un relevé récent téléchargé du site de votre banque, en fichier OFX, QFX, QBO ou CSV (cherchez « Télécharger les opérations » ou « Exporter » sur le site de la banque) ;
- pour les sauvegardes, un disque externe ou un dossier infonuagique (OneDrive, Google Drive, Dropbox) ;
- au besoin, un téléphone Android où RANN's Roost Mobile est installée.

## Étape 1 : Démarrer l'application {#first-start}

@index: premier démarrage; question sur les mises à jour

Démarrez RANN's Roost. L'écran de départ, **Bienvenue**, s'affiche.

Sur une copie Linux installée à partir d'un paquet .deb ou .rpm ou d'une AppImage, l'application demande d'abord **Vérifier les mises à jour?**. Cliquez sur **Vérifier une fois par jour** pour être averti des nouvelles versions, ou sur **Ne pas vérifier**. Seule la vérification sort de l'ordinateur ; rien sur votre ménage n'est envoyé. Vous pourrez changer d'avis plus tard sous [À propos](about). Les autres copies, comme celles du Microsoft Store ou de Flathub, ne posent pas la question : la boutique les met à jour.

> Conseil : les boutons **English** et **Français** en haut changent la langue en tout temps. L'application retient votre choix sur cet ordinateur.

## Étape 2 : Créer votre ménage {#create}

@index: créer un ménage; nouveau ménage

1. Cliquez sur **Créer un nouveau ménage**.
2. Cliquez sur **Choisir un dossier…** et choisissez où le ménage sera rangé, par exemple votre dossier Documents. L'application y crée un dossier au nom du ménage.
3. Remplissez le formulaire :
  - **Nom du ménage** : un nom pour le ménage, comme Famille Tremblay.
  - **Province ou territoire** : où vous habitez. Ce choix fixe les jours fériés bancaires, les catégories par défaut, les subventions provinciales et les formulaires fiscaux. Rien n'est choisi au départ : choisissez le vôtre.
  - **Votre nom** : votre nom tel qu'il paraît dans l'application.
  - **Nom d'utilisateur** : le nom court que vous taperez pour vous connecter, sans espaces.
  - **Mot de passe principal** et **Confirmer le mot de passe principal** : au moins 12 caractères, sans votre nom d'utilisateur, tapés deux fois. Ce sont les [règles des mots de passe](security#password-rules) intégrées; un administrateur peut les changer plus tard.
4. Cliquez sur **Créer le ménage**.

Vous êtes l'administrateur du ménage : vous pouvez tout faire, y compris ajouter d'autres utilisateurs plus tard. [S'y retrouver](basics#create-screen) décrit chaque champ de cet écran.

## Étape 3 : Conserver votre clé de récupération {#recovery-key}

@index: clé de récupération; mot de passe oublié

L'écran suivant, **Votre clé de récupération**, présente une longue clé faite de lettres et de chiffres en groupes de quatre.

> Important : aucun serveur ne peut réinitialiser votre mot de passe. Si vous l'oubliez, cette clé est le seul moyen de retrouver l'accès à votre ménage. Personne, pas même RANN, ne peut récupérer vos données sans elle.

1. Cliquez sur **Imprimer** pour l'imprimer, ou notez-la. Rangez le papier en lieu sûr, loin de cet ordinateur.
2. Cliquez sur **J'ai conservé ma clé de récupération**. Le ménage s'ouvre au tableau de bord.

## Étape 4 : Suivre le guide des premiers pas {#guide}

@index: guide des premiers pas; étapes de mise en route

Le tableau de bord présente une carte **Premiers pas** de cinq étapes : les personnes, les comptes, les factures et la paie, un premier relevé et le téléphone. Chaque étape a un bouton qui ouvre le bon écran, et la prochaine étape à faire est en gras. La carte compte ce qui est fait et disparaît une fois les quatre premières étapes faites. **Masquer ce guide** la retire pour vous ; **Afficher de nouveau le guide Premiers pas**, dans Affichage et accessibilité, la fait revenir.

Les étapes ci-dessous suivent le même ordre.

## Étape 5 : Ajouter les personnes {#people}

@index: membres du ménage; personnes

1. Cliquez sur **Ajouter des personnes** dans le guide. L'écran **Membres du ménage** s'ouvre.
2. Cliquez sur **Ajouter**, puis entrez le **Nom** de la personne, son **Lien** (Adulte, Enfant ou Autre personne à charge) et, si vous le voulez, sa **Date de naissance (AAAA-MM-JJ)**.
3. Cliquez sur **Enregistrer**. Recommencez pour chaque personne, vous compris.

Les personnes permettent aux comptes, aux dépenses, aux dossiers de santé et aux impôts d'appartenir à quelqu'un. Voir [Membres du ménage](members).

## Étape 6 : Ajouter vos comptes {#accounts}

@index: ajouter un compte; solde d'ouverture

1. Cliquez sur **Ajouter un compte** dans le guide. La fenêtre **Ajouter un compte** s'ouvre.
2. Entrez le **Nom du compte** (comme Compte chèques conjoint), choisissez le **Type** (Compte chèques, Compte d'épargne, Carte de crédit, Prêt hypothécaire, REER, CELI, etc.) et laissez **Devise (p. ex. CAD, USD, BTC)** à CAD, sauf si le compte est dans une autre monnaie.
3. Entrez le **Solde d'ouverture** et la **Date d'ouverture** : le solde à cette date. Le plus simple est le solde d'aujourd'hui avec la date d'aujourd'hui. Pour une carte de crédit, entrez ce que vous devez.
4. Cochez les **Titulaires** du compte et cliquez sur **Enregistrer**.

Ajoutez vos principaux comptes de la même façon à partir de l'écran **Comptes**, avec **Ajouter un compte**. Le type et la devise ne peuvent plus être changés ensuite ; vérifiez-les avant d'enregistrer. Voir [Comptes](accounts).

## Étape 7 : Ajouter les factures et la paie {#bills}

@index: ajouter une facture; jour de paie; abonnements

1. Cliquez sur **Ajouter des factures** dans le guide. L'écran **Factures** s'ouvre ; cliquez sur **Ajouter une facture**.
2. Choisissez le **Type** : Facture pour l'argent que vous payez, Revenu pour votre paie, Virement pour les transferts réguliers entre vos comptes.
3. Entrez le **Nom** (Hydro, Loyer, Paie), le compte qui paie ou qui reçoit, le **Montant**, et si **Le montant est** Fixe, Variable ou Estimé.
4. Choisissez la **Répétition** (Chaque mois, Aux deux semaines, etc.) et la **Première échéance**.
5. Dans **Me le rappeler (jours avant)**, entrez par exemple 7, 1 pour un rappel une semaine et un jour à l'avance.
6. Cliquez sur **Enregistrer**.

Les rappels s'affichent ensuite en haut de l'application et en notifications de l'ordinateur. Voir [Factures](bills).

## Étape 8 : Importer un premier relevé {#statement}

@index: importer un relevé; OFX; QFX; CSV

1. Cliquez sur **Ouvrir un compte** dans le guide, ou allez à **Comptes**, et cliquez sur le compte.
2. Cliquez sur **Importer un relevé…** et choisissez le fichier téléchargé de votre banque.
3. Pour un fichier CSV, une fenêtre demande quelle colonne contient quoi (date, montant, description). Vérifiez l'aperçu et cliquez sur **Importer**. L'application retient la disposition pour la prochaine fois.
4. Les opérations arrivent, classées quand l'application peut deviner leur catégorie, et l'écran de rapprochement s'ouvre. Un résumé indique combien ont été ajoutées, jumelées ou demandent une décision.

Vous pouvez vous arrêter ici et faire le rapprochement plus tard. Voir [Premiers pas avec l'argent](start-money) et [Comptes](accounts).

## Étape 9 : Régler les sauvegardes {#backups}

@index: sauvegardes; dossier de sauvegarde

Dès le premier jour, l'application est réglée pour faire une sauvegarde chaque jour dans un dossier à côté du ménage, nommé comme lui suivi de « - backups ». Ce dossier est sur le même disque : il ne vous protège donc pas si le disque tombe en panne. Déplacez-le :

1. Dans le menu, ouvrez **Réglages** et cliquez sur **Sauvegardes**.
2. Cliquez sur **Choisir le dossier…** et choisissez un dossier sur un disque externe, un lecteur réseau ou un dossier infonuagique.
3. Laissez **Sauvegardes automatiques** à Chaque jour et **Versions à conserver** tel quel, puis cliquez sur **Enregistrer**.
4. Cliquez sur **Sauvegarder maintenant** pour faire la première.

Les sauvegardes restent chiffrées. Voir [Sauvegardes](backups).

## Étape 10 : Jumeler votre téléphone (facultatif) {#phone}

@index: jumeler un téléphone; code QR

1. Assurez-vous que le téléphone est sur le même Wi-Fi que l'ordinateur.
2. Cliquez sur **Jumeler un téléphone** dans le guide, ou ouvrez **Réglages**, puis **Téléphones**, et cliquez sur **Jumeler un téléphone**. Un code QR s'affiche.
3. Sur le téléphone, ouvrez RANN's Roost Mobile, touchez « Jumeler à un ordinateur » et numérisez le code dans les 10 minutes.
4. Si Windows demande d'autoriser l'application sur les réseaux, autorisez-la sur les réseaux privés.

Dès lors, les reçus et factures que vous photographiez arrivent dans l'onglet **À vérifier** de **Documents** chaque fois que le ménage est ouvert. Voir [Premiers pas avec le téléphone](start-phone).

## Pour aller plus loin {#next}

@index: prochaines étapes

Votre ménage fonctionne. Quand vous serez prêt à aller plus loin, lisez le chapitre de premiers pas de chaque domaine :

- [Les réglages](start-settings) : catégories, bénéficiaires, règles, utilisateurs et affichage.
- [Les contacts](start-contacts) : rassembler et classer les personnes et organisations avec qui vous faites affaire.
- [L'argent](start-money) : le registre, le rapprochement et les virements.
- [Les factures et budgets](start-bills-budgets) : factures, budgets et objectifs d'épargne.
- [Les documents](start-documents) : reçus, factures et le coffre.
- [Le calendrier](start-calendar) : rendez-vous et rappels.
- [Les placements](start-investing) : placements, régimes enregistrés et prêts.
- [Les rapports](start-reports) : valeur nette, dépenses et vos propres rapports.
- [Les impôts](start-taxes) : feuillets, dons et dossier de fin d'année.
- [La maison et la famille](start-home-family) : santé, animaux, véhicules, la maison et l'argent en famille.
- [Le téléphone](start-phone) : tout ce que RANN's Roost Mobile peut envoyer.

Pour vous retrouver dans la fenêtre principale, le menu et la case de recherche, voir [S'y retrouver](basics).
