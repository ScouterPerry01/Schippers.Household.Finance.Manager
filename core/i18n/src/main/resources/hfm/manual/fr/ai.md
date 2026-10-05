# Lecture par IA

La lecture par IA permet à Claude, l'IA d'Anthropic, de lire les documents difficiles à lire sur cet ordinateur, comme un reçu froissé, un long relevé de carte de crédit ou un talon de paie, avec votre propre compte et votre propre clé Anthropic. Elle est désactivée tant que vous ne l'activez pas. L'écran se trouve dans le groupe **Réglages** du menu, sous **Lecture par IA**.

## Comment fonctionne la lecture par IA {#how-it-works}

@index: intelligence artificielle; IA; Claude; Anthropic; lecture infonuagique; ROC; lire avec l'IA; clé d'API

Les documents que vous ajoutez sont d'abord lus sur cet ordinateur, sans Internet (voir [Documents](documents)). Cette lecture convient aux reçus clairs, mais peut manquer des champs sur les documents difficiles. La lecture par IA est une deuxième façon, facultative :

1. Vous activez la lecture par IA et vous enregistrez votre propre clé Anthropic dans cet écran.
2. Dans la révision d'un document, vous cliquez sur **Lire avec l'IA**. La révision indique « Certains champs sont incertains : l'IA peut lire ce document. » quand la lecture faite par l'ordinateur est incertaine.
3. À moins que vous l'ayez désactivé, l'application vous montre d'abord chaque page qui sera envoyée, pour que vous puissiez en masquer des parties (un numéro de compte, par exemple) ou ne garder qu'une partie d'une page. Rien n'est envoyé avant que vous cliquiez sur **Envoyer**.
4. Claude lit les pages et répond avec les champs de ce type de document. L'application vérifie la réponse sur cet ordinateur : les champs attendus et les sommes (les articles par rapport au sous-total, les taxes par rapport au total, les opérations d'un relevé par rapport à ses soldes, la paie brute moins les retenues par rapport à la paie nette). Si la vérification échoue, elle demande une seconde fois en disant ce qui n'allait pas ; si elle échoue encore, vous entrez les champs à la main.
5. Les champs lus par l'IA sont marqués « lu par l'IA » dans la révision, et le document indique quel modèle l'a lu et quand. Un relevé lu par l'IA peut aller directement dans un compte pour la conciliation, les articles d'un reçu peuvent devenir une opération ventilée, et un talon de paie peut devenir l'opération de paie. Voir [Documents](documents).

Ce qui quitte l'ordinateur : seulement les images des pages que vous avez approuvées, après votre rognage et vos zones masquées, d'au plus 2 000 pixels sur le côté long. Les zones masquées sont remplacées par des blocs unis avant que l'image quitte cet ordinateur. Aucun texte, aucune donnée de compte ni aucune autre donnée du ménage n'est envoyé. Anthropic reçoit les pages pour les lire.

Ce que ça coûte : chaque lecture coûte quelques cents à votre propre compte Anthropic. RANN ne reçoit rien et ne facture rien. L'application montre une estimation avant l'envoi et garde un journal de chaque demande avec son coût estimé.

> Important : La lecture par IA utilise votre propre compte Anthropic, facturé par Anthropic. Lisez les conditions d'Anthropic, et voyez la politique de confidentialité sous [À propos](about).

## Activer la lecture par IA {#turn-on}

1. Créez une clé d'API sur console.anthropic.com, sous API keys, dans votre propre compte Anthropic, et ajoutez-y du crédit.
2. Dans cet écran, collez la clé dans **Clé d'API** et cliquez sur **Enregistrer la clé**.
3. Cliquez sur **Vérifier la clé (gratuit)**. L'application indique « La clé fonctionne. » ou pourquoi elle ne fonctionne pas.
4. Cochez **Lire les documents difficiles avec l'IA**.

### Réglages {#settings}

