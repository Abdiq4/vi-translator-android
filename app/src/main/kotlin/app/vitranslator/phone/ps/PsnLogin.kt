package app.vitranslator.phone.ps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import java.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.vitranslator.phone.Lang
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import app.vitranslator.phone.ui.clearOfSystemBars

/**
 * The PSN account ID, which registering with a PS5 needs: the user signs in on Sony's own page
 * (Remote Play's sign-in, as chiaki-ng does it), which sends back a code; the code gives a token,
 * and the token's details give the account's number (8 bytes, little-endian, in base64). The app
 * keeps only the account ID: no password, and the token is dropped.
 */
object Psn {
    // Sony's Remote Play app (the PC one), as used by chiaki-ng (gui/include/psnaccountid.h)
    private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
    const val REDIRECT = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private const val SCOPE = "psn:clientapp referenceDataService:countryConfig.read pushNotification:webSocket.desktop.connect sessionManager:remotePlaySession.system.update"
    private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"

    val loginUrl: String
        get() = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize?service_entity=urn:service-entity:psn" +
            "&response_type=code&client_id=$CLIENT_ID&redirect_uri=${enc(REDIRECT)}&scope=${enc(SCOPE)}" +
            "&request_locale=en_US&ui=pr&service_logo=ps&layout_type=popup&smcid=remoteplay&prompt=always&PlatformPrivacyWs1=minimal"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private val basic = "Basic " + Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())

    /** The account ID (base64) for the code Sony's page redirected with. Blocking. */
    fun accountId(code: String): String {
        val body = "grant_type=authorization_code&code=${enc(code)}&scope=${enc(SCOPE)}&redirect_uri=${enc(REDIRECT)}"
        val token = JSONObject(http(TOKEN_URL, body)).getString("access_token")
        val info = JSONObject(http("$TOKEN_URL/${enc(token)}", null))
        return accountIdOf(info.getString("user_id"))
    }

    /** The account's number (decimal) as Remote Play's account ID: 8 bytes little-endian, base64. */
    fun accountIdOf(userId: String): String {
        var n = userId.trim().toULong()
        val b = ByteArray(8)
        for (i in 0 until 8) {
            b[i] = (n and 0xffUL).toByte()
            n = n shr 8
        }
        return Base64.getEncoder().encodeToString(b)
    }

    private fun http(url: String, form: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Authorization", basic)
            if (form != null) {
                val bytes = form.toByteArray()
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(bytes) }
            }
            val ok = c.responseCode in 200..299
            val text = (if (ok) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (!ok) throw IOException("PSN answered ${c.responseCode}: ${text.take(200)}")
            return text
        } finally {
            c.disconnect()
        }
    }
}

/** Sony's sign-in page; returns [EXTRA_ACCOUNT_ID] (or an [EXTRA_ERROR]). */
class PsnLoginActivity : Activity() {
    private lateinit var web: WebView
    private var done = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // (Sony's page asks to sign in every time: prompt=always. The cookies are shared with the
        // Xbox player, so they aren't cleared here)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Sony's sign-in page talks to its account servers on another domain, with their cookies
        // (blocked in a WebView by default: "can't connect to the server" at Sign In)
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        // (the WebView's own identity is kept: Sony's bot check compares it with the browser's other
        // signals. Its "Can't connect to the server" comes and goes: waiting a little on the page,
        // then Sign In again, gets through)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = caught(request.url)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                caught(Uri.parse(url))
            }
        }
        web.clearOfSystemBars()
        setContentView(web)
        web.loadUrl(Psn.loginUrl)
    }

    /** Sony's page sends back to the redirect address with ?code=: take it, never load it. */
    private fun caught(u: Uri): Boolean {
        if (!u.toString().startsWith(Psn.REDIRECT)) return false
        if (done) return true
        done = true
        web.stopLoading()
        val code = u.getQueryParameter("code")
        if (code.isNullOrEmpty()) {
            finishWith(null, u.getQueryParameter("error_description") ?: u.getQueryParameter("error") ?: "no code")
            return true
        }
        Thread {
            val r = try {
                Psn.accountId(code) to null
            } catch (e: Exception) {
                null to (e.message ?: e.toString())
            }
            runOnUiThread { finishWith(r.first, r.second) }
        }.start()
        return true
    }

    private fun finishWith(accountId: String?, error: String?) {
        val data = Intent()
        if (accountId != null) data.putExtra(EXTRA_ACCOUNT_ID, accountId) else data.putExtra(EXTRA_ERROR, error)
        setResult(if (accountId != null) RESULT_OK else RESULT_CANCELED, data)
        finish()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_ERROR = "error"
    }
}
