# Véhicules et entretien
@about: Ajoutez un véhicule avec ses renouvellements, donnez-lui un calendrier d’entretien, et inscrivez chaque entretien fait.

## Ouvrir Véhicules {#open}
@screen: VEHICLES
@done: screen
@manual: vehicles#overview

Dans le menu, ouvrez **Maison et famille**, puis **Véhicules**.

## Ajouter un véhicule {#add}
@screen: VEHICLES
@target: vehicles.add
@done: shown vehicle.dialog
@manual: vehicles#vehicle-form

Cliquez sur **Ajouter un véhicule**.

## Le décrire {#details}
@target: vehicle.dialog.save
@done: added vehicle
@manual: vehicles#details

- **Nom** : comment vous l’appelez, comme Civic.
- **Marque**, **Modèle** et **Année** ; **Énergie** : **Électrique** remplace les pleins par des recharges et retire les tâches liées au moteur.
- **Renouvellement de l’immatriculation** et **Renouvellement de l’assurance** : un rappel vous est donné avant chacun.

Cliquez sur **Enregistrer**.

## L’odomètre {#odometer}
@screen: VEHICLES VehicleTab.OVERVIEW
@target: vehicles.addReading
@manual: vehicles#enter-odometer

Cliquez sur **Inscrire l’odomètre** et tapez la lecture du jour. Les lectures gardent justes les échéances d’entretien et les prévisions ; le téléphone peut aussi les envoyer, avec **Odomètre ou heures**.

## Un calendrier d’entretien {#tasks}
@screen: VEHICLES VehicleTab.MAINTENANCE
@target: vehicles.starterTasks
@manual: vehicles#usual-tasks

À l’onglet **Entretien**, cliquez sur **Ajouter les tâches habituelles** : vidanges d’huile, permutation des pneus, pose et retrait des pneus d’hiver, freins et filtres, chacune avec son intervalle en mois ou en kilomètres. Modifiez ou retirez celles que vous voulez, ou utilisez **Ajouter une tâche** pour les vôtres.

## Inscrire un entretien {#service}
@screen: VEHICLES VehicleTab.MAINTENANCE
@target: vehicles.markDone
@done: shown vehicle.service
@manual: vehicles#record-done

Quand une tâche est faite, cliquez sur **Inscrire comme faite** sur celle-ci.

## L’entretien {#service-form}
@target: vehicle.service.save
@manual: vehicles#service-form

Vérifiez la **Date** et l’**Odomètre (km)**, cochez les **Tâches faites**, inscrivez le **Garage ou fournisseur** et le **Coût**, et cliquez sur **Enregistrer**. Le calendrier de chaque tâche cochée recommence, et l’entretien paraît à l’onglet **Carnet d’entretien**.
