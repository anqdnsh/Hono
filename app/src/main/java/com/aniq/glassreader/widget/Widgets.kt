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
import com.aniq.glassreader.ui.HighlightColorInts

class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context); Widgets.updateContinue(context, manager, ids)
    }
}

class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context); Widgets.updateQuote(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Widgets.ACTION_NEXT) { Store.init(context); Widgets.nextQuote(context) }
    }
}

class HonoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context); Widgets.updateHono(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Widgets.ACTION_NEXT) { Store.init(context); Widgets.nextQuote(context) }
    }
}

class BookWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context); Widgets.updateBook(context, manager, ids)
    }
}

object Widgets {
    const val ACTION_NEXT = "com.aniq.glassreader.NEXT_QUOTE"
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
            val cover = Store.cover(book.id, 420)
            if (cover != null) v.setImageViewBitmap(R.id.cover, rounded(cover, 10f)) else v.setImageViewResource(R.id.cover, R.mipmap.ic_launcher)
            v.setContentDescription(R.id.root, "Continue ${book.title}")
            v.setOnClickPendingIntent(R.id.root, openIntent(context, 8) { putExtra(EXTRA_OPEN_BOOK, book.id) })
        } else {
            v.setOnClickPendingIntent(R.id.root, openIntent(context, 8) {})
        }
        ids.forEach { m.updateAppWidget(it, v) }
    }

    fun nextQuote(context: Context) {
        val size = quotes().size
        if (size > 1) {
            var next = (Math.random() * size).toInt()
            if (next == Store.quoteIndex.mod(size)) next = (next + 1) % size
            Store.quoteIndex = next
        }
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
            val cover = Store.cover(book.id, 300)
            if (cover != null) v.setImageViewBitmap(R.id.cover, rounded(cover, 14f))
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
            v.setInt(R.id.dot, "setColorFilter", HighlightColorInts[h.color.coerceIn(0, HighlightColorInts.size - 1)])
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

    private fun rounded(src: Bitmap, radiusDp: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = radiusDp * src.width / 120f
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, p)
        return out
    }
}
