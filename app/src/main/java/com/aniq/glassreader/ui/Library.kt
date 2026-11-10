package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import com.aniq.glassreader.data.BookStatus
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    haze: HazeState,
    importing: Boolean,
    onOpen: (String) -> Unit,
    onExport: (ExportKind, Book?) -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onOpenAt: (String, Int, Int) -> Unit = { id, _, _ -> onOpen(id) },
) {
    val books by Store.books.collectAsState()
    val current = remember(books) { Store.currentBook() }
    val inProgress = remember(books, current) {
        books.filter { (it.status == BookStatus.READING || it.status == BookStatus.PAUSED) && it.id != current?.id }
            .sortedByDescending { it.lastOpened }
    }
    val toReadIds = remember(books, current) {
        books.filter { it.status == BookStatus.TO_READ && it.id != current?.id }.sortedBy { it.rank }.map { it.id }
    }
    val finished = remember(books, current) {
        books.filter { it.isFinished && it.id != current?.id }.sortedByDescending { maxOf(it.finishedAt, it.lastOpened) }
    }
    val byId = remember(books) { books.associateBy { it.id } }
    var menuFor by remember { mutableStateOf<Book?>(null) }
    var confirmRemove by remember { mutableStateOf<Book?>(null) }
    var finishedOpen by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }
    val found = remember(books, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else books.filter { it.title.contains(q, true) || it.author.contains(q, true) }
            .sortedBy { it.title.lowercase() }
    }
    val haptics = rememberHaptics()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ---- hold and drag to reorder "To read" ----
    val order = remember { mutableStateListOf<String>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    val slots = remember { mutableMapOf<String, LayoutCoordinates>() }
    val toRead = (if (dragId != null) order.toList() else toReadIds).mapNotNull { byId[it] }
    fun dragTo(local: Offset) {
        val id = dragId ?: return
        dragDelta = local - dragStart
        val me = slots[id]?.takeIf { it.isAttached } ?: return
        val finger = me.positionInRoot() + local
        val target = order.firstOrNull { other ->
            other != id && slots[other]?.takeIf { it.isAttached }?.let { c ->
                val p = c.positionInRoot()
                finger.x in p.x..(p.x + c.size.width) && finger.y in p.y..(p.y + c.size.height)
            } == true
        } ?: return
        val to = order.indexOf(target)
        order.remove(id); order.add(to, id)
        haptics.play(Haptic.TextMove)
    }

    @Composable
    fun androidx.compose.foundation.lazy.grid.LazyGridItemScope.shelfCard(b: Book, draggable: Boolean, dim: Boolean = false) {
        val lifted = dragId == b.id
        var pressed by remember { mutableStateOf(false) }
        BookCard(
            b,
            Modifier
                .zIndex(if (lifted) 2f else 0f)
                .animateItem(placementSpec = if (lifted) null else spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold))
                .onGloballyPositioned { if (draggable) slots[b.id] = it }
                .coverGestures(
                    draggable = draggable,
                    onPress = { pressed = it },
                    onTap = { haptics.play(Haptic.Tap); onOpen(b.id) },
                    onMenu = { haptics.play(Haptic.LongPress); menuFor = b },
                    onLift = { at ->
                        haptics.play(Haptic.GestureStart)
                        order.clear(); order.addAll(toReadIds)
                        dragStart = at; dragDelta = Offset.Zero; dragId = b.id
                    },
                    onMove = { dragTo(it) },
                    onDrop = { moved ->
                        if (moved) { Store.reorderToRead(order.toList()); haptics.play(Haptic.GestureEnd) }
                        dragId = null; dragDelta = Offset.Zero
                    },
                ),
            delta = if (lifted) dragDelta else Offset.Zero,
            lifted = lifted, pressed = pressed, dim = dim,
            onMore = { menuFor = b },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                SearchableTitle("Library", haze, searching, query, "Search title or author",
                    onSearching = { searching = it }, onQuery = { query = it }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassCircle(Line.Plus, "Add book", haze, size = 40.dp, onClick = onImport)
                        GlassCircle(Line.Gear, "Settings", haze, size = 40.dp, onClick = onSettings)
                    }
                }
            }

            if (searching && query.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "foundCount") {
                    Text(if (found.isEmpty()) "No books match" else "Books · ${found.size}", color = Secondary, fontSize = 13.sp)
                }
                items(found, key = { "f-" + it.id }, span = { GridItemSpan(maxLineSpan) }) { b ->
                    FoundRow(b, query, Modifier.animateItem()) { onOpen(b.id) }
                }
            } else {
                if (books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyLibrary(onImport) }
                }
                if (current != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "recentTitle") { SectionTitle("Continue Reading") }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "continueCard") {
                        ContinueCard(current, onOpen = onOpen, onOpenAt = onOpenAt, onMore = { menuFor = current })
                    }
                }
                if (inProgress.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "progTitle") { ShelfTitle("In progress", inProgress.size) }
                    items(inProgress, key = { "b-" + it.id }) { b -> shelfCard(b, draggable = false) }
                }
                if (toRead.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "toReadTitle") {
                        ShelfTitle("To read", toRead.size, hint = if (toRead.size > 1) "Hold and drag to reorder" else null)
                    }
                    items(toRead, key = { "b-" + it.id }) { b -> shelfCard(b, draggable = true) }
                }
                if (finished.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "finTitle") {
                        ShelfTitle("Finished", finished.size, folded = !finishedOpen) { finishedOpen = !finishedOpen }
                    }
                    if (finishedOpen) items(finished, key = { "b-" + it.id }) { b -> shelfCard(b, draggable = false, dim = true) }
                }
            }
        }

        AnimatedVisibility(
            visible = importing,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                "Adding book…", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.glass(haze, RoundedCornerShape(50), floating = true).padding(horizontal = 22.dp, vertical = 13.dp),
            )
        }

        ActionSheet(visible = menuFor != null, haze = haze, onDismiss = { menuFor = null }) {
            var shown by remember { mutableStateOf<Book?>(null) }
            menuFor?.let { shown = it }
            val b = shown
            if (b != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(b, Modifier.width(44.dp).aspectRatio(0.67f))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(b.title, color = Primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(b.author, progressLabel(b)).filter { it.isNotBlank() }.joinToString(" · "), color = Secondary, fontSize = 13.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SheetAction("Read", null) { menuFor = null; onOpen(b.id) }
                SheetAction("Export highlights", ".md") { onExport(ExportKind.BookHighlights, b); menuFor = null }
                SheetAction("Export book file", ".${b.format}") { onExport(ExportKind.BookFile, b); menuFor = null }
                SheetAction("Export book with highlights", ".zip") { onExport(ExportKind.Bundle, b); menuFor = null }
                SheetAction("Remove from Library", null, Color(0xFFFF453A), haptic = Haptic.Reject) { confirmRemove = b; menuFor = null }
            }
        }

        ActionSheet(visible = confirmRemove != null, haze = haze, onDismiss = { confirmRemove = null }) {
            var shown by remember { mutableStateOf<Book?>(null) }
            confirmRemove?.let { shown = it }
            Text("Remove “${shown?.title ?: ""}”?", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text("The book and its highlights will be deleted from this phone.", color = Secondary, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            Spacer(Modifier.height(10.dp))
            SheetAction("Remove", null, Color(0xFFFF453A), haptic = Haptic.Reject) { shown?.let { Store.delete(it.id) }; confirmRemove = null }
            SheetAction("Cancel", null) { confirmRemove = null }
        }
    }
}

/** Widget-style card: where you are in the current book, plus its latest highlights. */
@Composable
private fun ContinueCard(book: Book, onOpen: (String) -> Unit, onOpenAt: (String, Int, Int) -> Unit, onMore: () -> Unit) {
    val all by Store.highlights.collectAsState()
    val latest = remember(all, book.id) { all.filter { it.bookId == book.id }.sortedByDescending { it.createdAt }.take(2) }
    val count = remember(all, book.id) { all.count { it.bookId == book.id } }
    val totalMs by Store.totalReadMs.collectAsState()
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f, onLongClick = onMore) { onOpen(book.id) }
            .liquidGlass(shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(book, Modifier.width(76.dp).aspectRatio(0.67f))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                // total time read, top-right
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Line.Clock, null, tint = Primary, modifier = Modifier.size(13.dp))
                        Text("${formatReadTime(totalMs)} read", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 5.dp))
                    }
                }
                Text(book.title, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                if (book.author.isNotBlank()) Text(book.author, color = Secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (book.isPdf) "Page ${book.progressIndex + 1} of ${book.totalParas}" else "${(book.progress * 100).toInt()}% read",
                        color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    )
                    Text(if (book.isPdf) "${(book.progress * 100).toInt()}%" else "", color = Secondary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(7.dp))
                ProgressLine(book.progress)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Primary.copy(alpha = 0.12f)))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent highlights", color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (count > 0) Text("$count", color = Tertiary, fontSize = 13.sp)
        }
        if (latest.isEmpty()) {
            Text("Hold on any sentence while reading to save it here.", color = Tertiary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        latest.forEach { h ->
            Text(
                if (h.isClip) "Clip from page ${h.para + 1}" else h.text.trim(),
                color = Primary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    .bouncyClick(pressedScale = 0.98f) { onOpenAt(h.bookId, h.para, h.start) },
            )
        }
    }
}

