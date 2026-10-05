# Sauvegardes

Les sauvegardes sont des copies de tout le ménage, faites automatiquement, vérifiées et gardées dans un dossier de votre choix, pour vous remettre d'un disque brisé, d'un ordinateur perdu ou volé, ou d'une erreur, et pour déplacer le ménage vers un nouvel ordinateur. L'écran se trouve dans le groupe **Réglages** du menu, sous **Sauvegardes**. Il contient aussi **Exporter toutes les données**.

## Ce qu'est une sauvegarde {#about-backups}

@index: sauvegarde; copie de sécurité; hfmbak; point de restauration; reprise après sinistre

- Une sauvegarde est un fichier qui se termine par .hfmbak, nommé d'après le ménage avec la date et l'heure, comme Schippers-20261005-143012.hfmbak.
- Elle contient tout : chaque groupe de comptes, chaque document, les clés de chaque utilisateur. Rien n'est laissé de côté selon la personne qui l'a faite.
- Elle reste chiffrée. L'ouvrir exige le mot de passe ou la clé de récupération d'un utilisateur, exactement comme le ménage lui-même, de sorte qu'une sauvegarde dans un dossier infonuagique ne révèle pas vos finances.
- Chaque sauvegarde est vérifiée dès qu'elle est faite : chaque fichier par rapport à sa somme de contrôle, qu'aucune base de données n'est gardée sans chiffrement, et que les bases de données que vous pouvez ouvrir s'ouvrent correctement. Seule une sauvegarde qui réussit cette vérification compte comme une réussite.
- Les anciennes sauvegardes ne sont supprimées qu'après qu'une nouvelle a réussi sa vérification, de sorte que vous n'avez jamais moins de bonnes copies.

