package com.aniq.glassreader.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks
    private val _reviews = MutableStateFlow<Map<String, Review>>(emptyMap())
    /** Reviews keyed by book id. */
    val reviews: StateFlow<Map<String, Review>> = _reviews
    /** Minutes read per day, keyed "2026-11-10". */
    private val _daily = MutableStateFlow<Map<String, Long>>(emptyMap())
    val daily: StateFlow<Map<String, Long>> = _daily
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings

    private val _wallpaper = MutableStateFlow<Bitmap?>(null)
    val wallpaper: StateFlow<Bitmap?> = _wallpaper
    private val _wallpaperBlur = MutableStateFlow<Bitmap?>(null)
    val wallpaperBlur: StateFlow<Bitmap?> = _wallpaperBlur

    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val contentCache = LruCache<String, BookContent>(2)

    val booksDir: File get() = File(app.filesDir, "books").apply { mkdirs() }
    private val cacheDir: File get() = File(app.filesDir, "parsed").apply { mkdirs() }
    private val coversDir: File get() = File(app.filesDir, "covers").apply { mkdirs() }
    val clipsDir: File get() = File(app.filesDir, "clips").apply { mkdirs() }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("reader", Context.MODE_PRIVATE)
        _books.value = readArray("library.json").map { Book.fromJson(it) }.map { b ->
            val (t, a) = Titles.split(Titles.clean(b.title), b.author)
            b.copy(title = t, author = a)
        }
        _highlights.value = readArray("highlights.json").map { Highlight.fromJson(it) }
        _bookmarks.value = readArray("bookmarks.json").map { Bookmark.fromJson(it) }
        _reviews.value = readArray("reviews.json").mapNotNull { runCatching { Review.fromJson(it) }.getOrNull() }
            .filterNot { it.isEmpty }.associateBy { it.bookId }
        _daily.value = runCatching {
            val o = org.json.JSONObject(prefs.getString("daily", "{}")!!)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
        val d = ReaderSettings()
        _settings.value = ReaderSettings(
            fontSize = prefs.getFloat("fontSize", d.fontSize),
            lineHeight = prefs.getFloat("lineHeight", d.lineHeight),
            theme = prefs.getInt("theme2", d.theme).let { if (it == 0 || it == 5) it else if (it >= 4) 5 else 0 },
            serif = prefs.getBoolean("serif2", d.serif),
            justify = prefs.getBoolean("justify", d.justify),
            margin = prefs.getInt("margin", d.margin),
            grain = false,
            mono = prefs.getBoolean("mono", d.mono),
            wallDim = prefs.getFloat("wallDim", d.wallDim),
            wallInReader = prefs.getBoolean("wallReader", d.wallInReader),
            grainAmount = prefs.getFloat("grainAmt", d.grainAmount),
            pdfMode = prefs.getInt("pdfMode", d.pdfMode),
            haptics = prefs.getBoolean("haptics", d.haptics),
            hapticLevel = prefs.getInt("hapticLevel", d.hapticLevel),
            volumeKeys = prefs.getBoolean("volKeys", d.volumeKeys),
            pageAnim = prefs.getInt("pageAnim", d.pageAnim),
            swipe = prefs.getBoolean("swipe", d.swipe),
            coverScreen = prefs.getBoolean("coverAuto", d.coverScreen),
            uiMode = prefs.getInt("uiMode", d.uiMode),
            twoPageUnfolded = prefs.getBoolean("twoPageOpen", d.twoPageUnfolded),
            dailyGoalMin = prefs.getInt("goalMin", d.dailyGoalMin),
            sideTools = prefs.getBoolean("sideTools", d.sideTools),
            toolsLeft = prefs.getBoolean("toolsLeft", d.toolsLeft),
            glassClarity = prefs.getFloat("glassClarity", d.glassClarity),
        )
        _totalReadMs.value = prefs.getLong("totalReadMs", _books.value.sumOf { it.readMs })
        initialized = true
        io.launch {
            val f = File(app.filesDir, "wallpaper.jpg"); val b = File(app.filesDir, "wallpaper_blur.jpg")
            if (f.exists()) _wallpaper.value = BitmapFactory.decodeFile(f.path)
            if (b.exists()) _wallpaperBlur.value = BitmapFactory.decodeFile(b.path)
        }
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
            writeAtomic("bookmarks.json", JSONArray().apply { _bookmarks.value.forEach { put(it.toJson()) } }.toString())
            writeAtomic("reviews.json", JSONArray().apply { _reviews.value.values.forEach { put(it.toJson()) } }.toString())
        }
        if (widgets) {
            widgetJob?.cancel()
            widgetJob = io.launch { delay(1200); Widgets.updateAll(app) }
        }
    }

    // ---------- Books ----------

    fun book(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun lastRead(): Book? = _books.value.filter { it.lastOpened > 0 }.maxByOrNull { it.lastOpened }

    /** The book you're on right now: the last one opened that isn't finished. */
    fun currentBook(): Book? = _books.value.filter { it.lastOpened > 0 && !it.isFinished }.maxByOrNull { it.lastOpened }

    fun setStatus(id: String, status: Int) {
        val cur = book(id) ?: return
        if (cur.status == status) return
        val now = System.currentTimeMillis()
        // a book moved back to "To read" goes to the end of the queue
        val rank = if (status == BookStatus.TO_READ) (_books.value.maxOfOrNull { it.rank } ?: now) + 1 else cur.rank
        _books.value = _books.value.map {
            if (it.id == id) it.copy(status = status, rank = rank,
                finishedAt = if (status == BookStatus.FINISHED) now else it.finishedAt) else it
        }
        scheduleSave()
    }

    /** New order for the "To read" shelf, first to last. */
    fun reorderToRead(ids: List<String>) {
        val pos = ids.withIndex().associate { (i, id) -> id to i.toLong() }
        _books.value = _books.value.map { b -> pos[b.id]?.let { b.copy(rank = it) } ?: b }
        scheduleSave(widgets = false)
    }

    // ---------- Reviews ----------

    fun review(bookId: String): Review? = _reviews.value[bookId]

    fun saveReview(r: Review) {
        _reviews.value = if (r.isEmpty) _reviews.value - r.bookId
            else _reviews.value + (r.bookId to r.copy(updatedAt = System.currentTimeMillis()))
        scheduleSave()
    }

    fun rate(bookId: String, stars: Int) {
        val cur = review(bookId) ?: Review(bookId)
        saveReview(cur.copy(rating = stars.coerceIn(0, 5)))
    }

    fun bookFile(b: Book) = File(booksDir, "${b.id}.${b.format}")

    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = app.contentResolver
            val name = queryName(uri) ?: "book"
            val mime = resolver.getType(uri) ?: ""
            val format = when {
                name.endsWith(".pdf", true) || mime == "application/pdf" -> "pdf"
                name.endsWith(".txt", true) || mime.startsWith("text/") -> "txt"
                else -> "epub"
            }
            val id = UUID.randomUUID().toString().take(12)
            val file = File(booksDir, "$id.$format")
            resolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }

            val book = if (format == "pdf") importPdf(id, file, name) else {
                val parsed = try { BookParser.parse(file, format, name) } catch (e: Exception) {
                    file.delete(); throw IllegalArgumentException("Couldn't read this file", e)
                }
                BookParser.writeCache(parsed.content, File(cacheDir, "$id.bin"))
                var hasCover = false
                parsed.cover?.let { bytes ->
                    val bmp = decodeSampled(bytes, 700)
                    if (bmp != null) { saveCover(id, bmp); hasCover = true }
                }
                contentCache.put(id, parsed.content)
                Book(id = id, title = parsed.title, author = parsed.author, format = format,
                    hasCover = hasCover, totalParas = parsed.content.paras.size)
            }
            val (t, a) = Titles.split(Titles.clean(book.title), book.author)
            val clean = book.copy(title = t, author = a)
            _books.value = listOf(clean) + _books.value
            scheduleSave()
            clean
        }
    }

    private fun importPdf(id: String, file: File, name: String): Book {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { r ->
                    val count = r.pageCount
                    require(count > 0)
                    r.openPage(0).use { p ->
                        val w = 600; val h = (w * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        saveCover(id, bmp)
                    }
                    return Book(id = id, title = name.substringBeforeLast('.'), author = "", format = "pdf",
                        hasCover = true, totalParas = count)
                }
            }
        } catch (e: Exception) {
            file.delete(); throw IllegalArgumentException("Couldn't open this PDF (it may be password-protected)", e)
        }
    }

    private fun saveCover(id: String, bmp: Bitmap) {
        File(coversDir, "$id.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    private fun queryName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) { uri.lastPathSegment }

    fun delete(id: String) {
        val clips = _highlights.value.filter { it.bookId == id && it.isClip }.map { it.id }
        _books.value = _books.value.filterNot { it.id == id }
        _highlights.value = _highlights.value.filterNot { it.bookId == id }
        _bookmarks.value = _bookmarks.value.filterNot { it.bookId == id }
        _reviews.value = _reviews.value - id
        contentCache.remove(id)
        io.launch {
            booksDir.listFiles()?.filter { it.name.startsWith(id) }?.forEach { it.delete() }
            File(cacheDir, "$id.bin").delete()
            File(coversDir, "$id.jpg").delete()
            clips.forEach { File(clipsDir, "$it.webp").delete() }
        }
        scheduleSave()
    }

    /** Lifetime reading time across every book (survives removing a book). */
    private val _totalReadMs = MutableStateFlow(0L)
    val totalReadMs: StateFlow<Long> = _totalReadMs

    fun addReadTime(id: String, ms: Long) {
        if (ms <= 0) return
        _books.value = _books.value.map { if (it.id == id) it.copy(readMs = it.readMs + ms) else it }
        _totalReadMs.value += ms
        val day = java.time.LocalDate.now().toString()
        _daily.value = _daily.value + (day to ((_daily.value[day] ?: 0L) + ms))
        val o = org.json.JSONObject(); _daily.value.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putLong("totalReadMs", _totalReadMs.value).putString("daily", o.toString()).apply()
        scheduleSave(widgets = false)
    }

    /** Push fresh numbers to the home-screen widgets (e.g. after a reading session). */
    fun refreshWidgets() {
        widgetJob?.cancel()
        widgetJob = io.launch { delay(300); Widgets.updateAll(app) }
    }

    fun markOpened(id: String) {
        _books.value = _books.value.map {
            if (it.id != id) it
            else it.copy(lastOpened = System.currentTimeMillis(),
                status = if (it.status == BookStatus.TO_READ || it.status == BookStatus.PAUSED) BookStatus.READING else it.status)
        }
        scheduleSave()
    }

    fun updateProgress(id: String, index: Int, offset: Int, total: Int, chapter: String? = null) {
        val cur = book(id) ?: return
        val ch = chapter ?: cur.chapter
        if (cur.progressIndex == index && cur.progressOffset == offset && cur.totalParas == total && cur.chapter == ch) return
        _books.value = _books.value.map {
            if (it.id == id) it.copy(progressIndex = index, progressOffset = offset, totalParas = total, chapter = ch) else it
        }
        scheduleSave()
    }

    /** Loads parsed paragraphs, preferring the fast binary cache. */
    suspend fun content(book: Book): BookContent = withContext(Dispatchers.IO) {
        contentCache.get(book.id)?.let { return@withContext it }
        val cache = File(cacheDir, "${book.id}.bin")
        val c = BookParser.readCache(cache) ?: run {
            val parsed = BookParser.parse(bookFile(book), book.format, book.title)
            BookParser.writeCache(parsed.content, cache)
            parsed.content
        }
        contentCache.put(book.id, c)
        c
    }

    fun cover(id: String, maxPx: Int = 600): Bitmap? = cachedBitmap("c:$id@$maxPx", File(coversDir, "$id.jpg"), maxPx)

    fun clip(id: String, maxPx: Int = 900): Bitmap? = cachedBitmap("k:$id@$maxPx", File(clipsDir, "$id.webp"), maxPx)

    private fun cachedBitmap(key: String, f: File, maxPx: Int): Bitmap? {
        bitmapCache.get(key)?.let { return it }
        if (!f.exists()) return null
        val bmp = decodeSampled(f.readBytes(), maxPx) ?: return null
        bitmapCache.put(key, bmp)
        return bmp
    }

    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (maxOf(opts.outHeight, opts.outWidth) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ---------- Highlights ----------

    fun addHighlight(h: Highlight) {
        _highlights.value = _highlights.value + h
        scheduleSave()
    }

    fun addClip(h: Highlight, image: Bitmap) {
        io.launch {
            File(clipsDir, "${h.id}.webp").outputStream().use { image.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) }
            _highlights.value = _highlights.value + h
            scheduleSave()
        }
    }

    fun updateHighlight(id: String, block: (Highlight) -> Highlight) {
        _highlights.value = _highlights.value.map { if (it.id == id) block(it) else it }
        scheduleSave()
    }

    /** Removes a highlight and returns it, so it can be put back. A PDF clip's picture is kept a little longer for that. */
    fun removeHighlight(id: String): Highlight? {
        val h = _highlights.value.firstOrNull { it.id == id } ?: return null
        _highlights.value = _highlights.value.filterNot { it.id == id }
        if (h.isClip) io.launch {
            delay(9_000)
            if (_highlights.value.none { it.id == id }) File(clipsDir, "$id.webp").delete()
        }
        scheduleSave()
        return h
    }

    /** Puts back a highlight removed moments ago. */
    fun restoreHighlight(h: Highlight) {
        if (_highlights.value.any { it.id == h.id }) return
        _highlights.value = _highlights.value + h
        scheduleSave()
    }

    // ---------- Bookmarks ----------

    fun addBookmark(b: Bookmark) { _bookmarks.value = _bookmarks.value + b; scheduleSave(widgets = false) }

    fun removeBookmark(id: String) { _bookmarks.value = _bookmarks.value.filterNot { it.id == id }; scheduleSave(widgets = false) }

    // ---------- Export ----------

    fun highlightsMarkdown(bookIds: List<String>?): String {
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val sb = StringBuilder()
        val books = _books.value.filter { bookIds == null || it.id in bookIds }
        val all = _highlights.value
        if (bookIds == null) sb.append("# My highlights\n\n_Exported from Hono · ${df.format(Date())}_\n\n")
        for (b in books) {
            val hs = all.filter { it.bookId == b.id }.sortedWith(compareBy({ it.para }, { it.start }))
            if (hs.isEmpty() && bookIds == null) continue
            sb.append(if (bookIds == null) "## " else "# ").append(b.title).append('\n')
            if (b.author.isNotBlank()) sb.append("_").append(b.author).append("_\n")
            sb.append("\n${hs.size} highlights · ${(b.progress * 100).toInt()}% read\n\n")
            var lastChapter = ""
            for (h in hs) {
                if (h.chapter.isNotBlank() && h.chapter != lastChapter) {
                    sb.append(if (bookIds == null) "### " else "## ").append(h.chapter).append("\n\n"); lastChapter = h.chapter
                }
                if (h.isClip) sb.append("> 🖼 Clip from page ${h.para + 1} (clips/${h.id}.webp)\n")
                else h.text.trim().lines().forEach { sb.append("> ").append(it).append('\n') }
                sb.append("\n")
                if (h.note.isNotBlank()) sb.append("**Note:** ").append(h.note.trim()).append("\n\n")
                val meta = listOfNotNull(if (h.favorite) "♥ favourite" else null, df.format(Date(h.createdAt)))
                sb.append("<sub>").append(meta.joinToString(" · ")).append("</sub>\n\n")
            }
        }
        return sb.toString()
    }

    suspend fun exportHighlights(uri: Uri, bookIds: List<String>?) = withContext(Dispatchers.IO) {
        app.contentResolver.openOutputStream(uri)!!.use { it.write(highlightsMarkdown(bookIds).toByteArray()) }
    }

    suspend fun exportBookFile(uri: Uri, b: Book) = withContext(Dispatchers.IO) {
        app.contentResolver.openOutputStream(uri)!!.use { out -> bookFile(b).inputStream().use { it.copyTo(out) } }
    }

    suspend fun exportBundle(uri: Uri, b: Book) = withContext(Dispatchers.IO) {
        app.contentResolver.openOutputStream(uri)!!.use { raw ->
            ZipOutputStream(raw).use { zip ->
                val safe = b.title.replace(Regex("[^\\w .-]"), "").trim().ifEmpty { "book" }
                zip.putNextEntry(ZipEntry("$safe.${b.format}"))
                bookFile(b).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                zip.putNextEntry(ZipEntry("$safe - highlights.md"))
                zip.write(highlightsMarkdown(listOf(b.id)).toByteArray()); zip.closeEntry()
                val hs = _highlights.value.filter { it.bookId == b.id }
                zip.putNextEntry(ZipEntry("highlights.json"))
                zip.write(JSONArray().apply { hs.forEach { put(it.toJson()) } }.toString(2).toByteArray()); zip.closeEntry()
                hs.filter { it.isClip }.forEach { h ->
                    val f = File(clipsDir, "${h.id}.webp")
                    if (f.exists()) { zip.putNextEntry(ZipEntry("clips/${h.id}.webp")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                }
            }
        }
    }

    // ---------- Wallpaper ----------

    suspend fun setWallpaper(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            var bmp = decodeSampled(bytes, 2200) ?: return@runCatching false
            // honour EXIF rotation from phone cameras
            val rot = runCatching {
                androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).rotationDegrees
            }.getOrDefault(0)
            if (rot != 0) {
                val m = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            // cheap, smooth blur: shrink hard, then scale back up with filtering
            val sw = 36; val sh = (36f * bmp.height / bmp.width).toInt().coerceAtLeast(1)
            val tiny = Bitmap.createScaledBitmap(bmp, sw, sh, true)
            val mid = Bitmap.createScaledBitmap(tiny, sw * 4, sh * 4, true)
            val blur = Bitmap.createScaledBitmap(mid, sw * 12, sh * 12, true)
            File(app.filesDir, "wallpaper.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            File(app.filesDir, "wallpaper_blur.jpg").outputStream().use { blur.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            _wallpaper.value = bmp
            _wallpaperBlur.value = blur
            true
        }.getOrDefault(false)
    }

    fun clearWallpaper() {
        _wallpaper.value = null; _wallpaperBlur.value = null
        io.launch { File(app.filesDir, "wallpaper.jpg").delete(); File(app.filesDir, "wallpaper_blur.jpg").delete() }
        updateSettings { it.copy(wallInReader = false) }
    }

    // ---------- Settings ----------

    fun updateSettings(block: (ReaderSettings) -> ReaderSettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit()
            .putFloat("fontSize", s.fontSize).putFloat("lineHeight", s.lineHeight)
            .putInt("theme2", s.theme).putBoolean("serif2", s.serif)
            .putBoolean("justify", s.justify).putInt("margin", s.margin)
            .putBoolean("grain", s.grain).putBoolean("mono", s.mono)
            .putFloat("wallDim", s.wallDim).putBoolean("wallReader", s.wallInReader)
            .putFloat("grainAmt", s.grainAmount).putInt("pdfMode", s.pdfMode)
            .putBoolean("haptics", s.haptics).putInt("hapticLevel", s.hapticLevel).putBoolean("volKeys", s.volumeKeys).putInt("pageAnim", s.pageAnim).putBoolean("swipe", s.swipe).putBoolean("coverAuto", s.coverScreen).putInt("uiMode", s.uiMode)
            .putBoolean("twoPageOpen", s.twoPageUnfolded).putInt("goalMin", s.dailyGoalMin)
            .putBoolean("sideTools", s.sideTools).putBoolean("toolsLeft", s.toolsLeft).putFloat("glassClarity", s.glassClarity)
            .apply()
    }

    /** Badge titles already shown as earned, so new ones can pop once. */
    var seenBadges: Set<String>
        get() = prefs.getStringSet("seenBadges", emptySet())!!.toSet()
        set(v) { prefs.edit().putStringSet("seenBadges", v).apply() }

    var quoteIndex: Int
        get() = prefs.getInt("quoteIdx", 0)
        set(v) { prefs.edit().putInt("quoteIdx", v).apply() }

    /** When the widget quote last changed (by the 2-hour cycle or the refresh button). */
    var quoteChangedAt: Long
        get() = prefs.getLong("quoteAt", 0L)
        set(v) { prefs.edit().putLong("quoteAt", v).apply() }
}
