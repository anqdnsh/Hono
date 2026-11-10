package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/** Where the text you selected (or the highlight you tapped) sits on screen, so the side tools can sit next to it. */
object ToolAnchor { var y by mutableFloatStateOf(Float.NaN) }

/** The colour part of a highlight toolbar. [selected] is set when changing an existing highlight. */
class ToolColors(val primary: Int, val selected: Int? = null, val onPick: (Int) -> Unit)

internal class SideToolsCtx(val theme: ReaderTheme, val haze: HazeState, val left: Boolean, val clarity: Float, val shown: Boolean) {
    var count = 0
}

internal val LocalSideTools = staticCompositionLocalOf<SideToolsCtx?> { null }

/**
 * Highlight tools: either the glass bar along the bottom, or (Settings › Highlight tools)
 * a column of clear liquid-glass circles at the side, next to what you selected.
 * [BarIcon]s inside [content] turn into circles on their own.
 */
@Composable
internal fun Toolbar(
    visible: Boolean,
    haze: HazeState,
    theme: ReaderTheme,
    bottomInset: androidx.compose.ui.unit.Dp,
    colors: ToolColors? = null,
    content: @Composable () -> Unit,
) {
    val settings by Store.settings.collectAsState()
    if (!settings.sideTools) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(140)) + scaleIn(spring(0.62f, 600f), initialScale = 0.82f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 22.dp),
            ) {
                Row(
                    Modifier
                        .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (colors != null) {
                        HighlightColors.forEachIndexed { i, col -> ColorDot(col, colors.selected == i) { colors.onPick(i) } }
                        Divider(theme)
                    }
                    content()
                }
            }
        }
        return
    }
    SideToolbar(visible, haze, theme, settings.toolsLeft, settings.glassClarity, colors, content)
}

@Composable
private fun SideToolbar(
    visible: Boolean,
    haze: HazeState,
    theme: ReaderTheme,
    left: Boolean,
    clarity: Float,
    colors: ToolColors?,
    content: @Composable () -> Unit,
) {
    // stay on screen while the circles slide away
    var keep by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) keep = true else { delay(320); keep = false } }
    if (!visible && !keep) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = constraints.maxHeight
        var colH by remember { mutableIntStateOf(0) }
        val margin = with(density) { 70.dp.roundToPx() }
        val anchor = ToolAnchor.y
        val target = if (anchor.isNaN() || colH == 0) (maxH - colH) / 2
            else (anchor - colH / 2f).toInt().coerceIn(margin, (maxH - colH - margin).coerceAtLeast(margin))
        // follows the selection up and down as you adjust it
        val y by animateIntAsState(target, spring(0.82f, 380f), label = "toolsY")
        val ctx = SideToolsCtx(theme, haze, left, clarity, visible)
        Column(
            Modifier
                .align(if (left) Alignment.TopStart else Alignment.TopEnd)
                .offset { IntOffset(0, if (colH == 0) target else y) }
                .padding(horizontal = 10.dp)
                .onSizeChanged { colH = it.height },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = if (left) Alignment.Start else Alignment.End,
        ) {
            CompositionLocalProvider(LocalSideTools provides ctx) {
                if (colors != null) ColorCircle(ctx, colors)
                content()
            }
        }
    }
}

/**
 * One clear liquid-glass circle: barely frosted, bright rim, a reflection across the top.
 * Slides in from the edge after the ones before it, and squishes like jelly when pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiquidCircle(
    ctx: SideToolsCtx,
    index: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val p = remember { Animatable(0f) }
    LaunchedEffect(ctx.shown) {
        if (ctx.shown) { delay(index * 32L); p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 480f)) }
        else { delay((4 - index).coerceAtLeast(0) * 18L); p.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val squish by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 620f), label = "squish")
    val t = ctx.theme
    val clear = ctx.clarity.coerceIn(0f, 1f)
    Box(
        Modifier
            .size(46.dp)
            .graphicsLayer {
                val v = p.value
                alpha = v.coerceIn(0f, 1f)
                translationX = (if (ctx.left) -1f else 1f) * 44.dp.toPx() * (1f - v)
                val base = 0.6f + 0.4f * v
                scaleX = base * (1f + 0.09f * squish)
                scaleY = base * (1f - 0.11f * squish)
            }
            .combinedClickable(
                interactionSource = src, indication = null,
                onLongClick = onLongClick?.let { l -> { haptics.play(Haptic.LongPress); l() } },
                onClick = { haptics.play(Haptic.Tap); onClick() },
            )
            .glass(
                ctx.haze, CircleShape,
                tint = t.glassTint.copy(alpha = t.glassTint.alpha * (1f - clear) * 0.9f + 0.03f),
                base = t.bg,
                blur = (1.5f + 16f * (1f - clear)).dp,
                light = t.light, floating = true,
            )
            .drawWithContent {
                drawContent()
                // the reflection: a soft bright lens across the top
                drawOval(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (t.light) 0.75f else 0.38f), Color.White.copy(alpha = 0f)),
                        startY = size.height * 0.06f, endY = size.height * 0.34f,
                    ),
                    topLeft = Offset(size.width * 0.2f, size.height * 0.06f),
                    size = Size(size.width * 0.6f, size.height * 0.28f),
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Tap = highlight in your last colour. Hold = all five colours flow out beside it. */
@Composable
private fun ColorCircle(ctx: SideToolsCtx, colors: ToolColors) {
    val index = remember { ctx.count++ }
    var fan by remember { mutableStateOf(false) }
    LaunchedEffect(ctx.shown) { if (!ctx.shown) fan = false }
    val shown = colors.selected ?: colors.primary
    val circle: @Composable () -> Unit = {
        LiquidCircle(ctx, index,
            onClick = { if (colors.selected != null) fan = !fan else colors.onPick(colors.primary) },
            onLongClick = { fan = true },
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(HighlightColors[shown.coerceIn(0, HighlightColors.lastIndex)])
                .border(0.8.dp, Color.White.copy(alpha = 0.7f), CircleShape))
        }
    }
    val fanRow: @Composable () -> Unit = {
        AnimatedVisibility(
            fan,
            enter = fadeIn(tween(120)) + expandHorizontally(spring(0.7f, 480f), expandFrom = if (ctx.left) Alignment.Start else Alignment.End),
            exit = fadeOut(tween(100)) + shrinkHorizontally(spring(1f, 700f), shrinkTowards = if (ctx.left) Alignment.Start else Alignment.End),
        ) {
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .glass(ctx.haze, RoundedCornerShape(50), ctx.theme.glassTint.copy(alpha = ctx.theme.glassTint.alpha * (1f - ctx.clarity) * 0.9f + 0.05f),
                        ctx.theme.bg, blur = (2f + 14f * (1f - ctx.clarity)).dp, light = ctx.theme.light, floating = true)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightColors.forEachIndexed { i, c -> ColorDot(c, i == colors.selected) { fan = false; colors.onPick(i) } }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ctx.left) { circle(); fanRow() } else { fanRow(); circle() }
    }
}

