package app.vitranslator.phone

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language: the phone's own, or one picked in the app (the desktop app's interface
 * languages; Arabic and English first). Arabic is written right to left: the screens follow
 * the language's direction.
 */
object Lang {
    data class Choice(val tag: String, val name: String)

    val ALL = listOf(
        Choice("ar", "العربية"),
        Choice("en", "English"),
    )

    fun wrap(base: Context): Context {
        val tag = Prefs(base).uiLanguage
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(locale)
        conf.setLayoutDirection(locale)
        return base.createConfigurationContext(conf)
    }
}
