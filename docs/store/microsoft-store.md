<!--
File path and name: docs/store/microsoft-store.md
Modified On Timestamp: 2026-10-10 @ 02:26 EDT
Created On Timestamp: 2026-10-03 @ 09:38 EDT
File Description: The Microsoft Store listing texts of RANN's Roost in English and French, with the caption and alt text of each screenshot.
Uses: docs/store/screenshots/desktop-en and desktop-fr; tools/dev/check_store_texts.py checks the limits.
Used By: the owner, pasting into Partner Center; docs/release-checklist.md section 7.
Purpose: Keep every Store field ready to paste, within its limit.
-->

# Microsoft Store listing: RANN's Roost

Texts for Partner Center (DIST-01, DIST-07). Each field is marked with its Partner Center name and
limit; `tools/dev/check_store_texts.py` checks the limits. Markets and age rating are the owner's choices in
Partner Center. Nothing has been published on the Microsoft Store yet: version 1.0.0 is the first submission.

- Price: CAD $19.99, one-time, from the first submission (SRS 16.2); a launch discount is a sale price in Partner Center.

- Privacy policy URL: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en (French: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr)
- Website: https://www.rann.ca/rann-apps/rann-roost (source code: https://github.com/ScouterPerry01/Schippers.Household.Finance.Manager)
- Support contact: info-rann-apps@NorthMail.ca
- Category: Personal finance
- Screenshots: `docs/store/screenshots/desktop-en/` and `desktop-fr/` (1440 × 900, eight per language, in listing order: dashboard, credit card register, spending by category, income tax estimate, investments, all bills with their columns, medical claims, budgets). Retake them with `./gradlew :app:desktop:storeScreenshots -Plang=en` (then `fr`), which draws the sample household offscreen. Each one has a caption and an alt text under "Screenshots: captions and alt text" at the end of this file.
- Store logos (Partner Center > Store listing > Store logos), in `branding/store/`, drawn by `python tools/dev/make_store_logos.py` from the logo files: `poster-1440x2160.png` (2:3 poster art), `box-2160x2160.png` (1:1 box art), `hero-3840x2160.png` and `hero-1920x1080.png` (16:9 super hero art: the kite alone, no lettering, since the Store writes the title over it), `tile-300x300.png` (1:1 app tile icon, used instead of the package icon), and `tile-150x150.png` and `tile-71x71.png` (the 1:1 150 × 150 and 71 × 71 tile icons). The name and kite keep to the top two-thirds, where the Store lays no text; the black lettering on sky blue `#3D8FCB` has a contrast of 6.0:1 (4.5:1 needed).
  - Poster and box art alt text: "The RANN's Roost logo: a Brahminy kite in flight, wings spread, between the words RANN and ROOST in black on a sky blue background." French: "Le logo de RANN's Roost : un milan sacré en vol, ailes déployées, entre les mots RANN et ROOST en noir sur un fond bleu ciel."
  - Super hero art alt text: "A Brahminy kite in flight, wings spread, against a clear sky blue background." French: "Un milan sacré en vol, ailes déployées, sur un fond bleu ciel uni."
  - App tile icons (all three sizes) alt text: "RANN's Roost icon: a Brahminy kite, white head and chestnut wings, against a sky blue background." French: "Icône de RANN's Roost : un milan sacré, tête blanche et ailes brun-roux, sur un fond bleu ciel."

## English (en-CA)

### Short description (max 1,000)

Your household's finances and family life, on your own computer. Accounts, budgets, bills with their statements, investments, registered plans, mortgages, taxes with an income tax estimate, medical claims, a family calendar with work and school schedules, seasonal checklists for the home, vehicle trips and fuel, utilities, contacts and everything you own, for every province and territory, in English and French, with step-by-step guides for the common tasks. Your data stays encrypted on your computer: no account to create, no advertising, no analytics.

### Description (max 10,000)

