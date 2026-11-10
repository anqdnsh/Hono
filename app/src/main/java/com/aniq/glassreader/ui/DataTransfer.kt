package com.aniq.glassreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Backup
import com.aniq.glassreader.data.BackupInfo
import com.aniq.glassreader.data.ImportResult
import com.aniq.glassreader.data.Store
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

enum class Transfer { Idle, Exporting, Exported, Preview, ConfirmReplace, Importing, Imported, Error }

/** Export data / Import data, from Settings › Library. */
class DataTransferState(val onOpen: (String) -> Unit) {
    var stage by mutableStateOf(Transfer.Idle)
    var progress by mutableFloatStateOf(0f)
    var info by mutableStateOf<BackupInfo?>(null)
    var covers by mutableStateOf<List<ImageBitmap>>(emptyList())
    var uri: Uri? = null
    var replace by mutableStateOf(false)
    var result by mutableStateOf<ImportResult?>(null)
    var error by mutableStateOf("")
    var startExport: () -> Unit = {}
    var startImport: () -> Unit = {}
    var runImport: () -> Unit = {}
    val busy get() = stage == Transfer.Exporting || stage == Transfer.Importing
}

@Composable
fun rememberDataTransfer(onOpen: (String) -> Unit): DataTransferState {
    val st = remember { DataTransferState(onOpen) }
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        st.progress = 0f; st.stage = Transfer.Exporting
        scope.launch {
            runCatching { Backup.export(uri) { p -> st.progress = p } }
                .onSuccess { st.stage = Transfer.Exported }
                .onFailure { st.error = "Couldn't export. Check there's enough space and try again."; st.stage = Transfer.Error }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val info = Backup.peek(uri)
            if (info == null) { st.error = "That isn't a Hono export file."; st.stage = Transfer.Error; return@launch }
            st.uri = uri; st.info = info; st.replace = false
            st.covers = withContext(Dispatchers.IO) {
                Backup.peekCovers(uri, info.covers).values.mapNotNull { b ->
                    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                }
            }
            st.stage = Transfer.Preview
        }
    }
    st.startExport = { saver.launch("Hono-backup-${java.time.LocalDate.now()}.hono") }
    st.startImport = { picker.launch(arrayOf("*/*")) }
    st.runImport = {
        val uri = st.uri
        if (uri != null) {
            st.progress = 0f; st.stage = Transfer.Importing
            scope.launch {
                runCatching { Backup.import(uri, st.replace) { p -> st.progress = p } }
                    .onSuccess { st.result = it; st.stage = Transfer.Imported }
                    .onFailure { st.error = it.message?.takeIf { m -> m.startsWith("That") } ?: "Couldn't import that file."; st.stage = Transfer.Error }
            }
        }
    }
    return st
}

private fun mb(bytes: Long) = if (bytes >= 1_000_000) "${bytes / 1_000_000} MB" else "${(bytes / 1000).coerceAtLeast(1)} KB"