fun progressLabel(b: Book): String = when {
    b.lastOpened == 0L -> "New"
    b.progress >= 0.995f -> "Finished"
    else -> "${(b.progress * 100).toInt()}%"
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Primary, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
        modifier = Modifier.padding(top = 6.dp))
}

/** Lets a horizontal row run edge to edge inside padded content. */
private fun Modifier.layoutBleed(by: androidx.compose.ui.unit.Dp): Modifier = this.layout { m, c ->
    val extra = by.roundToPx() * 2
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

@Composable
fun SheetAction(title: String, hint: String?, color: Color = Primary, haptic: Haptic = Haptic.Tap, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, haptic = haptic, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Primary.copy(alpha = 0.06f))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, color = Secondary, fontSize = 13.sp)
    }
    Spacer(Modifier.height(8.dp))
}

/** Bottom action sheet in liquid glass. */
@Composable
fun ActionSheet(visible: Boolean, haze: HazeState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    HideTabBarWhile(visible)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
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
                    .padding(start = 10.dp, end = 10.dp, bottom = bottom + 10.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .glass(haze, RoundedCornerShape(34.dp), blur = 34.dp, floating = true)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.2f)))
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No books yet", color = Primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(6.dp))
        Text("EPUB, PDF and text files all work.", color = Secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(
            "Add a book", color = AppBg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.bouncyClick(onClick = onImport).clip(RoundedCornerShape(50)).background(Primary)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun rememberCover(book: Book, px: Int = 600): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, book.id, book.hasCover) {
        value = if (book.hasCover) withContext(Dispatchers.IO) { Store.cover(book.id, px)?.asImageBitmap() } else null
    }
    return bmp
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress, spring(0.9f, 200f), label = "p")
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.001f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.85f)))
    }
}

