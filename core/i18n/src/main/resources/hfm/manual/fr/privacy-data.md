# Confidentialité, données et sécurité

Ce chapitre explique où vos renseignements sont gardés, comment ils sont protégés, le peu qui quitte votre ordinateur et pourquoi, comment fonctionnent les mots de passe et les clés de récupération, et comment garder une copie en lieu sûr ou passer à un nouvel ordinateur.

En bref : vos renseignements financiers restent sur votre ordinateur et votre téléphone, chiffrés. RANN ne les reçoit pas et ne les recueille pas. Il n’y a ni compte RANN, ni publicité, ni outil d’analyse.

## Où vivent vos données {#where-data-lives}

@index: emplacement des données; stockage; données locales

### Le dossier du ménage {#household-folder}

@index: dossier du ménage; dossier .hfm; fichier de données

Un ménage est un dossier sur votre ordinateur, créé quand vous avez créé le ménage, dont le nom se termine par .hfm, comme Famille Tremblay.hfm. Tout ce qui concerne le ménage s’y trouve :

- un fichier trousseau, qui contient les noms d’utilisateur et les clés verrouillées de chaque utilisateur, mais aucun nom de personne, aucun montant ni aucune institution ;
- une base de données principale chiffrée : les utilisateurs, les personnes, les catégories, les bénéficiaires, les budgets, les réglages et le journal d’activité, que chaque utilisateur du ménage peut lire ;
- une base de données chiffrée pour chaque groupe de comptes : ses comptes, ses opérations, les détails de ses documents, ses placements et tout autre dossier tenu dans ce groupe ;
- le coffre des documents : un fichier chiffré pour chaque reçu, facture ou autre document ;
- des copies prises automatiquement avant que l’application mette une base de données à niveau vers une nouvelle version, au cas où quelque chose tournerait mal.