@Composable
fun DataTransferSheets(st: DataTransferState, haze: HazeState) {
    ActionSheet(visible = st.stage != Transfer.Idle, haze = haze, onDismiss = { if (!st.busy) st.stage = Transfer.Idle }) {
        var shown by remember { mutableStateOf(Transfer.Exporting) }
        if (st.stage != Transfer.Idle) shown = st.stage
        when (shown) {
            Transfer.Exporting, Transfer.Exported -> ExportBody(st, done = shown == Transfer.Exported)
            Transfer.Preview -> PreviewBody(st)
            Transfer.ConfirmReplace -> ConfirmBody(st)
            Transfer.Importing -> ProgressBody("Importing", "Bringing your library across", st.progress)
            Transfer.Imported -> DoneBody(st)
            Transfer.Error -> {
                Text(st.error, color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                Spacer(Modifier.height(12.dp))
                SheetAction("OK", null) { st.stage = Transfer.Idle }
            }
            Transfer.Idle -> {}
        }
    }
}

@Composable
private fun Bar(progress: Float) {
    val p by animateFloatAsState(progress, spring(0.9f, 200f), label = "bar")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.10f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.01f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Primary))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Primary.copy(alpha = 0.06f)).padding(12.dp)) {
        Text(value, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun ExportBody(st: DataTransferState, done: Boolean) {
    val books = Store.books.value; val hl = Store.highlights.value; val rv = Store.reviews.value
    val size = remember { Backup.estimateBytes() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (done) CheckBadge() else Icon(Line.Upload, null, tint = Primary, modifier = Modifier.size(26.dp))
        Text(if (done) "Exported" else "Exporting", color = Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(if (done) "Open it on your new phone with Settings › Import data." else "Keep Hono open until it's done.",
            color = Secondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(16.dp))
        if (!done) { Bar(st.progress); Spacer(Modifier.height(6.dp))
            Text("${(st.progress * 100).toInt()}%", color = Secondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.End)) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${books.size}", "books", Modifier.weight(1f)); Stat("${hl.size}", "highlights", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${rv.size}", "reviews", Modifier.weight(1f)); Stat(mb(size), "in total", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if (done) SheetAction("Done", null) { st.stage = Transfer.Idle }
    }
}

@Composable
private fun CheckBadge() {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF30D158).copy(alpha = 0.16f))
        .border(0.8.dp, Color(0xFF30D158).copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Line.Check, null, tint = Color(0xFF30D158), modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PreviewBody(st: DataTransferState) {
    val info = st.info ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 64.dp, height = 62.dp)) {
                st.covers.take(3).forEachIndexed { i, c ->
                    Image(c, null, contentScale = ContentScale.Crop, modifier = Modifier.offset(x = (i * 13).dp, y = if (i == 1) 0.dp else 5.dp)
                        .size(width = 36.dp, height = 54.dp).graphicsLayer { rotationZ = (i - 1) * 9f }.clip(RoundedCornerShape(5.dp)))
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Import this data?", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("From ${info.device} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(info.created))}", color = Secondary, fontSize = 13.sp)
            }
        }
        Text("${info.books} books · ${info.highlights} highlights · ${info.reviews} reviews · ${info.bookmarks} bookmarks · settings and wallpaper",
            color = Secondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(Line.Plus, "Merge", "Keep what's here, add the rest", !st.replace, Modifier.weight(1f)) { st.replace = false }
            ModeCard(Line.Refresh, "Replace", "Make this phone an exact copy", st.replace, Modifier.weight(1f)) { st.replace = true }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Import", color = AppBg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.96f) {
                if (st.replace) st.stage = Transfer.ConfirmReplace else st.runImport()
            }.clip(RoundedCornerShape(50)).background(Primary).padding(vertical = 14.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ModeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, spring(0.8f, 500f), label = "mode")
    Column(
        modifier.bouncyClick(pressedScale = 0.96f, haptic = Haptic.Tick, onClick = onClick)
            .clip(RoundedCornerShape(20.dp)).background(Primary.copy(alpha = 0.05f + 0.06f * ring))
            .border((0.6f + 0.6f * ring).dp, Primary.copy(alpha = 0.10f + 0.5f * ring), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(19.dp))
        Text(title, color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(sub, color = Secondary, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ConfirmBody(st: DataTransferState) {
    Text("Replace everything on this phone?", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    Text("The books, highlights and settings here now will be removed and replaced by the ones in the file.", color = Secondary, fontSize = 13.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
    Spacer(Modifier.height(10.dp))
    SheetAction("Replace everything", null, Color(0xFFFF453A), haptic = Haptic.Reject) { st.runImport() }
    SheetAction("Back", null) { st.stage = Transfer.Preview }
}

@Composable
private fun ProgressBody(title: String, sub: String, progress: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Line.Download, null, tint = Primary, modifier = Modifier.size(26.dp))
        Text(title, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(sub, color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(16.dp))
        Bar(progress)
        Spacer(Modifier.height(6.dp))
        Text("${(progress * 100).toInt()}%", color = Secondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DoneBody(st: DataTransferState) {
    val r = st.result ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CheckBadge()
        Text("Welcome back", color = Primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text("${r.books} books and ${r.highlights} highlights are on this phone." +
            (if (r.lastBookTitle != null) "\nYou were at ${r.lastPage} of ${r.lastBookTitle}." else ""),
            color = Secondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        if (r.lastBookId != null) SheetAction("Continue reading", null) { st.stage = Transfer.Idle; st.onOpen(r.lastBookId) }
        SheetAction("Done", null) { st.stage = Transfer.Idle }
    }
}

@Suppress("unused") private val keepWidth = Modifier.width(0.dp)
