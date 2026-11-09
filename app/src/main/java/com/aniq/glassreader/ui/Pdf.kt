package com.aniq.glassreader.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.ReaderSettings
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A tappable link on a page: normalised bounds, and either a page to go to or a URL. */
private class PdfLink(val r: android.graphics.RectF, val page: Int, val uri: String?)

/** A rendered page plus the rectangle (in bitmap pixels) that actually has content. */
private class Rendered(val bmp: Bitmap, val crop: android.graphics.Rect, val links: List<PdfLink> = emptyList(), val hasText: Boolean = false)

/** Text selected on a page: the words and their line boxes (normalised, 4 floats per box). */
private class TextSel(val page: Int, val text: String, val rects: FloatArray)

/** Thin wrapper around the platform PdfRenderer. Only ever touched from [paginationThread]. */
private class PdfDoc(file: java.io.File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    val count = renderer.pageCount

    fun render(index: Int, targetW: Int): Rendered {
        renderer.openPage(index).use { p ->
            val w = targetW.coerceIn(200, 2200)
            val h = (w * p.height.toFloat() / p.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val links = if (android.os.Build.VERSION.SDK_INT >= 35) runCatching { pageLinks(p) }.getOrDefault(emptyList()) else emptyList()
            val hasText = android.os.Build.VERSION.SDK_INT >= 35 &&
                runCatching { p.textContents.any { it.text.isNotBlank() } }.getOrDefault(false)
            return Rendered(bmp, contentBounds(bmp), links, hasText)
        }
    }

    /** Native text selection between two points (normalised page coordinates). */
    fun select(index: Int, a: Offset, b: Offset): TextSel? {
        if (android.os.Build.VERSION.SDK_INT < 35) return null
        return selectApi35(index, a, b)
    }

    @androidx.annotation.RequiresApi(35)
    private fun selectApi35(index: Int, a: Offset, b: Offset): TextSel? {
        renderer.openPage(index).use { p ->
            val pw = p.width.toFloat(); val ph = p.height.toFloat()
            fun pt(o: Offset) = android.graphics.Point((o.x * pw).roundToInt(), (o.y * ph).roundToInt())
            val sel = p.selectContent(
                android.graphics.pdf.models.selection.SelectionBoundary(pt(a)),
                android.graphics.pdf.models.selection.SelectionBoundary(pt(b)),
            ) ?: return null
            val parts = sel.selectedTextContents
            val text = parts.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return null
            val boxes = parts.flatMap { it.bounds }
            val arr = FloatArray(boxes.size * 4)
            boxes.forEachIndexed { i, r -> arr[i * 4] = r.left / pw; arr[i * 4 + 1] = r.top / ph; arr[i * 4 + 2] = r.right / pw; arr[i * 4 + 3] = r.bottom / ph }
            return TextSel(index, text, arr)
        }
    }

    @androidx.annotation.RequiresApi(35)
    private fun pageLinks(p: PdfRenderer.Page): List<PdfLink> {
        val pw = p.width.toFloat(); val ph = p.height.toFloat()
        fun norm(r: android.graphics.RectF) = android.graphics.RectF(r.left / pw, r.top / ph, r.right / pw, r.bottom / ph)
        val out = ArrayList<PdfLink>()
        for (g in p.gotoLinks) {
            val dest = g.destination.pageNumber
            for (r in g.bounds) out.add(PdfLink(norm(r), dest, null))
        }
        for (l in p.linkContents) {
            for (r in l.bounds) out.add(PdfLink(norm(r), -1, l.uri.toString()))
        }
        return out
    }

    /** Finds the inked area so blank page margins can be trimmed away. */
    private fun contentBounds(b: Bitmap): android.graphics.Rect {
        val w = b.width; val h = b.height
        val row = IntArray(w)
        var top = -1; var bottom = -1; var left = w; var right = -1
        var y = 0
        while (y < h) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0; var any = false
            while (x < w) {
                val c = row[x]
                val lum = ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10
                if (lum < 235) { any = true; if (x < left) left = x; if (x > right) right = x }
                x += 2
            }
            if (any) { if (top < 0) top = y; bottom = y }
            y += 2
        }
        if (top < 0 || right <= left) return android.graphics.Rect(0, 0, w, h)
        val pad = (w * 0.045f).toInt()
        return android.graphics.Rect((left - pad).coerceAtLeast(0), (top - pad).coerceAtLeast(0),
            (right + pad).coerceAtMost(w), (bottom + pad).coerceAtMost(h))
    }

    override fun close() { renderer.close(); pfd.close() }
}

private val invert = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )
)

