package com.aniq.glassreader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aniq.glassreader.R
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// ---------------- Type ----------------

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// ---------------- Palette (monochrome) ----------------

val AppBg = Color(0xFF000000)
val Card = Color(0xFF141416)
val Hairline = Color.White.copy(alpha = 0.07f)
val Primary = Color(0xFFF5F5F7)
val Secondary = Color(0xFF8E8E93)
val Tertiary = Color(0xFF5A5A5F)

val HighlightColors = listOf(
    Color(0xFFFFD60A), // yellow
    Color(0xFF30D158), // green
    Color(0xFF64D2FF), // blue
    Color(0xFFFF6482), // pink
    Color(0xFFBF5AF2), // purple
)
val HighlightColorInts: List<Int> = HighlightColors.map { it.toArgb() }

class ReaderTheme(
    val name: String,
    val bg: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val light: Boolean,
    val hlAlpha: Float,
) {
    val glassTint: Color get() = if (light) Color.White.copy(alpha = 0.62f) else Color(0xFF1C1C1E).copy(alpha = 0.55f)
}

val ReaderThemes = listOf(
    ReaderTheme("White", Color(0xFFFFFFFF), Color(0xFF1C1C1E), Color(0xFF8A8A8E), Color(0xFF1C1C1E), true, 0.42f),
    ReaderTheme("Paper", Color(0xFFF6F1E7), Color(0xFF2B2722), Color(0xFF8C8478), Color(0xFF2B2722), true, 0.42f),
    ReaderTheme("Sepia", Color(0xFFEEDFC4), Color(0xFF3A2F22), Color(0xFF8A7A63), Color(0xFF3A2F22), true, 0.42f),
    ReaderTheme("Sage", Color(0xFFE6EBDD), Color(0xFF263024), Color(0xFF7D8A78), Color(0xFF263024), true, 0.42f),
    ReaderTheme("Graphite", Color(0xFF1C1C1E), Color(0xFFE5E5EA), Color(0xFF8E8E93), Color(0xFFE5E5EA), false, 0.30f),
    ReaderTheme("Black", Color(0xFF000000), Color(0xFFC9C9CE), Color(0xFF6E6E73), Color(0xFFD1D1D6), false, 0.28f),
)

val MonoTheme = ReaderTheme("Mono", Color(0xFF000000), Color(0xFFBDBDBD), Color(0xFF6A6A6A), Color(0xFFE0E0E0), false, 0.30f)

val WallTheme = ReaderTheme("Wallpaper", Color(0xFF0E0E10), Color(0xFFF0F0F2), Color(0xFFA0A0A6), Color(0xFFF0F0F2), false, 0.34f)

fun themeFor(s: com.aniq.glassreader.data.ReaderSettings): ReaderTheme = when {
    s.mono -> MonoTheme
    s.wallInReader && Wall.blurred != null -> WallTheme
    else -> ReaderThemes[s.theme.coerceIn(0, ReaderThemes.lastIndex)]
}

/** Reader page background: a blurred, dimmed wallpaper when that option is on. */
fun Modifier.readerBackdrop(theme: ReaderTheme): Modifier = if (theme !== WallTheme) this else this.drawBehind {
    Wall.blurred?.let { drawCover(it) }
    drawRect(Color.Black.copy(alpha = (Wall.dim + 0.15f).coerceAtMost(0.85f)))
}

// ---------------- Paper grain ----------------

private val grain: androidx.compose.ui.graphics.ImageBitmap by lazy {
    val size = 256
    val px = IntArray(size * size)
    val r = java.util.Random(11)
    for (i in px.indices) {
        val v = r.nextInt(256)
        val a = (r.nextFloat().let { it * it * it } * 90).toInt()
        px[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    // a few faint fibres
    repeat(140) {
        var x = r.nextInt(size).toFloat(); var y = r.nextInt(size).toFloat()
        val ang = r.nextFloat() * 6.283f
        repeat(10 + r.nextInt(18)) {
            val ix = ((x.toInt() % size) + size) % size; val iy = ((y.toInt() % size) + size) % size
            px[iy * size + ix] = (28 shl 24) or 0x404040
            x += kotlin.math.cos(ang); y += kotlin.math.sin(ang)
        }
    }
    val b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    b.setPixels(px, 0, size, 0, 0, size, size)
    b.asImageBitmap()
}

/** Optional paper texture: fine grain + fibres + a soft vignette, drawn under the text. */
fun Modifier.paper(enabled: Boolean, light: Boolean, amount: Float = 0.6f): Modifier = if (!enabled || amount <= 0f) this else this.drawBehind {
    drawRect(
        androidx.compose.ui.graphics.ShaderBrush(
            androidx.compose.ui.graphics.ImageShader(grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)
        ),
        alpha = (if (light) 0.9f else 0.36f) * amount,
        blendMode = if (light) androidx.compose.ui.graphics.BlendMode.Multiply else androidx.compose.ui.graphics.BlendMode.Screen,
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = (if (light) 0.11f else 0.35f) * amount)),
            center = Offset(size.width / 2, size.height / 2),
            radius = maxOf(size.width, size.height) * 0.75f,
        )
    )
}

