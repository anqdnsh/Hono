package com.aniq.glassreader.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A screen title with a glass search circle; tapping it stretches the circle into a
 * full-width glass search field. ✕ shrinks it back.
 */
@Composable
fun SearchableTitle(
    title: String,
    haze: HazeState,
    searching: Boolean,
    query: String,
    placeholder: String,
    onSearching: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    actions: @Composable () -> Unit = {},
) {
    AnimatedContent(
        targetState = searching,
        transitionSpec = {
            if (targetState) {
                (fadeIn(tween(160)) + expandHorizontally(spring(0.82f, 420f), expandFrom = Alignment.End)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(spring(1f, 600f), targetScale = 0.96f))
            } else {
                (fadeIn(tween(200)) + scaleIn(spring(0.85f, 420f), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(120)) + shrinkHorizontally(spring(0.95f, 600f), shrinkTowards = Alignment.End))
            }
        },
        label = "search",
    ) { on ->
        if (!on) {
            ScreenTitle(title) {
                Row {
                    GlassCircle(Line.Search, "Search", haze, size = 40.dp) { onSearching(true) }
                    Spacer(Modifier.width(10.dp))
                    actions()
                }
            }
        } else {
            SearchField(query, placeholder, onQuery) { onQuery(""); onSearching(false) }
        }
    }
}

@Composable
private fun SearchField(query: String, placeholder: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .height(52.dp)
            .pillGlass(RoundedCornerShape(50))
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Line.Search, null, tint = Primary.copy(alpha = 0.7f), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = Secondary, fontSize = 16.sp)
            BasicTextField(
                query, onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Primary, fontFamily = Inter, fontSize = 16.sp),
                cursorBrush = SolidColor(Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(alpha = 0.10f))
                .bouncyClick(pressedScale = 0.85f) { keyboard?.hide(); onClose() },
            contentAlignment = Alignment.Center,
        ) { Icon(Line.Close, "Close search", tint = Primary, modifier = Modifier.size(15.dp)) }
    }
}

/** [text] with every match of [query] marked like a highlighter. */
fun marked(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    val q = query.trim()
    if (q.isEmpty()) return@buildAnnotatedString
    var i = text.indexOf(q, ignoreCase = true)
    while (i >= 0) {
        addStyle(SpanStyle(background = StarYellow.copy(alpha = 0.35f)), i, i + q.length)
        i = text.indexOf(q, i + q.length, ignoreCase = true)
    }
}

// ---------------- Cover glow ----------------

private val glowCache = android.util.LruCache<String, ImageBitmap>(80)

/** A very soft, pre-blurred copy of a book's cover, made once and kept, for glowing cards. */
@Composable
fun rememberCoverGlow(bookId: String): ImageBitmap? {
    val bmp by produceState(glowCache.get(bookId), bookId) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                Store.cover(bookId, 200)?.let { src ->
                    // shrink hard, then scale back up with filtering: a smooth blur for free
                    val tiny = Bitmap.createScaledBitmap(src, 6, 9, true)
                    val mid = Bitmap.createScaledBitmap(tiny, 24, 36, true)
                    Bitmap.createScaledBitmap(mid, 96, 144, true).asImageBitmap()
                }
            }.getOrNull()?.also { glowCache.put(bookId, it) }
        }
    }
    return bmp
}

/** Washes a card with its cover's colours, strongest on the left where the cover sits. */
@Composable
fun Modifier.coverGlow(glow: ImageBitmap?, strength: Float = 0.55f): Modifier {
    val a by animateFloatAsState(if (glow != null) 1f else 0f, tween(420), label = "glow")
    val dark = UiMode.dark
    return this.drawBehind {
        val g = glow ?: return@drawBehind
        if (a <= 0f) return@drawBehind
        drawImage(
            g,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = (if (dark) strength else strength * 0.7f) * a,
            filterQuality = FilterQuality.Low,
        )
        // fade towards the right and keep text legible
        drawRect(Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to (if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f)),
        ), alpha = a)
    }
}
