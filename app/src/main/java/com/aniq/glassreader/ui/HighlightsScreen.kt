package com.aniq.glassreader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.draw.clipToBounds
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

/** How cards glide when others open or close around them: soft, no bounce. */
private val GlidePlacement = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)

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

/** Blurred cover art behind a piece in the full-screen view. No highlight colour. */
@Composable
private fun ArtBackdrop(h: Highlight, modifier: Modifier, blur: Int = 50) {
    val cover = rememberCoverOf(h.bookId)
    // clipToBounds: the cover is scaled up 1.5x for the blur, and without a clip it spilled
    // over the neighbouring page in the pager and covered the right side of the quote.
    Box(modifier.clipToBounds().background(Color(0xFF121214))) {
        if (cover != null) {
            Image(cover, null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.blur(blur.dp))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.62f)))))
    }
}

/** "The Mirror · Page 56" (PDF) or "Book · Chapter" (EPUB). */
private fun sourceLine(h: Highlight, title: String): String =
    listOf(title, h.chapter.takeIf { it.isNotBlank() && it != title }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
fun HighlightsScreen(haze: HazeState, onOpen: (String, Int?, Int) -> Unit, onExportAll: () -> Unit, onSettings: () -> Unit) {
    val all by Store.highlights.collectAsState()
    val books by Store.books.collectAsState()
    val reviews by Store.reviews.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }
    val results = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else all.filter { it.text.contains(q, ignoreCase = true) || it.note.contains(q, ignoreCase = true) }
            .sortedByDescending { it.createdAt }
    }
    val bookById = remember(books) { books.associateBy { it.id } }
    val context = androidx.compose.ui.platform.LocalContext.current
    var filter by remember { mutableIntStateOf(0) }
    // the full-screen view gets its own list, so the quote of the day can open even under "Loved"
    var exhibit by remember { mutableStateOf<Pair<List<Highlight>, Int>?>(null) }
    val titles = remember(books) { books.associate { it.id to it.title } }
    val shown = remember(all, filter, reviews) {
        when (filter) {
            1 -> all.filter { it.favorite }
            2 -> all.filter { it.bookId in reviews }
            else -> all
        }
    }
    // one group per book, newest-touched book first; inside a book, in reading order
    val groups = remember(shown, filter, reviews, books) {
        val g = shown.groupBy { it.bookId }.entries
            .sortedByDescending { e -> e.value.maxOf { it.createdAt } }
            .map { e -> e.key to e.value.sortedWith(compareBy({ it.para }, { it.start })) }
        // under "Reviewed", books you reviewed without saving highlights still show up
        if (filter != 2) g else g + reviews.values.filter { r -> g.none { it.first == r.bookId } && books.any { it.id == r.bookId } }
            .sortedByDescending { it.updatedAt }.map { it.bookId to emptyList() }
    }
    val favCount = remember(all) { all.count { it.favorite } }
    // books fold away; the one read most recently starts open
    var opened by remember { mutableStateOf<Set<String>?>(null) }
    val openSet = opened ?: setOfNotNull(
        Store.lastRead()?.id?.takeIf { id -> groups.any { it.first == id } } ?: groups.firstOrNull()?.first
    )
    // 0 = folded into its book, 1 = fully dropped out. Highlights stay on screen until it reaches 0,
    // so collapsing plays out before they leave the grid.
    val unfold = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    fun unfoldOf(id: String) = unfold.getOrPut(id) { Animatable(if (id in openSet) 1f else 0f) }
    // where each book header sits, so its highlights can slide out from underneath it
    val headerAt = remember { mutableMapOf<String, LayoutCoordinates>() }
    val scope = rememberCoroutineScope()
    // books whose highlights are still folding back in after being closed
    val folding = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    // same pick as the home-screen widget; re-read every minute so the 2-hour change shows up here too
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); tick++ } }
    val quote = remember(all, tick) { com.aniq.glassreader.widget.Widgets.quoteOfTheDay() }
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
                    SearchableTitle(
                        "Highlights", haze, searching, query, "Search highlights and notes",
                        onSearching = { searching = it }, onQuery = { query = it },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (all.isNotEmpty()) GlassCircle(Line.Share, "Export all highlights", haze, size = 40.dp, onClick = onExportAll)
                            GlassCircle(Line.Gear, "Settings", haze, size = 40.dp, onClick = onSettings)
                        }
                    }
                    val subtitle = when {
                        searching && query.isNotBlank() -> {
                            val notes = results.count { it.note.contains(query.trim(), ignoreCase = true) }
                            val hl = results.size
                            if (hl == 0) "No matches" else "$hl highlight${if (hl == 1) "" else "s"}" + if (notes > 0) " · $notes note${if (notes == 1) "" else "s"}" else ""
                        }
                        all.isEmpty() -> "Nothing saved yet"
                        else -> "${all.size} saved" + (if (favCount > 0) " · $favCount loved" else "") +
                            (if (reviews.isNotEmpty()) " · ${reviews.size} reviewed" else "")
                    }
                    Text(subtitle, color = Secondary, fontSize = 14.sp)
                }
            }
            if (searching && query.isNotBlank()) {
                itemsIndexed(results, key = { _, h -> "r-" + h.id }) { i, h ->
                    Frame(h, titles[h.bookId] ?: "", Modifier.animateItem(fadeInSpec = tween(180, delayMillis = (i * 25).coerceAtMost(200)), fadeOutSpec = tween(120), placementSpec = GlidePlacement),
                        query = query, showBook = true) { exhibit = results to i }
                }
            }
            if (!(searching && query.isNotBlank()) && quote != null) {
                item(span = StaggeredGridItemSpan.FullLine, key = "today") {
                    QuoteOfTheDay(quote, titles[quote.bookId] ?: "",
                        onClick = { exhibit = listOf(quote) to 0 },
                        onNext = { com.aniq.glassreader.widget.Widgets.nextQuote(context); tick++ })
                }
            }
            if (searching && query.isNotBlank()) {
                // results only
            } else {
            if (all.isNotEmpty() || reviews.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "filter") {
                    Segmented(listOf("All", "Loved", "Reviewed"), filter, { filter = it }, Modifier.padding(top = 6.dp).widthIn(max = 330.dp))
                }
            }
            if (groups.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(when (filter) { 0 -> "No highlights yet"; 1 -> "Nothing loved yet"; else -> "No reviews yet" },
                            color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (filter) {
                                0 -> "Hold on a sentence while you read.\nIn a PDF, hold and drag over the part you want."
                                1 -> "Tap the heart on a highlight to keep it here."
                                else -> "Tap the stars on a book to rate and review it."
                            },
                            color = Secondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
                        )
                    }
                }
            }
            groups.forEach { (bookId, list) ->
                val isOpen = bookId in openSet
                val review = reviews[bookId]
                val hasCard = review != null && (review.text.isNotBlank() || review.tags.isNotEmpty())
                item(span = StaggeredGridItemSpan.FullLine, key = "book-$bookId") {
                    BookHeader(bookById[bookId], bookId, titles[bookId] ?: "Removed book", list.size, isOpen, review?.rating ?: 0,
                        Modifier.zIndex(1f).animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = GlidePlacement)
                            .onPlaced { headerAt[bookId] = it }) {
                        val anim = unfoldOf(bookId)
                        if (isOpen) {
                            opened = openSet - bookId
                            folding += bookId
                            scope.launch {
                                try { anim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f)) }
                                finally { folding -= bookId }
                            }
                        } else {
                            opened = openSet + bookId
                            scope.launch { anim.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 260f)) }
                        }
                    }
                }
                val a = unfoldOf(bookId)
                val n = list.size + if (hasCard) 1 else 0
                if (hasCard && (isOpen || bookId in folding)) item(span = StaggeredGridItemSpan.FullLine, key = "review-$bookId") {
                    val mine = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    ReviewCard(review!!, bookId,
                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = GlidePlacement)
                            .unfold(a, 0, n, { headerAt[bookId] }, mine)) { ReviewPrompt.open(bookId) }
                }
                if (isOpen || bookId in folding) itemsIndexed(list, key = { _, h -> h.id }) { i, h ->
                    val mine = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    Frame(h, titles[h.bookId] ?: "",
                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = GlidePlacement)
                            .unfold(a, i + (if (hasCard) 1 else 0), n, { headerAt[bookId] }, mine)) { exhibit = list to list.indexOf(h) }
                }
            }
            }
        }

        // full-screen exhibit
        AnimatedVisibility(
            visible = exhibit != null,
            enter = fadeIn(tween(220)) + scaleIn(spring(0.85f, 420f), initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f),
        ) {
            var last by remember { mutableStateOf<Pair<List<Highlight>, Int>>(emptyList<Highlight>() to 0) }
            exhibit?.let { last = it }
            // keep the list live so loving or deleting shows straight away
            val ids = remember(last) { last.first.map { it.id }.toSet() }
            val live = remember(all, ids) { all.filter { it.id in ids }.sortedBy { h -> last.first.indexOfFirst { it.id == h.id } } }
            Exhibit(live, last.second.coerceIn(0, (live.size - 1).coerceAtLeast(0)), titles,
                onClose = { exhibit = null },
                onOpen = { h -> exhibit = null; onOpen(h.bookId, h.para, h.start) })
        }
    }
}

