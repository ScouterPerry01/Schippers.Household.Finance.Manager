# Argent en famille

L’écran Argent en famille suit l’argent qui circule entre des personnes plutôt qu’entre vos comptes : les dépenses partagées en voyage ou entre colocataires, l’argent prêté dans la famille et les allocations des enfants. Il se trouve dans le groupe **Argent** du menu, sous **Argent en famille**.

Rien sur cet écran ne crée d’opérations dans vos comptes ni ne change vos soldes, vos budgets ou vos rapports. C’est un carnet à part de qui doit quoi à qui. Quand l’argent change vraiment de mains (vous faites un virement Interac à un ami, vous remettez de l’argent comptant à votre enfant), inscrivez aussi ce paiement dans le registre du compte si vous voulez qu’il figure dans vos livres.

@index: argent entre personnes; reconnaissance de dette; qui doit quoi

## L’écran Argent en famille {#screen}

L’écran présente une courte explication en haut et trois onglets :

- **Dépenses partagées** : des groupes de personnes qui partagent des frais, avec le solde de chacun et les paiements qui mettent tout le monde quitte.
- **Prêts en famille** : l’argent prêté entre membres de la famille ou amis, avec un intérêt simple facultatif et le registre des remboursements.
- **Allocations** : l’allocation régulière d’un enfant, ce qui lui est encore dû et l’argent qu’il a.

L’écran s’ouvre sur **Dépenses partagées**. L’onglet choisi est gardé seulement tant que vous restez sur l’écran.

### Où les données sont gardées {#where-kept}

Les nouveaux groupes, prêts et allocations sont enregistrés dans le groupe de comptes partagé où vous pouvez ajouter des données (ou, s’il n’y en a pas, le premier groupe où vous le pouvez). Ils sont affichés avec ceux de tous les groupes que vous voyez. Enregistrer, modifier ou supprimer demande la permission de modifier les données de ce groupe : un utilisateur en lecture seule voit l’onglet mais reçoit une erreur en enregistrant. Voir [Utilisateurs](users).

### Supprimer demande d’abord {#immediate}

Chaque bouton **✕** de cet écran (une dépense, un remboursement, une inscription d’allocation) et chaque bouton **Supprimer** demande d’abord, en disant ce qui sera supprimé ; **Annuler** le garde. Une fois confirmé, il n’y a pas d’annulation ; inscrivez-la de nouveau si vous l’avez retirée par erreur.

## Dépenses partagées {#shared-expenses}

@index: partager la facture; partage des frais; colocataires; dépenses de voyage; régler les comptes; fin de semaine au chalet

Utilisez un groupe partagé chaque fois que plusieurs personnes paient des choses et partagent les frais : un voyage, une fin de semaine au chalet, un appartement partagé, un cadeau acheté ensemble. Chaque personne peut être membre du ménage ou non.

L’onglet a deux parties : à gauche, la liste des groupes ; à droite, le groupe choisi.

### La liste des groupes {#group-list}