@Composable
private fun BookCard(
    book: Book,
    modifier: Modifier,
    delta: Offset,
    lifted: Boolean,
    pressed: Boolean,
    dim: Boolean,
    onMore: () -> Unit,
) {
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(0.62f, 420f), label = "lift")
    val press by animateFloatAsState(if (pressed && !lifted) 0.96f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "press")
    Column(
        modifier.graphicsLayer {
            translationX = delta.x; translationY = delta.y
            val s = press * (1f + 0.06f * lift)
            scaleX = s; scaleY = s
            rotationZ = -3f * lift
            alpha = if (dim && !lifted) 0.82f else 1f
        }
    ) {
        Cover(book, Modifier.fillMaxWidth().aspectRatio(0.67f).shadow((22 * lift).dp, RoundedCornerShape(10.dp), clip = false))
        if (book.status != BookStatus.TO_READ) ThinProgress(if (book.isFinished) 1f else book.progress, Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = if (book.status != BookStatus.TO_READ) 6.dp else 9.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(book.title, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(book, Modifier.padding(top = 6.dp))
            }
            Box(
                Modifier.padding(start = 4.dp).size(30.dp).bouncyClick(pressedScale = 0.8f, onClick = onMore),
                contentAlignment = Alignment.Center,
            ) { Icon(Line.More, "More", tint = Secondary, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** A hairline of progress under a cover. */
@Composable
private fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress, spring(0.9f, 200f), label = "thin")
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.001f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.9f)))
    }
}

