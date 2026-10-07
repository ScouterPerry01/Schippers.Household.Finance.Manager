# Comptes de placement et titres détenus
@about: Ajoutez un compte de courtage ou de régime, faites entrer ce qu’il détient, et gardez ses cours et ses opérations à jour.

## Ajouter le compte de placement {#account}
@screen: ACCOUNTS
@target: accounts.add
@done: shown account.dialog
@manual: investments#investments-screen

Les comptes de placement se créent à l’écran **Comptes**. Cliquez sur **Ajouter un compte**.

## Son type et ses titulaires {#type}
@target: account.dialog.save
@done: added account
@manual: accounts#investment-accounts

Choisissez le **Type** : **Compte de courtage (non enregistré)**, **REER**, **CELI**, **CELIAPP**, **REEE**, etc. Cochez ses **Titulaires** : ils décident à qui comptent les gains en capital et les droits de cotisation. Inscrivez l’argent du compte comme **Solde d’ouverture**. Cliquez sur **Enregistrer**.

## Ouvrir Placements {#open}
@screen: INVESTMENTS
@done: screen
@manual: investments#account-list

Dans le menu, ouvrez **Placements et emprunts**, puis **Placements**, et cliquez sur le compte dans la liste.

## Importer de votre courtier {#import}
@screen: INVESTMENTS
@target: investments.import
@manual: investments#import-statement

Si votre courtier vous permet de télécharger un fichier OFX, QFX ou CSV, cliquez sur **Importer un relevé…** et choisissez-le : les titres, achats, ventes et dividendes entrent d’un coup, et importer de nouveau n’ajoute rien en double.

Sinon, saisissez à la main ce que détient le compte, comme suit.

## Ajouter une opération {#add}
@screen: INVESTMENTS
@target: investments.add
@done: shown investments.txn
@manual: investments#transaction-dialog

Cliquez sur **Ajouter une opération**.

## Les unités que vous détenez déjà {#units}
@target: investments.txn.save
@manual: investments#transaction-fields

Pour ce que vous déteniez avant de commencer, choisissez le **Type** Unités transférées en entrée, choisissez le **Titre** (ou **Nouveau titre…** pour l’ajouter), la **Quantité**, et leur coût d’origine tiré de votre relevé : le prix de base rajusté en dépend. Pour un achat, choisissez Achat, avec le **Cours** et la commission.

Cliquez sur **Enregistrer**. Chaque placement paraît alors dans l’onglet **Titres détenus**, avec sa valeur marchande et son gain.

## Garder les cours à jour {#prices}
@screen: INVESTMENTS
@target: investments.updatePrices
@manual: investments#update-prices

Cliquez sur **Mettre à jour les cours** pour inscrire le cours du jour de chaque titre. Les cours des actions et des FNB peuvent aussi être téléchargés : activez-le dans Taux et cours.
