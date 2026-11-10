package com.aniq.glassreader.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.Review
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- Numbers ----------------

class YearStats(
    val year: Int,
    val finished: List<Book>,
    val hours: Float,
    val highlights: Int,
    val average: Float,
    val histogram: IntArray,       // books per star, index 0 = 1 star
    val bookOfYear: Book?,
    val loved: Highlight?,
    val tags: List<Pair<String, Int>>,
    val top: List<Book>,
)

private fun yearOf(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

/** Every year that has something to show, newest first (always including this one). */
fun yearsWithReading(books: List<Book>, daily: Map<String, Long>, highlights: List<Highlight>): List<Int> {
    val ys = sortedSetOf(LocalDate.now().year)
    books.filter { it.isFinished && it.finishedAt > 0 }.forEach { ys += yearOf(it.finishedAt) }
    daily.keys.forEach { k -> k.take(4).toIntOrNull()?.let { ys += it } }
    highlights.filter { it.createdAt > 0 }.forEach { ys += yearOf(it.createdAt) }
    return ys.toList().filter { it > 2000 }.sortedDescending()
}

fun yearStats(year: Int, books: List<Book>, reviews: Map<String, Review>, highlights: List<Highlight>, daily: Map<String, Long>): YearStats {
    val finished = books.filter { it.isFinished && it.finishedAt > 0 && yearOf(it.finishedAt) == year }.sortedBy { it.finishedAt }
    // reading time comes from the day-by-day log, so it's true to the year
    val ms = daily.filterKeys { it.startsWith("$year-") }.values.sum()
    val hl = highlights.filter { it.createdAt > 0 && yearOf(it.createdAt) == year }
    val rated = finished.mapNotNull { b -> reviews[b.id]?.rating?.takeIf { it > 0 } }
    val hist = IntArray(5).also { h -> rated.forEach { h[it - 1]++ } }
    val hlCount = highlights.groupingBy { it.bookId }.eachCount()
    val ranked = finished.sortedWith(compareByDescending<Book> { reviews[it.id]?.rating ?: 0 }.thenByDescending { hlCount[it.id] ?: 0 })
    val best = ranked.firstOrNull()
    val loved = best?.let { b ->
        highlights.filter { it.bookId == b.id && !it.isClip }.let { hs -> hs.firstOrNull { it.favorite } ?: hs.maxByOrNull { it.createdAt } }
    }
    val tags = finished.flatMap { reviews[it.id]?.tags.orEmpty() }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(4).map { it.key to it.value }
    return YearStats(year, finished, ms / 3_600_000f, hl.size, if (rated.isEmpty()) 0f else rated.average().toFloat(),
        hist, best, loved, tags, ranked.take(5))
}

// ---------------- Goals card ----------------

/** The way in, on the Goals tab: a glowing card with this year's count. */
@Composable
fun YearCard(onOpen: () -> Unit) {
    val books by Store.books.collectAsState()
    val reviews by Store.reviews.collectAsState()
    val highlights by Store.highlights.collectAsState()
    val daily by Store.daily.collectAsState()
    val year = LocalDate.now().year
    val st = remember(books, reviews, highlights, daily) { yearStats(year, books, reviews, highlights, daily) }
    val glows = st.top.take(3).map { rememberCoverGlow(it.id) }
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.97f, onClick = onOpen).liquidGlass(shape)
            .drawWithContent {
                glows.forEachIndexed { i, g ->
                    if (g != null) drawImage(g, dstOffset = androidx.compose.ui.unit.IntOffset((size.width * i / 3f).toInt(), 0),
                        dstSize = androidx.compose.ui.unit.IntSize((size.width / 2f).toInt(), size.height.toInt()), alpha = 0.45f)
                }
                drawRect(Color.Black.copy(alpha = if (UiMode.dark) 0.2f else 0f))
                drawContent()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 64.dp, height = 62.dp)) {
            st.top.take(3).forEachIndexed { i, b ->
                Cover(b, Modifier.width(36.dp).aspectRatio(0.67f).offset(x = (i * 13).dp, y = if (i == 1) 0.dp else 5.dp)
                    .graphicsLayer { rotationZ = (i - 1) * 9f })
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text("Your year in books", color = Secondary, fontSize = 13.sp)
            Text("$year · ${st.finished.size} book${if (st.finished.size == 1) "" else "s"}", color = Primary, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            val bits = listOfNotNull(
                if (st.average > 0f) "★ ${"%.1f".format(st.average)} average" else null,
                if (st.hours >= 1f) "${st.hours.roundToInt()}h" else null,
            )
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), color = Secondary, fontSize = 13.sp)
        }
        Icon(Line.Chevron, null, tint = Secondary, modifier = Modifier.size(18.dp))
    }
}

