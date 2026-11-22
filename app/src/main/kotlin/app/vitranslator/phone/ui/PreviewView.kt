package app.vitranslator.phone.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import kotlin.math.max

/**
 * The game picture (16:9), as the desktop's preview, with the latest translation of each area where
 * it would show. In the Read areas tab ([editAreas]) the areas are drawn over it too: each read area
 * as a dashed box in the accent colour, and the region its translation goes in as a blue dashed box
 * (the selected ones with their corner handles, the selected region with its "Translation · name"
 * tag). While the profile in use can be changed, an area or its translation region moves when
 * dragged and resizes from its corner (stored as fractions of the picture); a translation region
 * dragged from a preset place becomes a custom one at the same spot, as on the desktop.
 */
@SuppressLint("ViewConstructor")
class PreviewView(private val k: Kit, private val cfg: Cfg) : View(k.ctx) {
    var picture: Bitmap? = null
        set(v) {
            field = v
            invalidate()
        }

    /** the latest translation of each area (area id -> text) */
    var shown: Map<String, String> = emptyMap()
        set(v) {
            field = v
            invalidate()
        }

    /** the sample text shown in every area when there is no translation (Show sample text) */
    var sample: String? = null
        set(v) {
            field = v
            invalidate()
        }

    var selected: String? = null
        set(v) {
            field = v
            invalidate()
        }

    var rtl = true

    /** the Read areas tab: the read areas and translation regions drawn, and dragged (off in Play) */
    var editAreas = false
        set(v) {
            field = v
            invalidate()
        }

    /** draw the translations (off when the core's TV page draws them over this) */
    var drawText = true
    var fps: String? = null

    /** an area was moved or resized (by a drag) */
    var moved: ((JSONObject) -> Unit)? = null

    /** a translation region dragged from its preset place became a custom one */
    var madeCustom: ((JSONObject) -> Unit)? = null

    /** an area was tapped */
    var tapped: ((String) -> Unit)? = null

    private val frame = RectF()
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = k.dp(1.5f).toFloat()
        color = k.accent
        pathEffect = DashPathEffect(floatArrayOf(k.dp(5).toFloat(), k.dp(4).toFloat()), 0f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = k.dp(10).toFloat()
        typeface = k.bold
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = BLUE
        pathEffect = DashPathEffect(floatArrayOf(k.dp(5).toFloat(), k.dp(4).toFloat()), 0f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * 9 / 16)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(k.stage)
        frame.set(0f, 0f, width.toFloat(), height.toFloat())
        picture?.let { c.drawBitmap(it, null, frame, fill) }
        val areas = cfg.areas()
        val style = cfg.style()
        val edit = !cfg.locked()
        for (i in 0 until areas.length()) {
            val a = areas.optJSONObject(i) ?: continue
            val r = rectOf(a)
            val sel = a.optString("id") == selected
            val on = a.optBoolean("enabled", true)
            val band = bandOf(a)
            // the region the translation goes in (blue), then the read area (yellow) over it
            if (editAreas && on) {
                bandPaint.strokeWidth = k.dp(if (sel) 2f else 1.5f).toFloat()
                bandPaint.alpha = if (sel) 255 else 115
                if (sel) {
                    fill.color = BLUE
                    fill.alpha = 15
                    c.drawRect(band, fill)
                    fill.alpha = 255
                }
                c.drawRect(band, bandPaint)
            }
            if (editAreas) {
                box.alpha = if (on) 255 else 90
                box.strokeWidth = k.dp(if (sel) 2.2f else 1.4f).toFloat()
                c.drawRect(r, box)
                // its name, in a little tab above the box
                labelPaint.color = k.accent
                c.drawText(a.optString("name"), r.left + k.dp(3), max(r.top - k.dp(4), labelPaint.textSize), labelPaint)
                if (sel && edit) {
                    fill.color = k.accent
                    c.drawCircle(r.right, r.bottom, k.dp(7).toFloat(), fill)
                }
            }
            val t = (if (drawText) shown[a.optString("id")] else null) ?: sample
            if (!t.isNullOrEmpty() && on) drawText(c, t, a, Cfg.styleOf(a, style), band)
            if (editAreas && on && sel) {
                tag(c, T("Translation · {name}", "name" to a.optString("name")), band)
                if (edit) {
                    fill.color = BLUE
                    c.drawCircle(band.right, band.bottom, k.dp(7).toFloat(), fill)
                }
            }
        }
        fps?.let {
            labelPaint.color = k.neon
            c.drawText(it, width - labelPaint.measureText(it) - k.dp(8), k.dp(16).toFloat(), labelPaint)
        }
    }

