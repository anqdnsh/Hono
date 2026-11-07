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
import com.aniq.glassreader.data.Store
import com.aniq.glassreader.ui.HighlightColorInts

class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        Widgets.updateContinue(context, manager, ids)
    }
}

class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        Widgets.updateQuote(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Widgets.ACTION_NEXT) {
            Store.init(context)
            val size = Store.highlights.value.size
            if (size > 1) {
                var next = (Math.random() * size).toInt()
                if (next == Store.quoteIndex) next = (next + 1) % size
                Store.quoteIndex = next
            }
            val m = AppWidgetManager.getInstance(context)
            Widgets.updateQuote(context, m, m.getAppWidgetIds(ComponentName(context, QuoteWidget::class.java)))
        }
    }
}

object Widgets {
    const val ACTION_NEXT = "com.aniq.glassreader.NEXT_QUOTE"
    const val EXTRA_OPEN_BOOK = "open_book"
    const val EXTRA_OPEN_PARA = "open_para"
    const val EXTRA_OPEN_HIGHLIGHTS = "open_highlights"

    fun updateAll(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        updateContinue(context, m, m.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java)))
        updateQuote(context, m, m.getAppWidgetIds(ComponentName(context, QuoteWidget::class.java)))
    }

    private fun openIntent(context: Context, code: Int, fill: Intent.() -> Unit): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            fill()
        }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun updateContinue(context: Context, m: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val book = Store.lastRead() ?: Store.books.value.firstOrNull()
        val v = RemoteViews(context.packageName, R.layout.widget_continue)
        if (book != null) {
            v.setTextViewText(R.id.title, book.title)
            v.setTextViewText(R.id.author, book.author)
            v.setProgressBar(R.id.progress, 1000, (book.progress * 1000).toInt(), false)
            v.setTextViewText(R.id.percent, "${(book.progress * 100).toInt()}% read")
            val cover = Store.cover(book.id, 300)
            if (cover != null) v.setImageViewBitmap(R.id.cover, rounded(cover, 18f))
            else v.setImageViewResource(R.id.cover, R.drawable.ic_launcher_foreground)
            v.setOnClickPendingIntent(R.id.root, openIntent(context, 1) { putExtra(EXTRA_OPEN_BOOK, book.id) })
        } else {
            v.setTextViewText(R.id.title, "Add your first book")
            v.setTextViewText(R.id.author, "Tap to open Glass Reader")
            v.setOnClickPendingIntent(R.id.root, openIntent(context, 1) {})
        }
        ids.forEach { m.updateAppWidget(it, v) }
    }

    fun updateQuote(context: Context, m: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val all = Store.highlights.value.sortedBy { it.createdAt }
        val v = RemoteViews(context.packageName, R.layout.widget_quote)
        if (all.isNotEmpty()) {
            val h = all[Store.quoteIndex.mod(all.size)]
            val book = Store.book(h.bookId)
            v.setTextViewText(R.id.quote, "“${h.text.trim()}”")
            v.setTextViewText(R.id.source, listOfNotNull(book?.title, h.chapter.takeIf { it.isNotBlank() && it != book?.title }).joinToString(" · "))
            v.setInt(R.id.dot, "setColorFilter", HighlightColorInts[h.color.coerceIn(0, HighlightColorInts.size - 1)])
            v.setOnClickPendingIntent(R.id.quote, openIntent(context, 2) {
                putExtra(EXTRA_OPEN_BOOK, h.bookId); putExtra(EXTRA_OPEN_PARA, h.para)
            })
        } else {
            v.setOnClickPendingIntent(R.id.quote, openIntent(context, 2) {})
        }
        v.setOnClickPendingIntent(R.id.root, openIntent(context, 3) { putExtra(EXTRA_OPEN_HIGHLIGHTS, true) })
        val next = Intent(context, QuoteWidget::class.java).setAction(ACTION_NEXT)
        v.setOnClickPendingIntent(R.id.next, PendingIntent.getBroadcast(context, 4, next,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
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
