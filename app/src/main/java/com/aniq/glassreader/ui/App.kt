package com.aniq.glassreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Book
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface NavRequest {
    data class Import(val uri: Uri) : NavRequest
    data class OpenBook(val id: String, val para: Int?) : NavRequest
    data object Highlights : NavRequest
}

data class ReaderTarget(val bookId: String, val para: Int?, val char: Int = 0, val nonce: Long = System.nanoTime())

enum class ExportKind { BookHighlights, BookFile, Bundle, AllHighlights }

private val screenSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 400f)

private fun safeName(s: String) = s.replace(Regex("[^\\w .-]"), "").trim().ifEmpty { "book" }

@Composable
fun App(nav: MutableState<NavRequest?>) {
    var reader by remember { mutableStateOf<ReaderTarget?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val settings by Store.settings.collectAsState()
    val wall by Store.wallpaper.collectAsState()
    val wallBlur by Store.wallpaperBlur.collectAsState()
    LaunchedEffect(wall) { Wall.image = wall?.asImageBitmap() }
    LaunchedEffect(wallBlur) { Wall.blurred = wallBlur?.asImageBitmap() }
    LaunchedEffect(settings.wallDim) { Wall.dim = settings.wallDim }
    val wallPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { toast = if (Store.setWallpaper(uri)) "Wallpaper set" else "Couldn't use that image" }
    }
    val pickWallpaper = { wallPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    fun import(uri: Uri) {
        importing = true
        scope.launch {
            Store.import(uri)
                .onSuccess { tab = 0; reader = ReaderTarget(it.id, null) }
                .onFailure { toast = it.message ?: "Couldn't open that file" }
            importing = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) import(uri) }
    val launchPicker = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream")) }

    // ---- export ----
    var pending by remember { mutableStateOf<Pair<ExportKind, Book?>?>(null) }
    fun runExport(uri: Uri?) {
        val (kind, book) = pending ?: return
        pending = null
        uri ?: return
        scope.launch {
            runCatching {
                when (kind) {
                    ExportKind.BookHighlights -> Store.exportHighlights(uri, listOf(book!!.id))
                    ExportKind.AllHighlights -> Store.exportHighlights(uri, null)
                    ExportKind.BookFile -> Store.exportBookFile(uri, book!!)
                    ExportKind.Bundle -> Store.exportBundle(uri, book!!)
                }
            }.onSuccess { toast = "Exported" }.onFailure { toast = "Export failed" }
        }
    }
    val mdSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { runExport(it) }
    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { runExport(it) }
    val fileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { runExport(it) }
    val export: (ExportKind, Book?) -> Unit = { kind, book ->
        pending = kind to book
        when (kind) {
            ExportKind.BookHighlights -> mdSaver.launch("${safeName(book!!.title)} - highlights.md")
            ExportKind.AllHighlights -> mdSaver.launch("Hono highlights.md")
            ExportKind.BookFile -> fileSaver.launch("${safeName(book!!.title)}.${book.format}")
            ExportKind.Bundle -> zipSaver.launch("${safeName(book!!.title)} + highlights.zip")
        }
    }

    LaunchedEffect(nav.value) {
        when (val r = nav.value) {
            is NavRequest.Import -> import(r.uri)
            is NavRequest.OpenBook -> if (Store.book(r.id) != null) reader = ReaderTarget(r.id, r.para)
            NavRequest.Highlights -> { reader = null; tab = 1 }
            null -> {}
        }
        nav.value = null
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (settings.uiMode) { 1 -> false; 2 -> true; else -> systemDark }
    UiMode.dark = dark
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(dark, reader) {
        val w = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (reader == null) androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(background = AppBg, surface = AppBg) else androidx.compose.material3.lightColorScheme(background = AppBg, surface = AppBg)) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Inter, color = Primary)) {
            Box(Modifier.fillMaxSize().background(AppBg).monochrome(settings.mono)) {
                AnimatedContent(
                    targetState = reader,
                    contentKey = { it?.nonce },
                    transitionSpec = {
                        if (targetState != null) {
                            (fadeIn(tween(240)) + scaleIn(screenSpring, initialScale = 0.94f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(screenSpring, targetScale = 1.03f))
                        } else {
                            (fadeIn(tween(240)) + scaleIn(screenSpring, initialScale = 1.03f)) togetherWith
                                (fadeOut(tween(180)) + scaleOut(screenSpring, targetScale = 0.94f))
                        }
                    },
                    label = "screen",
                ) { target ->
                    if (target == null) {
                        Home(
                            tab = tab,
                            onTab = { tab = it },
                            importing = importing,
                            onImport = launchPicker,
                            onOpen = { id, para, char -> reader = ReaderTarget(id, para, char) },
                            onExport = export,
                            onPickWallpaper = pickWallpaper,
                        )
                    } else {
                        ReaderScreen(target.bookId, target.para, target.char, onBack = { reader = null })
                    }
                }

                AnimatedVisibility(
                    visible = toast != null,
                    enter = fadeIn() + slideInVertically(spring(0.7f, 500f)) { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp),
                ) {
                    var last by remember { mutableStateOf("") }
                    toast?.let { last = it }
                    Text(
                        last, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (dark) Color(0xF21C1C1E) else Color(0xF2F2F2F7))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Home(
    tab: Int,
    onTab: (Int) -> Unit,
    importing: Boolean,
    onImport: () -> Unit,
    onOpen: (String, Int?, Int) -> Unit,
    onExport: (ExportKind, Book?) -> Unit,
    onPickWallpaper: () -> Unit,
) {
    val haze = remember { HazeState() }
    var settingsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    var barVisible by remember { mutableStateOf(true) }
    LaunchedEffect(tab) { barVisible = true }
    // iOS 26-style: the tab bar steps aside while you scroll down, returns when you scroll up
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -4f) barVisible = false else if (available.y > 4f) barVisible = true
                return Offset.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(scrollWatcher)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "tab",
        ) { t ->
            when (t) {
                0 -> LibraryScreen(haze, importing, onOpen = { onOpen(it, null, 0) }, onExport = onExport, onSettings = { settingsOpen = true }, onImport = onImport)
                else -> HighlightsScreen(haze, onOpen = onOpen, onExportAll = { onExport(ExportKind.AllHighlights, null) }, onSettings = { settingsOpen = true })
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(spring(0.9f, 420f)) { it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(spring(0.95f, 500f)) { it } + fadeOut(tween(160)),
        ) {
            val sHaze = remember { HazeState() }
            Box(Modifier.fillMaxSize().background(AppBg)) {
                SettingsScreen(sHaze, onPickWallpaper = onPickWallpaper, onExport = onExport, onBack = { settingsOpen = false })
            }
        }

        AnimatedVisibility(
            visible = barVisible && !settingsOpen,
            enter = slideInVertically(spring(0.8f, 500f)) { it * 2 } + fadeIn(tween(160)),
            exit = slideOutVertically(spring(0.9f, 500f)) { it * 2 } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
        ) {
            GlassTabBar(tab, onTab, haze)
        }
    }
}

@Composable
private fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, haze: HazeState, modifier: Modifier = Modifier) {
    val tabs = listOf("Library" to Line.Books, "Highlights" to Line.Quote)
    val shape = RoundedCornerShape(50)
    val itemW = 84.dp
    Box(
        modifier
            .width(itemW * tabs.size + 8.dp)
            .height(54.dp)
            .glass(haze, shape, floating = true)
            .padding(4.dp)
    ) {
        val x by animateDpAsState(itemW * selected, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "pill")
        Box(Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(shape).background(Primary.copy(alpha = 0.09f)))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, (label, icon) -> TabItem(label, icon, i == selected, Modifier.width(itemW)) { onSelect(i) } }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) Primary else Secondary
    Column(
        modifier.fillMaxHeight().bouncyClick(pressedScale = 0.9f, haptic = Haptic.Tick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(1.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/** Just enough blur under the status bar for the clock to stay legible over content. */
@Composable
fun StatusFade(haze: HazeState) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().height(top + 14.dp).topFade(haze))
}

/** A plain title that scrolls with the content, no bar behind it. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp, modifier = Modifier.weight(1f))
        trailing()
    }
}
