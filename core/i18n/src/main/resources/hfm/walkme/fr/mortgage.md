# Hypothèques
@about: Ajoutez votre hypothèque avec ce que vous devez, saisissez ses conditions, et inscrivez les versements et les renouvellements.

## Ajouter le compte hypothécaire {#account}
@screen: ACCOUNTS
@target: accounts.add
@done: shown account.dialog
@manual: loans#set-up-loan-account

L’hypothèque est un compte. À l’écran **Comptes**, cliquez sur **Ajouter un compte**.

## Ce que vous devez {#owed}
@target: account.dialog.save
@done: added account
@manual: loans#set-up-loan-account

Choisissez le **Type** **Prêt hypothécaire**. Pour le **Solde d’ouverture**, inscrivez ce que vous devez en montant négatif, comme -350000, et pour la **Date d’ouverture**, le début du terme en cours. Cliquez sur **Enregistrer**.

## Ouvrir Prêts et hypothèques {#open}
@screen: LOANS
@done: screen
@manual: loans#loans-screen

Dans le menu, ouvrez **Placements et emprunts**, puis **Prêts et hypothèques**.

## Choisir l’hypothèque {#choose}
@screen: LOANS
@target: loans.row
@done: shown loans.terms
@manual: loans#loan-list

Cliquez sur l’hypothèque dans la liste à gauche. Tant que ses conditions ne sont pas saisies, c’est indiqué en rouge.

## Saisir les conditions {#terms}
@target: loans.terms
@done: shown loans.termsDialog
@manual: loans#loan-terms

Cliquez sur **Saisir les conditions**.

## Les conditions du terme en cours {#fields}
@target: loans.termsDialog.save
@manual: loans#loan-terms

- **Capital** : le solde au début du terme en cours.
- **Taux annuel (%)** et **Type de taux**.
- **Intérêts composés** : **Semestriellement (hypothèques canadiennes)** pour un taux fixe ; bien des taux variables sont composés chaque mois : vérifiez votre contrat.
- **Amortissement (années)** : le temps qui reste pour tout rembourser.
- **Fréquence des versements**, **Premier versement** et **Versement du prêteur (s’il est connu)**, pour que le calendrier corresponde aux relevés du prêteur.
- **Fin du terme (renouvellement)** : un rappel vous est donné avant le renouvellement.
- **Payé depuis** : le compte d’où viennent les versements.

Cliquez sur **Enregistrer**. Le calendrier, le prochain versement et la date de remboursement complet s’affichent.

## Inscrire chaque versement {#payment}
@screen: LOANS
@target: loans.recordPayment
@manual: loans#record-payment

Cliquez sur **Inscrire un versement** : les intérêts, le capital, les taxes foncières et l’assurance sont calculés à partir de ce que vous devez. Cliquez sur **Enregistrer**. Si les versements arrivent avec votre relevé bancaire comme virements vers l’hypothèque, vous n’en avez pas besoin.

## Au renouvellement {#renew}
@screen: LOANS
@target: loans.renew
@manual: loans#renew

À la fin du terme, cliquez sur **Renouveler** et saisissez le nouveau taux et la nouvelle fin du terme. Pour comparer d’abord des remboursements anticipés ou un autre versement, utilisez **Et si…**.
