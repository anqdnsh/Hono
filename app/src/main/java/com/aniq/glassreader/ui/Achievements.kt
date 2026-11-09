package com.aniq.glassreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Gold = Color(0xFFFFB340)
private val Flame = Color(0xFFFF6B35)

/** Reading stats worked out from the daily log, books and highlights. */
class Progress(
    val todayMin: Int,
    val goalMin: Int,
    val streak: Int,
    val bestStreak: Int,
    val week: List<Pair<LocalDate, Int>>,
    val totalHours: Float,
    val goalDays: Int,
    val bestDayMin: Int,
    val highlights: Int,
    val notes: Int,
    val finished: Int,
)

fun progressOf(daily: Map<String, Long>, goalMin: Int, totalMs: Long, highlights: Int, notes: Int, finished: Int): Progress {
    val today = LocalDate.now()
    fun min(d: LocalDate) = ((daily[d.toString()] ?: 0L) / 60_000L).toInt()
    fun met(d: LocalDate) = min(d) >= goalMin
    // today still counts towards the streak until midnight, so it isn't lost before you've read
    var streak = 0
    var d = if (met(today)) today else today.minusDays(1)
    while (met(d)) { streak++; d = d.minusDays(1) }
    val days = daily.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    var best = 0; var run = 0; var prev: LocalDate? = null
    for (day in days) {
        if (!met(day)) { run = 0; prev = day; continue }
        run = if (prev != null && prev.plusDays(1) == day && run > 0) run + 1 else 1
        best = maxOf(best, run); prev = day
    }
    val monday = today.with(DayOfWeek.MONDAY)
    return Progress(
        todayMin = min(today), goalMin = goalMin, streak = streak, bestStreak = maxOf(best, streak),
        week = (0..6).map { monday.plusDays(it.toLong()).let { day -> day to min(day) } },
        totalHours = totalMs / 3_600_000f,
        goalDays = days.count { met(it) },
        bestDayMin = days.maxOfOrNull { min(it) } ?: 0,
        highlights = highlights, notes = notes, finished = finished,
    )
}

enum class Tier(val base: Color, val ink: Color) {
    Bronze(Color(0xFFD29A68), Color(0xFF6E4019)),
    Silver(Color(0xFFC8D0DA), Color(0xFF465263)),
    Gold(Color(0xFFF2C24B), Color(0xFF6E4800)),
    Rare(Color(0xFFA3ACFF), Color(0xFF2F35A0)),
}

enum class BadgeShape { Circle, Hex, Pill, Diamond, Octagon, Burst, Flower, Gem, Shield, Pentagon, Squircle, Drop }

class Badge(
    val title: String, val desc: String, val have: Float, val need: Float,
    val shape: BadgeShape, val tier: Tier, val glyph: String, val unit: String = "",
) {
    val done get() = have >= need
    val frac get() = (have / need).coerceIn(0f, 1f)
    val progressText: String get() {
        val h = if (need >= 10 || unit.isEmpty()) minOf(have, need).toInt().toString() else "%.1f".format(minOf(have, need))
        return "$h of ${need.toInt()}${if (unit.isNotEmpty()) " $unit" else ""}"
    }
}

private fun circ(r: Float) = "M${-r},0 A$r,$r 0 1,0 $r,0 A$r,$r 0 1,0 ${-r},0"

