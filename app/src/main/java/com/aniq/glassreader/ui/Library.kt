package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
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
fun LibraryScreen(haze: HazeState, importing: Boolean, onImport: () -> Unit, onOpen: (String) -> Unit) {
    val books by Store.books.collectAsState()
    val last = remember(books) { books.filter { it.lastOpened > 0 }.maxByOrNull { it.lastOpened } }
    val sorted = remember(books) { books.sortedByDescending { maxOf(it.lastOpened, it.addedAt) } }
    var menuFor by remember { mutableStateOf<Book?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 92.dp, bottom = bottom + 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (last != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                    HeroCard(last, Modifier.animateItem()) { onOpen(last.id) }
                }
            }
            if (sorted.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyLibrary(onImport) }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                    Text(
                        "All books · ${sorted.size}",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(sorted, key = { it.id }) { b ->
                    BookCard(b, Modifier.animateItem(), onClick = { onOpen(b.id) }, onLongClick = { menuFor = b })
                }
            }
        }

        GlassHeader(
            title = "Library",
            haze = haze,
            trailing = { GlassIconButton(Icons.Filled.Add, "Add book", onClick = onImport) },
        )

        AnimatedVisibility(
            visible = importing,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                "Importing…",
                color = Color.White, fontSize = 15.sp,
                modifier = Modifier
                    .glass(haze, RoundedCornerShape(50), tint = Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }

        GlassDialog(
            visible = menuFor != null,
            haze = haze,
            onDismiss = { menuFor = null },
        ) {
            var shown by remember { mutableStateOf<Book?>(null) }
            menuFor?.let { shown = it }
            Text(shown?.title ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("Its highlights will be removed too.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            DialogButton("Delete book", Color(0xFFFF6B6B)) { shown?.let { Store.delete(it.id) }; menuFor = null }
            Spacer(Modifier.height(8.dp))
            DialogButton("Cancel", Color.White) { menuFor = null }
        }
    }
}

@Composable
fun DialogButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

/** Centered glass dialog with a dimmed scrim. */
@Composable
fun GlassDialog(visible: Boolean, haze: HazeState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(160)) + scaleIn(spring(0.7f, 500f), initialScale = 0.86f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                Modifier
                    .padding(28.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .glass(haze, RoundedCornerShape(30.dp), tint = Color(0x33202030))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).staticGlass(RoundedCornerShape(30.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your shelf is empty", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Add an EPUB or TXT file from your phone.", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(180.dp)) { DialogButton("Add a book", Color.White, onImport) }
    }
}

@Composable
private fun HeroCard(book: Book, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, onClick = onClick)
            .staticGlass(RoundedCornerShape(28.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, Modifier.width(78.dp).aspectRatio(0.66f))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text("CONTINUE READING", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(4.dp))
            Text(book.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.author.isNotBlank()) Text(book.author, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            ProgressLine(book.progress)
            Spacer(Modifier.height(4.dp))
            Text("${(book.progress * 100).toInt()}% read", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress, spring(0.9f, 200f), label = "p")
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f))) {
        Box(
            Modifier.fillMaxWidth(p.coerceIn(0.001f, 1f)).height(5.dp).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Color(0xFF9EE7FF), Color(0xFFC7A6FF))))
        )
    }
}

@Composable
private fun BookCard(book: Book, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier.bouncyClick(onLongClick = onLongClick, onClick = onClick)) {
        Cover(book, Modifier.fillMaxWidth().aspectRatio(0.66f))
        Spacer(Modifier.height(10.dp))
        Text(book.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        if (book.author.isNotBlank()) Text(book.author, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (book.progress > 0f) {
            Spacer(Modifier.height(6.dp))
            ProgressLine(book.progress)
        }
    }
}

private val coverGradients = listOf(
    listOf(Color(0xFF4B3BD6), Color(0xFF1E8AC8)),
    listOf(Color(0xFFD9467A), Color(0xFF7A3BD6)),
    listOf(Color(0xFF0FA38A), Color(0xFF1E4FC8)),
    listOf(Color(0xFFE08A2E), Color(0xFFC83B5B)),
    listOf(Color(0xFF3B4A6B), Color(0xFF1B2133)),
)

@Composable
fun Cover(book: Book, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val bmp by produceState<ImageBitmap?>(null, book.id, book.hasCover) {
        value = if (book.hasCover) withContext(Dispatchers.IO) { Store.cover(book.id)?.asImageBitmap() } else null
    }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(260), label = "cover")
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(coverGradients[(book.title.hashCode() and 0x7fffffff) % coverGradients.size]))
            .staticGlass(shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            book.title, color = Color.White, fontFamily = FontFamily.Serif, fontSize = 15.sp, textAlign = TextAlign.Center,
            maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp),
        )
        bmp?.let {
            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
        }
        // glass sheen over the cover
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Transparent))
            )
        )
    }
}
