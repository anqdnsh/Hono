package com.aniq.glassreader.data

import org.json.JSONObject

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: String,          // "epub" | "txt"
    val hasCover: Boolean,
    val progressIndex: Int = 0,
    val progressOffset: Int = 0,
    val totalParas: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpened: Long = 0L,
) {
    val progress: Float
        get() = if (totalParas <= 1) 0f else (progressIndex.toFloat() / (totalParas - 1)).coerceIn(0f, 1f)

    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("author", author); put("format", format)
        put("hasCover", hasCover); put("pi", progressIndex); put("po", progressOffset)
        put("tp", totalParas); put("added", addedAt); put("opened", lastOpened)
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
        )
    }
}

data class Highlight(
    val id: String,
    val bookId: String,
    val para: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val color: Int,              // index into HighlightColors
    val chapter: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("book", bookId); put("para", para); put("s", start); put("e", end)
        put("text", text); put("color", color); put("chapter", chapter)
        put("fav", favorite); put("at", createdAt)
    }

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
        )
    }
}

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.6f,
    val theme: Int = 2,          // 0 Paper, 1 Sepia, 2 Night, 3 Black
    val serif: Boolean = true,
    val justify: Boolean = true,
    val margin: Int = 24,
)

/** Para styles are packed triples: start, end, type (1 = italic, 2 = bold). */
class Para(val text: String, val heading: Boolean, val styles: IntArray)

class Chapter(val title: String, val para: Int)

class BookContent(val paras: List<Para>, val chapters: List<Chapter>) {
    fun chapterAt(para: Int): Chapter? {
        var lo = 0; var hi = chapters.size - 1; var ans: Chapter? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (chapters[mid].para <= para) { ans = chapters[mid]; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
