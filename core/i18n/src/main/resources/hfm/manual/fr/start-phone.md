# Premiers pas avec l’application mobile

RANN's Roost Mobile, sur votre téléphone Android, vous permet de photographier un reçu à la caisse, d’inscrire un achat comptant ou de noter l’odomètre, et de le retrouver sur votre ordinateur, prêt à vérifier. Ce chapitre vous mène de l’installation à la vérification de votre première capture. Chaque étape renvoie aux explications complètes.

@index: configurer le téléphone; configuration mobile; première capture

## Ce qu’il vous faut {#needs}

- Un téléphone Android, et RANN's Roost sur votre ordinateur avec votre ménage configuré.
- Le téléphone et l’ordinateur sur le même Wi-Fi de la maison pour le jumelage.
- RANN's Roost ouvert sur l’ordinateur, le ménage déverrouillé, chaque fois que vous voulez que le téléphone envoie par Wi-Fi.

## 1. Installer l’application {#install}

1. Installez RANN's Roost Mobile à partir de Google Play, ou l’édition GitHub à partir des versions publiées par RANN sur GitHub (voir [Deux éditions](phone-app#editions)).
2. Ouvrez-la. Autorisez les notifications si Android le demande, pour recevoir les rappels de factures et de budgets.
3. Choisissez un NIP de 4 à 8 chiffres et saisissez-le de nouveau. L’application le redemande quand vous y revenez après une minute d’absence, ou après le délai choisi dans les Réglages (voir [Le verrou](phone-app#lock) et [Redemander le NIP](phone-app#lock-time)). L’application suit la langue du téléphone ; les Réglages peuvent la garder en anglais ou en français (voir [Langue de l’application](phone-app#language)).
4. Édition GitHub seulement : choisissez si elle peut vérifier les mises à jour une fois par jour.

## 2. Jumeler le téléphone à votre ordinateur {#pair}

1. Sur l’ordinateur, allez à **Téléphones** dans le groupe **Réglages** du menu et cliquez sur **Jumeler un téléphone**. Un code QR apparaît ; il est valide 10 minutes.
2. Sur le téléphone, touchez **Jumeler à un ordinateur**, puis **Numériser le code**, et pointez l’appareil photo vers l’écran.
3. Le téléphone affiche **Jumelé à** et le nom de votre ménage ; l’ordinateur indique que le téléphone est jumelé. Fermez la fenêtre sur l’ordinateur.

Si le téléphone ne peut pas joindre l’ordinateur, vérifiez que les deux sont sur le même Wi-Fi et que Windows autorise RANN's Roost sur les réseaux privés. Détails : [Jumeler un téléphone](phones#pair) et [Jumeler à un ordinateur](phone-app#pair-screen).

## 3. Capturer un reçu {#capture}

![L’onglet Capturer](images/phone-capture.png)

1. Dans l’onglet **Capturer**, touchez **Reçu**.
2. Tenez le reçu à plat ; le numériseur en trouve les bords. Ajoutez des pages pour un long reçu, puis terminez.
3. Le téléphone lit le reçu et remplit le commerce, la date et le montant. Corrigez-les au besoin et choisissez, si vous le voulez, le compte avec lequel vous avez payé, la catégorie, pour qui c’était et une note. Tout est facultatif.
4. Touchez **Enregistrer**.

Utilisez **Facture** pour une facture, **Autre document** pour tout autre document à garder, **Dépense rapide** pour un achat sans reçu, et **Odomètre ou heures** pour une lecture de véhicule. Détails : [Le formulaire de capture](phone-app#capture-form).

Les boutons en dessous inscrivent d’autres choses que garde l’ordinateur : **Liste saisonnière** pour cocher les tâches de la saison, **Relevé de compteur ou de réservoir**, **Heures travaillées**, **Tâches ménagères** et **Bénévolat**, **Déplacement** pour inscrire un trajet du départ à l’arrivée, **Plein ou recharge** pour un plein, et **Stations à proximité**. Voir [Liste saisonnière sur le téléphone](phone-app#seasonal-form), [Formulaires d’inscription](phone-app#log-forms), [Déplacement](phone-app#trip-form) et [Plein ou recharge](phone-app#fuel-form).

## 4. L’envoyer {#send}

À la maison, avec RANN's Roost ouvert sur l’ordinateur, la capture part dès que vous l’enregistrez. Sinon, elle attend sur le téléphone et part d’elle-même la prochaine fois que le téléphone est sur votre Wi-Fi.

Dans l’onglet **Envois**, chaque capture affiche **En attente**, puis **Sur l’ordinateur** une fois reçue. **Envoyer maintenant** envoie aussitôt et met le résumé à jour. Détails : [L’onglet Envois](phone-app#sent-tab).

> Conseil : Loin de la maison pour un moment? Choisissez le même dossier de votre Google Drive, OneDrive, Dropbox ou Nextcloud dans les **Réglages** du téléphone et dans l’écran **Téléphones** de l’ordinateur : les captures y transitent, chiffrées. Voir [Loin de la maison](phones#away-from-home).

## 5. La vérifier sur l’ordinateur {#review}

1. Sur l’ordinateur, ouvrez **Documents** et son onglet **À vérifier**. Votre capture s’y trouve avec l’image, le texte lu et ce que vous avez saisi.
2. Cliquez sur **Vérifier**, contrôlez les détails, puis rattachez-la à une opération, inscrivez-la sur une facture, ou classez-la. Voir [Documents](documents). **Nouvelle opération à partir de ce document** commence avec le compte, la catégorie et la personne choisis sur le téléphone.

Les lectures d’odomètre, les déplacements, les pleins, les inscriptions et les tâches cochées sautent cette étape : ils vont directement dans leurs écrans sur l’ordinateur. Les notes et photos prises pendant un déplacement attendent ici jusqu’à l’arrivée du déplacement.

## 6. Consulter votre résumé {#summary}

L’onglet **Résumé** du téléphone affiche les soldes de vos comptes, les factures à payer, les budgets du mois et l’entretien à faire, au dernier transfert, et sous **À venir** les heures de travail et d’école du jour et les prochains rendez-vous. L’icône de calendrier en haut à droite, ou **Voir l’agenda**, ouvre l’agenda des 60 prochains jours, jour par jour ou par mois. Voir [L’onglet Résumé](phone-app#summary-tab) et [L’agenda](phone-app#agenda).

![L’onglet Résumé](images/phone-summary.png)

## 7. Trouver un contact {#contacts}

![L’onglet Contacts](images/phone-contacts.png)

1. Sur le téléphone, ouvrez l’onglet **Contacts** : les contacts du ménage venus de votre ordinateur, sans les numéros de compte.
2. Tapez une partie d’un nom, ou « pharmacie », dans **Chercher un contact**, ou choisissez un **Type**.
3. Touchez un contact, puis un numéro de téléphone pour appeler, un courriel pour écrire, ou l’adresse pour la voir sur une carte.
4. Vous avez rencontré quelqu’un? Touchez **Nouveau contact**, inscrivez le nom et ce que vous savez, puis **Enregistrer**. Sur l’ordinateur, il attend sous **Du téléphone** à l’écran Contacts jusqu’à ce que vous l’ajoutiez.

Détails : [L’onglet Contacts](phone-app#contacts-tab) et [Les contacts venant du téléphone](contacts#from-phone).

## Si le téléphone est perdu {#lost}

Sur l’ordinateur, allez à **Téléphones** et cliquez sur **Retirer** à côté du téléphone. Il ne peut plus rien envoyer ni recevoir. Le NIP de l’application protège aussi ce qu’il contient. Voir [La liste des téléphones](phones#phone-list).
