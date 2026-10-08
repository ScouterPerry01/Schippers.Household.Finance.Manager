# Raccourcis clavier et accessibilité

RANN's Roost peut s’utiliser au clavier, être lue par un lecteur d’écran et s’afficher en plus grand ou en couleurs sombres. Ce chapitre présente chaque raccourci que l’application ajoute aux raccourcis habituels, puis ce qui aide les personnes qui voient, lisent ou bougent différemment.

## Raccourcis partout {#anywhere}

@index: raccourcis; clavier; touches de raccourci

Ces raccourcis fonctionnent dans chaque écran :

- **F1** : ouvre le panneau d’aide au sujet de l’écran affiché. Appuyez de nouveau sur F1, ou sur Échap, pour le fermer. Il fonctionne aussi dans les écrans de départ, avant qu’un ménage soit ouvert.
- **Maj+F1** : ouvre le manuel au chapitre de l’écran affiché. Voir [La fenêtre du manuel](welcome#manual-window).
- **Ctrl+F** : place le curseur dans la case de recherche en haut de la fenêtre, prêt à taper. Il fonctionne quand un ménage est ouvert.

## Se déplacer au clavier {#keyboard-navigation}

@index: Tab; Maj+Tab; focus; navigation au clavier

Ce sont les touches habituelles de Windows et de Linux, et elles fonctionnent aussi dans l’application :

- **Tab** : passe au champ, au bouton ou à la case à cocher suivant.
- **Maj+Tab** : revient au précédent.
- **Entrée** ou **Espace** : appuie sur le bouton qui a le focus.
- **Échap** : ferme une fenêtre ouverte par-dessus l’écran.

## Dans la case de recherche {#search-box}

@index: raccourci de recherche

- **Entrée** : cherche ce que vous avez tapé. Tapez au moins deux caractères ; une recherche plus courte est ignorée.

Les résultats s’ouvrent dans une fenêtre ; **Fermer** la ferme. Voir [La recherche](basics#search).

## Dans le registre d’un compte {#register}

@index: raccourcis du registre; saisir une opération

Le formulaire sous les opérations, où vous entrez ou modifiez une opération, répond à deux touches, où que soit le curseur dans le formulaire :

- **Entrée** : enregistre l’opération, comme son bouton d’enregistrement.
- **Échap** : vide le formulaire sans enregistrer, pour recommencer.

Après un enregistrement, le formulaire est vidé pour l’opération suivante et garde la date : vous pouvez ainsi entrer toute une pile de reçus du même jour sans la souris, en passant d’un champ à l’autre avec Tab, puis Entrée. Voir [Comptes](accounts).

## Dans les champs de date {#date-fields}

@index: raccourci de date; touche plus; touche moins; jour suivant

- La touche plus, tapée à la fin d’une date valide, l’avance d’un jour (2026-03-05 + devient 2026-03-06).
- La touche moins, tapée à la fin d’une date valide, la recule d’un jour.

Tapez la touche plusieurs fois pour avancer ou reculer de plusieurs jours. Les dates se tapent au format AAAA-MM-JJ ; voir [Les champs de date](basics#date-fields).

## Dans les champs de montant {#amount-fields}

@index: calculatrice; calcul; raccourci de montant

Les champs de montant sont des calculatrices. Tapez un calcul et le résultat s’affiche sous le champ :

- Additionnez et soustrayez avec + et -, comme 12,50 + 3,25.
- Multipliez avec * ou ×, comme 3 * 4,99.
- Divisez avec / ou ÷, comme 120 / 4.
- Regroupez avec des parenthèses, comme (100 - 20) / 4.

Seul le résultat final est arrondi au cent. Voir [Les champs de montant et la calculatrice](basics#amount-fields).

## Dans les listes déroulantes {#pickers}

@index: filtrer une liste; taper pour chercher

Taper dans une liste déroulante ne garde que les choix qui contiennent ce que vous avez tapé. C’est la façon la plus rapide de choisir une catégorie, un compte ou un bénéficiaire dans une longue liste.

## Dans les formulaires en fenêtre {#dialogs}

@index: touche Échap; fermer une fenêtre

- **Échap** : ferme la fenêtre sans enregistrer, comme **Annuler**.
- Passez avec Tab jusqu’à **Enregistrer** et appuyez sur Entrée ou Espace pour enregistrer.

## Dans le panneau d’aide {#help-panel}

- Le curseur se place dans **Chercher dans le guide** : tapez pour chercher aussitôt.
- **Échap** ou **F1** : ferme le panneau.

## Dans la fenêtre du manuel {#manual-window}

@index: Alt+Gauche; Alt+Droite; raccourcis du manuel

- **Alt+Gauche** et **Alt+Droite** : reculer et avancer, comme **Retour** et **Avancer**.
- **Ctrl+F** : ouvre l’onglet **Recherche**, le curseur dans sa case.

Voir [La fenêtre du manuel](welcome#manual-window).

## Dans les pages d’un document {#document-pages}

@index: zoom; Page précédente; Page suivante; Ctrl+molette; raccourcis des documents

Dans l’aperçu d’un document, après avoir cliqué dessus :

- **Pg suiv** et **Pg préc** : la page suivante et la page précédente.
- **Ctrl+plus** et **Ctrl+moins** (aussi sur le pavé numérique) : agrandir et réduire. Ctrl et la molette de la souris font de même.
- **Ctrl+0** : ajuster de nouveau la page à la fenêtre.
- Faites glisser avec la souris pour vous déplacer dans une page plus grande que la fenêtre.

Voir [L’aperçu, les pages et le zoom](documents#preview).

## Accessibilité {#accessibility}

@index: accessibilité; basse vision; lecteur d’écran; mode sombre; grand texte

### Taille du texte et couleurs {#text-size-colours}

Sous [Affichage et accessibilité](display), dans le groupe Réglages :

- **Taille du texte** : de 90 % à 150 %. Chaque écran, et le manuel, grandit avec elle, et le menu de gauche s’élargit pour que les noms tiennent sur une ligne. Une ligne d’exemple montre la taille choisie.
- **Couleurs** : comme le système (clair ou sombre), toujours clair ou toujours sombre. Les graphiques suivent le même choix.

Chaque ordinateur garde son propre choix, de sorte qu’un grand écran et un portable peuvent différer.

### Les lecteurs d’écran {#screen-readers}

@index: Narrateur; NVDA; Orca

Les lecteurs d’écran, comme Narrateur ou NVDA sous Windows et Orca sous Linux, lisent les libellés des champs, des boutons et des cases à cocher. L’application leur indique aussi :

- si chaque groupe du menu de gauche est ouvert ou fermé ;
- que chaque groupe du menu du haut ouvre un menu ;
- si chaque étape du guide des premiers pas, au tableau de bord, est faite ou à faire ;
- ce que fait chaque petit bouton **✕** : Retirer (une ligne d’un formulaire) ou Supprimer (un élément). Le même mot s’affiche quand la souris s’y arrête.

### Moins de choses à retenir {#memory-aids}

- Le bandeau de rappels et les notifications de l’ordinateur disent en mots ce qui arrive à échéance : vous n’avez rien à retenir.
- Le guide des premiers pas du tableau de bord montre quoi faire ensuite, et sa liste À vérifier montre ce qui attend une décision.
- Chaque champ garde son libellé visible pendant que vous y tapez.

## Aide-mémoire {#quick-reference}

@index: liste des raccourcis; aide-mémoire

- F1 : l’aide sur cet écran.
- Maj+F1 : le manuel sur cet écran.
- Ctrl+F : la case de recherche.
- Entrée dans la case de recherche : chercher.
- Entrée dans le formulaire de saisie d’un registre : enregistrer l’opération.
- Échap dans le formulaire de saisie d’un registre : vider le formulaire.
- Échap dans une fenêtre : fermer sans enregistrer.
- Pg préc et Pg suiv dans un document : page précédente et suivante.
- Ctrl+plus, Ctrl+moins et Ctrl+0 dans un document : agrandir, réduire, ajuster la page.
- Alt+Gauche et Alt+Droite dans le manuel : reculer et avancer.
- + ou - après une date : un jour plus tard ou plus tôt.
- Tab et Maj+Tab : champ ou bouton suivant et précédent.
- Entrée ou Espace : appuyer sur le bouton qui a le focus.