// ---------------- Undo ----------------

/** The highlight you just made or deleted, for three seconds, so it can be taken back. */
object UndoState {
    var pending by mutableStateOf<Highlight?>(null)
    /** true: [pending] was just made; false: it was just deleted. */
    var added by mutableStateOf(false)
    /** Highlights a new one merged into, put back if the new one is undone. */
    private var replaced: List<Highlight> = emptyList()
    var at by mutableLongStateOf(0L)

    fun offer(h: Highlight?) {
        if (h == null) return
        pending = h; added = false; replaced = emptyList(); at = System.currentTimeMillis()
    }

    fun offerAdded(h: Highlight, merged: List<Highlight> = emptyList()) {
        pending = h; added = true; replaced = merged; at = System.currentTimeMillis()
    }

    fun undo() {
        val h = pending ?: return
        if (added) { Store.removeHighlight(h.id); replaced.forEach { Store.restoreHighlight(it) } }
        else Store.restoreHighlight(h)
        pending = null
    }

    fun dismiss() { pending = null }
}

/**
 * After deleting a highlight: one glass Undo circle where the tools were (side mode), or a glass
 * pill at the bottom. A ring counts down the five seconds, then it fades away.
 */
@Composable
internal fun UndoBubble(haze: HazeState, theme: ReaderTheme, bottomInset: androidx.compose.ui.unit.Dp) {
    val settings by Store.settings.collectAsState()
    val h = UndoState.pending
    val ring = remember { Animatable(1f) }
    LaunchedEffect(UndoState.at) {
        if (UndoState.pending == null) return@LaunchedEffect
        ring.snapTo(1f)
        ring.animateTo(0f, tween(3000, easing = LinearEasing))
        UndoState.pending = null
    }
    var last by remember { mutableStateOf<Highlight?>(null) }
    if (h != null) last = h
    val haptics = rememberHaptics()
    val undo = {
        UndoState.undo()
        haptics.play(Haptic.Confirm)
    }
    val ringMod = Modifier.drawWithContent {
        drawContent()
        val stroke = 2.dp.toPx()
        drawArc(
            theme.text.copy(alpha = 0.85f), startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
            topLeft = Offset(stroke, stroke), size = Size(size.width - stroke * 2, size.height - stroke * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    if (settings.sideTools) {
        val ctx = SideToolsCtx(theme, haze, settings.toolsLeft, settings.glassClarity, h != null)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxH = constraints.maxHeight
            val anchor = ToolAnchor.y
            val size = with(LocalDensity.current) { 52.dp.roundToPx() }
            val y = if (anchor.isNaN()) (maxH - size) / 2 else (anchor - size / 2f).toInt().coerceIn(size, maxH - size * 2)
            var keep by remember { mutableStateOf(false) }
            LaunchedEffect(h != null) { if (h != null) keep = true else { delay(320); keep = false } }
            if (h != null || keep) Column(
                Modifier.align(if (settings.toolsLeft) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, y) }.padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(ringMod) {
                    LiquidCircle(ctx, 0, onClick = undo) {
                        Icon(Line.Undo, "Undo", tint = theme.text, modifier = Modifier.size(21.dp))
                    }
                }
                Text("Undo", color = theme.sub, fontFamily = Inter, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                h != null,
                enter = fadeIn(tween(140)) + scaleIn(spring(0.62f, 600f), initialScale = 0.82f),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 22.dp),
            ) {
                Row(
                    Modifier
                        .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (UndoState.added) last?.let {
                        Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(HighlightColors[it.color.coerceIn(0, HighlightColors.lastIndex)]))
                    }
                    Text(if (UndoState.added) "Saved to Highlights" else "Highlight removed", color = theme.text, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Box(
                        ringMod.size(40.dp).clip(CircleShape).bouncyClick(pressedScale = 0.85f, haptic = null, onClick = undo),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Line.Undo, "Undo", tint = theme.text, modifier = Modifier.size(19.dp)) }
                }
            }
        }
    }
}
