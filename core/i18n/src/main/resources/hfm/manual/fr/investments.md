# Placements

Placements montre ce que vous détenez dans vos comptes de courtage, vos régimes enregistrés, vos portefeuilles de cryptoactifs et vos comptes de métaux précieux, ce que cela vaut, ce que cela a coûté et les gains en capital réalisés. L’écran se trouve dans le groupe Placements et emprunts du menu.

L’écran a trois parties : la liste des comptes à gauche, le compte ou la vue choisi à droite, et les fenêtres qui s’ouvrent à partir des boutons. Ce chapitre suit cet ordre, puis traite des portefeuilles de cryptoactifs et des métaux précieux, qui ont leurs propres vues.

> Remarque : Les chiffres de cet écran sont une aide à l’organisation, et non des conseils fiscaux. Vos feuillets, vos relevés de courtage et l’ARC ou Revenu Québec ont le dernier mot.

## L’écran Placements {#investments-screen}

@index: portefeuille; courtage; titres détenus; titres; actions; FNB; fonds commun de placement

Les comptes de placement ne se créent pas ici. Créez-les dans [Comptes](accounts) avec un type de placement : Compte de courtage (non enregistré), REER, REER de conjoint, FERR, FERR de conjoint, CRI, FRV, CELI, CELIAPP, REEE, Régime de retraite, Portefeuille de cryptoactifs ou Métaux précieux. Indiquez-y aussi les titulaires de chaque compte : ce sont eux qui déterminent à qui reviennent les gains en capital et les droits de cotisation du compte.

Quand vous ouvrez Placements à partir du registre d’un compte, ce compte s’affiche d’abord. Sinon, c’est le premier compte de la liste, ou la liste des titres s’il n’y a aucun compte.

Pour modifier quoi que ce soit dans cet écran, il faut la permission de modifier le groupe de comptes qui contient le compte. Avec un accès en lecture seule, vous pouvez consulter, mais non enregistrer.

### La liste des comptes {#account-list}

La colonne de gauche présente tous les comptes de placement que vous pouvez voir, en deux groupes :

- **Non enregistrés** : les comptes de courtage, les portefeuilles de cryptoactifs et les comptes de métaux précieux. Leurs ventes donnent des gains en capital imposables.
- **Régimes enregistrés** : REER, REER de conjoint, FERR, FERR de conjoint, CRI, FRV, CELI, CELIAPP, REEE et régimes de retraite. Les gains réalisés à l’intérieur ne sont pas imposés tant que l’argent reste dans le régime.

Chaque ligne montre le nom du compte, son type en dessous et sa valeur totale à droite. Un portefeuille de cryptoactifs montre son solde en cryptoactifs en dessous et, à droite, sa valeur dans votre devise de base.

Sous les comptes se trouvent trois autres choix :

