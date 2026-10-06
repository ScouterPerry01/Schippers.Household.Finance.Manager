# Objectifs d’épargne

Les objectifs d’épargne vous aident à mettre de l’argent de côté pour des projets précis, comme un voyage, une nouvelle auto, une toiture, les cadeaux des Fêtes ou un fonds d’urgence, dans un compte que vous avez déjà. Objectifs d’épargne se trouve dans le groupe Argent du menu.

## Le fonctionnement des objectifs d’épargne {#overview}
@index: objectif d’épargne; fonds d’amortissement; fonds d’urgence; enveloppe; réserver; épargner pour un achat

Un objectif réserve une partie du solde d’un compte. Rien ne change de compte et aucune opération n’est créée : l’argent reste dans votre compte d’épargne, et l’application note quelle part revient à chaque objectif. Ce qui n’est attribué à aucun objectif est « non attribué ».

Par exemple, un compte d’épargne à intérêt élevé contient 9 000 $. Il porte trois objectifs : Fonds d’urgence 5 000 $, Voyage 2 500 $ et Réparations de l’auto 600 $. Le compte affiche « Réservé aux objectifs : 8 100 $ · Non attribué : 900 $ ».

Vous inscrivez trois sortes de mouvements sur un objectif :

- **Mettre de côté** : une plus grande part du solde est réservée à l’objectif, à la main ou automatiquement selon un calendrier.
- **Utiliser** : l’objectif a payé ce pour quoi il était prévu, ou de l’argent a été repris pour autre chose.
- **Transférer** : une réserve passe d’un objectif à un autre objectif du même compte.

Les dépôts et achats réels s’inscrivent dans le compte comme d’habitude, à partir de son registre ou d’un relevé.

## L’écran Objectifs d’épargne {#goals-screen}

En haut, **Ajouter un objectif** ouvre le formulaire d’objectif, et une courte note explique le fonctionnement des objectifs. « Aucun objectif pour l’instant. Ajoutez-en un pour commencer à mettre de l’argent de côté. » signifie qu’il n’y en a aucun.

### Les cartes de compte {#account-cards}

Les objectifs sont regroupés par compte, une carte par compte qui a des objectifs. Chaque carte montre :

- le nom du compte et « Solde … » : son solde actuel dans les livres ;
- « Réservé aux objectifs : … · Non attribué : … » : le total réservé à ses objectifs, et le reste ;
- en rouge, quand les objectifs dépassent le solde : « Les objectifs dépassent le solde de … Reprenez une partie d’un objectif ou déposez davantage. »

Cet avertissement signifie que de l’argent a été dépensé dans le compte sans être retiré d’un objectif. Utilisez **Utiliser** sur un objectif pour l’inscrire, ou déposez davantage.

### Les lignes d’objectif {#goal-lines}

Chaque objectif du compte a une ligne avec :

