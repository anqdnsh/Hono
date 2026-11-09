package com.aniq.glassreader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.BookContent
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.Para
import com.aniq.glassreader.data.ReaderSettings
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Volume-key page turns, fed by MainActivity. */
object ReaderKeys {
    @Volatile var active = false
    val turns = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}

private data class Sel(val para: Int, val start: Int, val end: Int, val word: String? = null)
private enum class Sheet { None, Chapters, Settings, Marks, Define, Note }
private data class Anchor(val para: Int, val char: Int) : Comparable<Anchor> {
    override fun compareTo(other: Anchor) = if (para != other.para) para.compareTo(other.para) else char.compareTo(other.char)
}

/** A visible run of lines from one paragraph. */
private class Slice(val para: Int, val startChar: Int, val top: Float, val height: Float)
private class Page(val slices: List<Slice>) {
    val start = Anchor(slices.first().para, slices.first().startChar)
}
private class Section(val start: Int, val end: Int, val split: Boolean)

private data class LayoutKey(val s: ReaderSettings, val w: Int, val h: Int)

@OptIn(ExperimentalCoroutinesApi::class)
internal val paginationThread = Dispatchers.Default.limitedParallelism(1)

internal tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun annotate(para: Para): AnnotatedString = buildAnnotatedString {
    append(para.text)
    var k = 0
    while (k + 2 < para.styles.size) {
        val t = para.styles[k + 2]
        val st = when {
            t == 1 -> SpanStyle(fontStyle = FontStyle.Italic)
            t == 2 -> SpanStyle(fontWeight = FontWeight.Bold)
            else -> SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
        }
        addStyle(st, para.styles[k], para.styles[k + 1])
        k += 3
    }
}

private fun sectionsOf(c: BookContent): List<Section> {
    val n = c.paras.size
    val starts = (listOf(0) + c.chapters.map { it.para }).filter { it in 0 until n }.distinct().sorted()
    val out = ArrayList<Section>()
    for (i in starts.indices) {
        val a = starts[i]; val b = if (i + 1 < starts.size) starts[i + 1] else n
        if (b - a <= 160) out.add(Section(a, b, false))
        else { var s = a; while (s < b) { val e = min(b, s + 120); out.add(Section(s, e, true)); s = e } }
    }
    return out
}

private class Paginator(
    val measurer: TextMeasurer,
    val body: TextStyle,
    val heading: TextStyle,
    val widthPx: Int,
    val heightPx: Float,
    val paraGap: Float,
    val headingGap: Float,
    val c: BookContent,
) {
    fun gapBefore(p: Int) = if (c.paras[p].heading) headingGap else paraGap

    suspend fun paginate(sec: Section): List<Page> {
        val pages = ArrayList<Page>()
        val cur = ArrayList<Slice>()
        var y = 0f
        fun flush() { if (cur.isNotEmpty()) { pages.add(Page(cur.toList())); cur.clear() }; y = 0f }
        for (p in sec.start until sec.end) {
            currentCoroutineContext().ensureActive()
            val para = c.paras[p]
            val layout = measurer.measure(
                annotate(para),
                style = if (para.heading) heading else body,
                constraints = Constraints(maxWidth = widthPx),
            )
            val lc = layout.lineCount
            if (lc == 0) continue
            // keep headings with at least a few lines of what follows
            if (para.heading && cur.isNotEmpty() && y + gapBefore(p) + layout.size.height + heightPx * 0.15f > heightPx) flush()
            var line = 0
            while (line < lc) {
                val g = if (cur.isEmpty() || line != 0) 0f else gapBefore(p)
                val avail = heightPx - y - g
                val top = layout.getLineTop(line)
                var end = line
                while (end < lc && layout.getLineBottom(end) - top <= avail) end++
                if (end == line) {
                    if (cur.isEmpty()) end = line + 1 else { flush(); continue }
                }
                val h = layout.getLineBottom(end - 1) - top
                cur.add(Slice(p, layout.getLineStart(line), top, h))
                y += g + h
                line = end
                if (line < lc) flush()
            }
        }
        flush()
        return pages
    }
}

