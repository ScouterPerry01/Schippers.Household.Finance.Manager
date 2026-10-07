# Rapprocher un relevé
@about: Comparez les livres au relevé de la banque, au cent près, et verrouillez la période.

## Ouvrir le compte {#open-account}
@screen: ACCOUNTS
@target: accounts.row
@done: shown register.reconcile
@manual: accounts#reconcile

À l’écran **Comptes**, cliquez sur le compte. Un relevé importé se rapproche à partir de son registre ; un relevé papier aussi.

## Commencer le rapprochement {#start}
@target: register.reconcile
@done: shown reconcile.screen
@manual: accounts#reconcile

Cliquez sur **Rapprocher…**. Il reprend le relevé en cours, comme celui que vous venez d’importer.

S’il n’y a pas de relevé en cours, la liste des relevés s’ouvre : cliquez sur **Entrer un relevé papier**, tapez la **Date du relevé** et le **Solde de clôture du relevé**, puis cliquez sur **Enregistrer**.

## Le solde du relevé {#balance}
@target: reconcile.closing
@manual: accounts#reconcile-balance

Comparez la **Date du relevé** et le **Solde de clôture du relevé** avec le relevé ; ils sont remplis à partir du fichier quand il les donne. Changez-les au besoin et cliquez sur **Appliquer**.

L’écart entre le relevé et le solde compensé des livres s’affiche à côté, en vert à zéro.

## Régler chaque ligne {#lines}
@target: reconcile.screen
@manual: accounts#reconcile-attention

Sous À vérifier, choisissez une action pour chaque ligne :

- **Même opération** : c’est l’opération proposée.
- **Ajouter comme nouvelle** : vous ne l’aviez pas inscrite ; elle est ajoutée.
- **Jumeler à une opération inscrite** : choisissez celle qui correspond.
- **Ignorer** : ce n’est pas une vraie opération.

## Cocher ce qui est passé {#outstanding}
@target: reconcile.screen
@manual: accounts#reconcile-outstanding

Sous « Inscrites, mais absentes de ce relevé », cochez chaque opération qui figure bien sur le relevé. Laissez décochés les chèques pas encore encaissés : ils attendent le prochain relevé.

## Terminer {#finish}
@target: reconcile.finish
@done: added reconciled
@manual: accounts#reconcile-finish

Quand l’écart est nul et que chaque ligne est réglée, cliquez sur **Terminer le rapprochement**. Les opérations compensées sont marquées rapprochées et verrouillées, et la liste des comptes affiche la nouvelle date.

Si l’écart ne revient pas à zéro, cherchez une opération entrée deux fois, un montant mal tapé ou une ligne ignorée par erreur.
