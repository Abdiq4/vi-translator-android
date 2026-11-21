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
 * The game picture (16:9), as the desktop's preview: the read areas drawn over it (dashed boxes in
 * the accent colour, the selected one with its corner handle), and the latest translation of each
 * area where it would show. While the profile in use can be changed, an area moves when dragged and
 * resizes from its corner (stored as fractions of the picture).
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
    var fps: String? = null

    /** an area was moved or resized (by a drag) */
    var moved: ((JSONObject) -> Unit)? = null

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
        for (i in 0 until areas.length()) {
            val a = areas.optJSONObject(i) ?: continue
            val r = rectOf(a)
            val sel = a.optString("id") == selected
            box.alpha = if (a.optBoolean("enabled", true)) 255 else 90
            box.strokeWidth = k.dp(if (sel) 2.2f else 1.4f).toFloat()
            c.drawRect(r, box)
            // its name, in a little tab above the box
            labelPaint.color = k.accent
            c.drawText(a.optString("name"), r.left + k.dp(3), max(r.top - k.dp(4), labelPaint.textSize), labelPaint)
            if (sel && !cfg.locked()) {
                fill.color = k.accent
                c.drawCircle(r.right, r.bottom, k.dp(7).toFloat(), fill)
            }
            val t = shown[a.optString("id")] ?: sample
            if (!t.isNullOrEmpty() && a.optBoolean("enabled", true)) drawText(c, t, a, Cfg.styleOf(a, style), r)
        }
        fps?.let {
            labelPaint.color = k.neon
            c.drawText(it, width - labelPaint.measureText(it) - k.dp(8), k.dp(16).toFloat(), labelPaint)
        }
    }

    /** The translation in the area's text style, scaled from 1080p to this preview. */
    private fun drawText(c: Canvas, s: String, a: JSONObject, st: JSONObject, area: RectF) {
        val scale = height / 1080f
        textPaint.textSize = (st.optDouble("font_size", 26.0) * a.optDouble("font_scale", 1.0) * scale).toFloat().coerceAtLeast(k.dp(7).toFloat())
        textPaint.typeface = fontFor(a.optString("font_family").ifEmpty { st.optString("font_family") }, st.optBoolean("bold"))
        textPaint.color = Kit.parse(st.optString("text_color", "#ffffff"))
        val pad = (st.optDouble("padding", 10.0) * scale).toFloat()
        // (where the translation goes: its custom region, else over the area)
        val out = if (a.optString("placement") == "custom") RectF(
            (a.optDouble("out_x") * width).toFloat(), (a.optDouble("out_y") * height).toFloat(),
            ((a.optDouble("out_x") + a.optDouble("out_w")) * width).toFloat(), ((a.optDouble("out_y") + a.optDouble("out_h")) * height).toFloat(),
        ) else area
        val w = max(out.width() - 2 * pad, k.dp(20).toFloat()).toInt()
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, textPaint, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, st.optDouble("line_height", 1.5).toFloat().coerceIn(1f, 2.2f) * 0.8f)
            .setTextDirection(if (rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.LTR)
            .build()
        val lw = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val h = layout.height
        val valign = a.optString("out_valign", "bottom")
        val top = when (valign) {
            "top" -> out.top
            "middle" -> out.centerY() - h / 2f - pad
            else -> out.bottom - h - 2 * pad
        }
        val bg = a.optString("background")
        if (bg != "none") {
            val alpha = if (bg == "game") 0.7 else st.optDouble("bg_opacity", 0.65)
            fill.color = Kit.parse(st.optString("bg_color", "#000000"))
            fill.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            val r = (st.optDouble("radius", 10.0) * scale).toFloat()
            c.drawRoundRect(out.centerX() - lw / 2 - pad, top, out.centerX() + lw / 2 + pad, top + h + 2 * pad, r, r, fill)
            fill.alpha = 255
        }
        c.save()
        c.translate(out.centerX() - w / 2f, top + pad)
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

    // ---- dragging an area (move), or its corner (resize)
    private var drag: JSONObject? = null
    private var resizing = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                drag = null
                val areas = cfg.areas()
                val handle = k.dp(22)
                // the selected area's corner first, then the area under the finger (the smallest)
                for (i in 0 until areas.length()) {
                    val a = areas.getJSONObject(i)
                    val r = rectOf(a)
                    if (a.optString("id") == selected && !cfg.locked() && Math.hypot((e.x - r.right).toDouble(), (e.y - r.bottom).toDouble()) < handle) {
                        drag = a
                        resizing = true
                    }
                }
                if (drag == null) {
                    drag = (0 until areas.length()).map { areas.getJSONObject(it) }.filter { rectOf(it).contains(e.x, e.y) }
                        .minByOrNull { it.optDouble("w") * it.optDouble("h") }
                    resizing = false
                }
                if (drag != null) parent?.requestDisallowInterceptTouchEvent(true)
                return drag != null
            }
            MotionEvent.ACTION_MOVE -> {
                val a = drag ?: return false
                if (cfg.locked() || a.optString("id") != selected) return true
                val dx = (e.x - lastX) / width
                val dy = (e.y - lastY) / height
                lastX = e.x
                lastY = e.y
                if (resizing) {
                    a.put("w", round((a.optDouble("w") + dx).coerceIn(0.02, 1 - a.optDouble("x"))))
                    a.put("h", round((a.optDouble("h") + dy).coerceIn(0.02, 1 - a.optDouble("y"))))
                } else {
                    a.put("x", round((a.optDouble("x") + dx).coerceIn(0.0, 1 - a.optDouble("w"))))
                    a.put("y", round((a.optDouble("y") + dy).coerceIn(0.0, 1 - a.optDouble("h"))))
                }
                invalidate()
                moved?.invoke(a)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val a = drag
                drag = null
                if (a != null && Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) < k.dp(8)) {
                    selected = a.optString("id")
                    tapped?.invoke(a.optString("id"))
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun round(v: Double) = Math.round(v * 10000) / 10000.0
}
