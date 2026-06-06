package com.anchor.watch.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Locale plumbing built on the platform per-app language API ([LocaleManager],
 * Android 13+/API 33+; minSdk here is 34 so it is always available).
 *
 * The OS owns locale resolution and layout direction for *every* activity once
 * [applyLanguage] is called — there is no per-activity `attachBaseContext` wrapping and
 * no global `Locale.setDefault` mutation (both removed: the global default bled RTL into
 * screens that never wrapped, distorting English layouts).
 */
object LocaleHelper {

    /** The active locale, read from the OS-configured context (never a hardcoded guess). */
    fun currentLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }

    /**
     * Layout direction derived from the *resolved* locale via the framework's own
     * bidi rules — correct for any RTL locale, not just a hardcoded Hebrew check.
     */
    fun layoutDirection(context: Context): LayoutDirection =
        if (TextUtils.getLayoutDirectionFromLocale(currentLocale(context)) ==
            View.LAYOUT_DIRECTION_RTL
        ) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }

    /**
     * Wraps [base] with a configuration-overridden context whose locale matches the
     * saved language preference. Call from every Activity's attachBaseContext so that
     * string resources resolve to the correct language even when LocaleManager hasn't
     * propagated yet (e.g. activities started from alarms or FCM notifications).
     */
    fun wrapContext(base: Context): Context {
        val lang = LanguagePreference.language(base) ?: return base
        val locale = Locale.forLanguageTag(lang)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Persist & apply [language] (a BCP-47 tag, e.g. "he"/"en") through the platform API.
     * The system stores the preference and recreates activities with the matching locale
     * and layout direction. Call once from the language picker.
     */
    fun applyLanguage(context: Context, language: String) {
        context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales = LocaleList.forLanguageTags(language)
    }
}
