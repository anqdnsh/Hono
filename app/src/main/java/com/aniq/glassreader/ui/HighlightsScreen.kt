package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

@Composable
private fun rememberCoverOf(bookId: String, px: Int = 400): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, bookId) {
        value = withContext(Dispatchers.IO) { Store.cover(bookId, px)?.asImageBitmap() }
    }
    return bmp
}

@Composable
private fun rememberClip(h: Highlight): ImageBitmap? {
    if (!h.isClip) return null
    val bmp by produceState<ImageBitmap?>(null, h.id) {
        value = withContext(Dispatchers.IO) { Store.clip(h.id)?.asImageBitmap() }
    }
    return bmp
}

/** Blurred cover art + a glow in the highlight's colour, used behind every piece. */
@Composable
private fun ArtBackdrop(h: Highlight, modifier: Modifier, blur: Int = 50) {
    val cover = rememberCoverOf(h.bookId)
    val tint = HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)]
    Box(modifier.background(Color(0xFF121214))) {
        if (cover != null) {
            Image(cover, null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.blur(blur.dp))
        }
        Box(Modifier.matchParentSize().background(Brush.radialGradient(
            listOf(tint.copy(alpha = 0.22f), Color.Transparent), center = Offset(0f, 0f), radius = 900f)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.62f)))))
    }
}

@Composable
fun HighlightsScreen(haze: HazeState, onOpen: (String, Int?, Int) -> Unit, onExportAll: () -> Unit, onSettings: () -> Unit) {
    val all by Store.highlights.collectAsState()
    val books by Store.books.collectAsState()
    var filter by remember { mutableIntStateOf(0) }
    var exhibit by remember { mutableStateOf<Int?>(null) }
    val titles = remember(books) { books.associate { it.id to it.title } }
    val shown = remember(all, filter) { all.filter { filter == 0 || it.favorite }.sortedByDescending { it.createdAt } }
    val favCount = remember(all) { all.count { it.favorite } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(165.dp),
            modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "title") {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    ScreenTitle("Highlights") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (all.isNotEmpty()) GlassCircle(Line.Share, "Export all highlights", haze, size = 40.dp, onClick = onExportAll)
                            GlassCircle(Line.Gear, "Settings", haze, size = 40.dp, onClick = onSettings)
                        }
                    }
                    Text(
                        if (all.isEmpty()) "Nothing saved yet" else "${all.size} saved" + if (favCount > 0) " · $favCount loved" else "",
                        color = Secondary, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Segmented(listOf("All", "Loved"), filter, { filter = it }, Modifier.widthIn(max = 260.dp))
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (shown.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (filter == 0) "No highlights yet" else "Nothing loved yet",
                            color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (filter == 0) "Hold on a sentence while you read.\nIn a PDF, hold and drag over the part you want."
                            else "Tap the heart on a highlight to keep it here.",
                            color = Secondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { h ->
                Frame(h, titles[h.bookId] ?: "", Modifier.animateItem()) { exhibit = shown.indexOf(h) }
            }
        }

        // full-screen exhibit
        AnimatedVisibility(
            visible = exhibit != null && shown.isNotEmpty(),
            enter = fadeIn(tween(220)) + scaleIn(spring(0.85f, 420f), initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f),
        ) {
            var start by remember { mutableIntStateOf(0) }
            exhibit?.let { start = it }
            Exhibit(shown, start.coerceIn(0, (shown.size - 1).coerceAtLeast(0)), titles,
                onClose = { exhibit = null },
                onOpen = { h -> exhibit = null; onOpen(h.bookId, h.para, h.start) })
        }
    }
}

/** One framed piece in the gallery wall. */
@Composable
private fun Frame(h: Highlight, bookTitle: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val clip = rememberClip(h)
    val tint = HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)]
    Box(modifier.fillMaxWidth().bouncyClick(pressedScale = 0.96f, onClick = onClick).clip(shape)) {
        ArtBackdrop(h, Modifier.matchParentSize())
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(width = 18.dp, height = 3.dp).clip(RoundedCornerShape(50)).background(tint))
            Spacer(Modifier.height(12.dp))
            if (h.isClip) {
                if (clip != null) Image(clip, null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)))
                else Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)))
            } else {
                Text(
                    h.text.trim(), color = Color.White, fontSize = 16.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp,
                    maxLines = 10, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bookTitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (h.favorite) Icon(Line.HeartFill, null, tint = Color(0xFFFF375F), modifier = Modifier.size(14.dp))
            }
        }
        Box(Modifier.matchParentSize().glassRim(shape))
    }
}

