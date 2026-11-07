package com.aniq.glassreader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.DateFormat
import java.util.Date

@Composable
fun HighlightsScreen(haze: HazeState, onOpen: (String, Int?) -> Unit) {
    val all by Store.highlights.collectAsState()
    val books by Store.books.collectAsState()
    var filter by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Highlight?>(null) }
    val titles = remember(books) { books.associate { it.id to it.title } }
    val groups = remember(all, filter) {
        all.filter { filter == 0 || it.favorite }
            .sortedByDescending { it.createdAt }
            .groupBy { it.bookId }
            .toList()
    }
    val favCount = remember(all) { all.count { it.favorite } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 150.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "stats") {
                Text(
                    "${all.size} highlights · $favCount favourites",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 30.dp)
                            .staticGlass(RoundedCornerShape(28.dp)).padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (filter == 0) "No highlights yet" else "No favourites yet",
                            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (filter == 0) "While reading, long-press a sentence (or press and drag) and pick a colour."
                            else "Tap the heart on any highlight to keep it here.",
                            color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            groups.forEach { (bookId, list) ->
                item(key = "h_$bookId") {
                    Text(
                        titles[bookId] ?: "Book",
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 14.dp, start = 4.dp).animateItem(),
                    )
                }
                items(list, key = { it.id }) { h ->
                    HighlightCard(
                        h, dateFmt.format(Date(h.createdAt)),
                        Modifier.widthIn(max = 720.dp).fillMaxWidth().animateItem(),
                        onClick = { onOpen(h.bookId, h.para) },
                        onLongClick = { confirm = h },
                    )
                }
            }
        }

        GlassHeader(title = "Highlights", haze = haze, below = {
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("All", "Favourites"), filter, { filter = it }, Modifier.padding(end = 8.dp))
        })

        GlassDialog(visible = confirm != null, haze = haze, onDismiss = { confirm = null }) {
            Text("Delete this highlight?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            DialogButton("Delete", Color(0xFFFF6B6B)) { confirm?.let { Store.removeHighlight(it.id) }; confirm = null }
            Spacer(Modifier.height(8.dp))
            DialogButton("Cancel", Color.White) { confirm = null }
        }
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fg: Color = Color.White,
) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(40.dp).clip(shape).background(fg.copy(alpha = 0.08f)).padding(3.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(0.7f, Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(shape).background(fg.copy(alpha = 0.18f)))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, s ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().bouncyClick(pressedScale = 0.92f) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s, color = if (i == selected) fg else fg.copy(alpha = 0.6f), fontSize = 14.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(h: Highlight, date: String, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val heartScale by animateFloatAsState(if (h.favorite) 1.15f else 1f, spring(0.35f, 600f), label = "heart")
    Row(
        modifier
            .bouncyClick(pressedScale = 0.97f, onLongClick = onLongClick, onClick = onClick)
            .staticGlass(RoundedCornerShape(24.dp))
            .height(IntrinsicSize.Min)
    ) {
        Box(
            Modifier.padding(start = 14.dp, top = 16.dp, bottom = 16.dp).width(4.dp).fillMaxHeight()
                .clip(RoundedCornerShape(50)).background(HighlightColors[h.color.coerceIn(0, HighlightColors.lastIndex)])
        )
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 10.dp)) {
            Text(
                "“${h.text.trim()}”",
                color = Color.White, fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 24.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(h.chapter, date).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(38.dp).bouncyClick(pressedScale = 0.8f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Store.updateHighlight(h.id) { it.copy(favorite = !it.favorite) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (h.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Favourite",
                        tint = if (h.favorite) Color(0xFFFF7AA2) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = heartScale; scaleY = heartScale },
                    )
                }
            }
        }
    }
}
