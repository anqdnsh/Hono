package com.aniq.glassreader.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.aniq.glassreader.parser.BookParser
import com.aniq.glassreader.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * Tiny JSON-file store. Everything lives in memory as StateFlows and is
 * written back to disk on a background thread (debounced), so the UI never waits on I/O.
 */
object Store {
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var widgetJob: Job? = null
    private var initialized = false

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books
    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings

    private val coverCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val contentCache = LruCache<String, BookContent>(2)

    val booksDir: File get() = File(app.filesDir, "books").apply { mkdirs() }
    private val cacheDir: File get() = File(app.filesDir, "parsed").apply { mkdirs() }
    private val coversDir: File get() = File(app.filesDir, "covers").apply { mkdirs() }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("reader", Context.MODE_PRIVATE)
        _books.value = readArray("library.json").map { Book.fromJson(it) }
        _highlights.value = readArray("highlights.json").map { Highlight.fromJson(it) }
        _settings.value = ReaderSettings(
            fontSize = prefs.getFloat("fontSize", 19f),
            lineHeight = prefs.getFloat("lineHeight", 1.6f),
            theme = prefs.getInt("theme", 2),
            serif = prefs.getBoolean("serif", true),
            justify = prefs.getBoolean("justify", true),
            margin = prefs.getInt("margin", 24),
        )
        initialized = true
    }

    private fun readArray(name: String): List<org.json.JSONObject> {
        val f = File(app.filesDir, name)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { arr.getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeAtomic(name: String, text: String) {
        val tmp = File(app.filesDir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(app.filesDir, name))
    }

    private fun scheduleSave(widgets: Boolean = true) {
        saveJob?.cancel()
        saveJob = io.launch {
            delay(250)
            val b = JSONArray().apply { _books.value.forEach { put(it.toJson()) } }
            val h = JSONArray().apply { _highlights.value.forEach { put(it.toJson()) } }
            writeAtomic("library.json", b.toString())
            writeAtomic("highlights.json", h.toString())
        }
        if (widgets) {
            widgetJob?.cancel()
            widgetJob = io.launch { delay(1200); Widgets.updateAll(app) }
        }
    }

    // ---------- Books ----------

    fun book(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun lastRead(): Book? = _books.value.filter { it.lastOpened > 0 }.maxByOrNull { it.lastOpened }

    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = app.contentResolver
            val name = queryName(uri) ?: "book"
            val mime = resolver.getType(uri) ?: ""
            val isTxt = name.endsWith(".txt", true) || mime.startsWith("text/")
            val format = if (isTxt) "txt" else "epub"
            val id = UUID.randomUUID().toString().take(12)
            val file = File(booksDir, "$id.$format")
            resolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }

            val parsed = try { BookParser.parse(file, format, name) } catch (e: Exception) {
                file.delete(); throw IllegalArgumentException("Couldn't read this file", e)
            }
            BookParser.writeCache(parsed.content, File(cacheDir, "$id.bin"))
            var hasCover = false
            parsed.cover?.let { bytes ->
                val bmp = decodeSampled(bytes, 600)
                if (bmp != null) {
                    File(coversDir, "$id.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    hasCover = true
                }
            }
            val book = Book(
                id = id, title = parsed.title, author = parsed.author, format = format,
                hasCover = hasCover, totalParas = parsed.content.paras.size,
            )
            contentCache.put(id, parsed.content)
            _books.value = listOf(book) + _books.value
            scheduleSave()
            book
        }
    }

    private fun queryName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) { uri.lastPathSegment }

    fun delete(id: String) {
        _books.value = _books.value.filterNot { it.id == id }
        _highlights.value = _highlights.value.filterNot { it.bookId == id }
        coverCache.remove(id); contentCache.remove(id)
        io.launch {
            booksDir.listFiles()?.filter { it.name.startsWith(id) }?.forEach { it.delete() }
            File(cacheDir, "$id.bin").delete()
            File(coversDir, "$id.jpg").delete()
        }
        scheduleSave()
    }

    fun markOpened(id: String) {
        _books.value = _books.value.map { if (it.id == id) it.copy(lastOpened = System.currentTimeMillis()) else it }
        scheduleSave()
    }

    fun updateProgress(id: String, index: Int, offset: Int, total: Int) {
        val cur = book(id) ?: return
        if (cur.progressIndex == index && cur.progressOffset == offset && cur.totalParas == total) return
        _books.value = _books.value.map {
            if (it.id == id) it.copy(progressIndex = index, progressOffset = offset, totalParas = total) else it
        }
        scheduleSave()
    }

    /** Loads parsed paragraphs, preferring the fast binary cache. */
    suspend fun content(book: Book): BookContent = withContext(Dispatchers.IO) {
        contentCache.get(book.id)?.let { return@withContext it }
        val cache = File(cacheDir, "${book.id}.bin")
        val c = BookParser.readCache(cache) ?: run {
            val src = File(booksDir, "${book.id}.${book.format}")
            val parsed = BookParser.parse(src, book.format, book.title)
            BookParser.writeCache(parsed.content, cache)
            parsed.content
        }
        contentCache.put(book.id, c)
        c
    }

    fun cover(id: String, maxPx: Int = 600): Bitmap? {
        val key = "$id@$maxPx"
        coverCache.get(key)?.let { return it }
        val f = File(coversDir, "$id.jpg")
        if (!f.exists()) return null
        val bmp = decodeSampled(f.readBytes(), maxPx) ?: return null
        coverCache.put(key, bmp)
        return bmp
    }

    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ---------- Highlights ----------

    fun addHighlight(h: Highlight) {
        _highlights.value = _highlights.value + h
        scheduleSave()
    }

    fun updateHighlight(id: String, block: (Highlight) -> Highlight) {
        _highlights.value = _highlights.value.map { if (it.id == id) block(it) else it }
        scheduleSave()
    }

    fun removeHighlight(id: String) {
        _highlights.value = _highlights.value.filterNot { it.id == id }
        scheduleSave()
    }

    // ---------- Settings ----------

    fun updateSettings(block: (ReaderSettings) -> ReaderSettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit()
            .putFloat("fontSize", s.fontSize).putFloat("lineHeight", s.lineHeight)
            .putInt("theme", s.theme).putBoolean("serif", s.serif)
            .putBoolean("justify", s.justify).putInt("margin", s.margin)
            .apply()
    }

    var quoteIndex: Int
        get() = prefs.getInt("quoteIdx", 0)
        set(v) { prefs.edit().putInt("quoteIdx", v).apply() }
}
