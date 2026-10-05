# Dépannage

Ce chapitre présente les problèmes les plus courants, avec le message affiché par l'application, ce qu'il signifie et quoi faire. Cherchez les mots du message que vous voyez ; l'onglet Recherche du manuel les trouve aussi.

## La connexion {#signing-in}

@index: problèmes de connexion; impossible d'ouvrir le ménage

### Nom d'utilisateur ou mot de passe incorrect {#wrong-password}

@index: Nom d'utilisateur ou mot de passe incorrect; impossible de se connecter

L'écran de déverrouillage affiche « Nom d'utilisateur ou mot de passe incorrect. ». Il ne dit pas lequel est faux, pour que personne ne puisse découvrir quels noms d'utilisateur existent.

- Vérifiez le nom d'utilisateur : c'est le nom court choisi à la création de votre utilisateur, et non votre nom complet. Les majuscules n'y comptent pas.
- Les majuscules comptent dans le mot de passe : vérifiez que le verrouillage des majuscules est désactivé et que le clavier est dans la langue attendue.
- Assurez-vous d'ouvrir le bon ménage : son dossier est indiqué sous le titre.
- Si on vous a retiré le droit de vous connecter (sous Utilisateurs, **Peut se connecter** décoché), demandez à un administrateur de le cocher de nouveau.
- Si vous avez oublié le mot de passe, voir la section suivante.

### Un mot de passe oublié {#forgot-password}

@index: mot de passe oublié; mot de passe perdu; réinitialiser

1. Dans l'écran de déverrouillage, cliquez sur **Mot de passe oublié?**.
2. Entrez votre **Nom d'utilisateur**, votre **Clé de récupération** et deux fois un **Nouveau mot de passe**.
3. Cliquez sur **Réinitialiser le mot de passe**. Le ménage s'ouvre avec le nouveau mot de passe.

Un administrateur ne peut pas réinitialiser le mot de passe d'un autre utilisateur à sa place : chaque utilisateur a besoin de sa propre clé de récupération. Voir [Réinitialiser le mot de passe avec la clé de récupération](basics#reset-screen).

### Cette clé de récupération n'est pas valide {#invalid-key}

@index: Cette clé de récupération n'est pas valide; clé de récupération refusée

- La clé doit être celle de l'utilisateur nommé dans **Nom d'utilisateur** : chacun a la sienne.
- Cherchez les fautes de frappe. Les tirets, les espaces et les majuscules ne comptent pas, et I, L et O sont lus comme 1, 1 et 0, mais tous les autres caractères comptent. Les deux derniers caractères détectent la plupart des fautes de frappe.
- La clé d'un autre ménage ne fonctionne pas, même pour la même personne.

