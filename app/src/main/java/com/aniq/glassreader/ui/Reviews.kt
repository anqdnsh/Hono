package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.BookStatus
import com.aniq.glassreader.data.Review
import com.aniq.glassreader.data.ReviewTags
import com.aniq.glassreader.data.Store
import kotlinx.coroutines.delay

/** The one warm colour in the app: stars you've given. */
val StarYellow = Color(0xFFFFD60A)

/** Any screen can ask for the review sheet; the home screen shows it. */
object ReviewPrompt {
    var bookId by mutableStateOf<String?>(null)
    fun open(id: String) { bookId = id }
}

fun statusColor(s: Int): Color = when (s) {
    BookStatus.READING -> Color(0xFF64D2FF)
    BookStatus.PAUSED -> Color(0xFFFF9F0A)
    BookStatus.FINISHED -> Color(0xFF30D158)
    else -> Color(0xFF8E8E93)
}

// ---------------- Stars ----------------

/**
 * Five thin outline stars. The ones you pick fill yellow with a little pop, rippling
 * out from the first star to the one you tapped. Pass [onRate] = null for a read-only row.
 */
@Composable
fun StarRow(
    rating: Int,
    onRate: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    gap: Dp = 14.dp,
    idle: Color = Secondary,
) {
    val haptics = rememberHaptics()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            var lit by remember { mutableStateOf(i <= rating) }
            val pop = remember { Animatable(1f) }
            LaunchedEffect(rating) {
                val want = i <= rating
                if (want == lit) return@LaunchedEffect
                // fill from the first star outwards, empty from the last one inwards
                delay((if (want) i - 1 else 5 - i) * 38L)
                lit = want
                if (want) {
                    if (onRate != null) haptics.play(Haptic.Tick)
                    pop.snapTo(1.32f)
                    pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
                }
            }
            val tap = if (onRate != null) Modifier.bouncyClick(pressedScale = 0.78f, haptic = null) { onRate(i) } else Modifier
            Box(tap.size(size + 6.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (lit) Line.StarFill else Line.Star, null,
                    tint = if (lit) StarYellow else idle,
                    modifier = Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
            }
        }
    }
}

/** Small yellow stars for headers and pills. */
@Composable
fun MiniStars(rating: Int, modifier: Modifier = Modifier, size: Dp = 12.dp, empty: Color = Primary.copy(alpha = 0.22f)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) Icon(Line.StarFill, null, tint = if (i <= rating) StarYellow else empty, modifier = Modifier.size(size))
    }
}

// ---------------- Glass pill ----------------

/** Clear, light-catching glass for small pills that sit in scrolling content. */
fun Modifier.pillGlass(shape: Shape = RoundedCornerShape(50)): Modifier = composed {
    val dark = UiMode.dark
    this
        .clip(shape)
        .drawBehind {
            drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.20f else 0.70f), Color.White.copy(alpha = if (dark) 0.04f else 0.30f)),
                center = Offset(size.width * 0.25f, 0f), radius = size.maxDimension * 1.2f,
            ))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.10f else 0.45f), 0.55f to Color.Transparent))
            if (!dark) drawRect(Color.Black.copy(alpha = 0.035f))
        }
        .border(
            0.7.dp,
            Brush.linearGradient(
                if (dark) listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.30f))
                else listOf(Color.White, Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.05f), Color.White),
                start = Offset.Zero, end = Offset.Infinite,
            ),
            shape,
        )
}

private fun statusText(b: Book, rating: Int): String = when (b.status) {
    BookStatus.READING -> "Reading · ${(b.progress * 100).toInt()}%"
    BookStatus.PAUSED -> if (b.progress > 0f) "Paused · ${(b.progress * 100).toInt()}%" else "Paused"
    BookStatus.FINISHED -> if (rating > 0) "★ $rating" else "Rate"
    else -> "To read"
}

/**
 * The book's status as a small glass pill. Tap it and it stretches into a glass row of
 * all four statuses. A finished book you haven't rated says "Rate" and opens the review sheet.
 */
