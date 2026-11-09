package com.aniq.glassreader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/**
 * A page lifting off like paper. The sheet is hinged on a vertical line (the spine) and its free
 * bottom corner follows the finger; the fold is the perpendicular bisector between the corner's
 * rest position and where it is now. Everything is drawn from the sheet's own content, so it works
 * for text pages and PDF bitmaps alike.
 *
 * Coordinates are the sheet's own: [hinge] is the spine's x, [corner] the free edge's x.
 * [p] is where the bottom corner is now (null = lying flat).
 * [contentBack]: true = the back of the sheet is real content laid out mirrored on the other side of
 * the spine (a two-page spread); false = plain paper with the front faintly showing through.
 */
fun Modifier.pageCurl(
    p: () -> Offset?,
    hinge: Float,
    corner: Float,
    paper: Color,
    dark: Boolean,
    contentBack: Boolean,
): Modifier = this.drawWithContent {
    val h = size.height
    val raw = p()
    val sheetW = kotlin.math.abs(corner - hinge)
    val left = min(hinge, corner); val right = left + sheetW
    val sheet = listOf(Offset(left, 0f), Offset(right, 0f), Offset(right, h), Offset(left, h))
    if (raw == null) { clipPath(poly(sheet)) { drawRect(paper, Offset(left, 0f), androidx.compose.ui.geometry.Size(sheetW, h)); this@drawWithContent.drawContent() }; return@drawWithContent }

    // paper doesn't stretch: the corner stays within reach of the spine
    var pt: Offset = raw
    run {
        val toBottom = pt - Offset(hinge, h); val d = toBottom.getDistance()
        if (d > sheetW) pt = Offset(hinge, h) + toBottom * (sheetW / d)
        val diag = hypot(sheetW, h); val toTop = pt - Offset(hinge, 0f); val d2 = toTop.getDistance()
        if (d2 > diag) pt = Offset(hinge, 0f) + toTop * (diag / d2)
    }
    val c = Offset(corner, h)
    val v = c - pt; val len = v.getDistance()
    if (len < 0.5f) { clipPath(poly(sheet)) { drawRect(paper, Offset(left, 0f), androidx.compose.ui.geometry.Size(sheetW, h)); this@drawWithContent.drawContent() }; return@drawWithContent }
    val n = v / len; val m = (c + pt) / 2f
    val kept = clip(sheet, m, n, keep = true)
    val cut = clip(sheet, m, n, keep = false)
    val md = m.x * n.x + m.y * n.y
    // reflection across the fold
    val ra = 1 - 2 * n.x * n.x; val rb = -2 * n.x * n.y; val rd = 1 - 2 * n.y * n.y
    val re = 2 * md * n.x; val rf = 2 * md * n.y
    fun reflect(q: Offset): Offset { val s = (q.x - m.x) * n.x + (q.y - m.y) * n.y; return Offset(q.x - 2 * s * n.x, q.y - 2 * s * n.y) }
    val flap = cut.map(::reflect)
    if (fromNowOnLanded(pt, Offset(2 * hinge - corner, h), sheetW) && !contentBack) {
        // landed on the other side: nothing of this sheet is visible any more
        return@drawWithContent
    }

    // Clean paper only: no cast shadows, no blur, no shading bands. The page underneath shows
    // through where the sheet has lifted, the flap shows its back, and a hairline marks the fold.

    // 1. the part of the page still lying flat
    if (kept.size >= 3) clipPath(poly(kept)) {
        drawRect(paper, Offset(left, 0f), androidx.compose.ui.geometry.Size(sheetW, h))
        this@drawWithContent.drawContent()
    }
    if (flap.size < 3) return@drawWithContent
    val flapPath = poly(flap)
    // 2. the back of the sheet
    clipPath(flapPath) {
        val back = if (dark) lerp(paper, Color.White, 0.05f) else lerp(paper, Color.Black, 0.025f)
        drawRect(back, Offset(-size.width, 0f), androidx.compose.ui.geometry.Size(size.width * 3, h))
        if (contentBack) {
            // the next page, printed on the reverse: mirror across the spine, then fold
            val e2 = re + 2 * hinge * ra; val f2 = rf + 2 * hinge * rb
            withTransform({ transform(affine(-ra, -rb, rb, rd, e2, f2)) }) { this@drawWithContent.drawContent() }
        }
    }
    // 3. a hairline round the flap so its edge reads against the page beneath
    drawPath(flapPath, (if (dark) Color.White else Color.Black).copy(alpha = 0.10f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75.dp.toPx()))
}

private fun fromNowOnLanded(pt: Offset, land: Offset, sheetW: Float) = (pt - land).getDistance() < sheetW * 0.002f

private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** x' = a·x + c·y + e, y' = b·x + d·y + f */
private fun affine(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) =
    Matrix(floatArrayOf(a, b, 0f, 0f, c, d, 0f, 0f, 0f, 0f, 1f, 0f, e, f, 0f, 1f))

private fun poly(pts: List<Offset>) = Path().apply {
    pts.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
    close()
}

/** Sutherland–Hodgman against the fold line. */
private fun clip(poly: List<Offset>, m: Offset, n: Offset, keep: Boolean): List<Offset> {
    val out = ArrayList<Offset>()
    fun side(q: Offset) = (q.x - m.x) * n.x + (q.y - m.y) * n.y
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[(i + 1) % poly.size]
        val sa = side(a); val sb = side(b)
        val ina = if (keep) sa <= 0 else sa > 0
        val inb = if (keep) sb <= 0 else sb > 0
        if (ina) out.add(a)
        if (ina != inb) { val t = sa / (sa - sb); out.add(a + (b - a) * t) }
    }
    return out
}

/**
 * One page turn in flight. [dir] +1 = forward, -1 = back. [start] is where the corner begins, [end]
 * where it lands if the turn completes. [p] is animated by the finger and by the settle spring.
 */
class CurlTurn(val dir: Int, val start: Offset, val end: Offset) {
    val p: Animatable<Offset, AnimationVector2D> = Animatable(start, Offset.VectorConverter)
    var completing = false

    /** Finger moved by [dx] (and [lift] upwards). */
    suspend fun follow(dx: Float, lift: Float) { p.snapTo(Offset(start.x + dx, start.y - lift)) }

    /** How far through the turn it is, 0..1. */
    val fraction: Float get() = ((p.value.x - start.x) / (end.x - start.x)).coerceIn(0f, 1f)

    suspend fun settle(complete: Boolean) {
        completing = complete
        p.animateTo(if (complete) end else start, spring(dampingRatio = 1f, stiffness = 420f))
    }

    /** A whole turn on its own (volume keys): the corner lifts, arcs over and lays down. */
    suspend fun auto() {
        completing = true
        val mid = Offset((start.x + end.x) / 2f, start.y - 110f)
        p.animateTo(end, keyframes {
            durationMillis = 560
            Offset(start.x + (end.x - start.x) * 0.12f, start.y - 50f) at 90 using FastOutSlowInEasing
            mid at 300
        })
    }
}
