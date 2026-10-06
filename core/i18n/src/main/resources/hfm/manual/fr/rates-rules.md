# Taux et règles

Taux et règles liste chaque taux, plafond, seuil et délai que l'application applique dans ses calculs : plafonds des régimes enregistrés, taxes de vente, paliers d'impôt sur le revenu, chiffres médicaux et de placement, délais des rappels, réglages de sécurité, jours fériés et plus encore. Chaque valeur a sa date d'entrée en vigueur et, quand elle varie au Canada, la province ou le territoire auquel elle s'applique. L'écran se trouve dans le groupe **Réglages** du menu, sous **Taux et règles**, juste après **Taux et cours**.

## À quoi sert cet écran {#purpose}

@index: taux d'imposition; plafonds; seuils; délais; chiffres officiels; taux gouvernementaux; plafond du CELI; taux de taxe de vente; TPS; TVH; TVP; TVQ; paliers d'imposition; taux et règles

Les chiffres que fixent les gouvernements changent chaque année ou presque : un nouveau plafond du CELI chaque janvier, un nouveau palier d'impôt après un budget, un taux de taxe de vente qui monte ou qui baisse. L'application est livrée avec les valeurs officielles, chacune avec sa date et sa source, et utilise celle qui est en vigueur à la date de ce qu'elle calcule.

Quand un nouveau chiffre est annoncé avant la mise à jour de l'application, un administrateur peut l'entrer ici, avec sa date d'entrée en vigueur. À partir de cette date, l'application l'utilise partout où la règle s'applique. Rien d'autre n'est à changer : les calculs, rapports et rappels qui utilisent la règle prennent la nouvelle valeur d'eux-mêmes.

> Remarque : Les valeurs intégrées à l'application ne sont jamais modifiées ni retirées. Une valeur que vous ajoutez prend leur place à partir de sa date, et les valeurs intégrées restent dans l'historique.

## Comment la valeur en vigueur est choisie {#how-values-apply}

@index: date d'entrée en vigueur; date d'un taux; province ou territoire; par province; valeur en vigueur; quelle valeur s'applique