/** The home-screen quote widget, drawn in the app: today's quote, where it's from, and a shuffle button. */
@Composable
private fun QuoteOfTheDay(h: Highlight, bookTitle: String, onClick: () -> Unit, onNext: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).bouncyClick(pressedScale = 0.97f, onClick = onClick)
            .liquidGlass(shape)
            .padding(start = 22.dp, end = 14.dp, top = 20.dp, bottom = 14.dp)
    ) {
        Text("Quote of the day", color = Primary.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        val len = h.text.length
        Text(
            h.text.trim(), color = Primary,
            fontSize = if (len < 160) 21.sp else 18.sp, lineHeight = if (len < 160) 28.sp else 25.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
            maxLines = 9, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sourceLine(h, bookTitle), color = Primary.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Primary.copy(alpha = 0.10f))
                    .bouncyClick(pressedScale = 0.85f, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) { Icon(Line.Refresh, "Another quote", tint = Primary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp)) }
        }
    }
}

/**
 * Folds a card into its book header: 0 = tucked under the header, 1 = fully dropped out.
 * The first card leads out and the last one leads back in.
 */
private fun Modifier.unfold(
    a: Animatable<Float, AnimationVector1D>,
    i: Int,
    n: Int,
    header: () -> LayoutCoordinates?,
    mine: Array<LayoutCoordinates?>,
): Modifier = this
    .onPlaced { mine[0] = it }
    .graphicsLayer {
        val s = (0.5f / n.coerceAtLeast(1)).coerceAtMost(0.07f)
        val t = ((a.value - s * i) / (1f - s * (n - 1).coerceAtLeast(0))).coerceIn(0f, 1f)
        val head = header()?.takeIf { it.isAttached }
        val me = mine[0]?.takeIf { it.isAttached }
        // how far below the bottom of the book header this card sits
        val drop = if (head != null && me != null)
            (me.positionInRoot().y - (head.positionInRoot().y + head.size.height)).coerceAtLeast(0f) + size.height * 0.5f
        else size.height
        translationY = -drop * (1f - t)
        alpha = (t * 1.4f - 0.2f).coerceIn(0f, 1f)
        val sc = 0.86f + 0.14f * t
        scaleX = sc; scaleY = sc
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
    }

