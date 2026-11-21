package app.vitranslator.phone.ps

import android.content.Context
import java.util.Base64
import android.util.Log
import app.vitranslator.phone.ps.chiaki.ChiakiLog
import app.vitranslator.phone.ps.chiaki.CreateError
import app.vitranslator.phone.ps.chiaki.DiscoveryHost
import app.vitranslator.phone.ps.chiaki.DiscoveryService
import app.vitranslator.phone.ps.chiaki.DiscoveryServiceOptions
import app.vitranslator.phone.ps.chiaki.Regist
import app.vitranslator.phone.ps.chiaki.RegistEventCanceled
import app.vitranslator.phone.ps.chiaki.RegistEventFailed
import app.vitranslator.phone.ps.chiaki.RegistEventSuccess
import app.vitranslator.phone.ps.chiaki.RegistInfo
import app.vitranslator.phone.ps.chiaki.Target
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/** The PS5 this phone is registered with (Remote Play's keys for it), kept across runs. */
data class PsConsole(
    val host: String,
    val nickname: String,
    val mac: ByteArray,
    val registKey: ByteArray,
    val rpKey: ByteArray,
) {
    fun toJson(): String = JSONObject()
        .put("host", host).put("nickname", nickname)
        .put("mac", b64(mac)).put("regist_key", b64(registKey)).put("rp_key", b64(rpKey)).toString()

    /** The number the PS5 wakes up for (its registration key, written in hex). */
    val credential: ULong?
        get() {
            val end = registKey.indexOfFirst { it == 0.toByte() }.let { if (it >= 0) it else registKey.size }
            return try {
                registKey.copyOfRange(0, end).toString(StandardCharsets.UTF_8).toULong(16)
            } catch (e: NumberFormatException) {
                null
            }
        }

    companion object {
        private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
        private fun unb64(s: String) = Base64.getDecoder().decode(s)

        fun fromJson(s: String): PsConsole? = try {
            val o = JSONObject(s)
            PsConsole(o.getString("host"), o.optString("nickname"), unb64(o.getString("mac")), unb64(o.getString("regist_key")), unb64(o.getString("rp_key")))
        } catch (e: Exception) {
            null
        }
    }
}

object Ps5 {
    private const val TAG = "Ps5"
    private const val DISCOVERY_PORT = 987

    fun saved(ctx: Context): PsConsole? =
        ctx.getSharedPreferences("ps5", Context.MODE_PRIVATE).getString("console", null)?.let { PsConsole.fromJson(it) }

    fun save(ctx: Context, c: PsConsole?) {
        ctx.getSharedPreferences("ps5", Context.MODE_PRIVATE).edit().apply {
            if (c == null) remove("console") else putString("console", c.toJson())
        }.apply()
    }

    /** The PSN account ID from the sign-in (base64 of 8 bytes), kept for registering again. */
    fun accountId(ctx: Context): String = ctx.getSharedPreferences("ps5", Context.MODE_PRIVATE).getString("account_id", "") ?: ""

    fun saveAccountId(ctx: Context, id: String) {
        ctx.getSharedPreferences("ps5", Context.MODE_PRIVATE).edit().putString("account_id", id).apply()
    }

    /** Looks for PlayStations on the home network while open; [found] runs on chiaki's thread. */
    fun discover(found: (List<DiscoveryHost>) -> Unit): DiscoveryService? = try {
        DiscoveryService(DiscoveryServiceOptions(4U, 3U, 500U, InetSocketAddress("255.255.255.255", DISCOVERY_PORT))) { hosts ->
            found(hosts.filter { it.isPS5 })
        }
    } catch (e: CreateError) {
        Log.e(TAG, "discovery failed: $e")
        null
    }

    /** Wake a PS5 in rest mode. */
    fun wakeup(c: PsConsole) {
        val cred = c.credential ?: return
        DiscoveryService.wakeup(null, c.host, cred, true)
    }

    /**
     * Register with the PS5 at [host], with the PSN account ID (base64) and the 8-digit PIN the PS5
     * shows (Settings, System, Remote Play, Link Device). [done] gets the console, or null.
     */
    fun register(host: String, accountIdB64: String, pin: Int, log: (String) -> Unit, done: (PsConsole?) -> Unit): Regist? {
        val id = try {
            Base64.getDecoder().decode(accountIdB64.trim())
        } catch (e: IllegalArgumentException) {
            null
        }
        if (id == null || id.size != RegistInfo.ACCOUNT_ID_SIZE) {
            log("bad account ID")
            done(null)
            return null
        }
        val info = RegistInfo(Target.PS5_1, host, false, null, id, pin)
        val chiakiLog = ChiakiLog(ChiakiLog.Level.ALL.value and ChiakiLog.Level.VERBOSE.value.inv()) { level, text ->
            log(ChiakiLog.formatLog(level, text))
        }
        return try {
            Regist(info, chiakiLog) { ev ->
                when (ev) {
                    is RegistEventSuccess -> done(
                        PsConsole(host, ev.host.serverNickname, ev.host.serverMac, ev.host.rpRegistKey, ev.host.rpKey),
                    )
                    RegistEventFailed, RegistEventCanceled -> done(null)
                }
            }
        } catch (e: CreateError) {
            log("registration failed to start: $e")
            done(null)
            null
        }
    }
}
