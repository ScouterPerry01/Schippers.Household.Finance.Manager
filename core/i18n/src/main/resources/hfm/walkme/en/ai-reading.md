# AI reading
@about: Turn on AI reading with your own Anthropic key, and have a hard document read by Claude.

## Get a key {#key}
@manual: ai#turn-on

AI reading sends pictures of a document's pages to Anthropic's Claude, with your own key, billed a small amount per document. It is off until you turn it on.

On console.anthropic.com, in your own Anthropic account, create an API key under API keys and add credit there.

## Open AI reading {#open}
@screen: AI
@done: screen
@manual: ai#key

In the menu, open **Settings**, then **AI reading**.

## Save the key {#save-key}
@screen: AI
@target: ai.key
@manual: ai#key

Paste the key into **API key** and click **Save key**. It is kept in this computer's secret store, never in the household or its backups. Then click **Check key (free)**.

## Turn it on {#turn-on}
@screen: AI
@target: ai.enabled
@manual: ai#settings

Tick **Read hard documents with AI**. Keep **Show me each document and let me hide parts before it is sent** ticked: it is your chance to hide an account number before a page leaves the computer.

## Open a document {#document}
@screen: DOCUMENTS DocumentsTab.INBOX
@target: documents.review
@done: shown documents.window
@manual: documents#read-with-ai

On the Documents screen's **To review** tab, click **Review** on a document that was hard to read, such as a crumpled receipt or a long statement.

## Read with AI {#read}
@target: documents.aiRead
@done: shown ai.read
@manual: documents#ai-button

Click **Read with AI**.

## Hide what should not be sent {#hide}
@target: ai.send
@manual: documents#ai-read-window

Check the **Document type**. With **Hide an area**, drag a rectangle over anything not needed, such as a full account number. The window says how many pages will be sent and the estimated cost. Click **Send**.

## Check and file {#file}
@target: documents.window
@manual: documents#after-ai-reading

The values read by AI replace the store, date and total and are marked as read by AI; a receipt also gets its items and taxes. Check them, then file the document as usual. Each reading and its cost are listed on the AI reading screen.
