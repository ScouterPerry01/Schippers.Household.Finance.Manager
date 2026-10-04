# Politique de confidentialité : RANN's Roost et RANN's Roost Mobile

*English version: https://www.rann.ca/rann-apps/rann-roost/privacy-policy-en*

En vigueur le 3 octobre 2026

RANN's Roost (pour Windows et Linux) et RANN's Roost Mobile (pour Android) sont publiés par Perry Schippers, faisant affaire sous le nom de RANN, au Canada (« RANN », « nous »). Cette politique vise les deux applications. Elle est publiée à l'adresse https://www.rann.ca/rann-apps/rann-roost/privacy-policy-fr.

## En bref

- **Vos renseignements financiers restent sur vos propres appareils.** RANN ne les reçoit pas, ne les recueille pas, ne les vend pas et ne les communique à personne. Il n'y a ni compte RANN, ni serveur RANN, ni publicité, ni outil d'analyse.
- Les applications n'utilisent Internet que pour les usages énumérés ci-dessous. La plupart n'ont lieu qu'après que vous les avez activés. Aucun n'envoie de montants, de noms de comptes, de notes ni quoi que ce soit sur les personnes de votre ménage, sauf les fonctions facultatives que vous choisissez d'utiliser avec vos propres comptes, décrites dans leurs propres sections.

## Ce qui reste sur vos appareils

Tout ce que vous saisissez ou capturez reste dans le dossier de votre ménage, sur votre ordinateur : comptes, opérations, documents, dossiers de santé et frais médicaux, biens, utilisateurs et réglages. Ces données sont chiffrées en AES-256, avec des clés protégées par votre mot de passe. Les sauvegardes sont chiffrées de la même façon et enregistrées à l'endroit que vous choisissez.

Sur le téléphone, les captures attendent dans un stockage chiffré jusqu'à ce qu'elles parviennent à votre ordinateur. La clé est gardée par le magasin de clés sécurisé d'Android, et l'application est exclue des sauvegardes infonuagiques d'Android.

RANN n'en a jamais de copie et ne peut récupérer ni vos données ni votre mot de passe.

## Entre votre téléphone et votre ordinateur

Le téléphone envoie les captures directement à votre ordinateur sur votre réseau domestique. Chaque échange est chiffré de bout en bout avec une clé que les deux appareils créent au jumelage. Rien ne passe par RANN ni par une autre entreprise.

## Ce que les applications envoient sur Internet

Toutes les demandes utilisent HTTPS. Chaque service voit l'adresse Internet de votre appareil, comme pour toute page Web, et applique sa propre politique de confidentialité.

| Service | Quand | Ce qu'il apprend |
|---|---|---|
| Banque du Canada (banqueducanada.ca) | Automatiquement, quand votre ménage utilise une autre monnaie que le dollar canadien | Les codes de monnaie et les dates des taux de change nécessaires |
| ExchangeRate-API (open.er-api.com) | Seulement si vous activez la seconde source de taux (désactivée par défaut) | Que des taux ont été demandés |
| Yahoo Finance (finance.yahoo.com) | Seulement si vous activez les cours des titres ou des métaux (désactivés par défaut) | Les symboles des titres que vous détenez et les quatre symboles des métaux |
| CoinGecko (coingecko.com) | Seulement si vous activez les cours des cryptoactifs (désactivés par défaut) | Les noms des cryptoactifs que vous détenez |
| mempool.space | Seulement quand vous demandez de mettre à jour un portefeuille Bitcoin en lecture seule | Les adresses publiques de ce portefeuille |
| GitHub (github.com) | Seulement sur les copies qui vérifient les mises à jour, seulement si vous l'avez accepté, au plus une fois par jour. Ce sont les paquets Linux et l'application Android téléchargée sur GitHub. | Qu'une copie de l'application vérifie les mises à jour; les mises à jour que vous choisissez de télécharger |

Les versions du Microsoft Store et de Google Play ne consultent jamais GitHub : la boutique les met à jour.

## Fonctions facultatives qui utilisent vos propres comptes

Certaines fonctions facultatives sont encore en cours d'ajout aux applications avant la version 1.0. Elles sont décrites ici à l'avance, pour que cette politique n'ait pas à changer à leur arrivée. Elles sont toutes désactivées tant que vous ne les activez pas, et chacune indique ce qu'elle envoie avant d'envoyer quoi que ce soit.