Ces réglages sont les vôtres : chaque utilisateur qui se connecte choisit pour lui-même, et ils suivent le ménage sur n'importe quel ordinateur.

- **Lire les documents difficiles avec l'IA** : désactivé par défaut. Une fois activé, la révision d'un document offre **Lire avec l'IA** (ou, si vous n'avez pas encore de clé, « Pour lire avec l'IA, ajoutez votre clé sous Lecture par IA. »). Désactivé, rien n'est jamais envoyé, même avec une clé enregistrée. Les documents texte ne sont jamais offerts.
- **Me montrer chaque document et me laisser en masquer des parties avant l'envoi** : activé par défaut. Activé, un clic sur **Lire avec l'IA** ouvre l'aperçu, où vous pouvez masquer des zones et rogner les pages avant de cliquer sur **Envoyer**. Désactivé, chaque page est envoyée telle quelle dès que vous cliquez sur **Lire avec l'IA**.
- **Modèle** : le modèle Claude qui lit. Chacun est affiché avec son prix courant, en dollars américains par million de jetons lus et par million écrits. Claude Opus 5.5 (par défaut) est le plus performant ; Claude Sonnet 5.5 et Claude Haiku 4.5 coûtent moins cher. Un jeton est un petit morceau de texte ou d'image ; un reçu d'une page en utilise quelques milliers.

> Conseil : Gardez **Me montrer chaque document et me laisser en masquer des parties avant l'envoi** activé. C'est la seule occasion de masquer un numéro de compte ou de carte complet avant que la page quitte l'ordinateur.

## Votre clé Anthropic {#key}

@index: clé Anthropic; clé d'API; identifiant; Gestionnaire d'identification de Windows; trousseau

La clé est le mot de passe de votre compte Anthropic pour les programmes. Elle est gardée dans le magasin de secrets de l'ordinateur, pas dans le ménage :

- Sous Windows, dans le Gestionnaire d'identification de Windows.
- Sous Linux, dans le trousseau de votre bureau.
- Sur un bureau Linux sans trousseau actif, seulement en mémoire, jusqu'à la fermeture de l'application. L'écran indique alors « Aucun trousseau n'est actif sur cet ordinateur : la clé n'est gardée que jusqu'à la fermeture de l'application. »

La clé est gardée par ménage et par utilisateur. Elle n'est jamais écrite dans les fichiers ni dans les sauvegardes du ménage, de sorte qu'elle n'est pas sur un autre ordinateur, ni dans un ménage restauré, tant que vous ne l'y enregistrez pas aussi. Chaque utilisateur qui veut la lecture par IA apporte sa propre clé.

La ligne sous **Votre clé Anthropic** indique « Une clé est enregistrée dans » et l'endroit, ou « Aucune clé enregistrée pour l'instant. »

- **Clé d'API** : collez la clé ici. Le texte est masqué pendant que vous tapez. Les espaces autour sont retirés.
- **Enregistrer la clé** : enregistre la clé dans le magasin de secrets et vide le champ. L'écran indique « Clé enregistrée. », ou « La clé n'a pas pu être enregistrée : » avec la raison.
- **Vérifier la clé (gratuit)** : envoie une demande qui ne coûte rien pour voir si Anthropic accepte la clé. Disponible une fois une clé enregistrée. La réponse est « La clé fonctionne. » ou la raison de l'échec (voir [Quand une lecture échoue](ai#failures)).
- **Retirer la clé** : supprime tout de suite la clé du magasin de secrets de cet ordinateur. « Clé retirée de cet ordinateur. » La lecture par IA cesse alors de fonctionner pour vous sur cet ordinateur jusqu'à ce que vous enregistriez de nouveau une clé. Pour annuler la clé elle-même, supprimez-la sur console.anthropic.com.

## Types de documents {#document-types}

@index: schéma; type de document; type de document personnalisé