RANN's Roost keeps a Canadian household's complete finances on your own computer, encrypted, in English and French, for every province and territory.

EVERYDAY BOOKS
• Bank accounts, credit cards, cash, loans, investments and registered plans, in Canadian dollars or any other currency, with Bank of Canada exchange rates.
• Import statements (OFX, QFX, QBO and CSV) and your full history from Quicken, GnuCash or Moneydance (QIF). Categories filled in from a payee's habits are marked so you can review them in one pass.
• Reconcile each statement to the cent: automatic matching, one statement line matched to several transactions or the reverse, a running difference and a saved report.
• Categories, rules, split transactions, transfers, named templates, changes to many transactions at once and fast keyboard entry.
• Bills and subscriptions with reminders, each one Home or Business with its category, the account number with the company and every statement received. A utility bill's meter readings go to its meter. Pay a bill in part and the rest stays due; property taxes and other bills paid in instalments roll over to next year until the new statement comes.
• Budgets with rollover, savings goals inside an account, and alerts for a low balance, a card near its limit or unusual activity.
• A cash flow forecast of your bank accounts over the coming weeks.

RECEIPTS AND DOCUMENTS
• Receipts, bills and statements read on your computer, from the phone or from saved emails, kept in an encrypted vault and matched to their transactions.
• Optional AI reading with your own Anthropic account and key: receipts split by item with their sales taxes, and bank, card and investment statements read into the books. It is off until you turn it on; you see each page, can hide any part of it, and nothing is sent until you choose Send.
• Every page of a document, with zoom, and receipts itemized by hand into split lines with their sales taxes, without AI reading.

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
• Home inventory, appliances, vehicles, RVs, boats, pools and yards: warranties, insurance policies, maintenance by time, use or season, contractors, home projects and cost of ownership.
• A seasonal checklist for each change of season: every task across the home, cottage, vehicles, pool and yard, ticked off on the computer or the phone and recorded in the service log.
• Renovations and energy upgrades, such as insulation, a heat pump or windows, with the rebates and grants applied for and received.
• Utilities: electricity, gas and water meters compared with the same month last year, with unusual use flagged, and propane or oil tanks with a reminder to order before they run low.
• Health records, medications and pets.
• Contacts: the doctors, banks, advisors and contractors you deal with, linked to the accounts and records they serve.
• Family money: shared expenses and settling up, loans between family members, children's allowances and paid chores ticked off on the phone, and volunteer hours with a yearly total.
• Side income: invoices, with hours worked timed on the phone or entered on the computer and turned into invoice lines, rental properties and credit card rewards.
• An emergency and estate summary: where the will and papers are, who to call, and every account, policy and plan, saved as a protected PDF to hand over.

CALENDAR
• Agenda, day, week, month and year views, with appointments, bills, refills, renewals and maintenance, shown or hidden by kind and by person.
• Work and school schedules for each person, weekly or on a rotation for shift work, with holidays and exceptions, and children's activities with who drives each way and what they cost.
• Each person can bring in the calendars their phone already shows (Google, Outlook and others), each kept private, shown to others as busy only, or shared. If they choose both ways, the phone also writes the household's appointments, hours and bills into a calendar they pick. Calendar files (.ics) can be imported too.
• On the phone, an agenda of the coming 60 days, from the calendar icon: events, schedules, bills, refills, maintenance and renewals.

VEHICLES AND TRIPS
• Trips started and ended on the phone, with several stops, rest breaks, notes and photos: a location fix at the start, at each stop and at the end, never in the background. Addresses come from saved places, are typed, or are looked up if you turn that on. Distance comes from the odometer, and the purpose is suggested from the place.
• Fuel stations and EV chargers nearby, from OpenStreetMap, if you turn it on; a station can also be added by hand.
• Fuel and charging entered on the phone, with consumption shown separately for normal driving, towing and heavy loads, and the cost per km of charging compared with fuel.
• A forecast of each vehicle's fuel or charging and maintenance for the coming months, from your recent driving.
• A CRA logbook for a vehicle's work share, kilometres per province, inspection reminders, and medical travel.