**Lecture des documents par IA.** Si la lecture sur l'appareil ne suffit pas, vous pouvez demander à un service d'IA, comme Claude d'Anthropic, de lire un reçu ou une facture.
- L'image de ce seul document est envoyée au service d'IA, avec des instructions indiquant les champs à retourner. On peut vous demander de confirmer chaque document, et vous pouvez brouiller des parties comme les numéros de compte avant l'envoi.
- Vous utilisez votre propre compte auprès du service d'IA. Votre clé pour ce service est gardée dans le magasin d'identifiants sécurisé de votre ordinateur, et non par RANN.
- Le service d'IA traite l'image selon ses propres conditions et sa propre politique de confidentialité. RANN ne reçoit rien.

**Transfert par un dossier infonuagique ou par courriel.** Au lieu du réseau domestique, le téléphone peut déposer les captures dans un dossier infonuagique qui vous appartient (Google Drive, OneDrive, Dropbox ou Nextcloud), ou les envoyer à votre propre adresse courriel.
- Chaque capture est chiffrée sur le téléphone avec votre clé de jumelage avant de partir; le fournisseur infonuagique ou de courriel ne conserve donc que des fichiers qu'il ne peut pas lire. Il en voit toutefois la taille et le moment de l'envoi.
- L'application ne demande l'accès qu'à son propre dossier, et non à vos autres fichiers.

## L'application mobile et Google

Sur Android, la reconnaissance de texte, le numériseur de documents et le lecteur de codes QR sont fournis par Google (ML Kit et les services Google Play). Ils fonctionnent sur votre téléphone, et Google indique que vos images et le texte qui en est lu ne sont pas envoyés à ses serveurs.

Les composants de Google envoient toutefois à Google des renseignements techniques sur leur utilisation : le modèle de l'appareil et la version d'Android, le nom et la version de l'application, un identifiant de l'installation qui n'est pas destiné à vous identifier, et le rendement des fonctions. Ces renseignements sont régis par les [règles de confidentialité de Google](https://policies.google.com/privacy?hl=fr-CA). Ni RANN ni vous ne pouvez désactiver cet envoi, et il ne contient rien de vos documents ni de votre ménage.

Si vous installez l'application à partir de Google Play, Google s'occupe de son téléchargement et de ses mises à jour.

## Les boutiques

Quand vous obtenez une application dans le Microsoft Store ou Google Play, la boutique s'occupe du téléchargement, du paiement éventuel et des mises à jour selon sa propre politique de confidentialité. Les boutiques peuvent fournir à RANN des statistiques (comme le nombre d'installations) et des rapports de plantage qu'elles recueillent selon leurs propres conditions. Ces données ne contiennent pas vos renseignements financiers.

## Quand vous communiquez avec RANN

Si vous écrivez à info-rann-apps@NorthMail.ca ou ouvrez un billet sur GitHub, nous recevons ce que vous envoyez : votre adresse ou votre nom GitHub, et votre message. Nous ne l'utilisons que pour vous répondre et améliorer les applications. Les billets GitHub sont publics et hébergés par GitHub aux États-Unis.

Nous ne conservons les messages que le temps nécessaire pour vous aider. N'envoyez jamais de mots de passe, de clés de récupération, de sauvegardes ni de détails financiers.

## Vos droits et la personne à joindre

RANN est responsable des renseignements personnels qu'elle détient. Elle respecte la Loi sur la protection des renseignements personnels et les documents électroniques (LPRPDE) du Canada et la Loi sur la protection des renseignements personnels dans le secteur privé du Québec (Loi 25). La personne responsable de la protection des renseignements personnels est RANN, à **info-rann-apps@NorthMail.ca**.

Vous pouvez demander à consulter, à faire corriger ou à faire supprimer les renseignements personnels que RANN détient à votre sujet; en pratique, il s'agit de vos messages. Si notre réponse ne vous satisfait pas, vous pouvez porter plainte auprès du Commissariat à la protection de la vie privée du Canada ou, au Québec, de la Commission d'accès à l'information.

## Enfants

Les applications s'adressent aux adultes qui gèrent les finances de leur ménage, et non aux enfants.

## Modifications

Toute nouvelle version de cette politique sera publiée à la même adresse avec une nouvelle date d'entrée en vigueur et signalée dans les notes de version des applications.
