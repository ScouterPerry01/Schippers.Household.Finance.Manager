# Microsoft Store listing: RANN's Roost

Texts for Partner Center (DIST-01, DIST-07). Each field is marked with its Partner Center name and
limit; `tools/dev/check_store_texts.py` checks the limits. Price, markets and age rating are the
owner's choices in Partner Center.

- Privacy policy URL: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en (French: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr)
- Website: https://www.rann.ca/rann-apps/rann-roost (source code: https://github.com/ScouterPerry01/Schippers.Household.Finance.Manager)
- Support contact: info-rann-apps@NorthMail.ca
- Category: Personal finance
- Screenshots: `docs/store/screenshots/desktop-en/` and `desktop-fr/` (1440 × 900, eight per language, in listing order: dashboard, credit card register, spending by category, income tax estimate, investments, contacts, medical claims, budgets). Retake them with `./gradlew :app:desktop:storeScreenshots -Plang=en` (then `fr`), which draws the sample household offscreen.

## English (en-CA)

### Short description (max 1,000)

Your household's finances, on your own computer. Accounts, budgets, bills, investments, registered plans, mortgages, taxes with an income tax estimate, medical claims, contacts and everything you own, for every province and territory, in English and French. Your data stays encrypted on your computer: no account to create, no advertising, no analytics.

### Description (max 10,000)

RANN's Roost keeps a Canadian household's complete finances on your own computer, encrypted, in English and French, for every province and territory.

EVERYDAY BOOKS
• Bank accounts, credit cards, cash, loans, investments and registered plans, in Canadian dollars or any other currency, with Bank of Canada exchange rates.
• Import statements (OFX, QFX, QBO and CSV) and your full history from Quicken, GnuCash or Moneydance (QIF). Categories filled in from a payee's habits are marked so you can review them in one pass.
• Reconcile each statement to the cent: automatic matching, one statement line matched to several transactions or the reverse, a running difference and a saved report.
• Categories, rules, split transactions, transfers, named templates, changes to many transactions at once and fast keyboard entry.
• Bills and subscriptions with reminders, budgets with rollover, savings goals inside an account, and alerts for a low balance, a card near its limit or unusual activity.
• A cash flow forecast of your bank accounts over the coming weeks.

RECEIPTS AND DOCUMENTS
• Receipts, bills and statements read on your computer, from the phone or from saved emails, kept in an encrypted vault and matched to their transactions.
• Optional AI reading with your own Anthropic account and key: receipts split by item with their sales taxes, and bank, card and investment statements read into the books. It is off until you turn it on; you see each page, can hide any part of it, and nothing is sent until you choose Send.

TAXES
• One Taxes screen: the slips each person should receive (T4, T5, T3, RL slips and more), donations and their receipts, instalments, and a year-end package for your return or your accountant.
• An income tax estimate for each person, for every province and territory: tuition, carry-forwards, the disability amount, refundable credits, the OAS recovery tax, minimum tax and Quebec's health contributions.
• Sales taxes kept with each purchase, pay stubs split into earnings and deductions, and refunds linked to their purchase.
• Every rate, limit and threshold by effective date and province, with official figures and their sources, which you can review and adjust.

INVESTMENTS AND RETIREMENT
• Holdings, adjusted cost base across accounts, capital gains, and investment income estimated for your T3, T5, RL-3 and RL-16 slips.
• Time-weighted and personal rates of return, asset allocation with rebalancing.
• RRSP, spousal RRSP, TFSA, FHSA, RESP with CESG and provincial grants, RRIF and LIF minimums, defined benefit and contribution pensions, CPP/QPP and OAS.
• Mortgages and loans with Canadian semi-annual compounding, prepayments, renewals and what-if scenarios.
• Optional daily prices for stocks, ETFs, crypto-assets and precious metals; watch-only Bitcoin wallets.

HOME, HEALTH AND FAMILY
• Medical plans with coordination of benefits between spouses, claims from submission to payment with their deadlines, and the best 12-month period for the medical expense tax credit.
• Home inventory, appliances, vehicles, RVs and boats: warranties, insurance policies, maintenance schedules, contractors, home projects and cost of ownership.
• Health records, pets, a trip log for a vehicle's work share and medical travel, and a calendar for appointments, refills and renewals.
• Contacts: the doctors, banks, advisors and contractors you deal with, linked to the accounts and records they serve.
• Family money: shared expenses and settling up, loans between family members and children's allowances.
• Side income: invoices, rental properties and credit card rewards.
• An emergency and estate summary: where the will and papers are, who to call, and every account, policy and plan, saved as a protected PDF to hand over.

