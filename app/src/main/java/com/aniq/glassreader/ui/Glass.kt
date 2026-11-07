package com.aniq.glassreader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// ---------------- Palette ----------------

val AppBg = Color(0xFF0B0D1A)

val HighlightColors = listOf(
    Color(0xFFFFD54F), // yellow
    Color(0xFF7FE3A0), // green
    Color(0xFF7CC8FF), // blue
    Color(0xFFFF8FB8), // pink
    Color(0xFFC3A1FF), // purple
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
    val glassTint: Color get() = if (light) Color.White.copy(alpha = 0.55f) else bg.copy(alpha = 0.55f)
}

val ReaderThemes = listOf(
    ReaderTheme("Paper", Color(0xFFF8F5EE), Color(0xFF1F1F22), Color(0xFF6E6E73), Color(0xFF2F6BFF), true, 0.45f),
    ReaderTheme("Sepia", Color(0xFFF3E6CC), Color(0xFF3B2E1F), Color(0xFF7B6A54), Color(0xFFB5651D), true, 0.45f),
    ReaderTheme("Night", Color(0xFF1B1C22), Color(0xFFDCDCE2), Color(0xFF8E8E99), Color(0xFF8AB4FF), false, 0.33f),
    ReaderTheme("Black", Color(0xFF000000), Color(0xFFC7C7CC), Color(0xFF7C7C82), Color(0xFF8AB4FF), false, 0.30f),
)

// ---------------- Glass modifiers ----------------

private fun rim(light: Boolean) = Brush.linearGradient(
    if (light) listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.06f), Color.White.copy(alpha = 0.7f))
    else listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.26f))
)

/**
 * Real backdrop-blur "liquid glass": blurs whatever is drawn under it (via Haze),
 * adds a tint, a specular sheen across the top and a light-catching rim.
 */
fun Modifier.glass(
    state: HazeState,
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.08f),
    base: Color = AppBg,
    blur: Dp = 30.dp,
    light: Boolean = false,
): Modifier = this
    .clip(shape)
    .hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = base,
            tint = HazeTint(tint),
            blurRadius = blur,
            noiseFactor = 0.035f,
        ),
    )
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (light) 0.35f else 0.12f),
                0.55f to Color.Transparent,
            )
        )
    }
    .border(1.dp, rim(light), shape)

/** Cheap glass for cards that live inside scrolling content (no live blur needed). */
fun Modifier.staticGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.09f))
        )
    )
    .border(1.dp, rim(false), shape)

/** Colourful, static backdrop so the glass has something to refract. */
fun Modifier.glassBackdrop(): Modifier = this.drawBehind {
    drawRect(AppBg)
    val w = size.width; val h = size.height
    fun blob(c: Color, x: Float, y: Float, r: Float) {
        val center = Offset(w * x, h * y)
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = center, radius = r), radius = r, center = center)
    }
    blob(Color(0xFF5B3FD9).copy(alpha = 0.55f), 0.10f, 0.10f, w * 0.95f)
    blob(Color(0xFF0FA3A3).copy(alpha = 0.40f), 1.00f, 0.42f, w * 0.85f)
    blob(Color(0xFFD9467A).copy(alpha = 0.30f), 0.20f, 0.92f, w * 0.90f)
}

/** Springy press feedback, like iOS. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bouncyClick(
    pressedScale: Float = 0.94f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = src, indication = null, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(44.dp)
            .bouncyClick(pressedScale = 0.86f, onClick = onClick)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------------- Icons ----------------

val BookIcon: ImageVector by lazy {
    ImageVector.Builder("book", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z"
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}

val HighlighterIcon: ImageVector by lazy {
    ImageVector.Builder("highlighter", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M6,14l3,3v5h6v-5l3,-3V9H6V14zM11,2h2v3h-2V2zM3.5,5.88l1.41,-1.41l2.12,2.12L5.62,8L3.5,5.88zM16.96,6.59l2.12,-2.12l1.41,1.41L18.38,8L16.96,6.59z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}
