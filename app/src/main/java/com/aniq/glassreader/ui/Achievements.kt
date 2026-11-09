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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

class Badge(val title: String, val desc: String, val have: Float, val need: Float) {
    val done get() = have >= need
    val frac get() = (have / need).coerceIn(0f, 1f)
}

fun badgesOf(p: Progress) = listOf(
    Badge("First page", "Read for a minute", p.totalHours * 60f, 1f),
    Badge("Goal getter", "Hit your daily goal", p.goalDays.toFloat(), 1f),
    Badge("On a roll", "3-day streak", p.bestStreak.toFloat(), 3f),
    Badge("Week strong", "7-day streak", p.bestStreak.toFloat(), 7f),
    Badge("Fortnight", "14-day streak", p.bestStreak.toFloat(), 14f),
    Badge("Habit formed", "30-day streak", p.bestStreak.toFloat(), 30f),
    Badge("Marathon", "60 minutes in one day", p.bestDayMin.toFloat(), 60f),
    Badge("Ten hours", "10 hours of reading", p.totalHours, 10f),
    Badge("Fifty hours", "50 hours of reading", p.totalHours, 50f),
    Badge("Highlighter", "Save a highlight", p.highlights.toFloat(), 1f),
    Badge("Collector", "25 highlights", p.highlights.toFloat(), 25f),
    Badge("Curator", "100 highlights", p.highlights.toFloat(), 100f),
    Badge("Thinker", "Write 5 notes", p.notes.toFloat(), 5f),
    Badge("Finisher", "Finish a book", p.finished.toFloat(), 1f),
    Badge("Bookworm", "Finish 5 books", p.finished.toFloat(), 5f),
    Badge("Goal machine", "Hit your goal 50 times", p.goalDays.toFloat(), 50f),
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
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
        items(badges, key = { it.title }) { BadgeTile(it) }
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

@Composable
private fun BadgeTile(b: Badge) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().liquidGlass(shape).padding(horizontal = 10.dp, vertical = 14.dp)
            .graphicsLayer { alpha = if (b.done) 1f else 0.55f },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 3.dp.toPx()
                drawArc(Primary.copy(alpha = 0.10f), 0f, 360f, false, style = Stroke(w))
                drawArc(if (b.done) Gold else Primary.copy(alpha = 0.6f), -90f, 360f * b.frac, false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Icon(Line.Trophy, null, tint = if (b.done) Gold else Secondary, modifier = Modifier.size(22.dp))
        }
        Text(b.title, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        Text(b.desc, color = Secondary, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}
