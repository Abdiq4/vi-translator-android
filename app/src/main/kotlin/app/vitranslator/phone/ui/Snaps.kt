package app.vitranslator.phone.ui

import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import app.vitranslator.phone.App
import org.json.JSONObject

/**
 * Snapshots: the game's whole picture with every bit of its text translated in place, like a
 * translated photo (the desktop's Snapshot). The core translates (takeSnapshot); this draws it as the
 * desktop's ui/snapshot.js does (each block's English covered, blurred or a solid colour, and its
 * translation fitted into the block, the game's colours kept), shows it, and saves it.
 */
object Snaps {
    private val RTL = setOf("ar", "ur", "fa")

    /** Translate the last frame read; [done] gets the snapshot JSON or an error (on the main thread). */
    fun take(h: Host, done: (JSONObject?, String?) -> Unit) {
        val frame = App.reader?.last
        if (frame == null) {
            done(null, T("No picture yet"))
            return
        }
        h.io {
            val r = try {
                App.core.takeSnapshot(frame.first, frame.second, frame.third)?.let { JSONObject(it) } to null
            } catch (e: Exception) {
                null to (e.message ?: e.toString())
            }
            h.ui { done(r.first, r.second ?: if (r.first == null) T("Not in the phone app yet: coming with an update.") else null) }
        }
    }

    private fun picture(dataUrl: String): Bitmap? = try {
        val b = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(b, 0, b.size)?.copy(Bitmap.Config.ARGB_8888, true)
    } catch (e: Exception) {
        null
    }