@Composable
fun StatusPill(book: Book, modifier: Modifier = Modifier, small: Boolean = false) {
    val reviews by Store.reviews.collectAsState()
    val rating = reviews[book.id]?.rating ?: 0
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box(modifier) {
        Row(
            Modifier
                .bouncyClick(pressedScale = 0.9f, haptic = Haptic.Tick) {
                    if (book.isFinished && rating == 0) ReviewPrompt.open(book.id) else open = true
                }
                .pillGlass()
                .padding(start = if (small) 7.dp else 8.dp, end = if (small) 8.dp else 10.dp, top = if (small) 3.dp else 4.dp, bottom = if (small) 3.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor(book.status)))
            Spacer(Modifier.width(5.dp))
            Text(statusText(book, rating), color = Primary, fontSize = if (small) 10.sp else 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (open) {
            val shown = remember { MutableTransitionState(false).apply { targetState = true } }
            Popup(
                popupPositionProvider = remember { OverAnchor() },
                onDismissRequest = { shown.targetState = false },
                properties = PopupProperties(focusable = true),
            ) {
                LaunchedEffect(shown.currentState, shown.targetState) {
                    if (!shown.targetState && !shown.currentState) open = false
                }
                AnimatedVisibility(
                    shown,
                    enter = fadeIn(tween(120)) + scaleIn(spring(0.72f, 520f), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 0.5f)),
                    exit = fadeOut(tween(110)) + scaleOut(spring(1f, 700f), targetScale = 0.7f, transformOrigin = TransformOrigin(0f, 0.5f)),
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (UiMode.dark) Color(0xF01C1C1E) else Color(0xF5F7F7FA))
                            .pillGlass()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookStatus.all.forEach { s ->
                            val on = s == book.status
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) Primary.copy(alpha = 0.14f) else Color.Transparent)
                                    .bouncyClick(pressedScale = 0.9f, haptic = null) {
                                        haptics.play(if (s == BookStatus.FINISHED) Haptic.Confirm else Haptic.Tick)
                                        Store.setStatus(book.id, s)
                                        shown.targetState = false
                                    }
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(s)))
                                Spacer(Modifier.width(6.dp))
                                Text(BookStatus.label(s), color = Primary, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                        // a way into the review from here too
                        Box(
                            Modifier.padding(start = 2.dp).size(34.dp).clip(CircleShape)
                                .bouncyClick(pressedScale = 0.85f) { shown.targetState = false; ReviewPrompt.open(book.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (rating > 0) Line.StarFill else Line.Star, "Review", tint = if (rating > 0) StarYellow else Primary, modifier = Modifier.size(17.dp)) }
                    }
                }
            }
        }
    }
}

/** Puts the status row over the pill that opened it, nudged in so it never runs off screen. */
private class OverAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val margin = 24
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val y = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
        return IntOffset(x, y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
    }
}

// ---------------- Review editor ----------------

