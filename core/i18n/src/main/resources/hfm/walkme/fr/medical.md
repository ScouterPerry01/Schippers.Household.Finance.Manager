# Frais médicaux et réclamations
@about: Ajoutez vos régimes d’assurance santé, inscrivez chaque dépense, suivez ses réclamations, et obtenez les chiffres pour le crédit d’impôt.

## Ouvrir Réclamations médicales {#open}
@screen: MEDICAL MedicalTab.PLANS
@done: screen
@manual: medical#overview

Dans le menu, ouvrez **Maison et famille**, puis **Réclamations médicales**. Commencez par vos régimes à l’onglet **Régimes**.

## Ajouter un régime {#add-plan}
@screen: MEDICAL MedicalTab.PLANS
@target: medical.addPlan
@manual: medical#plan-form

Cliquez sur **Ajouter un régime**. Choisissez le **Type de régime**, comme **Assurance collective (employeur)** ou **Soins dentaires**, l’**Assureur** et les numéros inscrits sur la carte du régime.

## Qui il couvre {#order}
@screen: MEDICAL MedicalTab.PLANS
@manual: medical#coverage-order

Pour chaque personne, choisissez **paie en premier**, **paie en second** ou **Non couvert**. Quand chaque conjoint a un régime, chaque régime paie en premier pour son propre membre et en second pour l’autre. Enregistrez le régime.

## Ce qu’il paie {#coverage}
@screen: MEDICAL MedicalTab.PLANS
@target: medical.addCoverage
@manual: medical#coverage-form

Cliquez sur **Ajouter une couverture** pour chaque type de soins de votre brochure d’avantages sociaux : le **Type de soins**, le **Pourcentage payé**, la franchise et le maximum annuel. Un régime ne paie que les types de soins pour lesquels il a une ligne.

## Inscrire une dépense {#expense}
@screen: MEDICAL MedicalTab.EXPENSES
@target: medical.addExpense
@manual: medical#expense-form

À l’onglet **Dépenses et réclamations**, cliquez sur **Ajouter une dépense**. Choisissez la **Personne soignée** et le **Type de soins**, la date du service et le **Coût**, et laissez **Compte pour le crédit d’impôt pour frais médicaux** coché, sauf si l’ARC ne l’accepte pas. Cliquez sur **Enregistrer**.

## Envoyer la réclamation {#send}
@screen: MEDICAL MedicalTab.EXPENSES
@target: medical.submit
@manual: medical#send-claim

La dépense indique à quel régime réclamer d’abord, avant quelle date, et environ combien devrait vous revenir. Une fois la réclamation envoyée, cliquez sur **Envoyer à** suivi du nom du régime, et inscrivez le **Montant réclamé**.

## Quand le régime paie {#paid}
@screen: MEDICAL MedicalTab.EXPENSES
@target: medical.recordPayment
@manual: medical#record-payment

Cliquez sur **Inscrire le paiement** avec ce que le régime a payé, ou 0 s’il a refusé. Si un deuxième régime couvre la personne, il est proposé ensuite pour ce qui reste.

## Le crédit d’impôt {#credit}
@screen: REPORTS
@manual: medical#tax-credit

Le rapport Frais médicaux, sous **Rapports**, rassemble ce que vous avez payé de votre poche, trouve la meilleure période de 12 mois et indique quel conjoint devrait le demander. La trousse fiscale de fin d’année utilise les mêmes chiffres.
