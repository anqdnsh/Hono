package com.aniq.glassreader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class Sel(val para: Int, val start: Int, val end: Int)
private enum class Sheet { None, Chapters, Settings, Marks }

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun ReaderScreen(bookId: String, jumpPara: Int?, onBack: () -> Unit) {
    val book = remember(bookId) { Store.book(bookId) }
    if (book == null) { LaunchedEffect(Unit) { onBack() }; return }
    val settings by Store.settings.collectAsState()
    val theme = ReaderThemes[settings.theme.coerceIn(0, ReaderThemes.lastIndex)]
    val bg by animateColorAsState(theme.bg, tween(350), label = "bg")
    val content by produceState<BookContent?>(null, bookId) {
        value = runCatching { Store.content(book) }.getOrNull()
        if (value == null) onBack()
    }
    LaunchedEffect(bookId) { Store.markOpened(bookId) }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(bg)) {
        val c = content
        if (c == null) {
            Text("Opening…", color = theme.sub, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
        } else {
            ReaderBody(book, c, jumpPara, settings, theme, onBack)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ReaderBody(
    book: Book,
    c: BookContent,
    jumpPara: Int?,
    settings: ReaderSettings,
    theme: ReaderTheme,
    onBack: () -> Unit,
) {
    val haze = remember { HazeState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val n = c.paras.size
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (jumpPara ?: book.progressIndex).coerceIn(0, n - 1),
        initialFirstVisibleItemScrollOffset = if (jumpPara != null) 0 else book.progressOffset,
    )

    var chrome by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var selection by remember { mutableStateOf<Sel?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Highlight?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val allHl by Store.highlights.collectAsState()
    val bookHl = remember(allHl, book.id) { allHl.filter { it.bookId == book.id } }
    val byPara = remember(bookHl) { bookHl.groupBy { it.para } }
    // keep the tapped highlight in sync with the store (e.g. after recolouring)
    val activeLive = active?.let { a -> bookHl.firstOrNull { it.id == a.id } }

    // ---- system bars: immersive while reading, light/dark icons per theme ----
    val view = LocalView.current
    val window = remember(view) { view.context.activity()?.window }
    val controller = remember(window) { window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(controller, theme.light) {
        controller?.isAppearanceLightStatusBars = theme.light
        controller?.isAppearanceLightNavigationBars = theme.light
        onDispose {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
        }
    }
    LaunchedEffect(controller, chrome) {
        controller ?: return@LaunchedEffect
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            Store.updateProgress(book.id, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, n)
        }
    }

    // ---- progress persistence & auto-hide chrome while scrolling ----
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (i, o) -> delay(600); Store.updateProgress(book.id, i, o, n) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !scrubbing && sheet == Sheet.None) chrome = false
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }

    val progress by remember { derivedStateOf { if (n <= 1) 0f else listState.firstVisibleItemIndex.toFloat() / (n - 1) } }
    val chapterTitle by remember { derivedStateOf { c.chapterAt(listState.firstVisibleItemIndex)?.title ?: "" } }

    fun clearTransient(): Boolean {
        if (selection != null || active != null || sheet != Sheet.None) {
            selection = null; active = null; sheet = Sheet.None; return true
        }
        return false
    }
    BackHandler(enabled = selection != null || active != null || sheet != Sheet.None) { clearTransient() }

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
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selection = null
        toast = "Saved to Highlights"
    }

    fun jump(para: Int) {
        sheet = Sheet.None
        scope.launch { listState.scrollToItem(para.coerceIn(0, n - 1)) }
    }

    // ---- text styles ----
    val family = if (settings.serif) FontFamily.Serif else FontFamily.Default
    val body = remember(settings, theme) {
        TextStyle(
            color = theme.text, fontSize = settings.fontSize.sp, lineHeight = (settings.fontSize * settings.lineHeight).sp,
            fontFamily = family, textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph,
        )
    }
    val heading = remember(body) {
        body.copy(fontSize = (settings.fontSize * 1.4f).sp, lineHeight = (settings.fontSize * 1.75f).sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start)
    }

    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val currentSelection by rememberUpdatedState(selection)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = topInset + 56.dp, bottom = bottomInset + 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = !dragging,
        ) {
            items(count = n, key = { it }, contentType = { if (c.paras[it].heading) 1 else 0 }) { i ->
                val p = c.paras[i]
                Paragraph(
                    index = i,
                    para = p,
                    style = if (p.heading) heading else body,
                    highlights = byPara[i].orEmpty(),
                    selection = currentSelection?.takeIf { it.para == i },
                    selColor = theme.accent,
                    hlAlpha = theme.hlAlpha,
                    modifier = Modifier
                        .widthIn(max = 700.dp)
                        .fillMaxWidth()
                        .padding(
                            horizontal = settings.margin.dp,
                            vertical = if (p.heading) 22.dp else (settings.fontSize * 0.42f).dp,
                        ),
                    onTap = { if (!clearTransient()) chrome = !chrome },
                    onTapHighlight = { active = it; selection = null },
                    onSelect = { s, isDragging -> selection = s; dragging = isDragging; active = null },
                )
            }
        }

        // ---- top bar ----
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(180)) + slideInVertically(spring(0.85f, 500f)) { -it },
            exit = fadeOut(tween(160)) + slideOutVertically(spring(0.9f, 600f)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(haze, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), theme.glassTint, theme.bg, light = theme.light)
                    .padding(top = topInset)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.text, onClick = onBack)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(book.title, color = theme.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapterTitle, color = theme.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassIconButton(HighlighterIcon, "Highlights in this book", tint = theme.text) { sheet = Sheet.Marks }
            }
        }

        // ---- bottom bar ----
        AnimatedVisibility(
            visible = chrome && selection == null && active == null,
            enter = fadeIn(tween(180)) + slideInVertically(spring(0.85f, 500f)) { it },
            exit = fadeOut(tween(160)) + slideOutVertically(spring(0.9f, 600f)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + 12.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .glass(haze, RoundedCornerShape(30.dp), theme.glassTint, theme.bg, light = theme.light)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                var scrub by remember { mutableFloatStateOf(-1f) }
                Text(
                    "${((if (scrub >= 0) scrub else progress) * 100).roundToInt()}%  ·  ${chapterTitle}",
                    color = theme.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Slider(
                    value = if (scrub >= 0) scrub else progress,
                    onValueChange = { v ->
                        scrubbing = true; scrub = v
                        scope.launch { listState.scrollToItem((v * (n - 1)).roundToInt()) }
                    },
                    onValueChangeFinished = { scrub = -1f; scope.launch { delay(150); scrubbing = false } },
                    colors = SliderDefaults.colors(
                        thumbColor = theme.text, activeTrackColor = theme.accent,
                        inactiveTrackColor = theme.text.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.height(36.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BarButton("Chapters", theme) { sheet = Sheet.Chapters }
                    BarButton("Aa  Style", theme) { sheet = Sheet.Settings }
                    BarButton("Highlights", theme) { sheet = Sheet.Marks }
                }
            }
        }

        // ---- selection toolbar ----
        var lastSel by remember { mutableStateOf<Sel?>(null) }
        selection?.let { lastSel = it }
        AnimatedVisibility(
            visible = selection != null && !dragging,
            enter = fadeIn(tween(120)) + scaleIn(spring(0.6f, 600f), initialScale = 0.8f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 28.dp),
        ) {
            Row(
                Modifier
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightColors.forEachIndexed { i, col -> ColorDot(col, false) { commit(i) } }
                Divider(theme)
                ToolText("¶", theme) {
                    lastSel?.let { s -> selection = Sel(s.para, 0, c.paras[s.para].text.length) }
                }
                ToolText("Copy", theme) {
                    lastSel?.let { s -> clipboard.setText(AnnotatedString(c.paras[s.para].text.substring(s.start, s.end))) }
                    selection = null; toast = "Copied"
                }
                SmallIcon(Icons.Filled.Close, theme) { selection = null }
            }
        }

        // ---- existing highlight toolbar ----
        var lastActive by remember { mutableStateOf<Highlight?>(null) }
        activeLive?.let { lastActive = it }
        AnimatedVisibility(
            visible = activeLive != null,
            enter = fadeIn(tween(120)) + scaleIn(spring(0.6f, 600f), initialScale = 0.8f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 28.dp),
        ) {
            val h = lastActive
            Row(
                Modifier
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightColors.forEachIndexed { i, col ->
                    ColorDot(col, h?.color == i) { h?.let { Store.updateHighlight(it.id) { x -> x.copy(color = i) } } }
                }
                Divider(theme)
                SmallIcon(if (h?.favorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, theme,
                    tint = if (h?.favorite == true) Color(0xFFFF7AA2) else theme.text) {
                    h?.let { Store.updateHighlight(it.id) { x -> x.copy(favorite = !x.favorite) } }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ToolText("Copy", theme) {
                    h?.let { clipboard.setText(AnnotatedString(it.text)) }; active = null; toast = "Copied"
                }
                SmallIcon(Icons.Filled.Delete, theme) {
                    h?.let { Store.removeHighlight(it.id) }; active = null; toast = "Highlight removed"
                }
            }
        }

        // ---- sheets ----
        GlassSheet(sheet != Sheet.None, haze, theme, onDismiss = { sheet = Sheet.None }) {
            var lastSheet by remember { mutableStateOf(Sheet.Chapters) }
            if (sheet != Sheet.None) lastSheet = sheet
            when (lastSheet) {
                Sheet.Chapters -> ChaptersSheet(c, listState, theme, ::jump)
                Sheet.Settings -> SettingsSheet(settings, theme)
                Sheet.Marks -> MarksSheet(bookHl, theme, ::jump)
                Sheet.None -> {}
            }
        }

        // ---- toast ----
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically(spring(0.7f, 500f)) { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 14.dp),
        ) {
            var last by remember { mutableStateOf("") }
            toast?.let { last = it }
            Text(
                last, color = theme.text, fontSize = 14.sp,
                modifier = Modifier
                    .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

// ---------------- Paragraph with custom highlight selection ----------------

private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'

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
private fun Paragraph(
    index: Int,
    para: Para,
    style: TextStyle,
    highlights: List<Highlight>,
    selection: Sel?,
    selColor: Color,
    hlAlpha: Float,
    modifier: Modifier,
    onTap: () -> Unit,
    onTapHighlight: (Highlight) -> Unit,
    onSelect: (Sel, Boolean) -> Unit,
) {
    val text = para.text
    val annotated = remember(para) {
        buildAnnotatedString {
            append(text)
            var k = 0
            while (k + 2 < para.styles.size) {
                val st = if (para.styles[k + 2] == 1) SpanStyle(fontStyle = FontStyle.Italic) else SpanStyle(fontWeight = FontWeight.Bold)
                addStyle(st, para.styles[k], para.styles[k + 1])
                k += 3
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hl by rememberUpdatedState(highlights)
    val tap by rememberUpdatedState(onTap)
    val tapHl by rememberUpdatedState(onTapHighlight)
    val select by rememberUpdatedState(onSelect)
    val haptic = LocalHapticFeedback.current
    val selAlpha by animateFloatAsState(if (selection != null) 1f else 0f, tween(140), label = "sel")

    Text(
        annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .drawBehind {
                val l = layout ?: return@drawBehind
                val len = text.length
                for (h in highlights) {
                    val s = h.start.coerceIn(0, len); val e = h.end.coerceIn(0, len)
                    if (e > s) drawPath(l.getPathForRange(s, e), HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)].copy(alpha = hlAlpha))
                }
                if (selection != null && selection.end > selection.start) {
                    drawPath(l.getPathForRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len)),
                        selColor.copy(alpha = 0.30f * selAlpha))
                }
            }
            .pointerInput(index) {
                detectTapGestures(onTap = { pos ->
                    val off = layout?.getOffsetForPosition(pos) ?: -1
                    val h = hl.firstOrNull { off >= it.start && off < it.end }
                    if (h != null) tapHl(h) else tap()
                })
            }
            .pointerInput(index) {
                var anchor = 0 until 0
                var moved = false
                var lastEnd = -1
                var lastStart = -1
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        layout?.let { l ->
                            anchor = wordRange(text, l.getOffsetForPosition(pos))
                            moved = false
                            lastStart = anchor.first; lastEnd = anchor.last + 1
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            select(Sel(index, anchor.first, anchor.last + 1), true)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        layout?.let { l ->
                            val w = wordRange(text, l.getOffsetForPosition(change.position))
                            val s = min(anchor.first, w.first)
                            val e = max(anchor.last + 1, w.last + 1)
                            if (s != lastStart || e != lastEnd) {
                                moved = true; lastStart = s; lastEnd = e
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                select(Sel(index, s, e), true)
                            }
                        }
                    },
                    onDragEnd = {
                        if (!moved) {
                            val r = sentenceRange(text, anchor.first)
                            select(Sel(index, r.first, r.last + 1), false)
                        } else select(Sel(index, lastStart, lastEnd), false)
                    },
                    onDragCancel = {
                        if (lastEnd > lastStart) select(Sel(index, lastStart, lastEnd), false)
                    },
                )
            },
    )
}

// ---------------- Small controls ----------------

@Composable
private fun BarButton(label: String, theme: ReaderTheme, onClick: () -> Unit) {
    Text(
        label, color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .bouncyClick(pressedScale = 0.9f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val s by animateFloatAsState(if (selected) 1.15f else 1f, spring(0.5f, 600f), label = "dot")
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(30.dp)
            .bouncyClick(pressedScale = 0.8f, onClick = onClick)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.5.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.95f else 0.5f), CircleShape),
    )
}

@Composable
private fun Divider(theme: ReaderTheme) {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(theme.text.copy(alpha = 0.2f)))
}

@Composable
private fun ToolText(label: String, theme: ReaderTheme, onClick: () -> Unit) {
    Text(
        label, color = theme.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.bouncyClick(pressedScale = 0.85f, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: ReaderTheme,
    tint: Color = theme.text,
    onClick: () -> Unit,
) {
    Box(Modifier.size(38.dp).bouncyClick(pressedScale = 0.8f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

// ---------------- Sheets ----------------

@Composable
private fun GlassSheet(
    visible: Boolean,
    haze: HazeState,
    theme: ReaderTheme,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(spring(0.82f, 420f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(spring(0.95f, 600f)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
            Column(
                Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = bottom + 10.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .glass(haze, RoundedCornerShape(34.dp), theme.glassTint, theme.bg, blur = 36.dp, light = theme.light)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(50)).background(theme.text.copy(alpha = 0.25f)))
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String, theme: ReaderTheme) {
    Text(text, color = theme.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp))
}

@Composable
private fun ChaptersSheet(c: BookContent, listState: LazyListState, theme: ReaderTheme, jump: (Int) -> Unit) {
    val current = c.chapterAt(listState.firstVisibleItemIndex)
    SheetTitle("Chapters", theme)
    val sheetList = rememberLazyListState(initialFirstVisibleItemIndex = max(0, c.chapters.indexOf(current) - 2))
    LazyColumn(state = sheetList, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        itemsIndexed(c.chapters) { _, ch ->
            val isCur = ch === current
            Text(
                ch.title,
                color = if (isCur) theme.accent else theme.text,
                fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick(pressedScale = 0.97f) { jump(ch.para) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isCur) theme.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun MarksSheet(list: List<Highlight>, theme: ReaderTheme, jump: (Int) -> Unit) {
    val sorted = remember(list) { list.sortedWith(compareBy({ it.para }, { it.start })) }
    SheetTitle("Highlights in this book", theme)
    if (sorted.isEmpty()) {
        Text("Long-press a sentence to highlight it.", color = theme.sub, fontSize = 14.sp,
            modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sorted, key = { it.id }) { h ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .bouncyClick(pressedScale = 0.97f) { jump(h.para) }
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.text.copy(alpha = 0.06f))
                    .padding(14.dp),
            ) {
                Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(50))
                    .background(HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)]))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(h.text, color = theme.text, fontFamily = FontFamily.Serif, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (h.chapter.isNotBlank()) Text(h.chapter, color = theme.sub, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(s: ReaderSettings, theme: ReaderTheme) {
    SheetTitle("Reading style", theme)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ReaderThemes.forEachIndexed { i, t ->
                val sel = i == s.theme
                val scale by animateFloatAsState(if (sel) 1.08f else 1f, spring(0.5f, 500f), label = "t")
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .bouncyClick(pressedScale = 0.85f) { Store.updateSettings { it.copy(theme = i) } }
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .background(t.bg)
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) theme.accent else theme.text.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = t.text, fontFamily = FontFamily.Serif, fontSize = 17.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text(t.name, color = theme.sub, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LabeledSlider("Text size", "${s.fontSize.roundToInt()}", s.fontSize, 14f..32f, theme) { v -> Store.updateSettings { it.copy(fontSize = v) } }
        LabeledSlider("Line spacing", String.format("%.1f", s.lineHeight), s.lineHeight, 1.2f..2.2f, theme) { v -> Store.updateSettings { it.copy(lineHeight = v) } }
        LabeledSlider("Margins", "${s.margin}", s.margin.toFloat(), 8f..56f, theme) { v -> Store.updateSettings { it.copy(margin = v.roundToInt()) } }
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("Serif", "Sans"), if (s.serif) 0 else 1, { i -> Store.updateSettings { it.copy(serif = i == 0) } }, fg = theme.text)
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Justified", "Left aligned"), if (s.justify) 0 else 1, { i -> Store.updateSettings { it.copy(justify = i == 0) } }, fg = theme.text)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    v: Float,
    range: ClosedFloatingPointRange<Float>,
    theme: ReaderTheme,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = theme.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = theme.sub, fontSize = 13.sp)
    }
    Slider(
        value = v, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = theme.text, activeTrackColor = theme.accent, inactiveTrackColor = theme.text.copy(alpha = 0.18f)),
        modifier = Modifier.height(36.dp),
    )
}
