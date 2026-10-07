package ca.schippers.hfm.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ca.schippers.hfm.i18n.Manual

/** One line of the manual's contents: a part, chapter, section or subsection. */
private data class TocRow(val key: String, val title: String, val level: Int, val hasChildren: Boolean, val open: Boolean, val match: Boolean)

/** One piece of a chapter as shown: a heading, or a block under it. */
private sealed interface PageItem {
    data class Heading(val node: Manual.Node) : PageItem
    data class Body(val block: Manual.Block) : PageItem
}

private enum class ManualTab(val label: String) { CONTENTS("manual.contents"), INDEX("manual.index"), SEARCH("manual.search") }

/**
 * NFR-12: the user manual in its own window, beside the app. On the left, the contents (an
 * expandable tree), the index and a search, each filtered as the user types; on the right, the
 * chapter, with links, back and forward, and the previous and next chapters. It follows the app's
 * language, theme and text size.
 */
@Composable
fun ManualWindow(state: AppState) {
    Window(
        onCloseRequest = { state.manualPage = null },
        title = state.t("manual.title"),
        icon = AppIcon,
        state = rememberWindowState(width = 1240.dp, height = 880.dp),
    ) {
        // Asked for again while open (Shift+F1, the Manual button): come to the front.
        LaunchedEffect(state.manualRequests) { window.toFront() }
        AppTheme(state) {
            Surface(Modifier.fillMaxSize()) { ManualContent(state) }
        }
    }
}

@Composable
internal fun ManualContent(state: AppState) {
    val book = remember(state.language) { Manual.book(state.language) }
    if (book.chapters.isEmpty()) {
        Text(state.t("manual.missing"), Modifier.padding(24.dp))
        return
    }
    val history = remember { mutableStateListOf(state.manualPage ?: AppState.MANUAL_START) }
    var position by remember { mutableIntStateOf(0) }
    val page = history.getOrNull(position) ?: AppState.MANUAL_START
    fun go(key: String) {
        val target = if (book.node(key) != null) key else key.substringBefore('#').takeIf { book.chapter(it) != null } ?: return
        if (target == history.getOrNull(position)) return
        while (history.size > position + 1) history.removeAt(history.size - 1)
        history += target
        position = history.size - 1
    }
    LaunchedEffect(state.manualRequests) { state.manualPage?.let { go(it) } }

    var tab by remember { mutableStateOf(ManualTab.CONTENTS) }
    val searchFocus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().onKeys { ctrl, alt, key ->
            when {
                alt && key == Key.DirectionLeft && position > 0 -> { position--; true }
                alt && key == Key.DirectionRight && position < history.size - 1 -> { position++; true }
                ctrl && key == Key.F -> { tab = ManualTab.SEARCH; runCatching { searchFocus.requestFocus() }; true }
                else -> false
            }
        },
    ) {
        val chapterId = page.substringBefore('#')
        val (previous, next) = book.neighbours(chapterId)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { position-- }, enabled = position > 0) { Text(state.t("manual.back")) }
            TextButton(onClick = { position++ }, enabled = position < history.size - 1) { Text(state.t("manual.forward")) }
            Box(Modifier.weight(1f))
            TextButton(onClick = { previous?.let { go(it.chapterId) } }, enabled = previous != null) { Text(state.t("manual.previous")) }
            TextButton(onClick = { next?.let { go(it.chapterId) } }, enabled = next != null) { Text(state.t("manual.next")) }
        }
        HorizontalDivider()
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(360.dp).fillMaxHeight()) {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    for (t in ManualTab.entries) {
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text(state.t(t.label)) })
                    }
                }
                when (tab) {
                    ManualTab.CONTENTS -> ContentsPane(state, book, page) { go(it) }
                    ManualTab.INDEX -> IndexPane(state, book) { go(it) }
                    ManualTab.SEARCH -> SearchPane(state, book, searchFocus) { go(it) }
                }
            }
            VerticalDivider()
            ChapterView(state, book, page) { go(it) }
        }
    }
}

private fun Modifier.onKeys(handle: (ctrl: Boolean, alt: Boolean, key: Key) -> Boolean): Modifier =
    onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && handle(e.isCtrlPressed, e.isAltPressed, e.key) }

/** "Search the text too": shared by the contents and the index, kept while the window is open. */
private var searchTextToo by mutableStateOf(false)