/** Line icons for the badges, drawn in a 40-unit box centred on 0. */
private object Glyph {
    val book = "M0,-6 C-6,-11 -13,-11 -17,-9 V12 C-13,10 -6,10 0,14 C6,10 13,10 17,12 V-9 C13,-11 6,-11 0,-6 Z M0,-6 V14"
    val target = circ(14f) + " " + circ(7f) + " " + circ(1.4f)
    val roll = "M-14,-8 L-6,0 L-14,8 M-4,-8 L4,0 L-4,8 M6,-8 L14,0 L6,8"
    val flame = "M0,-16 C6,-8 12,-3 11,5 C10,12 5,16 0,16 C-5,16 -10,12 -11,5 C-12,-2 -6,-5 -4,-11 C-2,-6 0,-5 2,-6 C3,-10 2,-13 0,-16 Z"
    val calendar = "M-13,-9 H13 V13 H-13 Z M-13,-3 H13 M-7,-14 V-6 M7,-14 V-6 M-6,4 H-5 M0,4 H1 M6,4 H7"
    val sprout = "M0,15 V-2 M0,4 C-9,3 -14,-4 -12,-10 C-6,-10 -1,-6 0,4 M0,-1 C8,-3 13,-10 11,-15 C5,-15 0,-10 0,-1"
    val bolt = "M3,-16 L-9,2 H0 L-3,16 L9,-2 H0 Z"
    val clock = circ(14f) + " M0,-8 V0 L6,4"
    val hourglass = "M-10,-15 H10 M-10,15 H10 M-8,-15 C-8,-4 8,4 8,15 M8,-15 C8,-4 -8,4 -8,15"
    val pen = "M6,-16 L14,-8 L-2,8 L-10,8 L-10,0 Z M-14,15 H8"
    val quote = "M-12,8 V0 C-12,-7 -9,-11 -4,-12 M-12,0 H-5 V8 H-12 M3,8 V0 C3,-7 6,-11 11,-12 M3,0 H10 V8 H3"
    val gem = "M-11,-5 L-5,-12 H5 L11,-5 L0,12 Z M-11,-5 H11 M-5,-12 L0,12 M5,-12 L0,12"
    val bulb = "M-7,5 C-13,0 -12,-14 0,-14 C12,-14 13,0 7,5 V10 H-7 Z M-4,15 H4"
    val flag = "M-9,16 V-15 M-9,-14 H10 L6,-7 L10,0 H-9"
    val books = "M-14,5 H14 V13 H-14 Z M-12,-4 H12 V4 H-12 Z M-9,-13 H9 V-5 H-9 Z"
    val star = "M0,-15 L4.4,-5 L15,-4.6 L7,2.6 L9.3,13 L0,7.5 L-9.3,13 L-7,2.6 L-15,-4.6 L-4.4,-5 Z"
}

fun badgesOf(p: Progress) = listOf(
    Badge("First page", "Read for a minute", p.totalHours * 60f, 1f, BadgeShape.Circle, Tier.Bronze, Glyph.book, "min"),
    Badge("Goal getter", "Hit your daily goal", p.goalDays.toFloat(), 1f, BadgeShape.Hex, Tier.Bronze, Glyph.target),
    Badge("On a roll", "3-day streak", p.bestStreak.toFloat(), 3f, BadgeShape.Pill, Tier.Bronze, Glyph.roll, "days"),
    Badge("Highlighter", "Save a highlight", p.highlights.toFloat(), 1f, BadgeShape.Drop, Tier.Bronze, Glyph.pen),
    Badge("Week strong", "7-day streak", p.bestStreak.toFloat(), 7f, BadgeShape.Diamond, Tier.Silver, Glyph.flame, "days"),
    Badge("Fortnight", "14-day streak", p.bestStreak.toFloat(), 14f, BadgeShape.Pentagon, Tier.Silver, Glyph.calendar, "days"),
    Badge("Ten hours", "10 hours of reading", p.totalHours, 10f, BadgeShape.Octagon, Tier.Silver, Glyph.clock, "hours"),
    Badge("Collector", "25 highlights", p.highlights.toFloat(), 25f, BadgeShape.Hex, Tier.Silver, Glyph.quote),
    Badge("Thinker", "Write 5 notes", p.notes.toFloat(), 5f, BadgeShape.Circle, Tier.Silver, Glyph.bulb),
    Badge("Marathon", "60 minutes in one day", p.bestDayMin.toFloat(), 60f, BadgeShape.Squircle, Tier.Gold, Glyph.bolt, "min"),
    Badge("Finisher", "Finish a book", p.finished.toFloat(), 1f, BadgeShape.Shield, Tier.Gold, Glyph.flag),
    Badge("Curator", "100 highlights", p.highlights.toFloat(), 100f, BadgeShape.Gem, Tier.Gold, Glyph.gem),
    Badge("Bookworm", "Finish 5 books", p.finished.toFloat(), 5f, BadgeShape.Pentagon, Tier.Gold, Glyph.books),
    Badge("Habit formed", "30-day streak", p.bestStreak.toFloat(), 30f, BadgeShape.Flower, Tier.Rare, Glyph.sprout, "days"),
    Badge("Fifty hours", "50 hours of reading", p.totalHours, 50f, BadgeShape.Burst, Tier.Rare, Glyph.hourglass, "hours"),
    Badge("Goal machine", "Hit your goal 50 times", p.goalDays.toFloat(), 50f, BadgeShape.Burst, Tier.Rare, Glyph.star),
)

