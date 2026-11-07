package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
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
) {
    val books by Store.books.collectAsState()
    val recent = remember(books) { books.filter { it.lastOpened > 0 }.sortedByDescending { it.lastOpened }.take(12) }
    val sorted = remember(books) { books.sortedBy { it.title.lowercase() } }
    var menuFor by remember { mutableStateOf<Book?>(null) }
    var confirmRemove by remember { mutableStateOf<Book?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                ScreenTitle("Library") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassCircle(Line.Plus, "Add book", haze, size = 40.dp, onClick = onImport)
                        GlassCircle(Line.Gear, "Settings", haze, size = 40.dp, onClick = onSettings)
                    }
                }
            }

            if (sorted.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyLibrary(onImport) }
            }
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "recentTitle") { SectionTitle("Continue Reading") }
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.fillMaxWidth().layoutBleed(20.dp),
                    ) {
                        rowItems(recent, key = { it.id }) { b ->
                            Column(Modifier.width(150.dp).bouncyClick(onLongClick = { menuFor = b }) { onOpen(b.id) }) {
                                Cover(b, Modifier.fillMaxWidth().aspectRatio(0.67f))
                                Spacer(Modifier.height(9.dp))
                                Text(b.title, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(progressLabel(b), color = Secondary, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (sorted.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "allTitle") { SectionTitle("All Books") }
                items(sorted, key = { it.id }) { b ->
                    BookCard(b, Modifier.animateItem(), onClick = { onOpen(b.id) }, onMore = { menuFor = b })
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
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.001f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.9f)))
    }
}

@Composable
private fun BookCard(book: Book, modifier: Modifier, onClick: () -> Unit, onMore: () -> Unit) {
    Column(modifier) {
        Cover(book, Modifier.fillMaxWidth().aspectRatio(0.67f).bouncyClick(onLongClick = onMore, onClick = onClick))
        Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).bouncyClick(pressedScale = 0.98f, haptic = null, onClick = onClick)) {
                Text(book.title, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(book.author, progressLabel(book)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.padding(start = 4.dp).size(30.dp).bouncyClick(pressedScale = 0.8f, onClick = onMore),
                contentAlignment = Alignment.Center,
            ) { Icon(Line.More, "More", tint = Secondary, modifier = Modifier.size(20.dp)) }
        }
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