REPORTS
• Reports with charts and drill-down, a custom report builder, reports by person or for chosen accounts, and the year in review.
• Saved and scheduled reports, exported to PDF, Excel or CSV.

THE WHOLE HOUSEHOLD
• Several users, each with their own password, and private account groups that other users cannot open.
• RANN's Roost Mobile, the Android companion sold separately on Google Play, photographs receipts and bills, notes quick expenses and voice notes, logs trips, fill-ups, meter readings, hours worked, chores and volunteer hours, ticks off the seasonal checklist, shows the agenda of the coming two months and the month's budgets, and reminds you of bills, appointments and refills. It locks after the time you choose and speaks English or French, whatever the phone's language. It sends to your computer over your home Wi-Fi, or through a cloud folder or email of your own, encrypted end to end.
• Encrypted scheduled backups, tested after every backup, and a full export in open formats.
• A built-in manual with pictures, help for every screen, a getting started guide, a Help menu, and Walk-Me guides that lead you step by step through common tasks, from creating the household to the year-end tax package.

PRIVATE BY DESIGN
Your data is encrypted with AES-256 and stays on your computer: there is no RANN account, no RANN cloud, no advertising and no analytics. Price downloads, AI reading and, on the phone, address lookup and stations nearby stay off until you turn them on. RANN's Roost does not connect to your bank: you import the statements your bank provides.

Tax figures, including the income tax estimate, are organizational aids, not tax advice. RANN's Roost is free software (GPL-3.0); its source code is on GitHub.

### What's new in this version (max 1,500)

First release of RANN's Roost: a Canadian household's complete finances and family life on your own computer, encrypted, in English and French.

### Product features (up to 20, max 200 each)

1. Bank accounts, credit cards, loans and investments in any currency, with Bank of Canada exchange rates
2. Import OFX, QFX, QBO and CSV statements, and your Quicken, GnuCash or Moneydance history (QIF)
3. Reconcile statements to the cent, with automatic matching and saved reports
4. Bills Home or Business with statements, account numbers, payments in part and instalments; budgets, savings goals and alerts
5. Income tax estimate for each person, for every province and territory, and a year-end package with slips and donations
6. Optional AI reading of receipts and statements with your own Anthropic key, or receipts itemized by hand; nothing is sent until you choose
7. Adjusted cost base and capital gains; RRSP, TFSA, FHSA, RESP, RRIF, LIF and pensions
8. Mortgages with Canadian compounding, prepayments and renewals
9. Medical plans and claims with coordination of benefits between spouses
10. Family calendar: agenda, day, week, month and year views, work and school schedules, and children's activities
11. Each person's phone calendars brought in, each kept private, shown as busy only or shared, and synced both ways if they choose
12. Seasonal checklists for the home, cottage, vehicles, pools and yards, and energy upgrades with their rebates
13. Trips on the phone with stops, breaks, notes and photos, stations nearby, and fuel or charging with consumption by kind of driving
14. Vehicle forecasts of fuel, charging and maintenance, and a CRA logbook for a vehicle's work share
15. Utility meters with unusual use flagged, and propane or oil tanks with a reminder to order
16. Hours worked turned into invoices, children's chores toward their allowance, and volunteer hours
17. Home inventory, warranties, insurance, contacts, and an emergency and estate summary as a protected PDF
18. Custom, saved and scheduled reports with charts, exported to PDF, Excel or CSV
19. Android companion, sold separately on Google Play: capture receipts, log trips and hours, see the agenda and get reminders
20. Walk-Me guides, several users, encrypted backups; your data stays encrypted on your computer: no account, no RANN cloud, no ads

### Search terms (7 terms, max 30 characters each)

budget; personal finance; Quicken; income tax; RRSP TFSA; family calendar; mileage log

### Copyright and trademark info (max 200)

