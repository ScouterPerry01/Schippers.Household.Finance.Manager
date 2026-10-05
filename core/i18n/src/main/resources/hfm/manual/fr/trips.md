# Déplacements

Le registre des déplacements garde les trajets que vous faites en voiture pour le travail ou pour des soins. À partir de lui, RANN’s Roost additionne les kilomètres de chaque personne par motif, calcule la part de la conduite de chaque véhicule qui était pour le travail, et transforme un long déplacement médical en dépense médicale. Il se trouve dans le groupe **Maison et famille** du menu, sous **Déplacements**.

@index: registre de kilométrage; carnet de route; kilomètres; usage d’un véhicule pour le travail; kilomètres de travail; frais de véhicule

> Remarque : L’ARC s’attend à un carnet de route des déplacements d’affaires ou d’emploi (date, destination, motif et distance) pour appuyer une déduction de frais de véhicule, avec le total des kilomètres parcourus dans l’année. RANN’s Roost ne donne pas de conseils fiscaux ; vérifiez les règles de l’ARC avant de déduire quoi que ce soit.

## L’écran Déplacements {#screen}

En haut de l’écran :

- **Année d’imposition** : l’année civile affichée, de cette année jusqu’à six ans en arrière. Les cartes et la liste montrent seulement les déplacements datés de cette année.
- **Ajouter un déplacement** : ouvre la [boîte du déplacement](#trip-dialog) pour un nouveau déplacement.

Les nouveaux déplacements sont enregistrés dans le groupe de comptes partagé où vous pouvez ajouter des données (ou le premier groupe où vous le pouvez). Enregistrer, modifier ou supprimer demande la permission de modifier les données de ce groupe.

### Les cartes de résumé {#cards}

Quand l’année compte des déplacements ou des lectures de véhicule, des cartes apparaissent au-dessus de la liste.

Une carte par personne (ou **Ménage** pour les déplacements qui ne sont liés à personne) donne ses kilomètres de l’année par motif, par exemple « Affaires : 1 240 km » et « Médical : 392 km ». Un aller-retour compte deux fois la distance aller simple.

Une carte par véhicule qui a des déplacements de travail ou des lectures d’odomètre dans l’année montre :

- **Pour le travail** : les kilomètres des déplacements **Affaires** et **Emploi** de l’année faits avec ce véhicule.
- **Parcourus en tout** : les kilomètres parcourus dans l’année, d’après les lectures d’odomètre du véhicule : la dernière lecture de l’année moins la première. Il faut au moins deux lectures dans l’année ; sinon, la carte vous demande d’ajouter une lecture au début et à la fin de l’année.
- **Part de travail** : les kilomètres de travail en pourcentage de tous les kilomètres parcourus, arrondis et jamais au-dessus de 100 %. C’est la part d’usage pour le travail demandée quand on déduit des frais de véhicule.

Les lectures d’odomètre se saisissent dans l’écran [Véhicules](vehicles), ou s’envoient du téléphone avec **Odomètre ou heures** (voir [RANN’s Roost Mobile](phone-app#odometer-form)). Les véhicules inactifs gardent leurs cartes pour les années où ils ont servi.

@index: part de travail; odomètre; pourcentage d’usage pour le travail

### La liste des déplacements {#trip-list}

Les déplacements de l’année sont listés du plus récent au plus ancien. Chaque ligne montre :

- la date ;
- le point de départ et la destination (« Maison → Bureau du client »), avec **↺** pour un aller-retour ;
- le motif, la personne, le véhicule et les notes ;
- **Ajouter aux frais médicaux**, pour un déplacement **Médical** de 40 km ou plus aller simple (voir [Déplacements pour des soins](#medical-travel)) ; une fois le déplacement ajouté, le bouton devient **Ajouté aux frais médicaux** et ne se clique plus ;
- la distance, doublée pour un aller-retour.

Cliquez sur un déplacement pour le modifier ou le supprimer. S’il n’y a aucun déplacement dans l’année, la liste le dit.

## Boîte Ajouter un déplacement {#trip-dialog}

La même boîte ajoute un déplacement (**Ajouter un déplacement**) ou le modifie (**Modifier le déplacement**).

- **Date** : le jour du déplacement, au format AAAA-MM-JJ. Aujourd’hui par défaut. Elle décide de l’année où le déplacement compte.
- **Motif** : pourquoi vous avez conduit. Il décide des totaux où le déplacement compte :
  - **Affaires** : conduire pour une entreprise que vous exploitez, comme visiter des clients pour votre travail d’appoint. Compte comme kilomètres de travail pour le véhicule.
  - **Emploi** : conduire parce que votre employeur l’exige, autrement que pour aller au travail et en revenir. Compte comme kilomètres de travail pour le véhicule.
  - **Médical** : aller recevoir des soins. Peut devenir une dépense médicale à 40 km ou plus aller simple.
  - **Personnel** : tout le reste. Compté seulement dans les totaux de la personne.
  Un nouveau déplacement commence à **Affaires**.
- **De** : d’où vous êtes parti, par exemple « Maison ». Facultatif.
- **À** : où vous êtes allé, comme l’adresse d’un client ou un hôpital. Obligatoire.
- **Kilomètres aller simple** : la distance aller simple, par exemple 23,5 (le point fonctionne aussi). Obligatoire, plus grande que zéro et sous 10 000. Elle est gardée avec une décimale.
- **Aller-retour** : coché quand vous êtes revenu par le même chemin ; le déplacement compte alors deux fois la distance. Coché par défaut.
- **Personne** : qui a fait le déplacement, ou **Ménage**. Elle décide de la carte où le déplacement compte et est proposée comme patient quand on l’ajoute aux frais médicaux.
- **Véhicule** : le véhicule utilisé, ou **Aucun véhicule**. Seuls les déplacements avec un véhicule comptent dans la part de travail de ce véhicule. Un nouveau déplacement propose le premier véhicule en service. Un déplacement enregistré garde son propre choix : **Aucun véhicule** reste **Aucun véhicule**, et un véhicule vendu ou retiré depuis reste dans la liste pour ce déplacement.
- **Notes** : ce qu’il faut retenir, comme le client ou le motif de la visite.
- Quand **Médical** est choisi, un rappel explique qu’un déplacement médical compte comme dépense médicale quand les soins sont à 40 km ou plus, aller simple, et ne sont pas offerts plus près. Les 40 km sont un chiffre de **Taux et règles** (Déplacement médical : distance minimale), lu pour la date du déplacement.
- **Supprimer** : affiché en modification. Demande « Supprimer le déplacement du date vers destination? » et, une fois confirmé, le supprime. C’est sans retour. Une dépense médicale faite à partir du déplacement reste dans l’écran Réclamations médicales.

## Déplacements pour des soins {#medical-travel}

@index: déplacements pour des soins; frais de déplacement pour soins médicaux; 40 km; 80 km; crédit d’impôt pour frais médicaux; CIFM; taux par kilomètre

Quand des soins ne sont pas offerts près de chez vous et que vous parcourez 40 km ou plus aller simple pour les recevoir, le coût du trajet peut compter comme dépense médicale pour le crédit d’impôt pour frais médicaux. La méthode simplifiée de l’ARC permet de demander un taux fixe par kilomètre au lieu de garder chaque reçu ; l’ARC publie ce taux pour chaque province chaque année. (Les trajets de 80 km ou plus aller simple peuvent aussi permettre d’autres frais, comme les repas et l’hébergement ; inscrivez-les dans l’écran [Réclamations médicales](medical).)

Un déplacement **Médical** dont la distance aller simple est de 40 km ou plus affiche **Ajouter aux frais médicaux** dans la liste.

### Boîte Ajouter aux frais médicaux {#to-medical-dialog}

- La première ligne montre la date et la destination du déplacement, suivie d’un rappel que l’ARC publie chaque année un taux par kilomètre pour chaque province et territoire, et que le taux affiché vient de **Taux et règles**.
- **Personne** : le patient, dont ce sera la dépense médicale. La personne du déplacement par défaut, sinon le premier membre du ménage. Le taux suit la province ou le territoire de cette personne (celui du ménage, sauf si la personne a le sien), puisque le déplacement commence là où elle habite.
- Sous la personne, une ligne indique d’où vient le taux : le taux de la province en vigueur depuis une date, intégré (avec la source de l’ARC) ou propre au ménage ; ou qu’il n’y a pas encore de taux pour la province.
- **Taux par kilomètre** : en dollars, par exemple 0,62 ou 0,605 (les taux de l’ARC peuvent avoir un demi-cent). Il est rempli avec le Déplacement médical : taux par kilomètre de Taux et règles pour la date du déplacement et la province de la personne : l’ARC publie les taux d’une année au début de l’année suivante ; d’ici là, le taux de la dernière année est affiché. Vous pouvez le changer pour ce déplacement. Un taux saisi pour une année dans une version antérieure de l’application sert encore pour cette année.
- **Garder ce taux pour province à partir du 1er janvier année, dans Taux et règles** : pour un administrateur seulement. Coché, quand le taux a été changé, le taux est aussi ajouté à Taux et règles comme valeur propre au ménage pour cette province à partir du 1er janvier de l’année du déplacement ; il est alors rempli pour les prochains déplacements.

**Enregistrer** est offert dès qu’une personne et un taux sont choisis. Il :

1. garde le taux dans Taux et règles, quand la case ci-dessus est cochée ;
2. ajoute pour la personne, dans l’écran [Réclamations médicales](medical), une dépense médicale du type **Déplacements pour des soins**, datée et payée à la date du déplacement, égale au taux fois les kilomètres du déplacement (doublés pour un aller-retour), avec comme description la destination et la distance.

La dépense compte ensuite dans la réclamation médicale comme toute autre. Un déplacement n’est ajouté qu’une fois : son bouton devient alors **Ajouté aux frais médicaux**. Pour l’ajouter de nouveau, par exemple à un autre taux, supprimez d’abord la dépense dans l’écran Réclamations médicales ; le bouton revient.
