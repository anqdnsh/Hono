package com.aniq.glassreader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * "Book with highlights": a copy of the book with your highlights marked right in the pages,
 * in their own colours, so it opens that way in any other reading app.
 *  - EPUB: highlights become coloured <mark>s in the text; notes become numbered footnotes at
 *    the end of each chapter. Each highlight is found by its words, in reading order.
 *  - TXT: becomes a simple EPUB with the highlights marked.
 *  - PDF: real highlight annotations on the page (notes attached), visible in any PDF app.
 */
object MarkedExport {
    // the five highlighter colours, as 0..1 RGB
    private val rgb = listOf(
        floatArrayOf(1f, 0.839f, 0.039f),   // yellow
        floatArrayOf(0.188f, 0.82f, 0.345f), // green
        floatArrayOf(0.392f, 0.824f, 1f),   // blue
        floatArrayOf(1f, 0.392f, 0.51f),    // pink
        floatArrayOf(0.749f, 0.353f, 0.949f), // purple
    )
    private fun css(c: Int): String {
        val v = rgb[c.coerceIn(0, rgb.lastIndex)]
        return "background-color: rgba(${(v[0] * 255).toInt()}, ${(v[1] * 255).toInt()}, ${(v[2] * 255).toInt()}, 0.45); color: inherit;"
    }

    /** File extension of the marked copy. */
    fun extensionFor(b: Book) = if (b.isPdf) "pdf" else "epub"

    suspend fun write(context: Context, book: Book, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val hs = Store.highlights.value.filter { it.bookId == book.id }.sortedWith(compareBy({ it.para }, { it.start }))
        when (book.format) {
            "pdf" -> pdf(context, book, hs, out)
            "txt" -> txt(book, hs, out)
            else -> epub(book, hs, out)
        }
    }

    // ---------------- EPUB ----------------

    private fun epub(book: Book, hs: List<Highlight>, out: OutputStream): Int {
        val file = Store.bookFile(book)
        var marked = 0
        ZipFile(file).use { zip ->
            fun text(name: String) = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } }
            val container = text("META-INF/container.xml") ?: error("Not an EPUB")
            val opfPath = Jsoup.parse(container, "", Parser.xmlParser()).selectFirst("rootfile")?.attr("full-path") ?: error("No OPF")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = Jsoup.parse(text(opfPath) ?: error("Missing OPF"), "", Parser.xmlParser())
            val items = opf.select("manifest > item, item").associate { it.attr("id") to it.attr("href") }
            val spine = opf.select("spine > itemref, itemref").mapNotNull { items[it.attr("idref")] }
                .map { href -> resolve(opfDir, java.net.URLDecoder.decode(href, "UTF-8")) }

