package com.aniq.glassreader.parser

import com.aniq.glassreader.data.BookContent
import com.aniq.glassreader.data.Chapter
import com.aniq.glassreader.data.Para
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

class ParsedBook(val title: String, val author: String, val content: BookContent, val cover: ByteArray?)

/**
 * A deliberately small, dependency-free parser. EPUBs are zip files of XHTML;
 * we walk the spine and turn each document into plain paragraphs with
 * italic/bold ranges. Images, CSS and scripts are ignored to keep it light.
 */
object BookParser {

    fun parse(file: File, format: String, fallbackTitle: String): ParsedBook =
        if (format == "txt") parseTxt(file, fallbackTitle) else parseEpub(file, fallbackTitle)

    // ---------------- EPUB ----------------

    private val attrRe = Regex("""([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    private fun attrs(tag: String): Map<String, String> =
        attrRe.findAll(tag).associate { m -> m.groupValues[1].lowercase() to (m.groups[2]?.value ?: m.groups[3]?.value ?: "") }

    private fun resolve(base: String, href: String): String {
        val clean = try { URLDecoder.decode(href.substringBefore('#'), "UTF-8") } catch (e: Exception) { href.substringBefore('#') }
        val parts = ArrayList<String>()
        if (base.isNotEmpty()) parts.addAll(base.split('/'))
        for (p in clean.split('/')) when (p) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(p)
        }
        return parts.joinToString("/")
    }

    private fun parseEpub(file: File, fallbackTitle: String): ParsedBook {
        ZipFile(file).use { zip ->
            fun read(path: String): String? {
                val e = zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, true) } ?: return null
                return zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
            }
            fun readBytes(path: String): ByteArray? {
                val e = zip.getEntry(path) ?: return null
                return zip.getInputStream(e).use { it.readBytes() }
            }

            val container = read("META-INF/container.xml") ?: error("Not an EPUB")
            val opfPath = Regex("""full-path\s*=\s*"([^"]+)"""").find(container)?.groupValues?.get(1) ?: error("No OPF")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = read(opfPath) ?: error("Missing OPF")

            val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL).find(opf)
                ?.groupValues?.get(1)?.let { decodeEntities(stripTags(it)).trim() }?.takeIf { it.isNotBlank() }
                ?: fallbackTitle.substringBeforeLast('.')
            val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL).find(opf)
                ?.groupValues?.get(1)?.let { decodeEntities(stripTags(it)).trim() } ?: ""

            // manifest
            class Item(val id: String, val href: String, val type: String, val props: String)
            val items = Regex("""<(?:\w+:)?item\b[^>]*>""").findAll(opf).map { attrs(it.value) }.mapNotNull { a ->
                val id = a["id"] ?: return@mapNotNull null
                Item(id, resolve(opfDir, a["href"] ?: return@mapNotNull null), a["media-type"] ?: "", a["properties"] ?: "")
            }.toList()
            val byId = items.associateBy { it.id }

            val spineTag = Regex("""<(?:\w+:)?spine\b[^>]*>""").find(opf)?.value ?: ""
            val spine = Regex("""<(?:\w+:)?itemref\b[^>]*>""").findAll(opf).map { attrs(it.value) }
                .filter { it["linear"] != "no" }.mapNotNull { byId[it["idref"]] }.toList()

            // cover
            val coverId = Regex("""<meta\b[^>]*>""").findAll(opf).map { attrs(it.value) }
                .firstOrNull { it["name"] == "cover" }?.get("content")
            val coverItem = items.firstOrNull { it.props.contains("cover-image") }
                ?: coverId?.let { byId[it] }?.takeIf { it.type.startsWith("image") }
                ?: items.firstOrNull { it.type.startsWith("image") && it.id.contains("cover", true) }
                ?: items.firstOrNull { it.type.startsWith("image") && it.href.contains("cover", true) }
            var cover = coverItem?.let { readBytes(it.href) }
            if (cover == null) {
                // Fallback: the first image referenced by the opening pages (usually cover.xhtml)
                val guideHref = Regex("""<reference\b[^>]*type\s*=\s*"cover"[^>]*>""").find(opf)?.value
                    ?.let { attrs(it)["href"] }?.let { resolve(opfDir, it) }
                val candidates = listOfNotNull(guideHref) + spine.take(3).map { it.href }
                for (doc in candidates) {
                    val html = read(doc) ?: continue
                    val docDir = doc.substringBeforeLast('/', "")
                    val src = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value?.let { attrs(it)["src"] }
                        ?: Regex("""<image\b[^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value
                            ?.let { a -> attrs(a).let { it["xlink:href"] ?: it["href"] } }
                    if (src != null) { cover = readBytes(resolve(docDir, src)); if (cover != null) break }
                }
            }
            if (cover == null) {
                // Last resort: the largest image in the book
                cover = items.filter { it.type.startsWith("image") }
                    .maxByOrNull { zip.getEntry(it.href)?.size ?: 0L }?.let { readBytes(it.href) }
            }

            // table of contents: file -> title (first entry per file wins)
            val toc = HashMap<String, String>()
            val navItem = items.firstOrNull { it.props.split(' ').contains("nav") }
            val ncxItem = attrs(spineTag)["toc"]?.let { byId[it] } ?: items.firstOrNull { it.type == "application/x-dtbncx+xml" }
            if (navItem != null) {
                read(navItem.href)?.let { nav ->
                    val navDir = navItem.href.substringBeforeLast('/', "")
                    val tocBlock = Regex("""<nav\b[^>]*toc[^>]*>(.*?)</nav>""", RegexOption.DOT_MATCHES_ALL).find(nav)?.groupValues?.get(1) ?: nav
                    Regex("""<a\b([^>]*)>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(tocBlock).forEach { m ->
                        val href = attrs(m.groupValues[1])["href"] ?: return@forEach
                        val t = decodeEntities(stripTags(m.groupValues[2])).replace(Regex("\\s+"), " ").trim()
                        if (t.isNotEmpty()) toc.putIfAbsent(resolve(navDir, href), t)
                    }
                }
            }
            if (toc.isEmpty() && ncxItem != null) {
                read(ncxItem.href)?.let { ncx ->
                    val ncxDir = ncxItem.href.substringBeforeLast('/', "")
                    Regex("""<navLabel>\s*<text>(.*?)</text>\s*</navLabel>\s*<content\b([^>]*)/?>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(ncx).forEach { m ->
                            val src = attrs(m.groupValues[2])["src"] ?: return@forEach
                            val t = decodeEntities(stripTags(m.groupValues[1])).trim()
                            if (t.isNotEmpty()) toc.putIfAbsent(resolve(ncxDir, src), t)
                        }
                }
            }

            val paras = ArrayList<Para>(4096)
            val chapters = ArrayList<Chapter>()
            for (item in spine) {
                if (!item.type.contains("html") && !item.href.endsWith("html", true) && !item.href.endsWith("htm", true)) continue
                val html = read(item.href) ?: continue
                val startIdx = paras.size
                HtmlText.toParas(html, paras)
                if (paras.size == startIdx) continue
                val t = toc[item.href]
                    ?: paras[startIdx].takeIf { it.heading && it.text.length < 80 }?.text
                if (t != null) chapters.add(Chapter(t, startIdx))
            }
            if (paras.isEmpty()) error("This EPUB has no readable text")
            if (chapters.isEmpty() || chapters[0].para != 0) chapters.add(0, Chapter(title, 0))
            return ParsedBook(title, author, BookContent(paras, chapters), cover)
        }
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]*>"), "")

    // ---------------- TXT ----------------

    private val chapterRe = Regex("""^(chapter|part|book|prologue|epilogue|bab)\b.{0,60}$|^[IVXLC]+\.?$""", RegexOption.IGNORE_CASE)

    private fun parseTxt(file: File, name: String): ParsedBook {
        var text = file.readBytes().toString(Charsets.UTF_8)
        if (text.startsWith('﻿')) text = text.substring(1)
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = if (text.contains("\n\n")) text.split(Regex("\\n\\s*\\n")) else text.split('\n')
        val paras = ArrayList<Para>()
        val chapters = ArrayList<Chapter>()
        for (b in blocks) {
            val t = b.replace(Regex("\\s*\\n\\s*"), " ").replace(Regex("[ \\t]+"), " ").trim()
            if (t.isEmpty()) continue
            val heading = t.length < 70 && chapterRe.matches(t)
            if (heading) chapters.add(Chapter(t, paras.size))
            paras.add(Para(t, heading, IntArray(0)))
        }
        if (paras.isEmpty()) error("Empty file")
        val title = name.substringBeforeLast('.')
        if (chapters.isEmpty() || chapters[0].para != 0) chapters.add(0, Chapter(title, 0))
        return ParsedBook(title, "", BookContent(paras, chapters), null)
    }

    // ---------------- Binary cache (fast re-open) ----------------

    private const val MAGIC = 0x47524331 // "GRC1"

    fun writeCache(c: BookContent, f: File) {
        try {
            DataOutputStream(BufferedOutputStream(f.outputStream(), 64 * 1024)).use { o ->
                o.writeInt(MAGIC)
                o.writeInt(c.paras.size)
                for (p in c.paras) {
                    val b = p.text.toByteArray(Charsets.UTF_8)
                    o.writeInt(b.size); o.write(b)
                    o.writeBoolean(p.heading)
                    o.writeInt(p.styles.size); p.styles.forEach { o.writeInt(it) }
                }
                o.writeInt(c.chapters.size)
                for (ch in c.chapters) {
                    val b = ch.title.toByteArray(Charsets.UTF_8)
                    o.writeInt(b.size); o.write(b); o.writeInt(ch.para)
                }
            }
        } catch (e: Exception) { f.delete() }
    }

    fun readCache(f: File): BookContent? {
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(f.inputStream(), 64 * 1024)).use { i ->
                if (i.readInt() != MAGIC) return null
                val n = i.readInt()
                val paras = ArrayList<Para>(n)
                repeat(n) {
                    val b = ByteArray(i.readInt()); i.readFully(b)
                    val heading = i.readBoolean()
                    val s = IntArray(i.readInt()) { i.readInt() }
                    paras.add(Para(String(b, Charsets.UTF_8), heading, s))
                }
                val cn = i.readInt()
                val chapters = ArrayList<Chapter>(cn)
                repeat(cn) {
                    val b = ByteArray(i.readInt()); i.readFully(b)
                    chapters.add(Chapter(String(b, Charsets.UTF_8), i.readInt()))
                }
                BookContent(paras, chapters)
            }
        } catch (e: Exception) { null }
    }

    // ---------------- Entities ----------------

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "copy" to "©",
        "reg" to "®", "trade" to "™", "shy" to "", "zwj" to "", "zwnj" to "", "thinsp" to " ",
        "ensp" to " ", "emsp" to " ", "eacute" to "é", "egrave" to "è", "agrave" to "à",
        "aacute" to "á", "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "ntilde" to "ñ",
        "ccedil" to "ç", "uuml" to "ü", "ouml" to "ö", "auml" to "ä", "szlig" to "ß",
        "middot" to "·", "bull" to "•", "deg" to "°", "times" to "×", "prime" to "′",
    )

    fun entity(name: String): String? {
        if (name.startsWith("#")) {
            val code = try {
                if (name.length > 1 && (name[1] == 'x' || name[1] == 'X')) name.substring(2).toInt(16) else name.substring(1).toInt()
            } catch (e: Exception) { return null }
            return if (code in 1..0x10FFFF) String(Character.toChars(code)) else null
        }
        return named[name]
    }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i)
                if (semi in (i + 2)..(i + 10)) {
                    val rep = entity(s.substring(i + 1, semi))
                    if (rep != null) { sb.append(rep); i = semi + 1; continue }
                }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }
}

