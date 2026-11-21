package app.vitranslator.phone.xbox

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.window.OnBackInvokedDispatcher
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.vitranslator.phone.App
import app.vitranslator.phone.Lang
import app.vitranslator.phone.R
import app.vitranslator.phone.read.BitmapFrame
import app.vitranslator.phone.run.TranslateService

/**
 * Xbox: Microsoft's own Remote Play web player, in the app, signed in by the user there. While it
 * plays, the app copies the page's picture about 4 times a second (PixelCopy of this window) for
 * text recognition. If the copies come out black (the video drawn where a copy of the window
 * can't see it), it offers to capture this app's screen instead (MediaProjection), which Android
 * asks the user to allow.
 */
class XboxActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var fallback: Button
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("xbox-capture").apply { start() }
    private val capture = Handler(thread.looper)
    private var bitmap: Bitmap? = null
    private var running = false
    private var black = 0
    private var setupOnly = false

    @Volatile
    private var loaded = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // (the stream stops with the screen: it stays on while the player is open)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Microsoft's player is made for desktop browsers: a phone's is sent to the Xbox app
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                loaded = true
            }

            // sign-in and the player stay in here; anything else is opened by the phone's browser
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return true
                if (ALLOWED.any { host == it || host.endsWith(".$it") }) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (ignored: Exception) {
                    // (no browser)
                }
                return true
            }
        }

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(16, 8, 16, 8)
            textSize = 12f
        }
        fallback = Button(this).apply {
            text = getString(R.string.xbox_capture_screen)
            visibility = View.GONE
            setOnClickListener { askProjection() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(status)
            addView(fallback)
        }
        // (the bar is under the player, not over it: the copies are of the player alone, so the app
        // never reads its own words)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
        }
        if (savedInstanceState == null) web.loadUrl(URL) else web.restoreState(savedInstanceState)
        // (opened from Setup only to sign in and choose the console: nothing is read or translated)
        setupOnly = intent.getBooleanExtra(EXTRA_SETUP, false)
        if (setupOnly) status.text = getString(R.string.xbox_setup_status) else TranslateService.start(this, TranslateService.Source.XBOX)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        running = true
        if (!setupOnly) capture.post(tick)
    }

    override fun onPause() {
        running = false
        capture.removeCallbacks(tick)
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        running = false
        thread.quitSafely()
        web.destroy()
        super.onDestroy()
    }

    /** Back: the player's page back, else leave. */
    private fun back() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && Build.VERSION.SDK_INT < 33) {
            back()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** One copy of the page's picture, then the next a quarter of a second later. */
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (TranslateService.projecting) {
                // (MediaProjection gives the frames now)
                capture.postDelayed(this, 1000)
                return
            }
            val loc = IntArray(2)
            web.getLocationInWindow(loc)
            val w = web.width
            val h = web.height
            if (w <= 0 || h <= 0) {
                capture.postDelayed(this, PERIOD_MS)
                return
            }
            val b = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
            try {
                PixelCopy.request(window, Rect(loc[0], loc[1], loc[0] + w, loc[1] + h), b, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        noteBlack(isBlack(b))
                        App.reader?.offer(BitmapFrame(b))
                        val r = App.reader
                        if (r != null) main.post { status.text = getString(R.string.xbox_status, r.read, r.frameSize) }
                    }
                    if (running) capture.postDelayed(this, PERIOD_MS)
                }, capture)
            } catch (e: IllegalArgumentException) {
                // (the window isn't drawn yet)
                capture.postDelayed(this, PERIOD_MS)
            }
        }
    }

    /**
     * After ~10 s of black copies of a loaded page, offer to capture the screen instead (the video
     * is drawn where PixelCopy can't see it); a picture again takes the offer back.
     */
    private fun noteBlack(isBlack: Boolean) {
        if (!loaded) return
        if (!isBlack) {
            if (black >= BLACK_FRAMES) main.post { fallback.visibility = View.GONE }
            black = 0
            return
        }
        if (++black == BLACK_FRAMES) main.post { fallback.visibility = View.VISIBLE }
    }

    private fun askProjection() {
        val pm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(pm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            val loc = IntArray(2)
            web.getLocationOnScreen(loc)
            TranslateService.project(this, resultCode, data, Rect(loc[0], loc[1], loc[0] + web.width, loc[1] + web.height))
            fallback.visibility = View.GONE
        }
    }

    companion object {
        const val URL = "https://play.xbox.com/remoteplay"
        private const val PERIOD_MS = 250L
        private const val REQ_PROJECTION = 1
        private const val BLACK_FRAMES = 40
        private val ALLOWED = listOf(
            "xbox.com", "live.com", "microsoft.com", "microsoftonline.com", "msftauth.net", "msauth.net",
            "live.net", "xboxlive.com", "gssv-play-prod.xboxlive.com", "akamaized.net", "msecnd.net",
        )

        /** A current desktop Edge, which the player supports. */
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0"

        /** Every 64th pixel dark: a copy with no video in it. */
        fun isBlack(b: Bitmap): Boolean {
            val w = b.width
            val h = b.height
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val p = b.getPixel(x, y)
                    if ((p shr 16 and 255) + (p shr 8 and 255) + (p and 255) > 48) return false
                    x += 64
                }
                y += 64
            }
            return true
        }

        private const val EXTRA_SETUP = "setup"

        /** The player, to play and translate. */
        fun open(c: Context) {
            c.startActivity(Intent(c, XboxActivity::class.java))
        }

        /** The player only to sign in and choose the console (Setup): nothing is read. */
        fun openForSetup(c: Context) {
            c.startActivity(Intent(c, XboxActivity::class.java).putExtra(EXTRA_SETUP, true))
        }

        /**
         * Signed in to Microsoft in the player, as far as the app can tell: Microsoft's sign-in keeps
         * its "stay signed in" cookies on login.live.com (the player itself keeps its session in its
         * own storage, which the app can't read).
         */
        fun signedIn(): Boolean {
            val c = android.webkit.CookieManager.getInstance().getCookie("https://login.live.com") ?: return false
            return listOf("MSPAuth", "WLSSC", "ESTSAUTHPERSISTENT").any { c.contains("$it=") }
        }

        /** Forget the Microsoft sign-in and the player's saved session (cookies and its storage). */
        fun signOut() {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    }
}
