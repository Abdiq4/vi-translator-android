package app.vitranslator.phone.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The interface's words, as the desktop app's: each phrase is looked up by its English text in the
 * desktop's dictionaries (assets/i18n/<code>.json, from its ui/i18n by tools/sync_desktop_ui.py),
 * then in the phone's own (assets/i18n-phone/<code>.json, for what only the phone says); a phrase
 * missing from both stays English. {name} placeholders are filled from [vars].
 */
object T {
    data class UiLang(val code: String, val name: String, val rtl: Boolean)

    var langs: List<UiLang> = listOf(UiLang("en", "English", false))
        private set

    /** the interface language in use */
    var code = "en"
        private set

    val rtl get() = langs.firstOrNull { it.code == code }?.rtl ?: false

    private var dict: Map<String, String> = emptyMap()

    fun load(ctx: Context, wanted: String) {
        if (langs.size == 1) langs = readLangs(ctx)
        code = langs.firstOrNull { it.code == wanted }?.code ?: "en"
        dict = if (code == "en") emptyMap() else read(ctx, "i18n/$code.json") + read(ctx, "i18n-phone/$code.json")
    }

    /** The interface language for a phone whose own language is [locale] (the desktop's choice for a new install). */
    fun fromLocale(locale: Locale): String {
        val tag = locale.toLanguageTag()
        val exact = langs.firstOrNull { it.code.equals(tag, true) }
        if (exact != null) return exact.code
        return when (locale.language) {
            "zh" -> if (tag.contains("Hant") || locale.country in listOf("TW", "HK", "MO")) "zh-Hant" else "zh-Hans"
            "es" -> if (locale.country == "ES") "es-ES" else "es-MX"
            "pt" -> "pt-BR"
            "in" -> "id"
            "tl" -> "fil"
            else -> langs.firstOrNull { it.code == locale.language }?.code ?: "en"
        }
    }

    operator fun invoke(en: String, vararg vars: Pair<String, Any?>): String {
        var s = dict[en] ?: en
        for ((k, v) in vars) s = s.replace("{$k}", v.toString())
        return s
    }

    private fun readLangs(ctx: Context): List<UiLang> = try {
        val a = JSONArray(ctx.assets.open("i18n/languages.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { a.getJSONObject(it) }.map { UiLang(it.getString("code"), it.getString("name"), it.optBoolean("rtl")) }
    } catch (e: Exception) {
        listOf(UiLang("en", "English", false))
    }

    private fun read(ctx: Context, path: String): Map<String, String> = try {
        val o = JSONObject(ctx.assets.open(path).bufferedReader().use { it.readText() })
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }
}