    /** The snapshot drawn: translated (or the original picture), with [style] "blur" or "solid" behind the text. */
    fun draw(k: Kit, snap: JSONObject, translated: Boolean, style: String): Bitmap? {
        val ti = snap.optString("translated_image")
        val src = if (translated && ti.isNotEmpty()) ti else snap.optString("image")
        val img = picture(src) ?: return null
        if (!translated || ti.isNotEmpty()) return img
        val w = img.width.toFloat()
        val h = img.height.toFloat()
        val c = Canvas(img)
        val lang = snap.optString("lang")
        val rtl = lang in RTL
        // (a blurred copy of the whole picture: what shows through behind a block's text)
        val blurred = if (style != "solid") Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(img, (w / 24).toInt().coerceAtLeast(1), (h / 24).toInt().coerceAtLeast(1), true), img.width, img.height, true) else null
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = k.font("Tajawal-Bold.ttf") }
        val blocks = snap.optJSONArray("blocks") ?: return img
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            val words = words(b)
            if (words.isEmpty()) continue
            val r = b.optJSONArray("rect") ?: continue
            val lh = maxOf(8f, (b.optDouble("line_h", 0.03) * h).toFloat())
            val pad = maxOf(3f, lh * 0.28f)
            val box = RectF((r.optDouble(0) * w).toFloat() - pad, (r.optDouble(1) * h).toFloat() - pad,
                ((r.optDouble(0) + r.optDouble(2)) * w).toFloat() + pad, ((r.optDouble(1) + r.optDouble(3)) * h).toFloat() + pad)
            val bg = Kit.parse(b.optString("bg", "#000000"))
            c.save()
            c.clipRect(box)
            if (blurred != null) {
                c.drawBitmap(blurred, 0f, 0f, null)
                fill.color = bg
                fill.alpha = (0.82 * 255).toInt()
            } else {
                fill.color = bg
                fill.alpha = 255
            }
            c.drawRoundRect(box, minOf(8f, pad), minOf(8f, pad), fill)
            c.restore()
            val inner = RectF(box.left + pad * 0.4f, box.top + pad * 0.3f, box.right - pad * 0.4f, box.bottom - pad * 0.3f)
            val lines = layout(text, words, inner, lh * 0.95f) ?: continue
            val total = lines.size * text.textSize * 1.28f
            var y = inner.top + (inner.height() - total) / 2 + text.textSize * 1.28f / 2
            val fm = text.fontMetrics
            for (line in lines) {
                val lw = line.sumOf { text.measureText(it.first).toDouble() }.toFloat() + text.measureText(" ") * (line.size - 1)
                var x = when {
                    b.optString("align") == "center" -> inner.left + (inner.width() - lw) / 2
                    rtl && lines.size > 1 -> inner.right - lw
                    else -> inner.left
                }
                // (right to left: the first word on the right)
                for ((word, color) in if (rtl) line.reversed() else line) {
                    text.color = color
                    text.setShadowLayer(text.textSize * 0.18f, 0f, 0f, if (light(color)) 0xa6000000.toInt() else 0x8cffffff.toInt())
                    c.drawText(word, x, y - (fm.ascent + fm.descent) / 2, text)
                    x += text.measureText(word) + text.measureText(" ")
                }
                y += text.textSize * 1.28f
            }
        }
        blurred?.recycle()
        return img
    }

    /** A block's translation as words, each with its colour (the game's highlights kept; icons left out). */
    private fun words(b: JSONObject): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        val fg = Kit.parse(b.optString("fg", "#ffffff"))
        val segs = b.optJSONArray("segs") ?: return out
        for (i in 0 until segs.length()) {
            val s = segs.optJSONArray(i) ?: continue
            val t = s.optString(0)
            val c = s.optString(1)
            if (c.startsWith("icon:")) continue
            val color = when {
                Regex("^#[0-9a-fA-F]{6}$").matches(c) -> Kit.parse(c)
                Regex("^name:#[0-9a-fA-F]{6}$").matches(c) -> Kit.parse(c.substring(5))
                else -> fg
            }
            t.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { out += it to color }
        }
        return out
    }

    /** The biggest size at which the words wrap into the box (a little taller allowed). */
    private fun layout(p: Paint, words: List<Pair<String, Int>>, box: RectF, start: Float): List<List<Pair<String, Int>>>? {
        var size = start
        while (size >= 7f) {
            p.textSize = size
            val space = p.measureText(" ")
            val lines = ArrayList<List<Pair<String, Int>>>()
            var line = ArrayList<Pair<String, Int>>()
            var width = 0f
            var fits = true
            for (wd in words) {
                val ww = p.measureText(wd.first)
                if (line.isNotEmpty() && width + space + ww > box.width()) {
                    lines += line
                    line = ArrayList()
                    width = 0f
                }
                if (ww > box.width() * 1.02f) fits = false
                width += (if (line.isNotEmpty()) space else 0f) + ww
                line += wd
            }
            if (line.isNotEmpty()) lines += line
            if ((fits && lines.size * size * 1.28f <= box.height() * 1.25f) || size * 0.92f < 7f) return lines
            size *= 0.92f
        }
        return null
    }

    private fun light(c: Int) = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c) > 140

    /** Full screen: Translated / Original, Save, Close (the desktop's Snap.show). */
    fun show(h: Host, snap: JSONObject, info: String) {
        val k = h.k
        val style = h.cfg.str("snap_style", "blur")
        val d = Dialog(h.act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val img = ImageView(h.act).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        var translated = true
        var shown: Bitmap? = null
        fun paint() {
            h.io {
                val b = draw(k, snap, translated, style)
                h.ui {
                    shown = b
                    img.setImageBitmap(b)
                }
            }
        }
        val toggle = k.button(T("Original")) {}
        toggle.setOnClickListener {
            translated = !translated
            toggle.text = if (translated) T("Original") else T("Translated")
            paint()
        }
        val bar = k.row(
            k.grow(k.text(info, k.base - 1.5f, Color.WHITE)),
            toggle,
            k.button(T("Save"), primary = true) { shown?.let { b -> h.io { val f = save(h, b, snap.optString("id"), if (translated) snap.optString("lang") else "original"); h.ui { android.widget.Toast.makeText(h.act, if (f != null) T("Saved {file}", "file" to f) else T("Save failed"), android.widget.Toast.LENGTH_SHORT).show() } } } },
            k.button(T("Close")) { d.dismiss() },
            gap = 8,
        ).apply {
            setPadding(k.dp(12), k.dp(8), k.dp(12), k.dp(8))
            setBackgroundColor(0xcc000000.toInt())
        }
        d.setContentView(FrameLayout(h.act).apply {
            setBackgroundColor(Color.BLACK)
            addView(img, FrameLayout.LayoutParams(-1, -1))
            addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        })
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        paint()
        d.show()
    }

    /** Into the phone's Pictures, in VI Translator (Android 10 and later). The file's name, or null. */
    fun save(h: Host, b: Bitmap, id: String, lang: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val name = "snapshot-${id.ifEmpty { System.currentTimeMillis().toString() }}-$lang.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VI Translator")
        }
        return try {
            val uri = h.act.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            h.act.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "Pictures/VI Translator/$name"
        } catch (e: Exception) {
            null
        }
    }

    /** A snapshot taken: shown, and kept in Pictures when the settings say so (the desktop's snap_save). */
    fun got(h: Host, snap: JSONObject, open: Boolean = true): String {
        val blocks = snap.optJSONArray("blocks")?.length() ?: 0
        val s = snap.optDouble("ms") / 1000
        val service = TranslatorPanel.engineName(snap.optString("service"))
        val info = when {
            snap.optString("translated_image").isNotEmpty() -> T("Translated by {service} in {s} s", "service" to service, "s" to String.format(java.util.Locale.ROOT, "%.1f", s))
            blocks > 0 -> T("{n} text block(s) translated by {service} in {s} s", "n" to blocks, "service" to service, "s" to String.format(java.util.Locale.ROOT, "%.1f", s))
            else -> T("No text found on the picture.")
        }
        if (open) show(h, snap, info)
        if (h.cfg.bool("snap_save", true) && snap.optString("translated_image").isEmpty() && blocks > 0) {
            h.io { draw(h.k, snap, true, h.cfg.str("snap_style", "blur"))?.let { save(h, it, snap.optString("id"), snap.optString("lang")) } }
        }
        return info
    }
}
