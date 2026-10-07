# Importer un relevé ou un fichier Quicken
@about: Faites entrer les opérations téléchargées de votre banque, ou tout votre historique de Quicken.

## Télécharger le relevé {#download}
@manual: accounts#import-statement

Sur le site Web de votre banque, téléchargez les opérations du compte, de préférence en OFX, QFX ou QBO (format Quicken, Microsoft Money ou QuickBooks) ; sinon en CSV.

Vous venez plutôt de Quicken, GnuCash ou Moneydance? Exportez de ce programme un fichier QIF avec tous les comptes, et passez à l’étape Importer de Quicken.

## Ouvrir le compte {#open-account}
@screen: ACCOUNTS
@target: accounts.row
@done: shown register.import
@manual: accounts#register

À l’écran **Comptes**, cliquez sur le compte visé par le relevé. Son registre s’ouvre à droite.

## Importer le fichier {#import}
@target: register.import
@done: added statement
@manual: accounts#import-statement

Cliquez sur **Importer un relevé…** et choisissez le fichier téléchargé.

Un fichier CSV ouvre une fenêtre pour confirmer quelle colonne est laquelle : comparez la proposition avec l’aperçu, cochez **La première ligne contient le nom des colonnes** si c’est le cas, et choisissez **Importer**. La disposition est retenue pour la prochaine fois.

## Vérifier ce qui a été importé {#results}
@target: reconcile.screen
@manual: accounts#import-results

Le rapprochement s’ouvre avec un résumé de l’importation : les nouvelles opérations, celles déjà présentes et celles qui demandent une décision. Les catégories remplies d’après les habitudes d’un bénéficiaire sont marquées à vérifier.

Suivez le guide Rapprocher un relevé pour le terminer maintenant, ou cliquez sur **Retour au registre** pour le faire plus tard.

## Importer de Quicken {#quicken}
@screen: ACCOUNTS
@target: accounts.quicken
@done: shown quicken.dialog
@manual: accounts#quicken-import

Pour un historique Quicken, cliquez sur **Importer de Quicken…** sous la liste des comptes et choisissez le fichier QIF.

## Choisir où va chaque compte {#quicken-accounts}
@target: quicken.dialog
@manual: accounts#quicken-accounts

- **Dates dans le fichier** : **Mois d’abord (03/14/2024)** ou **Jour d’abord (14/03/2024)**, quand le fichier ne le dit pas.
- **Enregistrer dans** : le groupe de comptes des nouveaux comptes.
- Pour chaque compte Quicken : cochez-le, puis choisissez **Importer dans** un nouveau compte ou un compte existant, et vérifiez son **Type** et sa devise.

Cliquez sur **Importer**. Importer de nouveau le même fichier n’ajoute rien de ce qui est déjà là.
