package com.aniq.glassreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface NavRequest {
    data class Import(val uri: Uri) : NavRequest
    data class OpenBook(val id: String, val para: Int?) : NavRequest
    data object Highlights : NavRequest
}

data class ReaderTarget(val bookId: String, val para: Int?, val nonce: Long = System.nanoTime())

private val screenSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

@Composable
fun App(nav: MutableState<NavRequest?>) {
    var reader by remember { mutableStateOf<ReaderTarget?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun import(uri: Uri) {
        importing = true
        scope.launch {
            Store.import(uri)
                .onSuccess { tab = 0; reader = ReaderTarget(it.id, null) }
                .onFailure { toast = it.message ?: "Couldn't open that file" }
            importing = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) import(uri)
    }
    val launchPicker = { picker.launch(arrayOf("application/epub+zip", "text/plain", "application/octet-stream")) }

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

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(AppBg)) {
            AnimatedContent(
                targetState = reader,
                contentKey = { it?.nonce },
                transitionSpec = {
                    if (targetState != null) {
                        (fadeIn(tween(220)) + scaleIn(screenSpring, initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(screenSpring, targetScale = 1.04f))
                    } else {
                        (fadeIn(tween(220)) + scaleIn(screenSpring, initialScale = 1.04f)) togetherWith
                            (fadeOut(tween(180)) + scaleOut(screenSpring, targetScale = 0.92f))
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
                        onOpen = { id, para -> reader = ReaderTarget(id, para) },
                    )
                } else {
                    ReaderScreen(target.bookId, target.para, onBack = { reader = null })
                }
            }

            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn() + slideInVertically(spring(0.7f, 500f)) { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
            ) {
                var last by remember { mutableStateOf("") }
                toast?.let { last = it }
                Text(
                    last,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6202230))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
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
    onOpen: (String, Int?) -> Unit,
) {
    val haze = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(0.9f, 500f)) { dir * it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(spring(0.9f, 500f)) { -dir * it / 5 } + fadeOut(tween(160)))
            },
            label = "tab",
        ) { t ->
            if (t == 0) LibraryScreen(haze, importing, onImport, onOpen = { onOpen(it, null) })
            else HighlightsScreen(haze, onOpen = onOpen)
        }

        GlassTabBar(
            selected = tab,
            onSelect = onTab,
            haze = haze,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, haze: HazeState, modifier: Modifier = Modifier) {
    val tabs = listOf("Library" to BookIcon, "Highlights" to HighlighterIcon)
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .width(260.dp)
            .height(66.dp)
            .glass(haze, shape, tint = Color.White.copy(alpha = 0.10f))
            .padding(6.dp)
    ) {
        val itemW = maxWidth / tabs.size
        val x by animateDpAsState(itemW * selected, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), label = "pill")
        Box(
            Modifier
                .offset(x = x)
                .width(itemW)
                .fillMaxHeight()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.16f))
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, (label, icon) ->
                TabItem(label, icon, i == selected, Modifier.weight(1f)) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) Color.White else Color.White.copy(alpha = 0.55f)
    Column(
        modifier.fillMaxHeight().bouncyClick(pressedScale = 0.88f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Shared glass top bar for the home tabs. */
@Composable
fun GlassHeader(
    title: String,
    haze: HazeState,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(haze, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp), tint = Color.White.copy(alpha = 0.07f))
            .statusBarsPadding()
            .padding(start = 22.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            trailing()
        }
        below()
    }
}
