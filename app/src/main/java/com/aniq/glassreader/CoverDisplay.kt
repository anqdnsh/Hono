package com.aniq.glassreader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.aniq.glassreader.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shows the current book's cover on a foldable's outer screen while the phone is open
 * (Jetpack WindowManager "rear display presentation"). Only some devices support it;
 * [supported] reports whether this one does.
 */
@OptIn(ExperimentalWindowApi::class)
class CoverDisplay(private val activity: Activity, private val scope: CoroutineScope) {
    private val controller: WindowAreaController? = runCatching { WindowAreaController.getOrCreate() }.getOrNull()
    private var area: WindowAreaInfo? = null
    private var session: WindowAreaSessionPresenter? = null
    private var starting = false
    private var shownBook: String? = null

    fun start() {
        val c = controller ?: run { supported.value = false; return }
        scope.launch {
            c.windowAreaInfos.collect { infos ->
                area = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                val status = area?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status
                supported.value = status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE ||
                    status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE
                available.value = status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE
            }
        }
        scope.launch {
            // automatic: on while a book is open sideways (two pages), off as soon as it turns upright
            combine(openBook, landscape, Store.settings, available) { id, wide, s, ok -> Triple(id?.takeIf { wide }, s.coverScreen, ok) }
                .collect { (id, on, ok) -> if (id != null && on) show(id, ok) else hide() }
        }
    }

    private fun show(bookId: String, ok: Boolean) {
        session?.let { if (shownBook != bookId) { shownBook = bookId; it.setContentView(buildView(it, bookId)) }; return }
        if (!ok || starting) return
        val c = controller ?: return
        val token = area?.token ?: return
        starting = true
        runCatching {
            c.presentContentOnWindowArea(token, activity, ContextCompat.getMainExecutor(activity), object : WindowAreaPresentationSessionCallback {
                override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                    starting = false
                    this@CoverDisplay.session = session
                    val id = openBook.value
                    if (id == null || !landscape.value || !Store.settings.value.coverScreen) { session.close(); return }
                    shownBook = id
                    session.setContentView(buildView(session, id))
                }
                override fun onSessionEnded(t: Throwable?) { starting = false; session = null; shownBook = null }
                override fun onContainerVisibilityChanged(isVisible: Boolean) {}
            })
        }.onFailure { starting = false }
    }

    private fun hide() {
        runCatching { session?.close() }
        session = null; shownBook = null
    }

    private fun buildView(s: WindowAreaSessionPresenter, bookId: String): android.view.View {
        val v = LayoutInflater.from(s.context).inflate(R.layout.cover_display, null)
        val cover = Store.cover(bookId, 2000)
        val iv = v.findViewById<ImageView>(R.id.cover)
        if (cover != null) iv.setImageBitmap(rounded(cover)) else iv.setImageResource(R.mipmap.ic_launcher)
        return v
    }

    private fun rounded(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = src.width * 0.02f
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN); c.drawBitmap(src, 0f, 0f, p)
        return out
    }

    companion object {
        /** True while the reader is sideways showing two pages (set by the reader screens). */
        val landscape = MutableStateFlow(false)
        /** Book currently open in the reader (set by the reader screens). */
        val openBook = MutableStateFlow<String?>(null)
        /** Whether this device can present on its outer screen at all. */
        val supported = MutableStateFlow(false)
        /** Whether it can do so right now (phone unfolded, screen free). */
        val available = MutableStateFlow(false)
    }
}