- **Nouveau groupe partagé…** : ouvre la [boîte Nouveau groupe partagé](#group-dialog).
- Les groupes sont classés par nom, les groupes actifs d’abord, puis les groupes archivés marqués **(archivé)**. Cliquez sur un groupe pour l’afficher à droite. Quand aucun groupe n’est choisi, le premier est affiché.
- S’il n’y a encore aucun groupe, la liste le dit et suggère d’en créer un.

### Le groupe choisi {#group-view}

En haut figurent le nom du groupe et deux boutons :

- **Ajouter une dépense…** : ouvre la [boîte Ajouter une dépense](#expense-dialog).
- **Modifier le groupe…** : ouvre la même boîte que **Nouveau groupe partagé…**, pour renommer le groupe, changer ses personnes ou l’archiver.

Plus bas, une carte par personne indique où elle en est :

- **doit recevoir** un montant : cette personne a payé plus que sa part ; les autres lui doivent de l’argent.
- **doit** un montant, en rouge : cette personne a payé moins que sa part.
- **quitte** : rien n’est dû d’un côté ni de l’autre.

Le solde d’une personne est tout ce qu’elle a payé, moins sa part de chaque dépense, corrigé des remboursements qu’elle a faits ou reçus.

### Pour régler {#settle-up}

Quand les soldes ne sont pas tous à zéro, **Pour régler** énumère les paiements qui mettent tout le monde quitte, un par ligne, par exemple « Sam → Alex : 42,50 $ ». La liste utilise le moins de paiements possible : la personne qui doit le plus paie celle à qui l’on doit le plus, et ainsi de suite jusqu’à ce que tout le monde soit quitte.

- **Marquer payé** : inscrit ce paiement comme un remboursement dans le groupe, daté d’aujourd’hui, avec la description **Remboursement**. Les soldes et la liste se mettent à jour aussitôt. Utilisez-le une fois l’argent réellement remis. Il ne crée pas d’opération dans vos comptes.

### La liste des dépenses {#expense-list}

Sous les cartes, chaque dépense et chaque remboursement du groupe sont listés, du plus récent au plus ancien :

- la date ;
- pour quoi, et dessous soit « Payé par Alex, partagé entre Alex, Sam, Léa » (seules les personnes qui ont une part plus grande que zéro sont nommées), soit, pour un remboursement, « Sam a payé Alex » ;
- le montant ;
- **✕** : demande « Supprimer « description » (montant, date)? Qui doit quoi à qui est recalculé. » et, une fois confirmé, supprime cette dépense ou ce remboursement. Les soldes sont recalculés sans elle.

Pour corriger une dépense, supprimez-la et ajoutez-la de nouveau.

### Boîte Nouveau groupe partagé {#group-dialog}

La même boîte crée un groupe (**Nouveau groupe partagé…**) ou le modifie (**Modifier le groupe…**).

- **Nom (un voyage, un appartement…)** : le nom du groupe, affiché dans la liste. Obligatoire.
- **Personnes** : une ligne par personne. Un nouveau groupe commence avec deux lignes vides.
  - **Nom** : le nom de la personne tel que vous le voulez sur les cartes et dans la liste des dépenses. Une ligne laissée sans nom est ignorée à l’enregistrement.
  - **Membre du ménage** : relie au besoin la personne à un membre du ménage. Choisissez **Hors du ménage** pour un ami ou un parent qui n’en fait pas partie. Choisir un membre remplit le nom s’il est encore vide. Ce lien sert de repère ; les soldes se calculent de la même façon dans les deux cas.
  - **✕** : retire la ligne de cette personne.
- **Ajouter une personne** : ajoute une ligne vide.
- **Archiver ce groupe (tout est réglé)** : affiché seulement en modification. Un groupe archivé passe à la fin de la liste, avec **(archivé)** après son nom. Ses dépenses et ses soldes sont gardés, et vous pouvez toujours l’ouvrir, y ajouter des dépenses ou le désarchiver.
- **Supprimer** : affiché seulement en modification. Demande « Supprimer le groupe « nom » avec toutes ses dépenses et tous ses remboursements? Qui doit quoi à qui est perdu. » et, une fois confirmé, supprime le groupe, ses personnes et toutes ses inscriptions. C’est sans retour ; archivez plutôt un groupe pour garder son historique.
- **Enregistrer** enregistre le groupe ; **Annuler** ferme sans enregistrer.

Règles vérifiées à l’enregistrement :

- Un groupe compte au moins deux personnes qui ont un nom.
- Une personne qui a déjà une dépense, une part ou un remboursement dans le groupe ne peut pas être retirée. Supprimez d’abord ces inscriptions.

Les montants du groupe sont dans la devise de base du ménage, telle qu’elle était à la création du groupe.

### Boîte Ajouter une dépense {#expense-dialog}

- **Date** : le jour où la dépense a été payée, au format AAAA-MM-JJ. Aujourd’hui par défaut.
- **Pour quoi** : une courte description, comme « Épicerie » ou « Location du bateau ». Laissée vide, la dépense affiche un tiret.
- **Montant** : ce qui a été payé, dans la devise du groupe. Obligatoire et plus grand que zéro. Un signe moins est ignoré.
- **Payé par** : la personne qui a payé. La première personne du groupe par défaut.
- **Parts** : une case par personne, 1 par défaut. Le montant est réparti en proportion des parts :
  - 1 chacun partage également ;
  - 2 pour qui compte double, comme un couple inscrit sur une seule ligne ;
  - 0 exclut quelqu’un de cette dépense.
  Seuls des nombres entiers peuvent être saisis. Au moins une personne doit avoir une part.

Le partage est exact au cent près : les cents qui restent vont aux personnes dont la part a été le plus arrondie à la baisse, si bien que les parts donnent toujours le montant total.

L’enregistrement ajoute la dépense à la liste et met à jour chaque carte et la liste **Pour régler**.

## Prêts en famille {#family-loans}

@index: prêt personnel; prêt à un enfant; prêt à un parent; prêter de l’argent en famille; reconnaissance de dette

Inscrivez ici l’argent prêté entre membres de la famille ou amis : un parent qui aide à payer une réparation d’auto, un prêt à un frère ou une sœur, de l’argent qu’un enfant a emprunté. Ces prêts sont gardés à part des prêts bancaires et des hypothèques de l’écran [Prêts et hypothèques](loans) et n’entrent pas dans votre valeur nette.

L’intérêt, s’il y en a, est un intérêt simple sur ce qui reste dû, compté jour par jour au taux annuel. Chaque remboursement paie d’abord l’intérêt dû jusque-là, puis le montant prêté.

> Remarque : L’ARC a des règles sur les prêts à faible taux ou sans intérêt entre conjoints et avec des membres de la famille (les règles d’attribution), qui peuvent rendre imposable chez le prêteur le revenu gagné avec cet argent. Consultez un conseiller avant d’en faire un. RANN’s Roost ne donne pas de conseils fiscaux.

### La liste des prêts {#loan-list}

- **Ajouter un prêt en famille…** : ouvre la [boîte du prêt](#loan-dialog) pour un nouveau prêt.
- Chaque prêt affiche « Prêteur à Emprunteur » en gras, avec **(remboursé)** s’il est fermé, et dessous le montant prêté et sa date, le taux s’il y en a un (« 2,50 % par année ») et ce qui a été remboursé jusqu’ici.
- À droite : ce qui reste dû aujourd’hui, intérêt compris, et, quand une partie est de l’intérêt, « dont … d’intérêt ».
- Les prêts ouverts sont listés d’abord, du plus récent au plus ancien ; les prêts fermés viennent ensuite.
- Cliquez sur un prêt pour ouvrir ses [remboursements](#repayments-dialog).

### Boîte Ajouter un prêt en famille {#loan-dialog}

La même boîte sert à ajouter un prêt et, depuis la fenêtre des remboursements, à le modifier (**Modifier le prêt…**).

- **Prêté par** : qui a prêté l’argent. N’importe quel nom ; ce n’est pas forcément un membre du ménage. Obligatoire.
- **Prêté à** : qui l’a emprunté. Obligatoire.
- **Montant prêté** : le montant prêté, plus grand que zéro, dans la devise de base du ménage.
- **Date du prêt** : le jour où l’argent a été prêté, au format AAAA-MM-JJ. L’intérêt, s’il y en a, court à partir de ce jour, et aucun remboursement ne peut être daté avant. Aujourd’hui par défaut.
- **Taux d’intérêt (% par année)** : le taux annuel, par exemple 2,5 (le point fonctionne aussi). Laissez vide pour un prêt sans intérêt. De 0 à 50 % ; il est gardé avec deux décimales, arrondi au plus près (3,125 devient 3,13).
- **Notes** : ce qu’il faut retenir, comme l’objet du prêt ou l’entente de remboursement.
- **Remboursé en entier (le fermer)** : affiché en modification. Un prêt fermé affiche **(remboursé)** et passe à la fin de la liste, et son intérêt s’arrête : aucun intérêt n’est compté après son dernier remboursement (après la date du prêt, s’il n’y en a aucun). Les montants affichés viennent toujours des remboursements inscrits ; inscrivez donc aussi le dernier remboursement ; ce qui paraît encore dû est ce que ces remboursements ont laissé. Décocher la case laisse l’intérêt courir de nouveau, jusqu’à aujourd’hui.
- **Supprimer** : affiché en modification. Demande « Supprimer le prêt de prêteur à emprunteur avec ses remboursements? » et, une fois confirmé, supprime le prêt et tous ses remboursements.

### Fenêtre des remboursements {#repayments-dialog}

Cliquer sur un prêt ouvre une fenêtre intitulée « Prêteur à Emprunteur ».

- La première ligne résume le prêt aujourd’hui : **Reste dû** (ce qui reste du montant prêté plus l’intérêt non payé), **intérêt jusqu’ici** (tout l’intérêt compté depuis le prêt) et **remboursé** (le total des remboursements).
- Les remboursements sont listés du plus récent au plus ancien, chacun avec sa date, son montant et **✕** pour le supprimer. Il demande d’abord « Supprimer le remboursement de montant du date? Le solde est recalculé. » ; une fois le remboursement supprimé, la fenêtre se ferme (rouvrez le prêt pour voir les nouveaux chiffres).
- **Date** et **Remboursement** : saisissez un nouveau remboursement, puis cliquez sur **Ajouter le remboursement**. Le bouton est offert dès qu’un montant est saisi. Un remboursement ne peut pas précéder le prêt. Un remboursement daté dans l’avenir ne compte qu’à partir de sa date.
- **Modifier le prêt…** : ouvre la [boîte du prêt](#loan-dialog).
- **Fermer** : ferme la fenêtre.

## Allocations {#allowances}

@index: argent de poche; allocation des enfants; argent des enfants; tâches ménagères

Une allocation est un montant fixe qu’un enfant reçoit à intervalles réguliers. RANN’s Roost compte chaque jour d’allocation comme dû à l’enfant jusqu’à ce que vous le marquiez payé, et tient l’argent de l’enfant : ce qu’il a reçu en allocation, gagné ou reçu, moins ce qu’il a dépensé.

### La liste des allocations {#allowance-list}

- **Prévoir une allocation…** : ouvre la [boîte de l’allocation](#allowance-dialog). Le bouton apparaît dès que le ménage compte au moins un membre (voir [Membres du ménage](members)) ; il propose le premier membre de type enfant, sinon le premier membre.
- Chaque allocation affiche la personne, le montant et la fréquence (par exemple « Léa · 10,00 $ par semaine »), et dessous **A** (l’argent de l’enfant aujourd’hui) et, tant que l’allocation court, **prochaine le** (le prochain jour d’allocation).
- Quand des jours d’allocation sont passés sans être payés, la ligne affiche **… dus** en rouge et un bouton **Marquer payé**. **Marquer payé** inscrit un seul paiement, daté d’aujourd’hui, pour tout le montant dû.
- Cliquez sur une allocation pour ouvrir [l’argent de l’enfant](#allowance-entries).

### Boîte Prévoir une allocation {#allowance-dialog}

- **Personne** : le membre du ménage qui reçoit l’allocation.
- **Montant** : le montant de chaque jour d’allocation, plus grand que zéro, dans la devise de base du ménage.
- **Fréquence** : **par semaine**, **aux deux semaines** ou **par mois**. Les jours d’allocation sont comptés à partir du premier jour : tous les 7 jours, tous les 14 jours, ou le même jour chaque mois (une allocation qui commence le 31 tombe le dernier jour des mois plus courts).
- **Premier jour** : le premier jour d’allocation, au format AAAA-MM-JJ. Aujourd’hui par défaut.
- **Dernier jour (facultatif)** : le dernier jour où l’allocation court. Laissez vide s’il n’y a pas de fin. Il ne peut pas précéder le premier jour. Après lui, aucun jour d’allocation n’est plus compté.
- **Notes** : ce qu’il faut retenir, comme ce que l’allocation doit couvrir ou les règles convenues avec l’enfant. Facultatif ; on peut taper plusieurs lignes.
- **Supprimer** : affiché en modification. Demande « Supprimer l’allocation de nom avec toutes ses inscriptions? » et, une fois confirmé, la supprime avec toutes ses inscriptions.

Changer plus tard le montant, la fréquence ou les dates recalcule ce qui était dû depuis le premier jour, au nouveau montant.

### Allocation et argent de l’enfant {#allowance-entries}

Cliquer sur une allocation ouvre cette fenêtre.

- La première ligne la résume : **Dû jusqu’ici** (le nombre de jours d’allocation jusqu’à aujourd’hui, ou jusqu’au dernier jour, fois le montant), **à payer** (ce qui est dû moins les allocations payées, jamais sous zéro) et **l’enfant a** (les allocations payées plus l’argent gagné ou reçu, moins l’argent dépensé).
- Les 30 dernières inscriptions sont listées de la plus récente à la plus ancienne : date, genre, note et montant (les dépenses en négatif). **✕** demande « Supprimer cette inscription (genre, montant, date)? » et, une fois confirmé, supprime l’inscription ; la fenêtre se ferme.
- Pour ajouter une inscription, remplissez :
  - **Date** : aujourd’hui par défaut. Une inscription datée dans l’avenir ne compte qu’à partir de sa date.
  - **Quoi** :
    - **Allocation payée** : vous avez remis son allocation à l’enfant. Elle réduit ce qui est dû et s’ajoute à l’argent de l’enfant.
    - **Gagné ou reçu** : de l’argent gagné par des tâches, un cadeau d’anniversaire, une vente. Il s’ajoute seulement à l’argent de l’enfant.
    - **Dépensé** : de l’argent que l’enfant a dépensé. Il est retranché de son argent.
  - **Montant** : plus grand que zéro.
  - **Notes** : par exemple « Ramassé les feuilles » ou « Livre ».
  Cliquez ensuite sur **Ajouter**, offert dès qu’un montant est saisi.
- **Modifier l’allocation…** : ouvre la [boîte de l’allocation](#allowance-dialog).
- **Fermer** : ferme la fenêtre.

> Conseil : Servez-vous de l’argent de l’enfant comme d’une tirelire que vous gardez pour lui : inscrivez ce qu’il gagne et dépense, et **A** vous dit toujours combien lui appartient.
