# Premiers pas avec les comptes et les opérations

Ce guide vous mène d’un ménage vide à des comptes qui concordent avec la banque, en cinq étapes. Il n’utilise que les options utiles dès le premier jour ; chaque étape renvoie à la description complète dans [Comptes](accounts).

## Avant de commencer {#before-you-start}

@index: premières étapes; créer les comptes; nouveau ménage

Ayez sous la main :

- le dernier relevé de chaque compte à suivre (compte chèques, épargne, cartes de crédit, marges de crédit, prêts), avec sa date et son solde de clôture ;
- si possible, un moyen de télécharger vos opérations sur le site de votre banque, en fichiers OFX, QFX, QBO ou CSV ;
- si vous utilisiez Quicken, une exportation QIF de tous ses comptes.

> Conseil : Ajoutez d’abord les personnes du ménage dans [Membres du ménage](members), pour pouvoir indiquer à qui appartient chaque compte.

## Étape 1 : ajouter vos comptes {#add-accounts}

1. Dans le menu, ouvrez **Comptes** et choisissez **Ajouter un compte**.
2. Tapez un **Nom du compte** que vous reconnaîtrez, comme Compte chèques conjoint.
3. Choisissez le **Type** : Compte chèques, Compte d’épargne, Carte de crédit, Prêt hypothécaire, etc. Il ne pourra plus changer.
4. Laissez **Devise (p. ex. CAD, USD, BTC)** à CAD, sauf si le compte est dans une autre devise.
5. Pour le **Solde d’ouverture** et la **Date d’ouverture**, prenez le solde et la date de clôture du dernier relevé. Pour une carte de crédit ou un prêt, tapez ce que vous devez en montant négatif, comme -1250.
6. Au besoin, choisissez l’**Institution**, tapez le **Numéro de compte** (seuls les quatre derniers chiffres sont affichés) et cochez les **Titulaires**.
7. Choisissez **Enregistrer**. Le compte paraît dans la liste et son registre s’ouvre.

Recommencez pour chaque compte. Pour une carte de crédit, choisissez ensuite **Détails de la carte** dans son registre pour entrer les taux et les frais annuels. Tous les champs sont décrits dans [Champs du formulaire de compte](accounts#account-fields).

## Étape 2 : reprendre votre historique {#bring-in-history}

Vous pouvez partir d’aujourd’hui et n’ajouter que les nouvelles opérations, ou reprendre ce que vous avez déjà :

- De Quicken : choisissez **Importer de Quicken…** sous la liste des comptes, choisissez le fichier QIF, vérifiez où va chaque compte Quicken et choisissez **Importer**. Voir [Importer de Quicken](accounts#quicken-import). Dans ce cas, vous pouvez sauter l’étape 1 pour les comptes que Quicken crée.
- De la banque : ouvrez le compte, choisissez **Importer un relevé…** et choisissez le fichier téléchargé. Pour un fichier CSV, vérifiez les colonnes une fois ; la disposition est mémorisée. Voir [Importer un relevé](accounts#import-statement).

Les opérations importées arrivent déjà classées selon vos règles et vos bénéficiaires, et déjà compensées.

## Étape 3 : entrer les opérations au fil des jours {#enter-transactions}

Pour les dépenses en argent comptant, les chèques, ou tout ce que vous voulez dans les livres avant l’arrivée du relevé :

1. Ouvrez le compte et allez au formulaire sous le registre.
2. Vérifiez la **Date** (+ et - changent le jour).
3. Tapez le **Bénéficiaire**. Choisir un bénéficiaire connu remplit le montant et la catégorie de la dernière fois.
4. Choisissez la **Catégorie**, ou Virement : et l’autre compte quand l’argent passe d’un de vos comptes à un autre, comme le paiement d’une carte de crédit.
5. Tapez le montant en **Paiement** (argent qui sort) ou en **Dépôt** (argent qui entre).
6. Appuyez sur Entrée. Le formulaire se vide pour la suivante, en gardant la date.

Quand le relevé sera importé, ces opérations seront jumelées à ses lignes au lieu d’être ajoutées deux fois. Pour plusieurs catégories sur un même reçu, utilisez **Ventiler…**. Voir [Entrer une opération](accounts#enter-transaction).

## Étape 4 : classer {#categorize}

Les catégories rendent les budgets et les rapports utiles.

1. Dans le tableau de bord, cherchez sous À vérifier une ligne comme 5 opérations n’ont pas de catégorie.
2. Dans chaque registre, cliquez sur une opération qui affiche (non catégorisé), choisissez sa **Catégorie** et enregistrez.
3. Pour les bénéficiaires qui reviennent chaque mois, ajoutez une règle dans [Règles de catégorie](rules) pour que les prochaines importations se classent d’elles-mêmes, ou donnez une catégorie par défaut au bénéficiaire dans [Bénéficiaires](payees).

## Étape 5 : rapprocher chaque relevé {#reconcile-statements}

Le rapprochement prouve que le compte concorde avec la banque au cent près, et verrouille la période.

1. Importez le relevé (étape 2). Le rapprochement s’ouvre. Avec un relevé papier, choisissez **Rapprocher…**, puis **Entrer un relevé papier**, et tapez la date et le solde de clôture.
2. Vérifiez la **Date du relevé** et le **Solde de clôture du relevé** ; corrigez-les et choisissez **Appliquer** au besoin.
3. Sous À vérifier, réglez chaque ligne : **Même opération**, **Ajouter comme nouvelle**, **Jumeler à une opération inscrite** ou **Ignorer**.
4. Sous Inscrites, mais absentes de ce relevé, cochez ce qui figure sur le relevé ; laissez décochés les chèques pas encore encaissés.
5. Quand l’Écart est nul, choisissez **Terminer le rapprochement**.

La liste des comptes affiche ensuite Rapproché au avec la date du relevé. Voir [Rapprocher un relevé](accounts#reconcile).

## Garder le rythme {#keeping-up}

@index: routine; routine mensuelle

Une routine simple garde les livres justes :

- Environ chaque semaine, entrez les reçus et les dépenses en argent comptant, ou envoyez-les du téléphone.
- Chaque mois, à la sortie d’un relevé, importez-le et rapprochez-le.
- Jetez un œil au tableau de bord : À vérifier liste les factures en retard, les lignes de relevé en attente, les opérations sans catégorie et les comptes non rapprochés depuis plus de 45 jours. Voir [Tableau de bord](dashboard).

## Et ensuite {#next}

- Inscrivez vos factures et vos jours de paie pour recevoir un rappel avant l’échéance : [Factures](bills).
- Fixez des objectifs de dépenses mensuels : [Budgets](budgets).
- Suivez vos placements et vos régimes enregistrés : [Placements](investments) et [Régimes enregistrés](plans).
- Entrez les conditions de vos prêts et de votre prêt hypothécaire : [Prêts et hypothèques](loans).
- Voyez où va l’argent : [Rapports](reports).
