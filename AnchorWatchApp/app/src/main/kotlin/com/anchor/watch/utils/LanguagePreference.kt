package com.anchor.watch.utils

import android.content.Context

object LanguagePreference {

    const val LANG_HEBREW = "he"
    const val LANG_ENGLISH = "en"

    private const val PREFS = "anchor_language"
    private const val KEY_LANG = "language"

    fun language(context: Context): String? =
        prefs(context).getString(KEY_LANG, null)

    fun isConfigured(context: Context): Boolean = language(context) != null

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_LANG, language).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LANG).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
