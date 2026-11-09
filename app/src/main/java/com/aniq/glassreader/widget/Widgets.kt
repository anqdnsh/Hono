package com.aniq.glassreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.RemoteViews
import com.aniq.glassreader.MainActivity
import com.aniq.glassreader.R
import com.aniq.glassreader.data.Highlight
import com.aniq.glassreader.data.Store
import com.aniq.glassreader.ui.formatReadTime

class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context); Widgets.updateContinue(context, manager, ids)
    }
}

class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        if (!Widgets.cycleIfDue(context)) Widgets.updateQuote(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Widgets.ACTION_NEXT -> { Store.init(context); Widgets.nextQuote(context) }
            Widgets.ACTION_CYCLE -> { Store.init(context); Widgets.cycleIfDue(context) }
        }
    }
}

class HonoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        if (!Widgets.cycleIfDue(context)) Widgets.updateHono(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Widgets.ACTION_NEXT -> { Store.init(context); Widgets.nextQuote(context) }
            Widgets.ACTION_CYCLE -> { Store.init(context); Widgets.cycleIfDue(context) }
        }
    }
}

class BookWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context); Widgets.updateBook(context, manager, ids)
    }
}

object Widgets {
    const val ACTION_NEXT = "com.aniq.glassreader.NEXT_QUOTE"
    const val ACTION_CYCLE = "com.aniq.glassreader.CYCLE_QUOTE"
    private const val CYCLE_MS = 2 * 60 * 60 * 1000L
    const val EXTRA_OPEN_BOOK = "open_book"
    const val EXTRA_OPEN_PARA = "open_para"
    const val EXTRA_OPEN_HIGHLIGHTS = "open_highlights"

    private fun ids(context: Context, m: AppWidgetManager, cls: Class<*>) = m.getAppWidgetIds(ComponentName(context, cls))

    fun updateAll(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        updateContinue(context, m, ids(context, m, ContinueReadingWidget::class.java))
        updateQuote(context, m, ids(context, m, QuoteWidget::class.java))
        updateHono(context, m, ids(context, m, HonoWidget::class.java))
        updateBook(context, m, ids(context, m, BookWidget::class.java))
    }

