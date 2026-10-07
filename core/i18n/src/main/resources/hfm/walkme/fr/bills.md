# Factures
@about: Créez une facture qui revient, avec ses rappels, puis marquez-la payée quand vous la payez.

## Ouvrir Factures {#open}
@screen: BILLS BillsTab.AGENDA
@done: screen
@manual: bills#overview

Dans le menu, ouvrez **Argent**, puis **Factures**. L’onglet **À payer** montre ce qui est en retard et ce qui arrive bientôt à échéance.

## Ajouter une facture {#add}
@screen: BILLS
@target: bills.add
@done: shown bill.dialog
@manual: bills#bill-form

Cliquez sur **Ajouter une facture**.

## Nom et compte {#name}
@target: bill.dialog
@manual: bills#type-and-name

- **Type** : **Facture**, **Revenu** (comme votre paie) ou **Virement** (comme vers l’épargne).
- **Nom** : comme vous voulez la voir, par exemple Hydro-Québec ou Loyer.
- **Maison ou entreprise**, **Catégorie de facture** et **Sous-catégorie** : à quoi sert la facture ; la sous-catégorie remplit la **Catégorie** pour vous.
- **Payée à partir de** : le compte d’où sort l’argent. Il ne peut pas être changé plus tard.

## Montant et calendrier {#schedule}
@target: bill.dialog
@manual: bills#repeats

- **Montant** et **Le montant est** : **Fixe** pour le même montant chaque fois, **Variable** pour une facture comme l’électricité, **Estimé** pour une estimation.
- **Répétition** : par exemple **Chaque mois** ou **Aux deux semaines**.
- **Première échéance** : la prochaine date où elle est due.

## Rappels et enregistrement {#reminders}
@target: bill.dialog.save
@done: added bill
@manual: bills#reminders

**Me le rappeler (jours avant)** : par exemple 7, 1 pour une semaine avant et la veille. Une facture due aujourd’hui ou en retard est toujours rappelée.

Cliquez sur **Enregistrer**. La prochaine échéance de la facture apparaît à l’onglet **À payer**, dans le calendrier et dans la prévision de trésorerie.

## La marquer payée {#pay}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@done: shown bills.pay
@manual: bills#mark-paid

Quand vous la payez, cliquez sur **Marquer payée** sur sa ligne.

## Inscrire le paiement {#record}
@target: bills.pay.save
@manual: bills#mark-paid

Vérifiez la **Date du paiement** et le **Montant**, puis cliquez sur **Enregistrer**. Une opération est ajoutée au compte, et la prochaine échéance prend sa place. Quand vous importez le relevé bancaire, sa ligne est jumelée à cette opération au lieu d’être ajoutée deux fois.