© 2026 Perry Schippers, trading as RANN. RANN's Roost is free software under GPL-3.0-or-later; the name and logo are not covered by that licence.

## Français (fr-CA)

### Brève description (max 1 000)

Les finances et la vie de famille de votre ménage, sur votre propre ordinateur. Comptes, budgets, factures avec leurs relevés, placements, régimes enregistrés, prêts hypothécaires, impôts avec une estimation de l'impôt sur le revenu, frais médicaux, un calendrier familial avec les horaires de travail et d'école, des listes saisonnières pour la maison, les trajets et le carburant des véhicules, les services publics, les contacts et tous vos biens, pour chaque province et territoire, en français et en anglais, avec des guides pas à pas pour les tâches courantes. Vos données restent chiffrées sur votre ordinateur : aucun compte à créer, aucune publicité, aucun outil d'analyse.

### Description (max 10 000)

RANN's Roost tient l'ensemble des finances d'un ménage canadien sur votre propre ordinateur, chiffrées, en français et en anglais, pour chaque province et territoire.

LA COMPTABILITÉ DE TOUS LES JOURS
• Comptes bancaires, cartes de crédit, argent comptant, prêts, placements et régimes enregistrés, en dollars canadiens ou dans toute autre monnaie, avec les taux de change de la Banque du Canada.
• Importation des relevés (OFX, QFX, QBO et CSV) et de tout votre historique de Quicken, GnuCash ou Moneydance (QIF). Les catégories reprises des habitudes d'un bénéficiaire sont marquées pour que vous les vérifiiez d'un seul coup.
• Rapprochement de chaque relevé au cent près : jumelage automatique, une ligne de relevé jumelée à plusieurs opérations ou l'inverse, écart en continu et rapport enregistré.
• Catégories, règles, opérations ventilées, virements, modèles nommés, modification de plusieurs opérations à la fois et saisie rapide au clavier.
• Factures et abonnements avec rappels, chacun Maison ou Entreprise avec sa catégorie, le numéro de compte chez l'entreprise et chaque relevé reçu. Les relevés de compteur d'une facture de services publics vont à son compteur. Payez une facture en partie et le reste demeure dû; les taxes municipales et autres factures payées par versements sont reportées à l'année suivante jusqu'à l'arrivée du nouveau relevé.
• Budgets avec report, objectifs d'épargne à l'intérieur d'un compte, et alertes pour un solde bas, une carte près de sa limite ou une activité inhabituelle.
• Une prévision de trésorerie de vos comptes bancaires pour les semaines à venir.

REÇUS ET DOCUMENTS
• Reçus, factures et relevés lus sur votre ordinateur, depuis le téléphone ou à partir de courriels enregistrés, gardés dans un coffre chiffré et jumelés à leurs opérations.
• Lecture par IA facultative avec votre propre compte et votre propre clé Anthropic : reçus ventilés par article avec leurs taxes de vente, et relevés bancaires, de carte et de placement lus dans les livres. Elle reste désactivée tant que vous ne l'activez pas; vous voyez chaque page, pouvez en masquer toute partie, et rien n'est envoyé avant que vous choisissiez Envoyer.
• Chaque page d'un document, avec zoom, et reçus détaillés à la main en lignes ventilées avec leurs taxes de vente, sans lecture par IA.

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
• Inventaire de la maison, électroménagers, véhicules, VR, bateaux, piscine et terrain : garanties, polices d'assurance, entretien selon le temps, l'usage ou la saison, entrepreneurs, projets à la maison et coût de possession.
• Une liste saisonnière à chaque changement de saison : toutes les tâches de la maison, du chalet, des véhicules, de la piscine et du terrain, cochées sur l'ordinateur ou le téléphone et inscrites au carnet d'entretien.
• Rénovations écoénergétiques, comme l'isolation, une thermopompe ou des fenêtres, avec les remises et subventions demandées et reçues.
• Services publics : compteurs d'électricité, de gaz et d'eau comparés au même mois l'an dernier, avec la consommation inhabituelle signalée, et réservoirs de propane ou de mazout avec un rappel de commander avant qu'ils soient bas.
• Dossiers de santé, médicaments et animaux.
• Contacts : les médecins, banques, conseillers et entrepreneurs avec qui vous faites affaire, liés aux comptes et aux dossiers qu'ils servent.
• Argent en famille : dépenses partagées et règlement des comptes, prêts entre membres de la famille, allocations des enfants et tâches payées cochées sur le téléphone, et heures de bénévolat avec un total annuel.
• Revenus d'appoint : factures, avec les heures travaillées chronométrées sur le téléphone ou saisies sur l'ordinateur et transformées en lignes de facture, immeubles locatifs et récompenses de cartes de crédit.
• Un sommaire d'urgence et de succession : où sont le testament et les papiers, qui appeler, et chaque compte, police et régime, enregistré en PDF protégé à remettre.

