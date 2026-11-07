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
    onPickWallpaper: () -> Unit,
) {
    var appearance by remember { mutableStateOf(false) }
    val settings by Store.settings.collectAsState()
    val hasWall = Store.wallpaper.collectAsState().value != null
    val books by Store.books.collectAsState()
    val all by Store.highlights.collectAsState()
    val last = remember(books) { books.filter { it.lastOpened > 0 }.maxByOrNull { it.lastOpened } }
    val sorted = remember(books) { books.sortedByDescending { maxOf(it.lastOpened, it.addedAt) } }
    var menuFor by remember { mutableStateOf<Book?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 104.dp, bottom = bottom + 110.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (last != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                    HeroCard(last, all.count { it.bookId == last.id }, Modifier.animateItem()) { onOpen(last.id) }
                }
            }
            if (sorted.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyLibrary() }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                    Text("Books", color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
                items(sorted, key = { it.id }) { b ->
                    BookCard(b, Modifier.animateItem(), onClick = { onOpen(b.id) }, onLongClick = { menuFor = b })
                }
            }
        }

        LargeTitle(
            "Library", haze,
            subtitle = if (books.isEmpty()) null else "${books.size} ${if (books.size == 1) "book" else "books"}",
            trailing = { GlassCircle(Line.Sliders, "Appearance", haze, size = 42.dp) { appearance = true } },
        )

        AnimatedVisibility(
            visible = importing,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                "Importing…", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.glass(haze, RoundedCornerShape(50), floating = true).padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }

        ActionSheet(visible = appearance, haze = haze, onDismiss = { appearance = false }) {
            val ui = ReaderThemes[4] // graphite text colours for the controls
            Text("Appearance", color = Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            Spacer(Modifier.height(12.dp))
            SheetAction(if (hasWall) "Change wallpaper" else "Choose wallpaper from gallery", null) { appearance = false; onPickWallpaper() }
            if (hasWall) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    LabeledSlider("Dim wallpaper", "${(settings.wallDim * 100).toInt()}%", settings.wallDim, 0f..0.9f, ui) { v ->
                        Store.updateSettings { it.copy(wallDim = v) }
                    }
                    ToggleRow("Use while reading", "Blurred wallpaper behind your pages", settings.wallInReader, ui) { v ->
                        Store.updateSettings { it.copy(wallInReader = v) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                SheetAction("Remove wallpaper", null, Color(0xFFFF453A), haptic = Haptic.Reject) { Store.clearWallpaper() }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                ToggleRow("Black & White", "No colour anywhere, pure black reading", settings.mono, ui) { v ->
                    Store.updateSettings { it.copy(mono = v) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ActionSheet(visible = menuFor != null, haze = haze, onDismiss = { menuFor = null }) {
            var shown by remember { mutableStateOf<Book?>(null) }
            menuFor?.let { shown = it }
            val b = shown
            Text(b?.title ?: "", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(14.dp))
            SheetAction("Export highlights", ".md") { b?.let { onExport(ExportKind.BookHighlights, it) }; menuFor = null }
            SheetAction("Export book file", ".${b?.format ?: ""}") { b?.let { onExport(ExportKind.BookFile, it) }; menuFor = null }
            SheetAction("Export book + highlights", ".zip") { b?.let { onExport(ExportKind.Bundle, it) }; menuFor = null }
            SheetAction("Delete from library", null, Color(0xFFFF453A), haptic = Haptic.Reject) { b?.let { Store.delete(it.id) }; menuFor = null }
        }
    }
}

@Composable
fun SheetAction(title: String, hint: String?, color: Color = Primary, haptic: Haptic = Haptic.Tap, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, haptic = haptic, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
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
                Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)))
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing here yet", color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(8.dp))
        Text("Tap + to add an EPUB, PDF or TXT.", color = Secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun rememberCover(book: Book, px: Int = 600): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, book.id, book.hasCover) {
        value = if (book.hasCover) withContext(Dispatchers.IO) { Store.cover(book.id, px)?.asImageBitmap() } else null
    }
    return bmp
}

/** "Now playing"-style card: the cover, blurred and enlarged, becomes its own backdrop. */
@Composable
private fun HeroCard(book: Book, highlights: Int, modifier: Modifier, onClick: () -> Unit) {
    val cover = rememberCover(book)
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(Card),
    ) {
        if (cover != null) {
            Image(
                cover, null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(60.dp),
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.65f)))))
        Box(Modifier.matchParentSize().hairline(shape))
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book, Modifier.width(92.dp).aspectRatio(0.67f))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text("CONTINUE READING", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(book.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
                if (book.author.isNotBlank()) Text(book.author, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                ProgressLine(book.progress)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(book.progress * 100).toInt()}%" + if (highlights > 0) "  ·  $highlights highlights" else "",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress, spring(0.9f, 200f), label = "p")
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.001f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.9f)))
    }
}

@Composable
private fun BookCard(book: Book, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier.bouncyClick(onLongClick = onLongClick, onClick = onClick)) {
        Cover(book, Modifier.fillMaxWidth().aspectRatio(0.67f))
        Spacer(Modifier.height(10.dp))
        Text(book.title, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = buildList {
            if (book.author.isNotBlank()) add(book.author)
            if (book.progress > 0f) add("${(book.progress * 100).toInt()}%") else if (book.lastOpened == 0L) add("New")
        }.joinToString(" · ")
        Text(sub.ifEmpty { book.format.uppercase() }, color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
