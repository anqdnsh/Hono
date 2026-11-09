package com.aniq.glassreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.ReaderSettings
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

/** Colours used for controls on the (always dark) settings screen. */
private val UiTheme: ReaderTheme get() = ReaderTheme("ui", Card, Primary, Secondary, Primary, !UiMode.dark, 0.3f)
private val Group: Color get() = if (UiMode.dark) Color(0xFF151517) else Color(0xFFF2F2F7)

@Composable
fun SettingsScreen(haze: HazeState, onPickWallpaper: () -> Unit, onExport: (ExportKind, Book?) -> Unit, onBack: () -> Unit) {
    val s by Store.settings.collectAsState()
    val books by Store.books.collectAsState()
    val all by Store.highlights.collectAsState()
    val hasWall = Store.wallpaper.collectAsState().value != null
    var remove by remember { mutableStateOf<Book?>(null) }
    val haptics = rememberHaptics()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val set: ((ReaderSettings) -> ReaderSettings) -> Unit = { Store.updateSettings(it) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().glassBackdrop().hazeSource(haze),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 12.dp, bottom = bottom + 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(Line.Back, "Back", haze, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f)) { ScreenTitle("Settings") }
                }
            }

            item {
                Section("Reading") {
                    Pad {
                        Label("Background")
                        BackgroundPicker(s, Primary, onNeedWallpaper = onPickWallpaper)
                        Spacer(Modifier.height(8.dp))
                    }
                    Line()
                    Pad {
                        Label("Font")
                        Segmented(listOf("Inter", "Serif"), if (s.serif) 1 else 0, { i -> set { it.copy(serif = i == 1) } })
                        Spacer(Modifier.height(6.dp))
                    }
                    Pad { LabeledSlider("Text size", "${s.fontSize.roundToInt()}", s.fontSize, 14f..32f, UiTheme) { v -> set { it.copy(fontSize = v) } } }
                    Pad { LabeledSlider("Line spacing", String.format("%.1f", s.lineHeight), s.lineHeight, 1.2f..2.2f, UiTheme) { v -> set { it.copy(lineHeight = v) } } }
                    Pad { LabeledSlider("Margins", "${s.margin}", s.margin.toFloat(), 8f..56f, UiTheme) { v -> set { it.copy(margin = v.roundToInt()) } } }
                    Line()
                    Pad { ToggleRow("Justify text", "Even edges on both sides", s.justify, UiTheme) { v -> set { it.copy(justify = v) } } }
                    Line()
                    Pad {
                        Label("Turning pages")
                        Segmented(listOf("Tap", "Swipe"), if (s.swipe) 1 else 0, { i -> set { it.copy(swipe = i == 1) } })
                        Text(
                            if (s.swipe) "Drag the page like paper. Swipe left for next, right to go back."
                            else "Tap the right edge for next, the left edge to go back.",
                            color = Secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                        if (!s.swipe) {
                            Spacer(Modifier.height(10.dp))
                            Segmented(listOf("Slide", "Fade"), s.pageAnim, { i -> set { it.copy(pageAnim = i) } })
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Line()
                    Pad { ToggleRow("Volume keys turn pages", "Down for next, up for previous", s.volumeKeys, UiTheme) { v -> set { it.copy(volumeKeys = v) } } }
                }
            }

            item {
                Section("PDF pages", "Night inverts pages for reading in the dark. Match page blends pages into your background.") {
                    Pad {
                        Spacer(Modifier.height(10.dp))
                        Segmented(listOf("Original", "Match page", "Night"), s.pdfMode, { i -> set { it.copy(pdfMode = i) } })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            item {
                Section("Appearance") {
                    Pad {
                        Label("Theme")
                        Segmented(listOf("System", "Light", "Dark"), s.uiMode, { i -> set { it.copy(uiMode = i) } })
                        Spacer(Modifier.height(10.dp))
                    }
                    Line()
                    NavRow(if (hasWall) "Change wallpaper" else "Choose wallpaper", "From your gallery", onClick = onPickWallpaper)
                    AnimatedVisibility(hasWall, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Line()
                            Pad { LabeledSlider("Dim wallpaper", "${(s.wallDim * 100).roundToInt()}%", s.wallDim, 0f..0.9f, UiTheme) { v -> set { it.copy(wallDim = v) } } }
                            Line()
                            NavRow("Remove wallpaper", null, color = Color(0xFFFF453A), chevron = false, haptic = Haptic.Reject) { Store.clearWallpaper() }
                        }
                    }
                    Line()
                    Pad { ToggleRow("Black & White", "No colour anywhere, pure black reading", s.mono, UiTheme) { v -> set { it.copy(mono = v) } } }
                }
            }

            item {
                val coverOk by com.aniq.glassreader.CoverDisplay.supported.collectAsState()
                Section(
                    "Outer screen",
                    if (coverOk) "Turns on by itself when you read sideways with two pages, and off when you turn the phone upright."
                    else "This phone doesn't offer outer-screen display to apps right now. Try with the phone unfolded, or it may not be supported.",
                ) {
                    Pad { ToggleRow("Show book cover on outer screen", "Fills the outer screen with the cover of what you're reading", s.coverScreen, UiTheme) { v -> set { it.copy(coverScreen = v) } } }
                }
            }

            item {
                Section("Haptics and sound") {
                    Pad { ToggleRow("Page turn sound", "A soft paper sound as pages turn. Muted when your phone is on silent or vibrate", s.pageSound, UiTheme) { v -> set { it.copy(pageSound = v) } } }
                    Pad { ToggleRow("Haptic feedback", "Feel page turns, highlights and controls", s.haptics, UiTheme) { v -> set { it.copy(haptics = v) } } }
                    AnimatedVisibility(s.haptics, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Pad {
                            Label("Strength")
                            Segmented(listOf("Light", "Medium", "Strong"), s.hapticLevel, { i ->
                                set { it.copy(hapticLevel = i) }
                                haptics.play(Haptic.Confirm)
                            })
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }

            item {
                Section("Books", if (books.isEmpty()) null else "Removing a book also deletes its highlights.") {
                    if (all.isNotEmpty()) {
                        NavRow("Export all highlights", ".md") { onExport(ExportKind.AllHighlights, null) }
                        if (books.isNotEmpty()) Line()
                    }
                    if (books.isEmpty()) {
                        Text("No books yet", color = Secondary, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                    }
                    books.sortedBy { it.title.lowercase() }.forEachIndexed { i, b ->
                        if (i > 0) Line(inset = 74.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(b, Modifier.width(42.dp).aspectRatio(0.67f))
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(b.title, color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${b.format.uppercase()} · ${progressLabel(b)}", color = Secondary, fontSize = 13.sp)
                            }
                            Box(
                                Modifier.size(38.dp).bouncyClick(pressedScale = 0.8f) { remove = b },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Line.Trash, "Remove", tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }

            item {
                Text("Hono", color = Tertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 26.dp), textAlign = TextAlign.Center)
            }
        }

        ActionSheet(visible = remove != null, haze = haze, onDismiss = { remove = null }) {
            var shown by remember { mutableStateOf<Book?>(null) }
            remove?.let { shown = it }
            Text("Remove “${shown?.title ?: ""}”?", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text("The book and its highlights will be deleted from this phone.", color = Secondary, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            Spacer(Modifier.height(10.dp))
            SheetAction("Remove", null, Color(0xFFFF453A), haptic = Haptic.Reject) { shown?.let { Store.delete(it.id) }; remove = null }
            SheetAction("Cancel", null) { remove = null }
        }
    }
}

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 26.dp)) {
        Text(title, color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Group).hairline(RoundedCornerShape(22.dp)),
            content = content,
        )
        if (footer != null) Text(footer, color = Tertiary, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
    }
}

@Composable
private fun Pad(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), content = content)
}

@Composable
private fun Label(text: String) {
    Text(text, color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
}

@Composable
private fun Line(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.6.dp).background(Primary.copy(alpha = 0.08f)))
}

@Composable
private fun NavRow(
    title: String,
    hint: String?,
    color: Color = Primary,
    chevron: Boolean = true,
    haptic: Haptic = Haptic.Tap,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.98f, haptic = haptic, onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, color = Secondary, fontSize = 14.sp)
        if (chevron) Icon(Line.Chevron, null, tint = Tertiary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

/** Page colour swatches; shared by Settings and the in-book Aa sheet. */
@Composable
fun ThemeSwatches(s: ReaderSettings, theme: ReaderTheme, inset: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReaderThemes.forEachIndexed { i, t ->
            val sel = i == s.theme && !s.mono
            val scale by animateFloatAsState(if (sel) 1.06f else 1f, spring(0.5f, 500f), label = "t")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 74.dp)
                        .bouncyClick(pressedScale = 0.9f, haptic = Haptic.Tick) { Store.updateSettings { it.copy(theme = i, mono = false) } }
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.bg)
                        .paper(s.grain, t.light, s.grainAmount)
                        .border(if (sel) 2.dp else 0.6.dp, if (sel) theme.text else theme.text.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("Aa", color = t.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFamily = if (s.serif) androidx.compose.ui.text.font.FontFamily.Serif else Inter) }
                Spacer(Modifier.height(6.dp))
                Text(t.name, color = if (sel) theme.text else theme.sub, fontSize = 11.sp, fontFamily = Inter)
            }
        }
    }
}

/** Reading background: plain white, plain black, or the user's wallpaper. */
@Composable
fun BackgroundPicker(s: ReaderSettings, fg: Color, onNeedWallpaper: (() -> Unit)? = null) {
    val hasWall = Store.wallpaper.collectAsState().value != null
    val options = buildList { add("White"); add("Black"); if (hasWall || onNeedWallpaper != null) add("Wallpaper") }
    val selected = when {
        s.wallInReader && hasWall -> 2
        s.theme == 5 -> 1
        else -> 0
    }
    Segmented(options, selected, { i ->
        when (i) {
            0 -> Store.updateSettings { it.copy(theme = 0, wallInReader = false, mono = false) }
            1 -> Store.updateSettings { it.copy(theme = 5, wallInReader = false) }
            else -> if (hasWall) Store.updateSettings { it.copy(wallInReader = true, mono = false) } else onNeedWallpaper?.invoke()
        }
    }, fg = fg)
}