CALENDRIER
• Vues agenda, jour, semaine, mois et année, avec les rendez-vous, factures, renouvellements d'ordonnance, échéances et entretiens, affichés ou masqués par genre et par personne.
• Horaires de travail et d'école de chaque personne, hebdomadaires ou en rotation pour le travail par quarts, avec jours fériés et exceptions, et activités des enfants avec qui conduit à l'aller et au retour et ce qu'elles coûtent.
• Chacun peut apporter les calendriers que son téléphone affiche déjà (Google, Outlook et autres), chacun gardé privé, montré aux autres comme occupé seulement, ou partagé. S'il choisit les deux sens, le téléphone inscrit aussi les rendez-vous, horaires et factures du ménage dans un calendrier de son choix. Les fichiers de calendrier (.ics) peuvent aussi être importés.
• Sur le téléphone, un agenda des 60 prochains jours, par l'icône de calendrier : événements, horaires, factures, renouvellements d'ordonnance, entretiens et échéances.

VÉHICULES ET TRAJETS
• Trajets commencés et terminés sur le téléphone, avec plusieurs arrêts, des pauses, des notes et des photos : une position au départ, à chaque arrêt et à l'arrivée, jamais en arrière-plan. Les adresses viennent des lieux enregistrés, sont tapées, ou sont trouvées si vous l'activez. La distance vient de l'odomètre, et le motif est suggéré selon le lieu.
• Stations-service et bornes de recharge à proximité, d'OpenStreetMap, si vous l'activez; une station peut aussi être ajoutée à la main.
• Carburant et recharges saisis sur le téléphone, avec la consommation montrée à part pour la conduite normale, le remorquage et les charges lourdes, et le coût au kilomètre de la recharge comparé au carburant.
• Une prévision du carburant ou de la recharge et de l'entretien de chaque véhicule pour les mois à venir, selon votre conduite récente.
• Un registre de l'ARC pour l'usage professionnel d'un véhicule, les kilomètres par province, les rappels d'inspection et les déplacements médicaux.

RAPPORTS
• Rapports avec graphiques et détails, un générateur de rapports personnalisés, des rapports par personne ou pour les comptes choisis, et le bilan de l'année.
• Rapports enregistrés et planifiés, exportés en PDF, Excel ou CSV.

TOUT LE MÉNAGE
• Plusieurs utilisateurs, chacun avec son mot de passe, et des groupes de comptes privés que les autres ne peuvent pas ouvrir.
• RANN's Roost Mobile, le compagnon Android vendu séparément sur Google Play, photographie les reçus et les factures, note les dépenses rapides et les notes vocales, inscrit les trajets, les pleins, les relevés de compteur, les heures travaillées, les tâches ménagères et les heures de bénévolat, coche la liste saisonnière, montre l'agenda des deux prochains mois et les budgets du mois, et vous rappelle les factures, les rendez-vous et les renouvellements d'ordonnance. Il se verrouille après le délai choisi et parle français ou anglais, quelle que soit la langue du téléphone. Il envoie à votre ordinateur par le Wi-Fi de la maison, ou par un dossier infonuagique ou un courriel à vous, chiffré de bout en bout.
• Sauvegardes chiffrées planifiées, vérifiées après chaque sauvegarde, et une exportation complète dans des formats ouverts.
• Un manuel intégré avec images, de l'aide pour chaque écran, un guide de démarrage, un menu Aide, et des guides Walk-Me qui vous mènent pas à pas dans les tâches courantes, de la création du ménage à la trousse fiscale de fin d'année.

