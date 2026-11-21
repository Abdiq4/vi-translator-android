package app.vitranslator.phone.core

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The TV apps' side of connecting (the same as the PC app does it): each VI Translator TV app that
 * waits to be connected answers GET /vit-tv on port 8792, and takes the code with this phone's
 * address and a key at POST /vit-tv/pair. Both need the X-VIT header (a web page can't send it).
 * Used by the stand-in core; the real core does this itself.
 */
object TvLink {
    const val TV_PORT = 8792

    /** This phone's IPv4 address on its active network (Wi-Fi), or null. */
    fun ownAddress(ctx: Context): Inet4Address? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) } ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } as Inet4Address?
    }

    /** "192.168.1.30" -> "192.168.1.30:8792"; "192.168.1.30:9000" stays. Null when it isn't an address. */
    fun normalize(address: String): String? {
        val a = address.trim()
        val m = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::(\\d{1,5}))?$").matchEntire(a) ?: return null
        if ((1..4).any { m.groupValues[it].toInt() > 255 }) return null
        val port = m.groupValues[5].ifEmpty { TV_PORT.toString() }.toInt()
        if (port !in 1..65535) return null
        return "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}.${m.groupValues[4]}:$port"
    }

    /** The TV app at [address], or null when none answers there. */
    fun ask(address: String, connectMs: Int = 400, readMs: Int = 1500): Tv? {
        val (status, body) = request("GET", "http://$address/vit-tv", null, connectMs, readMs) ?: return null
        if (status != 200) return null
        return try {
            val o = JSONObject(body)
            if (o.optString("app") != "vi-translator-tv") return null
            Tv(o.optString("id"), o.optString("name").ifBlank { address }, address)
        } catch (e: Exception) {
            null
        }
    }

    /** Every TV app waiting on this phone's /24 network (about two seconds). */
    fun scan(ctx: Context): List<Tv> {
        val mine = ownAddress(ctx) ?: return emptyList()
        val b = mine.address
        val prefix = "${b[0].toInt() and 255}.${b[1].toInt() and 255}.${b[2].toInt() and 255}."
        val me = b[3].toInt() and 255
        val found = Collections.synchronizedList(mutableListOf<Tv>())
        val pool = Executors.newFixedThreadPool(64)
        for (i in 1..254) {
            if (i == me) continue
            pool.execute { ask("$prefix$i:$TV_PORT", 300, 1200)?.let { found.add(it) } }
        }
        pool.shutdown()
        try {
            pool.awaitTermination(15, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        pool.shutdownNow()
        return found.sortedBy { it.name }
    }

    /** Send the code: the TV takes this phone's page address ([pc], "ip:port") and [token]. */
    fun pair(address: String, code: String, pc: String, token: String, pcName: String): Pair<PairResult, Tv?> {
        val body = JSONObject().put("code", code).put("pc", pc).put("token", token).put("pc_name", pcName).toString()
        val (status, answer) = request("POST", "http://$address/vit-tv/pair", body, 1500, 4000)
            ?: return PairResult.UNREACHABLE to null
        return when (status) {
            200 -> {
                val o = try {
                    JSONObject(answer)
                } catch (e: Exception) {
                    JSONObject()
                }
                PairResult.OK to Tv(o.optString("id"), o.optString("name").ifBlank { address }, address)
            }
            429 -> PairResult.NEW_CODE to null
            403 -> PairResult.WRONG_CODE to null
            else -> PairResult.UNREACHABLE to null
        }
    }

    /** (status, body), or null when nothing answered. */
    private fun request(method: String, url: String, body: String?, connectMs: Int, readMs: Int): Pair<Int, String>? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.useCaches = false
            c.setRequestProperty("X-VIT", "1")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else c.inputStream
            status to (stream?.let { read(it, 64 * 1024) } ?: "")
        } catch (e: IOException) {
            null
        } finally {
            c?.disconnect()
        }
    }

    fun read(input: InputStream, max: Int): String = input.use { s ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = s.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("too big")
        }
        out.toString("UTF-8")
    }
}
