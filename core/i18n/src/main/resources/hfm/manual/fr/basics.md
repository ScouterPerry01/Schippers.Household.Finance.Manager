# S'y retrouver

Ce chapitre décrit ce que vous voyez avant qu'un ménage soit ouvert (les écrans de départ), puis la fenêtre principale : la barre du haut, le menu, la recherche, le verrouillage, les messages, les commandes utilisées dans chaque formulaire, ainsi que les rappels et les notifications. Les autres chapitres tiennent pour acquis que vous connaissez ce qui se trouve ici.

## Les écrans de départ {#start-screens}

@index: écran de départ; connexion; ouvrir un ménage

Quand RANN's Roost démarre, et chaque fois qu'un ménage est verrouillé, l'application présente les écrans de départ. La barre du haut contient déjà les boutons **English** et **Français** et **Aide (F1)** : vous pouvez donc changer de langue et lire l'aide avant de vous connecter.

### Bienvenue {#welcome-screen}

@index: écran Bienvenue; ménages récents

L'écran Bienvenue est le premier que vous voyez. Il présente le nom de l'application et ces choix :

- **Créer un nouveau ménage** : ouvre l'écran [Créer un ménage](basics#create-screen), pour commencer de nouveaux comptes.
- **Ouvrir un ménage existant** : ouvre un sélecteur de dossier. Choisissez le dossier du ménage lui-même (son nom se termine par .hfm, comme Famille Tremblay.hfm). L'écran [Déverrouiller le ménage](basics#unlock-screen) s'ouvre ensuite pour lui.
- **Restaurer une sauvegarde…** : ramène un ménage à partir d'un fichier de sauvegarde (.hfmbak), par exemple après une panne de disque ou pour passer à un nouvel ordinateur. Voir [Restaurer une sauvegarde](basics#restore).
- **À propos et confidentialité** : présente la page [À propos](about) (version, mises à jour, confidentialité, licence, soutien) avant qu'un ménage soit ouvert. **Retour** ramène à Bienvenue.
- **Ménages récents** : les cinq derniers ménages ouverts sur cet ordinateur, du plus récent au plus ancien. Cliquez sur l'un d'eux pour aller directement à son écran de déverrouillage. Un ménage dont le dossier a été déplacé, renommé ou supprimé n'y paraît plus ; utilisez **Ouvrir un ménage existant** pour le retrouver.

### Restaurer une sauvegarde {#restore}

@index: restaurer; fichier de sauvegarde; hfmbak; nouvel ordinateur

1. Dans Bienvenue, cliquez sur **Restaurer une sauvegarde…**.
2. Choisissez le fichier de sauvegarde. Le sélecteur présente les Sauvegardes de ménage (.hfmbak).
3. Choisissez où placer le ménage restauré : un dossier, comme Documents.
4. L'application y crée un nouveau dossier de ménage, au nom de la sauvegarde (avec (2), (3), etc. si le nom est déjà pris) ; le bouton affiche **Restauration…** pendant ce temps. Une sauvegarde n'est jamais restaurée par-dessus un ménage existant.
5. L'écran de déverrouillage s'ouvre pour le ménage restauré. Connectez-vous avec le nom d'utilisateur et le mot de passe en usage au moment de la sauvegarde, ou réinitialisez le mot de passe avec votre clé de récupération.