REPORTS
• Reports with charts and drill-down, a custom report builder, reports by person or for chosen accounts, and the year in review.
• Saved and scheduled reports, exported to PDF, Excel or CSV.

THE WHOLE HOUSEHOLD
• Several users, each with their own password, and private account groups that other users cannot open.
• RANN's Roost Mobile, the free Android companion, photographs receipts and bills, notes quick expenses and voice notes, shows what is coming up and the month's budgets, and reminds you of bills, appointments and refills. It sends to your computer over your home Wi-Fi, or through a cloud folder or email of your own, encrypted end to end.
• Encrypted scheduled backups, tested after every backup, and a full export in open formats.
• A built-in manual with pictures, help for every screen and a getting started guide.

PRIVATE BY DESIGN
Your data is encrypted with AES-256 and stays on your computer: there is no RANN account, no RANN cloud, no advertising and no analytics. Price downloads and AI reading stay off until you turn them on. RANN's Roost does not connect to your bank: you import the statements your bank provides.

Tax figures, including the income tax estimate, are organizational aids, not tax advice. RANN's Roost is free software (GPL-3.0); its source code is on GitHub.

### What's new in this version (max 1,500)

First release of RANN's Roost.

### Product features (up to 20, max 200 each)

1. Bank accounts, credit cards, loans and investments in any currency, with Bank of Canada exchange rates
2. Import OFX, QFX, QBO and CSV statements, and your Quicken, GnuCash or Moneydance history (QIF)
3. Reconcile statements to the cent, with automatic matching, several lines or transactions matched together and saved reports
4. Bills, subscriptions, budgets, savings goals and account alerts, with reminders
5. Income tax estimate for each person, for every province and territory
6. Taxes screen: expected slips, donations, instalments and a year-end package for your return
7. Optional AI reading of receipts and statements with your own Anthropic key; nothing is sent until you choose
8. Adjusted cost base, capital gains and investment income for T3, T5, RL-3 and RL-16 slips
9. RRSP, TFSA, FHSA, RESP, RRIF, LIF and pensions, with contribution room and minimum withdrawals
10. Mortgages with Canadian compounding, prepayments and renewals
11. Medical plans and claims with coordination of benefits between spouses
12. Home inventory, vehicles, warranties, insurance, maintenance and contractors
13. Contacts linked to the accounts and records they serve
14. Emergency and estate summary, saved as a protected PDF
15. Custom, saved and scheduled reports with charts, exported to PDF, Excel or CSV
16. Several users with private, encrypted account groups
17. Free Android companion: capture receipts, see what is coming up and get reminders
18. Encrypted backups, tested after every backup
19. Built-in manual with pictures, in English and French
20. Your data stays encrypted on your computer: no account, no RANN cloud, no ads

### Search terms (7 terms, max 30 characters each)

budget; personal finance; Quicken; income tax; RRSP TFSA; household; Canada

### Copyright and trademark info (max 200)

© 2026 Perry Schippers, trading as RANN. RANN's Roost is free software under GPL-3.0-or-later; the name and logo are not covered by that licence.

## Français (fr-CA)

### Brève description (max 1 000)

Les finances de votre ménage, sur votre propre ordinateur. Comptes, budgets, factures, placements, régimes enregistrés, prêts hypothécaires, impôts avec une estimation de l'impôt sur le revenu, frais médicaux, contacts et tous vos biens, pour chaque province et territoire, en français et en anglais. Vos données restent chiffrées sur votre ordinateur : aucun compte à créer, aucune publicité, aucun outil d'analyse.

### Description (max 10 000)

RANN's Roost tient l'ensemble des finances d'un ménage canadien sur votre propre ordinateur, chiffrées, en français et en anglais, pour chaque province et territoire.

