# Politique de confidentialité : RANN's Roost et RANN's Roost Mobile

*English version: [privacy-policy.md](privacy-policy.md)*

En vigueur le 3 octobre 2026

RANN's Roost (pour Windows et Linux) et RANN's Roost Mobile (pour Android) sont publiés par Perry Schippers, faisant affaire sous le nom de RANN, au Canada (« RANN », « nous »).

## En bref

- **Vos renseignements financiers restent sur vos propres appareils.** RANN ne les reçoit pas, ne les recueille pas, ne les vend pas et ne les communique à personne. Il n'y a ni compte RANN, ni serveur RANN, ni publicité, ni outil d'analyse.
- Les applications ne communiquent avec Internet que pour les usages énumérés ci-dessous, la plupart seulement après que vous les avez activés, et n'envoient jamais de montants, de noms de comptes, de notes ni quoi que ce soit sur les personnes de votre ménage.

## Ce qui reste sur vos appareils

Tout ce que vous saisissez ou capturez (comptes, opérations, documents, dossiers de santé et frais médicaux, biens, utilisateurs et réglages) est conservé dans le dossier de votre ménage, sur votre ordinateur, chiffré en AES-256 avec des clés protégées par votre mot de passe. Les sauvegardes sont chiffrées de la même façon et enregistrées à l'endroit que vous choisissez. Sur le téléphone, les captures attendent dans un stockage chiffré avec une clé gardée par le magasin de clés sécurisé d'Android jusqu'à ce qu'elles parviennent à votre ordinateur, et l'application est exclue des sauvegardes infonuagiques d'Android. RANN n'en a jamais de copie et ne peut récupérer ni vos données ni votre mot de passe.

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
| GitHub (github.com) | Seulement sur les copies qui vérifient les mises à jour (paquets Linux et application Android de GitHub), seulement si vous l'avez accepté, au plus une fois par jour | Qu'une copie de l'application vérifie les mises à jour; les mises à jour que vous choisissez de télécharger |

Les versions du Microsoft Store et de Google Play ne consultent jamais GitHub : la boutique les met à jour.

## L'application mobile et Google

Sur Android, la reconnaissance de texte, le numériseur de documents et le lecteur de codes QR sont fournis par Google (ML Kit et les services Google Play). Ils fonctionnent sur votre téléphone, et vos images sont lues sur votre téléphone. Les composants de Google peuvent envoyer à Google des renseignements techniques sur leur utilisation, comme le modèle de l'appareil et le rendement des fonctions, selon les [règles de confidentialité de Google](https://policies.google.com/privacy?hl=fr-CA). Si vous installez l'application à partir de Google Play, Google s'occupe de son téléchargement et de ses mises à jour.

## Les boutiques

Quand vous obtenez une application dans le Microsoft Store ou Google Play, la boutique s'occupe du téléchargement, du paiement éventuel et des mises à jour selon sa propre politique de confidentialité. Les boutiques peuvent fournir à RANN des statistiques (comme le nombre d'installations) et des rapports de plantage qu'elles recueillent selon leurs propres conditions. Ces données ne contiennent pas vos renseignements financiers.

## Quand vous communiquez avec RANN

Si vous écrivez à info-rann-apps@NorthMail.ca ou ouvrez un billet sur GitHub, nous recevons ce que vous envoyez (votre adresse ou votre nom GitHub et votre message) et ne l'utilisons que pour vous répondre et améliorer les applications. Les billets GitHub sont publics et hébergés par GitHub aux États-Unis. Nous ne conservons les messages que le temps nécessaire pour vous aider. N'envoyez jamais de mots de passe, de clés de récupération, de sauvegardes ni de détails financiers.

## Vos droits et la personne à joindre

RANN est responsable des renseignements personnels qu'elle détient et respecte la Loi sur la protection des renseignements personnels et les documents électroniques (LPRPDE) du Canada et la Loi sur la protection des renseignements personnels dans le secteur privé du Québec (Loi 25). La personne responsable de la protection des renseignements personnels est RANN, à **info-rann-apps@NorthMail.ca**.

Vous pouvez demander à consulter, à faire corriger ou à faire supprimer les renseignements personnels que RANN détient à votre sujet (en pratique, vos messages). Si notre réponse ne vous satisfait pas, vous pouvez porter plainte auprès du Commissariat à la protection de la vie privée du Canada ou, au Québec, de la Commission d'accès à l'information.

## Enfants

Les applications s'adressent aux adultes qui gèrent les finances de leur ménage, et non aux enfants.

## Modifications

Toute nouvelle version de cette politique sera publiée à la même adresse avec une nouvelle date d'entrée en vigueur et signalée dans les notes de version des applications.