/** Inverted, then mapped onto the theme's own text/background colours so it matches the page. */
private fun dimInvert(t: ReaderTheme): ColorMatrix {
    val bg = t.bg; val fg = t.text
    fun ch(b: Float, f: Float) = floatArrayOf(-(f - b), b * 255f + (f - b) * 255f)
    val r = ch(bg.red, fg.red); val g = ch(bg.green, fg.green); val bl = ch(bg.blue, fg.blue)
    // luminance-based so colour images become tasteful greys
    return ColorMatrix(floatArrayOf(
        r[0] * 0.299f, r[0] * 0.587f, r[0] * 0.114f, 0f, r[1],
        g[0] * 0.299f, g[0] * 0.587f, g[0] * 0.114f, 0f, g[1],
        bl[0] * 0.299f, bl[0] * 0.587f, bl[0] * 0.114f, 0f, bl[1],
        0f, 0f, 0f, 1f, 0f,
    ))
}

/** Box coordinates → normalised full-page coordinates (accounts for trimmed margins). */
private fun toPage(rd: Rendered, box: Size, p: Offset, align: Float = 0f): Offset {
    val cr = rd.crop
    val r = fitRect(box, cr.width(), cr.height(), align)
    val bx = cr.left + (p.x - r.left) / r.width * cr.width()
    val by = cr.top + (p.y - r.top) / r.height * cr.height()
    return Offset(bx / rd.bmp.width, by / rd.bmp.height)
}

/** Normalised full-page coordinates → box coordinates. */
private fun fromPage(rd: Rendered, r: Rect, nx: Float, ny: Float): Offset {
    val cr = rd.crop
    return Offset(
        r.left + (nx * rd.bmp.width - cr.left) / cr.width() * r.width,
        r.top + (ny * rd.bmp.height - cr.top) / cr.height() * r.height,
    )
}

/**
 * Where a page image lands inside its box when fitted (ContentScale.Fit).
 * [align] -1 hugs the left edge, 1 the right (used to pull a two-page spread towards the fold).
 */