/** "In progress 3", optionally with a hint, or foldable like "Finished". */
@Composable
private fun ShelfTitle(text: String, count: Int, hint: String? = null, folded: Boolean = false, onToggle: (() -> Unit)? = null) {
    val turn by animateFloatAsState(if (folded) 0f else 90f, spring(0.7f, 500f), label = "fold")
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .then(if (onToggle != null) Modifier.bouncyClick(pressedScale = 0.98f, haptic = Haptic.Tick, onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Primary, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Text("$count", color = Tertiary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        Spacer(Modifier.weight(1f))
        if (hint != null) Text(hint, color = Tertiary, fontSize = 12.sp)
        if (onToggle != null) Icon(Line.Chevron, if (folded) "Show" else "Hide", tint = Secondary,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
    }
}

/** A search result: cover, title (matches marked), author and status. */
@Composable
private fun FoundRow(book: Book, query: String, modifier: Modifier, onClick: () -> Unit) {
    val reviews by Store.reviews.collectAsState()
    val rating = reviews[book.id]?.rating ?: 0
    Row(
        modifier.fillMaxWidth().bouncyClick(pressedScale = 0.98f, onClick = onClick)
            .liquidGlass(RoundedCornerShape(22.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, Modifier.width(42.dp).aspectRatio(0.67f))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(marked(book.title, query), color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val sub = listOf(book.author, BookStatus.label(book.status)).filter { it.isNotBlank() }.joinToString(" · ")
                Text(marked(sub, query), color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (rating > 0) MiniStars(rating, Modifier.padding(start = 8.dp), size = 11.dp)
            }
        }
    }
}

/**
 * Tap opens, hold opens the menu, and (for "To read") hold then move drags the book.
 * Scrolling the shelf before the hold kicks in cancels everything, so the list scrolls normally.
 */
private fun Modifier.coverGestures(
    draggable: Boolean,
    onPress: (Boolean) -> Unit,
    onTap: () -> Unit,
    onMenu: () -> Unit,
    onLift: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: (Boolean) -> Unit,
): Modifier = this.pointerInput(draggable) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress(true)
        val slop = viewConfiguration.touchSlop
        var outcome = 2 // 0 tap, 1 cancelled, 2 held
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (c == null) { outcome = 1; break }
                if (c.changedToUp()) { outcome = if (c.isConsumed) 1 else 0; break }
                if (c.isConsumed || (c.position - down.position).getDistance() > slop) { outcome = 1; break }
            }
        }
        if (outcome != 2) {
            onPress(false)
            if (outcome == 0) onTap()
            return@awaitEachGesture
        }
        onPress(false)
        if (!draggable) {
            onMenu()
            // swallow the rest of this touch so letting go doesn't also open the book
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (c.changedToUp()) break
            }
            return@awaitEachGesture
        }
        var last = down.position
        onLift(last)
        var moved = false
        while (true) {
            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (c.changedToUp()) { c.consume(); break }
            c.consume()
            last = c.position
            if ((last - down.position).getDistance() > slop * 2) moved = true
            onMove(last)
        }
        onDrop(moved)
        if (!moved) onMenu()
    }
}

private val coverTones = listOf(
    listOf(Color(0xFF3A3A3F), Color(0xFF111113)),
    listOf(Color(0xFF2C2C30), Color(0xFF050505)),
    listOf(Color(0xFF48484D), Color(0xFF1C1C1E)),
)

/** The book's own cover art; a quiet typographic cover only when the file has none. */
@Composable
fun Cover(book: Book, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val bmp = rememberCover(book)
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(260), label = "cover")
    Box(
        modifier
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.linearGradient(coverTones[(book.title.hashCode() and 0x7fffffff) % coverTones.size])),
        contentAlignment = Alignment.Center,
    ) {
        if (!book.hasCover) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(book.title, color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    maxLines = 5, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.2).sp)
                if (book.author.isNotBlank()) Text(book.author, color = Secondary, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) }
        Box(Modifier.fillMaxSize().hairline(shape))
    }
}
