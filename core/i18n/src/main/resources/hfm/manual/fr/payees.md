# Bénéficiaires

Les bénéficiaires sont les personnes et les entreprises à qui l'argent va ou de qui il vient : l'épicerie, Hydro-Québec, votre employeur, le propriétaire du logement. Une liste de bénéficiaires bien tenue rend les opérations faciles à lire, à chercher et à analyser, et permet à l'application de suggérer la bonne catégorie. L'écran se trouve dans le groupe **Réglages** du menu, sous **Bénéficiaires**.

## Comment fonctionnent les bénéficiaires {#about-payees}

@index: marchand; commerçant; magasin; fournisseur; payeur

- Chaque opération peut avoir un bénéficiaire. Quand vous tapez dans une opération un nom de bénéficiaire qui ne correspond à aucun bénéficiaire existant, un nouveau bénéficiaire est créé avec ce nom. La plupart des bénéficiaires sont donc créés pour vous ; cet écran sert à les mettre en ordre.
- Quand un relevé est importé, les banques impriment souvent les noms sous une forme courte ou étrange, comme AMZN MKTP CA*2X4 ou COSTCO WHOLESALE W512. L'application cherche un bénéficiaire dont l'alias se trouve dans ce texte ; si aucun ne correspond, elle utilise une version plus propre du texte de la banque (numéros de magasin retirés, majuscules adoucies : « Costco Wholesale »). Le texte d'origine de la banque est gardé avec l'opération.
- Un bénéficiaire peut avoir une catégorie par défaut, utilisée quand l'application n'a rien de mieux sur quoi se fonder.

## L'écran Bénéficiaires {#screen}

La gauche liste les bénéficiaires en ordre alphabétique, avec **Ajouter** au-dessus de la liste. Les bénéficiaires archivés sont en gris. La droite montre le formulaire du bénéficiaire choisi, ou d'un nouveau. Quand rien n'est choisi, elle indique « Choisissez un bénéficiaire pour le modifier, ou ajoutez-en un. »

## Ajouter un bénéficiaire {#add-payee}

1. Cliquez sur **Ajouter**.
2. Tapez le **Nom du bénéficiaire**.
3. Choisissez une **Catégorie par défaut** si vous en voulez une.
4. Cliquez sur **Enregistrer**.

Les alias peuvent être ajoutés une fois le bénéficiaire enregistré.

### Le formulaire du bénéficiaire {#payee-fields}

- **Nom du bénéficiaire** : le nom propre affiché sur les opérations, comme Amazon ou Hydro-Québec. Obligatoire. Taper ce nom dans une opération, en majuscules ou non, choisit ce bénéficiaire.
- **Catégorie par défaut** : (aucun), ou une catégorie de l'arbre. Les catégories archivées ne sont pas offertes. Elle sert :
  - Quand vous entrez une opération pour ce bénéficiaire et qu'il n'a jamais été utilisé dans ce groupe de comptes : la catégorie est remplie pour vous. Une fois que le bénéficiaire a des opérations, le registre remplit plutôt le montant et la catégorie de la plus récente.
  - Quand une ligne de relevé est importée et qu'aucune [règle de catégorie](rules) ne correspond : la catégorie par défaut du bénéficiaire vient ensuite, avant la catégorie de la dernière opération du bénéficiaire.
  - Quand une opération est créée à partir d'un reçu ou d'une facture dans [Documents](documents).
  - Sur le téléphone, qui reçoit chaque bénéficiaire avec sa catégorie par défaut.
