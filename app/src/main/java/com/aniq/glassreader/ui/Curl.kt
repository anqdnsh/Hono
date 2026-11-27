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
import kotlin.math.min

/**
 * A page lifting off like paper. The sheet is hinged on a vertical line (the spine) and the point
 * you grabbed on its free edge follows the finger; the fold is the perpendicular bisector between
 * that point's rest position and where it is now. Everything is drawn from the sheet's own content,
 * so it works for text pages and PDF bitmaps alike.
 *
 * Where you grab decides the bend ([originY]): the top or bottom corner peels diagonally, the middle
 * of the edge turns the page straight over (and tilts if you drag at an angle).
 *
 * Coordinates are the sheet's own: [hinge] is the spine's x, [corner] the free edge's x.
 * [p] is where the grabbed point is now (null = lying flat).
 * [contentBack]: true = the back of the sheet is real content laid out mirrored on the other side of
 * the spine (a two-page spread); false = plain paper with the front faintly showing through.
 *
 * Shading is matte paper: a warm contact shadow where the sheet meets the page beneath, a soft cast
 * shadow under the flap, light falling off across the bend (no shine) and a fine grain. All of it
 * fades out as the sheet rests or lands, so no dark band is left on the new page.
 */
fun Modifier.pageCurl(
    p: () -> Offset?,
    hinge: Float,
    corner: Float,
    paper: Color,
    dark: Boolean,
    contentBack: Boolean,
    originY: () -> Float = { Float.NaN },
): Modifier = this.drawWithContent {
    val h = size.height
    val raw = p()
    val sheetW = kotlin.math.abs(corner - hinge)
    val left = min(hinge, corner); val right = left + sheetW
    val sheet = listOf(Offset(left, 0f), Offset(right, 0f), Offset(right, h), Offset(left, h))
    if (raw == null) { clipPath(poly(sheet)) { drawRect(paper, Offset(left, 0f), androidx.compose.ui.geometry.Size(sheetW, h)); this@drawWithContent.drawContent() }; return@drawWithContent }

    val oy = originY().let { if (it.isNaN()) h else it.coerceIn(0f, h) }
    val c = Offset(corner, oy)
    // paper doesn't stretch: the grabbed point can't get further from either end of the spine
    var pt: Offset = raw
    repeat(2) {
        for (s in arrayOf(Offset(hinge, 0f), Offset(hinge, h))) {
            val r = (c - s).getDistance(); val d = pt - s; val l = d.getDistance()
            if (l > r && l > 0f) pt = s + d * (r / l)
        }
    }
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
    val land = Offset(2 * hinge - corner, oy)
    if (fromNowOnLanded(pt, land, sheetW) && !contentBack) {
        // landed on the other side: nothing of this sheet is visible any more
        return@drawWithContent
    }

    // how much shading to show: none lying flat, none once landed, full in between
    val fold = min(len / 2f, sheetW * 0.5f)
    val prog = (len / (2f * sheetW)).coerceIn(0f, 1f)
    val shade = ((pt - land).getDistance() / (sheetW * 0.22f)).coerceIn(0f, 1f) *
        (len / (sheetW * 0.06f)).coerceIn(0f, 1f)
    val tint = if (dark) Color.Black else Color(0xFF1E140A)
    val big = size.width * 3f

    // 1. warm contact shadow on the page beneath, hugging the fold
    if (cut.size >= 3 && shade > 0f) clipPath(poly(cut)) {
        val sw = (fold * 0.55f).coerceAtLeast(10.dp.toPx())
        val a = 0.32f * shade * (1f - prog * 0.6f) * (if (dark) 1.3f else 1f)
        drawRect(
            Brush.linearGradient(
                0f to tint.copy(alpha = a), 0.3f to tint.copy(alpha = a * 0.35f), 1f to Color.Transparent,
                start = m, end = m + n * sw,
            ),
            Offset(-big, -big), androidx.compose.ui.geometry.Size(big * 3, big * 3),
        )
    }

    // 2. the part of the page still lying flat
    if (kept.size >= 3) clipPath(poly(kept)) {
        drawRect(paper, Offset(left, 0f), androidx.compose.ui.geometry.Size(sheetW, h))
        this@drawWithContent.drawContent()
    }
    if (flap.size < 3) return@drawWithContent
    val flapPath = poly(flap)
    val back = if (dark) lerp(paper, Color.White, 0.05f) else lerp(paper, Color.Black, 0.035f)

    // 3. soft shadow the lifted flap casts onto the flat page
    if (shade > 0f) drawIntoCanvas { cv ->
        val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = back.toArgb()
            setShadowLayer(
                (8.dp.toPx() + fold * 0.10f), -n.x * 4.dp.toPx(), -n.y * 4.dp.toPx(),
                Color.Black.copy(alpha = 0.28f * shade).toArgb(),
            )
        }
        cv.nativeCanvas.drawPath(flapPath.asAndroidPath(), sp)
    }

    // 4. the back of the sheet
    clipPath(flapPath) {
        drawRect(back, Offset(-big, -big), androidx.compose.ui.geometry.Size(big * 3, big * 3))
        if (contentBack) {
            // the next page, printed on the reverse: mirror across the spine, then fold
            val e2 = re + 2 * hinge * ra; val f2 = rf + 2 * hinge * rb
            withTransform({ transform(affine(-ra, -rb, rb, rd, e2, f2)) }) { this@drawWithContent.drawContent() }
        } else {
            // the front's ink showing faintly through the paper, mirrored by the fold
            drawIntoCanvas { cv ->
                cv.saveLayer(Rect(-big, -big, big * 2, big * 2), Paint().apply { alpha = if (dark) 0.07f else 0.09f })
                withTransform({ transform(affine(ra, rb, rb, rd, re, rf)) }) { this@drawWithContent.drawContent() }
                cv.restore()
            }
        }
        if (shade > 0f) {
            // matte bend: darkest in the crease, light falling off across the curve, no highlight
            val ew = fold.coerceAtLeast(30.dp.toPx())
            val k = shade * (if (dark) 1.4f else 1f)
            drawRect(
                Brush.linearGradient(
                    0f to tint.copy(alpha = 0.22f * k),
                    0.06f to tint.copy(alpha = 0.12f * k),
                    0.22f to tint.copy(alpha = 0.045f * k),
                    0.5f to tint.copy(alpha = 0.012f * k),
                    0.85f to tint.copy(alpha = 0.03f * k),
                    1f to tint.copy(alpha = 0.06f * k),
                    start = m, end = m - n * ew,
                ),
                Offset(-big, -big), androidx.compose.ui.geometry.Size(big * 3, big * 3),
            )
        }
        // fine paper grain so the back reads as paper, not plastic
        drawIntoCanvas { cv ->
            val g = grainPaint(); g.alpha = if (dark) 110 else 255
            cv.nativeCanvas.drawRect(-big, -big, big * 2, big * 2, g)
        }
    }
    // 5. a hairline round the flap so its edge reads against the page beneath
    drawPath(flapPath, (if (dark) Color.White else Color.Black).copy(alpha = 0.08f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75.dp.toPx()))
}

