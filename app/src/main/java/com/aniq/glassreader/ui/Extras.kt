package com.aniq.glassreader.ui

import android.widget.Magnifier
import androidx.compose.foundation.border
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aniq.glassreader.data.Store
import kotlinx.coroutines.delay

// ---------------- Reading time ----------------

/** "42m", "3h 05m", "120h". */
fun formatReadTime(ms: Long): String {
    val mins = ms / 60_000
    val h = mins / 60
    val m = mins % 60
    return when {
        h == 0L -> "${m}m"
        h >= 100 -> "${h}h"
        else -> "${h}h ${m.toString().padStart(2, '0')}m"
    }
}

/**
 * Counts time spent reading [bookId] while the reader is on screen and the app is in front.
 * Only counts while the reader is actually being used: if nothing has changed for 5 minutes
 * (phone left open on a page), the clock stops until the next page turn.
 */
@Composable
fun ReadingClock(bookId: String, activity: Any?) {
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activity) { lastActivity = System.currentTimeMillis() }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { resumed = true; lastActivity = System.currentTimeMillis() }
            if (e == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); Store.refreshWidgets() }
    }
    LaunchedEffect(bookId, resumed) {
        if (!resumed) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (true) {
            delay(15_000)
            val now = System.currentTimeMillis()
            if (now - lastActivity < 5 * 60_000) Store.addReadTime(bookId, now - last)
            last = now
        }
    }
}

// ---------------- Clear liquid glass for cards inside scrolling content ----------------

/**
 * Clear liquid glass that works inside a scrolling list: it re-draws the (already blurred)
 * wallpaper that sits behind the card, lined up with the screen, then adds a whisper of
 * frost, a specular sheen and a light-catching rim. No colour tint.
 */
fun Modifier.liquidGlass(shape: Shape, radiusGlow: Boolean = true): Modifier = composed {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var root by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val dark = UiMode.dark
    this
        .onGloballyPositioned { c: LayoutCoordinates ->
            pos = c.positionInRoot()
            val r = c.findRootCoordinates().size
            root = androidx.compose.ui.geometry.Size(r.width.toFloat(), r.height.toFloat())
        }
        .clip(shape)
        .drawBehind {
            val blurred = Wall.blurred
            if (blurred != null && root.width > 0f) {
                translate(-pos.x, -pos.y) {
                    val iw = blurred.width.toFloat(); val ih = blurred.height.toFloat()
                    val scale = maxOf(root.width / iw, root.height / ih)
                    val sw = root.width / scale; val sh = root.height / scale
                    drawImage(
                        blurred,
                        srcOffset = androidx.compose.ui.unit.IntOffset(((iw - sw) / 2).toInt(), ((ih - sh) / 2).toInt()),
                        srcSize = androidx.compose.ui.unit.IntSize(sw.toInt(), sh.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(root.width.toInt(), root.height.toInt()),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    )
                }
                // the same wash the page backdrop gets, but lighter, so the glass reads brighter than the wall
                drawRect((if (dark) Color.Black else Color.White).copy(alpha = Wall.dim * 0.55f))
            }
            // frost
            drawRect(if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.45f))
            // specular sheen across the top, fading out by the middle
            drawRect(Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.10f else 0.5f),
                0.45f to Color.Transparent,
            ))
            // soft glow pooled in the top-left corner, like light hitting a lens
            if (radiusGlow) drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.08f else 0.25f), Color.Transparent),
                center = Offset(0f, 0f), radius = size.minDimension * 0.9f,
            ))
            // a faint darker floor so text at the bottom stays legible
            drawRect(Brush.verticalGradient(
                0.6f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (dark) 0.12f else 0.03f),
            ))
        }
        .border(1.dp, glassRim(dark), shape)
}

private fun glassRim(dark: Boolean) = Brush.linearGradient(
    if (dark) listOf(
        Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.20f),
    ) else listOf(Color.White, Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.04f), Color.White.copy(alpha = 0.9f)),
    start = Offset.Zero, end = Offset.Infinite,
)

// ---------------- Reader helpers ----------------

/**
 * Stops the phone's swipe gestures (edge swipe = back) from firing while reading.
 * Android only honours about 200dp of exclusion per edge, so it's placed across the
 * middle of the screen where thumbs swipe to turn pages.
 */
fun Modifier.blockEdgeSwipes(): Modifier = composed {
    val band = with(LocalDensity.current) { 200.dp.toPx() }
    val edge = with(LocalDensity.current) { 40.dp.toPx() }
    this.systemGestureExclusion { c ->
        val h = c.size.height.toFloat(); val w = c.size.width.toFloat()
        val top = ((h - band) / 2).coerceAtLeast(0f)
        // two thin strips, one per edge, so the whole page in between stays untouched
        Rect(0f, top, edge, top + band)
    }.systemGestureExclusion { c ->
        val h = c.size.height.toFloat(); val w = c.size.width.toFloat()
        val top = ((h - band) / 2).coerceAtLeast(0f)
        Rect(w - edge, top, w, top + band)
    }
}

/** A loupe that floats above the finger while selecting text, so you can see what you're picking. */
class TextLoupe(private val view: android.view.View, private val lift: Float) {
    private var m: Magnifier? = null

    /** [x], [y] in window/root coordinates of the Compose view. */
    fun show(x: Float, y: Float) {
        val mag = m ?: Magnifier.Builder(view)
            .setSize((view.resources.displayMetrics.density * 220).toInt(), (view.resources.displayMetrics.density * 72).toInt())
            .setInitialZoom(1.6f)
            .setCornerRadius(view.resources.displayMetrics.density * 36)
            .setElevation(view.resources.displayMetrics.density * 6)
            .setDefaultSourceToMagnifierOffset(0, -lift.toInt())
            .build().also { m = it }
        mag.show(x, y)
    }

    fun hide() { m?.dismiss() }
}

@Composable
fun rememberLoupe(): TextLoupe {
    val view = LocalView.current
    val lift = with(LocalDensity.current) { 80.dp.toPx() }
    val loupe = remember(view) { TextLoupe(view, lift) }
    DisposableEffect(loupe) { onDispose { loupe.hide() } }
    return loupe
}