Pour chaque calcul, l'application regarde la date de ce qu'elle calcule (une opération, une année d'imposition, une cotisation, un rappel) et, pour une règle qui varie selon la province, la province ou le territoire qui s'applique (en général celui du ménage, ou celui de la personne quand il est indiqué dans [Membres du ménage](members)). Ensuite :

1. Si la province ou le territoire a ses propres valeurs, la plus récente datée de ce jour ou avant s'applique.
2. Sinon, la plus récente valeur pour partout (ou, pour un chiffre fédéral, la seule) datée de ce jour ou avant s'applique.
3. Quand une valeur intégrée et une valeur du ménage ont la même date, la valeur du ménage s'applique.
4. Si aucune valeur n'est datée de ce jour ou avant, la règle n'a pas encore de valeur, et l'écran indique « Aucune valeur pour l'instant ».

Quelques conséquences :

- Une valeur s'applique à tout ce qui est daté de sa date ou après, jusqu'à une valeur plus récente. Une valeur datée dans l'avenir ne s'applique qu'une fois cette date arrivée ; d'ici là, la valeur actuelle reste en vigueur.
- Une valeur datée dans le passé s'applique aussi aux dates passées : les calculs et rapports de ces dates l'utilisent dès lors.
- La valeur propre d'une province passe toujours avant la valeur pour partout, quelles que soient leurs dates. Pour changer un chiffre dans une province qui a sa propre valeur, ajoutez la nouvelle valeur pour cette province.

## La liste des règles {#rule-list}

@index: domaines; trouver une règle; clé d'une règle

La gauche de l'écran liste les règles, regroupées par domaine, comme Régimes enregistrés, Taxes de vente, Impôt sur le revenu, Rappels et délais, Seuils, Sécurité et Jours fériés. La liste s'allonge à mesure que de nouvelles règles sont ajoutées à l'application.

- **Filtrer par nom ou par clé** : tapez une partie du nom d'une règle ou de sa clé pour raccourcir la liste. Les majuscules et les accents ne comptent pas : « quebec » trouve « Québec ». Quand rien ne correspond, la liste indique « Aucune règle ne correspond. » Videz la case pour revoir toutes les règles.

Chaque règle montre son nom et, en dessous, sa clé, comme tfsa.limit : un code court qui désigne la règle de la même façon en français et en anglais. Cliquez sur une règle pour l'afficher à droite. Tant que vous n'en choisissez pas, la droite explique l'écran et indique « Choisissez une règle à gauche pour voir ses valeurs. »

## Le détail d'une règle {#rule-details}

En haut à droite :

- Le nom de la règle, et « Clé : » avec sa clé.
- Ce qu'est la règle et où l'application l'utilise.
- Son type (voir [Types de valeurs](rates-rules#types)) et soit « Peut varier selon la province ou le territoire », soit « Le même partout au Canada ».

### Types de valeurs {#types}

@index: pourcentage; fraction; paliers; mois et jour; oui ou non; liste de nombres

- Taux, en pourcentage : comme 5 % ou 9,975 %. Affiché en pourcentage dans votre langue (9,975 % en français, 9.975% en anglais). Il ne peut pas dépasser 100 %.
- Montant en dollars : comme 7 000 $. Affiché avec le signe de dollar, et les cents s'il y en a.
- Nombre : un nombre simple, qui peut avoir des décimales, comme un âge ou un facteur.
- Nombre de jours : un nombre entier, comme 30 jours.
- Paliers : un taux sur le revenu au-dessus de chaque seuil, comme les paliers d'impôt sur le revenu. Affichés comme « 15 % au-dessus de 0 $ · 20,5 % au-dessus de 58 523 $ » : chaque taux s'applique à la partie du revenu au-dessus de son seuil, jusqu'au seuil suivant.
- Jour de l'année : un mois et un jour, comme le 15 novembre.
- Oui ou non : si quelque chose s'applique.
- Liste de nombres : plusieurs nombres lus dans l'ordre que donne la description de la règle.

## En vigueur aujourd'hui {#in-effect}

@index: taux actuel; valeur d'aujourd'hui

Sous **En vigueur aujourd'hui**, l'écran montre la valeur qui s'applique aujourd'hui, avec « depuis le » et la date de son entrée en vigueur.

Pour une règle qui peut varier selon la province ou le territoire, il y a une ligne pour **Partout** (la valeur utilisée là où une province n'a pas la sienne), puis une ligne par province et territoire : la vôtre d'abord, marquée « (votre province) », puis les autres par ordre alphabétique. Une ligne dont la valeur vient de la valeur pour partout le dit (« la valeur pour partout »). Votre province est celle du ménage, indiquée dans [Membres du ménage](members).

Pour une règle qui est la même partout, il y a une seule ligne.

## Historique des valeurs {#history}

@index: taux passés; historique des taux; source d'un taux; valeur intégrée; valeur du ménage

Sous **Historique des valeurs**, toutes les valeurs de la règle, de la plus récente à la plus ancienne. Chaque ligne montre :

- La date d'entrée en vigueur de la valeur.
- Pour une règle qui peut varier selon la province : la province ou le territoire, ou Partout.
- La valeur, présentée comme le décrit [Types de valeurs](rates-rules#types).
- Sa provenance : « Intégrée » avec sa source, comme l'Agence du revenu du Canada ou un gouvernement provincial, pour une valeur livrée avec l'application ; ou « Valeur du ménage · ajoutée par » l'utilisateur qui l'a ajoutée, avec la date et l'heure de l'ajout.
- « Note : » et la note entrée avec une valeur du ménage, s'il y en a une.
- **Supprimer** : affiché sur les valeurs du ménage, pour un administrateur. La question est posée d'abord : « Supprimer la valeur du ménage … du …? La valeur d'avant s'applique de nouveau à partir de cette date. » Une fois la valeur supprimée, la valeur d'avant s'applique de nouveau à partir de cette date. Pour annuler, ajoutez la valeur de nouveau. Les valeurs intégrées n'ont pas de bouton Supprimer : pour en changer une, ajoutez une valeur du ménage à la même date.

## Ajouter une valeur {#add-value}

@index: nouveau taux; nouveau plafond; changer un taux; remplacer un taux; entrer un taux

Sous **Ajouter une valeur**, un administrateur entre une nouvelle valeur de la règle. L'écran le rappelle : « Une valeur s'applique à partir de sa date à tout ce qui est daté de ce jour ou après, jusqu'à une valeur plus récente. Les valeurs intégrées ne sont jamais modifiées : elles restent dans l'historique. » Les utilisateurs qui ne sont pas administrateurs voient « Seul un administrateur peut ajouter ou supprimer des valeurs. Vous pouvez les consulter ici. » au lieu du formulaire.

- **En vigueur à partir du** : la date d'entrée en vigueur de la valeur, au format AAAA-MM-JJ. Aujourd'hui par défaut. Tapez + ou - pour avancer ou reculer d'un jour. Pour un chiffre annuel, entrez le 1er janvier de son année ; pour un changement de taxe, la date d'entrée en vigueur annoncée par le gouvernement.
- **Province ou territoire** : affiché seulement pour une règle qui peut varier selon la province. Choisissez une province ou un territoire, ou **Toutes les provinces et tous les territoires** (par défaut) pour la valeur pour partout. Une valeur pour toutes les provinces ne s'applique que là où une province n'a pas sa propre valeur : l'écran liste les provinces et territoires qui gardent la leur (« Les provinces et territoires qui ont leur propre valeur la gardent : »).
- La valeur : un champ qui convient au type de la règle, rempli avec la valeur en vigueur aujourd'hui (dans votre province, pour une règle qui varie selon la province), pour que vous ne changiez que ce qui est nouveau. Voir [L'éditeur de valeur](rates-rules#value-editor).
- **Note** : facultative. D'où vient le chiffre, comme « Budget fédéral, avril 2027 ». Elle est affichée dans l'historique.
- **Ajouter une valeur** : enregistre la valeur. Elle s'applique aussitôt à tous les calculs datés de sa date ou après. « Valeur ajoutée. » le confirme, et la valeur apparaît dans l'historique comme valeur du ménage. Elle est gardée dans le fichier du ménage : elle se trouve donc dans chaque sauvegarde et sur chaque ordinateur qui ouvre le ménage.

Si la valeur ne peut pas être enregistrée, la raison s'affiche sous le formulaire :

- « Cette valeur ne convient pas : vérifiez sa forme. » quand la valeur ne peut pas être lue, ou qu'elle est hors limites : un montant négatif, un taux de plus de 100 %, des paliers dont les seuils ne sont pas en ordre croissant, ou un jour qui n'existe pas.
- « Ce taux est le même partout : il n'a pas de province. » quand une province est donnée pour une règle qui est la même partout.
- « Cette règle n'existe pas. » si l'application ne connaît plus la règle.

### L'éditeur de valeur {#value-editor}

@index: taper un pourcentage; virgule décimale; tableau des paliers

Les nombres se tapent comme vous les écrivez dans votre langue : 9,975 en français, 9.975 en anglais (un point seul fonctionne aussi en français). Les espaces et les séparateurs de milliers, comme 7 500, sont acceptés. Le champ devient rouge avec « Cette valeur ne convient pas : vérifiez sa forme. » tant que ce qui est tapé ne peut pas être utilisé.

- **Taux (%)** : pour un taux, tapez le pourcentage, pas la fraction : 9,975 pour 9,975 %, 13 pour 13 %. Le signe % peut être tapé ou omis. L'application le garde en fraction (0,09975).
- **Montant ($)** : pour un montant, tapez des dollars, comme 7000 ou 2834,50. Un signe $ peut être tapé.
- **Valeur** : pour un nombre simple, tapez le nombre. Pour une règle oui ou non, choisissez Oui ou Non dans la liste.
- **Jours** : un nombre entier de jours, 0 ou plus.
- **Mois et jour (MM-JJ)** : pour un jour de l'année, le mois et le jour en chiffres, comme 11-15 pour le 15 novembre.
- **Valeurs, séparées par ;** : pour une liste, tapez les nombres dans l'ordre que décrit la règle, séparés par des points-virgules, comme 0,0528; 0,0540. Ils sont gardés tels quels, pas en pourcentage.

Pour des paliers, l'éditeur est un petit tableau, avec une ligne par palier :

- **Revenu au-dessus de ($)** : le seuil, en dollars. Celui du premier palier est habituellement 0.
- **Taux (%)** : le taux sur la partie du revenu au-dessus de ce seuil, en pourcentage, comme 20,5.
- **Ajouter un palier** : ajoute une ligne vide.
- **Retirer** : retire cette ligne. La dernière ligne ne peut pas être retirée.

Les lignes peuvent être entrées dans n'importe quel ordre : elles sont triées par seuil à l'enregistrement. Les lignes vides sont laissées de côté. Tous les paliers doivent être entrés, pas seulement celui qui change : la nouvelle valeur remplace l'ensemble des paliers à partir de sa date.

### Ce qui change {#what-changes}

@index: avant et après; aperçu d'un changement

Sous la valeur, une ligne montre ce que change la nouvelle valeur : « Avant le » la date, la valeur en vigueur la veille, et « À partir du » la date, la nouvelle valeur (ou « ? » tant que la valeur ne peut pas être lue). Pour une province, la valeur d'avant est celle de cette province.

Quand une valeur plus récente existe déjà pour la même province (ou pour partout), la ligne ajoute « Jusqu'au » sa date, « quand une valeur plus récente prend le relais » : la nouvelle valeur ne s'applique que jusque-là.

## Qui peut changer les taux et règles {#permissions}

- Administrateurs : ajouter des valeurs et supprimer les valeurs du ménage.
- Membres et lecteurs : voir chaque règle, sa valeur en vigueur et son historique. Le formulaire **Ajouter une valeur** et les boutons **Supprimer** ne sont pas affichés.

Chaque valeur ajoutée ou supprimée est inscrite au journal d'activité, avec qui l'a fait.

## Exemples {#examples}

### Entrer le plafond du CELI de l'an prochain {#example-tfsa}

@index: plafond annuel du CELI; nouveau plafond du CELI; droits de cotisation

Le gouvernement annonce en novembre le plafond annuel du CELI de l'année qui vient. S'il est annoncé avant la mise à jour de l'application :

1. Ouvrez **Taux et règles** et tapez « celi » dans **Filtrer par nom ou par clé**.
2. Cliquez sur **Plafond annuel du CELI** (Régimes enregistrés).
3. Sous **Ajouter une valeur**, réglez **En vigueur à partir du** au 1er janvier de l'année qui vient, comme 2027-01-01.
4. Dans **Montant ($)**, tapez le nouveau plafond, comme 7500.
5. Dans **Note**, indiquez d'où il vient, comme « ARC, annoncé en novembre 2026 ».
6. Vérifiez la ligne en dessous : « Avant le 2027-01-01 : 7 000 $. À partir du 2027-01-01 : 7 500 $. »
7. Cliquez sur **Ajouter une valeur**.

À partir du 1er janvier, les droits de cotisation dans [Régimes enregistrés](plans) comprennent le nouveau plafond. Si l'application intègre plus tard le même chiffre, les deux ont la même date et votre valeur s'applique ; vous pouvez supprimer la vôtre.

### Un nouveau taux de TVH {#example-hst}

@index: taxe de vente harmonisée; changement de TVH; changement de taxe de vente

Supposons qu'une province qui perçoit la TVH annonce que son taux passe de 13 % à 14 % le 1er juillet :

1. Ouvrez **Taux et règles** et trouvez la règle de la TVH sous Taxes de vente.
2. Sous **Ajouter une valeur**, réglez **En vigueur à partir du** à la date du changement, comme 2027-07-01.
3. Dans **Province ou territoire**, choisissez la province. Choisir **Toutes les provinces et tous les territoires** ne changerait pas une province qui a son propre taux.
4. Dans **Taux (%)**, tapez 14.
5. Vérifiez la ligne en dessous, puis cliquez sur **Ajouter une valeur**.

Les achats datés d'avant le 1er juillet gardent l'ancien taux ; ceux datés du 1er juillet ou après utilisent le nouveau.

### Un nouveau palier d'imposition {#example-bracket}

@index: palier d'impôt sur le revenu; changement des paliers d'imposition; budget fédéral

Supposons qu'un budget baisse le taux d'un palier ou en ajoute un à partir du 1er janvier :

1. Ouvrez **Taux et règles** et choisissez la règle des paliers sous Impôt sur le revenu (fédérale, ou celle de votre province).
2. Sous **Ajouter une valeur**, réglez **En vigueur à partir du** au 1er janvier de l'année, comme 2027-01-01.
3. Le tableau commence avec les paliers en vigueur aujourd'hui. Changez le taux ou le seuil qui change, et utilisez **Ajouter un palier** pour un nouveau, avec son **Revenu au-dessus de ($)** et son **Taux (%)**.
4. Vérifiez la ligne en dessous : elle montre les paliers avant et après.
5. Cliquez sur **Ajouter une valeur**.

Le tableau entier est la nouvelle valeur : les paliers laissés tels quels restent les mêmes, et l'application utilise le nouvel ensemble pour chaque année d'imposition à partir de 2027.

### Un chiffre pas encore publié {#example-unpublished}

@index: chiffre non publié; formulaires de 2026; supplément pour personnes handicapées; annexe 6

Certains chiffres ne sont publiés que sur les formulaires d'impôt de l'année, qui paraissent tard dans l'année ou au début de la suivante. D'ici là, l'application garde la dernière valeur publiée, et sa source le dit. Par exemple, le supplément pour personnes handicapées de l'Allocation canadienne pour les travailleurs garde ses valeurs de 2025 au Québec, en Alberta et au Nunavut jusqu'à ce que l'ARC publie l'annexe 6 de 2026. À ce moment :

1. Ouvrez **Taux et règles** et tapez « cwb » dans **Filtrer par nom ou par clé**.
2. Cliquez sur **Supplément pour personnes handicapées de l'Allocation canadienne pour les travailleurs** (Impôt sur le revenu).
3. Sous **Ajouter une valeur**, réglez **En vigueur à partir du** au 1er janvier de l'année, comme 2026-01-01, et choisissez la province ou le territoire, comme l'Alberta.
4. Dans **Valeurs, séparées par ;**, tapez les dix nombres dans l'ordre que donne la description de la règle, tirés des lignes 30 à 38 de l'annexe 6 de cette province, comme 910; 0,26; 0,26; 860; 38583; 51237; 51237; 51237; 0,15; 0,075 (un exemple, pas les chiffres publiés).
5. Dans **Note**, indiquez d'où ils viennent, comme « ARC, annexe 6 pour les résidents de l'Alberta (5009-S6) 2026 ».
6. Cliquez sur **Ajouter une valeur**.

L'estimation de l'impôt sur le revenu de l'écran [Impôts](taxes#estimate-refundable) utilise les nouveaux chiffres pour 2026 et après.

## Chapitres liés {#related}

- [Taux et cours](rates) : les taux de change et les cours du marché, qui sont téléchargés plutôt que fixés par la loi.
- [Utilisateurs](users) : qui est administrateur.
- [Régimes enregistrés](plans), [Impôts](taxes) et [Réclamations médicales](medical) : des écrans qui utilisent ces règles.
