package app.vitranslator.phone.ui

import android.app.Activity
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.vitranslator.phone.Prefs
import app.vitranslator.phone.R
import app.vitranslator.phone.core.Line

/**
 * The live translations full screen, to read them on the phone like a second screen: the newest at
 * the bottom in large text (the ones before it dimmer above), in portrait or landscape. A bar on top:
 * smaller / larger text (kept), the English line on or off (kept), how it turns (with the phone, or
 * held in portrait or landscape: kept), and close. The screen stays on while it is open.
 *
 * [source] gives the lines to show, oldest first (the Play tab's list, after its clear); [update]
 * is called by the Play tab when lines change.
 */
class LiveFull(
    private val act: Activity,
    private val k: Kit,
    private val rtl: Boolean,
    private val source: () -> List<Line>,
) {
    private val prefs = Prefs(act)
    private val dialog = Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val list = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
    }
    private val scroll = ScrollView(act).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(list, FrameLayout.LayoutParams(-1, -2))
    }
    private val sizeLabel = TextView(act)
    private val englishButton: TextView
    private val rotateButton: TextView
    private val before = act.requestedOrientation

    /** the newest line's id when last drawn (only scrolls to the bottom when one was added) */
    private var newest = -1L

    var onClose: (() -> Unit)? = null

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        englishButton = pill("EN") {
            prefs.liveEnglish = !prefs.liveEnglish
            styleEnglish()
            draw(force = true)
        }
        rotateButton = pill("") {
            prefs.liveRotation = when (prefs.liveRotation) {
                "auto" -> "portrait"
                "portrait" -> "landscape"
                else -> "auto"
            }
            orient()
        }
        val bar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(10), k.dp(8), k.dp(10), k.dp(8))
            setBackgroundColor(0xcc000000.toInt())
            addView(pill("A−", T("Smaller text")) { resize(-SIZE_STEP) })
            addView(sizeLabel.apply {
                setTextColor(0xffbdb3cc.toInt())
                textSize = k.base - 2f
                typeface = k.bold
                gravity = Gravity.CENTER
                minWidth = k.dp(36)
            })
            addView(pill("A+", T("Larger text")) { resize(SIZE_STEP) })
            addView(gap())
            addView(englishButton)
            addView(gap())
            addView(rotateButton)
            addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
            addView(k.iconButton(R.drawable.ic_close, T("Close")) { dialog.dismiss() }.apply {
                setImageDrawable(k.tinted(R.drawable.ic_close, Color.WHITE, 22))
                background = k.round(0x33ffffff, 999)
            })
        }
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.BLACK)
            addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { topMargin = k.dp(64) })
            addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        }
        dialog.setOnShowListener { hideSystemBars() }
        dialog.setOnDismissListener {
            act.requestedOrientation = before
            onClose?.invoke()
        }
        styleEnglish()
        showSize()
    }

    fun show() {
        orient()
        draw(force = true)
        dialog.show()
    }

    /** New or changed lines (the Play tab calls it). */
    fun update() = draw(force = false)

    private fun draw(force: Boolean) {
        val ls = source().takeLast(MAX_LINES)
        val last = ls.lastOrNull()?.id ?: 0L
        list.removeAllViews()
        if (ls.isEmpty()) {
            list.addView(TextView(act).apply {
                text = T("Translations appear here as the game shows text.")
                setTextColor(0xff8f84a3.toInt())
                textSize = k.base + 1f
                gravity = Gravity.CENTER
                setPadding(k.dp(24), k.dp(24), k.dp(24), k.dp(24))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        val size = prefs.liveTextSize
        ls.forEachIndexed { i, l ->
            val latest = i == ls.size - 1
            list.addView(lineView(l, size, latest), LinearLayout.LayoutParams(-1, -2))
        }
        // (stays where the user scrolled, unless a line was added or the look changed)
        if (force || last != newest) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        newest = last
    }

    private fun lineView(l: Line, size: Float, latest: Boolean): View {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(20), k.dp(12), k.dp(20), k.dp(12))
            alpha = if (latest) 1f else 0.55f
        }
        if (prefs.liveEnglish && l.original.isNotBlank()) col.addView(TextView(act).apply {
            text = l.original
            setTextColor(0xff9a90ad.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (size * 0.5f).coerceAtLeast(13f))
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = if (rtl) View.TEXT_ALIGNMENT_VIEW_END else View.TEXT_ALIGNMENT_VIEW_START
        })
        col.addView(TextView(act).apply {
            text = l.translated.ifEmpty { "…" }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            typeface = if (rtl) k.font("Tajawal-Bold.ttf") else k.bold
            setLineSpacing(0f, 1.2f)
            textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        })
        return col
    }

    private fun resize(by: Float) {
        prefs.liveTextSize = (prefs.liveTextSize + by).coerceIn(MIN_SIZE, MAX_SIZE)
        showSize()
        draw(force = true)
    }

    private fun showSize() {
        sizeLabel.text = prefs.liveTextSize.toInt().toString()
    }

    private fun styleEnglish() {
        val on = prefs.liveEnglish
        englishButton.background = if (on) k.sunset(999) else k.round(0x33ffffff, 999)
        englishButton.contentDescription = T("Show the English")
    }

    /** How it turns, as chosen: with the phone (even with auto-rotate off), or held. */
    private fun orient() {
        val (orientation, label) = when (prefs.liveRotation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT to T("Portrait")
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE to T("Landscape")
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR to T("Auto-rotate")
        }
        act.requestedOrientation = orientation
        rotateButton.text = "⟳  $label"
        rotateButton.contentDescription = label
    }

    /** All the screen for the text: the status and navigation bars hidden (a swipe shows them). */
    private fun hideSystemBars() {
        val w = dialog.window ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            w.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun pill(s: String, desc: String? = null, click: () -> Unit) = TextView(act).apply {
        text = s
        setTextColor(Color.WHITE)
        textSize = k.base
        typeface = k.bold
        gravity = Gravity.CENTER
        minWidth = k.dp(k.touch)
        minHeight = k.dp(k.touch)
        setPadding(k.dp(14), 0, k.dp(14), 0)
        background = k.round(0x33ffffff, 999)
        textDirection = View.TEXT_DIRECTION_LTR
        if (desc != null) contentDescription = desc
        isClickable = true
        setOnClickListener { click() }
        k.pressFeedback(this)
    }

    private fun gap() = View(act).apply { layoutParams = LinearLayout.LayoutParams(k.dp(8), 1) }

    companion object {
        const val MIN_SIZE = 16f
        const val MAX_SIZE = 72f
        const val SIZE_STEP = 4f
        /** at most this many lines on screen (the newest) */
        const val MAX_LINES = 30
    }
}