@Composable
fun rememberProgress(): Progress {
    val daily by Store.daily.collectAsState()
    val settings by Store.settings.collectAsState()
    val total by Store.totalReadMs.collectAsState()
    val hl by Store.highlights.collectAsState()
    val books by Store.books.collectAsState()
    return remember(daily, settings.dailyGoalMin, total, hl, books) {
        progressOf(daily, settings.dailyGoalMin, total, hl.size, hl.count { it.note.isNotBlank() }, books.count { it.progress >= 0.98f })
    }
}

/** Calls [onReached] once, the moment today's reading crosses the goal while the reader is open. */
@Composable
fun GoalWatcher(onReached: () -> Unit) {
    val p = rememberProgress()
    var was by remember { mutableStateOf<Boolean?>(null) }
    val reached = p.todayMin >= p.goalMin
    LaunchedEffect(reached) {
        if (was == false && reached) onReached()
        was = reached
    }
}

@Composable
fun AchievementsScreen(haze: HazeState, onSettings: () -> Unit) {
    val p = rememberProgress()
    val badges = remember(p) { badgesOf(p) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var detail by remember { mutableStateOf<Badge?>(null) }
    // badges earned since this screen was last opened get a little pop
    val seen = remember { Store.seenBadges }
    val fresh = remember(badges) { badges.filter { it.done && it.title !in seen }.map { it.title }.toSet() }
    LaunchedEffect(badges) { Store.seenBadges = badges.filter { it.done }.map { it.title }.toSet() + seen }

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        modifier = Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
            Column(Modifier.padding(horizontal = 4.dp)) {
                ScreenTitle("Goals") { GlassCircle(Line.Gear, "Settings", haze, size = 40.dp, onClick = onSettings) }
                Text("${badges.count { it.done }} of ${badges.size} badges", color = Secondary, fontSize = 14.sp)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "today") { TodayCard(p) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "week") { WeekCard(p) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "badgesTitle") {
            Text("Badges", color = Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp))
        }
        items(badges, key = { it.title }) { b -> BadgeTile(b, b.title in fresh) { detail = b } }
    }
    androidx.compose.animation.AnimatedVisibility(
        detail != null,
        enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.75f, 420f), initialScale = 0.9f),
        exit = androidx.compose.animation.fadeOut(tween(150)) + androidx.compose.animation.scaleOut(targetScale = 0.95f),
    ) {
        var last by remember { mutableStateOf<Badge?>(null) }
        detail?.let { last = it }
        last?.let { BadgeDetail(it) { detail = null } }
    }
    }
}

