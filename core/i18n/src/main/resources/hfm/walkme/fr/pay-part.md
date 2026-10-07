# Payer une facture en partie
@about: Payez une partie d’une facture maintenant et le reste plus tard, et voyez ce qui reste dû.

## Ouvrir À payer {#open}
@screen: BILLS BillsTab.AGENDA
@done: screen
@manual: bills#to-pay-tab

Dans le menu, ouvrez **Argent**, puis **Factures**, et l’onglet **À payer**.

## Taper le montant payé maintenant {#amount}
@screen: BILLS BillsTab.AGENDA
@target: bills.toPay
@manual: bills#pay-in-part

Sur la ligne de la facture, la colonne **À payer** commence à ce qui reste dû. Tapez la partie que vous payez maintenant, par exemple la moitié.

Une facture variable ou estimée dont le montant n’est pas encore indiqué est payée en entier par ce que vous payez : cliquez d’abord sur **Indiquer le montant de la facture** sur sa ligne.

## La marquer payée {#pay}
@target: bills.markPaid
@done: shown bills.pay
@manual: bills#pay-in-part

Cliquez sur **Marquer payée** sur la même ligne.

## Inscrire le paiement {#record}
@target: bills.pay.save
@manual: bills#pay-in-part

Le formulaire affiche **Reste à payer** et le montant que vous avez tapé. Vérifiez la **Date du paiement** et cliquez sur **Enregistrer**. Le paiement est inscrit comme une opération à part.

## Payer le reste plus tard {#rest}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@manual: bills#pay-in-part

La facture reste dans la liste avec le reste sous **Reste dû**, et vous êtes toujours rappelé. Quand vous payez le reste, cliquez de nouveau sur **Marquer payée** : il propose ce qui reste. Payer plus que le montant dû vous demande d’abord de confirmer.
