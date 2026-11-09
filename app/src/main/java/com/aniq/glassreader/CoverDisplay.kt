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
    private var coverView: ImageView? = null
    private var leaving = false

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
        session?.let { s ->
            if (leaving) { leaving = false; coverView?.let { popIn(it) } }   // turned back sideways mid-exit: spring straight back
            if (shownBook != bookId) {
                shownBook = bookId
                val iv = coverView
                if (iv == null) s.setContentView(buildView(s, bookId))
                else popOut(iv) { iv.setImageBitmap(coverBitmap(bookId)); popIn(iv) }   // swap books with a quick out-and-in
            }
            return
        }
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
                override fun onSessionEnded(t: Throwable?) { starting = false; stopAll(); session = null; shownBook = null; coverView = null; leaving = false }
                override fun onContainerVisibilityChanged(isVisible: Boolean) {}
            })
        }.onFailure { starting = false }
    }

    private fun hide() {
        val s = session ?: return
        val iv = coverView
        if (iv == null) { close(s); return }
        if (leaving) return
        leaving = true
        popOut(iv) { if (leaving) close(s) }
    }

    private fun close(s: WindowAreaSessionPresenter) {
        runCatching { s.close() }
        session = null; shownBook = null; coverView = null; leaving = false
    }

    private fun coverBitmap(bookId: String): Bitmap? = Store.cover(bookId, 2000)?.let { rounded(it) }

    private fun buildView(s: WindowAreaSessionPresenter, bookId: String): android.view.View {
        val v = LayoutInflater.from(s.context).inflate(R.layout.cover_display, null)
        val iv = v.findViewById<ImageView>(R.id.cover)
        val cover = coverBitmap(bookId)
        if (cover != null) iv.setImageBitmap(cover) else iv.setImageResource(R.mipmap.ic_launcher)
        coverView = iv
        // start small and clear, then spring up once the outer screen has laid the view out
        iv.alpha = 0f; iv.scaleX = 0.82f; iv.scaleY = 0.82f
        iv.translationY = 60f * iv.resources.displayMetrics.density
        iv.post { popIn(iv) }
        return v
    }

    // ---------- motion ----------
    // Springs are driven by the display's own frame clock, so they run at whatever the screen refreshes at;
    // on Android 15+ we also ask for the high frame-rate category while moving, so the panel goes to 120 Hz.

    private fun fast(v: android.view.View, on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 35) runCatching {
            v.requestedFrameRate = if (on) android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else android.view.View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        }
        v.setLayerType(if (on) android.view.View.LAYER_TYPE_HARDWARE else android.view.View.LAYER_TYPE_NONE, null)
    }

    private fun spring(v: android.view.View, prop: androidx.dynamicanimation.animation.DynamicAnimation.ViewProperty, to: Float, stiffness: Float, damping: Float) =
        androidx.dynamicanimation.animation.SpringAnimation(v, prop, to).apply {
            spring.stiffness = stiffness; spring.dampingRatio = damping
            setMinimumVisibleChange(androidx.dynamicanimation.animation.DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        }

    private val running = ArrayList<androidx.dynamicanimation.animation.SpringAnimation>()

    private fun stopAll() { running.forEach { it.cancel() }; running.clear() }

    /** Cover springs up into place with a gentle overshoot. */
    private fun popIn(v: android.view.View) {
        stopAll(); fast(v, true)
        val sx = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f, 260f, 0.68f)
        val sy = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f, 260f, 0.68f)
        val ty = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, 0f, 220f, 0.78f)
        val a = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.ALPHA, 1f, 420f, 1f)
        ty.addEndListener { _, _, _, _ -> if (running.none { it.isRunning }) fast(v, false) }
        listOf(sx, sy, ty, a).forEach { running.add(it); it.start() }
    }

    /** Cover sinks back and fades, then [done]. */
    private fun popOut(v: android.view.View, done: () -> Unit) {
        stopAll(); fast(v, true)
        val d = v.resources.displayMetrics.density
        val sx = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 0.86f, 500f, 1f)
        val sy = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 0.86f, 500f, 1f)
        val ty = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, 40f * d, 500f, 1f)
        val a = spring(v, androidx.dynamicanimation.animation.DynamicAnimation.ALPHA, 0f, 600f, 1f)
        var finished = false
        a.addEndListener { _, canceled, _, _ -> if (!canceled && !finished) { finished = true; fast(v, false); done() } }
        listOf(sx, sy, ty, a).forEach { running.add(it); it.start() }
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
