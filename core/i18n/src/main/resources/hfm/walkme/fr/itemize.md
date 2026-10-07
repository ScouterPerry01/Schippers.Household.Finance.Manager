# Détailler un reçu
@about: Divisez un reçu en ses articles, chacun avec sa catégorie et sa part des taxes de vente.

## Ouvrir le reçu {#open}
@screen: DOCUMENTS DocumentsTab.INBOX
@target: documents.review
@done: shown documents.window
@manual: documents#itemize

Détaillez un reçu quand un même commerce a vendu des choses de genres différents, comme de l’épicerie et des produits ménagers. À l’onglet **À vérifier** de l’écran Documents, cliquez sur **Vérifier** sur le reçu.

Pour ventiler plutôt une opération que vous tapez dans un registre, utilisez **Détailler…** dans son formulaire de saisie.

## Vérifier le total {#total}
@target: documents.window
@manual: documents#document-details

Vérifiez que le **Type** est **Reçu** et que le **Total** correspond au reçu : les articles et les taxes que vous tapez doivent l’égaler.

## Ouvrir Détailler {#itemize}
@target: documents.itemize
@done: shown itemize.dialog
@manual: documents#itemize

Cliquez sur **Détailler…**, à côté de **Nouvelle opération à partir de ce document**.

## Taper les articles {#items}
@target: itemize.add
@manual: documents#itemize

Pour chaque article, tapez son **Article** et son **Montant** avant taxes, choisissez sa **Catégorie** et cochez les pastilles des taxes qui s’y appliquent, comme le reçu les indique. **Ajouter un article** ajoute une ligne. Tapez un rabais en montant négatif.

## Les taxes {#taxes}
@target: itemize.dialog
@manual: documents#itemize

Sous **Taxes de vente sur le reçu**, tapez la TPS, la TVH, la TVQ ou la TVP imprimée sur le reçu. La ligne en dessous montre le total en cours et s’il correspond au total du reçu.

## Utiliser les articles {#use}
@target: itemize.use
@done: shown documents.transaction
@manual: documents#itemize

Quand ils correspondent, cliquez sur **Utiliser ces articles**. Le formulaire de nouvelle opération s’ouvre avec une ligne de ventilation par catégorie.

## Enregistrer l’opération {#save}
@target: documents.transaction.save
@done: added filed
@manual: documents#new-transaction

Choisissez **Payé avec** et **Pour**, puis cliquez sur **Enregistrer**. L’opération est créée avec ses lignes de ventilation et ses taxes de vente, et le reçu y est joint.