> Important : Les sauvegardes d'un ménage s'ouvrent avec les mêmes mots de passe et les mêmes clés de récupération que le ménage. Gardez votre clé de récupération (voir [Sécurité](security#recovery-key)) : sans mot de passe ni clé de récupération, personne, pas même RANN, ne peut ouvrir une sauvegarde.

## Des sauvegardes automatiques dès le départ {#defaults}

Un nouveau ménage est sauvegardé chaque jour dès le départ, dans un dossier à côté de lui nommé d'après le ménage suivi de « - backups », en gardant 10 versions. Cela protège contre les erreurs, mais pas contre la perte du disque : choisissez un dossier sur un autre disque dès que possible.

## Réglages de sauvegarde {#settings}

@index: dossier de sauvegarde; calendrier de sauvegarde; disque externe; dossier infonuagique; OneDrive; Google Drive; Dropbox

En haut de l'écran, le dossier utilisé, ou « Aucun dossier de sauvegarde choisi. »

- **Choisir le dossier…** : ouvre un sélecteur de dossier. Le dossier choisi est enregistré tout de suite. Pour une vraie protection, choisissez un disque externe, un lecteur réseau ou un dossier infonuagique (OneDrive, Google Drive, Dropbox), et non le même disque que le ménage.
- **Sauvegardes automatiques** : Désactivées, Chaque jour ou Chaque semaine. Enregistré dès que vous choisissez.
  - Chaque jour : une sauvegarde est faite quand la dernière sauvegarde réussie a au moins 20 heures.
  - Chaque semaine : quand la dernière sauvegarde réussie a au moins 7 jours.
  - Désactivées : aucune sauvegarde automatique. **Sauvegarder maintenant** fonctionne toujours.
- **Versions à conserver** : combien de sauvegardes de ce ménage garder dans le dossier, de 1 à 365. La valeur par défaut est 10. Une fois qu'une nouvelle sauvegarde a réussi sa vérification, les plus anciennes au-delà de ce nombre sont supprimées. Les autres fichiers du dossier ne sont jamais touchés.
- **Enregistrer** : enregistre **Versions à conserver**. Un nombre hors de 1 à 365 est refusé (« Conservez de 1 à 365 versions. »).

Le calendrier ne fonctionne que pendant que le ménage est ouvert et déverrouillé : l'application vérifie toutes les 30 minutes si une sauvegarde est due. Si l'ordinateur était éteint, la sauvegarde est faite peu après la prochaine ouverture du ménage.

Ces réglages appartiennent au ménage, pas à l'ordinateur, et les changements sont inscrits dans le journal d'activité.

> Conseil : Un dossier infonuagique synchronisé par OneDrive, Google Drive ou Dropbox est une copie hors site facile. Les sauvegardes y restent chiffrées.

## Sauvegarder maintenant {#back-up-now}

- **Sauvegarder maintenant** : fait une sauvegarde immédiatement, la vérifie, puis supprime les versions au-delà du nombre à conserver. Disponible une fois un dossier choisi. Pendant qu'elle s'exécute, le bouton indique « Sauvegarde en cours… ». L'écriture dans le ménage fait une pause le temps de copier les fichiers, habituellement une fraction de seconde.

Le résultat s'affiche ensuite :

- « Sauvegarde enregistrée et vérifiée : » avec le nom du fichier, ou « La vérification de la sauvegarde a échoué : » avec les problèmes trouvés.
- « Dernière sauvegarde : » avec la date et l'heure de la dernière sauvegarde réussie, ou « Aucune sauvegarde pour l'instant. » Elle devient rouge quand aucune sauvegarde n'a réussi depuis 7 jours.
- « Dernier problème : » en rouge, quand la dernière tentative a échoué, avec la raison (par exemple un dossier inaccessible, comme un disque débranché).

Si aucune sauvegarde n'a réussi dans les 7 derniers jours, la liste à revoir du tableau de bord indique aussi « Aucune sauvegarde réussie dans les 7 derniers jours ». Voir [Tableau de bord](dashboard).

## Sauvegardes dans le dossier {#backup-list}

« Sauvegardes dans le dossier » avec leur nombre liste les sauvegardes de ce ménage trouvées dans le dossier, les plus récentes en premier. Les sauvegardes d'autres ménages dans le même dossier ne sont pas listées. Chaque ligne montre la date et l'heure, le nom du fichier et sa taille en Ko.

- **Vérifier** : teste de nouveau cette sauvegarde, comme lorsqu'elle a été faite : chaque fichier par rapport à sa somme de contrôle, aucune base de données non chiffrée, et les bases de données que vous pouvez ouvrir ouvertes et vérifiées. Le résultat s'affiche sous la ligne : « La sauvegarde est complète et lisible » avec le nombre de bases de données vérifiées, ou « La vérification de la sauvegarde a échoué : » et les problèmes.

> Conseil : Vérifiez de temps en temps une sauvegarde plus ancienne, surtout sur un disque que vous branchez rarement.

## Restaurer une sauvegarde {#restore}

@index: restaurer; récupérer; nouvel ordinateur; déplacer le ménage; transférer vers un autre ordinateur

Une sauvegarde se restaure à partir de l'écran de départ, jamais par-dessus un ménage ouvert. Les mêmes étapes déplacent le ménage vers un nouvel ordinateur.

1. Verrouillez le ménage avec **Verrouiller** en haut de la fenêtre (ou fermez l'application et redémarrez-la).
2. À l'écran de départ, cliquez sur **Restaurer une sauvegarde…**.
3. Choisissez le fichier de sauvegarde. Le sélecteur montre « Sauvegardes de ménage (.hfmbak) ».
4. Choisissez le dossier où placer le ménage restauré (« Choisissez où placer le ménage restauré »).
5. L'application vérifie chaque fichier par rapport à sa somme de contrôle et restaure le ménage dans un nouveau dossier à l'intérieur de celui que vous avez choisi, nommé d'après la sauvegarde, comme Schippers.hfm (ou Schippers (2).hfm si ce nom est pris). Un ménage existant n'est jamais écrasé.
6. L'écran de déverrouillage s'ouvre. Connectez-vous avec votre nom d'utilisateur et votre mot de passe, ou utilisez **Mot de passe oublié?** avec votre clé de récupération.

Si la sauvegarde est endommagée, la restauration s'arrête et dit pourquoi ; rien n'est restauré.

Après une restauration :

- Choisissez de nouveau le dossier et le calendrier de sauvegarde si la copie restaurée est sur un nouvel ordinateur.
- Les réglages gardés sur chaque ordinateur, comme le délai de verrouillage automatique, les couleurs et la taille du texte, ne sont pas dans la sauvegarde. Voir [Affichage et accessibilité](display) et [Sécurité](security).
- Les clés de lecture par IA sont gardées dans le magasin de secrets de l'ordinateur, pas dans le ménage : enregistrez de nouveau votre clé sous [Lecture par IA](ai).

> Important : Restaurer crée une deuxième copie du ménage. Une fois que vous êtes certain que la copie restaurée est celle à utiliser, cessez d'utiliser l'ancienne, pour que les changements ne se retrouvent pas répartis entre deux copies.

## Exporter toutes les données {#export}

@index: exporter; exportation; CSV; JSON; format ouvert; portabilité des données; quitter l'application

Exporter toutes les données enregistre tout ce que vous pouvez voir dans des formats ouverts, pour que vos données puissent toujours être utilisées ailleurs, même sans RANN's Roost.

1. Cliquez sur **Exporter toutes les données…**.
2. Lisez l'avertissement : l'exportation N'EST PAS chiffrée. Toute personne ayant le fichier peut lire vos comptes, vos opérations, vos numéros de compte et vos documents.
3. Cliquez sur **Continuer**, ou sur **Annuler** pour arrêter.
4. Choisissez où enregistrer le fichier. Le nom proposé est household-export.zip.
5. L'écran indique « Exporté dans » et l'endroit.

Le fichier ZIP contient :

- Un fichier README.txt qui explique le contenu (en anglais).
- Un fichier CSV par table (UTF-8, séparé par des virgules) et un fichier JSON par base de données, pour chaque groupe de comptes que vous pouvez ouvrir.
- Un dossier documents avec le fichier original de chaque document.

Les montants sont en cents dans les colonnes qui se terminent par _minor (en satoshis pour les cryptoactifs), avec la devise dans sa propre colonne. Les dates sont au format AAAA-MM-JJ. Seul ce que vous pouvez ouvrir est exporté : un groupe privé auquel vous n'avez pas accès est laissé de côté. L'exportation est inscrite dans le journal d'activité, sans le nom du fichier.

> Important : Une exportation n'est pas une sauvegarde : elle ne peut pas être restaurée dans RANN's Roost, et elle n'est pas chiffrée. Gardez-la en lieu sûr et supprimez-la quand vous n'en avez plus besoin.
