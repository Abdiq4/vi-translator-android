package app.vitranslator.phone.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import app.vitranslator.phone.App
import app.vitranslator.phone.Lang
import app.vitranslator.phone.Prefs
import app.vitranslator.phone.R
import app.vitranslator.phone.Updater
import app.vitranslator.phone.core.CoreListener
import app.vitranslator.phone.core.PairResult
import app.vitranslator.phone.core.Tv
import app.vitranslator.phone.ps.Ps5
import app.vitranslator.phone.ps.PsnLoginActivity
import app.vitranslator.phone.ps.chiaki.Chiaki
import app.vitranslator.phone.ps.chiaki.DiscoveryHost
import app.vitranslator.phone.ps.chiaki.DiscoveryService
import app.vitranslator.phone.ps.chiaki.Regist
import app.vitranslator.phone.run.Live
import app.vitranslator.phone.run.TranslateService
import app.vitranslator.phone.xbox.XboxActivity

/**
 * The one screen, top to bottom: the console (and its setup), the TVs, the game and language,
 * Start translating, and the lines read and translated, live.
 */
class MainActivity : Activity(), Live.Watcher, CoreListener {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout

    private lateinit var psPanel: LinearLayout
    private lateinit var xboxPanel: LinearLayout
    private lateinit var psButton: Button
    private lateinit var xboxButton: Button
    private lateinit var psStatus: TextView
    private lateinit var psHost: EditText
    private lateinit var psAccount: EditText
    private lateinit var psPin: EditText
    private lateinit var psFound: LinearLayout
    private lateinit var tvList: LinearLayout
    private lateinit var tvFound: LinearLayout
    private lateinit var tvAddress: EditText
    private lateinit var tvCode: EditText
    private lateinit var tvStatus: TextView
    private lateinit var startButton: Button
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var lines: LinearLayout
    private lateinit var update: Button

    private var discovery: DiscoveryService? = null
    private var regist: Regist? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        build()
        debugOptions(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    override fun onResume() {
        super.onResume()
        Live.watch(this)
        App.core.setListener(this)
        refreshConsole()
        refreshTvs()
        changed()
        linesChanged()
        main.post(tick)
        Updater.check(this) { main.post { showUpdate() } }
        showUpdate()
    }

    override fun onPause() {
        Live.unwatch(this)
        App.core.setListener(null)
        main.removeCallbacks(tick)
        stopDiscovery()
        super.onPause()
    }

    override fun onDestroy() {
        regist?.dispose()
        super.onDestroy()
    }

    // ---- building the screen

    private fun build() {
        update = button(R.string.update_available) { Updater.available?.let { r -> Updater.install(this, r) { main.post { showUpdate() } } } }
        update.visibility = View.GONE
        root.addView(update)
        root.addView(row(
            TextView(this).apply { text = getString(R.string.ui_language) },
            spinner(Lang.ALL.map { it.name }.let { listOf(getString(R.string.phone_language)) + it }, Lang.ALL.indexOfFirst { it.tag == prefs.uiLanguage } + 1) { i ->
                val tag = if (i == 0) "" else Lang.ALL[i - 1].tag
                if (tag != prefs.uiLanguage) {
                    prefs.uiLanguage = tag
                    recreate()
                }
            },
        ))

        // 1. the console
        header(R.string.step_console)
        psButton = button(R.string.playstation) { choose("ps5") }
        xboxButton = button(R.string.xbox) { choose("xbox") }
        root.addView(row(psButton, xboxButton))

        psPanel = column()
        psStatus = text("")
        psPanel.addView(psStatus)
        psPanel.addView(hint(R.string.ps_setup_hint))
        psPanel.addView(button(R.string.ps_find) { startDiscovery() })
        psFound = column()
        psPanel.addView(psFound)
        psHost = edit(R.string.ps_address, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        psPanel.addView(psHost)
        psPanel.addView(hint(R.string.psn_secondary_hint))
        psPanel.addView(button(R.string.psn_sign_in) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, PsnLoginActivity::class.java), REQ_PSN)
        })
        psAccount = edit(R.string.psn_account_id, InputType.TYPE_CLASS_TEXT)
        psAccount.setText(Ps5.accountId(this))
        psPanel.addView(psAccount)
        psPin = edit(R.string.ps_pin, InputType.TYPE_CLASS_NUMBER)
        psPanel.addView(psPin)
        psPanel.addView(row(button(R.string.ps_register) { register() }, button(R.string.ps_wake) { Ps5.saved(this)?.let { if (Chiaki.available) Ps5.wakeup(it) } }))
        root.addView(psPanel)