/** Streaming HTML → paragraphs converter. Tolerant of sloppy markup. */
object HtmlText {
    private val blockTags = hashSetOf(
        "p", "div", "li", "blockquote", "section", "article", "tr", "dd", "dt", "pre", "figure",
        "figcaption", "hr", "table", "ul", "ol", "header", "footer", "aside", "h1", "h2", "h3",
        "h4", "h5", "h6", "body", "td", "th",
    )
    private val skipTags = hashSetOf("script", "style", "head", "title", "svg", "math")
    private val italicTags = hashSetOf("i", "em", "cite", "var", "dfn")
    private val boldTags = hashSetOf("b", "strong")

    fun toParas(html: String, out: MutableList<Para>) {
        val bodyAt = html.indexOf("<body", ignoreCase = true)
        var i = if (bodyAt >= 0) bodyAt else 0
        val n = html.length
        val sb = StringBuilder(512)
        val styles = ArrayList<Int>()
        var italicDepth = 0; var italicStart = 0
        var boldDepth = 0; var boldStart = 0
        var heading = false

        fun flush() {
            // trim trailing whitespace
            var end = sb.length
            while (end > 0 && sb[end - 1].isWhitespace()) end--
            if (italicDepth > 0 && end > italicStart) { styles.add(italicStart); styles.add(end); styles.add(1) }
            if (boldDepth > 0 && end > boldStart) { styles.add(boldStart); styles.add(end); styles.add(2) }
            if (end > 0) {
                val text = sb.substring(0, end)
                val arr = ArrayList<Int>(styles.size)
                var k = 0
                while (k + 2 < styles.size) {
                    val s = styles[k].coerceIn(0, end); val e = styles[k + 1].coerceIn(0, end)
                    if (e > s) { arr.add(s); arr.add(e); arr.add(styles[k + 2]) }
                    k += 3
                }
                if (text.isNotBlank()) out.add(Para(text, heading, arr.toIntArray()))
            }
            sb.setLength(0); styles.clear()
            italicStart = 0; boldStart = 0
        }

        fun appendSpace() {
            if (sb.isNotEmpty()) { val last = sb[sb.length - 1]; if (last != ' ' && last != '\n') sb.append(' ') }
        }

        while (i < n) {
            val c = html[i]
            when {
                c == '<' -> {
                    if (html.startsWith("<!--", i)) {
                        val e = html.indexOf("-->", i + 4); i = if (e < 0) n else e + 3; continue
                    }
                    if (html.startsWith("<![CDATA[", i)) {
                        val e = html.indexOf("]]>", i); i = if (e < 0) n else e + 3; continue
                    }
                    val close = html.indexOf('>', i)
                    if (close < 0) { i = n; continue }
                    val tag = html.substring(i + 1, close)
                    i = close + 1
                    val isEnd = tag.startsWith("/")
                    val raw = tag.trimStart('/', '!', '?').takeWhile { it.isLetterOrDigit() || it == ':' }.lowercase()
                    val name = raw.substringAfter(':')
                    val selfClose = tag.endsWith("/")
                    when {
                        name in skipTags && !isEnd && !selfClose -> {
                            val e = html.indexOf("</$raw", i, ignoreCase = true)
                            i = if (e < 0) n else (html.indexOf('>', e).let { if (it < 0) n else it + 1 })
                        }
                        name == "br" -> { if (sb.isNotEmpty()) sb.append('\n') }
                        name in blockTags -> {
                            flush()
                            heading = !isEnd && name.length == 2 && name[0] == 'h' && name[1] in '1'..'6'
                            if (isEnd) heading = false
                        }
                        name in italicTags -> if (!isEnd) { if (italicDepth++ == 0) italicStart = sb.length }
                            else if (italicDepth > 0) { if (--italicDepth == 0 && sb.length > italicStart) { styles.add(italicStart); styles.add(sb.length); styles.add(1) } }
                        name in boldTags -> if (!isEnd) { if (boldDepth++ == 0) boldStart = sb.length }
                            else if (boldDepth > 0) { if (--boldDepth == 0 && sb.length > boldStart) { styles.add(boldStart); styles.add(sb.length); styles.add(2) } }
                    }
                }
                c == '&' -> {
                    val semi = html.indexOf(';', i)
                    val rep = if (semi in (i + 2)..(i + 10)) BookParser.entity(html.substring(i + 1, semi)) else null
                    if (rep != null) {
                        if (rep == " ") appendSpace() else sb.append(rep)
                        i = semi + 1
                    } else { sb.append('&'); i++ }
                }
                c.isWhitespace() -> { appendSpace(); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        flush()
    }
}