CONFIDENTIEL DÈS LA CONCEPTION
Vos données sont chiffrées en AES-256 et restent sur votre ordinateur : pas de compte RANN, pas de nuage RANN, pas de publicité, pas d'outil d'analyse. Les téléchargements de cours, la lecture par IA et, sur le téléphone, la recherche d'adresse et les stations à proximité restent désactivés tant que vous ne les activez pas. RANN's Roost ne se connecte pas à votre banque : vous importez les relevés qu'elle vous fournit.

Les chiffres fiscaux, y compris l'estimation de l'impôt, sont une aide à l'organisation et non des conseils fiscaux. RANN's Roost est un logiciel libre (GPL-3.0); son code source est sur GitHub.

### Nouveautés de cette version (max 1 500)

Première version de RANN's Roost : toutes les finances et la vie familiale d'un ménage canadien sur votre propre ordinateur, chiffrées, en français et en anglais.

### Caractéristiques du produit (jusqu'à 20, max 200 chacune)

1. Comptes bancaires, cartes de crédit, prêts et placements dans toute monnaie, avec les taux de change de la Banque du Canada
2. Importation des relevés OFX, QFX, QBO et CSV, et de votre historique Quicken, GnuCash ou Moneydance (QIF)
3. Rapprochement des relevés au cent près, avec jumelage automatique et rapports enregistrés
4. Factures Maison ou Entreprise avec relevés, numéros de compte, paiements partiels et versements; budgets, objectifs d'épargne et alertes
5. Estimation de l'impôt de chaque personne, pour chaque province et territoire, et trousse de fin d'année avec feuillets et dons
6. Lecture par IA facultative des reçus et des relevés avec votre propre clé Anthropic, ou reçus détaillés à la main; rien n'est envoyé sans votre choix
7. Prix de base rajusté et gains en capital; REER, CELI, CELIAPP, REEE, FERR, FRV et régimes de retraite
8. Prêts hypothécaires avec l'intérêt composé à la canadienne, paiements anticipés et renouvellements
9. Régimes et réclamations médicales avec coordination des prestations entre conjoints
10. Calendrier familial : vues agenda, jour, semaine, mois et année, horaires de travail et d'école, et activités des enfants
11. Les calendriers du téléphone de chaque personne, chacun gardé privé, montré comme occupé seulement ou partagé, et synchronisés dans les deux sens au choix
12. Listes saisonnières pour la maison, le chalet, les véhicules, la piscine et le terrain, et rénovations écoénergétiques avec leurs remises
13. Trajets sur le téléphone avec arrêts, pauses, notes et photos, stations à proximité, et carburant ou recharge avec la consommation selon la conduite
14. Prévisions de carburant, de recharge et d'entretien des véhicules, et registre de l'ARC pour l'usage professionnel d'un véhicule
15. Compteurs de services publics avec la consommation inhabituelle signalée, et réservoirs de propane ou de mazout avec rappel de commander
16. Heures travaillées transformées en factures, tâches des enfants pour leur allocation, et heures de bénévolat
17. Inventaire de la maison, garanties, assurances, contacts, et sommaire d'urgence et de succession en PDF protégé
18. Rapports personnalisés, enregistrés et planifiés avec graphiques, exportés en PDF, Excel ou CSV
19. Compagnon Android vendu séparément sur Google Play : capturez vos reçus, inscrivez trajets et heures, consultez l'agenda et recevez des rappels
20. Guides Walk-Me, plusieurs utilisateurs, sauvegardes chiffrées; vos données restent chiffrées sur votre ordinateur : pas de compte, pas de nuage RANN, pas de publicité

