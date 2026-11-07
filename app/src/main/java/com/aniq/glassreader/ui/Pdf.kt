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
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Thin wrapper around the platform PdfRenderer. Only ever touched from [paginationThread]. */
private class PdfDoc(file: java.io.File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    val count = renderer.pageCount

    fun render(index: Int, targetW: Int): Bitmap {
        renderer.openPage(index).use { p ->
            val w = targetW.coerceIn(200, 2400)
            val h = (w * p.height.toFloat() / p.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
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

/** Where a page image lands inside its box when fitted (ContentScale.Fit). */
private fun fitRect(box: Size, w: Int, h: Int): Rect {
    val a = w.toFloat() / h
    return if (box.width / box.height > a) {
        val dw = box.height * a; Rect(Offset((box.width - dw) / 2, 0f), Size(dw, box.height))
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
    val density = LocalDensity.current
    val count = d.count
    var page by remember { mutableIntStateOf((jumpPage ?: book.progressIndex).coerceIn(0, count - 1)) }
    var chrome by remember { mutableStateOf(false) }
    var sheet by remember { mutableIntStateOf(0) } // 0 none, 1 settings, 2 clips
    var region by remember { mutableStateOf<Rect?>(null) }   // in box coordinates
    var selecting by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Highlight?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val bitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    val allHl by Store.highlights.collectAsState()
    val clips = remember(allHl, book.id) { allHl.filter { it.bookId == book.id && it.isClip } }
    val activeLive = active?.let { a -> clips.firstOrNull { it.id == a.id } }
    val clipsNow by rememberUpdatedState(clips)

    // system bars + keep awake + save
    val view = LocalView.current
    val window = remember(view) { view.context.activity()?.window }
    val controller = remember(window) { window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(controller, theme.light) {
        controller?.isAppearanceLightStatusBars = theme.light
        controller?.isAppearanceLightNavigationBars = theme.light
        onDispose { controller?.isAppearanceLightStatusBars = false; controller?.isAppearanceLightNavigationBars = false }
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
        onDispose {
            ReaderKeys.active = false
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            Store.updateProgress(book.id, pageNow, 0, count)
        }
    }
    LaunchedEffect(page) { delay(300); Store.updateProgress(book.id, page, 0, count) }
    LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }

    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()

    val filter: ColorFilter? = when {
        !theme.light -> ColorFilter.colorMatrix(invert)
        theme.bg != Color.White -> ColorFilter.tint(theme.bg, BlendMode.Multiply)
        else -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        val renderW = (constraints.maxWidth * 1.25f).roundToInt()

        LaunchedEffect(page, renderW) {
            for (p in listOf(page, page + 1, page - 1)) {
                if (p in 0 until count && bitmaps[p] == null) {
                    bitmaps[p] = withContext(paginationThread) { d.render(p, renderW) }
                }
            }
            bitmaps.keys.filter { abs(it - page) > 2 }.forEach { bitmaps.remove(it) }
        }

        val offset = remember { Animatable(0f) }
        var turnJob by remember { mutableStateOf<Job?>(null) }
        var pending by remember { mutableStateOf<Int?>(null) }

        fun clearTransient(): Boolean {
            if (region != null || active != null || sheet != 0) { region = null; active = null; sheet = 0; return true }
            return false
        }
        BackHandler(enabled = region != null || active != null || sheet != 0) { clearTransient() }

        fun turn(dir: Int, fromDrag: Boolean = false) {
            pending?.let { page = it; pending = null }
            turnJob?.cancel()
            chrome = false
            val target = page + dir
            if (target !in 0 until count) {
                turnJob = scope.launch {
                    if (!fromDrag) offset.animateTo(-dir * 36f, spring(0.6f, 900f))
                    offset.animateTo(0f, spring(0.55f, 500f))
                }
                return
            }
            pending = target
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
            page = p.coerceIn(0, count - 1)
        }

        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        val pageTop = topInset + 34.dp
        val pageBottom = bottomInset + 34.dp

        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .pointerInput(fullW) {
                    detectTapGestures(
                        onTap = { pos ->
                            // tapping an existing clip opens its actions
                            val bmp = bitmaps[page]
                            val topPx = with(density) { pageTop.toPx() }
                            val hit = if (bmp != null && boxSize != IntSize.Zero) {
                                val r = fitRect(Size(boxSize.width.toFloat(), boxSize.height.toFloat()), bmp.width, bmp.height)
                                val nx = (pos.x - with(density) { 8.dp.toPx() } - r.left) / r.width; val ny = (pos.y - topPx - r.top) / r.height
                                clipsNow.firstOrNull { it.para == page && it.rect!!.let { q -> nx in q[0]..q[2] && ny in q[1]..q[3] } }
                            } else null
                            when {
                                clearTransient() -> {}
                                hit != null -> active = hit
                                chrome -> chrome = false
                                pos.x < size.width * 0.3f -> turnRef(-1, false)
                                pos.x > size.width * 0.7f -> turnRef(1, false)
                                else -> chrome = true
                            }
                        },
                        onLongPress = { },
                    )
                }
                .pointerInput(fullW) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            pending?.let { page = it; pending = null }
                            turnJob?.cancel(); region = null; active = null; chrome = false
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            val o = offset.value
                            val dir = when { o < -fullW * 0.22f || v < -900f -> 1; o > fullW * 0.22f || v > 900f -> -1; else -> 0 }
                            if (dir != 0 && page + dir in 0 until count) turnRef(dir, true)
                            else turnJob = scope.launch { offset.animateTo(0f, spring(0.8f, 500f)) }
                        },
                        onDragCancel = { turnJob = scope.launch { offset.animateTo(0f, spring(0.8f, 500f)) } },
                    ) { change, dx ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val o = offset.value
                        val blocked = (o + dx < 0 && page + 1 >= count) || (o + dx > 0 && page - 1 < 0)
                        scope.launch { offset.snapTo(o + if (blocked) dx * 0.25f else dx) }
                    }
                }
                .pointerInput(fullW) {
                    // hold, then drag to frame a region of the page
                    var start = Offset.Zero
                    val topPx = pageTop.toPx()
                    val padPx = 8.dp.toPx()
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            start = Offset(pos.x - padPx, pos.y - topPx)
                            region = Rect(start, start)
                            selecting = true; active = null; chrome = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = Offset(change.position.x - padPx, change.position.y - topPx)
                            region = Rect(min(start.x, p.x), min(start.y, p.y), max(start.x, p.x), max(start.y, p.y))
                        },
                        onDragEnd = {
                            selecting = false
                            val r = region
                            if (r == null || r.width < 24f || r.height < 24f) region = null
                        },
                        onDragCancel = { selecting = false; region = null },
                    )
                },
        ) {
            for (dlt in listOf(-1, 1, 0)) {
                val p = page + dlt
                if (p !in 0 until count) continue
                val bmp = bitmaps[p] ?: continue
                key(p) {
                val img = remember(bmp) { bmp.asImageBitmap() }
                val pageClips = clips.filter { it.para == p }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = pageTop, bottom = pageBottom, start = 8.dp, end = 8.dp)
                        .graphicsLayer { translationX = offset.value + dlt * fullW }
                        .let { if (dlt == 0) it.onSize { boxSize = it } else it }
                        .drawBehind {
                            val r = fitRect(size, bmp.width, bmp.height)
                            drawImage(
                                img,
                                dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                                dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
                                colorFilter = filter,
                            )
                            for (h in pageClips) {
                                val q = h.rect!!
                                val tl = Offset(r.left + q[0] * r.width, r.top + q[1] * r.height)
                                val sz = Size((q[2] - q[0]) * r.width, (q[3] - q[1]) * r.height)
                                val c = HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)]
                                drawRoundRect(c.copy(alpha = 0.18f), tl, sz, CornerRadius(14f))
                                drawRoundRect(c.copy(alpha = 0.9f), tl, sz, CornerRadius(14f), style = Stroke(3f))
                            }
                            if (dlt == 0) region?.let { rg ->
                                drawRoundRect(theme.accent.copy(alpha = 0.12f), rg.topLeft, rg.size, CornerRadius(16f))
                                drawRoundRect(theme.accent.copy(alpha = 0.85f), rg.topLeft, rg.size, CornerRadius(16f), style = Stroke(3f))
                            }
                        },
                )
                }
            }
        }

        // quiet furniture
        val furniture by animateFloatAsState(if (chrome) 0f else 1f, tween(200), label = "f")
        Text(
            "${page + 1} of $count", color = theme.sub, fontFamily = Inter, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp).graphicsLayer { alpha = furniture },
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
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        book.title, color = theme.text, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                            .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                GlassCircle(Line.Quote, "Clips", haze, tint = theme.text, glassTint = theme.glassTint, base = theme.bg, light = theme.light) { sheet = 2 }
            }
        }

        // bottom controls
        AnimatedVisibility(
            visible = chrome && region == null && active == null,
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
                    val rg = region; val bmp = bitmaps[page]
                    if (rg != null && bmp != null && boxSize != IntSize.Zero) {
                        val r = fitRect(Size(boxSize.width.toFloat(), boxSize.height.toFloat()), bmp.width, bmp.height)
                        val l = ((rg.left - r.left) / r.width).coerceIn(0f, 1f)
                        val t = ((rg.top - r.top) / r.height).coerceIn(0f, 1f)
                        val rr = ((rg.right - r.left) / r.width).coerceIn(0f, 1f)
                        val b = ((rg.bottom - r.top) / r.height).coerceIn(0f, 1f)
                        if (rr - l > 0.02f && b - t > 0.02f) {
                            val crop = Bitmap.createBitmap(
                                bmp, (l * bmp.width).toInt(), (t * bmp.height).toInt(),
                                ((rr - l) * bmp.width).toInt().coerceAtLeast(1), ((b - t) * bmp.height).toInt().coerceAtLeast(1),
                            )
                            Store.addClip(
                                Highlight(UUID.randomUUID().toString(), book.id, page, 0, 0, "", i, "Page ${page + 1}", rect = floatArrayOf(l, t, rr, b)),
                                crop,
                            )
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            toast = "Saved to Highlights"
                        }
                    }
                    region = null
                }
            }
            Divider(theme)
            BarIcon(Line.Close, theme) { region = null }
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
            BarIcon(Line.Trash, theme) { h?.let { Store.removeHighlight(it.id) }; active = null; toast = "Removed" }
        }

        GlassSheet(sheet != 0, haze, theme, bottomInset, onDismiss = { sheet = 0 }) {
            var last by remember { mutableIntStateOf(1) }
            if (sheet != 0) last = sheet
            if (last == 1) SettingsSheet(settings, theme, pdf = true)
            else {
                SheetTitle("Clips", theme)
                if (clips.isEmpty()) {
                    Text("Hold on a page, then drag to frame the part you love.", color = theme.sub, fontFamily = Inter,
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
                            bmp?.let {
                                Image(it, null, contentScale = ContentScale.Fit, colorFilter = filter,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                            }
                            Text("Page ${h.para + 1}", color = theme.sub, fontFamily = Inter, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

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

private fun Modifier.onSize(block: (IntSize) -> Unit): Modifier = this.onSizeChanged(block)