- **Gains en capital et PBR** : le prix de base rajusté de tout ce que vous détenez hors des régimes enregistrés, et les gains et pertes de chaque année. Voir [Gains en capital et PBR](investments#capital-gains-acb).
- **Titres** : chaque action, FNB, fonds, obligation, CPG et option connu des livres. Voir [Titres](investments#securities).
- **Importer d’une plateforme…** : lit le fichier d’historique d’une plateforme d’échange de cryptoactifs. Voir [Importer d’une plateforme](investments#exchange-import).

Au bas, une ligne **Total** par devise additionne les valeurs de la liste (par exemple Total CAD et Total USD). Les portefeuilles de cryptoactifs y comptent à leur valeur dans votre devise de base.

### Historique Quicken conservé {#kept-quicken-history}

@index: Quicken; QIF

Si vous avez importé auparavant un fichier Quicken qui contenait un historique de placements, une bannière en couleur l’indique en haut : « Une importation Quicken antérieure a conservé son historique de placements ». Cliquez sur **Importer l’historique de placements** pour le lire dans les comptes de placement portant les mêmes noms que dans Quicken. Une fenêtre de résultat indique ensuite le nombre d’opérations ajoutées et de titres créés, et énumère les remarques sur les lignes qui n’ont pu être lues.

## Un compte de placement {#investment-account}

Choisissez un compte de courtage ou un régime enregistré dans la liste pour l’afficher. (Les portefeuilles de cryptoactifs et les comptes de métaux précieux ont leurs propres vues : voir [Portefeuilles de cryptoactifs](investments#crypto-wallets) et [Métaux précieux](investments#precious-metals).)

L’en-tête montre le nom du compte et son type. Pour un régime enregistré, il ajoute « enregistré : aucun gain en capital tant que l’argent reste dans le régime », puisqu’une vente à l’intérieur d’un REER, d’un CELI ou d’un autre régime ne donne aucun gain ni aucune perte en capital à déclarer.

### Chiffres du compte {#account-figures}

@index: valeur marchande; valeur comptable; gain non réalisé; encaisse

- **Valeur totale** : l’encaisse plus la valeur marchande de chaque titre. Un titre sans cours compte à sa valeur comptable.
- **Encaisse** : l’argent dans le compte : son solde d’ouverture plus chaque ligne d’encaisse de son registre (dépôts, retraits, achats, ventes, revenus et frais).
- **Valeur marchande** : les titres à leur dernier cours, sans l’encaisse. Un titre sans cours y compte aussi à sa valeur comptable.
- **Valeur comptable** : ce que les unités encore détenues vous ont coûté, commissions comprises, à leur coût moyen.
- **Gain non réalisé** : la valeur marchande moins la valeur comptable : ce que vous gagneriez ou perdriez en vendant tout aux cours d’aujourd’hui.

Si des titres n’ont pas encore de cours, une ligne en rouge les nomme : ils comptent à leur valeur comptable jusqu’à ce que vous saisissiez un cours avec **Mettre à jour les cours**.

Les montants sont dans la devise du compte. Un compte en dollars américains affiche des dollars américains ; un titre coté dans une autre devise que son compte est converti au taux de change du jour (voir [Taux et cours](rates)).

### Boutons du compte {#account-buttons}

- **Ajouter une opération** : inscrit un achat, une vente, un dividende ou tout autre événement de placement. Voir [Ajouter ou modifier une opération](investments#transaction-dialog).
- **Mettre à jour les cours** : saisit le cours du jour de chaque titre détenu, dans une seule fenêtre. Offert dès que le compte détient quelque chose. Voir [Mettre à jour les cours](investments#update-prices).
- **Importer un relevé…** : lit un fichier de courtage (OFX, QFX ou CSV). Voir [Importer un relevé de courtage](investments#import-statement).
- **Vérifier un relevé** : compare l’encaisse et les unités d’un relevé papier ou PDF avec les livres. Voir [Vérifier un relevé](investments#check-statement).

### Onglet Titres détenus {#holdings-tab}

Une ligne par titre détenu aujourd’hui, par ordre de nom :

- **Titre** : le symbole (ou le nom s’il n’y a pas de symbole), avec le nom complet en dessous. Pour une obligation ou un CPG, une troisième ligne montre son coupon et sa date d’échéance quand ils sont inscrits sur le titre, par exemple « coupon 3,25 % · échéance le 2027-06-01 ».
- **Quantité** : le nombre d’unités détenues, jusqu’à six décimales.
- **Cours** : le dernier cours à la date d’aujourd’hui ou avant, dans la devise du titre, avec sa date en dessous ; « aucun cours » s’il n’y en a pas.
- **Valeur marchande** : la quantité multipliée par le cours et par le multiplicateur de valeur du titre, dans la devise du compte ; un tiret s’il n’y a pas de cours.
- **Valeur comptable** : le coût moyen des unités détenues, commissions comprises.
- **Gain** : la valeur marchande moins la valeur comptable, avec le pourcentage en dessous. Une perte s’affiche en rouge.

### Onglet Opérations {#transactions-tab}

Toutes les opérations de placement du compte, des plus récentes aux plus anciennes : la date, le type, le titre (pour une fusion, une flèche vers le titre reçu), la quantité avec « @ cours » ou « × ratio », et l’effet sur l’encaisse du compte. Quand une opération ne déplace pas d’argent mais a un montant (une distribution théorique, des unités transférées en entrée), le montant s’affiche en gris.

Cliquez sur une ligne pour la modifier ou la supprimer.

### Onglet Relevés {#statements-tab}

@index: rapprochement; rapprocher; relevé de courtage

Les relevés enregistrés pour le compte, importés ou saisis à la main, des plus récents aux plus anciens. Chaque ligne montre la date du relevé, l’encaisse indiquée, le nombre de titres et soit **Rapproché** (en couleur), soit **À vérifier** (en rouge). Cliquez sur une ligne pour ouvrir la comparaison avec les livres. Voir [Vérifier un relevé](investments#check-statement).

## Ajouter ou modifier une opération {#transaction-dialog}

@index: achat; vente; dividende; RRD; remboursement de capital; fractionnement; fusion; commission

Cliquez sur **Ajouter une opération** ou sur une ligne de l’onglet Opérations. Les champs affichés changent selon le type d’opération. Cliquez sur **Enregistrer** pour l’inscrire, ou sur **Annuler**.

### Types d’opérations {#transaction-kinds}

- **Type** : ce qui s’est passé. Les choix sont :
  - Achat : l’encaisse paie les unités et la commission ; les deux forment leur coût.
  - Vente : les unités sortent à leur coût moyen ; l’écart avec le produit est le gain ou la perte en capital.
  - Revenu (dividende, intérêts, distribution) : payé en espèces. L’impôt étranger retenu est inscrit comme une dépense.
  - Revenu réinvesti (RRD) : un revenu utilisé pour acheter d’autres unités. Il compte comme revenu et s’ajoute au coût des nouvelles unités.
  - Remboursement de capital : de l’argent remis à même votre capital. Il réduit le coût des unités et n’est pas un revenu. Si les remboursements dépassent un jour le coût, l’excédent est un gain en capital et le coût reste à zéro.
  - Distribution réinvestie (théorique) : une distribution réinvestie sans nouvelles unités, comme l’indique un feuillet T3 ou RL-16. Elle augmente le coût des unités.
  - Fractionnement ou regroupement : change le nombre d’unités ; leur coût total reste le même.
  - Fusion ou échange : des unités d’un titre échangées contre des unités d’un autre ; le coût les suit.
  - Unités transférées en entrée : des unités venant d’une autre institution ou d’un autre compte, ou détenues avant que vous commenciez à utiliser l’application. Saisissez leur valeur comptable.
  - Unités transférées en sortie : des unités transférées à une autre institution ou à un autre compte ; leur coût les suit.
  - Frais : des frais de compte ou de gestion payés en espèces.

Sous le type, une courte phrase rappelle ce que fait le type choisi.

### Champs d’une opération {#transaction-fields}

- **Type** : voir la liste ci-dessus. Pour une nouvelle opération, Achat est choisi par défaut.
- **Date** : la date de l’opération, sous la forme année-mois-jour (par exemple 2026-03-15). Aujourd’hui par défaut. Les titres détenus, l’encaisse, le PBR et les gains sont calculés dans l’ordre des dates ; la date compte donc : une vente datée avant l’achat est refusée.
- **Type de revenu** : pour Revenu et Revenu réinvesti seulement. Dividende, Intérêts ou Distribution. Il choisit la catégorie de revenu (Dividendes, Intérêts ou Distributions de fonds) utilisée dans les rapports, les budgets et le rapport des revenus de placement.
- **Titre** : le titre acheté, vendu ou qui a versé le revenu. Obligatoire pour tous les types sauf Revenu et Frais, où vous pouvez choisir (aucun) pour un revenu ou des frais qui concernent tout le compte. Pour une fusion, le libellé est **Titre échangé** : le titre cédé. Les titres archivés ne sont pas proposés.
- **Nouveau titre…** : ouvre la fenêtre des titres pour en créer un sans quitter l’opération. Une fois enregistré, il est choisi dans le champ Titre.
- **Titre reçu** : pour une fusion seulement : le titre reçu. Il doit être différent du titre échangé.
- **Quantité** : le nombre d’unités, pour Achat, Vente, Revenu réinvesti et les unités transférées en entrée ou en sortie. Pour une fusion, c’est **Unités échangées** : les anciennes unités cédées. Obligatoire, plus que zéro. Les décimales sont permises (fractions de parts d’un fonds).
- **Cours** : le prix d’une unité, dans la devise du titre (le libellé l’indique, par exemple Cours (USD)). Pour Achat, Vente et Revenu réinvesti. Tant que vous n’avez pas tapé le montant vous-même, il est rempli pour vous : quantité multipliée par le cours et par le multiplicateur de valeur du titre.
- **Nouvelles unités par ancienne unité** : pour Fractionnement ou regroupement et Fusion ou échange. Un fractionnement de 2 pour 1 donne 2 ; un regroupement de 1 pour 4 donne 0,25. Pour une fusion, 1,5 signifie que chaque ancienne unité est devenue 1,5 nouvelle unité. Obligatoire, plus que zéro.
- **Montant** : l’argent en jeu, dans la devise du compte. Il n’est pas affiché pour les fractionnements, les fusions et les unités transférées en sortie, qui ne déplacent pas d’argent. Son libellé dépend du type :
  - Valeur (avant commission), pour Achat et Vente : la quantité multipliée par le cours, avant la commission.
  - Montant, pour Revenu : le revenu brut, avant l’impôt étranger retenu.
  - Montant réinvesti, pour Revenu réinvesti.
  - Montant remboursé, pour Remboursement de capital.
  - Montant ajouté au coût, pour une distribution théorique.
  - Valeur comptable des unités, pour les unités transférées en entrée : ce que ces unités vous ont coûté à l’origine (selon le relevé de l’institution d’où elles viennent), et non leur valeur marchande.
  - Frais, pour Frais.
- **Commission et frais** : pour Achat, Vente et Revenu réinvesti. À l’achat, elle s’ajoute au coût des unités ; à la vente, elle est retranchée du produit et réduit donc le gain.
- **Impôt étranger retenu** : pour Revenu seulement. L’impôt qu’un pays étranger a gardé (par exemple 15 % sur les dividendes américains). Il ne peut dépasser le revenu. Il est inscrit comme une dépense dans la catégorie Impôt étranger retenu et figure au rapport des revenus de placement, où il appuie le crédit pour impôt étranger.
- **Note** : un texte libre gardé avec l’opération et ses lignes du registre.
- **Supprimer** : affiché quand vous modifiez une opération existante. Voir [Supprimer une opération](investments#delete-transaction).

> Important : L’application refuse une opération qui laisserait, à une date donnée, moins d’unités que ce qui est vendu ou transféré en sortie. Si ce message s’affiche, cherchez un achat manquant ou une date mal tapée.

### Ce qu’une opération inscrit au registre {#register-lines}

Chaque opération de placement inscrit aussi ses lignes d’encaisse dans le registre du compte, pour que l’encaisse reste juste :

- Achat : une ligne pour la valeur plus la commission, retirée de l’encaisse.
- Vente : une ligne pour le produit après la commission, ajoutée à l’encaisse.
- Revenu : une ligne pour le revenu moins l’impôt retenu, répartie entre la catégorie de revenu et la catégorie Impôt étranger retenu.
- Revenu réinvesti : la ligne de revenu ci-dessus, et une deuxième ligne pour l’achat des nouvelles unités (avec la commission, s’il y a lieu).
- Remboursement de capital : une ligne pour l’argent reçu.
- Frais : une ligne dans la catégorie Frais de placement.
- Les fractionnements, les fusions, les distributions théoriques et les unités transférées en entrée ou en sortie n’inscrivent aucune ligne d’encaisse.

Les achats, les ventes et les remboursements de capital sont des mouvements à l’intérieur de votre propre argent : les rapports et les budgets les excluent. Les revenus, l’impôt étranger et les frais sont catégorisés ; ils paraissent donc dans les rapports et les budgets comme tout autre revenu ou dépense.

Modifier une opération réécrit ses lignes d’encaisse. Si ces lignes sont déjà rapprochées dans le registre, l’application demande d’abord « Modifier une opération rapprochée? », comme le registre : **Modifier** enregistre la modification (le compte ne concorde alors plus avec ce relevé, et la modification est gardée dans l’historique), **Annuler** laisse tout comme avant.

### Supprimer une opération {#delete-transaction}

Cliquez sur **Supprimer** dans la fenêtre de l’opération. L’application demande « Supprimer cette opération ? » : elle est retirée avec ses lignes d’encaisse dans le registre. Cliquez sur **Supprimer** pour confirmer, ou sur **Annuler**. Si ses lignes d’encaisse sont rapprochées, l’application demande ensuite « Modifier une opération rapprochée? » avant de supprimer. On ne peut l’annuler qu’en saisissant l’opération de nouveau. Une suppression qui laisserait, à une date donnée, plus d’unités vendues que détenues est refusée.

## Mettre à jour les cours {#update-prices}

@index: cotation; cours; prix du marché

Cliquez sur **Mettre à jour les cours** pour saisir le dernier cours de chaque titre du compte, dans une seule fenêtre.

- **Date** : la date des cours, aujourd’hui par défaut.
- Un champ par titre, nommé par son symbole et sa devise, par exemple XIC (CAD). Il commence avec le dernier cours connu ; la ligne en dessous indique « Dernier cours le » et la date. Les cours sont dans la devise de chaque titre.

Laissez un champ tel quel pour garder le dernier cours. Cliquez sur **Enregistrer** pour inscrire les cours qui ont changé (ou qui étaient à une autre date). Un titre a une seule liste de cours, partagée par tous les comptes qui le détiennent : un nouveau cours les met tous à jour. Les cours saisis à la main ne sont jamais remplacés par un téléchargement.

> Conseil : Les cours des actions et des FNB peuvent aussi être téléchargés automatiquement. C’est facultatif et désactivé tant que vous ne l’activez pas dans [Taux et cours](rates). Les fonds communs canadiens, les obligations et les CPG gardent des cours saisis à la main.

## Importer un relevé de courtage {#import-statement}

@index: OFX; QFX; CSV; téléchargement; fichier de courtage

Cliquez sur **Importer un relevé…** et choisissez un fichier OFX, QFX ou CSV téléchargé de votre courtier. La fenêtre « Importer » suivie du nom du fichier s’ouvre.

- Pour chaque compte trouvé dans le fichier, une ligne montre le format, les quatre derniers chiffres du numéro de compte quand le fichier les donne, la date du relevé et le nombre d’opérations.
- **Importer dans** : le compte de placement qui le reçoit. L’application choisit le compte dont le numéro se termine par les mêmes quatre chiffres quand un seul compte correspond ; sinon, le compte que vous regardiez. Changez-le au besoin.

Cliquez sur **Importer** (offert quand chaque relevé du fichier a un compte) ou sur **Annuler**. Si le fichier ne peut être lu, un message en rouge l’indique ; rien n’est importé.

Ce que fait l’importation :

- Les titres du fichier sont associés à vos titres existants par leur symbole (et leur devise) ou par leur nom ; les autres sont créés. Un symbole comme XIC.TO est lu comme XIC à la Bourse de Toronto.
- Les cours du fichier sont enregistrés comme cours importés. Ils ne remplacent jamais un cours que vous avez tapé.
- Chaque opération devient une opération de placement : achats, ventes, dividendes, intérêts, distributions, revenus réinvestis, remboursements de capital, fractionnements, unités transférées en entrée ou en sortie, et frais. Les dépôts et retraits d’argent deviennent des lignes ordinaires du registre, sans catégorie. Dans un régime enregistré, ils comptent comme cotisations et retraits (voir [Ce qui compte comme cotisation](plans#what-counts)) ; un dépôt déjà au registre à la même date pour le même montant, comme le virement que vous avez inscrit depuis votre banque, n’est pas ajouté de nouveau.
- Chaque opération n’est importée qu’une fois. Les identifiants du fichier sont conservés (ou, à défaut, une empreinte de la ligne) ; importer le même fichier de nouveau n’ajoute donc rien en double.
- Les unités transférées en entrée sont inscrites à la valeur marchande indiquée dans le fichier, avec une remarque : saisissez leur vraie valeur comptable si elle diffère, puisque le PBR en dépend.
- Quand le fichier donne les titres détenus et l’encaisse à la date du relevé, ils sont enregistrés comme relevé sous l’onglet Relevés, pour que vous puissiez les comparer avec les livres.

Les fichiers CSV sont lus d’après les titres de leurs colonnes, en français ou en anglais (date, opération ou type, symbole, description, quantité, prix ou cours, montant, commission, devise) ; la plupart des exportations de courtiers n’exigent donc aucun réglage. Les lignes dont l’opération n’est pas reconnue sont sautées et énumérées comme remarques plutôt que devinées.

Une ligne d’impôt retenu (retenue d’impôt, impôt étranger, impôt des non-résidents) est mise sur le dividende, la distribution ou les intérêts du même jour, pour le même titre quand la ligne en nomme un, comme son **Impôt étranger retenu** : il est alors dans la catégorie Impôt étranger retenu et au rapport des revenus de placement, pour le crédit pour impôt étranger. S’il n’y a pas de tel revenu ce jour-là, la ligne est inscrite comme des frais et une remarque le dit ; ouvrez le revenu et déplacez le montant dans son champ Impôt étranger retenu.

### Résultat de l’importation {#import-results}

La fenêtre « Importation terminée » montre :

- Les opérations ajoutées et les nouveaux titres.
- Le nombre d’opérations qui y étaient déjà et n’ont pas été ajoutées de nouveau.
- Si les titres et l’encaisse du relevé ont été enregistrés.
- **Remarques** : jusqu’à 50 remarques sur des lignes sautées ou à vérifier.

Cliquez sur **Fermer**. Vérifiez ensuite l’onglet Titres détenus, et l’onglet Relevés si un relevé a été enregistré.

## Vérifier un relevé {#check-statement}

@index: rapprochement; vérification de relevé

Vérifier un relevé compare ce que votre courtier dit que vous détenez avec ce que disent les livres, à la date du relevé. Un écart vient habituellement d’une opération manquante ou en double.

Cliquez sur **Vérifier un relevé**. La fenêtre commence avec les chiffres des livres ; vous ne changez donc que ce qui diffère :

- **Date du relevé** : la date pour laquelle le relevé a été établi. Aujourd’hui par défaut.
- **Encaisse** : l’encaisse indiquée sur le relevé, dans la devise du compte. Tant que vous n’y tapez rien, elle suit la date du relevé : elle montre l’encaisse des livres à la date choisie.
- Un champ par titre détenu dans les livres à cette date, avec le nombre d’unités indiqué sur le relevé.

Cliquez sur **Comparer** pour enregistrer le relevé et voir la comparaison, ou sur **Annuler**.

La fenêtre de comparaison, « Relevé du » suivi de la date, a deux colonnes : **Relevé** et **Livres**. Chaque ligne qui diffère est en rouge et en gras. Au bas :

- « Tout concorde. » ou « Certains chiffres diffèrent : vérifiez s’il manque des opérations ou s’il y en a en double. »
- **Marquer comme rapproché** : offert seulement quand tout concorde. Le relevé s’affiche alors comme Rapproché sous l’onglet Relevés.
- **Supprimer** : retire le relevé (pas les opérations). Il n’y a pas de confirmation.
- **Fermer**.

Les relevés importés d’un fichier s’ouvrent dans la même fenêtre de comparaison à partir de l’onglet Relevés.

## Titres {#securities}

@index: symbole boursier; téléscripteur; fonds; obligation; CPG; option

Choisissez **Titres** au bas de la liste des comptes. Chaque titre connu des livres est énuméré avec son symbole, son nom (« archivé » s’il est archivé), son genre, sa catégorie d’actif et son dernier cours avec sa date. Un titre est partagé par tous les comptes qui le détiennent : son nom, son genre, sa catégorie et ses cours sont les mêmes partout.

Les titres sont aussi créés pour vous quand vous inscrivez un achat avec **Nouveau titre…** ou importez un relevé. Cliquez sur **Ajouter un titre** pour en créer un d’avance, ou sur une ligne pour le modifier.

### Ajouter ou modifier un titre {#security-dialog}

- **Symbole** : le symbole boursier, comme XIC ou AAPL. Facultatif ; enregistré en majuscules. C’est ce que montrent les listes ; sans lui, c’est le nom. Il sert aussi à associer les fichiers importés et, si vous avez activé le téléchargement des cours, à trouver le cours.
- **Bourse** : par exemple TSX, NYSE, NASDAQ. Facultatif ; enregistré en majuscules. Avec le téléchargement des cours, il indique à quel marché s’adresser (un titre inscrit à Toronto se cherche autrement qu’un titre américain).
- **Nom** : le nom complet. Obligatoire.
- **Genre** : Action, FNB, Fonds commun, Obligation, CPG, Option ou Autre. FNB par défaut. Choisir un genre règle aussi le multiplicateur de valeur (100 pour une option, 0,01 pour une obligation, 1 autrement), et choisir Obligation ou CPG règle la catégorie d’actif à Revenu fixe. Vous pouvez les changer ensuite.
- **Devise (p. ex. CAD, USD, BTC)** : la devise dans laquelle le titre est coté, en code de trois lettres (CAD, USD, EUR...). Obligatoire ; un code inconnu est signalé. Un nouveau titre commence avec la devise du compte où vous travailliez, ou votre devise de base. Les cours se saisissent dans cette devise et sont convertis dans la devise du compte pour sa valeur.
- **Catégorie d’actif** : Actions, Revenu fixe, Encaisse et équivalents, Équilibré, Immobilier, Matières premières ou Autre. Sert à la répartition de l’actif du rapport Portefeuille de placements. Choisissez Équilibré pour un fonds qui détient à la fois des actions et des obligations, puis saisissez sa composition (voir [Composition d’un fonds](investments#fund-mix)).
- **Région** : Canada, États-Unis, International, Marchés émergents, Mondial ou Autre. Sert à la répartition par région. Choisissez Mondial pour un fonds qui investit partout dans le monde, puis saisissez sa composition.
- **Multiplicateur de valeur** : quantité multipliée par le cours et par le multiplicateur donne la valeur. 1 pour les actions et les parts de fonds, 100 pour les contrats d’option (un contrat porte sur 100 actions), 0,01 pour les obligations cotées par tranche de 100 de valeur nominale. Doit être plus que zéro. Il change les valeurs marchandes et le montant rempli pour les nouvelles opérations.
- **Échéance** et **Taux (%)** : pour les obligations et les CPG seulement. La date d’échéance et le taux du coupon ou d’intérêt. Les deux s’affichent sous le titre dans l’onglet Titres détenus. Tant que le titre est détenu, son échéance paraît au [Calendrier](calendar#renewals) et comme rappel à partir de 30 jours avant, jusqu’à ce que le remboursement soit inscrit (comme une vente).
- **Cours** : sous le titre Cours, un cours et sa **Date** (aujourd’hui par défaut). Si vous tapez un cours, il est inscrit à cette date quand vous enregistrez. Laissez vide pour n’en inscrire aucun.
- **Notes** : un texte libre.
- **Archivé (n’est plus utilisé)** : affiché quand vous modifiez un titre. Un titre archivé reste dans les livres et dans les opérations passées, mais n’est plus proposé quand vous ajoutez une opération.

Cliquez sur **Enregistrer** (offert dès qu’il y a un nom et une devise valide) ou sur **Annuler**.

> Remarque : Les titres ne peuvent pas être supprimés à partir de cette fenêtre ; archivez ceux que vous n’utilisez plus.

### Composition d’un fonds {#fund-mix}

@index: composition de l’actif; fonds équilibré; fonds mondial; répartition de l’actif

Quand la catégorie d’actif est Équilibré, la fenêtre ajoute **Composition du fonds par catégorie d’actif** : un champ de pourcentage pour Actions, Revenu fixe et Encaisse et équivalents. Quand la région est Mondial, elle ajoute **Régions du fonds** : Canada, États-Unis, International et Marchés émergents.

Prenez les pourcentages dans l’aperçu du fonds. La ligne sous les champs montre le total ; elle reste en rouge tant qu’il n’est pas exactement 100, et le titre ne peut être enregistré avec un autre total. La composition est facultative : sans elle, tout le fonds compte comme Équilibré ou Mondial dans la répartition. Avec elle, la valeur du fonds est répartie entre les parties, et la répartition et les suggestions de rééquilibrage sont plus précises.

### Historique des cours {#price-history}

Quand vous modifiez un titre, ses huit cours les plus récents sont énumérés sous les champs de cours, avec leur date. Cliquez sur **Supprimer** à côté d’un cours pour le retirer aussitôt (il n’y a pas de confirmation). La valeur de chaque compte qui détient le titre utilise alors le dernier cours restant.

## Gains en capital et PBR {#capital-gains-acb}

@index: PBR; prix de base rajusté; gain en capital; perte en capital; annexe 3; TP-21.4.39; gain en capital imposable

Choisissez **Gains en capital et PBR** au bas de la liste des comptes. Cette vue calcule, aux fins de l’impôt, le prix de base rajusté (PBR) de ce que vous détenez hors des régimes enregistrés, et les gains et pertes en capital de chaque année.

### Comment le PBR est calculé {#acb-rules}

- Le PBR est le coût moyen qu’utilise l’ARC : chaque achat ajoute son coût complet (commission comprise), et chaque vente retire le coût moyen des unités vendues.
- Les unités d’un même titre sont mises en commun pour tous les comptes non enregistrés qui ont les mêmes titulaires. Par exemple, le XIC que vous détenez à votre nom chez deux courtiers forme un seul groupe ; le XIC détenu conjointement avec votre conjoint en forme un autre. Les titulaires s’indiquent sur le compte dans [Comptes](accounts) ; un compte sans titulaire compte pour le ménage.
- Les montants sont dans votre devise de base, convertis au taux de la Banque du Canada à la date de chaque opération. S’il manque un taux, une ligne en rouge nomme la devise et ces montants comptent pour zéro : ajoutez le taux dans [Taux et cours](rates).
- Les régimes enregistrés sont entièrement exclus.
- Les cryptoactifs sont mis en commun de la même façon, par cryptoactif et par titulaires. Les métaux précieux vendus sont inclus dans les gains, chaque article à son propre coût.
- Si l’historique ne peut être calculé (par exemple plus d’unités vendues que détenues à une date), une ligne en rouge nomme le compte et la date.

### Gains de l’année {#gains-for-year}

- **Année** : l’année d’imposition à afficher, de cette année jusqu’à dix ans en arrière.
- **Gain en capital net en** (l’année) : les gains moins les pertes de l’année, pour tous les titulaires ensemble.
- **Moitié imposable** : la moitié de ce gain net, la partie incluse dans le revenu.

Le tableau « Gains et pertes en capital » de l’année énumère chaque disposition : **Date**, **Titre**, **Titulaires**, **Quantité**, **Produit** (après commission), **PBR** (le coût des unités vendues), **Gain** (une perte est négative), et la mention « perte apparente possible » s’il y a lieu. Un remboursement de capital qui fait passer le coût sous zéro y paraît aussi comme un gain.

Utilisez **Masquer le tableau** ou **Afficher le tableau** pour le replier, les boutons **CSV**, **Excel** et **PDF** pour l’exporter, et **Imprimer** pour l’imprimer.

> Remarque : Ces chiffres aident à préparer une déclaration (annexe 3, et au Québec le formulaire TP-21.4.39 aussi) ; ils ne constituent pas un conseil fiscal. Vérifiez-les avec vos feuillets et relevés.

### Pertes apparentes {#superficial-loss}

@index: perte apparente; vente à perte et rachat

Une perte porte la mention « perte apparente possible » quand des unités du même groupe ont été achetées dans les 30 jours avant ou après la vente. Selon les règles fiscales, une telle perte peut être refusée et ajoutée plutôt au coût des nouvelles unités. L’application ne fait que la signaler ; elle ne change pas les chiffres. Vérifiez avant de produire votre déclaration.

### Prix de base rajusté aujourd’hui {#acb-today}

Le deuxième tableau énumère chaque groupe détenu aujourd’hui : **Titre** (symbole et nom), **Titulaires**, **Quantité**, **PBR** (le total) et **PBR par unité**. Il se replie, s’exporte et s’imprime de la même façon. Le PBR par unité est ce à quoi se compare le produit de vente par unité.

## Portefeuilles de cryptoactifs {#crypto-wallets}

@index: cryptoactif; cryptomonnaie; bitcoin; BTC; ETH; portefeuille; jalonnement; minage

Un portefeuille de cryptoactifs est un compte créé dans [Comptes](accounts) avec le type Portefeuille de cryptoactifs et le cryptoactif comme devise (BTC, ETH, etc.). Son solde est tenu en cryptoactifs, à huit décimales. Choisissez-le dans la liste des comptes pour voir sa vue.

L’ARC traite les cryptoactifs comme des biens : acheter est une acquisition, et vendre, convertir en un autre cryptoactif, payer des frais en cryptoactifs ou en envoyer à quelqu’un d’autre sont des dispositions qui peuvent donner un gain ou une perte en capital. Des cryptoactifs transférés entre vos propres portefeuilles ne sont pas des dispositions.

### Chiffres du portefeuille {#wallet-figures}

L’en-tête montre le nom du portefeuille, le nom du cryptoactif et « suivi seulement » quand il suit une adresse. Puis :

- **Solde** : les cryptoactifs détenus.
- **Valeur marchande** : les cryptoactifs au dernier cours connu, dans votre devise de base ; « aucun cours » quand aucun cours n’est connu pour ce cryptoactif.
- **Cours au** : la date de ce cours.
- **PBR** : le prix de base rajusté des cryptoactifs. Quand plusieurs de vos portefeuilles détiennent le même cryptoactif pour les mêmes titulaires, ils partagent un groupe et le libellé indique « PBR, mis en commun sur » le nombre de portefeuilles.
- **Gain non réalisé** : la valeur moins le PBR. Avec un groupe de plusieurs portefeuilles, il s’intitule « Gain non réalisé, tous ensemble » et compare le PBR du groupe avec l’ensemble de ces portefeuilles.

Les cours des cryptoactifs sont gardés comme le taux de change de chaque cryptoactif en dollars canadiens. Saisissez-les à la main, ou activez le téléchargement facultatif des cours des cryptoactifs, dans [Taux et cours](rates).

Sous les boutons, les opérations du portefeuille sont énumérées des plus récentes aux plus anciennes : la date, la note ou le bénéficiaire, l’autre compte pour un virement, le montant dans l’autre devise s’il y en a un, et le montant en cryptoactifs.

### Boutons du portefeuille {#wallet-buttons}

- **Acheter**, **Vendre**, **Convertir**, **Transférer à mon portefeuille**, **Frais de réseau** et **Récompense ou revenu** ouvrent la fenêtre d’inscription décrite ci-dessous.
- **Détails du portefeuille** : le coût des cryptoactifs d’ouverture et l’adresse suivie. Voir [Détails du portefeuille](investments#wallet-details).
- **Synchroniser** : affiché pour un portefeuille Bitcoin en suivi seulement. Voir [Synchronisation en suivi seulement](investments#watch-only-sync).

### Inscrire une opération sur cryptoactifs {#wallet-dialog}

Le titre de la fenêtre est l’action et le nom du portefeuille ; une phrase en dessous explique l’effet fiscal. Les champs :

- **Payé depuis** (Acheter), **Versé dans** (Vendre) : le compte bancaire, d’encaisse ou de crédit d’où l’argent est venu ou où il est allé. S’il n’y en a aucun, la fenêtre demande d’ajouter d’abord un compte bancaire ou un compte d’encaisse de plateforme.
- **Dans le portefeuille** (Convertir) : un autre de vos portefeuilles, qui détient un cryptoactif différent.
- **Vers le portefeuille** (Transférer à mon portefeuille) : un autre de vos portefeuilles qui détient le même cryptoactif. S’il n’y en a aucun, la fenêtre demande de l’ajouter d’abord dans Comptes.
- **Date** : la date de l’opération, aujourd’hui par défaut.
- Le montant en cryptoactifs, dont le libellé dépend de l’action : **Cryptoactifs reçus** (Acheter, Récompense ou revenu), **Cryptoactifs vendus** (Vendre), **Cryptoactifs cédés** (Convertir), **Cryptoactifs arrivés** (Transférer à mon portefeuille), **Frais** (Frais de réseau). Obligatoire, plus que zéro.
- **Payé, frais compris** (Acheter) : l’argent sorti du compte, frais de la plateforme compris. C’est le coût des cryptoactifs.
- **Reçu, après les frais** (Vendre) : l’argent arrivé. L’écart avec le PBR des cryptoactifs est le gain ou la perte en capital.
- **Reçu** (Convertir) : les cryptoactifs de l’autre sorte reçus. Les cryptoactifs cédés sont réputés vendus à la valeur de ceux reçus.
- **Frais de réseau** (Transférer à mon portefeuille) : les frais payés en cryptoactifs pour le transfert, s’il y a lieu. Ils sont inscrits sur une ligne à part comme une petite disposition.
- **Genre** (Récompense ou revenu) : Jalonnement, Minage, Intérêts, Récompense ou Distribution gratuite. Les récompenses sont un revenu à leur valeur à la réception, qui devient aussi leur coût. Elles vont dans la catégorie Revenus de cryptoactifs.
- **Note** : un texte libre ; sans note, la ligne reçoit une description standard.

Acheter et Vendre s’inscrivent comme des virements entre le compte d’argent et le portefeuille, Convertir et Transférer comme des virements entre deux portefeuilles, et les frais de réseau et les récompenses comme des lignes du portefeuille (les frais vont dans la catégorie Frais de réseau et d’opérations sur cryptoactifs). Cliquez sur **Enregistrer** ou sur **Annuler**. Pour modifier ou supprimer une de ces opérations plus tard, passez par le registre du portefeuille dans [Comptes](accounts).

### Détails du portefeuille {#wallet-details}

- **Coût des cryptoactifs d’ouverture** : affiché quand le portefeuille a été créé avec un solde d’ouverture en cryptoactifs. Saisissez ce que ces cryptoactifs vous ont coûté, dans votre devise de base, pour que leur gain puisse être calculé. D’ici là, la vue du PBR signale que le coût d’ouverture n’est pas saisi et les compte à un coût nul.
- **Adresse ou clé publique étendue** : pour les portefeuilles Bitcoin seulement. Une adresse Bitcoin, ou la clé publique étendue (xpub, ypub ou zpub) que votre application de portefeuille affiche pour le compte. Elle met le portefeuille en suivi seulement. Pour les autres cryptoactifs, la fenêtre indique que le suivi seulement est offert pour les portefeuilles Bitcoin.

> Important : Ne saisissez jamais une clé privée ni une phrase de récupération. L’application refuse d’emblée une clé privée, et rejette tout ce qui n’est pas une adresse ou une clé publique valide.

### Synchronisation en suivi seulement {#watch-only-sync}

@index: xpub; mempool.space; suivi seulement

Quand un portefeuille Bitcoin a une adresse ou une clé publique étendue, **Synchroniser** demande à mempool.space, un explorateur de blocs public, les opérations confirmées de ces adresses (pour une clé publique étendue, chaque adresse utilisée jusqu’à 20 adresses inutilisées de suite). Les nouvelles opérations sont ajoutées au portefeuille, avec les frais de réseau de chacune sur une ligne à part. Une ligne indique ensuite combien de nouvelles opérations ont été trouvées sur combien d’adresses, ou pourquoi la synchronisation a échoué.

L’explorateur de blocs apprend les adresses demandées ; rien d’autre sur vous ou votre ménage n’est envoyé.

### Lier les transferts {#link-transfers}

Quand des cryptoactifs ont quitté un portefeuille sans être liés à un autre de vos portefeuilles, une ligne en rouge indique combien de ces envois il y a : ils comptent comme des dispositions à leur valeur. S’ils sont allés à l’un de vos propres portefeuilles, cliquez sur **Lier les transferts**. L’application cherche une réception correspondante du même cryptoactif dans un autre de vos portefeuilles dans les trois jours, avec un écart d’au plus 1 % (les frais de réseau), et inscrit chaque paire comme un seul transfert. Une ligne indique ensuite combien d’envois ont été liés.

### Importer d’une plateforme {#exchange-import}

@index: Kraken; Coinbase; Shakepay; Newton; historique de plateforme

Cliquez sur **Importer d’une plateforme…** au bas de la liste des comptes et choisissez le fichier CSV d’historique téléchargé de Kraken, Coinbase, Shakepay ou Newton. Un autre fichier donne le message qu’il ne s’agit pas d’un de ces historiques.

La fenêtre montre le nom de la plateforme et le nombre d’opérations, puis un choix par devise trouvée :

- **Encaisse de la plateforme en** (chaque devise, comme CAD) : le compte qui détient l’argent gardé à la plateforme. Choisissez un de vos comptes dans cette devise, ou laissez « Nouveau compte : » pour créer un compte d’encaisse nommé d’après la plateforme et la devise.
- **Portefeuille pour** (chaque cryptoactif) : le portefeuille de ce cryptoactif. Choisissez un de vos portefeuilles pour ce cryptoactif, ou laissez « Nouveau compte : » pour en créer un.
- **Enregistrer dans** : affiché quand vous pouvez modifier plus d’un groupe de comptes : où les nouveaux comptes sont créés.
- **Titulaires des comptes créés** : affiché quand un nouveau compte sera créé : une case par membre du ménage. Les portefeuilles et comptes d’encaisse de la plateforme créés appartiennent aux personnes cochées ; le membre du ménage lié à votre utilisateur est coché au départ. Sans personne de coché, ils appartiennent au ménage. Les titulaires décident pour qui comptent les gains en capital des cryptoactifs ; vous pouvez les changer plus tard sous [Comptes](accounts).

Cliquez sur **Importer** ou sur **Annuler**. Importer le même fichier de nouveau n’ajoute rien en double. Ensuite, les envois arrivés dans un autre de vos portefeuilles sont liés automatiquement. La fenêtre de résultat montre les opérations ajoutées, les portefeuilles créés, les envois liés et les remarques.

## Métaux précieux {#precious-metals}

@index: or; argent; platine; palladium; lingots; pièces; Feuille d’érable

Un compte de métaux précieux est un compte créé dans [Comptes](accounts) avec le type Métaux précieux. Il énumère vos pièces, lingots et rondelles, évalués d’après le cours au comptant de chaque métal. Choisissez-le dans la liste des comptes pour l’afficher.

### Chiffres des métaux {#metals-figures}

- **Valeur marchande** : chaque article détenu au cours au comptant, rajusté selon sa prime ; un article sans cours au comptant compte à son coût.
- **Valeur comptable** : ce que les articles détenus ont coûté, primes comprises.
- **Gain non réalisé** : la valeur marchande moins la valeur comptable.
- Un chiffre par métal détenu : les onces de métal pur, par exemple « 12,5 oz pur ».

Si un métal n’a pas encore de cours au comptant, une ligne en rouge l’indique. Les cours au comptant se saisissent, ou se téléchargent si vous l’activez, dans [Taux et cours](rates), en dollars canadiens par once troy de métal pur.

Le tableau énumère chaque article : **Article** (quantité × description, avec le métal, le poids et l’unité, la pureté, l’endroit où il est gardé et « assuré »), **Métal pur** (en onces), **Valeur marchande**, **Valeur comptable** et **Gain**. Les articles vendus sont énumérés en dessous sous **Vendus**, avec leur date de vente et leur produit. Cliquez sur une ligne pour la modifier. Cliquez sur **Ajouter des pièces ou lingots** pour en ajouter.

### Ajouter ou modifier des pièces ou lingots {#metal-item-dialog}

- **Métal** : Or, Argent, Platine ou Palladium. Il choisit le cours au comptant utilisé.
- **Forme** : Pièce, Lingot, Rondelle, Bijou ou Autre.
- **Description** : par exemple Feuille d’érable 1 oz, lingot PAMP 100 g. Obligatoire.
- **Quantité** : le nombre de pièces identiques, en nombre entier. 1 par défaut.
- **Poids d’une unité** : le poids d’une pièce. 1 par défaut.
- **Unité** : oz troy, g ou kg. Une once troy vaut 31,1035 grammes.
- **Pureté** : la fraction de métal pur, plus que 0 et au plus 1. 0,9999 pour 9999 de fin (par défaut), 0,999 pour 999, 0,925 pour l’argent sterling. Quantité × poids × pureté donne le métal pur évalué.
- **Acheté le** : la date d’achat, aujourd’hui par défaut. L’article compte dans la valeur du compte à partir de cette date.
- **Payé au total, prime comprise** : le coût total de la ligne, dans la devise du compte. C’est la valeur comptable, et le coût utilisé pour le gain en capital à la vente.
- **Payé depuis** : le compte d’où le coût a été payé : un compte bancaire, d’encaisse, de carte de crédit ou autre dans la même devise, ou **Aucun compte** (par défaut). Avec un compte, le coût en sort à la date **Acheté le**, comme une ligne ayant le marchand (ou la description) comme bénéficiaire ; il faut une date d’achat et un montant de plus de zéro. Changer plus tard le coût, la date ou le compte déplace cette ligne avec eux.
- **Prime ou escompte (%)** : combien au-dessus (+) ou au-dessous (-) de la valeur au comptant les pièces valent. Une pièce qui se vend 5 % au-dessus de la valeur du métal : 5 ; un rachat par le marchand à 2 % sous le cours : -2. Il rajuste la valeur marchande.
- **Marchand** : où vous les avez achetées.
- **Numéros de série** : des lingots ou des certificats.
- **Gardé dans** : Coffre-fort à la maison, Coffret de sûreté, Chambre forte, Gardé par le marchand ou Ailleurs.
- **Endroit précis** : par exemple la succursale bancaire et le numéro du coffret.
- **Assuré** : cochez si les articles sont assurés. **Assurance (assureur, police, couverture)** apparaît alors pour les détails.
- **Notes** : un texte libre.

Quand vous modifiez un article existant, la fenêtre montre aussi les champs de vente, les documents et **Supprimer** (voir ci-dessous). Cliquez sur **Enregistrer** (offert dès qu’il y a une description) ou sur **Annuler**.

Les lignes inscrites dans les comptes choisis sont des déplacements à l’intérieur de votre propre argent, comme l’achat d’un titre : les rapports et les budgets les excluent, et elles ne changent qu’à partir de cette fenêtre. Avec **Aucun compte**, rien ne bouge : inscrivez le paiement vous-même si vous voulez qu’il figure dans les livres. Si une ligne est déjà rapprochée, l’application demande « Modifier une opération rapprochée? » avant de la changer.

### Vendre des pièces ou lingots {#metal-sale}

Ouvrez l’article et remplissez :

- **Vendu le** : la date de la vente. À partir de cette date, l’article sort des avoirs et est énuméré sous Vendus.
- **Produit** : ce que vous avez reçu. Avec une date et un produit, le gain ou la perte en capital (le produit moins le coût de l’article) paraît dans [Gains en capital et PBR](investments#capital-gains-acb).
- **Vente déposée dans** : le compte où le produit a été déposé, ou **Aucun compte** (par défaut). Avec un compte, le produit y arrive à la date de la vente ; il faut une date de vente et un produit.

Pour ne vendre qu’une partie d’une ligne, réduisez d’abord sa quantité et son coût, et ajoutez la partie vendue comme un article distinct. Selon les règles fiscales, des lingots identiques achetés à des moments différents sont mis en commun ; inscrire chaque achat comme un article distinct et le vendre en entier garde des chiffres proches. Avec **Aucun compte** dans Vente déposée dans, inscrire une vente ne déplace aucun argent.

### Certificats et photos {#metal-documents}

Pour un article existant, **Certificats et photos** énumère les documents qui y sont liés. **Joindre un certificat ou une photo…** permet de choisir un fichier (un PDF ou une image) ; il est rangé dans le coffre à documents comme classé et lié à l’article. **Retirer** détache un document de l’article sans le supprimer du coffre. **Supprimer**, au bas, retire l’article lui-même aussitôt, sans confirmation, avec les lignes de son achat et de sa vente dans les comptes choisis (une ligne rapprochée demande d’abord).

## Rapports de placement {#investment-reports}

@index: rendement; taux de rendement; répartition de l’actif; rééquilibrage; T5; T3; RL-3; RL-16; revenus de placement

Plusieurs rapports dans [Rapports](reports) utilisent ce que vous saisissez ici :

- Portefeuille de placements : la valeur au fil du temps, le gain du début à la fin (cotisations, retraits, revenus, frais et variation du marché), le rendement pondéré en fonction du temps et le taux de rendement personnel par compte, les titres détenus, et la répartition de l’actif par catégorie d’actif, région, devise ou compte, avec des cibles et des suggestions de rééquilibrage. La catégorie d’actif, la région et la composition de chaque titre décident où va sa valeur.
- Revenus de placement et gains en capital : par personne et par année d’imposition, les feuillets T5 et T3 (avec les RL-3 et RL-16 au Québec) estimés à partir de vos opérations de revenu jusqu’à ce que vous saisissiez les vrais feuillets, et les gains en capital pour l’annexe 3.
- Régimes enregistrés : droits de cotisation, retraits et subventions (voir [Régimes enregistrés](plans)).
- La valeur nette inclut chaque compte de placement à sa valeur marchande.
