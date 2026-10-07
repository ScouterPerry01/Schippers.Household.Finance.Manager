# Saisir et traiter une facture
@about: Photographiez une facture avec le téléphone ou importez-la, puis inscrivez son montant sur votre facture ou créez la facture à partir d’elle.

## Sur le téléphone : numériser la facture {#scan}
@manual: phone-app#capture-tab

Sur le téléphone, touchez **Facture** à l’onglet Capturer et numérisez chaque page. Vérifiez le **Commerce ou fournisseur**, la date et le montant lus, puis touchez **Enregistrer**. La facture est envoyée à cet ordinateur quand le ménage est ouvert.

Sans téléphone, cliquez sur **Importer des fichiers…** à l’écran Documents et choisissez le PDF ou la photo de la facture.

## Ouvrir À vérifier {#review}
@screen: DOCUMENTS DocumentsTab.INBOX
@done: screen
@manual: documents#to-review-tab

Dans le menu, ouvrez **Argent**, puis **Documents**, et l’onglet **À vérifier**.

## Ouvrir la facture {#open}
@screen: DOCUMENTS DocumentsTab.INBOX
@target: documents.review
@done: shown documents.window
@manual: documents#document-window

Cliquez sur **Vérifier** sur la ligne de la facture.

## Vérifier ce qui a été lu {#details}
@target: documents.window
@manual: documents#document-details

Vérifiez que le **Type** est **Facture**, et comparez **Commerce ou fournisseur**, **Date** et **Total** avec l’image. La date d’échéance, votre numéro de compte chez le fournisseur et, sur une facture de services publics, les relevés du compteur sont lus aussi.

## L’inscrire sur votre facture {#record}
@target: documents.recordOnBill
@done: added filed
@manual: documents#record-on-bill

Quand l’application reconnaît une de vos factures, elle le dit : cliquez sur **L’inscrire comme état de compte de cette facture**. Le montant et la date d’échéance vont sur cette facture, et son échéance devient celle du document.

S’il s’agit d’une autre de vos factures, utilisez **Joindre à une facture** et choisissez-la.

## Ou créer la facture à partir d’elle {#create}
@target: documents.createBill
@done: added bill
@manual: documents#record-on-bill

Quand elle ne correspond à aucune de vos factures, cliquez sur **Créer une facture à partir de ceci**. Le formulaire de facture s’ouvre, rempli à partir du document : vérifiez le nom, **Payée à partir de** et le calendrier, puis cliquez sur **Enregistrer**. Un compte de taxes dont les versements ont été lus est réglé avec **Versements à dates fixes**.

## La payer {#pay}
@screen: BILLS BillsTab.AGENDA
@target: bills.markPaid
@manual: bills#mark-paid

L’échéance et le montant de la facture sont maintenant à l’onglet **À payer** de l’écran Factures, avec ses rappels. Cliquez sur **Marquer payée** quand vous la payez.
