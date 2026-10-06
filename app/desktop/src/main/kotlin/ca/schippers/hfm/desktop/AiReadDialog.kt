package ca.schippers.hfm.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.ai.AiReader
import ca.schippers.hfm.ai.DocumentType
import ca.schippers.hfm.ai.PageEdit
import ca.schippers.hfm.books.VaultDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.image.BufferedImage

private enum class Tool { HIDE, CROP }

/**
 * AI-04 and section 4.5: the pages exactly as they will be sent, where the user hides areas such
 * as a full account number (they are blurred into flat blocks before the picture leaves the
 * computer) and crops away what is not needed, then sends. Nothing is sent before Send.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiReadDialog(model: BooksModel, doc: VaultDocument, kind: ca.schippers.hfm.ocr.DocumentKind? = doc.kind, onClose: (read: Boolean) -> Unit) {
    val types = remember { DesktopAi.types().types }
    var type by remember { mutableStateOf(types.firstOrNull { it.id == DocumentType.idFor(kind ?: ca.schippers.hfm.ocr.DocumentKind.RECEIPT) } ?: types.first()) }
    var pages by remember { mutableStateOf<List<BufferedImage>?>(null) }
    val edits = remember { mutableStateListOf<PageEdit>() }
    // Pages the user chose not to send, by index; and whether the file has more pages than can be sent.
    val leftOut = remember { mutableStateListOf<Int>() }
    var truncated by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(0) }
    var tool by remember { mutableStateOf(Tool.HIDE) }
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val aiModel = remember { DesktopAi.chosenModel(model) }

    LaunchedEffect(doc.id) {
        // One page more than can be sent tells whether the document is longer than the limit.
        val read = withContext(Dispatchers.IO) {
            runCatching { DesktopOcr.reader.pageImages(model.books.documents.content(doc.id), maxPages = AiReader.MAX_PAGES + 1) }.getOrDefault(emptyList())
        }
        truncated = read.size > AiReader.MAX_PAGES
        val loaded = read.take(AiReader.MAX_PAGES)
        edits.clear()
        leftOut.clear()
        repeat(loaded.size) { edits += PageEdit() }
        pages = loaded
    }

    WideDialog(model.t("ai.read.title", doc.label), model.t("common.cancel"), { if (!sending) onClose(false) }) {
        val all = pages
        when {
            all == null -> Text(model.t("documents.loadingPreview"))
            all.isEmpty() -> Text(model.t("ai.read.noPages"))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PageEditor(all[index], edits[index], tool) { edits[index] = it }
                Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker(model.t("ai.read.type"), types, type, { t -> t.kind?.let { model.t("documentKind.$it") } ?: t.id }) { type = it }
                    Text(model.t("ai.read.tools"), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(tool == Tool.HIDE, { tool = Tool.HIDE }, { Text(model.t("ai.read.hide")) })
                        FilterChip(tool == Tool.CROP, { tool = Tool.CROP }, { Text(model.t("ai.read.crop")) })
                    }
                    TextButton(enabled = edits[index].blur.isNotEmpty(), onClick = { edits[index] = edits[index].copy(blur = edits[index].blur.dropLast(1)) }) { Text(model.t("ai.read.undo")) }
                    TextButton(enabled = edits[index] != PageEdit(), onClick = { edits[index] = PageEdit() }) { Text(model.t("ai.read.clear")) }
                    if (all.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = index > 0, onClick = { index-- }) { Text("<") }
                            Text(model.t("ai.read.page", index + 1, all.size))
                            TextButton(enabled = index < all.size - 1, onClick = { index++ }) { Text(">") }
                        }
                        // AI-04: a page that is not needed (a blank back, the terms) can be left out.
                        LabeledCheckbox(model.t("ai.read.leaveOut"), index in leftOut) { if (it) leftOut += index else leftOut -= index }
                    }
                    if (truncated) Text(model.t("ai.read.firstPagesOnly", AiReader.MAX_PAGES), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    val sentPages = all.filterIndexed { i, _ -> i !in leftOut }
                    val sentEdits = edits.filterIndexed { i, _ -> i !in leftOut }
                    val sizes = remember(all, edits.toList(), leftOut.toList()) { DesktopAi.prepare(sentPages, sentEdits).map { it.width to it.height } }
                    Text(model.t("ai.read.what", sentPages.size, aiModel.label, model.usd(aiModel.estimate(sizes, type.id))), style = MaterialTheme.typography.bodySmall)
                    Text(model.t("ai.read.privacy"), style = MaterialTheme.typography.bodySmall)
                    failure?.let { ErrorText(it) }
                    Button(enabled = !sending && sentPages.isNotEmpty(), onClick = {
                        sending = true
                        failure = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { DesktopAi.read(model, doc.id, type, DesktopAi.prepare(sentPages, sentEdits)) } }
                            sending = false
                            model.changed()
                            result.fold({ onClose(true) }, { failure = model.aiFailure(it) })
                        }
                    }) { Text(model.t(if (sending) "ai.read.sending" else "ai.read.send")) }
                }
            }
        }
    }
}

/** One page, with hidden areas drawn as solid blocks and the area outside the crop dimmed; drag to add. */
@Composable
private fun PageEditor(page: BufferedImage, edit: PageEdit, tool: Tool, onChange: (PageEdit) -> Unit) {
    val bitmap = remember(page) { page.toComposeImageBitmap() }
    val box = 520f
    val scale = minOf(box / page.width, 620f / page.height)
    val w = page.width * scale
    val h = page.height * scale
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    with(density) {
        Box(Modifier.size(w.toDp(), h.toDp()).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Image(bitmap, null, Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(tool, edit) {
                    detectDragGestures(
                        onDragStart = { start = it; end = it },
                        onDrag = { change, _ -> end = change.position },
                        onDragEnd = {
                            val a = start
                            val b = end
                            if (a != null && b != null) {
                                val r = Rectangle(
                                    (minOf(a.x, b.x) / scale).toInt(), (minOf(a.y, b.y) / scale).toInt(),
                                    (kotlin.math.abs(b.x - a.x) / scale).toInt(), (kotlin.math.abs(b.y - a.y) / scale).toInt(),
                                )
                                if (r.width > 4 && r.height > 4) onChange(if (tool == Tool.HIDE) edit.copy(blur = edit.blur + r) else edit.copy(crop = r))
                            }
                            start = null
                            end = null
                        },
                    )
                },
            ) {
                fun rect(r: Rectangle) = Offset(r.x * scale, r.y * scale) to Size(r.width * scale, r.height * scale)
                edit.crop?.let { c ->
                    val (o, s) = rect(c)
                    val dim = Color.Black.copy(alpha = 0.45f)
                    drawRect(dim, Offset.Zero, Size(size.width, o.y))
                    drawRect(dim, Offset(0f, o.y + s.height), Size(size.width, size.height - o.y - s.height))
                    drawRect(dim, Offset(0f, o.y), Size(o.x, s.height))
                    drawRect(dim, Offset(o.x + s.width, o.y), Size(size.width - o.x - s.width, s.height))
                    drawRect(Color(0xFF3D8FCB), o, s, style = Stroke(2f))
                }
                for (b in edit.blur) {
                    val (o, s) = rect(b)
                    drawRect(Color(0xFF6B6B6B), o, s)
                }
                val a = start
                val b = end
                if (a != null && b != null) {
                    drawRect(
                        if (tool == Tool.HIDE) Color(0xAA6B6B6B) else Color(0xFF3D8FCB), Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                        Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = if (tool == Tool.HIDE) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2f),
                    )
                }
            }
        }
    }
}
