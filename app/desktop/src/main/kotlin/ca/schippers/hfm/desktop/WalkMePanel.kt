package ca.schippers.hfm.desktop

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.i18n.WalkMe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Cursor
import java.util.prefs.Preferences

/**
 * HLP-03: the Walk-Me guide in progress, the controls on screen that guides can point at, and each
 * guide's progress, remembered on this computer so a closed guide resumes where it was left.
 */
class WalkMeState(private val prefs: Preferences) {
    /** The guide being followed, or null when none is open. */
    var guideId by mutableStateOf<String?>(null)
        private set

    /** The step shown, from 0. */
    var step by mutableIntStateOf(0)
        private set

    var minimized by mutableStateOf(false)

    /** The panel's width in dp, kept on this computer. */
    var width by mutableStateOf(prefs.getFloat(PREF_WIDTH, DEFAULT_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH))
        private set

    /** Asks the screen to show where the step's control is (Show me). Increases each time. */
    var showRequests by mutableIntStateOf(0)

    /** The controls on screen now, by id, with how many times each appears. */
    private val shown = mutableStateMapOf<String, Int>()

    /** Tabs on screen: whether each is selected. Their actions are kept apart, so they never cause a redraw. */
    private val selected = mutableStateMapOf<String, Boolean>()
    private val activators = HashMap<String, () -> Unit>()

    fun start(id: String, from: Int = savedStep(id)) {
        guideId = id
        step = from
        minimized = false
        save()
    }

    fun go(to: Int) {
        step = to
        save()
    }

    /** The guide was followed to its end: it starts from the beginning next time. */
    fun finish() {
        guideId?.let { id ->
            prefs.remove("$PREF_STEP.$id")
            prefs.putBoolean("$PREF_DONE.$id", true)
        }
        guideId = null
    }

    /** Closes the panel; the guide resumes at the same step next time. */
    fun close() {
        save()
        guideId = null
    }

    fun resize(dp: Float) {
        width = dp.coerceIn(MIN_WIDTH, MAX_WIDTH)
        prefs.putFloat(PREF_WIDTH, width)
    }

    /** The step a guide was left at on this computer (0 when never opened or finished). */
    fun savedStep(id: String): Int = prefs.getInt("$PREF_STEP.$id", 0)

    /** Whether the guide was once followed to its end on this computer. */
    fun finished(id: String): Boolean = prefs.getBoolean("$PREF_DONE.$id", false)

    private fun save() {
        guideId?.let { prefs.putInt("$PREF_STEP.$it", step) }
    }

    fun isShown(id: String): Boolean = (shown[id] ?: 0) > 0

    fun isSelected(id: String): Boolean = selected[id] == true

    /** Selects a tab on screen; false when it is not on screen. */
    fun activate(id: String): Boolean = activators[id]?.let { it(); true } ?: false

    /** The control the current step points at, highlighted on screen. */
    var highlighted by mutableStateOf<String?>(null)
        internal set

    internal fun register(id: String) {
        shown[id] = (shown[id] ?: 0) + 1
    }

    internal fun unregister(id: String) {
        val n = (shown[id] ?: 0) - 1
        if (n > 0) shown[id] = n else shown.remove(id)
        if (n <= 0) {
            selected.remove(id)
            activators.remove(id)
        }
    }

    internal fun tab(id: String, isSelected: Boolean, activate: () -> Unit) {
        if (selected[id] != isSelected) selected[id] = isSelected
        activators[id] = activate
    }

    companion object {
        private const val PREF_STEP = "walkme.step"
        private const val PREF_DONE = "walkme.done"
        private const val PREF_WIDTH = "walkme.width"
        const val DEFAULT_WIDTH = 360f
        const val MIN_WIDTH = 260f
        const val MAX_WIDTH = 640f
    }
}

/** The Walk-Me state, for the controls guides point at; null where no guide can run (the manual's pictures). */
val LocalWalkMe = staticCompositionLocalOf<WalkMeState?> { null }

/**
 * HLP-03: marks a control a Walk-Me guide can point at, by [id] (`area.what`, such as `bills.add`).
 * While a step points at it, a pulsing outline is drawn around it and it is scrolled into view.
 * Guides name these ids; `WalkMeGuidesTest` checks each id a guide uses is marked somewhere.
 */
fun Modifier.walkTarget(id: String): Modifier = composed {
    val walk = LocalWalkMe.current ?: return@composed Modifier
    DisposableEffect(walk, id) {
        walk.register(id)
        onDispose { walk.unregister(id) }
    }
    highlight(walk, id)
}

/**
 * HLP-03: marks a tab a guide can open: its id is the tab's enum and name (`BillsTab.ALL`), and Show
 * me selects it with [activate].
 */
fun Modifier.walkTab(tab: Enum<*>, isSelected: Boolean, activate: () -> Unit): Modifier = composed {
    val walk = LocalWalkMe.current ?: return@composed Modifier
    val id = tabId(tab)
    DisposableEffect(walk, id) {
        walk.register(id)
        onDispose { walk.unregister(id) }
    }
    SideEffect { walk.tab(id, isSelected, activate) }
    highlight(walk, id)
}