@Composable
private fun TodayCard(p: Progress) {
    val shape = RoundedCornerShape(30.dp)
    val frac by animateFloatAsState((p.todayMin.toFloat() / p.goalMin).coerceIn(0f, 1f), tween(700), label = "ring")
    val ringColor = if (p.todayMin >= p.goalMin) Gold else Primary
    Column(Modifier.fillMaxWidth().liquidGlass(shape).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 11.dp.toPx()
                    drawArc(Primary.copy(alpha = 0.10f), 0f, 360f, false, style = Stroke(w))
                    drawArc(ringColor, -90f, 360f * frac, false, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.todayMin}", color = Primary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    Text("of ${p.goalMin} min", color = Secondary, fontSize = 12.sp)
                }
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                Text(
                    when {
                        p.todayMin >= p.goalMin -> "Goal reached"
                        p.todayMin == 0 -> "Today's goal"
                        else -> "${p.goalMin - p.todayMin} min to go"
                    },
                    color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (p.streak > 0) Flame else Primary.copy(alpha = 0.2f)))
                    Text(if (p.streak == 1) "1 day streak" else "${p.streak} day streak", color = Primary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
                Text("Best ${p.bestStreak} · ${"%.1f".format(p.totalHours)} h total", color = Secondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily goal", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Stepper("−") { Store.updateSettings { it.copy(dailyGoalMin = (it.dailyGoalMin - 5).coerceAtLeast(5)) } }
            Text("${p.goalMin} min", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp))
            Stepper("+") { Store.updateSettings { it.copy(dailyGoalMin = (it.dailyGoalMin + 5).coerceAtMost(180)) } }
        }
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Primary.copy(alpha = 0.08f)).bouncyClick(pressedScale = 0.85f, haptic = Haptic.Tick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun WeekCard(p: Progress) {
    val shape = RoundedCornerShape(26.dp)
    val today = LocalDate.now()
    val top = maxOf(p.goalMin, p.week.maxOf { it.second }, 1)
    Column(Modifier.fillMaxWidth().liquidGlass(shape).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${p.week.sumOf { it.second }} min", color = Secondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            p.week.forEach { (day, m) ->
                val h by animateFloatAsState(m.toFloat() / top, tween(600), label = "bar")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(18.dp).height(84.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Primary.copy(alpha = 0.06f)))
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(h.coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp))
                                .background(if (m >= p.goalMin) Gold else Primary.copy(alpha = if (day == today) 0.75f else 0.4f))
                        )
                    }
                    Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (day == today) Primary else Secondary, fontSize = 12.sp,
                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Text("Gold bars hit your goal.", color = Secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun polygon(n: Int, r: Float, rot: Float) = androidx.compose.ui.graphics.Path().apply {
    for (i in 0 until n) {
        val ang = i / n.toFloat() * 2f * Math.PI.toFloat() + rot
        val x = kotlin.math.cos(ang) * r; val y = kotlin.math.sin(ang) * r
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun svg(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()

/** The badge outline in an 80-unit box centred on 0, scaled by [k]. */
private fun shapePath(shape: BadgeShape, k: Float): androidx.compose.ui.graphics.Path {
    val p = when (shape) {
        BadgeShape.Circle -> svg(circ(36f))
        BadgeShape.Hex -> polygon(6, 38f, -Math.PI.toFloat() / 2)
        BadgeShape.Pill -> androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(-35f, -30f, 35f, 30f, 22f, 22f)) }
        BadgeShape.Squircle -> androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(-32f, -32f, 32f, 32f, 20f, 20f)) }
        BadgeShape.Diamond -> androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(-26f, -26f, 26f, 26f, 12f, 12f))
            transform(androidx.compose.ui.graphics.Matrix().apply { rotateZ(45f) })
        }
        BadgeShape.Octagon -> polygon(8, 38f, Math.PI.toFloat() / 8)
        BadgeShape.Pentagon -> polygon(5, 39f, -Math.PI.toFloat() / 2)
        BadgeShape.Burst -> androidx.compose.ui.graphics.Path().apply {
            for (i in 0 until 32) {
                val ang = i / 32f * 2f * Math.PI.toFloat(); val r = if (i % 2 == 0) 37.5f else 33f
                val x = kotlin.math.cos(ang) * r; val y = kotlin.math.sin(ang) * r
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        BadgeShape.Flower -> androidx.compose.ui.graphics.Path().apply {
            for (i in 0 until 8) {
                val ang = i / 8f * 2f * Math.PI.toFloat()
                addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(kotlin.math.cos(ang) * 22f, kotlin.math.sin(ang) * 22f), 14f))
            }
            addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, 27f))
        }
        BadgeShape.Gem -> svg("M-30,-12 L-16,-30 H16 L30,-12 L0,36 Z")
        BadgeShape.Shield -> svg("M0,-37 L31,-26 L31,3 C31,21 17,32 0,38 C-17,32 -31,21 -31,3 L-31,-26 Z")
        BadgeShape.Drop -> svg("M0,-38 C14,-22 30,-8 30,8 A30,30 0 0,1 -30,8 C-30,-8 -14,-22 0,-38 Z")
    }
    if (k != 1f) p.transform(androidx.compose.ui.graphics.Matrix().apply { scale(k, k) })
    return p
}

