# Impôts

L’écran Impôts réunit l’année fiscale en un seul endroit : les feuillets que chaque personne devrait recevoir, les dons et leurs reçus officiels, les acomptes provisionnels, et une trousse de fin d’année avec les montants de la déclaration. Il se trouve dans le groupe **Rapports et impôts** du menu.

L’application ne prépare pas et ne produit pas de déclaration. Elle rassemble ce que les livres savent déjà, pour qu’au moment des impôts vous, ou votre comptable, ayez les montants, les feuillets et les reçus au même endroit.

> Important : Ces montants aident à préparer une déclaration ; ce ne sont pas des conseils fiscaux. Les feuillets et reçus font foi : vérifiez chaque montant, et consultez un fiscaliste en cas de doute.

## L’écran Impôts {#taxes-screen}

@index: impôt; impôt sur le revenu; déclaration de revenus; période des impôts

Sous le titre, cinq onglets :

- **Feuillets** : les feuillets attendus des employeurs, payeurs et institutions, et s’ils sont arrivés. Voir [Feuillets](taxes#slips).
- **Dons** : chaque don inscrit dans les livres, par personne, avec son reçu officiel. Voir [Dons](taxes#donations).
- **Acomptes** : le calendrier d’acomptes de l’ARC ou de Revenu Québec, et ce qui a été payé. Voir [Acomptes](taxes#instalments).
- **Trousse de fin d’année** : les montants de chaque personne pour la déclaration, avec la ligne de chacun, et un dossier pour le comptable. Voir [Trousse de fin d’année](taxes#year-end-package).
- **Estimation** : une estimation de l’impôt fédéral et provincial ou territorial de chaque personne, et du solde dû ou du remboursement. Voir [Estimation](taxes#estimate).

Chaque onglet a sa propre **Année d’imposition** en haut.

- Dans **Feuillets**, **Trousse de fin d’année** et **Estimation**, l’année est au départ celle qu’on prépare : l’an dernier jusqu’à la fin de juin, puis l’année en cours. Vous pouvez choisir l’année en cours ou l’une des six précédentes.
- Dans **Dons**, l’année est au départ l’année en cours, et les six précédentes sont aussi offertes.
- Dans **Acomptes**, l’année est au départ l’année en cours ; l’année suivante et les trois précédentes sont aussi offertes, pour entrer le calendrier de l’an prochain dès l’arrivée du rappel.

Une grande partie de ce qu’affiche cet écran vient de la façon dont les opérations sont inscrites pendant l’année : voir [Pendant l’année](taxes#through-the-year).

## Feuillets {#slips}

@index: feuillets d’impôt; T4; T4A; T5; T3; T5008; Relevé 1; RL-1; Relevé 24; reçus d’impôt; liste de contrôle

L’onglet **Feuillets** est une liste de contrôle des feuillets que chaque personne devrait recevoir pour l’année d’imposition, établie à partir des livres, plus ceux que vous ajoutez à la main. La plupart des feuillets arrivent d’ici la fin février ; les T3 et les Relevés 16, d’ici le 31 mars.

La liste est regroupée par personne, le ménage en dernier. L’en-tête de chaque personne indique combien de feuillets sont arrivés, par exemple « 3 sur 5 reçus » ; les feuillets marqués non attendus ne sont pas comptés. Chaque feuillet affiche :

- sa sorte, par exemple « T4, revenus d’emploi » ;
- de qui il vient (l’employeur, le payeur ou l’institution) ;
- pourquoi il est attendu, par exemple « Salaire versé par ce payeur », ou « Ajouté à la main » ;
- son état : **Attendu**, **Reçu** ou **Non attendu**. Après le 31 mars de l’année suivante, les feuillets encore attendus sont en rouge : ils auraient dû arriver ;
- le nombre de fichiers joints, s’il y en a.

- **Ajouter un feuillet…** : ajoute un feuillet que les livres ne peuvent pas connaître. Voir [Ajouter un feuillet](taxes#add-slip).

Cliquez sur un feuillet pour changer son état et le joindre. Voir [La fenêtre d’un feuillet](taxes#slip-window).

« Aucun feuillet attendu pour l’instant » signifie que rien dans les livres de l’année n’appelle encore de feuillet. Les feuillets apparaissent à mesure que salaires, rentes, prestations, activité des régimes et revenus de placement sont inscrits.

### Comment les feuillets attendus sont trouvés {#expected-slips}

@index: feuillet attendu; pourquoi ce feuillet; relevés du Québec; Relevé

L’application examine les opérations de l’année, par bénéficiaire et par personne (le champ **Pour** de la ligne d’opération, ou sinon le seul titulaire du compte) :

- Une paie dans **Salaire**, **Primes et commissions** ou **Revenus d’emploi** : un T4, « T4, revenus d’emploi », du bénéficiaire.
- **Régime de retraite de l’employeur** : un T4A. **RRQ / RPC** : un T4A(P). **Pension de la Sécurité de la vieillesse** : un T4A(OAS). **Retraits de FERR et rentes** : un T4RIF.
- **Assurance-emploi** ou **Assurance-emploi / RQAP** : un T4E.
- Des **Intérêts** de 50 $ ou plus dans l’année du même payeur : un T5. Sous 50 $, aucun feuillet n’est attendu.
- Des **Frais de scolarité** payés : un T2202 de l’établissement.
- Des **Frais de garde** payés, pour une personne qui produit au Québec : un Relevé 24.
- De l’argent versé dans un REER depuis l’extérieur des régimes enregistrés (un virement depuis un autre compte, ou un dépôt sans virement ni catégorie, comme l’encaisse importée d’un fichier de courtage) : un reçu de cotisation REER, pour le titulaire (pour un REER de conjoint, pour le cotisant). De l’argent retiré d’un REER : un T4RSP. D’un FERR, d’un FERR de conjoint ou d’un FRV : un T4RIF. Tout virement vers ou depuis un CELIAPP : un T4FHSA. L’émetteur est l’institution du régime, ou le nom du compte.
- Des revenus de placement dans un compte non enregistré : un T5 de l’institution, ou un T3 de chaque fonds canadien, selon le rapport des revenus de placement (voir [Revenus de placement et gains en capital](reports#investment-income)).
- Une vente de titres dans un compte de placement non enregistré : un T5008 pour chaque titulaire.

Pour une personne qui produit au Québec (selon la province indiquée pour elle, ou celle du ménage), le Relevé correspondant est aussi attendu : Relevé 1 avec un T4, Relevé 2 avec un T4A, T4A(P), T4RSP ou T4RIF, Relevé 3 avec un T5, Relevé 16 avec un T3, Relevé 18 avec un T5008, Relevé 8 avec un T2202.

Le revenu gagné dans les régimes enregistrés ne donne aucun feuillet tant que l’argent n’en sort pas. Une opération sans bénéficiaire ne donne aucun feuillet, faute de savoir de qui l’attendre.

### La fenêtre d’un feuillet {#slip-window}

Cliquez sur un feuillet pour ouvrir sa fenêtre. Le titre est la sorte de feuillet ; la première ligne donne la personne, l’émetteur et l’année, puis la raison pour laquelle il est attendu.

- **État** : **Attendu**, **Reçu** ou **Non attendu**. Marquez-le reçu à son arrivée, ou non attendu quand il ne viendra pas cette année (un compte sous le seuil d’intérêts, un feuillet envoyé à quelqu’un d’autre). Remettre un feuillet attendu à **Attendu** efface votre marque.
- **Fichiers du feuillet** : le feuillet lui-même, conservé dans le coffre de documents. Le nombre paraît entre parenthèses.
  - **Joindre un fichier…** : choisissez un PDF ou une photo du feuillet sur cet ordinateur. Il est conservé dans le coffre, classé, et lié à ce feuillet.
  - **Depuis la boîte de révision** : affiché quand des documents attendent dans la boîte de révision (par exemple un feuillet photographié avec le téléphone). Choisissez-en un pour le lier à ce feuillet et le classer.
- **Retirer ce feuillet** : seulement pour un feuillet ajouté à la main. Le retire tout de suite de la liste ; les fichiers joints restent dans le coffre.
- **Enregistrer** : enregistre l’état. **Annuler** : ferme sans changer l’état. Les fichiers sont joints tout de suite, sans attendre **Enregistrer**.

Les feuillets joints ici sont copiés dans le dossier pour le comptable (voir [Dossier pour le comptable](taxes#accountant-folder)). Changer l’état ou ajouter des feuillets exige le droit de modifier le groupe de comptes auquel le feuillet appartient.

### Ajouter un feuillet {#add-slip}

**Ajouter un feuillet…** ouvre « Ajouter un feuillet pour » l’année d’imposition affichée.

- **Personne** : qui reçoit le feuillet, ou **Ménage** quand ce n’est personne en particulier.
- **Feuillet** : la sorte de feuillet, par exemple **T4A, pension ou autres revenus** (par défaut), **Relevé 24, frais de garde**, **T2202, frais de scolarité** ou **Autre feuillet**. Tous les feuillets fédéraux et du Québec que connaît la liste sont offerts.
- **De (employeur, institution ou payeur)** : qui envoie le feuillet. Obligatoire.
- **Enregistrer** : l’ajoute à la liste comme attendu ; il porte la mention « Ajouté à la main ». **Annuler** : ferme.

Servez-vous-en pour un feuillet que les livres ne peuvent pas prévoir : un T4A pour un contrat, un Relevé 24 d’un camp de jour, un feuillet d’un payeur que vous n’inscrivez pas.

## Dons {#donations}

@index: don; organisme de bienfaisance; don de bienfaisance; contribution politique; reçu de don; reçu officiel; cadeaux

L’onglet **Dons** présente chaque paiement dans une catégorie dont le traitement fiscal est dons de bienfaisance ou contributions politiques (comme les catégories **Dons de bienfaisance** et **Contributions politiques**), ou dans une ligne de ventilation marquée ainsi, daté dans l’année d’imposition. Les dons de chaque personne sont présentés à part, selon le champ **Pour** de la ligne d’opération.

Les époux ou conjoints de fait peuvent demander les dons l’un de l’autre ; les regrouper sur une seule déclaration donne habituellement un crédit plus élevé, et les dons peuvent être reportés jusqu’à cinq ans.

En haut, une carte par personne donne :

- **Dons de bienfaisance** : le total admissible des dons de bienfaisance.
- **Contributions politiques** : le total admissible des contributions politiques, s’il y en a.
- **Reçus manquants** : le nombre de dons encore sans reçu, en rouge.

Les totaux ne comptent que les dons en dollars canadiens.

Sous les cartes, chaque don affiche sa date, la personne, l’organisme (ou le bénéficiaire), sa sorte et l’état du reçu, et son montant :

- **Reçu obtenu** : marqué reçu dans sa fenêtre, ou un fichier est joint à l’opération.
- **Sur le feuillet T4 (case 46)** : un don retenu sur la paie (voir [Paie selon le talon de paie](taxes#pay-stub)) ; le T4 tient lieu de reçu. Un tel don affiche l’organisme tiré de la note de la ligne, par exemple « Centraide, par la paie de Acme ».
- **Reçu manquant** : en rouge.
- **Admissible …** : affiché sous le montant quand le montant admissible du reçu est inférieur au don.

Un remboursement d’un don dans la même opération le réduit ; un don qui revient à zéro n’est pas affiché. Cliquez sur un don pour saisir son reçu.

### Reçu officiel {#official-receipt}

@index: numéro d’enregistrement; numéro d’organisme; montant admissible; avantage

La fenêtre affiche la date, le bénéficiaire et le montant du don, puis :

- **Organisme ou parti, comme sur le reçu** : le nom à afficher et à donner au comptable. Il est d’abord le bénéficiaire (ou, pour un don par la paie, la note de la ligne).
- **Numéro d’enregistrement** : pour les dons de bienfaisance seulement. Neuf chiffres, RR et quatre chiffres, par exemple 123456789RR0001 ; les espaces et les traits d’union sont retirés. Toute autre forme est refusée. Facultatif.
- **Numéro du reçu** : tel qu’imprimé. Facultatif.
- **Montant admissible** : si vous avez reçu quelque chose en retour (un souper, un lot d’encan), le reçu indique un montant admissible moindre : entrez-le. Laissez vide si tout le don est admissible. Il ne peut pas dépasser le don : les lignes de don de l’opération, donc pour un don par la paie le montant donné, et non tout le dépôt. C’est le montant admissible que comptent les totaux et la trousse de fin d’année.
- **Reçu obtenu** : cochez quand vous avez le reçu. La case est cochée d’avance quand un fichier est déjà joint à l’opération.
- **Fichiers du reçu** : le reçu lui-même. **Joindre un fichier…** conserve un fichier dans le coffre et le lie à l’opération ; **Depuis la boîte de révision** lie un document qui y attend. Ces fichiers vont dans le dossier pour le comptable.
- **Enregistrer** : enregistre les détails du reçu. **Annuler** : ferme sans les enregistrer.

Chaque don a ses propres détails de reçu : quand un même paiement contient des dons pour deux personnes (ou un don de bienfaisance et une contribution politique), chacun paraît sur sa propre ligne ici et garde son propre reçu. Un reçu inscrit avec une version antérieure pour tout le paiement vaut encore pour les dons qui n’ont pas le leur, son montant admissible étant réparti entre eux en proportion de chaque don, pour n’être compté qu’une fois. L’enregistrement exige le droit de modifier le groupe du compte.

## Acomptes {#instalments}

@index: acomptes provisionnels; paiements par acomptes; impôt trimestriel; rappel de l’ARC; Revenu Québec; instalments

Certaines personnes paient leur impôt pendant l’année par acomptes plutôt qu’à la production de la déclaration : habituellement celles dont l’impôt à payer à la production dépasse 3 000 $ (1 800 $ au Québec) cette année et l’une des deux années précédentes, comme les retraités ou les travailleurs autonomes. L’ARC et Revenu Québec envoient des rappels avec les montants. Les acomptes sont dus les 15 mars, 15 juin, 15 septembre et 15 décembre ; un paiement fait le jour ouvrable suivant une fin de semaine ou un jour férié est à temps.

L’onglet **Acomptes** énumère les acomptes de l’année d’imposition, regroupés par personne et par autorité, par exemple « Jean · Agence du revenu du Canada ». Cliquez sur l’en-tête, ou sur **Modifier** à côté, pour les changer. Chaque acompte affiche :

- sa date d’échéance ;
- son état : **Payé**, **Payé en partie** (avec la somme payée jusqu’ici, par exemple « 400,00 $ payé »), **À payer**, ou **En retard** en rouge quand la date est passée et qu’il n’est pas entièrement payé ;
- son montant.

- **Prévoir des acomptes…** : saisit un nouveau calendrier. Voir [Acomptes pour une année](taxes#instalment-window).

« Aucun acompte pour cette année. » signifie qu’aucun n’a été saisi pour l’année affichée.

Les acomptes non entièrement payés qui sont dus d’ici 30 jours, ou en retard d’au plus 30 jours, paraissent parmi les rappels de l’application sous « acompte provisionnel », avec l’autorité, et mènent à cet écran.

### Acomptes pour une année {#instalment-window}

La fenêtre « Acomptes pour » l’année :

- **Personne** : pour qui sont les acomptes, ou **Ménage**.
- **Versés à** : **Agence du revenu du Canada** ou **Revenu Québec**. Une personne au Québec a habituellement deux calendriers, un pour chacun.
- **Payés depuis** : le compte bancaire d’où sortent les paiements. Seuls les comptes bancaires sont offerts. Obligatoire.
- **Dû le 15 mars …**, **Dû le 15 juin …**, **Dû le 15 septembre …**, **Dû le 15 décembre …** : les quatre montants du rappel, sans signe moins. Un montant vide ou nul retire cette date.
- **Enregistrer** : enregistre le calendrier. Changer la personne, l’autorité ou le compte d’un calendrier existant le déplace : l’ancien est remplacé. **Annuler** : ferme.

Pour retirer un calendrier, ouvrez-le, videz les quatre montants et enregistrez. L’enregistrement exige le droit de modifier le groupe du compte.

### Comment les paiements comptent {#instalment-payments}

@index: catégorie Acomptes provisionnels

Les paiements comptent pour un calendrier quand ils sont :

- dans la catégorie **Acomptes provisionnels**,
- dans le compte choisi dans **Payés depuis**,
- datés du 1er janvier de l’année d’imposition au 31 janvier de l’année suivante,
- pour la même personne, ou pour personne en particulier,
- et à la bonne autorité : un paiement compte pour Revenu Québec quand son bénéficiaire le nomme (« Revenu Québec », « RQ » ou « ministère du Revenu »), et pour l’ARC autrement.

Les paiements comptent d’abord pour les acomptes les plus anciens. Les sommes payées vont dans la trousse de fin d’année sous **Acomptes payés**.

## Trousse de fin d’année {#year-end-package}

@index: trousse fiscale; sommaire fiscal; comptable; T1; montants de la déclaration; numéros de ligne

L’onglet **Trousse de fin d’année** présente les montants de chaque personne pour la déclaration, tirés des livres, avec la ligne fédérale de chacun. Choisissez l’**Année d’imposition** et la **Personne** ; le ménage (les montants qui ne sont à personne en particulier) vient en dernier.

Chaque ligne affiche l’élément, sa provenance (un payeur, un compte, une période), la ligne ou le formulaire, et le montant. Les lignes de même provenance sont additionnées. Les montants dans d’autres devises sont convertis en dollars canadiens à la date de chaque opération. Les opérations propres aux régimes enregistrés sont exclues.

« Rien pour cette année encore » signifie qu’aucune paie, déduction, aucun crédit ni feuillet n’a été inscrit pour l’année.

### Ce que regroupe chaque section {#package-sections}

@index: revenus d’emploi; RPC; RRQ; AE; RQAP; déduction REER; déduction CELIAPP; cotisations syndicales; frais de garde; frais de déménagement; frais médicaux; frais de scolarité; impôt retenu; travail autonome; T2125; impôt étranger; dividendes imposables

- Emploi : **Revenus d’emploi** (ligne 10100) tirés du salaire, des primes et des commissions ; **Cotisations au RPC ou au RRQ** (ligne 30800) et **Cotisations d’AE** (ligne 31200) retenues sur la paie. Au Québec, les cotisations d’AE et au RQAP partagent une catégorie et paraissent sous **Cotisations d’AE et au RQAP**, sans ligne fédérale unique.
- Rentes et prestations : **Pension de la Sécurité de la vieillesse** (11300), **Prestations du RPC ou du RRQ** (11400), **Autres pensions** (11500), **Revenus d’un FERR**, **Prestations d’assurance-emploi** (11900).
- Placements : **Dividendes imposables** (12000, les montants majorés des cases 11 et 25 du T5 et 32 et 50 du T3), **Intérêts et autres revenus de placement** (12100, tirés des feuillets et de la catégorie **Intérêts**), **Gains en capital imposables** (12700 : la moitié des gains nets, y compris les gains en capital des feuillets, s’ils sont positifs), **Impôt étranger payé** (formulaire T2209). Ces montants viennent du rapport des revenus de placement : voir [Revenus de placement et gains en capital](reports#investment-income).
- Travail autonome : **Revenus de travail autonome** et **Dépenses de travail autonome** (formulaire T2125), tirés des catégories dont le traitement fiscal est travail autonome ; **Taxes de vente payées sur les dépenses d’entreprise**, par taxe, tirées des taxes de vente inscrites sur ces dépenses (voir [Taxes de vente incluses](taxes#sales-tax)).
- Déductions : **Cotisations au régime de retraite** (20700), **Cotisations REER** (20800, tirées des cotisations aux REER et des retenues de REER collectif sur la paie), **Cotisations CELIAPP** (20805), **Cotisations syndicales et professionnelles** (21200), **Frais de garde d’enfants** (21400), **Frais de déménagement** (21900), **Autres dépenses d’emploi** (22900).
- Crédits : **Frais médicaux** (33099) comme le rapport Frais médicaux les demande : les frais des conjoints et des enfants ensemble, sur la meilleure période de 12 mois du ménage, dans la trousse du ménage, puisqu’un seul conjoint les demande tous ; et **Frais médicaux d’une personne à charge adulte** (33199), une ligne par personne à charge adulte sur sa propre meilleure période, aussi dans la trousse du ménage. Les reçus médicaux de ces périodes accompagnent la trousse du ménage dans le dossier pour le comptable. Puis **Frais de scolarité** (annexe 11), **Dons de bienfaisance** (annexe 9) et **Contributions politiques** (40900), à leurs montants admissibles.
- Impôt déjà payé : **Impôt retenu** (43700, l’impôt retenu sur la paie ; l’impôt payé à la production ne compte pas) et **Acomptes payés** (47600, par autorité).

La personne de chaque montant est celle pour qui est la ligne d’opération, ou sinon le seul titulaire du compte ; autrement, le montant va au ménage.

### Les notes sous la trousse {#package-notes}

Sous les lignes, et à la fin de chaque exportation, des notes indiquent ce qui manque encore et ce qu’il faut vérifier :

- Les feuillets encore attendus : ceux de cette personne toujours marqués **Attendu** dans l’onglet **Feuillets**, à réclamer avant de produire la déclaration.
- Pour les cotisations REER : la déduction d’une année vise les cotisations du 2 mars de cette année au 1er mars de la suivante ; celles des 60 premiers jours de l’année ont pu être déduites l’année précédente.
- Pour une personne au Québec : la déclaration du Québec a ses propres lignes ; les Relevés en donnent les montants.
- L’avis que ces montants ne sont pas des conseils fiscaux.

### Dossier pour le comptable {#accountant-folder}

@index: trousse pour le comptable; exporter la trousse fiscale; envoyer au comptable

**Dossier pour le comptable…** demande un dossier, puis y crée un dossier « Trousse fiscale » suivi de l’année. On y trouve, pour chaque personne de la trousse (pas seulement celle affichée) :

- un PDF et un fichier Excel du sommaire de la personne, à son nom ;
- un dossier à son nom, avec une copie de chaque document derrière les montants : les feuillets joints dans l’onglet **Feuillets**, les reçus de dons joints aux dons, et les reçus des frais médicaux comptés.

Le dossier s’ouvre une fois terminé, et une ligne indique où il a été enregistré. Le refaire remplace les fichiers du même nom.

### Exporter un sommaire {#export-summary}

Les boutons **CSV**, **Excel** et **PDF** à côté de **Dossier pour le comptable…** enregistrent le sommaire de la personne affichée seulement, avec ses notes, après avoir demandé où. Les formats sont ceux des rapports : voir [Le tableau, l’exportation et l’impression](reports#table-export).

## Estimation {#estimate}

@index: estimation de l’impôt sur le revenu; estimation d’impôt; remboursement; solde dû; combien d’impôt; calculatrice d’impôt; taux marginal; taux moyen

L’onglet **Estimation** calcule à peu près l’impôt sur le revenu qu’une personne paiera pour une année, à partir des montants de la trousse de fin d’année et des taux de sa province ou de son territoire, et le compare à l’impôt déjà retenu sur la paie et payé par acomptes. Il montre le résultat, chaque étape du calcul et l’origine de chaque montant. Vous pouvez changer n’importe quel montant pour en voir l’effet, par exemple pour essayer une cotisation REER avant la date limite.

> Important : Il s’agit d’une estimation, pas d’une déclaration ni d’un conseil fiscal. Elle applique les principales règles aux montants affichés et en laisse certaines de côté (voir [Ce que l’estimation laisse de côté](taxes#estimate-left-out)). Votre déclaration, votre logiciel d’impôt ou votre comptable, et votre avis de cotisation ont le dernier mot.

En haut :

- **Année d’imposition** : l’année à estimer. Elle est au départ l’année qu’on prépare (l’an dernier jusqu’à la fin de juin, puis l’année en cours) ; l’année en cours et les six précédentes sont offertes. Les taux intégrés commencent en 2024 : pour une année antérieure, l’onglet indique qu’il n’y a pas de taux.
- **Personne** : la personne dont on estime l’impôt. Les adultes et les adultes à charge du ménage sont offerts, ainsi que toute autre personne qui a des montants dans la trousse. Chacun produit sa propre déclaration, donc chacun a sa propre estimation.
- **Reprendre les montants des livres** : affiché dès que vous avez changé un montant ou la case de l’âge ; remet chaque montant tel que les livres l’indiquent.

La ligne rouge en dessous rappelle qu’il s’agit d’une estimation. « Personne à estimer pour l’instant » signifie que le ménage n’a aucun membre adulte : ajoutez-les dans [Membres du ménage](members).

La partie gauche énumère les montants utilisés ; la partie droite montre le résultat et le calcul.

### Montants utilisés {#estimate-figures}

@index: données de l’estimation; remplacer les montants fiscaux

Sous **Montants utilisés**, une ligne indique de quels taux il s’agit : ceux de la province ou du territoire où vit la personne (son **Habite au ou en** dans Membres du ménage), sinon ceux du ménage. Ensuite :

- **65 ans ou plus au 31 décembre** : cochée quand la date de naissance de la personne indique 65 ans ou plus à la fin de l’année. Elle donne le montant en raison de l’âge et fait compter les revenus d’un FERR comme revenus de pension. Changez-la quand la date de naissance n’est pas entrée.

Chaque montant est en dollars, avec une ligne en dessous qui en indique l’origine : **De la trousse de fin d’année** et les éléments de la trousse additionnés, **Absent des livres** quand les livres n’en ont pas la source, ou **Entré ici, non enregistré** dès que vous le changez. **Reprendre les livres** à côté d’un montant changé remet celui des livres. Ce que vous entrez ici n’est pas enregistré, ne change pas les livres et est oublié quand vous choisissez une autre personne ou une autre année ou que vous quittez l’écran. Un montant vide compte pour zéro. Un montant peut être une somme, comme 1200 + 350.

Les montants sont regroupés comme dans une déclaration.

Revenus :

- **Revenus d’emploi** : de **Revenus d’emploi** dans la trousse (salaire, traitement, primes des talons de paie et des catégories de revenus).
- **Revenus de pension admissibles au montant pour revenu de pension** : **Autres pensions** (une pension d’employeur), plus **Revenus d’un FERR** dès 65 ans. Ils donnent le montant pour revenu de pension.
- **Autres revenus (SV, RPC ou RRQ, AE, retraits de régimes)** : **Pension de la Sécurité de la vieillesse**, **Prestations du RPC ou du RRQ** et **Prestations d’assurance-emploi**, plus **Revenus d’un FERR** avant 65 ans. Ajoutez-y tout autre revenu imposable que les livres ne montrent pas, comme des retraits d’un REER.
- **Intérêts et autres revenus de placement** : de **Intérêts et autres revenus de placement**.
- **Dividendes déterminés (montant imposable)** et **Autres dividendes (montant imposable)** : les montants majorés des feuillets T5 et T3 (cases 25 et 11 du T5, cases 50 et 32 du T3), tirés du rapport des revenus de placement. Ils donnent le crédit d’impôt pour dividendes.
- **Gains en capital imposables** : de **Gains en capital imposables** (la moitié des gains nets de l’année).
- **Revenus de travail autonome, nets des dépenses** : **Revenus de travail autonome** moins **Dépenses de travail autonome** ; le montant peut être négatif.

Déductions :

- **Déduction pour REER** : de **Cotisations REER**. La déduction ne peut dépasser le plafond de déduction REER de l’avis de cotisation ; entrez un montant moindre au besoin.
- **Déduction pour CELIAPP** : de **Cotisations CELIAPP**.
- **Cotisations à un régime de pension agréé** : de **Cotisations au régime de retraite** retenues sur la paie.
- **Cotisations syndicales et professionnelles** : de **Cotisations syndicales et professionnelles**.
- **Frais de garde d’enfants** : de **Frais de garde d’enfants**. C’est habituellement le conjoint au revenu le plus bas qui les déduit, dans des limites par enfant ; entrez le montant déductible.
- **Autres déductions (déménagement, dépenses d’emploi)** : **Frais de déménagement** et **Autres dépenses d’emploi**.

Crédits :

- **Cotisations au RPC ou au RRQ** : de **Cotisations au RPC ou au RRQ** des talons de paie. La partie de base donne un crédit ; la partie bonifiée et la deuxième cotisation sont déduites (voir [Le calcul](taxes#estimate-lines)).
- **Cotisations d’AE (et au RQAP au Québec)** : de **Cotisations d’AE**, ou **Cotisations d’AE et au RQAP** au Québec.
- **Dons de bienfaisance** : de **Dons de bienfaisance**, à leur montant admissible. Les conjoints peuvent demander les dons l’un de l’autre : entrez ici les dons que cette personne demandera.
- **Frais médicaux demandés** : non rempli, parce que les frais médicaux du ménage sont demandés ensemble par un des conjoints. Une ligne sous le champ donne le montant du ménage tiré de la trousse ; entrez-le pour le conjoint qui le demande. L’estimation applique elle-même le seuil (3 % du revenu net ou le montant fixe) : entrez donc les frais eux-mêmes.
- **Revenu net de l’époux ou du conjoint** : vide, aucun montant pour époux n’est demandé. Entrez le revenu net d’un époux ou d’un conjoint de fait pour demander le montant pour époux, qui diminue à mesure que son revenu augmente. Au Québec, il s’ajoute au revenu de la personne pour former le revenu familial qui réduit les montants pour l’âge et pour revenus de retraite et fixe le seuil des frais médicaux.

Impôt déjà payé :

- **Impôt retenu** : de **Impôt retenu** (l’impôt retenu sur la paie, fédéral et du Québec ensemble).
- **Acomptes payés** : de **Acomptes payés** à l’ARC et à Revenu Québec.

### Le résultat {#estimate-result}

@index: solde dû; estimation du remboursement; taux d’imposition moyen; taux d’imposition marginal

L’encadré en haut à droite donne :

- **Impôt fédéral** : l’impôt fédéral après les crédits (après l’abattement du Québec pour un résident du Québec).
- L’impôt provincial ou territorial, au nom de la province ou du territoire : après les crédits, avec toute surtaxe, réduction ou contribution-santé.
- **Impôt sur le revenu total** : les deux ensemble.
- **Retenues à la source et acomptes** : **Impôt retenu** et **Acomptes payés**.
- **Solde dû** ou **Remboursement** : l’impôt total moins ce qui est déjà payé. Un solde dû doit être payé au plus tard le 30 avril de l’année suivante.
- **Taux moyen** : l’impôt total en proportion du revenu total.
- **Taux marginal** : l’impôt sur un dollar de plus de revenu ordinaire (comme des intérêts), fédéral et provincial ensemble ; c’est, en gros, ce qu’une déduction REER fait économiser sur chaque dollar.

Quand les taux de l’année ne sont pas encore dans Taux et règles, une ligne rouge indique que ceux de la dernière année connue sont utilisés.

### Le calcul {#estimate-lines}

@index: tranches d’imposition; montant personnel de base; crédits non remboursables; surtaxe; contribution-santé de l’Ontario; abattement du Québec

Sous le résultat, le calcul ligne par ligne, en trois parties. Chaque ligne montre son montant et, sous son nom, comment il a été calculé (un taux d’un montant, ou le montant sur lequel il repose).

Revenus :

- **Revenu total** : tous les revenus additionnés.
- **Cotisations bonifiées au RPC ou au RRQ (déduction)** : la partie des cotisations qui est déduite plutôt que créditée.
- **Déductions** : les déductions, avec cette partie.
- **Revenu net** et **Revenu imposable** : le revenu total moins les déductions (jamais sous zéro). L’estimation ne compte aucune autre déduction : les deux sont donc égaux.

Le fédéral, puis la province ou le territoire :

- **Tranche d’imposition** : une ligne par tranche atteinte, avec son taux, le revenu imposé à ce taux et le début de la tranche.
- **Impôt sur le revenu imposable** : les tranches additionnées.
- Les montants donnant droit aux crédits : **Montant personnel de base** (réduit aux revenus élevés quand les règles le prévoient), **Montant en raison de l’âge** (dès 65 ans, réduit au-delà d’un seuil de revenu), **Montant supplémentaire pour aînés** (Saskatchewan), **Montant pour époux ou conjoint de fait**, **Montant canadien pour emploi** (fédéral et Yukon), **Cotisations au RPC ou au RRQ (partie de base)**, **Cotisations d’AE**, **Montant pour revenu de pension** et **Frais médicaux au-delà du seuil**.
- **Total des montants donnant droit aux crédits**, et **Crédits d’impôt non remboursables** : ce total au taux le plus bas.
- **Crédit d’impôt complémentaire ou supplémentaire** : le crédit complémentaire fédéral, qui maintient 15 % sur les montants au-delà de la première tranche depuis la baisse du taux le plus bas en 2025, ou le crédit supplémentaire de l’Alberta sur les montants au-delà de sa tranche à 8 %.
- **Crédit d’impôt pour dons** : les premiers 200 $ au taux le plus bas, le reste à un taux plus élevé et, quand les règles en prévoient un, à un taux encore plus élevé sur les dons correspondant au revenu de la tranche supérieure.
- **Crédit d’impôt pour dividendes** : une part du montant imposable des dividendes déterminés et des autres dividendes.
- **Impôt après les crédits** : jamais sous zéro, puisque ces crédits ne sont pas remboursables.
- **Abattement du Québec remboursable** : pour un résident du Québec, 16,5 % de l’impôt fédéral après les crédits.
- **Surtaxe** et **Réduction d’impôt** : la surtaxe de l’Ontario sur son impôt au-delà de deux seuils, la réduction d’impôt de l’Ontario et la réduction d’impôt pour faible revenu de la Colombie-Britannique.
- **Contribution-santé** : la contribution-santé de l’Ontario, par paliers de revenu imposable.
- **Impôt** : l’impôt fédéral, ou provincial ou territorial.

### Résidents du Québec {#estimate-quebec}

@index: estimation de l’impôt du Québec; déduction pour travailleur; estimation Revenu Québec

Une personne qui habite au Québec le 31 décembre paie l’impôt fédéral, réduit de l’abattement du Québec, et l’impôt du Québec calculé selon les règles du Québec :

- **Déduction pour travailleur** : 6 % des revenus d’emploi, jusqu’à un maximum, déduits du revenu pour l’impôt du Québec.
- Les tranches et les crédits du Québec, à son propre taux. Le RRQ, l’AE et le RQAP ne donnent aucun crédit au Québec, puisque le montant personnel de base en tient déjà compte.
- Le montant en raison de l’âge et le montant pour revenus de retraite sont réduits ensemble selon le revenu familial (celui de la personne et de son conjoint).
- Le crédit pour frais médicaux est de 20 % des frais qui dépassent 3 % du revenu familial.
- Le RRQ remplace le RPC : la partie de base des cotisations donne un crédit fédéral, et le reste une déduction dans les deux déclarations.

### Ce que l’estimation laisse de côté {#estimate-left-out}

L’estimation ne compte pas : les frais de scolarité, les montants transférés d’un conjoint ou d’un enfant, les reports (frais de scolarité, dons ou pertes inutilisés), les réductions d’impôt pour faible revenu autres que celles de l’Ontario et de la Colombie-Britannique, les crédits et prestations remboursables (crédit pour la TPS/TVH, Allocation canadienne pour les travailleurs, crédit d’impôt pour solidarité du Québec), l’impôt de récupération de la SV, l’impôt minimum de remplacement, les contributions politiques, les crédits pour impôt étranger, le montant canadien pour aidant naturel et le montant pour personne à charge admissible, le montant pour personne vivant seule du Québec, sa cotisation au Fonds des services de santé et sa prime d’assurance médicaments, et les suppléments de 2024 de la Nouvelle-Écosse aux montants pour époux et pour l’âge. Au Yukon, le montant pour époux n’est pas réduit avec le montant personnel de base aux revenus élevés. Ces éléments peuvent changer le résultat : c’est la déclaration qui compte.

### D’où viennent les taux {#estimate-rates}

@index: taux d’impôt sur le revenu; taux d’impôt par province; changements de taux

Chaque taux, montant et seuil utilisé par l’estimation (tranches, montants personnels de base, taux des crédits, montants pour l’âge, pour époux, pour revenu de pension et pour emploi, seuils des frais médicaux, taux des crédits pour dons et pour dividendes, surtaxe et contribution-santé de l’Ontario, RPC et RRQ, déduction pour travailleur du Québec) est une valeur de [Taux et règles](rates-rules), sous **Impôt sur le revenu**, par date et par province ou territoire. L’application fournit les chiffres officiels de 2024, 2025 et 2026, chacun avec sa source. Un administrateur peut y ajouter une valeur pour une année ou une province, par exemple quand un budget change un taux ; l’estimation l’utilise à partir de sa date. Une année dont les chiffres n’y sont pas encore utilise les derniers connus.

## Pendant l’année : ce qui alimente les écrans fiscaux {#through-the-year}

@index: traitement fiscal; préparation des impôts toute l’année

L’écran Impôts ne fait que lire les livres. Voici les endroits où ce que vous inscrivez pendant l’année le change.

### Paie selon le talon de paie {#pay-stub}

@index: talon de paie; relevé de paie; paie; chèque de paie; paie brute; paie nette; retenues à la source

Inscrire la paie d’après son talon met dans les livres la paie brute et chaque retenue, et non seulement le dépôt net : la trousse de fin d’année a ainsi les revenus d’emploi, le RPC ou le RRQ, l’AE ou le RQAP, l’impôt retenu, le régime de retraite et les cotisations syndicales, et la liste des feuillets attend le T4.

Ouvrez-la avec **Talon de paie…** sous une nouvelle opération dans le registre d’un compte bancaire (voir [Comptes](accounts)), ou avec **Inscrire la paie…** sur un talon de paie dans Documents, où les champs sont remplis d’après une lecture par IA quand le talon a été lu par l’IA, et tapés à la main sinon (voir [Documents](documents)).

- **Employeur** : l’employeur, qui sera le bénéficiaire. Obligatoire. La liste des feuillets attend un T4 de ce nom.
- **Date de paie** : la date du dépôt, au format AAAA-MM-JJ. Par défaut la date du jour, ou la date lue sur le talon.
- **Pour** : la personne payée. Par défaut **(le ménage)** ; choisissez la personne pour que la paie, les retenues et les feuillets aillent à la bonne déclaration.
- **Déposée dans** : affiché seulement depuis Documents : le compte bancaire où la paie a été déposée.
- Gains : une ligne par ligne de gains du talon (salaire, heures supplémentaires, paie de vacances, prime).
  - **Description** : telle qu’imprimée. Une description qui contient bonus, commission, prime, incentive ou gratification va dans **Primes et commissions** ; toute autre dans **Salaire**.
  - **Montant** : la somme gagnée.
  - **✕** retire une ligne (une ligne reste toujours). **Ajouter un gain** ajoute une ligne.
- Retenues : une ligne par somme retenue sur la paie. Un nouveau talon commence avec **Impôt sur le revenu**, **RPC / RRQ** et **AE / RQAP**.
  - **Type** : **Impôt sur le revenu**, **RPC / RRQ**, **AE / RQAP**, **Cotisations syndicales**, **Régime de retraite**, **Assurance collective**, **REER collectif**, **Don de bienfaisance** ou **Autre**. Il choisit la catégorie : **Impôt sur le revenu**, **Cotisations au RPC / RRQ**, **Cotisations d’AE / RQAP**, **Cotisations syndicales et professionnelles**, **Cotisations au régime de retraite**, **Assurance collective**, **Cotisations au REER collectif**, **Dons de bienfaisance** ou **Autres retenues sur la paie**.
  - **Description** : facultative, conservée comme note de la ligne. Pour une retenue **Don de bienfaisance**, entrez l’organisme (Centraide, United Way) : il paraît dans l’onglet **Dons**.
  - **Montant** : sans signe moins. Une ligne laissée vide est ignorée.
  - **✕** retire une ligne. **Ajouter une retenue** ajoute une ligne de type **Autre**.
- La ligne sous les lignes affiche « Brut … − retenues … = net … », le dépôt qui sera inscrit. Quand le talon a été lu par IA et que sa paie nette imprimée diffère, une ligne rouge le signale : vérifiez les montants ou ajoutez une ligne manquante.
- **Enregistrer** : inscrit un seul dépôt de la paie nette, ventilé en chaque ligne de gains et chaque retenue (en ligne négative). Depuis Documents, le talon est classé avec le dépôt. **Enregistrer** est disponible dès qu’un montant de gains est entré et qu’un compte est connu. **Annuler** : ferme.

Le dépôt doit être supérieur à zéro : des retenues qui égalent ou dépassent la paie brute sont refusées.

### Taxes de vente incluses {#sales-tax}

@index: TPS; TVH; TVQ; TVP; taxes de vente; crédit de taxe sur les intrants; GST; QST

**Taxes de vente…**, sous une opération existante dans un registre (pas un virement ni une opération de placement), inscrit la TPS, la TVH, la TVQ ou la TVP indiquée sur le reçu.

- **TPS**, **TVH**, **TVQ**, **TVP** : les montants du reçu, sans signe moins. Laissez les autres vides.
- **Enregistrer** : les enregistre avec l’opération. **Annuler** : ferme.

Le montant de l’opération ne change pas. Pour les dépenses dont la catégorie a le traitement fiscal travail autonome, la trousse de fin d’année additionne les taxes de vente sous **Taxes de vente payées sur les dépenses d’entreprise**, par taxe, qu’une entreprise inscrite peut demander comme crédits de taxe sur les intrants. Laissez vide autrement.

### Traitement fiscal des catégories {#tax-treatment}

@index: indicateur fiscal; catégorie fiscale; déductible

Chaque catégorie a un **Traitement fiscal** (voir [Catégories](categories)) : frais médicaux, dons de bienfaisance, contributions politiques, frais de garde d’enfants, frais de scolarité, travail autonome, dépenses d’emploi ou frais de déménagement. L’onglet Dons, la trousse de fin d’année et les frais médicaux s’en servent. Les paiements dans une catégorie au bon traitement sont repérés automatiquement ; vérifiez les nouvelles catégories que vous créez.

### Feuillets de placement et gains en capital {#investment-slips}

Les montants des T5 et T3, et les gains en capital, sont calculés dans le rapport Revenus de placement et gains en capital, où chaque feuillet peut être saisi d’après le feuillet à son arrivée, pour que ses cases remplacent l’estimation : voir [Revenus de placement et gains en capital](reports#investment-income) et [Saisir un feuillet](reports#enter-slip).

### Frais médicaux et choix du conjoint {#medical-claim}

@index: crédit pour frais médicaux; quel conjoint

Les frais médicaux saisis dans l’écran Réclamations médicales (voir [Réclamations médicales](medical)) donnent les lignes **Frais médicaux** de la trousse du ménage, les mêmes montants que le rapport Frais médicaux. Le rapport trouve la meilleure période de 12 mois, produit un seul PDF des reçus et aide à choisir quel conjoint devrait demander le crédit : voir [Frais médicaux](reports#medical-expenses) et [Quel conjoint devrait demander le crédit](reports#who-claims).

### Cotisations aux régimes enregistrés {#plan-contributions}

Les cotisations REER et CELIAPP de la trousse sont l’argent versé dans ces régimes depuis vos autres comptes, par la personne dont elles utilisent les droits, comme dans l’écran Régimes enregistrés : voir [Régimes enregistrés](plans).

## Notions fiscales canadiennes et québécoises {#tax-notions}

@index: ARC; Agence du revenu du Canada; Revenu Québec; Relevé; avis de cotisation; crédit non remboursable

Quelques mots employés sur cet écran, en bref :

- L’ARC et Revenu Québec : l’Agence du revenu du Canada perçoit l’impôt fédéral partout ; les personnes qui habitent au Québec le 31 décembre produisent aussi une déclaration du Québec auprès de Revenu Québec.
- Feuillets et Relevés : les feuillets T (T4, T5 et autres) déclarent les revenus pour la déclaration fédérale ; au Québec, les Relevés correspondants (Relevé 1, Relevé 3 et autres) les déclarent pour la déclaration du Québec.
- Déduction et crédit : une déduction (REER, cotisations syndicales, frais de garde) réduit le revenu imposable ; un crédit (frais médicaux, dons, frais de scolarité) réduit l’impôt lui-même. La plupart des crédits ne sont pas remboursables : ils ne peuvent pas faire descendre l’impôt sous zéro.
- Acomptes provisionnels : l’impôt payé pendant l’année par les personnes à qui peu ou pas d’impôt est retenu à la source.
- Avis de cotisation : la réponse de l’ARC à une déclaration, avec le maximum déductible au titre des REER de l’année suivante.

L’ARC et Revenu Québec publient les règles chaque année ; les montants et les lignes indiqués ici suivent la déclaration fédérale.