/**
 * A book's row: cover, title, how many highlights, its status and your stars, washed in the
 * cover's own colours. Tap to open or fold away its highlights; tap the stars to review.
 */
@Composable
private fun BookHeader(book: com.aniq.glassreader.data.Book?, bookId: String, title: String, count: Int, open: Boolean, rating: Int, modifier: Modifier, onClick: () -> Unit) {
    val cover = rememberCoverOf(bookId, 200)
    val glow = rememberCoverGlow(bookId)
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, spring(0.7f, 500f), label = "chev")
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp).bouncyClick(pressedScale = 0.97f, haptic = Haptic.Tick, onClick = onClick)
            .liquidGlass(shape).coverGlow(glow).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 40.dp, height = 58.dp).clip(RoundedCornerShape(5.dp)).background(Primary.copy(alpha = 0.08f))) {
            if (cover != null) Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (count) { 0 -> "No highlights"; 1 -> "1 highlight"; else -> "$count highlights" }, color = Secondary, fontSize = 14.sp)
                if (book != null) StatusPill(book, Modifier.padding(start = 8.dp), small = true)
            }
            // the stars are the way into the review
            if (book != null) Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50))
                    .bouncyClick(pressedScale = 0.92f, haptic = Haptic.Tick) { ReviewPrompt.open(bookId) }
                    .padding(vertical = 2.dp, horizontal = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (rating > 0) MiniStars(rating, size = 13.dp)
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(5) { Icon(Line.Star, null, tint = Primary.copy(alpha = 0.3f), modifier = Modifier.size(13.dp)) }
                    }
                    Text("Rate", color = Primary.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Icon(Line.Chevron, if (open) "Hide highlights" else "Show highlights", tint = Secondary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
    }
}

