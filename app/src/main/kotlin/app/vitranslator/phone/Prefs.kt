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

    /** dark theme (the desktop's "theme" setting, kept here too for the first frame) */
    var dark: Boolean
        get() = p.getBoolean("dark", true)
        set(v) = p.edit().putBoolean("dark", v).apply()

    /** the live picture in the app (the header's Preview switch) */
    var preview: Boolean
        get() = p.getBoolean("preview", true)
        set(v) = p.edit().putBoolean("preview", v).apply()

    /** "play" or "setup", as last left */
    var lastMode: String
        get() = p.getString("mode", "setup") ?: "setup"
        set(v) = p.edit().putString("mode", v).apply()

    /** the PS5 stream's resolution: 720 (the default) or 1080 */
    var psResolution: Int
        get() = p.getInt("ps_resolution", 720)
        set(v) = p.edit().putInt("ps_resolution", v).apply()

    /** the capture card's 4K picture (else 1080p, as the desktop's 4K switch) */
    var card4k: Boolean
        get() = p.getBoolean("card_4k", false)
        set(v) = p.edit().putBoolean("card_4k", v).apply()

    // ---- the live translations full screen (LiveFull)

    /** the translations' text size there, in sp */
    var liveTextSize: Float
        get() = p.getFloat("live_text_size", 30f)
        set(v) = p.edit().putFloat("live_text_size", v).apply()

    /** the English line shown above each translation there */
    var liveEnglish: Boolean
        get() = p.getBoolean("live_english", true)
        set(v) = p.edit().putBoolean("live_english", v).apply()

    /** "auto" (turns with the phone), "portrait" or "landscape" */
    var liveRotation: String
        get() = p.getString("live_rotation", "auto") ?: "auto"
        set(v) = p.edit().putString("live_rotation", v).apply()
}
