package ca.schippers.hfm.desktop

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.VaultDocument
import ca.schippers.hfm.ocr.desktop.DocumentReader
import ca.schippers.hfm.ocr.desktop.Heif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** DOC-01: the zoom steps of the document viewer; 1 shows the page as wide as the viewer (fit). */
internal object PageZoom {
    const val FIT = 1f
    private val STEPS = listOf(0.5f, 0.67f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f, 4f, 5f)
    val MIN = STEPS.first()
    val MAX = STEPS.last()

    /** The next step in, or the largest. */
    fun zoomIn(zoom: Float): Float = STEPS.firstOrNull { it > zoom + 0.001f } ?: MAX

    /** The next step out, or the smallest. */
    fun zoomOut(zoom: Float): Float = STEPS.lastOrNull { it < zoom - 0.001f } ?: MIN

    /** Page [index] moved by [by], kept within a document of [count] pages. */
    fun turn(index: Int, by: Int, count: Int): Int = if (count <= 0) 0 else (index + by).coerceIn(0, count - 1)
}

/**
 * DOC-01: a document's pages, one at a time: a PDF page by page (a multi-page capture from the
 * phone is one PDF of its photos), an image as one page. Previous and next page (also Page Up and
 * Page Down), zoom in and out and back to fit (buttons, Ctrl and the mouse wheel, Ctrl + - 0), and
 * dragging or the scroll bars to move about a zoomed page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DocumentViewer(model: BooksModel, doc: VaultDocument, modifier: Modifier = Modifier) {
    var bytes by remember(doc.id) { mutableStateOf<ByteArray?>(null) }
    var count by remember(doc.id) { mutableStateOf(-1) }
    LaunchedEffect(doc.id) {
        withContext(Dispatchers.IO) {
            val content = runCatching { model.books.documents.content(doc.id) }.getOrNull()
            count = content?.let { runCatching { DocumentReader.pageCount(it) }.getOrDefault(0) } ?: 0
            bytes = content
        }
    }
    var index by remember(doc.id) { mutableStateOf(0) }
    var zoom by remember(doc.id) { mutableStateOf(PageZoom.FIT) }
    var page by remember(doc.id) { mutableStateOf<ImageBitmap?>(null) }
    var pageFailed by remember(doc.id) { mutableStateOf(false) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    LaunchedEffect(bytes, index) {
        val content = bytes ?: return@LaunchedEffect
        // Only the page shown is kept: a long statement would not fit in memory at this size.
        val image = withContext(Dispatchers.IO) { runCatching { DocumentReader.page(content, index)?.toComposeImageBitmap() }.getOrNull() }
        page = image
        pageFailed = image == null
        vertical.scrollTo(0)
        horizontal.scrollTo(0)
    }
    val focus = remember { FocusRequester() }
    fun turn(by: Int) { index = PageZoom.turn(index, by, count) }

    Column(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        if (count > 0 && doc.mimeType != "text/plain") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (count > 1) {
                    SymbolButton(model.t("common.previousPage"), "◀", enabled = index > 0) { turn(-1); focus.requestFocus() }
                    Text(model.t("documents.pageOf", index + 1, count), style = MaterialTheme.typography.bodySmall)
                    SymbolButton(model.t("common.nextPage"), "▶", enabled = index < count - 1) { turn(1); focus.requestFocus() }
                }
                Spacer(Modifier.weight(1f))
                SymbolButton(model.t("documents.zoomOut"), "−", enabled = zoom > PageZoom.MIN) { zoom = PageZoom.zoomOut(zoom) }
                Text("${(zoom * 100).roundToInt()} %", style = MaterialTheme.typography.bodySmall)
                SymbolButton(model.t("documents.zoomIn"), "+", enabled = zoom < PageZoom.MAX) { zoom = PageZoom.zoomIn(zoom) }
                TextButton(enabled = zoom != PageZoom.FIT, onClick = { zoom = PageZoom.FIT }) { Text(model.t("documents.zoomFit")) }
            }
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.PageDown -> { turn(1); true }
                        e.key == Key.PageUp -> { turn(-1); true }
                        e.isCtrlPressed && (e.key == Key.Equals || e.key == Key.Plus || e.key == Key.NumPadAdd) -> { zoom = PageZoom.zoomIn(zoom); true }
                        e.isCtrlPressed && (e.key == Key.Minus || e.key == Key.NumPadSubtract) -> { zoom = PageZoom.zoomOut(zoom); true }
                        e.isCtrlPressed && (e.key == Key.Zero || e.key == Key.NumPad0) -> { zoom = PageZoom.FIT; true }
                        else -> false
                    }
                }
                // Ctrl and the wheel zoom; caught before the page scrolls.
                .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
                    if (e.keyboardModifiers.isCtrlPressed) {
                        val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy < 0) zoom = PageZoom.zoomIn(zoom) else if (dy > 0) zoom = PageZoom.zoomOut(zoom)
                        e.changes.forEach { it.consume() }
                    }
                }
                .pointerInput(doc.id) { detectTapGestures { focus.requestFocus() } }
                .pointerInput(doc.id) {
                    detectDragGestures(onDragStart = { focus.requestFocus() }) { change, drag ->
                        change.consume()
                        horizontal.dispatchRawDelta(-drag.x)
                        vertical.dispatchRawDelta(-drag.y)
                    }
                },
        ) {
            val image = page
            when {
                // CAP-07: a quick expense from the phone has no photo, only what was typed.
                doc.mimeType == "text/plain" -> Text(model.t("documents.noPhoto"), Modifier.padding(16.dp))
                doc.mimeType == "image/heic" && !Heif.available -> Text(model.t("documents.heicNoPreview") + " " + model.heicDecoderHint(), Modifier.padding(16.dp))
                count == 0 || pageFailed -> Text(model.t("documents.noPreview"), Modifier.padding(16.dp))
                image == null -> Text(model.t("documents.loadingPreview"), Modifier.padding(16.dp))
                else -> {
                    val width = maxWidth * zoom
                    val height = width * (image.height.toFloat() / image.width)
                    Box(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)) {
                        Image(image, model.t("documents.pageOf", index + 1, count), Modifier.size(width, height), contentScale = ContentScale.FillBounds)
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(vertical), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    HorizontalScrollbar(rememberScrollbarAdapter(horizontal), Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
            }
        }
    }
    LaunchedEffect(doc.id, count) { if (count > 0) runCatching { focus.requestFocus() } }
}