/** Your words about the book, first out when the book opens. */
@Composable
private fun ReviewCard(r: com.aniq.glassreader.data.Review, bookId: String, modifier: Modifier, onClick: () -> Unit) {
    val glow = rememberCoverGlow(bookId)
    val df = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().bouncyClick(pressedScale = 0.98f, onClick = onClick)
            .liquidGlass(shape).coverGlow(glow, 0.32f)
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Text("Your review", color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (r.text.isNotBlank()) Text(
            r.text.trim(), color = Primary, fontSize = 16.sp, lineHeight = 23.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.tags.take(3).forEach { t ->
                    Text(t, color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.pillGlass().padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }
            if (r.updatedAt > 0) Text(df.format(java.util.Date(r.updatedAt)), color = Secondary, fontSize = 11.sp)
        }
    }
}

/** One piece on the gallery wall: clear liquid glass, the words, and where they came from. */
@Composable
private fun Frame(h: Highlight, bookTitle: String, modifier: Modifier, query: String = "", showBook: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val clip = rememberClip(h)
    Column(
        modifier.fillMaxWidth().bouncyClick(pressedScale = 0.96f, onClick = onClick)
            .liquidGlass(shape)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp)
    ) {
        if (h.isClip) {
            if (clip != null) Image(clip, null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)))
            else Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(Primary.copy(alpha = 0.06f)))
        } else {
            Text(
                marked(h.text.trim(), query), color = Primary, fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp,
                maxLines = 10, overflow = TextOverflow.Ellipsis,
            )
        }
        NoteLine(h.note, Primary, maxLines = 4, query = query)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Primary.copy(alpha = 0.12f)))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showBook) listOf(bookTitle, h.chapter.takeIf { it != bookTitle }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                else h.chapter.takeIf { it.isNotBlank() && it != bookTitle } ?: bookTitle,
                color = Primary.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (h.favorite) Icon(Line.HeartFill, null, tint = Color(0xFFFF375F), modifier = Modifier.size(14.dp).padding(start = 2.dp))
        }
    }
}

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
    HideTabBarWhile(true)
    val haze = remember { HazeState() }
    val pager = rememberPagerState(initialPage = start) { list.size }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(list.size) { if (list.isEmpty()) onClose() }
    val haptics = rememberHaptics()
    var editing by remember { mutableStateOf<Highlight?>(null) }
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
                    if (h.note.isNotBlank()) Text(h.note.trim(), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, lineHeight = 22.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 18.dp).widthIn(max = 640.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(titles[h.bookId] ?: "", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOf(h.chapter.takeIf { it != titles[h.bookId] }.orEmpty(), df.format(Date(h.createdAt))).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // close
        GlassCircle(Line.Close, "Close", haze, size = 42.dp, glassTint = Color(0xFF1C1C1E).copy(alpha = 0.5f), base = Color.Black, light = false, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(top = top + 10.dp, end = 16.dp), onClick = onClose)

        // counter
        Text(
            "${pager.currentPage + 1} / ${list.size}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopStart).padding(top = top + 22.dp, start = 24.dp),
        )

        // actions
        val cur = list.getOrNull(pager.currentPage)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 18.dp)
                .glass(haze, RoundedCornerShape(50), tint = Color(0xFF1C1C1E).copy(alpha = 0.5f), base = Color.Black, light = false).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExhibitAction(Line.Open, "Read") { cur?.let(onOpen) }
            ExhibitAction(if (cur?.favorite == true) Line.HeartFill else Line.Heart, "Love",
                tint = if (cur?.favorite == true) Color(0xFFFF375F) else Color.White) {
                cur?.let { Store.updateHighlight(it.id) { x -> x.copy(favorite = !x.favorite) } }
                haptics.play(if (cur?.favorite == true) Haptic.ToggleOff else Haptic.Confirm)
            }
            ExhibitAction(Line.Note, if (cur?.note?.isNotBlank() == true) "Edit note" else "Note") { editing = cur }
            if (cur?.isClip == false) ExhibitAction(Line.Copy, "Copy") { clipboard.setText(AnnotatedString(cur.text)); haptics.play(Haptic.Confirm) }
            ExhibitAction(Line.Trash, "Delete") { cur?.let { Store.removeHighlight(it.id) }; haptics.play(Haptic.Reject) }
        }

        // note editor, over the piece
        AnimatedVisibility(editing != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).bouncyClick(pressedScale = 1f, haptic = null) { editing = null })
        }
        AnimatedVisibility(
            editing != null,
            enter = androidx.compose.animation.slideInVertically(spring(0.85f, 420f)) { it } + fadeIn(tween(150)),
            exit = androidx.compose.animation.slideOutVertically(spring(0.95f, 600f)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var last by remember { mutableStateOf<Highlight?>(null) }
            editing?.let { last = it }
            val h = last ?: return@AnimatedVisibility
            BackHandler { editing = null }
            Box(
                Modifier.imePadding().padding(start = 8.dp, end = 8.dp, bottom = bottom + 8.dp).widthIn(max = 620.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp)).background(Color(0xFF1C1C1E)).padding(vertical = 14.dp)
            ) {
                androidx.compose.runtime.key(h.id) {
                    NoteEditor(h.text, h.note, Color.White, Color.White.copy(alpha = 0.55f)) { t ->
                        Store.updateHighlight(h.id) { x -> x.copy(note = t) }
                        haptics.play(Haptic.Confirm); editing = null
                    }
                }
            }
        }
    }
}

@Composable
private fun ExhibitAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.width(66.dp).bouncyClick(pressedScale = 0.85f, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
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
