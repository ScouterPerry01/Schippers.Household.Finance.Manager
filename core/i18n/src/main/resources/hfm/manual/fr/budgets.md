# Budgets

Budgets compare ce que vous prévoyez dépenser, ou recevoir, dans chaque catégorie avec ce qui s’est réellement passé. Vous fixez un montant par catégorie, mensuel ou annuel, et l’écran montre d’un coup d’œil les catégories qui respectent le budget et celles qui le dépassent. Budgets se trouve dans le groupe Argent du menu.

## Ce que font les budgets {#overview}
@index: budget; plan de dépenses; budget et réel; dépassement

Un budget est un montant pour une catégorie, comme Épicerie 800 $ par mois ou Assurance habitation 1 400 $ par année. Le montant réel est le total de vos opérations dans cette catégorie (et ses sous-catégories) pour la période. Rien n’est bloqué ni déplacé quand vous dépassez : un budget est un repère, pas une limite.

Les budgets utilisent les catégories de vos opérations ; ils ne valent donc que ce que vaut votre catégorisation. Voir [Catégories](categories).

## L’écran Budgets {#budgets-screen}

En haut :

- **Suggérer d’après les 12 derniers mois** : propose des budgets d’après vos dépenses passées. Voir [Suggérer d’après les 12 derniers mois](budgets#suggest).
- **Ajouter un budget** : fixe un budget sur une catégorie. Voir [Ajouter un budget](budgets#add-budget).

Quand il n’y a encore aucun budget, l’écran indique « Aucun budget pour l’instant. Ajoutez-en un, ou laissez l’application en suggérer d’après vos dépenses. »

### Le mois et l’année {#month-and-year}

- **◀** et **▶** : reculent ou avancent d’un mois (ou d’une année, avec **Année complète** cochée). Le mois ou l’année affiché se trouve entre les deux.
- **Année complète** : montre toute l’année civile au lieu d’un mois. Les budgets mensuels comptent alors douze fois, et les budgets annuels une fois. Les montants reportés ne sont pas montrés dans cette vue.

### Les totaux {#totals}

Les dépenses et les revenus sont montrés séparément, les dépenses d’abord. Au-dessus de chaque partie, trois chiffres additionnent ses lignes :

- **Dépensé** (dépenses) ou **Reçu** (revenus) : le total réel.
- **Budgété** : le total des budgets, y compris les montants reportés.
- **Reste** : le budgété moins le réel. Un montant négatif signifie un dépassement.

### Les barres de budget {#budget-bars}

Chaque catégorie budgétée a une barre. La longueur de la barre est le montant réel ; un repère montre le budget. Le texte indique « … sur … », le réel sur le budgété, et une note :

- « Reste … » : ce qui reste pour la période.
- « Dépassement de … » : pour une catégorie de dépenses dont le réel dépasse le budget. La barre est affichée comme une alerte.
- « dont … reporté » : la partie du budget reportée des mois précédents, quand le report est activé.
- « (annuel) » après le nom de la catégorie : un budget annuel vu dans la vue du mois. Il compare le cumul de l’année avec le montant de l’année entière.

Les barres sont triées avec le budget le plus utilisé (réel divisé par budget) en premier. Cliquez sur une barre pour modifier ce budget.

### Le tableau {#budget-table}

Sous les barres, les mêmes chiffres sont offerts sous forme de tableau intitulé « Budget et réel », avec la période et la devise :

- **Afficher le tableau** et **Masquer le tableau** : montrent ou cachent le tableau, avec les colonnes Catégorie, Budgété, Réel et Reste.
- **CSV**, **Excel**, **PDF** : enregistrent le tableau dans un fichier de ce format.
- **Imprimer** : imprime le tableau.

## Ajouter un budget {#add-budget}

### Choisir la catégorie {#choose-category}

**Ajouter un budget** demande d’abord la catégorie :

- **Catégorie** : choisissez n’importe quelle catégorie, de dépenses ou de revenus, à n’importe quel niveau. Les sous-catégories sont décalées sous leur catégorie parente. Tapez pour filtrer la liste.

**Continuer** ouvre le formulaire de budget de cette catégorie. Si la catégorie a déjà un budget, le formulaire le montre, et l’enregistrer le modifie : une catégorie a au plus un budget.

### Le formulaire de budget {#budget-form}

Le formulaire s’intitule « Budget de (catégorie) ».

- **Le budget est** : **Mensuel** ou **Annuel**. Par défaut : Mensuel. Voir [Budgets mensuels et annuels](budgets#monthly-and-yearly).
- **Montant** : le montant d’un mois, ou de toute l’année, dans la devise de base du ménage. Obligatoire. Vous pouvez taper un calcul simple, comme 120*12.
- **Reporter au mois suivant ce qui n’est pas dépensé ou ce qui est dépassé** : affichée pour les budgets mensuels seulement. Voir [Reporter les montants](budgets#carry-over). Par défaut : non cochée.
- **À partir du (AAAA-MM-JJ, premier mois visé)** : le premier mois où le budget s’applique. Vous pouvez entrer n’importe quel jour du mois ; le budget commence le premier de ce mois. Par défaut : le mois affiché à l’écran. Dans les mois précédents, le budget n’est pas montré. Le report se calcule à partir de ce mois.

La note « Le budget couvre aussi les sous-catégories, sauf celles qui ont leur propre budget. » rappelle comment le montant réel est compté.

**Enregistrer** fixe le budget. **Supprimer** (affiché quand la catégorie a déjà un budget) demande « Supprimer le budget de …? Vos opérations ne changent pas; la catégorie n’a simplement plus de budget. » et, après confirmation, retire le budget. Vos opérations ne sont pas touchées. Cette action ne peut pas être annulée ; fixez de nouveau le budget pour le retrouver.

## Budgets mensuels et annuels {#monthly-and-yearly}
@index: budget annuel; dépenses annuelles; dépenses irrégulières; taxes foncières; assurances

- Un budget **Mensuel** est comparé au mois affiché. Dans la vue de l’année complète, il compte douze fois.
- Un budget **Annuel** convient aux frais qui reviennent une ou quelques fois par année, comme l’assurance habitation, les taxes foncières, l’immatriculation de l’auto, les cadeaux ou les vacances. Dans la vue du mois, il compare tout ce qui a été dépensé du 1er janvier à la fin du mois affiché avec le montant de l’année entière, pour que vous voyiez quelle part du budget de l’année est déjà utilisée.

## Les sous-catégories {#subcategories}
@index: catégorie parente; sous-catégorie

Un budget sur une catégorie couvre ses sous-catégories. Par exemple, un budget sur Alimentation couvre Épicerie et Restaurants.

Si une sous-catégorie a son propre budget, ses dépenses ne comptent que dans son budget, pas dans celui de sa catégorie parente. Par exemple, avec un budget sur Alimentation et un autre sur Restaurants, la ligne Alimentation compte l’épicerie et les autres dépenses d’alimentation, et la ligne Restaurants compte les restaurants.

## Reporter les montants {#carry-over}
@index: report; budget par enveloppes; reporter au mois suivant

Avec **Reporter au mois suivant ce qui n’est pas dépensé ou ce qui est dépassé** cochée, ce qui reste de chaque mois est ajouté au budget du mois suivant, et chaque dépassement en est retranché.

Par exemple, avec un budget Vêtements de 100 $ par mois commençant en janvier : vous dépensez 40 $ en janvier, donc le budget de février est de 160 $ (« dont 60 $ reporté »). Si vous dépensez ensuite 200 $ en février, le budget de mars est de 100 $ − 40 $ = 60 $.

Le report compte chaque mois depuis le mois de départ du budget jusqu’au mois qui précède celui affiché. Il ne fonctionne que pour les budgets mensuels. Pour repartir à zéro, mettez le mois courant dans **À partir du**.

## Suggérer d’après les 12 derniers mois {#suggest}
@index: budget automatique; budget d’après l’historique; dépenses moyennes

**Suggérer d’après les 12 derniers mois** examine vos dépenses des 12 derniers mois complets (sans compter le mois en cours) et propose un budget mensuel pour chaque catégorie de dépenses de premier niveau qui a eu des dépenses : la moyenne mensuelle, arrondie au montant entier supérieur.

- Chaque catégorie est affichée avec son montant suggéré « par mois », la plus grande d’abord.
- Les catégories sans budget sont cochées. Celles qui en ont déjà un sont marquées « (a déjà un budget) » et laissées décochées ; en cocher une remplace son budget par la suggestion.
- « L’historique de dépenses est encore insuffisant. » signifie qu’il n’y a rien à suggérer.

**Créer les budgets** crée un budget mensuel pour chaque catégorie cochée, à partir du mois affiché à l’écran, sans report. Vous pouvez ensuite ajuster chacun : cliquez sur sa barre.

> Conseil : Les suggestions sont des moyennes. Baissez celles que vous voulez réduire, et changez les frais annuels comme les assurances en budgets annuels.

## Modifier ou supprimer un budget {#change-budget}

Cliquez sur la barre d’une catégorie pour ouvrir son formulaire de budget. Changez le montant, la période, le report ou le mois de départ et cliquez sur **Enregistrer**, ou cliquez sur **Supprimer** pour retirer le budget (une confirmation est demandée).

## Les devises {#currencies}
@index: devise étrangère; taux de change; devise de base

Les budgets sont dans la devise de base du ménage. Les opérations dans d’autres devises (un compte en dollars américains, par exemple) sont converties aux taux de change de l’application. Quand un taux manque, une ligne rouge indique « Aucun taux de change pour … : ces montants sont exclus. Ajoutez un taux dans Taux et cours. » Voir [Taux et cours](rates).

## Où les budgets apparaissent ailleurs {#elsewhere}

- Le [Tableau de bord](dashboard) montre les dépenses du mois par rapport à vos budgets de dépenses et le nombre de catégories qui dépassent leur budget.
- L’écran [Rapports](reports) offre un rapport de budget avec les mêmes barres et le même tableau.
