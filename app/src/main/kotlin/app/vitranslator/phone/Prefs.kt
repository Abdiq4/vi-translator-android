package app.vitranslator.phone

import android.content.Context

/** The user's choices, kept across runs. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** "ps5" or "xbox" ("" until chosen) */
    var console: String
        get() = p.getString("console", "") ?: ""
        set(v) = p.edit().putString("console", v).apply()

    var game: String
        get() = p.getString("game", "gta6") ?: "gta6"
        set(v) = p.edit().putString("game", v).apply()

    /** the language translated into */
    var language: String
        get() = p.getString("language", "ar") ?: "ar"
        set(v) = p.edit().putString("language", v).apply()

    /** the app's own language ("" for the phone's) */
    var uiLanguage: String
        get() = p.getString("ui_language", "") ?: ""
        set(v) = p.edit().putString("ui_language", v).apply()

    /** the PS5 stream's resolution: 720 or 1080 */
    var psResolution: Int
        get() = p.getInt("ps_resolution", 1080)
        set(v) = p.edit().putInt("ps_resolution", v).apply()
}