/** Black & white mode: drains all colour from whatever is drawn inside. */
fun Modifier.monochrome(enabled: Boolean): Modifier = if (!enabled) this else this.graphicsLayer {
    renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
    ).asComposeRenderEffect()
}

// ---------------- Liquid glass ----------------

private fun rim(light: Boolean) = Brush.linearGradient(
    if (light) listOf(Color.White, Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.03f), Color.White.copy(alpha = 0.8f))
    else listOf(
        Color.White.copy(alpha = 0.34f),
        Color.White.copy(alpha = 0.06f),
        Color.White.copy(alpha = 0.02f),
        Color.White.copy(alpha = 0.14f),
    ),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/**
 * Apple-Music-style liquid glass: heavy backdrop blur with a smoky tint,
 * a soft specular sheen on the upper half, an inner glow, and a light-catching rim
 * that's brightest at the top-left edge.
 */
fun Modifier.glass(
    state: HazeState,
    shape: Shape,
    tint: Color = Color(0xFF1C1C1E).copy(alpha = 0.52f),
    base: Color = AppBg,
    blur: Dp = 26.dp,
    light: Boolean = false,
    floating: Boolean = false,
): Modifier = this
    .clip(shape)
    .hazeEffect(
        state = state,
        style = HazeStyle(backgroundColor = base, tint = HazeTint(tint), blurRadius = blur, noiseFactor = 0.015f),
    )
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (light) 0.45f else 0.075f),
                0.5f to Color.Transparent,
                1f to Color.White.copy(alpha = if (light) 0.0f else 0.02f),
            )
        )
    }
    .border(0.8.dp, rim(light), shape)

/** Soft, progressive blur that fades content out under the status bar (iOS 26 style). */
fun Modifier.topFade(state: HazeState, base: Color = AppBg): Modifier = this
    .hazeEffect(
        state = state,
        style = HazeStyle(backgroundColor = base, tint = HazeTint(base.copy(alpha = 0.35f)), blurRadius = 18.dp, noiseFactor = 0f),
    ) {
        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0f to base.copy(alpha = 0.75f), 0.6f to base.copy(alpha = 0.2f), 1f to Color.Transparent))
    }

fun Modifier.bottomFade(state: HazeState, base: Color = AppBg): Modifier = this
    .hazeEffect(
        state = state,
        style = HazeStyle(backgroundColor = base, tint = HazeTint(base.copy(alpha = 0.35f)), blurRadius = 18.dp, noiseFactor = 0f),
    ) {
        progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
    }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.5f to base.copy(alpha = 0.3f), 1f to base.copy(alpha = 0.85f)))
    }

/** Flat card used inside scrolling content. */
fun Modifier.card(shape: Shape = RoundedCornerShape(22.dp)): Modifier = this
    .clip(shape)
    .background(Card)
    .border(0.6.dp, Hairline, shape)

/** The user's wallpaper (if any), mirrored into Compose state so draw code can read it. */
object Wall {
    var image by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var blurred by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var dim by mutableStateOf(0.45f)
}