    /** The translation in the area's text style, scaled from 1080p to this preview, placed in its region. */
    private fun drawText(c: Canvas, s: String, a: JSONObject, st: JSONObject, out: RectF) {
        val scale = height / 1080f
        textPaint.textSize = (st.optDouble("font_size", 26.0) * a.optDouble("font_scale", 1.0) * scale).toFloat().coerceAtLeast(k.dp(7).toFloat())
        textPaint.typeface = fontFor(a.optString("font_family").ifEmpty { st.optString("font_family") }, st.optBoolean("bold"))
        textPaint.color = Kit.parse(st.optString("text_color", "#ffffff"))
        val pad = (st.optDouble("padding", 10.0) * scale).toFloat()
        val (valign, halign) = alignOf(a.optString("placement"), a)
        val most = max(out.width() - 2 * pad, k.dp(20).toFloat()).toInt()
        val lw = layout(s, most, st).let { l -> (0 until l.lineCount).maxOf { l.getLineWidth(it) } }
        // (laid out again as wide as its longest line: the box then hugs the text, its lines centred)
        val layout = layout(s, kotlin.math.ceil(lw).toInt().coerceIn(1, most), st)
        val h = layout.height
        val top = when (valign) {
            "top" -> out.top
            "middle" -> out.centerY() - h / 2f - pad
            else -> out.bottom - h - 2 * pad
        }
        val left = when (halign) {
            "left" -> out.left
            "right" -> out.right - layout.width - 2 * pad
            else -> out.centerX() - layout.width / 2f - pad
        }
        val bg = a.optString("background")
        if (bg != "none") {
            val alpha = if (bg == "game") 0.7 else st.optDouble("bg_opacity", 0.65)
            fill.color = Kit.parse(st.optString("bg_color", "#000000"))
            fill.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            val r = (st.optDouble("radius", 10.0) * scale).toFloat()
            c.drawRoundRect(left, top, left + layout.width + 2 * pad, top + h + 2 * pad, r, r, fill)
            fill.alpha = 255
        }
        c.save()
        c.translate(left + pad, top + pad)
        val ow = st.optDouble("outline_width", 0.0).toFloat() * scale
        if (ow > 0f || bg == "none") {
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = max(ow * 2, if (bg == "none") 2f * scale + 1f else 0f)
            val col = textPaint.color
            textPaint.color = Kit.parse(st.optString("outline_color", "#000000"))
            layout.draw(c)
            textPaint.color = col
            textPaint.style = Paint.Style.FILL
        }
        layout.draw(c)
        c.restore()
    }