// ---------------- Full screen ----------------

private val fallbackGlow = listOf(Color(0xFF534AB7), Color(0xFF1D9E75), Color(0xFFBA7517), Color(0xFF993556))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearInBooks(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    HideTabBarWhile(true)
    val books by Store.books.collectAsState()
    val reviews by Store.reviews.collectAsState()
    val highlights by Store.highlights.collectAsState()
    val daily by Store.daily.collectAsState()
    val years = remember(books, daily, highlights) { yearsWithReading(books, daily, highlights) }
    var yi by remember { mutableIntStateOf(0) }
    val year = years.getOrElse(yi) { LocalDate.now().year }
    val st = remember(year, books, reviews, highlights, daily) { yearStats(year, books, reviews, highlights, daily) }
    val haze = remember { HazeState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shot = rememberGraphicsLayer()
    val haptics = rememberHaptics()

    Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
        // everything inside this box is what "Share my year" captures
        Box(
            Modifier.fillMaxSize().drawWithContent {
                shot.record { this@drawWithContent.drawContent() }
                drawLayer(shot)
            }
        ) {
            GlowBackdrop(st.top, Modifier.fillMaxSize().hazeSource(haze))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = top + 14.dp, bottom = bottom + 30.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your year in books", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (years.size > 1) {
                        val older = yi < years.lastIndex
                        val newer = yi > 0
                        YearChip(Line.Back, enabled = older) { if (older) { yi++; haptics.play(Haptic.Tick) } }
                        Spacer(Modifier.width(8.dp))
                        YearChip(Line.Chevron, enabled = newer) { if (newer) { yi--; haptics.play(Haptic.Tick) } }
                        Spacer(Modifier.width(8.dp))
                    }
                    YearChip(Line.Close, enabled = true, onClick = onClose)
                }
                AnimatedContent(year, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "year") { y ->
                    Text("$y", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp,
                        modifier = Modifier.padding(top = 6.dp))
                }

                CoverFan(st.top, year)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    CountUp(st.finished.size, year) { Text(it, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp) }
                    Text(if (st.finished.size == 1) "  book finished" else "  books finished", color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 9.dp))
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Frost(haze, Modifier.weight(1f)) {
                        Icon(Line.Clock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        CountUp(st.hours.roundToInt(), year, suffix = "h") { Text(it, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                        Text("spent reading", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Frost(haze, Modifier.weight(1f)) {
                        Icon(Line.Quote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        CountUp(st.highlights, year) { Text(it, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                        Text("passages saved", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
                if (st.average > 0f) {
                    Spacer(Modifier.height(10.dp))
                    Frost(haze, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Line.StarFill, null, tint = StarYellow, modifier = Modifier.size(22.dp))
                                    Text(" ${"%.1f".format(st.average)}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("average rating", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            Histogram(st.histogram, year)
                        }
                    }
                }

                st.bookOfYear?.let { b ->
                    Spacer(Modifier.height(10.dp))
                    Frost(haze, Modifier.fillMaxWidth()) {
                        Text("Book of the year", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(b, Modifier.width(48.dp).aspectRatio(0.67f))
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(b.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val r = reviews[b.id]?.rating ?: 0
                                if (r > 0) MiniStars(r, Modifier.padding(top = 4.dp), size = 13.dp, empty = Color.White.copy(alpha = 0.2f))
                            }
                        }
                        st.loved?.let { h ->
                            Text("“${h.text.trim()}”", color = Color.White.copy(alpha = 0.92f), fontFamily = FontFamily.Serif, fontSize = 16.sp,
                                lineHeight = 23.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                            Text(if (h.favorite) "Your most loved highlight" else "Your latest highlight", color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                if (st.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Frost(haze, Modifier.fillMaxWidth()) {
                        Text("This year you felt", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            st.tags.forEach { (t, n) ->
                                Text("$t · $n", color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 11.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                if (st.finished.isEmpty()) {
                    Text(
                        if (year == LocalDate.now().year) "Finish a book and it'll show up here." else "No finished books that year.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.96f) {
                        scope.launch {
                            val img = runCatching { shot.toImageBitmap() }.getOrNull()
                            if (img != null) shareYear(context, img, year)
                        }
                    }.clip(RoundedCornerShape(50)).background(Color(0xFFF5F5F7)).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Line.Share, null, tint = Color.Black, modifier = Modifier.size(17.dp))
                    Text("  Share my year", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun YearChip(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).bouncyClick(pressedScale = 0.85f, haptic = null, onClick = onClick)
            .clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.14f else 0.05f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(16.dp)) }
}

/** A frosted glass card over the glow. */
@Composable
private fun Frost(haze: HazeState, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .glass(haze, RoundedCornerShape(24.dp), tint = Color.White.copy(alpha = 0.07f), base = Color(0xFF050507), blur = 24.dp, light = false)
            .padding(16.dp)
    ) { content() }
}

/** Your top books' colours, blurred into slow-moving pools of light. */
@Composable
private fun GlowBackdrop(top: List<Book>, modifier: Modifier) {
    val glows = (0 until 4).map { i -> top.getOrNull(i)?.let { rememberCoverGlow(it.id) } }
    val t = rememberInfiniteTransition(label = "drift")
    val phase by t.animateFloat(0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    BoxWithConstraints(modifier) {
        val w = maxWidth; val h = maxHeight
        val spots = listOf(
            Triple(-0.25f, -0.08f, 0.95f), Triple(0.45f, 0.18f, 0.8f), Triple(-0.15f, 0.55f, 0.85f), Triple(0.5f, 0.82f, 0.75f),
        )
        Box(Modifier.fillMaxSize().blur(70.dp)) {
            spots.forEachIndexed { i, (x, y, s) ->
                val dx = (sin(phase + i * 1.7f) * 0.06f)
                val dy = (cos(phase * 0.8f + i * 2.1f) * 0.05f)
                val size = w * s
                Box(
                    Modifier.offset(x = w * (x + dx), y = h * (y + dy)).size(size).clip(CircleShape)
                        .background(fallbackGlow[i]).graphicsLayer { alpha = 0.85f }
                ) {
                    glows[i]?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
    }
}

/** The year's top books fan out from a stack. */
@Composable
private fun CoverFan(top: List<Book>, key: Int) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(key, top.size) { p.snapTo(0f); p.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 140f)) }
    if (top.isEmpty()) { Spacer(Modifier.height(24.dp)); return }
    // centre first, then alternating left and right, smaller and further back
    val slots = listOf(0, -1, 1, -2, 2)
    Box(Modifier.fillMaxWidth().height(170.dp).padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        top.take(5).withIndex().sortedByDescending { kotlin.math.abs(slots[it.index]) }.forEach { (i, b) ->
            val s = slots[i]
            Cover(
                b,
                Modifier.width(if (s == 0) 88.dp else 74.dp).aspectRatio(0.67f)
                    .graphicsLayer {
                        val v = p.value
                        translationX = s * 46.dp.toPx() * v
                        translationY = kotlin.math.abs(s) * 10.dp.toPx() * v
                        rotationZ = s * 9f * v
                    },
            )
        }
    }
}

/** A number that counts up from zero whenever the year changes. */
@Composable
private fun CountUp(target: Int, key: Int, suffix: String = "", text: @Composable (String) -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key, target) { a.snapTo(0f); a.animateTo(target.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    text("${a.value.roundToInt()}$suffix")
}

/** How many books got each star rating; the most common one lights up yellow. */
@Composable
private fun Histogram(h: IntArray, key: Int) {
    val maxV = (h.maxOrNull() ?: 0).coerceAtLeast(1)
    val peak = h.indices.maxByOrNull { h[it] } ?: -1
    Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        h.forEachIndexed { i, v ->
            val a = remember { Animatable(0f) }
            LaunchedEffect(key, v) { a.snapTo(0f); kotlinx.coroutines.delay(i * 60L); a.animateTo(v / maxV.toFloat(), spring(0.7f, 260f)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(11.dp).fillMaxHeight(0.82f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(11.dp).fillMaxHeight(a.value.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(3.dp))
                        .background(if (i == peak && v > 0) StarYellow else Color.White.copy(alpha = 0.25f)))
                }
                Text("${i + 1}", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
            }
        }
    }
}

/** Saves the captured screen and opens the share sheet. */
private suspend fun shareYear(context: Context, img: ImageBitmap, year: Int) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "hono-$year.png")
        f.outputStream().use { img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share my year").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