/** One badge, drawn: its shape in the tier colour, a soft inner rim, and its line icon. Locked = faint with a progress ring. */
@Composable
fun BadgeArt(b: Badge, modifier: Modifier) {
    val outer = remember(b.shape) { shapePath(b.shape, 1f) }
    val inner = remember(b.shape) { shapePath(b.shape, if (b.shape == BadgeShape.Flower) 0.8f else 0.84f) }
    val glyph = remember(b.glyph) { svg(b.glyph) }
    val faint = Primary
    Canvas(modifier) {
        val u = size.minDimension / 100f   // 100 units across; badge ~76, ring 94
        val c = center
        if (!b.done) {
            drawCircle(faint.copy(alpha = 0.10f), 47f * u, c, style = Stroke(2.6f * u))
            if (b.frac > 0f) drawArc(faint.copy(alpha = 0.55f), -90f, 360f * b.frac, false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - 47f * u, c.y - 47f * u),
                size = androidx.compose.ui.geometry.Size(94f * u, 94f * u), style = Stroke(2.6f * u, cap = StrokeCap.Round))
        }
        withTransform({ translate(c.x, c.y); scale(u, u, androidx.compose.ui.geometry.Offset.Zero) }) {
            if (b.done) {
                drawPath(outer, b.tier.base)
                if (b.shape == BadgeShape.Burst) drawPath(outer, b.tier.base, style = Stroke(4f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawPath(inner, Color.White.copy(alpha = 0.5f), style = Stroke(1.4f))
                drawPath(glyph, b.tier.ink, style = Stroke(2.6f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            } else {
                drawPath(outer, faint.copy(alpha = 0.07f))
                drawPath(inner, faint.copy(alpha = 0.10f), style = Stroke(1.4f))
                drawPath(glyph, faint.copy(alpha = 0.38f), style = Stroke(2.6f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, celebrate: Boolean, onClick: () -> Unit) {
    // a newly earned badge pops once the first time you see it
    val pop = remember { androidx.compose.animation.core.Animatable(if (celebrate) 0.6f else 1f) }
    LaunchedEffect(celebrate) { if (celebrate) pop.animateTo(1f, androidx.compose.animation.core.spring(0.45f, 320f)) }
    Column(
        Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.92f, haptic = Haptic.Tick, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgeArt(b, Modifier.size(92.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Text(b.title, color = if (b.done) Primary else Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(if (b.done) b.desc else b.progressText, color = Secondary, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Big view of one badge after a tap. */
@Composable
private fun BadgeDetail(b: Badge, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    HideTabBarWhile(true)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).bouncyClick(pressedScale = 1f, haptic = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp).widthIn(max = 340.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Card).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BadgeArt(b, Modifier.size(150.dp))
            Text(b.title, color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 14.dp))
            Text(b.desc, color = Secondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (b.done) "Earned · ${b.tier.name}" else b.progressText,
                color = if (b.done) b.tier.base else Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

// ---------------- Popups while reading ----------------

private class Celebration(val title: String, val sub: String, val badge: Badge?)

/**
 * Drops a little pill from the top of the page when you hit today's goal or earn a badge while reading.
 * Follows the reading theme: light pill on light pages, dark pill on dark ones. Several at once queue up.
 */
@Composable
fun Celebrations(theme: ReaderTheme, modifier: Modifier) {
    val p = rememberProgress()
    val badges = remember(p) { badgesOf(p) }
    val queue = remember { androidx.compose.runtime.mutableStateListOf<Celebration>() }
    var known by remember { mutableStateOf<Set<String>?>(null) }
    var goalWas by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(badges) {
        val reached = p.todayMin >= p.goalMin
        if (goalWas == false && reached) {
            queue.add(Celebration("Daily goal reached",
                "${p.todayMin} min today" + if (p.streak > 1) " · ${p.streak} day streak" else "", null))
        }
        goalWas = reached
        val done = badges.filter { it.done }.map { it.title }.toSet()
        known?.let { k -> badges.filter { it.done && it.title !in k }.forEach { queue.add(Celebration("Badge earned", "${it.title} · ${it.tier.name}", it)) } }
        known = done
    }

    val haptics = rememberHaptics()
    var current by remember { mutableStateOf<Celebration?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.snapshotFlow { queue.size }.first { it > 0 }
            current = queue.removeAt(0)
            visible = true
            kotlinx.coroutines.delay(120); haptics.play(Haptic.Confirm)
            kotlinx.coroutines.delay(2600)
            visible = false
            kotlinx.coroutines.delay(450)
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(0.58f, 360f)) { -it * 2 } +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.6f, 380f), initialScale = 0.85f) +
            androidx.compose.animation.fadeIn(tween(160)),
        exit = androidx.compose.animation.slideOutVertically(tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { -it * 2 } +
            androidx.compose.animation.scaleOut(tween(300), targetScale = 0.9f) + androidx.compose.animation.fadeOut(tween(260)),
    ) {
        val c = current ?: return@AnimatedVisibility
        val light = theme.light
        val bg = if (light) Color.White.copy(alpha = 0.97f) else Color(0xFF1C1C1E).copy(alpha = 0.95f)
        val fg = if (light) Color(0xFF1C1C1E) else Color.White
        val edge = if (light) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)
        val tint = c.badge?.tier?.base ?: Color(0xFFF2C24B)
        // icon spins and pops in, with a soft burst of its colour behind it
        val pop = remember(c) { androidx.compose.animation.core.Animatable(0.3f) }
        val spin = remember(c) { androidx.compose.animation.core.Animatable(-25f) }
        val glow = remember(c) { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(c) {
            kotlinx.coroutines.delay(110)
            launch { pop.animateTo(1f, androidx.compose.animation.core.spring(0.42f, 300f)) }
            launch { spin.animateTo(0f, androidx.compose.animation.core.spring(0.5f, 260f)) }
            glow.animateTo(1f, tween(850, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
        Row(
            Modifier.shadow(18.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(50)).background(bg)
                .border(0.8.dp, edge, RoundedCornerShape(50))
                .padding(start = 8.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp).graphicsLayer {
                    val g = glow.value; scaleX = 0.6f + g; scaleY = 0.6f + g; alpha = (1f - g) * 0.7f
                }.clip(CircleShape).background(tint))
                val iconMod = Modifier.size(48.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = spin.value }
                if (c.badge != null) BadgeArt(c.badge, iconMod)
                else Canvas(iconMod) {
                    val u = size.minDimension / 100f
                    drawCircle(tint, 34f * u, center, style = Stroke(8f * u))
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x - 14f * u, center.y + 1f * u); lineTo(center.x - 4f * u, center.y + 11f * u); lineTo(center.x + 15f * u, center.y - 10f * u)
                    }
                    drawPath(path, tint, style = Stroke(7f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(c.title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter)
                Text(c.sub, color = fg.copy(alpha = 0.62f), fontSize = 12.sp, fontFamily = Inter)
            }
        }
    }
}
