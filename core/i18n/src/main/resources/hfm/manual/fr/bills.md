# Factures

Factures suit tout ce qui revient selon un calendrier : le loyer ou l’hypothèque, l’électricité et le chauffage, le téléphone et Internet, les assurances, les taxes foncières, les abonnements, votre paie et les virements réguliers vers l’épargne. L’écran vous rappelle chaque échéance à l’avance, inscrit le paiement quand vous le marquez payé et prévoit le solde de vos comptes pour que vous voyiez venir un manque d’argent. Factures se trouve dans le groupe Argent du menu.

RANN's Roost ne paie pas les factures à votre place. Vous payez par votre banque comme d’habitude ; l’application inscrit, rappelle et prévoit.

## Ce que fait l’écran Factures {#overview}
@index: paiement de factures; paiements récurrents; opérations prévues; échéances; rappels

La barre de titre a deux boutons :

- **Exporter le calendrier…** : enregistre vos échéances dans un fichier de calendrier. Voir [Exporter le calendrier](bills#export-calendar).
- **Ajouter une facture** : ouvre le formulaire de facture. Voir [Ajouter ou modifier une facture](bills#bill-form).

En dessous se trouvent cinq onglets :

- **À payer** : ce qui est en retard, à payer aujourd’hui et à payer dans les 30 prochains jours, avec les boutons pour payer, sauter ou entrer un montant.
- **Toutes les factures** : chaque facture, revenu et virement que vous avez créé.
- **Calendrier** : les échéances sur une vue du mois.
- **Abonnements** : ce que coûte chaque abonnement par année.
- **Prévision de trésorerie** : le solde prévu de chaque compte sur 30, 60 ou 90 jours.

## Factures, revenus et virements {#bill-types}
@index: revenu récurrent; jour de paie; virement prévu; épargne automatique

Un seul formulaire sert à trois types d’éléments prévus :

- **Facture** : de l’argent que vous payez selon un calendrier. La marquer payée inscrit une sortie d’argent du compte de paiement.
- **Revenu** : de l’argent que vous recevez selon un calendrier, comme votre paie, une pension, l’Allocation canadienne pour enfants ou le loyer d’un locataire. Le marquer reçu inscrit une entrée d’argent dans le compte. Son montant est affiché en couleur.
- **Virement** : de l’argent que vous déplacez entre deux de vos comptes selon un calendrier, comme un virement mensuel vers l’épargne ou le paiement d’une carte de crédit à partir du compte chèques. Le marquer payé inscrit un virement entre les deux comptes.

## Ajouter ou modifier une facture {#bill-form}

**Ajouter une facture** ouvre un formulaire vide intitulé « Ajouter une facture » ; **Modifier** sur n’importe quelle ligne ouvre le même formulaire intitulé « Modifier la facture ». **Enregistrer** est offert dès que la facture a un nom et un compte. **Annuler** ferme le formulaire sans enregistrer.

### Type et nom {#type-and-name}

- **Type** : Facture, Revenu ou Virement. Voir [Factures, revenus et virements](bills#bill-types). Le type change les champs qui suivent. Par défaut : Facture.
- **Nom** : le nom de la facture dans chaque liste, rappel et calendrier, par exemple « Hydro-Québec », « Loyer » ou « Paie - Marie ». Obligatoire.

### Comptes et catégorie {#accounts-and-category}

- **Payée à partir de** (pour une facture ou un virement) ou **Déposé dans** (pour un revenu) : le compte d’où sort ou où arrive l’argent. Le montant de la facture est dans la devise de ce compte. Ce compte sert à la prévision et à l’avertissement de découvert, et le paiement y est inscrit. Il ne peut être choisi qu’à la création de la facture ; pour le changer plus tard, créez une nouvelle facture. Obligatoire.
- **Vers le compte** (virement seulement) : le compte où va l’argent. Ce doit être un autre compte.
- **Catégorie** (facture et revenu seulement) : la catégorie sous laquelle le paiement est inscrit, par exemple Services publics : Électricité ou Revenu : Salaire. Elle détermine où le paiement compte dans les budgets, les rapports et les chiffres d’impôt. « (aucun) » inscrit le paiement sans catégorie.

### Bénéficiaire {#payee}

Affiché pour une facture ou un revenu, pas pour un virement.

- **Bénéficiaire** : à qui vous payez, ou qui vous paie. Il devient le bénéficiaire de l’opération inscrite. S’il est vide, le nom de la facture est utilisé. Il aide aussi l’application à reconnaître une facture numérisée ou téléchargée comme étant celle-ci.
- **Votre numéro de compte chez le fournisseur** : le numéro de compte ou de client imprimé sur la facture. Facultatif. Quand vous numérisez ou importez une facture, l’application compare ses quatre derniers chiffres avec ce numéro pour trouver la bonne facture. Il n’est affiché nulle part ailleurs.

### Montant {#amount}

- **Montant** : le montant de chaque paiement, dans la devise du compte, en nombre positif. Pour une facture variable, entrez un montant typique. Vous pouvez taper une addition simple, comme 45,20+12. S’il est laissé vide, le montant est zéro.
- **Le montant est** : à quel point le montant est sûr.
  - **Fixe** : le même chaque fois, comme un loyer ou un abonnement. Le montant est utilisé tel quel.
  - **Variable** : change chaque fois, comme l’électricité ou une carte de crédit. Tant que vous n’avez pas entré le montant réel d’une échéance, l’application prévoit la moyenne des trois derniers montants payés (ou le montant que vous avez entré, avant tout paiement). Le montant est précédé de « ≈ ».
  - **Estimé** : un montant que vous estimez, comme un compte de taxes foncières annuel pas encore reçu. Précédé de « ≈ » jusqu’à ce que vous entriez le montant réel.

Pour les factures variables et estimées, l’onglet **À payer** offre **Entrer le montant** pour inscrire le montant réel quand la facture arrive. Voir [Entrer le montant](bills#enter-amount).

### Mode de paiement {#payment-method}

- **Mode de paiement** : comment cette facture est payée : Prélèvement automatique, Paiement en ligne, Carte de crédit, Chèque, Argent comptant ou Autre. Par défaut : Paiement en ligne. Il est affiché dans la liste **À payer** comme aide-mémoire, par exemple pour savoir quelles factures vous devez payer vous-même et lesquelles sont prélevées toutes seules. Il ne change pas la façon dont le paiement est inscrit.

### La répétition {#repeats}
@index: fréquence; aux deux semaines; deux fois par mois; mensuel; trimestriel; annuel; bimensuel

- **Répétition** : à quelle fréquence la facture revient.
  - **Une seule fois** : une seule échéance.
  - **Chaque semaine**, **Aux deux semaines** : à partir de la première échéance, tous les 7 ou 14 jours. Aux deux semaines convient à la plupart des paies.
  - **Deux fois par mois** : le jour de la première échéance et un deuxième jour chaque mois, comme le 1er et le 15.
  - **Chaque mois**, **Chaque trimestre**, **Deux fois par année**, **Chaque année** : tous les 1, 3, 6 ou 12 mois.
  - **Tous les … jours**, **Toutes les … semaines**, **Tous les … mois** : tout autre intervalle ; entrez le nombre sous **Tous les**.
- **Tous les** : affiché pour les choix « Tous les … ». Le nombre de jours, de semaines ou de mois entre deux échéances, 1 ou plus.
- **Deuxième jour** : affiché pour Deux fois par mois. Le deuxième jour du mois, de 1 à 31 ; entrez 0 pour le dernier jour du mois. Le premier jour est celui de la première échéance.
- **Jour du mois** : affiché pour les factures mensuelles, trimestrielles, semestrielles, annuelles et « tous les … mois ».
  - **Même jour chaque fois** : le jour de la première échéance. Dans un mois plus court, il passe au dernier jour du mois (une facture due le 31 tombe le 30 en avril et le 28 ou le 29 en février).
  - **Dernier jour du mois**.
  - **Dernier jour ouvrable** : le dernier jour de semaine du mois qui n’est pas un jour férié bancaire.

### Fins de semaine et jours fériés {#weekends-holidays}
@index: jour ouvrable; jour férié bancaire; congé férié

- **Les fins de semaine et jours fériés** : ce qui arrive quand une échéance tombe un samedi, un dimanche ou un jour férié bancaire.
  - **Garder la date** : l’échéance reste telle quelle. Par défaut.
  - **Avancer au jour ouvrable précédent** : pour les paiements qui doivent arriver à temps, comme un prélèvement automatique traité le jour ouvrable précédent.
  - **Reporter au jour ouvrable suivant** : pour les dépôts, comme un jour de paie reporté au lundi.

Les jours fériés bancaires sont les congés fédéraux observés par les banques, plus ceux de la province ou du territoire du ménage (par exemple la Fête nationale au Québec, le jour de la Famille ou le congé civique). Un congé qui tombe une fin de semaine est observé le jour de semaine suivant.

### Première et dernière échéances {#due-dates}

- **Première échéance** : la première date où la facture est due, au format AAAA-MM-JJ. Toutes les échéances suivantes sont calculées à partir d’elle. Par défaut : aujourd’hui. Obligatoire. Pour changer le calendrier d’une facture existante à partir de maintenant, vous pouvez y mettre la prochaine échéance.
- **Dernière échéance (facultatif)** : la dernière date où la facture est due, par exemple la fin d’un bail ou d’un prêt. Laissez vide pour qu’elle n’ait pas de fin. Elle ne peut pas précéder la première échéance.

### Rappels {#reminders}
@index: rappel de facture; notification; alerte d’échéance

- **Me le rappeler (jours avant)** : combien de jours avant chaque échéance vous voulez un rappel, séparés par des virgules, par exemple 7, 1 (par défaut). Chaque nombre va de 0 à 365. Laissez vide pour n’avoir aucun rappel à l’avance.

Quoi que vous entriez ici, une facture à payer aujourd’hui ou en retard figure toujours dans les rappels. Voir [Rappels et notifications](bills#reminder-banner).

### Abonnement {#subscription-fields}
@index: abonnement; diffusion en continu; annuler un abonnement; essai gratuit; renouvellement

- **Abonnement** : cochez-la pour les abonnements comme la diffusion en continu, les logiciels, les magazines ou le gym, afin de les suivre dans l’onglet **Abonnements** avec leur coût annuel.
- **Annuler avant le (rappel)** : affiché quand **Abonnement** est cochée. La date limite pour annuler avant le prochain renouvellement ou la fin d’un essai gratuit, au format AAAA-MM-JJ. À partir de 7 jours avant cette date, un rappel indique « annuler d’ici … jours pour éviter le renouvellement ». Elle est affichée dans l’onglet Abonnements.

### Active et Supprimer {#active-and-delete}

Ces éléments n’apparaissent que lorsque vous modifiez une facture existante.

- **Active** : décochez-la pour mettre une facture en veilleuse, par exemple une facture saisonnière ou un service annulé dont vous voulez garder l’historique. Une facture inactive disparaît de À payer, de l’onglet Calendrier, de l’onglet Abonnements, de la prévision, des rappels et de l’écran Calendrier. Elle reste dans **Toutes les factures**, marquée « (inactive) », où vous pouvez la réactiver.
- **Supprimer** : demande « Supprimer … et son historique de paiements? Les opérations déjà inscrites sont conservées. » et, après confirmation, supprime la facture et la liste des échéances payées ou sautées. Les opérations inscrites quand vous l’avez marquée payée restent dans les comptes. Cette action ne peut pas être annulée.

## L’onglet À payer {#to-pay-tab}
@index: factures en retard; factures à venir; agenda

**À payer** énumère les échéances de toutes les factures, revenus et virements actifs en quatre groupes, chacun avec son nombre :

- **En retard** : les échéances déjà passées, ni payées ni sautées, jusqu’à un an en arrière.
- **À payer aujourd’hui**.
- **30 prochains jours**.
- **Payées récemment** : les échéances des 31 derniers jours (et les suivantes) déjà marquées payées, de la plus récente à la plus ancienne.

« Rien à payer dans les 30 prochains jours. » signifie que les quatre groupes sont vides.

Chaque ligne montre l’échéance, le nom, le mode de paiement, le compte (et « → compte » pour un virement), la date du paiement s’il a eu lieu, et le montant (« ≈ » devant signifie que le montant est prévu, pas connu). Les boutons de la ligne sont décrits ci-dessous. **Modifier** ouvre le formulaire de la facture.

### Marquer payée ou Marquer reçu {#mark-paid}
@index: inscrire un paiement; payer une facture

**Marquer payée** (pour une facture ou un virement) ou **Marquer reçu** (pour un revenu) ouvre un petit formulaire. Il indique l’échéance, et qu’une opération sera ajoutée au compte et jumelée au relevé bancaire lors de son importation.

- **Date du paiement** : la date du paiement, au format AAAA-MM-JJ. Par défaut : aujourd’hui.
- **Montant** : le montant réellement payé ou reçu. Par défaut : le montant prévu. Il doit être supérieur à zéro.

**Enregistrer** alors :

- pour une facture : inscrit une sortie d’argent du compte de paiement, avec le bénéficiaire (ou le nom) de la facture comme bénéficiaire, sa catégorie et son nom en note ;
- pour un revenu : inscrit une entrée d’argent dans le compte de la même façon ;
- pour un virement : inscrit un virement du compte de paiement vers l’autre compte ;
- marque cette échéance payée. Elle passe dans **Payées récemment** et l’échéance suivante prend sa place.

Quand vous importez plus tard le relevé bancaire, l’importation jumelle la ligne du relevé avec cette opération au lieu de l’ajouter deux fois. Les montants payés sur une facture variable établissent aussi son montant prévu (la moyenne des trois derniers).

### Entrer le montant {#enter-amount}

**Entrer le montant** apparaît pour les factures variables et estimées. Quand la facture réelle arrive, entrez son montant :

- **Montant** : le montant de cette échéance seulement. Obligatoire.

La ligne montre alors le montant sans « ≈ », et la prévision et les rappels l’utilisent. Rien n’est inscrit dans le compte avant que vous la marquiez payée.

> Conseil : Quand vous numérisez ou importez une facture papier ou électronique dans l’écran Documents, Inscrire le montant sur cette facture fait la même chose et garde la facture avec l’échéance. Voir [Inscrire le montant sur une facture](documents#record-on-bill).

### Sauter {#skip}

**Sauter** passe par-dessus cette échéance seulement, par exemple un mois sans facture ou un paiement que vous ne ferez pas. L’échéance est marquée sautée aussitôt, sans question, et quitte la liste ; rien n’est inscrit dans le compte. L’écran n’offre aucun moyen de faire revenir une échéance sautée.

### Annuler un paiement {#undo-payment}

Sous **Payées récemment**, **Annuler** défait un paiement marqué par erreur : l’opération inscrite pour ce paiement est supprimée du compte, et l’échéance revient dans la liste comme non payée. Aucune question n’est posée.

> Remarque : Si vous avez déjà rapproché cette opération avec un relevé, modifiez-la plutôt à partir du registre du compte.

### Avertissements de découvert {#shortfall-warning}
@index: découvert; provision insuffisante; fonds insuffisants; solde bas

Une ligne rouge « Le compte de paiement passerait sous zéro. » apparaît sur une facture dont le paiement, selon la prévision des 30 prochains jours, ferait passer un compte bancaire sous zéro. Voir [L’onglet Prévision de trésorerie](bills#forecast-tab).

## L’onglet Toutes les factures {#all-bills-tab}

**Toutes les factures** énumère chaque facture, revenu et virement, y compris les inactifs (marqués « (inactive) »). Chaque ligne montre le nom, le type, la répétition, la prochaine échéance (« prochaine le … ») et le montant (« ≈ » pour un montant variable ou estimé). **Modifier** ouvre le formulaire de la facture.

« Aucune facture pour l’instant. Ajoutez le loyer, les services publics, les assurances, les abonnements, la paie et les virements réguliers. » signifie qu’aucune n’a été créée.

## L’onglet Calendrier {#calendar-tab}

**Calendrier** montre un mois, les semaines commençant le lundi, avec les échéances et les montants de chaque jour. Utilisez **◀** et **▶** pour changer de mois. La date du jour est en gras.

- Les échéances à payer sont en texte normal ; celles en retard, en rouge.
- Les échéances payées sont grisées ; les échéances sautées, plus pâles encore.
- Un jour montre jusqu’à trois factures, puis « +n » pour les autres.

L’écran [Calendrier](calendar) montre les mêmes échéances avec les rendez-vous, les dates de santé et les renouvellements.

## L’onglet Abonnements {#subscriptions-tab}
@index: coût des abonnements; coût annuel

**Abonnements** énumère chaque facture active marquée **Abonnement**, la plus chère d’abord. Chaque ligne montre la répétition, le montant, la prochaine échéance, la date « annuler avant le » si vous en avez mis une, et son coût par année, en gras. Au bas, « Tous les abonnements : … par année » en fait le total (un total par devise).

Le coût annuel est le montant multiplié par le nombre de paiements dans une année : 12 pour chaque mois, environ 26 pour aux deux semaines, 24 pour deux fois par mois, 4 pour chaque trimestre, et ainsi de suite. Un élément d’une seule fois ne compte pour rien.

« Aucun abonnement. Cochez « Abonnement » sur une facture pour la suivre ici. » signifie qu’aucune n’est marquée.

## L’onglet Prévision de trésorerie {#forecast-tab}
@index: trésorerie; prévision; solde prévu; aurai-je assez d’argent

**Prévision de trésorerie** prévoit le solde de chaque compte à partir d’aujourd’hui, d’après les factures, revenus et virements encore à payer.

- **30 jours**, **60 jours**, **90 jours** : jusqu’où regarder. Par défaut : 30 jours.

Pour chaque compte qui a quelque chose de prévu (ou dont le solde est déjà sous zéro), une carte montre :

- « Aujourd’hui … · à la fin … · plus bas … » : le solde d’aujourd’hui, le solde à la fin de la période et le point le plus bas entre les deux.
- Un avertissement rouge « n paiements mettraient ce compte à découvert » quand des paiements feraient passer un compte bancaire sous zéro.
- Une ligne par élément prévu : la date, le nom de la facture et le solde après. Les soldes sous zéro sont en rouge.

Comment le calcul est fait :

- Le solde d’aujourd’hui est le solde actuel du compte dans les livres ; importez ou inscrivez donc d’abord les opérations récentes.
- Les éléments en retard pas encore payés sont comptés aujourd’hui.
- Les factures variables comptent leur montant prévu ; les montants que vous avez entrés comptent tels quels.
- Un virement compte dans les deux comptes. Un virement vers un compte d’une autre devise n’est pas compté dans ce compte, puisque le montant qui y arrivera n’est pas connu.
- Les avertissements de découvert ne sont donnés que pour les comptes bancaires, pas pour les cartes de crédit ni les prêts.
- Les dépenses que vous n’avez pas prévues comme facture (épicerie, essence) ne sont pas dans la prévision.

## Exporter le calendrier {#export-calendar}
@index: iCalendar; ics; Google Agenda; Outlook; fichier de calendrier

**Exporter le calendrier…** enregistre les échéances des douze prochains mois dans un fichier de calendrier (bills.ics par défaut), au format iCalendar que Google Agenda, Outlook, Calendrier d’Apple et la plupart des logiciels de calendrier savent importer.

- Seules les échéances encore à payer sont incluses, comme événements d’une journée entière.
- Chaque événement porte le nom de la facture avec son montant, par exemple « Hydro · ≈ 142,00 $ ».
- Le fichier est un instantané : exportez de nouveau après avoir changé vos factures ou les avoir marquées payées.

## Rappels et notifications {#reminder-banner}
@index: bandeau de rappels; notification; alerte

Les rappels de factures apparaissent à deux endroits :

- Un bandeau de couleur en haut de tous les autres écrans, comme « 3 rappels  Hydro : à payer dans 7 jours (≈ 142,00 $) · Loyer : à payer aujourd’hui (1 450,00 $) ». Cliquez dessus pour ouvrir l’écran du premier rappel. Le bandeau porte aussi les rappels de rendez-vous, de renouvellements de médicaments, de renouvellements et d’entretien.
- Une notification du système de RANN's Roost, vérifiée toutes les quelques minutes tant que le ménage est ouvert. Chaque rappel est annoncé une fois par session.

Une facture figure dans les rappels :

- chaque jour qui correspond à l’un de ses nombres **Me le rappeler (jours avant)** (« à payer dans 7 jours », « à payer demain ») ;
- le jour de son échéance (« à payer aujourd’hui ») ;
- chaque jour où elle est en retard (« en retard de 3 jours »), jusqu’à ce que vous la marquiez payée ou la sautiez ;
- pour un abonnement, chaque jour à partir de 7 jours avant sa date **Annuler avant le (rappel)**.

## Factures à partir de documents numérisés {#bills-from-documents}

Une facture papier ou électronique importée dans l’écran [Documents](documents) peut être inscrite sur sa facture : l’application reconnaît la facture par votre numéro de compte chez le fournisseur ou par le nom du bénéficiaire, et **Inscrire le montant sur cette facture** fixe le montant réel de l’échéance la plus proche et garde le document avec elle. Remplissez **Bénéficiaire** et **Votre numéro de compte chez le fournisseur** sur vos factures pour qu’elles soient reconnues.

## Où les factures apparaissent ailleurs {#elsewhere}

- Le [Tableau de bord](dashboard) montre les factures en retard ou dues dans les 7 prochains jours et leur total.
- Le [Calendrier](calendar) montre chaque échéance avec les rendez-vous et les autres dates.
- Les paiements inscrits à partir des factures sont des opérations ordinaires : ils comptent dans les [Budgets](budgets), les [Rapports](reports) et les chiffres d’impôt sous la catégorie de la facture.

## Qui peut faire quoi {#permissions}

- Ajouter, modifier, supprimer, sauter, entrer un montant et annuler un paiement demandent la permission **Modification** sur le groupe de comptes du compte de paiement.
- Marquer une échéance payée ou reçue demande au moins la permission **Saisie seulement**.
- Une facture est visible par tous ceux qui peuvent ouvrir le groupe de comptes de son compte de paiement. Voir [Utilisateurs](users).
