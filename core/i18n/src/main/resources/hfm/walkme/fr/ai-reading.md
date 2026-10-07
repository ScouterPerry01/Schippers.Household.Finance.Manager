# Lecture par IA
@about: Activez la lecture par IA avec votre propre clé Anthropic, et faites lire un document difficile par Claude.

## Obtenir une clé {#key}
@manual: ai#turn-on

La lecture par IA envoie les images des pages d’un document à Claude, d’Anthropic, avec votre propre clé, facturée un petit montant par document. Elle reste désactivée tant que vous ne l’activez pas.

Sur console.anthropic.com, dans votre propre compte Anthropic, créez une clé d’API sous API keys et ajoutez-y du crédit.

## Ouvrir Lecture par IA {#open}
@screen: AI
@done: screen
@manual: ai#key

Dans le menu, ouvrez **Réglages**, puis **Lecture par IA**.

## Enregistrer la clé {#save-key}
@screen: AI
@target: ai.key
@manual: ai#key

Collez la clé dans **Clé d’API** et cliquez sur **Enregistrer la clé**. Elle est gardée dans le magasin secret de cet ordinateur, jamais dans le ménage ni dans ses sauvegardes. Cliquez ensuite sur **Vérifier la clé (gratuit)**.

## L’activer {#turn-on}
@screen: AI
@target: ai.enabled
@manual: ai#settings

Cochez **Lire les documents difficiles avec l’IA**. Laissez **Me montrer chaque document et me laisser en masquer des parties avant l’envoi** coché : c’est votre occasion de masquer un numéro de compte avant qu’une page quitte l’ordinateur.

## Ouvrir un document {#document}
@screen: DOCUMENTS DocumentsTab.INBOX
@target: documents.review
@done: shown documents.window
@manual: documents#read-with-ai

À l’onglet **À vérifier** de l’écran Documents, cliquez sur **Vérifier** sur un document difficile à lire, comme un reçu froissé ou un long relevé.

## Lire avec l’IA {#read}
@target: documents.aiRead
@done: shown ai.read
@manual: documents#ai-button

Cliquez sur **Lire avec l’IA**.

## Masquer ce qui ne doit pas partir {#hide}
@target: ai.send
@manual: documents#ai-read-window

Vérifiez le **Type de document**. Avec **Masquer une zone**, tracez un rectangle sur ce qui n’est pas nécessaire, comme un numéro de compte complet. La fenêtre indique combien de pages seront envoyées et le coût estimé. Cliquez sur **Envoyer**.

## Vérifier et classer {#file}
@target: documents.window
@manual: documents#after-ai-reading

Les valeurs lues par l’IA remplacent le commerce, la date et le total et sont marquées comme lues par l’IA ; un reçu reçoit aussi ses articles et ses taxes. Vérifiez-les, puis classez le document comme d’habitude. Chaque lecture et son coût sont listés à l’écran Lecture par IA.