@Composable
fun ReaderScreen(bookId: String, jumpPara: Int?, jumpChar: Int = 0, onBack: () -> Unit) {
    val book = remember(bookId) { Store.book(bookId) }
    if (book == null) { LaunchedEffect(Unit) { onBack() }; return }
    val settings by Store.settings.collectAsState()
    val theme = themeFor(settings)
    val bg by animateColorAsState(theme.bg, tween(350), label = "bg")
    if (book.isPdf) {
        Box(Modifier.fillMaxSize().background(bg).readerBackdrop(theme).paper(settings.grain, theme.light, settings.grainAmount)) {
            PdfReader(book, jumpPara, settings, theme, onBack)
        }
        return
    }
    val content by produceState<BookContent?>(null, bookId) {
        value = runCatching { Store.content(book) }.getOrNull()
        if (value == null) onBack()
    }
    LaunchedEffect(bookId) { Store.markOpened(bookId) }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(bg).readerBackdrop(theme).paper(settings.grain, theme.light, settings.grainAmount)) {
        val c = content
        if (c != null) ReaderBody(book, c, jumpPara, jumpChar, settings, theme, onBack)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderBody(
    book: Book,
    c: BookContent,
    jumpPara: Int?,
    jumpChar: Int,
    settings: ReaderSettings,
    theme: ReaderTheme,
    onBack: () -> Unit,
) {
    val haze = remember { HazeState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val fontResolver = LocalFontFamilyResolver.current
    val n = c.paras.size
    val sections = remember(c) { sectionsOf(c) }
    fun sectionOf(para: Int): Int {
        var lo = 0; var hi = sections.size - 1; var ans = 0
        while (lo <= hi) { val m = (lo + hi) ushr 1; if (sections[m].start <= para) { ans = m; lo = m + 1 } else hi = m - 1 }
        return ans
    }

    var anchor by remember {
        mutableStateOf(
            if (jumpPara != null) Anchor(jumpPara.coerceIn(0, n - 1), jumpChar.coerceAtLeast(0))
            else Anchor(book.progressIndex.coerceIn(0, n - 1), book.progressOffset.coerceIn(0, c.paras[book.progressIndex.coerceIn(0, n - 1)].text.length))
        )
    }
    var chrome by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var selection by remember { mutableStateOf<Sel?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Highlight?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var defineWord by remember { mutableStateOf("") }
    var noteFor by remember { mutableStateOf<Highlight?>(null) }

    val allHl by Store.highlights.collectAsState()
    val allMarks by Store.bookmarks.collectAsState()
    val bookMarks = remember(allMarks, book.id) { allMarks.filter { it.bookId == book.id }.sortedWith(compareBy({ it.para }, { it.char })) }
    val bookHl = remember(allHl, book.id) { allHl.filter { it.bookId == book.id } }
    val byPara = remember(bookHl) { bookHl.groupBy { it.para } }
    val activeLive = active?.let { a -> bookHl.firstOrNull { it.id == a.id } }

    // ---- system bars ----
    val view = LocalView.current
    val window = remember(view) { view.context.activity()?.window }
    val controller = remember(window) { window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(controller, theme.light) {
        controller?.isAppearanceLightStatusBars = theme.light
        controller?.isAppearanceLightNavigationBars = theme.light
        onDispose {
            controller?.isAppearanceLightStatusBars = !UiMode.dark
            controller?.isAppearanceLightNavigationBars = !UiMode.dark
        }
    }
    LaunchedEffect(controller, chrome) {
        controller ?: return@LaunchedEffect
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    val anchorNow by rememberUpdatedState(anchor)
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        ReaderKeys.active = true
        com.aniq.glassreader.CoverDisplay.openBook.value = book.id
        onDispose {
            ReaderKeys.active = false
            com.aniq.glassreader.CoverDisplay.openBook.value = null
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            Store.updateProgress(book.id, anchorNow.para, anchorNow.char, n, c.chapterAt(anchorNow.para)?.title.orEmpty())
        }
    }
    LaunchedEffect(anchor) { delay(400); Store.updateProgress(book.id, anchor.para, anchor.char, n, c.chapterAt(anchor.para)?.title.orEmpty()) }
    ReadingClock(book.id, anchor)
    GoalWatcher { toast = "Daily goal reached"; haptics.play(Haptic.Confirm) }
    LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }

    // ---- styles ----
    val family = if (settings.serif) FontFamily.Serif else Inter
    val body = remember(settings, theme) {
        TextStyle(
            color = theme.text, fontSize = settings.fontSize.sp, lineHeight = (settings.fontSize * settings.lineHeight).sp,
            fontFamily = family, textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph,
            letterSpacing = if (settings.serif) 0.sp else (-0.1).sp,
        )
    }
    val heading = remember(body) {
        body.copy(fontSize = (settings.fontSize * 1.45f).sp, lineHeight = (settings.fontSize * 1.8f).sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start, letterSpacing = (-0.4).sp)
    }

    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        // landscape (the Fold opened sideways): a page on each half of the screen, like an open book
        val landscape = constraints.maxWidth > constraints.maxHeight
        // and on the open Fold held upright, so lines don't run the whole width of the big screen
        val spread = landscape || (settings.twoPageUnfolded && maxWidth >= 600.dp)
        // the outer screen shows the cover only while reading sideways
        LaunchedEffect(landscape) { com.aniq.glassreader.CoverDisplay.landscape.value = landscape }
        DisposableEffect(Unit) { onDispose { com.aniq.glassreader.CoverDisplay.landscape.value = false } }
        val step = if (spread) 2 else 1
        val slotWdp = if (spread) maxWidth / 2 else maxWidth
        // use the whole screen: system bars are hidden while reading, so only the camera cutout is reserved
        val cutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val contentWdp = (slotWdp.value - settings.margin * 2f - 16f).coerceAtLeast(200f).dp
        val topReserve = maxOf(cutout, 30.dp) + 26.dp
        val bottomReserve = 40.dp
        val pageHdp = maxHeight - topReserve - bottomReserve
        val widthPx = with(density) { contentWdp.roundToPx() }
        val heightPx = with(density) { pageHdp.toPx() }
        val key = LayoutKey(settings, widthPx, heightPx.toInt())

        val paginator = remember(key, theme) {
            Paginator(
                TextMeasurer(fontResolver, density, LayoutDirection.Ltr, cacheSize = 0),
                body, heading, widthPx, heightPx,
                paraGap = with(density) { (settings.fontSize * 0.6f).sp.toPx() },
                headingGap = with(density) { (settings.fontSize * 1.6f).sp.toPx() },
                c,
            )
        }
        val cache = remember(key) { mutableStateMapOf<Int, List<Page>>() }
        val sec = sectionOf(anchor.para)
        LaunchedEffect(key, sec) {
            for (s in listOf(sec, sec + 1, sec - 1)) {
                if (s in sections.indices && cache[s] == null) {
                    cache[s] = withContext(paginationThread) { paginator.paginate(sections[s]) }
                }
            }
        }

        val pages = cache[sec]
        val cur = pages?.let { ps -> ps.indexOfLast { it.start <= anchor }.coerceAtLeast(0) } ?: 0
        /** The page [d] pages away from the current one, crossing into neighbouring chapters. */
        fun pageAt(d: Int): Page? {
            val ps = pages ?: return null
            if (ps.isEmpty()) return null
            val i = cur + d
            if (i in ps.indices) return ps[i]
            return if (i > ps.lastIndex) cache[sec + 1]?.getOrNull(i - ps.size)
            else cache[sec - 1]?.let { pv -> pv.getOrNull(pv.size + i) }
        }
        /** Where a turn in direction [d] lands: one page, or a whole spread in landscape. */
        fun neighbour(d: Int): Page? = when {
            d == 0 -> pageAt(0)
            !spread -> pageAt(d)
            d > 0 -> pageAt(2)
            else -> pageAt(-2) ?: pageAt(-1)
        }

        // ---- bookmarks: one counts as "here" if it falls on the page (or spread) on screen ----
        val hereStart = pageAt(0)?.start
        val hereEnd = neighbour(1)?.start
        val markHere = if (hereStart == null) null else bookMarks.firstOrNull {
            val a = Anchor(it.para, it.char); a >= hereStart && (hereEnd == null || a < hereEnd)
        }
        fun toggleBookmark() {
            val m = markHere
            if (m != null) { Store.removeBookmark(m.id); haptics.play(Haptic.ToggleOff); toast = "Bookmark removed"; return }
            val st = hereStart ?: anchor
            val snippet = c.paras[st.para].text.drop(st.char).trim().take(90)
            Store.addBookmark(com.aniq.glassreader.data.Bookmark(UUID.randomUUID().toString(), book.id, st.para, st.char,
                c.chapterAt(st.para)?.title ?: book.title, snippet))
            haptics.play(Haptic.Confirm); toast = "Bookmarked"
        }

        // ---- page turning ----
        val offset = remember { Animatable(0f) }
        val fade = remember { Animatable(1f) }
        var turnJob by remember { mutableStateOf<Job?>(null) }
        var pendingAnchor by remember { mutableStateOf<Anchor?>(null) }
        val turnSpring = spring<Float>(dampingRatio = 1f, stiffness = 420f)

        fun clearTransient(): Boolean {
            if (selection != null || active != null || sheet != Sheet.None) {
                selection = null; active = null; sheet = Sheet.None; return true
            }
            return false
        }

        fun turn(dir: Int, fromDrag: Boolean = false) {
            pendingAnchor?.let { anchor = it; pendingAnchor = null }
            turnJob?.cancel()
            scope.launch { fade.snapTo(1f) }
            val target = neighbour(dir)
            chrome = false
            if (target == null) {
                haptics.play(Haptic.Reject)
                turnJob = scope.launch {
                    if (!fromDrag) offset.animateTo(-dir * 36f, spring(0.6f, 900f))
                    offset.animateTo(0f, spring(0.55f, 500f))
                }
                return
            }
            pendingAnchor = target.start
            if (!fromDrag) haptics.play(Haptic.PageTurn)
            if (settings.pageAnim == 1 && !fromDrag) {
                turnJob = scope.launch {
                    fade.animateTo(0f, tween(90))
                    anchor = target.start; pendingAnchor = null
                    fade.animateTo(1f, tween(170))
                }
                return
            }
            turnJob = scope.launch {
                if (!fromDrag && abs(offset.value) < 1f) offset.snapTo(0f)
                offset.animateTo(-dir * fullW, turnSpring)
                anchor = target.start
                pendingAnchor = null
                offset.snapTo(0f)
            }
        }
        val turnNow by rememberUpdatedState<(Int) -> Unit>({ d -> turn(d) })
        val turnRef by rememberUpdatedState<(Int, Boolean) -> Unit>({ d, f -> turn(d, f) })
        val neighbourRef by rememberUpdatedState<(Int) -> Page?>({ neighbour(it) })
        LaunchedEffect(Unit) { ReaderKeys.turns.collect { if (sheet == Sheet.None) turnNow(it) } }

        fun jump(para: Int, char: Int = 0) {
            sheet = Sheet.None
            turnJob?.cancel(); pendingAnchor = null
            scope.launch { offset.snapTo(0f) }
            anchor = Anchor(para.coerceIn(0, n - 1), char)
        }

        // an accidental edge swipe just brings up the controls instead of closing the book
        BackHandler(enabled = !chrome && selection == null && active == null && sheet == Sheet.None) { chrome = true }
        BackHandler(enabled = selection != null || active != null || sheet != Sheet.None) { clearTransient() }
        val jumpRef by rememberUpdatedState<(Int) -> Unit>({ p -> jump(p) })

        fun commit(color: Int) {
            val s = selection ?: return
            val para = c.paras[s.para]
            val overlaps = bookHl.filter { it.para == s.para && it.start < s.end && s.start < it.end }
            val start = min(s.start, overlaps.minOfOrNull { it.start } ?: s.start)
            val end = max(s.end, overlaps.maxOfOrNull { it.end } ?: s.end)
            overlaps.forEach { Store.removeHighlight(it.id) }
            Store.addHighlight(
                Highlight(
                    id = UUID.randomUUID().toString(), bookId = book.id, para = s.para, start = start, end = end,
                    text = para.text.substring(start, end).trim(), color = color,
                    chapter = c.chapterAt(s.para)?.title ?: "", favorite = overlaps.any { it.favorite },
                )
            )
            haptics.play(Haptic.Confirm)
            selection = null
            toast = "Saved to Highlights"
        }

        // ---- the pages ----
        Box(
            Modifier
                .fillMaxSize()
                .blockEdgeSwipes()
                .hazeSource(haze)
                .pointerInput(fullW) {
                    detectTapGestures(
                        onTap = { pos ->
                            when {
                                clearTransient() -> {}
                                chrome -> chrome = false
                                pos.x < size.width * 0.3f -> turnRef(-1, false)
                                pos.x > size.width * 0.7f -> turnRef(1, false)
                                else -> { chrome = true; haptics.play(Haptic.Tick) }
                            }
                        },
                        onLongPress = { /* long-press on text = highlight (handled by the paragraph) */ },
                    )
                }
        ) {
            for (d in listOf(-1, 1, 0)) for (slot in 0 until step) {
                val page = (if (spread) pageAt(d * 2 + slot) else neighbour(d)) ?: continue
                val shift = if (!spread) 0f else if (slot == 0) -fullW / 4f else fullW / 4f
                key(page.start) { PageView(
                    page = page,
                    paginator = paginator,
                    c = c,
                    highlights = byPara,
                    selection = if (d == 0) selection else null,
                    theme = theme,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topReserve)
                        .width(contentWdp)
                        .graphicsLayer { translationX = offset.value + d * fullW + shift; alpha = if (d == 0) fade.value else 1f },
                    onTapHighlight = { active = it; selection = null },
                    onSelect = { s, isDragging -> selection = s; dragging = isDragging; active = null; chrome = false },
                    onLink = { target -> haptics.play(Haptic.Tick); jumpRef(target) },
                ) }
            }
        }

        // a small ribbon on a bookmarked page
        androidx.compose.animation.AnimatedVisibility(
            visible = markHere != null,
            enter = fadeIn(tween(160)) + slideInVertically(spring(0.6f, 500f)) { -it },
            exit = fadeOut(tween(140)) + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp),
        ) {
            Icon(Line.BookmarkFill, "Bookmarked", tint = Color(0xFFFF9F0A), modifier = Modifier.size(width = 18.dp, height = 30.dp))
        }

        // ---- quiet page furniture ----
        val chapterTitle = c.chapterAt(anchor.para)?.title ?: book.title
        val percent = if (n <= 1) 0 else ((anchor.para.toFloat() / (n - 1)) * 100).roundToInt()
        val chromeAlpha by animateFloatAsState(if (chrome) 0f else 1f, tween(200), label = "furniture")
        Text(
            chapterTitle, color = theme.sub, fontSize = 12.sp, fontFamily = Inter, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = maxOf(cutout, 30.dp)).widthIn(max = 280.dp)
                .graphicsLayer { alpha = chromeAlpha },
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).width(if (spread) maxWidth - (settings.margin * 2 + 16).dp else contentWdp)
                .graphicsLayer { alpha = chromeAlpha },
        ) {
            val s = sections[sec]
            val left = if (pages != null && !s.split) {
                val l = (pages.size - 1 - cur - (step - 1)).coerceAtLeast(0)
                if (l == 0) "Last page in chapter" else "$l ${if (l == 1) "page" else "pages"} left in chapter"
            } else ""
            Text(left, color = theme.sub, fontSize = 11.sp, fontFamily = Inter, modifier = Modifier.weight(1f))
            Text("$percent%", color = theme.sub, fontSize = 11.sp, fontFamily = Inter)
        }

        // ---- top controls ----
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(200)) + slideInVertically(spring(0.85f, 500f)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(spring(0.9f, 600f)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = topInset + 8.dp, start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircle(Line.Back, "Back", haze, tint = theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light, onClick = onBack)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(book.title, color = theme.text, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        Text(chapterTitle, color = theme.sub, fontFamily = Inter, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                    }
                }
                GlassCircle(if (markHere != null) Line.BookmarkFill else Line.Bookmark, "Bookmark this page", haze,
                    tint = if (markHere != null) Color(0xFFFF9F0A) else theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light) { toggleBookmark() }
                Spacer(Modifier.width(10.dp))
                GlassCircle(Line.Quote, "Highlights and bookmarks", haze, tint = theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light) { sheet = Sheet.Marks }
            }
        }

        // ---- bottom controls ----
        AnimatedVisibility(
            visible = chrome && selection == null && active == null,
            enter = fadeIn(tween(200)) + slideInVertically(spring(0.85f, 500f)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(spring(0.9f, 600f)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var scrub by remember { mutableFloatStateOf(-1f) }
            val shown = if (scrub >= 0) scrub else anchor.para.toFloat() / max(1, n - 1)
            Row(
                Modifier
                    .padding(start = 14.dp, end = 14.dp, bottom = bottomInset + 14.dp)
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Line.ListIcon, theme) { sheet = Sheet.Chapters }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    ThinSlider(
                        value = shown,
                        onChange = { scrub = it },
                        onFinished = {
                            if (scrub >= 0) jump((scrub * (n - 1)).roundToInt())
                            scrub = -1f
                        },
                        fg = theme.text,
                    )
                    Text(
                        if (scrub >= 0) "${(c.chapterAt((scrub * (n - 1)).roundToInt())?.title ?: "")} · ${(scrub * 100).roundToInt()}%"
                        else "$percent% of book",
                        color = theme.sub, fontFamily = Inter, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp), textAlign = TextAlign.Center,
                    )
                }
                Box(
                    Modifier.size(44.dp).bouncyClick(pressedScale = 0.85f) { sheet = Sheet.Settings },
                    contentAlignment = Alignment.Center,
                ) { Text("Aa", color = theme.text, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
            }
        }

        // ---- selection toolbar ----
        var lastSel by remember { mutableStateOf<Sel?>(null) }
        selection?.let { lastSel = it }
        Toolbar(visible = selection != null && !dragging, haze, theme, bottomInset) {
            HighlightColors.forEachIndexed { i, col -> ColorDot(col, false) { commit(i) } }
            Divider(theme)
            val word = lastSel?.let { s -> s.word ?: pickWord(c.paras[s.para].text.substring(s.start.coerceIn(0, c.paras[s.para].text.length), s.end.coerceIn(0, c.paras[s.para].text.length))) }
            if (word != null) BarIcon(Line.Define, theme) { defineWord = word; selection = null; sheet = Sheet.Define }
            BarIcon(Line.Paragraph, theme) { lastSel?.let { s -> selection = Sel(s.para, 0, c.paras[s.para].text.length) } }
            BarIcon(Line.Copy, theme) {
                lastSel?.let { s -> clipboard.setText(AnnotatedString(c.paras[s.para].text.substring(s.start, s.end))) }
                selection = null; toast = "Copied"
            }
            BarIcon(Line.Close, theme) { selection = null }
        }

        // ---- existing highlight toolbar ----
        var lastActive by remember { mutableStateOf<Highlight?>(null) }
        activeLive?.let { lastActive = it }
        Toolbar(visible = activeLive != null, haze, theme, bottomInset) {
            val h = lastActive
            HighlightColors.forEachIndexed { i, col ->
                ColorDot(col, h?.color == i) { h?.let { Store.updateHighlight(it.id) { x -> x.copy(color = i) } } }
            }
            Divider(theme)
            BarIcon(if (h?.favorite == true) Line.HeartFill else Line.Heart, theme,
                tint = if (h?.favorite == true) Color(0xFFFF375F) else theme.text) {
                h?.let { Store.updateHighlight(it.id) { x -> x.copy(favorite = !x.favorite) } }
                haptics.play(if (h?.favorite == true) Haptic.ToggleOff else Haptic.Confirm)
            }
            BarIcon(Line.Note, theme, tint = if (h?.note?.isNotBlank() == true) theme.accent else theme.text) { noteFor = h; active = null; sheet = Sheet.Note }
            BarIcon(Line.Copy, theme) { h?.let { clipboard.setText(AnnotatedString(it.text)) }; active = null; toast = "Copied" }
            BarIcon(Line.Trash, theme) { h?.let { Store.removeHighlight(it.id) }; haptics.play(Haptic.Reject); active = null; toast = "Highlight removed" }
        }

        // ---- sheets ----
        GlassSheet(sheet != Sheet.None, haze, theme, bottomInset, onDismiss = { sheet = Sheet.None }) {
            var lastSheet by remember { mutableStateOf(Sheet.Chapters) }
            if (sheet != Sheet.None) lastSheet = sheet
            when (lastSheet) {
                Sheet.Chapters -> ChaptersSheet(c, anchor.para, theme) { jump(it) }
                Sheet.Settings -> SettingsSheet(settings, theme)
                Sheet.Marks -> MarksSheet(bookHl, bookMarks, theme) { p, ch -> jump(p, ch) }
                Sheet.Define -> DefineContent(defineWord, theme.text, theme.sub)
                Sheet.Note -> noteFor?.let { h ->
                    androidx.compose.runtime.key(h.id) {
                        NoteEditor(h.text, bookHl.firstOrNull { it.id == h.id }?.note ?: h.note, theme.text, theme.sub) { t ->
                            Store.updateHighlight(h.id) { x -> x.copy(note = t) }
                            sheet = Sheet.None; toast = if (t.isBlank()) "Note removed" else "Note saved"
                        }
                    }
                }
                Sheet.None -> {}
            }
        }

        // ---- toast ----
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically(spring(0.7f, 500f)) { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 10.dp),
        ) {
            var last by remember { mutableStateOf("") }
            toast?.let { last = it }
            Text(
                last, color = theme.text, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

// ---------------- A page ----------------

@Composable
private fun PageView(
    page: Page,
    paginator: Paginator,
    c: BookContent,
    highlights: Map<Int, List<Highlight>>,
    selection: Sel?,
    theme: ReaderTheme,
    modifier: Modifier,
    onTapHighlight: (Highlight) -> Unit,
    onSelect: (Sel, Boolean) -> Unit,
    onLink: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    Column(modifier) {
        page.slices.forEachIndexed { i, s ->
            if (i > 0) Spacer(Modifier.height(with(density) { paginator.gapBefore(s.para).toDp() }))
            val para = c.paras[s.para]
            ParaText(
                index = s.para,
                para = para,
                style = if (para.heading) paginator.heading else paginator.body,
                highlights = highlights[s.para].orEmpty(),
                selection = selection?.takeIf { it.para == s.para },
                theme = theme,
                onTapHighlight = onTapHighlight,
                onSelect = onSelect,
                onLink = onLink,
                links = c.links,
                modifier = Modifier
                    .clipToBounds()
                    .layout { m, cons ->
                        val p = m.measure(Constraints.fixedWidth(cons.maxWidth))
                        layout(p.width, s.height.roundToInt()) { p.place(0, -s.top.roundToInt()) }
                    },
            )
        }
    }
}

// ---------------- Paragraph with highlight selection ----------------

private fun isWordChar(ch: Char) = ch.isLetterOrDigit() || ch == '\'' || ch == '’' || ch == '-'

private fun wordRange(t: String, off: Int): IntRange {
    if (t.isEmpty()) return 0 until 0
    var o = off.coerceIn(0, t.length - 1)
    if (!isWordChar(t[o]) && o > 0 && isWordChar(t[o - 1])) o--
    if (!isWordChar(t[o])) return o..o
    var s = o; while (s > 0 && isWordChar(t[s - 1])) s--
    var e = o; while (e < t.length - 1 && isWordChar(t[e + 1])) e++
    return s..e
}

private const val TERMINATORS = ".!?…"
private const val CLOSERS = ".!?…”’\"')]"

private fun sentenceRange(t: String, off: Int): IntRange {
    if (t.isEmpty()) return 0 until 0
    val o = off.coerceIn(0, t.length - 1)
    var s = o
    while (s > 0 && t[s - 1] !in TERMINATORS) s--
    while (s < o && (t[s].isWhitespace() || t[s] in "”’\"')]")) s++
    var e = o
    while (e < t.length && t[e] !in TERMINATORS) e++
    while (e < t.length && t[e] in CLOSERS) e++
    return s until e
}

@Composable
private fun ParaText(
    index: Int,
    para: Para,
    style: TextStyle,
    highlights: List<Highlight>,
    selection: Sel?,
    theme: ReaderTheme,
    onTapHighlight: (Highlight) -> Unit,
    onSelect: (Sel, Boolean) -> Unit,
    modifier: Modifier,
    onLink: (Int) -> Unit = {},
    links: IntArray = IntArray(0),
) {
    val text = para.text
    val link by rememberUpdatedState(onLink)
    val annotated = remember(para) { annotate(para) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hl by rememberUpdatedState(highlights)
    val tapHl by rememberUpdatedState(onTapHighlight)
    val select by rememberUpdatedState(onSelect)
    val haptics = rememberHaptics()
    val selAlpha by animateFloatAsState(if (selection != null) 1f else 0f, tween(140), label = "sel")
    val loupe = rememberLoupe()
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    fun showLoupe(pos: androidx.compose.ui.geometry.Offset) {
        val c = coords[0]?.takeIf { it.isAttached } ?: return
        val r = c.localToRoot(pos)
        loupe.show(r.x, r.y)
    }

    Text(
        annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { coords[0] = it }
            .drawBehind {
                val l = layout ?: return@drawBehind
                val len = text.length
                for (h in highlights) {
                    val s = h.start.coerceIn(0, len); val e = h.end.coerceIn(0, len)
                    if (e > s) drawPath(l.getPathForRange(s, e), HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)].copy(alpha = theme.hlAlpha))
                }
                if (selection != null && selection.end > selection.start) {
                    drawPath(
                        l.getPathForRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len)),
                        theme.accent.copy(alpha = 0.22f * selAlpha),
                    )
                }
            }
            // tap on an existing highlight (consumes so the page doesn't turn)
            .pointerInput(index) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val off = layout?.getOffsetForPosition(down.position) ?: -1
                    val h = hl.firstOrNull { off >= it.start && off < it.end }
                    var target = -1
                    if (h == null) {
                        var k = 0
                        while (k + 2 < para.styles.size) {
                            val t = para.styles[k + 2]
                            if (t >= com.aniq.glassreader.data.LINK_BASE && off >= para.styles[k] && off < para.styles[k + 1]) {
                                target = links.getOrElse(t - com.aniq.glassreader.data.LINK_BASE) { -1 }; break
                            }
                            k += 3
                        }
                        if (target < 0) return@awaitEachGesture
                    }
                    val up = waitForUpOrCancellation()
                    if (up != null && up.uptimeMillis - down.uptimeMillis < 400) {
                        up.consume()
                        if (h != null) tapHl(h) else link(target)
                    }
                }
            }
            // long-press to highlight: release = sentence, drag = exact words
            .pointerInput(index) {
                var anchor = 0 until 0
                var moved = false
                var lastStart = -1
                var lastEnd = -1
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        layout?.let { l ->
                            anchor = wordRange(text, l.getOffsetForPosition(pos))
                            moved = false
                            lastStart = anchor.first; lastEnd = anchor.last + 1
                            haptics.play(Haptic.LongPress)
                            showLoupe(pos)
                            select(Sel(index, anchor.first, anchor.last + 1), true)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        showLoupe(change.position)
                        layout?.let { l ->
                            val w = wordRange(text, l.getOffsetForPosition(change.position))
                            val s = min(anchor.first, w.first)
                            val e = max(anchor.last + 1, w.last + 1)
                            if (s != lastStart || e != lastEnd) {
                                moved = true; lastStart = s; lastEnd = e
                                haptics.play(Haptic.TextMove)
                                select(Sel(index, s, e), true)
                            }
                        }
                    },
                    onDragEnd = {
                        loupe.hide()
                        haptics.play(Haptic.GestureEnd)
                        if (!moved) {
                            val r = sentenceRange(text, anchor.first)
                            val w = if (anchor.last >= anchor.first) text.substring(anchor.first, anchor.last + 1) else null
                            select(Sel(index, r.first, r.last + 1, pickWord(w ?: "")), false)
                        } else select(Sel(index, lastStart, lastEnd), false)
                    },
                    onDragCancel = { loupe.hide(); if (lastEnd > lastStart) select(Sel(index, lastStart, lastEnd), false) },
                )
            },
    )
}