        xboxPanel = column()
        xboxPanel.addView(hint(R.string.xbox_hint))
        root.addView(xboxPanel)

        // 2. the TVs
        header(R.string.step_tv)
        root.addView(hint(R.string.tv_hint))
        tvList = column()
        root.addView(tvList)
        root.addView(button(R.string.tv_find) { findTvs() })
        tvFound = column()
        root.addView(tvFound)
        tvAddress = edit(R.string.tv_address, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        tvCode = edit(R.string.tv_code, InputType.TYPE_CLASS_NUMBER)
        root.addView(tvAddress)
        root.addView(tvCode)
        root.addView(button(R.string.tv_connect) { pairTv() })
        tvStatus = text("")
        root.addView(tvStatus)

        // 3. the game and the language
        header(R.string.step_game)
        val games = App.core.games()
        val langs = App.core.languages()
        root.addView(spinner(games.map { it.name }, games.indexOfFirst { it.id == prefs.game }.coerceAtLeast(0)) { prefs.game = games[it].id })
        root.addView(row(
            TextView(this).apply { text = getString(R.string.translate_into) },
            spinner(langs.map { it.name }, langs.indexOfFirst { it.code == prefs.language }.coerceAtLeast(0)) { prefs.language = langs[it].code },
        ))

        // 4. start
        header(R.string.step_start)
        root.addView(hint(R.string.keep_open_hint))
        startButton = button(R.string.start) { startOrStop() }
        root.addView(startButton)
        status = text("")
        root.addView(status)
        stats = text("").apply { textSize = 12f }
        root.addView(stats)

        // 5. the lines
        header(R.string.step_lines)
        lines = column()
        root.addView(lines)
        root.addView(hint(getString(R.string.about, App.core.name)))
    }

    /**
     * Debug builds only, for testing on emulators:
     * adb shell am start -n app.vitranslator.phone/.ui.MainActivity --es advertise 10.0.2.2:8791
     * (and --es fake_ps5 <address>)
     */
    private fun debugOptions(i: Intent?) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        i?.getStringExtra("advertise")?.let { a -> (App.core as? app.vitranslator.phone.core.StubCore)?.advertise = a }
        // --es fake_ps5 10.0.2.2: a PS5 "linked" with made-up keys, to try the stream's setup
        i?.getStringExtra("fake_ps5")?.let { host ->
            Ps5.save(this, app.vitranslator.phone.ps.PsConsole(host, "fake PS5", ByteArray(6), "0123abcd".toByteArray() + ByteArray(8), ByteArray(16)))
            refreshConsole()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        debugOptions(intent)
    }

    // ---- 1. the console

    private fun choose(console: String) {
        prefs.console = console
        refreshConsole()
    }

    private fun refreshConsole() {
        val c = prefs.console
        psButton.isSelected = c == "ps5"
        xboxButton.isSelected = c == "xbox"
        psButton.alpha = if (c == "ps5") 1f else 0.6f
        xboxButton.alpha = if (c == "xbox") 1f else 0.6f
        psPanel.visibility = if (c == "ps5") View.VISIBLE else View.GONE
        xboxPanel.visibility = if (c == "xbox") View.VISIBLE else View.GONE
        val saved = Ps5.saved(this)
        psStatus.text = when {
            !Chiaki.available -> getString(R.string.ps_not_in_build)
            saved != null -> getString(R.string.ps_registered, saved.nickname.ifEmpty { "PS5" }, saved.host)
            else -> getString(R.string.ps_not_registered)
        }
        if (saved != null && psHost.text.isEmpty()) psHost.setText(saved.host)
    }