- son nom, suivi de « (Atteint) » quand son état est Atteint ;
- « … sur … (n %) · il manque … » : ce qui est mis de côté, le montant visé, le pourcentage et ce qu’il reste à mettre de côté (absent une fois l’objectif atteint) ;
- une barre de progression ;
- le plan, voir [Progression et respect des délais](goals#progress) ;
- les boutons :
  - **Mettre de côté** : réserve davantage pour l’objectif. Voir [Mettre de côté](goals#set-aside).
  - **Utiliser** : inscrit que de l’argent a quitté l’objectif. Voir [Utiliser ou reprendre](goals#use-or-take-back).
  - **Transférer** : déplace un montant vers un autre objectif du même compte. Voir [Transférer vers un autre objectif](goals#move).
  - **Historique** : énumère chaque mouvement de l’objectif. Voir [Historique](goals#history).
  - **Modifier** : ouvre le formulaire de l’objectif.

### Progression et respect des délais {#progress}
@index: dans les temps; en retard; date prévue

Sous la barre, une ligne résume le plan :

- « Plan : …, … » avec le montant et le calendrier, par exemple « Plan : 250,00 $, chaque mois ». « (à la main) » est ajouté quand les montants ne sont pas inscrits automatiquement.
- « Aucun plan ; mettez de l’argent de côté quand vous le pouvez » quand l’objectif n’a pas de calendrier.
- « date cible … » quand l’objectif a une date cible.
- « atteint vers le … » : quand les montants prévus atteindraient le montant visé.
- « il faut environ … par mois » : pour un objectif qui a une date cible mais pas de plan, le montant mensuel qui permettrait d’atteindre l’objectif à temps.
- « Objectif atteint » dès que le montant mis de côté égale ou dépasse le montant visé.

En dessous, quand l’objectif a à la fois un plan et une date cible :

- « Dans les temps » : les montants prévus atteignent l’objectif d’ici la date cible.
- « En retard : mettez de côté … (mensuellement) pour atteindre l’objectif à temps » : ils ne l’atteignent pas ; le montant est ce que chaque montant prévu devrait être à partir de maintenant.

## Ajouter ou modifier un objectif {#goal-form}

**Ajouter un objectif** ouvre le formulaire intitulé « Ajouter un objectif » ; **Modifier** l’ouvre sous le titre « Modifier l’objectif ». **Enregistrer** est offert dès que l’objectif a un nom et un compte. **Annuler** ferme le formulaire sans enregistrer.

- **Pour quoi épargnez-vous?** : le nom de l’objectif, comme « Voyage en Gaspésie » ou « Fonds d’urgence ». Obligatoire.
- **Gardé dans le compte** : le compte qui contient l’argent. Les comptes chèques, d’épargne, d’épargne à intérêt élevé, d’argent comptant, de placement et semblables sont offerts ; les cartes de crédit, marges de crédit, marges hypothécaires, prêts et hypothèques ne le sont pas. Par défaut, votre premier compte d’épargne. Il ne peut être choisi qu’à la création de l’objectif. Les montants de l’objectif sont dans la devise de ce compte.
- **Montant visé** : combien vous voulez mettre de côté au total. Obligatoire, supérieur à zéro.
- **Date cible (facultative)** : quand vous voulez l’atteindre, au format AAAA-MM-JJ. Avec une date, l’application vous dit si vous êtes dans les temps et combien il faut. Laissez vide pour un objectif sans échéance.
- **Mettre un montant de côté selon un calendrier** : cochez-la pour prévoir des montants réguliers. Cochée par défaut pour un nouvel objectif. Voir [Montants prévus](goals#planned-set-asides).
- **État** : affiché en modification. Voir [L’état](goals#status).
- **Notes** : vos propres notes sur l’objectif.
- **Supprimer** : affiché en modification. Voir [Supprimer un objectif](goals#delete-goal).

### Montants prévus {#planned-set-asides}
@index: épargne automatique; cotisation prévue; se payer en premier

Avec **Mettre un montant de côté selon un calendrier** cochée :

- **Montant chaque fois** : le montant réservé à chaque date. Obligatoire, supérieur à zéro.
- **Répétition** : Chaque semaine, Aux deux semaines, Deux fois par mois, Chaque mois, Chaque trimestre, Deux fois par année ou Chaque année. Faites-la correspondre à votre paie ou au virement que vous faites vers l’épargne. Par défaut : Chaque mois.
- **Première date** : la première date du calendrier, au format AAAA-MM-JJ. Par défaut : aujourd’hui.
- **Deuxième jour** : affiché pour Deux fois par mois. Le deuxième jour du mois, de 1 à 31, ou 0 pour le dernier jour. Le premier jour est celui de la première date.
- **Inscrire automatiquement les montants à leur date** : quand elle est cochée (par défaut), chaque montant prévu est inscrit à sa date tant que l’application est ouverte, vérifié à l’ouverture du ménage puis toutes les heures. Les jours où l’application était fermée sont rattrapés à la prochaine ouverture. Quand elle est décochée, rien n’est inscrit tout seul : le plan ne sert qu’à la prévision, affiché « (à la main) », et vous cliquez vous-même sur **Mettre de côté**.

Les montants automatiques :

- ne sont inscrits que pour les objectifs dont l’état est Actif ;
- s’arrêtent dès que le montant visé est atteint ; le dernier n’est que ce qui manque pour l’atteindre ;
- apparaissent comme « Montant prévu » dans l’historique.

Si vous changez le calendrier ou sa première date, les montants sont inscrits de nouveau à partir de la nouvelle première date. Ceux déjà inscrits restent ; si la nouvelle première date est passée, supprimez les doubles dans l’historique.

> Conseil : Donnez au plan le même montant et les mêmes dates que le virement automatique que vous faites du compte chèques vers l’épargne. L’objectif grandit alors au rythme où l’argent arrive.

### L’état {#status}

- **Actif** : l’état habituel. Les montants prévus sont inscrits.
- **Atteint** : vous considérez l’objectif comme réalisé. « (Atteint) » est affiché après son nom et plus aucun montant prévu n’est inscrit.
- **Archivé** : l’objectif est caché de l’écran et son argent ne compte plus comme réservé ; le montant « Non attribué » du compte augmente d’autant. Son historique est conservé.

## Mettre de côté {#set-aside}

**Mettre de côté** ouvre un formulaire intitulé « Mettre de côté · (objectif) ». Il « Réserve une partie du solde du compte pour cet objectif. Aucun argent ne bouge. »

- **Date** : la date du montant mis de côté, au format AAAA-MM-JJ. Par défaut : aujourd’hui.
- **Montant** : combien réserver. Par défaut : le montant prévu chaque fois, s’il y en a un. Obligatoire, supérieur à zéro.
- **Note** : une note facultative, affichée dans l’historique.

## Utiliser ou reprendre {#use-or-take-back}
@index: dépenser un objectif; retirer d’un objectif

**Utiliser** ouvre un formulaire pour inscrire que de l’argent a quitté l’objectif.

- **Raison** :
  - **Dépensé pour ce qui était prévu** : l’objectif a payé ce pour quoi il était prévu (« L’objectif a payé ce pour quoi il était prévu. Inscrivez l’achat dans le compte comme d’habitude. »). Inscrit comme « Utilisé ».
  - **Repris pour autre chose** : l’argent retourne dans la partie non attribuée du solde du compte (« Remet l’argent dans la partie non attribuée du solde. »). Inscrit comme « Repris ». Utilisez-le pour corriger un montant mis de côté ou libérer de l’argent pour un autre besoin.
- **Achat (facultatif)** : affiché pour **Dépensé pour ce qui était prévu** quand le compte a des paiements dans les 120 derniers jours. Choisissez l’achat que l’objectif a payé pour l’y lier ; la date, le montant et (s’il est vide) la note en sont tirés, et peuvent encore être changés. Laissez « (non lié à un achat) » si l’achat n’est pas encore inscrit ou a été payé d’un autre compte.
- **Date** : par défaut, aujourd’hui.
- **Montant** : combien quitte l’objectif. Obligatoire, supérieur à zéro.
- **Note** : une note facultative.

Les deux raisons réduisent ce que l’objectif a mis de côté. Aucune ne crée d’opération : inscrivez l’achat ou le retrait dans le compte comme d’habitude, pour que le solde et les objectifs restent d’accord.

## Transférer vers un autre objectif {#move}

**Transférer** déplace une réserve de cet objectif vers un autre objectif du même compte, par exemple de Voyage vers Fonds d’urgence.

- **Transférer vers** : l’autre objectif. Seuls les objectifs du même compte sont offerts. « C’est le seul objectif de ce compte. » signifie qu’il n’y en a aucun, et le formulaire ne peut pas être enregistré.
- **Montant** : combien transférer. Obligatoire, supérieur à zéro.

Le transfert est daté d’aujourd’hui et apparaît dans les deux historiques, comme « Transféré à un autre objectif » et « Reçu d’un autre objectif ».

## Historique {#history}

**Historique** ouvre « Historique de (objectif) », chaque mouvement du plus récent au plus ancien, avec sa date, sa sorte (Mis de côté, Montant prévu, Utilisé, Reçu d’un autre objectif, Transféré à un autre objectif, Repris), sa note et son montant. Les montants qui ont quitté l’objectif sont négatifs, en rouge.

- **Supprimer** sur une ligne retire ce mouvement, par exemple un montant inscrit deux fois. Une question est d’abord posée : « Supprimer cette entrée : (type), (montant) le (date)? Le solde de l’objectif change de ce montant; rien ne bouge dans le compte. Impossible d’annuler. » Supprimer un côté d’un transfert ne supprime pas l’autre côté.

**Fermer** ferme l’historique.

## Supprimer un objectif {#delete-goal}

**Supprimer** dans le formulaire de l’objectif demande « Supprimer « … » et son historique? L’argent reste dans le compte. » Après confirmation, l’objectif et tous ses mouvements sont supprimés. Aucune opération ne change et le solde du compte reste le même ; le montant qu’il réservait devient « non attribué ». Cette action ne peut pas être annulée. Pour garder l’historique, mettez plutôt **État** à Archivé.

## Qui peut voir et modifier les objectifs {#permissions}

Un objectif appartient au groupe de comptes de son compte : il est visible par ceux qui peuvent ouvrir ce groupe, et le modifier, y mettre de l’argent de côté ou le supprimer demande la permission **Modification** sur ce groupe. Voir [Utilisateurs](users).
