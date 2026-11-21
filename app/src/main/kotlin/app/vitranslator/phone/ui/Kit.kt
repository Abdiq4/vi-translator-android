package app.vitranslator.phone.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * The desktop app's look ("Vice City", from its logo; ui/style.css), for Android views: a night-purple
 * base, a sunset pink → orange accent, a neon teal for "live". Dark is the main theme; light keeps
 * the accents on a soft, warm white. Every screen is built from the parts here.
 */
class Kit(val ctx: Context, val dark: Boolean) {
    private val cache = HashMap<String, Typeface>()

    // ---- colours (style.css :root and [data-theme="dark"])
    val bg = if (dark) 0xff0f0b17.toInt() else 0xfff7f3fa.toInt()
    val panel = if (dark) 0xff17111f.toInt() else 0xffffffff.toInt()
    val panel2 = if (dark) 0xff20182b.toInt() else 0xfff1eaf6.toInt()
    val panel3 = if (dark) 0xff2a2038.toInt() else 0xffe9dff1.toInt()
    val line = if (dark) 0xff2f2440.toInt() else 0xffe3d8ec.toInt()
    val text = if (dark) 0xfff1ebf8.toInt() else 0xff241a31.toInt()
    val muted = if (dark) 0xffa193b5.toInt() else 0xff6f6283.toInt()
    val pink = if (dark) 0xffff5aa5.toInt() else 0xffe0367f.toInt()
    val orange = if (dark) 0xffff9a4a.toInt() else 0xfff07a2a.toInt()
    val neon = if (dark) 0xff3ef0d8.toInt() else 0xff0d9488.toInt()
    val accentUi = if (dark) 0xffff6fae.toInt() else 0xffd42e78.toInt()
    val accentSoft = if (dark) 0x1fff5aa5 else 0x1ae0367f
    val stage = if (dark) 0xff07050b.toInt() else 0xff0d0915.toInt()
    val ok = if (dark) 0xff3ef0d8.toInt() else 0xff0f8f6b.toInt()
    val warn = if (dark) 0xffffb35c.toInt() else 0xffc2620a.toInt()
    val err = if (dark) 0xffff7a7a.toInt() else 0xffdc2626.toInt()

    /** the read-area boxes (drawn over the dark game picture) */
    val accent = 0xffffd25a.toInt()
    val sunsetFrom = 0xffff4f9a.toInt()
    val sunsetTo = 0xffff8a3d.toInt()

    // ---- type: the desktop's Tajawal for right-to-left interfaces, the system's sans for the rest
    private val arabicUi = T.code in listOf("ar", "fa", "ur")
    val regular: Typeface = if (arabicUi) font("Tajawal-Regular.ttf") else Typeface.create("sans-serif", Typeface.NORMAL)
    val bold: Typeface = if (arabicUi) font("Tajawal-Bold.ttf") else Typeface.create("sans-serif", Typeface.BOLD)
    val heavy: Typeface = if (arabicUi) font("Tajawal-ExtraBold.ttf") else Typeface.create("sans-serif-black", Typeface.NORMAL)
    val base = if (arabicUi) 15f else 14f

    /** the smallest touch target (Android's guideline) */
    val touch = 48

    fun font(file: String): Typeface = cache.getOrPut(file) {
        try {
            Typeface.createFromAsset(ctx.assets, "fonts/$file")
        } catch (e: RuntimeException) {
            Typeface.DEFAULT
        }
    }