// ---------------- Controls ----------------

@Composable
internal fun Toolbar(
    visible: Boolean,
    haze: HazeState,
    theme: ReaderTheme,
    bottomInset: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(140)) + scaleIn(spring(0.62f, 600f), initialScale = 0.82f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 22.dp),
        ) {
            Row(
                Modifier
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

@Composable
internal fun BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: ReaderTheme,
    tint: Color = theme.text,
    onClick: () -> Unit,
) {
    Box(Modifier.size(42.dp).bouncyClick(pressedScale = 0.82f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val s by animateFloatAsState(if (selected) 1.12f else 1f, spring(0.5f, 600f), label = "dot")
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(26.dp)
            .bouncyClick(pressedScale = 0.8f, haptic = null, onClick = onClick)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 0.6.dp, Color.White.copy(alpha = if (selected) 0.95f else 0.35f), CircleShape),
    )
}

@Composable
internal fun Divider(theme: ReaderTheme) {
    Box(Modifier.padding(horizontal = 6.dp).width(0.8.dp).height(22.dp).background(theme.text.copy(alpha = 0.18f)))
}

// ---------------- Sheets ----------------

@Composable
internal fun GlassSheet(
    visible: Boolean,
    haze: HazeState,
    theme: ReaderTheme,
    bottomInset: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(spring(0.84f, 420f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(spring(0.95f, 600f)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .imePadding()
                    .padding(start = 8.dp, end = 8.dp, bottom = bottomInset + 8.dp)
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .glass(haze, RoundedCornerShape(36.dp), theme.glassTint, theme.bg, blur = 34.dp, light = theme.light, floating = true)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(50)).background(theme.text.copy(alpha = 0.2f)))
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
internal fun SheetTitle(text: String, theme: ReaderTheme) {
    Text(text, color = theme.text, fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp))
}

@Composable
private fun ChaptersSheet(c: BookContent, para: Int, theme: ReaderTheme, jump: (Int) -> Unit) {
    val current = c.chapterAt(para)
    SheetTitle("Contents", theme)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = max(0, c.chapters.indexOf(current) - 2))
    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        items(c.chapters) { ch ->
            val isCur = ch === current
            Text(
                ch.title,
                color = if (isCur) theme.text else theme.text.copy(alpha = 0.72f),
                fontFamily = Inter,
                fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick(pressedScale = 0.97f) { jump(ch.para) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isCur) theme.text.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun MarksSheet(list: List<Highlight>, marks: List<com.aniq.glassreader.data.Bookmark>, theme: ReaderTheme, jump: (Int, Int) -> Unit) {
    val sorted = remember(list) { list.sortedWith(compareBy({ it.para }, { it.start })) }
    var tab by remember { mutableStateOf(0) }
    Segmented(listOf("Highlights", "Bookmarks"), tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fg = theme.text)
    if (tab == 1) {
        BookmarkList(marks, theme.text, theme.sub, "Tap the bookmark at the top while reading to save your place.",
            onJump = { jump(it.para, it.char) }, onRemove = { Store.removeBookmark(it.id) })
        return
    }
    if (sorted.isEmpty()) {
        Text("Hold on any sentence to highlight it.", color = theme.sub, fontFamily = Inter, fontSize = 14.sp,
            modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sorted, key = { it.id }) { h ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .bouncyClick(pressedScale = 0.97f) { jump(h.para, h.start) }
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.text.copy(alpha = 0.05f))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape)
                    .background(HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)]))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(h.text, color = theme.text, fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    NoteLine(h.note, theme.text, maxLines = 3)
                    if (h.chapter.isNotBlank()) Text(h.chapter, color = theme.sub, fontFamily = Inter, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
internal fun SettingsSheet(s: ReaderSettings, theme: ReaderTheme, pdf: Boolean = false) {
    SheetTitle("Themes & Settings", theme)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).verticalScroll(rememberScrollState())) {
        Text("Background", color = theme.text, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        BackgroundPicker(s, theme.text)
        Spacer(Modifier.height(6.dp))
        ToggleRow("Black & White", "No colour, pure black screen. Easiest on battery", s.mono, theme) { v -> Store.updateSettings { it.copy(mono = v) } }
        if (pdf) {
            Spacer(Modifier.height(8.dp))
            Text("PDF pages", color = theme.text, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf("Original", "Match page", "Night"), s.pdfMode, { i -> Store.updateSettings { it.copy(pdfMode = i) } }, fg = theme.text)
        }
        if (!pdf) {
            Spacer(Modifier.height(6.dp))
            LabeledSlider("Text size", "${s.fontSize.roundToInt()}", s.fontSize, 14f..32f, theme) { v -> Store.updateSettings { it.copy(fontSize = v) } }
            LabeledSlider("Line spacing", String.format("%.1f", s.lineHeight), s.lineHeight, 1.2f..2.2f, theme) { v -> Store.updateSettings { it.copy(lineHeight = v) } }
            LabeledSlider("Margins", "${s.margin}", s.margin.toFloat(), 8f..56f, theme) { v -> Store.updateSettings { it.copy(margin = v.roundToInt()) } }
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Inter", "Serif"), if (s.serif) 1 else 0, { i -> Store.updateSettings { it.copy(serif = i == 1) } }, fg = theme.text)
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Justified", "Left aligned"), if (s.justify) 0 else 1, { i -> Store.updateSettings { it.copy(justify = i == 0) } }, fg = theme.text)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, on: Boolean, theme: ReaderTheme, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.98f, haptic = if (on) Haptic.ToggleOff else Haptic.ToggleOn) { onChange(!on) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.text, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = theme.sub, fontFamily = Inter, fontSize = 12.sp)
        }
        GlassSwitch(on, theme.text)
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    value: String,
    v: Float,
    range: ClosedFloatingPointRange<Float>,
    theme: ReaderTheme,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = theme.text, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = theme.sub, fontFamily = Inter, fontSize = 13.sp)
    }
    ThinSlider(value = v, onChange = onChange, range = range, fg = theme.text)
}