    private fun layout(s: String, w: Int, st: JSONObject) = StaticLayout.Builder.obtain(s, 0, s.length, textPaint, w)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, st.optDouble("line_height", 1.5).toFloat().coerceIn(1f, 2.2f) * 0.8f)
        .setTextDirection(if (rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.LTR)
        .build()

    /** The selected region's tag, in a little blue tab over its top right corner. */
    private fun tag(c: Canvas, s: String, band: RectF) {
        val p = k.dp(5).toFloat()
        val w = labelPaint.measureText(s) + 2 * p
        val h = labelPaint.textSize + 2 * p
        val right = band.right.coerceIn(w, width.toFloat())
        val top = (band.top - h).coerceAtLeast(0f)
        fill.color = BLUE
        c.drawRoundRect(right - w, top, right, top + h, p, p, fill)
        labelPaint.color = 0xff04121c.toInt()
        c.drawText(s, right - w + p, top + p + labelPaint.textSize * 0.85f, labelPaint)
    }

    private fun fontFor(name: String, bold: Boolean) = when {
        name.startsWith("Lalezar") -> k.font("Lalezar-Regular.ttf")
        name.startsWith("Changa") -> k.font("Changa.ttf")
        name.startsWith("Cairo") -> k.font("Cairo.ttf")
        name.startsWith("Noto Sans Arabic") -> k.font("NotoSansArabic.ttf")
        // (Tajawal, and any font the phone doesn't have: Arabic needs one that has it)
        else -> k.font(if (bold) "Tajawal-Bold.ttf" else "Tajawal-Regular.ttf")
    }

    fun rectOf(a: JSONObject) = RectF(
        (a.optDouble("x") * width).toFloat(), (a.optDouble("y") * height).toFloat(),
        ((a.optDouble("x") + a.optDouble("w")) * width).toFloat(), ((a.optDouble("y") + a.optDouble("h")) * height).toFloat(),
    )

    /** Where the area's translation goes, in this view's pixels. */
    fun bandOf(a: JSONObject) = rectFor(a.optString("placement"), rectOf(a), width.toFloat(), height.toFloat(), a)

    // ---- dragging an area or its translation region (move), or its corner (resize)
    private var drag: JSONObject? = null
    private var resizing = false
    /** the translation region is dragged (not the read area) */
    private var dragBand = false
    /** the finger has moved far enough to be a drag, not a tap */
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // (Play's picture: nothing to drag, taps go to what holds it)
        if (!editAreas) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                drag = null
                dragging = false
                val areas = (0 until cfg.areas().length()).map { cfg.areas().getJSONObject(it) }
                val handle = k.dp(22)
                fun near(x: Float, y: Float) = Math.hypot((e.x - x).toDouble(), (e.y - y).toDouble()) < handle
                // the selected area's corners first (the read area's, then its region's), then the
                // read area under the finger (the smallest), then a translation region
                val sel = areas.firstOrNull { it.optString("id") == selected }
                if (sel != null && !cfg.locked()) {
                    val r = rectOf(sel)
                    val band = bandOf(sel)
                    when {
                        near(r.right, r.bottom) -> { drag = sel; resizing = true; dragBand = false }
                        sel.optBoolean("enabled", true) && near(band.right, band.bottom) -> { drag = sel; resizing = true; dragBand = true }
                    }
                }
                if (drag == null) {
                    resizing = false
                    drag = areas.filter { rectOf(it).contains(e.x, e.y) }.minByOrNull { it.optDouble("w") * it.optDouble("h") }
                    dragBand = false
                    if (drag == null) {
                        // (the selected one's region first, as it is drawn on top)
                        drag = areas.filter { it.optBoolean("enabled", true) && bandOf(it).contains(e.x, e.y) }
                            .sortedWith(compareBy({ if (it === sel) 0 else 1 }, { bandOf(it).width() * bandOf(it).height() }))
                            .firstOrNull()
                        dragBand = drag != null
                    }
                }
                if (drag != null) parent?.requestDisallowInterceptTouchEvent(true)
                return drag != null
            }
            MotionEvent.ACTION_MOVE -> {
                val a = drag ?: return false
                if (cfg.locked() || a.optString("id") != selected) return true
                if (!dragging) {
                    // (a plain tap only selects; a region turns into a custom one when it's dragged)
                    if (Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) < k.dp(6)) return true
                    dragging = true
                    if (dragBand && a.optString("placement") != "custom") {
                        toCustom(a)
                        madeCustom?.invoke(a)
                    }
                }
                val dx = (e.x - lastX) / width
                val dy = (e.y - lastY) / height
                lastX = e.x
                lastY = e.y
                val (kx, ky, kw, kh) = if (dragBand) listOf("out_x", "out_y", "out_w", "out_h") else listOf("x", "y", "w", "h")
                if (resizing) {
                    a.put(kw, round((a.optDouble(kw) + dx).coerceIn(0.02, 1 - a.optDouble(kx))))
                    a.put(kh, round((a.optDouble(kh) + dy).coerceIn(0.02, 1 - a.optDouble(ky))))
                } else {
                    a.put(kx, round((a.optDouble(kx) + dx).coerceIn(0.0, 1 - a.optDouble(kw))))
                    a.put(ky, round((a.optDouble(ky) + dy).coerceIn(0.0, 1 - a.optDouble(kh))))
                }
                invalidate()
                moved?.invoke(a)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val a = drag
                drag = null
                if (a != null && !dragging) {
                    selected = a.optString("id")
                    tapped?.invoke(a.optString("id"))
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun round(v: Double) = Math.round(v * 10000) / 10000.0

    companion object {
        /** the translation regions' blue, to tell them apart from the yellow read areas */
        const val BLUE = 0xff5db6e5.toInt()

        /**
         * Where the translation is drawn for an area (the desktop's common.js rectFor, the same
         * formula as the overlay's): [r] is the read area in pixels of a [cw] x [ch] picture.
         */
        fun rectFor(placement: String, r: RectF, cw: Float, ch: Float, a: JSONObject): RectF {
            fun clamp(v: Float, lo: Float, hi: Float) = v.coerceAtMost(hi).coerceAtLeast(lo)
            if (placement == "custom") {
                val w = (a.optDouble("out_w", 0.3) * cw).toFloat().coerceAtLeast(20f).coerceAtMost(cw)
                val h = (a.optDouble("out_h", 0.2) * ch).toFloat().coerceAtLeast(20f).coerceAtMost(ch)
                val x = clamp((a.optDouble("out_x") * cw).toFloat(), 0f, cw - w)
                val y = clamp((a.optDouble("out_y") * ch).toFloat(), 0f, ch - h)
                return RectF(x, y, x + w, y + h)
            }
            if (placement == "under_text" || placement == "above_text") {
                val h = (r.height() + kotlin.math.floor(ch * 0.4f)).coerceAtMost(ch)
                val w = r.width().coerceAtLeast(50f).coerceAtMost(cw)
                val x = clamp(r.left, 0f, cw - w)
                val y = clamp(if (placement == "under_text") r.top else r.bottom - h, 0f, ch - h)
                return RectF(x, y, x + w, y + h)
            }
            val band = kotlin.math.floor(ch * 0.4f)
            val side = kotlin.math.floor(cw * 0.35f)
            var (x, y, w, h) = when (placement) {
                "below" -> listOf(r.left, r.bottom, r.width(), band)
                "right" -> listOf(r.right, r.top, side, band)
                "left" -> listOf(r.left - side, r.top, side, band)
                "over" -> listOf(r.left, r.bottom - band, r.width(), band)
                else -> listOf(r.left, r.top - band, r.width(), band)
            }
            w = w.coerceAtLeast(50f).coerceAtMost(cw)
            h = h.coerceAtLeast(50f).coerceAtMost(ch)
            x = clamp(x, 0f, cw - w)
            y = clamp(y, 0f, ch - h)
            return RectF(x, y, x + w, y + h)
        }

        /** Where the text sits in its region (the desktop's alignOf): vertical to horizontal. */
        fun alignOf(placement: String, a: JSONObject): Pair<String, String> {
            if (placement == "under_text") return "top" to "left"
            if (placement == "above_text") return "bottom" to "center"
            if (placement == "custom") return a.optString("out_valign").ifEmpty { "bottom" } to a.optString("out_halign").ifEmpty { "center" }
            val tp = a.optString("text_pos").split("-")
            if (tp.size == 2 && tp[0].isNotEmpty() && tp[1].isNotEmpty()) return tp[0] to tp[1]
            return (if (placement == "above" || placement == "over") "bottom" else "top") to
                when (placement) { "right" -> "left"; "left" -> "right"; else -> "center" }
        }

        /** A preset placement turned into a custom region at the same spot (the desktop's toCustom). */
        fun toCustom(a: JSONObject) {
            val placement = a.optString("placement")
            if (placement == "custom") return
            val cw = 1920f
            val ch = 1080f // (the fractions don't depend on the size used here)
            val px = RectF((a.optDouble("x") * cw).toFloat(), (a.optDouble("y") * ch).toFloat(),
                ((a.optDouble("x") + a.optDouble("w")) * cw).toFloat(), ((a.optDouble("y") + a.optDouble("h")) * ch).toFloat())
            val r = rectFor(placement, px, cw, ch, a)
            val (v, h) = alignOf(placement, a)
            fun rd(d: Float) = Math.round(d * 10000.0) / 10000.0
            a.put("out_x", rd(r.left / cw)).put("out_y", rd(r.top / ch)).put("out_w", rd(r.width() / cw)).put("out_h", rd(r.height() / ch))
                .put("out_valign", v).put("out_halign", h).put("placement", "custom")
        }
    }
}
