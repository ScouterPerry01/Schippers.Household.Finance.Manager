# Téléphones

L’écran Téléphones sert à jumeler RANN’s Roost Mobile, l’application compagnon pour téléphones Android, à cet ordinateur, et à gérer les téléphones déjà jumelés. Les téléphones jumelés envoient à cet ordinateur des reçus, des factures, d’autres documents, des dépenses rapides et des lectures d’odomètre, et reçoivent en retour un résumé des soldes, des factures et des budgets. L’écran se trouve dans le groupe **Réglages** du menu, sous **Téléphones**.

Pour le côté téléphone, voir [RANN’s Roost Mobile](phone-app). Pour un parcours rapide, voir [Premiers pas avec l’application mobile](start-phone).

@index: Android; application mobile; application compagnon; synchronisation; synchroniser; jumelage; code QR

## Comment les téléphones et cet ordinateur travaillent ensemble {#how-it-works}

- L’ordinateur garde l’exemplaire de référence. Le téléphone ne garde que ses captures en attente d’envoi et un résumé venu de l’ordinateur.
- À la maison, le téléphone envoie par votre Wi-Fi directement à cet ordinateur. Rien ne passe par Internet ni par un serveur de RANN.
- Tout ce qui circule entre le téléphone et cet ordinateur est chiffré avec une clé que les deux ont créée au jumelage. Aucun autre téléphone ni ordinateur ne peut le lire.
- Les téléphones peuvent joindre cet ordinateur seulement pendant que le ménage est ouvert dans RANN’s Roost. Verrouiller le ménage ou fermer l’application arrête l’écoute ; le téléphone garde ses captures et les envoie plus tard.
- Loin de la maison, le téléphone peut déposer ses captures, toujours chiffrées, dans un dossier de votre propre stockage infonuagique, que cet ordinateur surveille (voir [Loin de la maison](#away-from-home)).
- Ce qui arrive n’entre jamais directement dans vos livres. Les reçus, factures, documents et dépenses rapides attendent dans l’onglet **À vérifier** de [Documents](documents) ; les lectures d’odomètre et d’heures sont ajoutées directement au véhicule ou à l’équipement.

@index: chiffrement; Wi-Fi; réseau local; réseau de la maison; confidentialité

### L’écoute {#listener}

Pendant que le ménage est ouvert, RANN’s Roost attend les téléphones sur votre réseau de la maison. La ligne sous l’explication indique où en sont les choses :

- « Les téléphones peuvent joindre cet ordinateur à 192.168.1.20:47311 pendant que le ménage est ouvert. » : tout va bien. L’adresse est celle de cet ordinateur sur votre réseau de la maison, suivie du port d’écoute (47311, ou le suivant qui est libre jusqu’à 47320 si un autre programme l’utilise).
- « Les téléphones ne peuvent pas joindre cet ordinateur : » suivi de la raison : l’écoute n’a pas pu démarrer, habituellement parce qu’aucun port n’était libre.
- « Cet ordinateur n’est pas connecté à un réseau local. » : aucun réseau de la maison n’a été trouvé. Connectez-vous à votre Wi-Fi ou à votre réseau filaire, puis revenez à l’écran.

Quand l’ordinateur a plusieurs cartes réseau, RANN’s Roost choisit l’adresse de votre réseau de la maison et évite celles des machines virtuelles et des cartes semblables.

> Remarque : La première fois, Windows peut demander d’autoriser RANN’s Roost sur les réseaux. Autorisez-le sur les réseaux privés, sinon les téléphones ne pourront pas le joindre.

## Jumeler un téléphone {#pair}

@index: jumeler; code de jumelage; numériser le code

**Jumeler un téléphone**, en haut à droite, affiche un code de jumelage. Le bouton est offert seulement pendant que l’écoute fonctionne et qu’une adresse du réseau de la maison a été trouvée.

### La fenêtre de jumelage {#pairing-window}

La fenêtre montre un code QR à gauche et, à droite :

- les étapes : sur le téléphone, ouvrez RANN’s Roost Mobile, touchez **Jumeler à un ordinateur** et numérisez le code ; l’appareil photo du téléphone peut aussi le numériser et ouvrir RANN’s Roost Mobile ;
- un rappel que le téléphone doit être sur le même Wi-Fi que cet ordinateur, et la question de Windows sur les réseaux ;
- le temps qui reste : chaque code est valide 10 minutes, puis « Ce code a expiré. Fermez et jumelez de nouveau. » ;
- l’adresse et le port que le téléphone utilisera ;
- **Copier en texte** : copie le lien de jumelage dans le presse-papiers, pour un téléphone dont l’appareil photo ne peut pas lire l’écran. Transmettez-le au téléphone par un moyen de confiance et collez-le dans **Ou collez le texte de jumelage** sur le téléphone.

Une fois le téléphone jumelé, la fenêtre indique « Le téléphone est jumelé. Vous pouvez fermer cette fenêtre. » et le téléphone apparaît dans la liste. **Fermer** ferme la fenêtre ; un code inutilisé expire simplement.

Chaque code ne sert qu’une fois. Rouvrir la fenêtre crée un nouveau code ; un code précédent reste valide jusqu’à son expiration.

### À qui appartient un téléphone {#owner}

Un téléphone est jumelé à l’utilisateur connecté au moment du jumelage. Seul cet utilisateur peut recevoir ses captures : quand quelqu’un d’autre a ouvert le ménage, le téléphone garde ses éléments et avertit son propriétaire que quelqu’un d’autre est connecté. Les captures arrivent la prochaine fois que le propriétaire ouvre le ménage.

L’endroit où sont enregistrés les documents d’un téléphone dépend de cet utilisateur : ceux d’un administrateur vont dans le groupe de comptes partagé, ceux d’un membre dans son propre groupe privé s’il en a un. Vous pouvez changer cela pour chaque téléphone avec **Modifier**.

@index: plusieurs utilisateurs; groupe privé; groupe partagé

## La liste des téléphones {#phone-list}

Chaque téléphone jumelé a sa carte, avec :

- son nom, tel que le téléphone l’a donné (sa marque et son modèle) ou tel que vous l’avez renommé, avec **(retiré)** après un téléphone que vous avez retiré ;
- **jumelé le** et la date et l’heure du jumelage ;
- **dernier transfert** et la date et l’heure de son dernier envoi, ou **rien d’envoyé pour l’instant** ;
- le nombre d’éléments envoyés (« 12 éléments reçus ») ;
- **documents dans** et le groupe de comptes où ses documents sont enregistrés.

Les boutons :

- **Modifier** : ouvre la [boîte du téléphone](#phone-dialog).
- **Retirer** : arrête le téléphone aussitôt, sans demander. Il ne peut plus rien envoyer ni recevoir : à sa prochaine tentative, il apprend qu’il a été retiré, annule son jumelage et garde ses captures non envoyées. Pour l’utiliser de nouveau, jumelez-le de nouveau. Servez-vous de **Retirer** pour un téléphone perdu, vendu ou remplacé. Ce qu’il a déjà envoyé reste dans RANN’s Roost.
- **Oublier** : affiché pour un téléphone retiré. Le retire de la liste.

@index: téléphone perdu; téléphone volé; révoquer; retirer un téléphone

### Boîte du téléphone {#phone-dialog}

- **Nom** : le nom affiché sur la carte, par exemple « Téléphone d’Alex ». Obligatoire.
- **Enregistrer dans** : le groupe de comptes où sont enregistrés les reçus, factures, documents et dépenses rapides du téléphone. Seuls les groupes où vous pouvez ajouter des données sont offerts ; un groupe privé affiche « (privé) » après son nom. Le changement vaut pour ce que le téléphone enverra désormais ; les documents déjà reçus restent où ils sont.

## Loin de la maison {#away-from-home}

@index: dossier de transfert; dossier infonuagique; Google Drive; OneDrive; Dropbox; Nextcloud; transfert par courriel; USB; roostsync

Quand le téléphone n’est pas sur le Wi-Fi de la maison, il peut déposer ses captures dans un dossier de transfert de votre propre Google Drive, OneDrive, Dropbox ou Nextcloud. L’application de ce service copie le dossier sur cet ordinateur, et RANN’s Roost en importe le contenu. Les fichiers sont chiffrés avec la clé du téléphone : le service ne garde jamais que des fichiers illisibles.

La carte **Loin de la maison** montre le dossier utilisé (« Dossier de transfert : … ») ou « Aucun dossier de transfert choisi. », et ces boutons :

- **Choisir un dossier de transfert…** (ou **Changer de dossier…**) : choisissez le dossier tel qu’il apparaît sur cet ordinateur, le même que celui choisi sur le téléphone. RANN’s Roost le consulte aussitôt et indique combien de saisies il a importées.
- **Ne plus l’utiliser** : affiché une fois un dossier choisi. RANN’s Roost cesse de surveiller le dossier. Rien n’y est supprimé.
- **Importer un fichier de transfert…** : choisissez un fichier de transfert (un fichier de transfert RANN’s Roost, qui se termine par .roostsync) arrivé autrement, comme une pièce jointe de courriel enregistrée ou un fichier copié par USB. Vous pouvez aussi déposer un tel fichier dans l’écran [Documents](documents). Cela fonctionne même quand l’écoute est arrêtée.

Le résultat de la dernière consultation ou importation est affiché au bas de la carte.

### Comment le dossier de transfert est surveillé {#watching}

Pendant que le ménage est ouvert, RANN’s Roost consulte le dossier de transfert toutes les 20 secondes :

- Chaque nouveau fichier d’un téléphone jumelé est importé, puis supprimé du dossier, et un fichier de confirmation est laissé à côté pour le téléphone. Le téléphone le récupère à sa prochaine consultation, et c’est seulement alors qu’il supprime ses propres copies. Un fichier que le service infonuagique est encore en train de copier est laissé pour la ronde suivante.
- Un fichier venant du téléphone d’un autre utilisateur du ménage reste en place jusqu’à ce que cet utilisateur ouvre le ménage ; la carte l’indique.
- Un fichier destiné à un autre ménage, par exemple un ménage qui partage le même dossier, n’est pas touché.
- Un fichier venant d’un téléphone qui n’est pas, ou plus, jumelé, ou un fichier qui n’est pas un fichier de transfert, est déplacé dans un dossier « Not imported » à l’intérieur du dossier de transfert.
- Les confirmations qu’un téléphone n’a jamais récupérées sont supprimées après 60 jours.

### Messages après l’importation d’un fichier {#import-messages}

- « … saisies importées. Le téléphone les confirmera à son prochain transfert. » : le fichier a été lu. « rien de nouveau (déjà importé) » signifie que ses captures avaient déjà été reçues. Un fichier importé à la main ne laisse pas de confirmation dans un dossier : le téléphone montre les éléments comme envoyés jusqu’à son prochain transfert par Wi-Fi ou par le dossier, qui les confirme.
- « … vient du téléphone de … ; … doit ouvrir le ménage pour l’importer. » : le téléphone appartient à un autre utilisateur.
- « … est destiné à un autre ménage. »
- « … vient d’un téléphone qui n’est pas jumelé à ce ménage. » : jumelez le téléphone de nouveau.
- « … n’est pas un fichier de transfert RANN’s Roost. »

## Ce que devient ce qu’envoie un téléphone {#received}

- Un reçu, une facture ou un autre document devient un document dans [Documents](documents), dans l’onglet **À vérifier**. Plusieurs pages deviennent un seul PDF ; une seule page reste une image ; un PDF partagé sur le téléphone reste tel quel.
- Le texte lu sur le téléphone l’accompagne. Quand le téléphone n’a pas pu le lire, cet ordinateur le lit.
- Ce que la personne a saisi sur le téléphone (commerce ou fournisseur, date, montant et note) remplace ce qui a été lu. Une facture est classée comme facture ; un reçu ou une dépense rapide comme reçu.
- Une dépense rapide sans photo devient un court document texte avec le commerce, la date, le montant et la note, à vérifier comme les autres.
- Une note vocale enregistrée avec une capture est gardée avec son document ; écoutez-la depuis la fenêtre de vérification du document.
- Une lecture d’odomètre ou d’heures est ajoutée aux lectures de ce véhicule dans l’écran [Véhicules](vehicles), ou au compteur de cet équipement dans [Maison et biens](assets), sans vérification.
- Chaque capture n’est reçue qu’une fois, même quand le téléphone l’envoie de nouveau.

Vérifiez chaque document dans l’onglet **À vérifier** : rattachez-le à une opération, inscrivez-le sur une facture, ou classez-le.

## Ce que reçoit le téléphone {#sent-to-phone}

Après chaque transfert, le téléphone reçoit un résumé à jour quand quelque chose y a changé : le nom et la langue du ménage, la devise de base, vos comptes et leurs soldes, les catégories, jusqu’à 400 bénéficiaires, les membres et les animaux, les véhicules et les équipements à compteur avec leur dernière lecture, les factures à payer dans les 60 prochains jours avec leurs jours de rappel, les budgets du mois pour les catégories de dépenses, et l’entretien prévu ce mois-ci. Le téléphone l’affiche dans son onglet **Résumé** et s’en sert pour ses rappels et ses listes de choix. Voir [L’onglet Résumé](phone-app#summary-tab).