    fun dp(v: Number): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    // ---- shapes
    fun round(color: Int, radius: Number, stroke: Int? = null, strokeWidth: Number = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(strokeWidth), stroke)
        }

    fun sunset(radius: Number): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(sunsetFrom, sunsetTo)).apply { cornerRadius = dp(radius).toFloat() }

    /** A panel: the desktop's .card / section look. */
    fun card(pad: Int = 14, vararg kids: View): LinearLayout = column(*kids).apply {
        background = round(panel, 14, line)
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        elevation = dp(1).toFloat()
    }

    fun column(vararg kids: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        for (k in kids) addView(k)
    }

    fun row(vararg kids: View, gap: Int = 8) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        kids.forEachIndexed { i, k ->
            // (a child's own layout is kept: grow()'s weight, an icon's size)
            val p = (k.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) p.marginStart = dp(gap)
            addView(k, p)
        }
    }

    /** [v] takes the row's free width */
    fun grow(v: View): View = v.also {
        // (an empty spacer: no height of its own, or it would take the row's whole height)
        it.layoutParams = LinearLayout.LayoutParams(0, if (v.javaClass == View::class.java) 1 else ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    fun gap(h: Int) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    fun scroll(content: View) = ScrollView(ctx).apply {
        isFillViewport = true
        addView(content)
    }

    // ---- text
    fun text(s: CharSequence, size: Float = base, color: Int = text, face: Typeface = regular) = TextView(ctx).apply {
        this.text = s
        // (a sentence in the interface's direction, even when it starts with a Latin word: "Xbox: …")
        textDirection = if (T.rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        textSize = size
        setTextColor(color)
        typeface = face
        setLineSpacing(0f, 1.15f)
    }

    /** a section title (h3) */
    fun h3(s: String) = text(s, base + 2f, text, bold).apply { setPadding(0, dp(18), 0, dp(6)) }

    fun muted(s: CharSequence, small: Boolean = false) = text(s, if (small) base - 1f else base - 0.5f, muted)

    /** the desktop's small uppercase label (glance cards, sub-headings) */
    fun label(s: String) = text(s.uppercase(), base - 2.5f, muted, bold).apply { letterSpacing = 0.06f }

    fun badge(s: String, on: Boolean = false) = text(s, base - 2.5f, if (on) Color.WHITE else muted, bold).apply {
        background = if (on) sunset(999) else round(panel2, 999)
        setPadding(dp(9), dp(3), dp(9), dp(3))
    }

    /** a note box (.callout) */
    fun callout(s: CharSequence = "", tone: Int? = null) = text(s, base - 0.5f, tone ?: text).apply {
        background = round(panel2, 9)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    // ---- buttons
    fun button(s: String, primary: Boolean = false, danger: Boolean = false, icon: Int = 0, click: () -> Unit) = TextView(ctx).apply {
        text = s
        textSize = base
        typeface = bold
        gravity = Gravity.CENTER
        minHeight = dp(touch)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        setTextColor(if (primary) Color.WHITE else if (danger) err else this@Kit.text)
        background = if (primary) sunset(9) else round(panel2, 9, line)
        if (icon != 0) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(icon, if (primary) Color.WHITE else this@Kit.text, 18), null, null, null)
            compoundDrawablePadding = dp(8)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        pressFeedback(this)
    }

    fun link(s: String, click: () -> Unit) = text(s, base, accentUi, bold).apply {
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(touch)
        setPadding(dp(8), dp(10), dp(8), dp(10))
        isClickable = true
        setOnClickListener { click() }
    }

    fun iconButton(icon: Int, desc: String, click: () -> Unit) = ImageView(ctx).apply {
        setImageDrawable(tinted(icon, muted, 21))
        scaleType = ImageView.ScaleType.CENTER
        background = round(panel2, 999)
        contentDescription = desc
        layoutParams = LinearLayout.LayoutParams(dp(touch), dp(touch))
        setOnClickListener { click() }
        pressFeedback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun pressFeedback(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.75f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = if (view.isEnabled) 1f else 0.45f
            }
            false
        }
    }

    fun tinted(res: Int, color: Int, sizeDp: Int): Drawable? = ctx.getDrawable(res)?.mutate()?.apply {
        setTint(color)
        setBounds(0, 0, dp(sizeDp), dp(sizeDp))
    }

    fun icon(res: Int, color: Int = muted, sizeDp: Int = 18) = ImageView(ctx).apply {
        setImageDrawable(tinted(res, color, sizeDp))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    // ---- switches: the desktop's .switch (a label and a track, filled with the sunset when on)
    fun switch(label: String, on: Boolean, small: Boolean = false, changed: (Boolean) -> Unit): SwitchRow =
        SwitchRow(this, label, on, small, changed)

    /** a checkbox line (.check), as a switch: easier to hit on a phone */
    fun check(label: String, on: Boolean, changed: (Boolean) -> Unit) = switch(label, on, false, changed)

    // ---- fields
    fun field(label: String, input: View, help: String? = null) = column(
        text(label, base - 1f, muted, bold).apply { setPadding(0, dp(12), 0, dp(6)) },
        input,
    ).also { if (help != null) it.addView(muted(help, true).apply { setPadding(0, dp(4), 0, 0) }) }

    fun input(value: String, hint: String = "", password: Boolean = false, number: Boolean = false, ltr: Boolean = false, changed: (String) -> Unit) =
        EditText(ctx).apply {
            setText(value)
            this.hint = hint
            textSize = base
            typeface = regular
            setTextColor(this@Kit.text)
            setHintTextColor(muted)
            background = round(panel2, 9, line)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(touch)
            isSingleLine = true
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            if (ltr) textDirection = View.TEXT_DIRECTION_LTR
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = changed(s?.toString() ?: "")
            })
        }

    /** A choice (the desktop's <select>): shows the chosen label; a tap opens the list. */
    fun select(options: List<Pair<String, String>>, value: String, title: String = "", fontOf: ((String) -> Typeface?)? = null, changed: (String) -> Unit): TextView {
        val tv = TextView(ctx)
        fun show(v: String) {
            tv.text = options.firstOrNull { it.first == v }?.second ?: v
            fontOf?.invoke(v)?.let { tv.typeface = it } ?: run { tv.typeface = regular }
        }
        tv.apply {
            textSize = base
            setTextColor(this@Kit.text)
            background = round(panel2, 9, line)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(touch)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tinted(app.vitranslator.phone.R.drawable.ic_chevron, muted, 16)?.let { RotatedDrawable(it, 90f) }, null)
            compoundDrawablePadding = dp(6)
            tag = value
            show(value)
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                pick(title, options, tag as String, fontOf) { v ->
                    tag = v
                    show(v)
                    changed(v)
                }
            }
        }
        return tv
    }

    /** The list of a select, in the app's look; [picked] gets the value chosen. */
    fun pick(title: String, options: List<Pair<String, String>>, current: String, fontOf: ((String) -> Typeface?)? = null, picked: (String) -> Unit) {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val list = column()
        if (title.isNotEmpty()) list.addView(text(title, base + 1f, text, bold).apply { setPadding(dp(18), dp(16), dp(18), dp(8)) })
        for ((v, label) in options) {
            if (v.startsWith(GROUP)) {
                list.addView(label(label).apply { setPadding(dp(18), dp(14), dp(18), dp(4)) })
                continue
            }
            val item = row(
                grow(text(label, base, if (v == current) accentUi else text, if (v == current) bold else (fontOf?.invoke(v) ?: regular))),
                icon(app.vitranslator.phone.R.drawable.ic_check, if (v == current) accentUi else Color.TRANSPARENT, 18),
            ).apply {
                setPadding(dp(18), dp(14), dp(18), dp(14))
                minimumHeight = dp(touch + 4)
                setOnClickListener {
                    d.dismiss()
                    if (v != current) picked(v)
                }
            }
            list.addView(item)
        }
        val box = FrameLayout(ctx).apply {
            background = round(panel, 16, line)
            addView(ScrollView(ctx).apply { addView(list) })
        }
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((ctx.resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** A question with OK / Cancel (the desktop's ask()). */
    fun ask(question: String, ok: String, yes: () -> Unit) {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = card(
            18,
            text(question, base + 0.5f),
            gap(16),
            row(
                grow(View(ctx)),
                link(T("Cancel")) { d.dismiss() },
                button(ok, danger = true) {
                    d.dismiss()
                    yes()
                },
            ),
        )
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((ctx.resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** A slider with its value shown (the desktop's range + .val). */
    fun slider(min: Float, max: Float, step: Float, value: Float, fmt: (Float) -> String, changed: (Float) -> Unit): LinearLayout {
        val steps = ((max - min) / step).toInt().coerceAtLeast(1)
        val v = text(fmt(value), base - 1f, muted, bold).apply { minWidth = dp(48); gravity = Gravity.END }
        val sb = SeekBar(ctx).apply {
            this.max = steps
            minimumHeight = dp(touch)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            progress = ((value - min) / step).toInt().coerceIn(0, steps)
            progressTintList = ColorStateList.valueOf(pink)
            thumbTintList = ColorStateList.valueOf(pink)
            progressBackgroundTintList = ColorStateList.valueOf(panel3)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val x = min + p * step
                    v.text = fmt(x)
                    if (fromUser) changed(x)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(v)
        }
    }

    fun hscroll(content: View) = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        addView(content)
    }

    /** A colour swatch that cycles through a palette on tap (the desktop's colour inputs). */
    fun colorPick(value: String, changed: (String) -> Unit): View {
        val sw = View(ctx).apply { minimumHeight = dp(touch) }
        val label = text(value, base - 1f, muted)
        fun paint(c: String) {
            sw.background = round(parse(c), 8, line)
            label.text = c
        }
        paint(value)
        val presets = listOf("#ffffff", "#ffd25a", "#3ef0d8", "#ff5aa5", "#ff9a4a", "#000000", "#1a1400", "#20182b")
        val row = row(sw, label)
        sw.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        row.minimumHeight = dp(touch)
        row.setOnClickListener {
            pick(T("Colour"), presets.map { it to it } + listOf("custom" to T("Custom…")), label.text.toString()) { c ->
                if (c == "custom") {
                    val e = input(label.text.toString(), "#rrggbb", ltr = true) {}
                    val d = Dialog(ctx)
                    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    d.setContentView(card(18, e, gap(12), button(T("OK"), primary = true) {
                        val v = e.text.toString().trim()
                        if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) {
                            paint(v.lowercase())
                            changed(v.lowercase())
                        }
                        d.dismiss()
                    }))
                    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    d.show()
                } else {
                    paint(c)
                    changed(c)
                }
            }
        }
        return row
    }

    companion object {
        /** an option that is a heading in a select's list */
        const val GROUP = "\u0000group:"

        fun parse(c: String): Int = try {
            Color.parseColor(c)
        } catch (e: IllegalArgumentException) {
            Color.WHITE
        }
    }
}

/** A drawable turned by [degrees] (the chevron, pointing down). */
class RotatedDrawable(private val d: Drawable, private val degrees: Float) : Drawable() {
    init {
        bounds = d.bounds
    }

    override fun draw(c: Canvas) {
        c.save()
        c.rotate(degrees, bounds.exactCenterX(), bounds.exactCenterY())
        d.bounds = bounds
        d.draw(c)
        c.restore()
    }

    override fun getIntrinsicWidth() = d.bounds.width()
    override fun getIntrinsicHeight() = d.bounds.height()
    override fun setAlpha(a: Int) { d.alpha = a }
    override fun setColorFilter(f: android.graphics.ColorFilter?) { d.colorFilter = f }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** The desktop's .switch: a label, then a track that fills with the sunset when on. */
@SuppressLint("ViewConstructor")
class SwitchRow(private val k: Kit, label: String, on: Boolean, small: Boolean, private val changed: (Boolean) -> Unit) : LinearLayout(k.ctx) {
    var on = on
        private set
    private val track = Track()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val t = k.text(label, if (small) k.base - 1f else k.base, k.text, if (small) k.bold else k.regular)
        addView(t, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(track, LayoutParams(k.dp(44), k.dp(26)).apply { marginStart = k.dp(10) })
        setPadding(0, k.dp(if (small) 6 else 11), 0, k.dp(if (small) 6 else 11))
        minimumHeight = k.dp(if (small) 44 else k.touch)
        isClickable = true
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            set(!this.on)
            changed(this.on)
        }
    }

    fun set(v: Boolean) {
        on = v
        track.invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }

    private inner class Track : View(k.ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = RectF(0f, 0f, w, h)
            if (on) {
                p.shader = LinearGradient(0f, 0f, w, 0f, k.sunsetFrom, k.sunsetTo, Shader.TileMode.CLAMP)
            } else {
                p.shader = null
                p.color = k.panel3
            }
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.shader = null
            p.color = Color.WHITE
            val kr = h / 2 - k.dp(3)
            // (the knob goes to the end when on, whatever the direction)
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            val atEnd = on != rtl
            c.drawCircle(if (atEnd) w - h / 2 else h / 2, h / 2, kr, p)
        }
    }
}
