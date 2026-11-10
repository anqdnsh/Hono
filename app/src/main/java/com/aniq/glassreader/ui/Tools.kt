package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
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

// ---------------- Glossy glass with depth ----------------

/**
 * A soft shadow just outside [shape], a little below it, so glass floats off the page.
 * It's clipped out of the shape itself, so clear glass stays clear in the middle.
 */
fun Modifier.depthShadow(shape: Shape, light: Boolean, radius: Dp = 10.dp): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val r = radius.toPx()
    clipPath(path, ClipOp.Difference) {
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(if (light) 72 else 190, 0, 0, 0)
                maskFilter = android.graphics.BlurMaskFilter(r, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            c.nativeCanvas.save()
            c.nativeCanvas.translate(0f, r * 0.55f)
            c.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            c.nativeCanvas.restore()
        }
    }
}

/**
 * The glossy, domed finish on top of clear glass: a bright reflection across the top, light
 * gathering along the bottom edge, a soft darker curve inside the bottom, a crisp rim and,
 * on circles, a small glint low on the right. Drawn behind the icon, over the glass.
 */
fun Modifier.gloss(shape: Shape, light: Boolean, glint: Boolean = false): Modifier = this.drawBehind {
    val w = size.width; val h = size.height
    val outline = shape.createOutline(size, layoutDirection, this)
    // light gathering at the bottom edge, as if it shines through
    drawRect(Brush.radialGradient(
        listOf(Color.White.copy(alpha = if (light) 0.32f else 0.16f), Color.Transparent),
        center = Offset(w / 2f, h * 1.06f), radius = maxOf(w * 0.55f, h * 0.9f),
    ))
    // the dome: a soft darker curve inside the bottom
    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = if (light) 0.07f else 0.32f)))
    // the reflection across the top
    val rh = h * 0.34f
    val inset = if (w > h * 1.4f) h * 0.45f else w * 0.19f
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (light) 0.92f else 0.58f), Color.White.copy(alpha = if (light) 0.32f else 0.16f), Color.Transparent),
            startY = h * 0.06f, endY = h * 0.06f + rh,
        ),
        topLeft = Offset(inset, h * 0.06f), size = Size(w - inset * 2, rh),
        cornerRadius = CornerRadius(rh / 2f, rh / 2f),
    )
    // crisp rim: brightest on top, softer at the bottom
    drawOutline(outline, Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (light) 1f else 0.8f),
        0.5f to Color.White.copy(alpha = if (light) 0.25f else 0.12f),
        1f to Color.White.copy(alpha = if (light) 0.6f else 0.32f),
    ), style = Stroke(1.2.dp.toPx()))
    if (glint) drawOval(
        Color.White.copy(alpha = if (light) 0.9f else 0.65f),
        topLeft = Offset(w * 0.64f, h * 0.76f), size = Size(w * 0.15f, h * 0.08f),
    )
}

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
                val clear = settings.glassClarity.coerceIn(0f, 1f)
                Row(
                    Modifier
                        .depthShadow(RoundedCornerShape(50), theme.light)
                        .glass(haze, RoundedCornerShape(50), theme.glassTint.copy(alpha = theme.glassTint.alpha * (0.35f + 0.65f * (1f - clear))), theme.bg,
                            blur = (6f + 20f * (1f - clear)).dp, light = theme.light, floating = true)
                        .gloss(RoundedCornerShape(50), theme.light)
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
            .depthShadow(CircleShape, t.light)
            .glass(
                ctx.haze, CircleShape,
                tint = t.glassTint.copy(alpha = t.glassTint.alpha * (1f - clear) * 0.8f + 0.01f),
                base = t.bg,
                blur = (1f + 14f * (1f - clear)).dp,
                light = t.light, floating = true,
            )
            .gloss(CircleShape, t.light, glint = true),
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
                    .depthShadow(RoundedCornerShape(50), ctx.theme.light)
                    .glass(ctx.haze, RoundedCornerShape(50), ctx.theme.glassTint.copy(alpha = ctx.theme.glassTint.alpha * (1f - ctx.clarity) * 0.9f + 0.05f),
                        ctx.theme.bg, blur = (2f + 14f * (1f - ctx.clarity)).dp, light = ctx.theme.light, floating = true)
                    .gloss(RoundedCornerShape(50), ctx.theme.light)
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
 * Undo, for three seconds after you make or delete a highlight. It waits until the highlight
 * tools have left so the two never overlap, and steps aside as soon as you select new text.
 * The arrow, circle and countdown ring are one piece, so they leave together.
 */
@Composable
internal fun UndoBubble(haze: HazeState, theme: ReaderTheme, bottomInset: androidx.compose.ui.unit.Dp, toolsShowing: Boolean) {
    val settings by Store.settings.collectAsState()
    val ring = remember { Animatable(1f) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(UndoState.at) {
        ready = false
        if (UndoState.pending == null) return@LaunchedEffect
        ring.snapTo(1f)
        delay(240)            // let the tools finish leaving first
        ready = true
        coroutineScope {
            launch { ring.animateTo(0f, tween(3000, easing = LinearEasing)) }
            delay(2800)       // start leaving just as the ring closes, so nothing lingers
            UndoState.dismiss()
        }
    }
    // a new selection takes the spot straight away
    LaunchedEffect(toolsShowing) { if (toolsShowing && ready) UndoState.dismiss() }
    val showing = UndoState.pending != null && ready && !toolsShowing
    var last by remember { mutableStateOf<Highlight?>(null) }
    UndoState.pending?.let { last = it }
    var lastAdded by remember { mutableStateOf(false) }
    if (UndoState.pending != null) lastAdded = UndoState.added
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
            topLeft = Offset(stroke * 1.5f, stroke * 1.5f), size = Size(size.width - stroke * 3, size.height - stroke * 3),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    if (settings.sideTools) {
        val ctx = SideToolsCtx(theme, haze, settings.toolsLeft, settings.glassClarity, showing)
        var keep by remember { mutableStateOf(false) }
        LaunchedEffect(showing) { if (showing) keep = true else { delay(260); keep = false } }
        if (!showing && !keep) return
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxH = constraints.maxHeight
            val anchor = ToolAnchor.y
            val size = with(LocalDensity.current) { 52.dp.roundToPx() }
            val y = if (anchor.isNaN()) (maxH - size) / 2 else (anchor - size / 2f).toInt().coerceIn(size, maxH - size * 2)
            Column(
                Modifier.align(if (settings.toolsLeft) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, y) }.padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // index 4: no delay on the way out
                LiquidCircle(ctx, 4, onClick = undo) {
                    Box(Modifier.fillMaxSize().then(ringMod), contentAlignment = Alignment.Center) {
                        Icon(Line.Undo, "Undo", tint = theme.text, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                showing,
                enter = fadeIn(tween(140)) + scaleIn(spring(0.62f, 600f), initialScale = 0.82f),
                exit = fadeOut(tween(150)) + scaleOut(spring(1f, 900f), targetScale = 0.86f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 22.dp),
            ) {
                Row(
                    Modifier
                        .depthShadow(RoundedCornerShape(50), theme.light)
                        .glass(haze, RoundedCornerShape(50), theme.glassTint, theme.bg, light = theme.light, floating = true)
                        .gloss(RoundedCornerShape(50), theme.light)
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lastAdded) last?.let {
                        Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(HighlightColors[it.color.coerceIn(0, HighlightColors.lastIndex)]))
                    }
                    Text(if (lastAdded) "Saved to Highlights" else "Highlight removed", color = theme.text, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).bouncyClick(pressedScale = 0.85f, haptic = null, onClick = undo).then(ringMod),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Line.Undo, "Undo", tint = theme.text, modifier = Modifier.size(19.dp)) }
                }
            }
        }
    }
}