/** Draws [img] scaled to fill the area (centre-crop), fixed to the viewport. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCover(img: androidx.compose.ui.graphics.ImageBitmap) {
    val iw = img.width.toFloat(); val ih = img.height.toFloat()
    val scale = maxOf(size.width / iw, size.height / ih)
    val sw = size.width / scale; val sh = size.height / scale
    drawImage(
        img,
        srcOffset = androidx.compose.ui.unit.IntOffset(((iw - sw) / 2).toInt(), ((ih - sh) / 2).toInt()),
        srcSize = androidx.compose.ui.unit.IntSize(sw.toInt(), sh.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
    )
}

/** Black → graphite backdrop with a soft grey glow, or the user's own wallpaper. */
fun Modifier.glassBackdrop(): Modifier = this.drawBehind {
    val w = size.width; val h = size.height
    val wall = Wall.image
    if (wall != null) {
        drawCover(wall)
        drawRect(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = Wall.dim * 0.8f),
            0.5f to Color.Black.copy(alpha = Wall.dim),
            1f to Color.Black.copy(alpha = minOf(1f, Wall.dim + 0.25f)),
        ))
        return@drawBehind
    }
    // near-black with the faintest graphite lift at the top, like Apple Music in dark mode
    drawRect(Color.Black)
    drawRect(Brush.verticalGradient(0f to Color(0xFF1A1A1C), 0.35f to Color(0xFF0A0A0B), 0.7f to Color.Black), size = androidx.compose.ui.geometry.Size(w, h * 0.7f))
}

// ---------------- Interaction ----------------

/** Springy press feedback, like iOS. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bouncyClick(
    pressedScale: Float = 0.95f,
    haptic: Haptic? = Haptic.Tap,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val haptics = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val dim by animateFloatAsState(if (pressed) 0.75f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "dim")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = dim }
        .combinedClickable(
            interactionSource = src, indication = null,
            onLongClick = onLongClick?.let { lc -> { haptics.play(Haptic.LongPress); lc() } },
            onClick = { haptic?.let { haptics.play(it) }; onClick() },
        )
}

/** Circular liquid-glass button. */
@Composable
fun GlassCircle(
    icon: ImageVector,
    description: String,
    haze: HazeState,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = Primary,
    glassTint: Color = Color(0xFF1C1C1E).copy(alpha = 0.52f),
    base: Color = AppBg,
    light: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .bouncyClick(pressedScale = 0.88f, onClick = onClick)
            .glass(haze, CircleShape, glassTint, base, light = light, floating = true),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Minimal slider: hairline track that thickens while dragging, small white knob. */
@Composable
fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    fg: Color = Primary,
    onFinished: () -> Unit = {},
) {
    var active by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    var lastStep by remember { mutableStateOf(-1) }
    val rawChange by rememberUpdatedState(onChange)
    val change: (Float) -> Unit = { v ->
        // a soft detent every 1/40th of the track, like a ratcheting dial
        val sp = range.endInclusive - range.start
        val step = if (sp == 0f) 0 else (((v - range.start) / sp) * 40).toInt()
        if (lastStep != -1 && step != lastStep) haptics.play(Haptic.Tick)
        lastStep = step
        rawChange(v)
    }
    val finished by rememberUpdatedState(onFinished)
    val trackH by animateDpAsState(if (active) 8.dp else 5.dp, spring(0.7f, 600f), label = "track")
    val knob by animateDpAsState(if (active) 22.dp else 16.dp, spring(0.55f, 600f), label = "knob")
    val span = range.endInclusive - range.start
    val frac = if (span == 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(range) {
                detectTapGestures(onPress = { pos ->
                    active = true; lastStep = -1; haptics.play(Haptic.GestureStart)
                    change(range.start + span * (pos.x / size.width).coerceIn(0f, 1f))
                    tryAwaitRelease()
                    active = false
                    finished()
                })
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragStart = { active = true },
                    onDragEnd = { active = false; finished() },
                    onDragCancel = { active = false; finished() },
                ) { ch, _ ->
                    ch.consume()
                    change(range.start + span * (ch.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(trackH).clip(CircleShape).background(fg.copy(alpha = 0.16f))) {
            Box(Modifier.fillMaxWidth(frac).height(trackH).background(fg.copy(alpha = 0.85f), RectangleShape))
        }
        val x = with(density) { ((w - knob) * frac).toPx() }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), 0) }
                .size(knob)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** iOS-style switch with a springy knob. */
@Composable
fun GlassSwitch(on: Boolean, fg: Color = Primary) {
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(0.6f, 600f), label = "sw")
    val track by androidx.compose.animation.animateColorAsState(
        if (on) fg.copy(alpha = 0.85f) else fg.copy(alpha = 0.16f), label = "swc")
    Box(
        Modifier.size(width = 50.dp, height = 30.dp).clip(CircleShape).background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = x + 2.dp).size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape)
                .background(if (on && fg.luminanceIsDark()) Color.White else Color.White)
        )
    }
}

private fun Color.luminanceIsDark() = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

fun Modifier.hairline(shape: Shape): Modifier = this.border(0.6.dp, Hairline, shape)
