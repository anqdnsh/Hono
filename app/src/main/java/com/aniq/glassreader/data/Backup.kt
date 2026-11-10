package com.aniq.glassreader.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What's inside a Hono export, read from its first entry before anything changes. */
class BackupInfo(
    val created: Long,
    val device: String,
    val books: Int,
    val highlights: Int,
    val reviews: Int,
    val bookmarks: Int,
    val covers: List<String>,   // ids of a few books, for the cover fan
)

class ImportResult(val books: Int, val highlights: Int, val lastBookId: String?, val lastBookTitle: String?, val lastPage: String?)

/**
 * Everything in Hono as one ".hono" file (a zip), to move to a new phone:
 * books, covers, PDF clips, highlights, notes, bookmarks, reviews, statuses, reading progress,
 * daily reading history, badges, settings and wallpaper. The parsed-book cache is left out;
 * it's rebuilt when a book is opened.
 */
object Backup {
    private const val FORMAT = "hono-backup"
    private const val VERSION = 1
    private val jsons = listOf("library.json", "highlights.json", "bookmarks.json", "reviews.json")

    private val files get() = Store.app.filesDir
    private fun mediaFiles(): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        Store.booksDir.listFiles()?.forEach { out += "books/${it.name}" to it }
        Store.coversDir.listFiles()?.forEach { out += "covers/${it.name}" to it }
        Store.clipsDir.listFiles()?.forEach { out += "clips/${it.name}" to it }
        listOf("wallpaper.jpg", "wallpaper_blur.jpg").map { File(files, it) }.filter { it.exists() }
            .forEach { out += "wallpaper/${it.name}" to it }
        return out
    }

    /** Roughly how big an export will be, for the Settings row. */
    fun estimateBytes(): Long = runCatching { mediaFiles().sumOf { it.second.length() } }.getOrDefault(0L)

    var lastExport: Long
        get() = Store.prefs.getLong("lastExport", 0L)
        set(v) { Store.prefs.edit().putLong("lastExport", v).apply() }

    // ---------------- Export ----------------

    suspend fun export(uri: Uri, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        Store.saveNow()
        val media = mediaFiles()
        val total = media.sumOf { it.second.length() }.coerceAtLeast(1L)
        var done = 0L
        Store.app.contentResolver.openOutputStream(uri)!!.use { raw ->
            ZipOutputStream(raw.buffered(1 shl 16)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                // the manifest goes first so the new phone can preview it without reading the whole file
                put("manifest.json", JSONObject().apply {
                    put("format", FORMAT); put("version", VERSION)
                    put("created", System.currentTimeMillis())
                    put("device", android.os.Build.MODEL ?: "Android")
                    put("books", Store.books.value.size); put("highlights", Store.highlights.value.size)
                    put("reviews", Store.reviews.value.size); put("bookmarks", Store.bookmarks.value.size)
                    put("covers", JSONArray().apply {
                        Store.books.value.sortedByDescending { it.lastOpened }.filter { it.hasCover }.take(3).forEach { put(it.id) }
                    })
                }.toString().toByteArray())
                for (j in jsons) { val f = File(files, j); if (f.exists()) put("data/$j", f.readBytes()) }
                put("data/prefs.json", prefsToJson().toString().toByteArray())
                val buf = ByteArray(1 shl 16)
                for ((name, f) in media) {
                    zip.putNextEntry(ZipEntry(name))
                    f.inputStream().use { inp ->
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            zip.write(buf, 0, n); done += n
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        lastExport = System.currentTimeMillis()
        onProgress(1f)
    }

    private fun prefsToJson(): JSONObject = JSONObject().apply {
        Store.prefs.all.forEach { (k, v) ->
            if (k == "lastExport") return@forEach
            val o = JSONObject()
            when (v) {
                is Boolean -> { o.put("t", "b"); o.put("v", v) }
                is Int -> { o.put("t", "i"); o.put("v", v) }
                is Long -> { o.put("t", "l"); o.put("v", v) }
                is Float -> { o.put("t", "f"); o.put("v", v.toDouble()) }
                is String -> { o.put("t", "s"); o.put("v", v) }
                is Set<*> -> { o.put("t", "set"); o.put("v", JSONArray().apply { v.forEach { put(it.toString()) } }) }
                else -> return@forEach
            }
            put(k, o)
        }
    }

    // ---------------- Preview ----------------

    suspend fun peek(uri: Uri): BackupInfo? = withContext(Dispatchers.IO) {
        runCatching {
            Store.app.contentResolver.openInputStream(uri)!!.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    val e = zip.nextEntry ?: return@runCatching null
                    if (e.name != "manifest.json") return@runCatching null
                    val m = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    if (m.optString("format") != FORMAT) return@runCatching null
                    BackupInfo(
                        m.optLong("created"), m.optString("device", "another phone"),
                        m.optInt("books"), m.optInt("highlights"), m.optInt("reviews"), m.optInt("bookmarks"),
                        m.optJSONArray("covers")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                    )
                }
            }
        }.getOrNull()
    }

    /** Pulls a few cover images out of an export for the preview. */
    suspend fun peekCovers(uri: Uri, ids: List<String>): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val want = ids.map { "covers/$it.jpg" }.toSet()
        val out = HashMap<String, ByteArray>()
        runCatching {
            Store.app.contentResolver.openInputStream(uri)!!.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (out.size < want.size) {
                        val e = zip.nextEntry ?: break
                        if (e.name in want) out[e.name.removePrefix("covers/").removeSuffix(".jpg")] = zip.readBytes()
                    }
                }
            }
        }
        out
    }

    // ---------------- Import ----------------

    /**
     * Unpacks an export. [replace] = make this phone an exact copy; otherwise merge: keep
     * what's here and add the rest. A book that's on both phones (same file) is kept once,
     * with the furthest reading position, the newest review and no duplicate highlights.
     */
    suspend fun import(uri: Uri, replace: Boolean, onProgress: (Float) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        val tmp = File(Store.app.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        try {
            // 1. unpack to a scratch folder
            val total = runCatching {
                Store.app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()?.takeIf { it > 0 } ?: 1L
            var read = 0L
            Store.app.contentResolver.openInputStream(uri)!!.use { raw ->
                val counting = object : java.io.FilterInputStream(raw.buffered(1 shl 16)) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0) { read += n; onProgress((read.toFloat() / total * 0.8f).coerceAtMost(0.8f)) }
                        return n
                    }
                }
                ZipInputStream(counting).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory || e.name.contains("..")) continue
                        val f = File(tmp, e.name)
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val manifest = File(tmp, "manifest.json").takeIf { it.exists() }?.readText()?.let { JSONObject(it) }
            require(manifest?.optString("format") == FORMAT) { "That isn't a Hono export" }

            fun arr(name: String): List<JSONObject> = File(tmp, "data/$name").takeIf { it.exists() }?.let { f ->
                val a = JSONArray(f.readText()); List(a.length()) { a.getJSONObject(it) }
            } ?: emptyList()
            val inBooks = arr("library.json").map { Book.fromJson(it) }
            val inHl = arr("highlights.json").map { Highlight.fromJson(it) }
            val inMarks = arr("bookmarks.json").map { Bookmark.fromJson(it) }
            val inReviews = arr("reviews.json").map { Review.fromJson(it) }
            val inPrefs = File(tmp, "data/prefs.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) } ?: JSONObject()

            Store.saveJob?.cancel()
            if (replace) replaceAll(tmp, inPrefs) else mergeIn(tmp, inBooks, inHl, inMarks, inReviews, inPrefs)
            onProgress(0.97f)
            withContext(Dispatchers.Main) { Store.reload() }
            onProgress(1f)
            val last = Store.lastRead()
            ImportResult(
                Store.books.value.size, Store.highlights.value.size, last?.id, last?.title,
                last?.let { b -> if (b.isPdf) "page ${b.progressIndex + 1}" else "${(b.progress * 100).toInt()}%" },
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun moveDir(from: File, to: File) {
        to.mkdirs()
        from.listFiles()?.forEach { f -> val dst = File(to, f.name); if (!f.renameTo(dst)) { f.copyTo(dst, true); f.delete() } }
    }

    private fun replaceAll(tmp: File, prefs: JSONObject) {
        // clear this phone's library
        listOf(Store.booksDir, Store.coversDir, Store.clipsDir, Store.cacheDir).forEach { it.deleteRecursively() }
        jsons.forEach { File(files, it).delete() }
        listOf("wallpaper.jpg", "wallpaper_blur.jpg").forEach { File(files, it).delete() }
        // put the export in its place
        moveDir(File(tmp, "books"), Store.booksDir)
        moveDir(File(tmp, "covers"), Store.coversDir)
        moveDir(File(tmp, "clips"), Store.clipsDir)
        File(tmp, "wallpaper").listFiles()?.forEach { it.copyTo(File(files, it.name), true) }
        jsons.forEach { j -> File(tmp, "data/$j").takeIf { it.exists() }?.copyTo(File(files, j), true) }
        // settings, reading history, badges
        val keepExport = Backup.lastExport
        val ed = Store.prefs.edit().clear()
        prefs.keys().forEach { k -> applyPref(ed, k, prefs.getJSONObject(k)) }
        ed.putLong("lastExport", keepExport).commit()
    }

    private fun applyPref(ed: android.content.SharedPreferences.Editor, k: String, o: JSONObject) {
        when (o.optString("t")) {
            "b" -> ed.putBoolean(k, o.optBoolean("v"))
            "i" -> ed.putInt(k, o.optInt("v"))
            "l" -> ed.putLong(k, o.optLong("v"))
            "f" -> ed.putFloat(k, o.optDouble("v").toFloat())
            "s" -> ed.putString(k, o.optString("v"))
            "set" -> ed.putStringSet(k, o.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet())
        }
    }

    private fun sha1(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { inp -> val buf = ByteArray(1 shl 16); while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun mergeIn(tmp: File, inBooks: List<Book>, inHl: List<Highlight>, inMarks: List<Bookmark>, inReviews: List<Review>, prefs: JSONObject) {
        val books = Store.readArray("library.json").map { Book.fromJson(it) }.toMutableList()
        val hl = Store.readArray("highlights.json").map { Highlight.fromJson(it) }.toMutableList()
        val marks = Store.readArray("bookmarks.json").map { Bookmark.fromJson(it) }.toMutableList()
        val reviews = Store.readArray("reviews.json").mapNotNull { runCatching { Review.fromJson(it) }.getOrNull() }
            .associateBy { it.bookId }.toMutableMap()

        // which books are already here: same file contents, whatever id they were given
        val hashHere = HashMap<String, String>()   // hash -> local id
        books.forEach { b -> sha1(Store.bookFile(b))?.let { hashHere[it] = b.id } }
        val idMap = HashMap<String, String>()      // imported id -> id on this phone
        for (b in inBooks) {
            val src = File(tmp, "books/${b.id}.${b.format}")
            if (!src.exists()) continue
            val local = sha1(src)?.let { hashHere[it] }
            if (local != null) {
                idMap[b.id] = local
                val i = books.indexOfFirst { it.id == local }
                if (i >= 0) {
                    val cur = books[i]
                    val further = b.progressIndex > cur.progressIndex || (b.progressIndex == cur.progressIndex && b.progressOffset > cur.progressOffset)
                    books[i] = cur.copy(
                        progressIndex = if (further) b.progressIndex else cur.progressIndex,
                        progressOffset = if (further) b.progressOffset else cur.progressOffset,
                        chapter = if (further) b.chapter else cur.chapter,
                        lastOpened = maxOf(cur.lastOpened, b.lastOpened),
                        readMs = maxOf(cur.readMs, b.readMs),
                        status = if (cur.isFinished || b.isFinished) BookStatus.FINISHED else if (b.lastOpened > cur.lastOpened) b.status else cur.status,
                        finishedAt = maxOf(cur.finishedAt, b.finishedAt),
                    )
                }
            } else {
                val id = if (books.any { it.id == b.id }) java.util.UUID.randomUUID().toString().take(12) else b.id
                idMap[b.id] = id
                src.copyTo(File(Store.booksDir, "$id.${b.format}"), true)
                File(tmp, "covers/${b.id}.jpg").takeIf { it.exists() }?.copyTo(File(Store.coversDir, "$id.jpg"), true)
                books += b.copy(id = id)
            }
        }
        // highlights and notes, without duplicates
        for (h in inHl) {
            val bid = idMap[h.bookId] ?: continue
            if (hl.any { it.id == h.id }) continue
            if (hl.any { it.bookId == bid && it.para == h.para && it.start == h.start && it.end == h.end && it.text == h.text }) continue
            File(tmp, "clips/${h.id}.webp").takeIf { it.exists() }?.copyTo(File(Store.clipsDir, "${h.id}.webp"), true)
            hl += h.copy(bookId = bid)
        }
        for (m in inMarks) {
            val bid = idMap[m.bookId] ?: continue
            if (marks.any { it.id == m.id || (it.bookId == bid && it.para == m.para && it.char == m.char) }) continue
            marks += m.copy(bookId = bid)
        }
        // the newer review wins
        for (r in inReviews) {
            val bid = idMap[r.bookId] ?: continue
            val cur = reviews[bid]
            if (cur == null || r.updatedAt > cur.updatedAt) reviews[bid] = r.copy(bookId = bid)
        }
        Store.writeAtomic("library.json", JSONArray().apply { books.forEach { put(it.toJson()) } }.toString())
        Store.writeAtomic("highlights.json", JSONArray().apply { hl.forEach { put(it.toJson()) } }.toString())
        Store.writeAtomic("bookmarks.json", JSONArray().apply { marks.forEach { put(it.toJson()) } }.toString())
        Store.writeAtomic("reviews.json", JSONArray().apply { reviews.values.forEach { put(it.toJson()) } }.toString())

        // reading history: keep the bigger number for each day; badges: everything seen on either phone
        val ed = Store.prefs.edit()
        prefs.optJSONObject("daily")?.optString("v")?.let { s ->
            val incoming = runCatching { JSONObject(s) }.getOrNull()
            val here = runCatching { JSONObject(Store.prefs.getString("daily", "{}")!!) }.getOrDefault(JSONObject())
            incoming?.keys()?.forEach { d -> here.put(d, maxOf(here.optLong(d), incoming.optLong(d))) }
            ed.putString("daily", here.toString())
        }
        prefs.optJSONObject("totalReadMs")?.optLong("v")?.let { ed.putLong("totalReadMs", maxOf(it, Store.prefs.getLong("totalReadMs", 0L))) }
        prefs.optJSONObject("seenBadges")?.optJSONArray("v")?.let { a ->
            ed.putStringSet("seenBadges", Store.prefs.getStringSet("seenBadges", emptySet())!! + (0 until a.length()).map { a.getString(it) })
        }
        ed.commit()
        // the wallpaper only comes across if this phone doesn't have one
        if (!File(files, "wallpaper.jpg").exists()) File(tmp, "wallpaper").listFiles()?.forEach { it.copyTo(File(files, it.name), true) }
    }
}