LA COMPTABILITÉ DE TOUS LES JOURS
• Comptes bancaires, cartes de crédit, argent comptant, prêts, placements et régimes enregistrés, en dollars canadiens ou dans toute autre monnaie, avec les taux de change de la Banque du Canada.
• Importation des relevés (OFX, QFX, QBO et CSV) et de tout votre historique de Quicken, GnuCash ou Moneydance (QIF). Les catégories reprises des habitudes d'un bénéficiaire sont marquées pour que vous les vérifiiez d'un seul coup.
• Rapprochement de chaque relevé au cent près : jumelage automatique, une ligne de relevé jumelée à plusieurs opérations ou l'inverse, écart en continu et rapport enregistré.
• Catégories, règles, opérations ventilées, virements, modèles nommés, modification de plusieurs opérations à la fois et saisie rapide au clavier.
• Factures et abonnements avec rappels, budgets avec report, objectifs d'épargne à l'intérieur d'un compte, et alertes pour un solde bas, une carte près de sa limite ou une activité inhabituelle.
• Une prévision de trésorerie de vos comptes bancaires pour les semaines à venir.

REÇUS ET DOCUMENTS
• Reçus, factures et relevés lus sur votre ordinateur, depuis le téléphone ou à partir de courriels enregistrés, gardés dans un coffre chiffré et jumelés à leurs opérations.
• Lecture par IA facultative avec votre propre compte et votre propre clé Anthropic : reçus ventilés par article avec leurs taxes de vente, et relevés bancaires, de carte et de placement lus dans les livres. Elle reste désactivée tant que vous ne l'activez pas; vous voyez chaque page, pouvez en masquer toute partie, et rien n'est envoyé avant que vous choisissiez Envoyer.

IMPÔTS
• Un seul écran Impôts : les feuillets que chaque personne devrait recevoir (T4, T5, T3, relevés RL et plus), les dons et leurs reçus, les acomptes, et une trousse de fin d'année pour votre déclaration ou votre comptable.
• Une estimation de l'impôt sur le revenu de chaque personne, pour chaque province et territoire : frais de scolarité, reports, montant pour personnes handicapées, crédits remboursables, impôt de récupération de la PSV, impôt minimum et cotisations santé du Québec.
• Taxes de vente gardées avec chaque achat, talons de paie ventilés en gains et retenues, et remboursements liés à leur achat.
• Chaque taux, limite et seuil par date d'entrée en vigueur et par province, avec les chiffres officiels et leurs sources, que vous pouvez revoir et ajuster.

PLACEMENTS ET RETRAITE
• Avoirs, prix de base rajusté entre les comptes, gains en capital et revenus de placement estimés pour vos feuillets T3, T5, RL-3 et RL-16.
• Rendements pondérés dans le temps et personnels, répartition de l'actif et rééquilibrage.
• REER, REER de conjoint, CELI, CELIAPP, REEE avec la SCEE et les subventions provinciales, minimums des FERR et FRV, régimes de retraite à prestations ou à cotisations déterminées, RRQ/RPC et PSV.
• Prêts hypothécaires et autres prêts avec l'intérêt composé semestriellement à la canadienne, paiements anticipés, renouvellements et simulations.
• Cours quotidiens facultatifs des actions, des FNB, des cryptoactifs et des métaux précieux; portefeuilles Bitcoin en lecture seule.

MAISON, SANTÉ ET FAMILLE
• Régimes d'assurance médicaments et soins avec coordination des prestations entre conjoints, réclamations de l'envoi au paiement avec leurs délais, et la meilleure période de 12 mois pour le crédit d'impôt pour frais médicaux.
• Inventaire de la maison, électroménagers, véhicules, VR et bateaux : garanties, polices d'assurance, calendriers d'entretien, entrepreneurs, projets à la maison et coût de possession.
• Dossiers de santé, animaux, un registre des déplacements pour l'usage professionnel d'un véhicule et les déplacements médicaux, et un calendrier pour les rendez-vous, les renouvellements d'ordonnance et les échéances.
• Contacts : les médecins, banques, conseillers et entrepreneurs avec qui vous faites affaire, liés aux comptes et aux dossiers qu'ils servent.
• Argent en famille : dépenses partagées et règlement des comptes, prêts entre membres de la famille et allocations des enfants.
• Revenus d'appoint : factures, immeubles locatifs et récompenses de cartes de crédit.
• Un sommaire d'urgence et de succession : où sont le testament et les papiers, qui appeler, et chaque compte, police et régime, enregistré en PDF protégé à remettre.