/** A tab's Walk-Me id: its enum's name and its own, such as `BillsTab.ALL`. */
fun tabId(tab: Enum<*>): String = "${tab.declaringJavaClass.simpleName}.${tab.name}"

@Composable
private fun highlight(walk: WalkMeState, id: String): Modifier {
    val active = walk.highlighted == id
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active, walk.showRequests) { if (active) runCatching { requester.bringIntoView() } }
    if (!active) return Modifier.bringIntoViewRequester(requester)
    val pulse by rememberInfiniteTransition(label = "walk").animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val color = MaterialTheme.colorScheme.tertiary
    return Modifier.bringIntoViewRequester(requester).drawWithContent {
        drawContent()
        val pad = 3.dp.toPx()
        val stroke = 3.dp.toPx()
        drawRoundRect(
            color.copy(alpha = pulse),
            topLeft = Offset(-pad, -pad),
            size = Size(size.width + 2 * pad, size.height + 2 * pad),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(stroke),
        )
    }
}

/** Whether [step]'s condition holds now; [baseline] is the count of the kind when the step was shown. */
internal fun conditionMet(state: AppState, step: WalkMe.Step, baseline: Int?): Boolean {
    val done = step.done ?: return false
    val walk = state.walkMe
    val model = (state.screen as? Screen.Main)?.model
    return when (done.kind) {
        "screen" -> onScreen(state, step)
        "shown" -> done.argument?.let { walk.isShown(it) } == true
        "open" -> model != null
        "added" -> model != null && baseline != null && (walkCount(model, done.argument) ?: 0) > baseline
        else -> false
    }
}

/** Whether the step's screen (and tab) is the one shown. */
internal fun onScreen(state: AppState, step: WalkMe.Step): Boolean {
    val screen = step.screen ?: return false
    val shownHere = when (screen) {
        WELCOME -> state.screen == Screen.Welcome
        else -> (state.screen as? Screen.Main)?.model?.section?.name == screen
    }
    return shownHere && step.tab.let { it == null || state.walkMe.isSelected(it) }
}

/** The pseudo-screen before a household is open. */
const val WELCOME = "WELCOME"

/** The records `added` can wait for, counted in the open household. */
val WALK_KINDS: Map<String, (BooksModel) -> Int> = mapOf(
    "institution" to { m -> m.books.institutions.list().size },
    "account" to { m -> m.books.accounts.list().size },
    "member" to { m -> m.books.members.list().size },
    "user" to { m -> m.books.users.list().size },
    "bill" to { m -> m.books.bills.list().size },
    "budget" to { m -> m.books.budgets.list().size },
    "goal" to { m -> m.books.goals.list().size },
    "phone" to { m -> m.books.sync.devices().count { !it.revoked } },
    "document" to { m -> m.books.documents.search(ca.schippers.hfm.books.DocumentQuery(limit = 100_000)).size },
    // Documents filed out of To review.
    "filed" to { m ->
        m.books.documents.search(ca.schippers.hfm.books.DocumentQuery(limit = 100_000)).size - m.books.documents.inboxCount()
    },
    "statement" to { m -> m.books.accounts.list().sumOf { a -> m.books.statements.statements(a.account.id).size } },
    "reconciled" to { m ->
        m.books.accounts.list().sumOf { a -> m.books.statements.statements(a.account.id).count { it.status == ca.schippers.hfm.books.StatementStatus.RECONCILED } }
    },
    // The time of the last good backup, in seconds: it grows with each new one.
    "backup" to { m -> m.books.backups.status().lastSuccess?.epochSecond?.toInt() ?: 0 },
    "contact" to { m -> m.books.contacts.list().size },
    "vehicle" to { m -> m.books.vehicles.list().size },
)

internal fun walkCount(model: BooksModel, kind: String?): Int? = kind?.let { WALK_KINDS[it] }?.let { runCatching { it(model) }.getOrNull() }

/**
 * HLP-03: the guide in progress, docked on the right of the window and never blocking the app: the
 * step, what to do, Show me, Back and Next, and a link to the manual. It can be resized by its left
 * edge and minimized to a narrow strip. Steps with a condition move on by themselves once it holds.
 */