Si ni le mot de passe ni la clé de récupération ne peuvent être retrouvés, personne ne peut ouvrir le ménage, ni ses sauvegardes. Commencez un nouveau ménage ; voir [Confidentialité, données et sécurité](privacy-data#forgotten-password).

### Le ménage s'est verrouillé de lui-même {#locked-itself}

@index: verrouillage automatique; verrouillé tout seul; déconnecté

Le ménage se verrouille après une période sans activité du clavier ou de la souris (10 minutes au départ). Reconnectez-vous ; tout ce qui était enregistré est encore là, mais un formulaire laissé ouvert sans être enregistré est perdu. Pour verrouiller plus tard, ou jamais, changez **Verrouiller après une période d'inactivité de** sous [Sécurité](security). Ce réglage ne s'applique qu'à cet ordinateur.

### Le ménage n'est pas dans Ménages récents {#not-in-recent}

@index: ménage introuvable; ménage manquant

La liste présente les cinq derniers ménages ouverts sur cet ordinateur dont le dossier existe encore. Si le dossier a été déplacé ou renommé, cliquez sur **Ouvrir un ménage existant** et choisissez le dossier lui-même, dont le nom se termine par .hfm. S'il a été supprimé, restaurez-le à partir d'une sauvegarde avec **Restaurer une sauvegarde…** ; voir [Restaurer une sauvegarde](basics#restore).

### La création d'un ménage échoue {#create-fails}

@index: Une erreur s'est produite; le dossier n'est pas vide

Si **Créer le ménage** affiche « Une erreur s'est produite : » suivi d'un message indiquant que le dossier n'est pas vide, un dossier au nom du ménage existe déjà à l'emplacement choisi et contient des fichiers. Choisissez un autre emplacement ou un autre nom de ménage. Le bouton reste grisé tant que le dossier, le nom du ménage, votre nom et le nom d'utilisateur ne sont pas tous remplis.

## L'enregistrement et les permissions {#saving}

@index: impossible d'enregistrer; problèmes d'enregistrement

### Impossible d'enregistrer {#cannot-save}

@index: Impossible d'enregistrer; fenêtre d'erreur

La fenêtre Impossible d'enregistrer donne la raison en une phrase, comme « Un nom est requis. » ou « Entrez la date au format AAAA-MM-JJ. ». Cliquez sur **OK**, corrigez ce qu'elle indique et enregistrez de nouveau. Rien n'a été changé. Le chapitre de référence de l'écran explique les règles de chaque champ.

### Vous n'avez pas la permission de faire ceci {#permission}

@index: Vous n'avez pas la permission de faire ceci; accès refusé; lecture seulement

Votre rôle, ou l'accès qu'on vous a donné à ce groupe de comptes, ne permet pas cette modification. Les lecteurs peuvent seulement consulter ; les membres peuvent modifier leurs propres comptes privés et les groupes partagés auxquels on leur a donné l'accès Modification ; Saisie seulement permet d'ajouter des reçus et des opérations. Les lecteurs ne peuvent pas non plus modifier les listes du ménage (catégories, bénéficiaires, règles, institutions, taux), et seuls les administrateurs modifient les membres du ménage, les réglages de sauvegarde et les téléchargements de cours. Demandez à un administrateur de changer votre accès sous [Utilisateurs](users).

### Des dates ou des nombres sont refusés {#dates-numbers}

@index: Entrez la date au format AAAA-MM-JJ; L'un des nombres n'est pas valide; date non valide

- Les dates se tapent au format AAAA-MM-JJ, comme 2026-03-05, dans les deux langues. Le champ reste rouge tant que la date n'est pas valide.
- Les montants utilisent les séparateurs de votre langue (1 234,56 en français, 1,234.56 en anglais). Un point d'interrogation sous un champ de montant signifie qu'il ne peut pas être lu. Retirez les lettres et les caractères égarés ; un signe $ ou le code de la devise sont acceptés.
- Les heures se tapent au format HH:MM, comme 09:30.

Voir [Les commandes courantes](basics#controls).

### Modifier une opération rapprochée? {#reconciled}

@index: opération rapprochée; relevé verrouillé

Cette question paraît quand vous modifiez une opération qui fait partie d'un rapprochement terminé. Si vous allez de l'avant avec **Modifier**, le compte ne concorde plus avec ce relevé, et la modification est inscrite dans l'historique. Si c'est le rapprochement lui-même qui était faux, annulez-le plutôt : dans les **Relevés** du compte, **Annuler le rapprochement** rouvre le plus récent (donnez une raison ; seul le plus récent peut être annulé) : le relevé redevient En cours, pour être rapproché de nouveau. Voir [Comptes](accounts).

## L'importation des relevés {#importing}

@index: problèmes d'importation; importation de relevés; OFX; CSV

### Ce type de fichier ne peut pas être importé {#import-format}

@index: Ce type de fichier ne peut pas être importé

Les relevés s'importent à partir de fichiers OFX, QFX, QBO ou CSV. Téléchargez de nouveau le relevé du site de votre banque en choisissant l'un de ces formats (souvent appelés Quicken, Money, Microsoft Money, QuickBooks, OFX ou « tableur (CSV) »). Un relevé PDF ne peut pas être importé de cette façon. Gardez-le dans [Documents](documents), où la [lecture par IA](ai), si vous l'utilisez, peut le lire et proposer **Rapprocher avec ce relevé**, ou entrez un relevé papier à la main à partir des **Relevés** du compte. L'exportation propre à Quicken (QIF) s'importe avec **Importer de Quicken…** ; voir [Comptes](accounts).

### Le fichier n'a pas pu être lu {#import-failed}

@index: Le fichier n'a pas pu être lu

Le fichier a la bonne extension, mais son contenu est abîmé ou n'est pas ce qu'il prétend être. Téléchargez-le de nouveau ; ouvrez un CSV dans un éditeur de texte pour vérifier qu'il contient des opérations. Si la banque offre plusieurs formats, essayez-en un autre.

### Le relevé est dans une autre monnaie {#import-currency}

@index: Le relevé est en; monnaie différente

« Le relevé est en USD, mais le compte est en CAD. » signifie que le fichier appartient à un compte dans une autre monnaie. Importez-le plutôt dans ce compte, ou créez un compte dans la monnaie du relevé. La devise d'un compte ne peut pas être changée.

### Déjà importé {#already-imported}

@index: Ce fichier de relevé a déjà été importé dans ce compte; doublons

« Ce fichier de relevé a déjà été importé dans ce compte. » Le même fichier a déjà été importé ; rien n'est ajouté deux fois. Quand un nouveau fichier chevauche un fichier précédent de quelques jours, les lignes déjà importées sont marquées Déjà importée et ne sont pas ajoutées de nouveau.

### Le relevé ne contient aucune opération {#import-empty}

@index: Le relevé ne contient aucune opération

Le fichier ne contient aucune opération pour les dates choisies sur le site de la banque. Téléchargez-le de nouveau pour une période où il y a eu de l'activité.

### Un fichier de plusieurs comptes {#several-accounts}

@index: Ce fichier contient plusieurs comptes

Certaines banques mettent tous vos comptes dans un seul fichier. L'application demande « Ce fichier contient plusieurs comptes. Lequel correspond à ce compte? » et présente chacun avec son numéro de compte, sa devise et son nombre d'opérations. Cliquez sur le bon. Pour importer les autres, ouvrez chaque compte et importez de nouveau le même fichier en choisissant sa ligne.

### Les colonnes du CSV sont mauvaises {#csv}

@index: colonnes CSV; format de date; virgule décimale; signes inversés

Dans la fenêtre CSV, comparez chaque choix avec l'aperçu :

- **La première ligne contient le nom des colonnes** : cochez-la quand la première ligne contient des titres, pour qu'elle ne soit pas lue comme une opération.
- **Colonne de la date** et **Format de date** : des dates mal lues (le 4 mars au lieu du 3 avril) indiquent que le mauvais format est choisi.
- **Montants** : choisissez Une colonne, négative pour les sorties d'argent, ou Colonnes séparées pour les retraits et les dépôts, selon le fichier.
- **Les montants utilisent la virgule décimale (1 234,56)** : cochez-la pour les fichiers des sites bancaires en français.
- **Les achats sont des nombres positifs (inverser les signes)** : cochez-la quand les achats arrivent comme des dépôts, comme dans bien des fichiers de cartes de crédit.

La disposition est retenue pour l'institution, ou pour le compte s'il n'en a pas, de sorte que la prochaine importation ne demande aucun changement.

### Les lignes ne correspondent pas {#not-matching}

@index: jumelage; À confirmer; Aucune correspondance; Même opération; Ajouter comme nouvelle

Après une importation, chaque ligne du relevé a un état : Jumelée, Ajoutée, À confirmer, Déjà importée, Aucune correspondance ou Ignorée. Les lignes sous À vérifier demandent une décision :

- À confirmer : l'application a trouvé une opération inscrite qui est probablement la même. Cliquez sur **Même opération** si c'est le cas, ou sur **Ajouter comme nouvelle** sinon.
- Aucune correspondance : cliquez sur **Ajouter comme nouvelle**, ou sur **Jumeler à une opération inscrite** pour l'associer à une opération entrée à la main. Une ligne ne peut être jumelée qu'à une opération du même montant, et une opération ne peut être jumelée qu'à une seule ligne.
- **Ignorer** : pour une ligne qui ne doit pas entrer dans les comptes.
- **Annuler le jumelage** : défait un mauvais jumelage.

Quand une opération entrée à la main diffère un peu (un pourboire, une conversion de devise), corrigez d'abord son montant, puis jumelez-la. Les règles de catégorie et les alias de bénéficiaires aident les importations futures ; voir [Règles de catégorie](rules) et [Bénéficiaires](payees).

### L'écart n'est pas nul {#difference}

@index: L'écart doit être nul pour terminer; écart de rapprochement; Certaines lignes du relevé demandent encore une décision

Pour terminer un rapprochement, entrez le **Solde de clôture du relevé** et ramenez l'écart à zéro.

- « Certaines lignes du relevé demandent encore une décision. » : réglez d'abord chaque ligne sous À vérifier.
- Il reste un écart : cherchez une opération inscrite deux fois, une opération manquante ou un montant mal tapé. Sous Inscrites, mais absentes de ce relevé, ne cochez que les opérations qui figurent sur le relevé.
- Vérifiez le solde et la date d'ouverture du compte : un solde d'ouverture erroné paraît comme un écart au premier rapprochement.

## Les documents et leur lecture {#documents}

@index: problèmes de documents; reçus; numérisation

### Un fichier est refusé {#file-not-accepted}

@index: Illisible; Le fichier est vide ou dépasse 50 Mo; Ce genre de fichier ne peut pas être gardé dans le coffre

- « Illisible : …. Utilisez un fichier PDF, JPEG, PNG ou HEIC. » : le coffre accepte les fichiers PDF, les photos (JPEG, PNG, HEIC) et les courriels enregistrés. Enregistrez ou imprimez d'abord les autres fichiers en PDF.
- « Le fichier est vide ou dépasse 50 Mo. » : numérisez à une résolution plus basse, ou divisez un long PDF.
- « 1 était déjà dans le coffre. » : le même fichier a déjà été importé ; il n'est pas gardé deux fois.

### Le texte a été mal lu {#ocr}

@index: reconnaissance du texte; ROC; À vérifier : difficile à lire; reçu flou

Les documents sont lus sur cet ordinateur. Les champs dont l'application n'était pas sûre sont marqués « À vérifier : difficile à lire » ; comparez-les avec l'image et corrigez-les avant d'enregistrer.

- Pour une meilleure lecture, photographiez le reçu à plat, sous un bon éclairage, en remplissant le cadre, sans ombres ; numérisez à 300 ppp.
- Les reçus thermiques pâlis se lisent mal ; photographiez-les peu après l'achat.
- Pour les documents difficiles, vous pouvez les faire lire par l'IA avec votre propre clé ; voir [Lecture par IA](ai).

### Les photos HEIC {#heic}

@index: HEIC; photos d'iPhone; HEIF; aucun décodeur HEIC

HEIC est le format de photo des iPhone et de certains téléphones Android. L'application le lit avec un décodeur installé sur l'ordinateur.

- « Gardés dans le coffre mais non lus, faute de décodeur HEIC sur cet ordinateur » : la photo est en sécurité dans le coffre, mais elle n'a pas pu être lue ni affichée.
- Sous Windows, installez Extensions d'images HEIF et Extensions vidéo HEVC à partir du Microsoft Store, puis ouvrez la photo de nouveau.
- Sous Linux, installez la prise en charge HEIC de votre distribution (libheif avec son module HEVC, comme libheif-plugin-libde265), puis redémarrez l'application.
- Ou réglez le téléphone ou l'appareil photo pour qu'il enregistre les photos en JPEG.

### Doublon possible {#duplicate}

@index: Doublon possible; document en double

« Doublon possible : un autre document a le même commerce, la même date et le même montant. » Le même reçu est peut-être arrivé deux fois, par exemple du téléphone et d'un numériseur. Ouvrez les deux ; supprimez-en un s'ils sont pareils, ou gardez les deux s'il s'agit vraiment de deux achats.

### Les problèmes de lecture par IA {#ai}

@index: erreurs de lecture par IA; clé Anthropic; La clé a été refusée

- « Pour lire avec l'IA, ajoutez votre clé sous Lecture par IA. » : aucune clé n'est encore enregistrée sur cet ordinateur ; voir [Lecture par IA](ai).
- « La clé a été refusée. Vérifiez-la sous Lecture par IA. » : la clé a été mal tapée, supprimée ou désactivée sur console.anthropic.com. Utilisez **Vérifier la clé (gratuit)** après l'avoir enregistrée.
- « Anthropic a refusé pour l'instant : trop de demandes, ou plus de crédit dans votre compte. » : attendez un peu, ou ajoutez du crédit à votre compte Anthropic.
- « Impossible de joindre Anthropic. Vérifiez la connexion Internet. » : l'ordinateur n'est pas en ligne ou un pare-feu bloque la connexion.
- « Claude a refusé de lire ce document. » ou « La réponse n'a pas pu être vérifiée, même après une seconde demande ; entrez les champs à la main. » : entrez les champs vous-même ; rien n'est perdu.
- « Aucun trousseau n'est actif sur cet ordinateur : la clé n'est gardée que jusqu'à la fermeture de l'application. » : sous Linux sans trousseau, entrez la clé de nouveau après chaque démarrage, ou démarrez le trousseau de votre bureau.

## Le téléphone {#phone}

@index: problèmes de téléphone; jumelage; RANN's Roost Mobile; Wi-Fi

### Le code de jumelage a expiré {#code-expired}

@index: Ce code a expiré; code de jumelage

Un code de jumelage sert une seule fois, pendant 10 minutes ; la fenêtre affiche le compte à rebours. « Ce code a expiré. Fermez et jumelez de nouveau. » : fermez la fenêtre et cliquez de nouveau sur **Jumeler un téléphone** pour obtenir un nouveau code.

### Les téléphones ne peuvent pas joindre cet ordinateur {#cannot-reach}

@index: Les téléphones ne peuvent pas joindre cet ordinateur; pare-feu; pas connecté à un réseau local

L'écran Téléphones indique si les téléphones peuvent joindre l'ordinateur, et à quelle adresse.

- « Cet ordinateur n'est pas connecté à un réseau local. » : connectez l'ordinateur au Wi-Fi ou au réseau de la maison.
- « Les téléphones ne peuvent pas joindre cet ordinateur : » suivi d'une raison : l'application n'a pas pu se mettre à l'écoute des téléphones. Verrouillez le ménage et ouvrez-le de nouveau ; si le message reste, redémarrez l'ordinateur.
- Vérifiez que le téléphone est sur le même Wi-Fi que l'ordinateur, et non sur les données cellulaires ou un réseau d'invités.
- Sous Windows, quand on vous demande d'autoriser l'application sur les réseaux, autorisez-la sur les réseaux privés. Si vous avez refusé, autorisez RANN's Roost dans les réglages du pare-feu de Windows, et assurez-vous que votre réseau de la maison est réglé comme réseau privé.
- Si l'adresse de l'ordinateur a changé (un nouveau routeur, par exemple), jumelez de nouveau le téléphone.

### Rien n'arrive du téléphone {#nothing-arrives}

@index: saisies qui n'arrivent pas; À vérifier

- Le ménage doit être ouvert sur l'ordinateur : les téléphones ne peuvent rien envoyer à un ménage verrouillé.
- Un téléphone n'envoie qu'à l'utilisateur qui l'a jumelé. Si un autre utilisateur est connecté, le téléphone garde ses saisies et en indique la raison ; elles arrivent quand son propriétaire ouvre le ménage.
- Les saisies n'entrent jamais directement dans les comptes : regardez l'onglet À vérifier de [Documents](documents). L'entrée Documents du menu indique combien attendent.
- Dans l'écran Téléphones, chaque téléphone indique quand il a envoyé pour la dernière fois et combien d'éléments ont été reçus.

### Les fichiers et le dossier de transfert {#transfer-files}

@index: dossier de transfert; roostsync; loin de la maison

- « Le dossier de transfert n'a pas pu être lu : » : le dossier a été déplacé, ou l'application du service infonuagique ne fonctionne pas. Choisissez-le de nouveau sous [Téléphones](phones).
- « … est destiné à un autre ménage. » : le fichier appartient à un autre ménage ; ouvrez ce ménage.
- « … vient d'un téléphone qui n'est pas jumelé à ce ménage. » : jumelez d'abord le téléphone.
- « … vient du téléphone de … ; … doit ouvrir le ménage pour l'importer. » : seul le propriétaire du téléphone peut l'importer ; le fichier l'attend.
- « … n'est pas un fichier de transfert RANN's Roost. » : le fichier est abîmé ou d'une autre sorte.

### Un téléphone perdu ou remplacé {#lost-phone}

@index: téléphone perdu; téléphone volé; retirer un téléphone

Sous [Téléphones](phones), cliquez sur **Retirer** à côté du téléphone : il ne peut plus rien envoyer ni recevoir. Un téléphone ne détient jamais les clés du ménage ; il n'y a donc rien d'autre à faire. **Oublier** supprime ensuite de la liste un téléphone retiré. Jumelez le nouveau téléphone comme d'habitude.

## Les sauvegardes {#backups}

@index: problèmes de sauvegarde; sauvegarde échouée

- « Aucune sauvegarde réussie dans les 7 derniers jours » au tableau de bord : ouvrez [Sauvegardes](backups), lisez le Dernier problème, puis cliquez sur **Sauvegarder maintenant**.
- « Choisissez d'abord un dossier de sauvegarde. » : choisissez un dossier avec **Choisir le dossier…**.
- Le dossier de sauvegarde est sur un disque qui n'est pas branché : branchez-le, ou choisissez un autre dossier. Un dossier infonuagique exige que l'application de son service fonctionne.
- « La vérification de la sauvegarde a échoué : » : le fichier dans le dossier est abîmé. Faites une nouvelle sauvegarde tout de suite, et vérifiez le disque.
- « Conservez de 1 à 365 versions. » : entrez un nombre dans cet intervalle dans **Versions à conserver**.
- La restauration : une sauvegarde s'ouvre avec les mots de passe et les clés de récupération en usage au moment où elle a été faite.

## Les mises à jour {#updates}

@index: problèmes de mise à jour; mise à jour échouée

Ces messages paraissent sous Mises à jour dans [À propos](about), sur les copies qui vérifient les mises à jour.

- « Impossible de joindre GitHub pour vérifier les mises à jour. » : l'ordinateur n'est pas en ligne ou GitHub n'est pas disponible. L'application réessaie le lendemain, ou cliquez sur **Vérifier maintenant**.
- « La mise à jour a été refusée parce qu'on n'a pas pu confirmer qu'elle est une version signée par RANN. Rien n'a été installé. » : la liste des mises à jour ne portait pas une signature valide de RANN. Votre copie n'a pas changé ; réessayez plus tard.
- « Le téléchargement ne correspondait pas à la version signée par RANN ; il a été supprimé. » : le fichier a été abîmé ou modifié en route. Cliquez de nouveau sur **Télécharger et vérifier**.
- « Le téléchargement a échoué. » : vérifiez la connexion et l'espace libre dans Téléchargements, puis réessayez.
- « Cette copie ne vérifie pas les mises à jour. » : les copies du Microsoft Store et de Flathub sont mises à jour par la boutique ; une copie compilée à partir du code source se met à jour en la compilant de nouveau.

## Les rappels et les notifications {#reminders}

@index: aucune notification; rappels manquants

- Les rappels et les notifications exigent que le ménage soit ouvert ; rien n'est annoncé quand il est fermé ou verrouillé.
- Une facture ne fait l'objet d'un rappel qu'aux jours entrés dans **Me le rappeler (jours avant)**, et seulement si elle est **Active**.
- Si aucune notification de l'ordinateur ne paraît alors que le bandeau montre des rappels, vérifiez que les notifications sont permises pour RANN's Roost dans les réglages de votre système (sous Windows, Paramètres, Système, Notifications).

## Obtenir de l'aide {#getting-help}

@index: soutien; nous joindre; signaler un problème; billets GitHub

Si ce chapitre ne règle pas le problème, écrivez à info-rann-apps@NorthMail.ca, ou ouvrez un billet sur GitHub avec **Billets GitHub** sous [À propos](about). Dites ce que vous avez fait, ce que vous attendiez, le message exact et la version indiquée sous À propos.

> Important : n'envoyez jamais de mots de passe, de clés de récupération, de sauvegardes ni de détails financiers, ni par courriel ni sur GitHub.
