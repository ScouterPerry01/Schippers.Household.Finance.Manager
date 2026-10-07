# Taxes foncières municipales
@about: Créez votre facture de taxes foncières, puis entrez le compte de taxes de chaque année avec ses versements.

## Ouvrir Factures {#open}
@screen: BILLS BillsTab.ALL
@done: screen
@manual: bills#instalments

Les taxes foncières arrivent une fois par année et se paient en versements, aux dates imprimées sur le compte de taxes. Dans le menu, ouvrez **Argent**, puis **Factures**.

## Ajouter la facture de taxes foncières {#add}
@screen: BILLS
@target: bills.add
@done: shown bill.dialog
@manual: bills#instalments

Cliquez sur **Ajouter une facture**.

## La décrire {#describe}
@target: bill.dialog.save
@done: added bill
@manual: bills#classification

- **Nom** : par exemple Taxes foncières.
- **Maison ou entreprise** : Maison. **Catégorie de facture** : la catégorie du logement ; **Sous-catégorie** : Taxes foncières. Elle règle pour vous la **Répétition** à **Versements à dates fixes**.
- **Payée à partir de** : le compte avec lequel vous la payez.
- **Montant** : le total de l’année, ou un montant typique.

Cliquez sur **Enregistrer**.

## Rouvrir la facture {#edit}
@screen: BILLS BillsTab.ALL
@target: bills.edit
@done: shown bill.dialog
@manual: bills#statements

Quand le compte de taxes arrive, trouvez la facture à l’onglet **Toutes les factures** et cliquez sur **Modifier**.

## Ajouter le compte de taxes {#statement}
@target: bills.addStatement
@done: shown bills.statement
@manual: bills#statements

Sous **États de compte**, cliquez sur **Ajouter un état de compte**. Vous pouvez aussi saisir le compte papier à l’écran Documents ; voir le guide Saisir et traiter une facture.

## Entrer les versements {#instalments}
@target: bills.addInstalment
@manual: bills#instalments

Remplissez le montant et les dates à partir du compte de taxes. Cliquez sur **Ajouter un versement** pour chaque versement, avec son **Échéance** et son **Montant**, puis cliquez sur **Enregistrer**.

Certaines villes envoient deux comptes par année, comme un compte provisoire et un compte final : ajoutez chacun comme son propre état de compte.

## Payer chaque versement {#pay}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@manual: bills#mark-paid

Chaque versement est maintenant une échéance de la facture, avec ses rappels, affichée comme versement 2 de 3. Payez chacun avec **Marquer payée**, en entier ou en partie.

Tant que le compte de l’année suivante n’est pas entré, les versements de l’année suivante sont proposés aux mêmes dates avec les montants de cette année, marqués comme estimés.