@Composable
fun WalkMePanel(state: AppState) {
    val walk = state.walkMe
    val id = walk.guideId ?: return
    val guide = remember(id, state.language) { WalkMe.guide(state.language, id) }
    if (guide == null || guide.steps.isEmpty()) {
        LaunchedEffect(id) { walk.close() }
        return
    }
    val index = walk.step.coerceIn(0, guide.steps.size - 1)
    val step = guide.steps[index]
    val model = (state.screen as? Screen.Main)?.model
    // The highlighted control follows the step.
    SideEffect { walk.highlighted = step.target }
    DisposableEffect(Unit) { onDispose { walk.highlighted = null } }

    // Moves on by itself when the step's condition becomes true while it is shown.
    LaunchedEffect(id, index, model) {
        val baseline = if (step.done?.kind == "added" && model != null) walkCount(model, step.done?.argument) else null
        // Already true when the step is shown (such as going back to it): wait until it is false first.
        snapshotFlow { model?.revision; conditionMet(state, step, baseline) }.dropWhile { it }.first { it }
        delay(ADVANCE_DELAY)
        if (walk.guideId == id && walk.step == index) {
            if (index < guide.steps.size - 1) walk.go(index + 1) else walk.finish()
        }
    }
    val scope = rememberCoroutineScope()
    fun showMe() {
        val screen = step.screen ?: return
        val m = (state.screen as? Screen.Main)?.model
        if (screen != WELCOME && m != null) Section.entries.firstOrNull { it.name == screen }?.let { m.section = it }
        scope.launch {
            step.tab?.let { tab ->
                withTimeoutOrNull(SHOW_TIMEOUT) { snapshotFlow { walk.isShown(tab) }.first { it } }
                walk.activate(tab)
            }
            walk.showRequests++
        }
    }
    val canShow = step.screen != null && (step.screen == WELCOME || model != null) && !(step.screen == WELCOME && model != null)

    val density = LocalDensity.current
    if (walk.minimized) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxHeight().width(56.dp).semantics { paneTitle = guide.title }) {
            Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { walk.minimized = false }, modifier = Modifier.semantics { contentDescription = state.t("walkme.expand") }) { Text("◂") }
                Text("${index + 1}/${guide.steps.size}", style = MaterialTheme.typography.labelMedium)
            }
        }
        return
    }
    Row(Modifier.fillMaxHeight()) {
        // The left edge: drag to resize.
        Box(
            Modifier.fillMaxHeight().width(6.dp)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(rememberDraggableState { delta -> walk.resize(walk.width - with(density) { delta.toDp().value }) }, Orientation.Horizontal),
        )
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxHeight().width(Dp(walk.width)).semantics { paneTitle = state.t("walkme.panel", guide.title) },
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.t("walkme.name"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { walk.minimized = true }) { Text(state.t("walkme.minimize")) }
                    TextButton(onClick = { walk.close() }) { Text(state.t("common.close")) }
                }
                Text(guide.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(state.t("walkme.stepOf", index + 1, guide.steps.size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                LinearProgressIndicator(progress = { (index + 1f) / guide.steps.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 10.dp)
                        // Screen readers read each new step as it comes.
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    for (block in step.blocks) ManualBlock(state, block) { page -> state.openManual(page) }
                    val target = step.target
                    val hint = when {
                        target != null && walk.isShown(target) -> "walkme.highlighted"
                        canShow && !onScreen(state, step) -> "walkme.pressShowMe"
                        else -> null
                    }
                    hint?.let { Text(state.t(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (step.done != null) Text(state.t("walkme.auto"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    step.manual?.let { page ->
                        TextButton(onClick = { state.openManual(page) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(state.t("walkme.inManual")) }
                    }
                }
                HorizontalDivider()
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canShow) OutlinedButton(onClick = ::showMe) { Text(state.t("walkme.showMe")) }
                    OutlinedButton(onClick = { walk.go(index - 1) }, enabled = index > 0) { Text(state.t("walkme.back")) }
                    if (index < guide.steps.size - 1) Button(onClick = { walk.go(index + 1) }) { Text(state.t("walkme.next")) }
                    else Button(onClick = { walk.finish() }) { Text(state.t("walkme.finish")) }
                }
            }
        }
    }
}

/** How long a step that is done stays on screen before the next one, so the change is seen. */
private const val ADVANCE_DELAY = 700L

/** How long Show me waits for a screen's tabs to appear. */
private const val SHOW_TIMEOUT = 2_000L

/**
 * HLP-03: the Walk-Me guides by group, each with what it does and its number of steps; a guide
 * started earlier offers to resume where it was left. Shown from the Help menu, and from the
 * welcome screen before a household is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalkMeList(state: AppState) {
    val groups = remember(state.language) { WalkMe.groups(state.language) }
    val walk = state.walkMe
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.t("walkme.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(state.t("walkme.intro"), style = MaterialTheme.typography.bodyMedium)
        for (group in groups) {
            Text(group.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            for (guide in group.guides) {
                val saved = walk.savedStep(guide.id).coerceIn(0, (guide.steps.size - 1).coerceAtLeast(0))
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(guide.title, fontWeight = FontWeight.Bold)
                        Text(guide.about, style = MaterialTheme.typography.bodySmall)
                        Text(
                            state.t("walkme.steps", guide.steps.size) + if (walk.finished(guide.id) && saved == 0) " · " + state.t("walkme.done") else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (saved > 0) {
                        OutlinedButton(onClick = { walk.start(guide.id, 0) }) { Text(state.t("walkme.restart")) }
                        Button(onClick = { walk.start(guide.id, saved) }) { Text(state.t("walkme.resume", saved + 1)) }
                    } else {
                        Button(onClick = { walk.start(guide.id, 0) }) { Text(state.t("walkme.start")) }
                    }
                }
            }
        }
    }
}

/** The Walk-Me screen in the Help menu. */
@Composable
fun WalkMeScreen(state: AppState) {
    Column(Modifier.fillMaxHeight().padding(16.dp).verticalScroll(rememberScrollState())) { WalkMeList(state) }
}