    private fun startDiscovery() {
        if (!Chiaki.available) return
        stopDiscovery()
        psFound.removeAllViews()
        psFound.addView(hint(R.string.searching))
        discovery = Ps5.discover { hosts -> main.post { showPs(hosts) } }
        main.postDelayed({ stopDiscovery() }, 15000)
    }

    private fun stopDiscovery() {
        discovery?.dispose()
        discovery = null
    }

    private fun showPs(hosts: List<DiscoveryHost>) {
        psFound.removeAllViews()
        for (h in hosts) {
            val addr = h.hostAddr ?: continue
            val state = getString(if (h.state == DiscoveryHost.State.STANDBY) R.string.ps_standby else R.string.ps_ready)
            psFound.addView(button("${h.hostName ?: "PS5"} · $addr · $state") { psHost.setText(addr) })
        }
    }

    private fun register() {
        if (!Chiaki.available) return
        val host = psHost.text.toString().trim()
        val account = psAccount.text.toString().trim()
        val pin = psPin.text.toString().trim()
        if (host.isEmpty() || account.isEmpty() || !Regex("\\d{8}").matches(pin)) {
            psStatus.text = getString(R.string.ps_register_missing)
            return
        }
        Ps5.saveAccountId(this, account)
        psStatus.text = getString(R.string.ps_registering)
        regist?.dispose()
        regist = Ps5.register(host, account, pin.toInt(), { line -> android.util.Log.i("Regist", line) }) { console ->
            main.post {
                if (console != null) {
                    Ps5.save(this, console)
                    psPin.setText("")
                    refreshConsole()
                } else {
                    psStatus.text = getString(R.string.ps_register_failed)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PSN) return
        val id = data?.getStringExtra(PsnLoginActivity.EXTRA_ACCOUNT_ID)
        if (resultCode == RESULT_OK && id != null) {
            psAccount.setText(id)
            Ps5.saveAccountId(this, id)
        } else if (data?.getStringExtra(PsnLoginActivity.EXTRA_ERROR) != null) {
            psStatus.text = getString(R.string.psn_failed, data.getStringExtra(PsnLoginActivity.EXTRA_ERROR))
        }
    }

    // ---- 2. the TVs

    private fun refreshTvs() {
        Thread {
            val tvs = App.core.pairedTvs()
            main.post {
                tvList.removeAllViews()
                if (tvs.isEmpty()) tvList.addView(hint(R.string.tv_none))
                for (tv in tvs) tvList.addView(row(text("📺 ${tv.name} · ${tv.address}"), button(R.string.tv_forget) { forget(tv) }))
            }
        }.start()
    }

    private fun forget(tv: Tv) {
        Thread {
            App.core.forgetTv(tv.id)
            main.post { refreshTvs() }
        }.start()
    }

    private fun findTvs() {
        tvFound.removeAllViews()
        tvFound.addView(hint(R.string.searching))
        Thread {
            val tvs = App.core.findTvs()
            main.post {
                tvFound.removeAllViews()
                if (tvs.isEmpty()) tvFound.addView(hint(R.string.tv_found_none))
                for (tv in tvs) tvFound.addView(button("${tv.name} · ${tv.address}") {
                    tvAddress.setText(tv.address)
                    tvCode.requestFocus()
                })
            }
        }.start()
    }

    private fun pairTv() {
        val address = tvAddress.text.toString().trim()
        val code = tvCode.text.toString().trim()
        if (address.isEmpty() || !Regex("\\d{6}").matches(code)) {
            tvStatus.text = getString(R.string.tv_missing)
            return
        }
        tvStatus.text = getString(R.string.tv_connecting)
        Thread {
            val r = App.core.pairTv(address, code)
            main.post {
                tvStatus.text = getString(
                    when (r) {
                        PairResult.OK -> R.string.tv_connected
                        PairResult.WRONG_CODE -> R.string.tv_wrong_code
                        PairResult.NEW_CODE -> R.string.tv_new_code
                        PairResult.UNREACHABLE -> R.string.tv_unreachable
                    },
                )
                if (r == PairResult.OK) {
                    tvCode.setText("")
                    tvFound.removeAllViews()
                    refreshTvs()
                }
            }
        }.start()
    }

    // ---- 4. start

    private fun running() = Live.state in listOf(Live.State.STARTING, Live.State.CONNECTING, Live.State.READING)

    private fun startOrStop() {
        if (running()) {
            TranslateService.stop(this)
            return
        }
        when (prefs.console) {
            "ps5" -> TranslateService.start(this, TranslateService.Source.PS5)
            "xbox" -> XboxActivity.open(this)
            else -> status.text = getString(R.string.choose_console)
        }
    }

    override fun changed() {
        startButton.text = getString(if (running()) R.string.stop else R.string.start)
        val d = Live.detail
        status.text = when (Live.state) {
            Live.State.IDLE -> ""
            Live.State.STARTING -> getString(R.string.state_starting)
            Live.State.CONNECTING -> getString(R.string.state_connecting)
            Live.State.READING -> getString(R.string.state_reading)
            Live.State.ENDED -> getString(R.string.state_ended) + (d?.let { " ($it)" } ?: "")
            Live.State.FAILED -> getString(R.string.state_failed, d ?: "")
        }
    }

    override fun askPin(incorrect: Boolean) {
        val e = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this)
            .setTitle(if (incorrect) R.string.ps_login_pin_wrong else R.string.ps_login_pin)
            .setView(e)
            .setPositiveButton(android.R.string.ok) { _, _ -> TranslateService.instance?.pin(e.text.toString()) }
            .show()
    }

    /** Frames counted, twice a second, while translating. */
    private val tick = object : Runnable {
        override fun run() {
            val r = App.reader
            stats.text = if (r != null && running()) getString(R.string.stats, r.read, r.offered, r.lastMs, r.frameSize) else ""
            main.postDelayed(this, 500)
        }
    }

    // ---- 5. the lines

    override fun linesChanged() {
        main.post {
            val ls = App.core.lines(30).asReversed()
            lines.removeAllViews()
            if (ls.isEmpty()) lines.addView(hint(R.string.lines_none))
            val rtl = App.core.languages().firstOrNull { it.code == prefs.language }?.rtl ?: false
            for (l in ls) {
                lines.addView(text(l.original).apply {
                    textSize = 13f
                    alpha = 0.7f
                    textDirection = View.TEXT_DIRECTION_LTR
                })
                lines.addView(text(l.translated).apply {
                    textSize = 17f
                    textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                    setPadding(0, 0, 0, dp(10))
                })
            }
        }
    }

    // ---- updates

    private fun showUpdate() {
        val r = Updater.available
        update.visibility = if (r != null) View.VISIBLE else View.GONE
        if (r != null) {
            update.text = when (Updater.state) {
                Updater.State.DOWNLOADING -> getString(R.string.update_downloading)
                Updater.State.INSTALLING -> getString(R.string.update_installing)
                Updater.State.FAILED -> getString(R.string.update_failed)
                Updater.State.IDLE -> getString(R.string.update_to, r.version)
            }
        }
    }

    // ---- small helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(id: Int) {
        root.addView(TextView(this).apply {
            text = getString(id)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(6))
        })
    }

    private fun text(s: String) = TextView(this).apply {
        text = s
        textSize = 15f
    }

    private fun hint(id: Int) = hint(getString(id))

    private fun hint(s: String) = TextView(this).apply {
        text = s
        textSize = 13f
        alpha = 0.75f
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (v in views) addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun button(id: Int, click: () -> Unit) = button(getString(id), click)

    private fun button(s: String, click: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { click() }
    }

    private fun edit(hintId: Int, type: Int) = EditText(this).apply {
        hint = getString(hintId)
        inputType = type
        isSingleLine = true
    }

    private fun spinner(items: List<String>, selected: Int, picked: (Int) -> Unit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected.coerceAtLeast(0), false)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = picked(position)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    companion object {
        private const val REQ_PSN = 1
    }
}
