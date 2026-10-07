# Prêts et marges de crédit
@about: Préparez un prêt auto ou personnel avec son calendrier, et une marge de crédit avec sa limite et son taux.

## Ajouter le compte de prêt {#account}
@screen: ACCOUNTS
@target: accounts.add
@done: shown account.dialog
@manual: accounts#loan-accounts

Un prêt est un compte. À l’écran **Comptes**, cliquez sur **Ajouter un compte**.

## Ce que vous devez {#owed}
@target: account.dialog.save
@done: added account
@manual: loans#set-up-loan-account

Choisissez le **Type** : **Prêt** pour un prêt auto, étudiant ou personnel, **Marge de crédit** ou **Marge de crédit hypothécaire** pour une marge de crédit. Inscrivez ce que vous devez comme **Solde d’ouverture** négatif, comme -18500, à sa **Date d’ouverture**. Cliquez sur **Enregistrer**.

## Les conditions d’un prêt {#loan-terms}
@screen: LOANS
@target: loans.row
@manual: loans#loan-terms

Un prêt à versements fixes reçoit son calendrier à **Prêts et hypothèques**, dans le menu sous **Placements et emprunts**. Cliquez sur le prêt, puis sur **Saisir les conditions**.

## Remplir les conditions {#terms}
@target: loans.terms
@done: shown loans.termsDialog
@manual: loans#loan-terms

Inscrivez le **Capital**, le **Taux annuel (%)**, les **Intérêts composés** (habituellement chaque mois pour un prêt), l’**Amortissement (années)**, la **Fréquence des versements** et le **Premier versement**, et le compte d’où il est **Payé depuis**. Cliquez sur **Enregistrer** : le calendrier, le prochain versement et la date de remboursement complet s’affichent.

## Inscrire les versements {#payments}
@screen: LOANS
@target: loans.recordPayment
@manual: loans#record-payment

Cliquez sur **Inscrire un versement** pour chaque versement, sauf si les versements arrivent avec votre relevé bancaire comme virements vers le prêt.

## Une marge de crédit {#line-of-credit}
@screen: ACCOUNTS
@target: accounts.row
@done: shown register.cardDetails
@manual: accounts#credit-accounts

Une marge de crédit n’a pas de calendrier fixe : elle n’est pas dans Prêts et hypothèques. À l’écran **Comptes**, cliquez dessus pour ouvrir son registre.

## Sa limite et son taux {#limit}
@target: register.cardDetails
@manual: accounts#card-details

Cliquez sur **Détails de la carte** et inscrivez la **Limite de crédit** et le **Taux sur les achats (%)**. Le registre montre alors le crédit encore disponible, et le rapport Sommaire des dettes, sous Rapports, montre la marge avec son taux.