Si la sauvegarde ne peut pas être restaurée, un message sous le bouton en donne la raison. Voir [Sauvegardes](backups) pour faire des sauvegardes, et [Confidentialité, données et sécurité](privacy-data#new-computer) pour passer à un nouvel ordinateur.

### Créer un ménage {#create-screen}

@index: créer un ménage; nouveau ménage; dossier du ménage; administrateur

Cet écran crée un nouveau ménage vide. La province, votre nom et votre mot de passe peuvent être changés plus tard ; le dossier, le nom du ménage et le nom d'utilisateur ne le peuvent pas.

- **Choisir un dossier…** : obligatoire. Ouvre un sélecteur de dossier pour l'Emplacement : le dossier où le ménage sera gardé, comme Documents. Le chemin choisi s'affiche à côté du bouton. L'application crée dedans un nouveau dossier au nom du ménage, terminé par .hfm (Famille Tremblay.hfm). Ce dossier ne doit pas déjà exister avec des fichiers ; sinon, choisissez un autre emplacement ou un autre nom.
- **Nom du ménage** : obligatoire. Le nom du ménage, utilisé pour le nom du dossier et présenté aux téléphones que vous jumelez. Évitez les caractères que votre système refuse dans les noms de dossier, comme / ou :.
- **Province ou territoire** : où habite le ménage. Ses règles s'appliquent : les jours fériés bancaires qui déplacent les dates des factures, les catégories par défaut, les subventions provinciales aux REEE, les règles des régimes immobilisés et les formulaires fiscaux provinciaux. Obligatoire : rien n'est choisi au départ, choisissez donc le vôtre. Il peut être changé plus tard sous [Membres du ménage](members), et une personne qui vit ailleurs peut avoir le sien.
- **Votre nom** : obligatoire. Votre nom tel qu'il paraît dans l'application, par exemple dans le journal d'activité et la liste des utilisateurs.
- **Nom d'utilisateur** : obligatoire. Le nom que vous tapez pour vous connecter. Gardez-le court et sans espaces. Il ne peut pas être changé plus tard.
- **Mot de passe principal** : obligatoire. Les [règles des mots de passe](security#password-rules) intégrées s'appliquent, puisque le ménage n'existe pas encore : au moins 12 caractères, sans le nom d'utilisateur; la ligne sous le champ le rappelle. Un administrateur peut changer les règles plus tard pour tout le ménage. Une phrase de passe de quelques mots est facile à retenir et difficile à deviner. Il protège les clés de chiffrement du ménage : il n'est jamais enregistré, et personne ne peut le réinitialiser pour vous.
- **Confirmer le mot de passe principal** : le même mot de passe une seconde fois, pour attraper les fautes de frappe.
- **Retour** : revient à Bienvenue sans rien créer.
- **Créer le ménage** : disponible une fois le dossier, le nom du ménage, la province ou le territoire, votre nom et le nom d'utilisateur remplis. Si le mot de passe ne respecte pas les règles, l'écran dit ce qui lui manque, comme « Le mot de passe doit avoir au moins 12 caractères. » ; si les deux mots de passe diffèrent, il affiche « Les mots de passe ne correspondent pas. ». Sinon, l'application crée le ménage, ce qui prend quelques secondes (un cercle tourne pendant ce temps).

Le nouveau ménage a le dollar canadien comme monnaie de base. Il commence avec un groupe de comptes partagé et vous comme seul utilisateur, avec le rôle Administrateur. Le ménage est créé dans la langue qu'utilise l'application.

### Votre clé de récupération {#recovery-key-screen}

@index: clé de récupération; imprimer la clé; mot de passe oublié

Tout de suite après la création d'un ménage, cet écran présente votre clé de récupération : 54 lettres et chiffres en groupes de quatre séparés par des tirets.

Aucun serveur ne peut réinitialiser votre mot de passe. Si vous l'oubliez, cette clé est le seul moyen de retrouver l'accès à votre ménage. Imprimez-la ou notez-la et rangez-la en lieu sûr, loin de cet ordinateur.

- **Imprimer** : ouvre la fenêtre d'impression du système et imprime une page avec la clé, le nom du ménage et la raison de la garder. La page va directement à l'imprimante : la clé n'est pas enregistrée dans un fichier sur cet ordinateur.
- **Copier** : copie la clé dans le presse-papiers, pour la coller dans un gestionnaire de mots de passe. Videz ensuite le presse-papiers si d'autres personnes utilisent cet ordinateur.
- **J'ai conservé ma clé de récupération** : ouvre le ménage. La clé n'est plus jamais affichée.

> Important : chaque utilisateur a sa propre clé de récupération. Quand un administrateur ajoute un utilisateur sous [Utilisateurs](users), la clé de cet utilisateur est affichée une seule fois de la même façon, pour lui être remise.

Si le ménage se verrouille pendant que cet écran est affiché (voir [Le verrouillage](basics#locking)), la clé disparaît ; le ménage n'a rien et s'ouvre avec votre mot de passe.

### Déverrouiller le ménage {#unlock-screen}

@index: déverrouiller; se connecter; ouvrir une session

Cet écran ouvre un ménage. Le dossier du ménage est indiqué sous le titre.

- **Nom d'utilisateur** : votre nom d'utilisateur pour ce ménage. Les majuscules n'y comptent pas.
- **Mot de passe** : votre mot de passe. Les majuscules y comptent.
- **Retour** : revient à Bienvenue.
- **Déverrouiller** : disponible une fois les deux champs remplis. L'ouverture peut prendre un moment, exprès : le mot de passe est vérifié d'une manière qui ralentit les tentatives de devinette, et un cercle tourne pendant ce temps. Si le nom d'utilisateur ou le mot de passe est faux, l'écran affiche « Nom d'utilisateur ou mot de passe incorrect. » sans dire lequel.
- **Mot de passe oublié?** : ouvre [Réinitialiser le mot de passe avec la clé de récupération](basics#reset-screen).

Une fois déverrouillé, le ménage s'ouvre au tableau de bord. Un utilisateur à qui on a retiré le droit de se connecter (sous Utilisateurs, **Peut se connecter** décoché) ne peut pas le déverrouiller.

### Réinitialiser le mot de passe avec la clé de récupération {#reset-screen}

@index: réinitialiser le mot de passe; mot de passe oublié; clé de récupération

Utilisez cet écran quand un mot de passe est oublié. Il fixe un nouveau mot de passe à l'aide de la clé de récupération de cet utilisateur.

- **Nom d'utilisateur** : le nom d'utilisateur de la personne dont le mot de passe est oublié.
- **Clé de récupération** : la clé de récupération de cet utilisateur. Tapez-la avec ou sans les tirets et les espaces, en majuscules ou non ; les lettres I et L sont lues comme le chiffre 1 et la lettre O comme 0, de sorte que les caractères semblables ne nuisent pas. Les deux derniers caractères servent de contrôle : une faute de frappe est détectée.
- **Nouveau mot de passe** : le nouveau mot de passe. Il doit suivre les [règles des mots de passe](security#password-rules) du ménage (au moins 12 caractères par défaut); elles sont vérifiées une fois que la clé a ouvert le ménage, et un mot de passe qui ne les respecte pas est refusé avec ce qui lui manque.
- **Confirmer le mot de passe principal** : le nouveau mot de passe une seconde fois.
- **Retour** : revient à l'écran de déverrouillage.
- **Réinitialiser le mot de passe** : disponible une fois le nom d'utilisateur et la clé remplis. Si la clé est mal tapée ou n'appartient pas à cet utilisateur, l'écran affiche « Cette clé de récupération n'est pas valide. ». Sinon, le mot de passe est changé et le ménage s'ouvre.

La clé de récupération continue de fonctionner après la réinitialisation.

## La question sur les mises à jour {#update-question}

@index: mises à jour; Vérifier les mises à jour?; vérification des mises à jour

Sur les copies Linux installées à partir d'un paquet .deb ou .rpm ou d'une AppImage, le tout premier démarrage demande **Vérifier les mises à jour?**. RANN's Roost peut vérifier une fois par jour sur GitHub si une nouvelle version est parue et vous avertir quand elle est prête. Seule la vérification sort de l'ordinateur : GitHub voit l'adresse Internet de votre ordinateur et le fait que l'application est utilisée, comme pour toute page Web. Rien sur votre ménage n'est envoyé.

- **Vérifier une fois par jour** : active la vérification quotidienne. La première vérification se fait tout de suite.
- **Ne pas vérifier** : aucune vérification n'est faite.

La question est posée une fois par ordinateur ; changez la réponse plus tard avec **Vérifier les mises à jour une fois par jour** sous [À propos](about). Les copies du Microsoft Store ou de Flathub ne la posent jamais, puisque ces boutiques les mettent à jour, et une copie compilée à partir du code source n'a pas de vérification des mises à jour.

## La fenêtre principale {#main-window}

@index: fenêtre principale; disposition

Une fois un ménage ouvert, la fenêtre présente, de haut en bas : la barre du haut, les bandeaux s'il y en a, et en dessous le menu (à gauche ou en haut) à côté de l'écran choisi.

### La barre du haut {#top-bar}

@index: barre du haut; langue; bouton Verrouiller

La barre du haut est toujours visible. De gauche à droite :

- Le nom de l'application, RANN's Roost.
- **English** et **Français** : font passer toute l'application à cette langue d'un coup ; le bouton de la langue en usage est grisé. Le choix est retenu sur cet ordinateur et sert au prochain démarrage. Tant que vous n'avez rien choisi, l'application suit la langue de votre ordinateur. Les lignes que l'application écrit elle-même dans les comptes à partir de ce moment (comme les notes automatiques) sont écrites dans la langue en usage. Le manuel et l'aide suivent le même choix.
- **Rechercher (Ctrl+F)** : la case de recherche, présente seulement quand un ménage est ouvert. Voir [La recherche](basics#search).
- **Aide (F1)** : ouvre le court panneau d'aide au sujet de l'écran affiché. Voir [L'aide (F1) et le manuel](welcome#help-and-manual).
- **Manuel** : ouvre ce manuel dans sa propre fenêtre. Maj+F1 l'ouvre au chapitre de l'écran affiché. Voir [La fenêtre du manuel](welcome#manual-window).
- **Verrouiller** : présent seulement quand un ménage est ouvert. Verrouille le ménage immédiatement. Voir [Le verrouillage](basics#locking).

### Les bandeaux {#banners}

@index: bandeau; bandeau de mise à jour; bandeau de rappels

Deux sortes de bandeaux de couleur peuvent paraître sous la barre du haut :

- Le bandeau de mise à jour, « La version x de RANN's Roost est disponible. Voir À propos. », sur les copies qui vérifient les mises à jour. Cliquez dessus pour aller à [À propos](about), où vous pouvez télécharger la mise à jour. Il reste là jusqu'à ce que vous visitiez À propos.
- Le bandeau de rappels, comme « 3 rappels », suivi des trois premiers rappels. Il paraît dans chaque écran sauf celui auquel les rappels appartiennent. Cliquez dessus pour aller à l'écran du premier rappel. Voir [Les rappels et les notifications](basics#reminders).

### Le menu {#menu}

@index: menu; navigation; menu latéral; menu en haut

Le menu mène à chaque écran. Le **Tableau de bord** et **Contacts** sont seuls en haut, en dehors des groupes ; les autres écrans sont répartis en cinq groupes : Argent, Placements et emprunts, Rapports et impôts, Maison et famille, et Réglages.

Le menu peut se présenter de deux façons, et le choix de chaque utilisateur est retenu sur cet ordinateur :

- En liste à gauche (la disposition de départ). Cliquez sur le nom d'un groupe pour le replier (▸) ou le déplier (▾). Le groupe de l'écran où vous êtes est toujours ouvert. Réglages est replié au départ. Les groupes que vous laissez repliés sont retenus pour vous sur cet ordinateur. La liste s'élargit quand le texte est plus grand, pour que les noms tiennent sur une ligne.
- En barre en haut. Chaque groupe est un bouton qui ouvre une liste déroulante de ses écrans. Le groupe et l'écran en usage sont en gras.

- **Menu en haut** : au bas de la liste de gauche ; place le menu en barre en haut.
- **Menu à gauche** : au bout de la barre du haut ; remet le menu à gauche.

Les nombres entre parenthèses indiquent ce qui vous attend. Documents indique le nombre de documents qui attendent dans son onglet À vérifier, par exemple Documents (3). Quand un groupe est replié, ou dans la barre du haut, le nom du groupe indique le total de ses écrans, comme Argent (3).

### Les groupes du menu et leurs écrans {#menu-groups}

@index: écrans; parties de l'application

- Tableau de bord : un aperçu des soldes, de ce qui demande votre attention et du [guide des premiers pas](quick-start#guide). Voir [Tableau de bord](dashboard).
- Contacts : les banques, conseillers, assureurs, médecins, pharmacies et tous ceux avec qui le ménage fait affaire, chacun avec ce pour quoi il sert, et liés aux éléments qui les concernent. Voir [Contacts](contacts).
- Argent : [Comptes](accounts), [Documents](documents), [Factures](bills), [Budgets](budgets), [Objectifs d'épargne](goals), [Argent en famille](family), [Revenus d'appoint](side) et [Calendrier](calendar).
- Placements et emprunts : [Placements](investments), [Régimes enregistrés](plans) et [Prêts et hypothèques](loans).
- Rapports et impôts : [Rapports](reports) et [Impôts](taxes).
- Maison et famille : [Santé](health), [Réclamations médicales](medical), [Urgence et succession](estate), [Animaux](pets), [Véhicules](vehicles), [Déplacements](trips) et [Maison et biens](assets).
- Réglages : [Membres du ménage](members), [Utilisateurs](users), [Catégories](categories), [Bénéficiaires](payees), [Règles de catégorie](rules), [Institutions financières](institutions), [Taux et cours](rates), [Téléphones](phones), [Lecture par IA](ai), [Sauvegardes](backups), [Sécurité](security), [Affichage et accessibilité](display) et [À propos](about).

Ce que chaque utilisateur voit dans ces écrans dépend de son rôle et des groupes de comptes qu'il peut ouvrir ; voir [Utilisateurs](users).

## La recherche {#search}

@index: recherche; trouver; Ctrl+F; recherche globale

Une seule case de recherche parcourt tout le ménage.

### La case de recherche {#search-box}

- **Rechercher (Ctrl+F)** : dans la barre du haut. Cliquez dessus, ou appuyez sur Ctrl+F de n'importe où dans le ménage, tapez au moins deux caractères et appuyez sur Entrée. Les recherches plus courtes sont ignorées.

La recherche parcourt tout ce que vous avez le droit de voir, y compris les comptes fermés et les bénéficiaires et catégories archivés. Les majuscules et les accents ne comptent pas : epicerie trouve Épicerie.

- Les opérations dont le bénéficiaire, la note ou un autre texte contient ce que vous avez tapé. Si vous tapez un montant, comme 45,99, les opérations de ce montant sont aussi trouvées, que l'argent soit sorti ou entré.
- Les comptes par leur nom ou leurs notes ; les bénéficiaires par leur nom ; les catégories par leur nom français ou anglais ; les factures par leur nom ou leur bénéficiaire ; les institutions par leur nom.
- Les documents par le texte lu, leur titre, le commerce ou le fournisseur, leurs notes et le nom du fichier.

### La fenêtre des résultats {#search-results}

@index: résultats de recherche

Les résultats s'ouvrent dans une fenêtre intitulée Recherche : suivi de ce que vous avez tapé. Ils sont groupés par sorte, chaque groupe avec le nombre trouvé :

- Opérations : la date, le compte, le bénéficiaire, la note et le montant, des plus récentes aux plus anciennes, jusqu'à 200. Cliquez sur une opération pour ouvrir le registre de son compte, déroulé jusqu'à elle et avec l'opération ouverte dans le formulaire de saisie, prête à être modifiée.
- Comptes : cliquez sur un compte pour l'ouvrir.
- Bénéficiaires, Catégories, Factures et Institutions : cliquez sur un résultat pour ouvrir cet écran.
- Documents : cliquez sur un document pour ouvrir l'écran Documents sur lui.

Quand rien ne correspond, la fenêtre affiche « Aucun résultat. ». **Fermer** ferme la fenêtre sans aller nulle part.

## Le verrouillage {#locking}

@index: verrouiller; verrouillage automatique; inactivité; déconnexion

Un ménage verrouillé est fermé : ses clés sont effacées de la mémoire et rien ne peut être lu tant que quelqu'un ne s'est pas reconnecté. Le verrouillage ramène à l'écran [Déverrouiller le ménage](basics#unlock-screen) du même ménage.

- **Verrouiller** : dans la barre du haut ; verrouille immédiatement. Utilisez-le quand vous vous éloignez.
- Fermer la fenêtre verrouille aussi le ménage avant que l'application se ferme.
- Verrouillage automatique : après une durée choisie sans activité du clavier ou de la souris, le ménage se verrouille de lui-même. Elle est de 10 minutes au départ ; choisissez 1, 5, 10, 15, 30 ou 60 minutes, ou Jamais, sous [Sécurité](security). Ce réglage s'applique à cet ordinateur. La vérification se fait toutes les 15 secondes : le verrouillage peut donc survenir quelques secondes après la durée choisie.

Tant que le ménage est verrouillé, les téléphones ne peuvent rien envoyer à cet ordinateur, les rappels et les notifications s'arrêtent, et les sauvegardes automatiques, les rapports planifiés et les téléchargements de cours attendent sa réouverture. Ce qui n'est pas enregistré dans un formulaire ouvert est perdu : enregistrez avant de vous éloigner.

## Les messages et les erreurs {#messages}

@index: message d'erreur; Impossible d'enregistrer; avertissement

L'application vous signale les problèmes de quelques façons :

- Sous un champ, en rouge : la valeur ne peut pas être utilisée telle qu'elle est tapée, comme une date qui n'est pas au format AAAA-MM-JJ ou un code de devise inconnu. Le contour du champ devient rouge aussi. Corrigez la valeur ; le bouton Enregistrer reste souvent grisé tant qu'elle n'est pas valide.
- Sous un formulaire ou un bouton, en rouge : l'action a été refusée, comme « Nom d'utilisateur ou mot de passe incorrect. » ou « Une erreur s'est produite : » suivi de détails.
- La fenêtre Impossible d'enregistrer : affichée quand une modification est refusée, avec la raison, comme « Vous n'avez pas la permission de faire ceci. », « Un nom est requis. » ou « Entrez la date au format AAAA-MM-JJ. ». Cliquez sur **OK**, corrigez ce qu'elle indique et réessayez. Rien n'a été changé.
- **Modifier une opération rapprochée?** : affichée quand vous modifiez une opération qui fait partie d'un rapprochement terminé. La modifier fera en sorte que le compte ne concordera plus avec ce relevé, et la modification est inscrite dans l'historique. **Modifier** va de l'avant ; **Annuler** laisse l'opération telle quelle.
- Les fenêtres de confirmation avant une suppression : elles nomment ce qui sera supprimé et ce qui est conservé. Une suppression ne peut pas être annulée.

Le chapitre [Dépannage](troubleshooting) explique les messages les plus courants et quoi faire.

## Les commandes courantes {#controls}

@index: commandes; formulaires; champs

Les mêmes quelques commandes servent dans chaque écran.

### Les champs de texte {#text-fields}

Tapez dans la case ; le libellé au-dessus indique ce qui y va. Les champs marqués « facultatif » dans leur libellé peuvent rester vides. Les champs de mot de passe affichent des points au lieu des caractères. Les champs de notes peuvent contenir plusieurs lignes.

### Les champs de date {#date-fields}

@index: format de date; AAAA-MM-JJ; date ISO

Les dates se tapent sous la forme année-mois-jour avec des tirets, comme 2026-03-05 pour le 5 mars 2026 : le format normalisé canadien, le même en français et en anglais. Le contour devient rouge tant que la date n'est pas valide.

- Tapez + à la fin d'une date valide pour l'avancer d'un jour, ou - pour la reculer d'un jour. Recommencez pour continuer.
- Plusieurs champs de date sont remplis au départ avec la date du jour.

### Les champs de montant et la calculatrice {#amount-fields}

@index: montant; calculatrice; virgule décimale; montant négatif

Les montants se tapent comme votre langue les écrit : 1 234,56 en français, 1,234.56 en anglais (en français, un point seul est aussi accepté comme séparateur décimal, puisque bien des pavés numériques n'ont qu'un point). Le signe de dollar, le code de la devise et les espaces sont ignorés. Un signe moins ou des parenthèses, comme (45,00), rendent le montant négatif.

Chaque champ de montant est aussi une calculatrice : tapez 12,50 + 3,25, 3 * 4,99 ou (100 - 20) / 4, et le résultat s'affiche sous le champ, comme = 15,75 $. Les signes × et ÷ fonctionnent aussi. Seul le résultat final est arrondi au cent. Un point d'interrogation sous le champ signifie qu'il ne peut pas être lu comme un montant.

### Les listes déroulantes {#pickers}

@index: liste déroulante; sélecteur; liste

Une liste déroulante présente le choix actuel avec une flèche. Cliquez dessus pour ouvrir sa liste, puis cliquez sur un choix. Vous pouvez aussi y taper : la liste ne garde que les choix qui contiennent ce que vous avez tapé, ce qui est le plus rapide dans les longues listes comme les catégories ou les comptes. Certaines listes ont un premier choix comme (aucun) ou Tout le monde, utilisé quand rien en particulier n'est choisi. Les longues listes présentent leurs 200 premières correspondances ; tapez plus de lettres pour les réduire.

### Les champs à suggestions {#suggestion-fields}

@index: champ Bénéficiaire; suggestions; saisie semi-automatique

Un champ à suggestions, comme Bénéficiaire dans un registre, accepte n'importe quel texte et propose, à mesure que vous tapez, les noms correspondants que vous utilisez déjà. Cliquez sur une suggestion pour la prendre, ou continuez à taper un nouveau nom : un bénéficiaire tapé qui n'existe pas encore est créé à l'enregistrement.

### Les cases à cocher {#checkboxes}

Une case cochée active une option. Le texte à côté indique ce que fait l'option.

### Les formulaires en fenêtre : Enregistrer et Annuler {#form-dialogs}

@index: Enregistrer; Annuler; boîte de dialogue; fenêtre de formulaire

Bien des formulaires s'ouvrent dans une fenêtre par-dessus l'écran, comme **Ajouter un compte** ou **Ajouter une facture**.

- **Enregistrer** : enregistre et ferme la fenêtre. Il est grisé tant qu'un élément obligatoire manque ou n'est pas valide. Si la modification est refusée, une fenêtre Impossible d'enregistrer en donne la raison et vos entrées restent dans le formulaire.
- **Annuler** : ferme la fenêtre sans rien enregistrer. Échap fait la même chose.

D'autres écrans présentent leur formulaire à côté d'une liste, avec leur propre bouton **Enregistrer** ; choisir un autre élément de la liste sans enregistrer abandonne ce qui avait été tapé.

### Les sélecteurs de fichier et de dossier {#file-choosers}

@index: sélecteur de fichier; choisir un dossier

Les boutons qui se terminent par … (comme **Importer un relevé…** ou **Choisir le dossier…**) ouvrent le sélecteur de votre système. La sorte de fichier attendue figure dans sa liste des types de fichier, comme Relevés bancaires (OFX, QFX, QBO, CSV). Annulez dans le sélecteur pour ne rien changer.

## Les rappels et les notifications {#reminders}

@index: rappels; notifications; zone de notification; alertes

L'application vous rappelle ce qui arrive à échéance, tant qu'un ménage est ouvert.

### Ce qui fait l'objet d'un rappel {#what-is-reminded}

- Les factures, la paie et les virements, aux jours choisis dans **Me le rappeler (jours avant)**, le jour de l'échéance et en cas de retard ; pour un abonnement, la date limite pour l'annuler avant son renouvellement. Voir [Factures](bills).
- Les événements et les rendez-vous du calendrier, aux heures choisies pour eux. Voir [Calendrier](calendar).
- Les renouvellements d'ordonnances, et les ordonnances sans renouvellement restant. Voir [Santé](health).
- Les renouvellements : permis et assurance des animaux, immatriculation et assurance des véhicules, garanties, termes de prêt et d'hypothèque qui prennent fin, frais annuels de cartes, réclamations médicales à envoyer, polices d'assurance et acomptes provisionnels.
- L'entretien à faire sur les véhicules, la maison et les autres biens, selon la date ou selon les kilomètres ou les heures.
- Les avertissements des régimes enregistrés, comme les cotisations excédentaires, les retraits minimums des FERR et des FRV, et les REER à convertir avant la fin de l'année de vos 71 ans. Voir [Régimes enregistrés](plans).

### Le bandeau de rappels {#reminder-banner}

Le bandeau de couleur sous la barre du haut compte les rappels et présente les trois premiers. Il paraît dans chaque écran sauf celui auquel les rappels appartiennent (les rappels de factures ne sont pas répétés dans l'écran Factures, par exemple). Cliquez dessus pour ouvrir l'écran du premier rappel. Le bandeau suit la langue en usage.

### Les notifications de l'ordinateur et l'icône de la zone de notification {#tray}

@index: notification du système; icône de la zone de notification; barre des tâches

Pendant que l'application fonctionne, son icône se trouve dans la zone de notification de la barre des tâches ; en y passant la souris, on voit RANN's Roost. Quand un ménage est ouvert, l'application cherche de nouveaux rappels toutes les cinq minutes et affiche une notification de l'ordinateur qui indique leur nombre et en présente jusqu'à quatre. Chaque rappel est annoncé une fois tant que le ménage reste ouvert ; le bandeau continue de l'afficher jusqu'à ce qu'il soit réglé.

> Remarque : les notifications exigent que le ménage soit ouvert. Si l'application est fermée ou verrouillée, rien n'est annoncé ; les rappels paraissent à la prochaine ouverture.

### Le tableau de bord {#dashboard-attention}

La liste À vérifier du tableau de bord rassemble d'autres éléments à examiner : les factures en retard, les lignes de relevé qui demandent une décision, les opérations sans catégorie, les comptes non rapprochés depuis plus de 45 jours (un nombre modifiable dans [Taux et règles](rates-rules)) et l'absence de sauvegarde réussie dans les 7 derniers jours. Voir [Tableau de bord](dashboard).

## Le travail fait pendant que le ménage est ouvert {#background}

@index: tâches de fond; automatique

Certaines tâches se font d'elles-mêmes, seulement quand un ménage est ouvert :

- Les taux de change manquants de la Banque du Canada sont téléchargés quand le ménage utilise une autre monnaie, et les téléchargements de cours que vous avez activés se font. Voir [Taux et cours](rates).
- Les mises de côté prévues pour les objectifs d'épargne sont inscrites à leur date. Voir [Objectifs d'épargne](goals).
- Les téléphones jumelés peuvent envoyer leurs saisies par le Wi-Fi de la maison, et le dossier de transfert est consulté pour les saisies envoyées de l'extérieur. Voir [Téléphones](phones).
- Les fichiers enregistrés dans le dossier surveillé de Documents sont importés. Voir [Documents](documents).
- Les rapports planifiés sont produits quand leur période est terminée. Voir [Rapports](reports).
- Les sauvegardes automatiques se font selon leur horaire. Voir [Sauvegardes](backups).
- Sur les copies qui vérifient les mises à jour, la vérification se fait une fois par jour si vous l'avez acceptée.

Quand l'ordinateur n'est pas en ligne, ces tâches attendent simplement ; tout le reste fonctionne sans Internet.

## Les réglages gardés sur cet ordinateur {#computer-settings}

@index: réglages propres à l'ordinateur; préférences

La plupart des choix sont enregistrés dans le ménage et le suivent sur n'importe quel ordinateur. Quelques-uns appartiennent plutôt à l'ordinateur, de sorte que chaque ordinateur peut différer :

- la langue (English ou Français) ;
- les couleurs et la taille du texte, sous [Affichage et accessibilité](display) ;
- la durée avant le verrouillage automatique, sous [Sécurité](security) ;
- le menu à gauche ou en haut, et les groupes du menu laissés repliés, pour chaque utilisateur ;
- la liste des ménages récents ;
- la réponse à la question sur les mises à jour ;
- la clé de lecture par IA, gardée dans le magasin sécurisé de l'ordinateur, sous [Lecture par IA](ai).