    fun updateBook(context: Context, m: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_book)
        val book = Store.lastRead() ?: Store.books.value.firstOrNull()
        if (book != null) {
            v.setProgressBar(R.id.progress, 1000, (book.progress * 1000).toInt(), false)
            v.setTextViewText(R.id.percent, "${(book.progress * 100).toInt()}%")
            val cover = Store.cover(book.id, 420)
            if (cover != null) v.setImageViewBitmap(R.id.cover, bookObject(cover)) else v.setImageViewResource(R.id.cover, R.mipmap.ic_launcher)
            v.setTextViewText(R.id.title, book.title)
            val where = when {
                book.isPdf && book.totalParas > 0 -> "Page ${book.progressIndex + 1} of ${book.totalParas}"
                book.chapter.isNotBlank() && book.chapter != book.title -> book.chapter
                else -> book.author
            }
            v.setTextViewText(R.id.chapter, where)
            v.setViewVisibility(R.id.chapter, if (where.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
            v.setTextViewText(R.id.continueBtn, if (book.lastOpened > 0) "Continue" else "Start")
            v.setContentDescription(R.id.root, "Continue ${book.title}")
            val open = openIntent(context, 8) { putExtra(EXTRA_OPEN_BOOK, book.id) }
            v.setOnClickPendingIntent(R.id.root, open)
            v.setOnClickPendingIntent(R.id.continueBtn, open)
        } else {
            v.setTextViewText(R.id.title, "Add your first book")
            v.setTextViewText(R.id.chapter, "")
            v.setViewVisibility(R.id.chapter, android.view.View.GONE)
            v.setTextViewText(R.id.continueBtn, "Open Hono")
            val open = openIntent(context, 8) {}
            v.setOnClickPendingIntent(R.id.root, open)
            v.setOnClickPendingIntent(R.id.continueBtn, open)
        }
        v.setTextViewText(R.id.hours, "${formatReadTime(Store.totalReadMs.value)} read")
        ids.forEach { m.updateAppWidget(it, v) }
    }

    /**
     * Moves the quote widgets on to a new highlight once 2 hours have passed since the last change.
     * Driven by an inexact alarm plus the widgets' own 2-hour update, so a missed alarm
     * (after a reboot, say) still catches up. Returns true if it changed the quote.
     */
    fun cycleIfDue(context: Context): Boolean {
        scheduleCycle(context)
        // a little slack so an alarm that fires a few minutes early still counts
        if (System.currentTimeMillis() - Store.quoteChangedAt < CYCLE_MS - 10 * 60 * 1000L) return false
        nextQuote(context)
        return true
    }

    private fun scheduleCycle(context: Context) {
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val cls = if (ids(context, AppWidgetManager.getInstance(context), QuoteWidget::class.java).isNotEmpty()) QuoteWidget::class.java else HonoWidget::class.java
        val pi = PendingIntent.getBroadcast(
            context, 9, Intent(context, cls).setAction(ACTION_CYCLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // non-waking: if the phone is asleep it simply changes when the screen comes back on
        val first = (Store.quoteChangedAt + CYCLE_MS).coerceAtLeast(System.currentTimeMillis() + 60_000L)
        am.setInexactRepeating(android.app.AlarmManager.RTC, first, CYCLE_MS, pi)
    }

    fun nextQuote(context: Context) {
        val size = quotes().size
        if (size > 1) {
            var next = (Math.random() * size).toInt()
            if (next == Store.quoteIndex.mod(size)) next = (next + 1) % size
            Store.quoteIndex = next
        }
        Store.quoteChangedAt = System.currentTimeMillis()
        scheduleCycle(context)
        val m = AppWidgetManager.getInstance(context)
        updateQuote(context, m, ids(context, m, QuoteWidget::class.java))
        updateHono(context, m, ids(context, m, HonoWidget::class.java))
    }

    /** Text highlights only; framed PDF clips don't read well at widget size. */
    private fun quotes(): List<Highlight> = Store.highlights.value.filter { !it.isClip }.sortedBy { it.createdAt }

    private fun currentQuote(): Highlight? = quotes().let { if (it.isEmpty()) null else it[Store.quoteIndex.mod(it.size)] }

    private fun openIntent(context: Context, code: Int, fill: Intent.() -> Unit): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            fill()
        }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun nextIntent(context: Context, cls: Class<*>, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, cls).setAction(ACTION_NEXT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun bindContinue(context: Context, v: RemoteViews, clickId: Int, code: Int) {
        val book = Store.lastRead() ?: Store.books.value.firstOrNull()
        if (book != null) {
            v.setTextViewText(R.id.title, book.title)
            v.setTextViewText(R.id.author, book.author)
            v.setProgressBar(R.id.progress, 1000, (book.progress * 1000).toInt(), false)
            v.setTextViewText(R.id.percent, "${(book.progress * 100).toInt()}% read")
            v.setTextViewText(R.id.hours, "${formatReadTime(Store.totalReadMs.value)} total")
            val cover = Store.cover(book.id, 300)
            if (cover != null) v.setImageViewBitmap(R.id.cover, bookObject(cover))
            else v.setImageViewResource(R.id.cover, R.mipmap.ic_launcher)
            v.setOnClickPendingIntent(clickId, openIntent(context, code) { putExtra(EXTRA_OPEN_BOOK, book.id) })
        } else {
            v.setTextViewText(R.id.title, "Add your first book")
            v.setTextViewText(R.id.author, "Tap to open Hono")
            v.setImageViewResource(R.id.cover, R.mipmap.ic_launcher)
            v.setOnClickPendingIntent(clickId, openIntent(context, code) {})
        }
    }

    private fun bindQuote(context: Context, v: RemoteViews, clickId: Int, code: Int) {
        val h = currentQuote()
        if (h != null) {
            val book = Store.book(h.bookId)
            v.setTextViewText(R.id.quote, h.text.trim())
            v.setTextViewText(R.id.source, listOfNotNull(book?.title, h.chapter.takeIf { it.isNotBlank() && it != book?.title }).joinToString(" · "))
            v.setInt(R.id.dot, "setColorFilter", 0xFF1C1C1E.toInt())
            v.setOnClickPendingIntent(clickId, openIntent(context, code) {
                putExtra(EXTRA_OPEN_BOOK, h.bookId); putExtra(EXTRA_OPEN_PARA, h.para)
            })
        } else {
            v.setOnClickPendingIntent(clickId, openIntent(context, code) { putExtra(EXTRA_OPEN_HIGHLIGHTS, true) })
        }
    }

    fun updateContinue(context: Context, m: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_continue)
        bindContinue(context, v, R.id.root, 1)
        ids.forEach { m.updateAppWidget(it, v) }
    }

    fun updateQuote(context: Context, m: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        scheduleCycle(context)
        val v = RemoteViews(context.packageName, R.layout.widget_quote)
        bindQuote(context, v, R.id.root, 2)
        v.setOnClickPendingIntent(R.id.next, nextIntent(context, QuoteWidget::class.java, 4))
        ids.forEach { m.updateAppWidget(it, v) }
    }

    fun updateHono(context: Context, m: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_hono)
        bindContinue(context, v, R.id.top, 5)
        bindQuote(context, v, R.id.quoteBox, 6)
        v.setOnClickPendingIntent(R.id.next, nextIntent(context, HonoWidget::class.java, 7))
        ids.forEach { m.updateAppWidget(it, v) }
    }

    /**
     * The cover drawn as a physical book: soft drop shadow, a bound spine down the left edge,
     * a sheen across the top, squarer corners at the spine, and a glow in the cover's own colour
     * behind it so each book tints the glass a little.
     */
    private fun bookObject(src: Bitmap): Bitmap {
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val pad = w * 0.30f
        val out = Bitmap.createBitmap((w + pad * 2).toInt(), (h + pad * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val box = RectF(pad, pad, pad + w, pad + h)

        // glow: the cover's average colour, lifted a touch, blurred out behind it
        val avg = Bitmap.createScaledBitmap(src, 1, 1, true).getPixel(0, 0)
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(avg, it) }
        hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1f); hsv[2] = (hsv[2] * 1.5f).coerceIn(0.35f, 0.9f)
        c.drawOval(RectF(pad - w * 0.05f, pad + h * 0.08f, pad + w * 1.05f, pad + h * 0.95f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.HSVToColor(130, hsv)
            maskFilter = android.graphics.BlurMaskFilter(w * 0.22f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        })

        val rs = w * 0.035f; val re = w * 0.08f   // spine corners squarer than the open edge
        val shape = android.graphics.Path().apply {
            addRoundRect(box, floatArrayOf(rs, rs, re, re, re, re, rs, rs), android.graphics.Path.Direction.CW)
        }
        // drop shadow (offset down, so the book stands on the glass)
        c.drawPath(android.graphics.Path(shape).apply { offset(0f, h * 0.035f) }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99000000.toInt()
            maskFilter = android.graphics.BlurMaskFilter(w * 0.07f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        })

        c.save(); c.clipPath(shape)
        c.drawBitmap(src, null, box, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        // spine: dark crease, bright ridge, soft fade
        c.drawRect(box.left, box.top, box.left + w * 0.13f, box.bottom, Paint().apply {
            shader = android.graphics.LinearGradient(box.left, 0f, box.left + w * 0.13f, 0f,
                intArrayOf(0x66000000, 0x38FFFFFF, 0x1A000000, 0x00000000),
                floatArrayOf(0f, 0.25f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        })
        // sheen across the top
        c.drawRect(box.left, box.top, box.right, box.top + h * 0.45f, Paint().apply {
            shader = android.graphics.LinearGradient(0f, box.top, 0f, box.top + h * 0.45f,
                0x24FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
        })
        c.restore()
        // hairline edge so dark covers still separate from dark glass
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w * 0.006f; color = 0x2EFFFFFF
        })
        return out
    }

}
