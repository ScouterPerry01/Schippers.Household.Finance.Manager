# Services publics et compteurs
@about: Suivez votre consommation d’électricité, de gaz et d’eau mois par mois, et vos réservoirs de combustible avant qu’ils baissent trop.

## Ouvrir Services publics {#open}
@screen: UTILITIES UtilitiesTab.METERS
@done: screen
@manual: utilities#screen

Dans le menu, ouvrez **Maison et famille**, puis **Services publics**. L’onglet **Compteurs** suit l’électricité, le gaz et l’eau ; l’onglet **Réservoirs** suit le propane ou le mazout.

## Ajouter un compteur {#add}
@screen: UTILITIES UtilitiesTab.METERS
@target: meters.add
@done: shown meter.dialog
@manual: utilities#meter-dialog

Cliquez sur **Ajouter un compteur**.

## Décrire le compteur {#meter}
@target: meter.dialog.save
@manual: utilities#meter-dialog

- **Nom** : comme Électricité de la maison.
- **Mesure** : **Électricité**, **Gaz naturel** ou **Eau**.
- **Relevés selon l’heure** : cochez-le pour un compteur d’électricité avec des totaux en période de pointe, de mi-pointe et hors pointe.
- **Facture pour le coût unitaire** : votre facture d’électricité ou de gaz, pour que chaque mois montre à peu près ce qu’il a coûté.

Cliquez sur **Enregistrer**.

## Inscrire un relevé {#reading}
@screen: UTILITIES UtilitiesTab.METERS
@target: meters.readings
@done: shown meter.reading
@manual: utilities#readings

Cliquez sur **Relevés** sur la carte du compteur.

## Le chiffre du compteur {#number}
@target: meter.reading.save
@manual: utilities#readings

Tapez la **Date** et le chiffre lu sur le compteur, puis cliquez sur **Ajouter le relevé**. Avec deux relevés ou plus, chaque mois montre sa consommation, le même mois l’an dernier, et **Inhabituel** en rouge quand il a consommé beaucoup plus.

Les relevés d’une facture peuvent aussi porter les lectures : liez le compteur à la facture sous **Compteur (Services publics)**.

## Depuis le téléphone {#phone}
@manual: utilities#phone

Sur un téléphone jumelé, touchez **Relevé de compteur ou de réservoir** à l’onglet **Capturer** pour envoyer un relevé là où est le compteur.

## Réservoirs {#tanks}
@screen: UTILITIES UtilitiesTab.TANKS
@target: tanks.add
@manual: utilities#tanks

À l’onglet **Réservoirs**, cliquez sur **Ajouter un réservoir** pour le propane ou le mazout. Avec ses niveaux et ses livraisons, l’application calcule quand commander, et le téléphone vous le rappelle.
