package com.aniq.glassreader.data

import org.json.JSONArray
import org.json.JSONObject

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: String,          // "epub" | "txt" | "pdf"
    val hasCover: Boolean,
    val progressIndex: Int = 0,  // paragraph (text) or page (pdf)
    val progressOffset: Int = 0, // character offset within the paragraph
    val totalParas: Int = 0,     // paragraphs (text) or pages (pdf)
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpened: Long = 0L,
    val readMs: Long = 0L,       // time spent reading this book
    val chapter: String = "",    // title of the chapter the reader is in, for widgets
) {
    val isPdf get() = format == "pdf"
    val progress: Float
        get() = if (totalParas <= 1) 0f else (progressIndex.toFloat() / (totalParas - 1)).coerceIn(0f, 1f)

    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("author", author); put("format", format)
        put("hasCover", hasCover); put("pi", progressIndex); put("po", progressOffset)
        put("tp", totalParas); put("added", addedAt); put("opened", lastOpened)
        put("readMs", readMs); put("ch", chapter)
    }

    companion object {
        fun fromJson(o: JSONObject) = Book(
            id = o.getString("id"),
            title = o.optString("title", "Untitled"),
            author = o.optString("author", ""),
            format = o.optString("format", "epub"),
            hasCover = o.optBoolean("hasCover", false),
            progressIndex = o.optInt("pi", 0),
            progressOffset = o.optInt("po", 0),
            totalParas = o.optInt("tp", 0),
            addedAt = o.optLong("added", 0L),
            lastOpened = o.optLong("opened", 0L),
            readMs = o.optLong("readMs", 0L),
            chapter = o.optString("ch", ""),
        )
    }
}

/**
 * A highlight is either a text range in a paragraph, or (for PDFs) a framed
 * region of a page, stored as a normalised rect plus a cropped image.
 */
data class Highlight(
    val id: String,
    val bookId: String,
    val para: Int,               // paragraph index, or page index for clips
    val start: Int,
    val end: Int,
    val text: String,
    val color: Int,
    val chapter: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val rect: FloatArray? = null, // PDF only: one or more l,t,r,b boxes in 0..1 page space
) {
    /** A framed picture of a page region (scanned PDFs), rather than selected text. */
    val isClip get() = rect != null && text.isEmpty()

    fun toJson() = JSONObject().apply {
        put("id", id); put("book", bookId); put("para", para); put("s", start); put("e", end)
        put("text", text); put("color", color); put("chapter", chapter)
        put("fav", favorite); put("at", createdAt)
        rect?.let { r -> put("rect", JSONArray().apply { r.forEach { put(it.toDouble()) } }) }
    }

    override fun equals(other: Any?) = other is Highlight && other.id == id && other.color == color &&
        other.favorite == favorite && other.start == start && other.end == end && other.text == text
    override fun hashCode() = id.hashCode()

    companion object {
        fun fromJson(o: JSONObject) = Highlight(
            id = o.getString("id"),
            bookId = o.getString("book"),
            para = o.getInt("para"),
            start = o.getInt("s"),
            end = o.getInt("e"),
            text = o.optString("text"),
            color = o.optInt("color", 0),
            chapter = o.optString("chapter"),
            favorite = o.optBoolean("fav", false),
            createdAt = o.optLong("at", 0L),
            rect = o.optJSONArray("rect")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } },
        )
    }
}

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.6f,
    val theme: Int = 0,          // index into ReaderThemes (0 White, 5 Black)
    val serif: Boolean = false,
    val justify: Boolean = true,
    val margin: Int = 18,
    val grain: Boolean = false,  // paper texture
    val mono: Boolean = false,   // black & white mode
    val wallDim: Float = 0.45f,  // darkening over a custom wallpaper
    val wallInReader: Boolean = false,
    val grainAmount: Float = 0.6f,
    val pdfMode: Int = 0,        // 0 original, 1 match page colour, 2 night (inverted)
    val haptics: Boolean = true,
    val hapticLevel: Int = 2,    // 0 light, 1 medium, 2 strong
    val volumeKeys: Boolean = true,
    val pageAnim: Int = 0,       // 0 slide, 1 fade
    val coverScreen: Boolean = false,
    val uiMode: Int = 0,         // 0 system, 1 light, 2 dark
)

/** Para styles are packed triples: start, end, type (1 = italic, 2 = bold). */
class Para(val text: String, val heading: Boolean, val styles: IntArray)

class Chapter(val title: String, val para: Int)

/** Style type ≥ LINK_BASE marks an internal link; (type - LINK_BASE) indexes [BookContent.links]. */
const val LINK_BASE = 100

class BookContent(val paras: List<Para>, val chapters: List<Chapter>, val links: IntArray = IntArray(0)) {
    fun chapterAt(para: Int): Chapter? {
        var lo = 0; var hi = chapters.size - 1; var ans: Chapter? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (chapters[mid].para <= para) { ans = chapters[mid]; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