### Termes de recherche (7 termes, max 30 caractères chacun)

budget; finances personnelles; Quicken; impôt sur le revenu; REER CELI; calendrier familial; registre de kilométrage

### Droits d'auteur et marques (max 200)

© 2026 Perry Schippers, faisant affaire sous le nom de RANN. RANN's Roost est un logiciel libre (GPL-3.0 ou ultérieure); le nom et le logo ne sont pas visés par cette licence.

## Screenshots: captions and alt text

Upload the pictures in this order, the English set with the English listing and the French set with the French
listing. Partner Center shows a **Caption** box under each screenshot (max 200): paste the caption there. The alt
text describes the picture for people who use a screen reader; paste it wherever an alt text or description box
is offered (Partner Center, and the same pictures on rann.ca: in Google Sites, click the picture, then the three
dots, then **Alt text**). Alt texts are kept under 250 characters so they are read in one go. They do not quote
amounts, so they stay true when the pictures are retaken.

### English 1, 1-dashboard.png: caption (max 200)

Your household at a glance: net worth, cash, what you owe, bills due this week, spending against budget and what needs your attention.

### English 1, 1-dashboard.png: alt text (max 250)

Dashboard of a sample household: cards for net worth, cash available, credit and loans owing, bills due in the next 7 days and spending this month, a list of items needing attention, a net worth chart and a top spending chart.

### English 2, 2-accounts.png: caption (max 200)

Every account in one list, with a credit card register, statement import, reconciling and quick entry of new transactions.

### English 2, 2-accounts.png: alt text (max 250)

Accounts page: chequing, savings, US dollar, credit card, mortgage and investment accounts with their balances, and a credit card register with its transactions and a form to enter a new one.

### English 3, 3-reports.png: caption (max 200)

Spending by category for the year, for everyone or one person, by tag or group of accounts, saved as your own report.

### English 3, 3-reports.png: alt text (max 250)

Spending by category report for this year: a bar for each category such as housing, taxes, transportation, food, utilities and pets, with its amount and share, beside the list of reports.

### English 4, 4-taxes.png: caption (max 200)

An income tax estimate for each person, for every province and territory, from the figures of your year-end package.

### English 4, 4-taxes.png: alt text (max 250)

Taxes page, Estimate tab: one person's income figures on the left; federal and Ontario tax, refundable credits, the refund, and the average and marginal rates on the right.

### English 5, 5-investments.png: caption (max 200)

Investments with market value, book cost and unrealized gains, beside registered plans, crypto and precious metals.

### English 5, 5-investments.png: alt text (max 250)

Investments page: non-registered accounts, crypto, precious metals and registered plans on the left; a brokerage account's holdings with quantity, price, market value, book cost and gain on the right.

### English 6, 6-bills.png: caption (max 200)

All your bills with due dates, amounts, account numbers and instalments; mark each one paid in one click.

### English 6, 6-bills.png: alt text (max 250)

Bills page, All bills tab: upcoming bills and pay with their due date, kind, category, amount due, amount to pay and amount outstanding, each with a Mark paid or Mark received button.

### English 7, 7-medical.png: caption (max 200)

Medical and dental expenses from the receipt to every claim, with the plans of both spouses in the right order.

### English 7, 7-medical.png: alt text (max 250)

Medical claims page: open expenses for each family member, with the date, the kind of care, the plan to claim from, the deadline, the amount expected back and the amount paid.

### English 8, 8-budgets.png: caption (max 200)

Monthly budgets by category, with what is left, amounts carried over and overspending shown in red.

### English 8, 8-budgets.png: alt text (max 250)