            // mark each spine document, keeping a cursor so repeated phrases land in the right place
            val changed = HashMap<String, ByteArray>()
            val docs = spine.mapNotNull { name -> text(name)?.let { name to Jsoup.parse(it, "", Parser.xmlParser()) } }
            // marking never changes the words, so each document's flat text is worked out once
            val flats = docs.map { flatten(body(it.second)).first }
            var di = 0; var pos = 0
            var note = 0
            val notesFor = HashMap<String, MutableList<Pair<Int, String>>>()
            for (h in hs) {
                val needle = norm(h.text)
                if (needle.length < 2 || h.isClip) continue
                var hit: Triple<Int, Int, Int>? = null  // doc index, start, end in that doc's flat text
                // search forward from the cursor, then from the start as a fallback
                loop@ for (pass in 0..1) {
                    val from = if (pass == 0) di else 0
                    for (d in from until docs.size) {
                        val i = flats[d].indexOf(needle, if (pass == 0 && d == di) pos else 0)
                        if (i >= 0) { hit = Triple(d, i, i + needle.length); break@loop }
                    }
                }
                val (d, s, e) = hit ?: continue
                val (name, doc) = docs[d]
                val last = wrap(body(doc), s, e, css(h.color))
                if (last != null) {
                    marked++
                    if (h.note.isNotBlank()) {
                        note++
                        val n = note
                        last.after("<sup class=\"hono-note\"><a href=\"#hono-note-$n\" id=\"hono-ref-$n\">$n</a></sup>")
                        notesFor.getOrPut(name) { mutableListOf() } += n to h.note.trim()
                    }
                }
                di = d; pos = e
            }
            for ((name, doc) in docs) {
                notesFor[name]?.let { list ->
                    val b = body(doc)
                    val box = b.appendElement("div").attr("class", "hono-notes")
                        .attr("style", "margin-top: 2em; border-top: 1px solid #ccc; padding-top: 0.6em; font-size: 0.85em;")
                    list.forEach { (n, t) ->
                        box.appendElement("p").attr("id", "hono-note-$n")
                            .appendElement("a").attr("href", "#hono-ref-$n").text("$n").parent()!!
                            .appendText(" Your note: $t")
                    }
                }
                if (doc.select("mark.hono-hl").isNotEmpty() || notesFor.containsKey(name)) {
                    doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml)
                        .prettyPrint(false).charset(Charsets.UTF_8)
                    changed[name] = doc.outerHtml().toByteArray(Charsets.UTF_8)
                }
            }
            // repack: "mimetype" must come first and uncompressed
            ZipOutputStream(out.buffered()).use { z ->
                zip.getEntry("mimetype")?.let { e ->
                    val bytes = zip.getInputStream(e).use { it.readBytes() }
                    val entry = ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                    z.putNextEntry(entry); z.write(bytes); z.closeEntry()
                }
                for (e in zip.entries()) {
                    if (e.name == "mimetype" || e.isDirectory) continue
                    z.putNextEntry(ZipEntry(e.name))
                    val c = changed[e.name]
                    if (c != null) z.write(c) else zip.getInputStream(e).use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return marked
    }

    private fun resolve(dir: String, href: String): String {
        val parts = (if (dir.isEmpty()) href else "$dir/$href").split('/').fold(ArrayList<String>()) { acc, p ->
            when (p) { "", "." -> {}; ".." -> if (acc.isNotEmpty()) acc.removeAt(acc.lastIndex); else -> acc.add(p) }; acc
        }
        return parts.joinToString("/")
    }

    private fun body(doc: Document): Element = doc.selectFirst("body") ?: doc

    private fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim()

    /** All text under [root] with whitespace collapsed, and where each character came from. */
    private fun flatten(root: Element): Pair<String, List<Pair<TextNode, Int>>> {
        val sb = StringBuilder()
        val map = ArrayList<Pair<TextNode, Int>>()
        var lastSpace = true
        fun walk(n: org.jsoup.nodes.Node) {
            if (n is TextNode) {
                val t = n.wholeText
                for (i in t.indices) {
                    val ch = t[i]
                    if (ch.isWhitespace()) { if (!lastSpace) { sb.append(' '); map += n to i; lastSpace = true } }
                    else { sb.append(ch); map += n to i; lastSpace = false }
                }
            } else n.childNodes().forEach { walk(it) }
        }
        walk(root)
        return sb.toString() to map
    }

    /** Wraps flat-text range [s, e) in coloured marks, one per text node it crosses. Returns the last mark. */
    private fun wrap(root: Element, s: Int, e: Int, style: String): Element? {
        val (_, map) = flatten(root)
        if (s >= map.size) return null
        val end = (e - 1).coerceAtMost(map.lastIndex)
        // group the range by text node, in order
        val spans = LinkedHashMap<TextNode, IntArray>()
        for (k in s..end) {
            val (node, off) = map[k]
            val r = spans.getOrPut(node) { intArrayOf(off, off) }
            r[0] = minOf(r[0], off); r[1] = maxOf(r[1], off)
        }
        var last: Element? = null
        for ((node, r) in spans) {
            var target = node
            if (r[1] + 1 < target.wholeText.length) target.splitText(r[1] + 1)
            if (r[0] > 0) target = target.splitText(r[0])
            val mark = Element("mark").attr("class", "hono-hl").attr("style", style)
            target.replaceWith(mark)
            mark.appendChild(target)
            last = mark
        }
        return last
    }

    // ---------------- TXT → EPUB ----------------

    private suspend fun txt(book: Book, hs: List<Highlight>, out: OutputStream): Int {
        val c = Store.content(book)
        val byPara = hs.groupBy { it.para }
        val body = StringBuilder()
        var note = 0
        val notes = StringBuilder()
        c.paras.forEachIndexed { i, p ->
            val t = p.text
            val tag = if (p.heading) "h2" else "p"
            body.append("<$tag>")
            var at = 0
            for (h in (byPara[i] ?: emptyList()).sortedBy { it.start }) {
                val a = h.start.coerceIn(at, t.length); val b = h.end.coerceIn(a, t.length)
                body.append(esc(t.substring(at, a)))
                body.append("<mark style=\"").append(css(h.color)).append("\">").append(esc(t.substring(a, b))).append("</mark>")
                if (h.note.isNotBlank()) {
                    note++
                    body.append("<sup><a href=\"#n$note\" id=\"r$note\">$note</a></sup>")
                    notes.append("<p id=\"n$note\"><a href=\"#r$note\">$note</a> Your note: ").append(esc(h.note.trim())).append("</p>")
                }
                at = b
            }
            body.append(esc(t.substring(at))).append("</$tag>\n")
        }
        if (notes.isNotEmpty()) body.append("<div style=\"margin-top:2em;border-top:1px solid #ccc;font-size:0.85em\">").append(notes).append("</div>")
        val title = esc(book.title)
        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head><body><h1>$title</h1>
$body</body></html>"""
        val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">hono-${book.id}</dc:identifier><dc:title>$title</dc:title><dc:creator>${esc(book.author)}</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata>
<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="text" href="text.xhtml" media-type="application/xhtml+xml"/></manifest>
<spine><itemref idref="text"/></spine></package>"""
        val nav = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>$title</title></head><body><nav epub:type="toc"><ol><li><a href="text.xhtml">$title</a></li></ol></nav></body></html>"""
        val container = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
        ZipOutputStream(out.buffered()).use { z ->
            val mt = "application/epub+zip".toByteArray()
            z.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mt.size.toLong(); compressedSize = mt.size.toLong(); crc = CRC32().apply { update(mt) }.value
            }); z.write(mt); z.closeEntry()
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("META-INF/container.xml", container)
            put("OEBPS/content.opf", opf)
            put("OEBPS/nav.xhtml", nav)
            put("OEBPS/text.xhtml", xhtml)
        }
        return hs.size
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---------------- PDF ----------------

    private fun pdf(context: Context, book: Book, hs: List<Highlight>, out: OutputStream): Int {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        var marked = 0
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(Store.bookFile(book)).use { doc ->
            for (h in hs) {
                val r = h.rect ?: continue
                if (h.para !in 0 until doc.numberOfPages || r.size < 4) continue
                val page = doc.getPage(h.para)
                val box = page.cropBox
                val w = box.width; val ht = box.height; val x0 = box.lowerLeftX; val y0 = box.lowerLeftY
                val rot = ((page.rotation % 360) + 360) % 360
                // our boxes are 0..1 in the page as it's shown (rotation applied); PDF space starts bottom-left
                fun toPdf(u: Float, v: Float): Pair<Float, Float> = when (rot) {
                    90 -> (x0 + v * w) to (y0 + u * ht)
                    180 -> (x0 + (1 - u) * w) to (y0 + v * ht)
                    270 -> (x0 + (1 - v) * w) to (y0 + (1 - u) * ht)
                    else -> (x0 + u * w) to (y0 + (1 - v) * ht)
                }
                val quads = ArrayList<Float>()
                var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                var k = 0
                while (k + 3 < r.size) {
                    val a = toPdf(r[k], r[k + 1]); val b = toPdf(r[k + 2], r[k + 3])
                    val lx = minOf(a.first, b.first); val rx = maxOf(a.first, b.first)
                    val by = minOf(a.second, b.second); val ty = maxOf(a.second, b.second)
                    quads.addAll(listOf(lx, ty, rx, ty, lx, by, rx, by))
                    minX = minOf(minX, lx); minY = minOf(minY, by); maxX = maxOf(maxX, rx); maxY = maxOf(maxY, ty)
                    k += 4
                }
                if (quads.isEmpty()) continue
                val ann = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT,
                )
                ann.rectangle = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(minX, minY, maxX - minX, maxY - minY)
                ann.quadPoints = quads.toFloatArray()
                ann.color = com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                    rgb[h.color.coerceIn(0, rgb.lastIndex)], com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE,
                )
                ann.constantOpacity = 0.5f
                if (h.note.isNotBlank()) ann.contents = h.note.trim()
                ann.titlePopup = "Hono"
                runCatching { ann.constructAppearances(doc) }
                page.annotations.add(ann)
                marked++
            }
            doc.save(out)
        }
        return marked
    }
}