- **Archivé (masqué des listes)** : affiché pour un bénéficiaire déjà enregistré. Voir [Archiver un bénéficiaire](payees#archive-payee).
- **Alias** : affiché pour un bénéficiaire déjà enregistré : les alias du bénéficiaire en ordre alphabétique, ou « Aucun alias pour l'instant. ». En dessous, le champ **Alias** et **Ajouter l'alias** en ajoutent un. Voir [Alias](payees#aliases).
- **Enregistrer** : enregistre le nom, la catégorie par défaut et la case Archivé. Rien n'est enregistré avant que vous cliquiez.

## Alias {#aliases}

@index: alias; nom sur le relevé; description bancaire; correspondance des bénéficiaires; renommer les bénéficiaires importés

Un alias est un bout de texte qui identifie ce bénéficiaire sur les relevés, comme AMZN MKTP pour Amazon ou HYDRO-QUE pour Hydro-Québec.

1. Choisissez le bénéficiaire dans la liste.
2. Tapez le texte dans **Alias**.
3. Cliquez sur **Ajouter l'alias**. Le champ se vide quand l'alias est enregistré, et l'alias s'ajoute à la liste au-dessus.

Comment les alias sont reconnus :

- Un alias correspond quand le texte de la banque le contient n'importe où, sans tenir compte des majuscules. AMZN correspond à « AMZN MKTP CA*2X4 » et à « amzn.com/bill ».
- Quand plusieurs alias correspondent, le plus long l'emporte, de sorte qu'un alias précis passe avant un alias général.
- Quand aucun alias ne correspond, un bénéficiaire dont le nom est exactement le texte, sans tenir compte des majuscules, est utilisé.
- Un bénéficiaire peut avoir autant d'alias qu'il faut. Ajoutez un alias pour chaque façon dont le nom apparaît sur vos relevés.

Les alias s'appliquent aux lignes de relevé importées à partir de ce moment et aux noms que vous tapez. Ils ne renomment pas les opérations déjà inscrites.

> Remarque : Un alias ne peut pas être retiré une fois ajouté. Choisissez vos alias avec soin : un alias trop court, comme « CA », correspondrait à beaucoup trop de choses.

Les administrateurs et les membres peuvent ajouter des bénéficiaires, les modifier et ajouter des alias. Les lecteurs voient la liste, les formulaires et les alias en gris, avec la remarque « À titre de lecteur, vous pouvez voir cette liste, mais pas la modifier. »

> Conseil : Les alias donnent le bon nom ; les [Règles de catégorie](rules) donnent la bonne catégorie. Quand un même magasin vend des choses très différentes, utilisez une règle avec des limites de montant plutôt qu'une catégorie par défaut.

## Modifier un bénéficiaire {#change-payee}

1. Cliquez sur le bénéficiaire dans la liste.
2. Changez le **Nom du bénéficiaire** ou la **Catégorie par défaut**.
3. Cliquez sur **Enregistrer**.

Un nouveau nom s'affiche sur chaque opération classée sous ce bénéficiaire, y compris les anciennes. Il n'y a aucun moyen de fusionner deux bénéficiaires : donnez à celui que vous gardez les alias de l'autre, et archivez l'autre.

## Archiver un bénéficiaire {#archive-payee}

@index: supprimer un bénéficiaire; masquer un bénéficiaire; retirer un bénéficiaire

Il n'y a pas de bouton pour supprimer un bénéficiaire. Pour ne plus le voir :

1. Cliquez dessus dans la liste.
2. Cochez **Archivé (masqué des listes)**.
3. Cliquez sur **Enregistrer**.

Les opérations passées gardent leur bénéficiaire. Un bénéficiaire archivé n'est plus envoyé au téléphone. Ses alias fonctionnent encore quand des relevés sont importés, de sorte qu'un bénéficiaire archivé peut encore être choisi par une importation.

## Où les bénéficiaires servent {#where-used}

- Le registre de chaque compte : le bénéficiaire de chaque opération, avec le montant et la catégorie de sa dernière opération suggérés. Voir [Comptes](accounts).
- Les importations de relevés : les alias donnent le nom propre, et la catégorie par défaut donne la catégorie quand aucune règle ne correspond.
- [Documents](documents) : le marchand lu sur un reçu devient le bénéficiaire de la nouvelle opération.
- [Rapports](reports) et recherche : les opérations peuvent être trouvées et regroupées par bénéficiaire.
- Le téléphone : jusqu'à 400 bénéficiaires, avec leur catégorie par défaut, parmi lesquels choisir en saisissant un reçu. Voir [Téléphones](phones).
