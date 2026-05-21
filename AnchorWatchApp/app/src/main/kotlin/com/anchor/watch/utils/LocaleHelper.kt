package com.anchor.watch.utils

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

object LocaleHelper {

    private val HEBREW: Locale = Locale(LanguagePreference.LANG_HEBREW)
    private val ENGLISH: Locale = Locale(LanguagePreference.LANG_ENGLISH)

    fun wrap(context: Context): Context {
        val lang = LanguagePreference.language(context) ?: return context
        val locale = localeFor(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun layoutDirection(context: Context): LayoutDirection =
        when (LanguagePreference.language(context)) {
            LanguagePreference.LANG_HEBREW -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }

    private fun localeFor(language: String): Locale = when (language) {
        LanguagePreference.LANG_HEBREW -> HEBREW
        else -> ENGLISH
    }
}
