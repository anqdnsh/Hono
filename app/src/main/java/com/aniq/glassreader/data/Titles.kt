package com.aniq.glassreader.data

/** Turns messy file names into clean book titles. */
object Titles {
    private val ext = Regex("""\.(epub|pdf|txt|mobi|azw3?)$""", RegexOption.IGNORE_CASE)
    private val brackets = Regex("""\s*[\(\[\{][^\)\]\}]*(z-?lib|libgen|anna|pdf|epub|ebook|www\.|\.com|\.org|edition|retail|ocr|scan)[^\)\]\}]*[\)\]\}]""", RegexOption.IGNORE_CASE)
    private val numberBits = Regex("""(?<![A-Za-z])[\d][\d\-_.]{3,}\d(?![A-Za-z])""")   // ids, ISBNs, timestamps
    private val hashes = Regex("""\b(?=[0-9a-fA-F]*\d)[0-9a-fA-F]{8,}\b""")
    private val emptyBrackets = Regex("""[\(\[\{]\s*[\)\]\}]""")
    private val spaces = Regex("""\s{2,}""")

    fun clean(raw: String): String {
        var s = raw.trim().replace(ext, "")
        s = s.replace('_', ' ').replace(Regex("""(?<=\S)\.(?=\S)(?!\d)"""), " ")
        s = s.replace(brackets, "").replace(hashes, "").replace(numberBits, "")
        s = s.replace(emptyBrackets, "").replace(spaces, " ")
        s = s.trim(' ', '-', '–', '—', ',', '.', '#', '+')
        return s.ifBlank { raw.replace(ext, "").trim() }
    }

    /** "Title by Author" → (Title, Author) when the author is unknown. */
    fun split(title: String, author: String): Pair<String, String> {
        if (author.isNotBlank()) return title to author
        val i = title.lastIndexOf(" by ", ignoreCase = true)
        if (i > 2 && title.length - i in 6..60) return title.substring(0, i).trim() to title.substring(i + 4).trim()
        return title to author
    }
}