/** Stars, mood tags and your own words. Used in the home review sheet and inside the reader. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewEditor(bookId: String, fg: Color = Primary, sub: Color = Secondary, onDone: () -> Unit) {
    val book = remember(bookId) { Store.book(bookId) }
    val start = remember(bookId) { Store.review(bookId) ?: Review(bookId) }
    var rating by remember(bookId) { mutableStateOf(start.rating) }
    var tags by remember(bookId) { mutableStateOf(start.tags.toSet()) }
    var text by remember(bookId) { mutableStateOf(start.text) }
    val haptics = rememberHaptics()

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (book != null) Cover(book, Modifier.width(44.dp).aspectRatio(0.67f))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(book?.title ?: "", color = fg, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Your review", color = sub, fontFamily = Inter, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        StarRow(rating, onRate = { rating = it }, Modifier.align(Alignment.CenterHorizontally), size = 32.dp, gap = 12.dp, idle = sub)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReviewTags.forEach { t ->
                val on = t in tags
                val glow by animateFloatAsState(if (on) 1f else 0f, spring(0.8f, 500f), label = "tag")
                Text(
                    t, color = fg.copy(alpha = 0.7f + 0.3f * glow), fontFamily = Inter, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .bouncyClick(pressedScale = 0.9f, haptic = Haptic.Tick) { tags = if (on) tags - t else tags + t }
                        .clip(RoundedCornerShape(50))
                        .background(fg.copy(alpha = 0.06f + 0.12f * glow))
                        .border(0.7.dp, fg.copy(alpha = 0.10f + 0.35f * glow), RoundedCornerShape(50))
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 90.dp).clip(RoundedCornerShape(18.dp)).background(fg.copy(alpha = 0.06f)).padding(14.dp)) {
            if (text.isEmpty()) Text("What did you think?", color = sub, fontFamily = Inter, fontSize = 15.sp)
            BasicTextField(
                text, { text = it },
                textStyle = TextStyle(color = fg, fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(fg),
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp, max = 200.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Save review", color = if (fg.red > 0.5f) Color.Black else Color.White, fontFamily = Inter, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .bouncyClick(pressedScale = 0.96f, haptic = null) {
                    haptics.play(Haptic.Confirm)
                    Store.saveReview(Review(bookId, rating, ReviewTags.filter { it in tags }, text.trim()))
                    onDone()
                }
                .clip(RoundedCornerShape(50)).background(fg)
                .padding(vertical = 14.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------- End of the book ----------------

private val backMatter = Regex(
    "^(acknowledg|notes?\\b|end ?notes|about the (author|authors|publisher)|also by|bibliograph|index\\b|glossary|" +
        "references|further reading|copyright|credits|permissions|preview|excerpt|sneak peek|reading group|" +
        "discussion|book club|praise for|a note on|newsletter|more from|other books|coming soon)",
    RegexOption.IGNORE_CASE,
)

/**
 * Where the story itself ends: the start of the trailing notes / acknowledgements /
 * "about the author" sections, or the very end when there aren't any.
 */
fun endOfStory(c: com.aniq.glassreader.data.BookContent): Int {
    val n = c.paras.size
    val chapters = c.chapters.filter { it.para in 0 until n }.sortedBy { it.para }
    var end = n
    for (ch in chapters.asReversed()) {
        if (backMatter.containsMatchIn(ch.title.trim())) end = ch.para else break
    }
    // never treat most of the book as back matter (odd tables of contents)
    return if (end < n * 0.6f) n else end.coerceAtLeast(1)
}

/** "You finished …": rises over the last page in the reader's own colours. */
@Composable
fun EndOfBookCard(
    book: Book,
    theme: ReaderTheme,
    haze: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    onKeepReading: () -> Unit,
    onWriteReview: () -> Unit,
) {
    val reviews by Store.reviews.collectAsState()
    val highlights by Store.highlights.collectAsState()
    val books by Store.books.collectAsState()
    val rating = reviews[book.id]?.rating ?: 0
    val count = remember(highlights, book.id) { highlights.count { it.bookId == book.id } }
    val readMs = books.firstOrNull { it.id == book.id }?.readMs ?: 0L
    Column(
        modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .glass(haze, RoundedCornerShape(30.dp), theme.glassTint, theme.bg, blur = 30.dp, light = theme.light, floating = true)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Line.Open, null, tint = theme.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text("You finished ${book.title}", color = theme.text, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        val bits = listOfNotNull(
            if (readMs > 60_000) "${formatReadTime(readMs)} read" else null,
            if (count > 0) "$count highlight${if (count == 1) "" else "s"}" else null,
        )
        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), color = theme.sub, fontFamily = Inter, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        StarRow(rating, onRate = { Store.rate(book.id, it) }, size = 28.dp, gap = 10.dp, idle = theme.sub)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Keep reading", color = theme.text, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).bouncyClick(pressedScale = 0.95f, onClick = onKeepReading)
                    .clip(RoundedCornerShape(50)).border(0.8.dp, theme.text.copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(vertical = 12.dp),
            )
            Text(
                "Write review", color = theme.bg, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).bouncyClick(pressedScale = 0.95f, onClick = onWriteReview)
                    .clip(RoundedCornerShape(50)).background(theme.text)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