private fun Modifier.glassRim(shape: androidx.compose.ui.graphics.Shape) = this.then(
    Modifier.background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 0.4f to Color.Transparent))
        .hairline(shape)
)

/** Swipeable, full-bleed view of each piece. */
@Composable
private fun Exhibit(
    list: List<Highlight>,
    start: Int,
    titles: Map<String, String>,
    onClose: () -> Unit,
    onOpen: (Highlight) -> Unit,
) {
    BackHandler(onBack = onClose)
    val haze = remember { HazeState() }
    val pager = rememberPagerState(initialPage = start) { list.size }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(list.size) { if (list.isEmpty()) onClose() }
    val haptics = rememberHaptics()
    LaunchedEffect(pager) {
        var first = true
        androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { if (!first) haptics.play(Haptic.PageTurn); first = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize().hazeSource(haze), beyondViewportPageCount = 1) { i ->
            val h = list.getOrNull(i) ?: return@HorizontalPager
            val frac = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize()) {
                ArtBackdrop(h, Modifier.fillMaxSize(), blur = 80)
                Column(
                    Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = top + 90.dp, bottom = bottom + 120.dp)
                        .graphicsLayer { alpha = 1f - frac * 0.6f; val s = 1f - frac * 0.06f; scaleX = s; scaleY = s },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(width = 26.dp, height = 4.dp).clip(RoundedCornerShape(50))
                        .background(HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)]))
                    Spacer(Modifier.height(20.dp))
                    if (h.isClip) {
                        val clip = rememberClip(h)
                        if (clip != null) Image(clip, null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(16.dp)))
                    } else {
                        val len = h.text.length
                        Text(
                            h.text.trim(), color = Color.White,
                            fontSize = when { len < 120 -> 30.sp; len < 260 -> 25.sp; len < 500 -> 21.sp; else -> 18.sp },
                            lineHeight = when { len < 120 -> 38.sp; len < 260 -> 33.sp; len < 500 -> 29.sp; else -> 26.sp },
                            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
                            modifier = Modifier.widthIn(max = 640.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(titles[h.bookId] ?: "", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOf(h.chapter, df.format(Date(h.createdAt))).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // close
        GlassCircle(Line.Close, "Close", haze, size = 42.dp, modifier = Modifier.align(Alignment.TopEnd).padding(top = top + 10.dp, end = 16.dp), onClick = onClose)

        // counter
        Text(
            "${pager.currentPage + 1} / ${list.size}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopStart).padding(top = top + 22.dp, start = 24.dp),
        )

        // actions
        val cur = list.getOrNull(pager.currentPage)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 18.dp)
                .glass(haze, RoundedCornerShape(50), floating = true).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExhibitAction(Line.Open, "Read") { cur?.let(onOpen) }
            ExhibitAction(if (cur?.favorite == true) Line.HeartFill else Line.Heart, "Love",
                tint = if (cur?.favorite == true) Color(0xFFFF375F) else Primary) {
                cur?.let { Store.updateHighlight(it.id) { x -> x.copy(favorite = !x.favorite) } }
                haptics.play(if (cur?.favorite == true) Haptic.ToggleOff else Haptic.Confirm)
            }
            if (cur?.isClip == false) ExhibitAction(Line.Copy, "Copy") { clipboard.setText(AnnotatedString(cur.text)); haptics.play(Haptic.Confirm) }
            ExhibitAction(Line.Trash, "Delete") { cur?.let { Store.removeHighlight(it.id) }; haptics.play(Haptic.Reject) }
        }
    }
}

@Composable
private fun ExhibitAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Primary, onClick: () -> Unit) {
    Column(
        Modifier.width(66.dp).bouncyClick(pressedScale = 0.85f, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = Secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fg: Color = Primary,
) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(36.dp).clip(shape).background(fg.copy(alpha = 0.08f)).padding(3.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(0.72f, Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(shape).background(fg.copy(alpha = 0.16f)).hairline(shape))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, s ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().bouncyClick(pressedScale = 0.94f, haptic = Haptic.Tick) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s, color = if (i == selected) fg else fg.copy(alpha = 0.6f), fontFamily = Inter, fontSize = 13.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

@Suppress("unused") private val unusedCircle = CircleShape