private var grain: android.graphics.Paint? = null
/** Tiled fine noise and a few fibres, built once. */
private fun grainPaint(): android.graphics.Paint = grain ?: run {
    val s = 160
    val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = java.util.Random(7)
    val px = IntArray(s * s) {
        val v = rnd.nextFloat(); val a = (kotlin.math.abs(v - 0.5f) * 22).toInt(); val g = if (v < 0.5f) 60 else 255
        (a shl 24) or (g shl 16) or (g shl 8) or g
    }
    bmp.setPixels(px, 0, s, 0, 0, s, s)
    val cv = android.graphics.Canvas(bmp)
    val fibre = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(13, 90, 70, 40); style = android.graphics.Paint.Style.STROKE; strokeWidth = 0.6f
    }
    repeat(40) {
        val x = rnd.nextFloat() * s; val y = rnd.nextFloat() * s; val a = rnd.nextFloat() * 6.3f; val l = 4 + rnd.nextFloat() * 10
        cv.drawPath(android.graphics.Path().apply {
            moveTo(x, y); quadTo(x + kotlin.math.cos(a + .5f) * l / 2, y + kotlin.math.sin(a + .5f) * l / 2, x + kotlin.math.cos(a) * l, y + kotlin.math.sin(a) * l)
        }, fibre)
    }
    android.graphics.Paint().apply {
        shader = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    }.also { grain = it }
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
 * One page turn in flight. [dir] +1 = forward, -1 = back. [start] is where the grabbed point begins
 * (its y is the height you grabbed the edge at), [end] where it lands if the turn completes.
 * [lean]: -1 grabbed the bottom corner, +1 the top corner, 0 the middle of the edge; a corner lifts
 * a little toward the page's middle as it travels, like real paper.
 * [p] is animated by the finger and by the settle spring.
 */
class CurlTurn(val dir: Int, val start: Offset, val end: Offset, val lean: Int = -1) {
    val p: Animatable<Offset, AnimationVector2D> = Animatable(start, Offset.VectorConverter)
    var completing = false
    /** The haptic was already played (when the turn was decided), so landing stays silent. */
    var quiet = false

    /** Finger moved by [dx], [dy]. The grabbed point follows it; corners also lift a little. */
    suspend fun follow(dx: Float, dy: Float) {
        val lift = kotlin.math.abs(dx) * (if (lean == 0) 0f else 0.10f) * lean
        val follow = if (lean == 0) 0.6f else 0.85f
        p.snapTo(Offset(start.x + dx, start.y + dy * follow + lift))
    }

    /** How far through the turn it is, 0..1. */
    val fraction: Float get() = ((p.value.x - start.x) / (end.x - start.x)).coerceIn(0f, 1f)

    suspend fun settle(complete: Boolean) {
        completing = complete
        p.animateTo(if (complete) end else start, spring(dampingRatio = 1f, stiffness = 420f))
    }

    /** A whole turn on its own (volume keys): the grabbed point lifts, arcs over and lays down. */
    suspend fun auto() {
        completing = true
        val up = if (lean == 0) 0f else lean.toFloat()   // top corner arcs down, bottom arcs up
        val mid = Offset((start.x + end.x) / 2f, start.y + 110f * up)
        p.animateTo(end, keyframes {
            durationMillis = 560
            Offset(start.x + (end.x - start.x) * 0.12f, start.y + 50f * up) at 90 using FastOutSlowInEasing
            mid at 300
        })
    }
}
