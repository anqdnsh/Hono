package com.aniq.glassreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniq.glassreader.data.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ---------------- Dictionary ----------------

class Sense(val partOfSpeech: String, val definition: String, val example: String)
class Entry(val word: String, val phonetic: String, val senses: List<Sense>)

/**
 * Looks words up in the Free Dictionary API (dictionaryapi.dev, built from Wiktionary).
 * Needs a connection; results are kept for the session so a second look is instant.
 */
object Dictionary {
    private val cache = HashMap<String, Entry?>()

    /** null = no entry for that word; throws if the phone is offline. */
    suspend fun define(raw: String): Entry? = withContext(Dispatchers.IO) {
        val word = raw.trim().trim('\'', '’', '-', '"', '“', '”').lowercase()
        cache[word]?.let { return@withContext it }
        if (word in cache) return@withContext null
        val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word, "UTF-8").replace("+", "%20"))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            if (c.responseCode == 404) { cache[word] = null; return@withContext null }
            val body = c.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            val senses = ArrayList<Sense>()
            var phonetic = ""
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                if (phonetic.isBlank()) phonetic = e.optString("phonetic")
                val ms = e.optJSONArray("meanings") ?: continue
                for (m in 0 until ms.length()) {
                    val mo = ms.getJSONObject(m)
                    val defs = mo.optJSONArray("definitions") ?: continue
                    for (d in 0 until minOf(defs.length(), 3)) {
                        val dobj = defs.getJSONObject(d)
                        senses.add(Sense(mo.optString("partOfSpeech"), dobj.optString("definition"), dobj.optString("example")))
                    }
                }
            }
            val entry = Entry(arr.getJSONObject(0).optString("word", word), phonetic, senses.take(6))
            cache[word] = entry
            entry
        } finally { c.disconnect() }
    }
}

/** The word picked from a selection: the selection itself if it's one word, else the word that was pressed. */
fun pickWord(text: String): String? {
    val w = text.trim().trim { !it.isLetterOrDigit() }
    return w.takeIf { it.isNotEmpty() && it.none { ch -> ch.isWhitespace() } && it.length <= 40 }
}

@Composable
fun DefineContent(word: String, fg: Color, sub: Color) {
    val state by produceState<Pair<Boolean, Entry?>?>(null, word) {
        value = runCatching { true to Dictionary.define(word) }.getOrElse { false to null }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp)) {
        val entry = state?.second
        Text(entry?.word ?: word, color = fg, fontFamily = Inter, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        if (!entry?.phonetic.isNullOrBlank()) Text(entry!!.phonetic, color = sub, fontFamily = Inter, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        when {
            state == null -> Text("Looking it up…", color = sub, fontFamily = Inter, fontSize = 15.sp)
            state?.first == false -> Text("Couldn't reach the dictionary. Check your connection and try again.", color = sub, fontFamily = Inter, fontSize = 15.sp)
            entry == null || entry.senses.isEmpty() -> Text("No definition found for “$word”.", color = sub, fontFamily = Inter, fontSize = 15.sp)
            else -> {
                var lastPos = ""
                entry.senses.forEach { s ->
                    if (s.partOfSpeech != lastPos) {
                        lastPos = s.partOfSpeech
                        Text(s.partOfSpeech, color = sub, fontFamily = Inter, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    }
                    Text(s.definition, color = fg, fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 4.dp))
                    if (s.example.isNotBlank()) Text("“${s.example}”", color = sub, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
                }
                Text("Source: Wiktionary, via dictionaryapi.dev", color = sub.copy(alpha = 0.7f), fontFamily = Inter, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            }
        }
    }
}

// ---------------- Notes ----------------

@Composable
fun NoteEditor(quote: String, initial: String, fg: Color, sub: Color, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
        Text("Note", color = fg, fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        if (quote.isNotBlank()) Text(quote.trim(), color = sub, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(18.dp)).background(fg.copy(alpha = 0.06f)).padding(14.dp)) {
            if (text.isEmpty()) Text("What does this make you think?", color = sub, fontFamily = Inter, fontSize = 16.sp)
            BasicTextField(
                text, { text = it },
                textStyle = TextStyle(color = fg, fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(fg),
                modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp, max = 240.dp).focusRequester(focus),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (initial.isNotBlank()) {
                Text("Delete", color = Color(0xFFFF453A), fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).bouncyClick(pressedScale = 0.92f) { onSave("") }.padding(horizontal = 16.dp, vertical = 10.dp))
                Spacer(Modifier.weight(1f))
            }
            Text("Save", color = if (fg.red > 0.5f) Color.Black else Color.White, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(fg).bouncyClick(pressedScale = 0.92f) { onSave(text.trim()) }
                    .padding(horizontal = 22.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A note under a quote, in a quieter voice. */
@Composable
fun NoteLine(note: String, fg: Color, maxLines: Int = 4, modifier: Modifier = Modifier) {
    if (note.isBlank()) return
    Row(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Box(Modifier.padding(top = 2.dp).size(width = 2.dp, height = 16.dp).clip(RoundedCornerShape(1.dp)).background(fg.copy(alpha = 0.35f)))
        Text(note.trim(), color = fg.copy(alpha = 0.75f), fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---------------- Bookmarks ----------------

@Composable
fun BookmarkList(list: List<Bookmark>, fg: Color, sub: Color, empty: String, onJump: (Bookmark) -> Unit, onRemove: (Bookmark) -> Unit) {
    if (list.isEmpty()) {
        Text(empty, color = sub, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id }) { b ->
            Row(
                Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.97f) { onJump(b) }
                    .clip(RoundedCornerShape(18.dp)).background(fg.copy(alpha = 0.05f)).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Line.BookmarkFill, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(b.label, color = fg, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.snippet.isNotBlank()) Text(b.snippet, color = sub, fontFamily = Inter, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(40.dp).bouncyClick(pressedScale = 0.85f) { onRemove(b) }, contentAlignment = Alignment.Center) {
                    Icon(Line.Close, "Remove bookmark", tint = sub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
