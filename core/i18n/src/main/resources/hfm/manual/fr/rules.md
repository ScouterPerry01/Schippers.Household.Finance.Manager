# Règles de catégorie

Les règles de catégorie donnent automatiquement leur catégorie aux opérations importées. Une règle dit : quand la description d'une ligne importée contient ce texte (et, au besoin, que le montant est dans cette fourchette), utiliser cette catégorie. L'écran se trouve dans le groupe **Réglages** du menu, sous **Règles de catégorie**.

## Comment les règles s'appliquent {#how-rules-apply}

@index: catégorisation automatique; catégoriser automatiquement; règles d'importation; catégoriser les importations; correspondance

Les règles servent quand un relevé est importé dans un compte, par exemple un fichier OFX, QFX ou CSV de votre banque, ou un relevé lu par l'IA. Voir [Comptes](accounts).

Pour chaque ligne du relevé :

1. Si la ligne est déjà inscrite (même référence bancaire), elle est ignorée comme doublon.
2. Si elle correspond à une opération déjà entrée, par exemple une opération tapée par vous ou saisie avec le téléphone, les deux sont liées (ou, quand la correspondance n'est pas certaine, proposées pour que vous confirmiez), et l'opération garde sa catégorie.
3. Sinon, une nouvelle opération est créée, et sa catégorie est choisie dans cet ordre :
  - la première règle de catégorie qui correspond, en essayant les règles du haut de la liste vers le bas ;
  - si aucune règle ne correspond, la catégorie par défaut du bénéficiaire (voir [Bénéficiaires](payees)) ;
  - si le bénéficiaire n'en a pas, la catégorie de la plus récente opération du bénéficiaire, quand cette opération avait une seule catégorie ;
  - sinon, la ligne arrive sans catégorie, à remplir par vous.

Le même ordre sert quand vous choisissez d'ajouter une nouvelle opération pour une ligne de relevé pendant une conciliation.

Une règle correspond quand :

- La description de la ligne, telle que la banque l'a imprimée, contient le texte de la règle n'importe où, sans tenir compte des majuscules. « HYDRO » correspond à « HYDRO-QUEBEC PAIEMENT » et à « Hydro One ».
- Et, si la règle a des limites de montant, la grandeur du montant est dans ces limites. Les limites s'appliquent à la grandeur du montant, qu'il s'agisse d'argent qui entre ou qui sort : une limite de 100 correspond à un paiement de 100 comme à un dépôt de 100.

Les règles ne changent jamais les opérations déjà inscrites. Ajouter ou supprimer une règle ne touche que les lignes importées par la suite. Vous pouvez toujours changer une catégorie après coup dans le registre.

> Remarque : Les règles s'appliquent seulement aux relevés importés. Quand vous tapez vous-même une opération, le registre suggère plutôt le dernier montant et la dernière catégorie du bénéficiaire.

## L'écran Règles de catégorie {#screen}

La gauche liste les règles, avec **Ajouter** au-dessus de la liste. Chaque règle se lit comme son texte entre guillemets, une flèche et sa catégorie, comme « HYDRO-QUEBEC » → Électricité. La liste est dans l'ordre où les règles sont essayées, c'est-à-dire l'ordre dans lequel elles ont été ajoutées.

La droite commence par le rappel « Quand la description d'une opération importée contient le texte, elle reçoit la catégorie. Les règles sont essayées de haut en bas. » En dessous se trouve la règle choisie, le formulaire d'une nouvelle règle, ou « Choisissez une règle, ou ajoutez-en une. »

## Ajouter une règle {#add-rule}

1. Cliquez sur **Ajouter**.
2. Tapez le texte dans **La description contient**.
3. Choisissez la **Catégorie**.
4. Au besoin, remplissez **Montant d'au moins** et **Montant d'au plus**.
5. Cliquez sur **Enregistrer**. Le bouton devient disponible une fois le texte et la catégorie remplis.

### Le formulaire de règle {#rule-fields}

- **La description contient** : le texte à chercher dans la description de la banque, par exemple HYDRO-QUEBEC ou PAIE. Obligatoire. Les majuscules n'importent pas. Utilisez un bout de texte qui est toujours là et qui est assez précis : le nom sans numéros de magasin, villes ou dates.
- **Catégorie** : la catégorie à donner. Obligatoire. Les catégories archivées ne sont pas offertes.
- **Montant d'au moins** : facultatif. La règle correspond seulement quand la grandeur du montant est au moins de cette somme. Tapé dans la devise de base du ménage (le dollar canadien dans la plupart des ménages).
- **Montant d'au plus** : facultatif. La règle correspond seulement quand la grandeur du montant est au plus de cette somme. Quand les deux sont remplis, le minimum ne peut pas dépasser le maximum (« Le montant minimum est plus grand que le maximum. »).
- **Enregistrer** : ajoute la règle au bas de la liste.

Une règle avec des limites de montant ne correspond qu'aux lignes dans la même devise que ses limites. Une règle sans limites correspond aux lignes de toutes les devises.

> Conseil : Utilisez des limites de montant pour distinguer des lignes qui ont la même description. Par exemple, un dépôt de paie d'au moins 1 000 est du Salaire, alors qu'un dépôt plus petit du même employeur est un remboursement de dépenses.

## Voir ou supprimer une règle {#rule-details}

Cliquez sur une règle dans la liste. La droite montre son résumé et, quand elle en a, ses limites (« Montant d'au moins » et « Montant d'au plus » avec leurs montants).

- **Supprimer** : retire la règle tout de suite, sans demander. Cela ne peut pas être annulé, mais vous pouvez ajouter la même règle de nouveau. Les opérations déjà importées gardent leurs catégories.

Les règles ne peuvent pas être modifiées ni déplacées. Pour en changer une, supprimez-la et ajoutez-la de nouveau ; elle va alors au bas de la liste.

## L'ordre compte {#order}

@index: priorité des règles; première correspondance

La première règle qui correspond l'emporte. Comme les nouvelles règles vont au bas de la liste, ajoutez les règles précises avant les règles générales. Par exemple, ajoutez « COSTCO GAS » → Carburant avant « COSTCO » → Épicerie ; si « COSTCO » avait été ajoutée en premier, elle attraperait aussi les lignes de la station-service.

Si une règle générale est déjà là, supprimez-la et ajoutez-la de nouveau après la règle précise.

## Règles et bénéficiaires {#rules-and-payees}

- Les alias de bénéficiaire de l'écran [Bénéficiaires](payees) nettoient le nom d'une ligne importée. Les règles choisissent sa catégorie.
- Une règle est essayée avant la catégorie par défaut du bénéficiaire, de sorte qu'une règle peut remplacer la catégorie par défaut pour certaines lignes, comme les gros achats dans un magasin dont la catégorie par défaut est Épicerie.
- Chaque règle ajoutée, et chaque règle supprimée, est inscrite dans le journal d'activité de l'écran [Utilisateurs](users).