Budgets page for one month: totals spent, budgeted and remaining, and a bar for each category, with housing and transportation over budget in red, and food, restaurants and utilities within budget.

### Français 1, 1-tableau-de-bord.png : légende (max 200)

Votre ménage d'un coup d'œil : valeur nette, encaisse, ce que vous devez, factures de la semaine, dépenses par rapport au budget et ce qui demande votre attention.

### Français 1, 1-tableau-de-bord.png : texte de remplacement (max 250)

Tableau de bord d'un ménage fictif : cases de la valeur nette, de l'encaisse, du crédit et des prêts dus, des factures des 7 prochains jours et des dépenses du mois, points à voir, graphique de la valeur nette et des dépenses.

### Français 2, 2-comptes.png : légende (max 200)

Tous vos comptes dans une liste, avec le registre d'une carte de crédit, l'importation des relevés, le rapprochement et la saisie rapide.

### Français 2, 2-comptes.png : texte de remplacement (max 250)

Page Comptes : comptes chèques, épargne, en dollars US, carte de crédit, prêt hypothécaire et placements avec leur solde, et le registre d'une carte de crédit avec ses opérations et un formulaire de saisie.

### Français 3, 3-rapports.png : légende (max 200)

Les dépenses par catégorie de l'année, pour tous ou une personne, par étiquette ou groupe de comptes, enregistrées comme votre propre rapport.

### Français 3, 3-rapports.png : texte de remplacement (max 250)

Rapport des dépenses par catégorie de l'année : une barre par catégorie, comme l'habitation, les impôts, le transport, l'alimentation, les services publics et les animaux, avec le montant et la part, à côté de la liste des rapports.

### Français 4, 4-impots.png : légende (max 200)

Une estimation de l'impôt de chaque personne, pour chaque province et territoire, à partir des chiffres de votre trousse de fin d'année.

### Français 4, 4-impots.png : texte de remplacement (max 250)

Page Impôts, onglet Estimation : les revenus d'une personne à gauche; l'impôt fédéral et du Québec, les crédits remboursables, le remboursement et les taux moyen et marginal à droite.

### Français 5, 5-placements.png : légende (max 200)

Vos placements avec la valeur marchande, le prix de base et le gain non réalisé, à côté des régimes enregistrés, des cryptomonnaies et des métaux précieux.

### Français 5, 5-placements.png : texte de remplacement (max 250)

Page Placements : comptes non enregistrés, cryptomonnaies, métaux précieux et régimes enregistrés à gauche; les titres d'un compte de courtage avec quantité, cours, valeur marchande, prix de base et gain à droite.

### Français 6, 6-factures.png : légende (max 200)

Toutes vos factures avec l'échéance, le montant, le numéro de compte et les versements; marquez-les payées d'un seul clic.

### Français 6, 6-factures.png : texte de remplacement (max 250)

Page Factures, onglet Toutes les factures : factures et paies à venir avec l'échéance, le genre, la catégorie, le montant dû, le montant à payer et le solde, chacune avec un bouton pour la marquer payée ou reçue.

### Français 7, 7-reclamations-medicales.png : légende (max 200)

Les frais médicaux et dentaires, du reçu à chaque réclamation, avec les régimes des deux conjoints dans le bon ordre.

### Français 7, 7-reclamations-medicales.png : texte de remplacement (max 250)

Page Réclamations médicales : les frais encore ouverts de chaque membre de la famille, avec la date, le genre de soins, le régime à réclamer, la date limite, le montant attendu et le montant payé.

### Français 8, 8-budgets.png : légende (max 200)

Les budgets du mois par catégorie, avec ce qui reste, les montants reportés et les dépassements en rouge.

### Français 8, 8-budgets.png : texte de remplacement (max 250)

Page Budgets d'un mois : totaux dépensé, budgété et restant, et une barre par catégorie, l'habitation et le transport dépassés en rouge, l'alimentation, les restaurants et les services publics dans le budget.