La partie **Types de documents** liste ce que l'IA peut lire, chacun avec sa version : Reçu, Facture, Facture détaillée, Relevé de carte de crédit, Relevé bancaire, Relevé de placements, Talon de paie et Relevé de prestations (les noms suivent les sortes de documents de l'écran [Documents](documents)). La version est inscrite avec chaque lecture.

Pour les utilisateurs avancés :

- La ligne « Vos propres types vont dans » donne un dossier sur cet ordinateur : sous Windows, le dossier ai-types sous RANN's Roost dans votre dossier AppData\Roaming ; sous Linux, ~/.config/ranns-roost/ai-types.
- **Ouvrir le dossier** : crée le dossier au besoin et l'ouvre.
- Un type, ce sont deux fichiers de même nom : un schéma JSON (nom.json) qui dit exactement quels champs retourner, et une instruction facultative (nom.txt). Un fichier qui porte le même nom qu'un type fourni le remplace.
- Les types que vous avez ajoutés affichent « ajouté » après leur version. Un fichier qui ne peut pas être utilisé est listé en rouge comme « Non utilisé : » avec son nom et la raison, comme un schéma qui n'est pas un objet JSON fermé.

## Utilisation {#usage}

@index: coût; jetons; journal d'utilisation; facture Anthropic

La partie **Utilisation** liste vos propres demandes, les plus récentes en premier. Chaque utilisateur ne voit que les siennes, puisque chacun paie pour les siennes.

- **Période** : Ce mois-ci, Cette année ou Tout.
- Le total de la période : le nombre de demandes et, « environ », leur coût estimé en dollars américains.

Chaque ligne montre :

- La date et l'heure de la demande.
- Le document, ou « (document supprimé) » s'il a été supprimé depuis.
- Le type de document.
- Le modèle qui a répondu. Avec Claude Opus 5.5, Anthropic peut faire répondre un autre modèle à une demande qu'il a déclinée ; le journal nomme le modèle qui a vraiment répondu.
- Les jetons lus et écrits.
- Le coût estimé, d'après le prix courant.
- « acceptée » si la réponse a passé les vérifications, « refusée » sinon. Une réponse refusée est quand même facturée par Anthropic.

Le coût est une estimation d'après les prix courants ; c'est votre facture Anthropic qui compte. L'écran montre jusqu'à 200 lignes.

## Quand une lecture échoue {#failures}

@index: erreur IA; clé refusée; plus de crédit

La révision du document dit ce qui n'a pas fonctionné :

- « La clé a été refusée. Vérifiez-la sous Lecture par IA. » : la clé est erronée, expirée ou supprimée. Enregistrez une nouvelle clé.
- « Anthropic a refusé pour l'instant : trop de demandes, ou plus de crédit dans votre compte. » : attendez, ou ajoutez du crédit sur console.anthropic.com.
- « Impossible de joindre Anthropic. Vérifiez la connexion Internet. »
- « Claude a refusé de lire ce document. »
- « La réponse n'a pas pu être vérifiée, même après une seconde demande ; entrez les champs à la main. »
- « Le document est trop long pour être lu d'un coup ; envoyez moins de pages. » : rognez ou laissez de côté des pages dans l'aperçu.
- « Anthropic a renvoyé une erreur. », avec le message du service.

Une lecture qui échoue ne change rien dans vos livres.

## Confidentialité {#privacy}

- Rien n'est envoyé à moins que la lecture par IA soit activée, qu'une clé soit enregistrée et que vous cliquiez sur **Lire avec l'IA** pour un document.
- Seules des images de pages sont envoyées, après votre rognage et vos zones masquées.
- Les documents d'un groupe de comptes privé restent privés : la lecture et son journal sont gardés dans ce groupe.
- Le journal d'activité partagé du ménage inscrit qu'un document a été lu par l'IA et avec quel modèle, jamais ce qu'il contient.

Voir [Confidentialité et vos données](privacy-data).