RAPPORTS
• Rapports avec graphiques et détails, un générateur de rapports personnalisés, des rapports par personne ou pour les comptes choisis, et le bilan de l'année.
• Rapports enregistrés et planifiés, exportés en PDF, Excel ou CSV.

TOUT LE MÉNAGE
• Plusieurs utilisateurs, chacun avec son mot de passe, et des groupes de comptes privés que les autres ne peuvent pas ouvrir.
• RANN's Roost Mobile, le compagnon Android gratuit, photographie les reçus et les factures, note les dépenses rapides et les notes vocales, montre ce qui s'en vient et les budgets du mois, et vous rappelle les factures, les rendez-vous et les renouvellements d'ordonnance. Il envoie à votre ordinateur par le Wi-Fi de la maison, ou par un dossier infonuagique ou un courriel à vous, chiffré de bout en bout.
• Sauvegardes chiffrées planifiées, vérifiées après chaque sauvegarde, et une exportation complète dans des formats ouverts.
• Un manuel intégré avec images, de l'aide pour chaque écran et un guide de démarrage.

CONFIDENTIEL DÈS LA CONCEPTION
Vos données sont chiffrées en AES-256 et restent sur votre ordinateur : pas de compte RANN, pas de nuage RANN, pas de publicité, pas d'outil d'analyse. Les téléchargements de cours et la lecture par IA restent désactivés tant que vous ne les activez pas. RANN's Roost ne se connecte pas à votre banque : vous importez les relevés qu'elle vous fournit.

Les chiffres fiscaux, y compris l'estimation de l'impôt, sont une aide à l'organisation et non des conseils fiscaux. RANN's Roost est un logiciel libre (GPL-3.0); son code source est sur GitHub.

### Nouveautés de cette version (max 1 500)

Première version de RANN's Roost.

### Caractéristiques du produit (jusqu'à 20, max 200 chacune)

1. Comptes bancaires, cartes de crédit, prêts et placements dans toute monnaie, avec les taux de change de la Banque du Canada
2. Importation des relevés OFX, QFX, QBO et CSV, et de votre historique Quicken, GnuCash ou Moneydance (QIF)
3. Rapprochement des relevés au cent près, avec jumelage automatique, plusieurs lignes ou opérations jumelées ensemble et rapports enregistrés
4. Factures, abonnements, budgets, objectifs d'épargne et alertes de compte, avec rappels
5. Estimation de l'impôt sur le revenu de chaque personne, pour chaque province et territoire
6. Écran Impôts : feuillets attendus, dons, acomptes et trousse de fin d'année pour votre déclaration
7. Lecture par IA facultative des reçus et des relevés avec votre propre clé Anthropic; rien n'est envoyé sans votre choix
8. Prix de base rajusté, gains en capital et revenus de placement pour les feuillets T3, T5, RL-3 et RL-16
9. REER, CELI, CELIAPP, REEE, FERR, FRV et régimes de retraite, avec droits de cotisation et retraits minimums
10. Prêts hypothécaires avec l'intérêt composé à la canadienne, paiements anticipés et renouvellements
11. Régimes et réclamations médicales avec coordination des prestations entre conjoints
12. Inventaire de la maison, véhicules, garanties, assurances, entretien et entrepreneurs
13. Contacts liés aux comptes et aux dossiers qu'ils servent
14. Sommaire d'urgence et de succession, enregistré en PDF protégé
15. Rapports personnalisés, enregistrés et planifiés avec graphiques, exportés en PDF, Excel ou CSV
16. Plusieurs utilisateurs avec des groupes de comptes privés et chiffrés
17. Compagnon Android gratuit : capturez vos reçus, voyez ce qui s'en vient et recevez des rappels
18. Sauvegardes chiffrées, vérifiées après chaque sauvegarde
19. Manuel intégré avec images, en français et en anglais
20. Vos données restent chiffrées sur votre ordinateur : pas de compte, pas de nuage RANN, pas de publicité

### Termes de recherche (7 termes, max 30 caractères chacun)

budget; finances personnelles; Quicken; impôt sur le revenu; REER CELI; ménage; Canada

### Droits d'auteur et marques (max 200)

© 2026 Perry Schippers, faisant affaire sous le nom de RANN. RANN's Roost est un logiciel libre (GPL-3.0 ou ultérieure); le nom et le logo ne sont pas visés par cette licence.