private fun fitRect(box: Size, w: Int, h: Int, align: Float = 0f): Rect {
    val a = w.toFloat() / h
    return if (box.width / box.height > a) {
        val dw = box.height * a; Rect(Offset((box.width - dw) * (1f + align) / 2f, 0f), Size(dw, box.height))
    } else {
        val dh = box.width / a; Rect(Offset(0f, (box.height - dh) / 2), Size(box.width, dh))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfReader(book: Book, jumpPage: Int?, settings: ReaderSettings, theme: ReaderTheme, onBack: () -> Unit) {
    val doc by produceState<PdfDoc?>(null, book.id) {
        value = runCatching { withContext(paginationThread) { PdfDoc(Store.bookFile(book)) } }.getOrNull()
        if (value == null) onBack()
    }
    DisposableEffect(doc) {
        val d = doc
        onDispose { if (d != null) CoroutineScope(paginationThread).launch { runCatching { d.close() } } }
    }
    LaunchedEffect(book.id) { Store.markOpened(book.id) }
    BackHandler(onBack = onBack)
    val d = doc ?: return

    val haze = remember { HazeState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val haptics = rememberHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardPdf = androidx.compose.ui.platform.LocalClipboardManager.current
    val density = LocalDensity.current
    val count = d.count
    var page by remember { mutableIntStateOf((jumpPage ?: book.progressIndex).coerceIn(0, count - 1)) }
    var chrome by remember { mutableStateOf(false) }
    var sheet by remember { mutableIntStateOf(0) } // 0 none, 1 settings, 2 clips, 3 define, 4 note
    var defineWord by remember { mutableStateOf("") }
    var noteFor by remember { mutableStateOf<Highlight?>(null) }
    var lastColor by remember { mutableIntStateOf(0) }
    val allMarks by Store.bookmarks.collectAsState()
    val bookMarks = remember(allMarks, book.id) { allMarks.filter { it.bookId == book.id }.sortedBy { it.para } }
    var region by remember { mutableStateOf<Rect?>(null) }   // in box coordinates
    var selecting by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Highlight?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val bitmaps = remember { mutableStateMapOf<Int, Rendered>() }

    val allHl by Store.highlights.collectAsState()
    val clips = remember(allHl, book.id) { allHl.filter { it.bookId == book.id && it.rect != null } }
    var tsel by remember { mutableStateOf<TextSel?>(null) }
    val selectRequests = remember { kotlinx.coroutines.flow.MutableStateFlow<Triple<Int, Offset, Offset>?>(null) }
    val activeLive = active?.let { a -> clips.firstOrNull { it.id == a.id } }
    val clipsNow by rememberUpdatedState(clips)

    // system bars + keep awake + save
    val view = LocalView.current
    val window = remember(view) { view.context.activity()?.window }
    val controller = remember(window) { window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(controller, theme.light) {
        controller?.isAppearanceLightStatusBars = theme.light
        controller?.isAppearanceLightNavigationBars = theme.light
        onDispose { controller?.isAppearanceLightStatusBars = !UiMode.dark; controller?.isAppearanceLightNavigationBars = !UiMode.dark }
    }
    LaunchedEffect(controller, chrome) {
        controller ?: return@LaunchedEffect
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    val pageNow by rememberUpdatedState(page)
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        ReaderKeys.active = true
        com.aniq.glassreader.CoverDisplay.openBook.value = book.id
        onDispose {
            ReaderKeys.active = false
            com.aniq.glassreader.CoverDisplay.openBook.value = null
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            Store.updateProgress(book.id, pageNow, 0, count)
        }
    }
    LaunchedEffect(page) { delay(300); Store.updateProgress(book.id, page, 0, count) }
    ReadingClock(book.id, page)
    val loupe = rememberLoupe()
    LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }

    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()

    // 0 = original pages, 1 = blend into the page colour, 2 = night (inverted)
    val filter: ColorFilter? = when (settings.pdfMode) {
        2 -> ColorFilter.colorMatrix(invert)
        1 -> if (theme.light) { if (theme.bg != Color.White) ColorFilter.tint(theme.bg, BlendMode.Multiply) else null }
             else ColorFilter.colorMatrix(dimInvert(theme))
        else -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        // landscape (the Fold opened sideways): one page on each half of the screen
        val spread = constraints.maxWidth > constraints.maxHeight && count > 1
        val step = if (spread) 2 else 1
        // the outer screen shows the cover only while reading two pages sideways
        LaunchedEffect(spread) { com.aniq.glassreader.CoverDisplay.landscape.value = spread }
        DisposableEffect(Unit) { onDispose { com.aniq.glassreader.CoverDisplay.landscape.value = false } }
        val markHere = bookMarks.firstOrNull { it.para in page until page + step }
        fun toggleBookmark() {
            val m = markHere
            if (m != null) { Store.removeBookmark(m.id); haptics.play(Haptic.ToggleOff); toast = "Bookmark removed"; return }
            Store.addBookmark(com.aniq.glassreader.data.Bookmark(UUID.randomUUID().toString(), book.id, page, 0, "Page ${page + 1}", ""))
            haptics.play(Haptic.Confirm); toast = "Bookmarked"
        }
        val slotW = if (spread) fullW / 2f else fullW
        val renderW = (slotW * 1.3f).roundToInt()
        LaunchedEffect(spread) { if (spread && page % 2 == 1) page -= 1 }

        LaunchedEffect(page, renderW, spread) {
            val want = if (spread) listOf(page, page + 1, page + 2, page + 3, page - 2, page - 1) else listOf(page, page + 1, page - 1)
            val target = renderW.coerceIn(200, 2200)
            for (p in want) {
                if (p in 0 until count && bitmaps[p]?.bmp?.width != target) {
                    bitmaps[p] = withContext(paginationThread) { d.render(p, renderW) }
                }
            }
            bitmaps.keys.filter { it < page - 2 * step || it > page + 2 * step + 1 }.forEach { bitmaps.remove(it) }
        }

        val offset = remember { Animatable(0f) }
        val fade = remember { Animatable(1f) }
        var turnJob by remember { mutableStateOf<Job?>(null) }
        var pending by remember { mutableStateOf<Int?>(null) }
        var swipeTurn: (Int) -> Unit = {}

        fun clearTransient(): Boolean {
            if (region != null || tsel != null || active != null || sheet != 0) { region = null; tsel = null; active = null; sheet = 0; return true }
            return false
        }
        // an accidental edge swipe just brings up the controls instead of closing the book
        BackHandler(enabled = !chrome && region == null && tsel == null && active == null && sheet == 0) { chrome = true }
        BackHandler(enabled = region != null || tsel != null || active != null || sheet != 0) { clearTransient() }
        LaunchedEffect(Unit) {
            selectRequests.collectLatest { req ->
                req ?: return@collectLatest
                val r = withContext(paginationThread) { runCatching { d.select(req.first, req.second, req.third) }.getOrNull() }
                if (r != null) { if (tsel?.text != r.text) haptics.play(Haptic.TextMove); tsel = r }
            }
        }

        fun turn(dir: Int, fromDrag: Boolean = false) {
            if (settings.swipe) { swipeTurn(dir); return }   // volume keys in swipe mode: a full curl on its own
            pending?.let { page = it; pending = null }
            turnJob?.cancel()
            scope.launch { fade.snapTo(1f) }
            chrome = false
            val target = page + dir * step
            if (target !in 0 until count) {
                haptics.play(Haptic.Reject)
                turnJob = scope.launch {
                    if (!fromDrag) offset.animateTo(-dir * 36f, spring(0.6f, 900f))
                    offset.animateTo(0f, spring(0.55f, 500f))
                }
                return
            }
            pending = target
            if (!fromDrag) haptics.play(Haptic.PageTurn)
            if (settings.pageAnim == 1 && !fromDrag) {
                turnJob = scope.launch { fade.animateTo(0f, tween(90)); page = target; pending = null; fade.animateTo(1f, tween(170)) }
                return
            }
            turnJob = scope.launch {
                offset.animateTo(-dir * fullW, spring(1f, 420f))
                page = target; pending = null
                offset.snapTo(0f)
            }
        }
        val turnNow by rememberUpdatedState<(Int) -> Unit>({ turn(it) })
        val turnRef by rememberUpdatedState<(Int, Boolean) -> Unit>({ d, f -> turn(d, f) })
        LaunchedEffect(Unit) { ReaderKeys.turns.collect { if (sheet == 0) turnNow(it) } }

        fun jump(p: Int) {
            sheet = 0; turnJob?.cancel(); pending = null
            scope.launch { offset.snapTo(0f) }
            page = p.coerceIn(0, count - 1).let { if (spread) it - it % 2 else it }
        }
        val jumpRef by rememberUpdatedState<(Int) -> Unit>({ p -> jump(p) })

        val cutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val pageTop = maxOf(cutout, 30.dp) + 10.dp
        val pageBottom = 34.dp
        val sidePad = 18.dp
        val topPxC = with(density) { pageTop.toPx() }
        val padPxC = with(density) { sidePad.toPx() }
        // the box each page is fitted into
        val boxSz = Size(slotW - 2 * padPxC, constraints.maxHeight - topPxC - with(density) { pageBottom.toPx() })
        // in a spread the left page hugs the fold from the left, the right page from the right
        fun alignOf(slot: Int) = if (!spread) 0f else if (slot == 0) 1f else -1f
        /** Which page a screen point falls on, and where it is inside that page's box. */
        fun locate(pos: Offset): Triple<Int, Offset, Float>? {
            val slot = if (spread && pos.x >= slotW) 1 else 0
            val p = page + slot
            if (p !in 0 until count) return null
            return Triple(p, Offset(pos.x - slot * slotW - padPxC, pos.y - topPxC), alignOf(slot))
        }
        val extraEnd = if (spread) maxWidth / 2 else 0.dp
        var regionPage by remember { mutableIntStateOf(0) }
        var regionAlign by remember { mutableFloatStateOf(0f) }
        var rootOff by remember { mutableStateOf(Offset.Zero) }

        /** Draws page [p] (bitmap, highlights, and — if [live] — the selection) fitted into this box. */
        fun androidx.compose.ui.graphics.drawscope.DrawScope.paintPage(p: Int, align: Float, live: Boolean) {
            val rd = bitmaps[p] ?: return
            val img = rd.bmp.asImageBitmap()
            val cr = rd.crop
            val r = fitRect(size, cr.width(), cr.height(), align)
            drawImage(
                img,
                srcOffset = IntOffset(cr.left, cr.top),
                srcSize = IntSize(cr.width(), cr.height()),
                dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
                colorFilter = filter,
            )
            fun boxes(q: FloatArray, color: Color, framed: Boolean) {
                var i = 0
                while (i + 3 < q.size) {
                    val a = fromPage(rd, r, q[i], q[i + 1]); val b = fromPage(rd, r, q[i + 2], q[i + 3])
                    val sz = Size(b.x - a.x, b.y - a.y)
                    if (framed) {
                        drawRoundRect(color.copy(alpha = 0.16f), a, sz, CornerRadius(14f))
                        drawRoundRect(color.copy(alpha = 0.9f), a, sz, CornerRadius(14f), style = Stroke(3f))
                    } else {
                        drawRoundRect(color.copy(alpha = if (theme.light) 0.38f else 0.32f), a, sz, CornerRadius(6f),
                            blendMode = if (theme.light && settings.pdfMode != 2) BlendMode.Multiply else BlendMode.SrcOver)
                    }
                    i += 4
                }
            }
            for (h in clips) if (h.para == p) boxes(h.rect!!, HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)], h.isClip)
            if (live) tsel?.takeIf { it.page == p }?.let { boxes(it.rects, theme.accent.copy(alpha = 0.7f), false) }
            if (live && p == regionPage) region?.let { rg ->
                drawRoundRect(theme.accent.copy(alpha = 0.12f), rg.topLeft, rg.size, CornerRadius(16f))
                drawRoundRect(theme.accent.copy(alpha = 0.85f), rg.topLeft, rg.size, CornerRadius(16f), style = Stroke(3f))
            }
        }

        // ---- swipe mode: paper curl ----
        var curl by remember { mutableStateOf<CurlTurn?>(null) }
        var curlTarget by remember { mutableIntStateOf(-1) }
        val fullH = constraints.maxHeight.toFloat()
        fun beginCurl(dir: Int): CurlTurn? {
            if (curl != null) return null
            val target = page + dir * step
            if (target !in 0 until count || (spread && dir < 0 && page - 2 < 0)) { haptics.play(Haptic.Reject); return null }
            clearTransient(); chrome = false
            val t = when {
                !spread && dir > 0 -> CurlTurn(1, Offset(fullW, fullH), Offset(-fullW, fullH))
                !spread -> CurlTurn(-1, Offset(-fullW, fullH), Offset(fullW, fullH))
                dir > 0 -> CurlTurn(1, Offset(slotW, fullH), Offset(-slotW, fullH))
                else -> CurlTurn(-1, Offset(0f, fullH), Offset(fullW, fullH))
            }
            curlTarget = target; curl = t
            return t
        }
        fun endCurl(t: CurlTurn) {
            if (t.completing && curlTarget >= 0) { page = curlTarget; haptics.play(Haptic.PageTurn) }
            curl = null; curlTarget = -1
        }
        val beginRef by rememberUpdatedState<(Int) -> CurlTurn?>({ beginCurl(it) })
        val endRef by rememberUpdatedState<(CurlTurn) -> Unit>({ endCurl(it) })
        swipeTurn = { d -> beginCurl(d)?.let { t -> scope.launch { t.auto(); endCurl(t) } } }

        Box(
            Modifier
                .fillMaxSize()
                .blockEdgeSwipes()
                .onRootPosition { rootOff = it }
                .hazeSource(haze)
                .then(if (!settings.swipe) Modifier else Modifier.pointerInput(fullW, spread) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { ch, _ -> ch.consume() }
                            ?: return@awaitEachGesture
                        val dir = if (slop.position.x < start.x) 1 else -1
                        val t = beginRef(dir) ?: return@awaitEachGesture
                        var last = slop.position.x; var vx = 0f
                        horizontalDrag(slop.id) { ch ->
                            val dx = ch.position.x - start.x; val dy = ch.position.y - start.y
                            val lift = max(0f, -dy) * 0.35f + kotlin.math.abs(dx) * 0.10f
                            scope.launch { t.follow(dx, lift) }
                            vx = ch.position.x - last; last = ch.position.x
                            ch.consume()
                        }
                        val fling = if (dir > 0) vx < -8f else vx > 8f
                        val backFling = if (dir > 0) vx > 8f else vx < -8f
                        scope.launch { t.settle(!backFling && (fling || t.fraction > 0.25f)); endRef(t) }
                    }
                })
                .pointerInput(fullW, spread) {
                    detectTapGestures(
                        onTap = { pos ->
                            // tapping an existing clip opens its actions
                            val at = locate(pos)
                            val rd = at?.let { bitmaps[it.first] }
                            val hit = if (at != null && rd != null) {
                                val n = toPage(rd, boxSz, at.second, at.third)
                                val nx = n.x; val ny = n.y
                                val lk = rd.links.firstOrNull { it.r.contains(nx, ny) }
                                if (lk != null && region == null && tsel == null && active == null && sheet == 0) {
                                    haptics.play(Haptic.Tick)
                                    if (lk.page >= 0) jumpRef(lk.page)
                                    else lk.uri?.let { u -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) } }
                                    return@detectTapGestures
                                }
                                clipsNow.firstOrNull { it.para == at.first && it.rect!!.let { q -> (q.indices step 4).any { i -> nx in q[i]..q[i + 2] && ny in q[i + 1]..q[i + 3] } } }
                            } else null
                            when {
                                clearTransient() -> {}
                                hit != null -> { active = hit; haptics.play(Haptic.Tick) }
                                chrome -> chrome = false
                                settings.swipe && (pos.x < size.width * 0.3f || pos.x > size.width * 0.7f) -> {}   // swipe mode: no tap turns
                                pos.x < size.width * 0.3f -> turnRef(-1, false)
                                pos.x > size.width * 0.7f -> turnRef(1, false)
                                else -> { chrome = true; haptics.play(Haptic.Tick) }
                            }
                        },
                        onLongPress = { },
                    )
                }
                // page turns are tap-only: no swipe to turn
                .pointerInput(fullW, spread) {
                    // hold, then drag to select text (or frame a region on scanned pages)
                    var start = Offset.Zero
                    var origin = Offset.Zero
                    var selPage = 0
                    var align = 0f
                    var textMode = false
                    var startN = Offset.Zero
                    var rdNow: Rendered? = null
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val at = locate(pos) ?: return@detectDragGesturesAfterLongPress
                            selPage = at.first; align = at.third
                            start = at.second
                            origin = pos - at.second
                            regionPage = selPage; regionAlign = align
                            rdNow = bitmaps[selPage]
                            textMode = rdNow?.hasText == true
                            selecting = true; active = null; chrome = false; tsel = null; region = null
                            haptics.play(Haptic.LongPress)
                            loupe.show(rootOff.x + pos.x, rootOff.y + pos.y)
                            if (textMode) {
                                startN = toPage(rdNow!!, boxSz, start, align)
                                // a hair-wide span grabs the word under the finger
                                selectRequests.value = Triple(selPage, startN.copy(x = startN.x - 0.004f), startN.copy(x = startN.x + 0.004f))
                            } else region = Rect(start, start)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (!selecting) return@detectDragGesturesAfterLongPress
                            loupe.show(rootOff.x + change.position.x, rootOff.y + change.position.y)
                            val p = change.position - origin
                            if (textMode) {
                                selectRequests.value = Triple(selPage, startN, toPage(rdNow!!, boxSz, p, align))
                            } else {
                                region = Rect(min(start.x, p.x), min(start.y, p.y), max(start.x, p.x), max(start.y, p.y))
                            }
                        },
                        onDragEnd = {
                            loupe.hide()
                            selecting = false
                            haptics.play(Haptic.GestureEnd)
                            if (!textMode) {
                                val r = region
                                if (r == null || r.width < 24f || r.height < 24f) region = null
                            }
                        },
                        onDragCancel = { loupe.hide(); selecting = false; region = null; tsel = null },
                    )
                },
        ) {
            val ct = curl
            @Composable
            fun PdfSheet(p: Int, align: Float, modifier: Modifier) {
                Box(modifier) {
                    Box(Modifier.fillMaxSize().padding(top = pageTop, bottom = pageBottom, start = sidePad, end = sidePad)
                        .drawBehind { if (p in 0 until count) paintPage(p, align, false) })
                }
            }
            val paper = theme.bg.copy(alpha = 1f)
            val halfDp = with(density) { (fullW / 2f).toDp() }
            if (ct != null) {
                val pv = { ct.p.value }
                when {
                    !spread && ct.dir > 0 -> {
                        PdfSheet(page + 1, 0f, Modifier.fillMaxSize())
                        PdfSheet(page, 0f, Modifier.fillMaxSize().pageCurl(pv, 0f, fullW, paper, !theme.light, false))
                    }
                    !spread -> {
                        PdfSheet(page, 0f, Modifier.fillMaxSize())
                        PdfSheet(page - 1, 0f, Modifier.fillMaxSize().pageCurl(pv, 0f, fullW, paper, !theme.light, false))
                    }
                    ct.dir > 0 -> {
                        PdfSheet(page, 1f, Modifier.width(halfDp).fillMaxHeight())
                        PdfSheet(page + 3, -1f, Modifier.offset(x = halfDp).width(halfDp).fillMaxHeight())
                        Box(Modifier.offset(x = halfDp).width(halfDp).fillMaxHeight().pageCurl(pv, 0f, slotW, paper, !theme.light, true)) {
                            PdfSheet(page + 1, -1f, Modifier.fillMaxSize())
                            PdfSheet(page + 2, 1f, Modifier.offset(x = -halfDp).width(halfDp).fillMaxHeight())
                        }
                    }
                    else -> {
                        PdfSheet(page + 1, -1f, Modifier.offset(x = halfDp).width(halfDp).fillMaxHeight())
                        PdfSheet(page - 2, 1f, Modifier.width(halfDp).fillMaxHeight())
                        Box(Modifier.width(halfDp).fillMaxHeight().pageCurl(pv, slotW, 0f, paper, !theme.light, true)) {
                            PdfSheet(page, 1f, Modifier.fillMaxSize())
                            PdfSheet(page - 1, -1f, Modifier.offset(x = halfDp).width(halfDp).fillMaxHeight())
                        }
                    }
                }
            } else for (dlt in listOf(-1, 1, 0)) {
                for (slot in 0 until step) {
                val p = page + dlt * step + slot
                if (p !in 0 until count) continue
                if (bitmaps[p] == null) continue
                val align = alignOf(slot)
                key(p) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = offset.value + dlt * fullW + slot * slotW; alpha = if (dlt == 0) fade.value else 1f }
                        .padding(top = pageTop, bottom = pageBottom, start = sidePad, end = sidePad + extraEnd)
                        .drawBehind { paintPage(p, align, dlt == 0) },
                )
                }
                }
            }
            // a soft crease down the middle of a spread, like the spine of a book
            if (spread) Box(
                Modifier.fillMaxSize().graphicsLayer { translationX = offset.value; alpha = fade.value }.drawBehind {
                    val cx = size.width / 2f; val w = 28.dp.toPx()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to Color.Transparent, 0.5f to (if (theme.light) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f)), 1f to Color.Transparent,
                            startX = cx - w, endX = cx + w,
                        ),
                        topLeft = Offset(cx - w, 0f), size = Size(w * 2, size.height),
                    )
                }
            )
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

        // quiet furniture
        val furniture by animateFloatAsState(if (chrome) 0f else 1f, tween(200), label = "f")
        Text(
            if (spread && page + 1 < count) "${page + 1}–${page + 2} of $count" else "${page + 1} of $count", color = theme.sub, fontFamily = Inter, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).graphicsLayer { alpha = furniture },
        )

        // top controls
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(200)) + slideInVertically(spring(0.85f, 500f)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(spring(0.9f, 600f)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = topInset + 8.dp, start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(Line.Back, "Back", haze, tint = theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light, onClick = onBack)
                // balances the two buttons on the right, so the title sits in the true centre of the screen
                androidx.compose.foundation.layout.Spacer(Modifier.width(56.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        book.title, color = theme.text, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                            .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                GlassCircle(if (markHere != null) Line.BookmarkFill else Line.Bookmark, "Bookmark this page", haze,
                    tint = if (markHere != null) Color(0xFFFF9F0A) else theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light) { toggleBookmark() }
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                GlassCircle(Line.Quote, "Highlights and bookmarks", haze, tint = theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light) { sheet = 2 }
            }
        }

        // bottom controls
        AnimatedVisibility(
            visible = chrome && region == null && tsel == null && active == null,
            enter = fadeIn(tween(200)) + slideInVertically(spring(0.85f, 500f)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(spring(0.9f, 600f)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var scrub by remember { mutableFloatStateOf(-1f) }
            val shown = if (scrub >= 0) scrub else page.toFloat() / max(1, count - 1)
            Row(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = bottomInset + 14.dp).widthIn(max = 620.dp).fillMaxWidth()
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    ThinSlider(shown, { scrub = it }, fg = theme.text, onFinished = {
                        if (scrub >= 0) jump((scrub * (count - 1)).roundToInt()); scrub = -1f
                    })
                    Text("Page ${(shown * (count - 1)).roundToInt() + 1} of $count", color = theme.sub, fontFamily = Inter, fontSize = 11.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp))
                }
                Box(Modifier.size(44.dp).bouncyClick(pressedScale = 0.85f) { sheet = 1 }, contentAlignment = Alignment.Center) {
                    Text("Aa", color = theme.text, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
            }
        }

        // region toolbar
        Toolbar(visible = region != null && !selecting, haze, theme, bottomInset) {
            HighlightColors.forEachIndexed { i, col ->
                ColorDot(col, false) {
                    val rg = region; val rp = regionPage; val rd = bitmaps[rp]
                    if (rg != null && rd != null) {
                        val bmp = rd.bmp
                        val p0 = toPage(rd, boxSz, rg.topLeft, regionAlign); val p1 = toPage(rd, boxSz, rg.bottomRight, regionAlign)
                        val l = p0.x.coerceIn(0f, 1f); val t = p0.y.coerceIn(0f, 1f)
                        val rr = p1.x.coerceIn(0f, 1f); val b = p1.y.coerceIn(0f, 1f)
                        if (rr - l > 0.02f && b - t > 0.02f) {
                            val crop = Bitmap.createBitmap(
                                bmp, (l * bmp.width).toInt(), (t * bmp.height).toInt(),
                                ((rr - l) * bmp.width).toInt().coerceAtLeast(1), ((b - t) * bmp.height).toInt().coerceAtLeast(1),
                            )
                            Store.addClip(
                                Highlight(UUID.randomUUID().toString(), book.id, rp, 0, 0, "", i, "Page ${rp + 1}", rect = floatArrayOf(l, t, rr, b)),
                                crop,
                            )
                            haptics.play(Haptic.Confirm)
                            toast = "Saved to Highlights"
                        }
                    }
                    region = null
                }
            }
            Divider(theme)
            BarIcon(Line.Close, theme) { region = null }
        }

        // text selection toolbar
        var lastSel by remember { mutableStateOf<TextSel?>(null) }
        tsel?.let { lastSel = it }
        Toolbar(visible = tsel != null && !selecting, haze, theme, bottomInset) {
            HighlightColors.forEachIndexed { i, col ->
                ColorDot(col, false) {
                    lastSel?.let { t ->
                        Store.addHighlight(Highlight(UUID.randomUUID().toString(), book.id, t.page, 0, 0, t.text, i, "Page ${t.page + 1}", rect = t.rects))
                        haptics.play(Haptic.Confirm); toast = "Saved to Highlights"; lastColor = i
                    }
                    tsel = null
                }
            }
            Divider(theme)
            // highlight and write a note in one go (uses the last colour you picked)
            BarIcon(Line.Note, theme) {
                lastSel?.let { t ->
                    val h = Highlight(UUID.randomUUID().toString(), book.id, t.page, 0, 0, t.text, lastColor, "Page ${t.page + 1}", rect = t.rects)
                    Store.addHighlight(h); haptics.play(Haptic.Confirm)
                    noteFor = h; sheet = 4
                }
                tsel = null
            }
            val word = lastSel?.let { pickWord(it.text) }
            if (word != null) BarIcon(Line.Define, theme) { defineWord = word; tsel = null; sheet = 3 }
            BarIcon(Line.Copy, theme) {
                lastSel?.let { clipboardPdf.setText(androidx.compose.ui.text.AnnotatedString(it.text)) }
                haptics.play(Haptic.Confirm); tsel = null; toast = "Copied"
            }
            BarIcon(Line.Close, theme) { tsel = null }
        }

        // existing clip toolbar
        var lastActive by remember { mutableStateOf<Highlight?>(null) }
        activeLive?.let { lastActive = it }
        Toolbar(visible = activeLive != null, haze, theme, bottomInset) {
            val h = lastActive
            HighlightColors.forEachIndexed { i, col ->
                ColorDot(col, h?.color == i) { h?.let { Store.updateHighlight(it.id) { x -> x.copy(color = i) } } }
            }
            Divider(theme)
            BarIcon(if (h?.favorite == true) Line.HeartFill else Line.Heart, theme, tint = if (h?.favorite == true) Color(0xFFFF375F) else theme.text) {
                h?.let { Store.updateHighlight(it.id) { x -> x.copy(favorite = !x.favorite) } }
            }
            BarIcon(Line.Note, theme, tint = if (h?.note?.isNotBlank() == true) theme.accent else theme.text) { noteFor = h; active = null; sheet = 4 }
            BarIcon(Line.Trash, theme) { h?.let { Store.removeHighlight(it.id) }; haptics.play(Haptic.Reject); active = null; toast = "Removed" }
        }

        GlassSheet(sheet != 0, haze, theme, bottomInset, onDismiss = { sheet = 0 }) {
            var last by remember { mutableIntStateOf(1) }
            if (sheet != 0) last = sheet
            var marksTab by remember { mutableIntStateOf(0) }
            if (last == 1) SettingsSheet(settings, theme, pdf = true)
            else if (last == 3) DefineContent(defineWord, theme.text, theme.sub)
            else if (last == 4) noteFor?.let { h ->
                key(h.id) {
                    NoteEditor(h.text, clips.firstOrNull { it.id == h.id }?.note ?: h.note, theme.text, theme.sub) { t ->
                        Store.updateHighlight(h.id) { x -> x.copy(note = t) }
                        sheet = 0; toast = if (t.isBlank()) "Note removed" else "Note saved"
                    }
                }
            }
            else {
                Segmented(listOf("Highlights", "Bookmarks"), marksTab, { marksTab = it }, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fg = theme.text)
                if (marksTab == 1) BookmarkList(bookMarks, theme.text, theme.sub, "Tap the bookmark at the top while reading to save your place.",
                    onJump = { jump(it.para) }, onRemove = { Store.removeBookmark(it.id) })
                else if (clips.isEmpty()) {
                    Text("Hold on a word, then drag across the text you want.", color = theme.sub, fontFamily = Inter,
                        fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                } else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(clips.sortedBy { it.para }, key = { it.id }) { h ->
                        val bmp by produceState<ImageBitmap?>(null, h.id) {
                            value = withContext(Dispatchers.IO) { Store.clip(h.id, 700)?.asImageBitmap() }
                        }
                        Column(
                            Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.97f) { jump(h.para) }
                                .clip(RoundedCornerShape(18.dp)).background(theme.text.copy(alpha = 0.05f)).padding(10.dp)
                        ) {
                            if (h.isClip) bmp?.let {
                                Image(it, null, contentScale = ContentScale.Fit, colorFilter = filter,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                            } else Text(h.text, color = theme.text, fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            NoteLine(h.note, theme.text, maxLines = 3)
                            Text("Page ${h.para + 1}", color = theme.sub, fontFamily = Inter, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        Celebrations(theme, Modifier.align(Alignment.TopCenter).padding(top = topInset + 10.dp))

        AnimatedVisibility(
            visible = toast != null, enter = fadeIn() + slideInVertically(spring(0.7f, 500f)) { -it / 2 }, exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 10.dp),
        ) {
            var last by remember { mutableStateOf("") }
            toast?.let { last = it }
            Text(last, color = theme.text, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                    .padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

private fun Modifier.onRootPosition(block: (Offset) -> Unit): Modifier =
    this.then(Modifier.onGloballyPositioned { block(it.positionInRoot()) })