Traitez le dossier comme un tout : ne renommez pas, ne déplacez pas et ne modifiez pas les fichiers qu’il contient. Pour déplacer le ménage, voir [Passer à un nouvel ordinateur](privacy-data#new-computer).

> Important : quiconque supprime le dossier supprime le ménage. Gardez des sauvegardes, sur un autre disque ; voir [Sauvegardes](backups).

### Le chiffrement {#encryption}

@index: chiffrement; AES-256; chiffré; comptes privés; groupes de comptes

Chaque base de données du ménage est chiffrée avec AES-256, chacune avec sa propre clé aléatoire. Ces clés sont à leur tour verrouillées avec le mot de passe de chaque utilisateur, et une seconde fois avec la clé de récupération de cet utilisateur. Rien dans le dossier ne peut être lu sans un mot de passe ou une clé de récupération qui l’ouvre.

Le mot de passe n’est jamais enregistré. Quand vous vous connectez, l’application en tire une clé d’une manière volontairement lente, de sorte que quelqu’un qui volerait le dossier ne pourrait pas essayer des millions de mots de passe.

Les groupes de comptes ajoutent une seconde protection à l’intérieur du ménage :

- Les groupes partagés peuvent être ouverts par chaque administrateur et par les autres utilisateurs à qui on en donne l’accès.
- Le groupe privé d’un membre ne peut être ouvert que par son propriétaire et les personnes avec qui il le partage. Un administrateur ne peut pas l’ouvrir non plus, à moins que le propriétaire ne le partage, et même quelqu’un qui copie le dossier ne peut pas le lire sans le mot de passe du propriétaire.

Voir [Utilisateurs](users) pour les rôles et les accès.

### Le coffre des documents {#document-vault}

@index: coffre; documents; reçus; documents chiffrés

Les reçus, factures et autres papiers que vous importez ou recevez du téléphone sont gardés dans le coffre, à l’intérieur du dossier du ménage, chacun scellé dans son propre fichier chiffré sous la clé de son groupe de comptes. Un document ne peut être lu que par les personnes qui peuvent ouvrir son groupe. Les documents sont lus (reconnaissance du texte) sur cet ordinateur ; rien n’est envoyé ailleurs, sauf si vous choisissez d’en faire lire un par l’IA.

Les fichiers de 50 Mo au plus peuvent être gardés. L’onglet Anciens documents de [Documents](documents) présente les documents classés qui datent de plus de six ans, la durée pendant laquelle l’Agence du revenu du Canada demande de garder les dossiers fiscaux ; rien n’est jamais supprimé automatiquement.

### Les réglages gardés sur cet ordinateur {#computer-settings}

@index: préférences; réglages propres à l’ordinateur

Quelques choix sont enregistrés sur l’ordinateur plutôt que dans le ménage : la langue, les couleurs et la taille du texte, la durée avant le verrouillage automatique, la disposition du menu de chaque utilisateur, la liste des ménages récents et la réponse à la question sur les mises à jour. Ils ne contiennent rien sur vos finances. Voir [Les réglages gardés sur cet ordinateur](basics#computer-settings).

La clé de [lecture par IA](ai), si vous en ajoutez une, est gardée dans le magasin sécurisé de votre système : le Gestionnaire d’identification de Windows sous Windows, ou le trousseau de votre bureau sous Linux. Quand aucun trousseau n’est actif sous Linux, la clé n’est gardée en mémoire que jusqu’à la fermeture de l’application.

### Les numéros de compte {#account-numbers}

@index: numéro de compte; numéro masqué; Afficher le numéro

Les numéros de compte complets sont gardés chiffrés comme tout le reste, et les écrans n’en montrent que les derniers chiffres. Pour voir un numéro complet, utilisez **Afficher le numéro** sur le compte : l’application demande d’abord votre mot de passe.

### Sur le téléphone {#on-the-phone}

@index: stockage du téléphone; NIP

RANN's Roost Mobile garde ses réglages, les listes et les contacts qu’elle reçoit de l’ordinateur et sa file de saisies et de nouveaux contacts dans des fichiers chiffrés avec une clé conservée dans le matériel sécurisé du téléphone. Elle est verrouillée par un NIP, avec le déverrouillage par empreinte digitale ou par le visage si vous le choisissez, et elle est exclue des sauvegardes infonuagiques du téléphone. Un téléphone ne détient jamais les clés du ménage : il peut seulement envoyer des saisies et de nouveaux contacts, et recevoir de courtes listes. Voir [RANN's Roost Mobile](phone-app).

Ce qui va au téléphone : le nom et la langue du ménage, la devise de base, les comptes et leurs soldes, les catégories, les bénéficiaires, les membres et les animaux, les véhicules, les remorques et les équipements à compteur avec leurs lectures, les factures à payer dans les 60 prochains jours, les budgets et l’entretien du mois (et l’entretien et les renouvellements dus dans les 60 jours), les prochains rendez-vous du calendrier, les heures de travail et d’école et les activités des enfants, les renouvellements de médicaments à venir, la liste saisonnière en cours, les compteurs, réservoirs, clients, tâches et organismes parmi lesquels les formulaires de suivi font choisir, les lieux enregistrés pour les trajets, et les contacts que son propriétaire peut voir (sauf les contacts archivés). Les numéros de compte complets, les numéros de compte et de client des contacts, les numéros de police, les plaques, les opérations et les documents ne vont pas au téléphone, ni les dossiers de santé au-delà du nom des médicaments dont le renouvellement approche et des rendez-vous du calendrier. Seul ce que son propriétaire peut voir sur cet ordinateur va à un téléphone : les groupes privés d’un autre utilisateur, jamais. Voir [Ce que reçoit le téléphone](phones#sent-to-phone).

## Les mots de passe et la clé de récupération {#passwords}

@index: mot de passe; mot de passe principal; clé de récupération

### Le mot de passe {#master-password}

@index: mot de passe principal; longueur du mot de passe; phrase de passe

Chaque utilisateur se connecte avec son propre nom d’utilisateur et son propre mot de passe. Un mot de passe a au moins 12 caractères. Une phrase de passe de quelques mots sans lien entre eux est facile à retenir et difficile à deviner. Ne réutilisez pas le mot de passe d’un autre service.

Il n’y a pas de serveur : personne ne peut réinitialiser un mot de passe oublié par courriel, ni RANN, ni un administrateur. Seule la clé de récupération de l’utilisateur le peut.

### La clé de récupération {#recovery-key}

@index: clé de récupération; mot de passe perdu; mot de passe oublié

Chaque utilisateur reçoit une clé de récupération quand son compte est créé : à la création du ménage pour le premier administrateur, et quand un administrateur ajoute un utilisateur. Elle compte 54 lettres et chiffres en groupes de quatre, affichés une seule fois.

- Imprimez-la ou notez-la et gardez-la en lieu sûr, loin de l’ordinateur, par exemple avec vos papiers importants.
- Elle fonctionne aussi avec chaque sauvegarde du ménage.
- Elle reste la même quand vous changez votre mot de passe, et continue de fonctionner après une réinitialisation.
- Quiconque a votre clé de récupération et votre nom d’utilisateur peut fixer un nouveau mot de passe et ouvrir ce que vous pouvez ouvrir. Protégez-la comme une clé de maison.

### Changer un mot de passe {#change-password}

@index: changer le mot de passe

Pour changer votre mot de passe, allez à [Utilisateurs](users) et cliquez sur **Changer mon mot de passe…**. Entrez votre mot de passe actuel, puis le nouveau deux fois. Votre clé de récupération reste la même.

### Un mot de passe oublié {#forgotten-password}

@index: mot de passe oublié; réinitialiser le mot de passe

Dans l’écran de déverrouillage, cliquez sur **Mot de passe oublié?**, puis entrez votre nom d’utilisateur, votre clé de récupération et un nouveau mot de passe. Voir [Réinitialiser le mot de passe avec la clé de récupération](basics#reset-screen).

Si le mot de passe et la clé de récupération sont tous deux perdus, personne ne peut ouvrir le ménage, ni ses sauvegardes. C’est le prix d’un ménage que personne d’autre ne peut lire.

## Le verrouillage {#locking}

@index: verrouiller; verrouillage automatique; s’absenter

Quand le ménage est verrouillé, ses clés sont effacées de la mémoire. Utilisez **Verrouiller** dans la barre du haut quand vous vous éloignez, et choisissez sous [Sécurité](security) après combien de temps sans activité du clavier ou de la souris le ménage se verrouille de lui-même (10 minutes au départ). Fermer la fenêtre verrouille aussi. Sur un ordinateur que d’autres utilisent, une courte durée est plus sûre. Voir [Le verrouillage](basics#locking).

## Ce qui quitte cet ordinateur {#what-leaves}

@index: Internet; réseau; en ligne; données envoyées; tiers

RANN's Roost fonctionne sans Internet. Elle ne va en ligne que dans les cas ci-dessous, la plupart désactivés tant que vous ne les activez pas. RANN elle-même ne reçoit rien dans aucun de ces cas.

### Les taux de change {#exchange-rates}

@index: Banque du Canada; taux de change; ExchangeRate-API

Quand votre ménage a un compte ou un montant dans une autre monnaie, l’application télécharge les taux de change quotidiens de la Banque du Canada. Seule la liste des taux est téléchargée ; rien sur vous n’est envoyé.

Si vous activez la deuxième source sous [Taux et cours](rates), les taux des monnaies que la Banque du Canada ne publie pas sont téléchargés une fois par jour d’ExchangeRate-API, un service public gratuit, de la même façon.

### Les cours du marché {#market-prices}

@index: cours; Yahoo Finance; CoinGecko; cours des actions

Les téléchargements de cours sont désactivés tant que vous ne les activez pas sous [Taux et cours](rates) :

- les cours des actions et des FNB, et ceux de l’or, de l’argent, du platine et du palladium, de Yahoo Finance ;
- les cours des cryptoactifs de CoinGecko.

Chacun envoie les symboles de ce que vous détenez, comme XIC ou BTC, et rien d’autre sur vous ou vos montants.

### Les portefeuilles en lecture seule {#watch-only}

@index: Bitcoin; portefeuille en lecture seule; mempool.space; xpub

Quand vous demandez à l’application de mettre à jour un portefeuille Bitcoin en lecture seule, elle interroge mempool.space, un explorateur de chaîne de blocs public, sur les adresses du portefeuille. Ce service apprend ces adresses et, comme tout site Web, l’adresse Internet (IP) de votre ordinateur, qui peut indiquer à peu près où vous êtes ; rien d’autre sur vous. N’entrez jamais de clé privée ni de phrase de récupération : l’application les refuse. Voir [Placements](investments).

### La lecture par IA {#ai-reading}

@index: IA; Claude; Anthropic; lecture infonuagique

La lecture par IA est désactivée tant que vous ne l’activez pas sous [Lecture par IA](ai) et n’ajoutez pas votre propre clé Anthropic. Ensuite, pour les documents difficiles à lire sur cet ordinateur, vous pouvez envoyer des pages à Claude, l’IA d’Anthropic :

- seules les pages que vous approuvez sont envoyées, après que vous les avez rognées et que vous avez masqué ce que vous voulez, comme un numéro de compte complet ; les zones masquées sont remplacées par des blocs unis avant que l’image quitte l’ordinateur ;
- Anthropic les reçoit pour les lire, sous votre propre compte, qui paie quelques cents par lecture ;
- rien d’autre sur votre ménage ne quitte l’ordinateur.

### Les transferts du téléphone {#phone-transfers}

@index: Wi-Fi; synchronisation du téléphone; dossier de transfert; dossier infonuagique

RANN's Roost Mobile ne parle qu’à cet ordinateur, par le Wi-Fi de la maison, de façon chiffrée entre les deux. Rien du ménage ne passe par Internet. Le téléphone envoie des saisies et de nouveaux contacts, et reçoit le résumé et les contacts décrits sous [Sur le téléphone](#on-the-phone). Loin de la maison, le téléphone peut déposer ses saisies et nouveaux contacts, chiffrés, dans un dossier de votre propre Google Drive, OneDrive, Dropbox ou Nextcloud ; ce service ne voit que des fichiers illisibles. RANN n’a aucun compte auprès de ces services. Voir [Téléphones](phones).

### Les calendriers et la position du téléphone {#phone-calendars-location}

@index: accès au calendrier; position; recherche d’adresse; stations à proximité; Overpass; géocodeur

L’application du téléphone ne lit les calendriers du téléphone qu’après que vous avez permis l’accès au calendrier et coché des calendriers dans ses Réglages, et n’écrit dans l’un d’eux que si vous choisissez **Dans les deux sens** ; cette lecture et cette écriture se font sur le téléphone, sans Internet, et ce qui est apporté arrive à cet ordinateur par le transfert chiffré habituel. Voir [Calendriers de ce téléphone](phone-app#phone-calendars) et [Calendriers des téléphones et des fichiers](calendar-sync).

Le téléphone n’utilise sa position que si vous le permettez, et seulement pour les trajets : une position quand vous partez, vous arrêtez, prenez une pause, arrivez ou enregistrez un lieu. Deux recherches passent par Internet, toutes deux désactivées tant que vous ne les activez pas dans les Réglages du téléphone (voir [Déplacements : adresses et stations](phone-app#trip-lookups)) :

- **Trouver les adresses** : toucher **Trouver l’adresse** envoie les coordonnées de cette position à Google, par le géocodeur d’Android, qui renvoie l’adresse.
- **Stations à proximité** : ouvrir cette liste envoie une position approximative, arrondie à environ un kilomètre, au service Overpass d’OpenStreetMap, qui renvoie les stations-service et les bornes de recharge autour.

Rien d’autre sur vous ou vos trajets n’est envoyé avec elles.

### La vérification des mises à jour {#update-checks}

@index: vérification des mises à jour; GitHub

Sur les copies Linux installées à partir d’un paquet .deb ou .rpm ou d’une AppImage, et seulement si vous l’avez accepté, l’application vérifie une fois par jour sur GitHub si une nouvelle version est parue. L’application du téléphone téléchargée de GitHub fait de même, si vous l’avez accepté (voir [Mises à jour](phone-app#updates)). GitHub voit l’adresse Internet de votre ordinateur ou de votre téléphone et le fait que l’application est utilisée, comme pour toute page Web. Chaque mise à jour est vérifiée avec la signature de RANN avant de pouvoir être installée. Les copies du Microsoft Store, de Flathub ou de Google Play sont mises à jour par leur boutique et ne font aucune vérification.

### Les liens que vous ouvrez {#links}

Les boutons sous [À propos](about), comme **Lire la politique de confidentialité** et **Code source sur GitHub**, ouvrent ces pages dans votre navigateur Web, comme n’importe quel lien.

### Ce qui ne sort jamais {#never-leaves}

Vos comptes, opérations, soldes, documents, dossiers de santé, mots de passe et clés ne quittent jamais votre ordinateur et votre téléphone, sauf dans une sauvegarde ou une exportation que vous placez vous-même, et dans les pages que vous choisissez d’envoyer pour la lecture par IA.

La politique de confidentialité, accessible sous [À propos](about), indique ce que chaque service extérieur apprend.

## Les sauvegardes {#backups}

@index: sauvegarde; restaurer; hfmbak; copie du ménage

Une sauvegarde est un seul fichier (.hfmbak) qui contient une copie de tout le dossier du ménage. Elle reste chiffrée : la restaurer exige le même mot de passe ou la même clé de récupération que le ménage. Chaque sauvegarde est vérifiée dès qu’elle est faite.

Par défaut, l’application fait une sauvegarde chaque jour dans un dossier à côté du ménage et en garde 10 versions. Choisissez un dossier sur un autre disque (un disque externe, un lecteur réseau ou un dossier infonuagique) pour qu’une panne de disque ou un ordinateur perdu n’emporte pas les sauvegardes avec lui. Comme les sauvegardes sont chiffrées, un dossier infonuagique ne voit que des fichiers illisibles. Si aucune sauvegarde n’a réussi depuis 7 jours, le tableau de bord vous le signale.

Voir [Sauvegardes](backups) pour chaque réglage, et [Restaurer une sauvegarde](basics#restore) pour en ramener une.

## Exporter toutes les données {#export}

@index: exportation; CSV; JSON; formats ouverts; non chiffré

**Exporter toutes les données…**, dans l’écran [Sauvegardes](backups), enregistre tout ce que vous voyez dans des formats ouverts (CSV et JSON), en un seul fichier zip, pour que vos données puissent toujours être utilisées ailleurs. Avant de commencer, l’application vous avertit :

> Important : l’exportation n’est pas chiffrée. Toute personne ayant le fichier peut lire vos comptes, vos opérations, vos numéros de compte et vos documents. Enregistrez-la en lieu sûr et supprimez-la quand vous n’en avez plus besoin.

## Passer à un nouvel ordinateur {#new-computer}

@index: nouvel ordinateur; déplacer le ménage; migration

1. Sur l’ancien ordinateur, ouvrez le ménage et cliquez sur **Sauvegarder maintenant** sous [Sauvegardes](backups). Attendez « Sauvegarde enregistrée et vérifiée ».
2. Copiez le fichier .hfmbak le plus récent du dossier de sauvegarde vers le nouvel ordinateur, avec une clé USB ou votre dossier infonuagique.
3. Installez RANN's Roost sur le nouvel ordinateur et démarrez-la.
4. Dans l’écran Bienvenue, cliquez sur **Restaurer une sauvegarde…**, choisissez le fichier, puis choisissez où placer le ménage.
5. Connectez-vous avec votre nom d’utilisateur et votre mot de passe habituels.

Ensuite, sur le nouvel ordinateur :

- vérifiez les dossiers sous [Sauvegardes](backups) (le dossier de sauvegarde), [Téléphones](phones) (le dossier de transfert) et [Documents](documents) (le dossier surveillé) : ils peuvent désigner des endroits qui n’existent pas sur cet ordinateur ;
- choisissez de nouveau la langue, les couleurs, la taille du texte et la durée avant le verrouillage automatique, puisqu’ils appartiennent à chaque ordinateur ;
- ajoutez de nouveau votre clé sous [Lecture par IA](ai) si vous l’utilisez ;
- si un téléphone ne peut plus joindre l’ordinateur, jumelez-le de nouveau sous [Téléphones](phones).

Chaque utilisateur du ménage se connecte au nouvel ordinateur avec son propre mot de passe, comme avant.

## Partager un ordinateur ou un ménage {#sharing}

@index: ordinateur partagé; plusieurs utilisateurs; membres de la famille

- Donnez à chaque adulte son propre utilisateur sous [Utilisateurs](users), avec le rôle qui lui convient : Administrateur, Membre ou Lecteur. Chacun se connecte avec son propre mot de passe et ne voit que ce qui lui est permis.
- Verrouillez le ménage quand vous vous éloignez, et gardez une courte durée avant le verrouillage automatique.
- Les téléphones de chaque utilisateur n’envoient qu’à cet utilisateur : un autre utilisateur connecté à ce moment ne peut pas lire leurs saisies, et le téléphone attend que son propriétaire ouvre le ménage.
- Le journal d’activité, sous [Utilisateurs](users), indique qui a changé quoi.