@Composable
private fun ContentsPane(state: AppState, book: Manual.Book, page: String, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    // Parts start open; the chapter shown and its sections open as the reader moves.
    val opened = remember(book) { mutableStateListOf<String>().apply { addAll(book.parts.map { "part:" + it.id }) } }
    val closed = remember(book) { mutableStateListOf<String>() }
    val rows = remember(book, filter, searchTextToo, opened.toList(), closed.toList()) {
        tocRows(book, filter, searchTextToo) { key -> key in opened && key !in closed }
    }
    val listState = rememberLazyListState()
    // Open the part and chapter being read, and keep its line in view.
    LaunchedEffect(page) {
        val chapter = page.substringBefore('#')
        val part = book.parts.firstOrNull { p -> p.chapters.any { it.chapterId == chapter } }
        for (k in listOfNotNull(part?.let { "part:" + it.id }, chapter)) { closed.remove(k); if (k !in opened) opened += k }
        val now = tocRows(book, filter, searchTextToo) { key -> key in opened && key !in closed }
        val i = now.indexOfFirst { it.key == page }.takeIf { it >= 0 } ?: now.indexOfFirst { it.key == chapter }
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.scrollToItem((i - 3).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TextInput(state.t("manual.filter"), filter, Modifier.fillMaxWidth().padding(top = 8.dp)) { filter = it }
        LabeledCheckbox(state.t("manual.textToo"), searchTextToo) { searchTextToo = it }
        if (filter.isNotBlank() && rows.isEmpty()) Text(state.t("manual.nothing"), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(rows, key = { it.key }) { r ->
                val selected = r.key == page
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                        .clickable(role = Role.Button) {
                            if (r.level == 0) {
                                if (r.open) { opened.remove(r.key); closed += r.key } else { closed.remove(r.key); opened += r.key }
                            } else {
                                onOpen(r.key)
                            }
                        }
                        .padding(start = (4 + 14 * r.level).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (!r.hasChildren) "  " else if (r.open) "▾" else "▸",
                        Modifier.width(20.dp)
                            .then(
                                if (r.hasChildren && filter.isBlank()) {
                                    Modifier.clickable(role = Role.Button) {
                                        if (r.open) { opened.remove(r.key); closed += r.key } else { closed.remove(r.key); opened += r.key }
                                    }.semantics { stateDescription = state.t(if (r.open) "nav.expanded" else "nav.collapsed") }
                                } else {
                                    Modifier
                                },
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        r.title,
                        style = if (r.level == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        color = if (r.level == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected || (filter.isNotBlank() && r.match)) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * The contents as rows. With no [filter], parts, chapters and sections show as opened by the
 * reader; with one, only the headings that match (and those above them) show, all open.
 */
private fun tocRows(book: Manual.Book, filter: String, textToo: Boolean, isOpen: (String) -> Boolean): List<TocRow> {
    val rows = ArrayList<TocRow>()
    val filtering = filter.isNotBlank()
    fun matches(n: Manual.Node) = book.matches(n.title, filter) || (textToo && book.matches(n.text, filter))
    fun shown(n: Manual.Node): Boolean = matches(n) || n.children.any { shown(it) }
    fun add(n: Manual.Node) {
        if (filtering && !shown(n)) return
        val open = filtering || isOpen(n.key)
        rows += TocRow(n.key, n.title, n.level, n.children.isNotEmpty(), open, filtering && matches(n))
        if (open) n.children.forEach { add(it) }
    }
    for (part in book.parts) {
        val key = "part:" + part.id
        if (filtering && part.chapters.none { shown(it) }) continue
        val open = filtering || isOpen(key)
        rows += TocRow(key, part.title, 0, part.chapters.isNotEmpty(), open, false)
        if (open) part.chapters.forEach { add(it) }
    }
    return rows
}

@Composable
private fun IndexPane(state: AppState, book: Manual.Book, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val entries = remember(book, filter, searchTextToo) {
        if (filter.isBlank()) {
            book.index
        } else {
            book.index.filter { e ->
                book.matches(e.term, filter) || (searchTextToo && e.places.any { p -> book.node(p.key)?.let { book.matches(it.text, filter) } == true })
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TextInput(state.t("manual.filter"), filter, Modifier.fillMaxWidth().padding(top = 8.dp)) { filter = it }
        LabeledCheckbox(state.t("manual.textToo"), searchTextToo) { searchTextToo = it }
        if (filter.isNotBlank() && entries.isEmpty()) Text(state.t("manual.nothing"), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            var letter = ""
            for (e in entries) {
                val first = Manual.fold(e.term).take(1).uppercase()
                if (first != letter) {
                    letter = first
                    item(key = "letter:$first") {
                        Text(first, Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                item(key = "term:" + e.term) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            e.term,
                            Modifier.fillMaxWidth().then(if (e.places.size == 1) Modifier.clickable(role = Role.Button) { onOpen(e.places[0].key) } else Modifier),
                            fontWeight = FontWeight.Medium,
                        )
                        for (p in e.places) {
                            Text(
                                p.path,
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(p.key) }.padding(start = 14.dp, top = 1.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPane(state: AppState, book: Manual.Book, focus: FocusRequester, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val hits = remember(book, query) { book.search(query) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TextInput(state.t("manual.searchBox"), query, Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus)) { query = it }
        Text(
            if (query.isBlank()) state.t("manual.searchHint") else state.t("manual.results", hits.size),
            Modifier.padding(vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(hits, key = { it.node.key }) { h ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(h.node.key) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Text(h.node.title, fontWeight = FontWeight.Medium)
                    Text(h.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (h.snippet != h.node.title) Text(h.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                }
            }
        }
    }
}

@Composable
private fun ChapterView(state: AppState, book: Manual.Book, page: String, onOpen: (String) -> Unit) {
    val chapter = book.chapter(page.substringBefore('#')) ?: return
    val items = remember(chapter) {
        chapter.flatten().flatMap { n -> listOf<PageItem>(PageItem.Heading(n)) + n.blocks.map { PageItem.Body(it) } }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(page, items) {
        val i = items.indexOfFirst { it is PageItem.Heading && it.node.key == page }
        listState.scrollToItem(i.coerceAtLeast(0))
    }
    val part = book.parts.firstOrNull { p -> p.chapters.any { it.chapterId == chapter.chapterId } }
    val linkColor = MaterialTheme.colorScheme.primary
    SelectionContainer(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            part?.let { p ->
                item(key = "part") { Text(p.title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            items(items.size) { i ->
                when (val item = items[i]) {
                    is PageItem.Heading -> {
                        val n = item.node
                        val style = when (n.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }
                        Text(
                            n.title,
                            Modifier.padding(top = if (n.level == 1) 4.dp else 14.dp).semantics { heading() },
                            style = style,
                            color = if (n.key == page && n.level > 1) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    }
                    is PageItem.Body -> BlockView(state, item.block, linkColor, onOpen)
                }
            }
            item(key = "end") { Box(Modifier.padding(bottom = 48.dp)) }
        }
    }
}

@Composable
private fun BlockView(state: AppState, block: Manual.Block, linkColor: Color, onOpen: (String) -> Unit) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is Manual.Block.Paragraph -> Text(rich(block.text, linkColor, onOpen), style = body)
        is Manual.Block.Bullet -> Row(Modifier.padding(start = (if (block.level == 1) 8 else 32).dp)) {
            Text(if (block.level == 1) "•" else "◦", Modifier.width(18.dp), style = body)
            Text(rich(block.text, linkColor, onOpen), style = body)
        }
        is Manual.Block.Step -> Row(Modifier.padding(start = 8.dp)) {
            Text("${block.number}.", Modifier.width(28.dp), style = body, fontWeight = FontWeight.Bold)
            Text(rich(block.text, linkColor, onOpen), style = body)
        }
        is Manual.Block.Field -> Column(Modifier.padding(start = 8.dp)) {
            Text(block.name, style = body, fontWeight = FontWeight.Bold)
            if (block.text.isNotEmpty()) Text(rich(block.text, linkColor, onOpen), Modifier.padding(start = 16.dp), style = body)
        }
        is Manual.Block.Callout -> Surface(
            color = when (block.kind) {
                Manual.CalloutKind.TIP -> MaterialTheme.colorScheme.secondaryContainer
                Manual.CalloutKind.NOTE -> MaterialTheme.colorScheme.surfaceVariant
                Manual.CalloutKind.IMPORTANT -> MaterialTheme.colorScheme.errorContainer
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    state.t(
                        when (block.kind) {
                            Manual.CalloutKind.TIP -> "manual.tip"
                            Manual.CalloutKind.NOTE -> "manual.note"
                            Manual.CalloutKind.IMPORTANT -> "manual.important"
                        },
                    ), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(rich(block.text, linkColor, onOpen), style = body)
            }
        }
        is Manual.Block.Image -> ManualPicture(state, block)
    }
}

/**
 * A picture of the app with its caption below. It fills the page's width but never grows past its
 * own size (one picture pixel per screen pixel), and a thin outline sets it apart from the page.
 * In dark colours it shows the picture taken in dark colours when there is one (the phone's are
 * light only). Screen readers read the caption once, as the picture's description.
 */
@Composable
private fun ManualPicture(state: AppState, block: Manual.Block.Image) {
    val dark = LocalDarkTheme.current
    val bitmap = remember(state.language, block.path, dark) {
        Manual.image(state.language, block.path, dark)?.let { bytes -> runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() }
    }
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = block.caption,
                modifier = Modifier.widthIn(max = with(density) { bitmap.width.toDp() }).fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            block.caption,
            Modifier.padding(top = 6.dp).then(if (bitmap != null) Modifier.clearAndSetSemantics { } else Modifier),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A block's text with its **bold** words and [links](chapter#section) made live. */
private fun rich(text: String, linkColor: Color, onOpen: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var at = 0
    fun plain(until: Int) {
        val parts = text.substring(at, until).split("**")
        parts.forEachIndexed { i, s -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) } else append(s) }
    }
    for (m in Manual.LINK.findAll(text)) {
        plain(m.range.first)
        val target = m.groupValues[2]
        withLink(LinkAnnotation.Clickable(target, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) { onOpen(target) }) {
            append(m.groupValues[1].replace("**", ""))
        }
        at = m.range.last + 1
    }
    plain(text.length)
}
