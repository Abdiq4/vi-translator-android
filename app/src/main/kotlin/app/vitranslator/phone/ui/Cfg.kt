package app.vitranslator.phone.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.vitranslator.phone.core.Core
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * The settings, as the desktop app keeps them (its config.json, held by the core), with the
 * desktop's rules for game profiles (ui/app.js): the profile in use gives `areas`, `style` and
 * `profile_opts`; a built-in profile can't be changed ("Make a copy"); the player's own profiles
 * get every change written back (syncProfile). Changes are saved to the core shortly after they're
 * made, only the keys that changed. Main thread only.
 */
class Cfg(private val core: Core) {
    var c: JSONObject = JSONObject()
        private set
    private val dirty = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    /** told after a save (the header's "Saved ✓") */
    var saved: ((Boolean) -> Unit)? = null

    /** Read the settings from the core (blocking: off the main thread). */
    fun loadBlocking(): JSONObject = JSONObject(core.configJson())

    fun set(json: JSONObject) {
        c = json
        dirty.clear()
    }

    // ---- plain settings
    fun str(k: String, def: String = "") = if (c.isNull(k)) def else c.optString(k, def)
    fun bool(k: String, def: Boolean = false) = if (c.has(k) && !c.isNull(k)) c.optBoolean(k, def) else def
    fun num(k: String, def: Double = 0.0) = if (c.has(k) && !c.isNull(k)) c.optDouble(k, def) else def

    fun put(k: String, v: Any?, now: Boolean = false) {
        c.put(k, v ?: JSONObject.NULL)
        dirty.add(k)
        save(now)
    }

    /** Something inside an object or array setting changed (its key is saved whole). */
    fun touched(vararg keys: String) {
        dirty.addAll(keys)
        if (keys.any { it in listOf("areas", "style", "profile_opts") }) syncProfile()
        save()
    }

    private val saveNow = Runnable { flush() }

    fun save(now: Boolean = false) {
        main.removeCallbacks(saveNow)
        if (now) flush() else main.postDelayed(saveNow, 400)
    }

    fun flush() {
        main.removeCallbacks(saveNow)
        if (dirty.isEmpty()) return
        val patch = JSONObject()
        for (k in dirty) patch.put(k, c.opt(k) ?: JSONObject.NULL)
        dirty.clear()
        val s = patch.toString()
        io.execute {
            val ok = try {
                core.updateConfig(s)
                true
            } catch (e: Exception) {
                Log.w("Cfg", "save failed", e)
                false
            }
            main.post { saved?.invoke(ok) }
        }
    }

    // ---- game profiles (the desktop's activeProfile, isBuiltin, locked...)
    fun builtins(): List<JSONObject> = list("builtins")
    fun profiles(): List<JSONObject> = list("profiles")
    fun allProfiles() = builtins() + profiles()
    fun activeProfile(): JSONObject? = allProfiles().firstOrNull { it.optString("id") == str("profile") } ?: builtins().firstOrNull()
    fun isBuiltin(p: JSONObject?) = p != null && builtins().any { it.optString("id") == p.optString("id") }
    fun locked() = isBuiltin(activeProfile())
    fun profileGame() = activeProfile()?.optString("game")?.ifEmpty { null } ?: "gta6"
    fun gameName(g: String) = builtins().firstOrNull { it.optString("id") == g }?.optString("name") ?: g

    fun areas(): JSONArray = c.optJSONArray("areas") ?: JSONArray().also { c.put("areas", it) }
    fun style(): JSONObject = c.optJSONObject("style") ?: JSONObject().also { c.put("style", it) }
    fun profileOpts(): JSONObject = c.optJSONObject("profile_opts") ?: JSONObject().also { c.put("profile_opts", it) }

    /** One of the player's profiles is in use: keep it equal to what is being edited. */
    private fun syncProfile() {
        val arr = c.optJSONArray("profiles") ?: return
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            if (p.optString("id") == str("profile")) {
                p.put("areas", clone(areas())).put("style", clone(style())).put("pages", clone(profileOpts()))
                dirty.add("profiles")
            }
        }
    }

    /** Use another profile: its areas, style and pages become the ones in use. */
    fun useProfile(id: String) {
        syncProfile()
        val p = allProfiles().firstOrNull { it.optString("id") == id } ?: return
        c.put("profile", id)
        c.put("areas", clone(p.optJSONArray("areas") ?: JSONArray()))
        c.put("style", clone(p.optJSONObject("style") ?: JSONObject()))
        c.put("profile_opts", clone(p.optJSONObject("pages") ?: JSONObject()))
        dirty.addAll(listOf("profile", "areas", "style", "profile_opts"))
        save()
    }

    /** A copy of a profile (built-in or not) that the player can change; it is put in use. */
    fun copyProfile(from: JSONObject) {
        syncProfile()
        val id = "custom-${java.lang.Long.toString(System.currentTimeMillis(), 36)}"
        val arr = c.optJSONArray("profiles") ?: JSONArray().also { c.put("profiles", it) }
        arr.put(
            JSONObject().put("id", id).put("name", T("{name} (copy)", "name" to from.optString("name"))).put("game", from.optString("game"))
                .put("areas", clone(from.optJSONArray("areas") ?: JSONArray())).put("style", clone(from.optJSONObject("style") ?: JSONObject()))
                .put("pages", clone(from.optJSONObject("pages") ?: JSONObject())).put("timing", clone(from.optJSONObject("timing") ?: JSONObject())),
        )
        dirty.add("profiles")
        useProfile(id)
    }

    fun deleteProfile(p: JSONObject) {
        val arr = c.optJSONArray("profiles") ?: return
        val keep = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("id") != p.optString("id")) keep.put(arr.get(i))
        c.put("profiles", keep)
        dirty.add("profiles")
        val game = p.optString("game")
        useProfile(if (builtins().any { it.optString("id") == game }) game else "gta6")
    }

    fun renameProfile(name: String) {
        val arr = c.optJSONArray("profiles") ?: return
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            if (p.optString("id") == str("profile")) p.put("name", name)
        }
        dirty.add("profiles")
        save()
    }

    /** Show or hide a read area or a game page of the profile in use: the player's own choice, kept apart from the profile. */
    fun setShown(kind: String, id: String, on: Boolean) {
        val shown = c.optJSONObject("shown") ?: JSONObject().also { c.put("shown", it) }
        val s = shown.optJSONObject(str("profile")) ?: JSONObject().also { shown.put(str("profile"), it) }
        val part = s.optJSONObject(if (kind == "area") "areas" else "pages") ?: JSONObject().also { s.put(if (kind == "area") "areas" else "pages", it) }
        part.put(id, on)
        dirty.add("shown")
    }

    private fun list(k: String): List<JSONObject> {
        val a = c.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    companion object {
        fun clone(o: JSONObject): JSONObject = JSONObject(o.toString())
        fun clone(a: JSONArray): JSONArray = JSONArray(a.toString())

        /** the style an area uses: its own, or the profile's */
        fun styleOf(a: JSONObject, profileStyle: JSONObject): JSONObject = a.optJSONObject("style") ?: profileStyle
    }
}
